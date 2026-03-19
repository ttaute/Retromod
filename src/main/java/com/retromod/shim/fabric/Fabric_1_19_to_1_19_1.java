/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.19 to run on 1.19.1.
 * Minor release with chat signing changes but no significant API breaks.
 */
public class Fabric_1_19_to_1_19_1 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19 to 1.19.1"; }
    @Override public String getSourceVersion() { return "1.19"; }
    @Override public String getTargetVersion() { return "1.19.1"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // No redirects needed — 1.19.1 added chat signing (new classes) but
        // removed nothing. No API renames, removals, or signature changes.
        // This shim exists solely for BFS chain continuity (ShimRegistry needs
        // a connected path from any version to 26.1).
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
