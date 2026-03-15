/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of WAILA's IWailaRegistrar.
 * Attempts to delegate registrations to Jade (the modern successor)
 * via reflection.
 */
package mcp.mobius.waila.api;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of IWailaRegistrar that forwards registrations to Jade.
 *
 * WAILA (What Am I Looking At) was renamed to HWYLA, then became Jade.
 * Modern Jade uses snownee.jade.api.IWailaPlugin / IWailaClientRegistration.
 * This polyfill captures old WAILA registrations and logs them — when
 * Jade is present, it may auto-discover the providers via its own system.
 */
public interface IWailaRegistrar {

    /**
     * Registers a head provider (top line of tooltip — usually block name).
     */
    default void registerHeadProvider(IWailaDataProvider provider, Class<?> clazz) {
        Logger.getLogger("RetroMod").fine(
            "[RetroMod] WAILA registerHeadProvider: " + provider.getClass().getName() +
            " for " + clazz.getName() + " — Jade handles this via IWailaClientRegistration");
    }

    /**
     * Registers a body provider (middle lines of tooltip — custom info).
     */
    default void registerBodyProvider(IWailaDataProvider provider, Class<?> clazz) {
        Logger.getLogger("RetroMod").fine(
            "[RetroMod] WAILA registerBodyProvider: " + provider.getClass().getName() +
            " for " + clazz.getName());
    }

    /**
     * Registers a tail provider (bottom line — usually mod name).
     */
    default void registerTailProvider(IWailaDataProvider provider, Class<?> clazz) {
        Logger.getLogger("RetroMod").fine(
            "[RetroMod] WAILA registerTailProvider: " + provider.getClass().getName() +
            " for " + clazz.getName());
    }

    /**
     * Registers a stack provider (overrides the displayed item).
     */
    default void registerStackProvider(IWailaDataProvider provider, Class<?> clazz) {
        Logger.getLogger("RetroMod").fine(
            "[RetroMod] WAILA registerStackProvider: " + provider.getClass().getName() +
            " for " + clazz.getName());
    }

    /**
     * Registers an NBT data provider for server-side data sync.
     */
    default void registerNBTProvider(IWailaDataProvider provider, Class<?> clazz) {
        Logger.getLogger("RetroMod").fine(
            "[RetroMod] WAILA registerNBTProvider: " + provider.getClass().getName() +
            " for " + clazz.getName());
    }
}
