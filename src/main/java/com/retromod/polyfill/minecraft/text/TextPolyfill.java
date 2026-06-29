/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.text;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Redirects for the two big text/chat API restructurings: the 1.13 Flattening
 * (net.minecraft.util.text.* to net.minecraft.network.chat.*) and Fabric's
 * 1.19.1 removal of LiteralText/TranslatableText. Removed classes redirect to
 * Component or to embedded shims that delegate to Component's static factories.
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
            // Forge pre-Flattening
            "net/minecraft/util/text/ITextComponent",
            "net/minecraft/util/text/TextComponentString",
            "net/minecraft/util/text/TextComponentTranslation",
            "net/minecraft/util/text/Style",
            "net/minecraft/util/text/TextFormatting",
            "net/minecraft/util/text/event/ClickEvent",
            "net/minecraft/util/text/event/HoverEvent",

            // Fabric pre-1.19.1
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
        // Forge pre-Flattening (1.13) class redirects
        transformer.registerClassRedirect(
            "net/minecraft/util/text/ITextComponent",
            "net/minecraft/network/chat/Component");

        // constructor calls need a shim, but plain class refs go to Component
        transformer.registerClassRedirect(
            "net/minecraft/util/text/TextComponentString",
            "net/minecraft/network/chat/Component");

        transformer.registerClassRedirect(
            "net/minecraft/util/text/TextComponentTranslation",
            "net/minecraft/network/chat/Component");

        transformer.registerClassRedirect(
            "net/minecraft/util/text/Style",
            "net/minecraft/network/chat/Style");

        // TextFormatting became ChatFormatting in the root package
        transformer.registerClassRedirect(
            "net/minecraft/util/text/TextFormatting",
            "net/minecraft/ChatFormatting");

        transformer.registerClassRedirect(
            "net/minecraft/util/text/event/ClickEvent",
            "net/minecraft/network/chat/ClickEvent");
        transformer.registerClassRedirect(
            "net/minecraft/util/text/event/HoverEvent",
            "net/minecraft/network/chat/HoverEvent");

        // Fabric pre-1.19.1 class redirects
        transformer.registerClassRedirect(
            "net/minecraft/text/LiteralText",
            "com/retromod/polyfill/minecraft/text/embedded/LiteralTextShim");

        transformer.registerClassRedirect(
            "net/minecraft/text/TranslatableText",
            "com/retromod/polyfill/minecraft/text/embedded/TranslatableTextShim");

        transformer.registerClassRedirect(
            "net/minecraft/text/Text$Serializer",
            "net/minecraft/network/chat/Component$Serializer");

        // TranslatableContents lost its single- and two-arg constructors in 1.19.3+;
        // route them through a reflective factory shim
        transformer.registerConstructorRedirect(
            "net/minecraft/network/chat/contents/TranslatableContents",
            "(Ljava/lang/String;)V",
            "com/retromod/polyfill/minecraft/text/embedded/TranslatableContentsShim",
            "create",
            "(Ljava/lang/String;)Ljava/lang/Object;");

        transformer.registerConstructorRedirect(
            "net/minecraft/network/chat/contents/TranslatableContents",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "com/retromod/polyfill/minecraft/text/embedded/TranslatableContentsShim",
            "createWithArgs",
            "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");
    }
}
