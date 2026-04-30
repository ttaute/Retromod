/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Identifier / ResourceLocation. The two-arg constructor is a constructor →
 * factory redirect (RetroMod rewrites {@code NEW + DUP + INVOKESPECIAL} to
 * {@code INVOKESTATIC} of the new factory method). The single-arg
 * colon-separated form goes through a slightly different path. {@code tryParse}
 * is just a static method redirect.
 */
public final class IdentifierTests {

    private IdentifierTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("Identifier(ns, path)", () -> {
                Identifier id = new Identifier("retromod_test_mod", "x");
                if (id == null) return TestResult.fail("null");
                if (!"retromod_test_mod:x".equals(id.toString())) {
                    return TestResult.fail("toString=" + id);
                }
                return TestResult.success();
            }),
            new SimpleTest("Identifier(\"ns:path\")", () -> {
                Identifier id = new Identifier("retromod_test_mod:y");
                if (id == null) return TestResult.fail("null");
                if (!"retromod_test_mod".equals(id.getNamespace())
                    || !"y".equals(id.getPath())) {
                    return TestResult.fail("ns=" + id.getNamespace() + " path=" + id.getPath());
                }
                return TestResult.success();
            }),
            new SimpleTest("Identifier.tryParse(invalid)", () -> {
                Identifier bad = Identifier.tryParse("BAD ID WITH SPACES");
                return bad == null
                    ? TestResult.success()
                    : TestResult.fail("expected null, got " + bad);
            })
        );
    }
}
