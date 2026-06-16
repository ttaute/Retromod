/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;

/**
 * Loader-agnostic Minecraft class moves/renames for the 1.21.11 → 26.1 jump.
 *
 * <h2>Why shared</h2>
 * These are <b>vanilla MC</b> Mojang→Mojang renames (e.g. {@code GuiGraphics} →
 * {@code GuiGraphicsExtractor}). They apply identically whether the mod is Fabric
 * or NeoForge, because both loaders use Mojang names on a 26.1 host (Fabric's
 * intermediary→Mojang harvest already lands old Fabric mods on Mojang names by
 * the time these run; NeoForge mods are Mojang-named since 1.20.2). Keeping them
 * in one place means a rename added here fixes both loaders at once — the
 * compat-audit's biggest finding was that snapshot.1 added these to the Fabric
 * shim only, so NeoForge mods (e.g. the 1.21.1-on-NeoForge mods in #64) still
 * hit {@code NoClassDefFoundError} on {@code GuiGraphics}/{@code RenderType}.
 *
 * <h2>Scope</h2>
 * Vanilla {@code net/minecraft/**} (+ {@code com/mojang/blaze3d/**}) type
 * relocations ONLY. Loader-API renames (Fabric networking S2C/C2S, ScreenHandler
 * → Menu, ClientWorldEvents → ClientLevelEvents, NeoForge-specific types, …) stay
 * in each loader's own shim — they're not portable.
 *
 * <p>Called from both {@code Fabric_1_21_11_to_26_1} and
 * {@code NeoForge_1_21_11_to_26_1}.</p>
 */
public final class Common_1_21_11_to_26_1_ClassMoves {

    private Common_1_21_11_to_26_1_ClassMoves() {}

    /** Register every loader-agnostic vanilla 1.21.11→26.1 class move. */
    public static void register(RetromodTransformer transformer) {
        // GuiGraphics → GuiGraphicsExtractor — the type that bundles PoseStack +
        // BufferSource + scissor stack for in-GUI rendering. Every overlay/HUD/
        // screen mod takes a GuiGraphics parameter, so this is the single
        // most-referenced 26.1 rename in the compat audit (1400+ refs).
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiGraphics",
            "net/minecraft/client/gui/GuiGraphicsExtractor");

        // RenderType + RenderTypes moved into their own `rendertype` sub-package.
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderType",
            "net/minecraft/client/renderer/rendertype/RenderType");
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderTypes",
            "net/minecraft/client/renderer/rendertype/RenderTypes");

        // BlockAndTintGetter moved from `world/level/` to `client/renderer/block/`
        // when the type became client-only (the server doesn't tint).
        transformer.registerClassRedirect(
            "net/minecraft/world/level/BlockAndTintGetter",
            "net/minecraft/client/renderer/block/BlockAndTintGetter");

        // Tier-2 render-state soft-fail: neutralize the imperative RenderSystem
        // state setters deleted in the blaze3d GpuDevice/RenderPipeline refactor
        // (gone by 1.21.11, stay gone through 26.x). Loader-agnostic blaze3d, so
        // it rides this shared path for Fabric + NeoForge; Forge wires it
        // directly. See RemovedRenderStateNeutralize for the full rationale.
        RemovedRenderStateNeutralize.register(transformer);
    }
}
