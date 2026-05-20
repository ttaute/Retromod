/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.minecraft.world.BlockPropertyPolyfill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for issue #24: the removed
 * {@code net.minecraft.world.level.block.state.properties.DirectionProperty}.
 *
 * Verifies the polyfill registers the type redirect to the surviving
 * {@code EnumProperty} and bridges all four removed {@code create(...)}
 * factories — keyed on the post-class-remap call shape (no {@code Class} arg),
 * which must not collide with the real {@code EnumProperty.create} overloads.
 */
class BlockPropertyPolyfillTest {

    private static final String DIRECTION_PROPERTY =
            "net/minecraft/world/level/block/state/properties/DirectionProperty";
    private static final String ENUM_PROPERTY =
            "net/minecraft/world/level/block/state/properties/EnumProperty";
    private static final String ENUM_DESC =
            "Lnet/minecraft/world/level/block/state/properties/EnumProperty;";
    private static final String LOOKUP =
            "com/retromod/polyfill/registry/DirectionPropertyLookup";

    @Test
    @DisplayName("Polyfill reports DirectionProperty as a removed class")
    void reportsRemovedClass() {
        BlockPropertyPolyfill p = new BlockPropertyPolyfill();
        assertEquals("minecraft_vanilla", p.getCategory());
        assertArrayEquals(new String[]{ DIRECTION_PROPERTY }, p.getRemovedClasses());
    }

    @Test
    @DisplayName("Type redirect: DirectionProperty -> EnumProperty")
    void registersTypeRedirect() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new BlockPropertyPolyfill().registerPolyfills(t);
        assertEquals(ENUM_PROPERTY, t.getClassRedirects().get(DIRECTION_PROPERTY),
                "DirectionProperty must redirect to its surviving superclass EnumProperty");
    }

    @Test
    @DisplayName("All four create(...) factories bridge to DirectionPropertyLookup")
    void bridgesAllCreateFactories() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new BlockPropertyPolyfill().registerPolyfills(t);

        // The four post-class-remap create shapes, none of which collide with the
        // real EnumProperty.create(String, Class, ...) overloads.
        String[] descs = {
                "(Ljava/lang/String;)" + ENUM_DESC,
                "(Ljava/lang/String;Ljava/util/function/Predicate;)" + ENUM_DESC,
                "(Ljava/lang/String;[Lnet/minecraft/core/Direction;)" + ENUM_DESC,
                "(Ljava/lang/String;Ljava/util/Collection;)" + ENUM_DESC,
        };
        for (String desc : descs) {
            var key = new RetromodTransformer.MethodKey(ENUM_PROPERTY, "create", desc);
            var target = t.getMethodRedirects().get(key);
            assertNotNull(target, "missing create bridge for " + desc);
            assertEquals(LOOKUP, target.owner());
            assertEquals("create", target.name());
            assertTrue(target.desc().endsWith(")Ljava/lang/Object;"),
                    "bridge must return Object so the transformer emits CHECKCAST EnumProperty: " + desc);
        }
    }

    @Test
    @DisplayName("Bridges do NOT shadow the real EnumProperty.create(String, Class) overloads")
    void doesNotShadowRealOverloads() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new BlockPropertyPolyfill().registerPolyfills(t);
        // The genuine 26.1 factory takes a Class<Direction> 2nd arg — must be untouched.
        var realKey = new RetromodTransformer.MethodKey(
                ENUM_PROPERTY, "create",
                "(Ljava/lang/String;Ljava/lang/Class;)" + ENUM_DESC);
        assertNull(t.getMethodRedirects().get(realKey),
                "the real EnumProperty.create(String, Class) must not be redirected");
    }
}
