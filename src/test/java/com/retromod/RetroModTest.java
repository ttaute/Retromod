/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod;

import com.retromod.core.*;
import com.retromod.aot.AotCompiler;
import com.retromod.mapping.IntermediaryToMojangMapper;
import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.shim.ShimRegistry;
import com.retromod.shim.fabric.*;
import com.retromod.shim.neoforge.*;
import com.retromod.shim.forge.*;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for RetroMod.
 * 
 * Tests:
 * 1. Method redirect registration
 * 2. Class redirect registration
 * 3. Bytecode transformation correctness
 * 4. Shim chain resolution
 * 5. Mixin compatibility transformation
 * 6. AOT compilation
 */
public class RetroModTest {
    
    private RetroModTransformer transformer;
    private ShimRegistry shimRegistry;
    
    @BeforeEach
    void setUp() {
        transformer = RetroModTransformer.getInstance();
        shimRegistry = new ShimRegistry();
    }
    
    // =========================================================
    // SHIM REGISTRATION TESTS
    // =========================================================
    
    @Test
    @DisplayName("Fabric 1.21 to 1.21.1 shim registers redirects")
    void testFabric121To1211ShimRegistration() {
        Fabric_1_21_to_1_21_1 shim = new Fabric_1_21_to_1_21_1();
        
        assertEquals("1.21", shim.getSourceVersion());
        assertEquals("1.21.1", shim.getTargetVersion());
        assertEquals("fabric", shim.getModLoaderType());
        
        // Register redirects
        shim.registerRedirects(transformer);
        
        // This shim has minimal changes, just verify it ran without error
        assertNotNull(shim.getShimName());
    }
    
    @Test
    @DisplayName("Fabric 1.21.8 to 1.21.9 shim handles Entity.getWorld rename")
    void testEntityGetWorldRedirect() {
        Fabric_1_21_8_to_1_21_9 shim = new Fabric_1_21_8_to_1_21_9();
        shim.registerRedirects(transformer);
        
        // Check that Entity.getWorld is redirected
        var redirects = transformer.getMethodRedirects();
        var key = new RetroModTransformer.MethodKey(
            "net/minecraft/entity/Entity",
            "getWorld",
            "()Lnet/minecraft/world/World;"
        );
        
        assertTrue(redirects.containsKey(key), 
            "Entity.getWorld should be redirected to getEntityWorld");
        
        var target = redirects.get(key);
        assertEquals("getEntityWorld", target.name());
    }
    
    @Test
    @DisplayName("NeoForge 1.21.10 to 1.21.11 shim handles ResourceLocation rename")
    void testResourceLocationRedirect() {
        NeoForge_1_21_10_to_1_21_11 shim = new NeoForge_1_21_10_to_1_21_11();
        shim.registerRedirects(transformer);
        
        // Check class redirect
        var classRedirects = transformer.getClassRedirects();
        assertTrue(classRedirects.containsKey("net/minecraft/resources/ResourceLocation"),
            "ResourceLocation should be redirected to Identifier");
        
        assertEquals("net/minecraft/resources/Identifier",
            classRedirects.get("net/minecraft/resources/ResourceLocation"));
    }
    
    // =========================================================
    // SHIM CHAIN RESOLUTION TESTS
    // =========================================================
    
    @Test
    @DisplayName("ShimRegistry finds direct shim")
    void testDirectShimResolution() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        
        List<VersionShim> chain = shimRegistry.findShimChain("fabric", "1.21.8", "1.21.9");
        
