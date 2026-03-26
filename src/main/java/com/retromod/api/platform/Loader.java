/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.api.platform;

/**
 * Represents the Minecraft mod loader that is currently running.
 *
 * <p>Minecraft has three major mod loaders, each with its own ecosystem, API conventions,
 * and mod format. RetroMod supports all three, but certain behaviors differ between them
 * (for example, Fabric uses intermediary names while NeoForge uses Mojang names). This
 * enum lets mod developers write loader-aware code without importing loader-specific classes.</p>
 *
 * <h3>Why does this exist?</h3>
 * <p>A mod that targets multiple loaders (a "multi-loader" mod) often needs to know which
 * loader is active at runtime — for example, to pick the right config directory or to
 * decide which APIs are available. Checking for loader classes via reflection everywhere
 * is tedious and error-prone. This enum centralizes that detection behind a clean API.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Check which loader is running
 * if (Platform.getLoader() == Loader.FABRIC) {
 *     // Fabric-specific behavior
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public enum Loader {

    /**
     * The Fabric mod loader ({@literal fabricmc.net}).
     *
     * <p>Fabric is a lightweight, modular mod loader that emerged around Minecraft 1.14.
     * It uses "intermediary" names for obfuscated Minecraft code, which means Fabric mods
     * need full name remapping when targeting MC 26.1+ (where Mojang removed all obfuscation).
     * Fabric is the strictest loader about mod version constraints — it rejects mods whose
     * {@code fabric.mod.json} declares an incompatible Minecraft version range.</p>
     */
    FABRIC,

    /**
     * The NeoForge mod loader ({@literal neoforged.net}).
     *
     * <p>NeoForge is the successor to Minecraft Forge, forked around MC 1.20.1. It already
     * uses Mojang official names (since 1.17), so NeoForge mods mainly need metadata
     * patching rather than full name remapping. Mod metadata lives in
     * {@code META-INF/neoforge.mods.toml} (or {@code META-INF/mods.toml} for older versions).</p>
     */
    NEOFORGE,

    /**
     * The original Minecraft Forge mod loader ({@literal minecraftforge.net}).
     *
     * <p>Forge has been the dominant mod loader since the early days of Minecraft modding
     * (around MC 1.2). It uses SRG (Searge) names for obfuscated code. For modern MC
     * versions Forge is being replaced by NeoForge, but many older mods still target it.
     * Mod metadata lives in {@code META-INF/mods.toml}.</p>
     */
    FORGE,

    /**
     * The mod loader could not be detected.
     *
     * <p>This value is returned when RetroMod is running in an environment where none of
     * the known loader classes can be found — for example, in unit tests, in the CLI tool,
     * or in a standalone Java application. Code that handles this case should provide
     * sensible defaults rather than crashing.</p>
     */
    UNKNOWN
}
