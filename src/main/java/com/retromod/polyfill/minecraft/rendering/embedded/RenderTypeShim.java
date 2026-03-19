/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.rendering.embedded;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bridge for RenderType static methods that moved to RenderTypes in MC 26.1.
 *
 * In 26.1, Mojang split the concrete render type accessors out of the abstract
 * RenderType class into a new RenderTypes utility class. Old mods that called:
 *   RenderType.solid()
 *   RenderType.cutout()
 *   RenderType.cutoutMipped()
 *   RenderType.translucent()
 * need to be redirected to RenderTypes.solid(), etc.
 *
 * This shim first tries the new RenderTypes class (26.1+), then falls back to
 * the old RenderType class (pre-26.1) for backwards compatibility with older
 * MC versions where RetroMod might also be running.
 */
public class RenderTypeShim {

    private static final MethodHandle SOLID;
    private static final MethodHandle CUTOUT;
    private static final MethodHandle CUTOUT_MIPPED;
    private static final MethodHandle TRANSLUCENT;

    static {
        MethodHandle solid = null;
        MethodHandle cutout = null;
        MethodHandle cutoutMipped = null;
        MethodHandle translucent = null;

        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            // Try RenderTypes first (26.1+)
            Class<?> renderTypes = null;
            try {
                renderTypes = Class.forName("net.minecraft.client.renderer.RenderTypes");
            } catch (ClassNotFoundException e) {
                // Fall back to RenderType (pre-26.1)
                renderTypes = Class.forName("net.minecraft.client.renderer.RenderType");
            }

            Class<?> renderTypeReturn = Class.forName("net.minecraft.client.renderer.RenderType");

            solid = lookup.findStatic(renderTypes, "solid",
                MethodType.methodType(renderTypeReturn));
            cutout = lookup.findStatic(renderTypes, "cutout",
                MethodType.methodType(renderTypeReturn));
            cutoutMipped = lookup.findStatic(renderTypes, "cutoutMipped",
                MethodType.methodType(renderTypeReturn));
            translucent = lookup.findStatic(renderTypes, "translucent",
                MethodType.methodType(renderTypeReturn));
        } catch (Exception e) {
            // Rendering classes not available (dedicated server or classloading issue)
        }

        SOLID = solid;
        CUTOUT = cutout;
        CUTOUT_MIPPED = cutoutMipped;
        TRANSLUCENT = translucent;
    }

    /**
     * Returns the solid render type.
     * Delegates to RenderTypes.solid() on 26.1+, or RenderType.solid() on older versions.
     *
     * @return The solid RenderType instance, or null if unavailable
     */
    public static Object solid() {
        if (SOLID != null) {
            try {
                return SOLID.invoke();
            } catch (Throwable t) {
                // Fall through
            }
        }
        return null;
    }

    /**
     * Returns the cutout render type (no alpha blending, hard cutoff).
     * Delegates to RenderTypes.cutout() on 26.1+, or RenderType.cutout() on older versions.
     *
     * @return The cutout RenderType instance, or null if unavailable
     */
    public static Object cutout() {
        if (CUTOUT != null) {
            try {
                return CUTOUT.invoke();
            } catch (Throwable t) {
                // Fall through
            }
        }
        return null;
    }

    /**
     * Returns the cutout-mipped render type (cutout with mipmapping).
     * Delegates to RenderTypes.cutoutMipped() on 26.1+, or RenderType.cutoutMipped() on older versions.
     *
     * @return The cutoutMipped RenderType instance, or null if unavailable
     */
    public static Object cutoutMipped() {
        if (CUTOUT_MIPPED != null) {
            try {
                return CUTOUT_MIPPED.invoke();
            } catch (Throwable t) {
                // Fall through
            }
        }
        return null;
    }

    /**
     * Returns the translucent render type (alpha blending).
     * Delegates to RenderTypes.translucent() on 26.1+, or RenderType.translucent() on older versions.
     *
     * @return The translucent RenderType instance, or null if unavailable
     */
    public static Object translucent() {
        if (TRANSLUCENT != null) {
            try {
                return TRANSLUCENT.invoke();
            } catch (Throwable t) {
                // Fall through
            }
        }
        return null;
    }
}
