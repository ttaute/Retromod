/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One pattern-detection hit: pattern X matched class Y with confidence Z.
 *
 * <ul>
 *   <li>{@code patternName}: identifier of the {@link ClassPattern} that fired,
 *       matching {@link ClassPattern#name()}.</li>
 *   <li>{@code className}: JVM internal name of the matched mod class.</li>
 *   <li>{@code confidence}: 0.0 to 1.0. 1.0 is an unambiguous signal (an
 *       annotation); lower values come from heuristics (method-name patterns,
 *       structural shape).</li>
 *   <li>{@code metadata}: pattern-specific extra info as an ordered key-value
 *       map, so the gap report can print it without knowing the pattern.</li>
 * </ul>
 *
 * <p>Metadata is a generic map because each pattern extracts different info and
 * it is only ever rendered, never introspected by name.</p>
 */
public record PatternMatch(
        String patternName,
        String className,
        double confidence,
        Map<String, Object> metadata
) {

    public PatternMatch {
        Objects.requireNonNull(patternName, "patternName");
        Objects.requireNonNull(className, "className");
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be in [0,1], got " + confidence);
        }
        metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /** One-line description for gap-report output, like {@code "com/example/Foo (0.90) - handlerCount=3"}. */
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append(" (").append(String.format("%.2f", confidence)).append(")");
        if (!metadata.isEmpty()) {
            sb.append(" - ");
            boolean first = true;
            for (Map.Entry<String, Object> e : metadata.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        return sb.toString();
    }
}
