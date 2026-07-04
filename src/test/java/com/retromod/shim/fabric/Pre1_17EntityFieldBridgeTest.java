/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * A 1.16.5 mod's direct {@code entity.onGround} access must become the accessor calls on a
 * host where the field went non-public (1.17 access cleanup): otherwise it dies
 * {@code IllegalAccessError} at first touch (Collective on 1.20.1, snapshot.8 round 6).
 */
class Pre1_17EntityFieldBridgeTest {

    private static final String ENTITY = "net/minecraft/class_1297";

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    /** A mod class reading AND writing Entity.field_5952 directly (the 1.16.5 idiom). */
    private static byte[] directFieldAccessClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/GroundChecker", null, "java/lang/Object", null);

        MethodVisitor read = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "isGrounded",
                "(L" + ENTITY + ";)Z", null, null);
        read.visitCode();
        read.visitVarInsn(ALOAD, 0);
        read.visitFieldInsn(GETFIELD, ENTITY, "field_5952", "Z");
        read.visitInsn(IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();

        MethodVisitor write = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "ground",
                "(L" + ENTITY + ";)V", null, null);
        write.visitCode();
        write.visitVarInsn(ALOAD, 0);
        write.visitInsn(ICONST_1);
        write.visitFieldInsn(PUTFIELD, ENTITY, "field_5952", "Z");
        write.visitInsn(RETURN);
        write.visitMaxs(0, 0);
        write.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("GETFIELD/PUTFIELD field_5952 become isOnGround()/setOnGround(boolean)")
    void fieldAccessBecomesAccessorCalls() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        // mirror Pre1_17EntityFieldBridge's registration (its register() is host-probing
        // and correctly no-ops in a JUnit JVM with no Minecraft on the classpath)
        t.registerFieldAccessorRedirect(ENTITY, "field_5952",
                "method_24828", "()Z", "method_24830", "(Z)V");

        byte[] out = t.transformClass(directFieldAccessClass(), "test/GroundChecker.class");
        assertNotNull(out);

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);

        boolean sawGetter = false;
        boolean sawSetter = false;
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof FieldInsnNode fi && "field_5952".equals(fi.name)) {
                    fail("direct field access survived in " + mn.name
                            + " (IllegalAccessError at runtime): " + fi.owner + "." + fi.name);
                }
                if (insn instanceof MethodInsnNode mi && ENTITY.equals(mi.owner)) {
                    if ("method_24828".equals(mi.name) && "()Z".equals(mi.desc)
                            && mi.getOpcode() == INVOKEVIRTUAL) {
                        sawGetter = true;
                    }
                    if ("method_24830".equals(mi.name) && "(Z)V".equals(mi.desc)
                            && mi.getOpcode() == INVOKEVIRTUAL) {
                        sawSetter = true;
                    }
                }
            }
        }
        assertTrue(sawGetter, "GETFIELD must become the isOnGround() call");
        assertTrue(sawSetter, "PUTFIELD must become the setOnGround(boolean) call");
    }
}
