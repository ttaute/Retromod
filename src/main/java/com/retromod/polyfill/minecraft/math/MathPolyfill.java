/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.math;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for math and vector class changes across Minecraft versions.
 *
 * In 1.19.3, Minecraft migrated from its custom math classes to the JOML
 * (Java OpenGL Math Library). All vector, matrix, and quaternion classes
 * in {@code net.minecraft.util.math} were replaced by their JOML equivalents
 * in {@code org.joml}.
 *
 * Additionally covers:
 * - MathHelper -> Mth rename (intermediary -> Mojang mapping)
 * - MutableBoundingBox -> BoundingBox (structure package relocation)
 * - Vec2f relocation
 *
 * Covered changes:
 * - Vec3f -> org.joml.Vector3f (1.19.3 JOML migration)
 * - Matrix4f -> org.joml.Matrix4f (1.19.3 JOML migration)
 * - Matrix3f -> org.joml.Matrix3f (1.19.3 JOML migration)
 * - Quaternion -> org.joml.Quaternionf (1.19.3 JOML migration)
 * - Vec2f -> net.minecraft.world.phys.Vec2 or org.joml.Vector2f
 * - MathHelper -> net.minecraft.util.Mth (Mojang mapping)
 * - MutableBoundingBox -> BoundingBox (structure package move)
 */
public class MathPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Math/Vector Class Changes";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // JOML migration (1.19.3) - custom MC math classes removed
            "net/minecraft/util/math/Vec3f",
            "net/minecraft/util/math/Matrix4f",
            "net/minecraft/util/math/Matrix3f",
            "net/minecraft/util/math/Quaternion",
            "net/minecraft/util/math/Vec2f",

            // Mojang mapping renames
            "net/minecraft/util/math/MathHelper",

            // Structure package relocation
            "net/minecraft/util/math/MutableBoundingBox",
            "net/minecraft/world/gen/feature/structure/MutableBoundingBox"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed - pure class redirects to JOML and Mojang names
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // JOML migration (1.19.3)
        // Minecraft replaced its custom math classes with JOML equivalents.
        // JOML is bundled with Minecraft since 1.19.3, so these classes are
        // available at runtime.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/util/math/Vec3f",
            "org/joml/Vector3f");

        transformer.registerClassRedirect(
            "net/minecraft/util/math/Matrix4f",
            "org/joml/Matrix4f");

        transformer.registerClassRedirect(
            "net/minecraft/util/math/Matrix3f",
            "org/joml/Matrix3f");

        transformer.registerClassRedirect(
            "net/minecraft/util/math/Quaternion",
            "org/joml/Quaternionf");

        // Vec2f has two possible targets depending on context:
        // - For general 2D math: org.joml.Vector2f
        // - For MC-specific 2D (like player rotation): net.minecraft.world.phys.Vec2
        // We redirect to Vec2 as it's more commonly used in MC mod code.
        transformer.registerClassRedirect(
            "net/minecraft/util/math/Vec2f",
            "net/minecraft/world/phys/Vec2");

        // =====================================================================
        // Vec3f method redirects
        // The old Vec3f had MC-specific methods that differ from JOML's API.
        // =====================================================================

        // Vec3f.getX/Y/Z() -> Vector3f.x/y/z (fields, not methods in JOML)
        // But JOML also has x(), y(), z() accessor methods
        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Vec3f", "getX",
            "()F",
            "org/joml/Vector3f", "x",
            "()F");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Vec3f", "getY",
            "()F",
            "org/joml/Vector3f", "y",
            "()F");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Vec3f", "getZ",
            "()F",
            "org/joml/Vector3f", "z",
            "()F");

        // =====================================================================
        // Quaternion method redirects
        // MC's Quaternion had different method names than JOML's Quaternionf.
        // =====================================================================

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Quaternion", "getX",
            "()F",
            "org/joml/Quaternionf", "x",
            "()F");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Quaternion", "getY",
            "()F",
            "org/joml/Quaternionf", "y",
            "()F");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Quaternion", "getZ",
            "()F",
            "org/joml/Quaternionf", "z",
            "()F");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Quaternion", "getW",
            "()F",
            "org/joml/Quaternionf", "w",
            "()F");

        // hamiltonProduct -> mul (JOML equivalent)
        transformer.registerMethodRedirect(
            "net/minecraft/util/math/Quaternion", "hamiltonProduct",
            "(Lnet/minecraft/util/math/Quaternion;)V",
            "org/joml/Quaternionf", "mul",
            "(Lorg/joml/Quaternionfc;)Lorg/joml/Quaternionf;");

        // =====================================================================
        // MathHelper -> Mth (Mojang mapping rename)
        // The class was renamed from the intermediary name MathHelper to the
        // Mojang official name Mth.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/util/math/MathHelper",
            "net/minecraft/util/Mth");

        // Also handle the Forge-era package path
        transformer.registerClassRedirect(
            "net/minecraft/util/MathHelper",
            "net/minecraft/util/Mth");

        // Common MathHelper methods that were renamed
        transformer.registerMethodRedirect(
            "net/minecraft/util/math/MathHelper", "floor_double",
            "(D)I",
            "net/minecraft/util/Mth", "floor",
            "(D)I");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/MathHelper", "floor_float",
            "(F)I",
            "net/minecraft/util/Mth", "floor",
            "(F)I");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/MathHelper", "ceiling_double_int",
            "(D)I",
            "net/minecraft/util/Mth", "ceil",
            "(D)I");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/MathHelper", "clamp_double",
            "(DDD)D",
            "net/minecraft/util/Mth", "clamp",
            "(DDD)D");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/MathHelper", "clamp_float",
            "(FFF)F",
            "net/minecraft/util/Mth", "clamp",
            "(FFF)F");

        transformer.registerMethodRedirect(
            "net/minecraft/util/math/MathHelper", "clamp_int",
            "(III)I",
            "net/minecraft/util/Mth", "clamp",
            "(III)I");

        // =====================================================================
        // MutableBoundingBox -> BoundingBox (structure package relocation)
        // Used in world generation / structure code.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/util/math/MutableBoundingBox",
            "net/minecraft/world/level/levelgen/structure/BoundingBox");

        transformer.registerClassRedirect(
            "net/minecraft/world/gen/feature/structure/MutableBoundingBox",
            "net/minecraft/world/level/levelgen/structure/BoundingBox");
    }
}
