/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.entity;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Redirects pre-Flattening (1.13) entity class names to their modern Mojang-mapped
 * equivalents, so old Forge mods referencing the removed names still link.
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
            "net/minecraft/entity/EntityLivingBase",
            "net/minecraft/entity/EntityLiving",
            "net/minecraft/entity/EntityCreature",
            "net/minecraft/entity/EntityAgeable",
            "net/minecraft/entity/monster/EntityMob",

            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/entity/player/EntityPlayerMP",

            "net/minecraft/entity/item/EntityItem",
            "net/minecraft/entity/item/EntityXPOrb",

            "net/minecraft/entity/passive/EntityAnimal",
            "net/minecraft/entity/passive/EntityVillager",

            "net/minecraft/entity/projectile/EntityArrow",

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
        // Pure class redirects; no stub implementations needed.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
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

        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/world/entity/player/Player");
        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayerMP",
            "net/minecraft/server/level/ServerPlayer");

        transformer.registerClassRedirect(
            "net/minecraft/entity/item/EntityItem",
            "net/minecraft/world/entity/item/ItemEntity");
        transformer.registerClassRedirect(
            "net/minecraft/entity/item/EntityXPOrb",
            "net/minecraft/world/entity/ExperienceOrb");

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

        // AI system: the entire EntityAI* hierarchy was replaced with Goal-based classes
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
