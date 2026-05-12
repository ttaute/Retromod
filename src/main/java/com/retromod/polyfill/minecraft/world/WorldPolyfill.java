/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.world;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for world, dimension, and world-gen class renames.
 *
 * Covers the major world system refactors:
 * - World/WorldServer/WorldClient renamed to Level/ServerLevel/ClientLevel
 * - WorldProvider replaced by DimensionType
 * - World storage classes relocated
 * - World-gen classes moved to data-driven system
 * - BiomeProvider renamed to BiomeSource
 *
 * Also handles method and field renames on the World/Level class:
 * - setBlockState -> setBlock (signature changed to include flags parameter)
 * - isRemote field -> isClientSide field
 */
public class WorldPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "World and Dimension Class Renames";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/world/WorldServer",
            "net/minecraft/world/WorldClient",
            "net/minecraft/world/World",
            "net/minecraft/world/WorldProvider",
            "net/minecraft/world/DimensionType",
            "net/minecraft/world/storage/WorldInfo",
            "net/minecraft/world/storage/WorldSavedData",
            "net/minecraft/world/gen/feature/WorldGenAbstractTree",
            "net/minecraft/world/gen/NoiseChunkGenerator",
            "net/minecraft/world/biome/BiomeProvider"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed - pure class, method, and field redirects
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // World class renames
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/world/WorldServer",
            "net/minecraft/server/level/ServerLevel");

        transformer.registerClassRedirect(
            "net/minecraft/world/WorldClient",
            "net/minecraft/client/multiplayer/ClientLevel");

        transformer.registerClassRedirect(
            "net/minecraft/world/World",
            "net/minecraft/world/level/Level");

        // =====================================================================
        // Dimension renames
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/world/WorldProvider",
            "net/minecraft/world/level/dimension/DimensionType");

        // Old DimensionType (pre-1.16 registry-based) -> modern DimensionType
        transformer.registerClassRedirect(
            "net/minecraft/world/DimensionType",
            "net/minecraft/world/level/dimension/DimensionType");

        // =====================================================================
        // World storage renames
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/world/storage/WorldInfo",
            "net/minecraft/world/level/storage/LevelData");

        transformer.registerClassRedirect(
            "net/minecraft/world/storage/WorldSavedData",
            "net/minecraft/world/level/saveddata/SavedData");

        // =====================================================================
        // World-gen renames
        // =====================================================================

        // WorldGenAbstractTree was removed entirely when tree generation became
        // data-driven (1.16.2+). We redirect to Object as a tombstone so mods
        // referencing it don't crash with ClassNotFoundException. Actual tree
        // generation code will need manual porting.
        transformer.registerClassRedirect(
            "net/minecraft/world/gen/feature/WorldGenAbstractTree",
            "java/lang/Object");

        transformer.registerClassRedirect(
            "net/minecraft/world/gen/NoiseChunkGenerator",
            "net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator");

        transformer.registerClassRedirect(
            "net/minecraft/world/biome/BiomeProvider",
            "net/minecraft/world/level/biome/BiomeSource");

        // =====================================================================
        // World method redirects
        // =====================================================================

        // World.setBlockState(BlockPos, IBlockState) -> Level.setBlock(BlockPos, BlockState, int)
        // The modern method requires a flags parameter (3 = NOTIFY_NEIGHBORS | BLOCK_UPDATE).
        // We redirect to a 2-arg version that the class redirect will resolve,
        // and the shim chain handles adding the default flags parameter.
        transformer.registerMethodRedirect(
            "net/minecraft/world/World", "setBlockState",
            "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z",
            "net/minecraft/world/level/Level", "setBlock",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z");

        // =====================================================================
        // World field redirects
        // =====================================================================

        // World.isRemote -> Level.isClientSide
        transformer.registerFieldRedirect(
            "net/minecraft/world/World", "isRemote",
            "net/minecraft/world/level/Level", "isClientSide");
    }
}
