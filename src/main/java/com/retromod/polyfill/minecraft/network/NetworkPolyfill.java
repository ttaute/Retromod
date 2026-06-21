/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.network;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for network/packet API class renames across major MC versions.
 *
 * Covers two major refactoring waves:
 * 1. The Flattening (1.13): PacketBuffer → FriendlyByteBuf, all packet classes
 *    moved from net.minecraft.network.play.{client,server} to
 *    net.minecraft.network.protocol.game with Serverbound/Clientbound naming.
 * 2. 1.17+ packet splits: Some compound packets (e.g., SPacketWorldBorder) were
 *    split into multiple specialized packets. For these, we redirect to the
 *    most commonly used successor.
 *
 * Note: SPacketWorldBorder was split into multiple packets in 1.17
 * (ClientboundSetBorderSizePacket, ClientboundSetBorderCenterPacket, etc.).
 * We redirect to ClientboundInitializeBorderPacket as the closest general equivalent.
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

            // Client → Server packets
            "net/minecraft/network/play/client/CPacketPlayer",
            "net/minecraft/network/play/client/CPacketChatMessage",

            // Server → Client packets
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
        // Pure class redirects - no stub implementations needed.
        // All old packet classes map directly to modern Mojang-named equivalents.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // Core networking
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/network/PacketBuffer",
            "net/minecraft/network/FriendlyByteBuf");

        // =====================================================================
        // Client → Server packets (CPacket* → Serverbound*Packet)
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/network/play/client/CPacketPlayer",
            "net/minecraft/network/protocol/game/ServerboundMovePlayerPacket");
        transformer.registerClassRedirect(
            "net/minecraft/network/play/client/CPacketChatMessage",
            "net/minecraft/network/protocol/game/ServerboundChatPacket");

        // =====================================================================
        // Server → Client packets (SPacket* → Clientbound*Packet)
        // =====================================================================

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

        // SPacketWorldBorder was split into multiple packets in 1.17:
        // ClientboundSetBorderSizePacket, ClientboundSetBorderCenterPacket,
        // ClientboundSetBorderLerpSizePacket, ClientboundSetBorderWarningDistancePacket,
        // ClientboundSetBorderWarningDelayPacket, ClientboundInitializeBorderPacket.
        // Redirect to InitializeBorder as the closest general-purpose equivalent.
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketWorldBorder",
            "net/minecraft/network/protocol/game/ClientboundInitializeBorderPacket");
    }
}
