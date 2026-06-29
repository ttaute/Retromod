/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill for DataFixerUpper API changes in DFU 9.x (MC 26.1): DataResult went from a
 * class to an interface and some methods were removed/renamed. DFU classes aren't on the
 * compile classpath, so this works through reflection.
 */
package com.retromod.polyfill.minecraft;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Polyfill methods for DataResult API changes.
 *
 * Old: DataResult.get() returns Either of (R, DataResult.PartialResult of R).
 * New: result() returns Optional of R, error() returns Optional of Error of R.
 * Wired in via a devirtualized redirect: DataResult.get() to DataResultPolyfill.get(DataResult).
 */
public class DataResultPolyfill {

    private static Method eitherLeftMethod;
    private static Method eitherRightMethod;

    static {
        try {
            Class<?> eitherClass = Class.forName("com.mojang.datafixers.util.Either");
            eitherLeftMethod = eitherClass.getMethod("left", Object.class);
            eitherRightMethod = eitherClass.getMethod("right", Object.class);
        } catch (Exception e) {
            // fails later only if get() is ever called
        }
    }

    /**
     * Polyfill for the removed DataResult.get(): Left holds the success value, Right an error wrapper.
     */
    @SuppressWarnings("unchecked")
    public static Object get(Object dataResult) {
        try {
            Method resultMethod = dataResult.getClass().getMethod("result");
            Optional<?> result = (Optional<?>) resultMethod.invoke(dataResult);

            if (result.isPresent()) {
                return eitherLeftMethod.invoke(null, result.get());
            }

            Method errorMethod = dataResult.getClass().getMethod("error");
            Optional<?> error = (Optional<?>) errorMethod.invoke(dataResult);

            if (error.isPresent()) {
                return eitherRightMethod.invoke(null, error.get());
            }

            // neither present: fall back to left(null)
            return eitherLeftMethod.invoke(null, (Object) null);
        } catch (Exception e) {
            throw new RuntimeException("DataResult.get() polyfill failed", e);
        }
    }
}
