/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Enchantments went data-driven (rarity/level methods removed) and event APIs were restructured. */
public class NeoForge_1_20_6_to_1_21 implements VersionShim {

    @Override public String getShimName() { return "NeoForge 1.20.6 to 1.21"; }
    @Override public String getSourceVersion() { return "1.20.6"; }
    @Override public String getTargetVersion() { return "1.21"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // ResourceLocation(namespace, path) ctor went private in 1.21; point it at the static factory (#92)
        transformer.registerConstructorRedirect(
            "net/minecraft/resources/ResourceLocation",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"
        );

        transformer.registerMethodRedirect(
            "net/minecraft/world/item/enchantment/Enchantment", "getRarity",
            "()Lnet/minecraft/world/item/enchantment/Enchantment$Rarity;",
            "com/retromod/shim/neoforge/embedded/EnchantmentShim", "getRarity",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/enchantment/Enchantment", "getMaxLevel",
            "()I",
            "com/retromod/shim/neoforge/embedded/EnchantmentShim", "getMaxLevel",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/enchantment/Enchantment", "getMinLevel",
            "()I",
            "com/retromod/shim/neoforge/embedded/EnchantmentShim", "getMinLevel",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/neoforged/neoforge/event/entity/EntityJoinLevelEvent", "getLevel",
            "()Lnet/minecraft/world/level/Level;",
            "com/retromod/shim/neoforge/embedded/EventShim", "getLevel",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
