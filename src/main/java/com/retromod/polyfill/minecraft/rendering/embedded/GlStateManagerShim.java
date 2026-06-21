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
 * In MC 1.17+, Mojang moved from the fixed-function OpenGL pipeline to a
 * shader-based rendering system. GlStateManager's static methods were either:
 * - Moved to RenderSystem (color, blend, depth test)
 * - Made into no-ops (alpha test, lighting, texture enable, matrix ops)
 *
 * This shim uses MethodHandles for efficient delegation to RenderSystem,
 * falling back to no-ops if RenderSystem is unavailable (e.g., dedicated server).
 * Reflection via MethodHandle is used to avoid compile-time dependency on MC classes.
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
            // Running on dedicated server or RenderSystem not available - all ops become no-ops
        }

        SET_SHADER_COLOR = setShaderColor;
        ENABLE_BLEND = enableBlend;
        DISABLE_BLEND = disableBlend;
        ENABLE_DEPTH_TEST = enableDepthTest;
        DISABLE_DEPTH_TEST = disableDepthTest;
    }

    // =========================================================================
    // Methods that delegate to RenderSystem
    // =========================================================================

    /**
     * Sets the shader color. Delegates to RenderSystem.setShaderColor().
     * Old mods called GlStateManager.color(r, g, b, a) which mapped to
     * glColor4f in the fixed-function pipeline.
     */
    public static void color(float r, float g, float b, float a) {
        if (SET_SHADER_COLOR != null) {
            try {
                SET_SHADER_COLOR.invokeExact(r, g, b, a);
            } catch (Throwable t) {
                // Fallback no-op
            }
        }
    }

    /**
     * Enables blending. Delegates to RenderSystem.enableBlend().
     */
    public static void enableBlend() {
        if (ENABLE_BLEND != null) {
            try {
                ENABLE_BLEND.invokeExact();
            } catch (Throwable t) {
                // Fallback no-op
            }
        }
    }

    /**
     * Disables blending. Delegates to RenderSystem.disableBlend().
     */
    public static void disableBlend() {
        if (DISABLE_BLEND != null) {
            try {
                DISABLE_BLEND.invokeExact();
            } catch (Throwable t) {
                // Fallback no-op
            }
        }
    }

    /**
     * Enables depth testing. Delegates to RenderSystem.enableDepthTest().
     */
    public static void enableDepthTest() {
        if (ENABLE_DEPTH_TEST != null) {
            try {
                ENABLE_DEPTH_TEST.invokeExact();
            } catch (Throwable t) {
                // Fallback no-op
            }
        }
    }

    /**
     * Disables depth testing. Delegates to RenderSystem.disableDepthTest().
     */
    public static void disableDepthTest() {
        if (DISABLE_DEPTH_TEST != null) {
            try {
                DISABLE_DEPTH_TEST.invokeExact();
            } catch (Throwable t) {
                // Fallback no-op
            }
        }
    }

    // =========================================================================
    // No-op methods: fixed-function features removed in core GL profile
    // =========================================================================

    /**
     * No-op. Alpha testing was removed from the core GL profile.
     * The shader pipeline handles alpha via discard in fragment shaders.
     */
    public static void enableAlpha() {
        // No-op: alpha test not available in core profile
    }

    /** No-op. See {@link #enableAlpha()}. */
    public static void disableAlpha() {
        // No-op: alpha test not available in core profile
    }

    /**
     * No-op. Fixed-function lighting was removed from the core GL profile.
     * Modern MC uses shader-based lighting exclusively.
     */
    public static void enableLighting() {
        // No-op: fixed-function lighting not available in core profile
    }

    /** No-op. See {@link #enableLighting()}. */
    public static void disableLighting() {
        // No-op: fixed-function lighting not available in core profile
    }

    /**
     * No-op. Texture enable/disable was a fixed-function concept.
     * In the shader pipeline, textures are always bound via samplers.
     */
    public static void enableTexture() {
        // No-op: texture enable/disable not meaningful in shader pipeline
    }

    /** No-op. See {@link #enableTexture()}. */
    public static void disableTexture() {
        // No-op: texture enable/disable not meaningful in shader pipeline
    }

    // =========================================================================
    // No-op methods: matrix stack replaced by PoseStack / GuiGraphics
    // =========================================================================

    /**
     * No-op. The fixed-function matrix stack (glPushMatrix) was removed.
     * Modern MC uses PoseStack for transforms and GuiGraphics for 2D rendering.
     * Mods that called pushMatrix/popMatrix around GUI rendering will still
     * work because the surrounding code is also being transformed.
     */
    public static void pushMatrix() {
        // No-op: use PoseStack instead
    }

    /** No-op. See {@link #pushMatrix()}. */
    public static void popMatrix() {
        // No-op: use PoseStack instead
    }

    /**
     * No-op. glTranslated was removed from core profile.
     * Modern equivalent: PoseStack.translate(x, y, z).
     */
    public static void translate(double x, double y, double z) {
        // No-op: use PoseStack.translate instead
    }

    /**
     * No-op. glRotatef was removed from core profile.
     * Modern equivalent: PoseStack.mulPose(Axis.of(x,y,z).rotationDegrees(angle)).
     */
    public static void rotate(float angle, float x, float y, float z) {
        // No-op: use PoseStack.mulPose instead
    }

    /**
     * No-op. glScaled was removed from core profile.
     * Modern equivalent: PoseStack.scale(x, y, z).
     */
    public static void scale(double x, double y, double z) {
        // No-op: use PoseStack.scale instead
    }
}
