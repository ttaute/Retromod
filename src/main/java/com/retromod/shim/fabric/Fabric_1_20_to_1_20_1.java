/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Shim for Fabric 1.20 mods on 1.20.1, a bugfix release with no API changes. */
public class Fabric_1_20_to_1_20_1 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20 to 1.20.1"; }
    @Override public String getSourceVersion() { return "1.20"; }
    @Override public String getTargetVersion() { return "1.20.1"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // no redirects; this shim only keeps the chain connected through 1.20.1
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
