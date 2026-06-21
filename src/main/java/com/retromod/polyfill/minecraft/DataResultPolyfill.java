/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill for DataFixerUpper API changes in DFU 9.x (used by MC 26.1).
 * DataResult changed from a class to an interface, and several methods were removed/renamed.
 *
 * NOTE: DFU classes are not available at compile time, so we use reflection.
 */
package com.retromod.polyfill.minecraft;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Polyfill methods for DataResult API changes.
 *
 * Old: DataResult.get() → Either<R, DataResult.PartialResult<R>>
 * New: DataResult.result() → Optional<R>, DataResult.error() → Optional<Error<R>>
 *
 * Called via devirtualized method redirect: DataResult.get() → DataResultPolyfill.get(DataResult)
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
            // Will fail at runtime if method is actually called
        }
    }

    /**
     * Polyfill for the removed DataResult.get() method.
     * Returns an Either where Left is the successful result and Right is an error-like wrapper.
     */
    @SuppressWarnings("unchecked")
    public static Object get(Object dataResult) {
        try {
            // Call dataResult.result() to get Optional<R>
            Method resultMethod = dataResult.getClass().getMethod("result");
            Optional<?> result = (Optional<?>) resultMethod.invoke(dataResult);

            if (result.isPresent()) {
                return eitherLeftMethod.invoke(null, result.get());
            }

            // Call dataResult.error() to get Optional<Error<R>>
            Method errorMethod = dataResult.getClass().getMethod("error");
            Optional<?> error = (Optional<?>) errorMethod.invoke(dataResult);

            if (error.isPresent()) {
                return eitherRightMethod.invoke(null, error.get());
            }

            // Shouldn't happen - return left(null)
            return eitherLeftMethod.invoke(null, (Object) null);
        } catch (Exception e) {
            throw new RuntimeException("DataResult.get() polyfill failed", e);
        }
    }
}
