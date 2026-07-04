/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.fabric.MaterialPolyfill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * MC 1.20 deleted the Material system; a pre-1.20 mod's {@code <clinit>} building a
 * {@code List<Material>} died {@code NoClassDefFoundError: net/minecraft/class_3614} and
 * took the whole class down (Collective's GlobalVariables, snapshot.8 round 7). After the
 * bridge, the SAME shape must not only transform but actually LOAD AND RUN against the
 * shipped {@link MaterialPolyfill}: this test executes the transformed fixture.
 */
class Pre1_20MaterialBridgeTest {

    private static final String MATERIAL = "net/minecraft/class_3614";
    private static final String BLOCK_STATE = "net/minecraft/class_2680";
    private static final String POLY = "com/retromod/polyfill/fabric/MaterialPolyfill";

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    /** Mirror Pre1_20MaterialBridge's registrations (register() itself is host-probing). */
    private static void registerBridge(RetromodTransformer t) {
        t.registerClassRedirect(MATERIAL, POLY);
        t.registerMethodRedirect(BLOCK_STATE, "method_26207", "()L" + POLY + ";",
                POLY, "fromState", "(Ljava/lang/Object;)L" + POLY + ";", true);
        t.registerMethodRedirect(BLOCK_STATE, "method_26207", "()L" + MATERIAL + ";",
                POLY, "fromState", "(Ljava/lang/Object;)L" + POLY + ";", true);
        t.registerMethodRedirect(POLY, "method_15798", "()Lnet/minecraft/class_3619;",
                POLY, "nullMaterialProperty", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        t.registerMethodRedirect(POLY, "method_15803", "()Lnet/minecraft/class_3620;",
                POLY, "nullMaterialProperty", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
    }

    /**
     * The GlobalVariables shape: a {@code <clinit>} building
     * {@code Arrays.asList(new Material[]{ 4 getstatics })}, a consumer calling
     * {@code BlockState.getMaterial()} into {@code List.contains}, and a method with
     * Material in its own signature.
     */
    private static byte[] globalVariablesShape() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/MatGlobals", null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "surfacematerials", "Ljava/util/List;",
                null, null).visitEnd();

        MethodVisitor cl = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        cl.visitCode();
        cl.visitInsn(ICONST_4);
        cl.visitTypeInsn(ANEWARRAY, MATERIAL);
        String[] ids = {"field_15920", "field_15922", "field_15958", "field_15928"};
        for (int i = 0; i < ids.length; i++) {
            cl.visitInsn(DUP);
            cl.visitLdcInsn(i);
            cl.visitFieldInsn(GETSTATIC, MATERIAL, ids[i], "L" + MATERIAL + ";");
            cl.visitInsn(AASTORE);
        }
        cl.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList",
                "([Ljava/lang/Object;)Ljava/util/List;", false);
        cl.visitFieldInsn(PUTSTATIC, "test/MatGlobals", "surfacematerials", "Ljava/util/List;");
        cl.visitInsn(RETURN);
        cl.visitMaxs(5, 0);
        cl.visitEnd();

        // boolean isSurfaceState(Object state): surfacematerials.contains(state.getMaterial())
        MethodVisitor ms = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "isSurfaceState",
                "(Ljava/lang/Object;)Z", null, null);
        ms.visitCode();
        ms.visitFieldInsn(GETSTATIC, "test/MatGlobals", "surfacematerials", "Ljava/util/List;");
        ms.visitVarInsn(ALOAD, 0);
        // receiver deliberately Object-typed: the devirt redirect turns this into
        // INVOKESTATIC fromState(Object) so the OUTPUT verifies (the input never loads)
        ms.visitMethodInsn(INVOKEVIRTUAL, BLOCK_STATE, "method_26207",
                "()L" + MATERIAL + ";", false);
        ms.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "contains",
                "(Ljava/lang/Object;)Z", true);
        ms.visitInsn(IRETURN);
        ms.visitMaxs(2, 1);
        ms.visitEnd();

        // Object pistonBehavior(Material m): a Material method whose return type can't be
        // declared on the polyfill must redirect to the null-returning static
        MethodVisitor pb = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "pistonBehavior",
                "(L" + MATERIAL + ";)Ljava/lang/Object;", null, null);
        pb.visitCode();
        pb.visitVarInsn(ALOAD, 0);
        pb.visitMethodInsn(INVOKEVIRTUAL, MATERIAL, "method_15798",
                "()Lnet/minecraft/class_3619;", false);
        pb.visitInsn(ARETURN);
        pb.visitMaxs(1, 1);
        pb.visitEnd();

        // boolean isSurface(Material m): Material in the mod's OWN signature must retype
        MethodVisitor im = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "isSurface",
                "(L" + MATERIAL + ";)Z", null, null);
        im.visitCode();
        im.visitFieldInsn(GETSTATIC, "test/MatGlobals", "surfacematerials", "Ljava/util/List;");
        im.visitVarInsn(ALOAD, 0);
        im.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "contains",
                "(Ljava/lang/Object;)Z", true);
        im.visitInsn(IRETURN);
        im.visitMaxs(2, 1);
        im.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static final class OneClassLoader extends ClassLoader {
        private final byte[] bytes;
        OneClassLoader(byte[] bytes) {
            super(Pre1_20MaterialBridgeTest.class.getClassLoader());
            this.bytes = bytes;
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if ("test.MatGlobals".equals(name)) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            throw new ClassNotFoundException(name);
        }
    }

    @Test
    @DisplayName("the GlobalVariables shape loads, <clinit> runs, and membership behaves")
    void clinitRunsAndMembershipBehaves() throws Exception {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        registerBridge(t);

        byte[] out = t.transformClass(globalVariablesShape(), "test/MatGlobals.class");
        assertNotNull(out);

        // The proof is EXECUTION: this very load died NoClassDefFoundError before the bridge
        Class<?> c = Class.forName("test.MatGlobals", true, new OneClassLoader(out));

        List<?> materials = (List<?>) c.getField("surfacematerials").get(null);
        assertEquals(4, materials.size(), "clinit must have built the 4-material list");
        assertTrue(materials.stream().allMatch(m -> m instanceof MaterialPolyfill),
                "constants must be the polyfill singletons");
        assertEquals(4, materials.stream().distinct().count(), "constants must be distinct");

        // getMaterial() devirt: UNKNOWN is a member of no list -> feature no-ops, no crash
        Method isSurfaceState = c.getMethod("isSurfaceState", Object.class);
        assertFalse((Boolean) isSurfaceState.invoke(null, new Object()),
                "fromState returns UNKNOWN which matches no constant");

        // the mod's own Material-typed signature retypes to the polyfill and identity works
        Method isSurface = c.getMethod("isSurface", MaterialPolyfill.class);
        assertTrue((Boolean) isSurface.invoke(null, MaterialPolyfill.field_15920));
        assertFalse((Boolean) isSurface.invoke(null, MaterialPolyfill.field_15913));

        // undeclarable-return-type methods redirect to the null-returning static (a
        // NoSuchMethodError here would kill the calling class at first touch)
        Method pistonBehavior = c.getMethod("pistonBehavior", MaterialPolyfill.class);
        assertNull(pistonBehavior.invoke(null, MaterialPolyfill.field_15914),
                "piston-behavior query must null-return, not NoSuchMethodError");
    }
}
