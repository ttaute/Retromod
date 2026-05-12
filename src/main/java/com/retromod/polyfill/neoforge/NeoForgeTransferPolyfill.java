/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the NeoForge 21.9 Transfer API rework.
 * The old IItemHandler/IFluidHandler/IEnergyStorage interfaces were replaced
 * with a unified ResourceHandler/EnergyHandler system. This polyfill redirects
 * references from the old interfaces to their modern equivalents.
 */
public class NeoForgeTransferPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "NeoForge Transfer API Rework";
    }

    @Override
    public String getCategory() {
        return "neoforge";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/neoforged/neoforge/items/IItemHandler",
            "net/neoforged/neoforge/fluids/IFluidHandler",
            "net/neoforged/neoforge/energy/IEnergyStorage"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded shims needed — redirects point to classes that exist
        // in the modern NeoForge runtime (ResourceHandler, EnergyHandler)
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // IItemHandler → unified ResourceHandler
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/items/IItemHandler",
            "net/neoforged/neoforge/transfer/ResourceHandler"
        );

        // IFluidHandler → unified ResourceHandler
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/fluids/IFluidHandler",
            "net/neoforged/neoforge/transfer/ResourceHandler"
        );

        // IEnergyStorage → EnergyHandler
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/energy/IEnergyStorage",
            "net/neoforged/neoforge/energy/EnergyHandler"
        );

        // FluidStack still exists but API changed — no class redirect needed,
        // method-level redirects would be handled by version shims

        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
