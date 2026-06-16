/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;

/**
 * Tier-2 render-state soft-fail: NEUTRALIZE the imperative {@code RenderSystem}
 * state setters that were deleted in the modern blaze3d refactor.
 *
 * <h2>Why this can't be a redirect</h2>
 * Minecraft's GPU layer was rebuilt around an explicit {@code GpuDevice} +
 * immutable {@code RenderPipeline} model. The old imperative state calls —
 * {@code RenderSystem.enableBlend()}, {@code blendFunc(int,int)},
 * {@code depthMask(boolean)}, {@code colorMask(...)}, … — were <b>removed</b>:
 * render state is now declared on the pipeline object you build, not toggled by
 * a static call. Verified against the real client jars, these are already gone
 * by <b>1.21.11</b> and stay gone through 26.1/26.2. So there is <b>no surviving
 * method to redirect to</b> — pointing at one would just reintroduce the
 * {@code NoSuchMethodError}.
 *
 * <h2>What we do instead</h2>
 * An old mod (≈1.16–1.21.4) that calls these still has the call in its bytecode.
 * On a modern host that call dies with {@code NoSuchMethodError} at link time.
 * We {@linkplain RetromodTransformer#registerRemovedMethodNeutralize neutralize}
 * the call: pop its args, push a default return, emit no call. The mod
 * <b>loads and runs</b>; that one bit of manual GL state is simply lost
 * (<b>soft-fail</b>). In practice this is usually visually fine — modern MC's
 * render layers/pipelines already set correct blend/depth/cull state for the
 * draw calls a content mod makes — but a mod doing fully manual immediate-mode
 * state management may render with wrong blending. That residue is the same
 * boundary a native Vulkan renderer (e.g. VulkanMod) hits, and the truly
 * custom-renderer cases need hand-porting (tracked on the roadmap), not a stub.
 *
 * <h2>Relation to the Vulkan/26.2 work</h2>
 * This is <i>not</i> the OpenGL→Vulkan story — these methods were gone before
 * the Vulkan backend existed, so it's a general "old mod on modern MC" fix that
 * also happens to matter more once OpenGL is the non-default (26.2) / removed
 * (26.3) backend. Raw {@code org.lwjgl.opengl.GL11.*} calls are handled
 * separately by preferring the still-present OpenGL backend (Tier 0,
 * {@code GraphicsBackendCompat}); those are left untouched here because they
 * work fine on that backend.
 *
 * <h2>Safety</h2>
 * Match is exact on owner+name+descriptor, and every entry below was verified
 * <b>absent</b> on 1.21.11+ — so no live overload is ever neutralized. If a
 * descriptor here is slightly off, the match simply fails (the call is left
 * as-is); it can never neutralize the wrong method. Loader-agnostic:
 * {@code RenderSystem} is a {@code com.mojang.blaze3d} type with the same name on
 * Fabric, NeoForge, and Forge, so this registers identically for all three.
 */
public final class RemovedRenderStateNeutralize {

    private RemovedRenderStateNeutralize() {}

    private static final String RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem";

    /** Neutralize every removed imperative RenderSystem state setter. */
    public static void register(RetromodTransformer transformer) {
        // Blend state (all removed; state is now part of the RenderPipeline).
        neutralize(transformer, "enableBlend", "()V");
        neutralize(transformer, "disableBlend", "()V");
        neutralize(transformer, "defaultBlendFunc", "()V");
        neutralize(transformer, "blendFunc", "(II)V");
        neutralize(transformer, "blendFuncSeparate", "(IIII)V");
        // Depth state.
        neutralize(transformer, "enableDepthTest", "()V");
        neutralize(transformer, "disableDepthTest", "()V");
        neutralize(transformer, "depthMask", "(Z)V");
        neutralize(transformer, "depthFunc", "(I)V");
        // Cull / color-mask.
        neutralize(transformer, "enableCull", "()V");
        neutralize(transformer, "disableCull", "()V");
        neutralize(transformer, "colorMask", "(ZZZZ)V");
        // Clear state.
        neutralize(transformer, "clearColor", "(FFFF)V");
        neutralize(transformer, "clearDepth", "(D)V");
        // Scissor (the imperative form; the surviving 26.x scissor API is the
        // unrelated enableScissorForRenderTypeDraws(int,int,int,int)).
        neutralize(transformer, "enableScissor", "(IIII)V");
        neutralize(transformer, "disableScissor", "()V");
        // Polygon offset + line width.
        neutralize(transformer, "enablePolygonOffset", "()V");
        neutralize(transformer, "disablePolygonOffset", "()V");
        neutralize(transformer, "polygonOffset", "(FF)V");
        neutralize(transformer, "lineWidth", "(F)V");
        // Color logic op.
        neutralize(transformer, "enableColorLogicOp", "()V");
        neutralize(transformer, "disableColorLogicOp", "()V");
    }

    private static void neutralize(RetromodTransformer transformer, String name, String desc) {
        transformer.registerRemovedMethodNeutralize(RENDER_SYSTEM, name, desc);
    }
}
