/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for #94: PayloadTypeRegistry's static accessors were renamed twice and
 * 26.1 ships only the newest spelling, so the shim must map both older generations to it.
 */
class PayloadTypeRegistryRenameTest {

    private static final String PTR = "net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry";
    private static final String DESC = "()Lnet/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry;";

    @Test
    void bothOldGenerationsMapToThe26_1Names() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        String saved = RetromodVersion.TARGET_MC_VERSION;
        t.clearRedirectsForTesting();
        try {
            RetromodVersion.TARGET_MC_VERSION = "26.1.2";
            new Fabric_1_21_11_to_26_1().registerRedirects(t);
            var r = t.getMethodRedirects();

            // gen-1
            assertTarget(r, "configurationClientbound", "clientboundConfiguration");
            assertTarget(r, "configurationServerbound", "serverboundConfiguration");
            assertTarget(r, "playClientbound", "clientboundPlay");
            assertTarget(r, "playServerbound", "serverboundPlay");
            // gen-2
            assertTarget(r, "configurationS2C", "clientboundConfiguration");
            assertTarget(r, "configurationC2S", "serverboundConfiguration");
            assertTarget(r, "playS2C", "clientboundPlay");
            assertTarget(r, "playC2S", "serverboundPlay");
        } finally {
            RetromodVersion.TARGET_MC_VERSION = saved;
            t.clearRedirectsForTesting();
        }
    }

    private static void assertTarget(java.util.Map<RetromodTransformer.MethodKey, RetromodTransformer.MethodTarget> r,
                                     String oldName, String newName) {
        var tgt = r.get(new RetromodTransformer.MethodKey(PTR, oldName, DESC));
        assertNotNull(tgt, "PayloadTypeRegistry." + oldName + " must be redirected on 26.1");
        assertEquals(PTR, tgt.owner());
        assertEquals(newName, tgt.name(), oldName + " must map to " + newName);
        assertEquals(DESC, tgt.desc(), "return-type descriptor must be preserved");
    }
}
