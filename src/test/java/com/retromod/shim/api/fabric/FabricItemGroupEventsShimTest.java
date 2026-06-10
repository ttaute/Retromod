/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import com.retromod.shim.fabric.Fabric_1_21_11_to_26_1;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks for the synthetic classes + redirects of
 * {@link FabricItemGroupEventsShim} (the 83-mod {@code ItemGroupEvents} bridge).
 * Verifies the bytecode shape a runtime link depends on; the reflective forwarder
 * and the in-game tab modification still need a 26.1 launch.
 */
class FabricItemGroupEventsShimTest {

    private static final String OUTPUT = "net/fabricmc/fabric/api/creativetab/v1/FabricCreativeModeTabOutput";

    @BeforeAll
    static void pinHostTo26_1() {
        // The shim self-gates to 26.1+ hosts (pre-26.1, ItemGroupEvents is still alive
        // and must not be hijacked) — pin the detected host so registerRedirects runs.
        RetromodVersion.TARGET_MC_VERSION = "26.1";
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    @Test
    @DisplayName("holder is a class with modifyEntriesEvent + MODIFY_ENTRIES_ALL field + <clinit>")
    void holderShape() {
        ClassNode cn = read(FabricItemGroupEventsShim.generateHolder());
        assertEquals(0, cn.access & Opcodes.ACC_INTERFACE, "holder must be a class, not an interface");

        MethodNode mee = cn.methods.stream().filter(m -> m.name.equals("modifyEntriesEvent"))
                .findFirst().orElse(null);
        assertNotNull(mee, "holder must keep modifyEntriesEvent");
        assertEquals("(Lnet/minecraft/resources/ResourceKey;)Lnet/fabricmc/fabric/api/event/Event;", mee.desc);
        assertTrue((mee.access & Opcodes.ACC_STATIC) != 0, "modifyEntriesEvent is static");

        FieldNode all = cn.fields.stream().filter(f -> f.name.equals("MODIFY_ENTRIES_ALL"))
                .findFirst().orElse(null);
        assertNotNull(all, "holder must keep the MODIFY_ENTRIES_ALL field");
        assertEquals("Lnet/fabricmc/fabric/api/event/Event;", all.desc);

        assertTrue(cn.methods.stream().anyMatch(m -> m.name.equals("<clinit>")),
                "MODIFY_ENTRIES_ALL needs a static initializer");
    }

    @Test
    @DisplayName("ModifyEntries SAM keeps the old name with the redirected output param")
    void modifyEntriesSam() {
        ClassNode cn = read(FabricItemGroupEventsShim.generateModifyEntries());
        assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0);
        MethodNode sam = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).findFirst().orElse(null);
        assertNotNull(sam);
        assertEquals("modifyEntries", sam.name, "SAM name must stay modifyEntries so the lambda links");
        assertEquals("(L" + OUTPUT + ";)V", sam.desc,
                "param is the redirected FabricCreativeModeTabOutput");
    }

    @Test
    @DisplayName("ModifyEntriesAll SAM is the 2-arg (CreativeModeTab, output) form")
    void modifyEntriesAllSam() {
        ClassNode cn = read(FabricItemGroupEventsShim.generateModifyEntriesAll());
        MethodNode sam = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).findFirst().orElse(null);
        assertNotNull(sam);
        assertEquals("modifyEntries", sam.name);
        assertEquals("(Lnet/minecraft/world/item/CreativeModeTab;L" + OUTPUT + ";)V", sam.desc);
    }

    @Test
    @DisplayName("shim redirects the outer class + both inner SAMs onto the synthetics")
    void classRedirects() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricItemGroupEventsShim().registerRedirects(t);
        var cr = t.getClassRedirects();
        assertEquals("com/retromod/generated/legacyitemgroup/ItemGroupEvents",
                cr.get("net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents"));
        assertEquals("com/retromod/generated/legacyitemgroup/ModifyEntries",
                cr.get("net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents$ModifyEntries"));
        assertEquals("com/retromod/generated/legacyitemgroup/ModifyEntriesAll",
                cr.get("net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents$ModifyEntriesAll"));
    }

    @Test
    @DisplayName("shim renames addAfter/addBefore → insertAfter/insertBefore on the output (12 each)")
    void methodRenames() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricItemGroupEventsShim().registerRedirects(t);
        var mr = t.getMethodRedirects();

        // representative overload: (ItemLike, ItemLike[])
        String d = "(Lnet/minecraft/world/level/ItemLike;[Lnet/minecraft/world/level/ItemLike;)V";
        var after = mr.get(new RetromodTransformer.MethodKey(OUTPUT, "addAfter", d));
        assertNotNull(after, "addAfter must be redirected");
        assertEquals("insertAfter", after.name());
        var before = mr.get(new RetromodTransformer.MethodKey(OUTPUT, "addBefore", d));
        assertNotNull(before, "addBefore must be redirected");
        assertEquals("insertBefore", before.name());

        long addAfter = mr.keySet().stream()
                .filter(k -> k.owner().equals(OUTPUT) && k.name().equals("addAfter")).count();
        long addBefore = mr.keySet().stream()
                .filter(k -> k.owner().equals(OUTPUT) && k.name().equals("addBefore")).count();
        assertEquals(12, addAfter, "all 12 addAfter overloads renamed");
        assertEquals(12, addBefore, "all 12 addBefore overloads renamed");
    }

    @Test
    @DisplayName("end-to-end: FabricItemGroupEntries.addAfter → FabricCreativeModeTabOutput.insertAfter")
    void classRedirectThenMethodRenameCompose() {
        // The riskiest assumption: the class redirect (owner FabricItemGroupEntries →
        // FabricCreativeModeTabOutput, from the version shim) and the method rename
        // (addAfter → insertAfter, keyed on the NEW owner, from this shim) must
        // compose in one transform — i.e. the method redirect sees the post-class-
        // redirect owner. Verify on a real class through the transformer.
        RetromodTransformer t = RetromodTransformer.getInstance();
        new Fabric_1_21_11_to_26_1().registerRedirects(t);   // FabricItemGroupEntries → FabricCreativeModeTabOutput
        new FabricItemGroupEventsShim().registerRedirects(t); // addAfter → insertAfter on the new owner

        String entries = "net/fabricmc/fabric/api/itemgroup/v1/FabricItemGroupEntries";
        String addAfterDesc = "(Lnet/minecraft/world/level/ItemLike;[Lnet/minecraft/world/level/ItemLike;)V";

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/IGMod", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "use", "(L" + entries + ";)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);     // the entries receiver
        mv.visitInsn(Opcodes.ACONST_NULL);     // ItemLike
        mv.visitInsn(Opcodes.ACONST_NULL);     // ItemLike[]
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entries, "addAfter", addAfterDesc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] out = t.transformClass(cw.toByteArray(), "test/IGMod");

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        MethodInsnNode call = cn.methods.stream()
                .filter(m -> m.name.equals("use"))
                .flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode) i)
                .findFirst().orElse(null);
        assertNotNull(call, "the invoke must survive the transform");
        assertEquals("net/fabricmc/fabric/api/creativetab/v1/FabricCreativeModeTabOutput", call.owner,
                "owner must be rewritten by the class redirect");
        assertEquals("insertAfter", call.name,
                "addAfter must be renamed to insertAfter on the new owner (composition)");
    }
}
