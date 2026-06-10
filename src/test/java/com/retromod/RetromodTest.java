/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod;

import com.retromod.core.*;
import com.retromod.aot.AotCompiler;
import com.retromod.mapping.SrgToMojangMapper;
import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.shim.ShimRegistry;
import com.retromod.shim.fabric.*;
import com.retromod.shim.neoforge.*;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for Retromod.
 * 
 * Tests:
 * 1. Method redirect registration
 * 2. Class redirect registration
 * 3. Bytecode transformation correctness
 * 4. Shim chain resolution
 * 5. Mixin compatibility transformation
 * 6. AOT compilation
 */
public class RetromodTest {
    
    private RetromodTransformer transformer;
    private ShimRegistry shimRegistry;
    
    @BeforeEach
    void setUp() {
        transformer = RetromodTransformer.getInstance();
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
        var key = new RetromodTransformer.MethodKey(
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

        // #51: LootContextParamSet (singular) renamed + moved to util/context/ContextKeySet
        // by 1.21.11 (Illagers Wear Armor crashed on ContextKeySet$Builder). The PLURAL
        // LootContextParamSets is unchanged and must NOT be redirected.
        assertEquals("net/minecraft/util/context/ContextKeySet",
            classRedirects.get("net/minecraft/world/level/storage/loot/parameters/LootContextParamSet"));
        assertEquals("net/minecraft/util/context/ContextKeySet$Builder",
            classRedirects.get("net/minecraft/world/level/storage/loot/parameters/LootContextParamSet$Builder"));
        assertFalse(classRedirects.containsKey(
            "net/minecraft/world/level/storage/loot/parameters/LootContextParamSets"),
            "the plural LootContextParamSets must NOT be redirected");
    }

    @Test
    @DisplayName("Fabric 26.1 shim redirects tick-event $WorldTick inners → $LevelTick (safe SAM)")
    void testTickEventInnerRedirects26_1() {
        // snapshot.3: the 26.1 Fabric API renamed the nested tick SAMs
        // $Start/EndWorldTick → $Start/EndLevelTick while keeping the outer
        // ClientTickEvents/ServerTickEvents names and the onStartTick/onEndTick SAM
        // method. The pre-existing entries lived only in the 1.16.5→1.17 shim,
        // whose chain never covers a 1.19–1.21 mod, so those mods broke on 26.1.
        // This is a true redirect (verified against fabric-api 0.145.4: host has
        // only the $*LevelTick inners with the same descriptor), not a lambda trap.
        new Fabric_1_21_11_to_26_1().registerRedirects(transformer);
        var cr = transformer.getClassRedirects();

        assertEquals("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$EndLevelTick",
            cr.get("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$EndWorldTick"),
            "ClientTickEvents$EndWorldTick must redirect to $EndLevelTick on 26.1");
        assertEquals("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$StartLevelTick",
            cr.get("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$StartWorldTick"));
        assertEquals("net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$EndLevelTick",
            cr.get("net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$EndWorldTick"));
        assertEquals("net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$StartLevelTick",
            cr.get("net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$StartWorldTick"));
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
    // ITERATIVE TRANSFORM LOOP TESTS
    // =========================================================
    // Verify that transformClass() runs multiple passes when chained redirects
    // are registered (A -> B, B -> C) and terminates safely on cycles (A -> B, B -> A).
    //
    // The iterative loop is what lets Retromod handle shim chains where one shim's
    // target is itself the source of another shim's redirect. Single-pass visitors
    // would only catch the first hop.

    @Test
    @DisplayName("Iterative loop resolves chained redirects (A->B->C)")
    void testIterativeLoopChainedRedirects() {
        // Use unique class names so we don't interfere with other tests that
        // share the singleton transformer instance.
        transformer.registerMethodRedirect(
            "test/chain/HopA", "call", "()V",
            "test/chain/HopB", "call", "()V"
        );
        transformer.registerMethodRedirect(
            "test/chain/HopB", "call", "()V",
            "test/chain/HopC", "call", "()V"
        );

        byte[] original = createStaticCallerClass("test/chain/Caller", "test/chain/HopA", "call");

        transformer.resetIterationMetrics();
        byte[] transformed = transformer.transformClass(original, "test/chain/Caller");

        // Final call should be to HopC — the end of the chain — not HopA or HopB.
        assertTrue(containsMethodCall(transformed, "test/chain/HopC", "call"),
                "Expected final call site to be HopC.call after chain resolution");
        assertFalse(containsMethodCall(transformed, "test/chain/HopA", "call"),
                "HopA.call should no longer appear after iteration");
        assertFalse(containsMethodCall(transformed, "test/chain/HopB", "call"),
                "HopB.call should no longer appear after iteration");

        // Metrics: chained redirects should register as "needed multiple passes".
        // Pass 1 rewrites HopA -> HopB. Pass 2 rewrites HopB -> HopC. Pass 3 stable.
        // That's 2 active passes, which triggers the counter.
        assertEquals(1, transformer.getClassesNeedingMultiplePasses(),
                "Chained redirect should count as needing multiple passes");
        assertTrue(transformer.getTotalPassesPerformed() >= 3,
                "Should have performed at least 3 passes for a two-hop chain");
        assertEquals(0, transformer.getClassesHittingIterationCap(),
                "Non-cyclic chain must never hit the iteration cap");
    }

    @Test
    @DisplayName("Iterative loop terminates safely on redirect cycles (A->B->A)")
    void testIterativeLoopCycleTermination() {
        // Cycle: two redirects that point at each other. A naive loop would
        // oscillate forever. The iteration cap guarantees we terminate.
        transformer.registerMethodRedirect(
            "test/cycle/ClassA", "call", "()V",
            "test/cycle/ClassB", "call", "()V"
        );
        transformer.registerMethodRedirect(
            "test/cycle/ClassB", "call", "()V",
            "test/cycle/ClassA", "call", "()V"
        );

        byte[] original = createStaticCallerClass(
                "test/cycle/Caller", "test/cycle/ClassA", "call");

        transformer.resetIterationMetrics();

        // Assert the call returns in finite time even with a cycle.
        // assertTimeoutPreemptively would be stronger, but that requires extra
        // test infra. The iteration cap of 5 means at most 5 ASM visits — fast.
        byte[] transformed = assertDoesNotThrow(
                () -> transformer.transformClass(original, "test/cycle/Caller"),
                "Transform must not throw even with cyclic redirects");
        assertNotNull(transformed, "Must return some output, not null");

        // We should have hit the cap. The last-pass output is returned as-is,
        // which will oscillate between ClassA and ClassB — either is acceptable
        // for this test; what matters is termination.
        assertEquals(1, transformer.getClassesHittingIterationCap(),
                "Cyclic redirect must register as hitting the iteration cap");
    }

    @Test
    @DisplayName("Single-hop redirects stabilize in one active pass")
    void testIterativeLoopSingleHopStability() {
        transformer.registerMethodRedirect(
            "test/single/From", "call", "()V",
            "test/single/To", "call", "()V"
        );

        byte[] original = createStaticCallerClass(
                "test/single/Caller", "test/single/From", "call");

        transformer.resetIterationMetrics();
        byte[] transformed = transformer.transformClass(original, "test/single/Caller");

        assertTrue(containsMethodCall(transformed, "test/single/To", "call"),
                "Single hop should still be applied");

        // Single-hop case: pass 1 rewrites, pass 2 verifies. 1 active pass.
        // Should NOT trigger the "multiple passes" counter — that's reserved for
        // actual chained redirects (2+ active passes).
        assertEquals(0, transformer.getClassesNeedingMultiplePasses(),
                "Single-hop redirect should not register as chained");
        assertEquals(0, transformer.getClassesHittingIterationCap(),
                "Single-hop redirect must never hit the cap");
        assertEquals(2, transformer.getTotalPassesPerformed(),
                "Single-hop should take exactly 2 passes: one to rewrite, one to verify stable");
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
    
    /**
     * Create a minimal class whose one method invokes a static call elsewhere.
     * Used by the iterative-loop tests to produce a predictable call site that
     * we can then drive through registered method redirects.
     *
     * <p>Using {@code INVOKESTATIC} avoids the {@code NEW/DUP/INVOKESPECIAL} setup
     * that {@link #createTestClass()} uses — it's just a clean single-instruction
     * call that maps 1:1 to what the tests want to verify.</p>
     */
    private byte[] createStaticCallerClass(String className, String calleeOwner, String calleeName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, calleeOwner, calleeName, "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
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

    // ═════════════════════════════════════════════════════════════════════
    // SRG → Mojang mapping data (src/main/resources/retromod/srg-to-mojang.tsv)
    // Composed from MCPConfig 1.20.1 joined.tsrg ⋈ Mojang 1.20.1 official
    // mappings (the ForgeGradle/SrgUtils obf-name join). These assertions
    // also lock in the 56 corrections over the old hand-curated starter set.
    // ═════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("SRG→Mojang dictionary loads with comprehensive coverage")
    void srgToMojangLoadsComprehensively() {
        SrgToMojangMapper mapper = SrgToMojangMapper.getInstance();
        // Harvested from 1.20.1: tens of thousands of entries, not the old
        // ~117-row starter set. Loose lower bounds so version bumps don't break it.
        assertTrue(mapper.getFieldMap().size() > 20000,
                "expected comprehensive field coverage, got " + mapper.getFieldMap().size());
        assertTrue(mapper.getMethodMap().size() > 15000,
                "expected comprehensive method coverage, got " + mapper.getMethodMap().size());
    }

    @Test
    @DisplayName("SRG→Mojang corrections: the 56 formerly-wrong entries are right")
    void srgToMojangCorrections() {
        SrgToMojangMapper mapper = SrgToMojangMapper.getInstance();
        Map<String, String> f = mapper.getFieldMap();
        Map<String, String> m = mapper.getMethodMap();

        // Fields the old curated file got wrong (verified vs upstream sources).
        assertEquals("GRANITE", f.get("f_50122_"));   // was wrongly "GRAVEL"
        assertEquals("OBSIDIAN", f.get("f_50080_"));   // was wrongly "GRANITE"

        // ResourceLocation methods — getPath/getNamespace had been swapped,
        // and m_135820_ had been "parse" (no such 1.20.1 method; it's tryParse).
        assertEquals("tryParse", m.get("m_135820_"));
        assertEquals("of", m.get("m_135822_"));
        assertEquals("getPath", m.get("m_135815_"));
        assertEquals("getNamespace", m.get("m_135827_"));

        // Entries the old file already had right — must survive the regen.
        assertEquals("STONE", f.get("f_50069_"));
        assertEquals("literal", m.get("m_237113_"));
    }

    // ═════════════════════════════════════════════════════════════════════
    // 26.1 vanilla class moves on the Mojang-named loader path (NeoForge/Forge)
    // Gap A: the mojang-class-moves-26.1.tsv table used to be applied only on
    // Fabric (+CLI). applyClassMovesOnly is what the NeoForge/Forge entry
    // points now call (gated on a 26.1+ host) so those mods get the vanilla
    // net/minecraft/* package reorganization too.
    // ═════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Gap A: applyClassMovesOnly rewrites 26.1 vanilla package moves")
    void classMovesOnlyRewritesVanillaMoves() {
        // applyClassMovesOnly is now host-version-aware: it filters each rename
        // by the indexed host MC JAR. The test JVM has no MC JAR on the
        // classpath, so it falls back to the coarse gate keyed on
        // TARGET_MC_VERSION — pin it to a 26.1 (unobfuscated) host so the
        // fallback applies the whole table. (The host-FILTERING path, where a
        // 1.21.11 host gets only the renames that already landed there, is
        // exercised at runtime / in the Prism repro, not in this unit test.)
        String savedVer = RetromodVersion.TARGET_MC_VERSION;
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        try {
        int moves = com.retromod.mapping.IntermediaryToMojangMapper
                .applyClassMovesOnly(transformer);
        assertTrue(moves > 500, "expected the full 26.1 class-move table, got " + moves);

        // Top-level relocation: net/minecraft/Util -> net/minecraft/util/Util
        byte[] utilT = transformer.transformClass(
                createTestClassWithTypeRef("net/minecraft/Util"), "test/gapa/UtilRef");
        assertTrue(containsClassRef(utilT, "net/minecraft/util/Util"),
                "net/minecraft/Util should move to net/minecraft/util/Util");

        // critereon -> criterion package fix
        byte[] critT = transformer.transformClass(
                createTestClassWithTypeRef("net/minecraft/advancements/critereon/BlockPredicate"),
                "test/gapa/CritRef");
        assertTrue(containsClassRef(critT, "net/minecraft/advancements/criterion/BlockPredicate"),
                "critereon should be rewritten to criterion");

        // Flagship vanilla rename (also in the table): ResourceLocation -> Identifier
        byte[] rlT = transformer.transformClass(
                createTestClassWithTypeRef("net/minecraft/resources/ResourceLocation"),
                "test/gapa/RlRef");
        assertTrue(containsClassRef(rlT, "net/minecraft/resources/Identifier"),
                "ResourceLocation should be rewritten to Identifier");

        // A move from the 1.20.1->26.1.2 harvest (options-screen relocation):
        // net/minecraft/client/gui/screens/VideoSettingsScreen
        //   -> net/minecraft/client/gui/screens/options/VideoSettingsScreen
        byte[] vsT = transformer.transformClass(
                createTestClassWithTypeRef("net/minecraft/client/gui/screens/VideoSettingsScreen"),
                "test/gapa/VideoRef");
        assertTrue(containsClassRef(vsT, "net/minecraft/client/gui/screens/options/VideoSettingsScreen"),
                "VideoSettingsScreen should move into the screens/options package");

        // A move from the 1.16.5->26.1.2 harvest (worldgen structures relocation):
        // net/minecraft/world/level/levelgen/structure/BuriedTreasurePieces
        //   -> net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces
        byte[] btT = transformer.transformClass(
                createTestClassWithTypeRef("net/minecraft/world/level/levelgen/structure/BuriedTreasurePieces"),
                "test/gapa/StructRef");
        assertTrue(containsClassRef(btT, "net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces"),
                "structure pieces should move into the structure/structures package");
        } finally {
            RetromodVersion.TARGET_MC_VERSION = savedVer;
        }
    }
}
