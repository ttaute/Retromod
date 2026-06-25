/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import com.retromod.polyfill.minecraft.item.ItemComponentPolyfill;
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

/**
 * Regression test for the spawn-mod bootstrap crash (snapshot.3 in-game pass):
 * the ItemStack NBT polyfill registered instance→static redirects through the
 * 6-arg {@code registerMethodRedirect} form (no devirtualize flag), so
 * {@code ItemStack.getTag()} was rewritten to
 * {@code INVOKEVIRTUAL ItemStackNbtBridge.getTag(Object)}, an instruction that
 * pops a receiver <i>plus</i> an argument where the call site only pushed the
 * receiver. The resulting stack underflow made ASM's {@code Frame.merge} throw
 * {@code ArrayIndexOutOfBoundsException} when Mixin recomputed frames over the
 * broken handler ({@code MobBucketItemMixin} → {@code Bootstrap} crash).
 *
 * <p>The fix is in the transformer's emit path: an instance call redirected to a
 * descriptor with exactly one extra parameter is auto-devirtualized to
 * {@code INVOKESTATIC} (receiver-as-arg0 is the only meaning that shape can have).
 */
class AutoDevirtualizeTest {

    private static byte[] classCalling(String owner, String method, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/devirt/Caller", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "use", "(L" + owner + ";)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);                 // push ONLY the receiver
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, method, desc, false);
        if (desc.endsWith(")V")) { /* nothing */ } else { mv.visitInsn(Opcodes.POP); }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("ItemStack.getTag() (6-arg polyfill registration) emits INVOKESTATIC, not INVOKEVIRTUAL")
    void getTagIsDevirtualized() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new ItemComponentPolyfill().registerPolyfills(t); // the real registrations that crashed

        byte[] in = classCalling("net/minecraft/world/item/ItemStack",
                "getTag", "()Lnet/minecraft/nbt/CompoundTag;");
        byte[] out = t.transformClass(in, "test/devirt/Caller");

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        MethodInsnNode call = null;
        for (AbstractInsnNode insn : cn.methods.stream()
                .filter(m -> m.name.equals("use")).findFirst().orElseThrow().instructions) {
            if (insn instanceof MethodInsnNode m && m.name.equals("getTag")) { call = m; break; }
        }
        assertNotNull(call, "the getTag call must survive (redirected)");
        assertEquals("com/retromod/polyfill/minecraft/item/embedded/ItemStackNbtBridge", call.owner);
        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode(),
                "receiver-as-arg0 redirect MUST be INVOKESTATIC - INVOKEVIRTUAL pops one "
                + "value too many and corrupts the frame stack (the MobBucketItemMixin crash)");
        assertEquals("(Ljava/lang/Object;)Ljava/lang/Object;", call.desc);
    }

    @Test
    @DisplayName("a same-arity instance redirect stays an instance call (no false devirtualize)")
    void sameArityStaysVirtual() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.registerMethodRedirect(
                "test/devirt/Owner", "oldName", "()V",
                "test/devirt/Owner", "newName", "()V");

        byte[] in = classCalling("test/devirt/Owner", "oldName", "()V");
        byte[] out = t.transformClass(in, "test/devirt/Caller");

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        MethodInsnNode call = null;
        for (AbstractInsnNode insn : cn.methods.stream()
                .filter(m -> m.name.equals("use")).findFirst().orElseThrow().instructions) {
            if (insn instanceof MethodInsnNode m && m.name.equals("newName")) { call = m; break; }
        }
        assertNotNull(call);
        assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode(),
                "same-arity rename must keep the instance opcode");
    }
}
