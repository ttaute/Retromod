/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric.embedded;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback, whose EVENT-based
 * API was replaced by HudElementRegistry in Fabric 1.21.6+. Exposes the EVENT field old mod
 * bytecode reads and bridges registered callbacks onto HudElementRegistry.addLast().
 */
@FunctionalInterface
public interface HudRenderCallbackShim {

    // Object params (were DrawContext, RenderTickCounter) to skip a compile-time MC dependency.
    void onHudRender(Object drawContext, Object tickCounter);

    Event<HudRenderCallbackShim> EVENT = EventFactory.createArrayBacked(
        HudRenderCallbackShim.class,
        callbacks -> (drawContext, tickCounter) -> {
            for (HudRenderCallbackShim callback : callbacks) {
                callback.onHudRender(drawContext, tickCounter);
            }
        }
    );

    AtomicInteger COUNTER = new AtomicInteger(0);

    /** Registers one HudElement that fans out to every callback. Call once, after mods register. */
    static void bridgeToNewApi() {
        try {
            Class<?> registryClass = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry"
            );
            Class<?> hudElementClass = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement"
            );

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

            Object id = IdentifierShim.of("retromod", "compat_hud_bridge");

            Object hudElement = Proxy.newProxyInstance(
                hudElementClass.getClassLoader(),
                new Class<?>[] { hudElementClass },
                (proxy, method, args) -> {
                    if (method.getName().equals("render") && args != null && args.length == 2) {
                        HudRenderCallbackShim invoker = EVENT.invoker();
                        invoker.onHudRender(args[0], args[1]);
                    }
                    return null;
                }
            );

            addLastMethod.invoke(null, id, hudElement);

        } catch (ClassNotFoundException e) {
            // old Fabric API without HudElementRegistry, nothing to bridge
        } catch (Exception e) {
            System.err.println("Retromod: Failed to bridge HUD callbacks: " + e.getMessage());
        }
    }
}
