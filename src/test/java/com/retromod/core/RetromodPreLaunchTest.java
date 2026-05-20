/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RetromodPreLaunch#isUnobfuscatedTarget(String)} — the gate
 * that decides whether the intermediary→Mojang remap (and 26.1 class moves) are
 * applied.
 *
 * <p>Regression coverage for bugs #21 and #29: applying the 26.1 remap on a
 * pre-26.1 host rewrites a Fabric mod's working intermediary references into
 * Mojang names that don't exist at the pre-26.1 runtime (Identifier,
 * ParticleType, BuiltInRegistries, Component, …) → ClassNotFoundException.
 * The gate must return {@code false} for every legacy {@code 1.x} host and
 * {@code true} for the 26.1+ year-based scheme.
 */
class RetromodPreLaunchTest {

    @Test
    @DisplayName("26.1+ year-scheme hosts are unobfuscated (remap applies)")
    void unobfuscatedFor26Plus() {
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("26.1"));
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("26.1.2"));   // #24 host
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("26.1.2.60")); // NeoForge build str
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("27.0"));     // future year
    }

    @Test
    @DisplayName("Legacy 1.x hosts are obfuscated (remap skipped) — #21/#29")
    void obfuscatedForLegacy1x() {
        // The exact hosts from the bug reports:
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget("1.21.8"), "#21 host");
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget("1.21.1"), "#29 host");
        // Plus the rest of the legacy range Retromod still supports.
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget("1.20.1"));
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget("1.21.11"));
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget("1.16.5"));
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget("1.12.2"));
    }

    @Test
    @DisplayName("Unknown / unparseable versions default to true (preserve 26.1 behavior)")
    void defaultsToUnobfuscatedOnUnknown() {
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget(null));
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("unknown"));
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget(""));
    }

    @Test
    @DisplayName("Leading/trailing whitespace and suffixes are tolerated")
    void tolerateWhitespaceAndSuffix() {
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("  26.1.2  "));
        assertFalse(RetromodPreLaunch.isUnobfuscatedTarget(" 1.21.8 "));
        assertTrue(RetromodPreLaunch.isUnobfuscatedTarget("26.1.2-rc1"));
    }
}
