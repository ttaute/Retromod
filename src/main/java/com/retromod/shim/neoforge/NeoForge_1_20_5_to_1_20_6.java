/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** NeoForge 1.20.5 to 1.20.6: no API breaks to redirect. */
public class NeoForge_1_20_5_to_1_20_6 implements VersionShim {

    @Override public String getShimName() { return "NeoForge 1.20.5 to 1.20.6"; }
    @Override public String getSourceVersion() { return "1.20.5"; }
    @Override public String getTargetVersion() { return "1.20.6"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
