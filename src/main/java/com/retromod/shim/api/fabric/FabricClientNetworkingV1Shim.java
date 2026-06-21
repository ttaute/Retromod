/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the removed Fabric <b>client networking v1</b> raw-channel API
 * (the {@code Identifier}-keyed, raw-{@code FriendlyByteBuf} form used before the
 * 1.20.5 typed-payload rewrite) onto 26.1's {@code CustomPacketPayload} API.
 *
 * <p>Top remaining Fabric-API compat-audit gap: ~29 mods are sole-blocked on
 * {@code ClientPlayNetworking$PlayChannelHandler} (advanced-loot-info,
 * chiseled-bookshelf-visualizer, the cobblemon add-on family, exposure, …).</p>
 *
 * <h2>Three moving parts</h2>
 * <ol>
 *   <li><b>SAM interface preserved.</b> Mod code passes its handler as a lambda
 *       ({@code (mc, listener, buf, sender) -> …}); the lambda's
 *       {@code invokedynamic} hard-references {@code PlayChannelHandler} as the
 *       functional interface, and ASM cannot rewrite the SAM <i>name</i> a lambda
 *       implements. So we keep the interface alive: inject a synthetic
 *       {@code PlayChannelHandler} with the 4-arg {@code receive} SAM and redirect
 *       the old class name onto it.</li>
 *   <li><b>Static call sites redirected.</b> The old static entrypoints
 *       ({@code registerGlobalReceiver}, {@code registerReceiver}, {@code send},
 *       {@code canSend}) are redirected to {@link
 *       com.retromod.shim.api.fabric.embedded.ClientPlayNetworkingV1Bridge}, which
 *       does the raw↔typed translation reflectively at runtime.</li>
 *   <li><b>{@code getSender()} left ALONE.</b> Unchanged in 26.1 - redirecting it
 *       would break a working call.</li>
 * </ol>
 *
 * <h2>Why the synthetic SAM uses Mojang descriptors</h2>
 * Synthetic classes are injected <b>raw</b> (not re-remapped). The mod's lambda is
 * intermediary at ship time, but on a real 26.1 host the intermediary→Mojang remap
 * rewrites the lambda's {@code invokedynamic} SAM + impl-method descriptors to
 * Mojang names in the same pass that class-redirects {@code PlayChannelHandler →}
 * our synthetic. So for {@code LambdaMetafactory} to link at runtime the synthetic
 * {@code receive} must declare the <b>Mojang</b> param types (CLAUDE.md synthetics
 * rule). The audit/CLI path only checks class resolution (both intermediary and
 * Mojang names are in its manifest), so the SAM descriptor doesn't affect the audit.
 *
 * <h2>Redirect-key descriptors</h2>
 * Redirect keys are matched against the call-site descriptor <i>after</i> the
 * outer ClassRemapper runs, so the {@code Identifier}/{@code FriendlyByteBuf}
 * params appear as intermediary ({@code class_2960}/{@code class_2540}) on the CLI
 * path and Mojang on the runtime path - both variants are registered. The
 * {@code PlayChannelHandler} param is a class redirect (same name on both paths),
 * so only the one synthetic name is needed there.
 *
 * <p><b>STATUS - authored, not yet runtime-verified.</b> Contracts checked against
 * {@code minecraft-26.1.2-client} + {@code fabric-api-0.145.4+26.1.2}; the bridge
 * fails soft (logged no-op) on any reflective miss. A real 26.1 client launch with
 * a custom-packet round-trip is still required. See {@code docs/dev/cpn-v1-bridge-design.md}.</p>
 */
