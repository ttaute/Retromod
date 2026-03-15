/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of FabricItemSettings.
 * Delegates to Item.Settings via reflection to create real item settings
 * with actual properties applied.
 */
package net.fabricmc.fabric.api.item.v1;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of FabricItemSettings that delegates to the modern
 * Item.Settings class via reflection.
 *
 * Old: new FabricItemSettings().group(group).maxCount(64)
 * New: new Item.Settings().maxCount(64) (groups are registered differently in 1.19.3+)
 *
 * The internal realSettings object holds a real Item.Settings instance
 * so that the resulting settings can be passed to Item constructors.
 */
public class FabricItemSettings {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private static volatile boolean reflectionInitialized = false;
    private static volatile Class<?> settingsClass;
    private static volatile Constructor<?> settingsConstructor;

    private Object realSettings; // The actual Item.Settings object

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            // Try Item.Settings (Fabric mapped)
            settingsClass = Class.forName("net.minecraft.item.Item$Settings");
            settingsConstructor = settingsClass.getConstructor();
            LOGGER.fine("[RetroMod] FabricItemSettings: delegating to Item.Settings");
        } catch (Exception e) {
            try {
                // Try Item.Properties (Forge/MojMap)
                settingsClass = Class.forName("net.minecraft.world.item.Item$Properties");
                settingsConstructor = settingsClass.getConstructor();
            } catch (Exception ignored) {}
        }
    }

    public FabricItemSettings() {
        if (!reflectionInitialized) initReflection();
        if (settingsConstructor != null) {
            try {
                realSettings = settingsConstructor.newInstance();
            } catch (Exception e) {
                LOGGER.fine("[RetroMod] FabricItemSettings: could not create real Settings");
            }
        }
    }

    /**
     * Returns the real Item.Settings object for use in Item constructors.
     */
    public Object getRealSettings() {
        return realSettings;
    }

    /**
     * Sets the item group (no-op in 1.19.3+ where groups are registered separately).
     */
    public FabricItemSettings group(Object itemGroup) {
        // In 1.19.3+, item groups are registered via ItemGroupEvents, not on settings
        // Try the old .group() method for pre-1.19.3
        if (realSettings != null) {
            try {
                Method groupMethod = realSettings.getClass().getMethod("group",
                    Class.forName("net.minecraft.item.ItemGroup"));
                groupMethod.invoke(realSettings, itemGroup);
            } catch (Exception ignored) {
                // Expected in 1.19.3+ where group() was removed from settings
            }
        }
        return this;
    }

    public FabricItemSettings maxCount(int maxCount) {
        invokeOnSettings("maxCount", int.class, maxCount);
        return this;
    }

    public FabricItemSettings maxDamage(int maxDamage) {
        invokeOnSettings("maxDamage", int.class, maxDamage);
        return this;
    }

    public FabricItemSettings recipeRemainder(Object recipeRemainder) {
        if (realSettings != null) {
            try {
                Class<?> itemClass = Class.forName("net.minecraft.item.Item");
                Method m = realSettings.getClass().getMethod("recipeRemainder", itemClass);
                m.invoke(realSettings, recipeRemainder);
            } catch (Exception ignored) {}
        }
        return this;
    }

    public FabricItemSettings rarity(Object rarity) {
        if (realSettings != null) {
            try {
                Class<?> rarityClass = Class.forName("net.minecraft.util.Rarity");
                Method m = realSettings.getClass().getMethod("rarity", rarityClass);
                m.invoke(realSettings, rarity);
            } catch (Exception ignored) {}
        }
        return this;
    }

    public FabricItemSettings fireproof() {
        if (realSettings != null) {
            try {
                Method m = realSettings.getClass().getMethod("fireproof");
                m.invoke(realSettings);
            } catch (Exception ignored) {}
        }
        return this;
    }

    public FabricItemSettings food(Object foodComponent) {
        if (realSettings != null) {
            try {
                Class<?> foodClass = Class.forName("net.minecraft.item.FoodComponent");
                Method m = realSettings.getClass().getMethod("food", foodClass);
                m.invoke(realSettings, foodComponent);
            } catch (Exception ignored) {}
        }
        return this;
    }

    public FabricItemSettings customDamage(Object handler) {
        // Fabric-specific, try reflection
        if (realSettings != null) {
            try {
                // Look for customDamage method with any parameter type
                for (Method m : realSettings.getClass().getMethods()) {
                    if (m.getName().equals("customDamage") && m.getParameterCount() == 1) {
                        m.invoke(realSettings, handler);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return this;
    }

    public FabricItemSettings equipmentSlot(Object equipmentSlotProvider) {
        // Fabric-specific extension
        if (realSettings != null) {
            try {
                for (Method m : realSettings.getClass().getMethods()) {
                    if (m.getName().equals("equipmentSlot") && m.getParameterCount() == 1) {
                        m.invoke(realSettings, equipmentSlotProvider);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return this;
    }

    private void invokeOnSettings(String methodName, Class<?> paramType, Object value) {
        if (realSettings != null) {
            try {
                Method m = realSettings.getClass().getMethod(methodName, paramType);
                m.invoke(realSettings, value);
            } catch (Exception ignored) {}
        }
    }
}
