/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20.2 to run on 1.20.3.
 * Minor release with no significant API changes.
 */
public class Fabric_1_20_2_to_1_20_3 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.2 to 1.20.3"; }
    @Override public String getSourceVersion() { return "1.20.2"; }
    @Override public String getTargetVersion() { return "1.20.3"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Minor bugfix release - minimal API changes

        // GravelBlock merged into ColoredFallingBlock
        transformer.registerClassRedirect(
            "net/minecraft/world/level/block/GravelBlock",
            "net/minecraft/world/level/block/ColoredFallingBlock"
        );
        // SandBlock merged into ColoredFallingBlock
        transformer.registerClassRedirect(
            "net/minecraft/world/level/block/SandBlock",
            "net/minecraft/world/level/block/ColoredFallingBlock"
        );
        // FernBlock renamed to ShortPlantBlock
        transformer.registerClassRedirect(
            "net/minecraft/world/level/block/FernBlock",
            "net/minecraft/world/level/block/ShortPlantBlock"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
