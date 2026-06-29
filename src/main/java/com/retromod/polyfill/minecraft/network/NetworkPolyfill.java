/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.network;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Redirects network/packet API class renames across major MC versions: the 1.13 Flattening
 * (PacketBuffer to FriendlyByteBuf, play.client/server packets to protocol.game with
 * Serverbound/Clientbound naming) and 1.17+ packet splits, where a compound packet is
 * pointed at its closest successor.
 */
public class NetworkPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Network/Packet API Changes";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/network/PacketBuffer",

            "net/minecraft/network/play/client/CPacketPlayer",
            "net/minecraft/network/play/client/CPacketChatMessage",

            "net/minecraft/network/play/server/SPacketChat",
            "net/minecraft/network/play/server/SPacketSpawnObject",
            "net/minecraft/network/play/server/SPacketEntityMetadata",
            "net/minecraft/network/play/server/SPacketChunkData",
            "net/minecraft/network/play/server/SPacketBlockChange",
            "net/minecraft/network/play/server/SPacketEffect",
            "net/minecraft/network/play/server/SPacketSoundEffect",
            "net/minecraft/network/play/server/SPacketTitle",
            "net/minecraft/network/play/server/SPacketPlayerAbilities",
            "net/minecraft/network/play/server/SPacketWorldBorder"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // pure class redirects, no stubs
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "net/minecraft/network/PacketBuffer",
            "net/minecraft/network/FriendlyByteBuf");

        transformer.registerClassRedirect(
            "net/minecraft/network/play/client/CPacketPlayer",
            "net/minecraft/network/protocol/game/ServerboundMovePlayerPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/client/CPacketChatMessage",
            "net/minecraft/network/protocol/game/ServerboundChatPacket");

        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketChat",
            "net/minecraft/network/protocol/game/ClientboundSystemChatPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketSpawnObject",
            "net/minecraft/network/protocol/game/ClientboundAddEntityPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketEntityMetadata",
            "net/minecraft/network/protocol/game/ClientboundSetEntityDataPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketChunkData",
            "net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketBlockChange",
            "net/minecraft/network/protocol/game/ClientboundBlockUpdatePacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketEffect",
            "net/minecraft/network/protocol/game/ClientboundLevelEventPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketSoundEffect",
            "net/minecraft/network/protocol/game/ClientboundSoundPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketTitle",
            "net/minecraft/network/protocol/game/ClientboundSetTitleTextPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketPlayerAbilities",
            "net/minecraft/network/protocol/game/ClientboundPlayerAbilitiesPacket");

        // SPacketWorldBorder split into several packets in 1.17; InitializeBorder is the closest catch-all
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketWorldBorder",
            "net/minecraft/network/protocol/game/ClientboundInitializeBorderPacket");
    }
}
