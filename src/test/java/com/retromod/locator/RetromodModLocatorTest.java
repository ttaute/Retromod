package com.retromod.locator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

/**
 * Host-independent coverage for {@link RetromodModLocator} (#78). The NeoForge SPI
 * is present here via the compile-time stubs (they're stripped only from the shipped
 * jar, not from {@code target/classes}), so we drive {@code findCandidates} with fake
 * implementations and assert exactly which jars get offered to discovery.
 */
class RetromodModLocatorTest {

    /** Fake pipeline that records every offered path. */
    private static final class RecordingPipeline implements IDiscoveryPipeline {
        final List<Path> offered = new ArrayList<>();

        @Override
        public Optional<?> addPath(Path path, ModFileDiscoveryAttributes attributes,
                                   IncompatibleFileReporting reporting) {
            offered.add(path);
            return Optional.empty();
        }
    }

    /** ILaunchContext is single-method, so a lambda suffices. */
    private static ILaunchContext ctx(Path gameDir) {
        return () -> gameDir;
    }

    @Test
    void offersExactlyTheTopLevelJarsInTheSubfolderSorted(@TempDir Path gameDir) throws IOException {
        Path folder = Files.createDirectories(gameDir.resolve("mods").resolve(RetromodModLocator.SUBFOLDER));
        Path beta = Files.createFile(folder.resolve("beta.jar"));
        Path alpha = Files.createFile(folder.resolve("alpha.jar"));
        Files.createFile(folder.resolve("notes.txt"));        // not a jar → ignored
        Files.createFile(folder.resolve("pack.zip"));         // not a jar → ignored
        Path nested = Files.createDirectory(folder.resolve("nested"));
        Files.createFile(nested.resolve("deep.jar"));         // non-recursive → ignored
        // a jar in the top-level mods/ folder is the default locator's job, not ours
        Files.createFile(gameDir.resolve("mods").resolve("other.jar"));

        RecordingPipeline pipeline = new RecordingPipeline();
        new RetromodModLocator().findCandidates(ctx(gameDir), pipeline);

        assertEquals(List.of(alpha, beta), pipeline.offered,
                "only the top-level *.jar files in mods/Retromod, sorted");
    }

    @Test
    void absentFolderIsCreatedAndNothingIsOffered(@TempDir Path gameDir) {
        RecordingPipeline pipeline = new RecordingPipeline();

        assertDoesNotThrow(() -> new RetromodModLocator().findCandidates(ctx(gameDir), pipeline));

        assertTrue(pipeline.offered.isEmpty(), "no jars to offer from a fresh folder");
        assertTrue(Files.isDirectory(gameDir.resolve("mods").resolve(RetromodModLocator.SUBFOLDER)),
                "the folder should be created so users have somewhere to drop jars");
    }

    @Test
    void listJarsIsFlatAndSorted(@TempDir Path folder) throws IOException {
        Files.createFile(folder.resolve("zeta.jar"));
        Files.createFile(folder.resolve("alpha.jar"));
        Files.createFile(folder.resolve("ignore.zip"));

        assertEquals(List.of(folder.resolve("alpha.jar"), folder.resolve("zeta.jar")),
                RetromodModLocator.listJars(folder));
    }

    @Test
    void systemPropertyOverridesTheFolder(@TempDir Path root) throws IOException {
        Path custom = Files.createDirectories(root.resolve("custom-location"));
        Path jar = Files.createFile(custom.resolve("custom.jar"));

        String prev = System.getProperty(RetromodModLocator.OVERRIDE_PROPERTY);
        System.setProperty(RetromodModLocator.OVERRIDE_PROPERTY, custom.toString());
        try {
            RecordingPipeline pipeline = new RecordingPipeline();
            // gameDir's own mods/Retromod is never consulted when the override is set
            new RetromodModLocator().findCandidates(ctx(root), pipeline);
            assertEquals(List.of(jar), pipeline.offered);
        } finally {
            if (prev == null) {
                System.clearProperty(RetromodModLocator.OVERRIDE_PROPERTY);
            } else {
                System.setProperty(RetromodModLocator.OVERRIDE_PROPERTY, prev);
            }
        }
    }
}
