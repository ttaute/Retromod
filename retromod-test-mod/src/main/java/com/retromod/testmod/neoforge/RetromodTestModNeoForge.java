/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.neoforge;

import com.retromod.testmod.TestRunner;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point for the test mod.
 *
 * <p>Same shape as the Forge variant - {@code @Mod}-annotated class with a
 * no-arg constructor that NeoForge calls at mod-loading time. The
 * annotation here resolves to a compile-time stub; the real NeoForge
 * annotation shadows it at runtime.
 *
 * <p>NeoForge has its own event bus ({@code NeoForge.EVENT_BUS}) and lifecycle
 * events ({@code FMLClientSetupEvent}, etc.) that we'd hook into for the
 * deferred {@code CLIENT_STARTED} and {@code WORLD_JOIN} phases. Like the
 * Forge variant, those aren't wired here yet - initial v0.3 pass covers
 * the immediate phase only.
 */
@Mod("retromod_test_mod")
public class RetromodTestModNeoForge {

    public RetromodTestModNeoForge() {
        TestRunner.runImmediate();
    }
}
