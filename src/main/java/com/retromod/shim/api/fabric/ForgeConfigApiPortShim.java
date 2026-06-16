/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Forge Config API Port Compatibility Shim (for Fabric)
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge Config API Port compatibility shim.
 * 
 * This is the Fabric port of Forge's config system.
 * Many mods use it for cross-loader config compatibility.
 */
public class ForgeConfigApiPortShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge Config API Port Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "8.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // INTENTIONALLY EMPTY — do NOT redirect net.minecraftforge.common.ForgeConfigSpec
        // (or the FML config classes) onto fuzs.forgeconfigapiport paths.
        //
        // Forge Config API Port's entire purpose is to RE-IMPLEMENT the Forge
        // config classes at their ORIGINAL names — the bundled port jar ships
        // net/minecraftforge/common/ForgeConfigSpec (+ all 17 inner classes) and
        // net/minecraftforge/fml/config/ModConfig directly. So a mod's reference
        // to net.minecraftforge.common.ForgeConfigSpec resolves to the port with
        // NO redirect needed. The fuzs.forgeconfigapiport.api.config.v2.* package
        // is the port's OWN additional Fabric-native API — it is NOT a relocation
        // of ForgeConfigSpec, and those target classes do not exist.
        //
        // The previous redirects rewrote working references onto non-existent
        // fuzs/.../api/config/v2/ targets → NoClassDefFoundError, breaking every
        // Fabric mod that uses Forge Config API Port (verified: CoroUtil crashed
        // at ModConfigDataFabric.writeConfigFile after Retromod rewrote
        // net/minecraftforge/common/ForgeConfigSpec$Builder → the absent
        // fuzs path; #94 follow-up). The redirect was also inconsistent — it
        // moved ForgeConfigSpec/$Builder but left $ConfigValue/$IntValue/etc. at
        // the original name, which couldn't have type-checked even if the target
        // existed. Removed; the port provides the originals, so nothing is needed.
        //
        // (Kept as a registered no-op so this reasoning is findable and the bad
        // redirects don't get re-added.)
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
