/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2025/12/05/12111.html
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Fabric shim for 1.21.10 mods on 1.21.11, the last obfuscated MC version
 * (Yarn mappings stop here). Handles the ResourceLocation to Identifier rename.
 */
public class Fabric_1_21_10_to_1_21_11 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.10 to 1.21.11";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.10";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.11";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Other 1.21.11 changes are additive or internal-only and need no redirect.

        // ResourceLocation renamed to Identifier in vanilla 1.21.11.
        transformer.registerClassRedirect(
            "net/minecraft/resources/ResourceLocation",
            "net/minecraft/resources/Identifier"
        );

        // RenderType factory methods split out into RenderTypes.
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderType",
            "net/minecraft/client/renderer/RenderTypes"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
