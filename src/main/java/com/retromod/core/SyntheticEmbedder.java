/*
 * Retromod - per-mod synthetic-class embedding (split-package-safe).
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Embeds Retromod's registered synthetic classes (ASM-generated polyfills for deleted MC or
 * loader classes, such as a {@code DeferredSpawnEggItem} or {@code FMLJavaModLoadingContext}
 * bridge) into the NeoForge/Forge mods that reference them, under a unique-per-mod package,
 * and rewrites the mod's references to the embedded copy.
 *
 * <p>We can't embed at the original name: the deleted classes live in packages the
 * loader/neoforge modules still own, so embedding a synthetic at its original name into a mod
 * module split-packages with the loader (a JPMS crash), and two mods embedding it split-package
 * with each other. Instead we put each copy under {@code com/retromod/embedded/<mod-key>/},
 * unique per mod, so no two modules share an embedded package and none collides with a
 * loader/MC package.
 *
 * <p>No-op when no synthetics are registered or the mod references none. Soft-fails (returns 0,
 * leaves the mod untouched) on any error.
 */
public final class SyntheticEmbedder {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");
    /** Retromod-owned package root for embedded synthetics. */
    public static final String PREFIX = "com/retromod/embedded/";

    private SyntheticEmbedder() {}

    /**
     * Embed every registered synthetic that {@code modDir}'s classes reference, under a
     * unique-per-mod package, and rewrite those references to point at the embedded copies.
     *
     * @param modDir    extracted mod directory (classes at their package paths)
     * @param uniqueKey a per-mod identifier (mod id or jar name); guarantees the embedded
     *                  package is distinct from every other mod's, so no JPMS split-package
     * @return number of synthetics embedded (0 if none referenced / none registered / error)
     */
    public static int embed(Path modDir, String uniqueKey, RetromodTransformer transformer) {
        Map<String, byte[]> synthetics = transformer.getSyntheticClasses();
        if (synthetics == null || synthetics.isEmpty()) return 0;
        try {
            final List<Path> classFiles;
            try (var s = Files.walk(modDir)) {
                classFiles = s.filter(p -> p.toString().endsWith(".class"))
                        .filter(p -> !p.toString().contains("META-INF"))
                        .toList();
            }

            // Which registered synthetics does this mod actually reference?
            Set<String> referenced = new HashSet<>();
            for (Path cf : classFiles) {
                try {
                    referenced.addAll(referencedClasses(Files.readAllBytes(cf)));
                } catch (IOException ignored) {
                    // unreadable class; skip it
                }
            }
            referenced.retainAll(synthetics.keySet());
            if (referenced.isEmpty()) return 0;

            // Map each referenced original name to a unique-per-mod Retromod package.
            String base = PREFIX + sanitize(uniqueKey) + "/";
            Map<String, String> rename = new HashMap<>();
            for (String n : referenced) {
                rename.put(n, base + n.substring(n.lastIndexOf('/') + 1));
            }
            Remapper remapper = new SimpleRemapper(rename);

            // Embed each referenced synthetic, renamed into the unique package.
            for (String n : referenced) {
                byte[] renamed = remap(synthetics.get(n), remapper);
                Path target = modDir.resolve(rename.get(n) + ".class");
                Files.createDirectories(target.getParent());
                Files.write(target, renamed);
            }
            // Rewrite the mod's own classes so their references point at the embedded copies.
            for (Path cf : classFiles) {
                byte[] in = Files.readAllBytes(cf);
                byte[] out = remap(in, remapper);
                if (!Arrays.equals(in, out)) Files.write(cf, out);
            }
            LOGGER.info("Embedded {} referenced synthetic class(es) into '{}' under {}",
                    referenced.size(), uniqueKey, base);
            return referenced.size();
        } catch (Exception e) {
            LOGGER.warn("Synthetic embedding skipped for '{}': {}", uniqueKey, e.toString());
            return 0;
        }
    }

    /**
     * Jar-based variant for the offline CLI/AOT paths (no extract-to-dir): reads {@code jarPath},
     * embeds the referenced synthetics under a unique-per-mod package + rewrites references, and
     * writes the jar back. Uses {@code Zip*Stream} so {@code META-INF/MANIFEST.MF} and every other
     * entry are preserved verbatim. No-op (returns 0) if nothing referenced or on any error.
     */
    public static int embedIntoJar(Path jarPath, String uniqueKey, RetromodTransformer transformer) {
        Map<String, byte[]> synthetics = transformer.getSyntheticClasses();
        if (synthetics == null || synthetics.isEmpty()) return 0;
        try {
            java.util.LinkedHashMap<String, byte[]> entries = new java.util.LinkedHashMap<>();
            try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(jarPath))) {
                java.util.zip.ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (!e.isDirectory()) entries.put(e.getName(), zis.readAllBytes());
                }
            }
            Set<String> referenced = new HashSet<>();
            for (var en : entries.entrySet()) {
                if (en.getKey().endsWith(".class")) referenced.addAll(referencedClasses(en.getValue()));
            }
            referenced.retainAll(synthetics.keySet());
            if (referenced.isEmpty()) return 0;

            String base = PREFIX + sanitize(uniqueKey) + "/";
            Map<String, String> rename = new HashMap<>();
            for (String n : referenced) rename.put(n, base + n.substring(n.lastIndexOf('/') + 1));
            Remapper remapper = new SimpleRemapper(rename);

            java.util.LinkedHashMap<String, byte[]> out = new java.util.LinkedHashMap<>();
            for (var en : entries.entrySet()) {
                byte[] v = en.getValue();
                if (en.getKey().endsWith(".class")) v = remap(v, remapper);
                out.put(en.getKey(), v);
            }
            for (String n : referenced) out.put(rename.get(n) + ".class", remap(synthetics.get(n), remapper));

            try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jarPath))) {
                for (var en : out.entrySet()) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(en.getKey()));
                    zos.write(en.getValue());
                    zos.closeEntry();
                }
            }
            LOGGER.info("Embedded {} referenced synthetic class(es) into jar '{}' under {}",
                    referenced.size(), uniqueKey, base);
            return referenced.size();
        } catch (Exception e) {
            LOGGER.warn("Synthetic embedding (jar) skipped for '{}': {}", uniqueKey, e.toString());
            return 0;
        }
    }

    /** Internal class names referenced by a class (comprehensive, drives the ASM remapper). */
    static Set<String> referencedClasses(byte[] classBytes) {
        Set<String> refs = new HashSet<>();
        try {
            new ClassReader(classBytes).accept(new ClassRemapper(
                    new ClassWriter(0),
                    new Remapper() {
                        @Override public String map(String internalName) {
                            refs.add(internalName);
                            return internalName;
                        }
                    }), ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        } catch (Exception ignored) {
            // malformed class; contributes no references
        }
        return refs;
    }

    private static byte[] remap(byte[] classBytes, Remapper remapper) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(0); // pure rename: no frame/maxs recomputation needed
        cr.accept(new ClassRemapper(cw, remapper), 0);
        return cw.toByteArray();
    }

    /** A jar name / mod id reduced to a safe, package-legal segment. */
    private static String sanitize(String key) {
        String s = key.replaceAll("\\.jar$", "").replaceAll("[^A-Za-z0-9_]", "_");
        return s.isEmpty() ? "mod" : s;
    }
}