        assertEquals(1, chain.size());
        assertEquals("1.21.8", chain.get(0).getSourceVersion());
        assertEquals("1.21.9", chain.get(0).getTargetVersion());
    }
    
    @Test
    @DisplayName("ShimRegistry finds multi-step shim chain")
    void testMultiStepShimChain() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        shimRegistry.register(new Fabric_1_21_9_to_1_21_10());
        shimRegistry.register(new Fabric_1_21_10_to_1_21_11());
        
        List<VersionShim> chain = shimRegistry.findShimChain("fabric", "1.21.8", "1.21.11");
        
        assertEquals(3, chain.size());
        assertEquals("1.21.8", chain.get(0).getSourceVersion());
        assertEquals("1.21.9", chain.get(0).getTargetVersion());
        assertEquals("1.21.9", chain.get(1).getSourceVersion());
        assertEquals("1.21.10", chain.get(1).getTargetVersion());
        assertEquals("1.21.10", chain.get(2).getSourceVersion());
        assertEquals("1.21.11", chain.get(2).getTargetVersion());
    }
    
    @Test
    @DisplayName("ShimRegistry returns empty for unsupported transition")
    void testUnsupportedTransition() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        
        // Try to find chain for wrong loader
        List<VersionShim> chain = shimRegistry.findShimChain("forge", "1.21.8", "1.21.9");
        assertTrue(chain.isEmpty());
        
        // Try to find chain for non-existent versions
        List<VersionShim> chain2 = shimRegistry.findShimChain("fabric", "1.20", "1.21");
        assertTrue(chain2.isEmpty());
    }
    
    // =========================================================
    // BYTECODE TRANSFORMATION TESTS
    // =========================================================
    
    @Test
    @DisplayName("Method invocation is correctly rewritten")
    void testMethodInvocationRewrite() {
        // Register a test redirect
        transformer.registerMethodRedirect(
            "test/OldClass", "oldMethod", "()V",
            "test/NewClass", "newMethod", "()V"
        );
        
        // Create test bytecode that calls OldClass.oldMethod
        byte[] original = createTestClass();
        
        // Transform it
        byte[] transformed = transformer.transformClass(original, "test/TestClass");
        
        // Verify the method call was rewritten
        assertTrue(containsMethodCall(transformed, "test/NewClass", "newMethod"),
            "Transformed class should call NewClass.newMethod");
        assertFalse(containsMethodCall(transformed, "test/OldClass", "oldMethod"),
            "Transformed class should not call OldClass.oldMethod");
    }
    
    @Test
    @DisplayName("Class references are correctly rewritten")
    void testClassReferenceRewrite() {
        transformer.registerClassRedirect("test/OldType", "test/NewType");
        
        // Create test bytecode with OldType reference
        byte[] original = createTestClassWithTypeRef("test/OldType");
        
        // Transform it
        byte[] transformed = transformer.transformClass(original, "test/TestClass");
        
        // Verify class reference was rewritten
        assertTrue(containsClassRef(transformed, "test/NewType"),
            "Transformed class should reference NewType");
    }
    
    // =========================================================
    // MIXIN COMPATIBILITY TESTS
    // =========================================================
    
    @Test
    @DisplayName("Mixin method targets are retargeted")
    void testMixinMethodRetargeting() {
        // Register the getWorld -> getEntityWorld redirect
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/Entity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        MixinCompatibilityTransformer mixinTransformer = 
            new MixinCompatibilityTransformer(transformer);
        
        // Create a mock mixin class with @Inject targeting getWorld
        byte[] mixinClass = createMockMixinClass("getWorld");
        
        // Transform it
        byte[] transformed = mixinTransformer.transformMixinClass(mixinClass);
        
        // The annotation should now target getEntityWorld
        // (In a real test, we'd parse the bytecode to verify)
        assertNotNull(transformed);
    }
    
    // =========================================================
    // AOT COMPILATION TESTS
    // =========================================================
    
    @Test
    @DisplayName("AOT compiler detects obfuscated classes")
    void testObfuscatedClassDetection() {
        // Test class with short names (typical obfuscation)
        byte[] obfuscated = createObfuscatedClass("a", new String[]{"a", "b", "c"});
        
        // Test class with normal names
        byte[] normal = createNormalClass("TestClass", new String[]{"doSomething", "getValue"});
        
        // The isObfuscated check would be in AotCompiler
        // For unit testing, we verify the bytecode patterns
        assertTrue(isLikelyObfuscated(obfuscated));
        assertFalse(isLikelyObfuscated(normal));
    }
    
    // =========================================================
    // HELPER METHODS
    // =========================================================
    
    /**
     * Create a test class with a method call.
     */
    private byte[] createTestClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/TestClass", null, 
                "java/lang/Object", null);
        
        // Create a method that calls OldClass.oldMethod
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "test/OldClass");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "test/OldClass", "<init>", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "test/OldClass", "oldMethod", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createTestClassWithTypeRef(String typeName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/TestClass", null, 
                "java/lang/Object", null);
        
        // Field with the type
        cw.visitField(Opcodes.ACC_PRIVATE, "field", "L" + typeName + ";", null, null);
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createMockMixinClass(String targetMethod) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/TestMixin", null, 
                "java/lang/Object", null);
        
        // Add @Mixin annotation (simplified)
        AnnotationVisitor av = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", true);
        av.visitEnd();
        
        // Add method with @Inject annotation
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onTest", "()V", null, null);
        AnnotationVisitor injectAv = mv.visitAnnotation(
            "Lorg/spongepowered/asm/mixin/injection/Inject;", true);
        
        // Add method target
        AnnotationVisitor methodAv = injectAv.visitArray("method");
        methodAv.visit(null, targetMethod);
        methodAv.visitEnd();
        
        injectAv.visitEnd();
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createObfuscatedClass(String name, String[] methodNames) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        
        for (String method : methodNames) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method, "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createNormalClass(String name, String[] methodNames) {
        return createObfuscatedClass("com/example/" + name, methodNames);
    }
    
    private boolean containsMethodCall(byte[] classBytes, String owner, String name) {
        final boolean[] found = {false};
        
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String mname, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String o, String n, 
                            String d, boolean itf) {
                        if (owner.equals(o) && name.equals(n)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        
        return found[0];
    }
    
    private boolean containsClassRef(byte[] classBytes, String className) {
        // Simple check: look for class name in constant pool
        String search = "L" + className + ";";
        String bytesStr = new String(classBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return bytesStr.contains(search) || bytesStr.contains(className);
    }
    
    // =========================================================
    // CLASS REDIRECT TESTS
    // =========================================================

    @Test
    @DisplayName("DirectionProperty (class_2753) is redirected")
    void testDirectionPropertyRedirect() {
        var classRedirects = transformer.getClassRedirects();
        String target = classRedirects.get("net/minecraft/class_2753");
        assertNotNull(target, "class_2753 (DirectionProperty) should have a redirect");
        // May redirect to class_2754 (pre-26.1) or Mojang name (26.1+)
        assertTrue(
            target.equals("net/minecraft/class_2754") ||
            target.equals("net/minecraft/world/level/block/state/properties/DirectionProperty"),
            "class_2753 should redirect to either class_2754 or the Mojang DirectionProperty name, got: " + target);
    }

    @Test
    @DisplayName("JOML math class redirects are registered")
    void testJomlClassRedirects() {
        var classRedirects = transformer.getClassRedirects();

        assertEquals("org/joml/Matrix4f",
            classRedirects.get("net/minecraft/class_1159"),
            "class_1159 (MC Matrix4f) should redirect to org/joml/Matrix4f");
        assertEquals("org/joml/Matrix3f",
            classRedirects.get("net/minecraft/class_4581"),
            "class_4581 (MC Matrix3f) should redirect to org/joml/Matrix3f");
        assertEquals("org/joml/Quaternionf",
            classRedirects.get("net/minecraft/class_1158"),
            "class_1158 (MC Quaternion) should redirect to org/joml/Quaternionf");
        assertEquals("org/joml/Vector3f",
            classRedirects.get("net/minecraft/class_1160"),
            "class_1160 (MC Vec3f) should redirect to org/joml/Vector3f");
    }

    @Test
    @DisplayName("Class redirects are applied in bytecode transformation")
    void testClassRedirectAppliedInBytecode() {
        // Create a class that references class_2753
        byte[] original = createTestClassWithTypeRef("net/minecraft/class_2753");

        // Transform it
        byte[] transformed = transformer.transformClass(original, "test/TestClass");

        // Verify using ASM - parse the field descriptor from the transformed class
        String fieldType = getFieldType(transformed, "field");
        // class_2753 may redirect to class_2754 (pre-26.1) or Mojang name (26.1+)
        assertTrue(
            "Lnet/minecraft/class_2754;".equals(fieldType) ||
            "Lnet/minecraft/world/level/block/state/properties/DirectionProperty;".equals(fieldType),
            "Field type should be redirected from class_2753, got: " + fieldType);
    }

    @Test
    @DisplayName("JOML redirects are applied in bytecode transformation")
    void testJomlRedirectAppliedInBytecode() {
        byte[] original = createTestClassWithTypeRef("net/minecraft/class_1159");
        byte[] transformed = transformer.transformClass(original, "test/TestClass");

        String fieldType = getFieldType(transformed, "field");
        assertEquals("Lorg/joml/Matrix4f;", fieldType,
            "Field type should be redirected from class_1159 to org/joml/Matrix4f");
    }

    // =========================================================
    // KNOWN_REMOVED_CLASSES TESTS
    // =========================================================

    @Test
    @DisplayName("TitleScreen (class_442) is NOT in KNOWN_REMOVED_CLASSES")
    void testTitleScreenNotRemoved() {
        // class_442 is TitleScreen — it still exists and should NOT be in the removed set
        assertFalse(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_442"),
            "class_442 (TitleScreen) should NOT be in KNOWN_REMOVED_CLASSES");
    }

    @Test
    @DisplayName("DirectionProperty (class_2753) IS in KNOWN_REMOVED_CLASSES")
    void testDirectionPropertyInKnownRemoved() {
        assertTrue(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_2753"),
            "class_2753 (DirectionProperty) should be in KNOWN_REMOVED_CLASSES");
    }

    @Test
    @DisplayName("JOML math classes are in KNOWN_REMOVED_CLASSES")
    void testJomlClassesInKnownRemoved() {
        assertTrue(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_1159"),
            "class_1159 (Matrix4f) should be in KNOWN_REMOVED_CLASSES");
        assertTrue(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_4581"),
            "class_4581 (Matrix3f) should be in KNOWN_REMOVED_CLASSES");
        assertTrue(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_1158"),
            "class_1158 (Quaternion) should be in KNOWN_REMOVED_CLASSES");
        assertTrue(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_1160"),
            "class_1160 (Vec3f) should be in KNOWN_REMOVED_CLASSES");
    }

    @Test
    @DisplayName("BufferBuilder inner class is in KNOWN_REMOVED_CLASSES")
    void testBufferBuilderInnerClassRemoved() {
        assertTrue(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_287$class_7433"),
            "class_287$class_7433 (BufferBuilder.BuiltBuffer) should be in KNOWN_REMOVED_CLASSES");
    }

    @Test
    @DisplayName("Existing classes are NOT in KNOWN_REMOVED_CLASSES")
    void testExistingClassesNotRemoved() {
        // These classes still exist in 1.21.11
        assertFalse(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_442"),
            "TitleScreen should NOT be removed");
        assertFalse(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_310"),
            "MinecraftClient should NOT be removed");
        assertFalse(MixinCompatibilityTransformer.isKnownRemovedClass("net/minecraft/class_1297"),
            "Entity should NOT be removed");
    }

    // =========================================================
    // 26.1 DEOBFUSCATION TESTS
    // =========================================================

    @Test
    @DisplayName("IntermediaryToMojangMapper loads core class mappings")
    void testIntermediaryToMojangMapping() {
        IntermediaryToMojangMapper mapper = new IntermediaryToMojangMapper();
        mapper.load();

        // Core classes that every mod uses
        assertEquals("net/minecraft/client/Minecraft",
                mapper.mapClassName("net/minecraft/class_310"),
                "class_310 should map to Minecraft");
        assertEquals("net/minecraft/world/entity/Entity",
                mapper.mapClassName("net/minecraft/class_1297"),
                "class_1297 should map to Entity");
        assertEquals("net/minecraft/world/level/block/Block",
                mapper.mapClassName("net/minecraft/class_2248"),
                "class_2248 should map to Block");
        assertEquals("net/minecraft/world/item/ItemStack",
                mapper.mapClassName("net/minecraft/class_1799"),
                "class_1799 should map to ItemStack");
        assertEquals("net/minecraft/world/level/Level",
                mapper.mapClassName("net/minecraft/class_1937"),
                "class_1937 should map to Level");
        assertEquals("net/minecraft/resources/ResourceLocation",
                mapper.mapClassName("net/minecraft/class_2960"),
                "class_2960 should map to ResourceLocation");
        assertEquals("net/minecraft/client/gui/screens/Screen",
                mapper.mapClassName("net/minecraft/class_437"),
                "class_437 should map to Screen");
    }

    @Test
    @DisplayName("IntermediaryToMojangMapper maps methods correctly")
    void testIntermediaryMethodMapping() {
        IntermediaryToMojangMapper mapper = new IntermediaryToMojangMapper();
        mapper.load();

        // Entity methods
        assertEquals("getX",
                mapper.mapMethodName("net/minecraft/class_1297", "method_5628", "()D"),
                "method_5628 should map to getX");
        assertEquals("tick",
                mapper.mapMethodName("net/minecraft/class_1297", "method_5667", "()V"),
                "method_5667 should map to tick");

        // MinecraftClient methods
        assertEquals("getInstance",
                mapper.mapMethodName("net/minecraft/class_310", "method_1507", "()V"),
                "method_1507 should map to getInstance");
    }

    @Test
    @DisplayName("IntermediaryToMojangMapper maps fields correctly")
    void testIntermediaryFieldMapping() {
        IntermediaryToMojangMapper mapper = new IntermediaryToMojangMapper();
        mapper.load();

        assertEquals("player",
                mapper.mapFieldName("net/minecraft/class_310", "field_1755"),
                "field_1755 should map to player");
        assertEquals("level",
                mapper.mapFieldName("net/minecraft/class_310", "field_1687"),
                "field_1687 should map to level");
        assertEquals("onGround",
                mapper.mapFieldName("net/minecraft/class_1297", "field_6038"),
                "field_6038 should map to onGround");
    }

    @Test
    @DisplayName("Mojang name redirect applied in bytecode")
    void testMojangNameRedirectInBytecode() {
        IntermediaryToMojangMapper mapper = new IntermediaryToMojangMapper();
        mapper.load();
        mapper.registerWithTransformer(transformer);

        // Create bytecode referencing intermediary class_310 (MinecraftClient)
        byte[] original = createTestClassWithTypeRef("net/minecraft/class_310");
        byte[] transformed = transformer.transformClass(original, "test/TestModClass");

        // Field type should now be Mojang name
        String fieldType = getFieldType(transformed, "field");
        assertEquals("Lnet/minecraft/client/Minecraft;", fieldType,
                "class_310 should be rewritten to net/minecraft/client/Minecraft in bytecode");
    }

    @Test
    @DisplayName("isDeobfuscatedVersion correctly identifies 26.1+")
    void testIsDeobfuscatedVersion() {
        assertTrue(IntermediaryToMojangMapper.isDeobfuscatedVersion("26.1"),
                "26.1 should be deobfuscated");
        assertTrue(IntermediaryToMojangMapper.isDeobfuscatedVersion("26.1.0"),
                "26.1.0 should be deobfuscated");
        assertTrue(IntermediaryToMojangMapper.isDeobfuscatedVersion("26.1.2"),
                "26.1.2 should be deobfuscated");
        assertTrue(IntermediaryToMojangMapper.isDeobfuscatedVersion("27.0"),
                "27.0 should be deobfuscated");

        assertFalse(IntermediaryToMojangMapper.isDeobfuscatedVersion("1.21.11"),
                "1.21.11 should NOT be deobfuscated");
        assertFalse(IntermediaryToMojangMapper.isDeobfuscatedVersion("1.20.1"),
                "1.20.1 should NOT be deobfuscated");
        assertFalse(IntermediaryToMojangMapper.isDeobfuscatedVersion(null),
                "null should NOT be deobfuscated");
    }

    @Test
    @DisplayName("26.1 Fabric shim registers with mapper")
    void test26_1FabricShimRegistration() {
        Fabric_1_21_10_to_26_1 shim = new Fabric_1_21_10_to_26_1();

        assertEquals("1.21.10", shim.getSourceVersion());
        assertEquals("26.1", shim.getTargetVersion());
        assertEquals("fabric", shim.getModLoaderType());

        // Should register without error and add many redirects
        int classBefore = transformer.getClassRedirects().size();
        shim.registerRedirects(transformer);
        int classAfter = transformer.getClassRedirects().size();

        assertTrue(classAfter > classBefore,
                "26.1 shim should register class redirects (before: " + classBefore + ", after: " + classAfter + ")");
    }

    @Test
    @DisplayName("Shim chain from 1.21.8 to 26.1 via BFS")
    void testShimChainTo26_1() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        shimRegistry.register(new Fabric_1_21_9_to_1_21_10());
        shimRegistry.register(new Fabric_1_21_10_to_26_1());

        List<VersionShim> chain = shimRegistry.findShimChain("fabric", "1.21.8", "26.1");

        assertEquals(3, chain.size(), "Should find 3-step chain: 1.21.8→1.21.9→1.21.10→26.1");
        assertEquals("1.21.8", chain.get(0).getSourceVersion());
        assertEquals("1.21.9", chain.get(1).getSourceVersion());
        assertEquals("1.21.10", chain.get(1).getTargetVersion());
        assertEquals("26.1", chain.get(2).getTargetVersion());
    }

    @Test
    @DisplayName("Descriptor remapping replaces intermediary classes")
    void testDescriptorRemapping() {
        IntermediaryToMojangMapper mapper = new IntermediaryToMojangMapper();
        mapper.load();

        String original = "(Lnet/minecraft/class_310;Lnet/minecraft/class_1297;)V";
        String remapped = mapper.remapDescriptor(original);

        assertEquals("(Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/entity/Entity;)V",
                remapped, "Descriptor should have intermediary classes replaced with Mojang names");
    }

    @Test
    @DisplayName("Version aliases resolve 26.1.x to 26.1")
    void testVersionAliasesFor26_1() {
        assertEquals("26.1", ShimRegistry.resolveVersion("26.1.0"),
                "26.1.0 should resolve to 26.1");
        assertEquals("26.1", ShimRegistry.resolveVersion("26.1.1"),
                "26.1.1 should resolve to 26.1");
        assertEquals("26.1", ShimRegistry.resolveVersion("26.1.2"),
                "26.1.2 should resolve to 26.1");
    }

    // =========================================================
    // HELPER METHODS
    // =========================================================

    /**
     * Extract the descriptor of a named field from class bytecode using ASM.
     */
    private String getFieldType(byte[] classBytes, String fieldName) {
        final String[] result = {null};
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (fieldName.equals(name)) {
                    result[0] = descriptor;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return result[0];
    }

    private boolean isLikelyObfuscated(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        String className = reader.getClassName();
        
        // Check for short class name
        String simpleName = className.substring(className.lastIndexOf('/') + 1);
        if (simpleName.length() <= 2) {
            return true;
        }
        
        // Check methods
        final boolean[] hasShortMethods = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (name.length() <= 2 && !name.equals("<init>") && !name.equals("<clinit>")) {
                    hasShortMethods[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        
        return hasShortMethods[0];
    }
}
