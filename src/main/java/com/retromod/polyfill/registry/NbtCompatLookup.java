/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.polyfill.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Polyfill for the MC 1.21.5+ NBT getter signature change.
 *
 * <p>Before 1.21.5 {@code CompoundTag.getString(String)} returned {@code String}
 * directly (empty string on miss); 1.21.5+ returns {@code Optional<String>}.
 * {@code getInt}, {@code getDouble}, and {@code ListTag.getString(int)} changed
 * the same way. Mods built against the old shape hit {@code NoSuchMethodError}.
 *
 * <p>Each helper takes the receiver as its first argument (the call is
 * devirtualized to {@code INVOKESTATIC}), resolves whichever signature the
 * runtime has via reflection, and unwraps the {@code Optional} when present.
 * Default-on-miss matches legacy behavior: empty string, zero, false.
 */
public final class NbtCompatLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-NbtCompat");

    /** Resolved methods cached after first lookup. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Method> METHOD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private NbtCompatLookup() {}

    // CompoundTag getters

    public static String compoundGetString(Object compound, String key) {
        Object v = unwrap(invokeGetter(compound, "getString", String.class, key));
        return v != null ? v.toString() : "";
    }

    public static int compoundGetInt(Object compound, String key) {
        Object v = unwrap(invokeGetter(compound, "getInt", int.class, key));
        return v instanceof Number n ? n.intValue() : 0;
    }

    public static long compoundGetLong(Object compound, String key) {
        Object v = unwrap(invokeGetter(compound, "getLong", long.class, key));
        return v instanceof Number n ? n.longValue() : 0L;
    }

    public static float compoundGetFloat(Object compound, String key) {
        Object v = unwrap(invokeGetter(compound, "getFloat", float.class, key));
        return v instanceof Number n ? n.floatValue() : 0f;
    }

    public static double compoundGetDouble(Object compound, String key) {
        Object v = unwrap(invokeGetter(compound, "getDouble", double.class, key));
        return v instanceof Number n ? n.doubleValue() : 0d;
    }

    public static boolean compoundGetBoolean(Object compound, String key) {
        Object v = unwrap(invokeGetter(compound, "getBoolean", boolean.class, key));
        return v instanceof Boolean b && b;
    }

    // ListTag getters, keyed by int index

    public static String listGetString(Object list, int index) {
        Object v = unwrap(invokeIndexedGetter(list, "getString", String.class, index));
        return v != null ? v.toString() : "";
    }

    public static int listGetInt(Object list, int index) {
        Object v = unwrap(invokeIndexedGetter(list, "getInt", int.class, index));
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** Unwrap an {@code Optional} result, or pass through a direct one. */
    private static Object unwrap(Object result) {
        if (result == null) return null;
        if (result instanceof Optional<?> opt) {
            return opt.isPresent() ? opt.get() : null;
        }
        return result;
    }

    /** Try the modern Optional-returning signature first, then the legacy direct one. */
    private static Object invokeGetter(Object receiver, String methodName,
                                       Class<?> legacyReturn, String key) {
        if (receiver == null) return null;
        Class<?> cls = receiver.getClass();

        Method modern = resolveCachedMethod(cls, methodName, String.class, Optional.class);
        if (modern != null) {
            try {
                return modern.invoke(receiver, key);
            } catch (Throwable t) {
                LOGGER.trace("modern {}.{} failed: {}", cls.getName(), methodName, t.getMessage());
            }
        }

        Method legacy = resolveCachedMethod(cls, methodName, String.class, legacyReturn);
        if (legacy != null) {
            try {
                return legacy.invoke(receiver, key);
            } catch (Throwable t) {
                LOGGER.trace("legacy {}.{} failed: {}", cls.getName(), methodName, t.getMessage());
            }
        }
        return null;
    }

    private static Object invokeIndexedGetter(Object receiver, String methodName,
                                              Class<?> legacyReturn, int index) {
        if (receiver == null) return null;
        Class<?> cls = receiver.getClass();

        Method modern = resolveCachedMethod(cls, methodName, int.class, Optional.class);
        if (modern != null) {
            try {
                return modern.invoke(receiver, index);
            } catch (Throwable t) {
                LOGGER.trace("modern {}.{}(int) failed: {}", cls.getName(), methodName, t.getMessage());
            }
        }

        Method legacy = resolveCachedMethod(cls, methodName, int.class, legacyReturn);
        if (legacy != null) {
            try {
                return legacy.invoke(receiver, index);
            } catch (Throwable t) {
                LOGGER.trace("legacy {}.{}(int) failed: {}", cls.getName(), methodName, t.getMessage());
            }
        }
        return null;
    }

    /**
     * Look up and cache a method by (class, name, paramType, returnType), walking
     * the class hierarchy. Matching on return type picks between the Optional and
     * direct overloads deterministically.
     */
    private static Method resolveCachedMethod(Class<?> cls, String name,
                                              Class<?> paramType, Class<?> returnType) {
        String cacheKey = cls.getName() + "#" + name + "(" + paramType.getName() + ")"
                + returnType.getName();
        Method cached = METHOD_CACHE.get(cacheKey);
        if (cached != null) return cached;

        Class<?> walk = cls;
        while (walk != null) {
            for (Method m : walk.getDeclaredMethods()) {
                if (!name.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] != paramType) continue;
                if (m.getReturnType() != returnType) continue;
                m.setAccessible(true);
                METHOD_CACHE.put(cacheKey, m);
                return m;
            }
            walk = walk.getSuperclass();
        }
        return null;
    }
}
