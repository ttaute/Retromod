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
 * Tests that need the data-component registry to be <i>frozen</i> before
 * they can run. In MC 1.20.5+, {@code new ItemStack(Item, int)} touches
 * the data-component registry during its constructor; if the registry
 * isn't frozen yet the constructor throws
 * {@code NullPointerException: Components not bound yet}.
 *
 * <p>The registry isn't frozen at {@code ClientLifecycleEvents.CLIENT_STARTED}
 * (still mid-bootstrap then) — the freeze happens when the client actually
 * loads or joins a world. So these tests run on the {@code WORLD_JOIN}
 * phase via {@code ClientPlayConnectionEvents.JOIN}, not on
 * {@code CLIENT_STARTED}.
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
