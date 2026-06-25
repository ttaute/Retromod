/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** CCA 5 to 6 package move: same-path classes get redirected, classes CCA 6 removed do not. */
class CardinalComponentsPackageMoveTest {

    @Test
    void packageMoveRedirectsRegistered() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            new CardinalComponentsApiShim().registerRedirects(t);
            Map<String, String> r = t.getClassRedirects();

            assertEquals("org/ladysnake/cca/api/v3/component/ComponentProvider",
                    r.get("dev/onyxstudios/cca/api/v3/component/ComponentProvider"));
            assertEquals("org/ladysnake/cca/api/v3/block/BlockComponentInitializer",
                    r.get("dev/onyxstudios/cca/api/v3/block/BlockComponentInitializer"));
            // inner classes need their own entries; ASM matches by exact name
            assertEquals("org/ladysnake/cca/api/v3/block/BlockComponentFactoryRegistry$Registration",
                    r.get("dev/onyxstudios/cca/api/v3/block/BlockComponentFactoryRegistry$Registration"));

            r.forEach((from, to) -> {
                if (from.startsWith("dev/onyxstudios/cca/")) {
                    assertTrue(to.startsWith("org/ladysnake/cca/"),
                            from + " must move into org.ladysnake.cca, got " + to);
                }
            });

            // removed in CCA 6: no same-path target to redirect to
            assertFalse(r.containsKey("dev/onyxstudios/cca/api/v3/item/ItemComponent"),
                    "ItemComponent was removed in CCA 6 - no same-path target exists");
            assertFalse(r.containsKey("dev/onyxstudios/cca/api/v3/entity/PlayerComponent"),
                    "PlayerComponent was removed in CCA 6");
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
