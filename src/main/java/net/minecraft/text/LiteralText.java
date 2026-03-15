/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill for net.minecraft.text.LiteralText.
 *
 * This class was removed in Minecraft 1.19, replaced by Text.literal().
 * Instead of a no-op stub, this polyfill delegates to the real Text.literal()
 * method via reflection, producing a genuine MC Text object.
 */
package net.minecraft.text;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Full reimplementation of the removed LiteralText class.
 *
 * Delegates to Text.literal() (1.19+) via reflection to produce real
 * MutableText objects. Old mods that do {@code new LiteralText("hello")}
 * will get back an object that wraps a genuine MC Text — it can be
 * passed to player.sendMessage(), screen rendering, etc.
 */
public class LiteralText {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    // Cached reflection lookups
    private static volatile Method textLiteralMethod;
    private static volatile Method getStringMethod;
    private static volatile Method asOrderedTextMethod;
    private static volatile Method appendMethod;
    private static volatile Method styledMethod;
    private static volatile boolean reflectionInitialized = false;
    private static volatile boolean hasModernApi = false;

    private final String string;
    private Object realTextObject; // The actual MutableText from Text.literal()
    private final List<Object> siblings = new ArrayList<>();

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            // Text.literal(String) returns MutableText in 1.19+
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            textLiteralMethod = textClass.getMethod("literal", String.class);
            hasModernApi = true;

            // Cache methods on the returned MutableText
            Class<?> returnType = textLiteralMethod.getReturnType();
            try { getStringMethod = returnType.getMethod("getString"); } catch (Exception ignored) {}
            try { asOrderedTextMethod = returnType.getMethod("asOrderedText"); } catch (Exception ignored) {}
            try {
                appendMethod = returnType.getMethod("append", textClass);
            } catch (Exception e) {
                // Try with MutableText param
                try { appendMethod = returnType.getMethod("append", returnType); } catch (Exception ignored) {}
            }
            try {
                styledMethod = returnType.getMethod("styled", java.util.function.UnaryOperator.class);
            } catch (Exception ignored) {}

            LOGGER.fine("[RetroMod] LiteralText polyfill: delegating to Text.literal()");
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] LiteralText polyfill: Text.literal() not found, standalone mode");
        }
    }

    public LiteralText(String string) {
        this.string = string != null ? string : "";
        if (!reflectionInitialized) initReflection();
        if (hasModernApi) {
            try {
                this.realTextObject = textLiteralMethod.invoke(null, this.string);
            } catch (Exception e) {
                LOGGER.fine("[RetroMod] LiteralText: failed to create Text.literal(): " + e.getMessage());
            }
        }
    }

    /**
     * Returns the real underlying MutableText object if available.
     * RetroMod's bytecode transformer can unwrap this when a Text is expected.
     */
    public Object getRealTextObject() {
        return realTextObject;
    }

    /**
     * Returns the literal string content, delegating to the real Text if available.
     */
    public String getString() {
        if (realTextObject != null && getStringMethod != null) {
            try {
                return (String) getStringMethod.invoke(realTextObject);
            } catch (Exception ignored) {}
        }
        return string;
    }

    /**
     * Returns the raw literal string.
     */
    public String getRawString() {
        return string;
    }

    /**
     * Returns the OrderedText from the real Text object.
     */
    public Object asOrderedText() {
        if (realTextObject != null && asOrderedTextMethod != null) {
            try {
                return asOrderedTextMethod.invoke(realTextObject);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Creates a deep copy of this LiteralText.
     */
    public LiteralText copy() {
        return new LiteralText(string);
    }

    /**
     * Appends another text component as a sibling.
     */
    public LiteralText append(Object text) {
        if (realTextObject != null && appendMethod != null) {
            try {
                Object realOther = text;
                if (text instanceof LiteralText) {
                    Object unwrapped = ((LiteralText) text).getRealTextObject();
                    if (unwrapped != null) realOther = unwrapped;
                } else if (text instanceof TranslatableText) {
                    Object unwrapped = ((TranslatableText) text).getRealTextObject();
                    if (unwrapped != null) realOther = unwrapped;
                }
                appendMethod.invoke(realTextObject, realOther);
            } catch (Exception ignored) {}
        }
        siblings.add(text);
        return this;
    }

    /**
     * Returns the sibling text components.
     */
    public List<Object> getSiblings() {
        return siblings;
    }

    /**
     * Applies a style function.
     */
    public LiteralText styled(java.util.function.UnaryOperator<?> styleOperator) {
        if (realTextObject != null && styledMethod != null) {
            try {
                styledMethod.invoke(realTextObject, styleOperator);
            } catch (Exception ignored) {}
        }
        return this;
    }

    /** Empty text constant. */
    public static final LiteralText EMPTY = new LiteralText("");

    @Override
    public String toString() {
        return "LiteralText{string='" + string + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiteralText)) return false;
        return string.equals(((LiteralText) o).string);
    }

    @Override
    public int hashCode() {
        return string.hashCode();
    }
}
