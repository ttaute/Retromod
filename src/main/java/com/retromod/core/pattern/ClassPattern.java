/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern;

import org.objectweb.asm.tree.ClassNode;

/**
 * Detects one structural "shape" a mod class might have: a Forge event listener,
 * a {@code DeferredRegister} holder, a BlockEntity-like class, and so on. Recognizing
 * concepts structurally (rather than by exact class names) gives more actionable gap
 * reports and a hook for future targeted fixes. v1 is detection-only.
 *
 * <p>Implementations inspect the given {@link ClassNode} and return a
 * {@link PatternMatch} on a match, or {@code null} otherwise. They must be:
 * <ul>
 *   <li>read-only (never mutate the {@code ClassNode}: patterns run in any order),</li>
 *   <li>fast (run on every mod class, so aim for linear-or-better),</li>
 *   <li>scope-aware (return null for classes not in {@link MatchContext#modOwnClasses()},
 *       so MC and JDK classes don't match), and</li>
 *   <li>deterministic (same class plus context yields the same result, keeping gap
 *       reports diff-stable across CI runs).</li>
 * </ul>
 *
 * <p>The tree API {@code ClassNode} (not the streaming visitor) is used so patterns
 * get random access to methods, fields, and annotations as simple loops.
 */
public interface ClassPattern {

    /**
     * Stable machine-readable identifier for this pattern, appearing in
     * {@link PatternMatch#patternName()} and the gap report. Camel-case, no spaces
     * (for example {@code "ForgeEventListener"}).
     */
    String name();

    /** One-sentence description for the gap report header line. */
    String description();

    /**
     * Tests whether {@code cls} matches this pattern in the given context.
     *
     * @return a {@link PatternMatch} on a match, or {@code null} otherwise. A non-null
     *         result with {@code confidence=0.0} means "matched but low certainty" and
     *         should still appear in the report for review; a real non-match returns
     *         {@code null}.
     */
    PatternMatch match(ClassNode cls, MatchContext ctx);
}
