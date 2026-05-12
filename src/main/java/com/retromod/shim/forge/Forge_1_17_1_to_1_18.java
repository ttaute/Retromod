/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.17.1 to 1.18 shim - World generation and height changes.
 * The world height was extended to -64 through 320, requiring changes
 * to world generation structures, biome sources, and chunk handling.
 * StructureFeature was renamed to Structure.
 */
public class Forge_1_17_1_to_1_18 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.17.1 to 1.18"; }
    @Override public String getSourceVersion() { return "1.17.1"; }
    @Override public String getTargetVersion() { return "1.18"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "net/minecraft/world/level/levelgen/feature/StructureFeature",
            "net/minecraft/world/level/levelgen/structure/Structure"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/Level", "getMinBuildHeight", "()I",
            "com/retromod/shim/forge/embedded/WorldHeightShim", "getMinBuildHeight",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/biome/BiomeSource",
            "net/minecraft/world/level/biome/BiomeSource"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/chunk/LevelChunk", "getHighestSection",
            "()Lnet/minecraft/world/level/chunk/LevelChunkSection;",
            "com/retromod/shim/forge/embedded/ChunkShim", "getHighestSection",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // RenderWorldLastEvent renamed to RenderLevelLastEvent in 1.18
        // (reflects the World→Level rename throughout the codebase)
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderWorldLastEvent",
            "net/minecraftforge/client/event/RenderLevelLastEvent"
        );
        // fmllegacy package removed in Forge 1.18; RegistryObject moved back to registries package
        transformer.registerClassRedirect(
            "net/minecraftforge/fmllegacy/RegistryObject",
            "net/minecraftforge/registries/RegistryObject"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
