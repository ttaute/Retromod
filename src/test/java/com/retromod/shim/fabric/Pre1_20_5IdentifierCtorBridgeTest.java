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
 * {@link Pre1_20_5IdentifierCtorBridge} must register cleanly as a no-op when {@code class_2960}
 * is absent. A throw here would look like a registration failure to {@code RetromodPreLaunch}.
 */
class Pre1_20_5IdentifierCtorBridgeTest {

    @Test
    @DisplayName("no-op and does not throw when class_2960 is absent")
    void absentHostIsNoop() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        // class_2960 absent means no factory is probed, so nothing registers; we only assert no throw.
        assertDoesNotThrow(() -> Pre1_20_5IdentifierCtorBridge.register(t));
    }
}
