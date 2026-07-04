/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;

/**
 * Pre-1.17 Entity field-access bridge for Fabric on pre-26.1 hosts (intermediary names).
 *
 * <p>{@code Entity.onGround} ({@code class_1297.field_5952}) was PUBLIC through 1.16.x and
 * went non-public in the 1.17 access cleanup; a 1.16.5 mod reading it directly dies
 * {@code IllegalAccessError: tried to access private field net.minecraft.class_1297.field_5952}
 * on a modern host (found live, round 6 of the snapshot.8 acceptance pass: Collective's
 * {@code CollectiveEvents} on a 1.20.1 server). The accessors have existed since 1.16 with
 * stable intermediary IDs ({@code method_24828} isOnGround / {@code method_24830} setOnGround,
 * verified against the 1.20.1 intermediary jar), so GETFIELD/PUTFIELD rewrite cleanly.
 *
 * <p>Host-introspecting like the other Pre* bridges: registers only when the field actually
 * lost its public access and both accessors exist, so 1.16.x hosts are untouched. The probe
 * uses {@code Class.forName(name, false, loader)} (never initialize, pitfall #14).
 */
public final class Pre1_17EntityFieldBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String ENTITY = "net/minecraft/class_1297";

    private Pre1_17EntityFieldBridge() {}

    /** Register the field-to-accessor rewrites when the host's Entity shape needs them. */
    public static void register(RetromodTransformer transformer) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> entity = Class.forName("net.minecraft.class_1297", false, loader);
            java.lang.reflect.Field onGround = entity.getDeclaredField("field_5952");
            if (Modifier.isPublic(onGround.getModifiers())) {
                return; // pre-1.17 host: the field is still public, direct access works
            }
            entity.getMethod("method_24828");                // isOnGround, throws if absent
            entity.getMethod("method_24830", boolean.class); // setOnGround
        } catch (Throwable t) {
            return; // not an intermediary host, or an unexpected shape: never guess
        }

        transformer.registerFieldAccessorRedirect(ENTITY, "field_5952",
                "method_24828", "()Z", "method_24830", "(Z)V");
        LOGGER.info("Registered pre-1.17 Entity field bridge "
                + "(onGround field access -> isOnGround/setOnGround)");
    }
}
