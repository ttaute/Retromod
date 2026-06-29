/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.jar.*;

/**
 * Detects mods that launch but misbehave after transformation, then offers to
 * restore the original from backup.
 */
public class ModHealthChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Health");

    private static final String BACKUP_FOLDER = "retromod-backups";

    private static final Map<String, ModHealthInfo> monitoredMods = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> modErrors = new ConcurrentHashMap<>();

    private static final int ERROR_THRESHOLD = 3;
    
    public record ModHealthInfo(
        String modId,
        String modName,
        Path transformedPath,
        Path backupPath,
        long transformTime,
        boolean isHealthy
    ) {}
    
    /** Create the input/processed/backup folders. Call early during mod init. */
    public static void ensureFoldersExist(Path gameDir) {
        try {
            Path inputFolder = gameDir.resolve("retromod-input");
            if (!Files.exists(inputFolder)) {
                Files.createDirectories(inputFolder);
                LOGGER.info("Created retromod-input/ folder");
                createReadme(inputFolder);
            }

            Path processedFolder = inputFolder.resolve("processed");
            if (!Files.exists(processedFolder)) {
                Files.createDirectories(processedFolder);
            }

            Path backupFolder = gameDir.resolve(BACKUP_FOLDER);
            if (!Files.exists(backupFolder)) {
                Files.createDirectories(backupFolder);
                LOGGER.info("Created retromod-backups/ folder");
            }

        } catch (IOException e) {
            LOGGER.error("Could not create Retromod folders", e);
        }
    }

    private static void createReadme(Path inputFolder) {
        try {
            Path readme = inputFolder.resolve("README.txt");
            Files.writeString(readme, """
                ═══════════════════════════════════════════════════════════════
                RETROMOD INPUT FOLDER
                ═══════════════════════════════════════════════════════════════
                
                Put your OLD mods here (NOT in the mods/ folder!)
                
                Retromod will automatically:
                1. Transform them to work with your Minecraft version
                2. Copy the transformed versions to mods/
                3. Move the originals to processed/
                
                STEPS:
                1. Download old mods (e.g., from Modrinth or CurseForge)
                2. Put the .jar files in THIS folder
                3. Start Minecraft
                4. Done! The mods will be transformed automatically.
                
                WHY NOT PUT DIRECTLY IN MODS FOLDER?
                
                Fabric checks mod versions BEFORE Retromod can help.
                If you put old mods directly in mods/, Fabric will crash!
                
                By using this folder, Retromod transforms mods first.
                
                ═══════════════════════════════════════════════════════════════
                SERVER-ONLY MODS
                ═══════════════════════════════════════════════════════════════
                
                If a mod is server-only (like Lithium), then:
                - Only the SERVER needs Retromod installed
                - Players can join WITHOUT having Retromod!
                - The transformed mod just works on the server
                
                Retromod will tell you if a mod is server-only.
                
                ═══════════════════════════════════════════════════════════════
                NEED HELP? FOUND A BUG?
                ═══════════════════════════════════════════════════════════════
                
                Report bugs on GitHub:
                https://github.com/Bownlux/Retromod/issues

                - Open an issue for bug reports
                - Ask questions
                - Share mod compatibility info
                
                ═══════════════════════════════════════════════════════════════
                """);
        } catch (IOException e) {
            LOGGER.debug("Could not create README", e);
        }
    }
    
    /** Register a transformed mod for health monitoring. */
    public static void registerTransformedMod(String modId, String modName,
                                               Path transformedPath, Path originalPath,
                                               Path gameDir) {

        Path backupPath = null;
        try {
            Path backupFolder = gameDir.resolve(BACKUP_FOLDER);
            Files.createDirectories(backupFolder);
            
            String backupName = originalPath.getFileName().toString()
                .replace(".jar", "-original.jar");
            backupPath = backupFolder.resolve(backupName);
            
            if (!Files.exists(backupPath)) {
                Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug("Created backup: {}", backupPath.getFileName());
            }
        } catch (IOException e) {
            LOGGER.warn("Could not create backup for {}", modId);
        }
        
        ModHealthInfo info = new ModHealthInfo(
            modId, modName, transformedPath, backupPath,
            System.currentTimeMillis(), true
        );
        
        monitoredMods.put(modId, info);
        modErrors.put(modId, new CopyOnWriteArrayList<>());
        
        LOGGER.debug("Monitoring mod health: {}", modId);
    }
    
    /** Record an error caught from a mod; marks it broken past the threshold. */
    public static void reportModError(String modId, String errorMessage, Throwable error) {
        List<String> errors = modErrors.computeIfAbsent(modId, k -> new CopyOnWriteArrayList<>());

        String fullError = errorMessage + ": " +
            (error != null ? error.getClass().getSimpleName() + " - " + error.getMessage() : "Unknown");

        errors.add(fullError);

        LOGGER.warn("Mod error reported for {}: {}", modId, fullError);

        if (errors.size() >= ERROR_THRESHOLD) {
            markModAsBroken(modId, errors);
        }
    }

    /** Attribute an error to a mod by matching its id against the class name. */
    public static void reportErrorByClass(String className, Throwable error) {
        for (Map.Entry<String, ModHealthInfo> entry : monitoredMods.entrySet()) {
            String modId = entry.getKey();
            if (className.toLowerCase().contains(modId.toLowerCase())) {
                reportModError(modId, "Error in " + className, error);
                return;
            }
        }

        LOGGER.warn("Error in unknown mod class {}: {}", className,
            error != null ? error.getMessage() : "Unknown");
    }

    private static void markModAsBroken(String modId, List<String> errors) {
        ModHealthInfo info = monitoredMods.get(modId);
        if (info == null || !info.isHealthy()) return;

        monitoredMods.put(modId, new ModHealthInfo(
            info.modId(), info.modName(), info.transformedPath(),
            info.backupPath(), info.transformTime(), false
        ));
        
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  MOD BROKEN AFTER TRANSFORMATION!");
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  Mod: {} ({})", info.modName(), modId);
        LOGGER.error("");
        LOGGER.error("  The game launched but this mod isn't working properly.");
        LOGGER.error("  This might be a Retromod compatibility issue.");
        LOGGER.error("");
        LOGGER.error("  PLEASE REPORT THIS BUG:");
        LOGGER.error("  → https://github.com/Bownlux/Retromod/issues");
        LOGGER.error("");
        LOGGER.error("  Recent errors:");
        for (String err : errors) {
            LOGGER.error("    • {}", err);
        }
        LOGGER.error("═══════════════════════════════════════════════════════════");

        if (EnvironmentDetector.canShowGui()) {
            showBrokenModDialog(info, errors);
        }
    }

    private static void showBrokenModDialog(ModHealthInfo info, List<String> errors) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            StringBuilder message = new StringBuilder();
            message.append("⚠️ Mod Not Working Properly!\n\n");
            message.append("═══════════════════════════════════════\n\n");
            message.append("Mod: ").append(info.modName()).append("\n\n");
            message.append("The game launched but this mod isn't\n");
            message.append("working correctly after transformation.\n\n");
            message.append("═══════════════════════════════════════\n\n");
            message.append("Please report this bug on GitHub:\n");
            message.append("→ github.com/Bownlux/Retromod/issues\n\n");
            message.append("═══════════════════════════════════════\n\n");
            message.append("Recent errors:\n");
            for (int i = 0; i < Math.min(3, errors.size()); i++) {
                message.append("• ").append(truncate(errors.get(i), 50)).append("\n");
            }
            
            int choice = JOptionPane.showOptionDialog(
                null,
                message.toString(),
                "Retromod - Mod Issue Detected",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[]{"Open GitHub Issues", "Restore Original", "Ignore"},
                "Open GitHub Issues"
            );
            
            if (choice == 0) {
                openGitHub();
            } else if (choice == 1) {
                restoreOriginal(info);
            }
        });
    }

    /** Delete the broken transformed jar and put the backup back in retromod-input/ for a retry. */
    public static boolean restoreOriginal(ModHealthInfo info) {
        if (info.backupPath() == null || !Files.exists(info.backupPath())) {
            LOGGER.error("No backup available for {}", info.modId());
            return false;
        }

        try {
            if (Files.exists(info.transformedPath())) {
                Files.delete(info.transformedPath());
                LOGGER.info("Deleted broken transformed mod: {}", info.transformedPath().getFileName());
            }

            Path inputFolder = info.transformedPath().getParent().getParent().resolve("retromod-input");
            Path destination = inputFolder.resolve(
                info.backupPath().getFileName().toString().replace("-original.jar", ".jar")
            );

            Files.copy(info.backupPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Restored original to retromod-input/: {}", destination.getFileName());
            
            JOptionPane.showMessageDialog(
                null,
                "Original mod restored!\n\n" +
                "The broken transformed version has been deleted.\n" +
                "The original has been placed back in retromod-input/\n\n" +
                "Please restart Minecraft to try again.\n" +
                "If it still doesn't work, report on GitHub Issues",
                "Mod Restored",
                JOptionPane.INFORMATION_MESSAGE
            );
            
            return true;
            
        } catch (IOException e) {
            LOGGER.error("Could not restore original for {}", info.modId(), e);
            return false;
        }
    }
    
    /** Open GitHub Issues via the Desktop API (no Runtime.exec). */
    private static void openGitHub() {
        try {
            String url = "https://github.com/Bownlux/Retromod/issues/new?title=Bug%20Report%20-%20Mod%20Not%20Working";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(java.net.URI.create(url));
            } else {
                LOGGER.warn("Desktop API not available - please visit {} manually", url);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not open browser");
        }
    }
    
    public static boolean isModHealthy(String modId) {
        ModHealthInfo info = monitoredMods.get(modId);
        return info == null || info.isHealthy();
    }

    public static List<ModHealthInfo> getBrokenMods() {
        return monitoredMods.values().stream()
            .filter(info -> !info.isHealthy())
            .toList();
    }

    /** Clear a mod's error history, e.g. after a restart. */
    public static void clearErrors(String modId) {
        modErrors.remove(modId);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    /** Sanity-check a transformed jar: non-empty, has mod metadata, size in range. */
    public static boolean validateTransformation(Path originalJar, Path transformedJar) {
        try {
            try (JarFile jar = new JarFile(transformedJar.toFile())) {
                if (!jar.entries().hasMoreElements()) {
                    LOGGER.warn("Transformed JAR is empty!");
                    return false;
                }

                if (jar.getEntry("fabric.mod.json") == null &&
                    jar.getEntry("META-INF/mods.toml") == null) {
                    LOGGER.warn("Transformed JAR missing mod metadata!");
                    return false;
                }
            }

            long originalSize = Files.size(originalJar);
            long transformedSize = Files.size(transformedJar);

            // size drift past 50% is suspect but not fatal
            if (transformedSize < originalSize * 0.5 || transformedSize > originalSize * 2) {
                LOGGER.warn("Transformed JAR size differs significantly: {} -> {}",
                    originalSize, transformedSize);
            }

            return true;

        } catch (Exception e) {
            LOGGER.error("Transformation validation failed", e);
            return false;
        }
    }
}
