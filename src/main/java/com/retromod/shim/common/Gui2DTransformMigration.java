/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.util.SafeClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 26.x GUI 2D-transform migration, Phase 0 (peephole).
 *
 * <p>26.x moved GUI rendering off the 3D {@code com.mojang.blaze3d.vertex.PoseStack} onto a 2D
 * {@code org.joml.Matrix3x2fStack}: {@code GuiGraphics.pose()} now returns the 2D stack, whose ops
 * are {@code pushMatrix()/popMatrix()} (fluent) rather than {@code pushPose()/popPose()} (void).
 * {@code PoseStack} still exists for 3D world rendering, so a type-blind redirect would corrupt 3D
 * (see the design RFC: {@code docs/design/gui-2d-transform-migration.md}).
 *
 * <p>This phase handles ONLY the immediate, no-arg chain a 1.21.1 mod compiles for
 * {@code guiGraphics.pose().pushPose()} / {@code .popPose()}: the {@code pose()} call is IMMEDIATELY
 * followed by the {@code pushPose()}/{@code popPose()} call (the pose result is consumed on the spot,
 * never stored), so it is a self-contained two-instruction peephole with no dataflow, no local
 * retyping, and no chain-consistency concern. Each such pair is rewritten to
 * {@code pose():Matrix3x2fStack} + {@code pushMatrix()/popMatrix()} + {@code POP} (the old void call
 * becomes a fluent one whose return is popped). Anything else - a stored stack, {@code translate}/
 * {@code scale}/{@code mulPose}, non-adjacent uses - is left untouched (unresolved exactly as before,
 * never worse), and any re-emit failure returns the original bytes. So this can only migrate the
 * clean case or no-op; it can never make a class worse.
 *
 * <p>NeoForge/Forge only for now (Mojang-named; runs before the class redirect renames
 * {@code GuiGraphics} to {@code GuiGraphicsExtractor}). Fabric (intermediary names) and the
 * arg-carrying ops are later phases. Gated by the caller to 26.1+ hosts.
 */
public final class Gui2DTransformMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-gui2d");

    private static final String GUI = "net/minecraft/client/gui/GuiGraphics";
    private static final String GUI_EXTRACTOR = "net/minecraft/client/gui/GuiGraphicsExtractor";
    private static final String POSESTACK = "com/mojang/blaze3d/vertex/PoseStack";
    private static final String MATRIX3X2 = "org/joml/Matrix3x2fStack";
    private static final String POSE_DESC_OLD = "()Lcom/mojang/blaze3d/vertex/PoseStack;";
    private static final String POSE_DESC_NEW = "()Lorg/joml/Matrix3x2fStack;";
    private static final String MATRIX_OP_DESC = "()Lorg/joml/Matrix3x2fStack;";

    /** void PoseStack op -> the fluent Matrix3x2fStack op it became (no-arg only for Phase 0). */
    private static final Map<String, String> NO_ARG_OPS = Map.of(
            "pushPose", "pushMatrix",
            "popPose", "popMatrix");

    private Gui2DTransformMigration() {}

    /**
     * Rewrite the immediate {@code guiGraphics.pose().pushPose()/popPose()} peephole. Returns the
     * input unchanged when nothing matches or on any failure (so it can never ship broken bytecode).
     */
    public static byte[] migrate(byte[] classBytes) {
        // Cheap pre-filter: skip the parse unless a pushPose/popPose name is even in the pool.
        if (!referencesPoseOp(classBytes)) return classBytes;
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            int rewrites = 0;
            for (MethodNode m : cn.methods) rewrites += migrateMethod(m);
            if (rewrites == 0) return classBytes;
            SafeClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] out = cw.toByteArray();
            LOGGER.debug("GUI 2D migration: rewrote {} pose().push/popPose peephole(s) in {}",
                    rewrites, cn.name);
            return out;
        } catch (Throwable t) {
            // Never ship a failed transform: leave the class exactly as it was.
            LOGGER.debug("GUI 2D migration skipped a class ({}); left unchanged", t.toString());
            return classBytes;
        }
    }

    private static int migrateMethod(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode op)) continue;
            if (op.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            String matrixOp = NO_ARG_OPS.get(op.name);
            if (matrixOp == null || !POSESTACK.equals(op.owner) || !"()V".equals(op.desc)) continue;

            // The receiver must be produced by an IMMEDIATELY-preceding GuiGraphics(Extractor).pose().
            AbstractInsnNode prev = prevRealInsn(op);
            if (!(prev instanceof MethodInsnNode pose)) continue;
            if (pose.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !"pose".equals(pose.name)
                    || !(GUI.equals(pose.owner) || GUI_EXTRACTOR.equals(pose.owner))
                    || !POSE_DESC_OLD.equals(pose.desc)) {
                continue;
            }

            // Retype the pose() result to the 2D stack and turn the void op into the fluent 2D op,
            // popping its return. Stack effect is unchanged (receiver consumed, nothing left).
            pose.desc = POSE_DESC_NEW;
            op.owner = MATRIX3X2;
            op.name = matrixOp;
            op.desc = MATRIX_OP_DESC;
            m.instructions.insert(op, new InsnNode(Opcodes.POP));
            n++;
        }
        return n;
    }

    /** The previous real bytecode instruction, skipping labels/line-numbers/frames. */
    private static AbstractInsnNode prevRealInsn(AbstractInsnNode insn) {
        AbstractInsnNode p = insn.getPrevious();
        while (p != null && (p instanceof LabelNode || p instanceof LineNumberNode || p instanceof FrameNode)) {
            p = p.getPrevious();
        }
        return p;
    }

    /** Cheap UTF-8 byte scan: does the class pool even mention pushPose/popPose? */
    private static boolean referencesPoseOp(byte[] b) {
        return indexOf(b, "pushPose".getBytes()) >= 0 || indexOf(b, "popPose".getBytes()) >= 0;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
