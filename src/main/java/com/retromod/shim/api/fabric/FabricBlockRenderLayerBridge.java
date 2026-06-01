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
 * Fabric {@code BlockRenderLayerMap} soft-fail bridge — the single most-referenced
 * removed Fabric API class in the snapshot.2 audit (~628 refs).
 *
 * <h2>What it was</h2>
 * {@code net/fabricmc/fabric/api/blockrenderlayer/v1/BlockRenderLayerMap} was the
 * registry a mod used to declare a block/fluid's render layer:
 * {@code BlockRenderLayerMap.INSTANCE.putBlock(myBlock, RenderType.cutout())}.
 * Fabric removed it in the 26.1-era API (render layers are now resolved from the
 * block model / block data, not a side registry), so every content mod that set a
 * non-solid render layer hits {@code NoClassDefFoundError} on it.
 *
 * <h2>Bridge</h2>
 * Inject a no-op stand-in interface (in our own {@code com/retromod/generated/
 * legacyfabric/*} namespace) with the same {@code INSTANCE} field and the
 * {@code putBlock/putBlocks/putItem/putItems/putFluid/putFluids} methods, all
 * inert, plus a concrete impl. The class redirect itself lives in
 * {@code mojang-class-moves-26.1.tsv} so it's applied through the same
 * {@code isUnobfuscatedTarget}-gated path as the rest of the 26.1 moves — i.e.
 * only on a 26.1+ host where the real class is actually gone. A pre-26.1 host
 * (where {@code BlockRenderLayerMap} still ships in the Fabric API) is left
 * untouched.
 *
 * <h2>Type discipline</h2>
 * Synthetics are injected into the mod jar <b>raw</b> (not re-remapped), so the
 * stub's method descriptors use the <b>26.1 Mojang</b> types the mod's call sites
 * resolve to after Retromod's intermediary→Mojang + class-move passes — most
 * notably {@code RenderType} at its moved 26.1 path
 * {@code client/renderer/rendertype/RenderType}. Using intermediary names here
 * would make the call descriptors mismatch the stub at runtime
 * (a load-passes-but-call-fails false win).
 *
 * <h2>Functional trade-off</h2>
 * The {@code put*} calls become no-ops, so a block that needed CUTOUT/TRANSLUCENT
 * renders SOLID (transparent parts look opaque). The mod LOADS and everything
 * else works — strictly better than the current crash.
 */
public class FabricBlockRenderLayerBridge implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    static final String SELF = "com/retromod/generated/legacyfabric/BlockRenderLayerMap";
    private static final String IMPL = "com/retromod/generated/legacyfabric/BlockRenderLayerMapImpl";
    private static final String L_SELF = "L" + SELF + ";";

    // 26.1 Mojang internal names (verified against the 26.1.2 client jar).
    private static final String L_BLOCK = "Lnet/minecraft/world/level/block/Block;";
    private static final String L_RENDERTYPE = "Lnet/minecraft/client/renderer/rendertype/RenderType;";
    private static final String L_ITEM = "Lnet/minecraft/world/item/Item;";
    private static final String L_FLUID = "Lnet/minecraft/world/level/material/Fluid;";

    @Override public String getShimName() { return "Fabric BlockRenderLayerMap Soft-Fail"; }
    @Override public String getSourceVersion() { return "0.50.0"; }
    @Override public String getTargetVersion() { return "0.100.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerSyntheticClass(SELF, generateInterface());
        transformer.registerSyntheticClass(IMPL, generateImpl());
        // NB: the class redirect BlockRenderLayerMap → SELF lives in
        // mojang-class-moves-26.1.tsv (26.1-gated), not here, so pre-26.1 hosts
        // that still have the real class aren't redirected.
        LOGGER.debug("[Retromod] BlockRenderLayerMap bridge — injected no-op stand-in");
    }

    // ─── interface with INSTANCE + put* methods ───────────────────────────────
    private static byte[] generateInterface() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                SELF, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "INSTANCE", L_SELF, null, null).visitEnd();

        abstractMethod(cw, "putBlock",  "(" + L_BLOCK + L_RENDERTYPE + ")V");
        abstractMethod(cw, "putBlocks", "(" + L_RENDERTYPE + "[" + L_BLOCK + ")V");
        abstractMethod(cw, "putItem",   "(" + L_ITEM + L_RENDERTYPE + ")V");
        abstractMethod(cw, "putItems",  "(" + L_RENDERTYPE + "[" + L_ITEM + ")V");
        abstractMethod(cw, "putFluid",  "(" + L_FLUID + L_RENDERTYPE + ")V");
        abstractMethod(cw, "putFluids", "(" + L_RENDERTYPE + "[" + L_FLUID + ")V");

        // <clinit> { INSTANCE = new BlockRenderLayerMapImpl(); }
        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitTypeInsn(Opcodes.NEW, IMPL);
        clinit.visitInsn(Opcodes.DUP);
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, IMPL, "<init>", "()V", false);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, SELF, "INSTANCE", L_SELF);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── concrete no-op impl ──────────────────────────────────────────────────
    private static byte[] generateImpl() {
        ClassWriter cw = newClassWriter();
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                IMPL, null, "java/lang/Object", new String[]{SELF});
        emitDefaultCtor(cw);
        noopVoid(cw, "putBlock",  "(" + L_BLOCK + L_RENDERTYPE + ")V");
        noopVoid(cw, "putBlocks", "(" + L_RENDERTYPE + "[" + L_BLOCK + ")V");
        noopVoid(cw, "putItem",   "(" + L_ITEM + L_RENDERTYPE + ")V");
        noopVoid(cw, "putItems",  "(" + L_RENDERTYPE + "[" + L_ITEM + ")V");
        noopVoid(cw, "putFluid",  "(" + L_FLUID + L_RENDERTYPE + ")V");
        noopVoid(cw, "putFluids", "(" + L_RENDERTYPE + "[" + L_FLUID + ")V");
        cw.visitEnd();
        return cw.toByteArray();
    }

    // ─── ASM helpers (same shape as the other soft-fail bridges) ──────────────
    private static ClassWriter newClassWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String t1, String t2) {
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

    private static void noopVoid(ClassWriter cw, String name, String desc) {
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
        m.visitCode();
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
    }
}
