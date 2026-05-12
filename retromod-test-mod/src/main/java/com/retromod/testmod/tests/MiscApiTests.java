/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.UUID;

/**
 * Grab-bag of utility APIs that don't fit elsewhere — {@code Util},
 * {@code MathHelper}, {@code Random}, {@code SoundCategory}, plus more
 * sound and particle accessors than {@link SoundParticleTests} alone covered.
 */
public final class MiscApiTests {

    private MiscApiTests() {}

    public static List<Test> all() {
        return List.of(
            // Util
            new SimpleTest("Util.NIL_UUID", () ->
                Util.NIL_UUID != null && Util.NIL_UUID.equals(new UUID(0L, 0L))
                    ? TestResult.success()
                    : TestResult.fail("got " + Util.NIL_UUID)),
            new SimpleTest("Util.getMeasuringTimeMs() > 0", () -> {
                long t = Util.getMeasuringTimeMs();
                return t > 0
                    ? TestResult.success()
                    : TestResult.fail("got " + t);
            }),
            // MathHelper
            new SimpleTest("MathHelper.clamp(5, 0, 10) == 5", () ->
                MathHelper.clamp(5, 0, 10) == 5
                    ? TestResult.success()
                    : TestResult.fail("got " + MathHelper.clamp(5, 0, 10))),
            new SimpleTest("MathHelper.clamp(-1, 0, 10) == 0", () ->
                MathHelper.clamp(-1, 0, 10) == 0
                    ? TestResult.success()
                    : TestResult.fail("got " + MathHelper.clamp(-1, 0, 10))),
            new SimpleTest("MathHelper.clamp(99, 0, 10) == 10", () ->
                MathHelper.clamp(99, 0, 10) == 10
                    ? TestResult.success()
                    : TestResult.fail("got " + MathHelper.clamp(99, 0, 10))),
            new SimpleTest("MathHelper.floor(2.7) == 2", () ->
                MathHelper.floor(2.7) == 2
                    ? TestResult.success()
                    : TestResult.fail("got " + MathHelper.floor(2.7))),
            new SimpleTest("MathHelper.ceil(2.1) == 3", () ->
                MathHelper.ceil(2.1) == 3
                    ? TestResult.success()
                    : TestResult.fail("got " + MathHelper.ceil(2.1))),
            // Random
            new SimpleTest("Random.create() not null", () ->
                Random.create() != null
                    ? TestResult.success()
                    : TestResult.fail("null")),
            new SimpleTest("Random.create(42).nextInt() deterministic", () -> {
                Random r1 = Random.create(42);
                Random r2 = Random.create(42);
                int a = r1.nextInt();
                int b = r2.nextInt();
                return a == b
                    ? TestResult.success()
                    : TestResult.fail("seeded RNG diverged: " + a + " != " + b);
            }),
            // SoundCategory
            SimpleTest.notNull("SoundCategory.MASTER",  () -> SoundCategory.MASTER),
            SimpleTest.notNull("SoundCategory.MUSIC",   () -> SoundCategory.MUSIC),
            SimpleTest.notNull("SoundCategory.PLAYERS", () -> SoundCategory.PLAYERS),
            SimpleTest.notNull("SoundCategory.HOSTILE", () -> SoundCategory.HOSTILE),
            SimpleTest.notNull("SoundCategory.AMBIENT", () -> SoundCategory.AMBIENT),
            // More sounds
            SimpleTest.notNull("SoundEvents.ENTITY_PLAYER_DEATH", () -> SoundEvents.ENTITY_PLAYER_DEATH),
            SimpleTest.notNull("SoundEvents.ENTITY_ZOMBIE_AMBIENT", () -> SoundEvents.ENTITY_ZOMBIE_AMBIENT),
            SimpleTest.notNull("SoundEvents.BLOCK_GRASS_BREAK", () -> SoundEvents.BLOCK_GRASS_BREAK),
            SimpleTest.notNull("SoundEvents.BLOCK_NOTE_BLOCK_BELL", () -> SoundEvents.BLOCK_NOTE_BLOCK_BELL),
            // More particles
            SimpleTest.notNull("ParticleTypes.HEART",       () -> ParticleTypes.HEART),
            SimpleTest.notNull("ParticleTypes.SMOKE",       () -> ParticleTypes.SMOKE),
            SimpleTest.notNull("ParticleTypes.LARGE_SMOKE", () -> ParticleTypes.LARGE_SMOKE),
            SimpleTest.notNull("ParticleTypes.EXPLOSION",   () -> ParticleTypes.EXPLOSION),
            SimpleTest.notNull("ParticleTypes.ENCHANT",     () -> ParticleTypes.ENCHANT),
            SimpleTest.notNull("ParticleTypes.SOUL",        () -> ParticleTypes.SOUL)
        );
    }
}
