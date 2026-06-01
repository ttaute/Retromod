/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * NeoForge 1.21.11 → 26.1 shim
 *
 * MC 26.1 removed ALL code obfuscation — Mojang official names used directly.
 * NeoForge already uses Mojang names since 1.17, so NeoForge mods mainly need
 * metadata patching (handled by ForgeModTransformer) rather than full remapping.
 *
 * This shim covers vanilla MC API changes that affect NeoForge mods:
 * - EntityType.BOAT/CHEST_BOAT split into per-wood types
 * - AbstractWidget x/y/width/height fields became private (accessor methods)
 * - Listener.setGain(float) removed
 * - Item.getDefaultInstance() needs safe wrapper (components not bound early)
 * - Window.getWindow() → handle()
 * - KeyMapping.boundKey → key
 * - AbstractContainerScreen.findSlot → getHoveredSlot
 *
 * Vanilla class/package moves (587 entries) are handled separately by
 * mojang-class-moves-26.1.tsv loaded via IntermediaryToMojangMapper.
 *
 * NeoForge-specific API changes (capabilities, events, etc.) are handled
 * by the existing ForgeCapabilitiesShim and ForgeEventApiShim.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for NeoForge mods built for 1.21.11 to run on 26.1+.
 *
 * Since NeoForge already uses Mojang names, this shim only needs to handle
 * vanilla MC API changes (renamed/removed methods, field visibility changes,
 * split entity types, etc.) — NOT intermediary→Mojang remapping.
 */
public class NeoForge_1_21_11_to_26_1 implements VersionShim {

    @Override
    public String getShimName() {
        return "NeoForge 1.21.11 to 26.1";
    }

    @Override
    public String getSourceVersion() {
        return "1.21.11";
    }

    @Override
    public String getTargetVersion() {
        return "26.1";
    }

