/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Forge mods built for 1.21.10 (or earlier) to run on 26.1.
 *
 * Forge traditionally used SRG mappings (func_12345, field_12345) up through 1.20.x,
 * then switched to Mojang official mappings. For 26.1, all remaining obfuscation
 * is removed.
 *
 * This shim handles:
 * - SRG → Mojang name migration (for older Forge mods that still use SRG names)
 * - Forge API changes between 1.21.x and 26.1
 * - MinecraftForge event system changes
 */
public class Forge_1_21_10_to_26_1 implements VersionShim {

    @Override
    public String getShimName() {
        return "Forge 1.21.10 to 26.1";
    }

    @Override
    public String getSourceVersion() {
        return "1.21.10";
    }

    @Override
    public String getTargetVersion() {
        return "26.1";
    }

    @Override
    public String getModLoaderType() {
        return "forge";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // SRG → MOJANG OFFICIAL NAME MIGRATION
        // ============================================================

        // Older Forge mods (pre-1.17) used SRG names like func_12345_a
        // These need to be mapped to Mojang official names

        // Common SRG method names still found in legacy Forge mods:
        transformer.registerMethodRedirect(
                "net/minecraft/world/entity/Entity", "func_70005_c_",
                "()Ljava/lang/String;",
                "net/minecraft/world/entity/Entity", "getName",
                "()Lnet/minecraft/network/chat/Component;"
        );

        transformer.registerMethodRedirect(
                "net/minecraft/world/entity/Entity", "func_70011_f",
                "(DDD)D",
                "net/minecraft/world/entity/Entity", "distanceTo",
                "(DDD)D"
        );

        transformer.registerMethodRedirect(
                "net/minecraft/world/level/Level", "func_180501_a",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
                "net/minecraft/world/level/Level", "setBlock",
                "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        );

        // ============================================================
        // FORGE API CHANGES
        // ============================================================

        // MinecraftForge → NeoForge migration remnants
        // Some mods reference MinecraftForge classes that were renamed
        transformer.registerClassRedirect(
                "net/minecraftforge/event/entity/player/PlayerEvent",
                "net/neoforged/neoforge/event/entity/player/PlayerEvent"
        );
        transformer.registerClassRedirect(
                "net/minecraftforge/event/TickEvent",
                "net/neoforged/neoforge/event/tick/TickEvent"
        );
        transformer.registerClassRedirect(
                "net/minecraftforge/event/RegistryEvent",
                "net/neoforged/neoforge/registries/RegisterEvent"
        );

        // ForgeConfigSpec → NeoForge equivalent
        transformer.registerClassRedirect(
                "net/minecraftforge/common/ForgeConfigSpec",
                "net/neoforged/neoforge/common/ModConfigSpec"
        );

        // ============================================================
        // CAPABILITY MIGRATION
        // ============================================================

        // Old Forge capability system → NeoForge capability system
        transformer.registerClassRedirect(
                "net/minecraftforge/common/capabilities/ICapabilityProvider",
                "net/neoforged/neoforge/capabilities/ICapabilityProvider"
        );

        // ============================================================
        // RENDERING
        // ============================================================

        // RenderGameOverlayEvent → various specific events in NeoForge
        transformer.registerClassRedirect(
                "net/minecraftforge/client/event/RenderGameOverlayEvent",
                "net/neoforged/neoforge/client/event/RenderGuiLayerEvent"
        );
    }
}
