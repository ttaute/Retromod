/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Headless dedicated-server entry point: no Swing/AWT, console warnings, crash-time world save.
 */
@Environment(EnvType.SERVER)
public class RetromodServer implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Server");

    @Override
    public void onInitializeServer() {
        LOGGER.info("Retromod dedicated server initialization...");

        EnvironmentDetector.setEnvironment(false, true);

        initializeServerFeatures();
        registerServerCrashHandler();

        LOGGER.info("=======================================================");
        LOGGER.info("  Retromod: Server Mode Active");
        LOGGER.info("=======================================================");
        LOGGER.info("  • Bytecode transformation: ENABLED");
        LOGGER.info("  • AOT compilation: ENABLED");
        LOGGER.info("  • GUI features: DISABLED (headless)");
        LOGGER.info("  • Console warnings: ENABLED");
        LOGGER.info("=======================================================");
        
        LOGGER.info("Retromod server initialization complete!");
    }

    private void initializeServerFeatures() {
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();
        Path modsFolder = gameDir.resolve("mods");

        // backup in case PreLaunch missed
        ModHealthChecker.ensureFoldersExist(gameDir);

        try {
            HybridTransformationEngine hybrid = HybridTransformationEngine.getInstance();
            hybrid.initialize(modsFolder, "26.1");
            LOGGER.info("Hybrid AOT/JIT engine initialized for server");
        } catch (Exception e) {
            LOGGER.warn("Could not initialize hybrid engine: {}", e.getMessage());
        }

        try (java.util.stream.Stream<Path> s = java.nio.file.Files.list(modsFolder)) {
            long modCount = s
                .filter(p -> p.toString().endsWith(".jar"))
                .count();
            LOGGER.info("Found {} mod JARs in mods folder", modCount);
        } catch (Exception e) {
            LOGGER.debug("Could not count mods: {}", e.getMessage());
        }
    }

    private void registerServerCrashHandler() {
        try {
            SafeCrashHandler crashHandler = SafeCrashHandler.getInstance();

            // reflective probe avoids a compile-time MC dependency; instance is registered later
            try {
                Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
                LOGGER.debug("Server crash handler ready (will register instance when available)");
            } catch (Exception e) {
                LOGGER.debug("Could not prepare server crash handler");
            }
        } catch (Exception e) {
            LOGGER.debug("Crash handler not available: {}", e.getMessage());
        }
    }

    /** Registers the running server instance with the crash handler once it's started. */
    public static void onServerStarted(Object server) {
        try {
            SafeCrashHandler.getInstance().registerServer(server);
            LOGGER.info("Registered crash handler with Minecraft server");
        } catch (Exception e) {
            LOGGER.debug("Could not register server with crash handler");
        }
    }

    /** Per-tick TPS monitoring hook. */
    public static void onServerTick() {
        try {
            MemorySafetyMonitor.getInstance().onServerTick();
        } catch (Exception e) {
            // ignore tick errors
        }
    }
}
