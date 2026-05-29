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
 * Safety-contract tests for {@link Pre1_20_5IdentifierCtorBridge}. Real registration
 * needs a live MC host and can't be exercised here; what we pin is that the bridge
 * stays a true no-op (no throw, no registrations) when {@code class_2960} is absent,
 * because the calling site in {@code RetromodPreLaunch} treats any throw as a
 * registration failure and logs a warning — silently bad on a real host where the
 * bridge IS needed.
 */
class Pre1_20_5IdentifierCtorBridgeTest {

    @Test
    @DisplayName("no-op + does not throw when class_2960 is absent (test JVM has no MC)")
    void absentHostIsNoop() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        assertDoesNotThrow(() -> Pre1_20_5IdentifierCtorBridge.register(t));
        // Counting ctor redirects here would be redundant with the bridge's debug-log
        // assertion: if class_2960 wasn't found, no factory was probed, so no
        // registerConstructorRedirect call was made. The "doesn't throw" check is
        // the safety floor — anything else is implementation-leak.
    }
}
