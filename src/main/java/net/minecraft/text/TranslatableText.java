/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill for net.minecraft.text.TranslatableText.
 *
 * This class was removed in Minecraft 1.19, replaced by Text.translatable().
 * Delegates to the real Text.translatable() via reflection, producing
 * a genuine MC Text object with actual translation support.
 */
package net.minecraft.text;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Full reimplementation of the removed TranslatableText class.
 *
 * Delegates to Text.translatable() (1.19+) via reflection to produce real
 * MutableText objects. The resulting Text uses MC's I18n for translations.
 */
public class TranslatableText {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private static volatile Method textTranslatableMethod;
    private static volatile Method textTranslatableArgsMethod;
    private static volatile Method getStringMethod;
    private static volatile Method asOrderedTextMethod;
    private static volatile Method appendMethod;
    private static volatile Method styledMethod;
    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean hasModernApi = false;

    private final String key;
    private final Object[] args;
    private Object realTextObject;
    private final List<Object> siblings = new ArrayList<>();

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            try {
                textTranslatableMethod = textClass.getMethod("translatable", String.class);
            } catch (Exception ignored) {}
            try {
                textTranslatableArgsMethod = textClass.getMethod("translatable", String.class, Object[].class);
            } catch (Exception ignored) {}
            hasModernApi = (textTranslatableMethod != null || textTranslatableArgsMethod != null);
            if (hasModernApi) {
                Method refMethod = textTranslatableMethod != null ? textTranslatableMethod : textTranslatableArgsMethod;
                Class<?> returnType = refMethod.getReturnType();
                try { getStringMethod = returnType.getMethod("getString"); } catch (Exception ignored) {}
                try { asOrderedTextMethod = returnType.getMethod("asOrderedText"); } catch (Exception ignored) {}
                try { appendMethod = returnType.getMethod("append", textClass); } catch (Exception ignored) {}
                try { styledMethod = returnType.getMethod("styled", java.util.function.UnaryOperator.class); } catch (Exception ignored) {}
            }
            LOGGER.fine("[RetroMod] TranslatableText polyfill: delegating to Text.translatable()");
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] TranslatableText polyfill: standalone mode");
        }
    }

    public TranslatableText(String key, Object... args) {
        this.key = key != null ? key : "";
        this.args = args != null ? args : new Object[0];
        if (!reflectionInitialized) initReflection();
        if (hasModernApi) {
            try {
                if (this.args.length > 0 && textTranslatableArgsMethod != null) {
                    this.realTextObject = textTranslatableArgsMethod.invoke(null, this.key, this.args);
                } else if (textTranslatableMethod != null) {
                    this.realTextObject = textTranslatableMethod.invoke(null, this.key);
                }
            } catch (Exception e) {
                LOGGER.fine("[RetroMod] TranslatableText: Text.translatable() failed: " + e.getMessage());
            }
        }
    }

    /** Returns the real underlying MutableText object. */
    public Object getRealTextObject() { return realTextObject; }

    /** Returns the translation key. */
    public String getKey() { return key; }

    /** Returns the format arguments. */
    public Object[] getArgs() { return args; }

    /** Returns the translated string using MC's I18n system. */
    public String getString() {
        if (realTextObject != null && getStringMethod != null) {
            try { return (String) getStringMethod.invoke(realTextObject); } catch (Exception ignored) {}
        }
        // Fallback: try I18n directly
        try {
            Class<?> i18nClass = Class.forName("net.minecraft.client.resource.language.I18n");
            Method translateMethod = i18nClass.getMethod("translate", String.class, Object[].class);
            return (String) translateMethod.invoke(null, key, args);
        } catch (Exception ignored) {}
        return key;
    }

    /** Returns the OrderedText from the real Text object. */
    public Object asOrderedText() {
        if (realTextObject != null && asOrderedTextMethod != null) {
            try { return asOrderedTextMethod.invoke(realTextObject); } catch (Exception ignored) {}
        }
        return null;
    }

    /** Creates a copy with the same key and args. */
    public TranslatableText copy() { return new TranslatableText(key, args.clone()); }

    /** Appends another text component as a sibling. */
    public TranslatableText append(Object text) {
        if (realTextObject != null && appendMethod != null) {
            try {
                Object realOther = text;
                if (text instanceof LiteralText) {
                    Object u = ((LiteralText) text).getRealTextObject();
                    if (u != null) realOther = u;
                } else if (text instanceof TranslatableText) {
                    Object u = ((TranslatableText) text).getRealTextObject();
                    if (u != null) realOther = u;
                }
                appendMethod.invoke(realTextObject, realOther);
            } catch (Exception ignored) {}
        }
        siblings.add(text);
        return this;
    }

    public List<Object> getSiblings() { return siblings; }

    public TranslatableText styled(java.util.function.UnaryOperator<?> styleOperator) {
        if (realTextObject != null && styledMethod != null) {
            try { styledMethod.invoke(realTextObject, styleOperator); } catch (Exception ignored) {}
        }
        return this;
    }

    @Override
    public String toString() { return "TranslatableText{key='" + key + "'}"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranslatableText)) return false;
        return key.equals(((TranslatableText) o).key);
    }

    @Override
    public int hashCode() { return key.hashCode(); }
}
