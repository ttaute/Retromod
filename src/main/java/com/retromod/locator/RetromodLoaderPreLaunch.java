/*
 * Retromod - Fabric pre-launch for the standalone CurseForge "Retromod Loader" stub.
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.locator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fabric pre-launch entry point for the standalone CurseForge "Retromod Loader" stub
 * jar (#78).
 *
 * <p><b>What the stub is.</b> CurseForge rejects modpack <em>exports</em> that contain
 * jars not hosted on CurseForge - and Retromod (on Modrinth and CurseForge) and the mods it
 * transforms are exactly that. The stub is a tiny CF-hosted jar that is the <em>only</em>
 * CF-project jar a pack places in {@code mods/}; the real Retromod and the transformed
 * {@code *-retromod.jar} outputs ride in {@code mods/Retromod/}, shipped as pack
 * overrides (arbitrary bundled files, which CF export allows).
 *
 * <p><b>What this class does (Fabric only).</b> Fabric has no mod-file locator SPI and
 * pre-launch runs <em>after</em> Knot's mod scan, so jars in {@code mods/Retromod/}
 * can't be loaded in place. Instead this moves the loader-ready jars from
 * {@code mods/Retromod/} into {@code mods/} and relies on a one-time restart - after
 * which the real Retromod (now in {@code mods/}) takes over. This mirrors
 * {@code RetromodPreLaunch.drainReadyModsFolder}, but is duplicated here so the stub
 * jar stays self-contained (JDK + SLF4J + Fabric API only - no transform engine).
 *
 * <p>On NeoForge the stub uses {@link RetromodModLocator} instead, which loads
 * {@code mods/Retromod/} in place with no restart. Both ship in the one stub jar.
 */
public final class RetromodLoaderPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Loader");
    private static final String SUBFOLDER = "Retromod";

    @Override
    public void onPreLaunch() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir == null) {
                gameDir = Path.of(".");
            }
            Path mods = gameDir.resolve("mods");
            Path folder = mods.resolve(SUBFOLDER);
            if (!Files.isDirectory(folder)) {
                return;
            }
            // If -Dfabric.addMods already points here, Fabric loaded the jars in place
            // this launch - don't move loaded files.
            if (addModsCovers(gameDir, folder)) {
                LOGGER.info("[Retromod-Loader] mods/Retromod/ is on -Dfabric.addMods - loaded in place; nothing to drain");
                return;
            }
            List<Path> jars = listJars(folder);
            if (jars.isEmpty()) {
                return;
            }
            int moved = 0;
            for (Path jar : jars) {
                try {
                    Files.move(jar, mods.resolve(jar.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                    moved++;
                } catch (IOException e) {
                    LOGGER.error("[Retromod-Loader] could not move {}: {}", jar.getFileName(), e.toString());
                }
            }
            if (moved > 0) {
                LOGGER.info("[Retromod-Loader] moved {} jar(s) from mods/Retromod/ into mods/ - "
                        + "RESTART Minecraft once for them to load", moved);
            }
        } catch (Exception e) {
            LOGGER.error("[Retromod-Loader] pre-launch error: {}", e.toString());
        }
    }

    private static boolean addModsCovers(Path gameDir, Path folder) {
        String prop = System.getProperty("fabric.addMods");
        if (prop == null || prop.isBlank()) {
            return false;
        }
        Path target;
        try {
            target = folder.toAbsolutePath().normalize();
        } catch (Exception e) {
            return false;
        }
        for (String entry : prop.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path p = Path.of(entry.trim());
                if (!p.isAbsolute()) {
                    p = gameDir.resolve(p);
                }
                if (p.toAbsolutePath().normalize().equals(target)) {
                    return true;
                }
            } catch (Exception ignored) {
                // malformed entry - ignore
            }
        }
        return false;
    }

    private static List<Path> listJars(Path folder) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder)) {
            s.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
             .sorted()
             .forEach(out::add);
        } catch (IOException e) {
            LOGGER.error("[Retromod-Loader] could not list {}: {}", folder, e.toString());
        }
        return out;
    }
}
