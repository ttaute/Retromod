/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;

/**
 * Loader-agnostic Mojang->Mojang vanilla class moves/renames for the 1.21.11 -> 26.1 jump (#64).
 *
 * <p>Scope is vanilla {@code net/minecraft/**} and {@code com/mojang/blaze3d/**} only; loader-API
 * renames live in each loader's shim. Called from {@code Fabric_1_21_11_to_26_1} and
 * {@code NeoForge_1_21_11_to_26_1}.</p>
 */
public final class Common_1_21_11_to_26_1_ClassMoves {

    private Common_1_21_11_to_26_1_ClassMoves() {}

    public static void register(RetromodTransformer transformer) {
        // 26.1 removed the single-arg is() overloads from BlockState/ItemStack/FluidState
        // (NoSuchMethodError on block placement, tag checks, item comparisons - the Macaw's
        // Bridge_Block.onPlace crash). Bridged by a per-mod synthetic.
        IsOverloadBridgeSynthetic.register(transformer);

        // GuiGraphics -> GuiGraphicsExtractor
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiGraphics",
            "net/minecraft/client/gui/GuiGraphicsExtractor");

        // RenderType + RenderTypes moved into a rendertype sub-package.
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderType",
            "net/minecraft/client/renderer/rendertype/RenderType");
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderTypes",
            "net/minecraft/client/renderer/rendertype/RenderTypes");

        // BlockAndTintGetter became client-only and moved to client/renderer/block/.
        transformer.registerClassRedirect(
            "net/minecraft/world/level/BlockAndTintGetter",
            "net/minecraft/client/renderer/block/BlockAndTintGetter");

        // ItemNameBlockItem folded into BlockItem in 26.x; same (Block, Item.Properties)
        // ctor, so an extending mod loads. Place-time custom naming is lost.
        transformer.registerClassRedirect(
            "net/minecraft/world/item/ItemNameBlockItem",
            "net/minecraft/world/item/BlockItem");

        // ResourceKey.location() -> identifier() in 26.x. A method rename, not a redirect:
        // the call site is a method reference (ResourceKey::location in Resourceful Lib's
        // ExtraByteCodecs) that the direct-call pass can't reach.
        transformer.registerMethodRename(
            "net/minecraft/resources/ResourceKey", "location", "identifier");

        // ChunkPos(long) ctor removed in 26.x; rewrite to the static ChunkPos.unpack(long).
        transformer.registerConstructorRedirect(
            "net/minecraft/world/level/ChunkPos", "(J)V",
            "net/minecraft/world/level/ChunkPos", "unpack",
            "(J)Lnet/minecraft/world/level/ChunkPos;");

        // Painting + PaintingVariant moved into an entity/decoration/painting sub-package in 26.1
        // (verified: absent at the old path, present at the new path on 26.1 and 26.2). Fixes any mod
        // referencing them AND the @Mixin(Painting) target of Deeper and Darker's PaintingMixin (#28).
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/decoration/Painting",
            "net/minecraft/world/entity/decoration/painting/Painting");
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/decoration/PaintingVariant",
            "net/minecraft/world/entity/decoration/painting/PaintingVariant");

        // Husk moved into an entity/monster/zombie sub-package in 26.1 (verified: old monster/Husk
        // absent, monster/zombie/Husk present on 26.1 and 26.2).
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/monster/Husk",
            "net/minecraft/world/entity/monster/zombie/Husk");

        // MobSpawnType was renamed to EntitySpawnReason in 26.1 (verified: MobSpawnType absent,
        // EntitySpawnReason present on 26.1). Very common in mob-spawn checkXxxSpawnRules signatures.
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/MobSpawnType",
            "net/minecraft/world/entity/EntitySpawnReason");

        // Neutralize the imperative RenderSystem state setters deleted in the blaze3d
        // GpuDevice/RenderPipeline refactor (Forge wires this directly instead).
        RemovedRenderStateNeutralize.register(transformer);
    }
}
