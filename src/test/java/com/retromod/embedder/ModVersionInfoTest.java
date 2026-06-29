/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.embedder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link ModVersionInfo#needsTransformation(String)} (#84).
 *
 * <p>Forge mods declare their MC dependency as a range, so the detector reports a
 * target like {@code "1.20"} or {@code "1.20.1"}. needsTransformation compares at
 * minor precision: same major.minor as the host means no transform.
 */
class ModVersionInfoTest {

    private static ModVersionInfo forMc(String targetMc) {
        return new ModVersionInfo(
                "testmod", "1.0.0", targetMc, "forge", "47.4.20",
                Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("#84: a 1.20 mod (range lower bound) on a 1.20.1 host is NOT transformed")
    void sameMinorRangeLowerBoundNotTransformed() {
        assertFalse(forMc("1.20").needsTransformation("1.20.1"),
                "a mod whose MC range floor is 1.20 runs natively on 1.20.1 - leave it alone");
    }

    @Test
    @DisplayName("#84: a 1.20.1 mod on a 1.20.1 host is NOT transformed")
    void exactSameVersionNotTransformed() {
        assertFalse(forMc("1.20.1").needsTransformation("1.20.1"));
    }

    @Test
    @DisplayName("#84: a 1.20.4 mod on a 1.20.1 host is NOT transformed (same minor, newer patch)")
    void newerPatchSameMinorNotTransformed() {
        assertFalse(forMc("1.20.4").needsTransformation("1.20.1"),
                "patch differences within a minor don't need bytecode translation");
    }

    @Test
    @DisplayName("An older-minor mod IS transformed (1.20.1 mod on a 1.21.4 host)")
    void olderMinorIsTransformed() {
        assertTrue(forMc("1.20.1").needsTransformation("1.21.4"));
    }

    @Test
    @DisplayName("An older PATCH in the same minor IS transformed (1.21.1 mod on a 1.21.11 host)")
    void olderPatchSameMinorIsTransformed() {
        // a specific older patch (1.21.1) needs translation; only a bare major.minor floor is skipped
        assertTrue(forMc("1.21.1").needsTransformation("1.21.11"),
                "1.21.1 → 1.21.11 is a real translation and must not be skipped");
    }

    @Test
    @DisplayName("Whole-minor floor vs specific patch: 1.21 floor skipped on 1.21.11, 1.21.1 not")
    void minorFloorVsSpecificPatch() {
        assertFalse(forMc("1.21").needsTransformation("1.21.11"),
                "a [1.21,) range floor reads as whole-minor 1.21 - native on any 1.21.x host");
        assertTrue(forMc("1.21.1").needsTransformation("1.21.11"),
                "but a specific 1.21.1 target is an older patch that needs translation");
    }

    @Test
    @DisplayName("A much older mod IS transformed (1.16.5 mod on 26.1)")
    void muchOlderIsTransformed() {
        assertTrue(forMc("1.16.5").needsTransformation("26.1"));
    }

    @Test
    @DisplayName("A null or unreadable target MC version is NOT transformed (no guessing)")
    void nullTargetNotTransformed() {
        assertFalse(forMc(null).needsTransformation("1.20.1"));
        assertFalse(forMc("unknown").needsTransformation("1.20.1"),
                "unparseable version must not be transformed on a guess");
    }

    @Test
    @DisplayName("A newer-minor mod is NOT transformed (forward-translation isn't supported)")
    void newerMinorNotTransformed() {
        assertFalse(forMc("1.21.4").needsTransformation("1.20.1"),
                "Retromod only translates old → new, never new → old");
    }
}
