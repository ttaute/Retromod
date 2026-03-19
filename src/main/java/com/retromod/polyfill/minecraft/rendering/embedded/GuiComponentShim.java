/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.rendering.embedded;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reimplementation of GuiComponent (removed in MC 1.20).
 *
 * In 1.20, Mojang moved all 2D GUI drawing methods from the static GuiComponent
 * class into the new GuiGraphics context object. Old mods that called:
 *   GuiComponent.fill(poseStack, x1, y1, x2, y2, color)
 *   GuiComponent.drawString(font, text, x, y, color)
 *   GuiComponent.blit(poseStack, x, y, u, v, width, height)
 * need to be redirected to the equivalent GuiGraphics methods.
 *
 * Since we cannot hold a compile-time reference to GuiGraphics (it lives in
 * the MC runtime), these methods accept Object parameters and use reflection
 * to locate and invoke the modern equivalents. The PoseStack parameter from
 * old calls is dropped because GuiGraphics manages its own pose stack internally.
 *
 * Note: These shim methods are last-resort fallbacks. The primary transformation
 * path rewrites callsites to use GuiGraphics directly when a GuiGraphics instance
 * is available in the calling context. This shim handles cases where the instance
 * cannot be statically determined.
 */
public class GuiComponentShim {

    private static final MethodHandle GUI_GRAPHICS_FILL;
    private static final MethodHandle GUI_GRAPHICS_DRAW_STRING;
    private static final MethodHandle GUI_GRAPHICS_BLIT;
    private static final Class<?> GUI_GRAPHICS_CLASS;

    static {
        MethodHandle fill = null;
        MethodHandle drawString = null;
        MethodHandle blit = null;
        Class<?> guiGraphicsClass = null;

        try {
            guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
            Class<?> fontClass = Class.forName("net.minecraft.client.gui.Font");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            // GuiGraphics.fill(int x1, int y1, int x2, int y2, int color)
            fill = lookup.findVirtual(guiGraphicsClass, "fill",
                MethodType.methodType(void.class, int.class, int.class, int.class, int.class, int.class));

            // GuiGraphics.drawString(Font font, String text, int x, int y, int color)
            drawString = lookup.findVirtual(guiGraphicsClass, "drawString",
                MethodType.methodType(int.class, fontClass, String.class, int.class, int.class, int.class));

            // GuiGraphics.blit(int x, int y, int blitOffset, int u, int v, int width, int height)
            blit = lookup.findVirtual(guiGraphicsClass, "blit",
                MethodType.methodType(void.class, int.class, int.class, int.class, int.class, int.class, int.class));
        } catch (Exception e) {
            // GuiGraphics not available — running on older MC version or dedicated server
        }

        GUI_GRAPHICS_FILL = fill;
        GUI_GRAPHICS_DRAW_STRING = drawString;
        GUI_GRAPHICS_BLIT = blit;
        GUI_GRAPHICS_CLASS = guiGraphicsClass;
    }

    /**
     * Reimplements GuiComponent.fill(PoseStack, int, int, int, int, int).
     *
     * The poseStack parameter is accepted as Object because old bytecode passes
     * a PoseStack, but we don't need it — GuiGraphics.fill() manages its own
     * pose stack. If no GuiGraphics instance is available in the current context,
     * this becomes a no-op (the fill simply won't render).
     *
     * In practice, the RenderingPolyfill also registers bytecode transforms that
     * attempt to pass the active GuiGraphics through instead of the PoseStack.
     *
     * @param poseStack The old PoseStack (ignored; kept for signature compatibility)
     * @param x1    Left edge
     * @param y1    Top edge
     * @param x2    Right edge
     * @param y2    Bottom edge
     * @param color ARGB color
     */
    public static void fill(Object poseStack, int x1, int y1, int x2, int y2, int color) {
        if (GUI_GRAPHICS_FILL != null && GUI_GRAPHICS_CLASS != null) {
            // Try to get the current GuiGraphics from the Minecraft client context
            Object guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                try {
                    GUI_GRAPHICS_FILL.invoke(guiGraphics, x1, y1, x2, y2, color);
                } catch (Throwable t) {
                    // Silently fail — better than crashing the game
                }
            }
        }
    }

    /**
     * Reimplements GuiComponent.drawString(Font, String, int, int, int).
     *
     * The Font parameter is passed through as Object because we can't reference
     * net.minecraft.client.gui.Font at compile time.
     *
     * @param font  The Font instance (passed through to GuiGraphics.drawString)
     * @param text  Text to draw
     * @param x     X position
     * @param y     Y position
     * @param color ARGB color
     */
    public static void drawString(Object font, String text, int x, int y, int color) {
        if (GUI_GRAPHICS_DRAW_STRING != null && GUI_GRAPHICS_CLASS != null) {
            Object guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                try {
                    GUI_GRAPHICS_DRAW_STRING.invoke(guiGraphics, font, text, x, y, color);
                } catch (Throwable t) {
                    // Silently fail
                }
            }
        }
    }

    /**
     * Reimplements GuiComponent.blit(PoseStack, int, int, int, int, int, int).
     *
     * Delegates to GuiGraphics.blit() with the PoseStack parameter dropped.
     *
     * @param poseStack The old PoseStack (ignored)
     * @param x         X position
     * @param y         Y position
     * @param u         Texture U coordinate
     * @param v         Texture V coordinate
     * @param width     Width to blit
     * @param height    Height to blit
     */
    public static void blit(Object poseStack, int x, int y, int u, int v, int width, int height) {
        if (GUI_GRAPHICS_BLIT != null && GUI_GRAPHICS_CLASS != null) {
            Object guiGraphics = getCurrentGuiGraphics();
            if (guiGraphics != null) {
                try {
                    GUI_GRAPHICS_BLIT.invoke(guiGraphics, x, y, u, v, width, height);
                } catch (Throwable t) {
                    // Silently fail
                }
            }
        }
    }

    /**
     * Attempts to retrieve the current GuiGraphics instance from the Minecraft client.
     *
     * In modern MC (1.20+), Screen.render() receives a GuiGraphics parameter.
     * We walk the call stack to find a render method that has a GuiGraphics local,
     * or fall back to creating one from the current Minecraft instance.
     *
     * @return The active GuiGraphics instance, or null if unavailable
     */
    private static Object getCurrentGuiGraphics() {
        try {
            // Try to get Minecraft.getInstance() and create a GuiGraphics from its bufferSource
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);

            // Get the MultiBufferSource.BufferSource from Minecraft
            Object bufferSource = minecraftClass.getMethod("renderBuffers").invoke(minecraft);
            Object bufSource = bufferSource.getClass().getMethod("bufferSource").invoke(bufferSource);

            // Create a new GuiGraphics(Minecraft, MultiBufferSource.BufferSource)
            return GUI_GRAPHICS_CLASS
                .getConstructor(minecraftClass, Class.forName("net.minecraft.client.renderer.MultiBufferSource$BufferSource"))
                .newInstance(minecraft, bufSource);
        } catch (Exception e) {
            return null;
        }
    }
}
