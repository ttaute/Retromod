/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of FabricDimensions.
 * Delegates to Entity.teleportTo() / ServerPlayer methods via reflection
 * to actually teleport entities between dimensions.
 */
package net.fabricmc.fabric.api.dimension.v1;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of FabricDimensions.teleport() that actually teleports
 * entities to other dimensions using the modern MC teleportation API.
 *
 * Old API: FabricDimensions.teleport(entity, serverWorld, target)
 * New API: Entity.moveToWorld(ServerWorld) or entity.teleportTo(TeleportTarget)
 */
public class FabricDimensions {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private FabricDimensions() {}

    /**
     * Teleports an entity to the target dimension.
     *
     * Tries modern teleportation APIs via reflection:
     * 1. entity.teleportTo(TeleportTarget) — 1.21+
     * 2. entity.moveToWorld(ServerWorld) — 1.16-1.20
     * 3. entity.changeDimension(ServerLevel) — Forge/NeoForge
     *
     * @param entity          the entity to teleport
     * @param serverWorld     the target dimension world
     * @param teleportTarget  the teleport target (position/rotation/velocity)
     * @return the entity in the new dimension, or the original if teleport failed
     */
    @SuppressWarnings("unchecked")
    public static <E> E teleport(E entity, Object serverWorld, Object teleportTarget) {
        if (entity == null || serverWorld == null) return entity;

        // Try 1: entity.teleportTo(TeleportTarget) — 1.21+
        try {
            Class<?> teleportTargetClass = Class.forName("net.minecraft.world.TeleportTarget");
            Method teleportToMethod = entity.getClass().getMethod("teleportTo", teleportTargetClass);
            Object result = teleportToMethod.invoke(entity, teleportTarget);
            if (result != null) {
                LOGGER.fine("[RetroMod] FabricDimensions: teleported via teleportTo()");
                return (E) result;
            }
        } catch (Exception ignored) {}

        // Try 2: entity.moveToWorld(ServerWorld) — 1.16-1.20
        try {
            Class<?> serverWorldClass = Class.forName("net.minecraft.server.world.ServerWorld");
            Method moveToWorldMethod = entity.getClass().getMethod("moveToWorld", serverWorldClass);
            Object result = moveToWorldMethod.invoke(entity, serverWorld);
            if (result != null) {
                LOGGER.fine("[RetroMod] FabricDimensions: teleported via moveToWorld()");
                return (E) result;
            }
        } catch (Exception ignored) {}

        // Try 3: entity.changeDimension(ServerLevel) — Forge style
        try {
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Method changeDimMethod = entity.getClass().getMethod("changeDimension", serverLevelClass);
            Object result = changeDimMethod.invoke(entity, serverWorld);
            if (result != null) {
                LOGGER.fine("[RetroMod] FabricDimensions: teleported via changeDimension()");
                return (E) result;
            }
        } catch (Exception ignored) {}

        // Try 4: entity.teleport(x, y, z) — basic position teleport as last resort
        if (teleportTarget != null) {
            try {
                // Try to extract position from teleportTarget
                Method getPosMethod = null;
                for (Method m : teleportTarget.getClass().getMethods()) {
                    if (m.getName().equals("getPos") || m.getName().equals("position")) {
                        getPosMethod = m;
                        break;
                    }
                }
                if (getPosMethod != null) {
                    Object pos = getPosMethod.invoke(teleportTarget);
                    if (pos != null) {
                        Method getX = pos.getClass().getMethod("getX");
                        Method getY = pos.getClass().getMethod("getY");
                        Method getZ = pos.getClass().getMethod("getZ");
                        double x = ((Number) getX.invoke(pos)).doubleValue();
                        double y = ((Number) getY.invoke(pos)).doubleValue();
                        double z = ((Number) getZ.invoke(pos)).doubleValue();

                        Method teleportMethod = entity.getClass().getMethod("teleport", double.class, double.class, double.class);
                        teleportMethod.invoke(entity, x, y, z);
                        LOGGER.fine("[RetroMod] FabricDimensions: basic position teleport as fallback");
                        return entity;
                    }
                }
            } catch (Exception ignored) {}
        }

        LOGGER.warning("[RetroMod] FabricDimensions: teleport failed — no compatible API found");
        return entity;
    }
}
