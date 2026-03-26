/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.api.text;

import java.util.ServiceLoader;

/**
 * Static facade for creating Minecraft text components in a version-independent way.
 *
 * <h3>The problem</h3>
 * <p>Creating text in Minecraft has changed dramatically across versions. A mod written
 * for MC 1.16 might use {@code new LiteralText("Hello")}, but that class was removed
 * in 1.19.1. A mod targeting 1.19+ would use {@code Component.literal("Hello")}, but
 * that method doesn't exist in older versions. This makes writing cross-version code
 * extremely painful.</p>
 *
 * <h3>The solution</h3>
 * <p>TextFactory provides a single, stable API that works on all supported MC versions.
 * Behind the scenes, it delegates to a {@link TextBackend} implementation that uses
 * reflection to call the correct Minecraft methods for the current version.</p>
 *
 * <h3>Usage examples</h3>
 * <pre>{@code
 * // Instead of: new LiteralText("Hello")           (pre-1.19.1)
 * // Instead of: Component.literal("Hello")          (1.19.1+)
 * // Just use:
 * Object text = TextFactory.literal("Hello, world!");
 *
 * // Instead of: new TranslatableText("item.minecraft.diamond")  (pre-1.19.1)
 * // Instead of: Component.translatable("item.minecraft.diamond") (1.19.1+)
 * // Just use:
 * Object item = TextFactory.translatable("item.minecraft.diamond");
 *
 * // With format arguments:
 * Object msg = TextFactory.translatable("commands.give.success", playerName, count);
 *
 * // Empty component (useful as a container for appending children):
 * Object container = TextFactory.empty();
 * }</pre>
 *
 * <h3>Return type</h3>
 * <p>All methods return {@code Object} because the Minecraft {@code Component} class is
 * not available at compile time in the API module. At runtime, the returned object is a
 * valid Minecraft {@code MutableComponent} that can be cast and used normally:</p>
 * <pre>{@code
 * // In code that has access to MC classes:
 * Component text = (Component) TextFactory.literal("Hello");
 * player.sendSystemMessage(text);
 * }</pre>
 *
 * @see TextBackend
 * @since 0.1.0
 */
public final class TextFactory {

    /*
     * The cached backend instance, loaded lazily via ServiceLoader.
     * Volatile for safe publication across threads (same pattern as Platform).
     */
    private static volatile TextBackend backend;

    /*
     * Private constructor prevents instantiation. This is a static utility class.
     */
    private TextFactory() {
        throw new AssertionError("TextFactory is a static utility class and cannot be instantiated");
    }

    /**
     * Creates a literal (plain text) component.
     *
     * <p>The returned object is a Minecraft {@code MutableComponent} at runtime.
     * The text is displayed exactly as given, with no translation or formatting.</p>
     *
     * @param text the text to display; must not be null
     * @return a text component containing the given string
     * @since 0.1.0
     */
    public static Object literal(String text) {
        return getBackend().createLiteral(text);
    }

    /**
     * Creates a translatable component that is localized to the player's language.
     *
     * <p>The key is looked up in the active language file (e.g., {@code en_us.json}).
     * Any additional arguments are substituted into the translated string using
     * {@link String#format}-style placeholders ({@code %s}, {@code %d}, etc.).</p>
     *
     * @param key  the translation key (e.g., {@code "block.minecraft.stone"})
     * @param args optional format arguments for the translated string
     * @return a translatable text component
     * @since 0.1.0
     */
    public static Object translatable(String key, Object... args) {
        return getBackend().createTranslatable(key, args);
    }

    /**
     * Creates an empty text component.
     *
     * <p>Empty components serve as containers: you can append styled child components
     * to build rich formatted text without any root text of its own.</p>
     *
     * @return an empty text component
     * @since 0.1.0
     */
    public static Object empty() {
        return getBackend().createEmpty();
    }

    /**
     * Loads and caches the {@link TextBackend} implementation via ServiceLoader.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization, identical to
     * the pattern in {@link com.retromod.api.platform.Platform}. See that class for a
     * detailed explanation of why this pattern is used.</p>
     *
     * <p>If no backend implementation is found on the classpath (e.g., in unit tests or
     * when the full RetroMod runtime is not present), a fallback backend is used that
     * returns plain strings instead of Minecraft Component objects. This ensures the API
     * never throws an exception — callers just get degraded functionality.</p>
     *
     * @return the backend instance, never null
     */
    private static TextBackend getBackend() {
        // Fast path: already loaded
        TextBackend b = backend;
        if (b != null) {
            return b;
        }

        // Slow path: first call
        synchronized (TextFactory.class) {
            b = backend;
            if (b != null) {
                return b;
            }

            // Load the backend via ServiceLoader, falling back to a simple implementation
            // that returns plain strings. This fallback ensures the API is usable even
            // without the full RetroMod runtime (e.g., in tests).
            b = ServiceLoader.load(TextBackend.class)
                    .findFirst()
                    .orElse(new FallbackTextBackend());

            backend = b;
            return b;
        }
    }

    /**
     * Fallback backend used when no real implementation is on the classpath.
     *
     * <p>Returns plain {@link String} objects instead of Minecraft Components. This is
     * safe because Minecraft's text rendering can often handle raw strings, and in
     * environments where this fallback is used (tests, CLI), Minecraft rendering isn't
     * happening anyway.</p>
     */
    private static final class FallbackTextBackend implements TextBackend {
        @Override
        public Object createLiteral(String text) {
            // Without MC classes, the best we can do is return the raw string
            return text;
        }

        @Override
        public Object createTranslatable(String key, Object... args) {
            // Return the translation key itself — at least it's identifiable in logs
            return key;
        }

        @Override
        public Object createEmpty() {
            // An empty string is the closest analog to an empty Component
            return "";
        }
    }
}
