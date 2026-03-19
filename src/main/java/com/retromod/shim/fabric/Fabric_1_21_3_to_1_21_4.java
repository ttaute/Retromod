/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim for Fabric 1.21.3 to 1.21.4.
 */
public class Fabric_1_21_3_to_1_21_4 implements VersionShim {
    @Override public String getShimName() { return "Fabric 1.21.3 to 1.21.4"; }
    @Override public String getSourceVersion() { return "1.21.3"; }
    @Override public String getTargetVersion() { return "1.21.4"; }
    @Override public String getModLoaderType() { return "fabric"; }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // BUILTIN ITEM RENDERER REGISTRY REMOVED
        // BuiltinItemRendererRegistry was removed in 1.21.4 with no direct replacement.
        // Mods using this API need to migrate to the new item model system.
        // We redirect to a stub that logs a warning and no-ops.
        // ============================================================

        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/BuiltinItemRendererRegistry",
            "com/retromod/shim/fabric/embedded/BuiltinItemRendererRegistryStub"
        );
    }
    
    @Override public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.BuiltinItemRendererRegistryStub"
        };
    }
}
