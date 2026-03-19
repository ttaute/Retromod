/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.vanilla;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed vanilla Minecraft API classes.
 *
 * Several core Minecraft classes were removed or refactored across
 * major versions. This provider registers stub implementations so
 * older mods referencing these classes do not crash with
 * ClassNotFoundException at startup.
 *
 * Covered removals:
 * - LiteralText / TranslatableText (replaced by Text.literal / Text.translatable)
 * - Material / MaterialColor (block material system removed in 1.19.3+)
 * - StructureFeature (replaced by Structure in 1.19+)
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
    public void registerPolyfills(RetroModTransformer transformer) {
        // Register class redirects for removed classes.
        // The transformer will rewrite old mod bytecode references.
        // These classes were removed in MC 1.19-1.20 and don't exist in 26.1.

        // LiteralText/TranslatableText removed in 1.19, replaced by Text.literal/Text.translatable
        // On 26.1 these use Mojang names: Component.literal / Component.translatable
        // Old mods referencing these will have calls rewritten by the version shims.

        // Material/MaterialColor removed in 1.20, properties inlined into BlockState
        // StructureFeature removed in 1.18.2, replaced by Structure
        // These are handled by the shim chain — no stub needed at runtime.

        // LazyLoadedValue removed in 26.1. Redirect to our embedded polyfill.
        // Mods like Jade reference this at runtime (e.g., ClientProxy.java:304).
        // Register BOTH Mojang name AND intermediary name — ASM remapper is single-pass,
        // so class_3528→LazyLoadedValue doesn't chain to LazyLoadedValue→polyfill.
        transformer.registerClassRedirect(
            "net/minecraft/util/LazyLoadedValue",
            "com/retromod/polyfill/minecraft/embedded/LazyLoadedValue");
        transformer.registerClassRedirect(
            "net/minecraft/class_3528",
            "com/retromod/polyfill/minecraft/embedded/LazyLoadedValue");
    }
}
