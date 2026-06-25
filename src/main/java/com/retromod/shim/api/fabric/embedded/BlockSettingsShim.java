/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps old {@code FabricBlockSettings.of(Material)} onto 1.20's
 * {@code AbstractBlock.Settings.create()} plus property setters, since 1.20 removed the Material system.
 */
public class BlockSettingsShim {

    private static final Map<String, MaterialProperties> MATERIAL_DEFAULTS = new HashMap<>();

    static {
        MATERIAL_DEFAULTS.put("STONE", new MaterialProperties(true, false, false, 1.5f, 6.0f));
        MATERIAL_DEFAULTS.put("WOOD", new MaterialProperties(true, true, false, 2.0f, 3.0f));
        MATERIAL_DEFAULTS.put("METAL", new MaterialProperties(true, false, false, 5.0f, 6.0f));
        MATERIAL_DEFAULTS.put("GLASS", new MaterialProperties(false, false, false, 0.3f, 0.3f));
        MATERIAL_DEFAULTS.put("WOOL", new MaterialProperties(true, true, false, 0.8f, 0.8f));
        MATERIAL_DEFAULTS.put("PLANT", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("DIRT", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("SAND", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("WATER", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("LAVA", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("ICE", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("SNOW", new MaterialProperties(true, false, false, 0.2f, 0.2f));
        MATERIAL_DEFAULTS.put("CLAY", new MaterialProperties(true, false, false, 0.6f, 0.6f));
        MATERIAL_DEFAULTS.put("GOURD", new MaterialProperties(true, false, false, 1.0f, 1.0f));
        MATERIAL_DEFAULTS.put("PORTAL", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("CAKE", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("COBWEB", new MaterialProperties(false, false, false, 4.0f, 4.0f));
        MATERIAL_DEFAULTS.put("PISTON", new MaterialProperties(true, false, false, 1.5f, 1.5f));
        MATERIAL_DEFAULTS.put("DECORATION", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("AIR", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("LEAVES", new MaterialProperties(true, true, false, 0.2f, 0.2f));
        MATERIAL_DEFAULTS.put("SPONGE", new MaterialProperties(true, false, false, 0.6f, 0.6f));
        MATERIAL_DEFAULTS.put("SHULKER_BOX", new MaterialProperties(true, false, false, 2.0f, 2.0f));
        MATERIAL_DEFAULTS.put("NETHER_WOOD", new MaterialProperties(true, false, false, 2.0f, 3.0f));
    }
    
    /** Translate {@code FabricBlockSettings.of(material)} to {@code AbstractBlock.Settings.create()}. */
    public static Object of(Object material) {
        try {
            Class<?> settingsClass = Class.forName("net.minecraft.block.AbstractBlock$Settings");
            Object settings = settingsClass.getMethod("create").invoke(null);

            if (material != null) {
                MaterialProperties props = MATERIAL_DEFAULTS.getOrDefault(getMaterialName(material),
                    new MaterialProperties(true, false, false, 1.0f, 1.0f));

                Method strength = settingsClass.getMethod("strength", float.class, float.class);
                settings = strength.invoke(settings, props.hardness, props.resistance);

                if (!props.solid) {
                    try {
                        settings = settingsClass.getMethod("noCollision").invoke(settings);
                    } catch (NoSuchMethodException e) {
                        // setter absent on this version
                    }
                }

                if (props.burnable) {
                    try {
                        settings = settingsClass.getMethod("burnable").invoke(settings);
                    } catch (NoSuchMethodException e) {
                        // setter absent on this version
                    }
                }

                if (props.replaceable) {
                    try {
                        settings = settingsClass.getMethod("replaceable").invoke(settings);
                    } catch (NoSuchMethodException e) {
                        // setter absent on this version
                    }
                }
            }

            return settings;

        } catch (Exception e) {
            try {
                Class<?> settingsClass = Class.forName("net.minecraft.block.AbstractBlock$Settings");
                return settingsClass.getMethod("create").invoke(null);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot create block settings", e2);
            }
        }
    }

    private static String getMaterialName(Object material) {
        try {
            String str = material.toString().toUpperCase();
            for (String key : MATERIAL_DEFAULTS.keySet()) {
                if (str.contains(key)) {
                    return key;
                }
            }
            return "STONE";
        } catch (Exception e) {
            return "STONE";
        }
    }

    private static class MaterialProperties {
        final boolean solid;
        final boolean burnable;
        final boolean replaceable;
        final float hardness;
        final float resistance;
        
        MaterialProperties(boolean solid, boolean burnable, boolean replaceable, 
                          float hardness, float resistance) {
            this.solid = solid;
            this.burnable = burnable;
            this.replaceable = replaceable;
            this.hardness = hardness;
            this.resistance = resistance;
        }
    }
}
