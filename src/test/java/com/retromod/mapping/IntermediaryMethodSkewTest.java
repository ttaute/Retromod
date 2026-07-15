/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Intermediary-map version skew (surfaced by the top-40 Fabric 1.21.1 corpus audit). The bundled
 * {@code intermediary-to-mojang.tsv} was harvested with 26.x intermediary ids, but a distributed
 * 1.21.1 Fabric mod references the 1.21.1-era id for any method Mojang RENAMED between 1.21.1 and
 * 26.x. {@code Minecraft.getTimer()} became {@code getDeltaTracker()}: the tsv had only the newer
 * {@code method_61966}, so the 1.21.1 id {@code method_60646} the mods actually call was unmapped and
 * left dangling (10 of the top-40 Fabric mods). Both ids now map to the current Mojang name.
 */
class IntermediaryMethodSkewTest {

    @Test
    @DisplayName("method_60646 (1.21.1 getTimer) and method_61966 both map to getDeltaTracker")
    void deltaTrackerGetterMapsAcrossTheRename() {
        IntermediaryToMojangMapper m = IntermediaryToMojangMapper.getInstance();
        assertEquals("getDeltaTracker", m.mapMethod("method_60646"),
                "the 1.21.1 intermediary id distributed mods reference must map to the 26.x name");
        assertEquals("getDeltaTracker", m.mapMethod("method_61966"),
                "the 26.x intermediary id keeps mapping too");
    }

    /**
     * FastColor$ARGB32/ABGR32 -> ARGB (26.1) re-minted the color-helper method ids. The class already
     * resolves (TSV class entry + FastColor->ARGB class-move), so only the 1.21.1-era method ids were
     * dangling on 26.x. Composed from the 1.21.1 intermediary tiny + Mojang ProGuard and cross-checked
     * against the real 26.2 ARGB (each name present, each id absent from the 26.x map, 0 conflicts).
     */
    @Test
    @DisplayName("FastColor->ARGB color-helper method ids (re-minted by the rename) map to their 26.2 names")
    void fastColorArgbSkewMapped() {
        IntermediaryToMojangMapper m = IntermediaryToMojangMapper.getInstance();
        assertEquals("color", m.mapMethod("method_57173"), "ARGB.color(int,int,int)");
        assertEquals("opaque", m.mapMethod("method_57174"), "ARGB.opaque(int)");
        assertEquals("color", m.mapMethod("method_58144"), "ARGB.color(int,int)");
        assertEquals("as8BitChannel", m.mapMethod("method_59553"), "ARGB.as8BitChannel");
        assertEquals("colorFromFloat", m.mapMethod("method_59554"), "ARGB.colorFromFloat(float x4)");
        assertEquals("average", m.mapMethod("method_60676"), "ARGB.average(int,int)");
    }
}
