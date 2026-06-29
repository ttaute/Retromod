/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Limited OptiFine support: detect it, warn about conflicts (Sodium/Iris/Indium and other
 * rendering mods), and point users at alternatives. OptiFine is closed source and rewrites
 * rendering bytecode, so we don't translate it.
 */
public class OptiFineCompat {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-OptiFine");
    
    private static boolean optiFineDetected = false;
    private static String optiFineVersion = null;
    
    /** Check if a JAR is OptiFine. */
    public static boolean isOptiFine(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("optifine/OptiFineTransformationService.class");
            if (entry != null) return true;

            entry = jar.getEntry("net/optifine/Config.class");
            if (entry != null) return true;

            entry = jar.getEntry("optifine/Installer.class");
            if (entry != null) return true;

            String name = jarPath.getFileName().toString().toLowerCase();
            if (name.contains("optifine")) {
                return true;
            }

        } catch (Exception e) {
            LOGGER.debug("Could not check JAR for OptiFine: {}", e.getMessage());
        }
        
        return false;
    }
    
    /** Get OptiFine version from JAR. */
    public static String getOptiFineVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("changelog.txt");
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    String content = new String(in.readAllBytes());
                    String firstLine = content.split("\n")[0];
                    if (firstLine.contains("OptiFine")) {
                        return firstLine.trim();
                    }
                }
            }

            // OptiFine_1.20.4_HD_U_I7.jar -> 1.20.4_HD_U_I7
            String name = jarPath.getFileName().toString();
            if (name.contains("OptiFine_")) {
                return name.replace("OptiFine_", "").replace(".jar", "");
            }

        } catch (Exception e) {
            LOGGER.debug("Could not get OptiFine version: {}", e.getMessage());
        }
        
        return "Unknown";
    }
    
    /** Warn about OptiFine and offer alternatives once it's been detected. */
    public static void handleOptiFineDetected(Path jarPath, boolean isServer) {
        optiFineDetected = true;
        optiFineVersion = getOptiFineVersion(jarPath);
        
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        LOGGER.warn("  OPTIFINE DETECTED!");
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        LOGGER.warn("  Version: {}", optiFineVersion);
        LOGGER.warn("");
        LOGGER.warn("  ⚠️  OptiFine has LIMITED support in Retromod! And Has MUCH better alternative's");
        LOGGER.warn("");
        LOGGER.warn("  KNOWN ISSUES:");
        LOGGER.warn("  - May crash with other rendering mods");
        LOGGER.warn("  - Shader support may not work");
        LOGGER.warn("  - Performance may be worse than alternatives");
        LOGGER.warn("  - Some features may be broken");
        LOGGER.warn("");
        LOGGER.warn("  RECOMMENDED ALTERNATIVES:");
        LOGGER.warn("  - Sodium (better FPS) + Iris (shaders)");
        LOGGER.warn("  - Both work great with Retromod!");
        LOGGER.warn("  - https://modrinth.com/mod/sodium");
        LOGGER.warn("  - https://modrinth.com/mod/iris");
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        
        if (!isServer) {
            showOptiFineWarningDialog();
        }
    }


    private static void showOptiFineWarningDialog() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        final int[] choiceHolder = {JOptionPane.CLOSED_OPTION};
        Runnable showDialog = () -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            String message = """
                ⚠️ OptiFine Detected!

                Retromod has LIMITED support for OptiFine.

                KNOWN ISSUES:
                • May crash with other rendering mods
                • Shader support may not work correctly
                • Performance may actually be worse
                • Some features may be completely broken

                ═══════════════════════════════════════

                RECOMMENDED ALTERNATIVES:

                • Sodium - Much better FPS optimization
                • Iris - Full shader support
                • Both work perfectly with Retromod!

                ═══════════════════════════════════════

                What would you like to do?
                """;

            choiceHolder[0] = JOptionPane.showOptionDialog(
                null,
                message,
                "Retromod - OptiFine Warning",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[]{"Open Sodium Page", "Continue Anyway", "Cancel"},
                "Open Sodium Page"
            );
        };

        try {
            if (SwingUtilities.isEventDispatchThread()) {
                showDialog.run();
            } else {
                SwingUtilities.invokeAndWait(showDialog);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return; // treat as "continue anyway"
        } catch (Exception e) {
            LOGGER.debug("Could not show OptiFine warning dialog: {}", e.getMessage());
            return;
        }

        int choice = choiceHolder[0];
        if (choice == 0) {
            try {
                Desktop.getDesktop().browse(URI.create("https://modrinth.com/mod/sodium"));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Please visit: https://modrinth.com/mod/sodium",
                    "Open Browser",
                    JOptionPane.INFORMATION_MESSAGE));
            }
        } else if (choice == 2) {
            throw new RuntimeException("User cancelled OptiFine installation");
        }
        // choice == 1 or dialog closed: continue anyway
    }

    public static boolean isOptiFinePresent() {
        return optiFineDetected;
    }

    public static String getDetectedVersion() {
        return optiFineVersion;
    }

    /** Mods known to conflict with OptiFine. */
    public static final String[] CONFLICTING_MODS = {
        "sodium",
        "iris",
        "indium",
        "rubidium",
        "embeddium",
        "canvas",
        "starlight",
        "phosphor"
    };
    
    /** Check if a mod ID conflicts with OptiFine. */
    public static boolean conflictsWithOptiFine(String modId) {
        if (!optiFineDetected) return false;

        String lower = modId.toLowerCase();
        for (String conflict : CONFLICTING_MODS) {
            if (lower.contains(conflict)) {
                return true;
            }
        }
        return false;
    }

    public static void logConflict(String modId) {
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  MOD CONFLICT DETECTED!");
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  OptiFine conflicts with: {}", modId);
        LOGGER.error("");
        LOGGER.error("  These mods CANNOT be used together!");
        LOGGER.error("  The game will likely crash or have severe issues.");
        LOGGER.error("");
        LOGGER.error("  SOLUTION: Remove either OptiFine or {}", modId);
        LOGGER.error("═══════════════════════════════════════════════════════════");
    }
}
