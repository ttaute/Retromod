/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import net.minecraft.block.Blocks;

import java.util.List;

/**
 * Static field accessors on {@code Blocks}. Same idea as {@link ItemTests}:
 * one test per commonly-used vanilla block, so a missing class or field
 * redirect on any of them shows up by name.
 */
public final class BlockTests {

    private BlockTests() {}

    public static List<Test> all() {
        return List.of(
            // Terrain basics
            SimpleTest.notNull("Blocks.STONE",         () -> Blocks.STONE),
            SimpleTest.notNull("Blocks.DIRT",          () -> Blocks.DIRT),
            SimpleTest.notNull("Blocks.GRASS_BLOCK",   () -> Blocks.GRASS_BLOCK),
            SimpleTest.notNull("Blocks.SAND",          () -> Blocks.SAND),
            SimpleTest.notNull("Blocks.GRAVEL",        () -> Blocks.GRAVEL),
            SimpleTest.notNull("Blocks.COBBLESTONE",   () -> Blocks.COBBLESTONE),
            SimpleTest.notNull("Blocks.BEDROCK",       () -> Blocks.BEDROCK),
            SimpleTest.notNull("Blocks.OBSIDIAN",      () -> Blocks.OBSIDIAN),
            // Wood
            SimpleTest.notNull("Blocks.OAK_LOG",       () -> Blocks.OAK_LOG),
            SimpleTest.notNull("Blocks.OAK_PLANKS",    () -> Blocks.OAK_PLANKS),
            SimpleTest.notNull("Blocks.OAK_LEAVES",    () -> Blocks.OAK_LEAVES),
            SimpleTest.notNull("Blocks.OAK_DOOR",      () -> Blocks.OAK_DOOR),
            // Ores
            SimpleTest.notNull("Blocks.IRON_ORE",      () -> Blocks.IRON_ORE),
            SimpleTest.notNull("Blocks.DIAMOND_ORE",   () -> Blocks.DIAMOND_ORE),
            SimpleTest.notNull("Blocks.COAL_ORE",      () -> Blocks.COAL_ORE),
            SimpleTest.notNull("Blocks.GOLD_ORE",      () -> Blocks.GOLD_ORE),
            SimpleTest.notNull("Blocks.COPPER_ORE",    () -> Blocks.COPPER_ORE),
            SimpleTest.notNull("Blocks.EMERALD_ORE",   () -> Blocks.EMERALD_ORE),
            // Mineral blocks
            SimpleTest.notNull("Blocks.IRON_BLOCK",    () -> Blocks.IRON_BLOCK),
            SimpleTest.notNull("Blocks.DIAMOND_BLOCK", () -> Blocks.DIAMOND_BLOCK),
            SimpleTest.notNull("Blocks.COPPER_BLOCK",  () -> Blocks.COPPER_BLOCK),
            SimpleTest.notNull("Blocks.AMETHYST_BLOCK",() -> Blocks.AMETHYST_BLOCK),
            // Functional blocks
            SimpleTest.notNull("Blocks.CHEST",         () -> Blocks.CHEST),
            SimpleTest.notNull("Blocks.FURNACE",       () -> Blocks.FURNACE),
            SimpleTest.notNull("Blocks.CRAFTING_TABLE",() -> Blocks.CRAFTING_TABLE),
            SimpleTest.notNull("Blocks.ANVIL",         () -> Blocks.ANVIL),
            SimpleTest.notNull("Blocks.ENCHANTING_TABLE", () -> Blocks.ENCHANTING_TABLE),
            SimpleTest.notNull("Blocks.HOPPER",        () -> Blocks.HOPPER),
            SimpleTest.notNull("Blocks.BEACON",        () -> Blocks.BEACON),
            // Liquids
            SimpleTest.notNull("Blocks.WATER",         () -> Blocks.WATER),
            SimpleTest.notNull("Blocks.LAVA",          () -> Blocks.LAVA),
            // Special
            SimpleTest.notNull("Blocks.AIR",           () -> Blocks.AIR),
            SimpleTest.notNull("Blocks.GLASS",         () -> Blocks.GLASS),
            SimpleTest.notNull("Blocks.TORCH",         () -> Blocks.TORCH),
            SimpleTest.notNull("Blocks.REDSTONE_TORCH",() -> Blocks.REDSTONE_TORCH),
            SimpleTest.notNull("Blocks.REDSTONE_WIRE", () -> Blocks.REDSTONE_WIRE),
            SimpleTest.notNull("Blocks.PISTON",        () -> Blocks.PISTON),
            SimpleTest.notNull("Blocks.STICKY_PISTON", () -> Blocks.STICKY_PISTON)
        );
    }
}
