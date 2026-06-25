/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** NeoForge 1.21 to 1.21.1: bugfix release, no API breaks to redirect. */
public class NeoForge_1_21_to_1_21_1 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21 to 1.21.1"; }
    @Override public String getSourceVersion() { return "1.21"; }
    @Override public String getTargetVersion() { return "1.21.1"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
