/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of BlockEntityRendererRegistry.
 * Delegates to the modern BlockEntityRendererFactories.register() or
 * net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry
 * via reflection.
 */
package net.fabricmc.fabric.api.client.rendereregistry.v1;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Reimplementation of the removed BlockEntityRendererRegistry.
 * Registers block entity renderers by delegating to the modern Fabric API
 * or vanilla registration methods via reflection.
 *
 * Note: the typo "rendereregistry" is intentional — it matches the
 * real Fabric API package name from older versions.
 */
public class BlockEntityRendererRegistry {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private BlockEntityRendererRegistry() {}

    /**
     * Registers a block entity renderer factory for the given block entity type.
     * Delegates to the modern registration API via reflection.
     */
    @SuppressWarnings("unchecked")
    public static <T> void register(Object blockEntityType, Function<Object, ?> rendererFactory) {
        // Try modern Fabric API: net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry
        try {
            Class<?> modernRegistry = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry");
            // register(BlockEntityType, Function<BlockEntityRendererFactory.Context, BlockEntityRenderer>)
            for (Method m : modernRegistry.getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 2) {
                    m.invoke(null, blockEntityType, rendererFactory);
                    LOGGER.fine("[RetroMod] BlockEntityRendererRegistry: registered via modern Fabric API");
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] BlockEntityRendererRegistry: modern Fabric API not available");
        }

        // Try vanilla: BlockEntityRendererFactories.register()
        try {
            Class<?> factoriesClass = Class.forName(
                "net.minecraft.client.render.block.entity.BlockEntityRendererFactories");
            for (Method m : factoriesClass.getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 2) {
                    m.invoke(null, blockEntityType, rendererFactory);
                    LOGGER.fine("[RetroMod] BlockEntityRendererRegistry: registered via vanilla Factories");
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] BlockEntityRendererRegistry: vanilla Factories not available");
        }

        LOGGER.warning("[RetroMod] BlockEntityRendererRegistry: could not register renderer — no API found");
    }
}
