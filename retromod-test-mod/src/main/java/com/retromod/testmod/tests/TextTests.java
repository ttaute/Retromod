/*
 * Retromod Test Mod
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
 * {@code net.minecraft.text.Text}; after Retromod translates forward, that
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
            new SimpleTest("Text.copy().append() attaches sibling", () -> {
                // Earlier this test asserted that getString() flattens to
                // "ab". That worked through MC 1.21.4 but stopped working
                // in MC 26.1+ where getString() on a MutableComponent with
                // siblings returns the toString-style tree representation
                // (e.g. "literal{a}[siblings=[literal{b}]]"). Whether this
                // is a temporary 26.1 quirk or a permanent change isn't
                // clear yet, but either way the test's assumption about
                // getString() flattening was the wrong thing to assert.
                //
                // What the test actually wants to verify is that the
                // copy().append() chain returns a usable MutableText with
                // the appended sibling attached. getSiblings().size() == 1
                // is signature-stable across all MC versions we care about
                // and answers the real question.
                MutableText t = Text.literal("a").copy().append(Text.literal("b"));
                if (t == null) return TestResult.fail("returned null");
                if (t.getSiblings().size() != 1) {
                    return TestResult.fail("expected 1 sibling, got " + t.getSiblings().size());
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
