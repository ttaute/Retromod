/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

/**
 * Block / Item / BlockState / ItemStack — the most-common gameplay-related
 * APIs. All static field accesses, all method calls, no constructors that
 * need a {@code World}.
 */
public final class BlockItemTests {

    private BlockItemTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("Blocks.STONE static field", () ->
                Blocks.STONE != null
                    ? TestResult.success()
                    : TestResult.fail("null")
            ),
            new SimpleTest("Items.DIAMOND static field", () ->
                Items.DIAMOND != null
                    ? TestResult.success()
                    : TestResult.fail("null")
            ),
            new SimpleTest("Block.getDefaultState()", () -> {
                BlockState s = Blocks.STONE.getDefaultState();
                return s != null
                    ? TestResult.success()
                    : TestResult.fail("null");
            }),
            new SimpleTest("BlockState.getBlock() round-trip", () -> {
                BlockState s = Blocks.STONE.getDefaultState();
                Block b = s.getBlock();
                return b == Blocks.STONE
                    ? TestResult.success()
                    : TestResult.fail("got " + b);
            }),
            new SimpleTest("BlockState.isAir() returns false for stone", () -> {
                BlockState s = Blocks.STONE.getDefaultState();
                return !s.isAir()
                    ? TestResult.success()
                    : TestResult.fail("stone reported as air");
            }),
            new SimpleTest("Blocks.AIR.getDefaultState().isAir() == true", () -> {
                BlockState s = Blocks.AIR.getDefaultState();
                return s.isAir()
                    ? TestResult.success()
                    : TestResult.fail("air not reported as air");
            }),
            // (`new ItemStack(item, count)` moved to DeferredItemStackTests
            // — it requires the data-component registry to be bootstrapped,
            // which doesn't happen until ClientLifecycleEvents.CLIENT_STARTED.)
            new SimpleTest("ItemStack.EMPTY.isEmpty()", () ->
                ItemStack.EMPTY.isEmpty()
                    ? TestResult.success()
                    : TestResult.fail("EMPTY reported non-empty")
            )
        );
    }
}
