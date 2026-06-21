/*
 * Retromod - NeoForge mod-file locator for the Retromod-owned mods subfolder.
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.locator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Feeds jars from a Retromod-owned folder ({@code mods/Retromod/}) into NeoForge's
 * mod discovery, so Retromod itself and its transformed {@code *-retromod.jar}
 * outputs can live <em>outside</em> the top-level {@code mods/} folder.
 *
 * <p><b>Why this exists (#78).</b> CurseForge rejects modpack <em>exports</em> that
 * contain jars not hosted on CurseForge, and both Retromod (hosted on Modrinth) and
 * the mods it transforms are arbitrary jars. CurseForge <em>does</em> allow bundling
 * arbitrary files as pack "overrides". So a pack author puts those jars in
 * {@code mods/Retromod/} (an override directory, which CF export accepts) and this
 * locator hands them to the loader at discovery time - the
 * <a href="https://github.com/Sinytra/Connector">Sinytra Connector</a> model.
 *
 * <p><b>How NeoForge picks this up.</b> The class is registered in
 * {@code META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator}.
 * At startup FML's early-service discovery walks the {@code mods/} folder, finds any
 * jar declaring that service, loads it onto the early-service layer, and runs its
 * {@link #findCandidates} during mod discovery - <em>before</em> the module layer is
 * built, so the jars we add load like any other mod. The top-level
 * {@code ModsFolderLocator} scans {@code mods/} non-recursively, so it never sees the
 * {@code mods/Retromod/} subfolder on its own; this locator is what loads it.
 *
 * <p><b>Loader scope.</b> NeoForge only (loader 10.x/11.x → MC 1.21.0 through 26.2,
 * which share this SPI). Forge uses a different locator SPI and Fabric has no
 * third-party extra-folder service - both are tracked separately. Jars placed here
 * should already be loader-correct (e.g. AOT-transformed via {@code retromod batch},
 * or a native NeoForge mod like Retromod): this locator only <em>discovers</em>, it
 * does not transform.
 *
 * <p>Self-contained by design (JDK + SLF4J + the SPI only) so the same class can ship
 * in a minimal CurseForge "Retromod Loader" stub jar without dragging in the
 * transform engine.
 */
public final class RetromodModLocator implements IModFileCandidateLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Locator");

    /** Subfolder of {@code mods/} this locator scans. */
    public static final String SUBFOLDER = "Retromod";

    /**
     * Optional override: an absolute path to scan instead of {@code <gameDir>/mods/Retromod}.
     * Mainly for testing / unusual layouts.
     */
    public static final String OVERRIDE_PROPERTY = "retromod.modfolder";

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        final Path folder;
        try {
            folder = resolveModFolder(context.gameDirectory());
        } catch (RuntimeException e) {
            LOGGER.warn("[Retromod] could not resolve the Retromod mods folder; skipping", e);
            return;
        }

        // Create the folder if absent so users have an obvious place to drop jars.
        // Best-effort: a read-only game dir must not break discovery.
        if (!Files.isDirectory(folder)) {
            try {
                Files.createDirectories(folder);
                LOGGER.info("[Retromod] created mod folder {} (drop transformed jars here)", folder);
            } catch (IOException e) {
                LOGGER.debug("[Retromod] mod folder {} is absent and could not be created: {}", folder, e.toString());
            }
            return; // freshly created (or uncreatable) → nothing to load yet
        }

        List<Path> jars = listJars(folder);
        if (jars.isEmpty()) {
            LOGGER.debug("[Retromod] no jars in {}", folder);
            return;
        }

        int added = 0;
        for (Path jar : jars) {
            try {
                pipeline.addPath(jar, ModFileDiscoveryAttributes.DEFAULT,
                        IncompatibleFileReporting.WARN_ON_KNOWN_INCOMPATIBILITY);
                added++;
            } catch (RuntimeException e) {
                LOGGER.warn("[Retromod] failed to offer {} to mod discovery: {}", jar.getFileName(), e.toString());
            }
        }
        LOGGER.info("[Retromod] offered {} jar(s) from {} to mod discovery", added, folder);
    }

    // Priority is left at the SPI default: this folder never overlaps the top-level
    // mods/ folder, and the pipeline already dedups already-located jars, so order
    // relative to the default mods-folder locator carries no behavioural meaning.

    @Override
    public String toString() {
        return "retromod folder locator (" + SUBFOLDER + ")";
    }

    // ── helpers (package-private for host-independent unit testing) ──────────────

    /**
     * Resolve the folder to scan: the {@code retromod.modfolder} system property if
     * set (absolute path), otherwise {@code <gameDir>/mods/Retromod}.
     */
    static Path resolveModFolder(Path gameDirectory) {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return gameDirectory.resolve("mods").resolve(SUBFOLDER);
    }

    /**
     * List regular {@code *.jar} files directly in {@code folder} (non-recursive),
     * sorted for deterministic load order. Never throws - returns empty on error.
     */
    static List<Path> listJars(Path folder) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> entries = Files.list(folder)) {
            entries.filter(RetromodModLocator::isJar)
                   .sorted()
                   .forEach(out::add);
        } catch (IOException e) {
            LOGGER.warn("[Retromod] could not list {}: {}", folder, e.toString());
        }
        return out;
    }

    private static boolean isJar(Path p) {
        return Files.isRegularFile(p)
                && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }
}
