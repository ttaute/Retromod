/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Registry lookups. In Yarn 1.20.1 the holder is {@code Registries}; in newer
 * Mojang-mapped MC it became {@code BuiltInRegistries}. Both the class
 * rename and the static-field references on it have to redirect for these
 * to resolve.
 */
public final class RegistryTests {

    private RegistryTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("Registries.BLOCK.get(stone)", () -> {
                Block b = Registries.BLOCK.get(new Identifier("minecraft", "stone"));
                if (b == null) return TestResult.fail("null");
                if (b != Blocks.STONE) return TestResult.fail("not == Blocks.STONE");
                return TestResult.success();
            }),
            new SimpleTest("Registries.ITEM.get(diamond)", () -> {
                Item i = Registries.ITEM.get(new Identifier("minecraft", "diamond"));
                if (i == null) return TestResult.fail("null");
                if (i != Items.DIAMOND) return TestResult.fail("not == Items.DIAMOND");
                return TestResult.success();
            }),
            new SimpleTest("Registries.ENTITY_TYPE.get(zombie)", () -> {
                EntityType<?> e = Registries.ENTITY_TYPE.get(new Identifier("minecraft", "zombie"));
                return e != null && e == EntityType.ZOMBIE
                    ? TestResult.success()
                    : TestResult.fail("got " + e);
            }),
            new SimpleTest("Registries.FLUID.get(water)", () -> {
                Fluid f = Registries.FLUID.get(new Identifier("minecraft", "water"));
                return f != null
                    ? TestResult.success()
                    : TestResult.fail("null");
            }),
            new SimpleTest("Registries.SOUND_EVENT.get(stone_break)", () -> {
                SoundEvent s = Registries.SOUND_EVENT.get(SoundEvents.BLOCK_STONE_BREAK.getId());
                return s != null
                    ? TestResult.success()
                    : TestResult.fail("null");
            })
        );
    }
}
