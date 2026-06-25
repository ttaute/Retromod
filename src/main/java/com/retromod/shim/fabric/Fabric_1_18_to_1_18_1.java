/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Fabric 1.18 to 1.18.1 shim (bugfix release, no API changes). */
public class Fabric_1_18_to_1_18_1 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.18 to 1.18.1"; }
    @Override public String getSourceVersion() { return "1.18"; }
    @Override public String getTargetVersion() { return "1.18.1"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // No remaps; empty shim keeps the BFS chain connected.
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
