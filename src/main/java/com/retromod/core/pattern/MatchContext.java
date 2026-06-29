/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern;

import com.retromod.core.verify.LoaderApiRenames;
import com.retromod.core.verify.McSymbolIndex;

import java.util.Collections;
import java.util.Set;

/**
 * Read-only context passed to {@link ClassPattern} implementations.
 *
 * <ul>
 *   <li>{@code modOwnClasses}: classes defined by the mod, for telling mod code
 *       apart from MC or JDK code. Never null.</li>
 *   <li>{@code loaderRenames}: loader-API rename table, used to recognize both
 *       old and renamed Fabric/NeoForge/Forge forms. Never null (empty when not
 *       configured).</li>
 *   <li>{@code mcIndex}: symbol index for the target MC JAR, for checking that a
 *       referenced class exists in MC. May be unavailable (see
 *       {@link McSymbolIndex#isAvailable()}), so patterns must handle that.</li>
 * </ul>
 *
 * <p>No defaults: callers construct one explicitly so an empty modOwnClasses
 * can't slip through unnoticed.</p>
 */
public record MatchContext(
        Set<String> modOwnClasses,
        LoaderApiRenames loaderRenames,
        McSymbolIndex mcIndex
) {

    public MatchContext {
        modOwnClasses = modOwnClasses == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(modOwnClasses);
    }

    /** Context with no mod classes and no loader/MC data, for unit tests. */
    public static MatchContext empty() {
        return new MatchContext(
                Collections.emptySet(),
                LoaderApiRenames.forTesting(null, null, null),
                new McSymbolIndex() {
                    @Override public boolean isAvailable() { return false; }
                    @Override public boolean hasClass(String n) { return false; }
                    @Override public boolean hasMethod(String o, String n, String d) { return false; }
                    @Override public boolean hasField(String o, String n, String d) { return false; }
                    @Override public java.util.List<String> suggestClassAlternatives(String n, int max) {
                        return java.util.List.of();
                    }
                    @Override public java.util.List<MemberSignature> suggestMethodAlternatives(
                            String o, String n, String d, int max) {
                        return java.util.List.of();
                    }
                    @Override public String targetMcVersion() { return "unknown"; }
                });
    }
}
