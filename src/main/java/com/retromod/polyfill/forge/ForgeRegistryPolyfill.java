/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the Forge registry system evolution.
 * Covers GameRegistry (removed 1.13), FML lifecycle events (removed 1.13),
 * OreDictionary (replaced by tags in 1.13), SidedProxy, and FMLCommonHandler.
 *
 * Old Forge mods (1.12.2 and earlier) used these APIs extensively.
 * This polyfill redirects all references to embedded shim implementations
 * that provide no-op or compatibility behavior.
 */
public class ForgeRegistryPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Forge Registry System Changes";
    }

    @Override
    public String getCategory() {
        return "forge";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraftforge/fml/common/registry/GameRegistry",
            "net/minecraftforge/fml/common/event/FMLPreInitializationEvent",
            "net/minecraftforge/fml/common/event/FMLInitializationEvent",
            "net/minecraftforge/fml/common/event/FMLPostInitializationEvent",
            "net/minecraftforge/fml/common/event/FMLServerStartingEvent",
            "net/minecraftforge/fml/common/SidedProxy",
            "net/minecraftforge/oredict/OreDictionary",
            "net/minecraftforge/fml/common/Loader",
            "net/minecraftforge/fml/common/FMLCommonHandler",
            "net/minecraftforge/fml/relauncher/Side",
            "net/minecraftforge/fml/relauncher/SideOnly"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "com.retromod.polyfill.forge.embedded.GameRegistryShim",
            "com.retromod.polyfill.forge.embedded.FMLEventShim",
            "com.retromod.polyfill.forge.embedded.FMLEventShim$PreInit",
            "com.retromod.polyfill.forge.embedded.FMLEventShim$Init",
            "com.retromod.polyfill.forge.embedded.FMLEventShim$PostInit",
            "com.retromod.polyfill.forge.embedded.FMLEventShim$ServerStarting",
            "com.retromod.polyfill.forge.embedded.OreDictionaryShim",
            "com.retromod.polyfill.forge.embedded.SideShim",
            "com.retromod.polyfill.forge.embedded.FMLCommonHandlerShim"
        };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // GameRegistry → embedded shim (old 1.12 registration)
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/registry/GameRegistry",
            "com/retromod/polyfill/forge/embedded/GameRegistryShim"
        );

        // FML lifecycle events → embedded shim stubs (no-op)
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLPreInitializationEvent",
            "com/retromod/polyfill/forge/embedded/FMLEventShim$PreInit"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLInitializationEvent",
            "com/retromod/polyfill/forge/embedded/FMLEventShim$Init"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLPostInitializationEvent",
            "com/retromod/polyfill/forge/embedded/FMLEventShim$PostInit"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLServerStartingEvent",
            "com/retromod/polyfill/forge/embedded/FMLEventShim$ServerStarting"
        );

        // SidedProxy → no-op annotation shim
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/SidedProxy",
            "com/retromod/polyfill/forge/embedded/GameRegistryShim"
        );

        // OreDictionary → tag-based shim
        transformer.registerClassRedirect(
            "net/minecraftforge/oredict/OreDictionary",
            "com/retromod/polyfill/forge/embedded/OreDictionaryShim"
        );

        // Loader → no-op shim (mod loading queries)
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Loader",
            "com/retromod/polyfill/forge/embedded/FMLCommonHandlerShim"
        );

        // FMLCommonHandler → embedded shim
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/FMLCommonHandler",
            "com/retromod/polyfill/forge/embedded/FMLCommonHandlerShim"
        );

        // Side enum → embedded shim
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/relauncher/Side",
            "com/retromod/polyfill/forge/embedded/SideShim"
        );

        // SideOnly → no-op annotation shim (annotation is simply ignored at runtime)
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/relauncher/SideOnly",
            "com/retromod/polyfill/forge/embedded/SideShim"
        );

        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
