/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for {@link ModDataMigrator}: the 1.21.x -> 26.x data-pack JSON rewrites
 * that let a transformed structure mod's loot tables / advancements parse on 26.1.
 * Grounded against a headless 26.1.2 server run of When Dungeons Arise.
 */
class ModDataMigratorTest {

    private static String mig(String name, String json, String target) {
        return new String(
                ModDataMigrator.migrate(name, json.getBytes(StandardCharsets.UTF_8), target),
                StandardCharsets.UTF_8);
    }

    @Test
    void chainRenamedTo26Name() {
        String in = "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:chain\"}]}]}";
        String out = mig("data/m/loot_table/chests/x.json", in, "26.1");
        assertTrue(out.contains("\"minecraft:iron_chain\""), "chain should become iron_chain");
        assertFalse(out.contains("\"minecraft:chain\""), "no bare minecraft:chain should remain");
    }

    @Test
    void chainRenameIsQuoteBounded() {
        // must NOT touch chain_command_block (a real 26.1 item) or a modid:chain
        String in = "{\"a\":\"minecraft:chain_command_block\",\"b\":\"dungeons_arise:chain\"}";
        assertEquals(in, mig("data/m/loot_table/x.json", in, "26.1"));
    }

    @Test
    void dyedColorObjectCollapsesToInt() {
        String in = "{\"components\":{\"minecraft:dyed_color\":{\"rgb\":32832}}}";
        String out = mig("data/m/loot_table/x.json", in, "26.1");
        assertTrue(out.contains("\"minecraft:dyed_color\":32832"), "dyed_color should be the bare int");
        assertFalse(out.contains("\"rgb\""), "the rgb wrapper object should be gone");
    }

    @Test
    void dyedColorHandlesExtraKeys() {
        String in = "{\"minecraft:dyed_color\":{\"rgb\":255,\"show_in_tooltip\":false}}";
        assertTrue(mig("data/m/loot_table/x.json", in, "26.1").contains("\"minecraft:dyed_color\":255"));
    }

    @Test
    void customModelDataIntBecomesObject() {
        String in = "{\"components\":{\"minecraft:custom_model_data\":2341669}}";
        String out = mig("data/m/loot_table/x.json", in, "26.1");
        assertTrue(out.contains("\"minecraft:custom_model_data\":{\"floats\":[2341669]}"),
                "legacy CMD int should become a floats object");
    }

    @Test
    void customModelDataObjectLeftAlone() {
        // an already-migrated object form must not be double-wrapped
        String in = "{\"minecraft:custom_model_data\":{\"floats\":[5]}}";
        assertEquals(in, mig("data/m/loot_table/x.json", in, "26.1"));
    }

    @Test
    void advancementIconItemBecomesId() {
        String in = "{\"display\":{\"icon\":{\"item\":\"minecraft:barrel\"}}}";
        String out = mig("data/m/advancement/find_x.json", in, "26.1");
        assertTrue(out.contains("\"icon\":{\"id\":\"minecraft:barrel\"}"), "icon item should become id");
        assertFalse(out.contains("\"item\""), "no item key should remain in the icon");
    }

    @Test
    void iconRuleScopedToAdvancementsOnly() {
        // outside an advancement path the icon item->id rule must not fire
        String in = "{\"display\":{\"icon\":{\"item\":\"minecraft:barrel\"}}}";
        assertTrue(mig("data/m/loot_table/x.json", in, "26.1").contains("\"item\""));
    }

    @Test
    void gatedOffForPre26Targets() {
        // on 1.21.x, minecraft:chain still exists and dyed_color is still an object
        String in = "{\"name\":\"minecraft:chain\",\"minecraft:dyed_color\":{\"rgb\":1}}";
        assertEquals(in, mig("data/m/loot_table/x.json", in, "1.21.1"));
    }

    @Test
    void noOpOnUnrelatedData() {
        String in = "{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"id\":\"minecraft:stick\"}}";
        assertEquals(in, mig("data/m/recipe/x.json", in, "26.1"));
    }

    @Test
    void noOpOnNonDataPaths() {
        String in = "{\"name\":\"minecraft:chain\"}";
        assertEquals(in, mig("assets/m/models/item/x.json", in, "26.1"), "only data paths are migrated");
    }

    // --- entity_type tag: minecraft:potion split (26.x ThrownPotionSplitFix) ---

