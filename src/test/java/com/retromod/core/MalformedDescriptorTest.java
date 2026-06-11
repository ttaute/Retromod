/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import com.retromod.mixin.MixinCompatibilityTransformer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Regression tests for the descriptor-parser infinite loops (snapshot.4).
 *
 * <p>Several parsers advanced with {@code i = desc.indexOf(';', i) + 1}. On a
 * malformed descriptor whose L-type never closes with {@code ';'} (corrupted
 * or hand-crafted jar), {@code indexOf} returns -1, {@code i} resets to 0 and
 * the parse loop spins forever — hanging the game mid-transform. In
 * {@code FuzzyMethodResolver} the "guard" was a {@code break} inside a switch
 * arrow-rule, which exits the <i>switch</i>, not the loop, so it spun too.
 *
 * <p>With the bug present these tests HANG rather than fail, so every call is
 * wrapped in a preemptive timeout.
 */
class MalformedDescriptorTest {

    private static final Duration LIMIT = Duration.ofSeconds(5);
    private static final String MALFORMED = "(LFoo)V";        // L-type missing ';'
    private static final String MALFORMED_ARRAY = "([LFoo)V"; // array of unterminated L-type
    private static final String WELL_FORMED = "(Ljava/lang/String;I[Ljava/lang/Object;)V";

    @Test
    void transformVerifierCountParametersTerminates() throws Exception {
        Method m = TransformVerifier.class.getDeclaredMethod("countParameters", String.class);
        m.setAccessible(true);
        assertTimeoutPreemptively(LIMIT, () -> {
            m.invoke(null, MALFORMED);
            m.invoke(null, MALFORMED_ARRAY);
            assertEquals(3, ((Integer) m.invoke(null, WELL_FORMED)).intValue());
        });
    }

    @Test
    void fabricModTransformerCountParametersTerminates() throws Exception {
        Method m = FabricModTransformer.class.getDeclaredMethod("countParameters", String.class);
        m.setAccessible(true);
        assertTimeoutPreemptively(LIMIT, () -> {
            m.invoke(null, MALFORMED);
            m.invoke(null, MALFORMED_ARRAY);
            assertEquals(3, ((Integer) m.invoke(null, WELL_FORMED)).intValue());
        });
    }

    @Test
    void mixinCountParameterSlotsTerminates() throws Exception {
        MixinCompatibilityTransformer mixin =
            new MixinCompatibilityTransformer(RetromodTransformer.getInstance());
        Method m = MixinCompatibilityTransformer.class
            .getDeclaredMethod("countParameterSlots", String.class);
        m.setAccessible(true);
        assertTimeoutPreemptively(LIMIT, () -> {
            m.invoke(mixin, MALFORMED);
            m.invoke(mixin, MALFORMED_ARRAY);
            // String (1 slot) + int (1) + Object[] (1) = 3
            assertEquals(3, ((Integer) m.invoke(mixin, WELL_FORMED)).intValue());
        });
    }

    @Test
    void fuzzyResolverParseParameterTypesTerminates() {
        assertTimeoutPreemptively(LIMIT, () -> {
            FuzzyMethodResolver.parseParameterTypes(MALFORMED);
            FuzzyMethodResolver.parseParameterTypes(MALFORMED_ARRAY);
            List<String> ok = FuzzyMethodResolver.parseParameterTypes(WELL_FORMED);
            assertEquals(List.of("Ljava/lang/String;", "I", "[Ljava/lang/Object;"), ok);
        });
    }
}
