/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod;

/**
 * One observable behavior of the transformed mod. Implementations should be
 * tiny — a single API call wrapped in a try/catch is the ideal shape. The
 * {@link TestRunner} catches Throwable for you, so a thrown exception
 * automatically becomes a fail with the exception's message as the reason.
 */
public interface Test {

    /** Short label that goes in the log line, e.g. "Component.literal". */
    String description();

    /** Return success or fail. Throwing also counts as fail. */
    TestResult run();
}
