/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of WAILA's IWailaDataAccessor.
 * Provides access to the block/entity being looked at via reflection
 * into MC's hit result and world data.
 */
package mcp.mobius.waila.api;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of IWailaDataAccessor.
 *
 * In WAILA, this provided context about what the player was looking at.
 * The modern Jade equivalent is snownee.jade.api.IBlockAccessor.
 *
 * Default implementations return null — when used as a callback parameter,
 * Jade/WAILA provides the real implementation. This interface just needs
 * to exist so old mods can compile against it.
 */
public interface IWailaDataAccessor {

    /** Returns the client world. */
    default Object getWorld() { return null; }

    /** Returns the player looking at the block/entity. */
    default Object getPlayer() { return null; }

    /** Returns the block being looked at. */
    default Object getBlock() { return null; }

    /** Returns the block state. */
    default Object getBlockState() { return null; }

    /** Returns the block entity at the position, if any. */
    default Object getBlockEntity() { return null; }

    /** Returns the block position being looked at. */
    default Object getPosition() { return null; }

    /** Returns the hit result. */
    default Object getHitResult() { return null; }

    /** Returns the ItemStack representation of the block. */
    default Object getStack() { return null; }

    /** Returns server-side NBT data synced via the data provider. */
    default Object getNBTData() { return null; }

    /** Returns the integer NBT value for the given key. */
    default int getNBTInteger(Object nbt, String key) { return 0; }
}
