/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.rendering.embedded;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reimplementation of GlStateManager that delegates to the modern RenderSystem API.
 *
 * MC 1.17+ replaced the fixed-function pipeline with a shader-based one. Color/blend/depth
 * calls map to RenderSystem; fixed-function calls (alpha test, lighting, texture enable, matrix
 * ops) become no-ops. Delegation goes through MethodHandles to avoid a compile-time dependency on
 * MC classes, falling back to no-ops when RenderSystem is absent (dedicated server).
 */
public class GlStateManagerShim {

    private static final MethodHandle SET_SHADER_COLOR;
    private static final MethodHandle ENABLE_BLEND;
    private static final MethodHandle DISABLE_BLEND;
    private static final MethodHandle ENABLE_DEPTH_TEST;
    private static final MethodHandle DISABLE_DEPTH_TEST;

    static {
        MethodHandle setShaderColor = null;
        MethodHandle enableBlend = null;
        MethodHandle disableBlend = null;
        MethodHandle enableDepthTest = null;
        MethodHandle disableDepthTest = null;

        try {
            Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            setShaderColor = lookup.findStatic(renderSystem, "setShaderColor",
                MethodType.methodType(void.class, float.class, float.class, float.class, float.class));
            enableBlend = lookup.findStatic(renderSystem, "enableBlend",
                MethodType.methodType(void.class));
            disableBlend = lookup.findStatic(renderSystem, "disableBlend",
                MethodType.methodType(void.class));
            enableDepthTest = lookup.findStatic(renderSystem, "enableDepthTest",
                MethodType.methodType(void.class));
            disableDepthTest = lookup.findStatic(renderSystem, "disableDepthTest",
                MethodType.methodType(void.class));
        } catch (Exception e) {
            // RenderSystem absent (dedicated server); all ops become no-ops
        }

        SET_SHADER_COLOR = setShaderColor;
        ENABLE_BLEND = enableBlend;
        DISABLE_BLEND = disableBlend;
        ENABLE_DEPTH_TEST = enableDepthTest;
        DISABLE_DEPTH_TEST = disableDepthTest;
    }

    /** Maps the old GlStateManager.color (glColor4f) to RenderSystem.setShaderColor. */
    public static void color(float r, float g, float b, float a) {
        if (SET_SHADER_COLOR != null) {
            try {
                SET_SHADER_COLOR.invokeExact(r, g, b, a);
            } catch (Throwable t) {
                // no-op
            }
        }
    }

    /** Delegates to RenderSystem.enableBlend. */
    public static void enableBlend() {
        if (ENABLE_BLEND != null) {
            try {
                ENABLE_BLEND.invokeExact();
            } catch (Throwable t) {
                // no-op
            }
        }
    }

    /** Delegates to RenderSystem.disableBlend. */
    public static void disableBlend() {
        if (DISABLE_BLEND != null) {
            try {
                DISABLE_BLEND.invokeExact();
            } catch (Throwable t) {
                // no-op
            }
        }
    }

    /** Delegates to RenderSystem.enableDepthTest. */
    public static void enableDepthTest() {
        if (ENABLE_DEPTH_TEST != null) {
            try {
                ENABLE_DEPTH_TEST.invokeExact();
            } catch (Throwable t) {
                // no-op
            }
        }
    }

    /** Delegates to RenderSystem.disableDepthTest. */
    public static void disableDepthTest() {
        if (DISABLE_DEPTH_TEST != null) {
            try {
                DISABLE_DEPTH_TEST.invokeExact();
            } catch (Throwable t) {
                // no-op
            }
        }
    }

    // no-ops: fixed-function features removed in the core GL profile

    /** No-op. Alpha testing is gone from the core profile (shaders discard instead). */
    public static void enableAlpha() {
    }

    /** No-op. See {@link #enableAlpha()}. */
    public static void disableAlpha() {
    }

    /** No-op. Fixed-function lighting is gone from the core profile. */
    public static void enableLighting() {
    }

    /** No-op. See {@link #enableLighting()}. */
    public static void disableLighting() {
    }

    /** No-op. Texture enable/disable is meaningless in the shader pipeline. */
    public static void enableTexture() {
    }

    /** No-op. See {@link #enableTexture()}. */
    public static void disableTexture() {
    }

    // no-ops: matrix stack replaced by PoseStack / GuiGraphics

    /** No-op. Use PoseStack instead of the fixed-function matrix stack. */
    public static void pushMatrix() {
    }

    /** No-op. See {@link #pushMatrix()}. */
    public static void popMatrix() {
    }

    /** No-op. Use PoseStack.translate instead. */
    public static void translate(double x, double y, double z) {
    }

    /** No-op. Use PoseStack.mulPose instead. */
    public static void rotate(float angle, float x, float y, float z) {
    }

    /** No-op. Use PoseStack.scale instead. */
    public static void scale(double x, double y, double z) {
    }
}
