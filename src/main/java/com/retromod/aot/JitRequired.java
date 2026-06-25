/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import java.lang.annotation.*;

/**
 * Marks a method that HybridCompiler couldn't fully analyze statically (reflection,
 * unknown invokedynamic bootstraps, obfuscated code) and that JitRuntime must
 * transform at class load time instead.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface JitRequired {

    /** When false, only the instruction indices in {@link #regions()} need JIT. */
    boolean fullMethod() default true;

    /** Instruction indices needing JIT; only read when {@link #fullMethod()} is false. */
    int[] regions() default {};

    /** Why JIT is required, for logging. */
    String reason() default "";
}
