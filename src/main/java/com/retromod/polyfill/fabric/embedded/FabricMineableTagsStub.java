/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub for net.fabricmc.fabric.api.mininglevel.v1.FabricMineableTags,
 * which was removed in Fabric API 26.1 (fabric-mining-level-api-v1 deprecated and removed).
 *
 * Old mods like Jade reference FabricMineableTags.SWORD_MINEABLE to create
 * tool handlers. This stub provides the tag keys using reflection to avoid
 * compile-time dependency on MC classes.
 *
 * The tag keys point to vanilla tags that still exist:
 * - SWORD_MINEABLE → minecraft:mineable/sword (or fabric:mineable/sword)
 *
 * Note: The old fabric-mining-level-api tags have been merged into vanilla
 * or the convention tags v2 module.
 */
public class FabricMineableTagsStub {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-MineableTags");

    // These fields are typed as Object but will hold TagKey<Block> instances
    // at runtime. The JVM allows this since old bytecode accesses them via
    // GETSTATIC which performs the type narrowing at the use site.
    public static final Object SWORD_MINEABLE;
    public static final Object AXE_MINEABLE;
    public static final Object HOE_MINEABLE;
    public static final Object PICKAXE_MINEABLE;
    public static final Object SHOVEL_MINEABLE;
    public static final Object SHEARS_MINEABLE;

    static {
        SWORD_MINEABLE = createBlockTagKey("minecraft", "mineable/sword");
        AXE_MINEABLE = createBlockTagKey("minecraft", "mineable/axe");
        HOE_MINEABLE = createBlockTagKey("minecraft", "mineable/hoe");
        PICKAXE_MINEABLE = createBlockTagKey("minecraft", "mineable/pickaxe");
        SHOVEL_MINEABLE = createBlockTagKey("minecraft", "mineable/shovel");
        SHEARS_MINEABLE = createBlockTagKey("fabric", "mineable/shears");
    }

    /**
     * Create a TagKey<Block> via reflection.
     * TagKey.create(ResourceKey<Registry<Block>>, ResourceLocation)
     */
    private static Object createBlockTagKey(String namespace, String path) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            // Get Registries.BLOCK (ResourceKey<Registry<Block>>)
            Class<?> registriesClass = cl.loadClass("net.minecraft.core.registries.Registries");
            Object blockRegistryKey = registriesClass.getField("BLOCK").get(null);

            // Create ResourceLocation
            Class<?> rlClass = cl.loadClass("net.minecraft.resources.Identifier");
            Object resourceLocation = null;
            // Try Identifier(String, String) constructor
            try {
                resourceLocation = rlClass.getConstructor(String.class, String.class)
                    .newInstance(namespace, path);
            } catch (NoSuchMethodException e) {
                // Try ResourceLocation.fromNamespaceAndPath(String, String) static method
                try {
                    resourceLocation = rlClass.getMethod("fromNamespaceAndPath", String.class, String.class)
                        .invoke(null, namespace, path);
                } catch (NoSuchMethodException e2) {
                    // Try parse
                    resourceLocation = rlClass.getMethod("parse", String.class)
                        .invoke(null, namespace + ":" + path);
                }
            }

            // TagKey.create(ResourceKey, ResourceLocation)
            Class<?> tagKeyClass = cl.loadClass("net.minecraft.tags.TagKey");
            return tagKeyClass.getMethod("create",
                    cl.loadClass("net.minecraft.resources.ResourceKey"),
                    rlClass)
                .invoke(null, blockRegistryKey, resourceLocation);

        } catch (Exception e) {
            LOGGER.warn("Failed to create block tag key {}:{} — {}", namespace, path, e.getMessage());
            return null;
        }
    }
}
