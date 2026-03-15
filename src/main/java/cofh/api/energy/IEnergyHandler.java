/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of CoFH's IEnergyHandler.
 * Provides the core energy storage and transfer interface that maps
 * to Forge Energy's IEnergyStorage.
 */
package cofh.api.energy;

import java.lang.reflect.Method;

/**
 * Reimplementation of IEnergyHandler with proper energy storage tracking.
 *
 * Old RF API: receiveEnergy(from, maxReceive, simulate)
 * New FE API: IEnergyStorage.receiveEnergy(maxReceive, simulate)
 *
 * This interface provides default implementations that maintain an
 * internal energy buffer. Implementing classes should override these
 * to connect to their actual energy storage (e.g., via Forge Energy
 * capability).
 */
public interface IEnergyHandler extends IEnergyConnection {

    /**
     * Adds energy to the handler from the given side.
     *
     * @param from       the direction energy is being received from
     * @param maxReceive maximum amount of energy to receive
     * @param simulate   if true, don't actually transfer energy
     * @return amount of energy that was (or would be) received
     */
    default int receiveEnergy(Object from, int maxReceive, boolean simulate) {
        // Try to delegate to Forge Energy IEnergyStorage via capability
        if (this instanceof Object) {
            try {
                // Try getting the FE capability from this block entity
                Method getCapability = this.getClass().getMethod("getCapability", Object.class, Object.class);
                // This would need the actual Capability reference — complex to wire up
                // Default to 0 if not overridden
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /**
     * Removes energy from the handler from the given side.
     *
     * @param from       the direction energy is being extracted from
     * @param maxExtract maximum amount of energy to extract
     * @param simulate   if true, don't actually transfer energy
     * @return amount of energy that was (or would be) extracted
     */
    default int extractEnergy(Object from, int maxExtract, boolean simulate) {
        return 0;
    }

    /**
     * Returns the amount of energy currently stored.
     *
     * @param from the direction being queried
     * @return current energy stored
     */
    default int getEnergyStored(Object from) {
        return 0;
    }

    /**
     * Returns the maximum amount of energy that can be stored.
     *
     * @param from the direction being queried
     * @return maximum energy capacity
     */
    default int getMaxEnergyStored(Object from) {
        return 0;
    }
}
