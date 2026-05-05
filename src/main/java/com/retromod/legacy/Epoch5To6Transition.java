/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * EPOCH 5 → 6: Data-Driven (1.19-1.20) to Modern (1.20.5+)
 */
package com.retromod.legacy;

import com.retromod.util.McReflect;

public class Epoch5To6Transition extends BaseEpochTransition {

    @Override public String name() { return "Data-Driven 1.19-1.20 → Modern 1.20.5+"; }
    @Override public int sourceEpoch() { return 5; }
    @Override public int targetEpoch() { return 6; }

    public Epoch5To6Transition() {
        // Identifier changes
        addMethod("net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V",
                  "net/minecraft/util/Identifier", "of", "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/util/Identifier;");
        addMethod("net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;)V",
                  "net/minecraft/util/Identifier", "of", "(Ljava/lang/String;)Lnet/minecraft/util/Identifier;");

        // Forge → NeoForge package migration.
        //
        // Apply only when the runtime IS NeoForge. On a Forge runtime these
        // remappings are actively harmful: they rewrite a Forge mod's
        // @net.minecraftforge.fml.common.Mod annotation to the NeoForge
        // equivalent, after which Forge can't find the @Mod annotation on
        // any class in the mod and dies with:
        //
        //     The Mod File <jar> has mods that were not found
        //
        // Surfaced when the Forge test mod and Jade/JEI all started
        // failing with that exact error after RetroMod transformed them.
        // The annotation rewrite was the culprit.
        if (McReflect.isNeoForge()) {
            addClass("net/minecraftforge/common/MinecraftForge",
                     "net/neoforged/neoforge/common/NeoForge");
            addClass("net/minecraftforge/fml/common/Mod",
                     "net/neoforged/fml/common/Mod");
            addClass("net/minecraftforge/eventbus/api/SubscribeEvent",
                     "net/neoforged/bus/api/SubscribeEvent");
            addClass("net/minecraftforge/eventbus/api/IEventBus",
                     "net/neoforged/bus/api/IEventBus");
            addClass("net/minecraftforge/registries/ForgeRegistries",
                     "net/neoforged/neoforge/registries/NeoForgeRegistries");
            addClass("net/minecraftforge/registries/DeferredRegister",
                     "net/neoforged/neoforge/registries/DeferredRegister");
        }

        addShim("com.retromod.virtual.ComponentShim");
        addShim("com.retromod.shim.fabric.embedded.IdentifierShim");
        addShim("com.retromod.shim.forge.embedded.ForgeRegistriesShim");
    }
}
