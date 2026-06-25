/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Fabric 1.17.1 mods on 1.18: worldgen overhaul plus the -64..320 world-height change. */
public class Fabric_1_17_1_to_1_18 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.17.1 to 1.18"; }
    @Override public String getSourceVersion() { return "1.17.1"; }
    @Override public String getTargetVersion() { return "1.18"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerMethodRedirect(
            "net/minecraft/world/World", "getBottomY", "()I",
            "com/retromod/shim/fabric/embedded/WorldHeightShim", "getBottomY",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/World", "getTopY", "()I",
            "com/retromod/shim/fabric/embedded/WorldHeightShim", "getTopY",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/gen/feature/StructureFeature",
            "net/minecraft/world/gen/structure/Structure"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/biome/source/VanillaLayeredBiomeSource",
            "net/minecraft/world/biome/source/MultiNoiseBiomeSource"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/chunk/Chunk", "getHighestNonEmptySection",
            "()Lnet/minecraft/world/chunk/ChunkSection;",
            "com/retromod/shim/fabric/embedded/ChunkShim", "getHighestNonEmptySection",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // 1.18 worldgen overhaul: surface builders became SurfaceRules, decorators became PlacementModifiers
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/NoiseSlideSettings",
            "net/minecraft/world/level/levelgen/NoiseSlider"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/surfacebuilders/SurfaceBuilder",
            "net/minecraft/world/level/levelgen/SurfaceRules"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/surfacebuilders/ConfiguredSurfaceBuilder",
            "net/minecraft/world/level/levelgen/SurfaceRules"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/surfacebuilders/SurfaceBuilderBaseConfiguration",
            "net/minecraft/world/level/levelgen/SurfaceRules"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/placement/ConfiguredDecorator",
            "net/minecraft/world/level/levelgen/placement/PlacementModifier"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/placement/FeatureDecorator",
            "net/minecraft/world/level/levelgen/placement/PlacementModifier"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/biome/OverworldBiomeSource",
            "net/minecraft/world/level/biome/MultiNoiseBiomeSource"
        );

        // save() -> saveAdditional(); return type changed CompoundTag -> void, so callers that chain it need more than this redirect
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/BlockEntity",
            "save",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "net/minecraft/world/level/block/entity/BlockEntity",
            "saveAdditional",
            "(Lnet/minecraft/nbt/CompoundTag;)V"
        );
        // RenderSystem.color -> setShaderColor (1.18 render pipeline)
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/systems/RenderSystem",
            "color", "(FFFF)V",
            "com/mojang/blaze3d/systems/RenderSystem",
            "setShaderColor", "(FFFF)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
