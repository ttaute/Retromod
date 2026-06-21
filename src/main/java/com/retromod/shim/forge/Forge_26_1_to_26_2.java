/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import com.retromod.shim.common.Mc26_1To26_2CoreMoves;

/**
 * Forge 26.1 → 26.2 shim - the shared Mojang-name class moves.
 *
 * <p>Registered ahead of a Forge 26.2 release (Forge doesn't ship snapshot
 * builds); the host-version gate keeps it inert until a 26.2 host actually
 * exists. Forge-API-level renames get added once that release is out.
 */
public class Forge_26_1_to_26_2 implements VersionShim {

    @Override
    public String getShimName() {
        return "Forge 26.1 to 26.2";
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
        return "forge";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        Mc26_1To26_2CoreMoves.register(transformer);
    }
}
