/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Shim for HUD Render API changes in Fabric 1.21.6+.
 *
 * The HUD API was completely rewritten to use HudElementRegistry.
 * This shim bridges the old HudRenderCallback to the new API.
 */
package com.retromod.shim.fabric.embedded;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shim for net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
 *
 * Old API: HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {...})
 * New API: HudElementRegistry.addLast(id, (context, tickCounter) -> {...})
 *
 * This shim provides the EVENT field with the correct type (Event) so old mod
 * bytecode that does GETSTATIC HudRenderCallback.EVENT : Event works.
 * When .register() is called, we bridge to HudElementRegistry.addLast().
 */
@FunctionalInterface
public interface HudRenderCallbackShim {

    /**
     * Called when the HUD is rendered.
     * Old signature: (DrawContext drawContext, RenderTickCounter tickCounter)
     * We accept Object params to avoid compile-time MC dependency.
     */
    void onHudRender(Object drawContext, Object tickCounter);

    /**
     * The EVENT field that legacy mod code accesses.
     * Type is Event<HudRenderCallbackShim> - matches the bytecode descriptor
     * GETSTATIC .EVENT : Lnet/fabricmc/fabric/api/event/Event;
     */
    Event<HudRenderCallbackShim> EVENT = EventFactory.createArrayBacked(
        HudRenderCallbackShim.class,
        callbacks -> (drawContext, tickCounter) -> {
            for (HudRenderCallbackShim callback : callbacks) {
                callback.onHudRender(drawContext, tickCounter);
            }
        }
    );

    // Counter for generating unique IDs for HudElementRegistry
    AtomicInteger COUNTER = new AtomicInteger(0);

    /**
     * Initialize the bridge to the new HUD API.
     * Called once after all mods have registered their callbacks via EVENT.register().
     * Registers a single HudElement that invokes all registered callbacks.
     */
    static void bridgeToNewApi() {
        try {
            // Find HudElementRegistry (new API in 26.1)
            Class<?> registryClass = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry"
            );

            // Find HudElement interface
            Class<?> hudElementClass = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement"
            );

            // Find addLast(Identifier, HudElement)
            Method addLastMethod = null;
            for (Method m : registryClass.getMethods()) {
                if (m.getName().equals("addLast") && m.getParameterCount() == 2) {
                    addLastMethod = m;
                    break;
                }
            }

            if (addLastMethod == null) {
                System.err.println("Retromod: HudElementRegistry.addLast not found");
                return;
            }

            // Create an Identifier for our bridge element
            Object id = IdentifierShim.of("retromod", "compat_hud_bridge");

            // Create a proxy HudElement that invokes all registered callbacks
            Object hudElement = Proxy.newProxyInstance(
                hudElementClass.getClassLoader(),
                new Class<?>[] { hudElementClass },
                (proxy, method, args) -> {
                    if (method.getName().equals("render") && args != null && args.length == 2) {
                        // Invoke all registered callbacks
                        HudRenderCallbackShim invoker = EVENT.invoker();
                        invoker.onHudRender(args[0], args[1]);
                    }
                    return null;
                }
            );

            addLastMethod.invoke(null, id, hudElement);

        } catch (ClassNotFoundException e) {
            // HudElementRegistry not available - old Fabric API, nothing to bridge
        } catch (Exception e) {
            System.err.println("Retromod: Failed to bridge HUD callbacks: " + e.getMessage());
        }
    }
}
