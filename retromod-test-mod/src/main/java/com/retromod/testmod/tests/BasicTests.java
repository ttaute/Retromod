/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;

import java.util.List;

/**
 * Sanity checks that don't touch any specific MC API. If even these fail,
 * something is very wrong with the entry point or class loader.
 */
public final class BasicTests {

    private BasicTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("mod loaded", TestResult::success)
        );
    }
}
