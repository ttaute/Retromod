/*
 * Retromod - ASM synthetics for classes NeoForge deleted that old mods still reference.
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.shim.forge;

import java.util.function.Supplier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.retromod.core.RetromodTransformer;

import static org.objectweb.asm.Opcodes.*;

/**
 * ASM-generated bridges for classes NeoForge DELETED that 1.20.1-1.21.x Forge/NeoForge mods
 * still reference. Each is registered with {@link RetromodTransformer#registerSyntheticClass}
 * and embedded per-mod, under a unique {@code com/retromod/embedded/<mod>/} package, by
 * {@link com.retromod.core.SyntheticEmbedder} (split-package-safe; see CLAUDE.md #13).
 *
 * <ul>
 *   <li><b>FMLJavaModLoadingContext (#85)</b> - deleted in the Forge→NeoForge split.
 *       {@code get().getModEventBus()} bridges to
 *       {@code ModLoadingContext.get().getActiveContainer().getEventBus()} (verified against
 *       NeoForge {@code loader-11.0.13.jar}).</li>
 *   <li><b>DeferredSpawnEggItem (#52)</b> - deleted in 26.x; 26.2's {@code SpawnEggItem} takes
 *       only {@code Item.Properties} (the entity type moved to a data component). The bridge
 *       extends {@code SpawnEggItem} and its old {@code (Supplier,int,int,Item.Properties)}
 *       ctor best-effort sets the type via {@code Item.Properties.spawnEgg(EntityType)} (the
 *       supplier is resolved in a try/catch, so registration-ordering can't crash construct)
 *       then calls super.</li>
 * </ul>
 *
 * <p>Each synthetic is registered ONLY when its original class is absent on the host - on an
 * older host where the class still exists, embedding the bridge would shadow the real one.
 */
public final class ForgeNeoForgeSynthetics {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private ForgeNeoForgeSynthetics() {}

    private static final String FMLJMLC = "net/neoforged/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String DSEI = "net/neoforged/neoforge/common/DeferredSpawnEggItem";
    private static final String SPAWN_EGG = "net/minecraft/world/item/SpawnEggItem";
    private static final String PROPS = "net/minecraft/world/item/Item$Properties";
    private static final String L_PROPS = "Lnet/minecraft/world/item/Item$Properties;";
    private static final String ENTITY_TYPE = "net/minecraft/world/entity/EntityType";

    /** Register the bridges whose original class is absent on the host (runtime entry points). */
    public static void register(RetromodTransformer t) {
        registerIfAbsent(t, FMLJMLC, ForgeNeoForgeSynthetics::generateFmlJavaModLoadingContext);
        registerIfAbsent(t, DSEI, ForgeNeoForgeSynthetics::generateDeferredSpawnEggItem);
    }

    /**
     * Register both bridges unconditionally - for the OFFLINE CLI/AOT path, where the host MC
     * isn't on the classpath so the absence check can't run. The caller version-gates (only
     * targets where the originals are gone, i.e. 26.1+) and loader-gates (NeoForge mods).
     */
    public static void registerAll(RetromodTransformer t) {
        try {
            t.registerSyntheticClass(FMLJMLC, generateFmlJavaModLoadingContext());
            t.registerSyntheticClass(DSEI, generateDeferredSpawnEggItem());
        } catch (Throwable e) {
            LOGGER.warn("Could not register Forge/NeoForge synthetics (offline): {}", e.toString());
        }
    }

    private static void registerIfAbsent(RetromodTransformer t, String internalName, Supplier<byte[]> gen) {
        if (classExists(internalName)) return; // original still present - don't shadow it
        try {
            t.registerSyntheticClass(internalName, gen.get());
            LOGGER.info("Registered synthetic bridge for deleted class {}", internalName);
        } catch (Throwable e) {
            LOGGER.warn("Could not register synthetic {}: {}", internalName, e.toString());
        }
    }

