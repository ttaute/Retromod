/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import com.retromod.shim.common.Mc26_1To26_2CoreMoves;
import com.retromod.shim.fabric.Fabric_1_21_11_to_26_1;
import com.retromod.shim.fabric.Fabric_26_1_to_26_2;
import com.retromod.shim.forge.Forge_26_1_to_26_2;
import com.retromod.shim.neoforge.NeoForge_26_1_to_26_2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC 26.2 readiness: the 26.1→26.2 shims chain on every loader, the
 * pre/rc/snapshot version aliases resolve, and the core move list (harvested
 * from the real 26.1.2 / 26.2-rc-1 client jars) contains the verified moves -
 * and does NOT touch {@code client/gui/Gui}, which 26.2 split (HUD half went
 * to the new {@code Hud}) but did not remove.
 */
class Mc26_2ReadinessTest {

    @Test
    void chainsExistOnEveryLoader() {
        ShimRegistry reg = new ShimRegistry();
        reg.register(new Fabric_26_1_to_26_2());
        reg.register(new NeoForge_26_1_to_26_2());
        reg.register(new Forge_26_1_to_26_2());

        for (String loader : new String[]{"fabric", "neoforge", "forge"}) {
            List<VersionShim> chain = reg.findShimChain(loader, "26.1", "26.2");
            assertEquals(1, chain.size(), loader + " 26.1→26.2 chain");
            assertEquals("26.2", chain.get(0).getTargetVersion());
        }
    }

    @Test
    void chainFrom1_21_11ReachesRc1HostThrough26_1() {
        ShimRegistry reg = new ShimRegistry();
        reg.register(new Fabric_1_21_11_to_26_1());
        reg.register(new Fabric_26_1_to_26_2());

        // Host reports the raw rc version string - the alias must normalize it
        List<VersionShim> chain = reg.findShimChain("fabric", "1.21.11", "26.2-rc-1");
        assertEquals(2, chain.size());
        assertEquals("26.1", chain.get(0).getTargetVersion());
        assertEquals("26.2", chain.get(1).getTargetVersion());
    }

    @Test
    void versionAliasesNormalizeThe26_2Family() {
        assertEquals("26.2", ShimRegistry.resolveVersion("26.2-rc-1"));
        assertEquals("26.2", ShimRegistry.resolveVersion("26.2-rc.1"));
        assertEquals("26.2", ShimRegistry.resolveVersion("26.2-pre-6"));
        assertEquals("26.2", ShimRegistry.resolveVersion("26.2 Release Candidate 1"));
        assertEquals("26.2", ShimRegistry.resolveVersion("26.2.1"));
        assertEquals("26.2", ShimRegistry.resolveVersion("26.2"));
    }

    @Test
    void coreMovesContainVerifiedRedirectsAndSpareGui() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        try {
            Mc26_1To26_2CoreMoves.register(t);
            Map<String, String> r = t.getClassRedirects();

            // advancements split - the bulk of 26.2
            assertEquals("net/minecraft/advancements/triggers/CriteriaTriggers",
                    r.get("net/minecraft/advancements/CriteriaTriggers"));
            assertEquals("net/minecraft/advancements/predicates/entity/EntityPredicate",
                    r.get("net/minecraft/advancements/criterion/EntityPredicate"));
            // cubemob refactor keeps Slime's identity (NOT the extracted base)
            assertEquals("net/minecraft/world/entity/monster/cubemob/Slime",
                    r.get("net/minecraft/world/entity/monster/Slime"));
            assertEquals("net/minecraft/world/entity/monster/cubemob/AbstractCubeMob$CubeMobMoveControl",
                    r.get("net/minecraft/world/entity/monster/Slime$SlimeMoveControl"));
            // contextualbar Renderer-suffix drop
            assertEquals("net/minecraft/client/gui/contextualbar/ExperienceBar",
                    r.get("net/minecraft/client/gui/contextualbar/ExperienceBarRenderer"));

            // Gui was SPLIT, not renamed - a redirect would hijack the live class
            assertFalse(r.containsKey("net/minecraft/client/gui/Gui"),
                    "Gui survives in 26.2; it must never be redirected");

            assertTrue(r.size() >= 180, "expected the full harvested move set, got " + r.size());

            // 26.2 extracted the registry constants into EntityTypes /
            // BlockEntityTypes holder classes - caught live on 26.2-rc-1
            // (27 test-mod failures, every EntityType.X a NoSuchFieldError)
            var zombie = t.getFieldRedirects().get(
                    new RetromodTransformer.FieldKey("net/minecraft/world/entity/EntityType", "ZOMBIE"));
            assertEquals("net/minecraft/world/entity/EntityTypes", zombie.owner());
            var chest = t.getFieldRedirects().get(
                    new RetromodTransformer.FieldKey("net/minecraft/world/level/block/entity/BlockEntityType", "CHEST"));
            assertEquals("net/minecraft/world/level/block/entity/BlockEntityTypes", chest.owner());
        } finally {
            t.clearRedirectsForTesting();
        }
    }
}
