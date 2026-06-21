/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for #87: a transformed mod jar must not ship
 * {@code org/spongepowered/asm/synthetic/} - that package belongs to
 * NeoForge's runtime {@code mixin_synthetic} module, and a jar that also
 * exports it (Blueprint 7.x ships a {@code Dummy.class} placeholder there)
 * fails JPMS module resolution for the whole layer at boot.
 *
 * <p>Exercises the host-agnostic mechanism
 * {@link ForgeModTransformer#stripMixinSyntheticEntries}; the NeoForge-host
 * gate around it is environment-dependent and covered by the NeoForge test
 * mod (which ships the same dummy and asserts it's gone after transform).
 */
class MixinSyntheticStripTest {

    /** Mirrors the exact layout of blueprint-1.20.1-7.1.4.jar. */
    @Test
    void stripsDummyAndPrunesEmptyParents() throws Exception {
        Path dir = Files.createTempDirectory("retromod-strip-test-");
        Path dummy = dir.resolve("org/spongepowered/asm/synthetic/args/Dummy.class");
        Files.createDirectories(dummy.getParent());
        Files.write(dummy, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        Path modClass = dir.resolve("com/teamabnormals/blueprint/Blueprint.class");
        Files.createDirectories(modClass.getParent());
        Files.write(modClass, new byte[]{1});

        assertTrue(ForgeModTransformer.stripMixinSyntheticEntries(dir));

        assertFalse(Files.exists(dir.resolve("org/spongepowered/asm/synthetic")),
                "synthetic package must be deleted");
        assertFalse(Files.exists(dir.resolve("org")),
                "empty ancestor dirs must be pruned so the jar doesn't declare the packages");
        assertTrue(Files.exists(modClass), "mod's own classes must be untouched");

        // Second run is a no-op
        assertFalse(ForgeModTransformer.stripMixinSyntheticEntries(dir));
    }

    /** A mod that shades full Mixin keeps its other org/spongepowered content. */
    @Test
    void keepsShadedMixinSiblings() throws Exception {
        Path dir = Files.createTempDirectory("retromod-strip-test-");
        Path dummy = dir.resolve("org/spongepowered/asm/synthetic/args/Dummy.class");
        Files.createDirectories(dummy.getParent());
        Files.write(dummy, new byte[]{1});
        Path shaded = dir.resolve("org/spongepowered/asm/mixin/Mixin.class");
        Files.createDirectories(shaded.getParent());
        Files.write(shaded, new byte[]{2});

        assertTrue(ForgeModTransformer.stripMixinSyntheticEntries(dir));

        assertFalse(Files.exists(dir.resolve("org/spongepowered/asm/synthetic")));
        assertTrue(Files.exists(shaded), "shaded Mixin classes outside synthetic/ must survive");
        assertTrue(Files.exists(dir.resolve("org/spongepowered/asm")),
                "non-empty ancestors must not be pruned");
    }

    /** Jars without the package (the overwhelming majority) are untouched. */
    @Test
    void noOpWithoutThePackage() throws Exception {
        Path dir = Files.createTempDirectory("retromod-strip-test-");
        Path modClass = dir.resolve("com/example/Mod.class");
        Files.createDirectories(modClass.getParent());
        Files.write(modClass, new byte[]{1});

        assertFalse(ForgeModTransformer.stripMixinSyntheticEntries(dir));
        assertTrue(Files.exists(modClass));
    }
}
