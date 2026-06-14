/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.20.6 to 1.21 shim - Enchantment and event changes.
 * Enchantments became data-driven, removing hardcoded rarity/level methods.
 * NeoForge event APIs were also restructured in this transition.
 */
public class NeoForge_1_20_6_to_1_21 implements VersionShim {

    @Override public String getShimName() { return "NeoForge 1.20.6 to 1.21"; }
    @Override public String getSourceVersion() { return "1.20.6"; }
    @Override public String getTargetVersion() { return "1.21"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // ============================================================
        // RESOURCE LOCATION CONSTRUCTOR CHANGE (#92)
        // new ResourceLocation(namespace, path) was made PRIVATE in 1.21 in
        // favour of the static factory ResourceLocation.fromNamespaceAndPath.
        // A NeoForge mod built for ≤1.20.6 still compiles the 2-arg ctor as
        // NEW + DUP + INVOKESPECIAL <init>(String,String) — which throws
        // IllegalAccessError on a 1.21+ host (Rings of Ascension, #92:
        // GlintRenderType.buildGlintRenderType). The Fabric 1.20.6→1.21 shim
        // already did this; the NeoForge chain was missing it, so any NeoForge
        // mod constructing a ResourceLocation by ctor crashed at <clinit>.
        // NeoForge is Mojang-named, so only the Mojang-name variant is needed
        // (no intermediary class_2960). Sits in the chain of every host ≥1.21
        // (1.21.x and 26.1).
        transformer.registerConstructorRedirect(
            "net/minecraft/resources/ResourceLocation",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"
        );

        // Enchantments became data-driven
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
        // NeoForge event changes
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
