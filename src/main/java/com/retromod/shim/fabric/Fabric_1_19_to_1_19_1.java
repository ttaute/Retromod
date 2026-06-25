/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** No API changed between 1.19 and 1.19.1; this shim just keeps the chain connected. */
public class Fabric_1_19_to_1_19_1 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19 to 1.19.1"; }
    @Override public String getSourceVersion() { return "1.19"; }
    @Override public String getTargetVersion() { return "1.19.1"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
