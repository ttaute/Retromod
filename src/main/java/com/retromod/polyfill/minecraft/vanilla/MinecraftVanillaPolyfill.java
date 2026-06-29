/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.vanilla;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for vanilla Minecraft classes removed across major versions, so older mods
 * referencing them do not crash with ClassNotFoundException at startup.
 */
public class MinecraftVanillaPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Vanilla Removed APIs";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/text/LiteralText",
            "net/minecraft/text/TranslatableText",
            "net/minecraft/block/Material",
            "net/minecraft/block/MaterialColor",
            "net/minecraft/world/gen/feature/StructureFeature",
            "net/minecraft/util/LazyLoadedValue"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "com/retromod/polyfill/minecraft/embedded/LazyLoadedValue"
        };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // LiteralText/TranslatableText, Material/MaterialColor and StructureFeature are
        // handled by the shim chain (calls rewritten to the Mojang names), no runtime stub.

        // LazyLoadedValue was removed in 26.1; redirect to our embedded polyfill (Jade
        // references it at runtime). Register both the Mojang name and the intermediary
        // class_3528: the ASM remapper is single-pass, so class_3528 redirected to
        // LazyLoadedValue would not chain on to the polyfill.
        //
        // Gated to 26.1+: below 26.1 net/minecraft/util/LazyLoadedValue still exists, and an
        // un-gated redirect would hijack the live class on a pre-26.1 NeoForge host (#17).
        if (!RetromodVersion.mcVersionExceeds("26.1", RetromodVersion.TARGET_MC_VERSION)) {
            transformer.registerClassRedirect(
                "net/minecraft/util/LazyLoadedValue",
                "com/retromod/polyfill/minecraft/embedded/LazyLoadedValue");
            transformer.registerClassRedirect(
                "net/minecraft/class_3528",
                "com/retromod/polyfill/minecraft/embedded/LazyLoadedValue");
        }
    }
}
