/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.util.ZipSecurity;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.regex.*;

/**
 * Transforms Forge and NeoForge mods to work on newer Minecraft versions.
 *
 * Same approach as FabricModTransformer but handles mods.toml and
 * neoforge.mods.toml metadata instead of fabric.mod.json.
 *
 * Steps:
 * 1. Extract mod JAR to temp directory
 * 2. Transform all class files (bytecode)
 * 3. Update mods.toml / neoforge.mods.toml version range
 * 4. Repackage as new JAR
 * 5. Copy to mods folder
 */
public class ForgeModTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-ForgeTransform");

    /** Maximum size for a single ZIP entry (50 MB) to prevent zip bomb attacks. */
    private static final long MAX_ENTRY_SIZE = 50 * 1024 * 1024;
    /** Maximum total extracted size (500 MB) to prevent zip bomb attacks. */
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024;

    /**
     * Forge/NeoForge mod IDs for APIs that Retromod provides compatibility shims for.
     * When a mod declares a dependency with a restrictive versionRange in mods.toml,
     * the mod loader will block the mod from loading. Since Retromod transforms the
     * bytecode, we also relax these constraints.
     */
    private static final Set<String> SHIMMED_API_MOD_IDS = Set.of(
        // Tech / content mod APIs
        "mekanism", "mekanismapi",
        "ae2", "appliedenergistics2",
        "botania", "botania_api",
        "create",
        "thermal", "thermal_foundation", "thermal_expansion", "cofh_core",

        // Equipment
        "curios", "curiosapi",
        "baubles",

        // Recipe viewers
        "jei", "just_enough_items",
        "nei",

        // Tooltips / overlays
        "jade", "waila", "wthit",

        // Config libraries
        "cloth_config", "cloth-config",

        // Animation / model
        "geckolib", "geckolib3", "geckolib4",

        // Cross-platform
        "architectury",

        // Guide
        "patchouli",

        // Utility
        "autoreglib"
    );

    private final String targetMcVersion;
    private final RetromodTransformer bytecodeTransformer;
    private final MixinCompatibilityTransformer mixinTransformer;

    public ForgeModTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.bytecodeTransformer = RetromodTransformer.getInstance();
        this.mixinTransformer = new MixinCompatibilityTransformer(bytecodeTransformer);
    }

    /**
     * Transform a Forge or NeoForge mod JAR.
     *
     * @param sourceJar Path to the original mod JAR
     * @param outputDir Directory to write the transformed JAR
     * @return Path to the transformed JAR, or null if failed/skipped
     */
    public Path transformMod(Path sourceJar, Path outputDir) throws IOException {
        String originalName = sourceJar.getFileName().toString();
        String baseName = originalName.replace(".jar", "");
        // NeoForge/Forge derive an automatic JPMS module name from the jar
        // FILENAME when a mod ships no module-info / Automatic-Module-Name
        // (common for MCreator and small mods). Spaces or other odd characters
        // in that name break the derived module's reads, and the transformed
        // mod's module then can't resolve core Minecraft classes - e.g.
        // "ClassNotFoundException: net.minecraft.resources.ResourceLocation" in
        // the mod's own <clinit> (#47, Luminous Nether: its jar name had spaces,
        // while the exact same mod renamed without spaces loaded fine). Sanitize
        // the output name so the derived module name is always valid. This is a
        // NeoForge/Forge concern (Fabric's Knot loader has no JPMS modules), but
        // a clean name is harmless everywhere.
        String safeBaseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        String outputName = safeBaseName + "-retromod.jar";
        Path outputJar = outputDir.resolve(outputName);

        LOGGER.info("Checking Forge/NeoForge mod: {}", originalName);

        // Honor mod-author opt-out (META-INF/retromod-opt-out marker file).
        // See com.retromod.util.OptOutCheck for the convention + override flag.
        if (com.retromod.util.OptOutCheck.isOptedOut(sourceJar)) {
            com.retromod.util.OptOutCheck.logSkipped(sourceJar);
            Path passthrough = outputDir.resolve(originalName);
            Files.copy(sourceJar, passthrough, StandardCopyOption.REPLACE_EXISTING);
            return passthrough;
        }

        // Check if mod is already for native version
        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (targetMcVersion.equals(modMcVersion)) {
            LOGGER.info("  {} is already for MC {} - no transformation needed", originalName, targetMcVersion);
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }

        LOGGER.info("Transforming Forge/NeoForge mod: {} -> {}", originalName, outputName);

        // Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("retromod-forge-");

        try {
            // Step 1: Extract JAR
            extractJar(sourceJar, tempDir);

            // Step 2: Transform bytecode
            int classesTransformed = transformClasses(tempDir);
            LOGGER.info("Transformed {} class files", classesTransformed);

            // Step 2.5: Make mixin configs non-fatal
            makeMixinConfigsNonFatal(tempDir);

            // Step 2.6: Handle access wideners/classtweakers (cross-loader mods)
            stripAccessWideners(tempDir);

            // Step 2.7: On a NeoForge (1.20.2+) host, strip the Mixin
            // synthetic-args dummy package some 1.20.1-era Forge mods ship (#87).
            stripMixinSyntheticPackage(tempDir);

            // Step 2.8: Embed any registered synthetic classes this mod references, under a
            // unique-per-mod Retromod package (split-package-safe), and redirect the mod's
            // references there. No-op until a NeoForge synthetic (e.g. a DeferredSpawnEggItem
            // or FMLJavaModLoadingContext bridge) is both registered AND referenced by this mod.
            int synthetics = SyntheticEmbedder.embed(
                    tempDir, sourceJar.getFileName().toString(), bytecodeTransformer);
            if (synthetics > 0) {
                LOGGER.info("Embedded {} referenced synthetic class(es)", synthetics);
            }

            // Step 3: Update mods.toml / neoforge.mods.toml
            updateModsToml(tempDir, "META-INF/mods.toml");
            updateModsToml(tempDir, "META-INF/neoforge.mods.toml");

            // Step 3.1: On a NeoForge host, promote a legacy META-INF/mods.toml to
            // META-INF/neoforge.mods.toml. Modern NeoForge SKIPS a jar that only has
            // mods.toml ("is for Minecraft Forge or an older version of NeoForge,
            // and cannot be loaded") at scan time, before any bytecode runs - so a
            // 1.20.1 (Neo)Forge mod never loads (issue #42; the real cause of #38).
            promoteToNeoForgeToml(tempDir);

            // Step 3.5: Recursively patch metadata in JIJ (Jar-In-Jar) deps.
            // Mods like Create bundle dependencies (e.g. Flywheel) at
            // META-INF/jarjar/*.jar - those nested JARs have their own
            // mods.toml that needs the same minecraft versionRange update,
            // otherwise Forge sees the JIJ's stale "[1.18.2,1.19)" range
            // and rejects it on 26.1, taking the whole loadout down.
            int jijPatched = patchJarInJarMetadata(tempDir);
            if (jijPatched > 0) {
                LOGGER.info("Patched metadata in {} JIJ dependencies", jijPatched);
            }

            // Step 3.6: Migrate bundled data-pack JSON across the 1.21.x -> 26.x data-only
            // format changes the bytecode pass can't reach (minecraft:chain -> iron_chain,
            // dyed_color object -> int, advancement icon "item" -> "id", custom_model_data
            // int -> object, entity_type tag minecraft:potion -> splash/lingering split).
            // A 1.21.x structure mod's STRUCTURES generate (vanilla worldgen types resolve)
            // but its loot tables / advancements / tags otherwise fail to parse on 26.x.
            // Gated to 26.x targets inside ModDataMigrator. This is the in-place / from-input
            // runtime path too (RetromodNeoForge calls transformMod).
            int dataMigrated = com.retromod.resources.ModDataMigrator.migrateTree(tempDir, targetMcVersion);
            if (dataMigrated > 0) {
                LOGGER.info("Migrated 26.x data formats in {} data file(s)", dataMigrated);
            }

            // Step 4: Repackage
            repackageJar(tempDir, outputJar);

            LOGGER.info("Created transformed mod: {}", outputJar.getFileName());

            // Verify transforms if enabled in config
            if (TransformVerifier.isEnabled()) {
                TransformVerifier.verifyAndReport(outputJar, originalName, targetMcVersion);
            }

            return outputJar;

        } catch (Exception e) {
            LOGGER.error("Failed to transform {}: {}", originalName, e.getMessage());
            return null;
        } finally {
            // Clean up temp directory
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /**
     * Extract minecraft version from a Forge/NeoForge mod JAR.
     */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try neoforge.mods.toml first
            ZipEntry entry = jar.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = jar.getEntry("META-INF/mods.toml");

            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                // Match versionRange = "[1.20.6]" or "[1.20.6,)" etc
                Pattern p = Pattern.compile("versionRange\\s*=\\s*\"\\[([0-9.]+)");
                Matcher m = p.matcher(content);
                // Skip the first match (usually forge/neoforge version), find minecraft
                while (m.find()) {
                    String version = m.group(1);
                    if (version.startsWith("1.") || version.matches("\\d{2,}\\..*")) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Extract JAR to directory with zip-bomb protection based on ACTUAL bytes
     * read, not the (attacker-controlled) declared entry size. See the
     * equivalent method in {@link FabricModTransformer#extractJar} for the
     * full rationale.
     */
    private void extractJar(Path jarPath, Path outputDir) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            long totalSize = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path outputPath = ZipSecurity.safeResolve(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    long writtenBytes;
                    try (InputStream is = jar.getInputStream(entry)) {
                        writtenBytes = copyBounded(is, outputPath, MAX_ENTRY_SIZE, entry.getName());
                    }
                    totalSize += writtenBytes;
                    if (totalSize > MAX_TOTAL_SIZE) {
                        throw new IOException("ZIP total extracted size exceeds limit ("
                            + MAX_TOTAL_SIZE + " bytes) - possible zip bomb (decompressed "
                            + totalSize + " bytes so far)");
                    }
                }
            }
        }
    }

    /** @see FabricModTransformer#copyBounded */
    private static long copyBounded(InputStream is, Path target, long maxBytes,
                                     String entryNameForError) throws IOException {
        long written = 0;
        byte[] buf = new byte[8192];
        try (var out = java.nio.file.Files.newOutputStream(target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
            int n;
            while ((n = is.read(buf)) > 0) {
                written += n;
                if (written > maxBytes) {
                    throw new IOException("ZIP entry too large: " + entryNameForError
                        + " (exceeded " + maxBytes + " bytes while reading, "
                        + "possible zip bomb - declared size in central directory "
                        + "may be falsified)");
                }
                out.write(buf, 0, n);
            }
        }
        return written;
    }

    private int transformClasses(Path dir) throws IOException {
        final java.util.List<Path> classFiles;
        try (var stream = Files.walk(dir)) {
            classFiles = stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("META-INF"))
                .toList();
        }

        // Parallel per-class transformation - same rationale as
        // FabricModTransformer.transformClasses. Classes transform
        // independently; the bytecodeTransformer's redirect tables are
        // thread-safe (ConcurrentHashMap reads). See RetromodExecutors
        // for pool-sizing rationale.
        final java.util.concurrent.atomic.AtomicInteger counter =
                new java.util.concurrent.atomic.AtomicInteger();

        com.retromod.core.parallel.RetromodExecutors.parallelForEach(classFiles, classFile -> {
            try {
                byte[] original = Files.readAllBytes(classFile);
                String className = dir.relativize(classFile).toString()
                    .replace(".class", "")
                    .replace(File.separator, "/");

                // Strip blocklisted mixin handlers first (NeoForge/Forge path; the
                // Fabric path does this inside FabricModTransformer). No-op for any
                // class that isn't a blocklisted mixin. Fixes #48 (Darker Depths'
                // addAdditionalSaveData/readAdditionalSaveData @Injects that crash
                // on the CompoundTag→ValueOutput signature change).
                byte[] preStripped = mixinTransformer.stripBlocklistedHandlers(original);
                byte[] transformed = bytecodeTransformer.transformClass(preStripped, className);
                boolean wroteFirst = false;
                if (transformed != null && transformed != original) {
                    Files.write(classFile, transformed);
                    counter.incrementAndGet();
                    wroteFirst = true;
                }

                // Post-processing: inject missing abstract methods for Button subclasses.
                byte[] current = (transformed != null && transformed != original) ? transformed : original;
                byte[] patched = injectMissingAbstractMethods(current, className);
                if (patched != null && patched != current) {
                    Files.write(classFile, patched);
                    if (!wroteFirst) counter.incrementAndGet();
                }
            } catch (Exception e) {
                // Include exception details so per-class transform failures are
                // actually diagnosable (previously only the filename was logged).
                LOGGER.warn("Could not transform class: {} ({}: {})",
                        classFile.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            }
        });

        return counter.get();
    }

    /**
     * Walk every {@code META-INF/jarjar/*.jar} entry under {@code dir} and
     * patch the metadata files (mods.toml / neoforge.mods.toml /
     * fabric.mod.json) inside each one in-place.
     *
     * <p>JIJ ("Jar-In-Jar") is Forge's mechanism for bundling dependencies
     * inside a mod. When Retromod transforms a mod like Create, it updates
     * Create's outer mods.toml correctly - but the bundled Flywheel jar at
     * {@code META-INF/jarjar/flywheel-forge-1.18.2-0.6.11-107.jar} has its
     * own mods.toml declaring "minecraft 1.18.2..1.19", and Forge rejects
     * that on 26.1+.
     *
     * <p>Strategy: extract → patch → repack each JIJ jar in-place. We only
     * touch the metadata files (no bytecode transformation of JIJ contents
     * yet - that's a bigger separate feature). For the metadata-only fix,
     * just rewriting versionRange and dependency mandatory flags is enough
     * to get Forge to accept the JIJ.
     *
     * @return the number of JIJ jars that were successfully patched
     */
    /** Max Jar-in-Jar nesting depth Retromod recurses through (libraries inside libraries). */
    private static final int MAX_JIJ_DEPTH = 4;

    private int patchJarInJarMetadata(Path dir) {
        return patchJarInJarMetadata(dir, 1);
    }

    private int patchJarInJarMetadata(Path dir, int depth) {
        Path jarjarDir = dir.resolve("META-INF/jarjar");
        if (!Files.isDirectory(jarjarDir)) {
            return 0;
        }

        int patched = 0;
        try (var entries = Files.list(jarjarDir)) {
            var jijList = entries.filter(p -> p.toString().endsWith(".jar")).toList();
            for (Path jijJar : jijList) {
                try {
                    if (patchSingleJijJar(jijJar, depth)) {
                        patched++;
                    }
                } catch (Exception e) {
                    // One bad JIJ shouldn't take down the whole transform.
                    // Log and continue - the worst case is the JIJ keeps
                    // its old metadata, which Forge will then reject only
                    // for that one nested mod.
                    LOGGER.warn("Could not patch JIJ {}: {}", jijJar.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not list JIJ directory: {}", e.getMessage());
        }
        return patched;
    }

    /**
     * Transform a single JIJ (Jar-in-Jar) library: extract to a temp dir, rewrite its
     * BYTECODE with the same transformer as the outer mod, patch its metadata
     * ({@link #updateModsToml} + {@link #promoteToNeoForgeToml}), recurse into its own
     * bundled jars, and repack over the original.
     *
     * <p>Previously this patched only metadata; #95 needs the nested bytecode transformed
     * too - a JiJ'd library referencing a relocated MC class (e.g. crackerslib bundled by
     * Cracker's Wither Storm) otherwise loads broken or is reported "missing". A
     * Forge-built nested lib also ships only {@code mods.toml}, which NeoForge skips at scan
     * ("for Minecraft Forge or an older version of NeoForge") before any bytecode runs, so
     * it gets the same {@code mods.toml}->{@code neoforge.mods.toml} promotion as the outer jar.
     *
     * @return true if the jar was rewritten (a class changed, metadata patched, or a
     *         nested jar changed); false for a pure library with nothing to do.
     */
    private boolean patchSingleJijJar(Path jijJar, int depth) throws IOException {
        Path tempDir = Files.createTempDirectory("retromod-jij-");
        try {
            // Extract using the same hardened extractor as the outer mod.
            // ZipSecurity / size limits / path-traversal checks all apply identically.
            extractJar(jijJar, tempDir);

            // (#95) Transform the nested library's bytecode with the outer mod's transformer.
            // Even a "pure library" jar with no mod metadata gets this; transformClasses
            // returns the count of classes actually rewritten.
            int classesTransformed = transformClasses(tempDir);

            boolean hasForgeToml      = Files.exists(tempDir.resolve("META-INF/mods.toml"));
            boolean hasNeoForgeToml   = Files.exists(tempDir.resolve("META-INF/neoforge.mods.toml"));

            if (hasForgeToml) {
                updateModsToml(tempDir, "META-INF/mods.toml");
            }
            if (hasNeoForgeToml) {
                updateModsToml(tempDir, "META-INF/neoforge.mods.toml");
            }
            // (#95/#42) Promote a bundled Forge lib's mods.toml so NeoForge will scan it
            // (self-gated to a NeoForge 1.20.2+ host; no-op otherwise).
            promoteToNeoForgeToml(tempDir);

            // Migrate bundled data-pack JSON inside the JiJ too (a mod can ship data through
            // a JiJ'd library). Counts toward the repackage decision so a JiJ whose ONLY
            // change is migrated data still gets rewritten.
            int dataMigrated = com.retromod.resources.ModDataMigrator.migrateTree(tempDir, targetMcVersion);

            // Recurse: the nested library may bundle its own jars.
            int nestedPatched = (depth < MAX_JIJ_DEPTH) ? patchJarInJarMetadata(tempDir, depth + 1) : 0;

            boolean changed = classesTransformed > 0 || hasForgeToml || hasNeoForgeToml
                    || dataMigrated > 0 || nestedPatched > 0;
            if (!changed) {
                // Pure library with nothing to rewrite (no relocated refs, no metadata).
                return false;
            }

            // Repack over the original JIJ in place; the outer jar picks up the rewritten
            // bytes when it gets repackaged.
            Files.delete(jijJar);
            repackageJar(tempDir, jijJar);
            LOGGER.debug("Transformed JIJ {} ({} class(es) rewritten)", jijJar.getFileName(), classesTransformed);
            return true;

        } finally {
            // Same cleanup pattern as the outer transform.
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /**
     * Update mods.toml or neoforge.mods.toml to target the correct MC version.
     */
    private void updateModsToml(Path dir, String tomlPath) throws IOException {
        Path tomlFile = dir.resolve(tomlPath);
        if (!Files.exists(tomlFile)) return;

        String content = Files.readString(tomlFile);
        String original = content;

        // Update minecraft versionRange
        // Matches: versionRange = "[1.20.6]" or "[1.20.6,)" or "[1.20,1.21)"
        // Only update the minecraft dependency, not the forge/neoforge one
        // Strategy: find [[dependencies.xxx]] blocks and update the minecraft one
        content = updateMinecraftVersionRange(content);

        // Forge 1.16+ requires a top-level `license` field; mods from before it
        // (and some old ones) omit it, so Forge fatally rejects the transformed jar
        // with "Missing License Information in file …" (#62). Add a neutral default
        // when the source has none - Forge only needs a non-empty value.
        content = ensureLicense(content);

        // Add Retromod marker if not present
        if (!content.contains("retromod_transformed")) {
            content = content + "\n# Transformed by Retromod (original version modified)\n";
        }

        if (!content.equals(original)) {
            Files.writeString(tomlFile, content);
            LOGGER.info("Updated {}: minecraft versionRange -> [{}]", tomlPath, targetMcVersion);
        }
    }

    /**
     * On a NeoForge host (1.20.2+), promote a legacy {@code META-INF/mods.toml} to
     * {@code META-INF/neoforge.mods.toml}.
     *
     * <p>NeoForge renamed its metadata file from {@code mods.toml} (the Forge name,
     * also used by NeoForge 1.20.1) to {@code neoforge.mods.toml} in 1.20.2. Modern
     * NeoForge SKIPS a jar that has only {@code mods.toml} at scan time - "File X is
     * for Minecraft Forge or an older version of NeoForge, and cannot be loaded" -
     * <em>before</em> any bytecode transform runs, so a 1.20.1 (Neo)Forge mod never
     * loads at all (issue #42; the real cause behind #38, which was wrongly filed as
     * a shim problem). We copy the (already version-patched) toml to the new name and
     * relax the top-level {@code loaderVersion}.
     *
     * <p>Gated on the host actually being NeoForge, so a LexForge setup - which still
     * uses {@code mods.toml} - is left untouched.
     */
    private void promoteToNeoForgeToml(Path tempDir) throws IOException {
        if (!com.retromod.util.McReflect.isNeoForge()) return;
        // Only NeoForge 1.20.2+ uses neoforge.mods.toml; 1.20.1 still wants mods.toml.
        if (RetromodVersion.mcVersionExceeds("1.20.2", targetMcVersion)) return;

        Path forgeToml = tempDir.resolve("META-INF/mods.toml");
        Path neoToml = tempDir.resolve("META-INF/neoforge.mods.toml");
        if (!Files.exists(forgeToml) || Files.exists(neoToml)) return;

        String content = relaxLoaderVersion(Files.readString(forgeToml));
        content = pointForgeDependencyAtNeoForge(content);
        Files.writeString(neoToml, content);
        Files.delete(forgeToml); // NeoForge keys off the filename; drop the legacy one
        LOGGER.info("Promoted META-INF/mods.toml -> neoforge.mods.toml for NeoForge {} "
                + "(was rejected as a Forge/old-NeoForge mod) (#42)", targetMcVersion);
    }

    /**
     * Strip the Mixin synthetic-args dummy package from the extracted mod (#87).
     *
     * <p>Some 1.20.1-era Forge mods (Blueprint 7.x and mods built from its
     * template) ship a placeholder class at
     * {@code org/spongepowered/asm/synthetic/args/Dummy.class} so their own
     * module exports Mixin's runtime-generated args package - a hack old Forge
     * needed for {@code @ModifyArgs} handlers to resolve generated Args classes.
     * NeoForge 1.20.2+ creates its own {@code mixin_synthetic} module that owns
     * that package, so a mod jar still shipping the dummy makes module
     * resolution fail for the WHOLE layer the moment any module reads both
     * exporters ("Modules blueprint and mixin_synthetic export package
     * org.spongepowered.asm.synthetic.args ..."), killing the game at boot.
     *
     * <p>Nothing references the dummy class itself, so removing it is safe.
     * Gated to NeoForge 1.20.2+ hosts: on old Forge hosts the hack is still
     * load-bearing and must survive the transform.
     */
    private void stripMixinSyntheticPackage(Path tempDir) throws IOException {
        if (!com.retromod.util.McReflect.isNeoForge()) return;
        if (RetromodVersion.mcVersionExceeds("1.20.2", targetMcVersion)) return;

        if (stripMixinSyntheticEntries(tempDir)) {
            LOGGER.info("Stripped org/spongepowered/asm/synthetic/ from mod jar - NeoForge's "
                    + "mixin_synthetic module owns that package (#87)");
        }
    }

    /**
     * The host-agnostic mechanism behind {@link #stripMixinSyntheticPackage}:
     * delete {@code org/spongepowered/asm/synthetic/} from the extracted tree,
     * then prune ancestor directories that became empty (so the repackaged jar
     * doesn't even declare the parent packages). A mod that shades full Mixin
     * keeps its other {@code org/spongepowered/asm/} content - only the
     * runtime-generated synthetic package is never legitimate to ship.
     * Package-private for tests.
     */
    static boolean stripMixinSyntheticEntries(Path tempDir) throws IOException {
        Path synthetic = tempDir.resolve("org/spongepowered/asm/synthetic");
        if (!Files.exists(synthetic)) return false;

        try (var walk = Files.walk(synthetic)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }

        // Prune now-empty ancestors (org/spongepowered/asm, org/spongepowered, org)
        Path parent = synthetic.getParent();
        while (parent != null && !parent.equals(tempDir)) {
            try (var children = Files.list(parent)) {
                if (children.findAny().isPresent()) break;
            }
            Files.delete(parent);
            parent = parent.getParent();
        }
        return true;
    }

    /**
     * Relax the top-level {@code loaderVersion} in a mods.toml to {@code "[1,)"}.
     * The value a Forge/old-NeoForge mod declares (e.g. {@code "[47,)"} for Forge
     * 1.20.1) is checked against the host's FancyModLoader version, which doesn't
     * track that number, so a literal range rejects the mod on modern NeoForge.
     */
    static String relaxLoaderVersion(String toml) {
        return toml.replaceAll("(?m)^(\\s*loaderVersion\\s*=\\s*)\"[^\"]*\"", "$1\"[1,)\"");
    }

    /**
     * Ensure the toml declares a top-level {@code license} field. Forge 1.16+ fatally
     * rejects a mod whose {@code mods.toml} lacks one ("Missing License Information in
     * file …"), which a mod authored before that became mandatory will hit after
     * transformation (#62). If absent, insert a neutral {@code "All Rights Reserved"} -
     * Forge only needs a non-empty value, and we don't assert a real license the author
     * never declared. Inserted before the first table header so it stays in the root
     * table (TOML requires top-level keys to precede any {@code [table]}).
     */
    static String ensureLicense(String toml) {
        if (java.util.regex.Pattern.compile("(?m)^\\s*license\\s*=").matcher(toml).find()) {
            return toml; // already declares a license - leave the author's value untouched
        }
        String line = "license=\"All Rights Reserved\" # added by Retromod (source declared none) (#62)\n";
        java.util.regex.Matcher firstTable =
                java.util.regex.Pattern.compile("(?m)^\\s*\\[").matcher(toml);
        if (firstTable.find()) {
            return toml.substring(0, firstTable.start()) + line + toml.substring(firstTable.start());
        }
        return toml.endsWith("\n") ? toml + line : toml + "\n" + line;
    }

    /**
     * Repoint a mod's {@code forge} loader dependency at {@code neoforge}.
     *
     * <p>A Forge/1.20.1 mod declares a mandatory dependency on the {@code forge}
     * mod-id, but NeoForge has no mod with that id, so it rejects the mod with
     * "Mod X requires forge 0 or above - forge is not installed" (#42) - even after
     * the metadata is promoted to {@code neoforge.mods.toml}. Pointing the
     * dependency at {@code neoforge} (which IS present) satisfies it; its
     * versionRange is already relaxed to {@code "[0,)"} by {@link #updateModsToml}.
     * Only the {@code forge} dependency id is touched (the mod's own id and the
     * {@code minecraft} dependency are left alone).
     */
    static String pointForgeDependencyAtNeoForge(String toml) {
        return toml.replaceAll("(modId\\s*=\\s*)\"forge\"", "$1\"neoforge\"");
    }

    /**
     * Update the minecraft versionRange in TOML content.
     * Finds the [[dependencies.xxx]] block with modId = "minecraft" and
     * updates its versionRange to the target version.
     *
     * Also relaxes version ranges for third-party APIs that Retromod has shims for.
     * Without this, the mod loader would block the mod at startup even though
     * Retromod has already transformed the bytecode to work with newer API versions.
     */
    private String updateMinecraftVersionRange(String toml) {
        // TOML structure:
        //   [[dependencies.modname]]
        //   modId = "minecraft"
        //   mandatory = true
        //   versionRange = "[1.20.6]"
        //
        //   [[dependencies.modname]]
        //   modId = "cloth_config"
        //   mandatory = true
        //   versionRange = "[6.0,7.0)"

        StringBuilder result = new StringBuilder();
        String[] lines = toml.split("\n");
        String currentDepModId = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Detect start of a new dependency block
            if (trimmed.startsWith("[[dependencies")) {
                currentDepModId = null;
            }

            // Detect modId = "xxx".
            // Use find() rather than matches() - many mods.toml files have a
            // trailing inline comment like  modId="forge" #mandatory  which
            // makes matches() (full-line match) return false. find() looks
            // for the pattern *anywhere* in the line, which correctly handles
            // both styles. This was the root cause of JEI / Create / etc.
            // having their minecraft versionRange go un-updated even though
            // the log claimed otherwise.
            Pattern modIdPattern = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
            Matcher modIdMatcher = modIdPattern.matcher(trimmed);
            if (modIdMatcher.find()) {
                currentDepModId = modIdMatcher.group(1);
            }

            // If we're in a dependency block, check if we should update versionRange
            if (currentDepModId != null && trimmed.startsWith("versionRange")) {
                if ("minecraft".equals(currentDepModId)) {
                    // Minecraft dependency: set to target version
                    result.append("versionRange = \"[").append(targetMcVersion).append(",)\"\n");
                    LOGGER.info("  Updated minecraft versionRange -> [{},...)", targetMcVersion);
                    currentDepModId = null;
                } else if (SHIMMED_API_MOD_IDS.contains(currentDepModId)) {
                    // Shimmed API dependency: relax to accept any version
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed API dependency: {} -> [0,...) (Retromod has shims)", currentDepModId);
                    currentDepModId = null;
                } else if ("forge".equals(currentDepModId) || "neoforge".equals(currentDepModId)) {
                    // Forge/NeoForge loader: also relax
                    result.append("versionRange = \"[0,)\"\n");
                    currentDepModId = null;
                } else if (targetMcVersion.startsWith("26.")) {
                    // For 26.1+, relax ALL non-core dependencies since most
                    // old mod versions won't be available
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed dependency: {} -> [0,...) (26.1+ compat)", currentDepModId);
                    currentDepModId = null;
                } else {
                    // Unknown dependency: keep as-is
                    result.append(line).append("\n");
                }
            } else if (currentDepModId != null &&
                       !("minecraft".equals(currentDepModId) || "neoforge".equals(currentDepModId) || "forge".equals(currentDepModId))
                       && trimmed.startsWith("mandatory")) {
                // Make non-core dependencies non-mandatory
                result.append("mandatory = false\n");
            } else if (currentDepModId != null &&
                       !("minecraft".equals(currentDepModId) || "neoforge".equals(currentDepModId) || "forge".equals(currentDepModId))
                       && trimmed.matches("type\\s*=\\s*\"required\".*")) {
                // For NeoForge format: change type="required" to type="optional"
                result.append(line.replaceFirst("\"required\"", "\"optional\"")).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Inject missing abstract methods for classes that extend Button or AbstractButton.
     *
     * In MC 26.1, AbstractButton gained a new abstract method:
     *   extractContents(GuiGraphicsExtractor, int, int, float)V
     *
     * Old mod widgets that extend Button don't implement this method, causing
     * AbstractMethodError when the game tries to call it.
     *
     * This injects a no-op implementation: { return; }
     */
    private byte[] injectMissingAbstractMethods(byte[] classBytes, String className) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            // Quick check: does this class extend Button or AbstractButton?
            String superName = reader.getSuperName();
            if (superName == null) return null;

            // Check for Button and AbstractButton class hierarchies
            // Mojang names used in 26.1 (no obfuscation)
            Set<String> buttonSuperclasses = Set.of(
                "net/minecraft/client/gui/components/Button",
                "net/minecraft/client/gui/components/AbstractButton",
                "net/minecraft/client/gui/components/AbstractWidget"
            );

            if (!buttonSuperclasses.contains(superName)) {
                return null;
            }

            // Parse the class to check for existing extractContents method
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            // The method signature for extractContents in 26.1:
            // void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick)
            // Descriptor: (Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V
            String methodName = "extractContents";
            String methodDesc = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";

            // Check if extractContents already exists
            boolean hasMethod = false;
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(methodName)) {
                    hasMethod = true;
                    break;
                }
            }

            if (hasMethod) {
                return null; // already has the method
            }

            // Inject a no-op implementation (can't call super - it's abstract):
            //   public void extractContents(GuiGraphicsExtractor g, int x, int y, float t) {
            //       // no-op - old mod doesn't know about this method
            //   }
            MethodNode newMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                methodDesc,
                null,
                null
            );

            // Generate bytecode: just return immediately
            newMethod.instructions = new InsnList();
            newMethod.instructions.add(new InsnNode(Opcodes.RETURN));
            newMethod.maxStack = 0;
            newMethod.maxLocals = 5;

            classNode.methods.add(newMethod);

            // Write the modified class
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            byte[] result = writer.toByteArray();
            LOGGER.info("Injected missing extractContents() into {} (extends {})", className, superName);
            return result;
        } catch (Exception e) {
            LOGGER.debug("Could not inject abstract methods into {}: {}", className, e.getMessage());
            return null;
        }
    }

    /**
     * Scan the extracted mod directory for mixin config files and make them non-fatal.
     * Sets "required": false and "injectors": {"defaultRequire": 0} so that
     * @Accessor/@Invoker failures on removed fields/methods don't crash the game.
     */
    private void makeMixinConfigsNonFatal(Path dir) {
        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String entryName = dir.relativize(file).toString()
                    .replace(File.separator, "/");
                if (isMixinConfigFile(entryName)) {
                    try {
                        String json = Files.readString(file);
                        String transformed = makeMixinConfigNonFatal(json);
                        if (!transformed.equals(json)) {
                            Files.writeString(file, transformed, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to process mixin config {}: {}", entryName, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan for mixin configs: {}", e.getMessage());
        }
    }

    /**
     * Make a mixin config JSON non-fatal by setting "required": false and
     * "injectors": {"defaultRequire": 0}. This prevents crashes from @Accessor/@Invoker
     * targeting fields/methods that were removed in newer MC versions.
     */
    private String makeMixinConfigNonFatal(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            // Set "required": false - makes the entire mixin config non-fatal
            root.addProperty("required", false);

            // Set "injectors": {"defaultRequire": 0} - makes all injection points optional
            com.google.gson.JsonObject injectors = root.has("injectors") && root.get("injectors").isJsonObject()
                ? root.getAsJsonObject("injectors")
                : new com.google.gson.JsonObject();
            injectors.addProperty("defaultRequire", 0);
            root.add("injectors", injectors);

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
            String result = gson.toJson(root);
            LOGGER.debug("Set mixin config to non-fatal (required=false, defaultRequire=0)");
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to make mixin config non-fatal: {}", e.getMessage());
            return json;
        }
    }

    /**
     * Check if a file path looks like a mixin config file.
     */
    private boolean isMixinConfigFile(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith(".mixins.json")) return true;       // modid.mixins.json
        if (name.endsWith("mixin.json")) return true;         // modid.mixin.json
        if (name.startsWith("mixins.") && name.endsWith(".json")) return true; // mixins.modid.json
        if (path.contains("mixins/") && name.endsWith(".json") && name.contains("mixin")) return true;
        return false;
    }

    /**
     * Strip or neutralize access widener and class tweaker files from cross-loader mods.
     * Some mods include both Fabric (.accesswidener) and Forge (META-INF/accesstransformer.cfg)
     * files. The access widener format is not understood by Forge/NeoForge and can cause issues.
     * Detection is case-insensitive to handle variations like .AccessWidener or .ACCESSWIDENER.
     */
    private void stripAccessWideners(Path dir) {
        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                String nameLower = name.toLowerCase(java.util.Locale.ROOT);
                if (nameLower.endsWith(".accesswidener") || nameLower.endsWith(".classtweaker")) {
                    // Access wideners are Fabric-only; remove from Forge/NeoForge JARs
                    // to prevent confusion. The access transformer (META-INF/accesstransformer.cfg)
                    // is the Forge equivalent and is left intact.
                    LOGGER.info("Removing Fabric access widener from Forge mod: {}", name);
                    Files.delete(file);
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to scan for access wideners: {}", e.getMessage());
        }
    }

    private void repackageJar(Path sourceDir, Path outputJar) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputJar)))) {

            // Deduplicate entries. A source mod's central directory can list the
            // same entry twice (or two entries collide on a case-insensitive
            // filesystem), and JarOutputStream.putNextEntry throws ZipException
            // on the second write - which would abort the whole transform and
            // silently drop the mod. Keep the first copy, skip the rest. This
            // mirrors the guard already in FabricModTransformer.
            Set<String> writtenEntries = new java.util.HashSet<>();

            try (var walk = Files.walk(sourceDir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");

                    if (!writtenEntries.add(entryName)) {
                        LOGGER.warn("Skipping duplicate JAR entry from source: {} "
                                + "(the source mod's central directory lists this "
                                + "entry more than once - keeping the first copy)",
                                entryName);
                        continue;
                    }

                    JarEntry entry = new JarEntry(entryName);
                    jos.putNextEntry(entry);
                    Files.copy(file, jos);
                    jos.closeEntry();
                }
            }
        }
    }
}
