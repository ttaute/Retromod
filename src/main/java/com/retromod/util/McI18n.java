/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Reflective wrapper around Minecraft's {@code Text.translatable() / Component.translatable()},
 * since Retromod has no compile-time MC classpath. A Text from {@link #translatable(String)}
 * resolves against the in-game language at render time. When the translatable API isn't
 * reachable we fall back to {@code Text.literal(key)}, so the user sees the raw key (MC's
 * own behavior for missing translations) rather than a crash.
 */
public final class McI18n {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static volatile Class<?> textClass;
    private static volatile Method translatableMethod;
    private static volatile Method literalMethod;
    private static volatile boolean resolved;

    private McI18n() {}

    /**
     * Build a Text/Component resolved against the current MC language at render time.
     * Falls back to a literal Text of the key, or null if neither path is reachable.
     *
     * @param translationKey the translation key
     */
    public static Object translatable(String translationKey) {
        resolve();
        try {
            if (translatableMethod != null) {
                return translatableMethod.invoke(null, translationKey);
            }
            if (literalMethod != null) {
                return literalMethod.invoke(null, translationKey);
            }
        } catch (Exception e) {
            LOGGER.debug("McI18n.translatable({}) failed: {}", translationKey, e.getMessage());
        }
        return null;
    }

    /** Build a literal Text/Component for text that should never be translated. */
    public static Object literal(String text) {
        resolve();
        try {
            if (literalMethod != null) {
                return literalMethod.invoke(null, text);
            }
        } catch (Exception e) {
            LOGGER.debug("McI18n.literal({}) failed: {}", text, e.getMessage());
        }
        return null;
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (McI18n.class) {
            if (resolved) return;
            textClass = McReflect.findClass(
                "net.minecraft.text.Text",
                "net.minecraft.network.chat.Component"
            );
            if (textClass != null) {
                translatableMethod = McReflect.findMethod(textClass,
                        new Class[]{String.class}, "translatable");
                literalMethod = McReflect.findMethod(textClass,
                        new Class[]{String.class}, "literal");
                // older MC has no Text.literal, try Text.of
                if (literalMethod == null) {
                    literalMethod = McReflect.findMethod(textClass,
                            new Class[]{String.class}, "of");
                }
            }
            resolved = true;
        }
    }
}
