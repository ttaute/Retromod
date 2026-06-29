/*
 * Retromod - per-mod synthetic-class embedding.
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
 * loader classes) into the NeoForge/Forge mods that reference them, and rewrites those
 * references to the embedded copies.
 *
 * <p>Each copy goes under {@code com/retromod/embedded/<mod-key>/}, unique per mod. Embedding at
 * the original name would split-package with the loader/neoforge modules that still own those
 * packages (and two mods would split-package with each other), which is a JPMS crash.
 */
public final class SyntheticEmbedder {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");
    /** Retromod-owned package root for embedded synthetics. */
    public static final String PREFIX = "com/retromod/embedded/";

    private SyntheticEmbedder() {}

    /**
     * Embed every registered synthetic that {@code modDir}'s classes reference and rewrite those
     * references to the embedded copies.
     *
     * @param modDir    extracted mod directory (classes at their package paths)
     * @param uniqueKey per-mod identifier (mod id or jar name) keeping the embedded package distinct
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

            // which registered synthetics does this mod reference?
            Set<String> referenced = new HashSet<>();
            for (Path cf : classFiles) {
                try {
                    referenced.addAll(referencedClasses(Files.readAllBytes(cf)));
                } catch (IOException ignored) {
                }
            }
            referenced.retainAll(synthetics.keySet());
            if (referenced.isEmpty()) return 0;

            String base = PREFIX + sanitize(uniqueKey) + "/";
            Map<String, String> rename = new HashMap<>();
            for (String n : referenced) {
                rename.put(n, base + n.substring(n.lastIndexOf('/') + 1));
            }
            Remapper remapper = new SimpleRemapper(rename);

            for (String n : referenced) {
                byte[] renamed = remap(synthetics.get(n), remapper);
                Path target = modDir.resolve(rename.get(n) + ".class");
                Files.createDirectories(target.getParent());
                Files.write(target, renamed);
            }
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
     * Jar-based variant for the offline CLI/AOT paths: reads {@code jarPath}, embeds the referenced
     * synthetics, rewrites references, and writes the jar back. Uses {@code Zip*Stream} so
     * {@code META-INF/MANIFEST.MF} and every other entry survive.
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

    /** Internal class names referenced by a class. */
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
        }
        return refs;
    }

    private static byte[] remap(byte[] classBytes, Remapper remapper) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(0); // pure rename, no frame/maxs recomputation
        cr.accept(new ClassRemapper(cw, remapper), 0);
        return cw.toByteArray();
    }

    /** A jar name / mod id reduced to a package-legal segment. */
    private static String sanitize(String key) {
        String s = key.replaceAll("\\.jar$", "").replaceAll("[^A-Za-z0-9_]", "_");
        return s.isEmpty() ? "mod" : s;
    }
}
