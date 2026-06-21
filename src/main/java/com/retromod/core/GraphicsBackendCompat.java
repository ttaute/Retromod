/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier-0 Vulkan compatibility: on a host where Minecraft offers <b>both</b>
 * OpenGL and Vulkan and defaults to Vulkan (26.2+), prefer the still-present
 * <b>OpenGL</b> backend so translated old mods keep rendering.
 *
 * <h2>Why</h2>
 * MC 26.2 added a Vulkan rendering backend ({@code com.mojang.blaze3d.vulkan})
 * alongside the existing OpenGL one ({@code com.mojang.blaze3d.opengl}) and made
 * Vulkan the default, selected via {@code net.minecraft.client.PreferredGraphicsApi}
 * from {@code options.txt}'s {@code preferredGraphicsBackend} key. Old mods that issue raw
 * OpenGL ({@code org.lwjgl.opengl.GL11.*}) or other GL-context-dependent calls
 * work fine on the OpenGL backend but not on Vulkan - and translating arbitrary
 * GL to Vulkan in bytecode is infeasible (you'd be writing a GL driver). Since
 * the OpenGL backend is <b>still shipped</b>, the correct, simple compat lever is
 * to select it. (Mods that use Minecraft's modern backend-agnostic render API are
 * unaffected either way - they run on Vulkan too.)
 *
 * <h2>Non-destructive by design</h2>
 * <ul>
 *   <li>Only acts on a <b>client</b>, on a host <b>≥ 26.2</b> (where Vulkan exists).</li>
 *   <li>Sets {@code preferredGraphicsBackend:"opengl"} only when the user hasn't made an explicit
 *       choice (key absent or {@code default}). If the user explicitly chose
 *       {@code opengl} we leave it; if they chose {@code vulkan} we <b>respect it</b>
 *       and only log a warning that old mods may render incorrectly.</li>
 *   <li>Opt out entirely with {@code -Dretromod.graphics.noPreference=true}.</li>
 *   <li>Every other {@code options.txt} line is preserved untouched.</li>
 * </ul>
 *
 * <p>The user can always switch to Vulkan afterwards in Video Settings; that
 * writes {@code preferredGraphicsBackend:"vulkan"}, which we then never override.
 *
 * <h2>Looking ahead to 26.3</h2>
 * When OpenGL is removed entirely (no backend to fall back to), this lever stops
 * working and raw-GL mods hit the hard boundary documented in the roadmap. This
 * is a <i>26.2-window</i> measure that buys time, not the 26.3 answer.
 */
public final class GraphicsBackendCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /** {@code -Dretromod.graphics.noPreference=true} disables this entirely. */
    public static final String OPT_OUT_PROPERTY = "retromod.graphics.noPreference";

    private static final String OPTIONS_FILE = "options.txt";

    // The options.txt key MC 26.2 actually persists. Verified against real 26.2
    // options.txt (version 4903): `preferredGraphicsBackend:"default"`. IMPORTANT:
    // `graphicsApi` is only the in-game label's translation key (`options.graphicsApi`,
    // shown as "Graphics API"), NOT the stored key - writing `graphicsApi:…` does
    // nothing. The stored value is a JSON-quoted enum serialized name:
    // "opengl" / "vulkan" / "default".
    private static final String KEY = "preferredGraphicsBackend";
    private static final String OPENGL = "opengl";
    private static final String VULKAN = "vulkan";
    /** The exact options.txt line that selects the OpenGL backend (value is JSON-quoted). */
    private static final String OPENGL_LINE = KEY + ":\"" + OPENGL + "\"";

    private GraphicsBackendCompat() {}

    /** Outcome of an {@link #ensureOpenGlForOldMods} call - surfaced for logging and tests. */
    public enum Result {
        /** Wrote {@code preferredGraphicsBackend:"opengl"} (key was absent or {@code "default"}). */
        SET_OPENGL,
        /** Already {@code preferredGraphicsBackend:"opengl"}; nothing to do. */
        ALREADY_OPENGL,
        /** User explicitly chose Vulkan - left as-is, warned. */
        RESPECTED_VULKAN,
        /** Host is below 26.2 (no Vulkan backend) - not applicable. */
        SKIPPED_OLD_HOST,
        /** Not a client (dedicated server / headless). */
        SKIPPED_NOT_CLIENT,
        /** Opted out via the system property. */
        SKIPPED_OPT_OUT,
        /** I/O error reading/writing options.txt (logged, non-fatal). */
        IO_ERROR
    }

    /**
     * Ensure the OpenGL backend is selected for old mods on a 26.2+ client.
     * Safe to call unconditionally from an entry point - it gates itself.
     *
     * @param gameDir        the Minecraft game directory (contains options.txt)
     * @param hostMcVersion  the detected host MC version (e.g. {@code "26.2"})
     * @return what it did (for logging / tests)
     */
    public static Result ensureOpenGlForOldMods(Path gameDir, String hostMcVersion) {
        if (Boolean.getBoolean(OPT_OUT_PROPERTY)) {
            return Result.SKIPPED_OPT_OUT;
        }
        if (!EnvironmentDetector.isClient()) {
            return Result.SKIPPED_NOT_CLIENT;
        }
        // Applies only where Vulkan exists / is the default: host >= 26.2.
        // mcVersionExceeds("26.2", host) is true when 26.2 > host, i.e. host < 26.2.
        if (hostMcVersion == null || RetromodVersion.mcVersionExceeds("26.2", hostMcVersion)) {
            return Result.SKIPPED_OLD_HOST;
        }
        if (gameDir == null) {
            return Result.IO_ERROR;
        }
        try {
            return apply(gameDir.resolve(OPTIONS_FILE));
        } catch (IOException e) {
            LOGGER.warn("[Retromod] Could not set graphics backend preference in options.txt: {}",
                    e.getMessage());
            return Result.IO_ERROR;
        }
    }

    /** Core options.txt read/modify/write - package-private for tests. */
    static Result apply(Path optionsTxt) throws IOException {
        // First launch on a fresh instance: options.txt doesn't exist yet (MC
        // creates it during startup, AFTER our pre-launch runs). Pre-create it
        // with just our key; MC fills in every other option with its defaults on
        // first save. This is what makes the very first launch come up on OpenGL.
        if (!Files.exists(optionsTxt)) {
            Files.writeString(optionsTxt, OPENGL_LINE + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            logSet(true);
            return Result.SET_OPENGL;
        }

        List<String> lines = Files.readAllLines(optionsTxt, StandardCharsets.UTF_8);
        int idx = -1;
        String currentValue = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equals(KEY)) {
                idx = i;
                // Value is JSON-quoted in options.txt (e.g. "vulkan") - unquote it.
                currentValue = unquote(line.substring(colon + 1).trim());
                break;
            }
        }

        if (currentValue != null) {
            String v = currentValue.toLowerCase();
            if (v.equals(OPENGL)) {
                return Result.ALREADY_OPENGL; // nothing to do
            }
            if (v.equals(VULKAN)) {
                // Explicit user choice - respect it, but make the trade-off visible.
                LOGGER.warn("[Retromod] options.txt has preferredGraphicsBackend:\"vulkan\". "
                        + "Translated old mods may render incorrectly or crash on the Vulkan "
                        + "backend - set Graphics API to OpenGL in Video Settings if you hit "
                        + "rendering issues.");
                return Result.RESPECTED_VULKAN;
            }
            // "default" or anything else → switch to opengl, preserving the file.
            lines.set(idx, OPENGL_LINE);
        } else {
            lines.add(OPENGL_LINE);
        }
        Files.write(optionsTxt, lines, StandardCharsets.UTF_8);
        logSet(false);
        return Result.SET_OPENGL;
    }

    /** Strip one pair of surrounding double-quotes if present (options.txt enum values are JSON-quoted). */
    private static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static void logSet(boolean created) {
        LOGGER.info("[Retromod] Set {} in options.txt{} so translated mods' OpenGL rendering "
                + "works on MC 26.2+ (Vulkan is the new default). To use Vulkan, change "
                + "Graphics API in Video Settings (disable with -D{}=true).",
                OPENGL_LINE, created ? " (created)" : "", OPT_OUT_PROPERTY);
    }
}
