/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.21.1 to 1.21.2 shim - Major API renames.
 * Significant changes: ShaderInstance renamed to CompiledShaderProgram,
 * InteractionResult types consolidated, Registry method renames,
 * Attribute field prefix removals (GENERIC_ dropped).
 */
public class NeoForge_1_21_1_to_1_21_2 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.1 to 1.21.2"; }
    @Override public String getSourceVersion() { return "1.21.1"; }
    @Override public String getTargetVersion() { return "1.21.2"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {

        // ============================================================
        // PATHFINDING ENUM RENAMES (PathType, the 1.21.2 mob-pathfinding
        // refactor). 1.20.x's standing/adjacent pair DAMAGE_X / DANGER_X
        // became X / X_IN_NEIGHBOR; the "OTHER" family became DAMAGING.
        // Verified against the 26.1.2 jar (constants present there); caught
        // live by the bridge-verification run (NoSuchFieldError DAMAGE_OTHER
        // from a real 1.20.1 mod's LandPathNodeTypesRegistry.register call).
        // Post-remap Mojang names (ClassRemapper runs first).
        // ============================================================
        String pathType = "net/minecraft/world/level/pathfinder/PathType";
        transformer.registerFieldRedirect(pathType, "DAMAGE_FIRE", pathType, "FIRE");
        transformer.registerFieldRedirect(pathType, "DANGER_FIRE", pathType, "FIRE_IN_NEIGHBOR");
        transformer.registerFieldRedirect(pathType, "DAMAGE_OTHER", pathType, "DAMAGING");
        transformer.registerFieldRedirect(pathType, "DANGER_OTHER", pathType, "DAMAGING_IN_NEIGHBOR");
        // ShaderInstance renamed to CompiledShaderProgram in 1.21.2
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/ShaderInstance",
            "net/minecraft/client/renderer/CompiledShaderProgram"
        );

        // InteractionResultHolder merged into InteractionResult
        transformer.registerClassRedirect(
            "net/minecraft/world/InteractionResultHolder",
            "net/minecraft/world/InteractionResult"
        );

        // ItemInteractionResult merged into InteractionResult
        transformer.registerClassRedirect(
            "net/minecraft/world/ItemInteractionResult",
            "net/minecraft/world/InteractionResult"
        );

        // RegistryAccess.registryOrThrow renamed to lookupOrThrow
        transformer.registerMethodRedirect(
            "net/minecraft/core/RegistryAccess", "registryOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;",
            "net/minecraft/core/RegistryAccess", "lookupOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;"
        );

        // Registry.getHolderOrThrow renamed to getOrThrow
        transformer.registerMethodRedirect(
            "net/minecraft/core/Registry", "getHolderOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;",
            "net/minecraft/core/Registry", "getOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"
        );

        // Registry.getOptional renamed to getOptionalValue
        transformer.registerMethodRedirect(
            "net/minecraft/core/Registry", "getOptional",
            "(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            "net/minecraft/core/Registry", "getOptionalValue",
            "(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"
        );

        // Item.getDescription renamed to getName
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getDescription",
            "()Lnet/minecraft/network/chat/Component;",
            "net/minecraft/world/item/Item", "getName",
            "()Lnet/minecraft/network/chat/Component;"
        );

        // Item.getCraftingRemainingItem renamed to getCraftingRemainder
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getCraftingRemainingItem",
            "()Lnet/minecraft/world/item/Item;",
            "net/minecraft/world/item/Item", "getCraftingRemainder",
            "()Lnet/minecraft/world/item/Item;"
        );

        // Attributes: GENERIC_ prefix removed in 1.21.2
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MAX_HEALTH",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MAX_HEALTH",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MOVEMENT_SPEED",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MOVEMENT_SPEED",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ATTACK_DAMAGE",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ATTACK_DAMAGE",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ARMOR",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ARMOR",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_KNOCKBACK_RESISTANCE",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "KNOCKBACK_RESISTANCE",
            "Lnet/minecraft/core/Holder;"
        );
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
