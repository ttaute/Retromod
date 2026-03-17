/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed Forge core APIs.
 * Covers SidedProxy, RegistryObject, MinecraftForge event bus,
 * capability system (LazyOptional, ICapabilityProvider).
 */
public class ForgeCorePolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Forge Core Removed APIs";
    }

    @Override
    public String getCategory() {
        return "forge";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraftforge/fml/common/SidedProxy",
            "net/minecraftforge/registries/RegistryObject",
            "net/minecraftforge/common/MinecraftForge",
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "net/minecraftforge/common/util/LazyOptional"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // Stubs removed from net.minecraftforge.* packages to avoid JPMS
        // split-package conflicts. Forge classes are handled via class redirects
        // to embedded shims in com.retromod.shim.forge.embedded/
        return new String[]{
            "com.retromod.shim.forge.embedded.CapabilityShim",
            "com.retromod.shim.forge.embedded.ForgeRegistriesShim",
            "com.retromod.shim.forge.embedded.NetworkShim"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // Register class redirects from old Forge classes to our embedded shims
        transformer.registerClassRedirect(
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "com/retromod/shim/api/forge/embedded/CapabilityProviderShim"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/util/LazyOptional",
            "com/retromod/shim/api/forge/embedded/LazyOptionalShim"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "com/retromod/shim/api/forge/embedded/ForgeCapabilitiesShim"
        );

        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
