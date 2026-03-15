/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for NeoForge mods built for 1.21.10 (or earlier) to run on 26.1.
 *
 * NeoForge has used Mojang official mappings since 1.17, so the class/method/field
 * names themselves DON'T change for NeoForge mods. The main changes are:
 * - NeoForge API changes (event system, registry, capabilities)
 * - Minecraft internal API changes between 1.21.x and 26.1
 * - Java 21 → Java 25 language feature changes
 */
public class NeoForge_1_21_10_to_26_1 implements VersionShim {

    @Override
    public String getShimName() {
        return "NeoForge 1.21.10 to 26.1";
    }

    @Override
    public String getSourceVersion() {
        return "1.21.10";
    }

    @Override
    public String getTargetVersion() {
        return "26.1";
    }

    @Override
    public String getModLoaderType() {
        return "neoforge";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // NEOFORGE API CHANGES
        // ============================================================

        // NeoForge already uses Mojang official names, so no intermediary
        // translation is needed. Only API-level changes matter.

        // Event system — NeoForge event bus changes
        // (Most events remain compatible, but some were reorganized)

        // Example: RenderLevelStageEvent replaced some older render events
        transformer.registerClassRedirect(
                "net/neoforged/neoforge/client/event/RenderLevelLastEvent",
                "net/neoforged/neoforge/client/event/RenderLevelStageEvent"
        );

        // ============================================================
        // CAPABILITY SYSTEM CHANGES
        // ============================================================

        // NeoForge 26.1 further refined the capability system
        // (introduced in NeoForge 1.20.5 as replacement for Forge capabilities)
        // Existing capability registrations should still work

        // ============================================================
        // REGISTRY CHANGES
        // ============================================================

        // DeferredRegister API remained stable but some internal methods changed
        // No redirects needed for standard usage

        // ============================================================
        // RENDERING PIPELINE
        // ============================================================

        // Rendering pipeline changes affect all loaders equally
        // PoseStack, MultiBufferSource, etc. — names unchanged for NeoForge
        // but internal implementation changed

        // ============================================================
        // DATA GENERATION
        // ============================================================

        // DataGen providers were reorganized in 26.1
        // Most provider base classes remained compatible
    }
}
