/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Fabric 1.19.1 to 1.19.2: bugfix release, no API changes. */
public class Fabric_1_19_1_to_1_19_2 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19.1 to 1.19.2"; }
    @Override public String getSourceVersion() { return "1.19.1"; }
    @Override public String getTargetVersion() { return "1.19.2"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // empty: kept so the BFS chain stays continuous
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
