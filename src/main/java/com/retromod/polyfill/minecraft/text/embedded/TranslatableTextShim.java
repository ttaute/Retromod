/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.text.embedded;

/**
 * Reimplementation of TranslatableText (removed 1.19.1).
 * Delegates to Component.translatable() via reflection at runtime.
 */
public class TranslatableTextShim {
    private final String key;
    private final Object[] args;

    public TranslatableTextShim(String key, Object... args) {
        this.key = key;
        this.args = args;
    }

    public String getKey() {
        return key;
    }

    public Object toComponent() {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            return componentClass.getMethod("translatable", String.class, Object[].class).invoke(null, key, args);
        } catch (Exception e) {
            return key; // fallback
        }
    }
}
