/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core.api;

import com.retromod.api.platform.Loader;
import com.retromod.api.platform.PlatformBackend;

/**
 * Concrete implementation of {@link PlatformBackend} that detects the runtime environment
 * using reflection.
 *
 * <h3>Why reflection?</h3>
 * <p>This class needs to detect which mod loader is running (Fabric, NeoForge, or Forge).
 * The obvious approach would be to import the loader's classes and call their APIs directly.
 * However, that creates a compile-time dependency — the code won't compile unless ALL THREE
 * loaders are on the classpath, which never happens in practice (a Minecraft instance runs
 * exactly one loader).</p>
 *
 * <p>Reflection solves this: we use {@link Class#forName(String)} to check if a
 * loader-specific class exists at runtime. If the class is found, we use
 * {@link java.lang.reflect.Method#invoke} to call its methods. If it's not found,
 * we catch the {@link ClassNotFoundException} and try the next loader.</p>
 *
 * <h3>Detection order</h3>
 * <p>We check loaders in this order:</p>
 * <ol>
 *   <li><b>Fabric</b> — check for {@code net.fabricmc.loader.api.FabricLoader}</li>
 *   <li><b>NeoForge</b> — check for {@code net.neoforged.fml.loading.FMLLoader}</li>
 *   <li><b>Forge</b> — check for {@code net.minecraftforge.versions.mcp.MCPVersion}</li>
 * </ol>
 * <p>This order matches what {@link com.retromod.core.RetroMod} does in its static
 * initializer (lines 38-70 of RetroMod.java).</p>
 *
 * <h3>Caching</h3>
 * <p>All detected values are cached in instance fields after first computation. The mod
 * loader, MC version, and environment type never change during a single JVM session, so
 * caching is both safe and important for performance (reflection is relatively slow).</p>
 *
 * <h3>ServiceLoader registration</h3>
 * <p>This class is registered in
 * {@code META-INF/services/com.retromod.api.platform.PlatformBackend} so that
 * {@link java.util.ServiceLoader} can discover it at runtime. The class MUST have a
 * public no-arg constructor (which Java provides by default).</p>
 *
 * @see PlatformBackend
 * @see com.retromod.api.platform.Platform
 * @since 0.1.0
 */
public class PlatformBackendImpl implements PlatformBackend {

    /*
     * Cached detection results. These are set once on first call and never change.
     *
     * We use volatile for thread-safe lazy initialization. In Minecraft, mod loading
     * can involve multiple threads, so we need to ensure that one thread's writes are
     * visible to other threads.
     */

    /** Cached loader detection result. Null means "not yet detected". */
    private volatile Loader cachedLoader;

    /** Cached MC version string. Null means "not yet detected". */
    private volatile String cachedMcVersion;

    /** Cached client detection result. Null means "not yet detected". */
    private volatile Boolean cachedIsClient;

    /** Cached dev environment detection result. Null means "not yet detected". */
    private volatile Boolean cachedIsDev;

    /**
     * Detects and returns the active mod loader.
     *
     * <p>The detection works by trying to load a class unique to each loader. Each loader
     * has a "signature" class that only exists when that loader is installed:</p>
     * <ul>
     *   <li>Fabric: {@code net.fabricmc.loader.api.FabricLoader}</li>
     *   <li>NeoForge: {@code net.neoforged.fml.loading.FMLLoader}</li>
     *   <li>Forge: {@code net.minecraftforge.versions.mcp.MCPVersion}</li>
     * </ul>
     *
     * @return the detected loader, or {@link Loader#UNKNOWN} if none is found
     */
    @Override
    public Loader getLoader() {
        // Return cached value if available (fast path)
        Loader loader = cachedLoader;
        if (loader != null) {
            return loader;
        }

        // Try each loader in order
        loader = detectLoader();
        cachedLoader = loader;
        return loader;
    }

