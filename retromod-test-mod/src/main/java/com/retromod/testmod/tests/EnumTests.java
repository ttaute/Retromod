/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * Vanilla enums. Mods use these constantly: {@code Hand.MAIN_HAND} for
 * tool-use callbacks, {@code Direction} for block placement, {@code Formatting}
 * for chat colors, {@code GameMode} for permissions checks. Many of these
 * enums had values added or repackaged across MC versions.
 */
public final class EnumTests {

    private EnumTests() {}

    public static List<Test> all() {
        return List.of(
            // Direction
            SimpleTest.notNull("Direction.NORTH",        () -> Direction.NORTH),
            SimpleTest.notNull("Direction.SOUTH",        () -> Direction.SOUTH),
            SimpleTest.notNull("Direction.EAST",         () -> Direction.EAST),
            SimpleTest.notNull("Direction.WEST",         () -> Direction.WEST),
            SimpleTest.notNull("Direction.UP",           () -> Direction.UP),
            SimpleTest.notNull("Direction.DOWN",         () -> Direction.DOWN),
            SimpleTest.notNull("Direction.Axis.X",       () -> Direction.Axis.X),
            SimpleTest.notNull("Direction.Axis.Y",       () -> Direction.Axis.Y),
            SimpleTest.notNull("Direction.Axis.Z",       () -> Direction.Axis.Z),
            new SimpleTest("Direction.byName(\"north\")", () ->
                Direction.byName("north") == Direction.NORTH
                    ? TestResult.success() : TestResult.fail("not == NORTH")),
            // Hand
            SimpleTest.notNull("Hand.MAIN_HAND",         () -> Hand.MAIN_HAND),
            SimpleTest.notNull("Hand.OFF_HAND",          () -> Hand.OFF_HAND),
            // Equipment
            SimpleTest.notNull("EquipmentSlot.HEAD",     () -> EquipmentSlot.HEAD),
            SimpleTest.notNull("EquipmentSlot.CHEST",    () -> EquipmentSlot.CHEST),
            SimpleTest.notNull("EquipmentSlot.LEGS",     () -> EquipmentSlot.LEGS),
            SimpleTest.notNull("EquipmentSlot.FEET",     () -> EquipmentSlot.FEET),
            SimpleTest.notNull("EquipmentSlot.MAINHAND", () -> EquipmentSlot.MAINHAND),
            SimpleTest.notNull("EquipmentSlot.OFFHAND",  () -> EquipmentSlot.OFFHAND),
            // ActionResult
            SimpleTest.notNull("ActionResult.SUCCESS",   () -> ActionResult.SUCCESS),
            SimpleTest.notNull("ActionResult.FAIL",      () -> ActionResult.FAIL),
            SimpleTest.notNull("ActionResult.PASS",      () -> ActionResult.PASS),
            SimpleTest.notNull("ActionResult.CONSUME",   () -> ActionResult.CONSUME),
            // Formatting
            SimpleTest.notNull("Formatting.RED",         () -> Formatting.RED),
            SimpleTest.notNull("Formatting.GREEN",       () -> Formatting.GREEN),
            SimpleTest.notNull("Formatting.BLUE",        () -> Formatting.BLUE),
            SimpleTest.notNull("Formatting.BOLD",        () -> Formatting.BOLD),
            SimpleTest.notNull("Formatting.ITALIC",      () -> Formatting.ITALIC),
            // GameMode + Difficulty
            SimpleTest.notNull("GameMode.SURVIVAL",      () -> GameMode.SURVIVAL),
            SimpleTest.notNull("GameMode.CREATIVE",      () -> GameMode.CREATIVE),
            SimpleTest.notNull("GameMode.ADVENTURE",     () -> GameMode.ADVENTURE),
            SimpleTest.notNull("GameMode.SPECTATOR",     () -> GameMode.SPECTATOR),
            SimpleTest.notNull("Difficulty.PEACEFUL",    () -> Difficulty.PEACEFUL),
            SimpleTest.notNull("Difficulty.EASY",        () -> Difficulty.EASY),
            SimpleTest.notNull("Difficulty.NORMAL",      () -> Difficulty.NORMAL),
            SimpleTest.notNull("Difficulty.HARD",        () -> Difficulty.HARD)
        );
    }
}
