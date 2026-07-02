/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.aot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The AOT cache must belong to exactly one Retromod build. Users updated Retromod, and the
 * Hybrid engine's unvalidated per-class preload kept serving the PREVIOUS build's cached
 * transforms until the cache was deleted by hand (the old CLAUDE.md pitfall #4). The generation
 * stamp wipes the directory whenever the owning build changes.
 */
class AotCacheStampTest {

    @TempDir
    Path tmp;

    private Path cacheDir() {
        return tmp.resolve("aot-cache");
    }

    private void seedStaleEntries() throws IOException {
        Files.createDirectories(cacheDir().resolve("com/example"));
        Files.write(cacheDir().resolve("com/example/Old.class"), new byte[]{(byte) 0xCA});
        Files.write(cacheDir().resolve("somemod-aot.jar"), new byte[]{0x50, 0x4B});
    }

    @Test
    @DisplayName("a cache with NO stamp (pre-stamp builds) is wiped and re-stamped")
    void unstampedCacheIsWiped() throws IOException {
        seedStaleEntries();
        AotCacheStamp.ensureCurrent(cacheDir(), "1.2.0-snapshot.7|abc");
        assertFalse(Files.exists(cacheDir().resolve("com/example/Old.class")),
                "stale per-class entry must be gone (the Hybrid preload trusts it blindly)");
        assertFalse(Files.exists(cacheDir().resolve("somemod-aot.jar")),
                "stale per-jar entry must be gone");
        assertEquals("1.2.0-snapshot.7|abc",
                Files.readString(cacheDir().resolve(".cache-stamp")).trim());
    }

    @Test
    @DisplayName("a cache stamped by a DIFFERENT build is wiped")
    void differentBuildWipes() throws IOException {
        seedStaleEntries();
        Files.writeString(cacheDir().resolve(".cache-stamp"), "1.2.0-snapshot.6|oldhash\n");
        AotCacheStamp.ensureCurrent(cacheDir(), "1.2.0-snapshot.7|newhash");
        assertFalse(Files.exists(cacheDir().resolve("somemod-aot.jar")));
        assertEquals("1.2.0-snapshot.7|newhash",
                Files.readString(cacheDir().resolve(".cache-stamp")).trim());
    }

    @Test
    @DisplayName("same version but changed self-hash (rebuilt Retromod) also wipes")
    void selfHashChangeWipes() throws IOException {
        seedStaleEntries();
        Files.writeString(cacheDir().resolve(".cache-stamp"), "1.2.0-snapshot.7|hashA\n");
        AotCacheStamp.ensureCurrent(cacheDir(), "1.2.0-snapshot.7|hashB");
        assertFalse(Files.exists(cacheDir().resolve("somemod-aot.jar")),
                "same AOT_VERSION with different own-classes hash is a different build");
    }

    @Test
    @DisplayName("a cache stamped by the SAME build is kept intact")
    void sameBuildKeepsEntries() throws IOException {
        seedStaleEntries();
        Files.writeString(cacheDir().resolve(".cache-stamp"), "1.2.0-snapshot.7|abc\n");
        AotCacheStamp.ensureCurrent(cacheDir(), "1.2.0-snapshot.7|abc");
        assertTrue(Files.exists(cacheDir().resolve("somemod-aot.jar")),
                "valid cache entries must survive (that is the point of the cache)");
        assertTrue(Files.exists(cacheDir().resolve("com/example/Old.class")));
    }

    @Test
    @DisplayName("a missing cache directory is created and stamped")
    void missingDirIsCreated() {
        AotCacheStamp.ensureCurrent(cacheDir(), "1.2.0-snapshot.7|abc");
        assertTrue(Files.isDirectory(cacheDir()));
        assertTrue(Files.exists(cacheDir().resolve(".cache-stamp")));
    }

    @Test
    @DisplayName("the real entry point uses AOT_VERSION + self-hash and never throws")
    void realEntryPointStamps() {
        assertDoesNotThrow(() -> AotCacheStamp.ensureCurrent(cacheDir()));
        assertTrue(Files.exists(cacheDir().resolve(".cache-stamp")));
        assertDoesNotThrow(() -> AotCacheStamp.ensureCurrent(cacheDir()),
                "second call with the same build must be a no-op");
    }
}
