/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.17.1 to run on 1.18.
 * Handles world generation overhaul and world height changes from -64 to 320.
 */
public class Fabric_1_17_1_to_1_18 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.17.1 to 1.18"; }
    @Override public String getSourceVersion() { return "1.17.1"; }
    @Override public String getTargetVersion() { return "1.18"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // World height changes
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
        // Structure feature renamed
        transformer.registerClassRedirect(
            "net/minecraft/world/gen/feature/StructureFeature",
            "net/minecraft/world/gen/structure/Structure"
        );
        // Biome source changes
        transformer.registerClassRedirect(
            "net/minecraft/world/biome/source/VanillaLayeredBiomeSource",
            "net/minecraft/world/biome/source/MultiNoiseBiomeSource"
        );
        // Chunk status changes
        transformer.registerMethodRedirect(
            "net/minecraft/world/chunk/Chunk", "getHighestNonEmptySection",
            "()Lnet/minecraft/world/chunk/ChunkSection;",
            "com/retromod/shim/fabric/embedded/ChunkShim", "getHighestNonEmptySection",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // --- 1.18 world generation overhaul: class renames and system replacements ---

        // NoiseSlideSettings renamed to NoiseSlider
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/NoiseSlideSettings",
            "net/minecraft/world/level/levelgen/NoiseSlider"
        );
        // Surface builder system completely replaced by SurfaceRules in 1.18
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
        // Placement decorator system replaced by PlacementModifier in 1.18
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/placement/ConfiguredDecorator",
            "net/minecraft/world/level/levelgen/placement/PlacementModifier"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/placement/FeatureDecorator",
            "net/minecraft/world/level/levelgen/placement/PlacementModifier"
        );
        // OverworldBiomeSource merged into MultiNoiseBiomeSource in 1.18
        transformer.registerClassRedirect(
            "net/minecraft/world/level/biome/OverworldBiomeSource",
            "net/minecraft/world/level/biome/MultiNoiseBiomeSource"
        );

        // --- 1.18 method renames ---

        // BlockEntity.save() renamed to saveAdditional() with return type change
        // NOTE: Return type changed from CompoundTag to void. Callers that chain
        // the return value will need additional handling beyond this redirect.
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/BlockEntity",
            "save",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "net/minecraft/world/level/block/entity/BlockEntity",
            "saveAdditional",
            "(Lnet/minecraft/nbt/CompoundTag;)V"
        );
        // RenderSystem.color() renamed to setShaderColor() in 1.18 rendering pipeline overhaul
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
