/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import net.minecraft.enchantment.Enchantments;

import java.util.List;

/**
 * Vanilla enchantments. In MC 1.21 these moved from a hardcoded class with
 * static {@code Enchantment} fields to a registry-keyed lookup with
 * {@code RegistryKey<Enchantment>} fields. Test compiles against the older
 * shape (1.20.1 yarn); a successful run means Retromod handled the lookup
 * model change for each entry.
 */
public final class EnchantmentTests {

    private EnchantmentTests() {}

    public static List<Test> all() {
        return List.of(
            SimpleTest.notNull("Enchantments.SHARPNESS",        () -> Enchantments.SHARPNESS),
            SimpleTest.notNull("Enchantments.SMITE",            () -> Enchantments.SMITE),
            SimpleTest.notNull("Enchantments.BANE_OF_ARTHROPODS", () -> Enchantments.BANE_OF_ARTHROPODS),
            SimpleTest.notNull("Enchantments.PROTECTION",       () -> Enchantments.PROTECTION),
            SimpleTest.notNull("Enchantments.FIRE_PROTECTION",  () -> Enchantments.FIRE_PROTECTION),
            SimpleTest.notNull("Enchantments.UNBREAKING",       () -> Enchantments.UNBREAKING),
            SimpleTest.notNull("Enchantments.MENDING",          () -> Enchantments.MENDING),
            SimpleTest.notNull("Enchantments.EFFICIENCY",       () -> Enchantments.EFFICIENCY),
            SimpleTest.notNull("Enchantments.FORTUNE",          () -> Enchantments.FORTUNE),
            SimpleTest.notNull("Enchantments.SILK_TOUCH",       () -> Enchantments.SILK_TOUCH),
            SimpleTest.notNull("Enchantments.POWER",            () -> Enchantments.POWER),
            SimpleTest.notNull("Enchantments.INFINITY",         () -> Enchantments.INFINITY),
            SimpleTest.notNull("Enchantments.FEATHER_FALLING",  () -> Enchantments.FEATHER_FALLING),
            SimpleTest.notNull("Enchantments.RESPIRATION",      () -> Enchantments.RESPIRATION),
            SimpleTest.notNull("Enchantments.AQUA_AFFINITY",    () -> Enchantments.AQUA_AFFINITY),
            SimpleTest.notNull("Enchantments.THORNS",           () -> Enchantments.THORNS)
        );
    }
}
