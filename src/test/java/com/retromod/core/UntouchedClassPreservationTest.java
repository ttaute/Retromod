/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * A class no redirect touches must come out of {@link RetromodTransformer#transformClass}
 * BYTE-IDENTICAL. The transform loop re-serializes with COMPUTE_FRAMES; for classes whose type
 * hierarchy isn't loadable at transform time (JiJ'd libraries), the frame recomputation merges
 * unknown sister types to java/lang/Object and the rewritten class fails verification at runtime.
 * Found on the 26.2 dedicated server: YungsApi's bundled javassist died with
 * "VerifyError: Bad type on operand stack in ConstPool.readOne" and took the whole
 * Reflections-based @AutoRegister registration path down with it.
 */
class UntouchedClassPreservationTest {

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    /**
     * Mimics the javassist shape: a branch merge whose incoming stack types are two SISTER
     * classes (off-classpath at transform time) with a common superclass that is also
     * off-classpath. COMPUTE_FRAMES can only merge them to Object; the original frames
     * (written here with COMPUTE_MAXS + hand frames) know the real common supertype.
     */
    private static byte[] offClasspathMergeFixture() {
        String base = "com/thirdparty/lib/Info";
        String a = "com/thirdparty/lib/Utf8Info";
        String b = "com/thirdparty/lib/ClassInfo";
        ClassWriter cw = new ClassWriter(0);   // hand-written frames, no recomputation
        cw.visit(V17, ACC_PUBLIC, "com/thirdparty/lib/Reader", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "readOne",
                "(I)L" + base + ";", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 0);
        Label elseL = new Label(), end = new Label();
        mv.visitJumpInsn(IFEQ, elseL);
        mv.visitTypeInsn(NEW, a);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, a, "<init>", "()V", false);
        mv.visitJumpInsn(GOTO, end);
        mv.visitLabel(elseL);
        mv.visitFrame(F_SAME, 0, null, 0, null);
        mv.visitTypeInsn(NEW, b);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, b, "<init>", "()V", false);
        mv.visitLabel(end);
        // the merge point: the REAL common type is Info, which only the original frames know
        mv.visitFrame(F_SAME1, 0, null, 1, new Object[]{base});
        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("a class no redirect touches ships byte-identical (original frames preserved)")
    void untouchedClassIsByteIdentical() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        // redirects exist (so the transform doesn't take the empty-maps shortcut), but none match
        t.registerClassRedirect("net/minecraft/some/OldClass", "net/minecraft/some/NewClass");
        t.registerMethodRedirect("net/minecraft/some/Owner", "oldMethod", "()V",
                "net/minecraft/some/Owner", "newMethod", "()V");

        byte[] in = offClasspathMergeFixture();
        byte[] out = t.transformClass(in, "com/thirdparty/lib/Reader.class");
        assertArrayEquals(in, out,
                "an untouched class must not be re-serialized (COMPUTE_FRAMES would merge the "
                + "off-classpath sister types to java/lang/Object and break verification)");
    }

    @Test
    @DisplayName("a class a redirect DOES touch is still rewritten")
    void touchedClassIsStillTransformed() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        t.registerClassRedirect("com/thirdparty/lib/Utf8Info", "com/thirdparty/lib/Renamed");

        byte[] in = offClasspathMergeFixture();
        byte[] out = t.transformClass(in, "com/thirdparty/lib/Reader.class");
        String text = new String(out, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("com/thirdparty/lib/Renamed"), "matching redirect must still apply");
        assertFalse(java.util.Arrays.equals(in, out), "changed class must produce new bytes");
    }
}
