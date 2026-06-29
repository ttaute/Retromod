/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * Mod-author opt-out mechanism.
 *
 * <p>A mod author can request that Retromod not transform their JAR by shipping
 * an empty marker file at {@code META-INF/retromod-opt-out} (add it under
 * {@code src/main/resources/}). When present, the JAR passes through untouched
 * to {@code mods/}. Only the file's presence matters; its contents are ignored.</p>
 *
 * <p>A user can force-transform an opted-out mod with
 * {@code -Dretromod.honorOptOut=false}. Default is {@code true} (opt-out honored).</p>
 */
public final class OptOutCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-OptOut");

    /** Marker path inside a mod JAR; presence is the opt-out signal. */
    public static final String OPT_OUT_MARKER = "META-INF/retromod-opt-out";

    /** Set by {@code -Dretromod.honorOptOut=false} to force-transform opted-out mods. */
    private static final boolean IGNORE_OPT_OUT =
            !Boolean.parseBoolean(System.getProperty("retromod.honorOptOut", "true"));

    private OptOutCheck() {}

    /**
     * @return {@code true} if the JAR contains the opt-out marker AND the user
     *         hasn't disabled marker-honoring. Caller should skip transformation
     *         when this returns true.
     */
    public static boolean isOptedOut(Path modJar) {
        if (IGNORE_OPT_OUT) return false;
        if (modJar == null) return false;
        try (JarFile jar = new JarFile(modJar.toFile())) {
            return jar.getEntry(OPT_OUT_MARKER) != null;
        } catch (IOException e) {
            // can't open the JAR; let the regular transform path surface the error
            LOGGER.debug("Could not read {} to check opt-out: {}", modJar, e.getMessage());
            return false;
        }
    }

    /** Log a consistent skip message; call from each entry point. */
    public static void logSkipped(Path modJar) {
        LOGGER.info("Skipping {} - mod author opted out via {} marker. " +
                "Override with -Dretromod.honorOptOut=false if you want to force transform.",
                modJar.getFileName(), OPT_OUT_MARKER);
    }
}
