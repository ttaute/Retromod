/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.world;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed block-state property classes.
 *
 * <p><b>DirectionProperty</b> — In MC 26.1 the dedicated
 * {@code net.minecraft.world.level.block.state.properties.DirectionProperty}
 * class was removed. It used to be a thin subclass of
 * {@code EnumProperty<Direction>}; now {@code EnumProperty} is {@code final}
 * and code constructs direction properties directly with
 * {@code EnumProperty.create(name, Direction.class, ...)}.
 *
 * <p>Mods built for older MC reference {@code DirectionProperty} both as a type
 * (fields, method signatures) and via its {@code create(...)} factories. A bare
 * type reference crashes the game with {@code NoClassDefFoundError} — issue #24,
 * where a mod's mixin pulled the type into {@code Blocks.<clinit>} and killed
 * bootstrap on NeoForge.
 *
 * <p>Fix:
 * <ul>
 *   <li>Redirect the <em>type</em> {@code DirectionProperty} → {@code EnumProperty}
 *       (the surviving superclass). This resolves every type reference, since a
 *       direction property always WAS an {@code EnumProperty<Direction>}.</li>
 *   <li>Bridge the four removed {@code DirectionProperty.create(...)} factories to
 *       {@link com.retromod.polyfill.registry.DirectionPropertyLookup}, which adds
 *       the {@code Direction.class} argument the modern {@code EnumProperty.create}
 *       requires. The redirect keys use the post-class-remap shapes
 *       ({@code EnumProperty.create} with NO {@code Class} arg), which cannot
 *       collide with the real {@code EnumProperty.create(String, Class, ...)}
 *       overloads.</li>
 * </ul>
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
        return new String[]{ DIRECTION_PROPERTY };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stub: the type redirect targets the real (surviving)
        // EnumProperty, and the create(...) bridge target lives in Retromod's
        // own classes, which transformed mods can resolve at runtime.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // Type DirectionProperty -> EnumProperty (its surviving superclass).
        transformer.registerClassRedirect(DIRECTION_PROPERTY, ENUM_PROPERTY);

        // Factory bridges. Keyed on the POST-class-remap call shape: after the
        // redirect above, `DirectionProperty.create(...)DirectionProperty`
        // becomes `EnumProperty.create(...)EnumProperty`. The modern
        // EnumProperty.create needs a Class<Direction> arg these lack, so route
        // them through the lookup, which supplies Direction.class. The lookup
        // returns Object; the transformer emits CHECKCAST EnumProperty.

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

        // create(String, Direction...) — varargs erases to a Direction[] param
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
