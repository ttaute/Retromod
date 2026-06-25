/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.resources;

import com.retromod.core.RetromodVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Migrates a mod's bundled data-pack JSON across the 1.21.x -> 26.x data changes the
 * bytecode pass can't reach (loot tables, advancements, recipes, tags, worldgen).
 *
 * <h2>Why</h2>
 * Retromod's mod-jar transform rewrites <b>bytecode</b> and loader metadata; it does
 * not touch the {@code data/} JSON a mod ships. But several 26.x changes are data-only.
 * A 1.21.x structure mod then loads and its content registers, yet its data-pack JSON
 * fails to load on 26.1, so chests come up empty, advancements never load, and (the
 * big one) <b>worldgen JSON fails to parse, taking down structure generation</b>.
 *
 * <h2>The strict-JSON fix (the main one)</h2>
 * 26.1 parses data-pack JSON with a <b>strict</b> Gson reader. Older mods relied on
 * Minecraft's historically <i>lenient</i> parsing and ship JSON with {@code //} / {@code /* *}{@code /}
 * comments and trailing commas, hugely common in hand-authored worldgen files (Jigsaw
 * template_pools, processor_lists). On 26.1 every such file throws
 * {@code MalformedJsonException: Use JsonReader.setStrictness(Strictness.LENIENT)} and stays
 * <b>unbound</b>. One mod's unbound template_pool is FATAL (FatalStartupException) and aborts
 * the <i>shared</i> worldgen registry load, so co-loaded structure mods then look like they
 * never registered their own types ("Unknown registry key" / "Unbound values"). Verified on a
 * headless 26.1.2 server: Philips Ruins crashed the whole server; once its comments were
 * stripped it AND a co-loaded YUNG's Extras both generated. {@link #normalizeLenientJson}
 * lenient-parses then strict-reserializes, but only for files that actually contain a
 * comment / trailing comma (a clean file is left byte-for-byte).
 *
 * <h2>The targeted rewrites (each verified against vanilla 26.1.2 reports)</h2>
 * <ul>
 *   <li><b>{@code minecraft:chain} -> {@code minecraft:iron_chain}.</b> 26.x split the
 *       plain chain item into iron/copper variants; {@code minecraft:chain} is absent
 *       from the 26.1.2 item registry. Quote-bounded so it can't touch
 *       {@code minecraft:chain_command_block} or a {@code <modid>:chain}.</li>
 *   <li><b>{@code dyed_color} object -> int.</b> 26.x wants the colour int (or an
 *       {@code [r,g,b]} array); 1.21.x shipped an object {@code {"rgb":N,...}}.</li>
 *   <li><b>advancement icon {@code "item"} -> {@code "id"}.</b> 26.x renamed the icon
 *       ItemStack key.</li>
 *   <li><b>{@code custom_model_data} int -> object.</b> 26.x (1.21.4+) expanded the
 *       component from a bare int to {@code {"floats":[...],...}}; the legacy int maps
 *       to the first float. So {@code "minecraft:custom_model_data":N} ->
 *       {@code {"floats":[N]}}.</li>
 *   <li><b>{@code minecraft:potion} entity-type -> split.</b> 26.x split the single
 *       thrown-potion entity {@code minecraft:potion} into {@code minecraft:splash_potion}
 *       and {@code minecraft:lingering_potion} (vanilla's {@code ThrownPotionSplitFix}).
 *       An {@code entity_type} tag that still lists {@code minecraft:potion} (e.g. WDA's
 *       {@code dungeons_arise:ignores_ensnaring}) fails to load, taking down any predicate
 *       that references the tag. Scoped to {@code tags/entity_type/} files only: the potion
 *       <i>item</i> is unchanged, so loot tables / item contexts must be left alone.</li>
 * </ul>
 *
 * <h2>Scope &amp; safety</h2>
 * The targeted rewrites are regex/string based and minimal-diff: each is a no-op when its
 * pattern is absent, so running it on an unaffected file is harmless. The strict-JSON pass
 * does reserialize, but only files that fail strict parsing, and it preserves member order
 * and number tokens. The whole pass is <b>gated to 26.x targets</b>
 * ({@link RetromodVersion#isUnobfuscatedTarget}). On a 1.21.x target the old shapes parse
 * leniently and are correct, so they must be left alone.
 */
public final class ModDataMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-DataMigrate");

    private static final String CHAIN_OLD = "\"minecraft:chain\"";
    private static final String CHAIN_NEW = "\"minecraft:iron_chain\"";

    // No nested braces inside a dyed_color object, so [^}] stays within the one object.
    private static final Pattern DYED_COLOR = Pattern.compile(
            "\"minecraft:dyed_color\"\\s*:\\s*\\{[^}]*?\"rgb\"\\s*:\\s*(-?\\d+)[^}]*?\\}");

    // The icon object's item id is its leading key, so anchor on the icon brace.
    private static final Pattern ICON_ITEM = Pattern.compile(
            "(\"icon\"\\s*:\\s*\\{\\s*)\"item\"(\\s*:)");

    // Bare-int custom_model_data -> {"floats":[N]}. The negative lookahead keeps us off
    // a value that's already a float (already-migrated) or a longer token.
    private static final Pattern CMD_INT = Pattern.compile(
            "\"minecraft:custom_model_data\"\\s*:\\s*(-?\\d+)(?![\\d.])");

    // A bare-string "minecraft:potion" array element inside an entity_type tag. Anchored on a
    // leading [ or , (an array element) and a trailing , or ] so it can't touch an object's
    // "id":"minecraft:potion" (which would corrupt to dangling strings); that preceding ':'
    // never matches [\[,]. The trailing delimiter stays in the lookahead, so the original
    // comma/bracket is preserved.
    private static final Pattern POTION_ENTITY_SPLIT = Pattern.compile(
            "([\\[,]\\s*)\"minecraft:potion\"(?=\\s*[,\\]])");

    // Strict gson (26.1's parser) rejects these lenient-only features. A real comment / trailing
    // comma is always caught; a `//` inside a string value (e.g. a URL) is a harmless false
    // positive that just triggers the (string-aware, lossless) strip. No false NEGATIVES.
    private static final Pattern LENIENT_JSON_HINT = Pattern.compile("//|/\\*|,\\s*[}\\]]");

    private ModDataMigrator() {}

    /**
     * True for data entries this migrator can act on (cheap pre-filter). Covers ALL datapack
     * JSON, not just loot/advancement/recipe/tags: the strict-JSON comment fix applies to any
     * data file (worldgen template_pools / processor_lists are the ones that actually bite).
     */
    public static boolean isMigratableData(String entryName) {
        return entryName.endsWith(".json")
                && (entryName.startsWith("data/")         // datapack JSON (worldgen, loot, tags, ...)
                 || entryName.contains("/data/")          // same, with a path prefix
                 || entryName.contains("/loot_table")     // lenient fallback for odd callers /
                 || entryName.contains("/advancement")    // legacy layouts that don't sit under data/
                 || entryName.contains("/recipe")
                 || entryName.contains("/tags/"));
    }

    /**
     * Apply the 26.x data rewrites to one JSON entry. Returns the input bytes unchanged
     * if the target is not 26.x, the entry is not migratable data, or nothing matched.
     */
    public static byte[] migrate(String entryName, byte[] json, String targetMcVersion) {
        if (!RetromodVersion.isUnobfuscatedTarget(targetMcVersion)) return json;
        if (!isMigratableData(entryName)) return json;

        // First: make the file parse under 26.1's STRICT gson (strip // /* */ comments and
        // trailing commas). For the common clean file this returns the SAME array unchanged,
        // so the targeted rewrites below still see the original bytes.
        byte[] normalized = normalizeLenientJson(json);

        String in = new String(normalized, StandardCharsets.UTF_8);
        String out = in.replace(CHAIN_OLD, CHAIN_NEW);
        out = DYED_COLOR.matcher(out).replaceAll("\"minecraft:dyed_color\":$1");
        out = CMD_INT.matcher(out).replaceAll("\"minecraft:custom_model_data\":{\"floats\":[$1]}");
        if (entryName.contains("/advancement")) {
            out = ICON_ITEM.matcher(out).replaceAll("$1\"id\"$2");
        }
        if (entryName.contains("/tags/entity_type/")) {
            out = POTION_ENTITY_SPLIT.matcher(out).replaceAll(
                    "$1\"minecraft:splash_potion\", \"minecraft:lingering_potion\"");
        }

        // No targeted rewrite fired: still return `normalized`, which IS `json` when the file was
        // already strict-clean, or the comment-stripped bytes when it wasn't.
        if (out.equals(in)) return normalized;
        LOGGER.debug("Migrated 26.x data formats in {}", entryName);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Make a data-pack JSON parse under 26.1's strict Gson by removing the two lenient-only
     * features old mods rely on: {@code //} / {@code /* *}{@code /} comments and trailing commas.
     * A string-aware single pass, NOT a reserialize: a {@code //} or {@code ,} inside a string
     * value (a URL, a {@code "a,b"} value) is copied verbatim, and the file's formatting is
     * otherwise preserved byte-for-byte (only the comments / dangling commas vanish). We hand-roll
     * rather than round-trip through Gson because Gson's own lenient reader rejects trailing commas.
     *
     * <p>Other lenient quirks (single quotes, unquoted keys, NaN) are left untouched; they're
     * vanishingly rare in datapack JSON, and the loader will report them. Returns the input array
     * <b>unchanged</b> (same reference) when there's nothing to strip, so callers can use
     * reference identity to detect a change.
     */
    static byte[] normalizeLenientJson(byte[] json) {
        String in = new String(json, StandardCharsets.UTF_8);
        if (!LENIENT_JSON_HINT.matcher(in).find()) return json;   // already strict; cheap exit
        String out = stripLenientJson(in);
        if (out.equals(in)) return json;
        LOGGER.debug("Normalized lenient JSON (stripped comments / trailing commas)");
        return out.getBytes(StandardCharsets.UTF_8);
    }

    /** String-aware strip of // and block comments plus trailing commas. See {@link #normalizeLenientJson}. */
    private static String stripLenientJson(String s) {
        int n = s.length();
        StringBuilder out = new StringBuilder(n);
        boolean inStr = false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (inStr) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(s.charAt(++i));   // escaped char (incl. \" and \\) copied verbatim
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (c == '"') { inStr = true; out.append(c); continue; }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {       // line comment
                i += 2;
                while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
                i--;                              // leave the newline for the loop to append
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {       // block comment
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i++;                              // skip the closing '/'
                continue;
            }
            if (c == ',' && isTrailingComma(s, i + 1)) continue;          // drop a dangling comma
            out.append(c);
        }
        return out.toString();
    }

    /** True if the next non-whitespace, non-comment char from {@code i} is a closing }} or ]. */
    private static boolean isTrailingComma(String s, int i) {
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; }
            else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                i += 2;
                while (i < n && s.charAt(i) != '\n' && s.charAt(i) != '\r') i++;
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                return c == '}' || c == ']';
            }
        }
        return false;
    }

    /**
     * Apply {@link #migrate} to every data file in an already-extracted mod tree, rewriting
     * changed files in place. Used by the runtime transformers (Fabric/Forge) and JiJ handlers,
     * which extract a mod to a temp dir before repackaging. The byte-stream {@link #migrate}
     * fits the offline CLI's entry-by-entry copy, this fits the extract/repack flow.
     *
     * <p>No-op (returns 0) for non-26.x targets or a missing/empty root. Entry names are
     * normalized to forward slashes so {@link #isMigratableData} path checks match on every OS.
     *
     * @return the number of files actually rewritten.
     */
    public static int migrateTree(Path root, String targetMcVersion) {
        if (!RetromodVersion.isUnobfuscatedTarget(targetMcVersion)) return 0;
        if (root == null || !Files.isDirectory(root)) return 0;

        int changed = 0;
        final List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            LOGGER.debug("Could not walk {} for data migration: {}", root, e.getMessage());
            return 0;
        }

        for (Path p : files) {
            String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
            if (!isMigratableData(rel)) continue;
            try {
                byte[] before = Files.readAllBytes(p);
                byte[] after = migrate(rel, before, targetMcVersion);
                if (after != before) {        // migrate() returns the same array when nothing matched
                    Files.write(p, after);
                    changed++;
                }
            } catch (IOException e) {
                LOGGER.debug("Could not migrate data file {}: {}", rel, e.getMessage());
            }
        }
        return changed;
    }
}
