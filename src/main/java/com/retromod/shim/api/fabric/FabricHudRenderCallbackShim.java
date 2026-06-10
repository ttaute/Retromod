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
 * Bridges the removed Fabric <b>{@code HudRenderCallback}</b> (single global HUD
 * render event) onto 26.1's layered {@code hud/HudElementRegistry}. Audit gap:
 * ~6 mods sole-blocked on it (HUD overlay mods).
 *
 * <p>26.1 deleted {@code HudRenderCallback} outright; the replacement is named
 * {@code HudElement}s in a registry. The SAM also differs
 * ({@code onHudRender} → {@code extractRenderState}), so a class redirect alone
 * would lambda-trap. The synthetic injected here uses the <b>extends + default
 * bridge</b> shape (same as the ScreenEvents render→extract synthetics):
 *
 * <pre>
 * interface HudRenderCallback extends hud.HudElement {
 *     void onHudRender(GuiGraphicsExtractor, DeltaTracker);            // old SAM (only abstract)
 *     default void extractRenderState(g, d) { onHudRender(g, d); }     // new SAM bridged
 *     Event&lt;HudRenderCallback&gt; EVENT = …;                         // wired in &lt;clinit&gt;
 * }
 * </pre>
 *
 * Old lambdas link against {@code onHudRender} (still the single abstract method),
 * every listener <i>is</i> a {@code HudElement}, and
 * {@link com.retromod.shim.api.fabric.embedded.HudRenderCallbackBridge} registers
 * the event's combined invoker as one {@code HudElement} layer.
 *
 * <p><b>Gated 26.1+</b> (pitfall #9): {@code HudRenderCallback} still exists and
 * works on pre-26.1 hosts — and the synthetic's Mojang/26.1 descriptors
 * ({@code GuiGraphicsExtractor}) wouldn't resolve there anyway.</p>
 *
 * <p><b>STATUS — authored, not yet runtime-verified</b> (needs an in-game 26.1 HUD
 * overlay check alongside the other bridges).</p>
 */
public class FabricHudRenderCallbackShim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String OLD = "net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback";
    private static final String SYNTH = "com/retromod/generated/legacyhud/HudRenderCallback";
    private static final String BRIDGE = "com/retromod/shim/api/fabric/embedded/HudRenderCallbackBridge";
    private static final String HUD_ELEMENT = "net/fabricmc/fabric/api/client/rendering/v1/hud/HudElement";

    private static final String EVENT   = "net/fabricmc/fabric/api/event/Event";
    private static final String L_EVENT = "L" + EVENT + ";";
    /** Old SAM in post-remap (Mojang + 26.1 class-move) form: class_332→GuiGraphicsExtractor, class_9779→DeltaTracker. */
    private static final String SAM_DESC =
            "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";

    @Override public String getShimName() { return "Fabric HudRenderCallback → HudElementRegistry bridge"; }
    @Override public String getSourceVersion() { return "0.40.0"; }
    @Override public String getTargetVersion() { return "0.145.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 26.1+ hosts ONLY (pitfall #9): HudRenderCallback is alive pre-26.1, and the
        // synthetic's GuiGraphicsExtractor descriptor only resolves on 26.1.
        if (!com.retromod.core.RetromodVersion.isUnobfuscatedTarget(
                com.retromod.core.RetromodVersion.TARGET_MC_VERSION)) {
            LOGGER.debug("[Retromod] HudRenderCallback bridge skipped (host {} < 26.1 — old API still present)",
                    com.retromod.core.RetromodVersion.TARGET_MC_VERSION);
            return;
        }

        transformer.registerEmbeddedShim(BRIDGE.replace('/', '.'));
        transformer.registerSyntheticClass(SYNTH, generateInterface());
        transformer.registerClassRedirect(OLD, SYNTH);

        LOGGER.info("[Retromod] Fabric HudRenderCallback bridge — kept onHudRender SAM "
                + "(extends HudElement, default extractRenderState forwards) + EVENT attached to "
                + "HudElementRegistry (STATUS: needs in-game verification)");
    }

    /**
     * Synthetic {@code HudRenderCallback}: extends the new {@code HudElement};
     * abstract old SAM + default new-SAM forwarder + {@code EVENT} wired in
     * {@code <clinit>} through the reflective bridge.
     */
    static byte[] generateInterface() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                SYNTH, null, "java/lang/Object", new String[]{HUD_ELEMENT});

        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "EVENT", L_EVENT, null, null).visitEnd();

        // The old SAM — stays the single abstract method so lambdas keep linking.
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "onHudRender", SAM_DESC, null, null)
                .visitEnd();

        // default void extractRenderState(g, d) { onHudRender(g, d); }
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "extractRenderState", SAM_DESC, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, SYNTH, "onHudRender", SAM_DESC, true);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // static { EVENT = (Event) HudRenderCallbackBridge.installEvent(HudRenderCallback.class); }
        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitLdcInsn(org.objectweb.asm.Type.getObjectType(SYNTH));
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "installEvent",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        clinit.visitTypeInsn(Opcodes.CHECKCAST, EVENT);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, SYNTH, "EVENT", L_EVENT);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
