/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Shim for Fabric 1.21.4 to 1.21.5. */
public class Fabric_1_21_4_to_1_21_5 implements VersionShim {
    @Override public String getShimName() { return "Fabric 1.21.4 to 1.21.5"; }
    @Override public String getSourceVersion() { return "1.21.4"; }
    @Override public String getTargetVersion() { return "1.21.5"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 1.21.5 folded the tool/armor item classes into Item (data components).
        transformer.registerClassRedirect(
            "net/minecraft/world/item/SwordItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/item/PickaxeItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/item/AxeItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/item/ShovelItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/item/HoeItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/item/DiggerItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/item/ArmorItem",
            "net/minecraft/world/item/Item"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/level/saveddata/maps/ForcedChunksSavedData",
            "net/minecraft/world/level/saveddata/maps/TicketStorage"
        );

        // blockUsingShield became blockUsingItem (any blocking item, not just shields).
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/LivingEntity", "blockUsingShield",
            "(Lnet/minecraft/world/entity/LivingEntity;)V",
            "net/minecraft/world/entity/LivingEntity", "blockUsingItem",
            "(Lnet/minecraft/world/entity/LivingEntity;)V"
        );
    }
    
    @Override public String[] getShimClasses() { return new String[0]; }
}
