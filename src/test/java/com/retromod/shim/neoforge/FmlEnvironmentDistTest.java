/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the {@code FMLEnvironment.dist} field → {@code getDist()} method rewrite
 * in the NeoForge 1.21.11→26.1 shim. 26.x removed the static {@code dist} field; the
 * ubiquitous client/server check {@code FMLEnvironment.dist} must become
 * {@code INVOKESTATIC FMLEnvironment.getDist()} or NeoForge/Forge mods NoSuchFieldError.
 */
class FmlEnvironmentDistTest {

    private static final String FML = "net/neoforged/fml/loading/FMLEnvironment";
    private static final String DIST = "Lnet/neoforged/api/distmarker/Dist;";

    /** A method that reads {@code FMLEnvironment.dist} and returns it. */
    private static byte[] fixtureReadingDist() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/FmlUser", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "side", "()" + DIST, null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, FML, "dist", DIST);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void distFieldRewrittenToGetDistMethod() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        new NeoForge_1_21_11_to_26_1().registerRedirects(t);

        byte[] out = t.transformClass(fixtureReadingDist(), "test/FmlUser");

        boolean[] hit = {false, false}; // [0]=INVOKESTATIC getDist, [1]=leftover GETSTATIC dist
        new ClassReader(out).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String o, String nm, String de, boolean i) {
                        if (op == Opcodes.INVOKESTATIC && o.equals(FML) && nm.equals("getDist")) hit[0] = true;
                    }
                    @Override public void visitFieldInsn(int op, String o, String nm, String de) {
                        if (op == Opcodes.GETSTATIC && o.equals(FML) && nm.equals("dist")) hit[1] = true;
                    }
                };
            }
        }, 0);

        assertTrue(hit[0], "GETSTATIC FMLEnvironment.dist should become INVOKESTATIC FMLEnvironment.getDist()");
        assertFalse(hit[1], "no leftover GETSTATIC dist field access should remain");
        t.clearRedirectsForTesting();
    }
}
