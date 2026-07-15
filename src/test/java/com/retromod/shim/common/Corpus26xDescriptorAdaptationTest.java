/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Corpus-mined 26.x DESCRIPTOR adaptations (top-50 Fabric+NeoForge 1.21.1 linkcheck audit): vanilla
 * methods that still exist on 26.1 but changed a primitive type or lost their static form, wired via
 * {@link RetromodTransformer#registerConvertingRedirect} / {@code registerSingletonStaticRedirect} /
 * {@code registerArgDropMethodRedirect}. Drives the real shim registration and asserts each call site
 * is both retargeted AND gets the right conversion/POP spliced in.
 */
public class Corpus26xDescriptorAdaptationTest {

    private RetromodTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = RetromodTransformer.getInstance();
        transformer.clearRedirectsForTesting();
        Common_1_21_11_to_26_1_ClassMoves.registerCorpus26xDescriptorAdaptations(transformer);
    }

    @AfterEach
    void tearDown() {
        transformer.clearRedirectsForTesting();
    }

    /** Build test/Caller with a single provided body, transform it, and return the method's insns. */
    private List<AbstractInsnNode> transformBody(String methodDesc, java.util.function.Consumer<MethodVisitor> body) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/Caller", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "call", methodDesc, null, null);
        mv.visitCode();
        body.accept(mv);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] out = transformer.transformClass(cw.toByteArray(), "test/Caller");
        // Re-readable => COMPUTE_FRAMES in the transformer succeeded (else it would have thrown / fallen back).
        ClassNode cn = new ClassNode();
        assertDoesNotThrow(() -> new ClassReader(out).accept(cn, 0));
        List<AbstractInsnNode> insns = new ArrayList<>();
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("call")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) insns.add(i);
        }
        return insns;
    }

    private static MethodInsnNode firstCall(List<AbstractInsnNode> insns, String owner) {
        for (AbstractInsnNode i : insns) if (i instanceof MethodInsnNode mi && mi.owner.equals(owner)) return mi;
        return null;
    }

    private static boolean hasOpcodeBefore(List<AbstractInsnNode> insns, int opcode, MethodInsnNode call) {
        int ci = insns.indexOf(call);
        for (int i = 0; i < ci; i++) if (insns.get(i).getOpcode() == opcode) return true;
        return false;
    }

    private static boolean hasOpcodeAfter(List<AbstractInsnNode> insns, int opcode, MethodInsnNode call) {
        int ci = insns.indexOf(call);
        for (int i = ci + 1; i < insns.size(); i++) if (insns.get(i).getOpcode() == opcode) return true;
        return false;
    }

    @Test
    @DisplayName("Mth.cos(F)F -> cos(D)F with an F2D on the arg")
    void mthCosArgWiden() {
        // static float call(float f) { return Mth.cos(f); }  (we drop the return for simplicity)
        List<AbstractInsnNode> insns = transformBody("(F)V", mv -> {
            mv.visitVarInsn(FLOAD, 0);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraft/util/Mth", "cos", "(F)F", false);
            mv.visitInsn(POP);
        });
        MethodInsnNode c = firstCall(insns, "net/minecraft/util/Mth");
        assertNotNull(c, "the Mth.cos call must survive");
        assertEquals("(D)F", c.desc, "cos retargeted to the double overload");
        assertTrue(hasOpcodeBefore(insns, F2D, c), "an F2D must be spliced in before the call");
    }

    @Test
    @DisplayName("Window.getGuiScale()D -> ()I with an I2D on the result")
    void windowGuiScaleReturnWiden() {
        List<AbstractInsnNode> insns = transformBody("(Lcom/mojang/blaze3d/platform/Window;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "com/mojang/blaze3d/platform/Window", "getGuiScale", "()D", false);
            mv.visitInsn(POP2); // double -> the caller's expected 2-slot
        });
        MethodInsnNode c = firstCall(insns, "com/mojang/blaze3d/platform/Window");
        assertNotNull(c);
        assertEquals("()I", c.desc, "getGuiScale retargeted to the int form");
        assertTrue(hasOpcodeAfter(insns, I2D, c), "an I2D must be spliced in after the call");
    }

    @Test
    @DisplayName("SoundManager.play(SoundInstance)V -> ()PlayResult with a trailing POP")
    void soundPlayResultPop() {
        List<AbstractInsnNode> insns = transformBody("(Lnet/minecraft/client/sounds/SoundManager;Lnet/minecraft/client/resources/sounds/SoundInstance;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/client/sounds/SoundManager", "play",
                    "(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", false);
        });
        MethodInsnNode c = firstCall(insns, "net/minecraft/client/sounds/SoundManager");
        assertNotNull(c);
        assertTrue(c.desc.endsWith(")Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"),
                "play retargeted to the PlayResult-returning form");
        assertTrue(hasOpcodeAfter(insns, POP, c), "a POP must discard the now-returned PlayResult");
    }

    @Test
    @DisplayName("CompoundTag.getList(String,int) -> getListOrEmpty(String) dropping the int")
    void compoundTagGetListArgDrop() {
        List<AbstractInsnNode> insns = transformBody("(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_5); // the type-hint int (e.g. 10 = compound)
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "getList",
                    "(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;", false);
            mv.visitInsn(POP);
        });
        MethodInsnNode c = firstCall(insns, "net/minecraft/nbt/CompoundTag");
        assertNotNull(c);
        assertEquals("getListOrEmpty", c.name, "renamed to the type-hint-free getter");
        assertEquals("(Ljava/lang/String;)Lnet/minecraft/nbt/ListTag;", c.desc, "the int param is dropped");
        assertTrue(hasOpcodeBefore(insns, POP, c), "the trailing int must be popped before the call");
    }

    @Test
    @DisplayName("NBT 1.21.5 refactor: contains(String,int)->contains(String) (drop int)")
    void nbtContainsArgDrop() {
        List<AbstractInsnNode> insns = transformBody("(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ICONST_3); // the tag-type-hint int
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "contains",
                    "(Ljava/lang/String;I)Z", false);
            mv.visitInsn(POP);
        });
        MethodInsnNode c = firstCall(insns, "net/minecraft/nbt/CompoundTag");
        assertNotNull(c);
        assertEquals("(Ljava/lang/String;)Z", c.desc, "the type-hint int is dropped");
        assertTrue(hasOpcodeBefore(insns, POP, c), "the trailing int is popped before the call");
    }

    @Test
    @DisplayName("NBT: getCompound->getCompoundOrEmpty (CompoundTag + ListTag); remove()V->remove()Tag+POP")
    void nbtGetterRenamesAndRemove() {
        // CompoundTag.getCompound(String)CompoundTag -> getCompoundOrEmpty
        List<AbstractInsnNode> a = transformBody("(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "getCompound",
                    "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;", false);
            mv.visitInsn(POP);
        });
        assertEquals("getCompoundOrEmpty", firstCall(a, "net/minecraft/nbt/CompoundTag").name);

        // ListTag.getCompound(int)CompoundTag -> getCompoundOrEmpty
        List<AbstractInsnNode> b = transformBody("(Lnet/minecraft/nbt/ListTag;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitInsn(ICONST_0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/nbt/ListTag", "getCompound",
                    "(I)Lnet/minecraft/nbt/CompoundTag;", false);
            mv.visitInsn(POP);
        });
        assertEquals("getCompoundOrEmpty", firstCall(b, "net/minecraft/nbt/ListTag").name);

        // CompoundTag.remove(String)V -> remove(String)Tag + POP
        List<AbstractInsnNode> r = transformBody("(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "remove",
                    "(Ljava/lang/String;)V", false);
        });
        MethodInsnNode rc = firstCall(r, "net/minecraft/nbt/CompoundTag");
        assertTrue(rc.desc.endsWith(")Lnet/minecraft/nbt/Tag;"), "remove now returns Tag");
        assertTrue(hasOpcodeAfter(r, POP, rc), "the returned Tag is popped (old call was void)");
    }

    @Test
    @DisplayName("NBT: TagParser.parseTag -> parseCompoundFully (rename)")
    void nbtParseTagRename() {
        List<AbstractInsnNode> insns = transformBody("(Ljava/lang/String;)V", mv -> {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESTATIC, "net/minecraft/nbt/TagParser", "parseTag",
                    "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;", false);
            mv.visitInsn(POP);
        });
        assertEquals("parseCompoundFully", firstCall(insns, "net/minecraft/nbt/TagParser").name);
    }

    @Test
    @DisplayName("new ClickEvent(Action,String) -> RetroTextEvents.clickEvent factory + CHECKCAST (ctor bridge)")
    void clickEventCtorBridged() {
        List<AbstractInsnNode> insns = transformBody("()V", mv -> {
            mv.visitTypeInsn(NEW, "net/minecraft/network/chat/ClickEvent");
            mv.visitInsn(DUP);
            mv.visitInsn(ACONST_NULL);                 // the Action (type-only for the test)
            mv.visitLdcInsn("hello");                  // the legacy String value
            mv.visitMethodInsn(INVOKESPECIAL, "net/minecraft/network/chat/ClickEvent", "<init>",
                    "(Lnet/minecraft/network/chat/ClickEvent$Action;Ljava/lang/String;)V", false);
            mv.visitInsn(POP);
        });
        // the direct constructor call is gone
        assertFalse(insns.stream().anyMatch(i -> i instanceof MethodInsnNode mi
                && mi.name.equals("<init>") && mi.owner.equals("net/minecraft/network/chat/ClickEvent")),
                "the removed ClickEvent constructor must not be called");
        MethodInsnNode factory = insns.stream().filter(i -> i instanceof MethodInsnNode mi
                && mi.owner.equals("com/retromod/polyfill/minecraft/RetroTextEvents")).map(i -> (MethodInsnNode) i)
                .findFirst().orElse(null);
        assertNotNull(factory, "rewritten to the RetroTextEvents factory");
        assertEquals("clickEvent", factory.name);
        assertEquals(INVOKESTATIC, factory.getOpcode());
        assertTrue(insns.stream().anyMatch(i -> i instanceof TypeInsnNode ti
                && ti.getOpcode() == CHECKCAST && ti.desc.equals("net/minecraft/network/chat/ClickEvent")),
                "the Object-returning factory result is CHECKCAST back to ClickEvent");
    }

    @Test
    @DisplayName("RetroTextEvents factory fails safe (null) on an unmappable/absent action")
    void textEventFactoryFailSafe() {
        // Non-enum action, null action, and a legacy CUSTOM-like action all yield null (inert), never throw.
        assertNull(com.retromod.polyfill.minecraft.RetroTextEvents.clickEvent(null, "x"));
        assertNull(com.retromod.polyfill.minecraft.RetroTextEvents.clickEvent("not-an-enum", "x"));
        assertNull(com.retromod.polyfill.minecraft.RetroTextEvents.clickEvent(java.time.DayOfWeek.MONDAY, "x"),
                "an enum whose name isn't a ClickEvent action maps to null, not a crash");
        assertNull(com.retromod.polyfill.minecraft.RetroTextEvents.hoverEvent(null, new Object()));
    }

    @Test
    @DisplayName("Screen.hasControlDown/Shift/Alt (static) -> Minecraft.getInstance().hasX() (instance)")
    void screenModifierKeysSingleton() {
        for (String m : new String[]{"hasControlDown", "hasShiftDown", "hasAltDown"}) {
            List<AbstractInsnNode> insns = transformBody("()V", mv -> {
                mv.visitMethodInsn(INVOKESTATIC, "net/minecraft/client/gui/screens/Screen", m, "()Z", false);
                mv.visitInsn(POP);
            });
            // No more static call on Screen.
            assertNull(firstCall(insns, "net/minecraft/client/gui/screens/Screen"),
                    m + ": the static Screen call must be gone");
            List<MethodInsnNode> mc = new ArrayList<>();
            for (AbstractInsnNode i : insns)
                if (i instanceof MethodInsnNode mi && mi.owner.equals("net/minecraft/client/Minecraft")) mc.add(mi);
            assertEquals(2, mc.size(), m + ": expect getInstance() + the instance call");
            assertEquals("getInstance", mc.get(0).name);
            assertEquals(INVOKESTATIC, mc.get(0).getOpcode());
            assertEquals(m, mc.get(1).name);
            assertEquals(INVOKEVIRTUAL, mc.get(1).getOpcode());
        }
    }
}
