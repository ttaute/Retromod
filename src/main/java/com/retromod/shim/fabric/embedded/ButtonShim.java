/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Bridges old 6-param Button constructor calls to the Builder API. */
public class ButtonShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-ButtonShim");

    /** Builds a Button via the Builder API, standing in for the removed 6-param constructor. */
    public static Object create(int x, int y, int w, int h, Object text, Object onPress) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> buttonClass = cl.loadClass("net.minecraft.client.gui.components.Button");
            Class<?> componentClass = cl.loadClass("net.minecraft.network.chat.Component");
            Class<?> onPressClass = cl.loadClass("net.minecraft.client.gui.components.Button$OnPress");
            Class<?> builderClass = cl.loadClass("net.minecraft.client.gui.components.Button$Builder");

            Method builder = buttonClass.getMethod("builder", componentClass, onPressClass);
            Object builderObj = builder.invoke(null, text, onPress);

            // bounds() on newer versions, pos()+size() on older ones
            try {
                Method bounds = builderClass.getMethod("bounds", int.class, int.class, int.class, int.class);
                builderObj = bounds.invoke(builderObj, x, y, w, h);
            } catch (NoSuchMethodException e) {
                Method pos = builderClass.getMethod("pos", int.class, int.class);
                builderObj = pos.invoke(builderObj, x, y);
                Method size = builderClass.getMethod("size", int.class, int.class);
                builderObj = size.invoke(builderObj, w, h);
            }

            Method build = builderClass.getMethod("build");
            return build.invoke(builderObj);
        } catch (Exception e) {
            LOGGER.error("Failed to create Button via builder: {}", e.getMessage());
            return null;
        }
    }
}
