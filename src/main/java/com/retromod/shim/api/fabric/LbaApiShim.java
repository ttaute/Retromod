/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * LibBlockAttributes (LBA) API -> Fabric Transfer API v2 Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Maps AlexIIL's LibBlockAttributes (1.14 - 1.18) onto Fabric's Transfer API v2 that replaced it (Fabric API 0.35+). */
public class LbaApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "LibBlockAttributes -> Fabric Transfer API Compatibility";
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
        return "fabric";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemAttributesCompat"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "INSERTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "ITEM_INSERTABLE",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "EXTRACTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "ITEM_EXTRACTABLE",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "GROUPED_INV",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "ITEM_GROUPED",
            "Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FixedItemInvCompat"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "getSlotCount",
            "()I",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getSlotCount",
            "(Ljava/lang/Object;)I"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "getInvStack",
            "(I)Lnet/minecraft/item/ItemStack;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getInvStack",
            "(Ljava/lang/Object;I)Lnet/minecraft/item/ItemStack;"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "setInvStack",
            "(ILnet/minecraft/item/ItemStack;Lalexiil/mc/lib/attributes/Simulation;)Z",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "setInvStack",
            "(Ljava/lang/Object;ILnet/minecraft/item/ItemStack;Ljava/lang/Object;)Z"
        );

        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemInsertable",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemInsertableCompat"
        );

        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemExtractable",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemExtractableCompat"
        );

        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemInvUtil",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemInvUtilCompat"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/ItemInvUtil",
            "move",
            "(Lalexiil/mc/lib/attributes/item/ItemExtractable;Lalexiil/mc/lib/attributes/item/ItemInsertable;Lalexiil/mc/lib/attributes/item/filter/ItemFilter;I)I",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "moveItems",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)I"
        );

        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/FluidAttributes",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FluidAttributesCompat"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/FluidAttributes",
            "INSERTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_INSERTABLE",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/FluidAttributes",
            "EXTRACTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_EXTRACTABLE",
            "Ljava/lang/Object;"
        );

        // FixedFluidInv -> Storage<FluidVariant>
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/FixedFluidInv",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FixedFluidInvCompat"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/FixedFluidInv",
            "getTankCount",
            "()I",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getTankCount",
            "(Ljava/lang/Object;)I"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/FixedFluidInv",
            "getInvFluid",
            "(I)Lalexiil/mc/lib/attributes/fluid/volume/FluidVolume;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getInvFluid",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );

        // LBA's fraction-based FluidAmount -> long droplets (81000 per bucket)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/amount/FluidAmount",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FluidAmountCompat"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/amount/FluidAmount",
            "BUCKET",
            "Lalexiil/mc/lib/attributes/fluid/amount/FluidAmount;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_BUCKET",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/amount/FluidAmount",
            "BOTTLE",
            "Lalexiil/mc/lib/attributes/fluid/amount/FluidAmount;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_BOTTLE",
            "Ljava/lang/Object;"
        );

        // LBA's combined FluidVolume (type + amount) -> separate FluidVariant + long
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/volume/FluidVolume",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FluidVolumeCompat"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/volume/FluidVolume",
            "getFluidKey",
            "()Lalexiil/mc/lib/attributes/fluid/volume/FluidKey;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getFluidKey",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/volume/FluidVolume",
            "getAmount_F",
            "()Lalexiil/mc/lib/attributes/fluid/amount/FluidAmount;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getFluidAmount",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Simulation enum (ACTION/SIMULATE) -> Transaction (open/commit/abort)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/Simulation",
            "com/retromod/shim/api/fabric/embedded/LbaShim$SimulationCompat"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.LbaShim"
        };
    }
}
