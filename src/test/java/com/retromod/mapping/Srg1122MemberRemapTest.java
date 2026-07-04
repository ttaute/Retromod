/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.mapping;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.12.2 Forge mods reference members by the OLD SRG namespace ({@code func_NNNNN_x} /
 * {@code field_NNNNN_x}), not the modern {@code m_NNNNNN_}/{@code f_NNNNN_} one, so the SRG
 * remap branch never fired for them (#103/#108/#117). These tests lock the new dictionary
 * (harvested from the MCPBot 1.12.2 CSVs, existence-filtered against Mojang 1.20.1 names)
 * and the remapper's func_/field_ pattern support.
 */
class Srg1122MemberRemapTest {

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    @Test
    @DisplayName("the bundled dictionary carries both SRG eras")
    void dictionaryLoadsBothEras() {
        SrgToMojangMapper m = SrgToMojangMapper.getInstance();
        Map<String, String> fields = m.getFieldMap();
        Map<String, String> methods = m.getMethodMap();
        // 1.12.2-era entries (from srg-1.12.2-to-mojang.tsv; values verified against the
        // official 1.20.1 mappings at harvest time)
        assertEquals("absorptionAmount", fields.get("field_110151_bq"),
                "a known 1.12.2 SRG field must resolve");
        assertTrue(methods.keySet().stream().anyMatch(k -> k.startsWith("func_")),
                "1.12.2 method entries must be present");
        // the modern era must still be there (regression guard on the loader rework)
        assertTrue(fields.keySet().stream().anyMatch(k -> k.startsWith("f_")),
                "modern f_ entries must still load");
        assertTrue(methods.keySet().stream().anyMatch(k -> k.startsWith("m_")),
                "modern m_ entries must still load");
    }

    /** A 1.12.2-style caller using old-SRG member names. */
    private static byte[] fixture() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/OldSrg", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use",
                "(Lnet/minecraft/world/entity/LivingEntity;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/world/entity/LivingEntity",
                "field_99999_zz", "F");
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity",
                "func_99999_zz", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("func_/field_NNN_x references remap through the SRG branch")
    void oldSrgPatternsRemap() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        t.registerSrgNameMappings(
                Map.of("func_99999_zz", "tickLegacy"),
                Map.of("field_99999_zz", "legacyHealth"));

        ClassNode cn = new ClassNode();
        new ClassReader(t.transformClass(fixture(), "test/OldSrg.class")).accept(cn, 0);
        boolean method = false, field = false;
        for (AbstractInsnNode in : cn.methods.stream()
                .filter(m -> m.name.equals("use")).findFirst().orElseThrow().instructions.toArray()) {
            if (in instanceof MethodInsnNode mi && mi.name.equals("tickLegacy")) method = true;
            if (in instanceof FieldInsnNode fi && fi.name.equals("legacyHealth")) field = true;
            if (in instanceof MethodInsnNode mi) {
                assertNotEquals("func_99999_zz", mi.name, "old SRG method name must not survive");
            }
            if (in instanceof FieldInsnNode fi) {
                assertNotEquals("field_99999_zz", fi.name, "old SRG field name must not survive");
            }
        }
        assertTrue(method, "func_ reference must remap");
        assertTrue(field, "field_NNN_x reference must remap");
    }

    @Test
    @DisplayName("Fabric intermediary field_NNNN names are untouched by the SRG branch")
    void intermediaryFieldsNotHijacked() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        // only SRG mappings registered; a Fabric intermediary-style name must pass through
        t.registerSrgNameMappings(Map.of(), Map.of("field_99999_zz", "legacyHealth"));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/Intermediary", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "use",
                "(Lnet/minecraft/class_1309;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/class_1309", "field_6017", "F");
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();

        ClassNode cn = new ClassNode();
        new ClassReader(t.transformClass(cw.toByteArray(), "test/Intermediary.class")).accept(cn, 0);
        boolean untouched = false;
        for (AbstractInsnNode in : cn.methods.stream()
                .filter(m -> m.name.equals("use")).findFirst().orElseThrow().instructions.toArray()) {
            if (in instanceof FieldInsnNode fi && fi.name.equals("field_6017")) untouched = true;
        }
        assertTrue(untouched, "an intermediary field with no SRG entry must pass through unchanged");
    }
}
