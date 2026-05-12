/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.block;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for block class renames from The Flattening (1.13+).
 *
 * Pre-flattening mods reference block classes in the old {@code net.minecraft.block}
 * package with legacy names (e.g., BlockContainer, BlockBush). These were renamed
 * and relocated to {@code net.minecraft.world.level.block} in the modern Mojang
 * mapping scheme. This provider registers class redirects so old bytecode references
 * resolve to the correct modern classes at runtime.
 */
public class BlockPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Block Class Renames";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/block/BlockContainer",
            "net/minecraft/block/BlockBush",
            "net/minecraft/block/BlockCrops",
            "net/minecraft/block/BlockDoor",
            "net/minecraft/block/BlockFence",
            "net/minecraft/block/BlockFenceGate",
            "net/minecraft/block/BlockLeaves",
            "net/minecraft/block/BlockLog",
            "net/minecraft/block/BlockOre",
            "net/minecraft/block/BlockSlab",
            "net/minecraft/block/BlockStairs",
            "net/minecraft/block/BlockWall",
            "net/minecraft/block/BlockPane",
            "net/minecraft/block/BlockTorch",
            "net/minecraft/block/BlockRedstoneOre",
            "net/minecraft/block/BlockGrass",
            "net/minecraft/block/BlockFlower",
            "net/minecraft/block/BlockButton",
            "net/minecraft/block/BlockPressurePlate",
            "net/minecraft/block/BlockTrapDoor",
            "net/minecraft/block/BlockChest",
            "net/minecraft/block/BlockFurnace",
            "net/minecraft/block/BlockWorkbench",
            "net/minecraft/block/BlockAnvil",
            "net/minecraft/block/BlockGlass",
            "net/minecraft/block/BlockStainedGlass",
            "net/minecraft/block/BlockCauldron",
            "net/minecraft/block/BlockHopper"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed - pure class redirects handle these renames
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // Block class renames: net.minecraft.block.* -> net.minecraft.world.level.block.*
        // These cover the 1.13 Flattening renames using Mojang official names.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockContainer",
            "net/minecraft/world/level/block/BaseEntityBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockBush",
            "net/minecraft/world/level/block/BushBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockCrops",
            "net/minecraft/world/level/block/CropBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockDoor",
            "net/minecraft/world/level/block/DoorBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockFence",
            "net/minecraft/world/level/block/FenceBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockFenceGate",
            "net/minecraft/world/level/block/FenceGateBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockLeaves",
            "net/minecraft/world/level/block/LeavesBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockLog",
            "net/minecraft/world/level/block/RotatedPillarBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockOre",
            "net/minecraft/world/level/block/DropExperienceBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockSlab",
            "net/minecraft/world/level/block/SlabBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockStairs",
            "net/minecraft/world/level/block/StairBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockWall",
            "net/minecraft/world/level/block/WallBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockPane",
            "net/minecraft/world/level/block/IronBarsBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockTorch",
            "net/minecraft/world/level/block/TorchBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockRedstoneOre",
            "net/minecraft/world/level/block/RedStoneOreBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockGrass",
            "net/minecraft/world/level/block/GrassBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockFlower",
            "net/minecraft/world/level/block/FlowerBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockButton",
            "net/minecraft/world/level/block/ButtonBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockPressurePlate",
            "net/minecraft/world/level/block/PressurePlateBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockTrapDoor",
            "net/minecraft/world/level/block/TrapDoorBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockChest",
            "net/minecraft/world/level/block/ChestBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockFurnace",
            "net/minecraft/world/level/block/FurnaceBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockWorkbench",
            "net/minecraft/world/level/block/CraftingTableBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockAnvil",
            "net/minecraft/world/level/block/AnvilBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockGlass",
            "net/minecraft/world/level/block/GlassBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockStainedGlass",
            "net/minecraft/world/level/block/StainedGlassBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockCauldron",
            "net/minecraft/world/level/block/CauldronBlock");

        transformer.registerClassRedirect(
            "net/minecraft/block/BlockHopper",
            "net/minecraft/world/level/block/HopperBlock");
    }
}
