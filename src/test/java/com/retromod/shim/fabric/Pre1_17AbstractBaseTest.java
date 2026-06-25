/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the synthetic model bases {@link Pre1_17ModelBridge} injects for the abstract models 26.1
 * removed (class_4592, class_4593, class_4595): valid bytecode, correct super, abstract getters,
 * concrete render, and the redirect that rebases a mod extending class_4592 onto the synthetic.
 */
class Pre1_17AbstractBaseTest {

    private static final String RENDER_DESC =
            "(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;IIFFFF)V";

    /** Reflective view of a generated class. */
    private static final class Info extends ClassVisitor {
        int access;
        String superName;
        final Map<String, Integer> methods = new HashMap<>();
        final Set<String> fields = new HashSet<>();

        Info() { super(Opcodes.ASM9); }

        @Override public void visit(int v, int a, String n, String sig, String sup, String[] itf) {
            access = a; superName = sup;
        }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
            methods.put(n + d, a); return null;
        }
        @Override public FieldVisitor visitField(int a, String n, String d, String s, Object val) {
            fields.add(n); return null;
        }
    }

    private static Info inspect(byte[] clazz) {
        Info i = new Info();
        new ClassReader(clazz).accept(i, 0); // throws on malformed bytecode
        return i;
    }

    @Test
    void animalModelBaseHasAbstractPartGettersAndConcreteRender() {
        byte[] b = Pre1_17ModelBridge.generateAgeableBase(
                "com/retromod/generated/LegacyAnimalModel",
                new String[]{"()V", "(ZFF)V", "(ZFFFFF)V", "(Ljava/util/function/Function;ZFFFFF)V"},
                new String[]{"method_22946", "method_22948"}, Opcodes.ACC_PROTECTED);
        Info i = inspect(b);

        assertEquals("net/minecraft/class_583", i.superName, "AnimalModel base extends class_583");
        assertNotEquals(0, i.access & Opcodes.ACC_ABSTRACT, "base must be abstract");

        // all four 1.16 constructors
        assertTrue(i.methods.containsKey("<init>()V"));
        assertTrue(i.methods.containsKey("<init>(ZFF)V"));
        assertTrue(i.methods.containsKey("<init>(ZFFFFF)V"));
        assertTrue(i.methods.containsKey("<init>(Ljava/util/function/Function;ZFFFFF)V"));

        // part getters stay abstract for the mod to implement
        assertNotEquals(0, i.methods.get("method_22946()Ljava/lang/Iterable;") & Opcodes.ACC_ABSTRACT);
        assertNotEquals(0, i.methods.get("method_22948()Ljava/lang/Iterable;") & Opcodes.ACC_ABSTRACT);

        Integer render = i.methods.get("method_2828" + RENDER_DESC);
        assertNotNull(render, "must declare the model render method_2828");
        assertEquals(0, render & Opcodes.ACC_ABSTRACT, "render must be concrete");
    }

    @Test
    void compositeModelBaseHasSinglePartsGetter() {
        byte[] b = Pre1_17ModelBridge.generateAgeableBase(
                "com/retromod/generated/LegacyCompositeModel",
                new String[]{"()V", "(Ljava/util/function/Function;)V"},
                new String[]{"method_22960"}, Opcodes.ACC_PUBLIC);
        Info i = inspect(b);

        assertEquals("net/minecraft/class_583", i.superName);
        assertNotEquals(0, i.access & Opcodes.ACC_ABSTRACT);
        assertTrue(i.methods.containsKey("<init>()V"));
        assertTrue(i.methods.containsKey("<init>(Ljava/util/function/Function;)V"));
        assertNotEquals(0, i.methods.get("method_22960()Ljava/lang/Iterable;") & Opcodes.ACC_ABSTRACT);
        assertNotNull(i.methods.get("method_2828" + RENDER_DESC));
    }

    @Test
    void tintedModelBaseExtendsAnimalAndHoldsTintFields() {
        byte[] b = Pre1_17ModelBridge.generateTintedBase(
                "com/retromod/generated/LegacyTintedAnimalModel",
                "com/retromod/generated/LegacyAnimalModel");
        Info i = inspect(b);

        assertEquals("com/retromod/generated/LegacyAnimalModel", i.superName,
                "tint subclass extends the synthetic AnimalModel");
        assertNotEquals(0, i.access & Opcodes.ACC_ABSTRACT);
        assertTrue(i.fields.containsAll(Set.of("field_20923", "field_20924", "field_20925")),
                "holds the three tint fields");
        assertNotNull(i.methods.get("method_22955(FFF)V"), "has the tint setter");
        assertNotNull(i.methods.get("method_2828" + RENDER_DESC), "has the tint render override");
    }

    @Test
    void modExtendingAnimalModelIsRedirectedToSynthetic() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        Pre1_17ModelBridge.register(t);

        byte[] out = t.transformClass(modExtendingAnimalModel(), "test/MyDeerModel");
        Info i = inspect(out);

        assertEquals("com/retromod/generated/LegacyAnimalModel", i.superName,
                "a mod extending the removed class_4592 must be rebased onto the synthetic");

        // the super(...) call must now target the synthetic's ctor
        boolean[] superRedirected = {false};
        new ClassReader(out).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                if (!"<init>".equals(n)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String o, String nm, String de, boolean itf) {
                        if (op == Opcodes.INVOKESPECIAL && "<init>".equals(nm)
                                && "com/retromod/generated/LegacyAnimalModel".equals(o)) {
                            superRedirected[0] = true;
                        }
                    }
                };
            }
        }, 0);
        assertTrue(superRedirected[0], "super(...) must be redirected to the synthetic ctor");

        t.clearRedirectsForTesting();
    }

    /** A pre-1.17 Fabric model: {@code MyDeerModel extends class_4592} with {@code super(false, 0, 0)}. */
    private static byte[] modExtendingAnimalModel() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/MyDeerModel", null,
                "net/minecraft/class_4592", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.FCONST_0);
        mv.visitInsn(Opcodes.FCONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/class_4592", "<init>", "(ZFF)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
