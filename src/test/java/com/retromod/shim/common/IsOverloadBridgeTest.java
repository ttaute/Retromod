/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * 26.1 removed the single-arg is() overloads from BlockState/ItemStack/FluidState (the Macaw's
 * Bridge_Block.onPlace placement crash: NoSuchMethodError BlockState.is(Block)). These tests prove
 * the bridge synthetic is well-formed and that old call sites are devirtualized onto it.
 */
class IsOverloadBridgeTest {

    private static final String BS = "net/minecraft/world/level/block/state/BlockState";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String FS = "net/minecraft/world/level/material/FluidState";

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    @Test
    @DisplayName("the generated bridge has every helper the redirects target")
    void bridgeShape() {
        ClassNode cn = new ClassNode();
        new ClassReader(IsOverloadBridgeSynthetic.generate()).accept(cn, 0);
        assertEquals(IsOverloadBridgeSynthetic.INTERNAL, cn.name);
        Set<String> methods = new HashSet<>();
        cn.methods.forEach(m -> methods.add(m.name));
        for (String required : new String[]{"blockStateIs", "blockStateIsTag", "blockStateIsHolder",
                "blockStateIsKey", "itemStackIs", "itemStackIsTag", "itemStackIsHolder",
                "fluidStateIs", "fluidStateIsTag"}) {
            assertTrue(methods.contains(required), "bridge missing helper: " + required);
        }
    }

    /** A class exercising the four most common removed overloads exactly as javac emits them. */
    private static byte[] fixture() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "com/example/UsesIs", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "checks",
                "(L" + BS + ";L" + STACK + ";L" + FS + ";)V", null, null);
        mv.visitCode();
        // state.is(Blocks.X)
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ACONST_NULL);
        mv.visitTypeInsn(CHECKCAST, "net/minecraft/world/level/block/Block");
        mv.visitMethodInsn(INVOKEVIRTUAL, BS, "is", "(Lnet/minecraft/world/level/block/Block;)Z", false);
        mv.visitInsn(POP);
        // state.is(BlockTags.X)
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ACONST_NULL);
        mv.visitTypeInsn(CHECKCAST, "net/minecraft/tags/TagKey");
        mv.visitMethodInsn(INVOKEVIRTUAL, BS, "is", "(Lnet/minecraft/tags/TagKey;)Z", false);
        mv.visitInsn(POP);
        // stack.is(Items.X)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ACONST_NULL);
        mv.visitTypeInsn(CHECKCAST, "net/minecraft/world/item/Item");
        mv.visitMethodInsn(INVOKEVIRTUAL, STACK, "is", "(Lnet/minecraft/world/item/Item;)Z", false);
        mv.visitInsn(POP);
        // fluid.is(Fluids.X)
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ACONST_NULL);
        mv.visitTypeInsn(CHECKCAST, "net/minecraft/world/level/material/Fluid");
        mv.visitMethodInsn(INVOKEVIRTUAL, FS, "is", "(Lnet/minecraft/world/level/material/Fluid;)Z", false);
        mv.visitInsn(POP);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("removed is() calls are devirtualized onto the bridge; no raw calls survive")
    void callsAreRedirected() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        IsOverloadBridgeSynthetic.register(t);

        byte[] out = t.transformClass(fixture(), "com/example/UsesIs.class");
        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        var insns = cn.methods.stream().filter(m -> m.name.equals("checks")).findFirst().orElseThrow().instructions;

        int bridged = 0;
        for (AbstractInsnNode in : insns.toArray()) {
            if (!(in instanceof MethodInsnNode mi)) continue;
            if (mi.owner.equals(IsOverloadBridgeSynthetic.INTERNAL)) {
                assertEquals(INVOKESTATIC, mi.getOpcode(), "bridge calls must be static (devirtualized)");
                bridged++;
            } else if (mi.name.equals("is")) {
                fail("raw removed-overload call survived: " + mi.owner + ".is" + mi.desc);
            }
        }
        assertEquals(4, bridged, "all four removed-overload calls must route through the bridge");
        assertTrue(t.getSyntheticClasses().containsKey(IsOverloadBridgeSynthetic.INTERNAL),
                "the bridge class must be registered for embedding/writing into the mod jar");
    }

    private static final String SPE = "net/minecraft/world/level/levelgen/structure/pools/StructurePoolElement";
    private static final String SHUFFLED_DESC =
            "(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;"
            + "Lnet/minecraft/util/RandomSource;)Ljava/util/List;";

    /** A 1.21.x-style caller: element.getShuffledJigsawBlocks(...) then Registry.getHolder(key). */
    private static byte[] genFixture() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "com/example/Jigsaw", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "gen",
                "(L" + SPE + ";Lnet/minecraft/core/Registry;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        for (int i = 0; i < 4; i++) mv.visitInsn(ACONST_NULL);
        mv.visitMethodInsn(INVOKEVIRTUAL, SPE, "getShuffledJigsawBlocks", SHUFFLED_DESC, false);
        mv.visitInsn(POP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitInsn(ACONST_NULL);
        mv.visitTypeInsn(CHECKCAST, "net/minecraft/resources/ResourceKey");
        mv.visitMethodInsn(INVOKEINTERFACE, "net/minecraft/core/Registry", "getHolder",
                "(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", true);
        mv.visitInsn(POP);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("26.x generation-path breaks: jigsaw list unwrap + Registry.getHolder rename (YUNG's)")
    void generationPathRedirects() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        IsOverloadBridgeSynthetic.register(t);

        byte[] out = t.transformClass(genFixture(), "com/example/Jigsaw.class");
        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        var insns = cn.methods.stream().filter(m -> m.name.equals("gen")).findFirst().orElseThrow().instructions;

        boolean jigsawBridged = false, holderRenamed = false;
        for (AbstractInsnNode in : insns.toArray()) {
            if (!(in instanceof MethodInsnNode mi)) continue;
            if (mi.owner.equals(IsOverloadBridgeSynthetic.INTERNAL)
                    && mi.name.equals("shuffledJigsawBlocks")) {
                assertEquals(INVOKESTATIC, mi.getOpcode());
                jigsawBridged = true;
            }
            if (mi.owner.equals("net/minecraft/core/Registry") && mi.name.equals("get")
                    && mi.desc.equals("(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;")) {
                assertEquals(INVOKEINTERFACE, mi.getOpcode(),
                        "same-owner rename must keep the interface call opcode");
                holderRenamed = true;
            }
            assertNotEquals("getShuffledJigsawBlocks", mi.name,
                    "raw getShuffledJigsawBlocks call survived (26.x returns JigsawBlockInfo "
                    + "elements; the 1.21.x caller's cast dies CCE)");
            assertNotEquals("getHolder", mi.name, "raw getHolder call survived (renamed on 26.x)");
        }
        assertTrue(jigsawBridged, "jigsaw list call must route through the unwrap bridge");
        assertTrue(holderRenamed, "Registry.getHolder must be renamed to get");

        // the bridge helper itself must unwrap via JigsawBlockInfo.info()
        ClassNode bridge = new ClassNode();
        new ClassReader(IsOverloadBridgeSynthetic.generate()).accept(bridge, 0);
        var helper = bridge.methods.stream().filter(m -> m.name.equals("shuffledJigsawBlocks"))
                .findFirst().orElseThrow();
        boolean unwraps = false;
        for (AbstractInsnNode in : helper.instructions.toArray()) {
            if (in instanceof MethodInsnNode mi && mi.name.equals("info")) unwraps = true;
        }
        assertTrue(unwraps, "helper must unwrap each element via JigsawBlockInfo.info()");
    }
}
