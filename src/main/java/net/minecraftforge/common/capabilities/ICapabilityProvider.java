/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of ICapabilityProvider.
 * Returns LazyOptional results via the polyfill LazyOptional class
 * so capability queries don't crash.
 */
package net.minecraftforge.common.capabilities;

import net.minecraftforge.common.util.LazyOptional;

/**
 * Reimplementation of Forge's ICapabilityProvider interface.
 *
 * Returns LazyOptional.empty() by default. Mods implementing this
 * interface will override getCapability() with actual capability logic.
 * The default implementation prevents NPEs in code that queries
 * capabilities on objects.
 */
public interface ICapabilityProvider {

    /**
     * Retrieves the capability for the given side.
     *
     * @param cap  the capability being requested
     * @param side the face of the block being queried (null for non-sided)
     * @return a LazyOptional holding the capability, or empty if not available
     */
    default <T> LazyOptional<T> getCapability(Object cap, Object side) {
        return LazyOptional.empty();
    }

    /**
     * Convenience overload for non-sided capability queries.
     */
    default <T> LazyOptional<T> getCapability(Object cap) {
        return getCapability(cap, null);
    }
}
