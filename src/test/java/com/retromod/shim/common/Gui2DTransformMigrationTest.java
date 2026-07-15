/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Phase 0 of the GUI 2D-transform migration: the immediate {@code guiGraphics.pose().pushPose()} /
 * {@code .popPose()} peephole becomes {@code pose():Matrix3x2fStack} + {@code pushMatrix()/popMatrix()}
 * + {@code POP}; anything non-adjacent (a stored stack) is left exactly as-is.
 */
class Gui2DTransformMigrationTest {

    private static final String GUI = "net/minecraft/client/gui/GuiGraphics";
    private static final String POSE = "com/mojang/blaze3d/vertex/PoseStack";
    private static final String M3 = "org/joml/Matrix3x2fStack";
    private static final String POSE_OLD = "()Lcom/mojang/blaze3d/vertex/PoseStack;";
    private static final String M3_DESC = "()Lorg/joml/Matrix3x2fStack;";

    private static byte[] drawClass(Consumer<MethodVisitor> body) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/G", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "draw", "(L" + GUI + ";)V", null, null);
        mv.visitCode();
        body.accept(mv);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void poseCall(MethodVisitor mv) {
        mv.visitMethodInsn(INVOKEVIRTUAL, GUI, "pose", POSE_OLD, false);
    }

    private static List<MethodInsnNode> calls(byte[] b) {
        ClassNode cn = new ClassNode();
        new ClassReader(b).accept(cn, 0);
        List<MethodInsnNode> out = new ArrayList<>();
        for (MethodNode m : cn.methods) {
            for (var i : m.instructions.toArray()) if (i instanceof MethodInsnNode mi) out.add(mi);
        }
        return out;
    }

    private static int pops(byte[] b) {
        ClassNode cn = new ClassNode();
        new ClassReader(b).accept(cn, 0);
        int p = 0;
        for (MethodNode m : cn.methods) for (var i : m.instructions.toArray()) if (i.getOpcode() == POP) p++;
        return p;
    }

    @Test
    @DisplayName("immediate pose().pushPose()/popPose() -> pose():Matrix3x2fStack + pushMatrix()/popMatrix() + POP")
    void immediateChainMigrated() {
        byte[] in = drawClass(mv -> {
            mv.visitVarInsn(ALOAD, 0); poseCall(mv);
            mv.visitMethodInsn(INVOKEVIRTUAL, POSE, "pushPose", "()V", false);
            mv.visitVarInsn(ALOAD, 0); poseCall(mv);
            mv.visitMethodInsn(INVOKEVIRTUAL, POSE, "popPose", "()V", false);
        });
        byte[] out = Gui2DTransformMigration.migrate(in);
        assertNotEquals(in.length == out.length && java.util.Arrays.equals(in, out), true,
                "the immediate chain must be rewritten");

        List<MethodInsnNode> c = calls(out);
        assertTrue(c.stream().anyMatch(mi -> mi.owner.equals(M3) && mi.name.equals("pushMatrix") && mi.desc.equals(M3_DESC)),
                "pushPose -> Matrix3x2fStack.pushMatrix()");
        assertTrue(c.stream().anyMatch(mi -> mi.owner.equals(M3) && mi.name.equals("popMatrix")),
                "popPose -> Matrix3x2fStack.popMatrix()");
        assertFalse(c.stream().anyMatch(mi -> mi.name.equals("pushPose") || mi.name.equals("popPose")),
                "the old void ops must be gone");
        assertTrue(c.stream().filter(mi -> mi.name.equals("pose")).allMatch(mi -> mi.desc.equals(M3_DESC)),
                "each consumed pose() is retyped to return the 2D stack");
        assertEquals(2, pops(out), "each fluent op's return is popped (the old call was void)");

        // structurally re-readable (COMPUTE_FRAMES succeeded, else migrate would have returned the input)
        assertDoesNotThrow(() -> new ClassReader(out).accept(new ClassNode(), 0));
    }

    @Test
    @DisplayName("a stored (non-adjacent) pose stack is left completely untouched")
    void storedStackNotMigrated() {
        byte[] in = drawClass(mv -> {
            mv.visitVarInsn(ALOAD, 0); poseCall(mv);
            mv.visitVarInsn(ASTORE, 1);            // PoseStack ps = g.pose();
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, POSE, "pushPose", "()V", false); // ps.pushPose();
        });
        byte[] out = Gui2DTransformMigration.migrate(in);
        assertArrayEquals(in, out, "a non-adjacent (stored) pose stack must not be migrated in Phase 0");
    }

    @Test
    @DisplayName("a class that never mentions pushPose/popPose is returned as-is (cheap pre-filter)")
    void unrelatedClassUntouched() {
        byte[] in = drawClass(mv -> {
            mv.visitVarInsn(ALOAD, 0); poseCall(mv); mv.visitInsn(POP);
        });
        assertSame(in, Gui2DTransformMigration.migrate(in), "no push/popPose -> no parse, same array back");
    }
}
