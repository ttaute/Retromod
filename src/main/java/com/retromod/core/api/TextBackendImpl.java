/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core.api;

import com.retromod.api.text.TextBackend;

import java.lang.reflect.Method;

/**
 * Concrete implementation of {@link TextBackend} that creates Minecraft text components
 * via reflection.
 *
 * <h3>The Minecraft text API: a history of breaking changes</h3>
 * <p>Minecraft's text system has been rewritten multiple times. Here's the timeline:</p>
 *
 * <table>
 *   <tr><th>MC Version</th><th>How to create literal text</th><th>How to create translatable text</th></tr>
 *   <tr>
 *     <td>Pre-1.13 (Forge)</td>
 *     <td>{@code new TextComponentString("x")}</td>
 *     <td>{@code new TextComponentTranslation("key")}</td>
 *   </tr>
 *   <tr>
 *     <td>1.14–1.19 (Fabric)</td>
 *     <td>{@code new LiteralText("x")}</td>
 *     <td>{@code new TranslatableText("key")}</td>
 *   </tr>
 *   <tr>
 *     <td>1.19.1+ (modern)</td>
 *     <td>{@code Component.literal("x")}</td>
 *     <td>{@code Component.translatable("key")}</td>
 *   </tr>
 *   <tr>
 *     <td>26.1</td>
 *     <td>{@code Component.literal("x")} (Mojang names, same API)</td>
 *     <td>{@code Component.translatable("key")} (Mojang names, same API)</td>
 *   </tr>
 * </table>
 *
 * <h3>Our approach</h3>
 * <p>Since RetroMod targets MC 26.1 (and recent versions), we use the modern
 * {@code Component.literal()} / {@code Component.translatable()} / {@code Component.empty()}
 * static factory methods. These are the canonical approach since 1.19.1.</p>
 *
 * <p>We access these methods via reflection because the API module cannot import Minecraft
 * classes. The reflected {@link Method} objects are cached after first lookup to avoid
 * repeated reflection overhead.</p>
 *
 * <h3>Fallback strategy</h3>
 * <p>If the primary reflection path fails (e.g., on an older MC version or in a test
 * environment), we fall back to returning plain strings. This matches what the existing
 * {@link com.retromod.polyfill.minecraft.text.embedded.LiteralTextShim} does — it returns
 * the raw string content as a fallback.</p>
 *
 * <h3>ServiceLoader registration</h3>
 * <p>This class is registered in
 * {@code META-INF/services/com.retromod.api.text.TextBackend} so that
 * {@link java.util.ServiceLoader} discovers it automatically.</p>
 *
 * @see TextBackend
 * @see com.retromod.api.text.TextFactory
 * @since 0.1.0
 */
public class TextBackendImpl implements TextBackend {

    /*
     * Cached reflection handles for Minecraft's Component class methods.
     *
     * We use volatile + double-checked locking (in the init() method) to ensure
     * thread-safe lazy initialization. These are set once and never change.
     *
     * Why cache Method objects?
     * - Class.forName() and Class.getMethod() are relatively slow operations that
     *   involve classloader lookups and security checks.
     * - Text creation is called frequently (every frame for GUIs, every chat message, etc.)
     * - Caching the Method objects means we only pay the reflection cost once.
     */

    /** Cached reference to Component.literal(String) */
    private static volatile Method literalMethod;

    /** Cached reference to Component.translatable(String, Object[]) */
    private static volatile Method translatableMethod;

    /** Cached reference to Component.empty() */
    private static volatile Method emptyMethod;

    /**
     * Flag indicating whether reflection initialization has been attempted.
     * If true and the methods are still null, initialization failed and we
     * should use the fallback path without retrying.
     */
    private static volatile boolean initialized;

    /**
     * Creates a literal text component by calling {@code Component.literal(text)}.
     *
     * <p>At runtime, this produces a {@code MutableComponent} — the mutable subtype of
     * Minecraft's {@code Component} interface. The returned object can be cast to
     * {@code Component} and used anywhere MC expects a text component.</p>
     *
     * @param text the literal string to display
     * @return a Minecraft MutableComponent, or the raw string as a fallback
     */
    @Override
    public Object createLiteral(String text) {
        // Ensure reflection handles are initialized
        ensureInitialized();

        if (literalMethod != null) {
            try {
                // Call Component.literal(text) — a static method that returns MutableComponent
                return literalMethod.invoke(null, text);
            } catch (Exception e) {
                // Reflection call failed at runtime (shouldn't happen if initialization
                // succeeded, but defensive coding is important in modded environments
                // where classloaders can behave unexpectedly)
            }
        }

        // Fallback: return the raw string. This is the same approach used by
        // LiteralTextShim.toComponent() when reflection fails.
        return text;
    }

