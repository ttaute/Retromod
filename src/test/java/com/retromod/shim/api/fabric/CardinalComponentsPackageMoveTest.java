/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The harvested dev.onyxstudios.cca → org.ladysnake.cca package move (CCA 5→6):
 * the public {@code api/} surface that kept its sub-path is redirected (incl.
 * inner classes), and classes CCA 6 genuinely removed are NOT redirected to a
 * non-existent target.
 */
class CardinalComponentsPackageMoveTest {

    @Test
    void packageMoveRedirectsRegistered() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            new CardinalComponentsApiShim().registerRedirects(t);
            Map<String, String> r = t.getClassRedirects();

            // representative same-path moves (verified present in both 5.2.3 and 6.1.3)
            assertEquals("org/ladysnake/cca/api/v3/component/ComponentProvider",
                    r.get("dev/onyxstudios/cca/api/v3/component/ComponentProvider"));
            assertEquals("org/ladysnake/cca/api/v3/block/BlockComponentInitializer",
                    r.get("dev/onyxstudios/cca/api/v3/block/BlockComponentInitializer"));
            // inner classes get explicit entries (ASM matches exact names)
            assertEquals("org/ladysnake/cca/api/v3/block/BlockComponentFactoryRegistry$Registration",
                    r.get("dev/onyxstudios/cca/api/v3/block/BlockComponentFactoryRegistry$Registration"));

            // every old dev.onyxstudios target must resolve to an org.ladysnake class
            r.forEach((from, to) -> {
                if (from.startsWith("dev/onyxstudios/cca/")) {
                    assertTrue(to.startsWith("org/ladysnake/cca/"),
                            from + " must move into org.ladysnake.cca, got " + to);
                }
            });

            // CCA 6 REMOVED these — must NOT be redirected to a phantom target
            assertFalse(r.containsKey("dev/onyxstudios/cca/api/v3/item/ItemComponent"),
                    "ItemComponent was removed in CCA 6 — no same-path target exists");
            assertFalse(r.containsKey("dev/onyxstudios/cca/api/v3/entity/PlayerComponent"),
                    "PlayerComponent was removed in CCA 6");
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
