/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Writes ZIP <em>directory entries</em> into a rewritten jar.
 *
 * <p>Gradle-built mod jars carry an entry per directory ({@code com/}, {@code com/example/}, ...).
 * Retromod's tree-rezip paths used to emit only file entries, which is a silent behavior change:
 * a jar classloader resolves package resources ({@code ClassLoader.getResources("com/example")})
 * only from directory entries, so classpath scanners find NOTHING in the transformed jar.
 * Found on the 26.2 dedicated server: YungsApi's Reflections-based {@code @AutoRegister} scan hit
 * "0 urls, producing 0 keys", none of its worldgen types registered, and every YUNG structure
 * failed datapack load with "Unknown registry key" - with no exception anywhere (the original jar
 * had 48 directory entries, the transformed one 0). Affects any Reflections/ClassGraph mod.
 */
public final class JarDirectoryEntries {

    private JarDirectoryEntries() {}

    /**
     * Variant for jar writers that build from entry-name maps rather than a directory tree
     * (the AOT compiler): derive every ancestor directory from the file entry names.
     */
    public static void writeAllForNames(JarOutputStream jos, java.util.Collection<String> fileNames) {
        java.util.TreeSet<String> dirs = new java.util.TreeSet<>();
        for (String n : fileNames) {
            for (int i = n.indexOf('/'); i >= 0; i = n.indexOf('/', i + 1)) {
                dirs.add(n.substring(0, i + 1));
            }
        }
        for (String d : dirs) {
            try {
                // names derive from untrusted jar entry names on the AOT path - zip-slip guard
                jos.putNextEntry(new JarEntry(ZipSecurity.safeEntryName(d)));
                jos.closeEntry();
            } catch (IOException | RuntimeException ignored) {
                // duplicate entry or rejected traversal name - skip
            }
        }
    }

    /**
     * Emit a directory entry for every directory under {@code root} (parents before children).
     * Call right after opening the {@link JarOutputStream}, before writing file entries.
     * Duplicate-entry collisions (e.g. a manifest-writing constructor) are ignored per entry.
     */
    public static void writeAll(JarOutputStream jos, Path root) {
        final java.util.List<Path> dirs;
        try (var stream = Files.walk(root)) {
            dirs = stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            return;
        }
        for (Path d : dirs) {
            String rel = root.relativize(d).toString().replace(File.separatorChar, '/');
            if (rel.isEmpty()) continue;
            try {
                jos.putNextEntry(new JarEntry(rel + "/"));
                jos.closeEntry();
            } catch (IOException ignored) {
                // duplicate entry - already present, which is fine
            }
        }
    }
}
