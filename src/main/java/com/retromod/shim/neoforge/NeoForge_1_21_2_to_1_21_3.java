/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** NeoForge 1.21.2 to 1.21.3: hotfix release, no API breaks to redirect. */
public class NeoForge_1_21_2_to_1_21_3 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.2 to 1.21.3"; }
    @Override public String getSourceVersion() { return "1.21.2"; }
    @Override public String getTargetVersion() { return "1.21.3"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
