package com.retromod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Covers the {@code getCommonSuperClass} fallback that fixes the forge-config-api-port
 * {@code ConfigTracker} {@code VerifyError} (#94 follow-up). When ASM can't resolve a
 * type during {@code COMPUTE_FRAMES} (e.g. a Fabric Jar-in-Jar exception), the fallback
 * must NOT collapse an exception merge to {@code Object} - that's what corrupts the
 * StackMapTable into a verifier failure. It must return {@code Throwable} whenever
 * either operand is one, and only fall back to {@code Object} otherwise.
 */
class CommonSuperFallbackTest {

    @Test
    void throwableMergedWithUnresolvableTypeBecomesThrowableNotObject() {
        // The exact ConfigTracker case: a JDK exception merged with a JiJ-bundled
        // exception the transformer can't resolve.
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
        // Neither operand is (or can be shown to be) a Throwable → Object, unchanged.
        assertEquals("java/lang/Object",
                RetromodTransformer.commonSuperFallback("java/lang/String", "com/example/Unknown"));
        assertEquals("java/lang/Object",
                RetromodTransformer.commonSuperFallback("com/example/UnknownA", "com/example/UnknownB"));
    }
}
