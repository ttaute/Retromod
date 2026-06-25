/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Host gate for the 26.1 Fabric API bridges (#9): below 26.1 the old APIs still exist so the
 * bridges register nothing, at 26.1+ they register their redirects. The shims' fabric-api-style
 * target versions parse below every MC version, so the {@code target <= host} gate alone would
 * let them register everywhere. The transformer is a JVM-wide singleton, so each test clears it
 * before re-registering.
 */
class Fabric26ApiBridgeGateTest {

    private static final String[] GATED_OLD_CLASSES = {
        "net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents",
        "net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents$ModifyEntries",
        "net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents",
        "net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents$Load",
        "net/fabricmc/fabric/api/client/rendering/v1/EntityModelLayerRegistry",
        "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking$PlayChannelHandler",
    };

    private static void registerAllGatedShims(RetromodTransformer t) {
        new FabricItemGroupEventsShim().registerRedirects(t);
        new FabricServerWorldEventsShim().registerRedirects(t);
        new FabricEntityModelLayerShim().registerRedirects(t);
        new FabricClientNetworkingV1Shim().registerRedirects(t);
    }

    @AfterEach
    void restoreHost() {
        // Other shim tests pin 26.1 in @BeforeAll.
        RetromodVersion.TARGET_MC_VERSION = "26.1";
    }

    @Test
    @DisplayName("pre-26.1 host: 26.1 API bridges register nothing")
    void gatedShimsNoOpBelow26_1() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "1.21.11";

        registerAllGatedShims(t);

        for (String old : GATED_OLD_CLASSES) {
            assertFalse(t.getClassRedirects().containsKey(old),
                    old + " must not be redirected on a 1.21.11 host where the API still exists");
        }
        assertTrue(t.getClassRedirects().isEmpty(), "no class redirects from gated shims below 26.1");
        assertTrue(t.getMethodRedirects().isEmpty(), "no method redirects from gated shims below 26.1");
    }

    @Test
    @DisplayName("26.1 host: the same bridges register their redirects")
    void gatedShimsRegisterAt26_1() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "26.1";

        registerAllGatedShims(t);

        for (String old : GATED_OLD_CLASSES) {
            assertTrue(t.getClassRedirects().containsKey(old),
                    old + " must be redirected on a 26.1 host where the old API is gone");
        }
    }

    @Test
    @DisplayName("command v1 bridge stays ungated (v1 is gone on every modern host)")
    void commandV1StaysUngated() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "1.21.11";

        new FabricCommandV1Shim().registerRedirects(t);

        assertEquals("com/retromod/generated/legacycmd/CommandRegistrationCallbackV1",
                t.getClassRedirects().get("net/fabricmc/fabric/api/command/v1/CommandRegistrationCallback"),
                "command/v1 was removed in the 1.19 era; the bridge helps pre-26.1 hosts too");
    }
}
