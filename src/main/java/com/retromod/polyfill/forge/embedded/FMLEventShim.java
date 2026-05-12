/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge.embedded;

/**
 * Stub implementations of removed FML lifecycle events.
 * Old Forge mods (1.12 and earlier) received these events via @Mod.EventHandler.
 * In modern Forge/NeoForge, lifecycle is handled via @Mod constructor + DeferredRegister.
 * These stubs prevent ClassNotFoundException without actually firing events.
 */
public class FMLEventShim {

    /** Stub for FMLPreInitializationEvent */
    public static class PreInit {
        public java.io.File getModConfigurationDirectory() {
            return new java.io.File("config");
        }

        public org.slf4j.Logger getModLog() {
            return org.slf4j.LoggerFactory.getLogger("Retromod-LegacyCompat");
        }
    }

    /** Stub for FMLInitializationEvent */
    public static class Init {
    }

    /** Stub for FMLPostInitializationEvent */
    public static class PostInit {
    }

    /** Stub for FMLServerStartingEvent */
    public static class ServerStarting {
    }
}
