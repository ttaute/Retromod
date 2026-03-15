/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of BaublesApi.
 * Delegates to Trinkets (Fabric) or Curios (Forge/NeoForge) via reflection
 * to provide real accessory slot functionality.
 */
package baubles.api;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of BaublesApi that delegates to modern accessory APIs.
 *
 * Baubles was a Forge 1.7-1.12 mod providing accessory slots (rings, amulets, etc).
 * In modern MC, this functionality is provided by:
 * - Trinkets (Fabric): dev.emi.trinkets.api.TrinketsApi
 * - Curios (Forge/NeoForge): top.theillusivec4.curios.api.CuriosApi
 *
 * This polyfill attempts to delegate to whichever is available.
 */
public class BaublesApi {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    /**
     * Gets the baubles inventory for a player.
     * Delegates to Trinkets or Curios if available.
     */
    public static Object getBaubles(Object player) {
        if (player == null) return null;

        // Try Trinkets (Fabric)
        try {
            Class<?> trinketsApiClass = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            Method getComponent = trinketsApiClass.getMethod("getTrinketComponent",
                Class.forName("net.minecraft.entity.LivingEntity"));
            Object component = getComponent.invoke(null, player);
            if (component != null) {
                LOGGER.fine("[RetroMod] BaublesApi: delegating to Trinkets");
                return component;
            }
        } catch (Exception ignored) {}

        // Try Curios (Forge/NeoForge)
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory",
                Class.forName("net.minecraft.world.entity.LivingEntity"));
            Object lazyOptional = getCuriosInventory.invoke(null, player);
            if (lazyOptional != null) {
                // Resolve the LazyOptional
                Method resolve = lazyOptional.getClass().getMethod("resolve");
                Object optional = resolve.invoke(lazyOptional);
                Method orElse = optional.getClass().getMethod("orElse", Object.class);
                Object inventory = orElse.invoke(optional, (Object) null);
                if (inventory != null) {
                    LOGGER.fine("[RetroMod] BaublesApi: delegating to Curios");
                    return inventory;
                }
            }
        } catch (Exception ignored) {}

        LOGGER.fine("[RetroMod] BaublesApi: no trinket/curio API available");
        return null;
    }

    /**
     * Gets the baubles handler for a player.
     * Same as getBaubles() but returns the handler interface.
     */
    public static Object getBaublesHandler(Object player) {
        return getBaubles(player);
    }
}