    /**
     * Returns the current Minecraft version string.
     *
     * <p>Each loader has its own way of reporting the MC version:</p>
     * <ul>
     *   <li><b>Fabric:</b> Reads from the "minecraft" mod container's metadata via
     *       {@code FabricLoader.getInstance().getModContainer("minecraft")}</li>
     *   <li><b>NeoForge:</b> Calls {@code FMLLoader.versionInfo().mcVersion()}</li>
     *   <li><b>Forge:</b> Calls {@code MCPVersion.getMCVersion()}</li>
     * </ul>
     *
     * @return the MC version string (e.g., {@code "26.1"}), or {@code "unknown"} on failure
     */
    @Override
    public String getMcVersion() {
        String version = cachedMcVersion;
        if (version != null) {
            return version;
        }

        version = detectMcVersion();
        cachedMcVersion = version;
        return version;
    }

    /**
     * Detects whether this is a client environment by checking for the Minecraft main class.
     *
     * <p>The dedicated server uses {@code net.minecraft.server.Main} as its entry point,
     * while the client uses {@code net.minecraft.client.main.Main}. We check for the
     * client class — if it exists, we're on a client.</p>
     *
     * <p>Some loaders also provide their own client-detection API, which we prefer when
     * available because it's more reliable than raw class detection.</p>
     *
     * @return {@code true} if running on a client
     */
    @Override
    public boolean isClient() {
        Boolean client = cachedIsClient;
        if (client != null) {
            return client;
        }

        client = detectIsClient();
        cachedIsClient = client;
        return client;
    }

    /**
     * Detects whether the game is running in a development environment.
     *
     * <p>Each loader reports this differently:</p>
     * <ul>
     *   <li><b>Fabric:</b> {@code FabricLoader.getInstance().isDevelopmentEnvironment()}</li>
     *   <li><b>NeoForge:</b> {@code FMLLoader.isProduction()} (note: inverted — returns
     *       true for production, so we negate it)</li>
     *   <li><b>Forge:</b> Check system property {@code forge.development}</li>
     * </ul>
     *
     * @return {@code true} if in a development environment
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        Boolean dev = cachedIsDev;
        if (dev != null) {
            return dev;
        }

        dev = detectIsDev();
        cachedIsDev = dev;
        return dev;
    }

    // =========================================================================
    // Private detection methods
    // =========================================================================
    // Each method follows the same pattern:
    //   1. Try to load a loader-specific class via Class.forName()
    //   2. If found, use reflection to call the appropriate method
    //   3. If not found (ClassNotFoundException), try the next loader
    //   4. If all loaders fail, return a safe default
    // =========================================================================

    /**
     * Performs the actual loader detection by trying to load loader-specific classes.
     *
     * <p>We use {@link Class#forName(String)} rather than checking the classpath directly
     * because forName actually verifies the class can be loaded and initialized, not just
     * that a file with that name exists.</p>
     */
    private Loader detectLoader() {
        // --- Attempt 1: Fabric ---
        // Fabric's main API class. Present in every Fabric environment.
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return Loader.FABRIC;
        } catch (ClassNotFoundException ignored) {
            // Not Fabric — continue to next check
        }

