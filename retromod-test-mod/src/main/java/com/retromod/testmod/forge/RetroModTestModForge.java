/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.forge;

import com.retromod.testmod.TestRunner;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entry point for the test mod.
 *
 * <p>Just kicks off {@link TestRunner#runImmediate()} from its constructor —
 * Forge calls the {@code @Mod}-annotated class's no-arg constructor at mod
 * loading time, the same place a Fabric {@code ClientModInitializer} runs.
 *
 * <p>The {@code @Mod} annotation here resolves at compile time to the
 * {@link net.minecraftforge.fml.common.Mod} stub in this same project; at
 * runtime Forge's real annotation class shadows the stub. This is the same
 * compile-time-stub pattern RetroMod itself uses for its own Forge entry
 * point — see {@code RetroModForge.java} in the parent project.
 *
 * <p>Note: deferred {@code CLIENT_STARTED} and {@code WORLD_JOIN} phases
 * aren't wired here yet. Forge has equivalent events
 * ({@code FMLClientSetupEvent}, {@code ClientPlayerNetworkEvent.LoggingIn})
 * but they're registered through Forge's {@code IEventBus} rather than
 * Fabric API events. Adding them is a follow-up; for v0.3 the immediate
 * phase covers the bulk of the test surface.
 */
@Mod("retromod_test_mod")
public class RetroModTestModForge {

    public RetroModTestModForge() {
        TestRunner.runImmediate();
    }
}
