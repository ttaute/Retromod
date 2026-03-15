/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of EntityRendererRegistry.
 * Delegates to the modern Fabric rendering registration API via reflection.
 */
package net.fabricmc.fabric.api.client.rendereregistry.v1;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of the removed EntityRendererRegistry.
 * Registers entity renderers by delegating to the modern Fabric API
 * or vanilla EntityRendererFactory registration via reflection.
 *
 * Note: the typo "rendereregistry" matches the real old Fabric API package name.
 */
public class EntityRendererRegistry {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    @FunctionalInterface
    public interface EntityRendererFactory {
        Object create(Object context);
    }

    private EntityRendererRegistry() {}

    /**
     * Registers an entity renderer factory for the given entity type.
     */
    public static void register(Object entityType, EntityRendererFactory factory) {
        // Try modern Fabric API: net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
        try {
            Class<?> modernRegistry = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry");
            for (Method m : modernRegistry.getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 2) {
                    // The modern API expects EntityRendererProvider which is a functional interface
                    // Our factory has the same shape: context -> renderer
                    m.invoke(null, entityType, (java.util.function.Function<Object, Object>) factory::create);
                    LOGGER.fine("[RetroMod] EntityRendererRegistry: registered via modern Fabric API");
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] EntityRendererRegistry: modern Fabric API not available");
        }

        // Try vanilla: EntityRenderers.register()
        try {
            Class<?> renderersClass = Class.forName(
                "net.minecraft.client.render.entity.EntityRenderers");
            for (Method m : renderersClass.getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 2) {
                    m.invoke(null, entityType, (java.util.function.Function<Object, Object>) factory::create);
                    LOGGER.fine("[RetroMod] EntityRendererRegistry: registered via vanilla EntityRenderers");
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] EntityRendererRegistry: vanilla EntityRenderers not available");
        }

        LOGGER.warning("[RetroMod] EntityRendererRegistry: could not register renderer — no API found");
    }
}
