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
 * Bridges the removed Fabric <b>{@code ServerWorldEvents}</b> lifecycle API
 * (server level load/unload) onto the surviving {@code ServerLevelEvents}. Audit
 * gap: ~21 mods sole-blocked on {@code ServerWorldEvents$Load}.
 *
 * <p>26.1 renamed the holder ({@code ServerWorldEvents} → {@code ServerLevelEvents})
 * and the SAM methods ({@code onWorldLoad}/{@code onWorldUnload} →
 * {@code onLevelLoad}/{@code onLevelUnload}). The SAM-name change makes a class
 * redirect a lambda trap, so we keep the old SAM names on synthetic interfaces and
 * a synthetic holder, wired to the live {@code ServerLevelEvents} fields by
 * reflection ({@link com.retromod.shim.api.fabric.embedded.ServerWorldEventsBridge}).
 * The only parameter change is {@code ServerWorld → ServerLevel}, which the harvest
 * already remaps in the lambda - so unlike the item-group bridge there are no
 * parameter-object method renames.</p>
 *
 * <p>Replaces the previous {@code ServerWorldEvents → ServerLevelEvents} class
 * redirect (a latent lambda trap) that lived in {@code Fabric_1_21_11_to_26_1}.</p>
 *
 * <p><b>STATUS - authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.141.1} / {@code 0.145.4+26.1.2}. A 26.1 launch is still required.</p>
 */
public class FabricServerWorldEventsShim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String GEN = "com/retromod/generated/legacylifecycle/";
    private static final String HOLDER = GEN + "ServerWorldEvents";
    private static final String LOAD   = GEN + "ServerWorldLoad";
    private static final String UNLOAD = GEN + "ServerWorldUnload";
    private static final String BRIDGE = "com/retromod/shim/api/fabric/embedded/ServerWorldEventsBridge";

    private static final String OLD = "net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents";

    private static final String EVENT   = "net/fabricmc/fabric/api/event/Event";
    private static final String L_EVENT = "L" + EVENT + ";";
    // SAM: (MinecraftServer, ServerLevel) - MinecraftServer is stable, ServerLevel is the harvested param.
    private static final String SAM_DESC =
            "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;)V";

    @Override public String getShimName() { return "Fabric ServerWorldEvents lifecycle bridge"; }
    @Override public String getSourceVersion() { return "0.40.0"; }
    @Override public String getTargetVersion() { return "0.145.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 26.1+ hosts ONLY (pitfall #9). Pre-26.1, ServerWorldEvents is still ALIVE in
        // the Fabric API - redirecting it would hijack a working API and wire it to
        // ServerLevelEvents, which doesn't exist there. The synthetic SAM also declares
        // the Mojang-named ServerLevel, unresolvable on an intermediary runtime.
        if (!com.retromod.core.RetromodVersion.isUnobfuscatedTarget(
                com.retromod.core.RetromodVersion.TARGET_MC_VERSION)) {
            LOGGER.debug("[Retromod] ServerWorldEvents bridge skipped (host {} < 26.1 - old API still present)",
                    com.retromod.core.RetromodVersion.TARGET_MC_VERSION);
            return;
        }

        transformer.registerEmbeddedShim(BRIDGE.replace('/', '.'));

        transformer.registerSyntheticClass(HOLDER, generateHolder());
        transformer.registerSyntheticClass(LOAD, generateLoad());
        transformer.registerSyntheticClass(UNLOAD, generateUnload());

        transformer.registerClassRedirect(OLD, HOLDER);
        transformer.registerClassRedirect(OLD + "$Load", LOAD);
        transformer.registerClassRedirect(OLD + "$Unload", UNLOAD);

        LOGGER.info("[Retromod] Fabric ServerWorldEvents bridge - kept LOAD/UNLOAD + "
                + "onWorldLoad/onWorldUnload SAMs wired to ServerLevelEvents "
                + "(STATUS: needs in-game verification)");
    }

    /** Holder class with the {@code LOAD}/{@code UNLOAD} fields wired in {@code <clinit>}. */
    static byte[] generateHolder() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                HOLDER, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "LOAD", L_EVENT, null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "UNLOAD", L_EVENT, null, null).visitEnd();

        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "installLoad", "()Ljava/lang/Object;", false);
        clinit.visitTypeInsn(Opcodes.CHECKCAST, EVENT);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, HOLDER, "LOAD", L_EVENT);
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "installUnload", "()Ljava/lang/Object;", false);
        clinit.visitTypeInsn(Opcodes.CHECKCAST, EVENT);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, HOLDER, "UNLOAD", L_EVENT);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Synthetic {@code ServerWorldEvents$Load}: SAM {@code onWorldLoad(MinecraftServer, ServerLevel)}. */
    static byte[] generateLoad() {
        return samInterface(LOAD, "onWorldLoad");
    }

    /** Synthetic {@code ServerWorldEvents$Unload}: SAM {@code onWorldUnload(MinecraftServer, ServerLevel)}. */
    static byte[] generateUnload() {
        return samInterface(UNLOAD, "onWorldUnload");
    }

    private static byte[] samInterface(String internalName, String samName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                internalName, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, samName, SAM_DESC, null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
