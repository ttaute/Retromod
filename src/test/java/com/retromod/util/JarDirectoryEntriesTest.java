/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rewritten jars must carry ZIP directory entries: a jar classloader resolves package resources
 * (ClassLoader.getResources("com/example")) only from them, so classpath scanners (Reflections:
 * YungsApi's @AutoRegister) silently find NOTHING in a jar without them. Found on the headless
 * 26.2 server: original YungsApi had 48 directory entries, the transformed jar 0, and every YUNG
 * worldgen type failed datapack load as "Unknown registry key" with no exception anywhere.
 */
class JarDirectoryEntriesTest {

    private static List<String> entryNames(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) names.add(e.getName());
        }
        return names;
    }

    @Test
    void treeWalkEmitsEveryDirectory(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("com/example/mod/util"));
        Files.writeString(dir.resolve("com/example/mod/A.txt"), "a");
        Files.writeString(dir.resolve("com/example/mod/util/B.txt"), "b");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            JarDirectoryEntries.writeAll(jos, dir);
            jos.putNextEntry(new JarEntry("com/example/mod/A.txt"));
            jos.write("a".getBytes());
            jos.closeEntry();
        }
        List<String> names = entryNames(baos.toByteArray());
        assertTrue(names.contains("com/"), "top-level package dir entry");
        assertTrue(names.contains("com/example/"), "intermediate dir entry");
        assertTrue(names.contains("com/example/mod/"), "package dir entry (what getResources needs)");
        assertTrue(names.contains("com/example/mod/util/"), "nested dir entry");
    }

    @Test
    void nameDerivedVariantCoversAllAncestors() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            JarDirectoryEntries.writeAllForNames(jos,
                    List.of("com/example/deep/pkg/Thing.class", "assets/m/lang/en_us.json"));
        }
        List<String> names = entryNames(baos.toByteArray());
        for (String d : new String[]{"com/", "com/example/", "com/example/deep/",
                "com/example/deep/pkg/", "assets/", "assets/m/", "assets/m/lang/"}) {
            assertTrue(names.contains(d), "missing derived dir: " + d);
        }
    }

    @Test
    void duplicateDirectoriesAreTolerated(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("pkg"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos)) {
            jos.putNextEntry(new JarEntry("pkg/"));   // already present (e.g. streamed through)
            jos.closeEntry();
            assertDoesNotThrow(() -> JarDirectoryEntries.writeAll(jos, dir));
        }
    }
}
