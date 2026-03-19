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
 * Prevents Zip Slip (path traversal) attacks where a crafted archive
 * contains entries like "../../malicious.class" that escape the target directory.
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