public class FabricClientNetworkingV1Shim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    // Old API surface (the form removed by 26.1). ClientPlayNetworking itself still
    // exists in 26.1 - we do NOT class-redirect it, only redirect its removed methods.
    private static final String OLD_CPN = "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking";
    private static final String OLD_SAM = OLD_CPN + "$PlayChannelHandler";

    // Our kept SAM interface (own namespace) + the reflective bridge.
    private static final String NEW_SAM = "com/retromod/generated/legacynet/LegacyPlayChannelHandler";
    private static final String BRIDGE  = "com/retromod/shim/api/fabric/embedded/ClientPlayNetworkingV1Bridge";
    private static final String L_NEW_SAM = "L" + NEW_SAM + ";";

    // Redirect-key MC param types - intermediary (CLI/audit) + Mojang (runtime).
    private static final String ID_INT  = "Lnet/minecraft/class_2960;";
    private static final String ID_MOJ  = "Lnet/minecraft/resources/Identifier;";
    private static final String BUF_INT = "Lnet/minecraft/class_2540;";
    private static final String BUF_MOJ = "Lnet/minecraft/network/FriendlyByteBuf;";

    // Synthetic SAM receive() param types - MOJANG ONLY (raw-injected; must resolve
    // at the 26.1 runtime where the remapped lambda links against it).
    private static final String MC_MOJ       = "Lnet/minecraft/client/Minecraft;";
    private static final String LISTENER_MOJ = "Lnet/minecraft/client/multiplayer/ClientPacketListener;";
    private static final String SENDER       = "Lnet/fabricmc/fabric/api/networking/v1/PacketSender;";

    // Bridge method descriptors - all params Object (verifier-safe reference widening),
    // so one bridge descriptor serves every intermediary/Mojang call-site variant.
    private static final String D_REGISTER = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
    private static final String D_SEND     = "(Ljava/lang/Object;Ljava/lang/Object;)V";
    private static final String D_CANSEND  = "(Ljava/lang/Object;)Z";

    @Override public String getShimName() { return "Fabric ClientPlayNetworking v1 (raw-channel) bridge"; }
    @Override public String getSourceVersion() { return "0.50.0"; }
    @Override public String getTargetVersion() { return "0.100.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 26.1+ hosts ONLY (pitfall #9). The synthetic SAM deliberately declares
        // MOJANG param types (see class javadoc) - on a pre-26.1 intermediary runtime
        // those don't resolve, so the redirect would turn a clean
        // ClassNotFoundException into a LambdaConversionException at link time. The
        // bridge's reflective contracts are also only verified against 26.1.
        if (!com.retromod.core.RetromodVersion.isUnobfuscatedTarget(
                com.retromod.core.RetromodVersion.TARGET_MC_VERSION)) {
            LOGGER.debug("[Retromod] client-networking v1 bridge skipped (host {} < 26.1)",
                    com.retromod.core.RetromodVersion.TARGET_MC_VERSION);
            return;
        }

        // (0) The reflective bridge is a real compiled class (java.* only) - embed it
        //     into every transformed mod jar so the redirected static calls resolve.
        transformer.registerEmbeddedShim(BRIDGE.replace('/', '.'));

        // (1) Keep the SAM interface alive and redirect the old name onto it.
        transformer.registerSyntheticClass(NEW_SAM, generateSamInterface());
        transformer.registerClassRedirect(OLD_SAM, NEW_SAM);

        // (2) Redirect the removed static entrypoints to the reflective bridge.
        //     The call is INVOKESTATIC and the bridge target is static, so the opcode
        //     is preserved (devirtualize=false). The PlayChannelHandler param is
        //     L_NEW_SAM on both paths (class redirect), the Identifier/Buf params vary.
        for (String idT : new String[]{ID_INT, ID_MOJ}) {
            redirect(transformer, "registerGlobalReceiver",
                    "(" + idT + L_NEW_SAM + ")Z", "registerGlobalReceiver", D_REGISTER);
            redirect(transformer, "registerReceiver",
                    "(" + idT + L_NEW_SAM + ")Z", "registerReceiver", D_REGISTER);
            redirect(transformer, "canSend", "(" + idT + ")Z", "canSend", D_CANSEND);
            for (String bufT : new String[]{BUF_INT, BUF_MOJ}) {
                redirect(transformer, "send", "(" + idT + bufT + ")V", "send", D_SEND);
            }
        }

        LOGGER.info("[Retromod] Fabric client-networking v1 bridge - kept PlayChannelHandler SAM "
                + "+ redirected register/send/canSend to reflective raw-bytes bridge "
                + "(getSender unchanged; STATUS: needs in-game verification)");
    }

    private static void redirect(RetromodTransformer t, String oldName, String oldDesc,
                                 String bridgeName, String bridgeDesc) {
        // devirtualize=false: source is already INVOKESTATIC; the redirect just swaps
        // owner/name/desc and keeps the opcode (RetromodTransformer line ~1785).
        t.registerMethodRedirect(OLD_CPN, oldName, oldDesc, BRIDGE, bridgeName, bridgeDesc, false);
    }

    /**
     * Synthetic {@code PlayChannelHandler} replacement - a functional interface with
     * the 4-arg {@code receive} SAM declared in <b>Mojang</b> types (see class javadoc).
     * {@code FriendlyByteBuf} and {@code PacketSender} round out the old signature.
     */
    private static byte[] generateSamInterface() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                NEW_SAM, null, "java/lang/Object", null);
        String receiveDesc = "(" + MC_MOJ + LISTENER_MOJ + BUF_MOJ + SENDER + ")V";
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "receive", receiveDesc, null, null)
                .visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
