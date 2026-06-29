/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The transform verifier is a diagnostic: it must never abort a transform (#102).
 *
 * <p>Its class/method/field resolution probes load referenced classes to check them against the
 * target. When a referenced class lives in a protected {@code @Mixin} package, Mixin throws
 * {@code IllegalClassLoadError} - an {@link Error}, not an {@link Exception} - during the load.
 * The probes used to catch only {@code Exception}, so that Error escaped and killed the whole
 * transform pass (a Mine Mine No Mi addon, Cart's, dragged in a mineminenomi mixin class). The
 * probes now catch {@link Throwable}. That exact Mixin Error needs a live Mixin classloader to
 * reproduce, so it's verified in-game; here we lock in the host-independent invariant: the probes
 * return a boolean and never propagate, including for names that don't resolve.
 */
class TransformVerifierTest {

    private static Object probe(String method, Class<?>[] sig, Object... args) throws Exception {
        Method m = TransformVerifier.class.getDeclaredMethod(method, sig);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    @DisplayName("#102 canResolveClass never throws; resolvable name → true, unresolvable → no throw")
    void canResolveClassNeverThrows() {
        Class<?>[] sig = {String.class};
        assertEquals(Boolean.TRUE, assertDoesNotThrow(() -> probe("canResolveClass", sig, "java/lang/String")));
        Object missing = assertDoesNotThrow(() -> probe("canResolveClass", sig, "no/such/Class$Missing"));
        assertInstanceOf(Boolean.class, missing, "must return a boolean, never propagate");
    }

    @Test
    @DisplayName("#102 canResolveMethod never throws for resolvable and unresolvable owners")
    void canResolveMethodNeverThrows() {
        Class<?>[] sig = {String.class, String.class, String.class};
        assertEquals(Boolean.TRUE,
                assertDoesNotThrow(() -> probe("canResolveMethod", sig, "java/lang/String", "length", "()I")));
        assertInstanceOf(Boolean.class,
                assertDoesNotThrow(() -> probe("canResolveMethod", sig, "no/such/Owner", "x", "()V")),
                "unresolvable owner must not propagate");
    }

    @Test
    @DisplayName("#102 canResolveField never throws for resolvable and unresolvable owners")
    void canResolveFieldNeverThrows() {
        Class<?>[] sig = {String.class, String.class};
        assertEquals(Boolean.TRUE,
                assertDoesNotThrow(() -> probe("canResolveField", sig, "java/lang/Integer", "MAX_VALUE")));
        assertInstanceOf(Boolean.class,
                assertDoesNotThrow(() -> probe("canResolveField", sig, "no/such/Owner", "x")),
                "unresolvable owner must not propagate");
    }
}
