/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Mixin to inject a RetroMod button into the Minecraft title screen.
 *
 * The button sits above the language selector button (bottom-left corner).
 * Clicking it opens the RetroMod mod manager (file picker + transformation).
 *
 * Uses reflection for all Minecraft class interactions because this project
 * is built with Maven (not Fabric Loom), so Minecraft classes are not on
 * the compile-time classpath. At runtime, all MC classes are available.
 */
@Mixin(targets = "net.minecraft.client.gui.screen.TitleScreen")
public abstract class TitleScreenMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Mixin");

    /**
     * Inject after the title screen initializes its buttons.
     * We add a RetroMod button above the language selector.
     */
    @Inject(method = "init", at = @At("RETURN"))
    private void retromod$addRetroModButton(CallbackInfo ci) {
        try {
            // "this" is the TitleScreen instance (which extends Screen)
            Object screen = this;
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screen.Screen");

            // Get width and height fields
            int width = getIntField(screen, screenClass, "width");
            int height = getIntField(screen, screenClass, "height");

            // Position: above the language selector (bottom-left)
            int buttonX = width / 2 - 124;
            int buttonY = height - 52;

            // Create the button text: Text.literal("RetroMod")
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Method literalMethod = textClass.getMethod("literal", String.class);
            Object buttonText = literalMethod.invoke(null, "RetroMod");

            // Create button press action using Proxy
            Class<?> buttonClass = Class.forName("net.minecraft.client.gui.widget.ButtonWidget");
            Class<?> pressActionClass = Class.forName(
                "net.minecraft.client.gui.widget.ButtonWidget$PressAction");

            Object pressAction = java.lang.reflect.Proxy.newProxyInstance(
                pressActionClass.getClassLoader(),
                new Class<?>[]{pressActionClass},
                (proxy, method, args) -> {
                    if ("onPress".equals(method.getName())) {
                        openRetroModManager(screen, screenClass);
                    }
                    return null;
                }
            );

            // ButtonWidget.builder(text, pressAction)
            Method builderMethod = buttonClass.getMethod("builder", textClass, pressActionClass);
            Object builder = builderMethod.invoke(null, buttonText, pressAction);

            // .dimensions(x, y, width, height)
            Method dimensionsMethod = builder.getClass().getMethod(
                "dimensions", int.class, int.class, int.class, int.class);
            builder = dimensionsMethod.invoke(builder, buttonX, buttonY, 20, 20);

            // .build()
            Method buildMethod = builder.getClass().getMethod("build");
            Object button = buildMethod.invoke(builder);

            // screen.addDrawableChild(button)
            // addDrawableChild is declared in Screen and takes Element
            Method addDrawableChild = findMethod(screenClass, "addDrawableChild");
            if (addDrawableChild != null) {
                addDrawableChild.setAccessible(true);
                addDrawableChild.invoke(screen, button);
            }

            LOGGER.debug("RetroMod button added to title screen");

        } catch (Exception e) {
            LOGGER.warn("Could not add RetroMod button to title screen: {}", e.getMessage());
            LOGGER.debug("Detailed error:", e);
        }
    }

    /**
     * Open the RetroMod mod manager when the button is clicked.
     * Uses the reflection-based RetroModScreen.
     */
    private static void openRetroModManager(Object screen, Class<?> screenClass) {
        try {
            // Get the MinecraftClient from screen.client field
            Object client = null;
            try {
                var field = screenClass.getField("client");
                client = field.get(screen);
            } catch (NoSuchFieldException e) {
                try {
                    var field = screenClass.getDeclaredField("client");
                    field.setAccessible(true);
                    client = field.get(screen);
                } catch (Exception e2) {
                    // Try getInstance
                    Class<?> mcClass = Class.forName("net.minecraft.client.MinecraftClient");
                    client = mcClass.getMethod("getInstance").invoke(null);
                }
            }

            if (client == null) {
                LOGGER.error("Could not get MinecraftClient instance");
                return;
            }

            // Create and show the RetroModScreen (uses reflection internally)
            com.retromod.gui.RetroModScreen retroScreen =
                new com.retromod.gui.RetroModScreen(client, screen);
            retroScreen.open();

        } catch (Exception e) {
            LOGGER.error("Could not open RetroMod manager", e);
        }
    }

    /**
     * Get an int field by name, trying public and declared variants.
     */
    private static int getIntField(Object obj, Class<?> clazz, String fieldName) {
        try {
            var field = clazz.getField(fieldName);
            return field.getInt(obj);
        } catch (Exception e) {
            try {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(obj);
            } catch (Exception e2) {
                return fieldName.equals("width") ? 854 : 480;
            }
        }
    }

    /**
     * Find a method by name (first match, any parameter types).
     */
    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }
}