    @Override
    public String getModLoaderType() {
        return "neoforge";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {

        // Vanilla 1.21.11 → 26.1 class moves, shared with the Fabric 26.1 shim.
        // GuiGraphics→GuiGraphicsExtractor, RenderType(s)→rendertype/*,
        // BlockAndTintGetter relocation. These also live in mojang-class-moves-26.1.tsv
        // (applied at runtime via IntermediaryToMojangMapper.applyTo), but registering
        // them in the shim chain too guarantees they apply in BOTH the CLI/audit path
        // and the runtime regardless of whether the mapper is loaded. Idempotent —
        // double-registration with the same target is a harmless Map.put. (#64)
        com.retromod.shim.common.Common_1_21_11_to_26_1_ClassMoves.register(transformer);

        // ============================================================
        // ENTITY TYPE SPLITS
        // EntityType.BOAT/CHEST_BOAT split into per-wood types in 26.1
        // OAK is the most common default for old mods
        // ============================================================

        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/EntityType", "BOAT",
            "Lnet/minecraft/world/entity/EntityType;",
            "net/minecraft/world/entity/EntityType", "OAK_BOAT",
            "Lnet/minecraft/world/entity/EntityType;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/EntityType", "CHEST_BOAT",
            "Lnet/minecraft/world/entity/EntityType;",
            "net/minecraft/world/entity/EntityType", "OAK_CHEST_BOAT",
            "Lnet/minecraft/world/entity/EntityType;"
        );

        // ============================================================
        // ABSTRACTWIDGET FIELD → ACCESSOR REDIRECTS
        // x/y/width/height became private in 26.1, now accessed via
        // getX()/setX(), getY()/setY(), getWidth()/setWidth(), getHeight()/setHeight()
        // ============================================================

        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "x",
            "getX", "()I", "setX", "(I)V"
        );
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "y",
            "getY", "()I", "setY", "(I)V"
        );
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "width",
            "getWidth", "()I", "setWidth", "(I)V"
        );
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "height",
            "getHeight", "()I", "setHeight", "(I)V"
        );

        // ============================================================
        // LISTENER.setGain(float) REMOVED
        // Volume control moved to per-source in 26.1.
        // Mods like Dynamic FPS call this to mute/unmute — no-op redirect.
        // ============================================================

        // CommandSourceStack.hasPermission(int) → bridge to new PermissionSet system
        transformer.registerMethodRedirect(
            "net/minecraft/commands/CommandSourceStack", "hasPermission",
            "(I)Z",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "hasPermission",
            "(Ljava/lang/Object;I)Z",
            true  // devirtualize
        );

        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/audio/Listener", "setGain",
            "(F)V",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "noOp",
            "(Ljava/lang/Object;F)V",
            true  // devirtualize: instance method → static method
        );

        // ============================================================
        // ITEM.getDefaultInstance() SAFE WRAPPER
        // In 26.1, item components are data-driven and bound during data pack
        // loading. Calling getDefaultInstance() before binding causes NPE.
        // ============================================================

        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getDefaultInstance",
            "()Lnet/minecraft/world/item/ItemStack;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "safeGetDefaultInstance",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            true  // devirtualize: instance method → static method
        );

        // ============================================================
        // WINDOW.getWindow() → handle()
        // MC 26.1 renamed getter methods to record-style accessors
        // ============================================================

        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/platform/Window", "getWindow",
            "()J",
            "com/mojang/blaze3d/platform/Window", "handle",
            "()J"
        );

        // ============================================================
        // KEYMAPPING.boundKey → key
        // Field renamed in 26.1
        // ============================================================

        transformer.registerFieldRedirect(
            "net/minecraft/client/KeyMapping", "boundKey",
            "Lcom/mojang/blaze3d/platform/InputConstants$Key;",
            "net/minecraft/client/KeyMapping", "key",
            "Lcom/mojang/blaze3d/platform/InputConstants$Key;"
        );

        // ============================================================
        // ABSTRACTCONTAINERSCREEN.findSlot → getHoveredSlot
        // Method renamed in 26.1
        // ============================================================

        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "findSlot",
            "(DD)Lnet/minecraft/world/inventory/Slot;",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "getHoveredSlot",
            "(DD)Lnet/minecraft/world/inventory/Slot;"
        );

        // ============================================================
        // DFU (DataFixerUpper) API CHANGES
        // DataResult changed from class to interface in DFU 9.x
        // DataResult.get() removed — redirect to polyfill
        // ============================================================

        transformer.registerMethodRedirect(
            "com/mojang/serialization/DataResult", "get",
            "()Lcom/mojang/datafixers/util/Either;",
            "com/retromod/polyfill/minecraft/DataResultPolyfill", "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            true  // devirtualize: instance method → static method
        );

        // ============================================================
        // NEOFORGE-SPECIFIC CHANGES
        // NeoForge API renames/deprecations in the 26.1 update
        // ============================================================

        // IItemHandler → ItemHandler (NeoForge dropped "I" prefix convention)
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/items/IItemHandler",
            "net/neoforged/neoforge/items/ItemHandler"
        );
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/items/IItemHandlerModifiable",
            "net/neoforged/neoforge/items/ItemHandlerModifiable"
        );

        // IFluidHandler → FluidHandler
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/fluids/IFluidHandler",
            "net/neoforged/neoforge/fluids/FluidHandler"
        );

        // IEnergyStorage → EnergyStorage
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/energy/IEnergyStorage",
            "net/neoforged/neoforge/energy/EnergyStorage"
        );

        // ForgeSpawnEggItem → NeoForgeSpawnEggItem (already renamed in earlier versions,
        // but some older NeoForge mods still reference the Forge name)
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/common/ForgeSpawnEggItem",
            "net/neoforged/neoforge/common/NeoForgeSpawnEggItem"
        );

        // ============================================================
        // JSPECIFY ANNOTATIONS (if not already handled by 1.21.10→1.21.11)
        // Ensure javax.annotation references are caught for mods
        // that skip intermediate shims via direct BFS path
        // ============================================================

        transformer.registerClassRedirect(
            "javax/annotation/Nullable",
            "org/jspecify/annotations/Nullable"
        );
        transformer.registerClassRedirect(
            "javax/annotation/Nonnull",
            "org/jspecify/annotations/NonNull"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            // Reuses Fabric's ItemSafetyShim for safe Item.getDefaultInstance()
            // and Listener.setGain() no-op — these are loader-agnostic utilities
            "com.retromod.shim.fabric.embedded.ItemSafetyShim"
        };
    }
}
