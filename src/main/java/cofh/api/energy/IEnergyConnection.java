/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of CoFH's IEnergyConnection.
 * The old RF (Redstone Flux) API was replaced by Forge Energy (FE).
 * This interface provides the connection check for energy transfer.
 */
package cofh.api.energy;

/**
 * Reimplementation of the CoFH IEnergyConnection interface.
 *
 * In the old RF system, this determined if a block could connect to
 * energy conduits from a given face. The modern Forge Energy (FE) API
 * handles this via capabilities — if a block has the energy capability
 * on a side, it can connect.
 *
 * Default: returns true to allow connections (most energy blocks do).
 */
public interface IEnergyConnection {
    /**
     * Returns true if this handler can connect for energy transfer from the given side.
     *
     * @param from the direction being queried (EnumFacing/Direction)
     * @return true if energy can be transferred from this side
     */
    default boolean canConnectEnergy(Object from) {
        return true; // Default to accepting connections like most energy blocks
    }
}