    @Test
    void potionEntitySplitInEntityTypeTag() {
        // The real When Dungeons Arise ignores_ensnaring tag shape.
        String in = "{\"values\":[\"minecraft:player\",\"minecraft:experience_orb\","
                + "\"minecraft:potion\",\"minecraft:item_frame\"]}";
        String out = mig("data/dungeons_arise/tags/entity_type/ignores_ensnaring.json", in, "26.1");
        assertTrue(out.contains("\"minecraft:splash_potion\""), "should add splash_potion");
        assertTrue(out.contains("\"minecraft:lingering_potion\""), "should add lingering_potion");
        assertFalse(out.contains("\"minecraft:potion\""), "old single potion entity should be gone");
        // neighbours preserved, still valid JSON array
        assertTrue(out.contains("\"minecraft:player\"") && out.contains("\"minecraft:item_frame\""));
    }

    @Test
    void potionSplitHandlesSoleAndTrailingEntries() {
        // sole entry
        assertTrue(mig("data/m/tags/entity_type/x.json", "{\"values\":[\"minecraft:potion\"]}", "26.1")
                .contains("\"minecraft:splash_potion\", \"minecraft:lingering_potion\""));
        // last entry (no trailing comma) must not corrupt the closing bracket
        String out = mig("data/m/tags/entity_type/x.json",
                "{\"values\":[\"minecraft:arrow\",\"minecraft:potion\"]}", "26.1");
        assertTrue(out.endsWith("\"minecraft:lingering_potion\"]}"), out);
    }

    @Test
    void potionSplitScopedToEntityTypeTagsOnly() {
        // the potion ITEM is unchanged in 26.x, so a loot table referencing it must be left alone
        String loot = "{\"pools\":[{\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:potion\"}]}]}";
        assertEquals(loot, mig("data/m/loot_table/x.json", loot, "26.1"));
        // and a non-entity_type tag (e.g. an item tag) is also not subject to the entity split
        String itemTag = "{\"values\":[\"minecraft:potion\"]}";
        assertEquals(itemTag, mig("data/m/tags/item/x.json", itemTag, "26.1"));
    }

    @Test
    void potionSplitLeavesObjectIdFormUntouched() {
        // {"id":"minecraft:potion"} object form is not an array element, so it must not be corrupted
        String in = "{\"values\":[{\"id\":\"minecraft:potion\",\"required\":false}]}";
        assertEquals(in, mig("data/m/tags/entity_type/x.json", in, "26.1"));
    }

    @Test
    void chainRenameAlsoAppliesInItemTags() {
        // tags are now migratable; the chain item split applies to an item tag too
        String in = "{\"values\":[\"minecraft:chain\"]}";
        assertTrue(mig("data/m/tags/item/x.json", in, "26.1").contains("\"minecraft:iron_chain\""));
    }

    @Test
    void tagsAreRecognizedAsMigratableData() {
        assertTrue(ModDataMigrator.isMigratableData("data/m/tags/entity_type/x.json"));
        assertTrue(ModDataMigrator.isMigratableData("data/m/tags/item/x.json"));
        assertFalse(ModDataMigrator.isMigratableData("data/m/tags/entity_type/x.nbt"));
    }

    // --- migrateTree: the extract/repack helper used by the runtime transformers ---

    @Test
    void migrateTreeRewritesOnlyAffectedFiles(@TempDir Path root) throws Exception {
        Path loot = root.resolve("data/m/loot_table/c.json");
        Path tag = root.resolve("data/m/tags/entity_type/e.json");
        Path clean = root.resolve("data/m/loot_table/ok.json");
        Path nonData = root.resolve("assets/m/models/x.json");
        for (Path p : new Path[]{loot, tag, clean, nonData}) Files.createDirectories(p.getParent());
        Files.writeString(loot, "{\"name\":\"minecraft:chain\"}");
        Files.writeString(tag, "{\"values\":[\"minecraft:potion\"]}");
        Files.writeString(clean, "{\"name\":\"minecraft:stick\"}");
        Files.writeString(nonData, "{\"name\":\"minecraft:chain\"}");

        int changed = ModDataMigrator.migrateTree(root, "26.1");

        assertEquals(2, changed, "only the chain loot table and the potion tag should change");
        assertTrue(Files.readString(loot).contains("iron_chain"));
        assertTrue(Files.readString(tag).contains("splash_potion"));
        assertEquals("{\"name\":\"minecraft:stick\"}", Files.readString(clean));
        assertEquals("{\"name\":\"minecraft:chain\"}", Files.readString(nonData), "non-data path untouched");
    }

