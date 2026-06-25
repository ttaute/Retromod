/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Redirects Fabric mods built for 1.21.9 onto 1.21.10. */
public class Fabric_1_21_9_to_1_21_10 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.9 to 1.21.10";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.9";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.10";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", 
            "getWorld", 
            "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/Entity", 
            "getEntityWorld", 
            "()Lnet/minecraft/world/World;"
        );

        transformer.registerMethodRedirect(
            "net/minecraft/block/BlockState",
            "getBlock",
            "()Lnet/minecraft/block/Block;",
            "net/minecraft/block/BlockState",
            "getBlockType",
            "()Lnet/minecraft/block/Block;"
        );

        // NbtType.getType was removed from Fabric API; route to our shim.
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/util/NbtType",
            "getType",
            "(B)I",
            "com/retromod/shim/fabric/embedded/NbtTypeShim",
            "getType",
            "(B)I"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.NbtTypeShim",
            "com.retromod.shim.fabric.embedded.EntityShim"
        };
    }
}
