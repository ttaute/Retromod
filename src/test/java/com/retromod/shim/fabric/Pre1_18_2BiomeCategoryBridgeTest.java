/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural tests for the {@code LegacyBiomeCategory} enum that {@link Pre1_18_2BiomeCategoryBridge}
 * generates. We can't load it here ({@code getCategory} references {@code class_1959}, off the test
 * classpath), so we inspect the bytecode with ASM instead.
 */
class Pre1_18_2BiomeCategoryBridgeTest {

    private static final String SELF = "com/retromod/generated/LegacyBiomeCategory";
    private static final String L_SELF = "L" + SELF + ";";
    private static final String L_BIOME = "Lnet/minecraft/class_1959;";

    private static final String[] EXPECTED_CONSTANTS = {
        "field_9354", "field_9355", "field_9356", "field_9357", "field_9358",
        "field_9359", "field_9360", "field_9361", "field_9362", "field_9363",
        "field_9364", "field_9365", "field_9366", "field_9367", "field_9368",
        "field_9369", "field_9370",
    };

    @Test
    @DisplayName("synthetic enum is well-formed and extends Enum")
    void generatesValidEnum() {
        byte[] bytes = Pre1_18_2BiomeCategoryBridge.generateLegacyBiomeCategory();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "no bytecode emitted");

        ClassReader cr = new ClassReader(bytes);
        assertEquals(SELF, cr.getClassName(), "wrong class name");
        assertEquals("java/lang/Enum", cr.getSuperName(), "must extend Enum");

        // dataFlow=false: full data-flow checking needs class_1959 on the classpath.
        ClassWriter sink = new ClassWriter(0);
        assertDoesNotThrow(
                () -> cr.accept(new CheckClassAdapter(sink, false), 0),
                "CheckClassAdapter rejected the synthetic enum"
        );
    }

    @Test
    @DisplayName("all 17 intermediary category constants are public-static-final fields of self type")
    void hasAllCategoryConstants() {
        byte[] bytes = Pre1_18_2BiomeCategoryBridge.generateLegacyBiomeCategory();
        Set<String> got = new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc,
                                           String signature, Object value) {
                int mask = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
                if ((access & mask) == mask && L_SELF.equals(desc)) {
                    got.add(name);
                }
                return null;
            }
        }, 0);

        for (String f : EXPECTED_CONSTANTS) {
            assertTrue(got.contains(f),
                    "missing category constant " + f
                            + " - pre-1.18.2 mods compiled against this slot will NoSuchFieldError");
        }
    }

    @Test
    @DisplayName("values(), valueOf(String), and getCategory(Biome) have the expected descriptors")
    void hasRequiredHelpers() {
        byte[] bytes = Pre1_18_2BiomeCategoryBridge.generateLegacyBiomeCategory();
        Set<String> sigs = new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_STATIC) != 0) {
                    sigs.add(name + desc);
                }
                return null;
            }
        }, 0);

        assertTrue(sigs.contains("values()[" + L_SELF), "missing values()");
        assertTrue(sigs.contains("valueOf(Ljava/lang/String;)" + L_SELF), "missing valueOf(String)");
        assertTrue(sigs.contains("getCategory(" + L_BIOME + ")" + L_SELF),
                "missing getCategory(Biome) - Biome.method_8688() rewrites land here");
    }

    @Test
    @DisplayName("bridge.register no-ops + does not throw when class_1959 is absent")
    void absentHostIsNoop() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        assertDoesNotThrow(() -> Pre1_18_2BiomeCategoryBridge.register(t));
    }
}
