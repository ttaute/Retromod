/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Fabric 1.20.5 to 1.20.6: a stabilization release with no API changes. */
public class Fabric_1_20_5_to_1_20_6 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.5 to 1.20.6"; }
    @Override public String getSourceVersion() { return "1.20.5"; }
    @Override public String getTargetVersion() { return "1.20.6"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 1.20.6 was a hotfix with no API changes; this shim just keeps the chain connected.
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
