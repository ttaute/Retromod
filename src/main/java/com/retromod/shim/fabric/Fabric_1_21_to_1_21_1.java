/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Fabric 1.21 to 1.21.1: bugfix release, no API changes. */
public class Fabric_1_21_to_1_21_1 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21 to 1.21.1";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.1";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // No-op: only keeps the ShimRegistry BFS chain connected.
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
