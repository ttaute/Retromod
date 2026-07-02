/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Forge 1.21.11 to 26.1 shim. MC 26.1 uses Mojang official names directly, and
 * Forge mods built for 1.21.11 already use Mojang names, so this handles vanilla
 * MC API changes rather than remapping. Most Forge-on-1.21+ mods are really
 * NeoForge; this covers the few that still target Lexforge. Forge-specific
 * changes (capabilities, registries) live in ForgeCapabilitiesShim and
 * ForgeRegistryApiShim.
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import com.retromod.core.VersionShim;
import com.retromod.util.McReflect;

/**
 * Compatibility shim for Forge mods built for 1.21.11 to run on 26.1+.
 * Covers vanilla MC API changes; Forge-specific redirects live in the
 * shim.api.forge package.
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
    public void registerRedirects(RetromodTransformer transformer) {

        // Neutralize the imperative RenderSystem state setters deleted in the
        // blaze3d refactor. Forge doesn't share the Common class-moves path, so
        // wire it here directly.
        com.retromod.shim.common.RemovedRenderStateNeutralize.register(transformer);

        // 26.1 removed the single-arg is() overloads from BlockState/ItemStack/FluidState;
        // same wiring note as above (Forge doesn't run the Common class-moves path).
        com.retromod.shim.common.IsOverloadBridgeSynthetic.register(transformer);

        // Forge 64.x for MC 26.1 renamed net.minecraft.resources.ResourceLocation
        // to .Identifier; pre-26.1 mods reference the old name and crash with
        // NoClassDefFoundError. The constructor became private too, handled below.
        transformer.registerClassRedirect(
            "net/minecraft/resources/ResourceLocation",
            "net/minecraft/resources/Identifier"
        );

        // 2-arg constructor became private; redirect to the static factory.
        // The lookup key uses the post-class-remap owner (Identifier), since
        // ClassRemapper renames class names before the constructor lookup runs.
        // Forge 64.x pairs the yarn class name with the Mojang factory name
        // fromNamespaceAndPath, not yarn's of (which gives NoSuchMethodError).
        transformer.registerConstructorRedirect(
            "net/minecraft/resources/Identifier",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/resources/Identifier",
            "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
        );
        // 1-arg colon-separated form: new Identifier("ns:path"), factory parse(String).
        transformer.registerConstructorRedirect(
            "net/minecraft/resources/Identifier",
            "(Ljava/lang/String;)V",
            "net/minecraft/resources/Identifier",
            "parse",
            "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
        );

        // EntityType.BOAT/CHEST_BOAT split into per-wood types in 26.1; OAK is
        // the common default for old mods.
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

        // AbstractWidget x/y/width/height became private in 26.1, now via getters/setters.
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

        // CommandSourceStack.hasPermission(int) bridges to the new PermissionSet system.
        transformer.registerMethodRedirect(
            "net/minecraft/commands/CommandSourceStack", "hasPermission",
            "(I)Z",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "hasPermission",
            "(Ljava/lang/Object;I)Z",
            true
        );

        // Listener.setGain(float) removed (volume moved per-source); no-op it.
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/audio/Listener", "setGain",
            "(F)V",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "noOp",
            "(Ljava/lang/Object;F)V",
            true
        );

        // Item components are data-driven in 26.1; getDefaultInstance() before
        // binding NPEs, so route through a safe wrapper.
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getDefaultInstance",
            "()Lnet/minecraft/world/item/ItemStack;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "safeGetDefaultInstance",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            true
        );

        // Window.getWindow() renamed to record-style accessor handle().
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/platform/Window", "getWindow",
            "()J",
            "com/mojang/blaze3d/platform/Window", "handle",
            "()J"
        );

        // KeyMapping.boundKey renamed to key.
        transformer.registerFieldRedirect(
            "net/minecraft/client/KeyMapping", "boundKey",
            "Lcom/mojang/blaze3d/platform/InputConstants$Key;",
            "net/minecraft/client/KeyMapping", "key",
            "Lcom/mojang/blaze3d/platform/InputConstants$Key;"
        );

        // AbstractContainerScreen.findSlot renamed to getHoveredSlot.
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "findSlot",
            "(DD)Lnet/minecraft/world/inventory/Slot;",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "getHoveredSlot",
            "(DD)Lnet/minecraft/world/inventory/Slot;"
        );

        // DataResult became an interface in DFU 9.x and dropped get(); use the polyfill.
        transformer.registerMethodRedirect(
            "com/mojang/serialization/DataResult", "get",
            "()Lcom/mojang/datafixers/util/Either;",
            "com/retromod/polyfill/minecraft/DataResultPolyfill", "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            true
        );

        // ForgeRegistries is NOT class-redirected here. Its field reads are migrated to
        // vanilla Registries ResourceKeys by ForgeRegistryApiShim via field redirects;
        // a class redirect would rewrite the GETSTATIC owner first (ClassRemapper runs
        // before field redirects) and re-break them.

        // Forge dropped the "I" prefix on its extension interfaces: IForgeItem -> ForgeItem, etc.
        // These target net/minecraftforge/common/extensions/, which only exists on a FORGE 26.1
        // host. On a NeoForge host the Forge -> NeoForge migration (Forge_1_20_to_NeoForge_1_21)
        // maps the same interfaces to NeoForge's IItemExtension/IBlockExtension/... instead;
        // registering the Forge-package rename on NeoForge too would clobber that (the redirect map
        // is last-writer-wins across ServiceLoader-discovered shims) and crash a Forge mod's custom
        // Item at construct with NoClassDefFoundError: net/minecraftforge/common/extensions/ForgeItem
        // (Macaw's on NeoForge 26.2). So gate these to Forge hosts only.
        // ALSO version-gated to a 26.1 host: the I-drop was Forge 26.1 (64.x) only - Forge 26.2
        // (65.x) RESTORED the I-names (verified: forge-26.2-65.0.0-universal ships IForgeItem and
        // no ForgeItem), so applying the rename on a 26.2 host recreates the same
        // NoClassDefFoundError: net/minecraftforge/common/extensions/ForgeItem it once fixed.
        if (!McReflect.isNeoForge()
                && RetromodVersion.mcVersionExceeds("26.2", RetromodVersion.TARGET_MC_VERSION)) {
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
        }

        // Catch javax.annotation refs for mods that skip intermediate shims via direct BFS.
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
            // Loader-agnostic; reused from Fabric for the safe Item/Listener redirects.
            "com.retromod.shim.fabric.embedded.ItemSafetyShim"
        };
    }
}
