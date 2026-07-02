/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 *
 * AOT cache generation stamp: auto-clears the cache when the Retromod build changes.
 */
package com.retromod.aot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Generation marker for {@code config/retromod/aot-cache}. Cached AOT output encodes the
 * transform logic of the Retromod build that wrote it, so after a Retromod update every entry
 * is stale. The per-jar entries carried version + self-hash headers already (checked on cache
 * hits), but stale files were never deleted, and the Hybrid engine's per-CLASS preload read the
 * directory with no validation at all, so an updated Retromod could keep serving a previous
 * build's transforms until the user manually deleted the cache (the old CLAUDE.md pitfall #4).
 *
 * <p>{@link #ensureCurrent} fixes that at the directory level: a {@code .cache-stamp} file
 * records {@code AOT_VERSION | self-hash} of the build that owns the cache. When the running
 * build's stamp differs (version bump, or ANY change to Retromod's own classes via the
 * self-hash), the whole cache directory is wiped and re-stamped. A missing stamp (caches
 * written by pre-stamp builds) also wipes, which is correct.
 *
 * <p>Residual caveat: on an unpackaged dev classpath (IDE / {@code mvn exec}) the verifier has
 * no jar to hash, so the stamp degrades to version-only and same-version dev iterations still
 * need a manual clear. Packaged builds (everything users run) always carry the self-hash.
 */
public final class AotCacheStamp {

    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-aot");

    private static final String STAMP_FILE = ".cache-stamp";

    /** Self-hash of the running Retromod jar; empty when unresolvable (dev classpath). */
    private static volatile String selfHashCache;

    private AotCacheStamp() {}

    static String currentSelfHash() {
        String s = selfHashCache;
        if (s != null) return s;
        try {
            com.retromod.security.SignatureVerifier.VerificationResult r =
                com.retromod.security.SignatureVerifier.verify();
            String h = r.selfHash();
            selfHashCache = (h != null ? h : "");
        } catch (Throwable t) {
            selfHashCache = "";
        }
        return selfHashCache;
    }

    private static String expectedStamp() {
        return AotCompiler.AOT_VERSION + "|" + currentSelfHash();
    }

    /**
     * Ensure {@code cacheDir} belongs to the running Retromod build: create it if absent,
     * and wipe + re-stamp it when the recorded generation differs. Never throws; a cache
     * problem must not break a launch.
     */
    public static void ensureCurrent(Path cacheDir) {
        ensureCurrent(cacheDir, expectedStamp());
    }

    /** Testable variant with an explicit stamp value. */
    static void ensureCurrent(Path cacheDir, String stamp) {
        try {
            Path stampFile = cacheDir.resolve(STAMP_FILE);
            if (Files.isDirectory(cacheDir)) {
                String recorded = Files.exists(stampFile)
                    ? Files.readString(stampFile, StandardCharsets.UTF_8).trim()
                    : null;
                if (stamp.equals(recorded)) {
                    return; // same build owns the cache
                }
                boolean empty;
                try (var s = Files.list(cacheDir)) {
                    empty = s.findFirst().isEmpty();
                }
                if (!empty) {
                    LOGGER.info("Retromod build changed since the AOT cache was written "
                            + "(recorded: {}), clearing {}",
                            recorded == null ? "no stamp" : recorded, cacheDir);
                    wipe(cacheDir);
                }
            }
            Files.createDirectories(cacheDir);
            Files.writeString(stampFile, stamp + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not validate AOT cache generation for {}: {}",
                    cacheDir, e.toString());
        }
    }

    /** Delete everything under {@code cacheDir}, including the directory itself. */
    private static void wipe(Path cacheDir) throws IOException {
        try (var paths = Files.walk(cacheDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete stale AOT cache entry: {}", p);
                }
            });
        }
    }
}
