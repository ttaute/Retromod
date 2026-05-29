/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric renderer-material API soft-fail bridge.
 *
 * <h2>The problem</h2>
 * Fabric API's {@code fabric-renderer-api-v1} module dropped the entire material
 * subsystem ({@code RenderMaterial}, {@code MaterialFinder}, {@code BlendMode},
 * {@code Renderer}, {@code RendererAccess}) somewhere around the 0.110 relocation
 * — the new API rewrote how custom rendering is wired. Continuity (the connected-
 * textures mod) is the most-affected: every overlay pass calls
 * {@code RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(...).find()}
 * to build a render material, and on a current Fabric API host that chain dies at
 * the first GETSTATIC because the types are gone.
 *
 * <h2>The bridge</h2>
 * Inject empty/no-op replicas of the five removed types — all in our own
 * {@code com/retromod/generated/legacyrenderer/*} namespace — plus class redirects
 * pointing every reference to the old paths at the synthetic. The shapes are the
 * minimum that lets {@code ClassNotFoundException} / {@code NoSuchMethodError}
 * not fire at load time:
 *
 * <ul>
 *   <li>{@code BlendMode} = enum with the original five constants (DEFAULT, SOLID,
 *       CUTOUT, CUTOUT_MIPPED, TRANSLUCENT) so {@code GETSTATIC BlendMode.CUTOUT}
 *       resolves.</li>
 *   <li>{@code RenderMaterial} = empty marker interface. Plain object reference,
 *       no methods of consequence — callers just pass it back into the renderer
 *       which is also a stub.</li>
 *   <li>{@code MaterialFinder} = interface with chainable {@code clear()},
 *       {@code blendMode(BlendMode)} / {@code blendMode(int, BlendMode)} that
 *       return {@code this}, and {@code find()} returning a stub
 *       {@code RenderMaterial}.</li>
 *   <li>{@code Renderer} = interface returning a stub {@code MaterialFinder}.</li>
 *   <li>{@code RendererAccess} = interface with a static {@code INSTANCE} field
 *       initialized to an implementation that returns a stub {@code Renderer}.</li>
 * </ul>
 *
 * <h2>The functional trade-off — explicit</h2>
 * This is intentionally inert: the methods return stubs, so any rendering the
 * mod tries to do through this API is a no-op. Continuity's connected textures
 * won't actually render. But the mod LOADS — it doesn't crash the rest of the
 * client during init — and that's strictly better than the current behavior of
 * "the moment Continuity touches the renderer, you get a NoSuchMethodError + a
 * dead client." Same trade as the Pre1_18_2 BiomeCategory bridge: the feature
 * the mod provides goes dark, but the user gets a launchable game.
 */
public class FabricRendererMaterialBridge implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    // ── Old paths (what the mod's bytecode references) ──
    private static final String OLD_RENDERER_ACCESS  = "net/fabricmc/fabric/api/renderer/v1/RendererAccess";
    private static final String OLD_RENDERER         = "net/fabricmc/fabric/api/renderer/v1/Renderer";
    private static final String OLD_MATERIAL_FINDER  = "net/fabricmc/fabric/api/renderer/v1/material/MaterialFinder";
    private static final String OLD_RENDER_MATERIAL  = "net/fabricmc/fabric/api/renderer/v1/material/RenderMaterial";
    private static final String OLD_BLEND_MODE       = "net/fabricmc/fabric/api/renderer/v1/material/BlendMode";
    // Additional deleted-without-replacement renderer types — same soft-fail
    // treatment so call sites resolve. All five were dropped when the renderer
    // module was rewritten; modern Fabric API has no equivalent at any path.
    private static final String OLD_MESH_BUILDER     = "net/fabricmc/fabric/api/renderer/v1/mesh/MeshBuilder";
    private static final String OLD_RENDER_CONTEXT   = "net/fabricmc/fabric/api/renderer/v1/render/RenderContext";
    private static final String OLD_QUAD_TRANSFORM   = "net/fabricmc/fabric/api/renderer/v1/render/RenderContext$QuadTransform";
    private static final String OLD_SPRITE_FINDER    = "net/fabricmc/fabric/api/renderer/v1/model/SpriteFinder";
    private static final String OLD_FORWARDING_BAKED = "net/fabricmc/fabric/api/renderer/v1/model/ForwardingBakedModel";

    // ── Our stand-ins ──
    private static final String NEW_RENDERER_ACCESS  = "com/retromod/generated/legacyrenderer/RendererAccess";
    private static final String NEW_RENDERER         = "com/retromod/generated/legacyrenderer/Renderer";
    private static final String NEW_MATERIAL_FINDER  = "com/retromod/generated/legacyrenderer/MaterialFinder";
    private static final String NEW_RENDER_MATERIAL  = "com/retromod/generated/legacyrenderer/RenderMaterial";
    private static final String NEW_BLEND_MODE       = "com/retromod/generated/legacyrenderer/BlendMode";
    private static final String IMPL_ACCESS          = "com/retromod/generated/legacyrenderer/RendererAccessImpl";
    private static final String IMPL_RENDERER        = "com/retromod/generated/legacyrenderer/RendererImpl";
    private static final String IMPL_MATERIAL_FINDER = "com/retromod/generated/legacyrenderer/MaterialFinderImpl";
    private static final String IMPL_RENDER_MATERIAL = "com/retromod/generated/legacyrenderer/RenderMaterialImpl";
    private static final String NEW_MESH_BUILDER     = "com/retromod/generated/legacyrenderer/MeshBuilder";
    private static final String NEW_RENDER_CONTEXT   = "com/retromod/generated/legacyrenderer/RenderContext";
    private static final String NEW_QUAD_TRANSFORM   = "com/retromod/generated/legacyrenderer/RenderContext$QuadTransform";
    private static final String NEW_SPRITE_FINDER    = "com/retromod/generated/legacyrenderer/SpriteFinder";
    private static final String NEW_FORWARDING_BAKED = "com/retromod/generated/legacyrenderer/ForwardingBakedModel";

    private static final String L_RENDERER_ACCESS  = "L" + NEW_RENDERER_ACCESS + ";";
    private static final String L_RENDERER         = "L" + NEW_RENDERER + ";";
    private static final String L_MATERIAL_FINDER  = "L" + NEW_MATERIAL_FINDER + ";";
    private static final String L_RENDER_MATERIAL  = "L" + NEW_RENDER_MATERIAL + ";";
    private static final String L_BLEND_MODE       = "L" + NEW_BLEND_MODE + ";";

    /** BlendMode constants in the original 1.16-era order — ordinal must match
     *  what `GETSTATIC BlendMode.<name>` resolved to in old jars. The audit's
     *  five mods that hit BlendMode (Continuity, et al) all use these names. */
    private static final String[] BLEND_MODE_CONSTANTS = {
        "DEFAULT", "SOLID", "CUTOUT", "CUTOUT_MIPPED", "TRANSLUCENT",
    };

    @Override public String getShimName() { return "Fabric Renderer Material API Soft-Fail"; }
    @Override public String getSourceVersion() { return "0.50.0"; }
    @Override public String getTargetVersion() { return "0.110.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Synthetic types
        transformer.registerSyntheticClass(NEW_BLEND_MODE,       generateBlendMode());
        transformer.registerSyntheticClass(NEW_RENDER_MATERIAL,  generateMarkerInterface(NEW_RENDER_MATERIAL));
        transformer.registerSyntheticClass(NEW_MATERIAL_FINDER,  generateMaterialFinderInterface());
        transformer.registerSyntheticClass(NEW_RENDERER,         generateRendererInterface());
        transformer.registerSyntheticClass(NEW_RENDERER_ACCESS,  generateRendererAccessInterface());

        // Concrete no-op implementations (Renderer/MaterialFinder/RenderMaterial/RendererAccess)
        transformer.registerSyntheticClass(IMPL_RENDER_MATERIAL, generateRenderMaterialImpl());
        transformer.registerSyntheticClass(IMPL_MATERIAL_FINDER, generateMaterialFinderImpl());
        transformer.registerSyntheticClass(IMPL_RENDERER,        generateRendererImpl());
        transformer.registerSyntheticClass(IMPL_ACCESS,          generateRendererAccessImpl());

        // Additional marker stubs for the wholly-removed types — these don't
        // need real method shapes, just enough for `Lold/path;` references in
        // mod bytecode to resolve to *something* at class-load time. If a mod
        // actually CALLS methods on them at runtime, an AbstractMethodError
        // happens then — a clean failure mode the user can hit only if the
        // mod's feature actually tries to fire, vs the current "instant crash."
        transformer.registerSyntheticClass(NEW_MESH_BUILDER,     generateMarkerInterface(NEW_MESH_BUILDER));
        transformer.registerSyntheticClass(NEW_RENDER_CONTEXT,   generateMarkerInterface(NEW_RENDER_CONTEXT));
        transformer.registerSyntheticClass(NEW_QUAD_TRANSFORM,   generateMarkerInterface(NEW_QUAD_TRANSFORM));
        transformer.registerSyntheticClass(NEW_SPRITE_FINDER,    generateMarkerInterface(NEW_SPRITE_FINDER));
        // ForwardingBakedModel was a CLASS the user extended — make it a concrete
        // empty class so subclassing still verifies. Not an interface.
        transformer.registerSyntheticClass(NEW_FORWARDING_BAKED, generateEmptyClass(NEW_FORWARDING_BAKED));

        // Class redirects — every reference in mod bytecode to the old paths gets
        // pointed at our synthetics by the ClassRemapper pass.
        transformer.registerClassRedirect(OLD_BLEND_MODE,      NEW_BLEND_MODE);
        transformer.registerClassRedirect(OLD_RENDER_MATERIAL, NEW_RENDER_MATERIAL);
        transformer.registerClassRedirect(OLD_MATERIAL_FINDER, NEW_MATERIAL_FINDER);
        transformer.registerClassRedirect(OLD_RENDERER,        NEW_RENDERER);
        transformer.registerClassRedirect(OLD_RENDERER_ACCESS, NEW_RENDERER_ACCESS);
        transformer.registerClassRedirect(OLD_MESH_BUILDER,     NEW_MESH_BUILDER);
        transformer.registerClassRedirect(OLD_RENDER_CONTEXT,   NEW_RENDER_CONTEXT);
        transformer.registerClassRedirect(OLD_QUAD_TRANSFORM,   NEW_QUAD_TRANSFORM);
        transformer.registerClassRedirect(OLD_SPRITE_FINDER,    NEW_SPRITE_FINDER);
        transformer.registerClassRedirect(OLD_FORWARDING_BAKED, NEW_FORWARDING_BAKED);

        LOGGER.info("[Retromod] Fabric renderer-material bridge — injected {} synthetic types "
                + "+ {} class redirects (soft-fail: mods load, custom rendering inert)", 14, 10);
    }

    private static byte[] generateEmptyClass(String name) {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        emitDefaultCtor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── BlendMode enum ───────────────────────────────────────────────────────
    private static byte[] generateBlendMode() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM,
                NEW_BLEND_MODE,
                "Ljava/lang/Enum<" + L_BLEND_MODE + ">;",
                "java/lang/Enum", null);

        for (String name : BLEND_MODE_CONSTANTS) {
            cw.visitField(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                    name, L_BLEND_MODE, null, null).visitEnd();
        }
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                "$VALUES", "[" + L_BLEND_MODE, null, null).visitEnd();

        // private (String,int) ctor → Enum.<init>
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>",
                "(Ljava/lang/String;I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitVarInsn(Opcodes.ILOAD, 2);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>",
                "(Ljava/lang/String;I)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        emitEnumValues(cw, NEW_BLEND_MODE, L_BLEND_MODE);
        emitEnumValueOf(cw, NEW_BLEND_MODE, L_BLEND_MODE);
        emitEnumClinit(cw, NEW_BLEND_MODE, L_BLEND_MODE, BLEND_MODE_CONSTANTS);

        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── Marker interface (used by RenderMaterial) ────────────────────────────
    private static byte[] generateMarkerInterface(String name) {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                name, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── MaterialFinder interface ─────────────────────────────────────────────
    private static byte[] generateMaterialFinderInterface() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                NEW_MATERIAL_FINDER, null, "java/lang/Object", null);
        // Chainable builder methods — every one returns Lthis;
        abstractMethod(cw, "clear",      "()" + L_MATERIAL_FINDER);
        abstractMethod(cw, "blendMode",  "(" + L_BLEND_MODE + ")" + L_MATERIAL_FINDER);
        abstractMethod(cw, "blendMode",  "(I" + L_BLEND_MODE + ")" + L_MATERIAL_FINDER);
        abstractMethod(cw, "disableAo",  "(IZ)" + L_MATERIAL_FINDER);
        abstractMethod(cw, "disableColorIndex", "(IZ)" + L_MATERIAL_FINDER);
        abstractMethod(cw, "disableDiffuse",    "(IZ)" + L_MATERIAL_FINDER);
        abstractMethod(cw, "emissive",   "(IZ)" + L_MATERIAL_FINDER);
        abstractMethod(cw, "copyFrom",   "(" + L_RENDER_MATERIAL + ")" + L_MATERIAL_FINDER);
        abstractMethod(cw, "find",       "()" + L_RENDER_MATERIAL);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── Renderer interface ───────────────────────────────────────────────────
    private static byte[] generateRendererInterface() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                NEW_RENDERER, null, "java/lang/Object", null);
        abstractMethod(cw, "materialFinder", "()" + L_MATERIAL_FINDER);
        abstractMethod(cw, "materialById",   "(Lnet/minecraft/class_2960;)" + L_RENDER_MATERIAL);
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── RendererAccess interface (with INSTANCE static field) ────────────────
    private static byte[] generateRendererAccessInterface() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                NEW_RENDERER_ACCESS, null, "java/lang/Object", null);

        // public static final RendererAccess INSTANCE = new RendererAccessImpl();
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "INSTANCE", L_RENDERER_ACCESS, null, null).visitEnd();
        abstractMethod(cw, "getRenderer",            "()" + L_RENDERER);
        abstractMethod(cw, "registerRenderer",       "(" + L_RENDERER + ")V");
        abstractMethod(cw, "hasRenderer",            "()Z");

        // <clinit> { INSTANCE = new RendererAccessImpl(); }
        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitTypeInsn(Opcodes.NEW, IMPL_ACCESS);
        clinit.visitInsn(Opcodes.DUP);
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, IMPL_ACCESS, "<init>", "()V", false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, NEW_RENDERER_ACCESS, "INSTANCE", L_RENDERER_ACCESS);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── Concrete impls (all no-op singletons) ────────────────────────────────
    private static byte[] generateRenderMaterialImpl() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                IMPL_RENDER_MATERIAL, null, "java/lang/Object",
                new String[]{NEW_RENDER_MATERIAL});
        emitDefaultCtor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateMaterialFinderImpl() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                IMPL_MATERIAL_FINDER, null, "java/lang/Object",
                new String[]{NEW_MATERIAL_FINDER});
        emitDefaultCtor(cw);

        // Cached stub RenderMaterial we always hand back from find()
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "STUB_MATERIAL", L_RENDER_MATERIAL, null, null).visitEnd();

        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitTypeInsn(Opcodes.NEW, IMPL_RENDER_MATERIAL);
        clinit.visitInsn(Opcodes.DUP);
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, IMPL_RENDER_MATERIAL, "<init>", "()V", false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, IMPL_MATERIAL_FINDER, "STUB_MATERIAL", L_RENDER_MATERIAL);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        // Every chainable: return this
        returnThis(cw, "clear",                "()" + L_MATERIAL_FINDER);
        returnThis(cw, "blendMode",            "(" + L_BLEND_MODE + ")" + L_MATERIAL_FINDER);
        returnThis(cw, "blendMode",            "(I" + L_BLEND_MODE + ")" + L_MATERIAL_FINDER);
        returnThis(cw, "disableAo",            "(IZ)" + L_MATERIAL_FINDER);
        returnThis(cw, "disableColorIndex",    "(IZ)" + L_MATERIAL_FINDER);
        returnThis(cw, "disableDiffuse",       "(IZ)" + L_MATERIAL_FINDER);
        returnThis(cw, "emissive",             "(IZ)" + L_MATERIAL_FINDER);
        returnThis(cw, "copyFrom",             "(" + L_RENDER_MATERIAL + ")" + L_MATERIAL_FINDER);

        // find() returns the cached stub
        MethodVisitor find = cw.visitMethod(Opcodes.ACC_PUBLIC, "find", "()" + L_RENDER_MATERIAL, null, null);
        find.visitCode();
        find.visitFieldInsn(Opcodes.GETSTATIC, IMPL_MATERIAL_FINDER, "STUB_MATERIAL", L_RENDER_MATERIAL);
        find.visitInsn(Opcodes.ARETURN);
        find.visitMaxs(0, 0);
        find.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateRendererImpl() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                IMPL_RENDERER, null, "java/lang/Object",
                new String[]{NEW_RENDERER});
        emitDefaultCtor(cw);

        // materialFinder() → new MaterialFinderImpl()
        MethodVisitor mf = cw.visitMethod(Opcodes.ACC_PUBLIC, "materialFinder",
                "()" + L_MATERIAL_FINDER, null, null);
        mf.visitCode();
        mf.visitTypeInsn(Opcodes.NEW, IMPL_MATERIAL_FINDER);
        mf.visitInsn(Opcodes.DUP);
        mf.visitMethodInsn(Opcodes.INVOKESPECIAL, IMPL_MATERIAL_FINDER, "<init>", "()V", false);
        mf.visitInsn(Opcodes.ARETURN);
        mf.visitMaxs(0, 0);
        mf.visitEnd();

        // materialById(class_2960) → null (a lookup miss is a valid response)
        MethodVisitor mbi = cw.visitMethod(Opcodes.ACC_PUBLIC, "materialById",
                "(Lnet/minecraft/class_2960;)" + L_RENDER_MATERIAL, null, null);
        mbi.visitCode();
        mbi.visitInsn(Opcodes.ACONST_NULL);
        mbi.visitInsn(Opcodes.ARETURN);
        mbi.visitMaxs(0, 0);
        mbi.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateRendererAccessImpl() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                IMPL_ACCESS, null, "java/lang/Object",
                new String[]{NEW_RENDERER_ACCESS});
        emitDefaultCtor(cw);

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "STUB_RENDERER", L_RENDERER, null, null).visitEnd();

        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitTypeInsn(Opcodes.NEW, IMPL_RENDERER);
        clinit.visitInsn(Opcodes.DUP);
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, IMPL_RENDERER, "<init>", "()V", false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, IMPL_ACCESS, "STUB_RENDERER", L_RENDERER);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        MethodVisitor gr = cw.visitMethod(Opcodes.ACC_PUBLIC, "getRenderer", "()" + L_RENDERER, null, null);
        gr.visitCode();
        gr.visitFieldInsn(Opcodes.GETSTATIC, IMPL_ACCESS, "STUB_RENDERER", L_RENDERER);
        gr.visitInsn(Opcodes.ARETURN);
        gr.visitMaxs(0, 0);
        gr.visitEnd();

        // registerRenderer(Renderer) — silently ignore (we always return our stub)
        MethodVisitor rr = cw.visitMethod(Opcodes.ACC_PUBLIC, "registerRenderer",
                "(" + L_RENDERER + ")V", null, null);
        rr.visitCode();
        rr.visitInsn(Opcodes.RETURN);
        rr.visitMaxs(0, 0);
        rr.visitEnd();

        // hasRenderer() — yes, we have a stub
        MethodVisitor hr = cw.visitMethod(Opcodes.ACC_PUBLIC, "hasRenderer", "()Z", null, null);
        hr.visitCode();
        hr.visitInsn(Opcodes.ICONST_1);
        hr.visitInsn(Opcodes.IRETURN);
        hr.visitMaxs(0, 0);
        hr.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── ASM helpers ──────────────────────────────────────────────────────────
    private static ClassWriter newClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String t1, String t2) {
                // Fall back to Object when resolving without MC on the classpath
                // (lets unit tests run without a live runtime).
                try { return super.getCommonSuperClass(t1, t2); }
                catch (Throwable t) { return "java/lang/Object"; }
            }
        };
    }

    private static void abstractMethod(ClassWriter cw, String name, String desc) {
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, desc, null, null).visitEnd();
    }

    private static void emitDefaultCtor(ClassWriter cw) {
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    /** Concrete method whose body is just {@code return this;} — for chainable builders. */
    private static void returnThis(ClassWriter cw, String name, String desc) {
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void emitEnumValues(ClassWriter cw, String self, String lSelf) {
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "values", "()[" + lSelf, null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, self, "$VALUES", "[" + lSelf);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[" + lSelf, "clone", "()Ljava/lang/Object;", false);
        m.visitTypeInsn(Opcodes.CHECKCAST, "[" + lSelf);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void emitEnumValueOf(ClassWriter cw, String self, String lSelf) {
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "valueOf", "(Ljava/lang/String;)" + lSelf, null, null);
        m.visitCode();
        m.visitLdcInsn(org.objectweb.asm.Type.getType(lSelf));
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        m.visitTypeInsn(Opcodes.CHECKCAST, self);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void emitEnumClinit(ClassWriter cw, String self, String lSelf, String[] names) {
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        m.visitCode();
        for (int i = 0; i < names.length; i++) {
            m.visitTypeInsn(Opcodes.NEW, self);
            m.visitInsn(Opcodes.DUP);
            m.visitLdcInsn(names[i]);
            pushInt(m, i);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, self, "<init>", "(Ljava/lang/String;I)V", false);
            m.visitFieldInsn(Opcodes.PUTSTATIC, self, names[i], lSelf);
        }
        pushInt(m, names.length);
        m.visitTypeInsn(Opcodes.ANEWARRAY, self);
        for (int i = 0; i < names.length; i++) {
            m.visitInsn(Opcodes.DUP);
            pushInt(m, i);
            m.visitFieldInsn(Opcodes.GETSTATIC, self, names[i], lSelf);
            m.visitInsn(Opcodes.AASTORE);
        }
        m.visitFieldInsn(Opcodes.PUTSTATIC, self, "$VALUES", "[" + lSelf);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }

    private static void pushInt(MethodVisitor mv, int n) {
        if (n >= -1 && n <= 5) mv.visitInsn(Opcodes.ICONST_0 + n);
        else if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) mv.visitIntInsn(Opcodes.BIPUSH, n);
        else if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) mv.visitIntInsn(Opcodes.SIPUSH, n);
        else mv.visitLdcInsn(n);
    }
}
