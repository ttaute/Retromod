/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.List;

/**
 * NBT (named binary tag). Used heavily by mods for save data, item NBT, and
 * networking. Yarn name {@code NbtCompound} → Mojang {@code CompoundTag}.
 */
public final class NbtTests {

    private NbtTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("NbtCompound.putString round-trip", () -> {
                NbtCompound c = new NbtCompound();
                c.putString("key", "value");
                return "value".equals(c.getString("key"))
                    ? TestResult.success()
                    : TestResult.fail("getString=" + c.getString("key"));
            }),
            new SimpleTest("NbtCompound.putInt round-trip", () -> {
                NbtCompound c = new NbtCompound();
                c.putInt("k", 42);
                return c.getInt("k") == 42
                    ? TestResult.success()
                    : TestResult.fail("getInt=" + c.getInt("k"));
            }),
            new SimpleTest("NbtCompound.contains", () -> {
                NbtCompound c = new NbtCompound();
                c.putBoolean("here", true);
                if (!c.contains("here")) return TestResult.fail("contains() returned false");
                if (c.contains("not_here")) return TestResult.fail("returned true for missing key");
                return TestResult.success();
            }),
            new SimpleTest("NbtList.add + size + get", () -> {
                NbtList list = new NbtList();
                list.add(NbtString.of("a"));
                list.add(NbtString.of("b"));
                if (list.size() != 2) return TestResult.fail("size=" + list.size());
                if (!"a".equals(list.getString(0))) return TestResult.fail("get(0)=" + list.getString(0));
                return TestResult.success();
            })
        );
    }
}
