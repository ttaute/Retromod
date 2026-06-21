/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

/**
 * Loader-agnostic holder for the runtime-detected target MC version.
 *
 * <p>Used to live as a {@code public static String} on {@link Retromod},
 * but {@code Retromod implements net.fabricmc.api.ModInitializer} - a Fabric-only
 * supertype. Reading {@code Retromod.TARGET_MC_VERSION} from {@code RetromodForge}
 * (or {@code RetromodNeoForge}) on a Forge runtime triggered class linkage of
 * {@code Retromod}, which then needed {@code ModInitializer} on the classpath,
 * which doesn't exist there. Forge then crashed with:
 *
 * <pre>
 *   java.lang.NoClassDefFoundError: net/fabricmc/api/ModInitializer
 *     at RetromodForge.initializeHybridEngine
 * </pre>
 *
 * <p>Putting the constant on a class with no Fabric / Forge / NeoForge supertype
 * makes it safe to read from any loader's entry point.
 *
 * <p>The value is mutable because it's set by the loader-specific entry points
 * after they auto-detect the running MC version. Default is a sensible fallback
 * for the case where detection fails.
 */
public final class RetromodVersion {

    /**
     * The MC version Retromod is translating mods <i>to</i>. Auto-detected
     * from the running mod loader by whichever entry point boots first
     * ({@link Retromod#onInitialize()} on Fabric, {@code RetromodForge.<init>()}
     * on Forge, {@code RetromodNeoForge.<init>()} on NeoForge).
     */
    public static volatile String TARGET_MC_VERSION = "1.21.4";

    private RetromodVersion() {}

    /** Guards {@link #logPresenceBanner}: every loader entry point calls it, but it prints once. */
    private static volatile boolean bannerLogged = false;

    /**
     * Log a prominent "Retromod is present" banner near the top of the log on every loader, so
     * anyone reading a log or crash report knows Retromod is transforming mods on this instance
     * and should reproduce WITHOUT Retromod before blaming a transformed mod's author. Called
     * from each entry point ({@link Retromod#onInitialize} on Fabric, the NeoForge/Forge
     * constructors, and {@code RetromodPreLaunch}); the guard makes it print exactly once.
     */
    public static void logPresenceBanner(org.slf4j.Logger logger) {
        if (bannerLogged) {
            return;
        }
        bannerLogged = true;
        logger.warn("************************************************************************");
        logger.warn("* RETROMOD IS PRESENT - it is transforming older mods to run on this");
        logger.warn("* newer Minecraft version (bytecode transformation + API shims).");
        logger.warn("* If ANY mod misbehaves, reproduce WITHOUT Retromod before reporting");
        logger.warn("* to that mod's author: the cause may be Retromod's transform, not the");
        logger.warn("* mod itself.  Retromod issues -> https://github.com/Bownlux/Retromod/issues");
        logger.warn("************************************************************************");
    }

    // ── MC version math ─────────────────────────────────────────────────────
    // Loader-agnostic and free of any Fabric/Forge/NeoForge supertype, so any
    // entry point can call these. They used to live on RetromodPreLaunch, but
    // that class `implements PreLaunchEntrypoint` (Fabric-only) - so
    // RetromodNeoForge/RetromodForge calling them dragged in PreLaunchEntrypoint
    // and crashed with NoClassDefFoundError on NeoForge/Forge even with no mods
    // (issue #40). Same lesson as TARGET_MC_VERSION above.

