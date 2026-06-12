/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The renamed-SAM event bridge batch (lambda-trap fixes): generator output is
 * structurally right (old SAM stays the only abstract method, new SAM is a
 * default forwarder, synthetic extends the new interface), the shim registers
 * every old name at 26.1, and nothing registers on a pre-26.1 host where the
 * old APIs are still alive (pitfall #9).
 */
class FabricRenamedSamBridgesTest {

    private static final String[] OLD_NAMES = {
        "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientWorldEvents",
        "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientWorldEvents$AfterClientWorldChange",
        "net/fabricmc/fabric/api/entity/event/v1/ServerEntityWorldChangeEvents",
        "net/fabricmc/fabric/api/entity/event/v1/ServerEntityWorldChangeEvents$AfterEntityChange",
        "net/fabricmc/fabric/api/entity/event/v1/ServerEntityWorldChangeEvents$AfterPlayerChange",
        "net/fabricmc/fabric/api/client/rendering/v1/LivingEntityFeatureRendererRegistrationCallback",
        "net/fabricmc/fabric/api/client/rendering/v1/DrawItemStackOverlayCallback",
        "net/fabricmc/fabric/api/client/rendering/v1/TooltipComponentCallback",
        "net/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents$LevelTypeChange",
        "net/fabricmc/fabric/api/registry/LandPathNodeTypesRegistry",
        "net/fabricmc/fabric/api/registry/LandPathNodeTypesRegistry$StaticPathNodeTypeProvider",
        "net/fabricmc/fabric/api/registry/LandPathNodeTypesRegistry$DynamicPathNodeTypeProvider",
    };

    @Test
    void samInterfaceShape() {
        byte[] bytes = SamBridgeSynthetics.samInterface(
                "com/retromod/generated/test/OldIface", "new/iface/NewIface",
                "oldSam", "newSam",
                "(Lnet/minecraft/client/Minecraft;I)Ljava/lang/String;",
                "new.iface.NewIface", "EVENT");
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);

        assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0);
        assertEquals("new/iface/NewIface", cn.interfaces.get(0), "must extend the new interface");

        MethodNode oldSam = cn.methods.stream().filter(m -> m.name.equals("oldSam")).findFirst().orElseThrow();
        assertTrue((oldSam.access & Opcodes.ACC_ABSTRACT) != 0, "old SAM stays abstract (lambdas link here)");

        MethodNode newSam = cn.methods.stream().filter(m -> m.name.equals("newSam")).findFirst().orElseThrow();
        assertEquals(0, newSam.access & Opcodes.ACC_ABSTRACT, "new SAM is a default forwarder");
        assertEquals(oldSam.desc, newSam.desc, "same descriptor, only the name differs");

        // exactly one abstract method — otherwise it's not a functional interface
        long abstracts = cn.methods.stream().filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).count();
        assertEquals(1, abstracts);

        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("EVENT")), "EVENT mirrored");
        assertTrue(cn.methods.stream().anyMatch(m -> m.name.equals("<clinit>")), "EVENT wired in <clinit>");
    }

    @Test
    void eventHolderShape() {
        byte[] bytes = SamBridgeSynthetics.eventHolder("com/retromod/generated/test/OldHolder",
                new String[][]{
                        {"AFTER_A", "new.Holder", "AFTER_A_LEVEL"},
                        {"AFTER_B", "new.Holder", "AFTER_B_LEVEL"},
                });
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        assertEquals(0, cn.access & Opcodes.ACC_INTERFACE);
        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("AFTER_A")));
        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("AFTER_B")));
        assertTrue(cn.methods.stream().anyMatch(m -> m.name.equals("<clinit>")));
    }

    @Test
    void registersEverythingAt26_1AndNothingBelow() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        String saved = RetromodVersion.TARGET_MC_VERSION;
        try {
            // Pre-26.1 host: the old APIs are alive — nothing may be hijacked.
            t.clearRedirectsForTesting();
            RetromodVersion.TARGET_MC_VERSION = "1.21.11";
            new FabricRenamedSamBridgesShim().registerRedirects(t);
            for (String old : OLD_NAMES) {
                assertFalse(t.getClassRedirects().containsKey(old),
                        old + " must NOT be redirected on a 1.21.11 host");
            }

            // 26.1 host: every old name covered.
            t.clearRedirectsForTesting();
            RetromodVersion.TARGET_MC_VERSION = "26.1";
            new FabricRenamedSamBridgesShim().registerRedirects(t);
            Map<String, String> r = t.getClassRedirects();
            for (String old : OLD_NAMES) {
                assertTrue(r.containsKey(old), old + " must be redirected at 26.1");
            }
            // Spot-check targets: lambda-trap ifaces go to synthetics, not the new API names
            assertTrue(r.get(OLD_NAMES[1]).startsWith("com/retromod/generated/legacyevents/"));
            // The helper inner kept its SAM name → plain redirect to the live class
            assertEquals(
                    "net/fabricmc/fabric/api/client/rendering/v1/LivingEntityRenderLayerRegistrationCallback$RegistrationHelper",
                    r.get("net/fabricmc/fabric/api/client/rendering/v1/LivingEntityFeatureRendererRegistrationCallback$RegistrationHelper"));
            // ServerChunkEvents holder survives: field redirect, not a class redirect
            assertFalse(r.containsKey("net/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents"));
            var chunkField = t.getFieldRedirects().get(new RetromodTransformer.FieldKey(
                    "net/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents", "CHUNK_LEVEL_TYPE_CHANGE"));
            assertNotNull(chunkField);
            assertEquals("FULL_CHUNK_STATUS_CHANGE", chunkField.name());
        } finally {
            RetromodVersion.TARGET_MC_VERSION = saved;
            t.clearRedirectsForTesting();
        }
    }
}
