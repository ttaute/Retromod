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
 * Bridges the removed Fabric <b>{@code EntityModelLayerRegistry}</b> API onto the
 * surviving {@code ModelLayerRegistry}. Audit gap: ~18 mods sole-blocked on
 * {@code EntityModelLayerRegistry$TexturedModelDataProvider}.
 *
 * <p>The provider SAM renamed ({@code createModelData} → {@code createLayerDefinition})
 * — a lambda trap — but its return type is unchanged: Yarn {@code TexturedModelData}
 * is Mojang {@code LayerDefinition} (intermediary {@code class_5607}), so the harvest
 * already fixes the lambda body. We keep a synthetic {@code EntityModelLayerRegistry}
 * holder and a {@code TexturedModelDataProvider} SAM at the old names; the holder's
 * {@code registerModelLayer} forwards to
 * {@link com.retromod.shim.api.fabric.embedded.EntityModelLayerRegistryBridge}, which
 * wraps the old provider as a new one and registers it.</p>
 *
 * <p>Replaces the previous {@code EntityModelLayerRegistry → ModelLayerRegistry}
 * class redirect (a latent lambda trap) in {@code Fabric_1_21_11_to_26_1}.</p>
 *
 * <p><b>STATUS — authored, not yet runtime-verified.</b> Checked against
 * {@code fabric-api-0.141.1} / {@code 0.145.4+26.1.2}. A 26.1 client launch with a
 * custom-entity-model mod is still required.</p>
 */
public class FabricEntityModelLayerShim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String GEN = "com/retromod/generated/legacymodellayer/";
    private static final String HOLDER   = GEN + "EntityModelLayerRegistry";
    private static final String PROVIDER = GEN + "TexturedModelDataProvider";
    private static final String BRIDGE = "com/retromod/shim/api/fabric/embedded/EntityModelLayerRegistryBridge";

    private static final String OLD = "net/fabricmc/fabric/api/client/rendering/v1/EntityModelLayerRegistry";

    private static final String MODEL_LAYER_LOCATION = "net/minecraft/client/model/geom/ModelLayerLocation";
    private static final String LAYER_DEFINITION = "net/minecraft/client/model/geom/builders/LayerDefinition";
    private static final String REGISTER_DESC = "(L" + MODEL_LAYER_LOCATION + ";L" + PROVIDER + ";)V";
    private static final String SAM_DESC = "()L" + LAYER_DEFINITION + ";";

    @Override public String getShimName() { return "Fabric EntityModelLayerRegistry bridge"; }
    @Override public String getSourceVersion() { return "0.40.0"; }
    @Override public String getTargetVersion() { return "0.145.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 26.1+ hosts ONLY (pitfall #9). Pre-26.1, EntityModelLayerRegistry is still
        // ALIVE in the Fabric API — redirecting it would hijack a working API and wire
        // it to ModelLayerRegistry, which doesn't exist there. The synthetics also
        // declare Mojang-named MC types (ModelLayerLocation, LayerDefinition),
        // unresolvable on an intermediary runtime.
        if (!com.retromod.core.RetromodVersion.isUnobfuscatedTarget(
                com.retromod.core.RetromodVersion.TARGET_MC_VERSION)) {
            LOGGER.debug("[Retromod] EntityModelLayerRegistry bridge skipped (host {} < 26.1 — old API still present)",
                    com.retromod.core.RetromodVersion.TARGET_MC_VERSION);
            return;
        }

        transformer.registerEmbeddedShim(BRIDGE.replace('/', '.'));

        transformer.registerSyntheticClass(HOLDER, generateHolder());
        transformer.registerSyntheticClass(PROVIDER, generateProvider());

        transformer.registerClassRedirect(OLD, HOLDER);
        transformer.registerClassRedirect(OLD + "$TexturedModelDataProvider", PROVIDER);

        LOGGER.info("[Retromod] Fabric EntityModelLayerRegistry bridge — kept registerModelLayer + "
                + "TexturedModelDataProvider SAM (createModelData) wired to ModelLayerRegistry "
                + "(STATUS: needs in-game verification)");
    }

    /**
     * Holder with {@code static void registerModelLayer(ModelLayerLocation, TexturedModelDataProvider)}
     * forwarding to the bridge. No static fields, so no {@code <clinit>}.
     */
    static byte[] generateHolder() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                HOLDER, null, "java/lang/Object", null);

        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "registerModelLayer", REGISTER_DESC, null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0); // ModelLayerLocation
        m.visitVarInsn(Opcodes.ALOAD, 1); // provider
        m.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "registerModelLayer",
                "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Synthetic {@code TexturedModelDataProvider}: SAM {@code createModelData() : LayerDefinition}. */
    static byte[] generateProvider() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                PROVIDER, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "createModelData", SAM_DESC, null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
