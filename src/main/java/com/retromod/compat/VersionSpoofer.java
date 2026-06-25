/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reports a faked dependency version to mods that do their own in-code version check.
 *
 * <p>Relaxing {@code "depends"} metadata stops Fabric Loader from blocking mods at
 * load time, but some mods (REI, old tech mods) also query the loader at init for an
 * installed dependency version and compare it against a hard-coded range. If the
 * installed version is newer than the range they pop a "your X is unsupported" error
 * and refuse to work. REI 14 on Cloth Config 15 reads
 * {@code getModContainer("cloth-config").get().getMetadata().getVersion().getFriendlyString()},
 * sees {@code "15.0.140"}, and bails.
 *
 * <p>The transformer redirects {@code FabricLoader.getModContainer(modId)} in
 * transformed mods to {@link #getModContainer(Object, String)}. Mods in the spoof
 * table get a {@link Proxy}-wrapped container whose version chain returns the spoofed
 * string; everything else, and every non-version accessor, passes through to the real
 * objects so authors/description/dependencies stay accurate.
 *
 * <p>The rules live in {@code /retromod/version-spoofs.json}, each spoofed version
 * picked to satisfy the widest practical range for that mod family (Cloth Config maps
 * to {@code "13.999.999"}, which clears REI 14's {@code ">=13.0.0 <14.0.0"} and most
 * other "cloth-config 13-something" checks).
 *
 * <p>Parameters are typed {@code Object} rather than the Fabric interfaces: Retromod
 * also runs in the CLI without Fabric Loader on the classpath, so the spoofer reaches
 * the Fabric types reflectively and no-ops when they are absent.
 */
public final class VersionSpoofer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-VersionSpoofer");

    private static final String SPOOF_RESOURCE = "/retromod/version-spoofs.json";

    /** Default: ON. Disable via {@code -Dretromod.spoofVersions=false} for debugging. */
    private static final boolean SPOOF_ENABLED =
            Boolean.parseBoolean(System.getProperty("retromod.spoofVersions", "true"));

    /** {@code modId -> spoofedVersionString}, loaded lazily on first use. */
    private static volatile Map<String, String> spoofTable;

    private static final java.util.concurrent.atomic.AtomicInteger spoofsApplied =
            new java.util.concurrent.atomic.AtomicInteger();

    private VersionSpoofer() {}

    /**
     * Replacement for {@code FabricLoader.getModContainer(String)}, called from
     * transformed mod bytecode. {@code fabricLoader} is the receiver (a Fabric
     * {@code FabricLoader} instance), typed {@code Object} to build without a Fabric dep.
     *
     * @return real or proxy-wrapped {@code Optional<ModContainer>}
     */
    public static Optional<?> getModContainer(Object fabricLoader, String modId) {
        // Empty on reflection failure matches Fabric's behaviour for a missing mod.
        Optional<?> real;
        try {
            Method m = fabricLoader.getClass().getMethod("getModContainer", String.class);
            Object result = m.invoke(fabricLoader, modId);
            real = (result instanceof Optional<?>) ? (Optional<?>) result : Optional.empty();
        } catch (Throwable t) {
            LOGGER.debug("Spoofer passthrough failed for {}: {}", modId, t.getMessage());
            return Optional.empty();
        }

        if (!SPOOF_ENABLED) return real;

        String spoofedVersion = getSpoofTable().get(modId);
        if (spoofedVersion == null) return real;
        if (!real.isPresent()) return real;               // mod not installed, can't fake it

        try {
            Object wrapped = wrapContainer(real.get(), spoofedVersion);
            spoofsApplied.incrementAndGet();
            return Optional.of(wrapped);
        } catch (Throwable t) {
            // Spoofing is best-effort; fall back to the real container if the proxy fails.
            LOGGER.debug("Could not build spoof proxy for {} ({}): {}",
                    modId, spoofedVersion, t.getMessage());
            return real;
        }
    }

    /** Wrap a {@code ModContainer} so {@code getMetadata()} returns a spoofing proxy. */
    private static Object wrapContainer(Object realContainer, String spoofedVersion)
            throws ClassNotFoundException {
        Class<?> containerIface = Class.forName("net.fabricmc.loader.api.ModContainer");
        return Proxy.newProxyInstance(
                containerIface.getClassLoader(),
                new Class<?>[]{containerIface},
                new ContainerHandler(realContainer, spoofedVersion));
    }

    /** Wrap a {@code ModMetadata} so {@code getVersion()} returns a spoofing proxy; other accessors pass through. */
    private static Object wrapMetadata(Object realMetadata, String spoofedVersion)
            throws ClassNotFoundException {
        Class<?> metaIface = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");
        return Proxy.newProxyInstance(
                metaIface.getClassLoader(),
                new Class<?>[]{metaIface},
                new MetadataHandler(realMetadata, spoofedVersion));
    }

    /**
     * Wrap a {@code Version} so {@code getFriendlyString()}/{@code toString()} return the
     * spoofed string and {@code compareTo} reports 0, letting SemVer range checks accept us.
     */
    private static Object wrapVersion(Object realVersion, String spoofedVersion)
            throws ClassNotFoundException {
        Class<?> versionIface = Class.forName("net.fabricmc.loader.api.Version");
        return Proxy.newProxyInstance(
                versionIface.getClassLoader(),
                new Class<?>[]{versionIface},
                new VersionHandler(realVersion, spoofedVersion));
    }

    private static final class ContainerHandler implements InvocationHandler {
        private final Object real;
        private final String spoofVersion;
        ContainerHandler(Object real, String spoofVersion) {
            this.real = real; this.spoofVersion = spoofVersion;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getMetadata".equals(method.getName()) && method.getParameterCount() == 0) {
                try {
                    Object meta = method.invoke(real);
                    return meta == null ? null : wrapMetadata(meta, spoofVersion);
                } catch (Throwable t) {
                    return method.invoke(real, args);
                }
            }
            return method.invoke(real, args);
        }
    }

    private static final class MetadataHandler implements InvocationHandler {
        private final Object real;
        private final String spoofVersion;
        MetadataHandler(Object real, String spoofVersion) {
            this.real = real; this.spoofVersion = spoofVersion;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getVersion".equals(method.getName()) && method.getParameterCount() == 0) {
                try {
                    Object ver = method.invoke(real);
                    return ver == null ? null : wrapVersion(ver, spoofVersion);
                } catch (Throwable t) {
                    return method.invoke(real, args);
                }
            }
            return method.invoke(real, args);
        }
    }

    private static final class VersionHandler implements InvocationHandler {
        private final Object real;
        private final String spoofVersion;
        VersionHandler(Object real, String spoofVersion) {
            this.real = real; this.spoofVersion = spoofVersion;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (("getFriendlyString".equals(name) || "toString".equals(name))
                    && method.getParameterCount() == 0) {
                return spoofVersion;
            }
            // Claim equal so SemVer range checks built on compareTo accept us.
            if ("compareTo".equals(name) && method.getParameterCount() == 1) {
                return 0;
            }
            return method.invoke(real, args);
        }
    }

    /**
     * Lazy-load the spoof table from the bundled JSON resource. A missing or malformed
     * resource leaves the table empty, so every call passes through to the real container.
     */
    private static Map<String, String> getSpoofTable() {
        Map<String, String> local = spoofTable;
        if (local != null) return local;
        synchronized (VersionSpoofer.class) {
            if (spoofTable != null) return spoofTable;
            spoofTable = Collections.unmodifiableMap(loadTable());
            LOGGER.info("VersionSpoofer loaded {} spoof rule(s) from {}",
                    spoofTable.size(), SPOOF_RESOURCE);
            return spoofTable;
        }
    }

    private static Map<String, String> loadTable() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = VersionSpoofer.class.getResourceAsStream(SPOOF_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("{} not found - spoofer will passthrough every call", SPOOF_RESOURCE);
                return out;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                // Schema: { "spoofs": { "cloth-config": "13.999.999", ... } }
                if (root.has("spoofs")) {
                    JsonObject spoofs = root.getAsJsonObject("spoofs");
                    for (Map.Entry<String, JsonElement> e : spoofs.entrySet()) {
                        out.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not load spoof table: {}", e.getMessage());
        }
        return out;
    }

    /** Number of spoofed containers returned so far (diagnostics). */
    public static int getSpoofsApplied() {
        return spoofsApplied.get();
    }

    /** Test hook: drop the cached table so a test can re-populate it. */
    static void resetForTesting() {
        spoofTable = null;
        spoofsApplied.set(0);
    }

    /** Test hook: install a rule map directly, bypassing the JSON load. */
    static void setSpoofTableForTesting(Map<String, String> table) {
        spoofTable = Collections.unmodifiableMap(new HashMap<>(table));
    }
}
