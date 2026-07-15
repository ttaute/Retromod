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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * 26.1 renamed the Registry <em>value</em> getter {@code get(Identifier)} to {@code getValue(Identifier)}
 * (the {@code ResourceLocation -> Identifier} companion). A 1.21.1 mod that calls the old value getter
 * (e.g. YUNG's Better Strongholds via {@code BuiltInRegistries.X.get(id)}) dies at construct time with
 * {@code NoSuchMethodError: DefaultedRegistry.get(Identifier)} because the surviving {@code get(Identifier)}
 * now returns {@code Optional<Holder.Reference>}. This drives the real
 * {@link Common_1_21_11_to_26_1_ClassMoves#registerRegistryValueGetterRename} alongside the
 * {@code ResourceLocation -> Identifier} class redirect and asserts the call is remapped to
 * {@code getValue(Identifier)} - proving the class-remap-then-method-redirect ordering.
 */
public class RegistryValueGetterRenameTest {

    private RetromodTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = RetromodTransformer.getInstance();
        transformer.clearRedirectsForTesting();
    }

    @AfterEach
    void tearDown() {
        transformer.clearRedirectsForTesting();
    }

    /** static Object lookup(DefaultedRegistry r, ResourceLocation id) { return r.get(id); } */
    private static byte[] callerClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/RegCaller", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "lookup",
                "(Lnet/minecraft/core/DefaultedRegistry;Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
                null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/core/DefaultedRegistry", "get",
                "(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static MethodInsnNode theCall(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("lookup")) continue;
            for (var insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode mi && mi.owner.equals("net/minecraft/core/DefaultedRegistry")) {
                    return mi;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("DefaultedRegistry.get(ResourceLocation) -> getValue(Identifier) after the class remap")
    void getBecomesGetValue() {
        // The vanilla ResourceLocation -> Identifier class rename (26.1) runs before the method lookup.
        transformer.registerClassRedirect(
                "net/minecraft/resources/ResourceLocation", "net/minecraft/resources/Identifier");
        Common_1_21_11_to_26_1_ClassMoves.registerRegistryValueGetterRename(transformer);

        MethodInsnNode call = theCall(transformer.transformClass(callerClass(), "test/RegCaller"));
        assertNotNull(call, "the DefaultedRegistry call must survive");
        assertEquals("getValue", call.name, "the value getter get(Identifier) must be renamed to getValue");
        assertEquals("(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;", call.desc,
                "the ResourceLocation param must be remapped to Identifier");
    }

    @Test
    @DisplayName("The Optional-returning get(int) overload is NOT touched (descriptor-scoped)")
    void intOverloadUntouched() {
        transformer.registerClassRedirect(
                "net/minecraft/resources/ResourceLocation", "net/minecraft/resources/Identifier");
        Common_1_21_11_to_26_1_ClassMoves.registerRegistryValueGetterRename(transformer);

        // build a caller of get(int) -> Optional; it must stay 'get' (only the value-getter overload renames).
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/RegCaller", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "lookup",
                "(Lnet/minecraft/core/DefaultedRegistry;)Ljava/util/Optional;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/core/DefaultedRegistry", "get",
                "(I)Ljava/util/Optional;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
        cw.visitEnd();

        MethodInsnNode call = theCall(transformer.transformClass(cw.toByteArray(), "test/RegCaller"));
        assertNotNull(call);
        assertEquals("get", call.name, "get(int) is a different method and must be left alone");
    }
}
