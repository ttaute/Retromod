/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import net.minecraft.entity.effect.StatusEffects;

import java.util.List;

/**
 * Status effect static accessors ({@code MobEffects}/{@code StatusEffects}
 * across yarn ↔ Mojang). Used by potion mods, cooking mods, ability mods.
 */
public final class StatusEffectTests {

    private StatusEffectTests() {}

    public static List<Test> all() {
        return List.of(
            SimpleTest.notNull("StatusEffects.SPEED",         () -> StatusEffects.SPEED),
            SimpleTest.notNull("StatusEffects.SLOWNESS",      () -> StatusEffects.SLOWNESS),
            SimpleTest.notNull("StatusEffects.HASTE",         () -> StatusEffects.HASTE),
            SimpleTest.notNull("StatusEffects.MINING_FATIGUE",() -> StatusEffects.MINING_FATIGUE),
            SimpleTest.notNull("StatusEffects.STRENGTH",      () -> StatusEffects.STRENGTH),
            SimpleTest.notNull("StatusEffects.JUMP_BOOST",    () -> StatusEffects.JUMP_BOOST),
            SimpleTest.notNull("StatusEffects.NAUSEA",        () -> StatusEffects.NAUSEA),
            SimpleTest.notNull("StatusEffects.REGENERATION",  () -> StatusEffects.REGENERATION),
            SimpleTest.notNull("StatusEffects.RESISTANCE",    () -> StatusEffects.RESISTANCE),
            SimpleTest.notNull("StatusEffects.FIRE_RESISTANCE", () -> StatusEffects.FIRE_RESISTANCE),
            SimpleTest.notNull("StatusEffects.WATER_BREATHING",() -> StatusEffects.WATER_BREATHING),
            SimpleTest.notNull("StatusEffects.INVISIBILITY",  () -> StatusEffects.INVISIBILITY),
            SimpleTest.notNull("StatusEffects.NIGHT_VISION",  () -> StatusEffects.NIGHT_VISION),
            SimpleTest.notNull("StatusEffects.HUNGER",        () -> StatusEffects.HUNGER),
            SimpleTest.notNull("StatusEffects.WEAKNESS",      () -> StatusEffects.WEAKNESS),
            SimpleTest.notNull("StatusEffects.POISON",        () -> StatusEffects.POISON),
            SimpleTest.notNull("StatusEffects.WITHER",        () -> StatusEffects.WITHER),
            SimpleTest.notNull("StatusEffects.ABSORPTION",    () -> StatusEffects.ABSORPTION),
            SimpleTest.notNull("StatusEffects.LUCK",          () -> StatusEffects.LUCK)
        );
    }
}
