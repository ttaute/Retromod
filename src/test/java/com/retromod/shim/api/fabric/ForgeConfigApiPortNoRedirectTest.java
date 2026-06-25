/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forge Config API Port provides the Forge config classes at their original
 * {@code net.minecraftforge.*} names, so the shim must not redirect them (#94).
 */
class ForgeConfigApiPortNoRedirectTest {

    @Test
    void doesNotRedirectForgeConfigClasses() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            new ForgeConfigApiPortShim().registerRedirects(t);
            var r = t.getClassRedirects();
            for (String c : new String[]{
                    "net/minecraftforge/common/ForgeConfigSpec",
                    "net/minecraftforge/common/ForgeConfigSpec$Builder",
                    "net/minecraftforge/fml/ModLoadingContext",
                    "net/minecraftforge/fml/config/ModConfig$Type",
            }) {
                assertFalse(r.containsKey(c),
                        c + " should not be redirected; the Fabric port provides it at this name");
            }
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
