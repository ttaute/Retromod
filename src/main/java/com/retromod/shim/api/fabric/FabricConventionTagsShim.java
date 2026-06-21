/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.EnvironmentDetector;
import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redirects the removed Fabric <b>convention tags v1</b>
 * ({@code net.fabricmc.fabric.api.tag.convention.v1.*}) onto v2. Audit gap: ~5 mods
 * sole-blocked on {@code v1/ConventionalItemTags}.
 *
 * <p>The v1 classes are plain holders of {@code public static final TagKey} fields,
 * so this is field resolution, not a lambda problem. Verified against the real jars
 * (fabric-api 0.92.7 for v1, 0.145.4 for v2): <b>54 of 79</b> item-tag fields kept
 * their names across v1→v2 - those ride the class redirect untouched. The renamed
 * ones we could match with certainty get explicit field redirects (keyed on the v2
 * owner, because the ClassRemapper rewrites the GETSTATIC owner before the field
 * pass sees it). The handful with no certain v2 equivalent (e.g. {@code AXES},
 * {@code COAL}) are left unmapped: a mod touching one gets {@code NoSuchFieldError}
 * for that one tag instead of today's {@code NoClassDefFoundError} for the whole
 * class - strictly an improvement, never a regression.
 *
 * <h2>Gating</h2>
 * Gated on the <b>presence of v2 on the host</b> (probed without initializing,
 * CLAUDE.md #14) rather than an MC version: v1 was removed from the Fabric API
 * around the 1.21 line, and v2 exists on every host Retromod targets from 1.20.x
 * up - but if someone runs an exotic host without v2, redirecting onto a missing
 * class would be worse than the status quo, so we check.
 */
public class FabricConventionTagsShim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String V1 = "net/fabricmc/fabric/api/tag/convention/v1/";
    private static final String V2 = "net/fabricmc/fabric/api/tag/convention/v2/";

    /** The v1 holder classes; every one has a same-named v2 successor (verified). */
    private static final String[] HOLDERS = {
        "ConventionalItemTags",
        "ConventionalBlockTags",
        "ConventionalBiomeTags",
        "ConventionalEnchantmentTags",
        "ConventionalEntityTypeTags",
        "ConventionalFluidTags",
    };

    /**
     * v1 item-tag fields whose v2 name differs - only pairs where the v2 field was
     * verified to exist in fabric-api 0.145.4. Same {@code TagKey} type both sides,
     * so the desc-agnostic redirect form is exact.
     */
    private static final String[][] ITEM_FIELD_RENAMES = {
        {"SHEARS", "SHEAR_TOOLS"},
        {"BOWS", "BOW_TOOLS"},
        {"SHIELDS", "SHIELD_TOOLS"},
        {"SPEARS", "TRIDENT_TOOLS"},
        {"RAW_ORES", "RAW_MATERIALS"},
        {"RAW_IRON_ORES", "IRON_RAW_MATERIALS"},
        {"RAW_COPPER_ORES", "COPPER_RAW_MATERIALS"},
        {"RAW_GOLD_ORES", "GOLD_RAW_MATERIALS"},
        {"RAW_IRON_BLOCKS", "STORAGE_BLOCKS_RAW_IRON"},
        {"RAW_COPPER_BLOCKS", "STORAGE_BLOCKS_RAW_COPPER"},
        {"RAW_GOLD_BLOCKS", "STORAGE_BLOCKS_RAW_GOLD"},
        {"EMPTY_BUCKET", "EMPTY_BUCKETS"},
        {"WATER_BUCKET", "WATER_BUCKETS"},
        {"LAVA_BUCKET", "LAVA_BUCKETS"},
        {"MILK_BUCKET", "MILK_BUCKETS"},
        {"DIAMONDS", "DIAMOND_GEMS"},
        {"EMERALDS", "EMERALD_GEMS"},
        {"LAPIS", "LAPIS_GEMS"},
        {"QUARTZ", "QUARTZ_GEMS"},
    };

    @Override public String getShimName() { return "Fabric convention tags v1 → v2"; }
    @Override public String getSourceVersion() { return "0.92.0"; }
    @Override public String getTargetVersion() { return "0.100.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // On a 26.1+ host v2 is guaranteed (the fabric-api line that removed v1).
        // On a pre-26.1 host, probe (non-initializing) for v2 before redirecting
        // onto it - exotic hosts without v2 are left untouched.
        boolean v2Present = com.retromod.core.RetromodVersion.isUnobfuscatedTarget(
                        com.retromod.core.RetromodVersion.TARGET_MC_VERSION)
                || EnvironmentDetector.hostClassExists(
                        "net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags");
        if (!v2Present) {
            LOGGER.debug("[Retromod] convention tags v1→v2 skipped (no v2 on this host)");
            return;
        }

        for (String holder : HOLDERS) {
            transformer.registerClassRedirect(V1 + holder, V2 + holder);
        }
        // Renamed fields: keyed on the V2 owner - the ClassRemapper has already
        // rewritten the GETSTATIC owner by the time field redirects are applied.
        String itemOwner = V2 + "ConventionalItemTags";
        for (String[] r : ITEM_FIELD_RENAMES) {
            transformer.registerFieldRedirect(itemOwner, r[0], itemOwner, r[1]);
        }

        LOGGER.info("[Retromod] Fabric convention tags v1→v2 - {} holder redirects + {} field renames "
                + "(same-named fields ride the class redirect)", HOLDERS.length, ITEM_FIELD_RENAMES.length);
    }
}
