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
 * On a 26.2+ client (where MC ships both OpenGL and Vulkan and defaults to Vulkan),
 * select the OpenGL backend so old mods that issue raw GL calls keep rendering;
 * translating arbitrary GL to Vulkan in bytecode isn't feasible. Mods on MC's
 * backend-agnostic render API run on Vulkan regardless.
 *
 * <p>Acts only on a client at host >= 26.2, and only when the user hasn't chosen a
 * backend (key absent or {@code default}). An explicit {@code vulkan} choice is left
 * alone with a warning; opt out with {@code -Dretromod.graphics.noPreference=true}.
 * Other {@code options.txt} lines are preserved.
 *
 * <p>Once OpenGL is removed (26.3), there's no backend to fall back to and raw-GL
 * mods hit the hard boundary; this is a 26.2-window measure.
 */
public final class GraphicsBackendCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /** {@code -Dretromod.graphics.noPreference=true} disables this entirely. */
    public static final String OPT_OUT_PROPERTY = "retromod.graphics.noPreference";

    private static final String OPTIONS_FILE = "options.txt";

    // The persisted options.txt key (verified against 26.2 version 4903). `graphicsApi`
    // is only the in-game label's translation key, not the stored one. Value is a
    // JSON-quoted enum name: "opengl" / "vulkan" / "default".
    private static final String KEY = "preferredGraphicsBackend";
    private static final String OPENGL = "opengl";
    private static final String VULKAN = "vulkan";
    private static final String OPENGL_LINE = KEY + ":\"" + OPENGL + "\"";

    private GraphicsBackendCompat() {}

    /** Outcome of {@link #ensureOpenGlForOldMods}, for logging and tests. */
    public enum Result {
        SET_OPENGL,
        ALREADY_OPENGL,
        RESPECTED_VULKAN,
        SKIPPED_OLD_HOST,
        SKIPPED_NOT_CLIENT,
        SKIPPED_OPT_OUT,
        IO_ERROR
    }

    /**
     * Select the OpenGL backend for old mods on a 26.2+ client. Self-gating, so it's
     * safe to call unconditionally from an entry point.
     *
     * @param gameDir        the Minecraft game directory (contains options.txt)
     * @param hostMcVersion  the detected host MC version
     * @return what it did
     */
    public static Result ensureOpenGlForOldMods(Path gameDir, String hostMcVersion) {
        if (Boolean.getBoolean(OPT_OUT_PROPERTY)) {
            return Result.SKIPPED_OPT_OUT;
        }
        if (!EnvironmentDetector.isClient()) {
            return Result.SKIPPED_NOT_CLIENT;
        }
        // mcVersionExceeds("26.2", host) is true when host < 26.2.
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

    /** options.txt read/modify/write; package-private for tests. */
    static Result apply(Path optionsTxt) throws IOException {
        // On a fresh instance options.txt doesn't exist yet (MC creates it after our
        // pre-launch runs). Pre-create it with just our key; MC fills in the rest on
        // first save, so the very first launch comes up on OpenGL.
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
                currentValue = unquote(line.substring(colon + 1).trim());
                break;
            }
        }

        if (currentValue != null) {
            String v = currentValue.toLowerCase();
            if (v.equals(OPENGL)) {
                return Result.ALREADY_OPENGL;
            }
            if (v.equals(VULKAN)) {
                // Explicit user choice: keep it, but make the trade-off visible.
                LOGGER.warn("[Retromod] options.txt has preferredGraphicsBackend:\"vulkan\". "
                        + "Translated old mods may render incorrectly or crash on the Vulkan "
                        + "backend - set Graphics API to OpenGL in Video Settings if you hit "
                        + "rendering issues.");
                return Result.RESPECTED_VULKAN;
            }
            // "default" or anything else: switch to opengl, preserving the file.
            lines.set(idx, OPENGL_LINE);
        } else {
            lines.add(OPENGL_LINE);
        }
        Files.write(optionsTxt, lines, StandardCharsets.UTF_8);
        logSet(false);
        return Result.SET_OPENGL;
    }

    /** Strip one surrounding pair of double-quotes if present. */
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
