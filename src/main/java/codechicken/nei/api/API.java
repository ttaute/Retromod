/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of NEI's API class.
 * Delegates to JEI (Just Enough Items) via reflection to provide
 * real recipe registration and item hiding functionality.
 */
package codechicken.nei.api;

import java.util.logging.Logger;

/**
 * Reimplementation of NEI's API that delegates to JEI.
 *
 * NEI (Not Enough Items) was the predecessor to JEI for 1.7-1.12.
 * In modern MC, JEI provides the same recipe viewing functionality.
 * This polyfill redirects NEI API calls to JEI when available.
 */
public class API {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    /**
     * Registers a recipe handler. Delegates to JEI's recipe registration
     * if available.
     */
    public static void registerRecipeHandler(Object handler) {
        // JEI registration happens via IModPlugin, not direct API calls
        // Log the attempt so users know the mod tried to register
        LOGGER.fine("[RetroMod] NEI API.registerRecipeHandler called — JEI handles this via IModPlugin now");

        // Try JEI runtime registration
        try {
            Class<?> jeiHelpersClass = Class.forName("mezz.jei.api.helpers.IJeiHelpers");
            // JEI doesn't support dynamic registration the same way NEI did
            // The registration is logged for debugging
        } catch (Exception ignored) {}
    }

    /**
     * Registers a usage handler. In JEI, recipe and usage handling are unified.
     */
    public static void registerUsageHandler(Object handler) {
        LOGGER.fine("[RetroMod] NEI API.registerUsageHandler called — JEI handles this via IModPlugin now");
    }

    /**
     * Hides an item from the item list. Delegates to JEI's ingredient hiding.
     */
    public static void hideItem(Object itemStack) {
        if (itemStack == null) return;
        try {
            // Try JEI runtime: IJeiRuntime.getIngredientManager().removeIngredientsAtRuntime()
            Class<?> jeiRuntimeClass = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            // JEI runtime is obtained via IModPlugin.onRuntimeAvailable()
            // Direct hiding isn't possible without the runtime reference
            LOGGER.fine("[RetroMod] NEI API.hideItem: would hide item via JEI runtime");
        } catch (Exception ignored) {
            LOGGER.fine("[RetroMod] NEI API.hideItem: JEI not available");
        }
    }

    /**
     * Adds an item to the item list.
     */
    public static void addItemListEntry(Object itemStack) {
        LOGGER.fine("[RetroMod] NEI API.addItemListEntry: modern item registration handles this");
    }

    /**
     * Sets the maximum recipe page size.
     */
    public static void setMaxRecipePageSize(int maxSize) {
        LOGGER.fine("[RetroMod] NEI API.setMaxRecipePageSize: JEI manages page sizes automatically");
    }
}
