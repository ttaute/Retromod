/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * WAILA (What Am I Looking At) API -> Jade/HWYLA Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Redirects the old WAILA/HWYLA tooltip API (mcp.mobius.waila, Forge 1.5-1.12.2)
 * to Jade (snownee.jade).
 */
public class WailaApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "WAILA -> Jade/HWYLA API Compatibility";
    }

    @Override
    public String getSourceVersion() {
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        return "forge";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Jade keeps the IWailaPlugin name, different package
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaPlugin",
            "snownee/jade/api/IWailaPlugin"
        );
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/WailaPlugin",
            "snownee/jade/api/WailaPlugin"
        );

        // Jade splits the single registrar into client + common registration
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "com/retromod/shim/api/forge/embedded/WailaShim$RegistrarCompat"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerBodyProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerBodyProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerHeadProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerHeadProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerTailProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerTailProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerNBTProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerNBTProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerStackProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerStackProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        // the interface every WAILA addon implemented, now Jade's component providers
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "com/retromod/shim/api/forge/embedded/WailaShim$DataProviderCompat"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaBody",
            "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Ljava/util/List;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaBody",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaHead",
            "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Ljava/util/List;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaHead",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaTail",
            "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Ljava/util/List;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaTail",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaStack",
            "(Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Lnet/minecraft/item/ItemStack;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaStack",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lnet/minecraft/world/item/ItemStack;"
        );

        // server-side NBT sync for the client tooltip
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getNBTData",
            "(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/nbt/NBTTagCompound;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getNBTData",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // read-only context about the targeted block
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "snownee/jade/api/BlockAccessor"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getTileEntity",
            "()Lnet/minecraft/tileentity/TileEntity;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getBlockEntity",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getBlock",
            "()Lnet/minecraft/block/Block;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getBlock",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getPosition",
            "()Lnet/minecraft/util/math/BlockPos;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getPosition",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getNBTData",
            "()Lnet/minecraft/nbt/NBTTagCompound;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getServerData",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaConfigHandler",
            "com/retromod/shim/api/forge/embedded/WailaShim$ConfigHandlerCompat"
        );
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaConfigHandler",
            "getConfig",
            "(Ljava/lang/String;)Z",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getConfig",
            "(Ljava/lang/Object;Ljava/lang/String;)Z"
        );

        // entity-targeting providers
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaEntityProvider",
            "snownee/jade/api/IEntityComponentProvider"
        );
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaEntityAccessor",
            "snownee/jade/api/EntityAccessor"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.WailaShim"
        };
    }
}
