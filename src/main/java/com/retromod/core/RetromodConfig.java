/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loader-agnostic Retromod configuration helper.
 *
 * <h2>Why this exists</h2>
 * The default {@code config/retromod/config.json} used to be generated only by
 * {@code Retromod} (the Fabric {@code ModInitializer}); the Forge and NeoForge
 * entry points create {@code retromod-input/} but never wrote the config, so a
 * Forge/NeoForge user saw an empty {@code config/retromod/} (just the AOT-cache
 * folder) and no editable config (#74). The generation logic lives here as
 * plain {@code static} methods on a class with <b>no loader supertype</b>, so
 * all three entry points can call it without dragging another loader's classes
 * in (the loader-isolation rule, CLAUDE.md pitfall #8 - same reason
 * {@code RetromodVersion} is loader-agnostic).
 */
public final class RetromodConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Config");

    /** {@code config/retromod} relative to the game directory. */
    public static final Path CONFIG_DIR = Path.of("config", "retromod");
    /** {@code config/retromod/config.json}. */
    public static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");

    /**
     * The default config written when none exists. Kept here (not in a loader
     * entry point) so every loader writes the identical file.
     */
    private static final String DEFAULT_CONFIG = """
            {
              "_comment": "Retromod Configuration - https://github.com/Bownlux/Retromod",

              "use_aot": true,
              "use_hybrid": true,
              "instruction_level_granularity": true,

              "transform_mixins": true,
              "transform_refmaps": true,

              "remap_reflection": true,

              "log_level": "INFO",
              "log_transformations": false,

              "target_mc_version": "auto",

              "debug": false,
              "dump_bytecode": false,

              "force_translate_complex": false,

              "polyfills_enabled": true,

              "verify_transforms": true,

              "_network_comment": "Retromod never initiates network downloads without user consent. The flag below is OFF by default - flip it to true to enable the optional Modrinth lookup (queries Modrinth's public API to suggest native versions of mods you're transforming). No JAR files are ever auto-downloaded; the CLI's 'archive download' command is the only path that fetches files, and it prompts before each download.",
              "check_for_native_versions": false
            }
            """;

    private RetromodConfig() {}

    /**
     * Ensure {@code config/retromod/} exists and contains a default
     * {@code config.json}. Idempotent and best-effort: never throws, so a
     * failure here can't break mod loading. Safe to call from any loader's
     * entry point at startup.
     */
    public static void ensureDefaultConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (Exception e) {
            LOGGER.warn("Could not create config directory {}: {}", CONFIG_DIR, e.getMessage());
            return;
        }
        if (Files.exists(CONFIG_PATH)) {
            return;
        }
        try {
            Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
            LOGGER.info("Generated default config at {}", CONFIG_PATH);
        } catch (Exception e) {
            LOGGER.warn("Could not generate default config at {}: {}", CONFIG_PATH, e.getMessage());
        }
    }

    /**
     * Load the config as a {@link JsonObject}, generating the default first if
     * it's missing. Returns {@code null} if the file can't be read or parsed
     * (callers fall back to their hard-coded defaults).
     */
    public static JsonObject loadOrNull() {
        ensureDefaultConfig();
        try {
            if (!Files.exists(CONFIG_PATH)) return null;
            String json = Files.readString(CONFIG_PATH);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("Could not load config {}: {}", CONFIG_PATH, e.getMessage());
            return null;
        }
    }
}
