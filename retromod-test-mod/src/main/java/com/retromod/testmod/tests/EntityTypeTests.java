/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import net.minecraft.entity.EntityType;

import java.util.List;

/**
 * {@code EntityType} static fields. Mods that spawn or filter entities all
 * read these - broken redirects here mean broken AI, drops, and spawning logic.
 */
public final class EntityTypeTests {

    private EntityTypeTests() {}

    public static List<Test> all() {
        return List.of(
            // Players + items
            SimpleTest.notNull("EntityType.PLAYER",     () -> EntityType.PLAYER),
            SimpleTest.notNull("EntityType.ITEM",       () -> EntityType.ITEM),
            SimpleTest.notNull("EntityType.EXPERIENCE_ORB", () -> EntityType.EXPERIENCE_ORB),
            // Hostile mobs
            SimpleTest.notNull("EntityType.ZOMBIE",     () -> EntityType.ZOMBIE),
            SimpleTest.notNull("EntityType.SKELETON",   () -> EntityType.SKELETON),
            SimpleTest.notNull("EntityType.CREEPER",    () -> EntityType.CREEPER),
            SimpleTest.notNull("EntityType.SPIDER",     () -> EntityType.SPIDER),
            SimpleTest.notNull("EntityType.ENDERMAN",   () -> EntityType.ENDERMAN),
            SimpleTest.notNull("EntityType.WITCH",      () -> EntityType.WITCH),
            SimpleTest.notNull("EntityType.WITHER",     () -> EntityType.WITHER),
            SimpleTest.notNull("EntityType.ENDER_DRAGON", () -> EntityType.ENDER_DRAGON),
            // Passive mobs
            SimpleTest.notNull("EntityType.COW",        () -> EntityType.COW),
            SimpleTest.notNull("EntityType.PIG",        () -> EntityType.PIG),
            SimpleTest.notNull("EntityType.SHEEP",      () -> EntityType.SHEEP),
            SimpleTest.notNull("EntityType.CHICKEN",    () -> EntityType.CHICKEN),
            SimpleTest.notNull("EntityType.HORSE",      () -> EntityType.HORSE),
            SimpleTest.notNull("EntityType.WOLF",       () -> EntityType.WOLF),
            SimpleTest.notNull("EntityType.CAT",        () -> EntityType.CAT),
            SimpleTest.notNull("EntityType.VILLAGER",   () -> EntityType.VILLAGER),
            SimpleTest.notNull("EntityType.AXOLOTL",    () -> EntityType.AXOLOTL),
            // Projectiles
            SimpleTest.notNull("EntityType.ARROW",      () -> EntityType.ARROW),
            SimpleTest.notNull("EntityType.SNOWBALL",   () -> EntityType.SNOWBALL),
            SimpleTest.notNull("EntityType.FIREBALL",   () -> EntityType.FIREBALL),
            // World objects
            SimpleTest.notNull("EntityType.FALLING_BLOCK", () -> EntityType.FALLING_BLOCK),
            SimpleTest.notNull("EntityType.TNT",        () -> EntityType.TNT),
            SimpleTest.notNull("EntityType.LIGHTNING_BOLT", () -> EntityType.LIGHTNING_BOLT)
        );
    }
}
