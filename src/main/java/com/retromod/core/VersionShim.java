/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

/**
 * Interface for version-specific compatibility shims.
 * 
 * Each implementation handles transformations for a specific version transition
 * (e.g., 1.21.9 -> 1.21.10).
 * 
 * Shims are discovered via ServiceLoader, so external shim packs can be added.
 *
 * <p><b>Public Addon API.</b> This is a stable extension point for third-party
 * addons (see {@code docs/addons.md}). Across the 1.x line new methods will be
 * added only as {@code default}s - existing signatures won't break.
 */
public interface VersionShim {
    
    /**
     * @return Human-readable name of this shim (e.g., "Fabric 1.21.9 to 1.21.10")
     */
    String getShimName();
    
    /**
     * @return Source MC version this shim handles (e.g., "1.21.9")
     */
    String getSourceVersion();
    
    /**
     * @return Target MC version this shim transforms to (e.g., "1.21.10")
     */
    String getTargetVersion();
    
    /**
     * @return Mod loader type ("fabric", "forge", "neoforge")
     */
    String getModLoaderType();
    
    /**
     * Register all method/field/class redirects for this version transition.
     * 
     * @param transformer The transformer to register redirects with
     */
    void registerRedirects(RetromodTransformer transformer);
    
    /**
     * Get a list of classes that provide shim implementations.
     * These are classes containing replacement code for removed methods.
     * 
     * @return Array of fully qualified class names
     */
    default String[] getShimClasses() {
        return new String[0];
    }
    
    /**
     * Check if this shim applies to a given mod based on its metadata.
     * 
     * @param modId The mod's ID
     * @param modVersion The mod's version
     * @param targetMcVersion The MC version the mod targets
     * @return true if this shim should be applied
     */
    default boolean appliesTo(String modId, String modVersion, String targetMcVersion) {
        return getSourceVersion().equals(targetMcVersion);
    }
}
