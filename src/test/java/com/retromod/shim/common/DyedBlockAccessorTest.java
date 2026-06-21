/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * 26.2 collapsed the 16-per-color block families into {@code ColorCollection<Block>}
 * fields and DELETED the per-color static fields ({@code Blocks.WHITE_CANDLE}, …). A
 * 1.21.x mod that reads such a field hits {@code NoSuchFieldError} on 26.2 - and it
 * can't be a field-to-field redirect because the replacement is a method call
 * ({@code Blocks.DYED_CANDLE.pick(DyeColor.WHITE)}). The new
 * {@code registerStaticFieldAccessor} mechanism rewrites the GETSTATIC into that
 * accessor sequence; the 26.1→26.2 core shim registers all 4 families × 16 colours.
 * Real case: YUNG's Extras ({@code SwampFeatureProcessor} builds a candle list).
 */
class DyedBlockAccessorTest {

    private static final String BLOCKS = "net/minecraft/world/level/block/Blocks";
    private static final String COLOR_COLLECTION = "net/minecraft/world/level/block/ColorCollection";
    private static final String DYE_COLOR = "net/minecraft/world/item/DyeColor";
    private static final String BLOCK = "net/minecraft/world/level/block/Block";
    private static final String BLOCK_DESC = "Lnet/minecraft/world/level/block/Block;";

    /** A class whose methods read the removed per-color fields directly. */
    private static byte[] classReadingRemovedFields() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, "test/candle/UsesDyed", null, "java/lang/Object", null);
        // Block whiteCandle() { return Blocks.WHITE_CANDLE; }
        MethodVisitor a = cw.visitMethod(ACC_PUBLIC, "whiteCandle", "()" + BLOCK_DESC, null, null);
        a.visitCode();
        a.visitFieldInsn(GETSTATIC, BLOCKS, "WHITE_CANDLE", BLOCK_DESC);
        a.visitInsn(ARETURN);
        a.visitMaxs(1, 1);
        a.visitEnd();
        // Block blackShulker() { return Blocks.BLACK_SHULKER_BOX; }
        MethodVisitor b = cw.visitMethod(ACC_PUBLIC, "blackShulker", "()" + BLOCK_DESC, null, null);
        b.visitCode();
        b.visitFieldInsn(GETSTATIC, BLOCKS, "BLACK_SHULKER_BOX", BLOCK_DESC);
        b.visitInsn(ARETURN);
        b.visitMaxs(1, 1);
        b.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static MethodNode method(ClassNode cn, String name) {
        return cn.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("all 4 dyed-block families × 16 colours register as static-field accessors")
    void allFamiliesRegistered() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            Mc26_1To26_2CoreMoves.register(t);
            assertEquals(64, t.getStaticFieldAccessorCount(),
                    "CANDLE + CANDLE_CAKE + SHULKER_BOX + TERRACOTTA, 16 colours each");
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    @Test
    @DisplayName("GETSTATIC of a removed per-color field becomes DYED_<F>.pick(DyeColor.<C>) + checkcast")
    void removedFieldBecomesAccessorCall() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            Mc26_1To26_2CoreMoves.register(t);
            byte[] out = t.transformClass(classReadingRemovedFields(), "test/candle/UsesDyed");
            ClassNode cn = new ClassNode();
            new ClassReader(out).accept(cn, 0);

            assertWhiteCandle(method(cn, "whiteCandle"));
            assertBlackShulker(method(cn, "blackShulker"));
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    private static void assertWhiteCandle(MethodNode m) {
        // The removed field must be gone.
        assertFalse(hasField(m, GETSTATIC, BLOCKS, "WHITE_CANDLE"),
                "the removed Blocks.WHITE_CANDLE access must be rewritten away");
        // The accessor sequence must be present, with the WHITE color.
        assertTrue(hasField(m, GETSTATIC, BLOCKS, "DYED_CANDLE"), "must load Blocks.DYED_CANDLE");
        assertTrue(hasField(m, GETSTATIC, DYE_COLOR, "WHITE"), "must load DyeColor.WHITE");
        assertTrue(hasMethod(m, INVOKEVIRTUAL, COLOR_COLLECTION, "pick"), "must call ColorCollection.pick");
        assertTrue(hasCast(m, BLOCK), "must checkcast the Object result back to Block");
    }

    private static void assertBlackShulker(MethodNode m) {
        assertFalse(hasField(m, GETSTATIC, BLOCKS, "BLACK_SHULKER_BOX"),
                "the removed Blocks.BLACK_SHULKER_BOX access must be rewritten away");
        assertTrue(hasField(m, GETSTATIC, BLOCKS, "DYED_SHULKER_BOX"), "must load the right family collection");
        assertTrue(hasField(m, GETSTATIC, DYE_COLOR, "BLACK"), "must load the right colour");
        assertTrue(hasMethod(m, INVOKEVIRTUAL, COLOR_COLLECTION, "pick"), "must call ColorCollection.pick");
        assertTrue(hasCast(m, BLOCK), "must checkcast back to Block");
    }

    private static boolean hasField(MethodNode m, int op, String owner, String name) {
        for (AbstractInsnNode in : m.instructions.toArray()) {
            if (in instanceof FieldInsnNode f && f.getOpcode() == op
                    && owner.equals(f.owner) && name.equals(f.name)) return true;
        }
        return false;
    }

    private static boolean hasMethod(MethodNode m, int op, String owner, String name) {
        for (AbstractInsnNode in : m.instructions.toArray()) {
            if (in instanceof MethodInsnNode mi && mi.getOpcode() == op
                    && owner.equals(mi.owner) && name.equals(mi.name)) return true;
        }
        return false;
    }

    private static boolean hasCast(MethodNode m, String type) {
        for (AbstractInsnNode in : m.instructions.toArray()) {
            if (in instanceof TypeInsnNode ti && ti.getOpcode() == CHECKCAST && type.equals(ti.desc)) return true;
        }
        return false;
    }
}
