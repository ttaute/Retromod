/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric.embedded;

/**
 * Replacement for the removed ClientPlayNetworking$PlayChannelHandler interface.
 *
 * The original interface (removed in Fabric API 0.100+) had:
 *   void receive(MinecraftClient, ClientPlayNetworkHandler, FriendlyByteBuf, PacketSender)
 *
 * Uses Object parameters — see ServerPlayChannelHandler for rationale.
 */
@FunctionalInterface
public interface ClientPlayChannelHandler {
    void receive(Object client, Object handler, Object buf, Object responseSender);
}
