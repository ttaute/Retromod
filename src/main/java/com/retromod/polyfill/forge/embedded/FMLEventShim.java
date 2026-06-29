/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge.embedded;

/**
 * Stubs for removed FML lifecycle events that pre-1.13 Forge mods received via @Mod.EventHandler.
 * They keep those mods loading (no ClassNotFoundException); modern Forge/NeoForge handles lifecycle
 * via the @Mod constructor plus DeferredRegister, so nothing here fires.
 */
public class FMLEventShim {

    public static class PreInit {
        public java.io.File getModConfigurationDirectory() {
            return new java.io.File("config");
        }

        public org.slf4j.Logger getModLog() {
            return org.slf4j.LoggerFactory.getLogger("Retromod-LegacyCompat");
        }
    }

    public static class Init {
    }

    public static class PostInit {
    }

    public static class ServerStarting {
    }
}