    /**
     * Creates a translatable text component by calling
     * {@code Component.translatable(key, args)}.
     *
     * <p>The translation key is looked up in the player's active language file at render
     * time. If the key is not found, the raw key string is displayed instead.</p>
     *
     * @param key  the translation key
     * @param args optional format arguments
     * @return a Minecraft MutableComponent, or the raw key as a fallback
     */
    @Override
    public Object createTranslatable(String key, Object... args) {
        ensureInitialized();

        if (translatableMethod != null) {
            try {
                // Call Component.translatable(key, args) — note that args is already
                // an Object[] due to the varargs declaration, which matches the MC method
                // signature: translatable(String, Object...)
                return translatableMethod.invoke(null, key, args);
            } catch (Exception e) {
                // Reflection call failed — fall through to fallback
            }
        }

        // Fallback: return the translation key. At least the developer can see which
        // key was being used, which helps with debugging.
        return key;
    }

    /**
     * Creates an empty text component by calling {@code Component.empty()}.
     *
     * @return a Minecraft MutableComponent with no text, or an empty string as a fallback
     */
    @Override
    public Object createEmpty() {
        ensureInitialized();

        if (emptyMethod != null) {
            try {
                // Call Component.empty() — a static method with no arguments
                return emptyMethod.invoke(null);
            } catch (Exception e) {
                // Reflection call failed — fall through to fallback
            }
        }

        // Fallback: return an empty string
        return "";
    }

    // =========================================================================
    // Reflection initialization
    // =========================================================================

    /**
     * Ensures that the reflection handles have been initialized.
     *
     * <p>This method uses double-checked locking: the fast path checks the
     * {@code initialized} flag without synchronization. If not yet initialized,
     * we enter a synchronized block and check again (another thread may have
     * initialized while we were waiting for the lock).</p>
     */
    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (TextBackendImpl.class) {
            if (initialized) {
                return;
            }

            initReflection();
            initialized = true;
        }
    }

    /**
     * Performs the actual reflection lookup for Minecraft's Component class methods.
     *
     * <p>We look up three static methods on {@code net.minecraft.network.chat.Component}:</p>
     * <ul>
     *   <li>{@code literal(String)} — creates a literal text component</li>
     *   <li>{@code translatable(String, Object[])} — creates a translatable component</li>
     *   <li>{@code empty()} — creates an empty component</li>
     * </ul>
     *
     * <p>If the Component class doesn't exist (e.g., in a test environment without MC on
     * the classpath), all method references remain null and the fallback paths are used.</p>
     */
    private static void initReflection() {
        try {
            /*
             * Load the Component class. In MC 26.1 (with Mojang names), this is:
             *   net.minecraft.network.chat.Component
             *
             * This is the same class path used by the existing LiteralTextShim and
             * TranslatableContentsShim in the polyfill package.
             */
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");

            // --- Component.literal(String) ---
            // Returns: MutableComponent
            // Available since: MC 1.19.1
            // This replaced the old LiteralText constructor.
            try {
                literalMethod = componentClass.getMethod("literal", String.class);
            } catch (NoSuchMethodException e) {
                // Method doesn't exist — might be on an older MC version
                // where Component.literal() hasn't been added yet.
                // The fallback path will handle this gracefully.
            }

            // --- Component.translatable(String, Object...) ---
            // Returns: MutableComponent
            // Available since: MC 1.19.1
            // The varargs Object... compiles to Object[] in bytecode.
            try {
                translatableMethod = componentClass.getMethod("translatable",
                        String.class, Object[].class);
            } catch (NoSuchMethodException e) {
                // Method doesn't exist on this MC version
            }

            // --- Component.empty() ---
            // Returns: MutableComponent
            // Available since: MC 1.19.1
            // Creates a component with empty string content.
            try {
                emptyMethod = componentClass.getMethod("empty");
            } catch (NoSuchMethodException e) {
                // Method doesn't exist on this MC version
            }

        } catch (ClassNotFoundException e) {
            // The Component class doesn't exist at all. This happens when:
            // - Running in unit tests without MC on the classpath
            // - Running in the CLI tool
            // - Running in any standalone Java environment
            // All method references stay null, and the fallback paths will be used.
        }
    }
}
