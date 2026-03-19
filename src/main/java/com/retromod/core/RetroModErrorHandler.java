/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deduplicated error handler for non-fatal mod errors.
 *
 * When RetroMod wraps entrypoints and callbacks in try-catch, the catch block
 * calls this handler. It deduplicates errors so that recurring failures
 * (e.g., NPE in a tick callback 20x/sec) only log once instead of spamming
 * STDERR with thousands of stack traces that freeze the game.
 */
public class RetroModErrorHandler {

    // Track which error messages we've already logged (capped at 500 to prevent memory leak)
    private static final int MAX_SEEN_ERRORS = 500;
    private static final Set<String> seenErrors = ConcurrentHashMap.newKeySet();

    /**
     * Handle a non-fatal error from a wrapped entrypoint or callback.
     * Only logs the full stack trace for the first occurrence of each unique error.
     * Subsequent identical errors are silently suppressed.
     *
     * @param className the class where the error occurred
     * @param t the throwable that was caught
     */
    public static void handleNonFatal(String className, Throwable t) {
        // Create a dedup key from class + exception type + message
        String key = className + "|" + t.getClass().getName() + "|" + t.getMessage();

        // Cap the set to prevent unbounded memory growth
        if (seenErrors.size() >= MAX_SEEN_ERRORS) {
            return; // After 500 unique errors, stop logging entirely
        }

        if (seenErrors.add(key)) {
            // First time seeing this error — log it fully
            System.err.println("[RetroMod] Non-fatal: entrypoint failed in " + className + ": " + t);
            t.printStackTrace();
        }
        // Subsequent occurrences are silently suppressed
    }

    /**
     * Clear the dedup cache (e.g., on reload).
     */
    public static void reset() {
        seenErrors.clear();
    }
}
