/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;

/**
 * Stubs out the imperative {@code RenderSystem} state setters deleted in the blaze3d
 * GPU refactor (enableBlend/blendFunc/depthMask/colorMask/...). They're gone from
 * 1.21.11 on with no replacement to redirect to (render state moved onto the immutable
 * {@code RenderPipeline}), so an old mod (~1.16-1.21.4) calling them would hit
 * {@code NoSuchMethodError} at link time. We pop the args and push a default return:
 * the mod loads, minus that one bit of manual GL state. A fully manual immediate-mode
 * renderer may blend wrong and needs hand-porting (roadmap).
 *
 * <p>Match is on owner+name+descriptor, and every entry below is absent on 1.21.11+,
 * so a wrong descriptor just fails to match. {@code RenderSystem} keeps its
 * {@code com.mojang.blaze3d} name on all three loaders. Raw {@code GL11.*} calls are
 * left to {@code GraphicsBackendCompat}.
 */
public final class RemovedRenderStateNeutralize {

    private RemovedRenderStateNeutralize() {}

    private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";

    public static void register(RetromodTransformer transformer) {
        // blend
        neutralize(transformer, "enableBlend", "()V");
        neutralize(transformer, "disableBlend", "()V");
        neutralize(transformer, "defaultBlendFunc", "()V");
        neutralize(transformer, "blendFunc", "(II)V");
        neutralize(transformer, "blendFuncSeparate", "(IIII)V");
        // depth
        neutralize(transformer, "enableDepthTest", "()V");
        neutralize(transformer, "disableDepthTest", "()V");
        neutralize(transformer, "depthMask", "(Z)V");
        neutralize(transformer, "depthFunc", "(I)V");
        // cull / color mask
        neutralize(transformer, "enableCull", "()V");
        neutralize(transformer, "disableCull", "()V");
        neutralize(transformer, "colorMask", "(ZZZZ)V");
        // clear
        neutralize(transformer, "clearColor", "(FFFF)V");
        neutralize(transformer, "clearDepth", "(D)V");
        // scissor (imperative form; 26.x keeps enableScissorForRenderTypeDraws)
        neutralize(transformer, "enableScissor", "(IIII)V");
        neutralize(transformer, "disableScissor", "()V");
        // polygon offset + line width
        neutralize(transformer, "enablePolygonOffset", "()V");
        neutralize(transformer, "disablePolygonOffset", "()V");
        neutralize(transformer, "polygonOffset", "(FF)V");
        neutralize(transformer, "lineWidth", "(F)V");
        // color logic op
        neutralize(transformer, "enableColorLogicOp", "()V");
        neutralize(transformer, "disableColorLogicOp", "()V");
    }

    private static void neutralize(RetromodTransformer transformer, String name, String desc) {
        transformer.registerRemovedMethodNeutralize(RENDER_SYSTEM, name, desc);
    }
}
