/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link EnvironmentDetector#classExists(String)} - the
 * environment probe must report a class's presence <em>without initializing it</em>.
 *
 * <p>Issue #46: {@code detectDedicatedServer()} used the single-arg
 * {@link Class#forName(String)}, which initializes the class. Probing
 * {@code net.minecraft.server.MinecraftServer} during mod construction forced its
 * {@code <clinit>} far too early; with Legacy4J's mixins on {@code MinecraftServer}
 * and {@code LevelSettings} that cascaded into {@code PackAlbum.<clinit>} reading
 * {@code Minecraft.getInstance().gameDirectory} before the client existed → NPE.
 * Retromod's mere presence then crashed an otherwise-fine mod. These tests prove a
 * probe never triggers the target's static initializer.
 */
class EnvironmentDetectorTest {

    // A flag holder kept in a SEPARATE class so the test can read it without
    // initializing the probe target itself.
    static final class InitFlag {
        static volatile boolean SIDE_EFFECT_RAN = false;
    }

    // Probe target whose <clinit> flips the flag. If classExists() were to
    // initialize it, SIDE_EFFECT_RAN would become true.
    @SuppressWarnings("unused")
    static final class ProbeTarget {
        static { InitFlag.SIDE_EFFECT_RAN = true; }
        static final int X = 1;
    }

    static final class ControlFlag {
        static volatile boolean SIDE_EFFECT_RAN = false;
    }

    @SuppressWarnings("unused")
    static final class ControlTarget {
        static { ControlFlag.SIDE_EFFECT_RAN = true; }
        static final int X = 1;
    }

    @Test
    @DisplayName("#46: classExists() finds a present class WITHOUT running its <clinit>")
    void classExistsDoesNotInitialize() {
        assertFalse(InitFlag.SIDE_EFFECT_RAN, "precondition: probe target not yet initialized");

        boolean present = EnvironmentDetector.classExists(
                "com.retromod.core.EnvironmentDetectorTest$ProbeTarget");

        assertTrue(present, "classExists must find a class that is on the classpath");
        assertFalse(InitFlag.SIDE_EFFECT_RAN,
                "classExists must NOT trigger the probed class's static initializer (#46)");
    }

    @Test
    @DisplayName("classExists() returns false for an absent class and never throws")
    void classExistsAbsentClass() {
        assertFalse(EnvironmentDetector.classExists("net.minecraft.server.NoSuchClass_zzz_46"));
    }

    @Test
    @DisplayName("control: plain Class.forName(String) DOES initialize - the probe target's <clinit> is observable")
    void plainForNameInitializesControl() throws Exception {
        // Confirms the test harness can actually observe initialization, so the
        // assertion above is meaningful rather than vacuously passing.
        assertFalse(ControlFlag.SIDE_EFFECT_RAN);
        Class.forName("com.retromod.core.EnvironmentDetectorTest$ControlTarget");
        assertTrue(ControlFlag.SIDE_EFFECT_RAN,
                "single-arg Class.forName initializes - this is exactly the #46 footgun");
    }
}
