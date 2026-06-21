/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for Fabric networking API changes.
 *
 * The Fabric networking API underwent a major overhaul in 1.20.5 when Minecraft
 * migrated from PacketByteBuf to PacketCodec for custom payload serialization.
 * Several classes were removed entirely (PacketType, FabricPacket) and method
 * signatures on ServerPlayNetworking/ClientPlayNetworking changed.
 *
 * In 26.1, PlayChannelHandler interfaces were fully removed in favor of
 * PlayPayloadHandler. This polyfill provides bridge interfaces and a functional
 * networking bridge that adapts old 5-param handlers to the new 2-param API.
 *
 * Covered changes:
 * - PacketType (removed in 1.20.5, replaced by CustomPayload.Id)
 * - FabricPacket (removed in 1.20.5, replaced by CustomPayload)
 * - ServerPlayNetworking$PlayChannelHandler (removed, bridged via FabricNetworkingBridge)
 * - ClientPlayNetworking$PlayChannelHandler (removed, bridged via FabricNetworkingBridge)
 * - ServerPlayNetworking.registerGlobalReceiver (old signature redirected to bridge)
 * - ClientPlayNetworking.registerGlobalReceiver (old signature redirected to bridge)
 */
public class FabricNetworkingPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Fabric Networking API Changes";
    }

    @Override
    public String getCategory() {
        return "fabric_api";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // Removed in 1.20.5 - replaced by CustomPayload system
            "net/fabricmc/fabric/api/networking/v1/PacketType",
            "net/fabricmc/fabric/api/networking/v1/FabricPacket",

            // PlayChannelHandler removed from both server and client networking
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayChannelHandler",
            "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$PlayChannelHandler"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "com.retromod.polyfill.fabric.embedded.ServerPlayChannelHandler",
            "com.retromod.polyfill.fabric.embedded.ClientPlayChannelHandler",
            "com.retromod.polyfill.fabric.embedded.FabricNetworkingBridge"
        };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // Class redirects for removed types
        // =====================================================================

        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/PacketType",
            "net/minecraft/network/protocol/common/custom/CustomPacketPayload$Type");

        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/FabricPacket",
            "net/minecraft/network/protocol/common/custom/CustomPacketPayload");

        // Redirect removed PlayChannelHandler interfaces to our bridge interfaces.
        // These provide the @FunctionalInterface so lambdas in old mods can be created.
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayChannelHandler",
            "com/retromod/polyfill/fabric/embedded/ServerPlayChannelHandler");

        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$PlayChannelHandler",
            "com/retromod/polyfill/fabric/embedded/ClientPlayChannelHandler");

        // =====================================================================
        // Method redirects for registerGlobalReceiver and send
        //
        // IMPORTANT: Source descriptors use POST-REMAPPING names because
        // ClassRemapper (outer visitor) runs BEFORE RetromodClassVisitor (inner).
        // By the time RetromodMethodVisitor sees the bytecode:
        //   - class_2960 → net/minecraft/resources/Identifier (intermediary→Mojang)
        //   - PlayChannelHandler → our bridge interface (class redirect above)
        //   - class_2540 → net/minecraft/network/FriendlyByteBuf (intermediary→Mojang)
        //
        // Target descriptors use Object params because our bridge class can't
        // reference MC classes at compile time. The JVM verifier accepts this
        // because specific types are assignable to Object.
        // =====================================================================

        // Server: registerGlobalReceiver(Identifier, ServerPlayChannelHandler) -> boolean
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking",
            "registerGlobalReceiver",
            "(Lnet/minecraft/resources/Identifier;Lcom/retromod/polyfill/fabric/embedded/ServerPlayChannelHandler;)Z",
            "com/retromod/polyfill/fabric/embedded/FabricNetworkingBridge",
            "registerServerGlobalReceiver",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z");

        // Client: registerGlobalReceiver(Identifier, ClientPlayChannelHandler) -> boolean
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking",
            "registerGlobalReceiver",
            "(Lnet/minecraft/resources/Identifier;Lcom/retromod/polyfill/fabric/embedded/ClientPlayChannelHandler;)Z",
            "com/retromod/polyfill/fabric/embedded/FabricNetworkingBridge",
            "registerClientGlobalReceiver",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z");

        // Client: send(Identifier, FriendlyByteBuf) -> void
        // Old API sends raw bytes on a named channel; new API requires CustomPacketPayload.
        // Bridge wraps the raw bytes into a payload and sends via the new API.
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking",
            "send",
            "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)V",
            "com/retromod/polyfill/fabric/embedded/FabricNetworkingBridge",
            "clientSend",
            "(Ljava/lang/Object;Ljava/lang/Object;)V");

        // Server: send(ServerPlayer, Identifier, FriendlyByteBuf) -> void
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking",
            "send",
            "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)V",
            "com/retromod/polyfill/fabric/embedded/FabricNetworkingBridge",
            "serverSend",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");

        // Register embedded shim classes
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
