package com.retromod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Covers the {@code getCommonSuperClass} fallback that fixes the forge-config-api-port
 * {@code ConfigTracker} {@code VerifyError} (#94 follow-up). When ASM can't resolve a type
 * during {@code COMPUTE_FRAMES}, the fallback returns {@code Throwable} whenever either
 * operand is one, and falls back to {@code Object} otherwise. Collapsing an exception merge
 * to {@code Object} corrupts the StackMapTable into a verifier failure.
 */
class CommonSuperFallbackTest {

    @Test
    void throwableMergedWithUnresolvableTypeBecomesThrowableNotObject() {
        // ConfigTracker case: a JDK exception merged with a JiJ-bundled exception
        // the transformer can't resolve.
        assertEquals("java/lang/Throwable",
                RetromodTransformer.commonSuperFallback("java/io/IOException", "com/example/JijParsingException"));
        // order-independent
        assertEquals("java/lang/Throwable",
                RetromodTransformer.commonSuperFallback("com/example/JijParsingException", "java/io/IOException"));
    }

    @Test
    void twoThrowableSubtypesMergeToThrowable() {
        assertEquals("java/lang/Throwable",
                RetromodTransformer.commonSuperFallback("java/lang/RuntimeException", "java/lang/Error"));
    }

    @Test
    void throwableItselfCountsAsThrowable() {
        assertEquals("java/lang/Throwable",
                RetromodTransformer.commonSuperFallback("java/lang/Throwable", "com/example/Unknown"));
    }

    @Test
    void nonThrowableMergesStayObject() {
        // Neither operand is (or can be shown to be) a Throwable, so Object.
        assertEquals("java/lang/Object",
                RetromodTransformer.commonSuperFallback("java/lang/String", "com/example/Unknown"));
        assertEquals("java/lang/Object",
                RetromodTransformer.commonSuperFallback("com/example/UnknownA", "com/example/UnknownB"));
    }
}
