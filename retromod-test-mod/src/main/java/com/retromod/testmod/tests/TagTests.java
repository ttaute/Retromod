/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;

import java.util.List;

/**
 * Block + item tags. Mods read these for things like "is this an axe-effective
 * block" or "is this in the leaves tag" — the underlying class
 * {@code TagKey<T>} replaced the older {@code Tag.Identified<T>} a while
 * back, and the field shape has been stable since.
 */
public final class TagTests {

    private TagTests() {}

    public static List<Test> all() {
        return List.of(
            SimpleTest.notNull("BlockTags.LOGS",        () -> BlockTags.LOGS),
            SimpleTest.notNull("BlockTags.LEAVES",      () -> BlockTags.LEAVES),
            SimpleTest.notNull("BlockTags.PLANKS",      () -> BlockTags.PLANKS),
            SimpleTest.notNull("BlockTags.WOOL",        () -> BlockTags.WOOL),
            SimpleTest.notNull("BlockTags.DIRT",        () -> BlockTags.DIRT),
            SimpleTest.notNull("ItemTags.LOGS",         () -> ItemTags.LOGS),
            SimpleTest.notNull("ItemTags.LEAVES",       () -> ItemTags.LEAVES),
            SimpleTest.notNull("ItemTags.WOOL",         () -> ItemTags.WOOL),
            new SimpleTest("BlockTags.LOGS.id() = minecraft:logs", () -> {
                TagKey<?> tag = BlockTags.LOGS;
                String id = tag.id().toString();
                return "minecraft:logs".equals(id)
                    ? TestResult.success()
                    : TestResult.fail("got " + id);
            })
        );
    }
}
