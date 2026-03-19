/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Shim for Button constructor changes.
 * Old: new Button(int x, int y, int w, int h, Component text, OnPress onPress)
 * New: Button.builder(text, onPress).bounds(x, y, w, h).build()
 *
 * The 6-param constructor was removed; buttons must use the Builder pattern.
 */
package com.retromod.shim.fabric.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Factory bridge for Button creation.
 * Converts old 6-param constructor calls to Builder pattern.
 */
public class ButtonShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-ButtonShim");

    /**
     * Create a Button using the new Builder API.
     * Called via constructor redirect from old `new Button(x, y, w, h, text, onPress)`.
     *
     * @return a Button instance (as Object to avoid compile-time MC dependency)
     */
    public static Object create(int x, int y, int w, int h, Object text, Object onPress) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> buttonClass = cl.loadClass("net.minecraft.client.gui.components.Button");
            Class<?> componentClass = cl.loadClass("net.minecraft.network.chat.Component");
            Class<?> onPressClass = cl.loadClass("net.minecraft.client.gui.components.Button$OnPress");
            Class<?> builderClass = cl.loadClass("net.minecraft.client.gui.components.Button$Builder");

            // Button.builder(Component, OnPress) -> Builder
            Method builder = buttonClass.getMethod("builder", componentClass, onPressClass);
            Object builderObj = builder.invoke(null, text, onPress);

            // Builder.bounds(int, int, int, int) -> Builder
            // Try bounds() first (newer), then pos().size() (alternative)
            try {
                Method bounds = builderClass.getMethod("bounds", int.class, int.class, int.class, int.class);
                builderObj = bounds.invoke(builderObj, x, y, w, h);
            } catch (NoSuchMethodException e) {
                // Try pos() + size() separately
                Method pos = builderClass.getMethod("pos", int.class, int.class);
                builderObj = pos.invoke(builderObj, x, y);
                Method size = builderClass.getMethod("size", int.class, int.class);
                builderObj = size.invoke(builderObj, w, h);
            }

            // Builder.build() -> Button
            Method build = builderClass.getMethod("build");
            return build.invoke(builderObj);
        } catch (Exception e) {
            LOGGER.error("Failed to create Button via builder: {}", e.getMessage());
            return null;
        }
    }
}
