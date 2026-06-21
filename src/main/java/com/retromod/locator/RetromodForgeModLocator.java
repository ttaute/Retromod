/*
 * Retromod - Forge mod-file locator for the Retromod-owned mods subfolder.
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.locator;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

/**
 * Forge counterpart of {@link RetromodModLocator} (#78): discovers jars in
 * {@code mods/Retromod/} so Retromod (on Modrinth and CurseForge) and its transformed
 * {@code *-retromod.jar} outputs can ship as CurseForge pack overrides instead of
 * top-level {@code mods/} jars.
 *
 * <p><b>How Forge picks this up.</b> Registered in
 * {@code META-INF/services/net.minecraftforge.forgespi.locating.IModLocator}. Forge's
 * {@code ModDirTransformerDiscoverer} walks {@code mods/} for jars declaring that
 * service, puts them on the SERVICE module layer, and {@code ModDiscoverer} then runs
 * {@code initArguments} + {@code scanMods} on each - the same shape as NeoForge's
 * early-service scan, just a different SPI ({@code IModLocator.scanMods()} vs
 * NeoForge's {@code IModFileCandidateLocator.findCandidates}). Verified against Forge
 * forgespi 8.0.0 / fmlloader 26.2.
 *
 * <p><b>How it works.</b> Building a Forge {@code IModFile} from a jar path goes
 * through internal machinery (an internal {@code createMod}/{@code ModFileFactory}
 * path), so rather than reimplement it we reflectively delegate to Forge's own
 * {@code ModsFolderLocator(Path, String)} pointed at {@code mods/Retromod/} and return
 * its result. fmlloader ships as an automatic module (no {@code module-info}), so the
 * package-private constructor is reachable via {@code setAccessible}. The whole thing
 * is wrapped in a catch-all soft-fail: if Forge ever moves/renames that class (or
 * blocks reflection), the locator returns an empty list and Forge proceeds normally -
 * it can never break other mods.
 *
 * <p>Self-contained (JDK + SLF4J + the SPI only) so it can ride in the minimal
 * CurseForge "Retromod Loader" stub jar.
 */
public final class RetromodForgeModLocator implements IModLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Locator");

    /** Subfolder of {@code mods/} this locator scans. */
    public static final String SUBFOLDER = "Retromod";

    /** Optional absolute-path override (mainly for testing); see {@link RetromodModLocator}. */
    public static final String OVERRIDE_PROPERTY = "retromod.modfolder";

    @Override
    public List<?> scanMods() {
        final Path folder;
        try {
            folder = resolveModFolder();
        } catch (RuntimeException e) {
            LOGGER.warn("[Retromod] could not resolve the Retromod mods folder; skipping (Forge)", e);
            return Collections.emptyList();
        }
        if (folder == null || !Files.isDirectory(folder) || !hasJars(folder)) {
            return Collections.emptyList();
        }
        try {
            // Delegate to Forge's own directory locator so the jars become proper
            // IModFile instances built by Forge's internal createMod/ModFileFactory.
            Class<?> mfl = Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator");
            Constructor<?> ctor = mfl.getDeclaredConstructor(Path.class, String.class);
            ctor.setAccessible(true);
            Object inner = ctor.newInstance(folder, "Retromod");
            Object result = mfl.getMethod("scanMods").invoke(inner);
            List<?> list = (result instanceof List) ? (List<?>) result : Collections.emptyList();
            LOGGER.info("[Retromod] offered {} jar(s) from {} to Forge mod discovery", list.size(), folder);
            return list;
        } catch (Throwable t) {
            // Soft-fail: never break Forge discovery if the internal class moved or
            // reflection is blocked. mods/Retromod/ simply won't load on this Forge.
            LOGGER.warn("[Retromod] Forge mods/Retromod/ locator could not delegate to "
                    + "ModsFolderLocator ({}); those jars won't load on Forge", t.toString());
            return Collections.emptyList();
        }
    }

    /** {@code retromod.modfolder} override, else Forge's {@code FMLPaths.MODSDIR}/Retromod. */
    private static Path resolveModFolder() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        try {
            Class<?> fmlPaths = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object modsDir = fmlPaths.getField("MODSDIR").get(null);            // enum constant
            Path mods = (Path) fmlPaths.getMethod("get").invoke(modsDir);        // absolute path
            return mods.resolve(SUBFOLDER);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean hasJars(Path folder) {
        try (Stream<Path> s = Files.list(folder)) {
            return s.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"));
        } catch (Exception e) {
            return false;
        }
    }

    // ── IModProvider - the files we return belong to the inner ModsFolderLocator
    //    (their provider), so Forge calls these on it, not us; trivial impls suffice. ──

    @Override
    public String name() {
        return "retromod folder locator";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    @Override
    public boolean isValid(IModFile modFile) {
        return true;
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {
    }
}
