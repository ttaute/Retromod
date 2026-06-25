/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.item.embedded;

/**
 * Bridge between old NBT-based ItemStack API and new DataComponent system (1.20.5+).
 * Provides static helper methods that the bytecode transformer redirects old calls to.
 *
 * Uses reflection to avoid compile-time dependency on MC classes, allowing this
 * bridge to work across multiple MC versions at runtime.
 *
 * Flow for getTag():
 *   Old code: itemStack.getTag()
 *   Transformed to: ItemStackNbtBridge.getTag(itemStack)
 *   At runtime: tries DataComponents.CUSTOM_DATA first, falls back to old API
 */
public class ItemStackNbtBridge {

    /**
     * Gets NBT data from an ItemStack, bridging old getTag() to new component system.
     * Uses reflection to avoid compile-time dependency on MC classes.
     */
    public static Object getTag(Object itemStack) {
        try {
            // Try new component system first (1.20.5+)
            Class<?> dataComponents = Class.forName("net.minecraft.core.component.DataComponents");
            Object customDataType = dataComponents.getField("CUSTOM_DATA").get(null);
            Object result = itemStack.getClass().getMethod("get",
                Class.forName("net.minecraft.core.component.DataComponentType")).invoke(itemStack, customDataType);
            if (result != null) {
                // CustomData.getUnsafe() returns CompoundTag
                return result.getClass().getMethod("getUnsafe").invoke(result);
            }
            return null;
        } catch (Exception e) {
            // Fallback to old API (pre-1.20.5)
            try {
                return itemStack.getClass().getMethod("getTag").invoke(itemStack);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * Checks whether an ItemStack has NBT data attached.
     * Bridges old hasTag() to DataComponents.CUSTOM_DATA check.
     */
    public static boolean hasTag(Object itemStack) {
        try {
            Class<?> dataComponents = Class.forName("net.minecraft.core.component.DataComponents");
            Object customDataType = dataComponents.getField("CUSTOM_DATA").get(null);
            return (boolean) itemStack.getClass().getMethod("has",
                Class.forName("net.minecraft.core.component.DataComponentType")).invoke(itemStack, customDataType);
        } catch (Exception e) {
            try {
                return (boolean) itemStack.getClass().getMethod("hasTag").invoke(itemStack);
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /**
     * Sets NBT data on an ItemStack, bridging old setTag() to DataComponents.CUSTOM_DATA.
     * Creates a CustomData wrapper around the CompoundTag for the new system.
     */
    public static void setTag(Object itemStack, Object compoundTag) {
        try {
            // Create CustomData from CompoundTag
            Class<?> customDataClass = Class.forName("net.minecraft.world.item.component.CustomData");
            Object customData = customDataClass.getMethod("of",
                Class.forName("net.minecraft.nbt.CompoundTag")).invoke(null, compoundTag);
            Class<?> dataComponents = Class.forName("net.minecraft.core.component.DataComponents");
            Object customDataType = dataComponents.getField("CUSTOM_DATA").get(null);
            itemStack.getClass().getMethod("set",
                Class.forName("net.minecraft.core.component.DataComponentType"), Object.class)
                .invoke(itemStack, customDataType, customData);
        } catch (Exception e) {
            try {
                itemStack.getClass().getMethod("setTag",
                    Class.forName("net.minecraft.nbt.CompoundTag")).invoke(itemStack, compoundTag);
            } catch (Exception e2) {
                // Give up
            }
        }
    }

    /**
     * Gets or creates NBT data on an ItemStack.
     * Bridges old getOrCreateTag(): if no tag exists, creates a new CompoundTag
     * and attaches it to the ItemStack.
     */
    public static Object getOrCreateTag(Object itemStack) {
        Object tag = getTag(itemStack);
        if (tag != null) return tag;
        try {
            Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
            Object newTag = compoundTagClass.getDeclaredConstructor().newInstance();
            setTag(itemStack, newTag);
            return newTag;
        } catch (Exception e) {
            return null;
        }
    }
}
