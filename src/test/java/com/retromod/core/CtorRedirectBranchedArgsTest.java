/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Constructor-to-factory redirects whose ARG EXPRESSIONS contain branches or nested
 * constructors (snapshot.8 SS A, found live on Collective's DuskConfig on a 1.20.1 host).
 *
 * <p>The old single-slot deferral flushed the buffered NEW+DUP as soon as a nested plain
 * {@code new} appeared, i.e. mid-expression. With a ternary in the args that lands the
 * NEW+DUP on ONE branch path only, so the paths reach the convergent {@code <init>} with
 * different stack heights: ASM's COMPUTE_FRAMES dies (Frame.merge AIOOBE), the silent
 * COMPUTE_MAXS fallback preserved the original StackMapTable whose Uninitialized entries
 * pointed at the removed NEW, and the class died at load with
 * "StackMapTable format error: bad offset for Uninitialized".</p>
 *
 * <p>The deferral is now a STACK flushed only at the {@code <init>} convergence point,
 * and the COMPUTE_MAXS fallback refuses to ship frame-invalidating rewrites.</p>
 */
class CtorRedirectBranchedArgsTest {

    private static final String OLD = "net/minecraft/class_2588";
    private static final String FACTORY_OWNER = "net/minecraft/class_2561";
    private static final String FACTORY = "method_43471";
    private static final String FACTORY_DESC = "(Ljava/lang/String;)Lnet/minecraft/class_5250;";

    private RetromodTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = RetromodTransformer.getInstance();
        transformer.clearRedirectsForTesting();
        transformer.registerConstructorRedirect(OLD, "(Ljava/lang/String;)V",
                FACTORY_OWNER, FACTORY, FACTORY_DESC);
    }

    @AfterEach
    void tearDown() {
        transformer.clearRedirectsForTesting();
    }

    /** new class_2588(flag ? "gui.yes" : "gui.no") - a branch between DUP and &lt;init&gt;. */
    private static byte[] ternaryArgClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V17, ACC_PUBLIC, "test/TernaryArg", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "make",
                "(Z)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, OLD);
        mv.visitInsn(DUP);
        Label no = new Label();
        Label done = new Label();
        mv.visitVarInsn(ILOAD, 0);
        mv.visitJumpInsn(IFEQ, no);
        mv.visitLdcInsn("gui.yes");
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(no);
        mv.visitFrame(F_FULL, 1, new Object[]{INTEGER},
                2, new Object[]{OLD, OLD}); // uninit refs approximated; writer recomputes
        mv.visitLdcInsn("gui.no");
        mv.visitLabel(done);
        mv.visitFrame(F_FULL, 1, new Object[]{INTEGER},
                3, new Object[]{OLD, OLD, "java/lang/String"});
        mv.visitMethodInsn(INVOKESPECIAL, OLD, "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** new class_2588("k" + x) - a nested plain StringBuilder new inside the deferred args. */
    private static byte[] nestedPlainNewClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V17, ACC_PUBLIC, "test/NestedPlain", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "make",
                "(Ljava/lang/String;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, OLD);
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mv.visitLdcInsn("key.");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKESPECIAL, OLD, "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * The DuskConfig killer shape: branch inside the args AND a nested plain new on one
     * path only. new class_2588(flag ? ("a" + x via StringBuilder) : "b").
     */
    private static byte[] branchWithNestedNewClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V17, ACC_PUBLIC, "test/BranchNested", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "make",
                "(ZLjava/lang/String;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, OLD);
        mv.visitInsn(DUP);
        Label other = new Label();
        Label done = new Label();
        mv.visitVarInsn(ILOAD, 0);
        mv.visitJumpInsn(IFEQ, other);
        // fall-through path builds its arg with a nested plain new (the old code flushed
        // the deferred NEW+DUP right here, on this path only)
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mv.visitLdcInsn("a.");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                "()Ljava/lang/String;", false);
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(other);
        mv.visitLdcInsn("b");
        mv.visitLabel(done);
        mv.visitMethodInsn(INVOKESPECIAL, OLD, "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void assertRedirectedAndValid(byte[] in, String fileName) throws Exception {
        byte[] out = transformer.transformClass(in, fileName);
        assertNotNull(out);

        // EXPAND_FRAMES resolves every StackMapTable offset: a frame still pointing at a
        // removed NEW dies here (the in-game "bad offset for Uninitialized" failure mode)
        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, ClassReader.EXPAND_FRAMES);

        boolean sawFactory = false;
        boolean sawRawCtor = false;
        for (MethodNode mn : cn.methods) {
            // BasicVerifier catches branch-path stack-height mismatches without classloading
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(cn.name, mn);
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode mi && FACTORY_OWNER.equals(mi.owner)
                        && FACTORY.equals(mi.name)) {
                    sawFactory = true;
                }
                if (insn instanceof MethodInsnNode mi && OLD.equals(mi.owner)
                        && "<init>".equals(mi.name)) {
                    sawRawCtor = true;
                }
            }
        }
        assertTrue(sawFactory, "ctor->factory redirect must apply");
        assertFalse(sawRawCtor, "no raw removed-class constructor call may survive");
    }

    @Test
    @DisplayName("ternary in the ctor args: branch between DUP and <init> stays consistent")
    void ternaryArg() throws Exception {
        assertRedirectedAndValid(ternaryArgClass(), "test/TernaryArg.class");
    }

    @Test
    @DisplayName("nested plain new inside the deferred args does not forfeit the redirect")
    void nestedPlainNew() throws Exception {
        assertRedirectedAndValid(nestedPlainNewClass(), "test/NestedPlain.class");
    }

    @Test
    @DisplayName("branch + nested new on one path (the DuskConfig shape) transforms validly")
    void branchWithNestedNew() throws Exception {
        assertRedirectedAndValid(branchWithNestedNewClass(), "test/BranchNested.class");
    }
}
