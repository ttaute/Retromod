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
 * Like FabricModTransformer, but handles mods.toml / neoforge.mods.toml instead
 * of fabric.mod.json: extract, transform bytecode, patch metadata, repackage.
 */
public class ForgeModTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-ForgeTransform");

    /** Per-entry and total extracted size caps (zip-bomb guard). */
    private static final long MAX_ENTRY_SIZE = 50 * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024;

    /**
     * Forge/NeoForge mod IDs for APIs Retromod shims. A restrictive versionRange
     * on these in mods.toml would block the mod, so we relax the constraint since
     * the bytecode is already transformed.
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
     * Transform a Forge or NeoForge mod JAR. Returns the transformed JAR path,
     * or null if it failed or was skipped.
     */
    public Path transformMod(Path sourceJar, Path outputDir) throws IOException {
        String originalName = sourceJar.getFileName().toString();
        String baseName = originalName.replace(".jar", "");
        // When a mod ships no module-info / Automatic-Module-Name, NeoForge/Forge
        // derive the JPMS module name from the jar filename; spaces or odd chars
        // break the derived module's reads, so the mod can't resolve core MC
        // classes in its own <clinit> (#47). Sanitize so the name is always valid.
        String safeBaseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        String outputName = safeBaseName + "-retromod.jar";
        Path outputJar = outputDir.resolve(outputName);

        LOGGER.info("Checking Forge/NeoForge mod: {}", originalName);

        // Honor mod-author opt-out (META-INF/retromod-opt-out marker).
        if (com.retromod.util.OptOutCheck.isOptedOut(sourceJar)) {
            com.retromod.util.OptOutCheck.logSkipped(sourceJar);
            Path passthrough = outputDir.resolve(originalName);
            Files.copy(sourceJar, passthrough, StandardCopyOption.REPLACE_EXISTING);
            return passthrough;
        }

        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (targetMcVersion.equals(modMcVersion)) {
            LOGGER.info("  {} is already for MC {} - no transformation needed", originalName, targetMcVersion);
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }

        LOGGER.info("Transforming Forge/NeoForge mod: {} -> {}", originalName, outputName);

        Path tempDir = Files.createTempDirectory("retromod-forge-");

        try {
            extractJar(sourceJar, tempDir);

            int classesTransformed = transformClasses(tempDir);
            LOGGER.info("Transformed {} class files", classesTransformed);

            makeMixinConfigsNonFatal(tempDir);
            stripAccessWideners(tempDir);
            stripMixinSyntheticPackage(tempDir);

            // Embed registered synthetic classes this mod references, under a
            // unique-per-mod Retromod package (split-package-safe), and redirect
            // the mod's references there. No-op until a synthetic is both
            // registered and referenced by this mod.
            int synthetics = SyntheticEmbedder.embed(
                    tempDir, sourceJar.getFileName().toString(), bytecodeTransformer);
            if (synthetics > 0) {
                LOGGER.info("Embedded {} referenced synthetic class(es)", synthetics);
            }

            updateModsToml(tempDir, "META-INF/mods.toml");
            updateModsToml(tempDir, "META-INF/neoforge.mods.toml");
            promoteToNeoForgeToml(tempDir);

            // Patch metadata in nested Jar-in-Jar deps. A bundled dep (e.g.
            // Flywheel inside Create) carries its own mods.toml with a stale
            // minecraft versionRange that Forge would otherwise reject on 26.1.
            int jijPatched = patchJarInJarMetadata(tempDir);
            if (jijPatched > 0) {
                LOGGER.info("Patched metadata in {} JIJ dependencies", jijPatched);
            }

            // Migrate bundled data-pack JSON across the 1.21.x -> 26.x data-only
            // format changes the bytecode pass can't reach. Gated to 26.x inside
            // ModDataMigrator.
            int dataMigrated = com.retromod.resources.ModDataMigrator.migrateTree(tempDir, targetMcVersion);
            if (dataMigrated > 0) {
                LOGGER.info("Migrated 26.x data formats in {} data file(s)", dataMigrated);
            }

            repackageJar(tempDir, outputJar);
            LOGGER.info("Created transformed mod: {}", outputJar.getFileName());

            if (TransformVerifier.isEnabled()) {
                TransformVerifier.verifyAndReport(outputJar, originalName, targetMcVersion);
            }

            return outputJar;

        } catch (Exception e) {
            LOGGER.error("Failed to transform {}: {}", originalName, e.getMessage());
            return null;
        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /** Read the minecraft version a Forge/NeoForge mod JAR targets, or null. */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = jar.getEntry("META-INF/mods.toml");

            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                Pattern p = Pattern.compile("versionRange\\s*=\\s*\"\\[([0-9.]+)");
                Matcher m = p.matcher(content);
                // The first match is usually the forge/neoforge version; keep
                // scanning for the minecraft one.
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
     * Extract JAR with zip-bomb protection based on bytes actually read, not the
     * declared entry size. See {@link FabricModTransformer#extractJar}.
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

        // Parallel per-class transform: classes are independent and the
        // transformer's redirect tables are thread-safe for reads.
        final java.util.concurrent.atomic.AtomicInteger counter =
                new java.util.concurrent.atomic.AtomicInteger();

        com.retromod.core.parallel.RetromodExecutors.parallelForEach(classFiles, classFile -> {
            try {
                byte[] original = Files.readAllBytes(classFile);
                String className = dir.relativize(classFile).toString()
                    .replace(".class", "")
                    .replace(File.separator, "/");

                // Strip blocklisted mixin handlers first (NeoForge/Forge path; the
                // Fabric path does this inside FabricModTransformer). No-op for a
                // class that isn't a blocklisted mixin (#48).
                byte[] preStripped = mixinTransformer.stripBlocklistedHandlers(original);
                byte[] transformed = bytecodeTransformer.transformClass(preStripped, className);
                boolean wroteFirst = false;
                if (transformed != null && transformed != original) {
                    Files.write(classFile, transformed);
                    counter.incrementAndGet();
                    wroteFirst = true;
                }

                // Inject missing abstract methods for Button subclasses.
                byte[] current = (transformed != null && transformed != original) ? transformed : original;
                byte[] patched = injectMissingAbstractMethods(current, className);
                if (patched != null && patched != current) {
                    Files.write(classFile, patched);
                    if (!wroteFirst) counter.incrementAndGet();
                }
            } catch (Exception e) {
                LOGGER.warn("Could not transform class: {} ({}: {})",
                        classFile.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            }
        });

        return counter.get();
    }

    /** Max Jar-in-Jar nesting depth Retromod recurses through. */
    private static final int MAX_JIJ_DEPTH = 4;

    /** Patch metadata in every nested Jar-in-Jar dep. Returns the count patched. */
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
                    // One bad JIJ shouldn't abort the whole transform; log and continue.
                    LOGGER.warn("Could not patch JIJ {}: {}", jijJar.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not list JIJ directory: {}", e.getMessage());
        }
        return patched;
    }

    /**
     * Transform a single Jar-in-Jar library: extract, rewrite bytecode with the
     * outer mod's transformer, patch metadata, recurse into its own bundled jars,
     * and repack over the original. Returns true if the jar was rewritten, false
     * for a pure library with nothing to do (#95).
     */
    private boolean patchSingleJijJar(Path jijJar, int depth) throws IOException {
        Path tempDir = Files.createTempDirectory("retromod-jij-");
        try {
            extractJar(jijJar, tempDir);
            int classesTransformed = transformClasses(tempDir);

            boolean hasForgeToml      = Files.exists(tempDir.resolve("META-INF/mods.toml"));
            boolean hasNeoForgeToml   = Files.exists(tempDir.resolve("META-INF/neoforge.mods.toml"));

            if (hasForgeToml) {
                updateModsToml(tempDir, "META-INF/mods.toml");
            }
            if (hasNeoForgeToml) {
                updateModsToml(tempDir, "META-INF/neoforge.mods.toml");
            }
            promoteToNeoForgeToml(tempDir);

            int dataMigrated = com.retromod.resources.ModDataMigrator.migrateTree(tempDir, targetMcVersion);

            int nestedPatched = (depth < MAX_JIJ_DEPTH) ? patchJarInJarMetadata(tempDir, depth + 1) : 0;

            boolean changed = classesTransformed > 0 || hasForgeToml || hasNeoForgeToml
                    || dataMigrated > 0 || nestedPatched > 0;
            if (!changed) {
                return false;
            }

            Files.delete(jijJar);
            repackageJar(tempDir, jijJar);
            LOGGER.debug("Transformed JIJ {} ({} class(es) rewritten)", jijJar.getFileName(), classesTransformed);
            return true;

        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /** Update mods.toml / neoforge.mods.toml to target the correct MC version. */
    private void updateModsToml(Path dir, String tomlPath) throws IOException {
        Path tomlFile = dir.resolve(tomlPath);
        if (!Files.exists(tomlFile)) return;

        String content = Files.readString(tomlFile);
        String original = content;

        content = updateMinecraftVersionRange(content);

        // Forge 1.16+ rejects a jar whose mods.toml lacks a top-level license
        // ("Missing License Information in file ..."); supply a neutral default
        // when the source has none (#62).
        content = ensureLicense(content);

        if (!content.contains("retromod_transformed")) {
            content = content + "\n# Transformed by Retromod (original version modified)\n";
        }

        if (!content.equals(original)) {
            Files.writeString(tomlFile, content);
            LOGGER.info("Updated {}: minecraft versionRange -> [{}]", tomlPath, targetMcVersion);
        }
    }

    /**
     * On a NeoForge 1.20.2+ host, promote a legacy {@code META-INF/mods.toml} to
     * {@code META-INF/neoforge.mods.toml}. NeoForge renamed the file in 1.20.2 and
     * skips a jar carrying only {@code mods.toml} at scan time ("for Minecraft Forge
     * or an older version of NeoForge"), before any bytecode runs, so a 1.20.1
     * (Neo)Forge mod never loads (#42). Gated to NeoForge so LexForge is untouched.
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
     * Some 1.20.1-era Forge mods ship a placeholder at
     * {@code org/spongepowered/asm/synthetic/args/Dummy.class} to export Mixin's
     * generated args package. NeoForge 1.20.2+ has its own {@code mixin_synthetic}
     * module owning that package, so a jar still shipping the dummy split-packages
     * with it and module resolution fails for the whole layer at boot. Gated to
     * NeoForge 1.20.2+; old Forge hosts still need the hack.
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
     * Mechanism behind {@link #stripMixinSyntheticPackage}: delete
     * {@code org/spongepowered/asm/synthetic/} and prune now-empty ancestors. A
     * mod shading full Mixin keeps its other {@code org/spongepowered/asm/}
     * content. Package-private for tests.
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
     * Relax the top-level {@code loaderVersion} to {@code "[1,)"}. A Forge/old-NeoForge
     * value (e.g. {@code "[47,)"}) is checked against the host's FancyModLoader version,
     * which doesn't track that number, so a literal range rejects the mod on NeoForge.
     */
    static String relaxLoaderVersion(String toml) {
        return toml.replaceAll("(?m)^(\\s*loaderVersion\\s*=\\s*)\"[^\"]*\"", "$1\"[1,)\"");
    }

    /**
     * Ensure the toml declares a top-level {@code license}. Forge 1.16+ rejects a
     * mod whose {@code mods.toml} lacks one (#62); insert a neutral
     * {@code "All Rights Reserved"} when absent. Inserted before the first table
     * header, since TOML top-level keys must precede any {@code [table]}.
     */
    static String ensureLicense(String toml) {
        if (java.util.regex.Pattern.compile("(?m)^\\s*license\\s*=").matcher(toml).find()) {
            return toml; // already declares a license; leave the author's value untouched
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
     * Repoint a mod's mandatory {@code forge} loader dependency at {@code neoforge}.
     * NeoForge has no mod with id {@code forge}, so without this it rejects the mod
     * even after the toml is promoted ("Mod X requires forge ... is not installed",
     * #42). Only the {@code forge} dependency id is touched.
     */
    static String pointForgeDependencyAtNeoForge(String toml) {
        return toml.replaceAll("(modId\\s*=\\s*)\"forge\"", "$1\"neoforge\"");
    }

    /**
     * Update the minecraft versionRange in TOML content, and relax ranges for
     * third-party APIs Retromod shims (otherwise the loader blocks the mod even
     * though its bytecode is already transformed).
     */
    private String updateMinecraftVersionRange(String toml) {
        StringBuilder result = new StringBuilder();
        String[] lines = toml.split("\n");
        String currentDepModId = null;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("[[dependencies")) {
                currentDepModId = null;
            }

            // find() not matches(): a trailing inline comment like
            // modId="forge" #mandatory defeats a full-line match, which left
            // JEI/Create etc. with their versionRange un-updated.
            Pattern modIdPattern = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
            Matcher modIdMatcher = modIdPattern.matcher(trimmed);
            if (modIdMatcher.find()) {
                currentDepModId = modIdMatcher.group(1);
            }

            if (currentDepModId != null && trimmed.startsWith("versionRange")) {
                if ("minecraft".equals(currentDepModId)) {
                    result.append("versionRange = \"[").append(targetMcVersion).append(",)\"\n");
                    LOGGER.info("  Updated minecraft versionRange -> [{},...)", targetMcVersion);
                    currentDepModId = null;
                } else if (SHIMMED_API_MOD_IDS.contains(currentDepModId)) {
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed API dependency: {} -> [0,...) (Retromod has shims)", currentDepModId);
                    currentDepModId = null;
                } else if ("forge".equals(currentDepModId) || "neoforge".equals(currentDepModId)) {
                    result.append("versionRange = \"[0,)\"\n");
                    currentDepModId = null;
                } else if (targetMcVersion.startsWith("26.")) {
                    // For 26.1+, relax all non-core deps; most old versions aren't available.
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed dependency: {} -> [0,...) (26.1+ compat)", currentDepModId);
                    currentDepModId = null;
                } else {
                    result.append(line).append("\n");
                }
            } else if (currentDepModId != null &&
                       !("minecraft".equals(currentDepModId) || "neoforge".equals(currentDepModId) || "forge".equals(currentDepModId))
                       && trimmed.startsWith("mandatory")) {
                result.append("mandatory = false\n");
            } else if (currentDepModId != null &&
                       !("minecraft".equals(currentDepModId) || "neoforge".equals(currentDepModId) || "forge".equals(currentDepModId))
                       && trimmed.matches("type\\s*=\\s*\"required\".*")) {
                // NeoForge format: type="required" -> "optional"
                result.append(line.replaceFirst("\"required\"", "\"optional\"")).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Inject a no-op {@code extractContents} into Button/AbstractButton subclasses.
     * MC 26.1's AbstractButton gained that abstract method; old widgets that don't
     * implement it hit AbstractMethodError when the game calls it.
     */
    private byte[] injectMissingAbstractMethods(byte[] classBytes, String className) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            String superName = reader.getSuperName();
            if (superName == null) return null;

            Set<String> buttonSuperclasses = Set.of(
                "net/minecraft/client/gui/components/Button",
                "net/minecraft/client/gui/components/AbstractButton",
                "net/minecraft/client/gui/components/AbstractWidget"
            );

            if (!buttonSuperclasses.contains(superName)) {
                return null;
            }

            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            String methodName = "extractContents";
            String methodDesc = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";

            boolean hasMethod = false;
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(methodName)) {
                    hasMethod = true;
                    break;
                }
            }

            if (hasMethod) {
                return null;
            }

            // No-op body; can't call super since it's abstract.
            MethodNode newMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                methodDesc,
                null,
                null
            );

            newMethod.instructions = new InsnList();
            newMethod.instructions.add(new InsnNode(Opcodes.RETURN));
            newMethod.maxStack = 0;
            newMethod.maxLocals = 5;

            classNode.methods.add(newMethod);

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

    /** Make every mixin config under {@code dir} non-fatal. */
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
     * Set {@code "required": false} and {@code "injectors": {"defaultRequire": 0}}
     * so @Accessor/@Invoker targeting fields/methods removed in newer MC don't crash.
     */
    private String makeMixinConfigNonFatal(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            root.addProperty("required", false);

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

    private boolean isMixinConfigFile(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith(".mixins.json")) return true;       // modid.mixins.json
        if (name.endsWith("mixin.json")) return true;         // modid.mixin.json
        if (name.startsWith("mixins.") && name.endsWith(".json")) return true; // mixins.modid.json
        if (path.contains("mixins/") && name.endsWith(".json") && name.contains("mixin")) return true;
        return false;
    }

    /**
     * Remove Fabric access wideners / class tweakers from a Forge/NeoForge jar
     * (cross-loader mods ship both); Forge can't read the format. The Forge
     * equivalent, META-INF/accesstransformer.cfg, is left intact.
     */
    private void stripAccessWideners(Path dir) {
        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                String nameLower = name.toLowerCase(java.util.Locale.ROOT);
                if (nameLower.endsWith(".accesswidener") || nameLower.endsWith(".classtweaker")) {
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

            // A source mod's central directory can list an entry twice (or two
            // collide on a case-insensitive FS); the second putNextEntry throws
            // ZipException and aborts the transform. Keep the first, skip dupes.
            Set<String> writtenEntries = new java.util.HashSet<>();

            try (var walk = Files.walk(sourceDir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");

                    if (!writtenEntries.add(entryName)) {
                        LOGGER.warn("Skipping duplicate JAR entry from source: {} (keeping first copy)",
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
