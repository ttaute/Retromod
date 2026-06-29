/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.world;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for {@code DirectionProperty}, removed in MC 26.1. It used to be a
 * subclass of {@code EnumProperty<Direction>}; now {@code EnumProperty} is
 * {@code final} and direction properties are built via
 * {@code EnumProperty.create(name, Direction.class, ...)}.
 *
 * <p>Gated to 26.1+ on {@link RetromodVersion#TARGET_MC_VERSION}: pre-26.1 the
 * class still exists, and on a NeoForge host (Mojang-named) an un-gated redirect
 * would hijack live code (CLAUDE.md #17). A bare type reference on 26.1+ crashes
 * with {@code NoClassDefFoundError} (#24, mixin pulled the type into
 * {@code Blocks.<clinit>}).
 *
 * <p>The type {@code DirectionProperty} redirects to its surviving superclass
 * {@code EnumProperty}; the four {@code create(...)} factories route through
 * {@link com.retromod.polyfill.registry.DirectionPropertyLookup}, which supplies
 * the {@code Direction.class} argument modern {@code EnumProperty.create} wants.
 * Redirect keys use the post-class-remap shape ({@code EnumProperty.create} with
 * no {@code Class} arg), which can't collide with the real overloads.
 */
public class BlockPropertyPolyfill implements PolyfillProvider {

    private static final String DIRECTION_PROPERTY =
            "net/minecraft/world/level/block/state/properties/DirectionProperty";
    private static final String ENUM_PROPERTY =
            "net/minecraft/world/level/block/state/properties/EnumProperty";
    private static final String ENUM_PROPERTY_DESC =
            "Lnet/minecraft/world/level/block/state/properties/EnumProperty;";
    private static final String LOOKUP =
            "com/retromod/polyfill/registry/DirectionPropertyLookup";

    /** True only on a 26.1+ host, where DirectionProperty is gone. */
    private static boolean active() {
        return !RetromodVersion.mcVersionExceeds("26.1", RetromodVersion.TARGET_MC_VERSION);
    }

    @Override
    public String getName() {
        return "Block-State Property Removals (DirectionProperty)";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        if (!active()) return new String[0];
        return new String[]{ DIRECTION_PROPERTY };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stub: the type redirect targets surviving EnumProperty,
        // and the create(...) bridge lives in Retromod's own classes.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        if (!active()) {
            return; // DirectionProperty still exists pre-26.1
        }
        transformer.registerClassRedirect(DIRECTION_PROPERTY, ENUM_PROPERTY);

        // Factory bridges keyed on the post-class-remap shape: the redirect above
        // turns DirectionProperty.create(...) into EnumProperty.create(...), so
        // route those through the lookup, which adds the Direction.class arg modern
        // EnumProperty.create needs. The lookup returns Object; the transformer
        // emits CHECKCAST EnumProperty.

        // create(String) -> all directions
        transformer.registerMethodRedirect(
                ENUM_PROPERTY, "create",
                "(Ljava/lang/String;)" + ENUM_PROPERTY_DESC,
                LOOKUP, "create",
                "(Ljava/lang/String;)Ljava/lang/Object;");

        // create(String, Predicate)
        transformer.registerMethodRedirect(
                ENUM_PROPERTY, "create",
                "(Ljava/lang/String;Ljava/util/function/Predicate;)" + ENUM_PROPERTY_DESC,
                LOOKUP, "create",
                "(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/lang/Object;");

        // create(String, Direction...): varargs erases to a Direction[] param
        transformer.registerMethodRedirect(
                ENUM_PROPERTY, "create",
                "(Ljava/lang/String;[Lnet/minecraft/core/Direction;)" + ENUM_PROPERTY_DESC,
                LOOKUP, "create",
                "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;");

        // create(String, Collection)
        transformer.registerMethodRedirect(
                ENUM_PROPERTY, "create",
                "(Ljava/lang/String;Ljava/util/Collection;)" + ENUM_PROPERTY_DESC,
                LOOKUP, "create",
                "(Ljava/lang/String;Ljava/util/Collection;)Ljava/lang/Object;");
    }
}
