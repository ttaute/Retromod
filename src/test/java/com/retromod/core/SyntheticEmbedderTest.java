/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * The per-mod synthetic-embedding engine (§B/§C prerequisite for B2/B4). The key invariants,
 * all split-package safety properties NeoForge's JPMS module-per-mod loading demands:
 * <ul>
 *   <li>a synthetic is embedded only into a mod that actually references it (gating);</li>
 *   <li>it is embedded under a unique-per-mod {@code com/retromod/embedded/<key>/} package,
 *       NOT at its original (loader-owned) name, so it can never split-package with the
 *       loader module or with another mod;</li>
 *   <li>the mod's references are rewritten to the embedded copy;</li>
 *   <li>a mod that doesn't reference it is left completely untouched.</li>
 * </ul>
 */
class SyntheticEmbedderTest {

    private static final String SYNTH = "net/fake/loaderpkg/Removed"; // a "deleted" loader class

    private static byte[] simpleClass(String internalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        var c = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(ALOAD, 0);
        c.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(RETURN);
        c.visitMaxs(1, 1);
        c.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** A class with a field typed {@code L<SYNTH>;}, a reference the embedder must rewrite. */
    private static byte[] classReferencing(String name) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V17, ACC_PUBLIC, name, null, "java/lang/Object", null);
        cw.visitField(ACC_PRIVATE, "ref", "L" + SYNTH + ";", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] classReferencingNothing(String name) {
        return simpleClass(name);
    }

    private static void write(Path dir, String internalName, byte[] bytes) throws Exception {
        Path p = dir.resolve(internalName + ".class");
        Files.createDirectories(p.getParent());
        Files.write(p, bytes);
    }

    private static String fieldDesc(Path dir, String internalName) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(dir.resolve(internalName + ".class"))).accept(cn, 0);
        for (FieldNode f : cn.fields) if (f.name.equals("ref")) return f.desc;
        return null;
    }

    @Test
    @DisplayName("referenced synthetic is embedded under a unique-per-mod package, NOT its original name")
    void embedsReferencedSyntheticSafely(@TempDir Path dir) throws Exception {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            t.registerSyntheticClass(SYNTH, simpleClass(SYNTH));
            write(dir, "mod/Uses", classReferencing("mod/Uses"));
            write(dir, "mod/Other", classReferencingNothing("mod/Other"));

            int n = SyntheticEmbedder.embed(dir, "cool-mod.jar", t);

            assertEquals(1, n, "the one referenced synthetic should be embedded");
            String uniquePkg = SyntheticEmbedder.PREFIX + "cool_mod/Removed";
            // embedded under the unique Retromod package...
            assertTrue(Files.exists(dir.resolve(uniquePkg + ".class")),
                    "synthetic must be embedded under com/retromod/embedded/<key>/");
            // ...and NOT at its original loader-owned name (that would split-package the module)
            assertFalse(Files.exists(dir.resolve(SYNTH + ".class")),
                    "synthetic must NOT be embedded at its original (loader) package name");
            // the mod's reference was rewritten to the embedded copy
            assertEquals("L" + uniquePkg + ";", fieldDesc(dir, "mod/Uses"),
                    "the referencing class must now point at the embedded copy");
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    @Test
    @DisplayName("a mod that references no synthetic is left untouched")
    void noReferenceNoEmbed(@TempDir Path dir) throws Exception {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            t.registerSyntheticClass(SYNTH, simpleClass(SYNTH));
            write(dir, "mod/Plain", classReferencingNothing("mod/Plain"));
            byte[] before = Files.readAllBytes(dir.resolve("mod/Plain.class"));

            int n = SyntheticEmbedder.embed(dir, "plain-mod.jar", t);

            assertEquals(0, n, "nothing referenced -> nothing embedded");
            assertFalse(Files.exists(dir.resolve(SyntheticEmbedder.PREFIX + "plain_mod/Removed.class")));
            assertArrayEquals(before, Files.readAllBytes(dir.resolve("mod/Plain.class")),
                    "a non-referencing class must be byte-for-byte unchanged");
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    @Test
    @DisplayName("two mods get the synthetic in DISTINCT packages (no cross-mod split-package)")
    void distinctPackagesPerMod(@TempDir Path dirA, @TempDir Path dirB) throws Exception {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            t.registerSyntheticClass(SYNTH, simpleClass(SYNTH));
            write(dirA, "a/Uses", classReferencing("a/Uses"));
            write(dirB, "b/Uses", classReferencing("b/Uses"));

            SyntheticEmbedder.embed(dirA, "mod-a.jar", t);
            SyntheticEmbedder.embed(dirB, "mod-b.jar", t);

            assertTrue(Files.exists(dirA.resolve(SyntheticEmbedder.PREFIX + "mod_a/Removed.class")));
            assertTrue(Files.exists(dirB.resolve(SyntheticEmbedder.PREFIX + "mod_b/Removed.class")));
            // The two embedded packages differ, so the two mod modules never share a package.
            assertFalse(Files.exists(dirA.resolve(SyntheticEmbedder.PREFIX + "mod_b/Removed.class")));
        } finally {
            t.clearRedirectsForTesting();
        }
    }

    @Test
    @DisplayName("embedIntoJar (offline path): embeds + rewrites in-place and preserves the manifest")
    void embedIntoJarPreservesManifest(@TempDir Path dir) throws Exception {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            t.registerSyntheticClass(SYNTH, simpleClass(SYNTH));
            Path jar = dir.resolve("mod.jar");
            try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
                zos.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
                zos.write("Manifest-Version: 1.0\r\nFabric-Loom-Version: x\r\n\r\n".getBytes());
                zos.closeEntry();
                zos.putNextEntry(new java.util.zip.ZipEntry("mod/Uses.class"));
                zos.write(classReferencing("mod/Uses"));
                zos.closeEntry();
            }

            int n = SyntheticEmbedder.embedIntoJar(jar, "cool-mod.jar", t);
            assertEquals(1, n);

            java.util.Map<String, byte[]> out = new java.util.HashMap<>();
            try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(jar))) {
                java.util.zip.ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (!e.isDirectory()) out.put(e.getName(), zis.readAllBytes());
                }
            }
            assertTrue(out.containsKey("META-INF/MANIFEST.MF"), "manifest must be preserved");
            assertTrue(new String(out.get("META-INF/MANIFEST.MF")).contains("Manifest-Version: 1.0"),
                    "manifest content intact");
            assertTrue(out.containsKey(SyntheticEmbedder.PREFIX + "cool_mod/Removed.class"),
                    "synthetic embedded under unique pkg");
            assertFalse(out.containsKey(SYNTH + ".class"), "not at original (loader) name");
            ClassNode cn = new ClassNode();
            new ClassReader(out.get("mod/Uses.class")).accept(cn, 0);
            assertEquals("L" + SyntheticEmbedder.PREFIX + "cool_mod/Removed;", cn.fields.get(0).desc,
                    "reference rewritten to the embedded copy");
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