    private static boolean classExists(String internalName) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ForgeNeoForgeSynthetics.class.getClassLoader();
            Class.forName(internalName.replace('/', '.'), false, cl);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** A ClassWriter whose frame computation never needs the (absent) MC class hierarchy. */
    private static ClassWriter newWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
    }

    // ── #85: FMLJavaModLoadingContext.get().getModEventBus() ────────────────────────────────
    static byte[] generateFmlJavaModLoadingContext() {
        ClassWriter cw = newWriter();
        cw.visit(V17, ACC_PUBLIC | ACC_FINAL, FMLJMLC, null, "java/lang/Object", null);
        String selfDesc = "L" + FMLJMLC + ";";
        cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "INSTANCE", selfDesc, null, null).visitEnd();

        MethodVisitor ci = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        ci.visitCode();
        ci.visitTypeInsn(NEW, FMLJMLC);
        ci.visitInsn(DUP);
        ci.visitMethodInsn(INVOKESPECIAL, FMLJMLC, "<init>", "()V", false);
        ci.visitFieldInsn(PUTSTATIC, FMLJMLC, "INSTANCE", selfDesc);
        ci.visitInsn(RETURN);
        ci.visitMaxs(0, 0);
        ci.visitEnd();

        MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode();
        c.visitVarInsn(ALOAD, 0);
        c.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();

        MethodVisitor g = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "get", "()" + selfDesc, null, null);
        g.visitCode();
        g.visitFieldInsn(GETSTATIC, FMLJMLC, "INSTANCE", selfDesc);
        g.visitInsn(ARETURN);
        g.visitMaxs(0, 0);
        g.visitEnd();

        MethodVisitor m = cw.visitMethod(ACC_PUBLIC, "getModEventBus",
                "()Lnet/neoforged/bus/api/IEventBus;", null, null);
        m.visitCode();
        m.visitMethodInsn(INVOKESTATIC, "net/neoforged/fml/ModLoadingContext", "get",
                "()Lnet/neoforged/fml/ModLoadingContext;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "net/neoforged/fml/ModLoadingContext", "getActiveContainer",
                "()Lnet/neoforged/fml/ModContainer;", false);
        m.visitMethodInsn(INVOKEVIRTUAL, "net/neoforged/fml/ModContainer", "getEventBus",
                "()Lnet/neoforged/bus/api/IEventBus;", false);
        m.visitInsn(ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    // ── #52: DeferredSpawnEggItem extends SpawnEggItem ──────────────────────────────────────
    static byte[] generateDeferredSpawnEggItem() {
        ClassWriter cw = newWriter();
        cw.visit(V17, ACC_PUBLIC, DSEI, null, SPAWN_EGG, null);

        // private static Item.Properties retromod$resolve(Item.Properties props, Supplier type):
        //   try { return props.spawnEgg((EntityType) type.get()); } catch (Throwable t) { return props; }
        MethodVisitor r = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, "retromod$resolve",
                "(" + L_PROPS + "Ljava/util/function/Supplier;)" + L_PROPS, null, null);
        r.visitCode();
        Label tryStart = new Label(), tryEnd = new Label(), handler = new Label();
        r.visitTryCatchBlock(tryStart, tryEnd, handler, "java/lang/Throwable");
        r.visitLabel(tryStart);
        r.visitVarInsn(ALOAD, 0); // props
        r.visitVarInsn(ALOAD, 1); // supplier
        r.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Supplier", "get",
                "()Ljava/lang/Object;", true);
        r.visitTypeInsn(CHECKCAST, ENTITY_TYPE);
        r.visitMethodInsn(INVOKEVIRTUAL, PROPS, "spawnEgg",
                "(L" + ENTITY_TYPE + ";)" + L_PROPS, false);
        r.visitInsn(ARETURN);
        r.visitLabel(tryEnd);
        r.visitLabel(handler);
        r.visitInsn(POP); // discard the Throwable
        r.visitVarInsn(ALOAD, 0); // props
        r.visitInsn(ARETURN);
        r.visitMaxs(0, 0);
        r.visitEnd();

        // public <init>(Supplier type, int bg, int hl, Item.Properties props):
        //   super(retromod$resolve(props, type));
        MethodVisitor c = cw.visitMethod(ACC_PUBLIC, "<init>",
                "(Ljava/util/function/Supplier;II" + L_PROPS + ")V", null, null);
        c.visitCode();
        c.visitVarInsn(ALOAD, 0); // this
        c.visitVarInsn(ALOAD, 4); // props
        c.visitVarInsn(ALOAD, 1); // supplier
        c.visitMethodInsn(INVOKESTATIC, DSEI, "retromod$resolve",
                "(" + L_PROPS + "Ljava/util/function/Supplier;)" + L_PROPS, false);
        c.visitMethodInsn(INVOKESPECIAL, SPAWN_EGG, "<init>", "(" + L_PROPS + ")V", false);
        c.visitInsn(RETURN);
        c.visitMaxs(0, 0);
        c.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
