/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.text.embedded;

/**
 * Reimplementation of LiteralText (removed 1.19.1).
 * Delegates to Component.literal() via reflection at runtime.
 */
public class LiteralTextShim {
    private final String content;

    public LiteralTextShim(String content) {
        this.content = content;
    }

    public String getString() {
        return content;
    }

    /**
     * Creates a Component via reflection.
     * At runtime this calls Component.literal(content).
     */
    public Object toComponent() {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            return componentClass.getMethod("literal", String.class).invoke(null, content);
        } catch (Exception e) {
            return content; // fallback
        }
    }
}
