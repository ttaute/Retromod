/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * CoFH/Thermal/Redstone Flux API -> Forge Energy API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Maps the old CoFH RF (Redstone Flux) energy API to Forge Energy.
 *
 * RF was the tech-mod energy API on Forge 1.7.10-1.12.2; Forge 1.12+ folded it into
 * built-in Forge Energy and the CoFH API was removed in 1.13+.
 */
public class ThermalApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "CoFH/Thermal RF -> Forge Energy API Compatibility";
    }

    @Override
    public String getSourceVersion() {
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        return "forge";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // cofh.api.energy: the 1.7.10/1.10.2 API. IEnergyHandler maps to IEnergyStorage;
        // its methods carry a facing param Forge dropped, so they route through ThermalShim.
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyHandler",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "receiveEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "receiveEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "extractEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "extractEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "getEnergyStored",
            "(Lnet/minecraft/util/EnumFacing;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "getEnergyStored",
            "(Ljava/lang/Object;Ljava/lang/Object;)I"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "getMaxEnergyStored",
            "(Lnet/minecraft/util/EnumFacing;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "getMaxEnergyStored",
            "(Ljava/lang/Object;Ljava/lang/Object;)I"
        );

        // Receive/extract/connection interfaces all collapse onto IEnergyStorage.
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyReceiver",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyProvider",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyConnection",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyConnection",
            "canConnectEnergy",
            "(Lnet/minecraft/util/EnumFacing;)Z",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "canConnectEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        );

        // Reference impl: the three constructors line up with Forge's EnergyStorage.
        transformer.registerClassRedirect(
            "cofh/api/energy/EnergyStorage",
            "net/minecraftforge/energy/EnergyStorage"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/EnergyStorage",
            "<init>",
            "(I)V",
            "net/minecraftforge/energy/EnergyStorage",
            "<init>",
            "(I)V"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/EnergyStorage",
            "<init>",
            "(II)V",
            "net/minecraftforge/energy/EnergyStorage",
            "<init>",
            "(II)V"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/EnergyStorage",
            "<init>",
            "(III)V",
            "net/minecraftforge/energy/EnergyStorage",
            "<init>",
            "(III)V"
        );

        // cofh.redstoneflux.api: the 1.10/1.12 standalone library, same shape as cofh.api.energy.
        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyReceiver",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyProvider",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyStorage",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyConnection",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyReceiver",
            "receiveEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "receiveEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyProvider",
            "extractEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "extractEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        // RF's no-facing IEnergyStorage methods are already signature-compatible.
        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyStorage",
            "getEnergyStored",
            "()I",
            "net/minecraftforge/energy/IEnergyStorage",
            "getEnergyStored",
            "()I"
        );

        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyStorage",
            "getMaxEnergyStored",
            "()I",
            "net/minecraftforge/energy/IEnergyStorage",
            "getMaxEnergyStored",
            "()I"
        );

        // Energy items: Thermal stored RF on ItemStacks. Forge exposes this as an
        // IEnergyStorage capability, so the item methods route through ThermalShim,
        // which pulls the capability off the stack.
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "com/retromod/shim/api/forge/embedded/ThermalShim$EnergyContainerItemCompat"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "receiveEnergy",
            "(Lnet/minecraft/item/ItemStack;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemReceiveEnergy",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;IZ)I"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "extractEnergy",
            "(Lnet/minecraft/item/ItemStack;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemExtractEnergy",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;IZ)I"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "getEnergyStored",
            "(Lnet/minecraft/item/ItemStack;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemGetEnergyStored",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)I"
        );

        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "getMaxEnergyStored",
            "(Lnet/minecraft/item/ItemStack;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemGetMaxEnergyStored",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)I"
        );

        // 1.13 renamed EnumFacing to Direction.
        transformer.registerClassRedirect(
            "net/minecraft/util/EnumFacing",
            "net/minecraft/core/Direction"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.ThermalShim"
        };
    }
}
