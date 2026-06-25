/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance test for the pre-26.1 Fabric model bridge: drives intermediary-named Fabric model
 * mods through {@code Pre1_17ModelBridge.register()} + {@code transformClass()} and checks
 * synthetics are registered, abstract bases redirected, {@code extends}/{@code super(...)}
 * rebased onto the synthetic, abstract getters left untouched, and legacy
 * {@code new ModelPart(...)} rewritten onto the synthetic factory/statics.
 *
 * <p>Host is pinned pre-26.1 so the intermediary-&gt;Mojang remap stays off, matching the
 * namespace a distributed Fabric mod ships in.
 */
class Pre1_17ModelBridgeAcceptanceTest {

    private static final String MODEL_PART  = "net/minecraft/class_630";
    private static final String MODEL        = "net/minecraft/class_3879";
    private static final String ANIMAL       = "net/minecraft/class_4592";
    private static final String TINTED       = "net/minecraft/class_4593";
    private static final String COMPOSITE    = "net/minecraft/class_4595";
    private static final String SELF         = "com/retromod/generated/LegacyModelPart";
    private static final String GEN_ANIMAL   = "com/retromod/generated/LegacyAnimalModel";
    private static final String GEN_TINTED   = "com/retromod/generated/LegacyTintedAnimalModel";
    private static final String GEN_COMPOSITE = "com/retromod/generated/LegacyCompositeModel";

    private RetromodTransformer t;

    @BeforeEach
    void registerPre26() {
        t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "1.21.11"; // pre-26.1: intermediary remap off
        Pre1_17ModelBridge.register(t);
    }

    @AfterEach
    void restore() {
        t.clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "26.1"; // restore default
    }

    @Test
    @DisplayName("register() wires the full synthetic + redirect surface area")
    void bridgeRegistersFullSurfaceArea() {
        Set<String> synths = t.getSyntheticClasses().keySet();
        assertTrue(synths.containsAll(Set.of(SELF, GEN_ANIMAL, GEN_TINTED, GEN_COMPOSITE)),
                "all four model synthetics must be injected; got " + synths);
        assertTrue(synths.stream().anyMatch(s -> s.startsWith("com/retromod/generated/LegacyModelBase_")),
                "at least one Layer-3 concrete-base synthetic must be injected");

        assertEquals(GEN_ANIMAL, t.getClassRedirects().get(ANIMAL));
        assertEquals(GEN_COMPOSITE, t.getClassRedirects().get(COMPOSITE));
        assertEquals(GEN_TINTED, t.getClassRedirects().get(TINTED));

        assertFalse(t.getMethodRedirects().isEmpty(),
                "the de-virtualized class_630 build/render statics must be registered");
    }

    @Test
    @DisplayName("AnimalModel mod rebases onto the synthetic and stays valid")
    void animalModelModComposesEndToEnd() {
        byte[] out = t.transformClass(animalModelMod(), "test/MyDeerModel");
        Info i = inspect(out);

        assertEquals(GEN_ANIMAL, i.superName, "extends must be rebased onto the synthetic AnimalModel");
        assertTrue(i.methods.contains("method_22946()Ljava/lang/Iterable;")
                        && i.methods.contains("method_22948()Ljava/lang/Iterable;"),
                "the mod's intermediary abstract-getter overrides must survive untouched (remap is OFF)");

        Refs r = collectRefs(out);
        assertTrue(hasInvoke(r, Opcodes.INVOKESPECIAL, GEN_ANIMAL, "<init>"),
                "super(...) must target the synthetic ctor");
        assertFalse(hasInvoke(r, Opcodes.INVOKESPECIAL, ANIMAL, "<init>"),
                "no super(...) call to the removed class_4592 may remain");

        assertStructurallyValid(out, "MyDeerModel");
    }

    @Test
    @DisplayName("Legacy ModelPart construction + transform chain is rewritten onto the synthetic")
    void legacyPartConstructionRewritten() {
        byte[] out = t.transformClass(legacyPartUserMod(), "test/PartUser");
        Refs r = collectRefs(out);

        assertFalse(hasNew(r, MODEL_PART), "no NEW class_630 may remain - the ctor redirect removes it");
        assertFalse(hasInvoke(r, Opcodes.INVOKESPECIAL, MODEL_PART, "<init>"),
                "no INVOKESPECIAL class_630.<init> may remain");
        assertTrue(hasInvoke(r, Opcodes.INVOKESTATIC, SELF, "create"),
                "construction must become LegacyModelPart.create(...)");
        assertTrue(hasInvoke(r, Opcodes.INVOKESTATIC, SELF, "setPos"),
                "the de-virtualized setPos (method_2851) must become a LegacyModelPart static");
        assertFalse(hasInvoke(r, Opcodes.INVOKEVIRTUAL, MODEL_PART, "method_2851"),
                "no INVOKEVIRTUAL class_630.method_2851 may remain");

        assertStructurallyValid(out, "PartUser");
    }

