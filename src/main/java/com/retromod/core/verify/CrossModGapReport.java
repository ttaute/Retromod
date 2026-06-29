/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import com.retromod.core.pattern.PatternMatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates {@link VerificationReport}s across many mods into a single gap
 * report that ranks MC references by how many different mods miss them.
 */
public final class CrossModGapReport {

    private final String targetMcVersion;
    private final Map<String, AggregatedEntry> byIdentity = new HashMap<>();
    /** Pattern name to the mods that produced that match. */
    private final Map<String, PatternAggregation> patternAggregation = new HashMap<>();
    private final Set<String> modsSeen = new HashSet<>();
    private int totalClassesScanned;

    public CrossModGapReport(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion == null ? "unknown" : targetMcVersion;
    }

    /** Merge one mod's per-mod report into the aggregation. */
    public void merge(VerificationReport report) {
        if (report == null) return;
        modsSeen.add(report.modId());
        totalClassesScanned += report.classesScanned();

        for (UnresolvedReference ref : report.missingClasses()) mergeRef(ref, report.modId());
        for (UnresolvedReference ref : report.missingMethods()) mergeRef(ref, report.modId());
        for (UnresolvedReference ref : report.missingFields()) mergeRef(ref, report.modId());
        for (UnresolvedReference ref : report.badSignatures()) mergeRef(ref, report.modId());

        // count which class shapes are common across the ecosystem
        for (Map.Entry<String, List<PatternMatch>> entry : report.patternMatches().entrySet()) {
            patternAggregation.computeIfAbsent(entry.getKey(), k -> new PatternAggregation(k))
                               .observe(report.modId(), entry.getValue().size());
        }
    }

    private void mergeRef(UnresolvedReference ref, String modId) {
        byIdentity.computeIfAbsent(ref.identityKey(), k -> new AggregatedEntry(ref))
                   .observe(modId);
    }

    /**
     * Render the cross-mod report most-missed first, with a header of scan stats
     * and one entry per unique reference.
     */
    public void writeTo(Appendable out) throws IOException {
        List<AggregatedEntry> sorted = new ArrayList<>(byIdentity.values());
        // frequency desc, then pretty-print asc for stable output
        sorted.sort(Comparator
                .comparingInt((AggregatedEntry e) -> -e.count())
                .thenComparing(e -> e.reference.prettyPrint()));

        out.append("=== Retromod cross-mod gap report ===\n");
        out.append("Target MC: ").append(targetMcVersion).append('\n');
        out.append("Mods scanned: ").append(Integer.toString(modsSeen.size())).append('\n');
        out.append("Classes scanned: ").append(Integer.toString(totalClassesScanned)).append('\n');
        out.append("Unique unresolved references: ").append(Integer.toString(sorted.size())).append('\n');
        out.append('\n');

        if (sorted.isEmpty()) {
            out.append("No cross-mod gaps. Every mod verified clean.\n");
            return;
        }

        out.append("Top gaps (ranked by number of mods affected):\n");
        int rank = 1;
        for (AggregatedEntry e : sorted) {
            out.append(String.format("  %3d. (%3d mod%s) %s%n",
                    rank++, e.count(), e.count() == 1 ? " " : "s",
                    formatEntry(e)));
            if (!e.reference.suggestions().isEmpty()) {
                out.append("         → ")
                   .append(String.join(", ", e.reference.suggestions()))
                   .append('\n');
            }
        }

        // pattern matches are informational, not actionable breakage, so list them separately
        if (!patternAggregation.isEmpty()) {
            List<PatternAggregation> patterns = new ArrayList<>(patternAggregation.values());
            patterns.sort(Comparator.comparingInt((PatternAggregation p) -> -p.modCount())
                                      .thenComparing(PatternAggregation::patternName));
            out.append('\n');
            out.append("Pattern matches (structural shapes detected in mod code):\n");
            for (PatternAggregation p : patterns) {
                out.append(String.format("  %-32s %3d mod%s, %d total match%s%n",
                        p.patternName(),
                        p.modCount(), p.modCount() == 1 ? " " : "s",
                        p.totalMatches(), p.totalMatches() == 1 ? "" : "es"));
            }
        }
    }

    private static String formatEntry(AggregatedEntry e) {
        // prefix with kind so the same symbol as both a class-miss and a method-miss reads unambiguously
        return "[" + e.reference.kind().name() + "] " + e.reference.prettyPrint();
    }

    /**
     * @return unmodifiable map of identity-key to aggregated entry, for callers
     *         that want the data programmatically rather than the formatted text
     */
    public Map<String, AggregatedEntry> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(byIdentity));
    }

    /**
     * Cross-mod summary for a single pattern: how many distinct mods produced any
     * match, and the total match count across all mods.
     */
    public static final class PatternAggregation {
        private final String patternName;
        private final Set<String> modsWithMatch = new HashSet<>();
        private int totalMatches = 0;

        PatternAggregation(String patternName) {
            this.patternName = patternName;
        }

        void observe(String modId, int matchesInThisMod) {
            modsWithMatch.add(modId);
            totalMatches += matchesInThisMod;
        }

        public String patternName() { return patternName; }
        public int modCount() { return modsWithMatch.size(); }
        public int totalMatches() { return totalMatches; }
        public Set<String> affectedMods() { return Collections.unmodifiableSet(modsWithMatch); }
    }

    /**
     * One reference plus the set of mods that hit it. Count is derived from the
     * set size; using a set ensures the same mod reporting the same miss from
     * 10 call sites doesn't inflate the count to 10.
     */
    public static final class AggregatedEntry {
        private final UnresolvedReference reference;
        private final Set<String> affectedMods = new HashSet<>();

        AggregatedEntry(UnresolvedReference ref) {
            this.reference = ref;
        }

        void observe(String modId) {
            affectedMods.add(modId);
        }

        public UnresolvedReference reference() {
            return reference;
        }

        public int count() {
            return affectedMods.size();
        }

        public Set<String> affectedMods() {
            return Collections.unmodifiableSet(affectedMods);
        }
    }
}