        // --- Attempt 2: NeoForge ---
        // NeoForge's FML loader class. Present in every NeoForge environment.
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            return Loader.NEOFORGE;
        } catch (ClassNotFoundException ignored) {
            // Not NeoForge — continue to next check
        }

        // --- Attempt 3: Forge ---
        // Forge's MCP version class. Present in every Forge environment.
        try {
            Class.forName("net.minecraftforge.versions.mcp.MCPVersion");
            return Loader.FORGE;
        } catch (ClassNotFoundException ignored) {
            // Not Forge either
        }

        // None of the known loaders were found. This happens in unit tests,
        // the CLI tool, or any standalone Java environment.
        return Loader.UNKNOWN;
    }

    /**
     * Detects the Minecraft version from whichever loader is running.
     *
     * <p>This replicates the same logic as {@code RetroMod.java}'s static initializer
     * block. We intentionally duplicate it here rather than calling into RetroMod directly,
     * because the API should work independently of the RetroMod core module's initialization
     * order.</p>
     */
    private String detectMcVersion() {
        // --- Fabric version detection ---
        // Chain: FabricLoader.getInstance() -> getModContainer("minecraft") -> getMetadata()
        //        -> getVersion() -> getFriendlyString()
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");

            // Get the singleton FabricLoader instance
            Object instance = fabricLoaderClass.getMethod("getInstance").invoke(null);

            // Get the Optional<ModContainer> for the "minecraft" mod
            Object optional = fabricLoaderClass.getMethod("getModContainer", String.class)
                    .invoke(instance, "minecraft");

            // Check if the Optional has a value
            boolean present = (boolean) optional.getClass().getMethod("isPresent").invoke(optional);
            if (present) {
                // Unwrap the Optional to get the ModContainer
                Object modContainer = optional.getClass().getMethod("get").invoke(optional);

                // Get the ModMetadata from the container
                Object metadata = modContainer.getClass().getMethod("getMetadata").invoke(modContainer);

                // Get the Version object from the metadata
                Object version = metadata.getClass().getMethod("getVersion").invoke(metadata);

                // Get the human-readable version string (e.g., "26.1")
                String mcVersion = (String) version.getClass().getMethod("getFriendlyString")
                        .invoke(version);
                if (mcVersion != null) {
                    return mcVersion;
                }
            }
        } catch (Exception ignored) {
            // Not Fabric or detection failed — try next loader
        }

        // --- NeoForge version detection ---
        // Chain: FMLLoader.versionInfo() -> mcVersion()
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");

            // FMLLoader.versionInfo() returns a record with version information
            Object versionInfo = fmlLoader.getMethod("versionInfo").invoke(null);

            // The record has a mcVersion() accessor method
            String mcVersion = (String) versionInfo.getClass().getMethod("mcVersion")
                    .invoke(versionInfo);
            if (mcVersion != null) {
                return mcVersion;
            }
        } catch (Exception ignored) {
            // Not NeoForge or detection failed — try next loader
        }

        // --- Forge version detection ---
        // Direct static method: MCPVersion.getMCVersion()
        try {
            Class<?> mcpVersion = Class.forName("net.minecraftforge.versions.mcp.MCPVersion");
            String mcVersion = (String) mcpVersion.getMethod("getMCVersion").invoke(null);
            if (mcVersion != null) {
                return mcVersion;
            }
        } catch (Exception ignored) {
            // Not Forge or detection failed
        }

        // All detection attempts failed
        return "unknown";
    }

    /**
     * Detects whether this is a client environment.
     *
     * <p>We first try loader-specific APIs (more reliable), then fall back to checking
     * for the Minecraft client main class.</p>
     */
    private boolean detectIsClient() {
        // --- Fabric: use FabricLoader's environment type enum ---
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoaderClass.getMethod("getInstance").invoke(null);

            // getEnvironmentType() returns EnvType.CLIENT or EnvType.SERVER
            Object envType = fabricLoaderClass.getMethod("getEnvironmentType").invoke(instance);

            // Compare with the CLIENT enum constant
            return "CLIENT".equals(envType.toString());
        } catch (Exception ignored) {
            // Not Fabric — try generic detection
        }

        // --- NeoForge: use FMLLoader.getDist() ---
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");

            // getDist() returns Dist.CLIENT or Dist.DEDICATED_SERVER
            Object dist = fmlLoader.getMethod("getDist").invoke(null);
            return "CLIENT".equals(dist.toString());
        } catch (Exception ignored) {
            // Not NeoForge — try generic detection
        }

        // --- Generic fallback: check for the Minecraft client main class ---
        // The client has net.minecraft.client.main.Main; servers don't.
        try {
            Class.forName("net.minecraft.client.main.Main");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Detects whether the game is running in a development environment.
     */
    private boolean detectIsDev() {
        // --- Fabric: isDevelopmentEnvironment() API ---
        try {
            Class<?> fabricLoaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoaderClass.getMethod("getInstance").invoke(null);
            return (boolean) fabricLoaderClass.getMethod("isDevelopmentEnvironment")
                    .invoke(instance);
        } catch (Exception ignored) {
            // Not Fabric
        }

        // --- NeoForge: FMLLoader.isProduction() (note: inverted!) ---
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");

            // isProduction() returns true in production, false in dev.
            // We want the opposite, so we negate it.
            boolean isProduction = (boolean) fmlLoader.getMethod("isProduction").invoke(null);
            return !isProduction;
        } catch (Exception ignored) {
            // Not NeoForge
        }

        // --- Forge: check system property ---
        // Forge sets the "forge.development" system property in dev environments.
        String forgeDev = System.getProperty("forge.development");
        if (forgeDev != null) {
            return Boolean.parseBoolean(forgeDev);
        }

        // Default to false (assume production)
        return false;
    }
}
