/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge.embedded;

/**
 * Reimplementation of FMLCommonHandler (removed 1.13).
 * Old Forge mods called FMLCommonHandler.instance().getSide() etc.
 */
public class FMLCommonHandlerShim {
    private static final FMLCommonHandlerShim INSTANCE = new FMLCommonHandlerShim();

    public static FMLCommonHandlerShim instance() {
        return INSTANCE;
    }

    public SideShim getSide() {
        // Try to determine side from context
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return SideShim.CLIENT;
        } catch (ClassNotFoundException e) {
            return SideShim.SERVER;
        }
    }

    public SideShim getEffectiveSide() {
        return getSide();
    }

    public void exitJava(int exitCode, boolean hardExit) {
        System.exit(exitCode);
    }
}
