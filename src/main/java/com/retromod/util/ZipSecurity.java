/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Security utilities for ZIP/JAR extraction.
 *
 * <p>Guards against Zip Slip path traversal (CVE-2018-1263), zip bombs, and symlink
 * redirection while Retromod extracts and re-packages mod JARs.</p>
 *
 * @see <a href="https://security.snyk.io/research/zip-slip-vulnerability">Snyk Zip Slip Research</a>
 */
public final class ZipSecurity {

    /** Default per-entry size cap (50 MB), matching Fabric/Forge extractor defaults. */
    public static final long DEFAULT_MAX_ENTRY_SIZE = 50L * 1024 * 1024;
    /** Default total extraction cap (500 MB). */
    public static final long DEFAULT_MAX_TOTAL_SIZE = 500L * 1024 * 1024;

    private ZipSecurity() {}

    /**
     * Validate that a ZIP entry name has no traversal components, absolute path, or null/empty value.
     * Apply when copying an entry name to disk or into another archive, since downstream extractors
     * may be vulnerable to zip-slip too.
     *
     * @param entryName the entry name to validate
     * @return the unchanged entry name if safe
     * @throws IOException if the entry name contains unsafe components
     */
    public static String safeEntryName(String entryName) throws IOException {
        if (entryName == null || entryName.isEmpty()) {
            throw new IOException("ZIP entry has null/empty name");
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("\\")) {
            throw new IOException("ZIP entry name is absolute: " + entryName);
        }
        // Windows drive-letter absolute paths
        if (normalized.length() >= 3 && normalized.charAt(1) == ':' &&
                (normalized.charAt(2) == '/' || normalized.charAt(2) == '\\')) {
            throw new IOException("ZIP entry name has drive letter: " + entryName);
        }
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) {
                throw new IOException("ZIP entry name contains path traversal: " + entryName);
            }
        }
        return entryName;
    }

    /**
     * Read an InputStream into a byte array, capping total bytes read. Counts bytes read rather
     * than trusting the header-declared size, so an archive reporting size=0 while streaming
     * gigabytes is caught.
     *
     * @param is       the stream to read (not closed by this method)
     * @param maxBytes the maximum bytes to read before throwing
     * @return the bytes read
     * @throws IOException if the stream exceeds maxBytes, or any I/O error
     */
    public static byte[] safeReadAllBytes(InputStream is, long maxBytes) throws IOException {
        if (maxBytes <= 0) maxBytes = DEFAULT_MAX_ENTRY_SIZE;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("ZIP entry exceeds " + maxBytes + " bytes (possible zip bomb)");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Convenience overload that uses the default per-entry cap. */
    public static byte[] safeReadAllBytes(InputStream is) throws IOException {
        return safeReadAllBytes(is, DEFAULT_MAX_ENTRY_SIZE);
    }

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
     * Validate that a path is not a symbolic link, blocking redirection of reads/writes outside
     * the game directory.
     *
     * @param path the path to check
     * @throws IOException if the path is a symbolic link
     */
    public static void validateNotSymlink(Path path) throws IOException {
        if (Files.exists(path) && Files.isSymbolicLink(path)) {
            throw new IOException("Security: symlink detected at " + path
                + " - refusing to operate on symlinked directories");
        }
    }

    /**
     * Stream-copy from {@code is} to {@code target}, throwing if more than
     * {@code maxBytes} are written. Returns the actual number of bytes written.
     *
     * <p>Use this instead of {@link Files#copy(InputStream, Path, java.nio.file.CopyOption...)}
     * when extracting from an untrusted archive. The cap is enforced against bytes read, not the
     * size declared in the central directory, so an entry that lies about its size (a zip bomb
     * whose header reports a few KB but whose deflate stream expands to gigabytes) is caught
     * mid-stream rather than after the disk fills up.</p>
     *
     * @param is        the stream to read from (caller closes)
     * @param target    the file to write to (created/truncated)
     * @param maxBytes  the maximum bytes to write before throwing
     * @param entryNameForError entry name to include in any error message
     * @return the actual number of bytes written
     * @throws IOException if the stream exceeds maxBytes, or any I/O error
     */
    public static long copyBounded(InputStream is, Path target, long maxBytes,
                                    String entryNameForError) throws IOException {
        long written = 0;
        byte[] buf = new byte[8192];
        try (var out = Files.newOutputStream(target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
            int n;
            while ((n = is.read(buf)) > 0) {
                written += n;
                if (written > maxBytes) {
                    // partial write is left for the caller's temp-dir cleanup on the IOException path
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

    /** Convenience overload using {@link #DEFAULT_MAX_ENTRY_SIZE}. */
    public static long copyBounded(InputStream is, Path target, String entryNameForError) throws IOException {
        return copyBounded(is, target, DEFAULT_MAX_ENTRY_SIZE, entryNameForError);
    }
}
