/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of FabricBlockEntityTypeBuilder.
 * Delegates to BlockEntityType.Builder via reflection to create real
 * BlockEntityType instances.
 */
package net.fabricmc.fabric.api.object.builder.v1.block.entity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reimplementation that creates real BlockEntityType objects
 * by delegating to BlockEntityType.Builder.create() via reflection.
 */
public class FabricBlockEntityTypeBuilder {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private Object factory; // BlockEntityType.BlockEntityFactory
    private final List<Object> blocks = new ArrayList<>();

    @FunctionalInterface
    public interface Factory<T> {
        T create(Object pos, Object state);
    }

    private FabricBlockEntityTypeBuilder() {}

    /**
     * Creates a builder with the given factory.
     */
    public static FabricBlockEntityTypeBuilder create(Object factory, Object... blocks) {
        FabricBlockEntityTypeBuilder builder = new FabricBlockEntityTypeBuilder();
        builder.factory = factory;
        for (Object block : blocks) {
            builder.blocks.add(block);
        }
        return builder;
    }

    /**
     * Creates a builder without a factory (set later or use default).
     */
    public static FabricBlockEntityTypeBuilder create() {
        return new FabricBlockEntityTypeBuilder();
    }

    /**
     * Adds a supported block.
     */
    public FabricBlockEntityTypeBuilder addBlock(Object block) {
        blocks.add(block);
        return this;
    }

    /**
     * Adds multiple supported blocks.
     */
    public FabricBlockEntityTypeBuilder addBlocks(Object... blocks) {
        for (Object block : blocks) {
            this.blocks.add(block);
        }
        return this;
    }

    /**
     * Builds the BlockEntityType by delegating to the vanilla builder via reflection.
     */
    public Object build() {
        return build(null);
    }

    /**
     * Builds with a specific type parameter (ignored in modern MC).
     */
    public Object build(Object type) {
        try {
            // Try BlockEntityType.Builder.create(factory, blocks...)
            Class<?> betClass = Class.forName("net.minecraft.block.entity.BlockEntityType");
            Class<?> builderClass = null;

            // Find the inner Builder class
            for (Class<?> inner : betClass.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Builder")) {
                    builderClass = inner;
                    break;
                }
            }

            if (builderClass != null && factory != null) {
                // Find create method
                Class<?> blockClass = Class.forName("net.minecraft.block.Block");
                Object[] blockArray = blocks.toArray();

                // Try to find create(BlockEntityFactory, Block...)
                for (Method m : builderClass.getMethods()) {
                    if (m.getName().equals("create") && m.getParameterCount() == 2) {
                        // Create block array of correct type
                        Object typedArray = java.lang.reflect.Array.newInstance(blockClass, blocks.size());
                        for (int i = 0; i < blocks.size(); i++) {
                            java.lang.reflect.Array.set(typedArray, i, blocks.get(i));
                        }

                        Object builder = m.invoke(null, factory, typedArray);

                        // Call build() or build(Type)
                        Method buildMethod = builder.getClass().getMethod("build");
                        Object result = buildMethod.invoke(builder);
                        LOGGER.fine("[RetroMod] FabricBlockEntityTypeBuilder: created real BlockEntityType");
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] FabricBlockEntityTypeBuilder: reflection failed: " + e.getMessage());
        }

        LOGGER.warning("[RetroMod] FabricBlockEntityTypeBuilder: could not create BlockEntityType, returning null");
        return null;
    }
}
