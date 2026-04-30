/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.List;

/**
 * Sound + particle static accessors. These classes have many fields each
 * (every vanilla sound and particle type), and field renames between MC
 * versions are common — these tests would fail noisily if a redirect on
 * any frequently-used field was missing.
 */
public final class SoundParticleTests {

    private SoundParticleTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("SoundEvents.BLOCK_STONE_BREAK", () ->
                SoundEvents.BLOCK_STONE_BREAK != null
                    ? TestResult.success()
                    : TestResult.fail("null")
            ),
            new SimpleTest("SoundEvents.ENTITY_PLAYER_HURT", () ->
                SoundEvents.ENTITY_PLAYER_HURT != null
                    ? TestResult.success()
                    : TestResult.fail("null")
            ),
            new SimpleTest("ParticleTypes.FLAME", () ->
                ParticleTypes.FLAME != null
                    ? TestResult.success()
                    : TestResult.fail("null")
            ),
            new SimpleTest("ParticleTypes.BUBBLE", () ->
                ParticleTypes.BUBBLE != null
                    ? TestResult.success()
                    : TestResult.fail("null")
            )
        );
    }
}
