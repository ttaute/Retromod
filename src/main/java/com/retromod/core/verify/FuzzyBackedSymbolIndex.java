/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import com.retromod.core.FuzzyMethodResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link McSymbolIndex} backed by {@link FuzzyMethodResolver}, reusing its
 * existing MC-JAR index instead of building a second one. Adds exact-match
 * accessors on top of the resolver's API.
 *
 * <p>When the resolver isn't indexed (standalone CLI, tests, unknown MC JAR
 * path), {@link #isAvailable()} returns false so callers treat lookups as
 * "unknown" rather than "missing" and avoid false-positive unresolved-reference
 * reports.</p>
 */
public class FuzzyBackedSymbolIndex implements McSymbolIndex {

    private final FuzzyMethodResolver resolver;
    private final String mcVersion;

    /**
     * @param resolver  the fuzzy resolver to delegate to; must not be null but
     *                  need not be indexed yet
     * @param mcVersion the MC version this index describes; pass {@code "unknown"}
     *                  if not available
     */
    public FuzzyBackedSymbolIndex(FuzzyMethodResolver resolver, String mcVersion) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
        this.mcVersion = mcVersion != null ? mcVersion : "unknown";
    }

    @Override
    public boolean isAvailable() {
        return resolver.isIndexed();
    }

    @Override
    public boolean hasClass(String internalName) {
        return resolver.hasClass(internalName);
    }

    @Override
    public boolean hasMethod(String owner, String name, String descriptor) {
        return resolver.hasMethod(owner, name, descriptor);
    }

    @Override
    public boolean hasField(String owner, String name, String descriptor) {
        return resolver.hasField(owner, name, descriptor);
    }

    @Override
    public boolean hasMethodName(String owner, String name) {
        return resolver.hasMethodName(owner, name);
    }

    @Override
    public boolean hasFieldName(String owner, String name) {
        return resolver.hasFieldName(owner, name);
    }

    @Override
    public List<String> suggestClassAlternatives(String missingInternalName, int maxResults) {
        // The fuzzy resolver scores members, not classes, so match on simple
        // name instead: this catches a class that moved packages but kept its
        // name (BlockPos). Renames are handled by suggestMethodAlternatives.
        if (!isAvailable() || missingInternalName == null) {
            return List.of();
        }
        int lastSlash = missingInternalName.lastIndexOf('/');
        String simpleName = lastSlash >= 0
                ? missingInternalName.substring(lastSlash + 1)
                : missingInternalName;

        List<String> results = new ArrayList<>(maxResults);
        for (String indexed : resolver.getIndexedClassNames()) {
            if (results.size() >= maxResults) break;
            int slash = indexed.lastIndexOf('/');
            String indexedSimple = slash >= 0 ? indexed.substring(slash + 1) : indexed;
            if (indexedSimple.equals(simpleName) && !indexed.equals(missingInternalName)) {
                results.add(indexed);
            }
        }
        return results;
    }

    @Override
    public List<MemberSignature> suggestMethodAlternatives(String owner, String name,
                                                            String descriptor, int maxResults) {
        if (!isAvailable() || owner == null) {
            return List.of();
        }

        // Delegate to the fuzzy resolver's scoring (hierarchy, descriptor
        // distance, name similarity). It returns only the single best match.
        FuzzyMethodResolver.MethodInfo best = resolver.resolveMethod(owner, name, descriptor);
        List<MemberSignature> results = new ArrayList<>();
        if (best != null) {
            results.add(new MemberSignature(best.owner(), best.name(), best.descriptor()));
        }
        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    @Override
    public String targetMcVersion() {
        return mcVersion;
    }
}
