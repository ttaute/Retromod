/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod;

import java.util.function.Supplier;

/**
 * Concrete {@link Test} for cases where the body is short enough to inline as
 * a lambda. Most tests in the suite use this — only tests that need their own
 * supporting classes (e.g. a {@code Screen} subclass) are written as
 * standalone files.
 *
 * <p>Use {@link #notNull(String, Supplier)} for the very common "static field
 * exists and isn't null" pattern, and {@link #equalCheck(String, Supplier, Object)}
 * for "this value is what I expect".
 */
public record SimpleTest(String description, Supplier<TestResult> body) implements Test {

    @Override
    public TestResult run() {
        return body.get();
    }

    /**
     * The most common test shape: "this expression resolves to something
     * non-null after Retromod's transformer runs." Useful for the long lists
     * of static field accesses ({@code Items.DIAMOND}, {@code Blocks.STONE},
     * etc.) where the test body is otherwise four lines of boilerplate.
     */
    public static SimpleTest notNull(String description, Supplier<Object> field) {
        return new SimpleTest(description, () ->
            field.get() != null
                ? TestResult.success()
                : TestResult.fail("returned null"));
    }

    /**
     * Tests that {@code actual.get()} equals {@code expected}. Uses
     * {@link java.util.Objects#equals(Object, Object)} so nulls compare safely.
     */
    public static SimpleTest equalCheck(String description, Supplier<Object> actual, Object expected) {
        return new SimpleTest(description, () -> {
            Object a = actual.get();
            return java.util.Objects.equals(a, expected)
                ? TestResult.success()
                : TestResult.fail("got " + a + ", expected " + expected);
        });
    }
}
