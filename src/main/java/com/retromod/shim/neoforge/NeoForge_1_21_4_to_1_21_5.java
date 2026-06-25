/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** 1.21.5 folded the tool/armor Item subclasses into Item (data components). */
public class NeoForge_1_21_4_to_1_21_5 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.4 to 1.21.5"; }
    @Override public String getSourceVersion() { return "1.21.4"; }
    @Override public String getTargetVersion() { return "1.21.5"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
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
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
