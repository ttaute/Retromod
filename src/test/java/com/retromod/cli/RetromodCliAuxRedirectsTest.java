package com.retromod.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.retromod.core.RetromodTransformer;
import com.retromod.embedder.ModVersionInfo;

/**
 * Regression for the {@code batch}/{@code AOT} aux-redirects gap and its loader gating.
 *
 * <p>Background: {@code batch} (and {@code AotCompiler}) only registered the version-shim
 * chain, so AOT-prepped 26.x mods kept pre-26.x class names (e.g. {@code EndDragonFight}
 * instead of {@code EnderDragonFight}) and a 1.21.x mod's mixin {@code @Shadow}/{@code @Inject}
 * failed to apply. {@link RetromodCli#registerAuxiliaryRedirects} now layers the vanilla
 * class-move table + (Fabric-only) member mappings, matching the single-mod {@code transform}
 * and an in-game boot.
 *
 * <p>The critical gate: vanilla class moves apply to EVERY loader, but the Fabric
 * intermediary-&gt;Mojang MEMBER mappings are Fabric-only. Applying them to a NeoForge mod
 * (already Mojang-named) clobbered correct Mojang fields: it renamed YUNG's API's
 * {@code Blocks.WHITE_CANDLE} to a field 26.2 no longer has, crashing construction.
 */
class RetromodCliAuxRedirectsTest {

    private static ModVersionInfo info(String loader) {
        return new ModVersionInfo("testmod", "1.0.0", "1.21.4", loader, "1.0.0",
                Set.of(), Set.of(), false);
    }

    /** The summary ends with "... N member mapping(s)." Extract N. */
    private static int memberCount(String summary) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d+) member mapping").matcher(summary);
        assertTrue(m.find(), "summary should report a member-mapping count: " + summary);
        return Integer.parseInt(m.group(1));
    }

    private static int classMoveCount(String summary) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(\\d+) class move").matcher(summary);
        assertTrue(m.find(), "summary should report a class-move count: " + summary);
        return Integer.parseInt(m.group(1));
    }

    @Test
    void classMovesApplyToEveryLoaderButMemberMappingsAreFabricOnly() {
        RetromodTransformer transformer = RetromodTransformer.getInstance();

        String neoforge = RetromodCli.registerAuxiliaryRedirects(transformer, info("neoforge"), List.of());
        String forge = RetromodCli.registerAuxiliaryRedirects(transformer, info("forge"), List.of());
        String fabric = RetromodCli.registerAuxiliaryRedirects(transformer, info("fabric"), List.of());

        assertNotNull(neoforge, "26.1 target must register at least the class-move table");
        assertNotNull(forge);
        assertNotNull(fabric);

        // Class moves (vanilla Mojang->Mojang relocations) apply on every loader.
        assertTrue(classMoveCount(neoforge) > 0, "NeoForge must get the vanilla class moves");
        assertTrue(classMoveCount(fabric) > 0, "Fabric must get the vanilla class moves");

        // Member mappings (intermediary->Mojang) are Fabric-only: 0 on NeoForge/Forge,
        // >0 on Fabric. This is the WHITE_CANDLE clobber guard.
        assertTrue(memberCount(neoforge) == 0, "NeoForge must NOT get Fabric member mappings");
        assertTrue(memberCount(forge) == 0, "Forge must NOT get Fabric member mappings");
        assertFalse(memberCount(fabric) == 0, "Fabric MUST get the intermediary member mappings");
    }
}
