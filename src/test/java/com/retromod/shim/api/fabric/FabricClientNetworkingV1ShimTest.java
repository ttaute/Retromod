/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Positive-path coverage for the Fabric ClientPlayNetworking v1 raw-channel bridge (#51).
 *
 * <p>The host <i>gate</i> (registers nothing below 26.1) is covered by
 * {@link Fabric26ApiBridgeGateTest}. This test pins the 26.1+ behaviour: the removed static
 * entrypoints ({@code send}/{@code registerGlobalReceiver}/{@code canSend}) are redirected to
 * the reflective {@code ClientPlayNetworkingV1Bridge}, and the {@code PlayChannelHandler} SAM
 * is kept alive as a synthetic interface and class-redirected onto it.</p>
 *
 * <p>The actual packet round-trip is reflective and only resolves on a real 26.1 client, so it
 * is verified in-game - this locks the bytecode wiring against regressions.</p>
 */
class FabricClientNetworkingV1ShimTest {

    private static final String OLD_CPN = "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking";
    private static final String OLD_SAM = OLD_CPN + "$PlayChannelHandler";
    private static final String NEW_SAM = "com/retromod/generated/legacynet/LegacyPlayChannelHandler";
    private static final String BRIDGE  = "com/retromod/shim/api/fabric/embedded/ClientPlayNetworkingV1Bridge";
    private static final String ID  = "Lnet/minecraft/class_2960;";
    private static final String BUF = "Lnet/minecraft/class_2540;";

    @BeforeEach
    void pin26AndRegister() {
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        new FabricClientNetworkingV1Shim().registerRedirects(t);
    }

    @AfterEach
    void restore() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "26.1"; // other shim tests expect 26.1 ambient
    }

    @Test
    void playChannelHandlerSamKeptAndRedirected() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        assertEquals(NEW_SAM, t.getClassRedirects().get(OLD_SAM),
                "removed PlayChannelHandler SAM must redirect to the kept synthetic");

        byte[] sam = t.getSyntheticClasses().get(NEW_SAM);
        assertNotNull(sam, "the synthetic SAM interface must be registered");
        int[] access = {0};
        boolean[] hasReceive = {false};
        new ClassReader(sam).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int v, int a, String n, String s, String sup, String[] i) { access[0] = a; }
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                if ("receive".equals(n)) hasReceive[0] = true;
                return null;
            }
        }, 0);
        assertNotEquals(0, access[0] & Opcodes.ACC_INTERFACE, "kept SAM must be an interface");
        assertTrue(hasReceive[0], "kept SAM must declare receive(...)");
    }

    @Test
    void removedSendRedirectedToBridge() {
        byte[] out = RetromodTransformer.getInstance()
                .transformClass(fixtureCalling("send", "(" + ID + BUF + ")V"), "test/NetFixture");
        assertCallSite(out, "send");
    }

    @Test
    void removedRegisterGlobalReceiverRedirectedToBridge() {
        // The handler param is the old SAM; the class redirect rewrites it to NEW_SAM in the
        // descriptor before the method redirect (keyed on NEW_SAM) matches - the realistic path.
        byte[] out = RetromodTransformer.getInstance()
                .transformClass(fixtureCalling("registerGlobalReceiver", "(" + ID + "L" + OLD_SAM + ";)Z"),
                        "test/NetFixture");
        assertCallSite(out, "registerGlobalReceiver");
    }

    @Test
    void removedCanSendRedirectedToBridge() {
        byte[] out = RetromodTransformer.getInstance()
                .transformClass(fixtureCalling("canSend", "(" + ID + ")Z"), "test/NetFixture");
        assertCallSite(out, "canSend");
    }

    /** A {@code test/NetFixture} with {@code go()} that calls {@code ClientPlayNetworking.<name><desc>}. */
    private static byte[] fixtureCalling(String name, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/NetFixture", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "go", "()V", null, null);
        mv.visitCode();
        for (Type ignored : Type.getArgumentTypes(desc)) mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, OLD_CPN, name, desc, false);
        if (Type.getReturnType(desc).getSort() != Type.VOID) mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Assert the named call now targets the bridge and no call to the removed API remains. */
    private static void assertCallSite(byte[] clazz, String name) {
        boolean[] toBridge = {false};
        boolean[] leftover = {false};
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String o, String nm, String de, boolean itf) {
                        if (BRIDGE.equals(o) && name.equals(nm)) toBridge[0] = true;
                        if (OLD_CPN.equals(o) && name.equals(nm)) leftover[0] = true;
                    }
                };
            }
        }, 0);
        assertTrue(toBridge[0], name + "() must be redirected to " + BRIDGE);
        assertFalse(leftover[0], "no leftover call to the removed ClientPlayNetworking." + name + "()");
    }
}
