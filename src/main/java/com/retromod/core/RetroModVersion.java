/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

/**
 * Loader-agnostic holder for the runtime-detected target MC version.
 *
 * <p>Used to live as a {@code public static String} on {@link RetroMod},
 * but {@code RetroMod implements net.fabricmc.api.ModInitializer} — a Fabric-only
 * supertype. Reading {@code RetroMod.TARGET_MC_VERSION} from {@code RetroModForge}
 * (or {@code RetroModNeoForge}) on a Forge runtime triggered class linkage of
 * {@code RetroMod}, which then needed {@code ModInitializer} on the classpath,
 * which doesn't exist there. Forge then crashed with:
 *
 * <pre>
 *   java.lang.NoClassDefFoundError: net/fabricmc/api/ModInitializer
 *     at RetroModForge.initializeHybridEngine
 * </pre>
 *
 * <p>Putting the constant on a class with no Fabric / Forge / NeoForge supertype
 * makes it safe to read from any loader's entry point.
 *
 * <p>The value is mutable because it's set by the loader-specific entry points
 * after they auto-detect the running MC version. Default is a sensible fallback
 * for the case where detection fails.
 */
public final class RetroModVersion {

    /**
     * The MC version RetroMod is translating mods <i>to</i>. Auto-detected
     * from the running mod loader by whichever entry point boots first
     * ({@link RetroMod#onInitialize()} on Fabric, {@code RetroModForge.<init>()}
     * on Forge, {@code RetroModNeoForge.<init>()} on NeoForge).
     */
    public static volatile String TARGET_MC_VERSION = "1.21.4";

    private RetroModVersion() {}
}
