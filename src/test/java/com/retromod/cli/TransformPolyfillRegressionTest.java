/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeInsnNode;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import com.retromod.core.VersionShim;
import com.retromod.polyfill.PolyfillRegistry;
import com.retromod.shim.ShimRegistry;

/**
 * Regression for the {@code transform} CLI command's missing polyfill pass.
 *
 * <p>{@code transformCommand} once registered only the version-shim chain (or the all-shims
 * fallback) and NOT the removed-API polyfills, so a transform-only pass left references to
 * removed-vanilla classes dangling. The canonical case is {@code net/minecraft/util/LazyLoadedValue}
 * (removed in 26.1, referenced at runtime by Jade): the runtime/analyze/batch paths redirect it to
 * the embedded polyfill, but a plain {@code transform} did not. {@code transformCommand} now calls
 * {@link PolyfillRegistry#loadAndRegister} in BOTH branches, mirroring the other paths.
 *
 * <p>The polyfill is host-gated to 26.1+ (below 26.1 the class still exists), so these tests pin the
 * host to 26.1. They drive the exact registration sequence the CLI performs (shim chain +
 * {@code PolyfillRegistry.loadAndRegister}) on a fresh transformer, then assert both the redirect map
 * and an actual transformed class no longer name the removed class.
 */
class TransformPolyfillRegressionTest {

    private static final String REMOVED = "net/minecraft/util/LazyLoadedValue";
    private static final String POLYFILL = "com/retromod/polyfill/minecraft/embedded/LazyLoadedValue";

    private String savedVersion;

    @BeforeEach
    void setUp() {
        savedVersion = RetromodVersion.TARGET_MC_VERSION;
        // 26.1: LazyLoadedValue is removed here, so the polyfill is active.
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    @AfterEach
    void tearDown() {
        RetromodVersion.TARGET_MC_VERSION = savedVersion;
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    /**
     * Reproduces what {@code transformCommand}'s no-source-version all-shims branch does: register
     * every shim, then load the polyfills. The removed class must now have a class redirect onto the
     * embedded polyfill.
     */
    @Test
    @DisplayName("transform pipeline registers the LazyLoadedValue -> polyfill redirect")
    void registersRemovedVanillaClassRedirect() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        for (VersionShim shim : new ShimRegistry().getAllShims()) {
            shim.registerRedirects(t);
        }
        new PolyfillRegistry().loadAndRegister(t);

        assertEquals(POLYFILL, t.getClassRedirects().get(REMOVED),
                "transform must redirect the removed net/minecraft/util/LazyLoadedValue "
                        + "onto the embedded polyfill (parity with analyze/batch/runtime)");
    }

    /**
     * The {@code batch} (and AOT) path reaches the transformer ONLY through the shared
     * {@code registerAuxiliaryRedirects} step, which once registered class-moves/API-shims/mappings
     * but NOT the polyfills, so a batch-transformed mod referencing a removed-vanilla class was left
     * dangling (found by a corpus audit: raw {@code net/minecraft/util/Tuple} refs survived batch).
     * {@code registerAuxiliaryRedirects} now loads polyfills too.
     */
    @Test
    @DisplayName("batch path (registerAuxiliaryRedirects) also loads the removed-API polyfills")
    void batchAuxiliaryStepLoadsPolyfills() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        com.retromod.embedder.ModVersionInfo info = new com.retromod.embedder.ModVersionInfo(
                "test", "1.0", "1.21.1", "neoforge", "1.0",
                java.util.Set.of(), java.util.Set.of(), false);
        RetromodCli.registerAuxiliaryRedirects(t, info, java.util.List.of());
        assertEquals(POLYFILL, t.getClassRedirects().get(REMOVED),
                "batch/AOT reach the transformer only through registerAuxiliaryRedirects, which must "
                        + "load polyfills too (parity with the transform path)");
    }

    /**
     * End-to-end at the bytecode level: a class that references the removed class must, after the
     * transform pipeline runs, no longer name {@code net/minecraft/util/LazyLoadedValue} anywhere.
     */
    @Test
    @DisplayName("transformed output no longer references the removed class")
    void transformStripsRemovedVanillaReference() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        for (VersionShim shim : new ShimRegistry().getAllShims()) {
            shim.registerRedirects(t);
        }
        new PolyfillRegistry().loadAndRegister(t);

        byte[] out = t.transformClass(lazyLoadedValueReferencingClass(), "test/LazyFixture");

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);

        // No NEW / field type / any reference to the removed class survives.
        boolean referencesRemovedType = cn.methods.stream()
                .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof TypeInsnNode)
                .map(i -> ((TypeInsnNode) i).desc)
                .anyMatch(REMOVED::equals);
        assertFalse(referencesRemovedType,
                "no NEW/CHECKCAST of the removed net/minecraft/util/LazyLoadedValue may remain");

        boolean fieldReferencesRemoved = cn.fields.stream()
                .anyMatch(f -> f.desc.contains(REMOVED));
        assertFalse(fieldReferencesRemoved,
                "the field type must be rewritten off the removed class");

        // And the polyfill is what the reference was redirected onto.
        boolean referencesPolyfill = cn.methods.stream()
                .flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof TypeInsnNode)
                .map(i -> ((TypeInsnNode) i).desc)
                .anyMatch(POLYFILL::equals);
        assertTrue(referencesPolyfill,
                "the reference must be redirected onto the embedded LazyLoadedValue polyfill");
    }

    /** A class with a {@code LazyLoadedValue} field and a {@code new LazyLoadedValue} in a method. */
    private static byte[] lazyLoadedValueReferencingClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/LazyFixture", null, "java/lang/Object", null);

        FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE, "cached",
                "L" + REMOVED + ";", null, null);
        fv.visitEnd();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "make",
                "()L" + REMOVED + ";", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, REMOVED);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, REMOVED, "<init>", "()V", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
