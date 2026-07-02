/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #79 (snapshot.7): a pre-1.13 Forge mod ships only {@code mcmod.info}; modern Forge/NeoForge needs
 * a {@code mods.toml} or it never scans the mod, so the class-move + ctor bridges never run. This
 * verifies {@code ForgeModTransformer.generateTomlFromMcmodInfo} synthesizes a valid toml.
 */
class McmodInfoTomlGenTest {

    @Test
    void generatesModsTomlFromMcmodInfo(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("META-INF"));
        Files.writeString(dir.resolve("mcmod.info"),
                "[{\"modid\": \"naturescompass\", \"name\": \"Nature's Compass\", "
                        + "\"version\": \"1.8.5\", \"description\": \"Locate biomes.\", "
                        + "\"mcversion\": \"1.12.2\"}]");

        new ForgeModTransformer("26.1").generateTomlFromMcmodInfo(dir);

        Path toml = dir.resolve("META-INF/mods.toml");
        assertTrue(Files.exists(toml), "a mods.toml should be synthesized from mcmod.info");
        String c = Files.readString(toml);
        assertTrue(c.contains("modLoader=\"javafml\""), "valid modLoader: " + c);
        assertTrue(c.contains("modId=\"naturescompass\""), "carries the mod id: " + c);
        assertTrue(c.contains("version=\"1.8.5\""), "carries the version: " + c);
        assertTrue(c.contains("[[dependencies.naturescompass]]"), "has a dependencies block: " + c);
        assertTrue(c.contains("modId=\"minecraft\""), "depends on minecraft: " + c);
        assertTrue(c.contains("versionRange=\"[1,)\""), "minecraft range relaxed: " + c);
    }

    @Test
    void noopWhenModsTomlAlreadyPresent(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("META-INF"));
        Files.writeString(dir.resolve("mcmod.info"), "[{\"modid\":\"x\",\"version\":\"1\"}]");
        Files.writeString(dir.resolve("META-INF/mods.toml"), "modLoader=\"javafml\"\n# original");

        new ForgeModTransformer("26.1").generateTomlFromMcmodInfo(dir);

        // an existing toml must be left untouched (a 1.13+ mod owns its metadata)
        assertTrue(Files.readString(dir.resolve("META-INF/mods.toml")).contains("# original"),
                "existing mods.toml must not be overwritten");
    }

    @Test
    void noopWhenNoMcmodInfo(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("META-INF"));
        new ForgeModTransformer("26.1").generateTomlFromMcmodInfo(dir);
        assertFalse(Files.exists(dir.resolve("META-INF/mods.toml")), "nothing to generate without mcmod.info");
    }
}
