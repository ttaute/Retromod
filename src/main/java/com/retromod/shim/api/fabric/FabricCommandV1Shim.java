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
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the removed Fabric <b>command v1</b> API
 * ({@code net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback}) - server
 * command registration - onto the surviving {@code command/v2} callback.
 *
 * <p>Compat-audit gap: ~38 mods are sole-blocked on {@code command/v1/
 * CommandRegistrationCallback}. The v1 SAM is the 2-arg
 * {@code register(CommandDispatcher dispatcher, boolean dedicated)}; v2's is the
 * 3-arg {@code register(CommandDispatcher, CommandBuildContext, Commands$CommandSelection)}.
 * Because the descriptors differ, this is a <b>lambda trap</b>, not a redirect: a
 * mod registers its handler as {@code CommandRegistrationCallback.EVENT.register(
 * (dispatcher, dedicated) -> …)}, and that lambda's {@code invokedynamic} hard-codes
 * the v1 2-arg SAM. A class redirect onto v2 would make {@code LambdaMetafactory}
 * fail at link time. So we keep the v1 SAM alive.</p>
 *
 * <h2>Why this one is clean</h2>
 * Unlike the item-group bridge, the v1 lambda body operates only on the
 * <b>brigadier</b> {@code CommandDispatcher} (a {@code com.mojang.brigadier} type
 * that is never remapped) plus a primitive boolean - there is no Fabric parameter
 * object whose methods changed. So nothing inside the handler needs adapting; we
 * only need the SAM to keep linking and the {@code EVENT} to actually fire.
 *
 * <h2>Two moving parts</h2>
 * <ol>
 *   <li><b>Synthetic v1 interface.</b> Injected with the 2-arg {@code register} SAM
 *       <i>and</i> the static {@code EVENT} field the mod reads. Its {@code <clinit>}
 *       calls {@link com.retromod.shim.api.fabric.embedded.CommandRegistrationV1Bridge#installEvent(Class)}
 *       to populate {@code EVENT}. The old class name is redirected onto it.</li>
 *   <li><b>Reflective bridge.</b> {@code installEvent} builds a real Fabric
 *       {@code Event} (so {@code EVENT.register(handler)} works natively) and
 *       registers one forwarder on the live {@code command/v2} {@code EVENT} that
 *       replays each command build onto the v1 handlers, mapping the v2
 *       {@code CommandSelection} down to the v1 {@code dedicated} boolean.</li>
 * </ol>
 *
 * <h2>Why the synthetic SAM uses brigadier (not intermediary) types</h2>
 * Synthetic classes are injected raw (not re-remapped). On a 26.1 host the mod's
 * lambda has its {@code invokedynamic} SAM rewritten to Mojang/brigadier names in
 * the same pass that redirects the v1 class onto our synthetic; for the link to
 * succeed the synthetic's {@code register} must declare the {@code brigadier}
 * {@code CommandDispatcher}. That type is identical on the intermediary (CLI/audit)
 * and Mojang (runtime) paths, so a single descriptor serves both.
 *
 * <p><b>STATUS - authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.145.4+26.1.2}. A 26.1 launch that registers a command through
 * a v1 mod is still required.</p>
 */
public class FabricCommandV1Shim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /** Removed v1 callback (the form deleted well before 26.1). */
    private static final String OLD_V1  = "net/fabricmc/fabric/api/command/v1/CommandRegistrationCallback";
    /** Our kept SAM interface (own namespace). */
    private static final String NEW_SAM = "com/retromod/generated/legacycmd/CommandRegistrationCallbackV1";
    /** Reflective runtime bridge embedded into the mod jar. */
    private static final String BRIDGE  = "com/retromod/shim/api/fabric/embedded/CommandRegistrationV1Bridge";

    private static final String EVENT   = "net/fabricmc/fabric/api/event/Event";
    private static final String L_EVENT = "L" + EVENT + ";";
    /** v1 SAM: register(CommandDispatcher, boolean). Brigadier type is stable across paths. */
    private static final String SAM_DESC = "(Lcom/mojang/brigadier/CommandDispatcher;Z)V";

    @Override public String getShimName() { return "Fabric command v1 (CommandRegistrationCallback) bridge"; }
    @Override public String getSourceVersion() { return "0.25.0"; }
    @Override public String getTargetVersion() { return "0.100.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Deliberately NOT host-gated (unlike the other 26.1 API bridges): command/v1
        // was removed from the Fabric API back in the 1.19 era, so on EVERY modern host
        // the old class is genuinely gone (no live API to hijack - pitfall #9 doesn't
        // apply), and the replacement command/v2 exists on 1.19.1+. The synthetic SAM
        // uses only brigadier + fabric Event types (identical in intermediary and
        // Mojang namespaces), and the bridge resolves v2 purely by Fabric API names -
        // so the bridge is namespace-agnostic and extends coverage to pre-26.1 hosts.
        // (0) Embed the reflective bridge (java.* only) so the synthetic <clinit> resolves it.
        transformer.registerEmbeddedShim(BRIDGE.replace('/', '.'));

        // (1) Keep the v1 SAM + EVENT alive and redirect the old name onto it.
        transformer.registerSyntheticClass(NEW_SAM, generateSamInterface());
        transformer.registerClassRedirect(OLD_V1, NEW_SAM);

        LOGGER.info("[Retromod] Fabric command v1 bridge - kept CommandRegistrationCallback "
                + "register(dispatcher, dedicated) SAM + EVENT wired to command/v2 "
                + "(STATUS: needs in-game verification)");
    }

    /**
     * Synthetic v1 {@code CommandRegistrationCallback}: a functional interface with
     * the 2-arg {@code register} SAM, the static {@code EVENT} field the mod reads,
     * and a {@code <clinit>} that fills {@code EVENT} from the reflective bridge.
     */
    static byte[] generateSamInterface() { // package-private for FabricCommandV1ShimTest bytecode checks
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                NEW_SAM, null, "java/lang/Object", null);

        // public static final Event EVENT;  (implicitly so on an interface, set in <clinit>)
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "EVENT", L_EVENT, null, null).visitEnd();

        // void register(CommandDispatcher dispatcher, boolean dedicated);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "register", SAM_DESC, null, null)
                .visitEnd();

        // static { EVENT = (Event) CommandRegistrationV1Bridge.installEvent(NEW_SAM.class); }
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(Type.getObjectType(NEW_SAM)); // the interface being initialized - legal in its own <clinit>
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "installEvent",
                "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, EVENT);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, NEW_SAM, "EVENT", L_EVENT);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0); // recomputed by COMPUTE_MAXS
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
