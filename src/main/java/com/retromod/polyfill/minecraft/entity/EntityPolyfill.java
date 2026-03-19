/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.entity;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for entity class renames from The Flattening (1.13) through 1.21.
 *
 * Minecraft's entity hierarchy was massively restructured during the
 * Flattening (1.13) and subsequent updates. Old Forge mods reference
 * the pre-Flattening class names (e.g. EntityLivingBase, EntityPlayer)
 * which no longer exist in modern Minecraft. This provider registers
 * class redirects so that bytecode referencing old entity classes is
 * rewritten to use the modern Mojang-mapped names.
 *
 * Covers:
 * - Core entity hierarchy (EntityLivingBase, EntityLiving, EntityCreature, etc.)
 * - Player entities (EntityPlayer, EntityPlayerMP)
 * - Item/XP entities (EntityItem, EntityXPOrb)
 * - Passive mobs (EntityAnimal, EntityVillager)
 * - Projectiles (EntityArrow)
 * - AI system (EntityAIBase, EntityAITasks, all goal classes)
 */
public class EntityPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Entity Class Renames";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // Core entity hierarchy
            "net/minecraft/entity/EntityLivingBase",
            "net/minecraft/entity/EntityLiving",
            "net/minecraft/entity/EntityCreature",
            "net/minecraft/entity/EntityAgeable",
            "net/minecraft/entity/monster/EntityMob",

            // Player entities
            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/entity/player/EntityPlayerMP",

            // Item and XP entities
            "net/minecraft/entity/item/EntityItem",
            "net/minecraft/entity/item/EntityXPOrb",

            // Passive mobs
            "net/minecraft/entity/passive/EntityAnimal",
            "net/minecraft/entity/passive/EntityVillager",

            // Projectiles
            "net/minecraft/entity/projectile/EntityArrow",

            // AI system
            "net/minecraft/entity/ai/EntityAIBase",
            "net/minecraft/entity/ai/EntityAITasks",
            "net/minecraft/entity/ai/EntityAIAttackMelee",
            "net/minecraft/entity/ai/EntityAIFollowOwner",
            "net/minecraft/entity/ai/EntityAINearestAttackableTarget",
            "net/minecraft/entity/ai/EntityAIWander",
            "net/minecraft/entity/ai/EntityAISwimming",
            "net/minecraft/entity/ai/EntityAILookIdle",
            "net/minecraft/entity/ai/EntityAIWatchClosest"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // Pure class redirects — no stub implementations needed.
        // All old entity classes map directly to modern Mojang-named equivalents.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // Core entity hierarchy
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityLivingBase",
            "net/minecraft/world/entity/LivingEntity");
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityLiving",
            "net/minecraft/world/entity/Mob");
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityCreature",
            "net/minecraft/world/entity/PathfinderMob");
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityAgeable",
            "net/minecraft/world/entity/AgeableMob");
        transformer.registerClassRedirect(
            "net/minecraft/entity/monster/EntityMob",
            "net/minecraft/world/entity/monster/Monster");

        // Player entities
        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/world/entity/player/Player");
        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayerMP",
            "net/minecraft/server/level/ServerPlayer");

        // Item and XP entities
        transformer.registerClassRedirect(
            "net/minecraft/entity/item/EntityItem",
            "net/minecraft/world/entity/item/ItemEntity");
        transformer.registerClassRedirect(
            "net/minecraft/entity/item/EntityXPOrb",
            "net/minecraft/world/entity/ExperienceOrb");

        // Passive mobs
        transformer.registerClassRedirect(
            "net/minecraft/entity/passive/EntityAnimal",
            "net/minecraft/world/entity/animal/Animal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/passive/EntityVillager",
            "net/minecraft/world/entity/npc/Villager");

        // Projectiles
        transformer.registerClassRedirect(
            "net/minecraft/entity/projectile/EntityArrow",
            "net/minecraft/world/entity/projectile/AbstractArrow");

        // AI system — the entire EntityAI* hierarchy was replaced with Goal-based classes
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAIBase",
            "net/minecraft/world/entity/ai/goal/Goal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAITasks",
            "net/minecraft/world/entity/ai/goal/GoalSelector");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAIAttackMelee",
            "net/minecraft/world/entity/ai/goal/MeleeAttackGoal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAIFollowOwner",
            "net/minecraft/world/entity/ai/goal/FollowOwnerGoal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAINearestAttackableTarget",
            "net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAIWander",
            "net/minecraft/world/entity/ai/goal/WaterAvoidingRandomStrollGoal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAISwimming",
            "net/minecraft/world/entity/ai/goal/FloatGoal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAILookIdle",
            "net/minecraft/world/entity/ai/goal/RandomLookAroundGoal");
        transformer.registerClassRedirect(
            "net/minecraft/entity/ai/EntityAIWatchClosest",
            "net/minecraft/world/entity/ai/goal/LookAtPlayerGoal");
    }
}
