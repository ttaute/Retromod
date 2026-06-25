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
 * The test JVM has no host {@code class_1269}, so we pin the without-Minecraft contract:
 * register zero field redirects and don't throw when the host class is absent.
 */
class Pre1_21_2InteractionResultBridgeTest {

    @Test
    @DisplayName("no-op + does not throw when class_1269 is absent (test JVM has no MC)")
    void absentHostIsNoop() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        int before = t.getFieldRedirectCount();

        assertDoesNotThrow(() -> Pre1_21_2InteractionResultBridge.register(t));

        assertEquals(before, t.getFieldRedirectCount(),
                "bridge must not register field redirects when class_1269 is absent");
    }
}
