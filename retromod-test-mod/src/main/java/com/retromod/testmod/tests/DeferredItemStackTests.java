/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

/**
 * Tests that need the data-component registry to be bootstrapped before they
 * can run. In MC 1.20.5+, {@code new ItemStack(Item, int)} touches the
 * data-component registry during its constructor, and that registry is only
 * populated after Minecraft's {@code Bootstrap.bootStrap()} completes —
 * which happens later than {@code ClientModInitializer.onInitializeClient}.
 *
 * <p>Run these via the {@code ClientLifecycleEvents.CLIENT_STARTED} hook
 * (right before the title screen renders) — by that point all static
 * registries plus the data-component registry are ready.
 */
public final class DeferredItemStackTests {

    private DeferredItemStackTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("new ItemStack(Items.DIAMOND, 5)", () -> {
                ItemStack stack = new ItemStack(Items.DIAMOND, 5);
                if (stack.getCount() != 5) {
                    return TestResult.fail("count=" + stack.getCount());
                }
                if (stack.getItem() != Items.DIAMOND) {
                    return TestResult.fail("item=" + stack.getItem());
                }
                return TestResult.success();
            }),
            new SimpleTest("new ItemStack(Items.NETHERITE_INGOT, 1).isEmpty() == false", () -> {
                ItemStack stack = new ItemStack(Items.NETHERITE_INGOT, 1);
                return !stack.isEmpty()
                    ? TestResult.success()
                    : TestResult.fail("non-empty stack reported as empty");
            }),
            new SimpleTest("ItemStack.split(2) splits", () -> {
                ItemStack stack = new ItemStack(Items.DIAMOND, 5);
                ItemStack split = stack.split(2);
                if (split.getCount() != 2) return TestResult.fail("split count=" + split.getCount());
                if (stack.getCount() != 3) return TestResult.fail("remaining count=" + stack.getCount());
                return TestResult.success();
            })
        );
    }
}
