/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the {@code mods.toml} → {@code neoforge.mods.toml} promotion logic that
 * lets modern NeoForge accept 1.20.1 (Neo)Forge mods (issue #42). The file
 * rename + host gate are integration-level (need a NeoForge runtime), but the
 * {@code loaderVersion} relax — the part most likely to get the regex wrong — is
 * pure and testable.
 */
class ForgeModTransformerTest {

    @Test
    @DisplayName("#42: top-level loaderVersion is relaxed to [1,)")
    void relaxesLoaderVersion() {
        String in = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"MIT\"\n";
        String out = ForgeModTransformer.relaxLoaderVersion(in);
        assertTrue(out.contains("loaderVersion=\"[1,)\""), out);
        assertFalse(out.contains("[47,)"), "old Forge loaderVersion must be gone");
        assertTrue(out.contains("modLoader=\"javafml\""), "other fields untouched");
        assertTrue(out.contains("license=\"MIT\""), "other fields untouched");
    }

    @Test
    @DisplayName("#42: spaced form works and dependency versionRanges are left alone")
    void spacedFormAndDependenciesUntouched() {
        String in = "loaderVersion = \"[2,)\"\n"
                + "[[dependencies.foo]]\n"
                + "modId=\"minecraft\"\n"
                + "versionRange=\"[1.20.1]\"\n";
        String out = ForgeModTransformer.relaxLoaderVersion(in);
        assertTrue(out.contains("loaderVersion = \"[1,)\""), out);
        // relaxLoaderVersion must only touch the top-level loaderVersion, not deps.
        assertTrue(out.contains("versionRange=\"[1.20.1]\""), "dependency versionRange must be untouched");
    }

    @Test
    @DisplayName("No loaderVersion line → content unchanged")
    void noLoaderVersionIsNoop() {
        String in = "modLoader=\"javafml\"\nlicense=\"MIT\"\n";
        assertEquals(in, ForgeModTransformer.relaxLoaderVersion(in));
    }

    @Test
    @DisplayName("#42: a `forge` dependency is repointed at `neoforge` (which exists on NeoForge)")
    void pointsForgeDepAtNeoForge() {
        // Twigs-shaped block: a forge dep + a minecraft dep.
        String in = "[[dependencies.twigs]]\n"
                + "modId=\"forge\"\n"
                + "versionRange=\"[0,)\"\n"
                + "[[dependencies.twigs]]\n"
                + "modId = \"minecraft\"\n"
                + "versionRange=\"[1.21.1,)\"\n";
        String out = ForgeModTransformer.pointForgeDependencyAtNeoForge(in);
        assertTrue(out.contains("modId=\"neoforge\""), "forge dep must be repointed at neoforge: " + out);
        assertFalse(out.contains("modId=\"forge\""), "no `forge` dependency should remain");
        // The minecraft dependency (and its versionRange) must be untouched.
        assertTrue(out.contains("modId = \"minecraft\""), "minecraft dep untouched");
        assertTrue(out.contains("versionRange=\"[1.21.1,)\""), "minecraft versionRange untouched");
    }

    @Test
    @DisplayName("#62: a missing license is added as a root-table key (before [[mods]])")
    void addsMissingLicense() {
        String in = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\n\n[[mods]]\nmodId=\"survivalisland\"\n";
        String out = ForgeModTransformer.ensureLicense(in);
        assertTrue(out.contains("license="), "license must be added: " + out);
        // Must stay in the root table — i.e. appear before the [[mods]] header.
        assertTrue(out.indexOf("license=") < out.indexOf("[[mods]]"),
                "license must precede the [[mods]] table: " + out);
        assertTrue(out.contains("modId=\"survivalisland\""), "existing content preserved");
    }

    @Test
    @DisplayName("#62: an existing license is left untouched")
    void keepsExistingLicense() {
        String in = "modLoader=\"javafml\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"x\"\n";
        assertEquals(in, ForgeModTransformer.ensureLicense(in),
                "must not modify a toml that already declares a license");
    }

    @Test
    @DisplayName("#62: license is added even when there is no table header")
    void addsLicenseWithNoTableHeader() {
        String in = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\n";
        String out = ForgeModTransformer.ensureLicense(in);
        assertTrue(out.contains("license="), "license must be added even with no [table]: " + out);
    }
}
