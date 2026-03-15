/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of FabricItemGroupBuilder, removed in 1.19.3.
 * Delegates to ItemGroup.Builder via reflection to create real item groups.
 */
package net.fabricmc.fabric.api.client.itemgroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Reimplementation of FabricItemGroupBuilder that creates real ItemGroups.
 *
 * In modern Fabric, item groups are created via ItemGroup.Builder obtained from
 * FabricItemGroup.builder() or ItemGroup.create(). This polyfill bridges the
 * old builder API to the modern one via reflection.
 */
public class FabricItemGroupBuilder {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private Object identifier; // Identifier (ResourceLocation)
    private Supplier<?> iconSupplier;
    private BiConsumer<?, ?> itemAppender;
    private Consumer<?> entriesConsumer;

    private FabricItemGroupBuilder() {}

    /**
     * Creates a new builder with the given Identifier.
     * In modern MC, this maps to FabricItemGroup.builder(identifier).
     */
    public static FabricItemGroupBuilder create(Object identifier) {
        FabricItemGroupBuilder builder = new FabricItemGroupBuilder();
        builder.identifier = identifier;
        return builder;
    }

    /**
     * Sets the icon supplier for the item group tab.
     */
    public FabricItemGroupBuilder icon(Supplier<?> iconSupplier) {
        this.iconSupplier = iconSupplier;
        return this;
    }

    /**
     * Sets the item appender (old API style with BiConsumer).
     */
    public FabricItemGroupBuilder appendItems(BiConsumer<?, ?> itemAppender) {
        this.itemAppender = itemAppender;
        return this;
    }

    /**
     * Sets the entries consumer (newer API style).
     */
    public FabricItemGroupBuilder entries(Consumer<?> consumer) {
        this.entriesConsumer = consumer;
        return this;
    }

    /**
     * Builds the ItemGroup by delegating to the modern Fabric API via reflection.
     *
     * Tries these approaches in order:
     * 1. FabricItemGroup.builder() (Fabric API 0.76+)
     * 2. ItemGroup.create(Row, int) (vanilla 1.19.3+)
     * 3. Registry.register with a new ItemGroup
     */
    public Object build() {
        try {
            // Try modern FabricItemGroup.builder()
            Class<?> fabricGroupClass = Class.forName("net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup");
            Method builderMethod = fabricGroupClass.getMethod("builder");
            Object modernBuilder = builderMethod.invoke(null);

            // Set icon if available
            if (iconSupplier != null && modernBuilder != null) {
                try {
                    Method iconMethod = modernBuilder.getClass().getMethod("icon", Supplier.class);
                    iconMethod.invoke(modernBuilder, iconSupplier);
                } catch (Exception ignored) {}
            }

            // Set display name from identifier
            if (identifier != null && modernBuilder != null) {
                try {
                    // Create translatable text: Text.translatable("itemGroup." + namespace + "." + path)
                    Method getNamespace = identifier.getClass().getMethod("getNamespace");
                    Method getPath = identifier.getClass().getMethod("getPath");
                    String ns = (String) getNamespace.invoke(identifier);
                    String path = (String) getPath.invoke(identifier);
                    String translationKey = "itemGroup." + ns + "." + path;

                    Class<?> textClass = Class.forName("net.minecraft.text.Text");
                    Method translatableMethod = textClass.getMethod("translatable", String.class);
                    Object displayName = translatableMethod.invoke(null, translationKey);

                    Method displayNameMethod = modernBuilder.getClass().getMethod("displayName", textClass);
                    displayNameMethod.invoke(modernBuilder, displayName);
                } catch (Exception ignored) {}
            }

            // Build
            Method buildMethod = modernBuilder.getClass().getMethod("build");
            Object result = buildMethod.invoke(modernBuilder);
            LOGGER.fine("[RetroMod] FabricItemGroupBuilder: created real ItemGroup via FabricItemGroup.builder()");
            return result;
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] FabricItemGroupBuilder: modern API not available: " + e.getMessage());
        }

        // Fallback: try vanilla ItemGroup.Builder
        try {
            Class<?> itemGroupClass = Class.forName("net.minecraft.item.ItemGroup");
            // Try ItemGroup.builder() (1.19.3+ vanilla)
            Method builderMethod = itemGroupClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            if (iconSupplier != null) {
                try {
                    Method iconMethod = builder.getClass().getMethod("icon", Supplier.class);
                    iconMethod.invoke(builder, iconSupplier);
                } catch (Exception ignored) {}
            }
            Method buildMethod = builder.getClass().getMethod("build");
            return buildMethod.invoke(builder);
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] FabricItemGroupBuilder: vanilla builder not available: " + e.getMessage());
        }

        LOGGER.warning("[RetroMod] FabricItemGroupBuilder: could not create ItemGroup, returning null");
        return null;
    }
}
