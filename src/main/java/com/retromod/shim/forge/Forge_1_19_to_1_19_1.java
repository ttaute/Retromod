/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Forge 1.19 to 1.19.1: bugfix release, chat-signing changes stayed in Mojang's protocol layer, nothing to redirect. */
public class Forge_1_19_to_1_19_1 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.19 to 1.19.1"; }
    @Override public String getSourceVersion() { return "1.19"; }
    @Override public String getTargetVersion() { return "1.19.1"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
