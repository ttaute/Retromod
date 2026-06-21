/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forge Config API Port provides the Forge config classes at their ORIGINAL
 * {@code net.minecraftforge.*} names, so Retromod must NOT redirect them onto
 * {@code fuzs.forgeconfigapiport.api.config.v2.*} - those targets don't exist,
 * and the old redirect broke every mod using the port (CoroUtil crashed at
 * {@code ModConfigDataFabric.writeConfigFile}; #94 follow-up). The shim must be
 * inert for these classes.
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
                        c + " must NOT be redirected - the Fabric port provides it at this exact name");
            }
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