    @Test
    @DisplayName("Composite + Tinted mods rebase onto their synthetics and stay valid")
    void compositeAndTintedModsRebaseAndVerify() {
        byte[] composite = t.transformClass(
                subclassMod("test/MyComposite", COMPOSITE, "method_22960"), "test/MyComposite");
        Info ci = inspect(composite);
        assertEquals(GEN_COMPOSITE, ci.superName);
        assertTrue(ci.methods.contains("method_22960()Ljava/lang/Iterable;"));
        assertStructurallyValid(composite, "MyComposite");

        byte[] tinted = t.transformClass(subclassMod("test/MyTinted", TINTED), "test/MyTinted");
        Info ti = inspect(tinted);
        assertEquals(GEN_TINTED, ti.superName);
        assertStructurallyValid(tinted, "MyTinted");
    }

    // Fixtures use raw intermediary names and are never JVM-loaded, so no MC classpath is needed.

    /** {@code class MyDeerModel extends class_4592} with super(false,0,0) plus the abstract getters. */
    private static byte[] animalModelMod() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/MyDeerModel", null, ANIMAL, null);
        MethodVisitor c = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitInsn(Opcodes.ICONST_0); // false
        c.visitInsn(Opcodes.FCONST_0); // 0f
        c.visitInsn(Opcodes.FCONST_0); // 0f
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, ANIMAL, "<init>", "(ZFF)V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        for (String g : new String[]{"method_22946", "method_22948"}) {
            MethodVisitor m = cw.visitMethod(Opcodes.ACC_PROTECTED, g, "()Ljava/lang/Iterable;", null, null);
            m.visitCode();
            m.visitInsn(Opcodes.ACONST_NULL);
            m.visitInsn(Opcodes.ARETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A class doing {@code new class_630(model)} then {@code setPos(0,0,0)} (method_2851). */
    private static byte[] legacyPartUserMod() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/PartUser", null, "java/lang/Object", null);
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "build", "(L" + MODEL + ";)V", null, null);
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, MODEL_PART);
        m.visitInsn(Opcodes.DUP);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, MODEL_PART, "<init>", "(L" + MODEL + ";)V", false);
        m.visitInsn(Opcodes.FCONST_0);
        m.visitInsn(Opcodes.FCONST_0);
        m.visitInsn(Opcodes.FCONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MODEL_PART, "method_2851", "(FFF)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** {@code class <name> extends <superClass>} with super() + optional abstract getters. */
    private static byte[] subclassMod(String name, String superClass, String... abstractGetters) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, superClass, null);
        MethodVisitor c = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, superClass, "<init>", "()V", false);
        c.visitInsn(Opcodes.RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();
        for (String g : abstractGetters) {
            MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, g, "()Ljava/lang/Iterable;", null, null);
            m.visitCode();
            m.visitInsn(Opcodes.ACONST_NULL);
            m.visitInsn(Opcodes.ARETURN);
            m.visitMaxs(0, 0);
            m.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    // inspection helpers

    private static final class Info extends ClassVisitor {
        String superName;
        final Set<String> methods = new HashSet<>();
        final Set<String> fields = new HashSet<>();
        Info() { super(Opcodes.ASM9); }
        @Override public void visit(int v, int a, String n, String s, String sup, String[] i) { superName = sup; }
        @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
            methods.add(n + d); return null;
        }
        @Override public FieldVisitor visitField(int a, String n, String d, String s, Object val) {
            fields.add(n); return null;
        }
    }

    private static Info inspect(byte[] clazz) {
        Info i = new Info();
        new ClassReader(clazz).accept(i, 0);
        return i;
    }

    private static final class Refs {
        final List<String> methodInsns = new ArrayList<>(); // "op owner#name desc"
        final List<String> typeInsns = new ArrayList<>();    // "op type"
    }

    private static Refs collectRefs(byte[] clazz) {
        Refs r = new Refs();
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String o, String nm, String de, boolean itf) {
                        r.methodInsns.add(op + " " + o + "#" + nm + " " + de);
                    }
                    @Override public void visitTypeInsn(int op, String type) {
                        r.typeInsns.add(op + " " + type);
                    }
                };
            }
        }, 0);
        return r;
    }

    private static boolean hasInvoke(Refs r, int op, String owner, String name) {
        return r.methodInsns.stream().anyMatch(s -> s.startsWith(op + " " + owner + "#" + name + " "));
    }

    private static boolean hasNew(Refs r, String type) {
        return r.typeInsns.stream().anyMatch(s -> s.equals(Opcodes.NEW + " " + type));
    }

    /**
     * Structure-only check (checkDataFlow=false): catches malformed descriptors / constant pool.
     * Stack/dataflow needs the MC superclasses on the classpath, absent here; the transform's
     * COMPUTE_FRAMES pass and in-game launch cover that.
     */
    private static void assertStructurallyValid(byte[] clazz, String label) {
        try {
            new ClassReader(clazz).accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
        } catch (Throwable t) {
            fail("transformed " + label + " is not structurally valid: " + t);
        }
    }
}
