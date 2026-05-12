/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.gui.embedded;

/**
 * Reimplementation of ScaledResolution (removed in 1.14).
 *
 * ScaledResolution was the pre-1.14 way to get the GUI-scaled window
 * dimensions. In 1.14+, this was replaced by the Window class accessible
 * via Minecraft.getWindow(). This shim delegates to the modern Window API
 * via reflection so old mods that construct ScaledResolution still work.
 *
 * Old usage: new ScaledResolution(minecraft)
 * Modern equivalent: minecraft.getWindow().getGuiScaledWidth(), etc.
 */
public class ScaledResolutionShim {

    private int scaledWidth;
    private int scaledHeight;
    private double scaleFactor;

    public ScaledResolutionShim(Object minecraft) {
        try {
            Object window = minecraft.getClass().getMethod("getWindow").invoke(minecraft);
            this.scaledWidth = (int) window.getClass().getMethod("getGuiScaledWidth").invoke(window);
            this.scaledHeight = (int) window.getClass().getMethod("getGuiScaledHeight").invoke(window);
            this.scaleFactor = (double) window.getClass().getMethod("getGuiScale").invoke(window);
        } catch (Exception e) {
            // Fallback to sensible defaults if reflection fails
            this.scaledWidth = 854;
            this.scaledHeight = 480;
            this.scaleFactor = 1.0;
        }
    }

    public int getScaledWidth() { return scaledWidth; }

    public int getScaledHeight() { return scaledHeight; }

    public double getScaleFactor() { return scaleFactor; }

    public int getScaledWidth_double() { return scaledWidth; }

    public int getScaledHeight_double() { return scaledHeight; }
}
