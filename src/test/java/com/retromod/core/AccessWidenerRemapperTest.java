/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import com.retromod.mapping.IntermediaryToMojangMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An intermediary access widener / classTweaker crashes Fabric's classTweaker reader on a 26.1+
 * (official-namespace) host before any mixin/mod construction (no crash-report). Retromod must remap
 * the header namespace to {@code official} and every {@code class_/method_/field_} token to Mojang.
 * Found via an in-game 26.2 Fabric launch: cloth-config (nested in AppleSkin) ships
 * {@code accessWidener v1 intermediary} and the OFFLINE batch path (unlike the runtime path) left it
 * unremapped, dying with {@code ClassTweakerFormatException: Namespace (intermediary) does not match
 * current runtime namespace (official)}.
 */
class AccessWidenerRemapperTest {

    private final IntermediaryToMojangMapper mapper = IntermediaryToMojangMapper.getInstance();

    @Test
    @DisplayName("header namespace intermediary -> official, and body tokens remap to Mojang")
    void remapsHeaderAndBody() {
        // class_310 -> net/minecraft/client/Minecraft (a stable, always-present mapping)
        String expectClass = mapper.mapClass("net/minecraft/class_310");
        assertEquals("net/minecraft/client/Minecraft", expectClass, "sanity: mapping data loaded");

        String aw = "accessWidener\tv1\tintermediary\n"
                + "accessible\tclass\tnet/minecraft/class_310\n"
                + "# a comment stays\n"
                + "\n"
                + "accessible\tfield\tnet/minecraft/class_310\tfield_1724\tLnet/minecraft/class_638;\n";
        String out = AccessWidenerRemapper.remapToOfficial(aw, mapper);

        String header = out.split("\n", 2)[0];
        assertTrue(header.contains("official") && !header.contains("intermediary"),
                "the header namespace must become official: [" + header + "]");
        assertTrue(out.contains("net/minecraft/client/Minecraft"),
                "class_310 must be remapped to its Mojang name in the body");
        assertFalse(out.contains("class_310"), "no intermediary class token should remain");
        assertTrue(out.contains("# a comment stays"), "comments are preserved");
    }

    @Test
    @DisplayName("a non-intermediary (already-official or named) AW is returned unchanged")
    void leavesNonIntermediaryAlone() {
        String named = "accessWidener\tv2\tnamed\naccessible\tclass\tnet/minecraft/client/Minecraft\n";
        assertEquals(named, AccessWidenerRemapper.remapToOfficial(named, mapper),
                "only intermediary AWs are remapped; a named/official one is untouched");
        assertNull(AccessWidenerRemapper.remapToOfficial(null, mapper), "null is tolerated");
    }
}
