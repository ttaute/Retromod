/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shim for FabricItemGroupBuilder which was removed in 1.19.3.
 * 
 * Old API:
 *   FabricItemGroupBuilder.create(id)
 *       .icon(() -> new ItemStack(Items.DIAMOND))
 *       .appendItems(stacks -> { ... })
 *       .build()
 * 
 * New API (1.19.3+):
 *   FabricItemGroup.builder()
 *       .icon(() -> new ItemStack(Items.DIAMOND))
 *       .displayName(Text.translatable("..."))
 *       .entries((context, entries) -> { ... })
 *       .build()
 */
public class FabricItemGroupBuilderShim {
    
    private Object identifier;
    private Supplier<?> iconSupplier;
    private Consumer<?> appendItemsConsumer;
    
    private FabricItemGroupBuilderShim() {}
    
    /**
     * Static factory method matching old FabricItemGroupBuilder.create(Identifier).
     */
    public static FabricItemGroupBuilderShim create(Object identifier) {
        FabricItemGroupBuilderShim builder = new FabricItemGroupBuilderShim();
        builder.identifier = identifier;
        return builder;
    }
    
    /**
     * Set the icon supplier.
     */
    public FabricItemGroupBuilderShim icon(Supplier<?> iconSupplier) {
        this.iconSupplier = iconSupplier;
        return this;
    }
    
    /**
     * Old appendItems method - takes Consumer<List<ItemStack>>.
     */
    public FabricItemGroupBuilderShim appendItems(Consumer<?> appendItems) {
        this.appendItemsConsumer = appendItems;
        return this;
    }
    
    /**
     * Legacy static {@code FabricItemGroupBuilder.build(Identifier, Supplier<ItemStack>) → ItemGroup}
     * (the form #57's callers hit — Earth2Java's {@code Earth2JavaMod.<clinit>} among others).
     * Previously absent, so the class-redirect-rewritten call resolved to nothing and the mod
     * hard-crashed with {@code NoSuchMethodError}. Delegates to the modern-API path used by
     * {@link #build()} after wiring the id + icon supplier.
     *
     * <p>The MC types in this signature are compile-time stubs (see
     * {@code src/main/java/net/minecraft/class_2960.java} and {@code class_1761.java}); they're
     * stripped from the production jar so MC's real classes resolve at runtime — this is the
     * same compile-only-stub trick the {@code @Mod} stubs use.
     */
    public static net.minecraft.class_1761 build(net.minecraft.class_2960 id, Supplier<?> stack) {
        try {
            FabricItemGroupBuilderShim builder = create(id);
            if (stack != null) builder.icon(stack);
            Object result = builder.build();
            return (net.minecraft.class_1761) result;
        } catch (Throwable t) {
            // Best-effort: when neither the modern nor legacy Fabric ItemGroup API resolves on
            // the host, returning null lets the caller's static init keep going (Earth2Java's
            // {@code Earth2JavaMod.<clinit>}, #57). Items may end up ungrouped in creative tabs
            // but the mod itself loads, which is strictly better than an ExceptionInInitializerError
            // taking the whole entry point down.
            return null;
        }
    }

    /**
     * Build the item group using the new API.
     */
    public Object build() {
        try {
            // Try new FabricItemGroup.builder() API
            Class<?> fabricItemGroupClass = Class.forName("net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup");
            Method builderMethod = fabricItemGroupClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);
            
            Class<?> builderClass = builder.getClass();
            
            // Set icon
            if (iconSupplier != null) {
                Method iconMethod = findMethod(builderClass, "icon", Supplier.class);
                if (iconMethod != null) {
                    builder = iconMethod.invoke(builder, iconSupplier);
                }
            }
            
            // Set display name from identifier
            if (identifier != null) {
                try {
                    Method displayNameMethod = findMethod(builderClass, "displayName");
                    if (displayNameMethod != null) {
                        // Create Text from identifier
                        Object text = createDisplayName(identifier);
                        builder = displayNameMethod.invoke(builder, text);
                    }
                } catch (Exception e) {
                    // Ignore display name errors
                }
            }
            
            // Convert appendItems to entries
            if (appendItemsConsumer != null) {
                try {
                    // The new entries() takes a BiConsumer<DisplayContext, Entries>
                    // We need to adapt the old Consumer<List<ItemStack>>
                    Method entriesMethod = findMethod(builderClass, "entries");
                    if (entriesMethod != null) {
                        Object entriesCallback = createEntriesCallback(appendItemsConsumer);
                        builder = entriesMethod.invoke(builder, entriesCallback);
                    }
                } catch (Exception e) {
                    // Ignore entries errors
                }
            }
            
            // Build
            Method buildMethod = findMethod(builderClass, "build");
            return buildMethod.invoke(builder);
            
        } catch (Exception e) {
            // Try legacy API
            return tryLegacyBuild();
        }
    }
    
    private Object tryLegacyBuild() {
        try {
            // Try the actual old FabricItemGroupBuilder if it exists
            Class<?> oldBuilderClass = Class.forName("net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder");
            Method createMethod = oldBuilderClass.getMethod("create", 
                Class.forName("net.minecraft.util.Identifier"));
            Object builder = createMethod.invoke(null, identifier);
            
            if (iconSupplier != null) {
                Method iconMethod = oldBuilderClass.getMethod("icon", Supplier.class);
                builder = iconMethod.invoke(builder, iconSupplier);
            }
            
            if (appendItemsConsumer != null) {
                Method appendMethod = oldBuilderClass.getMethod("appendItems", Consumer.class);
                builder = appendMethod.invoke(builder, appendItemsConsumer);
            }
            
            Method buildMethod = oldBuilderClass.getMethod("build");
            return buildMethod.invoke(builder);
            
        } catch (Exception e) {
            throw new RuntimeException("Cannot build item group with either old or new API", e);
        }
    }
    
    private Object createDisplayName(Object identifier) {
        try {
            // Create Text.translatable("itemGroup." + namespace + "." + path)
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Method translatableMethod = textClass.getMethod("translatable", String.class);
            
            String namespace = identifier.getClass().getMethod("getNamespace").invoke(identifier).toString();
            String path = identifier.getClass().getMethod("getPath").invoke(identifier).toString();
            String key = "itemGroup." + namespace + "." + path;
            
            return translatableMethod.invoke(null, key);
        } catch (Exception e) {
            return null;
        }
    }
    
    private Object createEntriesCallback(Consumer<?> oldConsumer) {
        // Create a lambda that adapts old API to new API
        // This is complex - we return a proxy
        return java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] { findEntriesInterface() },
            (proxy, method, args) -> {
                if (method.getName().equals("accept") && args.length == 2) {
                    // args[0] = DisplayContext, args[1] = Entries
                    // Create a list and call old consumer
                    java.util.List<Object> items = new java.util.ArrayList<>();
                    ((Consumer<java.util.List<Object>>) oldConsumer).accept(items);
                    
                    // Add items to new Entries object
                    Object entries = args[1];
                    Method addMethod = entries.getClass().getMethod("add", 
                        Class.forName("net.minecraft.item.ItemStack"));
                    for (Object item : items) {
                        addMethod.invoke(entries, item);
                    }
                }
                return null;
            }
        );
    }
    
    private Class<?> findEntriesInterface() {
        try {
            return Class.forName("net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents$ModifyEntries");
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName("java.util.function.BiConsumer");
            } catch (ClassNotFoundException e2) {
                return Object.class;
            }
        }
    }
    
    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            if (params.length > 0) {
                return clazz.getMethod(name, params);
            }
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
