/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Fabric {@code FabricBlockSettings} bridge — the single most-referenced removed
 * Fabric API class in the compat audit (~880 refs across the top mods).
 *
 * <h2>What it was</h2>
 * {@code net/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings}
 * was Fabric's block-properties builder. It <b>extended</b> vanilla
 * {@code BlockBehaviour.Properties} ({@code class_4970$class_2251}) and overrode
 * every builder method to return {@code FabricBlockSettings} for fluent chaining,
 * plus added Yarn-named aliases ({@code hardness}, {@code resistance},
 * {@code sounds}, {@code luminance}, …). Fabric removed it (deprecated 1.20.5,
 * gone by 26.1); the migration target is {@code Properties} directly.
 *
 * <h2>Bridge strategy</h2>
 * Because {@code FabricBlockSettings} <i>is</i> a {@code Properties} subclass, a
 * single <b>class redirect</b> {@code FabricBlockSettings → Properties} does most
 * of the work: the type resolves (fixing the {@code NoClassDefFoundError} that's
 * the actual load-blocker), and every builder call whose name already matches
 * {@code Properties} ({@code strength}, {@code mapColor}, {@code noCollision},
 * {@code lightLevel(ToIntFunction)}, {@code instrument}, {@code replaceable}, …)
 * works for free. A synthetic subclass is impossible here — {@code Properties}'
 * only constructor is private, with a static {@code of()} factory.
 *
 * <p>The remaining breakage is the <b>renamed</b> builders (Yarn→Mojang). For the
 * ones whose descriptor has no Minecraft-typed parameter (most of the common
 * ones), a plain method redirect keyed on the post-class-redirect owner
 * ({@code Properties}) suffices and matches identically in both the CLI/audit
 * path and the in-game runtime. For the few with an MC-typed parameter
 * ({@code sounds(SoundType)}, {@code materialColor(MapColor/DyeColor)}) we
 * register BOTH the intermediary and the Mojang descriptor variant, because the
 * CLI doesn't run the intermediary→Mojang member remap that the runtime does, so
 * the parameter type differs between the two paths.
 *
 * <h2>Known follow-up (Phase 2)</h2>
 * Signature-changing builders — {@code luminance(int)} / {@code lightLevel(int)}
 * → {@code lightLevel(ToIntFunction)} (needs a constant-function adapter), and
 * the predicate-typed ones ({@code allowsSpawning}, {@code solidBlock}, …) — are
 * not yet bridged; a block that calls those still hits a {@code NoSuchMethodError}
 * at that call. The common property set (hardness/resistance/strength/sounds/
 * requiresTool/breakInstantly/…) is covered, which gets the large majority of
 * content mods' blocks constructing.
 */
public class FabricBlockSettingsShim implements VersionShim {

    private static final String FBS = "net/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings";
    // Pre-0.50 Fabric API (≈1.16) shipped FabricBlockSettings at a shorter path
    // before the object-builder-v1 module existed. A handful of very old mods
    // still reference it — redirect the type the same way (the builder methods
    // resolve via the shared method redirects, which are owner=Properties).
    private static final String FBS_OLD = "net/fabricmc/fabric/api/block/FabricBlockSettings";
    private static final String PROPS = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
    private static final String L_PROPS = "L" + PROPS + ";";

    // MC param types — intermediary (what the CLI/audit sees) + Mojang (runtime).
    private static final String SOUND_INT = "Lnet/minecraft/class_2498;";
    private static final String SOUND_MOJ = "Lnet/minecraft/world/level/block/SoundType;";
    private static final String MAPCOLOR_INT = "Lnet/minecraft/class_3620;";
    private static final String MAPCOLOR_MOJ = "Lnet/minecraft/world/level/material/MapColor;";
    private static final String DYE_INT = "Lnet/minecraft/class_1767;";
    private static final String DYE_MOJ = "Lnet/minecraft/world/item/DyeColor;";

    @Override public String getShimName() { return "Fabric BlockSettings → BlockBehaviour.Properties"; }
    @Override public String getSourceVersion() { return "0.50.0"; }
    @Override public String getTargetVersion() { return "0.100.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // The type itself → Properties (resolves the class + name-matching methods).
        transformer.registerClassRedirect(FBS, PROPS);
        transformer.registerClassRedirect(FBS_OLD, PROPS); // pre-0.50 path

        // Static factory: create() → of(). (of() already matches Properties.of().)
        renameStatic(transformer, "create", "()" + L_PROPS, "of");

        // --- Renamed instance builders, no MC-typed parameter (CLI == runtime) ---
        rename(transformer, "hardness", "(F)" + L_PROPS, "destroyTime");
        rename(transformer, "resistance", "(F)" + L_PROPS, "explosionResistance");
        rename(transformer, "breakInstantly", "()" + L_PROPS, "instabreak");
        rename(transformer, "ticksRandomly", "()" + L_PROPS, "randomTicks");
        rename(transformer, "requiresTool", "()" + L_PROPS, "requiresCorrectToolForDrops");
        rename(transformer, "nonOpaque", "()" + L_PROPS, "noOcclusion");
        rename(transformer, "slipperiness", "(F)" + L_PROPS, "friction");
        rename(transformer, "velocityMultiplier", "(F)" + L_PROPS, "speedFactor");
        rename(transformer, "jumpVelocityMultiplier", "(F)" + L_PROPS, "jumpFactor");
        rename(transformer, "dropsNothing", "()" + L_PROPS, "noLootTable");
        rename(transformer, "dynamicBounds", "()" + L_PROPS, "dynamicShape");
        rename(transformer, "noBlockBreakParticles", "()" + L_PROPS, "noTerrainParticles");

        // --- Renamed instance builders WITH an MC-typed parameter ---
        // sounds(SoundType) → sound(SoundType); register both descriptor variants.
        rename(transformer, "sounds", "(" + SOUND_INT + ")" + L_PROPS, "sound");
        rename(transformer, "sounds", "(" + SOUND_MOJ + ")" + L_PROPS, "sound");
        // materialColor(MapColor|DyeColor) → mapColor(...)
        rename(transformer, "materialColor", "(" + MAPCOLOR_INT + ")" + L_PROPS, "mapColor");
        rename(transformer, "materialColor", "(" + MAPCOLOR_MOJ + ")" + L_PROPS, "mapColor");
        rename(transformer, "materialColor", "(" + DYE_INT + ")" + L_PROPS, "mapColor");
        rename(transformer, "materialColor", "(" + DYE_MOJ + ")" + L_PROPS, "mapColor");
    }

    /**
     * Instance-method rename, keyed on the post-class-redirect owner
     * ({@code Properties}) so it matches after the {@code FabricBlockSettings →
     * Properties} class redirect runs. Descriptor return type is {@code Properties}
     * for the same reason (the class redirect rewrites the original
     * {@code …)LFabricBlockSettings;} return). {@code devirtualize=false} keeps it
     * an {@code INVOKEVIRTUAL} on the same receiver.
     */
    private static void rename(RetromodTransformer t, String fbsName, String desc, String propsName) {
        t.registerMethodRedirect(PROPS, fbsName, desc, PROPS, propsName, desc, false);
    }

    /** Static-factory rename (e.g. {@code create()} → {@code of()}). */
    private static void renameStatic(RetromodTransformer t, String fbsName, String desc, String propsName) {
        t.registerMethodRedirect(PROPS, fbsName, desc, PROPS, propsName, desc, false);
    }
}
