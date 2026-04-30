/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;

import java.util.List;

/**
 * DataFixerUpper {@code Codec} primitives. Mojang's serialization library
 * gets bumped frequently and various concrete classes have flipped to
 * interfaces between MC versions ({@code DataResult}, {@code DynamicOps},
 * {@code MapLike}, {@code Lifecycle}). RetroMod has these in
 * {@code KNOWN_INTERFACES} so the right INVOKE opcode is emitted; these
 * tests confirm the round-trip still works after translation.
 */
public final class CodecTests {

    private CodecTests() {}

    public static List<Test> all() {
        return List.of(
            SimpleTest.notNull("Codec.STRING", () -> Codec.STRING),
            SimpleTest.notNull("Codec.INT",    () -> Codec.INT),
            SimpleTest.notNull("Codec.BOOL",   () -> Codec.BOOL),
            SimpleTest.notNull("Codec.DOUBLE", () -> Codec.DOUBLE),
            SimpleTest.notNull("JsonOps.INSTANCE", () -> JsonOps.INSTANCE),
            new SimpleTest("Codec.STRING.encodeStart round-trip", () -> {
                DataResult<?> r = Codec.STRING.encodeStart(JsonOps.INSTANCE, "hello");
                if (r == null) return TestResult.fail("null DataResult");
                Object value = r.result().orElse(null);
                if (value == null) {
                    return TestResult.fail("DataResult has no value");
                }
                String json = value.toString();
                return "\"hello\"".equals(json)
                    ? TestResult.success()
                    : TestResult.fail("encoded as " + json);
            })
        );
    }
}
