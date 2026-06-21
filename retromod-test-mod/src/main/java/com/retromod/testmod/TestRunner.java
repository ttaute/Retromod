/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod;

import com.retromod.testmod.tests.BasicTests;
import com.retromod.testmod.tests.BlockItemTests;
import com.retromod.testmod.tests.BlockTests;
import com.retromod.testmod.tests.CodecTests;
import com.retromod.testmod.tests.DeferredItemStackTests;
import com.retromod.testmod.tests.EnchantmentTests;
import com.retromod.testmod.tests.EntityTypeTests;
import com.retromod.testmod.tests.EnumTests;
import com.retromod.testmod.tests.GuiTests;
import com.retromod.testmod.tests.IdentifierTests;
import com.retromod.testmod.tests.ItemTests;
import com.retromod.testmod.tests.LoaderTests;
import com.retromod.testmod.tests.MathTests;
import com.retromod.testmod.tests.MiscApiTests;
import com.retromod.testmod.tests.NbtTests;
import com.retromod.testmod.tests.RegistryTests;
import com.retromod.testmod.tests.SoundParticleTests;
import com.retromod.testmod.tests.StatusEffectTests;
import com.retromod.testmod.tests.TagTests;
import com.retromod.testmod.tests.Test05SuperKeyPressed;
import com.retromod.testmod.tests.TextTests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs the test suite in three lifecycle phases so each test runs at a point
 * where the APIs it touches are actually ready.
 *
 * <p><b>Phases:</b>
 * <ul>
 *   <li><b>{@code init}</b> (during {@code onInitializeClient}) - most tests.
 *       Static registries (Blocks, Items, EntityType) and core API surfaces
 *       (Text, Identifier, NBT, math, codecs, fabric loader API) are all
 *       available. Run by {@link #runImmediate()}.</li>
 *   <li><b>{@code client-started}</b> (during {@code ClientLifecycleEvents.CLIENT_STARTED})
 *       - tests that touch the data-component registry. {@code new ItemStack(item, count)}
 *       in MC 1.20.5+ requires the data-component registry, which is
 *       bootstrapped after {@code onInitializeClient} but before the title
 *       screen renders. Run by {@link #runOnClientStarted()}.</li>
 *   <li><b>{@code world-join}</b> (during {@code ClientPlayConnectionEvents.JOIN})
 *       - tests that touch dynamic registries. In MC 1.21+ enchantments are
 *       data-driven and live in the dynamic registry; same for mob effects in
 *       1.21.6+. The dynamic registry isn't populated until the client connects
 *       to a server (or opens a single-player world). Run by
 *       {@link #runOnWorldJoin()}.</li>
 * </ul>
 *
 * <p>Each phase logs its own {@code RUN_ID} and summary so a single log file
 * with mixed phases is still grep-able by phase.
 */
public final class TestRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Test");
    private static final String PREFIX = "[Retromod-Test]";

    private static final List<Test> IMMEDIATE      = buildImmediateSuite();
    private static final List<Test> CLIENT_STARTED = buildClientStartedSuite();
    private static final List<Test> WORLD_JOIN     = buildWorldJoinSuite();

    private TestRunner() {}

    // =====================================================================
    // PHASE BUILDERS
    // =====================================================================

    private static List<Test> buildImmediateSuite() {
        List<Test> all = new ArrayList<>();
        all.addAll(BasicTests.all());
        all.addAll(TextTests.all());
        all.addAll(IdentifierTests.all());
        all.addAll(RegistryTests.all());
        all.addAll(BlockItemTests.all());
        all.addAll(BlockTests.all());
        all.addAll(ItemTests.all());
        all.addAll(EntityTypeTests.all());
        all.addAll(EnumTests.all());
        all.addAll(MathTests.all());
        all.addAll(NbtTests.all());
        all.addAll(SoundParticleTests.all());
        all.addAll(GuiTests.all());
        all.addAll(LoaderTests.all());
        all.addAll(CodecTests.all());
        all.addAll(TagTests.all());
        all.addAll(MiscApiTests.all());
        all.add(new Test05SuperKeyPressed());
        return List.copyOf(all);
    }

    private static List<Test> buildClientStartedSuite() {
        // Empty for now. CLIENT_STARTED was supposed to be the right phase
        // for ItemStack-construction tests, but in MC 26.1 the data-component
        // registry isn't frozen until a world actually loads - at
        // CLIENT_STARTED the registration loop is still mid-bootstrap and
        // `new ItemStack(item, count)` throws
        //   NullPointerException: Components not bound yet
        // ItemStack tests have been moved to WORLD_JOIN where the registry
        // is guaranteed to be ready.
        return List.of();
    }

    private static List<Test> buildWorldJoinSuite() {
        List<Test> all = new ArrayList<>();
        all.addAll(DeferredItemStackTests.all());
        all.addAll(EnchantmentTests.all());
        all.addAll(StatusEffectTests.all());
        return List.copyOf(all);
    }

    // =====================================================================
    // PHASE ENTRY POINTS - called by RetromodTestMod's lifecycle hooks
    // =====================================================================

    /** {@code onInitializeClient} entry point. Runs all init-phase tests. */
    public static void runImmediate() {
        runPhase("init", IMMEDIATE);
    }

    /** {@code ClientLifecycleEvents.CLIENT_STARTED} entry point. */
    public static void runOnClientStarted() {
        runPhase("client-started", CLIENT_STARTED);
    }

    /**
     * {@code ClientPlayConnectionEvents.JOIN} entry point. Idempotent -
     * if you re-join a world the tests just run again; nothing latches.
     */
    public static void runOnWorldJoin() {
        runPhase("world-join", WORLD_JOIN);
    }

    // =====================================================================
    // SHARED RUNNER
    // =====================================================================

    private static void runPhase(String phaseName, List<Test> tests) {
        if (tests.isEmpty()) return;

        String runId = UUID.randomUUID().toString().substring(0, 8);
        LOGGER.info("{} RUN_ID={} phase={} ({} tests)", PREFIX, runId, phaseName, tests.size());

        int passed = 0;
        int failed = 0;
        for (int i = 0; i < tests.size(); i++) {
            Test test = tests.get(i);
            int n = i + 1;
            TestResult result;
            try {
                result = test.run();
                if (result == null) {
                    result = TestResult.fail("test returned null");
                }
            } catch (Throwable t) {
                String msg = t.getMessage();
                result = TestResult.fail(t.getClass().getSimpleName()
                        + (msg != null ? ": " + msg : ""));
            }
            if (result.passed()) {
                passed++;
                LOGGER.info("{} [{}] {} ({}): success",
                        PREFIX, phaseName, n, test.description());
            } else {
                failed++;
                LOGGER.warn("{} [{}] {} ({}): fail: {}",
                        PREFIX, phaseName, n, test.description(), result.reason());
            }
        }

        LOGGER.info("{} [{}] SUMMARY: {}/{} passed{}",
                PREFIX, phaseName, passed, tests.size(),
                failed > 0 ? " (" + failed + " failed)" : "");
    }
}
