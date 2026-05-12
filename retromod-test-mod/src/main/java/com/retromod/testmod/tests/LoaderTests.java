/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

/**
 * Fabric Loader APIs. These aren't part of vanilla MC at all, so Retromod
 * shouldn't be touching them — the test confirms that loader-API references
 * pass through the transformer untouched and still work after Retromod's
 * pipeline runs.
 *
 * <p>Bonus: confirms that Retromod itself reports as loaded, which is a
 * useful sanity check for any test reading these results.
 */
public final class LoaderTests {

    private LoaderTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("FabricLoader.getInstance() not null", () -> {
                FabricLoader loader = FabricLoader.getInstance();
                return loader != null
                    ? TestResult.success()
                    : TestResult.fail("null");
            }),
            new SimpleTest("FabricLoader.isModLoaded(\"fabricloader\")", () -> {
                return FabricLoader.getInstance().isModLoaded("fabricloader")
                    ? TestResult.success()
                    : TestResult.fail("returned false — impossible if we're running");
            }),
            new SimpleTest("FabricLoader.isModLoaded(\"retromod\")", () -> {
                return FabricLoader.getInstance().isModLoaded("retromod")
                    ? TestResult.success()
                    : TestResult.fail("returned false — Retromod missing?");
            }),
            new SimpleTest("FabricLoader.getGameDir() not null", () -> {
                Path dir = FabricLoader.getInstance().getGameDir();
                return dir != null
                    ? TestResult.success()
                    : TestResult.fail("null");
            })
        );
    }
}
