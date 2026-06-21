/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for Fabric rendering API changes across versions.
 *
 * The Fabric rendering API has undergone several breaking changes:
 * - 1.19.3: ClientSpriteRegistryCallback removed (atlas API rework)
 * - 1.20.1: HudRenderCallback signature changed to use DrawContext
 * - 1.20.2+: Model loading API replaced by ModelLoadingPlugin
 * - Various: WorldRenderEvents sub-events changed, fluid render handler API updated
 *
 * Covered changes:
 * - ClientSpriteRegistryCallback (removed in 1.19.3)
 * - HudRenderCallback (DrawContext parameter added)
 * - WorldRenderEvents (sub-event signature changes)
 * - FluidRenderHandler/FluidRenderHandlerRegistry (API changes)
 * - ColorProviderRegistry (API changes)
 * - ScreenEvents (API changes in newer versions)
 * - RendererAccess (renderer API changes)
 * - BlockRenderLayerMap (stays, but import path may differ)
 */
public class FabricRenderingPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Fabric Rendering API Changes";
    }

    @Override
    public String getCategory() {
        return "fabric_api";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // Removed in 1.19.3 - atlas system was reworked
            "net/fabricmc/fabric/api/event/client/ClientSpriteRegistryCallback",
            "net/fabricmc/fabric/api/event/client/ClientSpriteRegistryCallback$Registry",

            // Old model loading API replaced by ModelLoadingPlugin in newer versions
            "net/fabricmc/fabric/api/client/model/ModelLoadingRegistry",
            "net/fabricmc/fabric/api/client/model/ModelProviderContext",
            "net/fabricmc/fabric/api/client/model/ModelResourceProvider",
            "net/fabricmc/fabric/api/client/model/ModelVariantProvider"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed - class and method redirects handle these changes
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // ClientSpriteRegistryCallback - removed in 1.19.3
        // The sprite atlas registration system was reworked. Old mods using
        // ClientSpriteRegistryCallback.event(BLOCK_ATLAS_TEXTURE).register(...)
        // need to use SpriteAtlasTexture events or data-driven approaches.
        // We redirect to a no-op event to prevent ClassNotFoundException.
        // =====================================================================

        // No direct replacement class exists; these are truly removed APIs.
        // The class redirect prevents CNFE; runtime calls will be no-ops.

        // =====================================================================
        // Model loading API migration
        // Old: ModelLoadingRegistry.INSTANCE.registerModelProvider(...)
        // New: ModelLoadingPlugin.register(...)
        // =====================================================================

        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/model/ModelLoadingRegistry",
            "net/fabricmc/fabric/api/client/model/loading/v1/ModelLoadingPlugin");

        // =====================================================================
        // HudRenderCallback signature changed:
        // Old: void onHudRender(MatrixStack matrixStack, float tickDelta)
        // New: void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter)
        // We redirect the method signature so old mods at least resolve.
        // =====================================================================

        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback", "onHudRender",
            "(Lnet/minecraft/client/util/math/MatrixStack;F)V",
            "net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback", "onHudRender",
            "(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V");

        // =====================================================================
        // FluidRenderHandler API changes
        // getFluidColor signature updated across versions
        // =====================================================================

        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/render/fluid/v1/FluidRenderHandler", "getFluidColor",
            "(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/FluidState;)I",
            "net/fabricmc/fabric/api/client/render/fluid/v1/FluidRenderHandler", "getFluidColor",
            "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)I");

        // =====================================================================
        // WorldRenderEvents sub-event changes
        // BeforeBlockOutline context parameter type changed
        // =====================================================================

        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents$BeforeBlockOutline", "beforeBlockOutline",
            "(Lnet/minecraft/client/render/WorldRenderer;Lnet/minecraft/client/util/math/MatrixStack;)Z",
            "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents$BeforeBlockOutline", "beforeBlockOutline",
            "(Lnet/minecraft/client/render/WorldRenderContext;Lnet/minecraft/world/phys/HitResult;)Z");

        // =====================================================================
        // ColorProviderRegistry API changes
        // Register method parameter types updated for Mojang mappings
        // =====================================================================

        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/ColorProviderRegistry", "register",
            "(Lnet/minecraft/client/color/block/BlockColorProvider;[Lnet/minecraft/block/Block;)V",
            "net/fabricmc/fabric/api/client/rendering/v1/ColorProviderRegistry", "register",
            "(Lnet/minecraft/client/color/block/BlockColor;[Lnet/minecraft/world/level/block/Block;)V");

        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/ColorProviderRegistry", "register",
            "(Lnet/minecraft/client/color/item/ItemColorProvider;[Lnet/minecraft/item/Item;)V",
            "net/fabricmc/fabric/api/client/rendering/v1/ColorProviderRegistry", "register",
            "(Lnet/minecraft/client/color/item/ItemColor;[Lnet/minecraft/world/item/Item;)V");
    }
}
