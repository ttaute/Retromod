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
 * Migrates a mod's bundled data-pack JSON across the 1.21.x to 26.x data changes the
 * bytecode pass can't reach (loot tables, advancements, recipes, tags, worldgen).
 *
 * <p>The mod-jar transform rewrites bytecode and loader metadata but not the {@code data/}
 * JSON a mod ships, and several 26.x changes are data-only.
 *
 * <p>Main fix: 26.1 parses data-pack JSON with a strict Gson reader, rejecting the {@code //}
 * / {@code /* *}{@code /} comments and trailing commas common in hand-authored worldgen files
 * (Jigsaw template_pools, processor_lists). Each such file throws
 * {@code MalformedJsonException} and stays unbound, and one unbound template_pool is fatal
 * (FatalStartupException) and aborts the shared worldgen registry load, so co-loaded structure
 * mods then look like they never registered their own types. {@link #normalizeLenientJson}
 * strips the offending tokens, but only for files that contain one.
 *
 * <p>Targeted rewrites, each gated to 26.x targets ({@link RetromodVersion#isUnobfuscatedTarget}):
 * <ul>
 *   <li>{@code minecraft:chain} to {@code minecraft:iron_chain}: 26.x split the chain item
 *       into iron/copper variants. Quote-bounded so it can't touch
 *       {@code minecraft:chain_command_block} or a {@code <modid>:chain}.</li>
 *   <li>{@code dyed_color} object to int: 26.x wants the colour int (or an {@code [r,g,b]}
 *       array); 1.21.x shipped an object {@code {"rgb":N,...}}.</li>
 *   <li>advancement icon {@code "item"} to {@code "id"}: 26.x renamed the icon ItemStack key.</li>
 *   <li>{@code custom_model_data} int to object: 26.x (1.21.4+) expanded the component from a
 *       bare int to {@code {"floats":[...],...}}; the legacy int maps to the first float.</li>
 *   <li>{@code minecraft:potion} entity-type split: 26.x split the thrown-potion entity into
 *       {@code minecraft:splash_potion} and {@code minecraft:lingering_potion} (vanilla's
 *       {@code ThrownPotionSplitFix}). Scoped to {@code tags/entity_type/} files only, since
 *       the potion item is unchanged.</li>
 * </ul>
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

    // Bare-int custom_model_data. The negative lookahead skips a value that's already a float
    // or a longer token.
    private static final Pattern CMD_INT = Pattern.compile(
            "\"minecraft:custom_model_data\"\\s*:\\s*(-?\\d+)(?![\\d.])");

    // A bare-string "minecraft:potion" array element inside an entity_type tag. Anchored on a
    // leading [ or , and a trailing , or ] so it can't touch an object's "id":"minecraft:potion";
    // the preceding ':' never matches [\[,]. The trailing delimiter stays in the lookahead.
    private static final Pattern POTION_ENTITY_SPLIT = Pattern.compile(
            "([\\[,]\\s*)\"minecraft:potion\"(?=\\s*[,\\]])");

    // Lenient-only features 26.1's strict gson rejects. A `//` inside a string value only
    // triggers the string-aware strip, never a false negative.
    private static final Pattern LENIENT_JSON_HINT = Pattern.compile("//|/\\*|,\\s*[}\\]]");

    private ModDataMigrator() {}

    /**
     * True for data entries this migrator can act on (cheap pre-filter). Covers all datapack
     * JSON, since the strict-JSON comment fix applies to any data file.
     */
    public static boolean isMigratableData(String entryName) {
        return entryName.endsWith(".json")
                && (entryName.startsWith("data/")         // datapack JSON (worldgen, loot, tags, ...)
                 || entryName.contains("/data/")          // same, with a path prefix
                 || entryName.contains("/loot_table")     // fallback for layouts that don't sit
                 || entryName.contains("/advancement")    // under data/
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

        // Make the file parse under 26.1's strict gson (strip // /* */ comments and trailing
        // commas). A clean file comes back as the same array, so the rewrites below still see
        // the original bytes.
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

        // No rewrite fired: return normalized, which is `json` when the file was already
        // strict-clean, or the comment-stripped bytes otherwise.
        if (out.equals(in)) return normalized;
        LOGGER.debug("Migrated 26.x data formats in {}", entryName);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Make a data-pack JSON parse under 26.1's strict Gson by removing the two lenient-only
     * features old mods rely on: {@code //} / {@code /* *}{@code /} comments and trailing commas.
     * A string-aware single pass, not a reserialize: a {@code //} or {@code ,} inside a string
     * value is copied verbatim and the file's formatting is otherwise preserved. Hand-rolled
     * because Gson's own lenient reader rejects trailing commas.
     *
     * <p>Other lenient quirks (single quotes, unquoted keys, NaN) are left for the loader to
     * report. Returns the input array (same reference) when there's nothing to strip, so callers
     * can detect a change by reference identity.
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
            if (c == ',' && trailingComma(s, i + 1)) continue;            // drop a dangling comma
            out.append(c);
        }
        return out.toString();
    }

    /** True if the next non-whitespace, non-comment char from {@code i} is a closing }} or ]. */
    private static boolean trailingComma(String s, int i) {
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
     * which extract a mod before repackaging; the byte-stream {@link #migrate} fits the offline
     * CLI's entry-by-entry copy instead.
     *
     * <p>Returns 0 for non-26.x targets or a missing/empty root. Entry names are normalized to
     * forward slashes so {@link #isMigratableData} path checks match on every OS.
     *
     * @return the number of files rewritten.
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
                if (after != before) {        // same array back means nothing matched
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
