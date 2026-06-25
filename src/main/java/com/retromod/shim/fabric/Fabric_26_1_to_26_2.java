/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import com.retromod.shim.common.Mc26_1To26_2CoreMoves;

/**
 * Fabric 26.1 to 26.2 shim. Both versions are Mojang-named, so it's class moves
 * only ({@link Mc26_1To26_2CoreMoves}); Fabric API renames land here once a
 * fabric-api build targets 26.2.
 */
public class Fabric_26_1_to_26_2 implements VersionShim {

    @Override
    public String getShimName() {
        return "Fabric 26.1 to 26.2";
    }

    @Override
    public String getSourceVersion() {
        return "26.1";
    }

    @Override
    public String getTargetVersion() {
        return "26.2";
    }

    @Override
    public String getModLoaderType() {
        return "fabric";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        Mc26_1To26_2CoreMoves.register(transformer);
    }
}
