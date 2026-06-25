/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge.embedded;

import java.lang.reflect.*;
import java.util.*;

/**
 * Bridges the old String KeyMapping category onto the KeyMapping.Category record introduced in
 * NeoForge 1.21.9 (the constructor's 4th arg went from String to Category).
 */
public final class KeyMappingShim {

    private static final Map<String, Object> categoryCache = new HashMap<>();
    private static Class<?> categoryClass;
    private static Class<?> keyMappingClass;
    private static Constructor<?> newConstructor;
    private static Constructor<?> categoryConstructor;
    private static boolean initialized = false;

    private KeyMappingShim() {}

    /** Create a KeyMapping from the old String-category API. */
    public static Object create(String translationKey, Object inputType, int keyCode, String categoryString) {
        initialize();

        try {
            Object category = getOrCreateCategory(categoryString);
            return newConstructor.newInstance(translationKey, inputType, keyCode, category);
        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to create KeyMapping", e);
        }
    }
    
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            keyMappingClass = Class.forName("net.minecraft.client.KeyMapping");

            for (Class<?> inner : keyMappingClass.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Category")) {
                    categoryClass = inner;
                    break;
                }
            }

            if (categoryClass == null) {
                throw new ClassNotFoundException("KeyMapping.Category");
            }

            // Category constructor takes a ResourceLocation
            for (Constructor<?> c : categoryClass.getConstructors()) {
                if (c.getParameterCount() == 1) {
                    categoryConstructor = c;
                    break;
                }
            }

            for (Constructor<?> c : keyMappingClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 4 && params[3] == categoryClass) {
                    newConstructor = c;
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to initialize KeyMappingShim", e);
        }
    }
    
    /** Cached so a repeated category string doesn't re-register. */
    private static Object getOrCreateCategory(String categoryString) throws Exception {
        if (categoryCache.containsKey(categoryString)) {
            return categoryCache.get(categoryString);
        }

        String namespace;
        String path;

        if (categoryString.contains(":")) {
            String[] parts = categoryString.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        } else if (categoryString.startsWith("key.categories.")) {
            String rest = categoryString.substring("key.categories.".length());
            namespace = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : "minecraft";
            path = rest.contains(".") ? rest.substring(rest.indexOf('.') + 1) : rest;
        } else {
            namespace = "retromod_compat";
            path = categoryString.replace('.', '_').replace(' ', '_').toLowerCase();
        }

        Object identifier = com.retromod.shim.fabric.embedded.IdentifierShim.of(namespace, path);
        Object category = categoryConstructor.newInstance(identifier);

        categoryCache.put(categoryString, category);
        registerCategory(category);

        return category;
    }

    // Newer versions auto-register on the KeyMapping constructor, so caching is enough.
    private static void registerCategory(Object category) {
        try {
        } catch (Exception e) {
            System.err.println("Retromod: Could not register key category: " + e);
        }
    }

    public static Set<String> getRegisteredCategories() {
        return Collections.unmodifiableSet(categoryCache.keySet());
    }
}
