/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill for net.minecraft.util.LazyLoadedValue, removed in MC 26.1.
 * This is a simple lazy wrapper: takes a Supplier in its constructor and
 * caches the result on the first get() call. Thread-safe via double-checked
 * locking.
 *
 * Mods referencing net/minecraft/util/LazyLoadedValue will be redirected
 * to this class via a class redirect registered in MinecraftVanillaPolyfill.
 */
package com.retromod.polyfill.minecraft.embedded;

import java.util.function.Supplier;

/**
 * Drop-in replacement for the removed {@code net.minecraft.util.LazyLoadedValue}.
 *
 * @param <T> the type of the lazily computed value
 */
public class LazyLoadedValue<T> {

    private final Supplier<T> supplier;
    private volatile T value;
    private volatile boolean computed;

    public LazyLoadedValue(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T get() {
        if (!computed) {
            synchronized (this) {
                if (!computed) {
                    value = supplier.get();
                    computed = true;
                }
            }
        }
        return value;
    }
}
