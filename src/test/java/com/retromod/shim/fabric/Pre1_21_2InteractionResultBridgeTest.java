/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bridge-level tests for {@link Pre1_21_2InteractionResultBridge}.
 *
 * <p>The interesting branch (probing a real host {@code class_1269} and registering
 * descriptor rewrites) can't be exercised in a JVM that doesn't ship Minecraft, so
 * what we CAN pin down here is the safety contract: the bridge must register
 * <b>zero</b> field redirects when the host class isn't on the classpath, and must
 * never throw — registering it on every Fabric pre-26.1 host is the calling
 * convention in {@link com.retromod.core.RetromodPreLaunch}, and a thrown exception
 * there would degrade other bridges (or whole startup) for no reason.</p>
 */
class Pre1_21_2InteractionResultBridgeTest {

    @Test
    @DisplayName("no-op + does not throw when class_1269 is absent (test JVM has no MC)")
    void absentHostIsNoop() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        int before = t.getFieldRedirectCount();

        // The probe should swallow the ClassNotFoundException and exit early. If this
        // ever throws, fix the bridge — RetromodPreLaunch only wraps it in a
        // best-effort try/catch that LOGS THE FAILURE, which would silently degrade
        // a real-host scenario where the bridge is genuinely needed.
        assertDoesNotThrow(() -> Pre1_21_2InteractionResultBridge.register(t));

        // And: nothing got registered. A registration here would mean the bridge
        // somehow found a class_1269 on the test classpath (impossible without MC)
        // and emitted redirects that would later fire against real mod bytecode.
        assertEquals(before, t.getFieldRedirectCount(),
                "bridge must not register field redirects when class_1269 is absent");
    }
}
