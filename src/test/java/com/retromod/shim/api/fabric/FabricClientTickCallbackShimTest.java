/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks for the {@link FabricClientTickCallbackShim} synthetic and its redirect (#129).
 * The removed v0 {@code ClientTickCallback} must be replaced by a synthetic interface that keeps the
 * {@code tick(Minecraft)} SAM and an {@code EVENT} field wired, in {@code <clinit>}, to the runtime
 * bridge that fans it onto {@code ClientTickEvents.END_CLIENT_TICK}.
 */
class FabricClientTickCallbackShimTest {

    private static final String OLD   = "net/fabricmc/fabric/api/event/client/ClientTickCallback";
    private static final String SYNTH = "com/retromod/generated/legacytick/ClientTickCallback";
    private static final String BRIDGE = "com/retromod/shim/api/fabric/embedded/ClientTickCallbackBridge";
    private static final String SAM_DESC = "(Lnet/minecraft/client/Minecraft;)V";

    @BeforeAll
    static void pinHostTo26_1() {
        RetromodVersion.TARGET_MC_VERSION = "26.1";
    }

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    @Test
    @DisplayName("synthetic: functional interface, tick(Minecraft) is the only abstract, EVENT wired in <clinit>")
    void syntheticShape() {
        ClassNode cn = new ClassNode();
        new ClassReader(FabricClientTickCallbackShim.generateInterface()).accept(cn, 0);

        assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0, "must be an interface");
        assertEquals(SYNTH, cn.name);

        long abstracts = cn.methods.stream().filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).count();
        assertEquals(1, abstracts, "must stay a functional interface so old lambdas link");
        MethodNode sam = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).findFirst().orElseThrow();
        assertEquals("tick", sam.name, "old SAM name keeps mod lambdas/impls linking");
        assertEquals(SAM_DESC, sam.desc, "SAM param must be the remapped Mojang Minecraft type");

        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("EVENT")
                        && f.desc.equals("Lnet/fabricmc/fabric/api/event/Event;")),
                "EVENT field (typed Event) missing");

        MethodNode clinit = cn.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
        assertNotNull(clinit, "EVENT needs a static initializer");
        boolean callsInstall = false;
        for (AbstractInsnNode insn : clinit.instructions) {
            if (insn instanceof MethodInsnNode m
                    && m.owner.equals(BRIDGE) && m.name.equals("installEvent")) {
                callsInstall = true;
            }
        }
        assertTrue(callsInstall, "<clinit> must wire EVENT via ClientTickCallbackBridge.installEvent");
    }

    @Test
    @DisplayName("shim redirects the removed class onto the synthetic + registers the bridge (26.1 host)")
    void redirectAndRegistration() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricClientTickCallbackShim().registerRedirects(t);

        assertEquals(SYNTH, t.getClassRedirects().get(OLD), "old class must redirect to the synthetic");
        assertTrue(t.getSyntheticClasses().containsKey(SYNTH),
                "the synthetic interface must be registered for embedding");
    }

    @Test
    @DisplayName("a mod's ClientTickCallback.EVENT reference is rewritten to the synthetic (class loads)")
    void callerReferenceRewritten() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricClientTickCallbackShim().registerRedirects(t);

        byte[] out = t.transformClass(callerReferencingOld(), "net/example/ChatBubblesMod.class");
        assertNotNull(out);

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        MethodNode init = cn.methods.stream().filter(m -> m.name.equals("init")).findFirst().orElseThrow();

        boolean referencesOld = false, referencesSynth = false;
        for (AbstractInsnNode insn : init.instructions) {
            if (insn instanceof org.objectweb.asm.tree.FieldInsnNode f) {
                if (f.owner.equals(OLD)) referencesOld = true;
                if (f.owner.equals(SYNTH)) referencesSynth = true;
            }
        }
        assertFalse(referencesOld,
                "the deleted ClientTickCallback reference must be gone (else NoClassDefFoundError on load)");
        assertTrue(referencesSynth, "the reference must now point at the embedded synthetic");
    }

    /** A mod class doing {@code ClientTickCallback.EVENT} in init, the shape that NoClassDefFoundErrors. */
    private static byte[] callerReferencingOld() {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "net/example/ChatBubblesMod", null,
                "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "init", "()V", null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, OLD, "EVENT", "Lnet/fabricmc/fabric/api/event/Event;");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
