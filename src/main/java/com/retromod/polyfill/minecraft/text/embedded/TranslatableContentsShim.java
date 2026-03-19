/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.text.embedded;

import java.lang.reflect.Constructor;

/**
 * Factory shim for TranslatableContents constructor changes.
 *
 * Pre-1.19.3: TranslatableContents(String key) or TranslatableContents(String key, Object[] args)
 * Post-1.19.3: TranslatableContents(String key, String fallback, Object[] args)
 *
 * This class provides static factory methods that bridge the old 1-arg and 2-arg
 * constructors to the new 3-arg constructor using reflection (since we can't
 * compile against Minecraft classes directly).
 */
public class TranslatableContentsShim {

    private static volatile Constructor<?> threeArgConstructor;

    /**
     * Factory for old 1-arg constructor: new TranslatableContents(key)
     * Maps to: new TranslatableContents(key, null, new Object[0])
     */
    public static Object create(String key) {
        return createWithArgs(key, new Object[0]);
    }

    /**
     * Factory for old 2-arg constructor: new TranslatableContents(key, args)
     * Maps to: new TranslatableContents(key, null, args)
     */
    public static Object createWithArgs(String key, Object[] args) {
        try {
            if (threeArgConstructor == null) {
                synchronized (TranslatableContentsShim.class) {
                    if (threeArgConstructor == null) {
                        Class<?> clazz = Class.forName(
                            "net.minecraft.network.chat.contents.TranslatableContents");
                        threeArgConstructor = clazz.getConstructor(
                            String.class, String.class, Object[].class);
                    }
                }
            }
            return threeArgConstructor.newInstance(key, null, args);
        } catch (Exception e) {
            throw new RuntimeException(
                "RetroMod: Failed to create TranslatableContents for key '" + key + "'", e);
        }
    }
}
