/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Text / Component API. Source compiles against Yarn's
 * {@code net.minecraft.text.Text}; after RetroMod translates forward, that
 * resolves to Mojang's {@code net.minecraft.network.chat.Component}. Lots of
 * common patterns here — {@code literal}, {@code translatable}, copy + append,
 * formatting — each one is a different method redirect.
 */
public final class TextTests {

    private TextTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("Text.literal", () -> {
                Text t = Text.literal("hello");
                if (t == null) return TestResult.fail("returned null");
                if (!"hello".equals(t.getString())) {
                    return TestResult.fail("getString=" + t.getString());
                }
                return TestResult.success();
            }),
            new SimpleTest("Text.translatable", () -> {
                Text t = Text.translatable("retromod.test.key");
                return t != null
                    ? TestResult.success()
                    : TestResult.fail("returned null");
            }),
            new SimpleTest("Text.empty", () -> {
                Text t = Text.empty();
                if (t == null) return TestResult.fail("returned null");
                if (!"".equals(t.getString())) {
                    return TestResult.fail("getString=" + t.getString());
                }
                return TestResult.success();
            }),
            new SimpleTest("Text.copy().append()", () -> {
                MutableText t = Text.literal("a").copy().append(Text.literal("b"));
                if (!"ab".equals(t.getString())) {
                    return TestResult.fail("getString=" + t.getString());
                }
                return TestResult.success();
            }),
            new SimpleTest("Text.copy().formatted(Formatting.RED)", () -> {
                MutableText t = Text.literal("x").copy().formatted(Formatting.RED);
                return t != null
                    ? TestResult.success()
                    : TestResult.fail("returned null");
            })
        );
    }
}
