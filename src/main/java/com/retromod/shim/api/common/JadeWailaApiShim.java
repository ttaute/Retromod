/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Block/entity tooltip API shim: WAILA -> HWYLA -> Jade renamed packages and restructured the API,
 * while WTHIT keeps its own.
 */
public class JadeWailaApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Jade/WTHIT/WAILA API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "11.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "common";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaPlugin",
            "snownee/jade/api/IWailaPlugin"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IComponentProvider",
            "snownee/jade/api/IBlockComponentProvider"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IEntityComponentProvider",
            "snownee/jade/api/IEntityComponentProvider"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaConfigHandler",
            "snownee/jade/api/config/IWailaConfig"
        );

        transformer.registerClassRedirect(
            "snownee/jade/api/IBlockComponentProvider",
            "snownee/jade/api/IBlockComponentProvider"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/ITooltip",
            "snownee/jade/api/ITooltip"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IDataAccessor",
            "snownee/jade/api/BlockAccessor"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/BlockAccessor",
            "snownee/jade/api/BlockAccessor"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/EntityAccessor",
            "snownee/jade/api/EntityAccessor"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IRegistrar",
            "snownee/jade/api/IWailaClientRegistration"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IRegistrar",
            "registerComponentProvider",
            "(Lmcp/mobius/waila/api/IComponentProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/common/embedded/JadeShim",
            "registerBlockProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );
        
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IRegistrar",
            "registerEntityProvider",
            "(Lmcp/mobius/waila/api/IEntityComponentProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/common/embedded/JadeShim",
            "registerEntityProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/ITooltip",
            "add",
            "(Lnet/minecraft/network/chat/Component;)V",
            "snownee/jade/api/ITooltip",
            "add",
            "(Lnet/minecraft/network/chat/Component;)V"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/ITooltip",
            "addLine",
            "(Lnet/minecraft/network/chat/Component;)V",
            "snownee/jade/api/ITooltip",
            "add",
            "(Lnet/minecraft/network/chat/Component;)V"
        );

        // WTHIT keeps the mcp/mobius package
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaPlugin",
            "mcp/mobius/waila/api/IWailaPlugin"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IDataAccessor",
            "getTileEntity",
            "()Lnet/minecraft/world/level/block/entity/BlockEntity;",
            "snownee/jade/api/BlockAccessor",
            "getBlockEntity",
            "()Lnet/minecraft/world/level/block/entity/BlockEntity;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IDataAccessor",
            "getBlockState",
            "()Lnet/minecraft/world/level/block/state/BlockState;",
            "snownee/jade/api/BlockAccessor",
            "getBlockState",
            "()Lnet/minecraft/world/level/block/state/BlockState;"
        );

        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IDataAccessor",
            "getPlayer",
            "()Lnet/minecraft/world/entity/player/Player;",
            "snownee/jade/api/BlockAccessor",
            "getPlayer",
            "()Lnet/minecraft/world/entity/player/Player;"
        );

        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IServerDataProvider",
            "snownee/jade/api/IServerDataProvider"
        );
        
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IServerDataProvider",
            "appendServerData",
            "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Ljava/lang/Object;Z)V",
            "com/retromod/shim/api/common/embedded/JadeShim",
            "appendServerData",
            "(Ljava/lang/Object;Lnet/minecraft/nbt/CompoundTag;Ljava/lang/Object;)V"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.JadeShim"
        };
    }
}
