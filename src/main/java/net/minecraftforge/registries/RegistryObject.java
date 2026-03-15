/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of RegistryObject.
 * Performs actual registry lookups via MC's Registry system using reflection.
 */
package net.minecraftforge.registries;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Reimplementation of Forge's RegistryObject that performs real registry
 * lookups via MC's built-in registry system.
 *
 * When create() is called, the RegistryObject will attempt to look up
 * the registered object from the appropriate MC registry. The lookup
 * is deferred — it happens on first access via get().
 */
public class RegistryObject<T> implements Supplier<T> {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private final Object registryName; // Identifier/ResourceLocation
    private final Object registryKey;  // Registry key or forge registry reference
    private T value;
    private boolean resolved = false;

    private RegistryObject(Object name, Object registry) {
        this.registryName = name;
        this.registryKey = registry;
    }

    /**
     * Creates a RegistryObject that lazily resolves from the given registry.
     */
    public static <T> RegistryObject<T> create(Object name, Object registry) {
        return new RegistryObject<>(name, registry);
    }

    /**
     * Creates a RegistryObject with a specific namespace and path.
     */
    public static <T> RegistryObject<T> create(Object name, String modId, Object registry) {
        return new RegistryObject<>(name, registry);
    }

    /**
     * Returns the registered value, performing a lazy lookup on first access.
     */
    @SuppressWarnings("unchecked")
    @Override
    public T get() {
        if (!resolved) {
            resolved = true;
            value = (T) lookupFromRegistry();
        }
        return value;
    }

    /**
     * Returns an Optional containing the value if present.
     */
    public Optional<T> asOptional() {
        return Optional.ofNullable(get());
    }

    /**
     * Returns true if the registry entry exists.
     */
    public boolean isPresent() {
        return get() != null;
    }

    /**
     * Returns the registry name (Identifier/ResourceLocation).
     */
    public Object getId() {
        return registryName;
    }

    /**
     * Attempts to look up the value from MC's registry system.
     */
    private Object lookupFromRegistry() {
        if (registryName == null) return null;

        // Try NeoForge/modern registry: BuiltInRegistries.REGISTRY
        try {
            // Try Registries (Fabric 1.19.3+)
            Class<?> registriesClass = Class.forName("net.minecraft.registry.Registries");

            // If registryKey is a string like "minecraft:item", resolve the registry
            if (registryKey instanceof String) {
                String regName = (String) registryKey;
                Object registry = resolveRegistryByName(registriesClass, regName);
                if (registry != null) {
                    return lookupInRegistry(registry, registryName);
                }
            }

            // Try common registries directly
            for (String fieldName : new String[]{"ITEM", "BLOCK", "ENTITY_TYPE", "BLOCK_ENTITY_TYPE",
                    "POTION", "ENCHANTMENT", "PARTICLE_TYPE", "SOUND_EVENT"}) {
                try {
                    Object registry = registriesClass.getField(fieldName).get(null);
                    Object result = lookupInRegistry(registry, registryName);
                    if (result != null) {
                        LOGGER.fine("[RetroMod] RegistryObject: resolved from Registries." + fieldName);
                        return result;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Try Forge registry
        try {
            Class<?> forgeRegistries = Class.forName("net.minecraftforge.registries.ForgeRegistries");
            for (String fieldName : new String[]{"ITEMS", "BLOCKS", "ENTITY_TYPES", "BLOCK_ENTITY_TYPES",
                    "POTIONS", "ENCHANTMENTS", "PARTICLE_TYPES", "SOUND_EVENTS"}) {
                try {
                    Object registry = forgeRegistries.getField(fieldName).get(null);
                    Object result = lookupInRegistry(registry, registryName);
                    if (result != null) {
                        LOGGER.fine("[RetroMod] RegistryObject: resolved from ForgeRegistries." + fieldName);
                        return result;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        LOGGER.fine("[RetroMod] RegistryObject: could not resolve " + registryName);
        return null;
    }

    private Object resolveRegistryByName(Class<?> registriesClass, String name) {
        // Map common registry names to field names
        switch (name.toLowerCase().replace("minecraft:", "")) {
            case "item": return getField(registriesClass, "ITEM");
            case "block": return getField(registriesClass, "BLOCK");
            case "entity_type": return getField(registriesClass, "ENTITY_TYPE");
            case "block_entity_type": return getField(registriesClass, "BLOCK_ENTITY_TYPE");
            default: return null;
        }
    }

    private Object getField(Class<?> clazz, String name) {
        try { return clazz.getField(name).get(null); } catch (Exception e) { return null; }
    }

    /**
     * Look up an entry in a Registry by Identifier.
     */
    private Object lookupInRegistry(Object registry, Object name) {
        if (registry == null || name == null) return null;
        try {
            // Try registry.get(Identifier)
            for (Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1) {
                    Object result = m.invoke(registry, name);
                    if (result != null) return result;
                }
            }
        } catch (Exception ignored) {}

        // Try registry.getOrEmpty(Identifier)
        try {
            for (Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("getOrEmpty") && m.getParameterCount() == 1) {
                    Optional<?> result = (Optional<?>) m.invoke(registry, name);
                    return result.orElse(null);
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    @Override
    public String toString() {
        return "RegistryObject{name=" + registryName + ", resolved=" + resolved + "}";
    }
}
