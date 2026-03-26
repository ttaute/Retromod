/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.api.platform;

/**
 * SPI (Service Provider Interface) for platform detection.
 *
 * <h3>What is an SPI?</h3>
 * <p>An SPI is a design pattern where an interface defines a contract, and implementations
 * are discovered at runtime rather than being hard-coded. Java's {@link java.util.ServiceLoader}
 * mechanism is used for this discovery: you place a file in
 * {@code META-INF/services/com.retromod.api.platform.PlatformBackend} that contains the
 * fully-qualified class name of the implementation.</p>
 *
 * <h3>Why use SPI here?</h3>
 * <p>The API module ({@code com.retromod.api.*}) must have ZERO imports from Minecraft or
 * mod loader classes. This is essential because:</p>
 * <ul>
 *   <li>The API might be compiled separately from the main RetroMod JAR</li>
 *   <li>Mod developers should be able to depend on the API without pulling in MC classes</li>
 *   <li>Different environments (Fabric, NeoForge, CLI) need different implementations</li>
 * </ul>
 *
 * <p>By using SPI, the API defines <em>what</em> it needs (this interface), and the core
 * module provides <em>how</em> (the implementation in
 * {@code com.retromod.core.api.PlatformBackendImpl}). At runtime, {@link java.util.ServiceLoader}
 * finds the implementation automatically.</p>
 *
 * <h3>How ServiceLoader discovery works</h3>
 * <ol>
 *   <li>At startup, code calls {@code ServiceLoader.load(PlatformBackend.class)}</li>
 *   <li>ServiceLoader looks for a file at
 *       {@code META-INF/services/com.retromod.api.platform.PlatformBackend} on the classpath</li>
 *   <li>That file contains one line: {@code com.retromod.core.api.PlatformBackendImpl}</li>
 *   <li>ServiceLoader instantiates that class using its no-arg constructor</li>
 *   <li>The API caches this instance and delegates all calls to it</li>
 * </ol>
 *
 * <p>Mod developers never interact with this interface directly. Instead, they use the
 * {@link Platform} facade class, which calls through to whichever backend ServiceLoader
 * discovered.</p>
 *
 * @see Platform
 * @see java.util.ServiceLoader
 * @since 0.1.0
 */
public interface PlatformBackend {

    /**
     * Returns the mod loader that is currently running.
     *
     * <p>Implementations detect the loader by checking for loader-specific classes on the
     * classpath using reflection. For example, the presence of
     * {@code net.fabricmc.loader.api.FabricLoader} indicates Fabric.</p>
     *
     * @return the detected {@link Loader}, or {@link Loader#UNKNOWN} if detection fails
     * @since 0.1.0
     */
    Loader getLoader();

    /**
     * Returns the current Minecraft version string.
     *
     * <p>The version string uses Mojang's official format — for example {@code "1.21.4"},
     * {@code "26.1"}, etc. This is the same value that {@code RetroMod.TARGET_MC_VERSION}
     * holds at runtime.</p>
     *
     * <p>Implementations extract this from the mod loader's own version-reporting API
     * (e.g., Fabric's {@code ModContainer} metadata, NeoForge's {@code FMLLoader.versionInfo()},
     * or Forge's {@code MCPVersion.getMCVersion()}).</p>
     *
     * @return the Minecraft version string, or {@code "unknown"} if detection fails
     * @since 0.1.0
     */
    String getMcVersion();

    /**
     * Returns {@code true} if this is a client-side environment (i.e., the game with a GUI),
     * as opposed to a dedicated server.
     *
     * <p>This distinction matters because some APIs (like rendering, GUI components, and
     * input handling) only exist on the client. Mods that register client-only features
     * need to guard those registrations behind an {@code isClient()} check to avoid
     * crashes on dedicated servers.</p>
     *
     * @return {@code true} if running on a client, {@code false} if on a dedicated server
     * @since 0.1.0
     */
    boolean isClient();

    /**
     * Returns {@code true} if the game is running in a development environment.
     *
     * <p>A development environment is one where the game was launched from an IDE or build
     * tool (Gradle/Maven) rather than from the official Minecraft launcher. In dev
     * environments, classes are typically not obfuscated, and additional debugging features
     * may be available.</p>
     *
     * <p>This is useful for enabling verbose logging, debug overlays, or extra validation
     * that should not appear in production.</p>
     *
     * @return {@code true} if in a dev environment, {@code false} otherwise
     * @since 0.1.0
     */
    boolean isDevelopmentEnvironment();
}
