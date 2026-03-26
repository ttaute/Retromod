/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Security utilities for ZIP/JAR extraction.
 *
 * <p><b>Zip Slip</b> is a path traversal vulnerability (CVE-2018-1263) where a
 * crafted ZIP/JAR archive contains entries with paths like {@code "../../etc/malicious.class"}.
 * If extracted naively, these entries escape the intended output directory and can
 * overwrite arbitrary files on the filesystem. Since RetroMod extracts and re-packages
 * mod JARs, we must validate every entry path before writing.</p>
 *
 * <p><b>Symlink attacks:</b> An attacker could replace the retromod-input/ directory
 * with a symlink pointing to a sensitive location (e.g., {@code ~/.ssh/}). When
 * RetroMod writes transformed mods to that directory, it would actually be writing
 * to the symlinked target. We check for symlinks before operating on directories.</p>
 *
 * @see <a href="https://security.snyk.io/research/zip-slip-vulnerability">Snyk Zip Slip Research</a>
 */
public final class ZipSecurity {

    private ZipSecurity() {}

    /**
     * Safely resolve a ZIP entry name against an output directory.
     * Normalizes the path and verifies it stays within the target directory.
     *
     * @param outputDir the target extraction directory
     * @param entryName the ZIP entry name to resolve
     * @return the validated output path
     * @throws IOException if the entry name would escape the output directory
     */
    public static Path safeResolve(Path outputDir, String entryName) throws IOException {
        Path resolved = outputDir.resolve(entryName).normalize();
        if (!resolved.startsWith(outputDir.normalize())) {
            throw new IOException("Zip Slip: entry '" + entryName
                + "' resolves outside target directory");
        }
        return resolved;
    }

    /**
     * Validate that a path is not a symbolic link.
     * Prevents symlink attacks where an attacker replaces a directory with a symlink
     * to trick RetroMod into reading/writing files outside the game directory.
     *
     * @param path the path to check
     * @throws IOException if the path is a symbolic link
     */
    public static void validateNotSymlink(Path path) throws IOException {
        if (Files.exists(path) && Files.isSymbolicLink(path)) {
            throw new IOException("Security: symlink detected at " + path
                + " — refusing to operate on symlinked directories");
        }
    }
}
