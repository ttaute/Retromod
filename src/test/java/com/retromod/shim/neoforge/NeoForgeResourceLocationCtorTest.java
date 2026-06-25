/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for #92: the 2-arg {@code ResourceLocation(String,String)} ctor went private in 1.21,
 * so the shim rewrites the call to the {@code fromNamespaceAndPath} static factory.
 */
class NeoForgeResourceLocationCtorTest {

    private static final String RL = "net/minecraft/resources/ResourceLocation";

    private static byte[] classConstructingResourceLocation() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/rl/Maker", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "make", "()L" + RL + ";", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, RL);
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("ns");
        mv.visitLdcInsn("path");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, RL, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(4, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void ctorRewrittenToFactoryByNeoForgeShim() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            new NeoForge_1_20_6_to_1_21().registerRedirects(t);

            byte[] out = t.transformClass(classConstructingResourceLocation(), "test/rl/Maker");
            ClassNode cn = new ClassNode();
            new ClassReader(out).accept(cn, 0);
            var insns = cn.methods.stream().filter(m -> m.name.equals("make"))
                    .findFirst().orElseThrow().instructions;

            boolean privateCtorGone = true, factoryPresent = false, newGone = true;
            for (AbstractInsnNode insn : insns) {
                if (insn instanceof MethodInsnNode m && m.owner.equals(RL)) {
                    if (m.name.equals("<init>")) privateCtorGone = false;
                    if (m.name.equals("fromNamespaceAndPath") && m.getOpcode() == Opcodes.INVOKESTATIC) {
                        factoryPresent = true;
                        assertEquals("(Ljava/lang/String;Ljava/lang/String;)L" + RL + ";", m.desc);
                    }
                }
                if (insn instanceof TypeInsnNode ti && ti.getOpcode() == Opcodes.NEW && ti.desc.equals(RL)) {
                    newGone = false;
                }
            }
            assertTrue(privateCtorGone, "private 2-arg <init> call must be gone");
            assertTrue(factoryPresent, "must call ResourceLocation.fromNamespaceAndPath instead");
            assertTrue(newGone, "NEW ResourceLocation must be removed");
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