    /**
     * Whether the host MC version is 26.1+ (the first unobfuscated release, where
     * Fabric switched from intermediary to Mojang names). Gates the
     * intermediary→Mojang remap and 26.1 class moves: applying those on a pre-26.1
     * host rewrites a mod's working references to names that don't exist yet.
     * Unparseable/unknown → {@code true} (preserve published 26.1 behavior).
     */
    public static boolean isUnobfuscatedTarget(String hostVersion) {
        if (hostVersion == null) return true;
        try {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)").matcher(hostVersion.trim());
            if (!m.find()) return true;
            return Integer.parseInt(m.group(1)) >= 26;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Whether MC version {@code a} is strictly newer than {@code b}. Used to skip
     * version shims whose target is newer than the host. Unparseable → {@code false}
     * (don't skip) so we never over-exclude.
     */
    public static boolean mcVersionExceeds(String a, String b) {
        try {
            return compareMcVersions(a, b) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Numeric per-component MC version compare ({@code 1.21.8} vs {@code 26.1} → negative). */
    public static int compareMcVersions(String a, String b) {
        int[] pa = parseMcVersion(a);
        int[] pb = parseMcVersion(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /**
     * Whether {@code a} and {@code b} share the same {@code major.minor} (e.g. {@code 1.21}
     * and {@code 1.21.1}, or {@code 26.1} and {@code 26.1.2}). Used by the automatic
     * in-place mod scan to skip mods that only differ from the host by a <i>patch</i>:
     * those are generally drop-in compatible, so transforming them is unnecessary churn
     * and - worse - can break a working mod by firing API shims it never needed (#60). A
     * mod that genuinely needs a within-minor transform can still be placed in
     * {@code retromod-input/} explicitly. Unparseable (fewer than 2 components) → {@code false}
     * (don't skip), so we never silently miss a real cross-version transform.
     */
    public static boolean sameMinorVersion(String a, String b) {
        int[] pa = parseMcVersion(a);
        int[] pb = parseMcVersion(b);
        if (pa.length < 2 || pb.length < 2) return false;
        return pa[0] == pb[0] && pa[1] == pb[1];
    }

    private static int[] parseMcVersion(String v) {
        if (v == null) return new int[0];
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)*)").matcher(v.trim());
        if (!m.find()) return new int[0];
        String[] parts = m.group(1).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    // ── FancyModLoader host-version detection (NeoForge + new Forge) ─────────
    // Reflection-only (string class names), so this stays loader-neutral and
    // doesn't drag any FML class into RetromodVersion's linkage (the #40 lesson).
    // The accessor changed shape across FML generations; the old code only tried
    // the static {@code versionInfo()} form, which NoSuchMethodException'd on FML
    // 10.x (NeoForge 21.11) and silently fell back to the hardcoded default -
    // gating out every newer-MC shim and silently mistranslating mods
    // (#47/#51/#52, the ResourceLocation→Identifier rename never applied).

    /**
     * Read the host MC version from FancyModLoader across API generations, or
     * {@code null} if none work (caller should log loudly, not silently fall
     * back to the default). Tries, in order:
     * <ul>
     *   <li>FML 10.x: {@code FMLLoader.getCurrent().getVersionInfo().mcVersion()}</li>
     *   <li>older FML: static {@code FMLLoader.versionInfo().mcVersion()}</li>
     *   <li>variant: static {@code FMLLoader.getVersionInfo().mcVersion()}</li>
     * </ul>
     */
    public static String detectFmlMcVersion() {
        final Class<?> fmlLoader;
        try {
            fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
        } catch (Throwable t) {
            return null;
        }
        // FML 10.x - instance API reached via the static getCurrent()
        try {
            Object current = fmlLoader.getMethod("getCurrent").invoke(null);
            if (current != null) {
                String mc = mcVersionOf(fmlLoader.getMethod("getVersionInfo").invoke(current));
                if (mc != null) return mc;
            }
        } catch (Throwable ignored) {}
        // Older FML - static versionInfo()
        try {
            String mc = mcVersionOf(fmlLoader.getMethod("versionInfo").invoke(null));
            if (mc != null) return mc;
        } catch (Throwable ignored) {}
        // Variant - static getVersionInfo()
        try {
            String mc = mcVersionOf(fmlLoader.getMethod("getVersionInfo").invoke(null));
            if (mc != null) return mc;
        } catch (Throwable ignored) {}
        return null;
    }

    /** Pull {@code mcVersion()} off a VersionInfo-like object, or {@code null}. */
    private static String mcVersionOf(Object versionInfo) {
        if (versionInfo == null) return null;
        try {
            Object mc = versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo);
            if (mc instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}
        return null;
    }
}
