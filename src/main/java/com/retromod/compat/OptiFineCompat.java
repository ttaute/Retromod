/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Special compatibility handler for OptiFine.
 * 
 * OptiFine is notoriously difficult to work with because:
 * - Closed source (can't see what it changes)
 * - Heavy bytecode manipulation of rendering code
 * - Conflicts with MANY mods (especially Sodium, Iris, Indium)
 * - Uses non-standard rendering paths
 * - Breaks frequently between Minecraft versions
 * 
 * RetroMod provides LIMITED support for OptiFine:
 * - Detect when OptiFine is present
 * - Warn user about known issues
 * - Recommend alternatives (Sodium + Iris)
 * - Apply basic bytecode fixes where possible
 */
public class OptiFineCompat {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-OptiFine");
    
    private static boolean optiFineDetected = false;
    private static String optiFineVersion = null;
    
    /**
     * Check if a JAR is OptiFine.
     */
    public static boolean isOptiFine(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // OptiFine has a specific class structure
            ZipEntry entry = jar.getEntry("optifine/OptiFineTransformationService.class");
            if (entry != null) return true;
            
            entry = jar.getEntry("net/optifine/Config.class");
            if (entry != null) return true;
            
            entry = jar.getEntry("optifine/Installer.class");
            if (entry != null) return true;
            
            // Check filename
            String name = jarPath.getFileName().toString().toLowerCase();
            if (name.contains("optifine") || name.contains("optifine")) {
                return true;
            }
            
        } catch (Exception e) {
            LOGGER.debug("Could not check JAR for OptiFine: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Get OptiFine version from JAR.
     */
    public static String getOptiFineVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try to read version from changelog
            ZipEntry entry = jar.getEntry("changelog.txt");
            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                // First line usually has version
                String firstLine = content.split("\n")[0];
                if (firstLine.contains("OptiFine")) {
                    return firstLine.trim();
                }
            }
            
            // Parse from filename
            String name = jarPath.getFileName().toString();
            // OptiFine_1.20.4_HD_U_I7.jar -> 1.20.4_HD_U_I7
            if (name.contains("OptiFine_")) {
                return name.replace("OptiFine_", "").replace(".jar", "");
            }
            
        } catch (Exception e) {
            LOGGER.debug("Could not get OptiFine version: {}", e.getMessage());
        }
        
        return "Unknown";
    }
    
    /**
     * Called when OptiFine is detected.
     * Shows warning and offers alternatives.
     */
    public static void handleOptiFineDetected(Path jarPath, boolean isServer) {
        optiFineDetected = true;
        optiFineVersion = getOptiFineVersion(jarPath);
        
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        LOGGER.warn("  OPTIFINE DETECTED!");
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        LOGGER.warn("  Version: {}", optiFineVersion);
        LOGGER.warn("");
        LOGGER.warn("  ⚠️  OptiFine has LIMITED support in RetroMod!");
        LOGGER.warn("");
        LOGGER.warn("  KNOWN ISSUES:");
        LOGGER.warn("  - May crash with other rendering mods");
        LOGGER.warn("  - Shader support may not work");
        LOGGER.warn("  - Performance may be worse than alternatives");
        LOGGER.warn("  - Some features may be broken");
        LOGGER.warn("");
        LOGGER.warn("  RECOMMENDED ALTERNATIVES:");
        LOGGER.warn("  - Sodium (better FPS) + Iris (shaders)");
        LOGGER.warn("  - Both work great with RetroMod!");
        LOGGER.warn("  - https://modrinth.com/mod/sodium");
        LOGGER.warn("  - https://modrinth.com/mod/iris");
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        
        if (!isServer) {
            showOptiFineWarningDialog();
        }
    }
    
    /**
     * Queue in-game warning about OptiFine.
     */
    private static void showOptiFineWarningDialog() {
        String message =
            "RetroMod has LIMITED support for OptiFine.\n\n" +
            "KNOWN ISSUES:\n" +
            "- May crash with other rendering mods\n" +
            "- Shader support may not work correctly\n" +
            "- Performance may actually be worse\n\n" +
            "RECOMMENDED ALTERNATIVES:\n" +
            "- Sodium (better FPS): modrinth.com/mod/sodium\n" +
            "- Iris (shaders): modrinth.com/mod/iris\n" +
            "- Both work great with RetroMod!";

        com.retromod.gui.InGameNotificationManager.queue(
            "RetroMod - OptiFine Warning", message);
    }
    
    /**
     * Check if OptiFine was detected in this session.
     */
    public static boolean isOptiFinePresent() {
        return optiFineDetected;
    }
    
    /**
     * Get detected OptiFine version.
     */
    public static String getDetectedVersion() {
        return optiFineVersion;
    }
    
    /**
     * List of mods known to conflict with OptiFine.
     */
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
    
    /**
     * Check if a mod ID conflicts with OptiFine.
     */
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
    
    /**
     * Log conflict warning.
     */
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
