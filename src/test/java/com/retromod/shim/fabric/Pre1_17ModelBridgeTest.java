/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural tests for the synthetic {@code LegacyModelPart} from
 * {@link Pre1_17ModelBridge}. It extends {@code net/minecraft/class_630}, absent
 * from the test classpath, so we can't JVM-load it; instead we inspect it with ASM:
 * it parses, passes {@link CheckClassAdapter}, extends {@code class_630}, and declares
 * the factory plus de-virtualized targets the redirects point at. Descriptors are
 * asserted because a wrong one is a {@code VerifyError} in game.
 */
class Pre1_17ModelBridgeTest {

    private static final String MP = "net/minecraft/class_630";
    private static final String MODEL = "net/minecraft/class_3879";
    private static final String POSE = "net/minecraft/class_4587";
    private static final String VC = "net/minecraft/class_4588";

    @Test
    @DisplayName("synthetic LegacyModelPart is well-formed and extends class_630")
    void generatesValidSubclass() {
        byte[] bytes = Pre1_17ModelBridge.generateLegacyModelPart();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "no bytecode emitted");

        ClassReader cr = new ClassReader(bytes);
        assertEquals(Pre1_17ModelBridge.SELF, cr.getClassName(), "wrong class name");
        assertEquals(MP, cr.getSuperName(), "must extend ModelPart (class_630)");

        // checkDataFlow=false: frame/data-flow checks need class_630; leave those to
        // the JVM in game. This only catches malformed bytecode.
        assertDoesNotThrow(() ->
                cr.accept(new CheckClassAdapter(new ClassWriter(0), false), 0),
                "generated class failed structural verification");
    }

    @Test
    @DisplayName("declares every redirect target with the exact expected descriptor")
    void declaresAllRedirectTargets() {
        Set<String> methods = collectMethods(Pre1_17ModelBridge.generateLegacyModelPart());

        // ctors the constructor-redirects map onto.
        assertContains(methods, "<init>", "(L" + MODEL + ";II)V");
        assertContains(methods, "<init>", "(L" + MODEL + ";)V");
        assertContains(methods, "create", "(L" + MODEL + ";II)L" + MP + ";");
        assertContains(methods, "create", "(L" + MODEL + ";)L" + MP + ";");

        // de-virtualized targets: receiver (class_630) is the first param.
        assertContains(methods, "addBox",        "(L" + MP + ";FFFFFFF)V");
        assertContains(methods, "addBoxMirror",  "(L" + MP + ";FFFFFFFZ)V");
        assertContains(methods, "addBoxR",       "(L" + MP + ";FFFFFF)L" + MP + ";");
        assertContains(methods, "addBoxRMirror", "(L" + MP + ";FFFFFFZ)L" + MP + ";");
        assertContains(methods, "texOffs",       "(L" + MP + ";II)L" + MP + ";");
        assertContains(methods, "setTexSize",    "(L" + MP + ";II)L" + MP + ";");
        assertContains(methods, "setPos",        "(L" + MP + ";FFF)V");
        assertContains(methods, "addChild",      "(L" + MP + ";L" + MP + ";)V");
        assertContains(methods, "render",
                "(L" + MP + ";L" + POSE + ";L" + VC + ";IIFFFF)V");

        // layer 3 root: no-arg ctor + getChild/hasChild overrides (host IDs).
        assertContains(methods, "<init>", "()V");
        assertContains(methods, "method_32086", "(Ljava/lang/String;)L" + MP + ";"); // getChild
        assertContains(methods, "method_41919", "(Ljava/lang/String;)Z");            // hasChild
    }

    @Test
    @DisplayName("LegacyModelPart declares the EMPTY_ROOT field used by base bridges")
    void declaresEmptyRootField() {
        Set<String> fields = collectFields(Pre1_17ModelBridge.generateLegacyModelPart());
        assertTrue(fields.contains("EMPTY_ROOT:L" + MP + ";"),
                "missing EMPTY_ROOT field (have: " + fields + ")");
    }

    @Test
    @DisplayName("layer 2: render override + drawBox + emitVertex are declared with exact descriptors")
    void declaresLayer2RenderMethods() {
        Set<String> methods = collectMethods(Pre1_17ModelBridge.generateLegacyModelPart());
        // override of class_630.method_22699 (int-color render, 1.21.2+ signature).
        assertContains(methods, "method_22699", "(L" + POSE + ";L" + VC + ";III)V");
        // per-cube draw helper takes the inner Pose (class_4587$class_4665).
        assertContains(methods, "drawBox",
                "(L" + POSE + "$class_4665;L" + VC + ";[FIIIII)V");
        // per-vertex helper for the addVertex/setColor/setUv/setOverlay/setLight/setNormal chain.
        assertContains(methods, "emitVertex",
                "(L" + POSE + "$class_4665;L" + VC + ";FFFIFFIIFFF)V");
    }

    @Test
    @DisplayName("layer 2: instance fields (boxes, children, tex offsets, pos) are present")
    void declaresLayer2Fields() {
        Set<String> fields = collectFields(Pre1_17ModelBridge.generateLegacyModelPart());
        assertTrue(fields.contains("boxes:Ljava/util/List;"),       "missing boxes (have: " + fields + ")");
        assertTrue(fields.contains("children:Ljava/util/List;"),    "missing children");
        assertTrue(fields.contains("currentTexU:I"),                "missing currentTexU");
        assertTrue(fields.contains("currentTexV:I"),                "missing currentTexV");
        assertTrue(fields.contains("texW:I"),                       "missing texW");
        assertTrue(fields.contains("texH:I"),                       "missing texH");
        assertTrue(fields.contains("posX:F"),                       "missing posX");
        assertTrue(fields.contains("posY:F"),                       "missing posY");
        assertTrue(fields.contains("posZ:F"),                       "missing posZ");
    }

    @Test
    @DisplayName("concrete-base bridge extends the base with old + modern ctors")
    void generatesValidConcreteBaseBridge() {
        // class_623 (ZombieModel): old super (FFII)V plus modern host ctor (ModelPart)V.
        byte[] cls = Pre1_17ModelBridge.generateLegacyBase(
                "com/retromod/generated/LegacyModelBase_623",
                "net/minecraft/class_623", new String[]{"(FFII)V"});
        ClassReader cr = new ClassReader(cls);
        assertEquals("net/minecraft/class_623", cr.getSuperName(), "must extend the base");
        assertDoesNotThrow(() ->
                cr.accept(new CheckClassAdapter(new ClassWriter(0), false), 0),
                "base bridge failed structural verification");
        Set<String> methods = collectMethods(cls);
        assertContains(methods, "<init>", "(FFII)V");        // old super (args ignored, uses EMPTY_ROOT)
        assertContains(methods, "<init>", "(L" + MP + ";)V"); // modern (ModelPart) fallback
    }

    private static Set<String> collectMethods(byte[] bytes) {
        Set<String> out = new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] ex) {
                out.add(name + desc);
                return null;
            }
        }, 0);
        return out;
    }

    private static Set<String> collectFields(byte[] bytes) {
        Set<String> out = new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(int access, String name, String desc,
                                                             String sig, Object val) {
                out.add(name + ":" + desc);
                return null;
            }
        }, 0);
        return out;
    }

    private static void assertContains(Set<String> methods, String name, String desc) {
        assertTrue(methods.contains(name + desc),
                "missing method " + name + desc + " (have: " + methods + ")");
    }
}
