/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/** Bridges FabricBlockEntityType.Builder (removed in 1.21.2) to FabricBlockEntityTypeBuilder. */
public class FabricBlockEntityTypeBuilderShim<T> {

    private static Class<?> builderClass;
    private static Method createMethod;
    private static Method addBlockMethod;
    private static Method addBlocksMethod;
    private static Method buildMethod;
    private static boolean initialized = false;
    
    private Object realBuilder;
    private final Object blockEntityFactory;
    private final List<Object> blocks = new ArrayList<>();
    
    private FabricBlockEntityTypeBuilderShim(Object factory) {
        this.blockEntityFactory = factory;
        initializeRealBuilder();
    }
    
    private static synchronized void initializeApi() {
        if (initialized) return;
        initialized = true;
        
        try {
            builderClass = Class.forName(
                "net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder"
            );

            for (Method m : builderClass.getMethods()) {
                if (m.getName().equals("create") && Modifier.isStatic(m.getModifiers())) {
                    createMethod = m;
                    break;
                }
            }

            for (Method m : builderClass.getMethods()) {
                if (m.getName().equals("addBlock") && m.getParameterCount() == 1) {
                    addBlockMethod = m;
                } else if (m.getName().equals("addBlocks") && m.getParameterCount() == 1) {
                    addBlocksMethod = m;
                } else if (m.getName().equals("build") && m.getParameterCount() == 0) {
                    buildMethod = m;
                }
            }

        } catch (ClassNotFoundException e) {
            System.err.println("Retromod: FabricBlockEntityTypeBuilder not found");
        }
    }
    
    private void initializeRealBuilder() {
        initializeApi();
        
        if (createMethod != null) {
            try {
                realBuilder = createMethod.invoke(null, blockEntityFactory);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create FabricBlockEntityTypeBuilder", e);
            }
        }
    }
    
    public static <T> FabricBlockEntityTypeBuilderShim<T> create(Object factory, Object... blocks) {
        FabricBlockEntityTypeBuilderShim<T> builder = new FabricBlockEntityTypeBuilderShim<>(factory);
        
        if (blocks != null && blocks.length > 0) {
            for (Object block : blocks) {
                builder.addBlock(block);
            }
        }
        
        return builder;
    }
    
    public FabricBlockEntityTypeBuilderShim<T> addBlock(Object block) {
        blocks.add(block);
        
        if (realBuilder != null && addBlockMethod != null) {
            try {
                realBuilder = addBlockMethod.invoke(realBuilder, block);
            } catch (Exception e) {
                System.err.println("Retromod: Failed to add block to builder: " + e);
            }
        }
        
        return this;
    }
    
    public FabricBlockEntityTypeBuilderShim<T> addBlocks(Object... blocks) {
        for (Object block : blocks) {
            addBlock(block);
        }
        return this;
    }
    
    public Object build() {
        if (realBuilder != null && buildMethod != null) {
            try {
                return buildMethod.invoke(realBuilder);
            } catch (Exception e) {
                throw new RuntimeException("Failed to build BlockEntityType", e);
            }
        }
        return createBlockEntityTypeFallback();
    }

    /** Older API signature; the id was for registration, ignored here. */
    public Object build(Object id) {
        return build();
    }

    private Object createBlockEntityTypeFallback() {
        try {
            Class<?> vanillaBuilderClass = Class.forName(
                "net.minecraft.world.level.block.entity.BlockEntityType$Builder"
            );

            Method ofMethod = null;
            for (Method m : vanillaBuilderClass.getMethods()) {
                if (m.getName().equals("of") && Modifier.isStatic(m.getModifiers())) {
                    ofMethod = m;
                    break;
                }
            }

            if (ofMethod != null) {
                Object[] blocksArray = blocks.toArray();
                Object builder = ofMethod.invoke(null, blockEntityFactory, blocksArray);
                Method build = vanillaBuilderClass.getMethod("build", (Class<?>[]) null);
                return build.invoke(builder);
            }

        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to create BlockEntityType via fallback", e);
        }

        throw new RuntimeException("Retromod: No BlockEntityType creation method available");
    }
}
