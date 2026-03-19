/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric.embedded;

/**
 * Replacement for the removed ServerPlayNetworking$PlayChannelHandler interface.
 *
 * The original interface (removed in Fabric API 0.100+) had:
 *   void receive(MinecraftServer, ServerPlayer, ServerGamePacketListenerImpl, FriendlyByteBuf, PacketSender)
 *
 * We use Object parameters because Minecraft classes aren't on our compile classpath.
 * LambdaMetafactory handles the type adaptation at runtime — the specific types from
 * the mod's invokedynamic BSM args are subtypes of Object, so the factory generates
 * appropriate bridge code automatically.
 */
@FunctionalInterface
public interface ServerPlayChannelHandler {
    void receive(Object server, Object player, Object handler, Object buf, Object responseSender);
}