    @Test
    void migrateTreeGatedOffForPre26Targets(@TempDir Path root) throws Exception {
        Path loot = root.resolve("data/m/loot_table/c.json");
        Files.createDirectories(loot.getParent());
        Files.writeString(loot, "{\"name\":\"minecraft:chain\"}");
        assertEquals(0, ModDataMigrator.migrateTree(root, "1.21.1"));
        assertEquals("{\"name\":\"minecraft:chain\"}", Files.readString(loot));
    }

    @Test
    void migrateTreeHandlesMissingRoot(@TempDir Path root) {
        assertEquals(0, ModDataMigrator.migrateTree(root.resolve("does-not-exist"), "26.1"));
    }

    // --- strict-JSON normalization: the main worldgen fix (Philips Ruins, 26.1 strict gson) ---

    /** Asserts a string parses under a STRICT (non-lenient) Gson reader, like 26.1 does. */
    private static void assertStrictParses(String json) {
        try {
            var r = new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
            r.setLenient(false);
            com.google.gson.JsonParser.parseReader(r);
            // ensure nothing trailing
            assertEquals(com.google.gson.stream.JsonToken.END_DOCUMENT, r.peek(), "trailing content");
        } catch (Exception e) {
            fail("expected strict-parseable JSON but got: " + e.getMessage() + "\n--- json ---\n" + json);
        }
    }

    @Test
    void stripsLineCommentsFromWorldgenJson() {
        // the real Philips Ruins processor_list shape: comments + the // at line 2 col 6 that
        // threw MalformedJsonException on 26.1
        String in = "{\n  // Processor lists run a processor for every block placed.\n"
                + "  \"processors\": [ { \"processor_type\": \"minecraft:rule\", \"rules\": [] } ]\n}";
        String out = mig("data/philipsruins/worldgen/processor_list/randomize_crypt_brick.json", in, "26.1");
        assertFalse(out.contains("//"), "line comment should be gone");
        assertTrue(out.contains("minecraft:rule"), "real content preserved");
        assertStrictParses(out);
    }

    @Test
    void stripsBlockCommentsAndTrailingCommas() {
        String in = "{ /* header */ \"a\": 1, \"b\": [1, 2,], }";
        String out = mig("data/m/worldgen/template_pool/x.json", in, "26.1");
        assertFalse(out.contains("/*"), "block comment gone");
        assertStrictParses(out);
        // values survive
        assertTrue(out.contains("\"a\"") && out.contains("\"b\""));
    }

    @Test
    void doesNotCorruptDoubleSlashInsideStringValue() {
        // a // inside a string value (e.g. a URL) must survive the reserialize intact
        String in = "{\"url\":\"https://example.com/x\", \"trailing\":1,}";
        String out = mig("data/m/worldgen/template_pool/x.json", in, "26.1");
        assertStrictParses(out);
        assertTrue(out.contains("https://example.com/x"), "URL in a string value must be preserved");
    }

    @Test
    void cleanStrictJsonIsLeftByteForByte() {
        // a file with no comments / trailing commas must not be reserialized (byte-identical)
        String in = "{\"processors\":[{\"processor_type\":\"minecraft:rule\",\"rules\":[]}]}";
        assertEquals(in, mig("data/m/worldgen/processor_list/x.json", in, "26.1"));
    }

    @Test
    void worldgenJsonIsRecognizedAsMigratable() {
        assertTrue(ModDataMigrator.isMigratableData("data/m/worldgen/template_pool/x.json"));
        assertTrue(ModDataMigrator.isMigratableData("data/m/worldgen/processor_list/x.json"));
        assertFalse(ModDataMigrator.isMigratableData("assets/m/models/block/x.json"));
    }

    @Test
    void normalizeIsGatedOffForPre26() {
        String in = "{\n // c\n \"a\":1\n}";
        assertEquals(in, mig("data/m/worldgen/template_pool/x.json", in, "1.21.1"),
                "lenient JSON is fine on a 1.21.x target - leave it");
    }
}
