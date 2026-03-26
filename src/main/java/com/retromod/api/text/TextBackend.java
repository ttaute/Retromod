/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.api.text;

/**
 * SPI (Service Provider Interface) for creating Minecraft text components.
 *
 * <h3>Why does this exist?</h3>
 * <p>Minecraft's text/chat API has been rewritten multiple times across versions:</p>
 * <ul>
 *   <li><b>Pre-1.13 (Forge era):</b> {@code new TextComponentString("Hello")} and
 *       {@code new TextComponentTranslation("key")}</li>
 *   <li><b>1.14–1.19 (Fabric era):</b> {@code new LiteralText("Hello")} and
 *       {@code new TranslatableText("key")}</li>
 *   <li><b>1.19.1+ (modern):</b> {@code Component.literal("Hello")} and
 *       {@code Component.translatable("key")} — the old constructors were removed</li>
 *   <li><b>26.1:</b> Same as 1.19.1+ but with fully deobfuscated Mojang names
 *       (no more intermediary or SRG names)</li>
 * </ul>
 *
 * <p>This constant churn means a mod developer cannot simply call
 * {@code new LiteralText("Hello")} and expect it to work on all MC versions.
 * The text creation code needs to be abstracted behind an interface that each
 * environment can implement differently.</p>
 *
 * <h3>Design</h3>
 * <p>Like {@link com.retromod.api.platform.PlatformBackend}, this interface is discovered
 * at runtime via {@link java.util.ServiceLoader}. The implementation
 * ({@code com.retromod.core.api.TextBackendImpl}) uses reflection to call the correct
 * Minecraft methods without any compile-time dependency on MC classes.</p>
 *
 * <p>All methods return {@code Object} because the actual Minecraft {@code Component} type
 * is not available at compile time in the API module. At runtime, the returned object will
 * be a valid Minecraft {@code MutableComponent} that can be cast to {@code Component}
 * and used anywhere Minecraft expects a text component.</p>
 *
 * @see TextFactory
 * @see java.util.ServiceLoader
 * @since 0.1.0
 */
public interface TextBackend {

    /**
     * Creates a literal (plain text) component.
     *
     * <p>A literal component displays the given string exactly as-is, with no translation
     * or formatting processing. At runtime, this typically calls
     * {@code Component.literal(text)} on modern MC versions.</p>
     *
     * @param text the text content to display; must not be null
     * @return a Minecraft {@code MutableComponent} as an Object
     * @since 0.1.0
     */
    Object createLiteral(String text);

    /**
     * Creates a translatable component with optional format arguments.
     *
     * <p>A translatable component looks up the given key in the player's current language
     * file and substitutes any format arguments. For example,
     * {@code createTranslatable("item.minecraft.diamond", /* no args * /)} displays the
     * localized name for a diamond.</p>
     *
     * <p>At runtime, this typically calls {@code Component.translatable(key, args)} on
     * modern MC versions.</p>
     *
     * @param key  the translation key (e.g., {@code "item.minecraft.diamond"}); must not be null
     * @param args optional format arguments to substitute into the translated string
     * @return a Minecraft {@code MutableComponent} as an Object
     * @since 0.1.0
     */
    Object createTranslatable(String key, Object... args);

    /**
     * Creates an empty text component (a component with no text content).
     *
     * <p>Empty components are useful as containers: you can append child components to them
     * to build complex formatted text. At runtime, this typically calls
     * {@code Component.empty()} on modern MC versions.</p>
     *
     * @return a Minecraft {@code MutableComponent} as an Object
     * @since 0.1.0
     */
    Object createEmpty();
}
