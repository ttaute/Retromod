/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks for {@link FabricServerWorldEventsShim}: the synthetic holder/SAM
 * bytecode and the redirects. The reflective wiring needs a 26.1 launch.
 */
class FabricServerWorldEventsShimTest {

    private static final String SAM_DESC =
            "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;)V";

    @BeforeAll
    static void pinHostTo26_1() {
        // shim only fires on 26.1+
        RetromodVersion.TARGET_MC_VERSION = "26.1";
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    @Test
    @DisplayName("holder is a class with LOAD + UNLOAD event fields and a <clinit>")
    void holderShape() {
        ClassNode cn = read(FabricServerWorldEventsShim.generateHolder());
        assertEquals(0, cn.access & Opcodes.ACC_INTERFACE);
        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("LOAD")
                && f.desc.equals("Lnet/fabricmc/fabric/api/event/Event;")), "LOAD field");
        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("UNLOAD")
                && f.desc.equals("Lnet/fabricmc/fabric/api/event/Event;")), "UNLOAD field");
        assertTrue(cn.methods.stream().anyMatch(m -> m.name.equals("<clinit>")), "fields need a static init");
    }

    @Test
    @DisplayName("Load/Unload SAMs keep the old onWorld* names with the harvested ServerLevel param")
    void sams() {
        assertSam(read(FabricServerWorldEventsShim.generateLoad()), "onWorldLoad");
        assertSam(read(FabricServerWorldEventsShim.generateUnload()), "onWorldUnload");
    }

    private static void assertSam(ClassNode cn, String name) {
        assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0);
        MethodNode sam = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).findFirst().orElse(null);
        assertNotNull(sam);
        assertEquals(name, sam.name, "SAM name must stay " + name + " so the lambda links");
        assertEquals(SAM_DESC, sam.desc, "(MinecraftServer, ServerLevel); World->Level handled by the harvest");
    }

    @Test
    @DisplayName("shim redirects ServerWorldEvents + $Load + $Unload onto the synthetics")
    void redirects() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricServerWorldEventsShim().registerRedirects(t);
        var cr = t.getClassRedirects();
        assertEquals("com/retromod/generated/legacylifecycle/ServerWorldEvents",
                cr.get("net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents"));
        assertEquals("com/retromod/generated/legacylifecycle/ServerWorldLoad",
                cr.get("net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents$Load"));
        assertEquals("com/retromod/generated/legacylifecycle/ServerWorldUnload",
                cr.get("net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents$Unload"));
    }
}
