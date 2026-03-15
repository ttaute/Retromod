/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.retromod.gui.InGameNotificationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.regex.*;

/**
 * Pre-launch entry point for Fabric.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * CRITICAL: This runs BEFORE Fabric scans the mods folder!
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * RetroMod supports TWO input locations:
 * 1. .minecraft/retromod-input/           (PRIMARY - recommended)
 * 2. .minecraft/mods/retromod-input/      (SECONDARY - for convenience)
 * 
 * Users naturally look in the mods folder first, so we support both.
 * A guide file in mods/ directs users to the right place.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * WHY RESTART IS REQUIRED:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Fabric Loader scans the mods folder ONCE at startup, before mods initialize.
 * Even though PreLaunch runs early, it's not early enough - Fabric has already
 * read the mod list by the time we run.
 * 
 * The sequence is:
 * 1. Fabric scans mods/ folder (WE CAN'T CHANGE THIS)
 * 2. Fabric loads mod metadata
 * 3. PreLaunch entrypoints run (RetroMod transforms here)
 * 4. Mods initialize
 * 
 * So transformed mods only appear on the NEXT launch.
 * This is a Fabric limitation, not a RetroMod limitation.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RetroModPreLaunch implements PreLaunchEntrypoint {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod");
    
    // Two input locations
    private static final String PRIMARY_INPUT = "retromod-input";
    private static final String SECONDARY_INPUT = "mods/retromod-input";
    private static final String PROCESSED_SUFFIX = "/processed";

    // Default MC version if auto-detection fails
    private static final String DEFAULT_MC_VERSION = "1.21.11";

    // Pre-compiled patterns for version extraction
    private static final Pattern PAT_MC_VERSION_JSON = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_VERSION_RANGE_TOML = Pattern.compile("versionRange\\s*=\\s*\"\\[([0-9.]+)");
    
    // Track transformation results
    private static int totalTransformed = 0;
    private static List<String> transformedMods = new ArrayList<>();
    private static List<String> skippedComplexMods = new ArrayList<>();
    
    @Override
    public void onPreLaunch() {
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║  RetroMod v1.0.0-beta.1                                    ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir == null) {
                LOGGER.warn("Could not determine game directory, using current directory");
                gameDir = Path.of(".");
            }
            String targetVersion = getMinecraftVersion();
            
            LOGGER.info("Target Minecraft version: {}", targetVersion);

            // Step 0: Register shims BEFORE transforming so redirects are available
            registerShimsForTransform();

            // Step 1: Create all folders and guide files
            createFoldersAndGuides(gameDir);
            
            // Step 2: Transform mods from BOTH input locations
            int fromPrimary = transformModsFromFolder(
                gameDir.resolve(PRIMARY_INPUT),
                gameDir.resolve(PRIMARY_INPUT + PROCESSED_SUFFIX),
                gameDir.resolve("mods"),
                targetVersion
            );
            
            int fromSecondary = transformModsFromFolder(
                gameDir.resolve(SECONDARY_INPUT),
                gameDir.resolve(SECONDARY_INPUT + PROCESSED_SUFFIX),
                gameDir.resolve("mods"),
                targetVersion
            );
            
            totalTransformed = fromPrimary + fromSecondary;

            // Step 3: Process mixin configs in already-installed mods
            int mixinFixed = processMixinConfigsInMods(gameDir.resolve("mods"));
            if (mixinFixed > 0) {
                totalTransformed += mixinFixed;
                LOGGER.info("Processed mixin configs in {} already-installed mod(s) — restart required", mixinFixed);
            }

            // Step 4: Show restart message if we transformed anything
            if (totalTransformed > 0) {
                showRestartMessage();
            }

            // Step 5: Show complexity warnings for complex mods (still transformed)
            if (!skippedComplexMods.isEmpty()) {
                showComplexityWarning();
            }

            LOGGER.info("RetroMod pre-launch complete!");
            
        } catch (Exception e) {
            LOGGER.error("RetroMod pre-launch error: {}", e.getMessage());
        }
    }
    
    /**
     * Register all version shims and polyfills so that the bytecode transformer
     * has redirects available BEFORE AOT transformation runs.
     * Without this, mods are transformed with an empty redirect map.
     */
    private void registerShimsForTransform() {
        try {
            RetroModTransformer transformer = RetroModTransformer.getInstance();

            // Load version shims via ServiceLoader
            ServiceLoader<VersionShim> shims = ServiceLoader.load(VersionShim.class);
            int shimCount = 0;
            for (VersionShim shim : shims) {
                try {
                    shim.registerRedirects(transformer);
                    shimCount++;
                } catch (Exception e) {
                    LOGGER.debug("Could not register shim: {}", e.getMessage());
                }
            }
            LOGGER.info("Registered {} version shims for transformation", shimCount);

            // =====================================================================
            // Global redirects: return type changes that apply across many versions
            // =====================================================================

            // CrashReport.getFile() changed from File to Path in ~1.20.5+
            // Old: net/minecraft/class_128.method_572()Ljava/io/File;
            // New: net/minecraft/class_128.method_572()Ljava/nio/file/Path;
            // Bridge: CrashReportShim.getFileAsFile(CrashReport) -> File
            transformer.registerMethodRedirect(
                "net/minecraft/class_128", "method_572",
                "()Ljava/io/File;",
                "com/retromod/shim/fabric/embedded/CrashReportShim", "getFileAsFile",
                "(Ljava/lang/Object;)Ljava/io/File;"
            );
            LOGGER.debug("Registered global redirect: CrashReport.getFile() File→Path bridge");

            // Load polyfill providers via ServiceLoader
            try {
                ServiceLoader<com.retromod.polyfill.PolyfillProvider> polyfills =
                    ServiceLoader.load(com.retromod.polyfill.PolyfillProvider.class);
                int polyfillCount = 0;
                for (com.retromod.polyfill.PolyfillProvider provider : polyfills) {
                    try {
                        provider.registerPolyfills(transformer);
                        polyfillCount++;
                    } catch (Exception e) {
                        LOGGER.debug("Could not register polyfill: {}", e.getMessage());
                    }
                }
                if (polyfillCount > 0) {
                    LOGGER.info("Registered {} polyfill providers for transformation", polyfillCount);
                }
            } catch (Exception e) {
                LOGGER.debug("No polyfill providers found");
            }
        } catch (Exception e) {
            LOGGER.warn("Could not register shims for pre-launch transform: {}", e.getMessage());
        }
    }

    /**
     * Get the actual Minecraft version.
     */
    private String getMinecraftVersion() {
        try {
            return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(DEFAULT_MC_VERSION);
        } catch (Exception e) {
            return DEFAULT_MC_VERSION;
        }
    }
    
    /**
     * Create all folders and guide files.
     */
    private void createFoldersAndGuides(Path gameDir) {
        try {
            // Create PRIMARY input folder (.minecraft/retromod-input/)
            Path primaryInput = gameDir.resolve(PRIMARY_INPUT);
            Path primaryProcessed = gameDir.resolve(PRIMARY_INPUT + PROCESSED_SUFFIX);
            Files.createDirectories(primaryInput);
            Files.createDirectories(primaryProcessed);
            
            // Create SECONDARY input folder (.minecraft/mods/retromod-input/)
            Path secondaryInput = gameDir.resolve(SECONDARY_INPUT);
            Path secondaryProcessed = gameDir.resolve(SECONDARY_INPUT + PROCESSED_SUFFIX);
            Files.createDirectories(secondaryInput);
            Files.createDirectories(secondaryProcessed);
            
            // Create README in primary folder
            createPrimaryReadme(primaryInput);
            
            // Create README in secondary folder
            createSecondaryReadme(secondaryInput);
            
            // Create GUIDE in mods folder (most important!)
            createModsFolderGuide(gameDir.resolve("mods"));
            
            LOGGER.info("Created retromod-input/ folders");
            
        } catch (Exception e) {
            LOGGER.error("Could not create folders: {}", e.getMessage());
        }
    }
    
    /**
     * Create README in primary input folder.
     */
    private void createPrimaryReadme(Path folder) {
        try {
            Path readme = folder.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                    ╔════════════════════════════════════════════════════════════╗
                    ║  RETROMOD INPUT FOLDER (PRIMARY)                           ║
                    ╠════════════════════════════════════════════════════════════╣
                    ║                                                            ║
                    ║  Put your OLD mods here!                                   ║
                    ║                                                            ║
                    ║  HOW TO USE:                                               ║
                    ║  1. Drop old .jar mod files into this folder               ║
                    ║  2. Start Minecraft                                        ║
                    ║  3. RetroMod transforms them automatically                 ║
                    ║  4. ⚠️ RESTART Minecraft (required first time)             ║
                    ║  5. Your mods work!                                        ║
                    ║                                                            ║
                    ║  WHY RESTART?                                              ║
                    ║  Fabric scans mods BEFORE RetroMod can run. Transformed    ║
                    ║  mods only load on the next launch. This is a Fabric       ║
                    ║  limitation, not a RetroMod bug.                           ║
                    ║                                                            ║
                    ║  AFTER TRANSFORMATION:                                     ║
                    ║  - Originals move to: processed/                           ║
                    ║  - Transformed mods go to: mods/                           ║
                    ║                                                            ║
                    ║  ALTERNATIVE LOCATION:                                     ║
                    ║  You can also use: mods/retromod-input/                    ║
                    ║                                                            ║
                    ╚════════════════════════════════════════════════════════════╝
                    """);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Create README in secondary input folder (mods/retromod-input/).
     */
    private void createSecondaryReadme(Path folder) {
        try {
            Path readme = folder.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                    ╔════════════════════════════════════════════════════════════╗
                    ║  RETROMOD INPUT FOLDER (SECONDARY)                         ║
                    ╠════════════════════════════════════════════════════════════╣
                    ║                                                            ║
                    ║  This folder works too! Put old mods here.                 ║
                    ║                                                            ║
                    ║  Both locations work:                                      ║
                    ║  • .minecraft/retromod-input/      (primary)               ║
                    ║  • .minecraft/mods/retromod-input/ (this folder)           ║
                    ║                                                            ║
                    ║  Same instructions:                                        ║
                    ║  1. Drop old .jar files here                               ║
                    ║  2. Start Minecraft                                        ║
                    ║  3. RESTART Minecraft                                      ║
                    ║  4. Mods work!                                             ║
                    ║                                                            ║
                    ╚════════════════════════════════════════════════════════════╝
                    """);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Create guide file in mods folder to help users find retromod-input.
     */
    private void createModsFolderGuide(Path modsFolder) {
        try {
            Path guide = modsFolder.resolve("!RETROMOD-READ-ME-FIRST!.txt");
            
            // Always update the guide (in case instructions change)
            Files.writeString(guide, """
                ╔════════════════════════════════════════════════════════════╗
                ║                                                            ║
                ║   ██████╗ ███████╗████████╗██████╗  ██████╗               ║
                ║   ██╔══██╗██╔════╝╚══██╔══╝██╔══██╗██╔═══██╗              ║
                ║   ██████╔╝█████╗     ██║   ██████╔╝██║   ██║              ║
                ║   ██╔══██╗██╔══╝     ██║   ██╔══██╗██║   ██║              ║
                ║   ██║  ██║███████╗   ██║   ██║  ██║╚██████╔╝              ║
                ║   ╚═╝  ╚═╝╚══════╝   ╚═╝   ╚═╝  ╚═╝ ╚═════╝               ║
                ║                                                            ║
                ║   MOD INSTALLATION GUIDE                                   ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   ⚠️  DO NOT PUT OLD MODS DIRECTLY IN THIS FOLDER!  ⚠️    ║
                ║                                                            ║
                ║   If you put old mods here, Minecraft will CRASH with      ║
                ║   "mod requires Minecraft 1.20.x" errors!                  ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   WHERE TO PUT OLD MODS:                                   ║
                ║                                                            ║
                ║   Option 1 (Recommended):                                  ║
                ║   📁 .minecraft/retromod-input/                            ║
                ║                                                            ║
                ║   Option 2 (Also works):                                   ║
                ║   📁 .minecraft/mods/retromod-input/                       ║
                ║      (There's a folder inside THIS mods folder!)           ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   STEP BY STEP:                                            ║
                ║                                                            ║
                ║   1. Find the retromod-input folder                        ║
                ║      (either location above)                               ║
                ║                                                            ║
                ║   2. Put your OLD mod .jar files there                     ║
                ║      Example: sodium-1.20.4.jar                            ║
                ║                                                            ║
                ║   3. Start Minecraft                                       ║
                ║      RetroMod will transform the mods                      ║
                ║                                                            ║
                ║   4. ⚠️ RESTART Minecraft ⚠️                               ║
                ║      (This is required! Transformed mods load on restart)  ║
                ║                                                            ║
                ║   5. Your old mods now work! 🎉                            ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   WHERE DO MODS GO AFTER TRANSFORMATION?                   ║
                ║                                                            ║
                ║   • Transformed mods appear in THIS folder (mods/)         ║
                ║     with "-retromod" added to the name                     ║
                ║     Example: sodium-1.20.4-retromod.jar                    ║
                ║                                                            ║
                ║   • Original mods move to retromod-input/processed/        ║
                ║     (as a backup)                                          ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   WHY IS RESTART REQUIRED?                                 ║
                ║                                                            ║
                ║   Fabric Loader scans this folder ONCE at startup.         ║
                ║   RetroMod transforms mods, but Fabric already read        ║
                ║   the folder. On restart, Fabric sees the new mods.        ║
                ║                                                            ║
                ║   This is a Fabric limitation, not a RetroMod bug.         ║
                ║                                                            ║
                ╚════════════════════════════════════════════════════════════╝
                
                Need help? Visit: github.com/Bownlux/MC-RetroMod/issues
                """);
            
        } catch (Exception e) {
            LOGGER.debug("Could not create guide file: {}", e.getMessage());
        }
    }
    
    /**
     * Transform all mods from a specific input folder.
     */
    private int transformModsFromFolder(Path inputFolder, Path processedFolder, 
                                        Path outputFolder, String targetVersion) {
        if (!Files.exists(inputFolder)) {
            return 0;
        }
        
        int count = 0;
        
        try {
            // Find JAR files (not in subfolders, not README)
            List<Path> modsToTransform;
            try (var stream = Files.list(inputFolder)) {
                modsToTransform = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .filter(p -> Files.isRegularFile(p))
                    .toList();
            }
            
            if (modsToTransform.isEmpty()) {
                return 0;
            }
            
            LOGGER.info("Found {} mod(s) in {}", modsToTransform.size(), inputFolder.getFileName());
            
            // Create folders
            Files.createDirectories(processedFolder);
            Files.createDirectories(outputFolder);
            
            // Create transformer
            FabricModTransformer transformer = new FabricModTransformer(targetVersion);
            
            for (Path modJar : modsToTransform) {
                try {
                    String fileName = modJar.getFileName().toString();
                    String modVersion = extractModMinecraftVersion(modJar);

                    LOGGER.info("┌─ Processing: {}", fileName);
                    LOGGER.info("│  Mod version: {}", modVersion != null ? modVersion : "unknown");
                    LOGGER.info("│  Target: {}", targetVersion);

                    // Complexity check — warn but ALWAYS transform
                    // With required:false on mixins, even complex mods have a chance
                    com.retromod.gui.ModComplexityAnalyzer analyzer =
                        new com.retromod.gui.ModComplexityAnalyzer();
                    com.retromod.gui.ModComplexityAnalyzer.ComplexityReport report =
                        analyzer.analyze(modJar);

                    if (report.isUnlikelyToWork()) {
                        LOGGER.warn("│  ⚠ WARNING: Mod has high complexity (score: {})", report.score());
                        LOGGER.warn("│  Reason: {}", report.reason());
                        LOGGER.warn("│  Transforming anyway — broken mixins will be safely skipped at runtime");
                        skippedComplexMods.add(fileName);
                    }

                    // ALWAYS transform unless EXACT match
                    boolean needsTransform = !isExactVersionMatch(modVersion, targetVersion);

                    boolean success = false;

                    if (!needsTransform) {
                        LOGGER.info("│  Status: Already compatible (exact match)");
                        LOGGER.info("└─ Copying directly to mods/");
                        Files.copy(modJar, outputFolder.resolve(fileName),
                            StandardCopyOption.REPLACE_EXISTING);
                        success = true;
                    } else {
                        LOGGER.info("│  Status: Needs transformation");
                        Path transformed = transformer.transformMod(modJar, outputFolder);
                        if (transformed != null) {
                            LOGGER.info("└─ Created: {}", transformed.getFileName());
                            transformedMods.add(fileName);
                            success = true;
                        } else {
                            LOGGER.warn("└─ Transformation failed! Original kept in retromod-input/");
                        }
                    }

                    // Only move original to processed on success — keep it for retry on failure
                    if (success) {
                        Path processedPath = processedFolder.resolve(fileName);
                        Files.move(modJar, processedPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    count++;
                    
                } catch (Exception e) {
                    LOGGER.error("Failed to process {}: {}", modJar.getFileName(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error scanning {}: {}", inputFolder, e.getMessage());
        }
        
        return count;
    }
    
    /**
     * Process mixin configs in already-installed mods that haven't been mixin-processed yet.
     *
     * IMPORTANT: We CANNOT delete jars from mods/ during the current launch because
     * Fabric Loader has already scanned and loaded metadata from those files. Deleting
     * them causes NoSuchFileException when Fabric tries to access the jar later.
     *
     * Instead, we copy unprocessed jars to retromod-input/ and create a marker file
     * so that on the NEXT launch, the old version gets cleaned up before Fabric scans.
     */
    private int processMixinConfigsInMods(Path modsFolder) {
        if (!Files.exists(modsFolder)) return 0;

        Path gameDir = modsFolder.getParent();
        Path inputFolder = gameDir.resolve(PRIMARY_INPUT);

        int queued = 0;

        try {
            Files.createDirectories(inputFolder);

            // First, clean up any jars that were flagged for deletion on previous launch
            cleanupFlaggedJars(modsFolder);

            List<Path> modJars;
            try (var stream = Files.list(modsFolder)) {
                modJars = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("retromod-"))
                    .toList();
            }

            for (Path modJar : modJars) {
                try {
                    if (needsMixinReprocessing(modJar)) {
                        // Copy to retromod-input/ so the normal pipeline re-transforms it
                        // Strip the -retromod suffix so it gets treated as a fresh input
                        String fileName = modJar.getFileName().toString();
                        String originalName = fileName.replace("-retromod", "");
                        Path inputPath = inputFolder.resolve(originalName);

                        Files.copy(modJar, inputPath, StandardCopyOption.REPLACE_EXISTING);

                        // DON'T delete the jar now — Fabric already loaded it!
                        // Instead, flag it for deletion on next launch (before Fabric scans)
                        Path flagFile = modsFolder.resolve(fileName + ".retromod-delete");
                        Files.writeString(flagFile, "Delete this jar on next launch - needs mixin reprocessing");

                        queued++;
                        LOGGER.info("Queued '{}' for re-transformation on next launch (mixin processing needed)", fileName);
                        transformedMods.add(originalName + " (mixin re-transform, restart needed)");
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not queue {} for mixin reprocessing: {}",
                        modJar.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error scanning mods for mixin processing: {}", e.getMessage());
        }

        return queued;
    }

    /**
     * Clean up jars that were flagged for deletion on a previous launch.
     *
     * Only deletes the old jar if:
     *   1. The corresponding mod exists in retromod-input/ (ready for re-transform), OR
     *   2. The corresponding mod already exists in retromod-input/processed/ (already re-transformed)
     *
     * This prevents data loss if re-transformation fails — the old jar stays until
     * a replacement is confirmed.
     */
    private void cleanupFlaggedJars(Path modsFolder) {
        Path gameDir = modsFolder.getParent();
        Path inputFolder = gameDir.resolve(PRIMARY_INPUT);
        Path processedFolder = inputFolder.resolve("processed");

        try (var stream = Files.list(modsFolder)) {
            List<Path> flags = stream
                .filter(p -> p.toString().endsWith(".retromod-delete"))
                .toList();

            for (Path flagFile : flags) {
                String jarName = flagFile.getFileName().toString().replace(".retromod-delete", "");
                Path jarToDelete = modsFolder.resolve(jarName);

                // Derive the original name (without -retromod suffix)
                String originalName = jarName.replace("-retromod", "");

                // Check if the replacement source exists in retromod-input/ or processed/
                boolean replacementReady = Files.exists(inputFolder.resolve(originalName))
                    || Files.exists(processedFolder.resolve(originalName));

                try {
                    if (replacementReady && Files.exists(jarToDelete)) {
                        Files.delete(jarToDelete);
                        LOGGER.info("Cleaned up old jar flagged for deletion: {}", jarName);
                    } else if (!replacementReady) {
                        LOGGER.warn("Skipping deletion of {} — no replacement found in retromod-input/", jarName);
                    }
                    // Always clean up the flag file
                    Files.delete(flagFile);
                } catch (Exception e) {
                    LOGGER.warn("Could not clean up flagged jar {}: {}", jarName, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error cleaning up flagged jars: {}", e.getMessage());
        }
    }

    /**
     * Check if a mod JAR needs mixin config reprocessing.
     * Returns true if:
     * - It IS a RetroMod-transformed jar (has -retromod suffix or RetroMod-Transformed marker)
     * - It does NOT have the RetroMod-MixinProcessed manifest marker
     * - It has mixin config files (*.mixins.json)
     */
    private boolean needsMixinReprocessing(Path jarPath) {
        String fileName = jarPath.getFileName().toString();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();

            // Check if it's a RetroMod-transformed jar
            boolean isRetroModJar = fileName.contains("-retromod");
            if (manifest != null) {
                String transformed = manifest.getMainAttributes().getValue("RetroMod-Transformed");
                if ("true".equals(transformed)) isRetroModJar = true;
            }
            if (!isRetroModJar) return false;

            // Already mixin-processed?
            if (manifest != null) {
                String mixinProcessed = manifest.getMainAttributes().getValue("RetroMod-MixinProcessed");
                if ("true".equals(mixinProcessed)) return false;
            }

            // Has mixin configs?
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".mixins.json") || name.endsWith("mixin.json")) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore unreadable jars
        }
        return false;
    }

    /**
     * Check if mod version EXACTLY matches target (very strict).
     */
    private boolean isExactVersionMatch(String modVersion, String targetVersion) {
        if (modVersion == null) return false;
        
        // Clean the version
        String clean = modVersion
            .replace(">=", "")
            .replace("<=", "")
            .replace(">", "")
            .replace("<", "")
            .replace("~", "")
            .replace("^", "")
            .trim();
        
        // Only exact match counts
        return clean.equals(targetVersion);
    }
    
    /**
     * Extract Minecraft version from mod JAR.
     */
    private String extractModMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try Fabric
            ZipEntry fabricJson = jar.getEntry("fabric.mod.json");
            if (fabricJson != null) {
                String content = new String(jar.getInputStream(fabricJson).readAllBytes());
                Matcher m = PAT_MC_VERSION_JSON.matcher(content);
                if (m.find()) return m.group(1);
            }

            // Try Quilt
            ZipEntry quiltJson = jar.getEntry("quilt.mod.json");
            if (quiltJson != null) {
                String content = new String(jar.getInputStream(quiltJson).readAllBytes());
                Matcher m = PAT_MC_VERSION_JSON.matcher(content);
                if (m.find()) return m.group(1);
            }

            // Try Forge/NeoForge
            ZipEntry modsToml = jar.getEntry("META-INF/mods.toml");
            if (modsToml == null) modsToml = jar.getEntry("META-INF/neoforge.mods.toml");
            if (modsToml != null) {
                String content = new String(jar.getInputStream(modsToml).readAllBytes());
                Matcher m = PAT_VERSION_RANGE_TOML.matcher(content);
                if (m.find()) return m.group(1);
            }
            
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Show restart required message in logs and as a GUI popup.
     */
    private void showRestartMessage() {
        // Always log to console
        LOGGER.info("");
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   RESTART REQUIRED!                                        ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   RetroMod transformed {} mod(s):                          ║", String.format("%-2d", totalTransformed));
        for (String mod : transformedMods) {
            String display = mod.length() > 48 ? mod.substring(0, 45) + "..." : mod;
            LOGGER.info("║     - {}║", String.format("%-51s", display));
        }
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   Close Minecraft and launch again for mods to load.       ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   This is normal! Fabric needs a restart to see new mods.  ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        LOGGER.info("");

        // Queue in-game notification instead of Swing popup
        StringBuilder message = new StringBuilder();
        message.append("RetroMod transformed ").append(totalTransformed).append(" mod(s):\n\n");
        for (String mod : transformedMods) {
            String display = mod.length() > 50 ? mod.substring(0, 47) + "..." : mod;
            message.append("  - ").append(display).append("\n");
        }
        message.append("\nPlease close Minecraft and launch it again\n");
        message.append("for the transformed mods to load.\n\n");
        message.append("This only happens the first time. After restarting,\n");
        message.append("your old mods will work normally.");
        InGameNotificationManager.queue("RetroMod - Restart Required", message.toString());
    }

    /**
     * Show warning about mods that had high complexity scores.
     * These mods WERE still transformed — the warning is informational.
     */
    private void showComplexityWarning() {
        LOGGER.warn("");
        LOGGER.warn("╔════════════════════════════════════════════════════════════╗");
        LOGGER.warn("║   COMPLEX MODS (MAY HAVE ISSUES)                           ║");
        LOGGER.warn("╠════════════════════════════════════════════════════════════╣");
        for (String mod : skippedComplexMods) {
            String display = mod.length() > 51 ? mod.substring(0, 48) + "..." : mod;
            LOGGER.warn("║   ⚠ {}║", String.format("%-54s", display));
        }
        LOGGER.warn("╠════════════════════════════════════════════════════════════╣");
        LOGGER.warn("║   These mods use features that may not fully transform     ║");
        LOGGER.warn("║   (rendering, networking, ASM, etc.). They were still      ║");
        LOGGER.warn("║   transformed — broken mixins will be safely skipped.      ║");
        LOGGER.warn("╚════════════════════════════════════════════════════════════╝");
        LOGGER.warn("");

        // Queue for in-game display
        StringBuilder msg = new StringBuilder();
        msg.append(skippedComplexMods.size())
           .append(" mod(s) have high complexity and may not fully work:\n\n");
        for (String mod : skippedComplexMods) {
            msg.append("  - ").append(mod).append("\n");
        }
        msg.append("\nThey were still transformed — broken mixins\n");
        msg.append("will be safely skipped at runtime.");
        InGameNotificationManager.queue("RetroMod - Complex Mods", msg.toString());
    }
}
