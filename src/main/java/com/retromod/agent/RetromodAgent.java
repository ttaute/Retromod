/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.agent;

import com.retromod.core.RetromodTransformer;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent for Retromod, attached via -javaagent:retromod-agent.jar to enable
 * bytecode transformation outside the mod loaders.
 */
public class RetromodAgent {

    public static Instrumentation instrumentation;

    /** Attached at JVM startup via -javaagent. */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[Retromod Agent] Installing class transformer (premain)");

        instrumentation = inst;
        inst.addTransformer(RetromodTransformer.getInstance(), true);
        System.setProperty("retromod.agent.class", RetromodAgent.class.getName());

        System.out.println("[Retromod Agent] Transformer installed successfully");
    }

    /** Attached to a running JVM via the Attach API. */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("[Retromod Agent] Installing class transformer (agentmain)");

        instrumentation = inst;
        inst.addTransformer(RetromodTransformer.getInstance(), true);
        // classes loaded before we attached need a retransform pass
        retransformLoadedClasses(inst);
        System.setProperty("retromod.agent.class", RetromodAgent.class.getName());

        System.out.println("[Retromod Agent] Transformer installed and classes retransformed");
    }

    private static void retransformLoadedClasses(Instrumentation inst) {
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            String name = clazz.getName();
            if (shouldRetransform(name) && inst.isModifiableClass(clazz)) {
                try {
                    inst.retransformClasses(clazz);
                } catch (Exception e) {
                    System.err.println("[Retromod Agent] Failed to retransform: " + name);
                }
            }
        }
    }

    /** True for mod code; false for JDK, MC core, loaders, and Retromod itself. */
    private static boolean shouldRetransform(String className) {
        if (className.startsWith("java.") ||
            className.startsWith("javax.") ||
            className.startsWith("sun.") ||
            className.startsWith("jdk.")) {
            return false;
        }
        if (className.startsWith("net.minecraft.") ||
            className.startsWith("com.mojang.")) {
            return false;
        }
        if (className.startsWith("net.fabricmc.loader.") ||
            className.startsWith("net.minecraftforge.fml.") ||
            className.startsWith("cpw.mods.")) {
            return false;
        }
        if (className.startsWith("com.retromod.")) {
            return false;
        }
        return true;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static boolean isActive() {
        return instrumentation != null;
    }
}
