/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Fabric 1.20.6 -> 1.21: data-driven enchantments and EnchantmentHelper signature changes. */
public class Fabric_1_20_6_to_1_21 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.6 to 1.21"; }
    @Override public String getSourceVersion() { return "1.20.6"; }
    @Override public String getTargetVersion() { return "1.21"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Enchantments became data-driven (registry-based)
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/Enchantment", "getRarity",
            "()Lnet/minecraft/enchantment/Enchantment$Rarity;",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getRarity",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/Enchantment", "getMaxLevel",
            "()I",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getMaxLevel",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/Enchantment", "getMinLevel",
            "()I",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getMinLevel",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/EnchantmentHelper", "getLevel",
            "(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/item/ItemStack;)I",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getLevel",
            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;)I"
        );
        // Trial chambers / Breeze
        transformer.registerClassRedirect(
            "net/minecraft/entity/mob/BreezeEntity",
            "net/minecraft/entity/mob/BreezeEntity"
        );

        // 1.21 removed the 2-arg ResourceLocation ctor; route to the static factory.
        transformer.registerConstructorRedirect(
            "net/minecraft/resources/ResourceLocation",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"
        );

        // Intermediary-keyed copy for Fabric mods on a pre-26.1 host (remap off, 2-arg ctor still
        // private, #36). class_2960 = ResourceLocation, method_60655 = the factory.
        transformer.registerConstructorRedirect(
            "net/minecraft/class_2960",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/class_2960", "method_60655",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/class_2960;"
        );

        // Entity.changeDimension -> teleportTo (renamed in 1.21)
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "changeDimension",
            "(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",
            "net/minecraft/world/entity/Entity", "teleportTo",
            "(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
