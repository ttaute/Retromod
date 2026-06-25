/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deduplicating handler for non-fatal mod errors. Recurring failures (an NPE in a tick
 * callback firing 20x/sec) would otherwise flood stderr with stack traces and freeze the game,
 * so each distinct error is logged only once.
 */
public class RetromodErrorHandler {

    private static final int MAX_SEEN_ERRORS = 500;
    private static final Set<String> seenErrors = ConcurrentHashMap.newKeySet();

    /**
     * Log a caught non-fatal error, but only the first time each distinct error is seen.
     *
     * @param className the class where the error occurred
     * @param t the throwable that was caught
     */
    public static void handleNonFatal(String className, Throwable t) {
        String key = className + "|" + t.getClass().getName() + "|" + t.getMessage();

        // stop logging once the set is full, rather than let it grow without bound
        if (seenErrors.size() >= MAX_SEEN_ERRORS) {
            return;
        }

        if (seenErrors.add(key)) {
            System.err.println("[Retromod] Non-fatal: entrypoint failed in " + className + ": " + t);
            t.printStackTrace();
        }
    }

    public static void reset() {
        seenErrors.clear();
    }
}
