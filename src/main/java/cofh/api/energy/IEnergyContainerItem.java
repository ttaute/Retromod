/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of CoFH's IEnergyContainerItem.
 * Interface for items that store energy (RF), mapping to Forge Energy
 * item capability in modern MC.
 */
package cofh.api.energy;

/**
 * Reimplementation of IEnergyContainerItem for items that hold RF energy.
 *
 * In modern MC, item energy storage is handled via Forge Energy's
 * IEnergyStorage capability attached to ItemStacks. This interface
 * provides the old API surface so legacy mods compile and run.
 *
 * Implementing classes should store energy data in the ItemStack's NBT
 * or Components system via reflection.
 */
public interface IEnergyContainerItem {

    /**
     * Adds energy to the item.
     *
     * @param container  the ItemStack containing this item
     * @param maxReceive maximum energy to receive
     * @param simulate   if true, don't modify the item
     * @return amount of energy received
     */
    default int receiveEnergy(Object container, int maxReceive, boolean simulate) {
        return 0; // Override to implement real storage
    }

    /**
     * Removes energy from the item.
     *
     * @param container  the ItemStack containing this item
     * @param maxExtract maximum energy to extract
     * @param simulate   if true, don't modify the item
     * @return amount of energy extracted
     */
    default int extractEnergy(Object container, int maxExtract, boolean simulate) {
        return 0;
    }

    /**
     * Returns the current energy stored in the item.
     */
    default int getEnergyStored(Object container) {
        return 0;
    }

    /**
     * Returns the maximum energy the item can store.
     */
    default int getMaxEnergyStored(Object container) {
        return 0;
    }
}
