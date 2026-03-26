/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.api.platform;

import java.util.ServiceLoader;

/**
 * Static facade for platform detection — the main class mod developers interact with.
 *
 * <h3>What is the Facade pattern?</h3>
 * <p>A facade provides a simplified interface to a complex subsystem. In this case, the
 * "complex subsystem" is the SPI-based backend discovery ({@link PlatformBackend} +
 * {@link java.util.ServiceLoader}). Mod developers don't need to know about ServiceLoader,
 * SPI interfaces, or backend implementations. They just call static methods on this class.</p>
 *
 * <h3>Why a facade instead of direct ServiceLoader calls?</h3>
 * <ul>
 *   <li><b>Simplicity:</b> {@code Platform.getLoader()} is much easier than setting up
 *       a ServiceLoader, iterating over providers, and handling the "no provider found" case.</li>
 *   <li><b>Caching:</b> The backend is loaded once and cached. Repeated calls are free.</li>
 *   <li><b>Safety:</b> If no backend is found (e.g., in unit tests), the facade returns
 *       sensible defaults instead of throwing exceptions.</li>
 *   <li><b>Encapsulation:</b> If the underlying discovery mechanism changes (from ServiceLoader
 *       to something else), this class absorbs the change. Mod developer code stays the same.</li>
 * </ul>
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * // Check which mod loader is running
 * Loader loader = Platform.getLoader();
 * if (loader == Loader.FABRIC) {
 *     System.out.println("Running on Fabric!");
 * }
 *
 * // Get the Minecraft version
 * String version = Platform.getMcVersion();
 * System.out.println("Minecraft " + version);
 *
 * // Guard client-only code
 * if (Platform.isClient()) {
 *     registerClientRenderers();
 * }
 *
 * // Enable debug features in dev environments
 * if (Platform.isDevelopmentEnvironment()) {
 *     enableVerboseLogging();
 * }
 * }</pre>
 *
 * @see PlatformBackend
 * @see Loader
 * @since 0.1.0
 */
public final class Platform {

    /*
     * The cached backend instance. Loaded lazily on first use via ServiceLoader.
     * Once loaded, this field never changes — the mod loader doesn't switch at runtime.
     *
     * We use volatile to ensure safe publication across threads. In Minecraft, mod
     * initialization can happen on different threads depending on the loader.
     */
    private static volatile PlatformBackend backend;

    /*
     * Private constructor prevents instantiation. This is a utility class —
     * all methods are static, and creating instances would be meaningless.
     */
    private Platform() {
        throw new AssertionError("Platform is a static utility class and cannot be instantiated");
    }

    /**
     * Returns the mod loader that is currently running.
     *
     * <p>The result is cached after the first call, so subsequent calls are essentially free.</p>
     *
     * @return the detected {@link Loader}, or {@link Loader#UNKNOWN} if no backend is available
     * @since 0.1.0
     */
    public static Loader getLoader() {
        return getBackend().getLoader();
    }

    /**
     * Returns the current Minecraft version string (e.g., {@code "26.1"}, {@code "1.21.4"}).
     *
     * <p>This is the runtime-detected version, equivalent to {@code RetroMod.TARGET_MC_VERSION}.
     * Never hardcode version strings — always use this method instead.</p>
     *
     * @return the Minecraft version, or {@code "unknown"} if detection fails
     * @since 0.1.0
     */
    public static String getMcVersion() {
        return getBackend().getMcVersion();
    }

    /**
     * Returns {@code true} if running on a client (the game with a GUI), as opposed
     * to a dedicated server.
     *
     * @return {@code true} for client, {@code false} for dedicated server
     * @since 0.1.0
     */
    public static boolean isClient() {
        return getBackend().isClient();
    }

    /**
     * Returns {@code true} if running in a development environment (IDE/build tool launch).
     *
     * @return {@code true} for dev environments, {@code false} for production
     * @since 0.1.0
     */
    public static boolean isDevelopmentEnvironment() {
        return getBackend().isDevelopmentEnvironment();
    }

    /**
     * Loads and caches the {@link PlatformBackend} implementation via ServiceLoader.
     *
     * <p>This method uses double-checked locking to ensure thread safety while avoiding
     * unnecessary synchronization on subsequent calls. The pattern works as follows:</p>
     * <ol>
     *   <li>First check: if {@code backend} is already set, return it immediately (no lock).</li>
     *   <li>If null, acquire the lock and check again (another thread may have initialized it
     *       between our first check and acquiring the lock).</li>
     *   <li>Use ServiceLoader to find the first available implementation.</li>
     *   <li>If no implementation is found, fall back to a no-op backend that returns safe defaults.</li>
     * </ol>
     *
     * @return the backend instance, never null
     */
    private static PlatformBackend getBackend() {
        // Fast path: already initialized (the volatile read ensures we see the fully
        // constructed object, not a partially initialized one)
        PlatformBackend b = backend;
        if (b != null) {
            return b;
        }

        // Slow path: first call, need to load via ServiceLoader
        synchronized (Platform.class) {
            // Double-check: another thread may have beaten us here
            b = backend;
            if (b != null) {
                return b;
            }

            /*
             * ServiceLoader.load() searches the classpath for a file named
             * META-INF/services/com.retromod.api.platform.PlatformBackend
             * and instantiates the class named in that file.
             *
             * We use findFirst() because we only expect one implementation at runtime.
             * If multiple exist (which shouldn't happen), we take the first one found.
             */
            b = ServiceLoader.load(PlatformBackend.class)
                    .findFirst()
                    .orElse(new FallbackBackend());

            backend = b;
            return b;
        }
    }

    /**
     * A no-op fallback backend used when no real implementation is found on the classpath.
     *
     * <p>This happens in environments where the core RetroMod module is not present —
     * for example, when running unit tests against the API module alone, or when a mod
     * bundles only the API JAR without the full RetroMod runtime.</p>
     *
     * <p>All methods return safe defaults that won't cause crashes.</p>
     */
    private static final class FallbackBackend implements PlatformBackend {
        @Override
        public Loader getLoader() {
            return Loader.UNKNOWN;
        }

        @Override
        public String getMcVersion() {
            return "unknown";
        }

        @Override
        public boolean isClient() {
            // Default to false (server) because server code is a subset of client code.
            // Returning true could cause code to call client-only APIs that don't exist
            // on a server, leading to crashes. Returning false is the safer default.
            return false;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            // Default to false — production behavior is the safer assumption.
            return false;
        }
    }
}
