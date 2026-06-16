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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Tier-2 render-state soft-fail (Vulkan/26.x): the imperative {@code RenderSystem}
 * state setters were deleted in the blaze3d GpuDevice/RenderPipeline refactor, so
 * an old mod that calls {@code RenderSystem.enableBlend()} etc. dies with
 * {@code NoSuchMethodError} on a modern host — and there's no surviving method to
 * redirect to. {@link RemovedRenderStateNeutralize} registers them for
 * neutralization: the call is dropped (args + receiver popped, default return
 * pushed) so the mod LOADS and runs.
 *
 * <p>The strong assertion here is that a class which <i>only</i> calls neutralized
 * methods both (a) loses every {@code RenderSystem} reference and (b) still
 * <b>verifies and runs</b> with {@code RenderSystem} entirely absent from the
 * classpath — proving the pop/push sequence is stack-balanced, not just that the
 * call name is gone.
 */
class RemovedRenderStateNeutralizeTest {

    private static final String RS = "com/mojang/blaze3d/systems/RenderSystem";

    /** A class whose render() calls ONLY removed state setters (void, primitive args). */
    private static byte[] classOnlyNeutralizedCalls() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V17, ACC_PUBLIC, "test/render/OnlyDead", null, "java/lang/Object", null);
        MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(ALOAD, 0);
        c.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(RETURN);
        c.visitMaxs(1, 1);
        c.visitEnd();

        MethodVisitor m = cw.visitMethod(ACC_PUBLIC, "render", "()V", null, null);
        m.visitCode();
        m.visitMethodInsn(INVOKESTATIC, RS, "enableBlend", "()V", false);     // no args
        m.visitInsn(ICONST_1); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKESTATIC, RS, "blendFunc", "(II)V", false);     // 2 ints
        m.visitInsn(ICONST_1);
        m.visitMethodInsn(INVOKESTATIC, RS, "depthMask", "(Z)V", false);      // 1 bool
        m.visitInsn(ICONST_1); m.visitInsn(ICONST_1); m.visitInsn(ICONST_1); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKESTATIC, RS, "colorMask", "(ZZZZ)V", false);   // 4 bools
        m.visitInsn(RETURN);
        m.visitMaxs(4, 1);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static ClassNode parse(byte[] b) {
        ClassNode cn = new ClassNode();
        new ClassReader(b).accept(cn, 0);
        return cn;
    }

    private static long renderSystemCalls(ClassNode cn) {
        return cn.methods.stream()
                .filter(mn -> mn.instructions != null)
                .flatMap(mn -> java.util.Arrays.stream(mn.instructions.toArray()))
                .filter(in -> in instanceof MethodInsnNode min && RS.equals(min.owner))
                .count();
    }

    @Test
    @DisplayName("removed RenderSystem state calls are neutralized; class loads + runs without RenderSystem present")
    void neutralizedClassLoadsAndRuns() throws Exception {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            RemovedRenderStateNeutralize.register(t);
            byte[] out = t.transformClass(classOnlyNeutralizedCalls(), "test/render/OnlyDead");

            // (a) every RenderSystem reference is gone from the bytecode
            assertEquals(0, renderSystemCalls(parse(out)),
                    "all removed RenderSystem state calls must be neutralized away");

            // (b) the class verifies and the popped/pushed sequence is balanced:
            // define it in a loader that has NO RenderSystem on its classpath and
            // actually invoke render() — a NoSuchMethodError or a VerifyError here
            // would mean a call survived or the stack was left unbalanced.
            DefiningLoader dl = new DefiningLoader(getClass().getClassLoader());
            Class<?> defined = dl.define(out);
            Object inst = defined.getDeclaredConstructor().newInstance();
            defined.getMethod("render").invoke(inst); // must not throw
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    @Test
    @DisplayName("non-registered RenderSystem methods are NOT neutralized")
    void liveMethodsArePreserved() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            RemovedRenderStateNeutralize.register(t);
            // A class calling getBackendDescription() — a method that DOES survive
            // and is not in the neutralize set — must keep its call intact.
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(V17, ACC_PUBLIC, "test/render/Live", null, "java/lang/Object", null);
            MethodVisitor m = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "f", "()V", null, null);
            m.visitCode();
            m.visitMethodInsn(INVOKESTATIC, RS, "getBackendDescription", "()Ljava/lang/String;", false);
            m.visitInsn(POP);
            m.visitInsn(RETURN);
            m.visitMaxs(1, 0);
            m.visitEnd();
            cw.visitEnd();

            byte[] out = t.transformClass(cw.toByteArray(), "test/render/Live");
            boolean kept = java.util.Arrays.stream(parse(out).methods.get(0).instructions.toArray())
                    .anyMatch(in -> in instanceof MethodInsnNode min
                            && RS.equals(min.owner) && "getBackendDescription".equals(min.name));
            assertTrue(kept, "a surviving RenderSystem method must not be neutralized");
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    /** Loader with no MC on its classpath, used to prove the transformed class verifies standalone. */
    private static final class DefiningLoader extends ClassLoader {
        DefiningLoader(ClassLoader parent) { super(parent); }
        Class<?> define(byte[] b) { return defineClass("test.render.OnlyDead", b, 0, b.length); }
    }
}
