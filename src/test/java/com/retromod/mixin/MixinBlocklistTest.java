/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.mixin;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the mixin blocklist that turns fatal, unrepairable mixin handlers into
 * inert ones (issue #28: a MixinExtras {@code @WrapOperation}/{@code @Local}
 * that crashes with a {@code VerifyError} on 26.1).
 */
class MixinBlocklistTest {

    @AfterEach
    void reset() {
        MixinBlocklist.resetForTesting();
    }

    /** Build a minimal @Mixin class with a blocked handler, an injector helper, and a plain helper. */
    private static byte[] mixinClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        // Blocked MixinExtras @WrapOperation handler (the #28 shape).
        MethodVisitor h = cw.visitMethod(Opcodes.ACC_PRIVATE,
                "deeperdarker$decrementStackOnServer", "()V", null, null);
        h.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", false).visitEnd();
        h.visitCode();
        h.visitInsn(Opcodes.RETURN);
        h.visitMaxs(0, 1);
        h.visitEnd();

        // A standard @Inject handler (used by the whole-class case).
        MethodVisitor inj = cw.visitMethod(Opcodes.ACC_PRIVATE, "onSomething", "()V", null, null);
        inj.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false).visitEnd();
        inj.visitCode();
        inj.visitInsn(Opcodes.RETURN);
        inj.visitMaxs(0, 1);
        inj.visitEnd();

        // A plain helper with no injector annotation must always survive.
        MethodVisitor keep = cw.visitMethod(Opcodes.ACC_PRIVATE, "plainHelper", "()V", null, null);
        keep.visitCode();
        keep.visitInsn(Opcodes.RETURN);
        keep.visitMaxs(0, 1);
        keep.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Set<String> methodNames(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        Set<String> names = new HashSet<>();
        for (MethodNode m : cn.methods) names.add(m.name);
        return names;
    }

    @Test
    @DisplayName("Bundled blocklist includes Deeper&Darker's PaintingItemMixin handler (#28)")
    void loadsBundledDeeperDarkerEntry() {
        MixinBlocklist.resetForTesting(); // force a fresh load of the bundled resource
        Set<String> m = MixinBlocklist.methodsToStrip(
                "com/kyanite/deeperdarker/mixin/PaintingItemMixin");
        assertNotNull(m, "bundled blocklist should include PaintingItemMixin");
        assertTrue(m.contains("deeperdarker$decrementStackOnServer"),
                "the crashing @WrapOperation handler must be listed");
    }

    @Test
    @DisplayName("Surgical: only the named handler is stripped, everything else stays")
    void stripsNamedHandlerOnly() {
        MixinBlocklist.setForTesting(Map.of(
                "test/FooMixin", Set.of("deeperdarker$decrementStackOnServer")));
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(mixinClass("test/FooMixin")));
        assertFalse(names.contains("deeperdarker$decrementStackOnServer"), "blocked handler removed");
        assertTrue(names.contains("onSomething"), "other injector kept (not named)");
        assertTrue(names.contains("plainHelper"), "plain helper kept");
        assertTrue(names.contains("<init>"), "constructor kept");
    }

    @Test
    @DisplayName("Whole-class (empty methods): every injector dropped, helpers/ctor kept")
    void wholeClassStripsInjectorsKeepsHelpers() {
        MixinBlocklist.setForTesting(Map.of("test/BarMixin", Set.of())); // empty = all injectors
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(mixinClass("test/BarMixin")));
        assertFalse(names.contains("deeperdarker$decrementStackOnServer"), "MixinExtras handler stripped");
        assertFalse(names.contains("onSomething"), "@Inject handler stripped");
        assertTrue(names.contains("plainHelper"), "plain helper kept");
        assertTrue(names.contains("<init>"), "constructor kept");
    }

    @Test
    @DisplayName("End-to-end: the BUNDLED PaintingItemMixin entry strips the handler via transformMixinClass")
    void bundledEntryStripsRealMixin() {
        MixinBlocklist.resetForTesting(); // use the real bundled list, not a test injection
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(
                mixinClass("com/kyanite/deeperdarker/mixin/PaintingItemMixin")));
        assertFalse(names.contains("deeperdarker$decrementStackOnServer"),
                "bundled blocklist must strip the #28 handler from the real mixin name");
        assertTrue(names.contains("plainHelper"), "unrelated helper survives");
        assertTrue(names.contains("onSomething"), "non-listed handler survives");
    }

    /** Build a minimal @Mixin class with two named handler methods + ctor. */
    private static byte[] mixinClassWithMethods(String internalName, String... methodNames) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitEnd();
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        for (String name : methodNames) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, name, "()V", null, null);
            mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false).visitEnd();
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("Bundled blocklist includes Revamped Phantoms' PhantomMixin handler (#50)")
    void loadsBundledRevampedPhantomsEntry() {
        MixinBlocklist.resetForTesting();
        Set<String> m = MixinBlocklist.methodsToStrip(
                "dev/lukebemish/revampedphantoms/mixin/PhantomMixin");
        assertNotNull(m, "bundled blocklist should include PhantomMixin");
        assertTrue(m.contains("revamped_phantoms$getDefaultDimensions"),
                "the failing getDefaultDimensions handler must be listed");
    }

    @Test
    @DisplayName("#50 NeoForge path: strip getDefaultDimensions, keep the goals handler")
    void stripsPhantomDimensionsKeepsGoals() {
        MixinBlocklist.resetForTesting(); // use the real bundled list
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());
        Set<String> names = methodNames(t.stripBlocklistedHandlers(mixinClassWithMethods(
                "dev/lukebemish/revampedphantoms/mixin/PhantomMixin",
                "revamped_phantoms$getDefaultDimensions", "revamped_phantoms$registerGoals")));
        assertFalse(names.contains("revamped_phantoms$getDefaultDimensions"),
                "the failing dimension handler must be stripped (NeoForge path)");
        assertTrue(names.contains("revamped_phantoms$registerGoals"),
                "the unrelated goals handler must survive - self-contained soft-fail");
    }

    @Test
    @DisplayName("A mixin not on the blocklist is left untouched")
    void nonBlockedClassUntouched() {
        MixinBlocklist.setForTesting(Map.of("other/Thing", Set.of("x")));
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(mixinClass("test/FooMixin")));
        assertTrue(names.contains("deeperdarker$decrementStackOnServer"),
                "non-blocked mixin keeps all handlers");
        assertTrue(names.contains("onSomething"));
    }


    /** Build a @Mixin(value = {targetInternal.class}) class, for neutralization tests. */
    private static byte[] mixinClassWithTarget(String internalName, String targetInternal) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        org.objectweb.asm.AnnotationVisitor av =
                cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
        org.objectweb.asm.AnnotationVisitor arr = av.visitArray("value");
        arr.visit(null, org.objectweb.asm.Type.getObjectType(targetInternal));
        arr.visitEnd();
        av.visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();
        // An @Inject handler must NOT remain reachable once neutralized.
        MethodVisitor inj = cw.visitMethod(Opcodes.ACC_PRIVATE, "onRenderLevel", "()V", null, null);
        inj.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false).visitEnd();
        inj.visitCode();
        inj.visitInsn(Opcodes.RETURN);
        inj.visitMaxs(0, 1);
        inj.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Read the @Mixin annotation's string {@code targets} list from a class. */
    private static java.util.List<String> mixinStringTargets(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        java.util.List<org.objectweb.asm.tree.AnnotationNode> anns =
                cn.invisibleAnnotations != null ? cn.invisibleAnnotations
                        : (cn.visibleAnnotations != null ? cn.visibleAnnotations : java.util.List.of());
        for (var a : anns) {
            if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(a.desc) || a.values == null) continue;
            for (int i = 0; i < a.values.size(); i += 2) {
                if ("targets".equals(a.values.get(i)) && a.values.get(i + 1) instanceof java.util.List<?> l) {
                    java.util.List<String> out = new java.util.ArrayList<>();
                    for (Object o : l) out.add(String.valueOf(o));
                    return out;
                }
            }
        }
        return java.util.List.of();
    }

    /** Whether the @Mixin annotation still declares any Class[] {@code value} targets. */
    private static boolean hasMixinValueTargets(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        java.util.List<org.objectweb.asm.tree.AnnotationNode> anns =
                cn.invisibleAnnotations != null ? cn.invisibleAnnotations
                        : (cn.visibleAnnotations != null ? cn.visibleAnnotations : java.util.List.of());
        for (var a : anns) {
            if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(a.desc) || a.values == null) continue;
            for (int i = 0; i < a.values.size(); i += 2) {
                if ("value".equals(a.values.get(i)) && a.values.get(i + 1) instanceof java.util.List<?> l
                        && !l.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("Whole-class strip repoints @Mixin target to a non-existent placeholder (Fabric path)")
    void wholeClassStripNeutralizesMixinFabric() {
        MixinBlocklist.setForTesting(
                Map.of("test/InterfaceMixin", Set.of()),
                Set.of("test/InterfaceMixin")); // full strip
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        byte[] out = t.transformMixinClass(
                mixinClassWithTarget("test/InterfaceMixin", "net/minecraft/SomeTarget"));
        assertTrue(mixinStringTargets(out).contains("retromod/stripped/InterfaceMixin"),
                "neutralized mixin must point at the placeholder so the framework skips it");
        assertFalse(hasMixinValueTargets(out),
                "the original Class[] value target must be cleared");
    }

    @Test
    @DisplayName("Whole-class strip also works on the NeoForge/Forge path (stripBlocklistedHandlers)")
    void wholeClassStripNeutralizesMixinNeoForge() {
        MixinBlocklist.setForTesting(
                Map.of("test/InterfaceMixin", Set.of()),
                Set.of("test/InterfaceMixin"));
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        byte[] out = t.stripBlocklistedHandlers(
                mixinClassWithTarget("test/InterfaceMixin", "net/minecraft/SomeTarget"));
        assertTrue(mixinStringTargets(out).contains("retromod/stripped/InterfaceMixin"),
                "NeoForge path must neutralize too - #68 True Darkness is a NeoForge mod");
    }

    @Test
    @DisplayName("Bundled blocklist marks True Darkness render mixins as full-class strips (#68)")
    void bundledDarknessEntriesAreFullStrip() {
        MixinBlocklist.resetForTesting();
        assertTrue(MixinBlocklist.isFullStrip("grondag/darkness/mixin/MixinLightTexture"),
                "MixinLightTexture (interface-adder) must be a whole-class strip");
        assertTrue(MixinBlocklist.isFullStrip("grondag/darkness/mixin/MixinGameRenderer"),
                "MixinGameRenderer (the ClassCastException consumer) must be a whole-class strip");
    }

    @Test
    @DisplayName("Bundled blocklist marks Revamped Phantoms' SweepAttackMixin as full-class strip (#69)")
    void bundledSweepAttackIsFullStrip() {
        MixinBlocklist.resetForTesting();
        assertTrue(MixinBlocklist.isFullStrip("dev/lukebemish/revampedphantoms/mixin/SweepAttackMixin"),
                "SweepAttackMixin's critical injection failure needs whole-class strip");
        // PhantomMixin stays a surgical handler-strip (preserves the shared-goals feature).
        assertFalse(MixinBlocklist.isFullStrip("dev/lukebemish/revampedphantoms/mixin/PhantomMixin"),
                "PhantomMixin must remain handler-strip so goals/size features stay live");
    }
}
