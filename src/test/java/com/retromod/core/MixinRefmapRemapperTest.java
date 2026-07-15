/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.retromod.mapping.IntermediaryToMojangMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Fabric mixin refmap in the intermediary namespace makes {@code @Inject}/{@code @At} selectors
 * name intermediary targets ({@code net/minecraft/class_310}); on a 26.1+ (official) host Mixin
 * rejects those ({@code InvalidInjectionException: … 'net/minecraft/class_310' … not supported}),
 * breaking the mod. Retromod must remap the refmap to Mojang and add a {@code data.official} section.
 * Found via an in-game 26.2 Fabric launch: the OFFLINE batch path (unlike the runtime path) left
 * refmaps unremapped, so AppleSkin's mixins failed.
 */
class MixinRefmapRemapperTest {

    private final IntermediaryToMojangMapper mapper = IntermediaryToMojangMapper.getInstance();

    @Test
    @DisplayName("plain data.intermediary -> data.official, remapped to Mojang")
    void remapsPlainIntermediaryData() {
        assertEquals("net/minecraft/client/Minecraft", mapper.mapClass("net/minecraft/class_310"),
                "sanity: mapping data loaded");
        String refmap = "{\"data\":{\"intermediary\":{\"MinecraftClientMixin\":{"
                + "\"onTick\":\"net/minecraft/class_310;method_1574()V\"}}}}";
        JsonObject root = JsonParser.parseString(MixinRefmapRemapper.remap(refmap, mapper)).getAsJsonObject();
        assertTrue(root.getAsJsonObject("data").has("official"), "data.official added");
        String off = root.getAsJsonObject("data").getAsJsonObject("official")
                .getAsJsonObject("MinecraftClientMixin").get("onTick").getAsString();
        assertTrue(off.contains("net/minecraft/client/Minecraft") && !off.contains("class_310"),
                "official section is Mojang-mapped: " + off);
    }

    @Test
    @DisplayName("combined data.\"named:intermediary\" -> \"named:official\" (the format AppleSkin ships)")
    void remapsCombinedNamedIntermediaryData() {
        // the AppleSkin case: mappings are dev-named, data keyed "named:intermediary" (dev -> intermediary)
        String refmap = "{"
                + "\"mappings\":{\"DebugHudMixin\":{\"getLeftText\":\"net/minecraft/client/gui/Foo;bar()V\"}},"
                + "\"data\":{\"named:intermediary\":{\"DebugHudMixin\":{"
                + "\"getLeftText\":\"Lnet/minecraft/class_340;method_1835()Ljava/util/List;\"}}}"
                + "}";
        JsonObject data = JsonParser.parseString(MixinRefmapRemapper.remap(refmap, mapper))
                .getAsJsonObject().getAsJsonObject("data");
        assertTrue(data.has("named:official"),
                "a named:official section must be produced for the 26.1+ runtime namespace");
        String off = data.getAsJsonObject("named:official").getAsJsonObject("DebugHudMixin")
                .get("getLeftText").getAsString();
        assertFalse(off.contains("class_340"), "class_340 must be remapped to its Mojang name: " + off);
        assertTrue(off.contains("net/minecraft/client/gui/components/DebugScreenOverlay")
                        || !off.contains("class_"),
                "intermediary tokens are gone: " + off);
    }

    @Test
    @DisplayName("input with nothing to remap and non-JSON input are returned unchanged (fail-safe)")
    void toleratesNonRemappableInput() {
        String noMappings = "{\"foo\":\"bar\"}"; // no mappings, no data -> unchanged
        assertEquals(noMappings, MixinRefmapRemapper.remap(noMappings, mapper), "nothing to remap");
        assertEquals("not json", MixinRefmapRemapper.remap("not json", mapper),
                "unparseable input returned as-is");
    }
}
