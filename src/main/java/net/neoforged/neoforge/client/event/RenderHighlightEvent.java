/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of NeoForge's RenderHighlightEvent.
 * Provides cancel/getter functionality for render highlight events.
 */
package net.neoforged.neoforge.client.event;

/**
 * Reimplementation of RenderHighlightEvent with proper cancellation support.
 *
 * Mods use this to customize or cancel the block selection highlight.
 * The cancel state is properly tracked so event handlers work correctly.
 */
public class RenderHighlightEvent {

    private boolean canceled = false;

    /** Returns true if this event has been canceled. */
    public boolean isCanceled() { return canceled; }

    /** Sets the cancel state. When true, the default highlight is suppressed. */
    public void setCanceled(boolean canceled) { this.canceled = canceled; }

    /**
     * Sub-event for block highlight rendering.
     * Provides access to the block hit result and render context.
     */
    public static class Block extends RenderHighlightEvent {
        private Object target;
        private Object poseStack;
        private Object multiBufferSource;
        private Object camera;
        private float partialTick;

        public Block() {}

        /** Returns the block hit result. */
        public Object getTarget() { return target; }

        /** Returns the PoseStack/MatrixStack for rendering. */
        public Object getPoseStack() { return poseStack; }

        /** Returns the MultiBufferSource for rendering. */
        public Object getMultiBufferSource() { return multiBufferSource; }

        /** Returns the camera. */
        public Object getCamera() { return camera; }

        /** Returns the partial tick for interpolation. */
        public float getPartialTick() { return partialTick; }

        /** Sets context — used internally by event dispatch. */
        public void setContext(Object target, Object poseStack, Object bufferSource, Object camera, float partialTick) {
            this.target = target;
            this.poseStack = poseStack;
            this.multiBufferSource = bufferSource;
            this.camera = camera;
            this.partialTick = partialTick;
        }
    }
}
