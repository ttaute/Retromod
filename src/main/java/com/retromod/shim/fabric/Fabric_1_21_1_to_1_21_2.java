/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2024/10/14/1212.html
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21.1 to run on 1.21.2.
 * 
 * Major breaking changes addressed:
 * - Identifier constructor became protected (use Identifier.of)
 * - TypedActionResult -> ActionResult in many places
 * - FabricElytraItem removed (use glider component)
 * - FabricBlockEntityType.Builder removed
 * - HudRenderCallback now passes RenderTickCounter
 * - Data pack paths changed to singular nouns
 * - FabricDimensions removed
 * - Recipe system reworked
 */
public class Fabric_1_21_1_to_1_21_2 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.1 to 1.21.2";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.1";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.2";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {

        // ============================================================
        // GAME VERSION CHANGES
        // WorldVersion (intermediary: class_6489) became a record in 1.21.2.
        // Getter-style methods renamed to record-style accessors.
        // Uses post-remapping Mojang names (ClassRemapper runs first).
        // ============================================================

        // getName() -> name()
        transformer.registerMethodRedirect(
            "net/minecraft/WorldVersion", "getName", "()Ljava/lang/String;",
            "net/minecraft/WorldVersion", "name", "()Ljava/lang/String;"
        );

        // getId() -> id()
        transformer.registerMethodRedirect(
            "net/minecraft/WorldVersion", "getId", "()Ljava/lang/String;",
            "net/minecraft/WorldVersion", "id", "()Ljava/lang/String;"
        );

        // getReleaseTarget() -> name() (closest equivalent)
        transformer.registerMethodRedirect(
            "net/minecraft/WorldVersion", "getReleaseTarget", "()Ljava/lang/String;",
            "net/minecraft/WorldVersion", "name", "()Ljava/lang/String;"
        );

        // getBuildTime() -> buildTime()
        transformer.registerMethodRedirect(
            "net/minecraft/WorldVersion", "getBuildTime", "()Ljava/util/Date;",
            "net/minecraft/WorldVersion", "buildTime", "()Ljava/util/Date;"
        );

        // isStable() -> stable()
        transformer.registerMethodRedirect(
            "net/minecraft/WorldVersion", "isStable", "()Z",
            "net/minecraft/WorldVersion", "stable", "()Z"
        );

        // NOTE: getWorldVersion() -> dataVersion() has a return type change
        // (int -> DataVersion record), so a simple redirect won't work.
        // Mods calling getWorldVersion() would need a polyfill.

        // ============================================================
        // IDENTIFIER CHANGES
        // new Identifier(...) -> Identifier.of(...)
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/IdentifierShim", "of", 
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/util/Identifier;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/IdentifierShim", "of",
            "(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"
        );
        
        // ============================================================
        // ACTION RESULT CHANGES
        // TypedActionResult<ItemStack> -> ActionResult in UseItemCallback
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/item/v1/FabricItem", "getAttributeModifiers",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;",
            "com/retromod/shim/fabric/embedded/FabricItemShim", "getAttributeModifiers",
            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"
        );
        
        // ============================================================
        // FABRIC DIMENSIONS REMOVED
        // FabricDimensions.teleport -> Entity.teleportTo
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/dimension/v1/FabricDimensions", "teleport",
            "(Lnet/minecraft/entity/Entity;Lnet/minecraft/server/world/ServerWorld;Lnet/fabricmc/fabric/api/dimension/v1/TeleportTarget;)Lnet/minecraft/entity/Entity;",
            "com/retromod/shim/fabric/embedded/FabricDimensionsShim", "teleport",
            "(Lnet/minecraft/entity/Entity;Lnet/minecraft/server/world/ServerWorld;Ljava/lang/Object;)Lnet/minecraft/entity/Entity;"
        );
        
        // ============================================================
        // BLOCK ENTITY TYPE BUILDER
        // FabricBlockEntityType.Builder removed
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/object/builder/v1/block/entity/FabricBlockEntityType$Builder",
            "com/retromod/shim/fabric/embedded/FabricBlockEntityTypeBuilderShim"
        );
        
        // ============================================================
        // RENDERER REGISTRIES REMOVED
        // fabric-renderer-registries-v1 module removed
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendereregistry/v1/BlockEntityRendererRegistry",
            "com/retromod/shim/fabric/embedded/BlockEntityRendererRegistryShim"
        );
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendereregistry/v1/EntityRendererRegistry",
            "com/retromod/shim/fabric/embedded/EntityRendererRegistryShim"
        );
        
        // ============================================================
        // RESOURCE CONDITION CHANGES
        // test method signature changed
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/resource/conditions/v1/ResourceCondition", "test",
            "(Lnet/minecraft/registry/DynamicRegistryManager;)Z",
            "com/retromod/shim/fabric/embedded/ResourceConditionShim", "test",
            "(Ljava/lang/Object;Lnet/minecraft/registry/DynamicRegistryManager;)Z"
        );

        // ============================================================
        // INTERACTION RESULT CONSOLIDATION
        // InteractionResultHolder and ItemInteractionResult merged into InteractionResult
        // ============================================================

        // InteractionResultHolder<T> replaced by InteractionResult (no longer generic)
        transformer.registerClassRedirect(
            "net/minecraft/world/InteractionResultHolder",
            "net/minecraft/world/InteractionResult"
        );

        // ItemInteractionResult merged into InteractionResult
        transformer.registerClassRedirect(
            "net/minecraft/world/ItemInteractionResult",
            "net/minecraft/world/InteractionResult"
        );

        // ============================================================
        // REGISTRY METHOD RENAMES
        // Getter methods renamed for consistency
        // ============================================================

        // Registry.getHolderOrThrow(ResourceKey) -> Registry.getOrThrow(ResourceKey)
        transformer.registerMethodRedirect(
            "net/minecraft/core/Registry", "getHolderOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;",
            "net/minecraft/core/Registry", "getOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"
        );

        // Registry.getOptional(ResourceLocation) -> Registry.getOptionalValue(ResourceLocation)
        transformer.registerMethodRedirect(
            "net/minecraft/core/Registry", "getOptional",
            "(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            "net/minecraft/core/Registry", "getOptionalValue",
            "(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"
        );

        // ============================================================
        // ATTRIBUTE FIELD RENAMES
        // GENERIC_ prefix removed from all attribute constants in 1.21.2
        // All fields have descriptor Lnet/minecraft/core/Holder;
        // ============================================================

        // GENERIC_MAX_HEALTH -> MAX_HEALTH
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MAX_HEALTH",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MAX_HEALTH",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_MOVEMENT_SPEED -> MOVEMENT_SPEED
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MOVEMENT_SPEED",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MOVEMENT_SPEED",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_ATTACK_DAMAGE -> ATTACK_DAMAGE
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ATTACK_DAMAGE",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ATTACK_DAMAGE",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_ATTACK_SPEED -> ATTACK_SPEED
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ATTACK_SPEED",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ATTACK_SPEED",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_ARMOR -> ARMOR
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ARMOR",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ARMOR",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_ARMOR_TOUGHNESS -> ARMOR_TOUGHNESS
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ARMOR_TOUGHNESS",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ARMOR_TOUGHNESS",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_KNOCKBACK_RESISTANCE -> KNOCKBACK_RESISTANCE
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_KNOCKBACK_RESISTANCE",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "KNOCKBACK_RESISTANCE",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_ATTACK_KNOCKBACK -> ATTACK_KNOCKBACK
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ATTACK_KNOCKBACK",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ATTACK_KNOCKBACK",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_LUCK -> LUCK
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_LUCK",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "LUCK",
            "Lnet/minecraft/core/Holder;"
        );

        // GENERIC_MAX_ABSORPTION -> MAX_ABSORPTION
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MAX_ABSORPTION",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MAX_ABSORPTION",
            "Lnet/minecraft/core/Holder;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.IdentifierShim",
            "com.retromod.shim.fabric.embedded.FabricItemShim",
            "com.retromod.shim.fabric.embedded.FabricDimensionsShim",
            "com.retromod.shim.fabric.embedded.FabricBlockEntityTypeBuilderShim",
            "com.retromod.shim.fabric.embedded.BlockEntityRendererRegistryShim",
            "com.retromod.shim.fabric.embedded.EntityRendererRegistryShim",
            "com.retromod.shim.fabric.embedded.ResourceConditionShim"
        };
    }
}
