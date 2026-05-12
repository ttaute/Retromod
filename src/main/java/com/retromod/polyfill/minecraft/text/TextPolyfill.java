/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.text;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for Minecraft text/chat API changes across multiple versions.
 *
 * The text API underwent two major restructurings:
 * 1. The Flattening (1.13): net.minecraft.util.text.* -> net.minecraft.network.chat.*
 *    - ITextComponent -> Component
 *    - TextComponentString -> direct calls to Component.literal()
 *    - TextFormatting -> ChatFormatting (moved to root package)
 *
 * 2. Fabric 1.19.1: net.minecraft.text.LiteralText/TranslatableText removed
 *    - LiteralText -> Component.literal() (static factory)
 *    - TranslatableText -> Component.translatable() (static factory)
 *    - Text.Serializer -> Component.Serializer
 *
 * This provider handles class redirects for both Forge-era and Fabric-era
 * text class names, plus embedded shims for LiteralText and TranslatableText
 * that delegate to Component's static factories via reflection.
 *
 * Covers:
 * - ITextComponent, TextComponentString, TextComponentTranslation (Forge pre-1.13)
 * - Style, TextFormatting, ClickEvent, HoverEvent (Forge pre-1.13)
 * - LiteralText, TranslatableText, Text.Serializer (Fabric pre-1.19.1)
 */
public class TextPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Text API Renames";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // Forge pre-Flattening text classes
            "net/minecraft/util/text/ITextComponent",
            "net/minecraft/util/text/TextComponentString",
            "net/minecraft/util/text/TextComponentTranslation",
            "net/minecraft/util/text/Style",
            "net/minecraft/util/text/TextFormatting",
            "net/minecraft/util/text/event/ClickEvent",
            "net/minecraft/util/text/event/HoverEvent",

            // Fabric pre-1.19.1 text classes
            "net/minecraft/text/LiteralText",
            "net/minecraft/text/TranslatableText",
            "net/minecraft/text/Text$Serializer"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "com.retromod.polyfill.minecraft.text.embedded.LiteralTextShim",
            "com.retromod.polyfill.minecraft.text.embedded.TranslatableTextShim",
            "com.retromod.polyfill.minecraft.text.embedded.TranslatableContentsShim"
        };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // ---- Forge pre-Flattening (1.13) class redirects ----

        // ITextComponent -> Component
        transformer.registerClassRedirect(
            "net/minecraft/util/text/ITextComponent",
            "net/minecraft/network/chat/Component");

        // TextComponentString -> Component (constructor calls need shim,
        // but class references redirect to Component)
        transformer.registerClassRedirect(
            "net/minecraft/util/text/TextComponentString",
            "net/minecraft/network/chat/Component");

        // TextComponentTranslation -> Component
        transformer.registerClassRedirect(
            "net/minecraft/util/text/TextComponentTranslation",
            "net/minecraft/network/chat/Component");

        // Style stayed in the same conceptual place but moved packages
        transformer.registerClassRedirect(
            "net/minecraft/util/text/Style",
            "net/minecraft/network/chat/Style");

        // TextFormatting moved to root package as ChatFormatting
        transformer.registerClassRedirect(
            "net/minecraft/util/text/TextFormatting",
            "net/minecraft/ChatFormatting");

        // Event classes moved from util.text.event to network.chat
        transformer.registerClassRedirect(
            "net/minecraft/util/text/event/ClickEvent",
            "net/minecraft/network/chat/ClickEvent");
        transformer.registerClassRedirect(
            "net/minecraft/util/text/event/HoverEvent",
            "net/minecraft/network/chat/HoverEvent");

        // ---- Fabric pre-1.19.1 class redirects ----

        // LiteralText -> embedded shim that delegates to Component.literal()
        transformer.registerClassRedirect(
            "net/minecraft/text/LiteralText",
            "com/retromod/polyfill/minecraft/text/embedded/LiteralTextShim");

        // TranslatableText -> embedded shim that delegates to Component.translatable()
        transformer.registerClassRedirect(
            "net/minecraft/text/TranslatableText",
            "com/retromod/polyfill/minecraft/text/embedded/TranslatableTextShim");

        // Text.Serializer -> Component.Serializer
        transformer.registerClassRedirect(
            "net/minecraft/text/Text$Serializer",
            "net/minecraft/network/chat/Component$Serializer");

        // ---- TranslatableContents constructor changes (1.19.3+) ----
        // Old: new TranslatableContents(String key) — single-arg constructor removed
        // New: new TranslatableContents(String key, String fallback, Object[] args)
        // Redirect via factory shim using reflection
        transformer.registerConstructorRedirect(
            "net/minecraft/network/chat/contents/TranslatableContents",
            "(Ljava/lang/String;)V",
            "com/retromod/polyfill/minecraft/text/embedded/TranslatableContentsShim",
            "create",
            "(Ljava/lang/String;)Ljava/lang/Object;");

        // Old: new TranslatableContents(String key, Object[] args) — two-arg constructor removed
        transformer.registerConstructorRedirect(
            "net/minecraft/network/chat/contents/TranslatableContents",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "com/retromod/polyfill/minecraft/text/embedded/TranslatableContentsShim",
            "createWithArgs",
            "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
    }
}
