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
 * <p>Historically {@code DirectionProperty} was a thin subclass of
 * {@code EnumProperty<Direction>} with its own {@code create(...)} factory
 * methods. MC 26.1 removed the class entirely; {@code EnumProperty} is now
 * {@code final} and is used directly via
 * {@code EnumProperty.create(name, Direction.class, ...)}.
 *
 * <p>The {@code BlockPropertyPolyfill} shim redirects the <em>type</em>
 * {@code DirectionProperty} → {@code EnumProperty} (that alone fixes the
 * {@code NoClassDefFoundError} a mod hits when its bytecode references the
 * removed type — see issue #24, where it surfaced from {@code Blocks.<clinit>}).
 * It then routes the four old {@code DirectionProperty.create(...)} factories
 * — which lack the {@code Class<Direction>} argument the modern
 * {@code EnumProperty.create} requires — through the bridges below. Each bridge
 * supplies {@code Direction.class} and calls the real factory reflectively.
 *
 * <p>These return {@code Object}; the transformer emits a {@code CHECKCAST} to
 * {@code EnumProperty} at the call site (it always does when a method redirect's
 * return type differs), so the resulting value slots back into the mod's
 * {@code EnumProperty}-typed field/stack correctly.
 *
 * <p>MC classes are resolved reflectively (MC isn't on Retromod's compile
 * classpath) via a multi-classloader probe modeled on {@link RegistryRefLookup}
 * — the reliable anchor is the <em>caller's</em> classloader, which can see MC
 * on every loader.
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

    // =====================================================================
    // BRIDGES — one per historical DirectionProperty.create(...) overload.
    // Descriptors are chosen to match the post-class-remap call shapes
    // (EnumProperty.create with NO Class arg), which cannot collide with the
    // real EnumProperty.create(String, Class, ...) overloads.
    // =====================================================================

    /** {@code DirectionProperty.create(String)} → all directions. */
    public static Object create(String name) {
        ensureResolved();
        return invoke(mCreate2, name, directionClass);
    }

    /** {@code DirectionProperty.create(String, Predicate<Direction>)}. */
    public static Object create(String name, Predicate<?> filter) {
        ensureResolved();
        if (mCreate3pred != null) return invoke(mCreate3pred, name, directionClass, filter);
        // No filtered overload at runtime — fall back to all directions rather than crash.
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

    // =====================================================================
    // INTERNALS
    // =====================================================================

    private static Object invoke(Method m, Object... args) {
        if (m == null) {
            LOGGER.warn("EnumProperty.create overload unavailable — cannot build DirectionProperty for {}",
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
                    LOGGER.warn("Could not resolve EnumProperty/Direction — DirectionProperty bridges disabled");
                    resolved = true; // don't spin re-resolving every call
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
                    LOGGER.warn("EnumProperty.create(String, Class) not found — DirectionProperty bridges degraded");
                }
            } catch (Throwable t) {
                LOGGER.warn("DirectionProperty bridge resolution failed: {}", t.getMessage());
            } finally {
                resolved = true;
            }
        }
    }

    /**
     * Resolve an MC class across the loaders that might see it. The caller's
     * loader (walked off the stack) is the reliable anchor on every mod loader;
     * the others are fallbacks. Mirrors {@link RegistryRefLookup}'s probe.
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
