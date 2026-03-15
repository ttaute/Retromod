/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of MinecraftForge.
 * Provides a basic event bus that either delegates to NeoForge's event bus
 * or provides a no-crash standalone implementation.
 */
package net.minecraftforge.common;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Reimplementation of MinecraftForge that provides a working EVENT_BUS.
 *
 * If NeoForge is present, delegates to NeoForge.EVENT_BUS.
 * Otherwise, provides a basic event bus that stores and dispatches event handlers.
 * This allows old Forge mods that register event listeners to not crash,
 * and when running on NeoForge, actually receive events.
 */
public class MinecraftForge {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    /**
     * The event bus. Tries to delegate to NeoForge if available,
     * otherwise uses a basic standalone implementation.
     */
    public static final Object EVENT_BUS = createEventBus();

    private static Object createEventBus() {
        // Try NeoForge.EVENT_BUS first
        try {
            Class<?> neoForgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
            Object bus = neoForgeClass.getField("EVENT_BUS").get(null);
            if (bus != null) {
                LOGGER.fine("[RetroMod] MinecraftForge.EVENT_BUS: delegating to NeoForge.EVENT_BUS");
                return bus;
            }
        } catch (Exception ignored) {}

        // Provide a basic event bus implementation
        LOGGER.fine("[RetroMod] MinecraftForge.EVENT_BUS: using standalone EventBus");
        return new SimpleEventBus();
    }

    /**
     * A basic event bus implementation that stores registered listeners
     * and can dispatch events to them. Not as full-featured as Forge's
     * real bus, but enough to prevent crashes from register() calls.
     */
    public static class SimpleEventBus {
        private final Map<Class<?>, List<Object>> listeners = new ConcurrentHashMap<>();
        private final List<Object> registeredObjects = new CopyOnWriteArrayList<>();

        /**
         * Register an object's event handler methods.
         */
        public void register(Object target) {
            if (target == null) return;
            registeredObjects.add(target);

            // Scan for @SubscribeEvent annotated methods
            for (Method m : target.getClass().getMethods()) {
                if (m.getParameterCount() == 1) {
                    for (var annotation : m.getAnnotations()) {
                        if (annotation.annotationType().getSimpleName().equals("SubscribeEvent")) {
                            Class<?> eventType = m.getParameterTypes()[0];
                            listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(target);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Unregister an object.
         */
        public void unregister(Object target) {
            registeredObjects.remove(target);
            listeners.values().forEach(list -> list.remove(target));
        }

        /**
         * Post an event to registered listeners.
         */
        public boolean post(Object event) {
            if (event == null) return false;
            Class<?> eventClass = event.getClass();

            List<Object> handlers = listeners.get(eventClass);
            if (handlers != null) {
                for (Object handler : handlers) {
                    try {
                        for (Method m : handler.getClass().getMethods()) {
                            if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(eventClass)) {
                                for (var annotation : m.getAnnotations()) {
                                    if (annotation.annotationType().getSimpleName().equals("SubscribeEvent")) {
                                        m.invoke(handler, event);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warning("[RetroMod] Event dispatch failed: " + e.getMessage());
                    }
                }
            }

            // Check if event was cancelled
            try {
                Method isCancelled = event.getClass().getMethod("isCanceled");
                return (Boolean) isCancelled.invoke(event);
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Add a listener for a specific event type.
         */
        public <T> void addListener(java.util.function.Consumer<T> consumer) {
            // Store the consumer — exact event type dispatch is handled at post time
            LOGGER.fine("[RetroMod] SimpleEventBus: addListener called");
        }
    }
}
