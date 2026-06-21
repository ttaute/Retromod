/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.forge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * The two deleted-class bridges (#85 FMLJavaModLoadingContext, #52 DeferredSpawnEggItem).
 * These reference NeoForge/MC classes not on the build classpath, so they can't be fully
 * class-loaded here - but the generators must still emit STRUCTURALLY VALID bytecode (ASM's
 * COMPUTE_FRAMES succeeds) with the exact method shapes the old mods call, and the
 * {@link com.retromod.core.SyntheticEmbedder} rename has to keep them valid. This locks both.
 */
class ForgeNeoForgeSyntheticsTest {

    private static ClassNode parse(byte[] b) {
        ClassNode cn = new ClassNode();
        new ClassReader(b).accept(cn, 0);
        return cn;
    }

    private static MethodNode method(ClassNode cn, String name, String desc) {
        return cn.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc))
                .findFirst().orElse(null);
    }

    /** Structural verify (no dataflow - the referenced MC/NeoForge classes aren't loadable here). */
    private static void assertStructurallyValid(byte[] b, String what) {
        StringWriter sw = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(b), false, new PrintWriter(sw));
        assertTrue(sw.toString().isEmpty(), what + " has bytecode errors:\n" + sw);
    }

    @Test
    @DisplayName("#85 FMLJavaModLoadingContext bridge: get() + getModEventBus() delegation, valid bytecode")
    void fmlJavaModLoadingContext() {
        byte[] b = ForgeNeoForgeSynthetics.generateFmlJavaModLoadingContext();
        assertStructurallyValid(b, "FMLJavaModLoadingContext");
        ClassNode cn = parse(b);
        assertEquals("net/neoforged/fml/javafmlmod/FMLJavaModLoadingContext", cn.name);
        assertNotNull(method(cn, "get", "()Lnet/neoforged/fml/javafmlmod/FMLJavaModLoadingContext;"),
                "static get() returning self");
        assertNotNull(method(cn, "getModEventBus", "()Lnet/neoforged/bus/api/IEventBus;"),
                "getModEventBus() returning IEventBus");
        // the delegation target must be in the body
        MethodNode g = method(cn, "getModEventBus", "()Lnet/neoforged/bus/api/IEventBus;");
        boolean delegates = java.util.Arrays.stream(g.instructions.toArray())
                .anyMatch(in -> in instanceof org.objectweb.asm.tree.MethodInsnNode mi
                        && mi.owner.equals("net/neoforged/fml/ModContainer")
                        && mi.name.equals("getEventBus"));
        assertTrue(delegates, "getModEventBus must delegate to ModContainer.getEventBus()");
    }

    @Test
    @DisplayName("#52 DeferredSpawnEggItem bridge: extends SpawnEggItem, old ctor, valid bytecode")
    void deferredSpawnEggItem() {
        byte[] b = ForgeNeoForgeSynthetics.generateDeferredSpawnEggItem();
        assertStructurallyValid(b, "DeferredSpawnEggItem");
        ClassNode cn = parse(b);
        assertEquals("net/neoforged/neoforge/common/DeferredSpawnEggItem", cn.name);
        assertEquals("net/minecraft/world/item/SpawnEggItem", cn.superName, "must extend SpawnEggItem");
        // the classic MCreator ctor: (Supplier, int, int, Item.Properties)
        assertNotNull(method(cn, "<init>",
                "(Ljava/util/function/Supplier;IILnet/minecraft/world/item/Item$Properties;)V"),
                "the (Supplier,int,int,Properties) constructor");
        // it must set the type via 26.2's Properties.spawnEgg(EntityType)
        boolean usesSpawnEgg = cn.methods.stream().flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                .anyMatch(in -> in instanceof org.objectweb.asm.tree.MethodInsnNode mi
                        && mi.owner.equals("net/minecraft/world/item/Item$Properties")
                        && mi.name.equals("spawnEgg"));
        assertTrue(usesSpawnEgg, "must set the entity type via Item.Properties.spawnEgg(EntityType)");
    }
}
