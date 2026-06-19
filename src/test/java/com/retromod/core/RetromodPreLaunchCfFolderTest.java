package com.retromod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage for the Fabric side of the CurseForge-export folder (#78): the
 * {@code mods/Retromod/} drain ({@link RetromodPreLaunch#drainReadyModsFolder})
 * and the {@code -Dfabric.addMods} guard
 * ({@link RetromodPreLaunch#fabricAddModsCovers}) that keeps option #1 (drain +
 * restart) and option #2 (load in-place via JVM arg) from colliding.
 */
class RetromodPreLaunchCfFolderTest {

    @Test
    void drainMovesOnlyJarsIntoModsFolder(@TempDir Path gameDir) throws IOException {
        Path retromod = Files.createDirectories(gameDir.resolve("mods").resolve("Retromod"));
        Path mods = gameDir.resolve("mods");
        Files.createFile(retromod.resolve("alpha.jar"));
        Files.createFile(retromod.resolve("beta.jar"));
        Files.createFile(retromod.resolve("README.txt"));   // not a jar → left in place

        int moved = RetromodPreLaunch.drainReadyModsFolder(retromod, mods);

        assertEquals(2, moved, "both jars should be moved");
        assertTrue(Files.exists(mods.resolve("alpha.jar")), "alpha.jar landed in mods/");
        assertTrue(Files.exists(mods.resolve("beta.jar")), "beta.jar landed in mods/");
        assertFalse(Files.exists(retromod.resolve("alpha.jar")), "alpha.jar moved out of the subfolder");
        assertFalse(Files.exists(retromod.resolve("beta.jar")), "beta.jar moved out of the subfolder");
        assertTrue(Files.exists(retromod.resolve("README.txt")), "non-jar files are left alone");
    }

    @Test
    void drainOnAbsentFolderIsANoOp(@TempDir Path gameDir) {
        Path absent = gameDir.resolve("mods").resolve("Retromod");
        assertEquals(0, RetromodPreLaunch.drainReadyModsFolder(absent, gameDir.resolve("mods")));
    }

    @Test
    void addModsGuardMatchesRelativeAndAbsolutePaths(@TempDir Path gameDir) {
        Path folder = gameDir.resolve("mods").resolve("Retromod");
        String prev = System.getProperty("fabric.addMods");
        try {
            System.clearProperty("fabric.addMods");
            assertFalse(RetromodPreLaunch.fabricAddModsCovers(gameDir, folder),
                "unset property → not covered");

            // relative entry, resolved against the game dir
            System.setProperty("fabric.addMods", "mods/Retromod");
            assertTrue(RetromodPreLaunch.fabricAddModsCovers(gameDir, folder),
                "relative mods/Retromod resolves to the folder");

            // absolute entry
            System.setProperty("fabric.addMods", folder.toAbsolutePath().toString());
            assertTrue(RetromodPreLaunch.fabricAddModsCovers(gameDir, folder),
                "absolute path matches");

            // a different folder, among multiple entries
            System.setProperty("fabric.addMods",
                gameDir.resolve("somewhere-else").toString() + java.io.File.pathSeparator + "mods/other");
            assertFalse(RetromodPreLaunch.fabricAddModsCovers(gameDir, folder),
                "unrelated paths → not covered");
        } finally {
            if (prev == null) {
                System.clearProperty("fabric.addMods");
            } else {
                System.setProperty("fabric.addMods", prev);
            }
        }
    }
}
