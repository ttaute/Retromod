/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import com.retromod.mixin.MixinCompatibilityTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.regex.*;

/**
 * Transforms Fabric mods to work on newer Minecraft versions.
 *
 * APPROACH:
 * Instead of using fabric_loader_dependencies.json (which has timing issues),
 * we directly modify the mod's fabric.mod.json to say it supports the target version.
 *
 * This is the same approach used for Forge/NeoForge mods and avoids the need
 * for any global config files.
 *
 * Steps:
 * 1. Extract mod JAR to temp directory
 * 2. Transform all class files (bytecode)
 * 3. Update fabric.mod.json to support target version (including API dependencies)
 * 4. Repackage as new JAR with -retromod suffix
 * 5. Copy to mods folder
 */
public class FabricModTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-FabricTransform");

    /**
     * Fabric mod IDs for APIs that RetroMod provides compatibility shims for.
     * When a mod declares a dependency on one of these with a restrictive version
     * range (e.g., "cloth-config2": ">=6.0.0 <7.0.0"), Fabric Loader will block
     * the mod from loading if the installed version doesn't match.
     *
     * Since RetroMod transforms the bytecode to work with the newer API versions,
     * we also need to relax these dependency constraints in fabric.mod.json.
     */
    private static final Set<String> SHIMMED_API_MOD_IDS = Set.of(
        // Config libraries
        "cloth-config", "cloth-config2",
        "yacl", "yet-another-config-lib", "yet_another_config_lib",
        "forge-config-api-port", "forge_config_api_port",

        // Menu / UI libraries
        "modmenu", "mod-menu",
        "libgui", "lib-gui",
        "owo-lib", "owo_lib",

        // Recipe viewers
        "roughlyenoughitems", "rei",
        "emi",
        "jei", "just-enough-items",

        // Equipment / trinkets
        "trinkets",
        "curios", "curiosapi",

        // Component / data libraries
        "cardinal-components", "cardinal-components-api",
        "cardinal-components-base", "cardinal-components-entity",
        "cardinal-components-block", "cardinal-components-world",

        // Rendering / performance
        "sodium", "iris",
        "fabric-rendering-v1", "fabric-rendering-data-attachment-v1",

        // Animation / model libraries
        "geckolib", "geckolib3", "geckolib4",

        // Cross-platform
        "architectury", "architectury-api",

        // Guide / documentation
        "patchouli",

        // Tech mod APIs
        "ae2", "appliedenergistics2",
        "botania",
        "create",
        "mekanism",
        "thermal", "thermal_foundation", "thermal_expansion",

        // Tooltips / overlays
        "jade", "waila", "wthit",

        // Compatibility / utility
        "fabric-shield-lib", "fabricshieldlib",
        "lba", "libblockattributes",
        "mixinextras", "mixin-extras",
        "autoreglib", "auto-reg-lib"
    );

    // Pre-compiled regex patterns (avoid Pattern.compile in hot loops)
    private static final Pattern PAT_MOD_ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_MC_VERSION = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_MOD_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_PACKAGE = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_MIXINS_ARRAY = Pattern.compile("\"mixins\"\\s*:\\s*\\[([^\\]]+)\\]");
    private static final Pattern PAT_QUOTED_STRING = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern PAT_BREAKS = Pattern.compile("\"breaks\"\\s*:\\s*\\{");
    private static final Pattern PAT_DEPENDS_KEY = Pattern.compile("\"([a-z][a-z0-9_-]*)\"\\s*:");
    private static final Pattern PAT_DEP_UPPER_BOUND = Pattern.compile(
        "\"([a-z0-9_-]+)\"\\s*:\\s*\"([^\"]*<[^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern PAT_CLIENT_ARRAY = Pattern.compile("\"client\"\\s*:\\s*\\[([^\\]]+)\\]");
    private static final Pattern PAT_SERVER_ARRAY = Pattern.compile("\"server\"\\s*:\\s*\\[([^\\]]+)\\]");

    /** Cache for dynamically-built patterns keyed on the quoted string used to build them. */
    private static final Map<String, Pattern> DYNAMIC_PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final String targetMcVersion;
    private final RetroModTransformer bytecodeTransformer;
    
    public FabricModTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.bytecodeTransformer = RetroModTransformer.getInstance();
    }
    
    /**
     * Transform a Fabric mod JAR.
     * 
     * @param sourceJar Path to the original mod JAR
     * @param outputDir Directory to write the transformed JAR
     * @return Path to the transformed JAR, or null if failed/skipped
     */
    public Path transformMod(Path sourceJar, Path outputDir) throws IOException {
        String originalName = sourceJar.getFileName().toString();
        String baseName = originalName.replace(".jar", "");
        String outputName = baseName + "-retromod.jar";
        Path outputJar = outputDir.resolve(outputName);
        
        LOGGER.info("Checking Fabric mod: {}", originalName);
        
        // IMPORTANT: Check if mod is already for native version
        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (isNativeVersionMod(modMcVersion)) {
            LOGGER.info("═══════════════════════════════════════════════════════════");
            LOGGER.info("  {} is ALREADY for Minecraft {}", originalName, targetMcVersion);
            LOGGER.info("  NO TRANSFORMATION NEEDED - passing through!");
            LOGGER.info("═══════════════════════════════════════════════════════════");
            
            // Copy directly to output without transformation
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }
        
        // Check if mod uses Mixins
        boolean hasMixins = checkForMixins(sourceJar);
        if (hasMixins) {
            LOGGER.info("  Mod uses Mixins - will handle carefully");
        }
        
        LOGGER.info("Transforming Fabric mod: {} -> {}", originalName, outputName);
        
        // Log environment info (server-only, client-only, or both)
        ModEnvironmentDetector.logModEnvironment(sourceJar);
        
        // Check for OptiFine - special handling needed
        try {
            if (com.retromod.compat.OptiFineCompat.isOptiFine(sourceJar)) {
                com.retromod.compat.OptiFineCompat.handleOptiFineDetected(
                    sourceJar, 
                    com.retromod.core.EnvironmentDetector.isDedicatedServer()
                );
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("cancelled")) {
                throw new IOException("User cancelled OptiFine installation");
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check for OptiFine: {}", e.getMessage());
        }
        
        // Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("retromod-fabric-");
        
        // Get mod info for error reporting
        String modId = extractModId(sourceJar);
        String sourceVersion = extractMinecraftVersion(sourceJar);
        
        try {
            // Step 1: Extract JAR
            extractJar(sourceJar, tempDir);
            
            // Step 2: Transform bytecode
            int classesTransformed = transformClasses(tempDir);
            LOGGER.info("Transformed {} class files", classesTransformed);
            
            // Step 3: Update fabric.mod.json
            updateFabricModJson(tempDir);
            
            // Step 4: Repackage
            repackageJar(tempDir, outputJar, sourceJar);
            
            LOGGER.info("Created transformed mod: {}", outputJar.getFileName());
            
            // Validate the transformation
            if (!ModHealthChecker.validateTransformation(sourceJar, outputJar)) {
                LOGGER.warn("Transformation validation failed for {}, but continuing anyway", originalName);
            }
            
            // Register with health checker for runtime monitoring
            String modName = extractModName(sourceJar);
            ModHealthChecker.registerTransformedMod(
                modId != null ? modId : baseName,
                modName != null ? modName : originalName,
                outputJar,
                sourceJar,
                outputDir.getParent() // game dir
            );
            
            // Log success message for server-only mods
            if (ModEnvironmentDetector.isServerOnly(sourceJar)) {
                LOGGER.info("═══════════════════════════════════════════════════════════");
                LOGGER.info("  {} is SERVER-ONLY", originalName);
                LOGGER.info("  Clients can join WITHOUT having RetroMod installed!");
                LOGGER.info("═══════════════════════════════════════════════════════════");
            }
            
            return outputJar;
            
        } catch (Exception e) {
            // Handle transformation error
            LOGGER.error("Failed to transform mod: {}", originalName);
            
            // Report error with GitHub bug report option
            TransformationErrorHandler.handleError(
                sourceJar, e, modId, "fabric", sourceVersion
            );
            
            // Log bug report to console (for servers)
            if (EnvironmentDetector.isDedicatedServer()) {
                TransformationErrorHandler.logBugReportToConsole(
                    new TransformationErrorHandler.FailedMod(
                        originalName, modId, "fabric", sourceVersion,
                        e.getClass().getSimpleName(),
                        e.getMessage() != null ? e.getMessage() : "Unknown error",
                        ""
                    )
                );
            }
            
            throw new IOException("Transformation failed for " + originalName + ": " + e.getMessage(), e);
            
        } finally {
            // Cleanup temp directory
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Extract mod ID from fabric.mod.json.
     */
    private String extractModId(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(readAndClose(jar.getInputStream(entry)));
                Pattern pattern = PAT_MOD_ID;
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract mod ID");
        }
        return null;
    }
    
    /**
     * Extract Minecraft version requirement from fabric.mod.json.
     */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(readAndClose(jar.getInputStream(entry)));
                Pattern pattern = PAT_MC_VERSION;
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract MC version");
        }
        return null;
    }
    
    /**
     * Extract mod name from fabric.mod.json.
     */
    private String extractModName(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(readAndClose(jar.getInputStream(entry)));
                Pattern pattern = PAT_MOD_NAME;
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract mod name");
        }
        return null;
    }
    
    /**
     * Check if a mod is for the native/current Minecraft version.
     * 
     * IMPORTANT: We are VERY STRICT here. Only pass through if:
     * - Mod explicitly lists EXACT target version (e.g., "1.21.11")
     * - Mod has a range that STARTS at target version (e.g., ">=1.21.11")
     * 
     * We do NOT pass through:
     * - ">=1.20.4" even though 1.21.11 >= 1.20.4 (mods don't work across major versions)
     * - "1.21.x" unless it explicitly matches
     * - Anything uncertain
     * 
     * When in doubt, TRANSFORM. A transformed native mod still works.
     * A non-transformed old mod CRASHES.
     */
    private boolean isNativeVersionMod(String modMcVersion) {
        if (modMcVersion == null) return false;
        
        // Clean up version string
        String cleanVersion = modMcVersion
            .replace(">=", "")
            .replace("<=", "")
            .replace(">", "")
            .replace("<", "")
            .replace("~", "")
            .replace("^", "")
            .trim();
        
        // STRICT CHECK 1: Exact match only
        if (cleanVersion.equals(targetMcVersion)) {
            LOGGER.debug("Native: exact match {}", modMcVersion);
            return true;
        }
        
        // STRICT CHECK 2: Range starting at target version
        // e.g., ">=1.21.11" when target is "1.21.11"
        if (modMcVersion.startsWith(">=")) {
            String minVersion = modMcVersion.substring(2).trim();
            if (minVersion.equals(targetMcVersion)) {
                LOGGER.debug("Native: range starts at target {}", modMcVersion);
                return true;
            }
            // DO NOT pass through ">=1.20.4" - that's an OLD mod!
            // The >= only means "works on 1.20.4 or later OF THAT MAJOR VERSION"
            // It does NOT mean it works on 1.21.x
        }
        
        // STRICT CHECK 3: Wildcard that matches target's major.minor
        // e.g., "1.21.x" matches "1.21.11" but "1.20.x" does NOT
        if (cleanVersion.endsWith(".x") || cleanVersion.endsWith(".*")) {
            String base = cleanVersion.substring(0, cleanVersion.length() - 2);
            // Must match the start AND be the same major.minor
            // "1.21.x" matches "1.21.11" ✓
            // "1.20.x" does NOT match "1.21.11" ✗
            if (targetMcVersion.startsWith(base + ".")) {
                LOGGER.debug("Native: wildcard match {}", modMcVersion);
                return true;
            }
        }
        
        // STRICT CHECK 4: Version range that includes target version
        // e.g., ">=1.21.0 <1.22" when target is "1.21.11"
        // Parse ranges like ">=1.21.0 <1.22" or ">=1.21 <=1.21.99"
        if (modMcVersion.contains(">=") && modMcVersion.contains("<")) {
            try {
                String[] parts = modMcVersion.split("\\s+");
                String minStr = null, maxStr = null;
                boolean maxInclusive = false;
                for (String part : parts) {
                    if (part.startsWith(">=")) minStr = part.substring(2).trim();
                    else if (part.startsWith("<=")) { maxStr = part.substring(2).trim(); maxInclusive = true; }
                    else if (part.startsWith("<")) maxStr = part.substring(1).trim();
                }
                if (minStr != null && maxStr != null) {
                    // Pad versions to compare: "1.21" -> "1.21.0", "1.22" -> "1.22.0"
                    if (compareVersions(targetMcVersion, minStr) >= 0 &&
                        (maxInclusive ? compareVersions(targetMcVersion, maxStr) <= 0
                                      : compareVersions(targetMcVersion, maxStr) < 0)) {
                        LOGGER.debug("Native: range match {} contains {}", modMcVersion, targetMcVersion);
                        return true;
                    }
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }

        // DEFAULT: NOT native - transform it!
        // It's safer to transform a native mod (still works) than to
        // skip transforming an old mod (crashes)
        return false;
    }

    /**
     * Compare two version strings numerically.
     * Returns negative if a < b, 0 if equal, positive if a > b.
     */
    /**
     * Simple version comparison (returns positive if v1 > v2, 0 if equal, negative if v1 < v2)
     */
    private int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            
            int maxLen = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLen; i++) {
                int p1 = i < parts1.length ? Integer.parseInt(parts1[i].replaceAll("[^0-9]", "")) : 0;
                int p2 = i < parts2.length ? Integer.parseInt(parts2[i].replaceAll("[^0-9]", "")) : 0;
                if (p1 != p2) return p1 - p2;
            }
            return 0;
        } catch (Exception e) {
            // When in doubt, treat versions as different so mod gets transformed
            return -1;
        }
    }

    /**
     * Check if a mod uses Mixins.
     */
    private boolean checkForMixins(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Check fabric.mod.json for mixins array
            ZipEntry fabricJson = jar.getEntry("fabric.mod.json");
            if (fabricJson != null) {
                String content;
                try (InputStream is = jar.getInputStream(fabricJson)) {
                    content = new String(is.readAllBytes());
                }
                if (content.contains("\"mixins\"") && !content.contains("\"mixins\": []")) {
                    return true;
                }
            }
            
            // Check for common mixin config files
            if (jar.getEntry("mixins.json") != null) return true;
            if (jar.getEntry("modid.mixins.json") != null) return true;
            
            // Check for any .mixins.json file
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".mixins.json")) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check for mixins: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Extract JAR to directory.
     */
    private void extractJar(Path jarPath, Path outputDir) throws IOException {
        Path normalizedOutputDir = outputDir.normalize();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path outputPath = normalizedOutputDir.resolve(entry.getName()).normalize();

                // Zip Slip protection: ensure resolved path stays within output directory
                if (!outputPath.startsWith(normalizedOutputDir)) {
                    LOGGER.warn("Zip Slip detected, skipping entry: {}", entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
    
    /**
     * Transform all class files in directory.
     * Handles both regular classes and Mixin classes specially.
     */
    private int transformClasses(Path dir) throws IOException {
        int count = 0;
        
        // Get Mixin transformer for special handling
        com.retromod.mixin.MixinCompatibilityTransformer mixinTransformer = null;
        try {
            mixinTransformer = new com.retromod.mixin.MixinCompatibilityTransformer(bytecodeTransformer);
        } catch (Exception e) {
            LOGGER.debug("Mixin transformer not available");
        }
        
        // Find all Mixin classes (classes that might have @Mixin annotations)
        Set<String> mixinClasses = findMixinClasses(dir);
        
        try (var stream = Files.walk(dir)) {
            var classFiles = stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("META-INF"))
                .toList();
            
            for (Path classFile : classFiles) {
                try {
                    byte[] original = Files.readAllBytes(classFile);
                    String className = dir.relativize(classFile).toString()
                        .replace(".class", "")
                        .replace(File.separator, "/");
                    
                    byte[] transformed;
                    
                    // Check if this is a Mixin class - needs special handling
                    if (mixinTransformer != null && mixinClasses.contains(className)) {
                        // Use Mixin-specific transformation
                        transformed = mixinTransformer.transformMixinClass(original);
                        if (transformed != original) {
                            LOGGER.debug("Transformed Mixin class: {}", className);
                        }
                    } else {
                        // Regular transformation
                        transformed = bytecodeTransformer.transformClass(original, className);
                    }
                    
                    if (transformed != null && transformed != original) {
                        Files.write(classFile, transformed);
                        count++;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not transform class: {}", classFile.getFileName());
                }
            }
        }
        
        return count;
    }
    
    /**
     * Find all classes that are Mixins (declared in mixin config files).
     */
    private Set<String> findMixinClasses(Path dir) {
        Set<String> mixinClasses = new HashSet<>();
        
        try {
            // Look for mixin config files — must match all patterns that isMixinConfigFile() matches
            try (var stream = Files.walk(dir)) {
                stream.filter(p -> {
                        String name = dir.relativize(p).toString().replace(File.separator, "/");
                        return Files.isRegularFile(p) && isMixinConfigFile(name);
                    })
                    .forEach(mixinConfig -> {
                        try {
                            String content = Files.readString(mixinConfig);
                            // Extract package
                            Pattern pkgPattern = PAT_PACKAGE;
                            Matcher pkgMatcher = pkgPattern.matcher(content);
                            String pkg = pkgMatcher.find() ? pkgMatcher.group(1).replace('.', '/') + "/" : "";
                            
                            // Extract mixins array
                            Pattern mixinPattern = PAT_MIXINS_ARRAY;
                            Matcher mixinMatcher = mixinPattern.matcher(content);
                            if (mixinMatcher.find()) {
                                String mixinsStr = mixinMatcher.group(1);
                                Pattern classPattern = PAT_QUOTED_STRING;
                                Matcher classMatcher = classPattern.matcher(mixinsStr);
                                while (classMatcher.find()) {
                                    mixinClasses.add(pkg + classMatcher.group(1).replace('.', '/'));
                                }
                            }
                            
                            // Also check client/server arrays
                            for (Pattern arrayPattern : new Pattern[]{PAT_CLIENT_ARRAY, PAT_SERVER_ARRAY}) {
                                Matcher arrayMatcher = arrayPattern.matcher(content);
                                if (arrayMatcher.find()) {
                                    String arrayStr = arrayMatcher.group(1);
                                    Pattern classPattern2 = PAT_QUOTED_STRING;
                                    Matcher classMatcher2 = classPattern2.matcher(arrayStr);
                                    while (classMatcher2.find()) {
                                        mixinClasses.add(pkg + classMatcher2.group(1).replace('.', '/'));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Could not parse mixin config: {}", mixinConfig.getFileName());
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.debug("Could not scan for mixin configs");
        }
        
        if (!mixinClasses.isEmpty()) {
            LOGGER.debug("Found {} Mixin classes to handle specially", mixinClasses.size());
        }
        
        return mixinClasses;
    }
    
    /**
     * Update fabric.mod.json to support target Minecraft version.
     */
    private void updateFabricModJson(Path dir) throws IOException {
        Path modJson = dir.resolve("fabric.mod.json");
        
        if (!Files.exists(modJson)) {
            LOGGER.warn("No fabric.mod.json found - skipping metadata update");
            return;
        }
        
        String content = Files.readString(modJson);
        String originalMcVersion = extractVersionFromContent(content);
        String updated = updateVersionRequirements(content, originalMcVersion);
        
        Files.writeString(modJson, updated);
        LOGGER.info("Updated fabric.mod.json: {} → {}", originalMcVersion, targetMcVersion);
    }
    
    /**
     * Extract minecraft version from fabric.mod.json content.
     */
    private String extractVersionFromContent(String json) {
        Pattern p = PAT_MC_VERSION;
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "unknown";
    }
    
    /**
     * Update version requirements in fabric.mod.json.
     *
     * CRITICAL: This must set the EXACT target version or Fabric will reject the mod!
     *
     * In addition to relaxing minecraft/fabricloader/fabric-api versions, this also
     * relaxes version constraints for third-party APIs that RetroMod has shims for.
     * Without this, Fabric Loader would block the mod at startup even though RetroMod
     * has already transformed the bytecode to work with the newer API versions.
     *
     * Example: A mod declares "cloth-config2": ">=6.0.0 <7.0.0" but the installed
     * Cloth Config is v11.x. Without relaxation, Fabric blocks the mod immediately.
     * With relaxation, the mod loads and RetroMod's bytecode transforms handle the
     * API differences at runtime.
     */
    private String updateVersionRequirements(String json, String originalVersion) {
        String updated = json;

        // =====================================================================
        // 1. Fix "minecraft" dependency (string or array format)
        // =====================================================================
        // Array: "minecraft": ["1.20.5", "1.20.6"] → "minecraft": "1.21.11"
        updated = replaceDepValue(updated, "minecraft", "\"" + targetMcVersion + "\"");

        // =====================================================================
        // 2. Relax loader/API dependencies
        // =====================================================================
        updated = replaceDepValue(updated, "fabricloader", "\">=0.14.0\"");
        updated = replaceDepValue(updated, "fabric-api", "\"*\"");
        updated = replaceDepValue(updated, "fabric", "\"*\"");

        // =====================================================================
        // 3. Relax ALL cross-mod dependencies in "depends" to "*"
        //    Mods like Iris require specific Sodium versions — RetroMod can't
        //    guarantee those versions, and the mod may still partially work.
        //    Array format like ["0.5.9", "0.5.11"] must also be handled.
        // =====================================================================
        updated = relaxAllCrossModDeps(updated);

        // =====================================================================
        // 4. Remove "breaks" section entirely
        //    Mods declare "breaks" to prevent loading with specific other mods/
        //    versions. After transformation the breakage reason is usually gone
        //    (e.g., Voice Chat breaks fabric-api for networking reasons on 1.21.1
        //    but RetroMod patches the networking code).
        // =====================================================================
        updated = removeBreaksSection(updated);

        // Also remove "conflicts" section — same logic as "breaks"
        updated = removeJsonSection(updated, "conflicts");

        // =====================================================================
        // 5. Relax shimmed API dependencies
        // =====================================================================
        updated = relaxShimmedApiDependencies(updated);
        updated = moveBlockingDepsToSuggests(updated);

        // =====================================================================
        // 6. Add RetroMod transformation marker
        // =====================================================================
        if (!updated.contains("\"retromod_transformed\"")) {
            updated = updated.replaceFirst(
                "\\}\\s*$",
                ",\n  \"custom\": {\n    \"retromod_transformed\": true,\n    \"original_mc_version\": \"" + originalVersion + "\",\n    \"target_mc_version\": \"" + targetMcVersion + "\"\n  }\n}"
            );
        }

        return updated;
    }

    /**
     * Replace a dependency value ONLY inside "depends" block, handling both string and array formats:
     *   "key": "value"       → "key": newValue
     *   "key": ["v1", "v2"]  → "key": newValue
     *
     * Scoped to "depends" block to avoid corrupting other JSON sections (custom, contact, etc.).
     */
    private String replaceDepValue(String json, String key, String newValue) {
        // Find the "depends" block using balanced-brace matching
        String dependsContent = extractJsonBlock(json, "depends");
        if (dependsContent == null) {
            return json; // No depends block
        }

        int blockStart = json.indexOf(dependsContent);
        if (blockStart < 0) return json;

        String updatedDepends = dependsContent;
        String quotedKey = Pattern.quote(key);

        // Array format first: "key": [...]
        Pattern arrayPattern = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
            "dep-arr:" + key,
            k -> Pattern.compile("(\"" + quotedKey + "\"\\s*:\\s*)\\[[^\\]]*\\]", Pattern.MULTILINE)
        );
        updatedDepends = arrayPattern.matcher(updatedDepends).replaceAll("$1" + Matcher.quoteReplacement(newValue));

        // String format: "key": "..."
        Pattern stringPattern = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
            "dep-str:" + key,
            k -> Pattern.compile("(\"" + quotedKey + "\"\\s*:\\s*)\"[^\"]*\"", Pattern.MULTILINE)
        );
        updatedDepends = stringPattern.matcher(updatedDepends).replaceAll("$1" + Matcher.quoteReplacement(newValue));

        if (!updatedDepends.equals(dependsContent)) {
            return json.substring(0, blockStart) + updatedDepends + json.substring(blockStart + dependsContent.length());
        }
        return json;
    }

    /**
     * Extract the content of a JSON block by key, handling nested braces correctly.
     * Returns the content between the outermost { and }, or null if not found.
     *
     * Example: for key "depends" in {"depends": {"a": "1", "b": {"v": "2"}}},
     * returns {"a": "1", "b": {"v": "2"}}  (the full nested content).
     */
    private static String extractJsonBlock(String json, String key) {
        // Find "key" :
        Pattern keyPattern = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
            "block:" + key,
            k -> Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{")
        );
        Matcher keyMatcher = keyPattern.matcher(json);
        if (!keyMatcher.find()) return null;

        int braceStart = keyMatcher.end() - 1; // Position of opening {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Return content between { and } (exclusive)
                    return json.substring(braceStart + 1, i);
                }
            }
        }
        return null; // Unbalanced braces
    }

    /**
     * Relax all cross-mod dependencies in the "depends" block to "*".
     *
     * Keeps essential deps (minecraft, fabricloader, java, fabric-api, fabric)
     * but relaxes everything else (sodium, cloth-config, etc.) since RetroMod
     * can't guarantee specific versions of other mods are installed.
     *
     * Handles both string and array value formats.
     */
    private String relaxAllCrossModDeps(String json) {
        // Deps to keep strict (already handled above or essential)
        Set<String> keepStrict = Set.of(
            "minecraft", "fabricloader", "java", "fabric-api", "fabric",
            "fabric-language-kotlin", "mixinextras"
        );

        // IMPORTANT: Only modify keys INSIDE the "depends" block, not the whole JSON!
        // Extract the depends block, find its keys, then do targeted replacements.

        // Find the "depends" block using balanced-brace matching (handles nested objects)
        String dependsContent = extractJsonBlock(json, "depends");
        if (dependsContent == null) {
            return json; // No depends block
        }

        // Find all keys inside the depends block
        Matcher keyMatcher = PAT_DEPENDS_KEY.matcher(dependsContent);
        Set<String> keysToRelax = new LinkedHashSet<>();

        while (keyMatcher.find()) {
            String depKey = keyMatcher.group(1);
            if (!keepStrict.contains(depKey)) {
                keysToRelax.add(depKey);
            }
        }

        // Now replace ONLY within the depends block
        String updatedDepends = dependsContent;
        for (String depKey : keysToRelax) {
            // Replace array format: "key": [...] -> "key": "*"
            Pattern arrPat = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
                "dep-arr:" + depKey,
                k -> Pattern.compile("(\"" + Pattern.quote(depKey) + "\"\\s*:\\s*)\\[[^\\]]*\\]", Pattern.MULTILINE)
            );
            updatedDepends = arrPat.matcher(updatedDepends).replaceAll("$1\"*\"");

            // Replace string format: "key": "..." -> "key": "*"
            Pattern strPat = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
                "dep-str:" + depKey,
                k -> Pattern.compile("(\"" + Pattern.quote(depKey) + "\"\\s*:\\s*)\"[^\"]*\"", Pattern.MULTILINE)
            );
            updatedDepends = strPat.matcher(updatedDepends).replaceAll("$1\"*\"");

            LOGGER.debug("  Relaxed cross-mod dependency: {} → \"*\"", depKey);
        }

        // Replace the depends block content in the original JSON
        if (!updatedDepends.equals(dependsContent)) {
            int contentStart = json.indexOf(dependsContent);
            if (contentStart >= 0) {
                return json.substring(0, contentStart)
                     + updatedDepends
                     + json.substring(contentStart + dependsContent.length());
            }
        }

        return json;
    }

    /**
     * Remove the "breaks" section from fabric.mod.json entirely.
     *
     * The "breaks" field tells Fabric Loader to refuse to load if a listed mod
     * is present. After RetroMod transformation, these breakage declarations are
     * usually no longer valid (the incompatibility was version-specific).
     *
     * Example: Voice Chat 1.21.1 declares breaks: {"fabric-api": "*"} because
     * it has its own networking. After transformation this blocks loading entirely.
     */
    private String removeBreaksSection(String json) {
        // Find the "breaks" key and its full block using balanced-brace matching
        Pattern keyPattern = PAT_BREAKS;
        Matcher keyMatcher = keyPattern.matcher(json);
        if (!keyMatcher.find()) return json;

        // Find the matching closing brace
        int braceStart = keyMatcher.end() - 1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int braceEnd = -1;

        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { braceEnd = i + 1; break; }
            }
        }

        if (braceEnd < 0) return json; // Unbalanced braces

        // Find the full range to remove: including the "breaks" key, leading/trailing comma
        int removeStart = keyMatcher.start();
        int removeEnd = braceEnd;

        // Consume leading comma + whitespace
        int searchBack = removeStart - 1;
        while (searchBack >= 0 && Character.isWhitespace(json.charAt(searchBack))) searchBack--;
        if (searchBack >= 0 && json.charAt(searchBack) == ',') {
            removeStart = searchBack;
        } else {
            // No leading comma — consume trailing comma instead
            int searchForward = removeEnd;
            while (searchForward < json.length() && Character.isWhitespace(json.charAt(searchForward))) searchForward++;
            if (searchForward < json.length() && json.charAt(searchForward) == ',') {
                removeEnd = searchForward + 1;
            }
        }

        String updated = json.substring(0, removeStart) + json.substring(removeEnd);
        LOGGER.info("  Removed 'breaks' section from fabric.mod.json (no longer valid after transformation)");
        return updated;
    }

    /**
     * Remove an arbitrary JSON object section by key name using balanced-brace matching.
     * Handles leading/trailing comma cleanup. Returns json unchanged if key not found.
     */
    private String removeJsonSection(String json, String sectionKey) {
        Pattern keyPattern = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
            "block:" + sectionKey,
            k -> Pattern.compile("\"" + Pattern.quote(sectionKey) + "\"\\s*:\\s*\\{")
        );
        Matcher keyMatcher = keyPattern.matcher(json);
        if (!keyMatcher.find()) return json;

        int braceStart = keyMatcher.end() - 1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int braceEnd = -1;

        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { braceEnd = i + 1; break; }
            }
        }
        if (braceEnd < 0) return json;

        int removeStart = keyMatcher.start();
        int removeEnd = braceEnd;

        // Consume leading comma + whitespace
        int searchBack = removeStart - 1;
        while (searchBack >= 0 && Character.isWhitespace(json.charAt(searchBack))) searchBack--;
        if (searchBack >= 0 && json.charAt(searchBack) == ',') {
            removeStart = searchBack;
        } else {
            int searchForward = removeEnd;
            while (searchForward < json.length() && Character.isWhitespace(json.charAt(searchForward))) searchForward++;
            if (searchForward < json.length() && json.charAt(searchForward) == ',') {
                removeEnd = searchForward + 1;
            }
        }

        LOGGER.info("  Removed '{}' section from fabric.mod.json", sectionKey);
        return json.substring(0, removeStart) + json.substring(removeEnd);
    }

    /**
     * Relax version constraints for third-party APIs that RetroMod has shims for.
     *
     * Scans the JSON for any dependency key that matches a known shimmed API mod ID
     * and replaces its version constraint with "*" (any version).
     *
     * This handles cases like:
     *   "cloth-config2": ">=6.0.0 <7.0.0"  →  "cloth-config2": "*"
     *   "rei": ">=8.0.0"                   →  "rei": "*"
     *   "trinkets": ">=3.4.0 <3.5.0"       →  "trinkets": "*"
     */
    private String relaxShimmedApiDependencies(String json) {
        // Only modify inside the "depends" block
        String dependsContent = extractJsonBlock(json, "depends");
        if (dependsContent == null) return json;

        String updatedDepends = dependsContent;
        int relaxedCount = 0;

        for (String modId : SHIMMED_API_MOD_IDS) {
            Pattern apiPattern = DYNAMIC_PATTERN_CACHE.computeIfAbsent(
                "api:" + modId,
                k -> Pattern.compile("(\"" + Pattern.quote(modId) + "\"\\s*:\\s*)\"[^\"]+\"", Pattern.MULTILINE)
            );

            Matcher matcher = apiPattern.matcher(updatedDepends);
            if (matcher.find()) {
                String relaxed = matcher.replaceAll("$1\"*\"");
                if (!relaxed.equals(updatedDepends)) {
                    LOGGER.info("  Relaxed API dependency: {} → \"*\" (RetroMod has shims)", modId);
                    updatedDepends = relaxed;
                    relaxedCount++;
                }
            }
        }

        if (relaxedCount > 0) {
            LOGGER.info("Relaxed {} third-party API version constraint(s)", relaxedCount);
            int contentStart = json.indexOf(dependsContent);
            if (contentStart >= 0) {
                return json.substring(0, contentStart)
                     + updatedDepends
                     + json.substring(contentStart + dependsContent.length());
            }
        }

        return json;
    }

    /**
     * Move any remaining strict API dependencies from "depends" to "suggests".
     *
     * Some mods use dependency IDs we don't have in our known list (e.g., sub-modules
     * of Cardinal Components like "cardinal-components-item"). This method catches
     * any non-standard dependency that might still block loading by looking for
     * patterns that suggest an API version constraint (ranges with upper bounds).
     *
     * It converts entries like:
     *   "depends": { "some-api": ">=1.0.0 <2.0.0" }
     * to:
     *   "depends": { "some-api": "*" }
     *
     * This is safe because RetroMod's bytecode transformation handles API changes,
     * and we've already pinned minecraft/fabricloader/fabric-api correctly.
     */
    private String moveBlockingDepsToSuggests(String json) {
        // Only modify inside the "depends" block
        String dependsContent = extractJsonBlock(json, "depends");
        if (dependsContent == null) return json;

        Set<String> keepStrict = Set.of(
            "minecraft", "fabricloader", "fabric-api", "fabric", "java"
        );

        // Match "modid": "version" pairs with upper-bound constraints (contains '<')
        Pattern depPattern = Pattern.compile(
            "\"([a-z0-9_-]+)\"\\s*:\\s*\"([^\"]*<[^\"]+)\"",
            Pattern.MULTILINE
        );

        Matcher matcher = depPattern.matcher(dependsContent);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String depId = matcher.group(1);
            String constraint = matcher.group(2);

            if (!keepStrict.contains(depId) && !SHIMMED_API_MOD_IDS.contains(depId)) {
                LOGGER.info("  Relaxed unknown API dependency: {} (was: {})", depId, constraint);
                matcher.appendReplacement(sb, "\"" + depId + "\": \"*\"");
            }
        }
        matcher.appendTail(sb);

        String updatedDepends = sb.toString();
        if (!updatedDepends.equals(dependsContent)) {
            int contentStart = json.indexOf(dependsContent);
            if (contentStart >= 0) {
                return json.substring(0, contentStart)
                     + updatedDepends
                     + json.substring(contentStart + dependsContent.length());
            }
        }

        return json;
    }
    
    /**
     * Repackage directory contents as JAR.
     */
    private void repackageJar(Path sourceDir, Path outputJar, Path originalJar) throws IOException {
        // Copy manifest from original if exists
        Manifest manifest = null;
        try (JarFile original = new JarFile(originalJar.toFile())) {
            manifest = original.getManifest();
        } catch (Exception e) {
            // Use default manifest
        }
        
        if (manifest == null) {
            manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }
        
        // Add RetroMod info to manifest
        manifest.getMainAttributes().putValue("RetroMod-Transformed", "true");
        manifest.getMainAttributes().putValue("RetroMod-Target-Version", targetMcVersion);
        manifest.getMainAttributes().putValue("RetroMod-MixinProcessed", "true");
        
        // Build class data lookup ONCE for all mixin config processing
        Map<String, byte[]> classLookup = buildClassLookup(sourceDir);
        MixinCompatibilityTransformer mixinTransformer =
            new MixinCompatibilityTransformer(RetroModTransformer.getInstance());

        // First pass: process all mixin configs and collect modified class data
        // transformMixinConfig() may partial-strip classes, storing modified bytes in classLookup
        try (var configStream = Files.walk(sourceDir)) {
            for (Path file : configStream.filter(Files::isRegularFile).toList()) {
                String entryName = sourceDir.relativize(file).toString()
                    .replace(File.separator, "/");
                if (isMixinConfigFile(entryName)) {
                    try {
                        String json = Files.readString(file);
                        mixinTransformer.transformMixinConfig(json, classLookup);
                    } catch (Exception e) {
                        LOGGER.warn("Pre-pass mixin config processing failed for {}: {}", entryName, e.getMessage());
                    }
                }
            }
        }

        // Second pass: write the final JAR with all modifications applied
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(outputJar.toFile()), manifest)) {

            try (var stream = Files.walk(sourceDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");

                    // Skip manifest (we added our own)
                    if (entryName.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                        continue;
                    }

                    // Strip jar signing files — we modify contents so signatures are invalid
                    // Covers: .SF, .RSA, .DSA, .EC, and SIG-* files per JAR spec
                    String metaFileName = entryName.contains("/") ?
                        entryName.substring(entryName.lastIndexOf('/') + 1) : entryName;
                    if (entryName.startsWith("META-INF/") && (
                            entryName.endsWith(".SF") || entryName.endsWith(".RSA") ||
                            entryName.endsWith(".DSA") || entryName.endsWith(".EC") ||
                            metaFileName.startsWith("SIG-"))) {
                        LOGGER.debug("Stripping signing file: {}", entryName);
                        continue;
                    }

                    JarEntry entry = new JarEntry(entryName);
                    jos.putNextEntry(entry);

                    if (isMixinConfigFile(entryName)) {
                        // Write mixin config with safety modifications
                        try {
                            String json = Files.readString(file);
                            String transformed = mixinTransformer.transformMixinConfig(json, classLookup);
                            // Set "required": false so broken mixin configs don't crash the game
                            transformed = transformed.replaceAll(
                                "\"required\"\\s*:\\s*true",
                                "\"required\": false"
                            );
                            // Ensure "defaultRequire": 0 in injectors section
                            if (transformed.contains("\"defaultRequire\"")) {
                                transformed = transformed.replaceAll(
                                    "\"defaultRequire\"\\s*:\\s*[1-9]\\d*",
                                    "\"defaultRequire\": 0"
                                );
                            } else if (transformed.contains("\"injectors\"")) {
                                transformed = transformed.replaceFirst(
                                    "(\"injectors\"\\s*:\\s*\\{)",
                                    "$1\n    \"defaultRequire\": 0,"
                                );
                            } else {
                                int lastBrace = transformed.lastIndexOf('}');
                                if (lastBrace > 0) {
                                    transformed = transformed.substring(0, lastBrace)
                                        + ",\n  \"injectors\": {\n    \"defaultRequire\": 0\n  }\n}";
                                }
                            }
                            jos.write(transformed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            LOGGER.warn("Failed to process mixin config {}: {}", entryName, e.getMessage());
                            Files.copy(file, jos);
                        }
                    } else if (entryName.endsWith(".class") && classLookup.containsKey(entryName)) {
                        // Write class data from lookup — may have been modified by partial stripping
                        jos.write(classLookup.get(entryName));
                    } else if (entryName.startsWith("META-INF/jars/") && entryName.endsWith(".jar")) {
                        // Patch nested Jar-in-Jar dependencies
                        try {
                            byte[] patchedJar = patchNestedJar(Files.readAllBytes(file));
                            jos.write(patchedJar);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to patch nested jar {}: {}", entryName, e.getMessage());
                            Files.copy(file, jos);
                        }
                    } else {
                        Files.copy(file, jos);
                    }

                    jos.closeEntry();
                }
            }
        }
    }

    /**
     * Patch a nested Jar-in-Jar: update its fabric.mod.json to accept the target MC version.
     * Returns the patched jar bytes.
     */

    /**
     * Check if a file is a mixin config JSON based on naming conventions.
     *
     * Mixin config files use various naming patterns:
     *   - modid.mixins.json           (most common)
     *   - mixins.modid.json           (Iris, some others)
     *   - modid-common.mixins.json    (multi-platform mods)
     *   - modid_mixin.json            (some older mods)
     *   - modid.mixin.json            (alternate convention)
     *
     * Also checks if a file is referenced in fabric.mod.json "mixins" array.
     * We use a broad pattern to catch ALL variants.
     */
    /** Read all bytes from an InputStream and close it. Prevents resource leaks. */
    private static byte[] readAndClose(InputStream is) throws IOException {
        try (is) {
            return is.readAllBytes();
        }
    }

    private boolean isMixinConfigFile(String filename) {
        // Only JSON files
        if (!filename.endsWith(".json")) return false;
        // Skip META-INF paths (except jars handled elsewhere) and deep nesting,
        // but ALLOW mixin configs in subdirectories like "mixins/common/*.mixins.json"
        if (filename.startsWith("META-INF/") && !filename.startsWith("META-INF/jars/")) return false;

        String lower = filename.toLowerCase();

        // Pattern 1: *.mixins.json (e.g., "dynamic_fps.mixins.json")
        if (lower.endsWith(".mixins.json")) return true;

        // Pattern 2: *mixin.json or *mixin*.json (e.g., "modid_mixin.json", "modid.mixin.json")
        if (lower.endsWith(".mixin.json") || lower.endsWith("_mixin.json")) return true;

        // Pattern 3: mixins.*.json (e.g., "mixins.iris.json", "mixins.iris.compat.sodium.json")
        if (lower.startsWith("mixins.")) return true;

        // Pattern 4: contains "mixin" in the name (e.g., "iris-batched-entity-rendering.mixins.json")
        if (lower.contains("mixin") && lower.endsWith(".json")) return true;

        return false;
    }

    private byte[] patchNestedJar(byte[] jarBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(jarBytes));
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {

            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] data = zis.readAllBytes();

                // Patch fabric.mod.json inside the nested jar
                if (entry.getName().equals("fabric.mod.json")) {
                    String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    String originalVersion = extractVersionFromContent(json);
                    String patched = updateVersionRequirements(json, originalVersion);
                    data = patched.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    LOGGER.info("Patched nested jar fabric.mod.json: {} → {}", originalVersion, targetMcVersion);
                }

                zos.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                zos.write(data);
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }
    
    /**
     * Build a map of class name -> class bytes for mixin analysis.
     */
    private Map<String, byte[]> buildClassLookup(Path sourceDir) {
        Map<String, byte[]> lookup = new HashMap<>();
        try (var stream = Files.walk(sourceDir)) {
            for (Path file : stream.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = sourceDir.relativize(file).toString().replace(File.separator, "/");
                lookup.put(name, Files.readAllBytes(file));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to build class lookup: {}", e.getMessage());
        }
        return lookup;
    }

    /**
     * Delete directory recursively.
     */
    private void deleteRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.toString().length() - a.toString().length())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {}
                        });
                }
            }
        } catch (IOException ignored) {}
    }
    
    /**
     * Check if a mod JAR has already been transformed by RetroMod.
     */
    public static boolean isAlreadyTransformed(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Check manifest
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String transformed = manifest.getMainAttributes().getValue("RetroMod-Transformed");
                if ("true".equals(transformed)) {
                    return true;
                }
            }
            
            // Check fabric.mod.json
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(readAndClose(jar.getInputStream(entry)));
                return content.contains("\"retromod_transformed\"");
            }
            
        } catch (Exception e) {
            // Assume not transformed
        }
        
        return false;
    }
}
