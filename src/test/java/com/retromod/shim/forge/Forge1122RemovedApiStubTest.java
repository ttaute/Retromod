/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.forge;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for #131/#132/#134/#135: a 1.12.2 Forge mod's class {@code implements} a Forge type that
 * modern Forge/NeoForge deleted ({@code IWorldGenerator}, {@code IGuiHandler}, {@code IMessage},
 * {@code IMessageHandler}, {@code IForgeRegistryEntry}), so the class can't even load
 * ({@code NoClassDefFoundError}). {@link Forge_1_12_2_to_1_13_2} now embeds an empty stub interface
 * per removed type and redirects the old name onto it, so the class loads (the feature stays inert
 * until its registration idiom is bridged). This asserts the {@code implements} clause is rewritten
 * to the registered stubs and none of the removed names survive.
 */
class Forge1122RemovedApiStubTest {

    private static final String[][] REMOVED = {
            {"net/minecraftforge/fml/common/IWorldGenerator",
                    "com/retromod/generated/forge1122/IWorldGenerator"},
            {"net/minecraftforge/fml/common/network/IGuiHandler",
                    "com/retromod/generated/forge1122/IGuiHandler"},
            {"net/minecraftforge/fml/common/network/simpleimpl/IMessage",
                    "com/retromod/generated/forge1122/IMessage"},
            {"net/minecraftforge/fml/common/network/simpleimpl/IMessageHandler",
                    "com/retromod/generated/forge1122/IMessageHandler"},
            {"net/minecraftforge/registries/IForgeRegistryEntry",
                    "com/retromod/generated/forge1122/IForgeRegistryEntry"},
    };

    private RetromodTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = RetromodTransformer.getInstance();
        transformer.clearRedirectsForTesting();
        new Forge_1_12_2_to_1_13_2().registerRedirects(transformer);
    }

    @AfterEach
    void tearDown() {
        transformer.clearRedirectsForTesting();
    }

    @Test
    @DisplayName("each removed Forge type is a registered, empty, loadable synthetic interface")
    void stubsAreRegisteredEmptyInterfaces() {
        for (String[] pair : REMOVED) {
            String stub = pair[1];
            byte[] bytes = transformer.getSyntheticClasses().get(stub);
            assertNotNull(bytes, stub + " must be registered as a synthetic (else it is a phantom target)");
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0, stub + " must be an interface");
            assertEquals(stub, cn.name);
            assertTrue(cn.methods.isEmpty(), stub + " must be an EMPTY interface (no abstract methods to satisfy)");
        }
    }

    @Test
    @DisplayName("a mod class implementing the removed types is rewritten to implement the stubs")
    void modClassImplementsClauseRewritten() {
        byte[] out = transformer.transformClass(modImplementingAllRemoved(), "test/mod/LegacyStuff.class");
        assertNotNull(out);

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        List<String> ifaces = cn.interfaces;

        for (String[] pair : REMOVED) {
            assertFalse(ifaces.contains(pair[0]),
                    "removed type " + pair[0] + " must not survive in the implements clause (would NoClassDefFoundError)");
            assertTrue(ifaces.contains(pair[1]),
                    "implements clause must be rewritten to the stub " + pair[1]);
        }
    }

    @Test
    @DisplayName("eventClassBytes: extends a CLASS base, but implements an INTERFACE base (EventBus 7)")
    void eventClassBytesShapePerBaseKind() {
        // EventBus 6 (Forge 1.20.1) / NeoForge: base is a CLASS -> the stub extends it.
        ClassNode klass = read(Forge_1_12_2_to_1_13_2.eventClassBytes(
                "com/retromod/generated/forge1122/RegistryEvent",
                "net/minecraftforge/eventbus/api/Event", false));
        assertEquals(0, klass.access & Opcodes.ACC_INTERFACE, "the stub is a class");
        assertEquals("net/minecraftforge/eventbus/api/Event", klass.superName, "extends the class base");
        assertTrue(klass.interfaces == null || klass.interfaces.isEmpty(), "no implements for a class base");

        // EventBus 7 (Forge 26.2): base internal/Event is an INTERFACE -> the stub must implement it, not
        // extend it (extending an interface fails to load with IncompatibleClassChangeError).
        ClassNode iface = read(Forge_1_12_2_to_1_13_2.eventClassBytes(
                "com/retromod/generated/forge1122/RegistryEvent",
                "net/minecraftforge/eventbus/internal/Event", true));
        assertEquals("java/lang/Object", iface.superName, "extends Object when the base is an interface");
        assertTrue(iface.interfaces.contains("net/minecraftforge/eventbus/internal/Event"),
                "implements the interface base so it still passes isAssignableFrom(Event)");
    }

    @Test
    @DisplayName("RegistryEvent stubs are SKIPPED when the eventbus base can't be resolved (no re-crash)")
    void registryEventStubsSkippedWithoutEventBus() {
        // The unit-test JVM has no eventbus on the classpath, so resolveEventBusBaseClass() returns null
        // and the RegistryEvent stubs are safely skipped -- baking a wrong/absent base would re-crash.
        assertFalse(transformer.getSyntheticClasses().containsKey(
                        "com/retromod/generated/forge1122/RegistryEvent"),
                "without a resolvable eventbus base, the RegistryEvent stub must be skipped, not baked wrong");
        // The plain interface stubs (no Event-subtype requirement) are unaffected and still register.
        assertTrue(transformer.getSyntheticClasses().containsKey(
                        "com/retromod/generated/forge1122/IWorldGenerator"),
                "the removed-interface stubs still register regardless of the eventbus base");
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    /** A synthetic 1.12.2 mod class that implements every removed Forge type. */
    private static byte[] modImplementingAllRemoved() {
        String[] ifaces = new String[REMOVED.length];
        for (int i = 0; i < REMOVED.length; i++) ifaces[i] = REMOVED[i][0];

        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "test/mod/LegacyStuff", null, "java/lang/Object", ifaces);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
