/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.16.5 to run on 1.17.
 * Handles Java 16 transition, tag system restructuring, and ore generation changes.
 */
public class Fabric_1_16_5_to_1_17 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.16.5 to 1.17"; }
    @Override public String getSourceVersion() { return "1.16.5"; }
    @Override public String getTargetVersion() { return "1.17"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // ────────────────────────────────────────────────────────────────────────
        // 1.16 → 1.17 PACKAGE REORGANIZATION CLASS REDIRECTS
        //
        // MC 1.17 was a sweeping package rename - most core types moved from the
        // flat `net.minecraft.{block,entity,util,world}` layout into the modern
        // categorized `net.minecraft.world.{entity,level,phys}.…` tree. Mods
        // compiled against ≤1.16 reference the OLD paths in their constant
        // pools; on a 1.17+ host those classes don't exist and you get
        // NoClassDefFoundError the first time the mod touches one (which is
        // usually during static-init or onInitialize - i.e. immediate crash).
        //
        // The audit (`audit-workspace/results.csv`) confirmed every 1.16.x mod
        // in the top-50-by-downloads hits this - Lithium 1.16.4 alone has 80+
        // references to `net/minecraft/world/World`, `net/minecraft/entity/Entity`,
        // etc. Mapping each old path to its current location turns those
        // unresolvable references into clean calls against the modern types.
        //
        // The redirects are at CLASS granularity - Retromod's ClassRemapper
        // rewrites every owner-name + descriptor reference in the mod bytecode,
        // including in method signatures, field types, and CONSTANT_Class pool
        // entries. The METHOD redirects further down then rewrite the renamed
        // methods on the now-correctly-owner-named calls.
        // ────────────────────────────────────────────────────────────────────────

        // Entity hierarchy: net/minecraft/entity/* → net/minecraft/world/entity/*
        transformer.registerClassRedirect("net/minecraft/entity/Entity",
                "net/minecraft/world/entity/Entity");
        transformer.registerClassRedirect("net/minecraft/entity/LivingEntity",
                "net/minecraft/world/entity/LivingEntity");
        transformer.registerClassRedirect("net/minecraft/entity/MobEntity",
                "net/minecraft/world/entity/Mob");
        transformer.registerClassRedirect("net/minecraft/entity/player/PlayerEntity",
                "net/minecraft/world/entity/player/Player");
        transformer.registerClassRedirect("net/minecraft/entity/EntityType",
                "net/minecraft/world/entity/EntityType");

        // World/level: the W→L rename
        transformer.registerClassRedirect("net/minecraft/world/World",
                "net/minecraft/world/level/Level");
        transformer.registerClassRedirect("net/minecraft/world/WorldView",
                "net/minecraft/world/level/LevelReader");
        transformer.registerClassRedirect("net/minecraft/world/chunk/Chunk",
                "net/minecraft/world/level/chunk/ChunkAccess");
        transformer.registerClassRedirect("net/minecraft/world/chunk/WorldChunk",
                "net/minecraft/world/level/chunk/LevelChunk");

        // Blocks + items: were flat, now nested under world/level + world/item
        transformer.registerClassRedirect("net/minecraft/block/BlockState",
                "net/minecraft/world/level/block/state/BlockState");
        transformer.registerClassRedirect("net/minecraft/block/Block",
                "net/minecraft/world/level/block/Block");
        transformer.registerClassRedirect("net/minecraft/block/Blocks",
                "net/minecraft/world/level/block/Blocks");
        transformer.registerClassRedirect("net/minecraft/item/Item",
                "net/minecraft/world/item/Item");
        transformer.registerClassRedirect("net/minecraft/item/Items",
                "net/minecraft/world/item/Items");
        transformer.registerClassRedirect("net/minecraft/item/ItemStack",
                "net/minecraft/world/item/ItemStack");

        // Math types: most went to net.minecraft.core or net.minecraft.world.phys
        transformer.registerClassRedirect("net/minecraft/util/math/BlockPos",
                "net/minecraft/core/BlockPos");
        transformer.registerClassRedirect("net/minecraft/util/math/Direction",
                "net/minecraft/core/Direction");
        transformer.registerClassRedirect("net/minecraft/util/math/Box",
                "net/minecraft/world/phys/AABB");
        transformer.registerClassRedirect("net/minecraft/util/math/Vec3d",
                "net/minecraft/world/phys/Vec3");
        transformer.registerClassRedirect("net/minecraft/util/math/ChunkPos",
                "net/minecraft/world/level/ChunkPos");

        // Identifier → ResourceLocation: the most-referenced single rename
        transformer.registerClassRedirect("net/minecraft/util/Identifier",
                "net/minecraft/resources/ResourceLocation");

        // Client side: MinecraftClient → Minecraft, MatrixStack → PoseStack
        transformer.registerClassRedirect("net/minecraft/client/MinecraftClient",
                "net/minecraft/client/Minecraft");
        transformer.registerClassRedirect("net/minecraft/client/util/math/MatrixStack",
                "com/mojang/blaze3d/vertex/PoseStack");
        transformer.registerClassRedirect("net/minecraft/client/render/VertexConsumer",
                "com/mojang/blaze3d/vertex/VertexConsumer");
        transformer.registerClassRedirect("net/minecraft/client/render/VertexConsumerProvider",
                "net/minecraft/client/renderer/MultiBufferSource");
        transformer.registerClassRedirect("net/minecraft/client/gui/screen/Screen",
                "net/minecraft/client/gui/screens/Screen");
        transformer.registerClassRedirect("net/minecraft/client/gui/hud/InGameHud",
                "net/minecraft/client/gui/Gui");
        transformer.registerClassRedirect("net/minecraft/client/render/entity/model/EntityModel",
                "net/minecraft/client/model/EntityModel");

        // Network: ClientConnection → Connection
        transformer.registerClassRedirect("net/minecraft/network/ClientConnection",
                "net/minecraft/network/Connection");

        // Fabric API lifecycle-events: inner-class follow-on of the W→L rename.
        // ClientTickEvents$StartWorldTick / $EndWorldTick were renamed to
        // $StartLevelTick / $EndLevelTick in step with the MC World→Level rename
        // since they're tick callbacks parameterized on the level type.
        transformer.registerClassRedirect(
                "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$StartWorldTick",
                "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$StartLevelTick");
        transformer.registerClassRedirect(
                "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$EndWorldTick",
                "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents$EndLevelTick");
        transformer.registerClassRedirect(
                "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$StartWorldTick",
                "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$StartLevelTick");
        transformer.registerClassRedirect(
                "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$EndWorldTick",
                "net/fabricmc/fabric/api/event/lifecycle/v1/ServerTickEvents$EndLevelTick");

        // Tag system restructured
        transformer.registerClassRedirect(
            "net/minecraft/tag/ServerTagManagerHolder",
            "com/retromod/shim/fabric/embedded/TagShim"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/BlockTags",
            "net/minecraft/registry/tag/BlockTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/ItemTags",
            "net/minecraft/registry/tag/ItemTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/EntityTypeTags",
            "net/minecraft/registry/tag/EntityTypeTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/FluidTags",
            "net/minecraft/registry/tag/FluidTags"
        );
        // Ore feature config changed
        transformer.registerMethodRedirect(
            "net/minecraft/world/gen/feature/OreFeatureConfig", "<init>",
            "(Lnet/minecraft/structure/rule/RuleTest;Lnet/minecraft/block/BlockState;I)V",
            "com/retromod/shim/fabric/embedded/OreFeatureShim", "create",
            "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;"
        );

        // ============================================================
        // MCP → Mojang method renames (1.16.5 → 1.17)
        // The biggest rename wave: these are the "alphabet of modding"
        // methods that 99% of mods use. By 1.17, Mojang names are used.
        // ============================================================

        // Entity.onUpdate() → tick()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "onUpdate",
            "()V",
            "net/minecraft/world/entity/Entity", "tick",
            "()V"
        );
        // Entity.attackEntityFrom() → hurt()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "attackEntityFrom",
            "(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            "net/minecraft/world/entity/Entity", "hurt",
            "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        );
        // LivingEntity.onDeath() → die()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/LivingEntity", "onDeath",
            "(Lnet/minecraft/world/damagesource/DamageSource;)V",
            "net/minecraft/world/entity/LivingEntity", "die",
            "(Lnet/minecraft/world/damagesource/DamageSource;)V"
        );
        // Level.setBlockState() → setBlock() (3-param version: BlockPos, BlockState, int flags)
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/Level", "setBlockState",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            "net/minecraft/world/level/Level", "setBlock",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        );
        // Level.setBlockToAir() → removeBlock()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/Level", "setBlockToAir",
            "(Lnet/minecraft/core/BlockPos;)Z",
            "net/minecraft/world/level/Level", "removeBlock",
            "(Lnet/minecraft/core/BlockPos;Z)Z"
        );
        // Block.onBlockPlacedBy() → setPlacedBy()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/Block", "onBlockPlacedBy",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            "net/minecraft/world/level/block/Block", "setPlacedBy",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"
        );
        // Item.onItemRightClick() → use()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "onItemRightClick",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            "net/minecraft/world/item/Item", "use",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"
        );
        // Item.onItemUseFinish() → finishUsingItem()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "onItemUseFinish",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            "net/minecraft/world/item/Item", "finishUsingItem",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
        );
        // Item.onUpdate() → inventoryTick()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "onUpdate",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            "net/minecraft/world/item/Item", "inventoryTick",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V"
        );
        // Item.addInformation() → appendHoverText()
        // NOTE: descriptor needs verification - Component vs Text type depends on mappings
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "addInformation",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V",
            "net/minecraft/world/item/Item", "appendHoverText",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V"
        );
        // BlockEntity.readFromNBT() → load()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/BlockEntity", "readFromNBT",
            "(Lnet/minecraft/nbt/CompoundTag;)V",
            "net/minecraft/world/level/block/entity/BlockEntity", "load",
            "(Lnet/minecraft/nbt/CompoundTag;)V"
        );
        // BlockEntity.writeToNBT() → save()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/BlockEntity", "writeToNBT",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "net/minecraft/world/level/block/entity/BlockEntity", "save",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"
        );
        // Screen.drawScreen() → render()
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/Screen", "drawScreen",
            "(IIF)V",
            "net/minecraft/client/gui/screens/Screen", "render",
            "(IIF)V"
        );
        // Screen.initGui() → init()
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/Screen", "initGui",
            "()V",
            "net/minecraft/client/gui/screens/Screen", "init",
            "()V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
