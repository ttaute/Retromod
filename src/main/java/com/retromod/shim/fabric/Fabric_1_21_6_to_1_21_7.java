/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim for Fabric 1.21.6 to 1.21.7.
 */
public class Fabric_1_21_6_to_1_21_7 implements VersionShim {
    @Override public String getShimName() { return "Fabric 1.21.6 to 1.21.7"; }
    @Override public String getSourceVersion() { return "1.21.6"; }
    @Override public String getTargetVersion() { return "1.21.7"; }
    @Override public String getModLoaderType() { return "fabric"; }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // No redirects needed — 1.21.7 is a hotfix with no API renames,
        // removals, or signature changes. This shim exists solely for BFS
        // chain continuity (ShimRegistry needs a connected path from any
        // version to 26.1).
    }
    
    @Override public String[] getShimClasses() { return new String[0]; }
}
