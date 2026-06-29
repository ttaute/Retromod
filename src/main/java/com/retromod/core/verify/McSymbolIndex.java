/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import java.util.List;

/**
 * Read-only check for "does this class/method/field exist in the target Minecraft version?"
 *
 * <p>Shared by {@link ReferenceVerifier} (scans transformed bytecode and reports references
 * the index says don't exist) and {@link ReflectionStringRemapper} (checks whether a rewritten
 * reflection target would resolve before emitting it).</p>
 *
 * <p>An interface so tests can supply a small stub index instead of a full ~100MB MC JAR, and
 * so a future runtime-backed implementation can check the live classloader instead of a JAR.</p>
 *
 * <p>A member "exists" if it is declared on the target class or inherited through a superclass
 * or interface, so {@code hasMethod("net/minecraft/world/level/Level", "getBlockState", "(...)")}
 * is {@code true} even when {@code getBlockState} comes from a {@code LevelReader} interface that
 * {@code Level} extends. "Doesn't exist" means "not found in the index": an unpopulated index
 * (no MC JAR) returns {@code false} for every lookup, so callers that need to tell "not indexed"
 * from "missing" check {@link #isAvailable()} first.</p>
 *
 * <p>Covers everything under {@code net/minecraft/**} and {@code com/mojang/**} in the target MC
 * JAR. Loader-API renames (Fabric, NeoForge, Forge) live in {@link LoaderApiRenames}, a curated
 * table, not here.</p>
 */
public interface McSymbolIndex {

    /**
     * @return {@code true} if the index holds MC JAR data. When {@code false} every
     *         {@code hasXxx} call returns {@code false}, which callers treat as "unknown".
     */
    boolean isAvailable();

    /**
     * Exact class-existence check.
     *
     * @param internalName JVM internal name, slash-separated
     *                     (e.g., {@code "net/minecraft/core/BlockPos"})
     * @return {@code true} iff the class is present in the indexed MC JAR
     */
    boolean hasClass(String internalName);

    /**
     * Exact method-existence check, including inherited methods.
     *
     * @param owner      JVM internal class name
     * @param name       method name
     * @param descriptor JVM method descriptor (e.g., {@code "(IIF)V"})
     * @return {@code true} iff a matching method exists on {@code owner}
     *         or any ancestor (superclass or interface)
     */
    boolean hasMethod(String owner, String name, String descriptor);

    /**
     * Exact field-existence check, including inherited fields.
     *
     * @param owner      JVM internal class name
     * @param name       field name
     * @param descriptor JVM field descriptor (e.g., {@code "Ljava/lang/String;"})
     * @return {@code true} iff a matching field exists on {@code owner}
     *         or any ancestor
     */
    boolean hasField(String owner, String name, String descriptor);

    /**
     * Name-only method probe (hierarchy-aware), ignoring the descriptor. Lets the gap report
     * tell a signature change ({@code BAD_SIGNATURE}: the name still exists on the owner under a
     * different descriptor) from a rename/removal ({@code MISSING_METHOD}). Across the 1.17 model
     * rebuild and similar refactors, member names survive while descriptors change, so a shim keyed
     * on the old descriptor never fires (CLAUDE.md pitfall #17).
     *
     * <p>Defaults to {@code false} so an index that can't answer collapses back to
     * {@code MISSING_METHOD}.</p>
     */
    default boolean hasMethodName(String owner, String name) { return false; }

    /** Name-only field probe (hierarchy-aware), ignoring the descriptor. See {@link #hasMethodName}. */
    default boolean hasFieldName(String owner, String name) { return false; }

    /**
     * Suggest up to {@code maxResults} class names that exist in the index and resemble the
     * missing one, for the gap report's "did you mean" hints. A substring match on the simple
     * name (after the last slash) usually suffices: {@code net/minecraft/util/math/BlockPos}
     * matches {@code net/minecraft/core/BlockPos}.
     *
     * @param missingInternalName the class we couldn't find
     * @param maxResults          cap on number of suggestions (typically 3-5)
     * @return list of suggested replacements, possibly empty, never null
     */
    List<String> suggestClassAlternatives(String missingInternalName, int maxResults);

    /**
     * Suggest up to {@code maxResults} methods that exist on the owner (or its hierarchy) and
     * resemble the missing name, excluding the exact target itself.
     *
     * @param owner         the class the reference was against
     * @param name          the method name we couldn't find
     * @param descriptor    the descriptor we couldn't find
     * @param maxResults    cap on number of suggestions
     */
    List<MemberSignature> suggestMethodAlternatives(String owner, String name,
                                                    String descriptor, int maxResults);

    /**
     * @return the MC version this index describes (e.g., {@code "26.1"}),
     *         or {@code "unknown"} if the source didn't specify
     */
    String targetMcVersion();

    /** Method or field signature used in suggestions: the trio needed to identify a member. */
    record MemberSignature(String owner, String name, String descriptor) {
        public String prettyPrint() {
            return owner + "#" + name + descriptor;
        }
    }
}
