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
 * <p>In older MC, {@code CompoundTag.getString(String key)} returned
 * {@code String} directly (defaulting to empty string on miss). In MC
 * 1.21.5+ that signature is gone — the same method now returns
 * {@code Optional<String>}, and to get the underlying primitive you call
 * {@code .orElse("")}. {@code getInt}, {@code getDouble}, etc. follow the
 * same pattern. {@code ListTag.getString(int)} got the same treatment.
 *
 * <p>Mods compiled against the old shape do
 * {@code INVOKEVIRTUAL CompoundTag.getString(String)String} and crash
 * with {@code NoSuchMethodError} at runtime on the new MC.
 *
 * <p>Fix: route those calls through the static helpers below. Each helper
 * takes the receiver as its first argument (since the call shape becomes
 * {@code INVOKESTATIC} via {@code devirtualize=true}), looks up the
 * runtime method via reflection on whichever signature is actually
 * present, and unwraps the {@code Optional} when needed. Default-on-miss
 * matches the legacy behavior — empty string, zero, false.
 */
public final class NbtCompatLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-NbtCompat");

    /** Resolved methods cached after first lookup, keyed by reflective method ID. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Method> METHOD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private NbtCompatLookup() {}

    // =====================================================================
    // CompoundTag — replaces the legacy direct-return getters with helpers
    // that adapt to the new Optional-returning signatures.
    // =====================================================================

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

    // =====================================================================
    // ListTag — same pattern, but the key is an int index.
    // =====================================================================

    public static String listGetString(Object list, int index) {
        Object v = unwrap(invokeIndexedGetter(list, "getString", String.class, index));
        return v != null ? v.toString() : "";
    }

    public static int listGetInt(Object list, int index) {
        Object v = unwrap(invokeIndexedGetter(list, "getInt", int.class, index));
        return v instanceof Number n ? n.intValue() : 0;
    }

    /**
     * If the runtime returned an {@code Optional}, unwrap it; otherwise pass
     * through. Matches both the modern Optional-returning signature and the
     * legacy direct-returning one in a single helper.
     */
    private static Object unwrap(Object result) {
        if (result == null) return null;
        if (result instanceof Optional<?> opt) {
            return opt.isPresent() ? opt.get() : null;
        }
        return result;
    }

    // =====================================================================
    // INTERNALS
    // =====================================================================

    /**
     * Try the modern Optional-returning signature first, then the legacy
     * direct-returning one. Whichever resolves at runtime wins.
     */
    private static Object invokeGetter(Object receiver, String methodName,
                                       Class<?> legacyReturn, String key) {
        if (receiver == null) return null;
        Class<?> cls = receiver.getClass();

        // Modern: returns Optional<X>
        Method modern = resolveCachedMethod(cls, methodName, String.class, Optional.class);
        if (modern != null) {
            try {
                return modern.invoke(receiver, key);
            } catch (Throwable t) {
                LOGGER.trace("modern {}.{} failed: {}", cls.getName(), methodName, t.getMessage());
            }
        }

        // Legacy: returns the primitive/String directly
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
     * Look up a method by (class, name, paramType, returnType). Cached.
     * Walks the class hierarchy and only matches methods whose return type
     * is exactly {@code returnType} — that lets us pick between the
     * modern Optional-returning and legacy direct-returning overloads
     * deterministically.
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
