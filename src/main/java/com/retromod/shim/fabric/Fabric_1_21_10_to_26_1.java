/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;
import com.retromod.mapping.IntermediaryToMojangMapper;

/**
 * Compatibility shim for Fabric mods built for 1.21.10 (or earlier) to run on 26.1.
 *
 * This is the MAJOR version transition shim — Minecraft 26.1 removes all code
 * obfuscation. Every intermediary name (class_XXXX, method_XXXX, field_XXXX)
 * becomes a human-readable Mojang official name.
 *
 * This shim delegates the bulk of name translation to IntermediaryToMojangMapper,
 * which contains ~500+ curated class/method/field mappings plus support for
 * loading full mapping files.
 *
 * Additionally, this shim handles:
 * - Fabric API changes specific to 26.1
 * - Rendering API changes (new rendering pipeline)
 * - Removed/replaced APIs
 */
public class Fabric_1_21_10_to_26_1 implements VersionShim {

    @Override
    public String getShimName() {
        return "Fabric 1.21.10 to 26.1 (Deobfuscation)";
    }

    @Override
    public String getSourceVersion() {
        return "1.21.10";
    }

    @Override
    public String getTargetVersion() {
        return "26.1";
    }

    @Override
    public String getModLoaderType() {
        return "fabric";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // CORE: INTERMEDIARY → MOJANG OFFICIAL NAME MAPPING
        // ============================================================
        // This is the heart of the 26.1 transition. All class_XXXX,
        // method_XXXX, field_XXXX names need to become real Mojang names.

        IntermediaryToMojangMapper mapper = new IntermediaryToMojangMapper();
        mapper.load();
        mapper.registerWithTransformer(transformer);

        // ============================================================
        // FABRIC API 26.1 CHANGES
        // ============================================================

        // Fabric API now uses Mojang names in its own API
        // Most Fabric API classes kept their names (already human-readable),
        // but some internal references changed.

        // FabricLoader API — largely unchanged but namespace references updated
        // No class renames needed for Fabric API public interfaces

        // Fabric Rendering API v1 → v2 (26.1 uses new rendering pipeline)
        transformer.registerClassRedirect(
                "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents",
                "net/fabricmc/fabric/api/client/rendering/v2/WorldRenderEvents"
        );
        transformer.registerClassRedirect(
                "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext",
                "net/fabricmc/fabric/api/client/rendering/v2/WorldRenderContext"
        );

        // ============================================================
        // RENDERING CHANGES (26.1 new pipeline)
        // ============================================================

        // BufferBuilder was completely rewritten in recent versions
        // Mods using old BufferBuilder API need redirection
        transformer.registerMethodRedirect(
                "net/minecraft/class_287", "method_1326", "()V",
                "net/minecraft/client/renderer/BufferBuilder", "end", "()V"
        );

        // MatrixStack → PoseStack (already handled by class redirect,
        // but method names also changed)
        transformer.registerMethodRedirect(
                "net/minecraft/class_4587", "method_22903", "()V",
                "net/minecraft/client/renderer/PoseStack", "pushPose", "()V"
        );
        transformer.registerMethodRedirect(
                "net/minecraft/class_4587", "method_22909", "()V",
                "net/minecraft/client/renderer/PoseStack", "popPose", "()V"
        );

        // ============================================================
        // REMOVED APIs (no equivalent in 26.1)
        // ============================================================

        // TinyMappingFactory — no longer needed since there's no obfuscation
        // Mods that used this for remapping at runtime won't find it
        // We provide a no-op stub via polyfill

        // net.fabricmc.mapping.tree.* — Fabric mapping tree API removed
        // These were internal to Fabric's mapping system
    }
}
