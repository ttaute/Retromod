/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import net.minecraft.item.Items;

import java.util.List;

/**
 * Static field accessors on {@code Items}. Each one is a separate redirect
 * (yarn class {@code Items} → mojang {@code Items}, plus the field name on
 * top of that), and any one of them being broken means a whole class of mods
 * (recipes, item registries, drop tables, NEI-style mods) won't work. Long
 * list on purpose.
 */
public final class ItemTests {

    private ItemTests() {}

    public static List<Test> all() {
        return List.of(
            // Resources
            SimpleTest.notNull("Items.DIAMOND",        () -> Items.DIAMOND),
            SimpleTest.notNull("Items.NETHERITE_INGOT",() -> Items.NETHERITE_INGOT),
            SimpleTest.notNull("Items.IRON_INGOT",     () -> Items.IRON_INGOT),
            SimpleTest.notNull("Items.GOLD_INGOT",     () -> Items.GOLD_INGOT),
            SimpleTest.notNull("Items.COPPER_INGOT",   () -> Items.COPPER_INGOT),
            SimpleTest.notNull("Items.EMERALD",        () -> Items.EMERALD),
            SimpleTest.notNull("Items.COAL",           () -> Items.COAL),
            SimpleTest.notNull("Items.REDSTONE",       () -> Items.REDSTONE),
            // Tools
            SimpleTest.notNull("Items.DIAMOND_PICKAXE",() -> Items.DIAMOND_PICKAXE),
            SimpleTest.notNull("Items.IRON_AXE",       () -> Items.IRON_AXE),
            SimpleTest.notNull("Items.SHEARS",         () -> Items.SHEARS),
            SimpleTest.notNull("Items.BOW",            () -> Items.BOW),
            SimpleTest.notNull("Items.CROSSBOW",       () -> Items.CROSSBOW),
            SimpleTest.notNull("Items.TRIDENT",        () -> Items.TRIDENT),
            // Containers / utility
            SimpleTest.notNull("Items.BUCKET",         () -> Items.BUCKET),
            SimpleTest.notNull("Items.WATER_BUCKET",   () -> Items.WATER_BUCKET),
            SimpleTest.notNull("Items.LAVA_BUCKET",    () -> Items.LAVA_BUCKET),
            SimpleTest.notNull("Items.MILK_BUCKET",    () -> Items.MILK_BUCKET),
            SimpleTest.notNull("Items.ENDER_PEARL",    () -> Items.ENDER_PEARL),
            SimpleTest.notNull("Items.COMPASS",        () -> Items.COMPASS),
            SimpleTest.notNull("Items.MAP",            () -> Items.MAP),
            SimpleTest.notNull("Items.CLOCK",          () -> Items.CLOCK),
            // Food
            SimpleTest.notNull("Items.APPLE",          () -> Items.APPLE),
            SimpleTest.notNull("Items.GOLDEN_APPLE",   () -> Items.GOLDEN_APPLE),
            SimpleTest.notNull("Items.COOKED_BEEF",    () -> Items.COOKED_BEEF),
            SimpleTest.notNull("Items.BREAD",          () -> Items.BREAD),
            // Misc
            SimpleTest.notNull("Items.BOOK",           () -> Items.BOOK),
            SimpleTest.notNull("Items.ENCHANTED_BOOK", () -> Items.ENCHANTED_BOOK),
            SimpleTest.notNull("Items.WRITTEN_BOOK",   () -> Items.WRITTEN_BOOK),
            SimpleTest.notNull("Items.ARROW",          () -> Items.ARROW),
            SimpleTest.notNull("Items.TIPPED_ARROW",   () -> Items.TIPPED_ARROW),
            SimpleTest.notNull("Items.SADDLE",         () -> Items.SADDLE)
        );
    }
}
