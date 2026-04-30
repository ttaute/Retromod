/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Math types — {@code BlockPos}, {@code Vec3d}, {@code Box}/{@code AABB},
 * {@code Direction}. These constructors and methods have stayed mostly
 * stable across MC versions but the package paths and yarn/Mojang names
 * differ, so they're a good cheap sanity check.
 */
public final class MathTests {

    private MathTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("BlockPos.ORIGIN", () -> {
                BlockPos p = BlockPos.ORIGIN;
                return p.getX() == 0 && p.getY() == 0 && p.getZ() == 0
                    ? TestResult.success()
                    : TestResult.fail("got " + p);
            }),
            new SimpleTest("new BlockPos(1, 2, 3)", () -> {
                BlockPos p = new BlockPos(1, 2, 3);
                return p.getX() == 1 && p.getY() == 2 && p.getZ() == 3
                    ? TestResult.success()
                    : TestResult.fail("got " + p);
            }),
            new SimpleTest("BlockPos.up()", () -> {
                BlockPos p = new BlockPos(0, 0, 0).up();
                return p.getY() == 1
                    ? TestResult.success()
                    : TestResult.fail("y=" + p.getY());
            }),
            new SimpleTest("Vec3d.ZERO", () -> {
                Vec3d v = Vec3d.ZERO;
                return v.x == 0.0 && v.y == 0.0 && v.z == 0.0
                    ? TestResult.success()
                    : TestResult.fail("got " + v);
            }),
            new SimpleTest("new Vec3d(1.5, 2.5, 3.5)", () -> {
                Vec3d v = new Vec3d(1.5, 2.5, 3.5);
                return v.x == 1.5 && v.y == 2.5 && v.z == 3.5
                    ? TestResult.success()
                    : TestResult.fail("got " + v);
            }),
            new SimpleTest("Direction.NORTH.getOpposite() == SOUTH", () -> {
                Direction d = Direction.NORTH.getOpposite();
                return d == Direction.SOUTH
                    ? TestResult.success()
                    : TestResult.fail("got " + d);
            }),
            new SimpleTest("new Box(0,0,0,1,1,1)", () -> {
                Box b = new Box(0, 0, 0, 1, 1, 1);
                return b.getXLength() == 1.0
                    ? TestResult.success()
                    : TestResult.fail("xLength=" + b.getXLength());
            })
        );
    }
}
