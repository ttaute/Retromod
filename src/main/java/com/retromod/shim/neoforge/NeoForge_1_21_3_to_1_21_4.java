/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.21.3 to 1.21.4 shim - Data generation event split.
 * GatherDataEvent was split into Client and Server sub-events.
 */
public class NeoForge_1_21_3_to_1_21_4 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.3 to 1.21.4"; }
    @Override public String getSourceVersion() { return "1.21.3"; }
    @Override public String getTargetVersion() { return "1.21.4"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // GatherDataEvent split into Client/Server sub-events in 1.21.4;
        // redirect old unified event to Client variant (most common usage)
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/data/event/GatherDataEvent",
            "net/neoforged/neoforge/data/event/GatherDataEvent$Client"
        );

        // RecipesUpdatedEvent → RecipesReceivedEvent (#82). This is a 1.21.4-era
        // change, not 26.1: the old event last shipped in NeoForge 1.21.1 (vanilla
        // 1.21.2 stopped syncing the full RecipeManager) and the successor exists
        // from 1.21.4 on, byte-identical through 26.1 (verified by branch diff).
        // Living here, the redirect covers a 1.21.1 mod landing on ANY 1.21.4+
        // host - the 26.1 shim's copy only covered the final hop.
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/client/event/RecipesUpdatedEvent",
            "net/neoforged/neoforge/client/event/RecipesReceivedEvent"
        );
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
