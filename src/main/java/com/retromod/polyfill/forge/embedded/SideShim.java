/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge.embedded;

/**
 * Reimplementation of net.minecraftforge.fml.relauncher.Side (removed 1.13).
 * Old Forge mods used @SideOnly(Side.CLIENT) annotations.
 * In modern Forge, this is replaced by Dist.CLIENT / @OnlyIn(Dist.CLIENT).
 */
public enum SideShim {
    CLIENT,
    SERVER;

    public boolean isClient() { return this == CLIENT; }
    public boolean isServer() { return this == SERVER; }
}
