/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Forge 1.21.11 → 26.1 shim
 *
 * MC 26.1 removed ALL code obfuscation — Mojang official names used directly.
 * Forge mods built for 1.21.11 already use Mojang names (via MCP/official mappings),
 * so this shim handles vanilla MC API changes, not remapping.
 *
 * Note: Most Forge mods targeting 1.21+ are actually NeoForge mods. This shim
 * exists for the few mods that still target Lexforge (the original Forge fork
 * that continued separately from NeoForge).
 *
 * Vanilla MC changes are the same as the NeoForge shim:
 * - EntityType.BOAT/CHEST_BOAT split into per-wood types
 * - AbstractWidget x/y/width/height fields became private
 * - Listener.setGain(float) removed
 * - Item.getDefaultInstance() needs safe wrapper
 * - Window.getWindow() → handle()
 * - KeyMapping.boundKey → key
 * - AbstractContainerScreen.findSlot → getHoveredSlot
 * - DFU DataResult.get() removed
 *
 * Forge-specific API changes (capabilities, registries, etc.) are handled
 * by ForgeCapabilitiesShim and ForgeRegistryApiShim.
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Forge mods built for 1.21.11 to run on 26.1+.
 *
 * Covers vanilla MC API changes that affect Forge mods in the 26.1 update.
 * Forge-specific API redirects (MinecraftForge event bus, capabilities, etc.)
 * are handled by dedicated API shims in the shim.api.forge package.
 */
public class Forge_1_21_11_to_26_1 implements VersionShim {

    @Override
    public String getShimName() {
        return "Forge 1.21.11 to 26.1";
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
        return "forge";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {

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
        // FORGE-SPECIFIC CHANGES
        // Forge API renames/removals in the 26.1 update
        // ============================================================

        // ForgeRegistries → BuiltInRegistries (Forge finally aligned with vanilla)
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "net/minecraft/core/registries/BuiltInRegistries"
        );

        // IForgeItem → ForgeItem (Forge dropped "I" prefix convention)
        transformer.registerClassRedirect(
            "net/minecraftforge/common/extensions/IForgeItem",
            "net/minecraftforge/common/extensions/ForgeItem"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/extensions/IForgeBlock",
            "net/minecraftforge/common/extensions/ForgeBlock"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/extensions/IForgeEntity",
            "net/minecraftforge/common/extensions/ForgeEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/extensions/IForgeBlockEntity",
            "net/minecraftforge/common/extensions/ForgeBlockEntity"
        );

        // ============================================================
        // JSPECIFY ANNOTATIONS
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
