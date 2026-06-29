/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.polyfill.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * Polyfill for the removed
 * {@code net.minecraft.world.level.block.state.properties.DirectionProperty}.
 *
 * <p>{@code DirectionProperty} was a subclass of {@code EnumProperty<Direction>}
 * with its own {@code create(...)} factories. MC 26.1 removed it; {@code EnumProperty}
 * is now {@code final} and called via {@code EnumProperty.create(name, Direction.class, ...)}.
 *
 * <p>The {@code BlockPropertyPolyfill} shim redirects the type
 * {@code DirectionProperty} to {@code EnumProperty} (#24). The four old
 * {@code DirectionProperty.create(...)} factories lack the {@code Class<Direction>}
 * argument the modern {@code EnumProperty.create} needs, so they route through the
 * bridges below, each supplying {@code Direction.class} and calling the factory reflectively.
 *
 * <p>The bridges return {@code Object}; the transformer emits a {@code CHECKCAST} to
 * {@code EnumProperty} at the call site, so the value fits back into the mod's
 * {@code EnumProperty}-typed slot.
 *
 * <p>MC classes are resolved reflectively (MC isn't on Retromod's compile classpath)
 * via the multi-classloader probe from {@link RegistryRefLookup}, anchored on the
 * caller's classloader.
 */
public final class DirectionPropertyLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-DirectionProperty");

    private static final String ENUM_PROPERTY =
            "net.minecraft.world.level.block.state.properties.EnumProperty";
    private static final String DIRECTION = "net.minecraft.core.Direction";

    private static volatile boolean resolved;
    private static volatile Class<?> directionClass;
    private static volatile Method mCreate2;     // create(String, Class)
    private static volatile Method mCreate3pred; // create(String, Class, Predicate)
    private static volatile Method mCreate3arr;  // create(String, Class, T...) -> erased Enum[]
    private static volatile Method mCreate3list; // create(String, Class, List)

    private static volatile ClassLoader anchorClassLoader;

    private DirectionPropertyLookup() {}

    // One bridge per old DirectionProperty.create(...) overload; descriptors match the
    // post-remap call shapes (no Class arg), so they don't collide with EnumProperty.create.

    /** {@code DirectionProperty.create(String)}, all directions. */
    public static Object create(String name) {
        ensureResolved();
        return invoke(mCreate2, name, directionClass);
    }

    /** {@code DirectionProperty.create(String, Predicate<Direction>)}. */
    public static Object create(String name, Predicate<?> filter) {
        ensureResolved();
        if (mCreate3pred != null) return invoke(mCreate3pred, name, directionClass, filter);
        // no filtered overload at runtime, fall back to all directions
        return invoke(mCreate2, name, directionClass);
    }

    /** {@code DirectionProperty.create(String, Direction...)}. */
    public static Object create(String name, Object[] values) {
        ensureResolved();
        if (mCreate3arr != null) return invoke(mCreate3arr, name, directionClass, (Object) values);
        if (mCreate3list != null) {
            return invoke(mCreate3list, name, directionClass, new ArrayList<>(Arrays.asList(values)));
        }
        return invoke(mCreate2, name, directionClass);
    }

    /** {@code DirectionProperty.create(String, Collection<Direction>)}. */
    public static Object create(String name, Collection<?> values) {
        ensureResolved();
        if (mCreate3list != null) return invoke(mCreate3list, name, directionClass, new ArrayList<>(values));
        if (mCreate3arr != null) return invoke(mCreate3arr, name, directionClass, (Object) values.toArray());
        return invoke(mCreate2, name, directionClass);
    }

    private static Object invoke(Method m, Object... args) {
        if (m == null) {
            LOGGER.warn("EnumProperty.create overload unavailable - cannot build DirectionProperty for {}",
                    args.length > 0 ? args[0] : "?");
            return null;
        }
        try {
            return m.invoke(null, args);
        } catch (Throwable t) {
            LOGGER.warn("Failed to bridge DirectionProperty.create via {}: {}",
                    m, t.getMessage());
            return null;
        }
    }

    private static void ensureResolved() {
        if (resolved) return;
        synchronized (DirectionPropertyLookup.class) {
            if (resolved) return;
            try {
                Class<?> enumProperty = loadMcClass(ENUM_PROPERTY);
                directionClass = loadMcClass(DIRECTION);
                if (enumProperty == null || directionClass == null) {
                    LOGGER.warn("Could not resolve EnumProperty/Direction - DirectionProperty bridges disabled");
                    resolved = true; // don't re-resolve every call
                    return;
                }
                for (Method m : enumProperty.getMethods()) {
                    if (!"create".equals(m.getName()) || !Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 2 && p[0] == String.class && p[1] == Class.class) {
                        mCreate2 = m;
                    } else if (p.length == 3 && p[0] == String.class && p[1] == Class.class) {
                        Class<?> third = p[2];
                        if (third.isArray()) mCreate3arr = m;
                        else if (Predicate.class.isAssignableFrom(third)) mCreate3pred = m;
                        else if (Collection.class.isAssignableFrom(third)) mCreate3list = m;
                    }
                }
                if (mCreate2 == null) {
                    LOGGER.warn("EnumProperty.create(String, Class) not found - DirectionProperty bridges degraded");
                }
            } catch (Throwable t) {
                LOGGER.warn("DirectionProperty bridge resolution failed: {}", t.getMessage());
            } finally {
                resolved = true;
            }
        }
    }

    /**
     * Resolve an MC class across the loaders that might see it. The caller's loader
     * (walked off the stack) is the anchor on every mod loader; the rest are fallbacks.
     * Mirrors {@link RegistryRefLookup}'s probe.
     */
    private static Class<?> loadMcClass(String name) {
        ClassLoader anchor = anchorClassLoader;
        if (anchor != null) {
            Class<?> c = tryLoad(name, anchor);
            if (c != null) return c;
        }
        Class<?> stackHit = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(cls -> {
                            String n = cls.getName();
                            return !n.startsWith("com.retromod.")
                                    && !n.startsWith("java.")
                                    && !n.startsWith("jdk.")
                                    && !n.startsWith("sun.")
                                    && cls.getClassLoader() != null;
                        })
                        .map(cls -> tryLoad(name, cls.getClassLoader()))
                        .filter(c -> c != null)
                        .findFirst()
                        .orElse(null));
        if (stackHit != null) {
            anchorClassLoader = stackHit.getClassLoader();
            return stackHit;
        }
        for (ClassLoader cl : new ClassLoader[]{
                Thread.currentThread().getContextClassLoader(),
                DirectionPropertyLookup.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()}) {
            Class<?> c = tryLoad(name, cl);
            if (c != null) {
                anchorClassLoader = cl;
                return c;
            }
        }
        return null;
    }

    private static Class<?> tryLoad(String name, ClassLoader cl) {
        if (cl == null) return null;
        try {
            return Class.forName(name, true, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
