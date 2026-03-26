/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.api;

import com.retromod.api.platform.Loader;
import com.retromod.api.platform.Platform;
import com.retromod.api.text.TextFactory;

/**
 * Entry point and version constants for the RetroMod API.
 *
 * <h3>What is the RetroMod API?</h3>
 * <p>The RetroMod API provides a stable, version-independent interface for Minecraft mod
 * developers. Instead of writing code that only works on one MC version or one mod loader,
 * developers can use this API to write code that works across all supported versions and
 * loaders (Fabric, NeoForge, Forge).</p>
 *
 * <h3>How do mod developers use it?</h3>
 * <p>Mod developers add the RetroMod API as a dependency in their build file, then use
 * the facade classes for common operations:</p>
 * <ul>
 *   <li>{@link Platform} — Detect which mod loader and MC version are running</li>
 *   <li>{@link TextFactory} — Create text components without worrying about API changes</li>
 * </ul>
 *
 * <h3>Architecture: API vs. Core</h3>
 * <p>The API is split into two layers:</p>
 * <ol>
 *   <li><b>API classes</b> ({@code com.retromod.api.*}): Pure Java interfaces and facades
 *       with NO imports from Minecraft or mod loader classes. These are what mod developers
 *       compile against.</li>
 *   <li><b>Backend implementations</b> ({@code com.retromod.core.api.*}): Concrete classes
 *       that implement the SPI interfaces using reflection to interact with MC and loader
 *       classes. These ship inside the main RetroMod JAR.</li>
 * </ol>
 *
 * <h3>What is SPI (Service Provider Interface)?</h3>
 * <p>SPI is a Java mechanism for decoupling an interface from its implementation. The API
 * defines interfaces (like {@link com.retromod.api.platform.PlatformBackend}), and the
 * core module provides implementations. At runtime, Java's {@link java.util.ServiceLoader}
 * discovers implementations by reading files in {@code META-INF/services/}. This means
 * the API module never needs to know which concrete class implements the interface — it's
 * all wired up automatically.</p>
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * // Check API version
 * System.out.println("RetroMod API " + RetroModApi.API_VERSION);
 *
 * // Platform detection
 * if (Platform.getLoader() == Loader.FABRIC) {
 *     System.out.println("Running on Fabric, MC " + Platform.getMcVersion());
 * }
 *
 * // Create text components (works on any MC version)
 * Object greeting = TextFactory.literal("Hello from RetroMod!");
 * Object translated = TextFactory.translatable("retromod.welcome");
 * }</pre>
 *
 * @see Platform
 * @see TextFactory
 * @since 0.1.0
 */
public final class RetroModApi {

    /**
     * The current version of the RetroMod API.
     *
     * <p>This follows <a href="https://semver.org/">Semantic Versioning</a>:</p>
     * <ul>
     *   <li><b>Major</b> (0): Pre-release. The API is not yet stable and may change.</li>
     *   <li><b>Minor</b> (1): First feature set — Platform detection and TextFactory.</li>
     *   <li><b>Patch</b> (0): No bug fixes yet.</li>
     * </ul>
     *
     * <p>Once the API reaches version 1.0.0, breaking changes will only occur in major
     * version bumps, giving mod developers confidence that their code won't break.</p>
     *
     * @since 0.1.0
     */
    public static final String API_VERSION = "0.1.0";

    /*
     * Private constructor prevents instantiation. This class only holds constants
     * and static utility methods.
     */
    private RetroModApi() {
        throw new AssertionError("RetroModApi is a static utility class and cannot be instantiated");
    }

    /**
     * Returns {@code true} if the RetroMod API has a backend implementation available.
     *
     * <p>When this returns {@code true}, all API features (Platform, TextFactory, etc.)
     * are fully functional with real Minecraft integration. When {@code false}, the API
     * is operating in fallback mode — methods still work but return degraded results
     * (e.g., {@code Platform.getLoader()} returns {@link Loader#UNKNOWN}).</p>
     *
     * <p>Mod developers can use this to verify that RetroMod is properly installed:</p>
     * <pre>{@code
     * if (!RetroModApi.isBackendAvailable()) {
     *     logger.warn("RetroMod runtime not found — some features may not work");
     * }
     * }</pre>
     *
     * @return {@code true} if a real backend is available, {@code false} if using fallbacks
     * @since 0.1.0
     */
    public static boolean isBackendAvailable() {
        // If the platform backend can detect a real loader, the full runtime is present.
        // The fallback backend always returns UNKNOWN, so this is a reliable check.
        return Platform.getLoader() != Loader.UNKNOWN;
    }
}
