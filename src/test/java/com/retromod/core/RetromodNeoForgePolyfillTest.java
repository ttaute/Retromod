/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring + loader-safety coverage for {@link RetromodNeoForge#registerPolyfills}.
 *
 * <p>Until this was added, only the Fabric entry loaded the
 * {@link com.retromod.polyfill.PolyfillProvider}s, so the NeoForge in-place runtime
 * transform silently applied none. This pins the contract of the NeoForge wiring:
 * <ul>
 *   <li>the Forge -> NeoForge migration redirects (the representative {@code Dist}
 *       relocation) ARE registered;</li>
 *   <li>the {@code neoforge}-category transfer polyfills, which conflict with the
 *       host-gated {@code NeoForge_1_21_8_to_1_21_9} shim and crash on a pre-1.21.9
 *       host, are NOT registered;</li>
 *   <li>removed-class polyfills self-gate on the host version;</li>
 *   <li>the {@code polyfills_enabled=false} flag registers nothing.</li>
 * </ul>
 */
class RetromodNeoForgePolyfillTest {

    private static final String FORGE_DIST = "net/minecraftforge/api/distmarker/Dist";
    private static final String NEOFORGE_DIST = "net/neoforged/api/distmarker/Dist";
    private static final String IITEMHANDLER = "net/neoforged/neoforge/items/IItemHandler";
    private static final String ITEMSTACKHANDLER = "net/neoforged/neoforge/items/ItemStackHandler";
    private static final String DIRECTION_PROPERTY =
            "net/minecraft/world/level/block/state/properties/DirectionProperty";
    private static final String ENUM_PROPERTY =
            "net/minecraft/world/level/block/state/properties/EnumProperty";

    private final RetromodTransformer t = RetromodTransformer.getInstance();
    private String savedVersion;

    @BeforeEach
    void setUp() {
        savedVersion = RetromodVersion.TARGET_MC_VERSION;
        t.clearRedirectsForTesting();
    }

    @AfterEach
    void tearDown() {
        RetromodVersion.TARGET_MC_VERSION = savedVersion;
        t.clearRedirectsForTesting();
    }

    @Test
    @DisplayName("NeoForge entry registers the Forge -> NeoForge Dist redirect (representative)")
    void registersForgeToNeoForgeDistRedirect() {
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodNeoForge.registerPolyfills(t, true);

        assertEquals(NEOFORGE_DIST, t.getClassRedirects().get(FORGE_DIST),
                "the NeoForge entry must register the Forge -> NeoForge Dist relocation "
                        + "(AnnotationPolyfill); this is the acute DistExecutor case generalized");
    }

    @Test
    @DisplayName("neoforge-category transfer polyfills are NOT registered (the shim owns them)")
    void doesNotRegisterConflictingTransferPolyfills() {
        // Even on a host where IItemHandler is gone, the runtime path must leave the
        // transfer transition to the host-gated NeoForge_1_21_8_to_1_21_9 shim (#9):
        // the polyfill's class redirect conflicts with the shim's member redirects, and
        // un-gated it would NoClassDefFoundError on a pre-1.21.9 host.
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodNeoForge.registerPolyfills(t, true);

        assertFalse(t.getClassRedirects().containsKey(IITEMHANDLER),
                "IItemHandler -> ResourceHandler must NOT be registered on the NeoForge runtime");
        assertFalse(t.getClassRedirects().containsKey(ITEMSTACKHANDLER),
                "ItemStackHandler must not be hijacked on the NeoForge runtime");
    }

    @Test
    @DisplayName("Removed-class polyfills are host-gated: off pre-26.1, on at 26.1+")
    void removedClassPolyfillsAreHostGated() {
        // Pre-26.1 host: DirectionProperty still exists, so it must not be redirected.
        RetromodVersion.TARGET_MC_VERSION = "1.21.1";
        RetromodNeoForge.registerPolyfills(t, true);
        assertFalse(t.getClassRedirects().containsKey(DIRECTION_PROPERTY),
                "DirectionProperty exists pre-26.1; redirecting it would hijack live code");

        // 26.1 host: removed, so it redirects to its surviving superclass.
        t.clearRedirectsForTesting();
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodNeoForge.registerPolyfills(t, true);
        assertEquals(ENUM_PROPERTY, t.getClassRedirects().get(DIRECTION_PROPERTY),
                "DirectionProperty must bridge to EnumProperty on 26.1+");
    }

    @Test
    @DisplayName("Old-name redirects are additive/dead; live Mojang classes are never redirect sources")
    void onlyAbsentNamesAreRedirectSources() {
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodNeoForge.registerPolyfills(t, true);

        // Old MCP names (BlockPolyfill) DO register, but the source is a name absent from
        // any modern NeoForge mod, so the redirect is harmless dead weight, never a misfire.
        assertEquals("net/minecraft/world/level/block/AnvilBlock",
                t.getClassRedirects().get("net/minecraft/block/BlockAnvil"),
                "old MCP-named redirects still register (additive); they key on absent names");

        // The safety invariant that matters on a Mojang-named runtime: a live, never-removed
        // core class is never a class-redirect SOURCE, so it can't be hijacked.
        assertFalse(t.getClassRedirects().containsKey("net/minecraft/world/item/ItemStack"),
                "a live core Mojang class must never be a class-redirect source");
        assertFalse(t.getClassRedirects().containsKey("net/minecraft/resources/ResourceLocation"),
                "a live core Mojang class must never be a class-redirect source");
    }

    @Test
    @DisplayName("polyfills_enabled=false registers nothing")
    void disabledFlagRegistersNothing() {
        RetromodVersion.TARGET_MC_VERSION = "26.1";
        RetromodNeoForge.registerPolyfills(t, false);

        assertFalse(t.getClassRedirects().containsKey(FORGE_DIST),
                "with polyfills disabled, not even the Dist redirect should register");
        assertTrue(t.getClassRedirects().isEmpty(),
                "polyfills disabled => no class redirects registered");
    }
}
