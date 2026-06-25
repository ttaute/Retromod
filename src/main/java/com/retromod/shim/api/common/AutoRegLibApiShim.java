/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * AutoRegLib API -> Modern Registry Methods Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * AutoRegLib (Vazkii): the Forge 1.10-1.16 auto-registration library behind Quark/Psi/Botania,
 * deprecated once loaders gained DeferredRegister. Redirects its helpers to embedded compat
 * shims backed by modern registry APIs.
 */
public class AutoRegLibApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "AutoRegLib -> Modern Registry Compatibility";
    }

    @Override
    public String getSourceVersion() {
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        return "common"; // ARL was Forge-only, but the redirect pattern works on any loader
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // RegistryHelper: ARL's auto-registration entry point.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$RegistryHelperCompat"
        );

        // registerBlock(block, name): block + auto-generated BlockItem.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerBlock",
            "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerBlock",
            "(Ljava/lang/Object;Lnet/minecraft/world/level/block/Block;Ljava/lang/String;)Lnet/minecraft/world/level/block/Block;"
        );

        // registerBlock(block, name, itemBlock): block with a custom BlockItem.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerBlock",
            "(Lnet/minecraft/block/Block;Ljava/lang/String;Lnet/minecraft/item/BlockItem;)Lnet/minecraft/block/Block;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerBlockWithItem",
            "(Ljava/lang/Object;Lnet/minecraft/world/level/block/Block;Ljava/lang/String;Lnet/minecraft/world/item/BlockItem;)Lnet/minecraft/world/level/block/Block;"
        );

        // registerItem(item, name).
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerItem",
            "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerItem",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/Item;Ljava/lang/String;)Lnet/minecraft/world/item/Item;"
        );

        // registerEntity(entityType, name, mod, ...): entity type + tracking settings.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerEntity",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;III)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerEntity",
            "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;III)V"
        );

        // IModBlock: implementors got auto model loading, item registration, and tab placement.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/block/IModBlock",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$IModBlockCompat"
        );

        // getPrefix() fed the registry-name prefix; modern mods name registries explicitly.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/block/IModBlock",
            "getPrefix",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "getBlockPrefix",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        // createItemBlock() auto-built the matching BlockItem.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/block/IModBlock",
            "createItemBlock",
            "()Lnet/minecraft/item/BlockItem;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "createItemBlock",
            "(Ljava/lang/Object;)Lnet/minecraft/world/item/BlockItem;"
        );

        // IModItem: implementors got auto model loading and tab placement.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/item/IModItem",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$IModItemCompat"
        );

        transformer.registerMethodRedirect(
            "vazkii/autoreglib/item/IModItem",
            "getPrefix",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "getItemPrefix",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        // ModCreativeTab: an auto-populating creative tab, now CreativeModeTab.Builder.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/util/ModCreativeTab",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$ModCreativeTabCompat"
        );

        transformer.registerMethodRedirect(
            "vazkii/autoreglib/util/ModCreativeTab",
            "<init>",
            "(Ljava/lang/String;Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "createCreativeTab",
            "(Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;"
        );

        // RecipeHandler: auto serializer registration, now DeferredRegister<RecipeSerializer>.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/recipe/RecipeHandler",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$RecipeHandlerCompat"
        );

        // addShapedRecipe / addShapelessRecipe: code-defined recipes are gone, recipes are JSON now.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/recipe/RecipeHandler",
            "addShapedRecipe",
            "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "addShapedRecipe",
            "(Lnet/minecraft/world/item/ItemStack;[Ljava/lang/Object;)V"
        );

        transformer.registerMethodRedirect(
            "vazkii/autoreglib/recipe/RecipeHandler",
            "addShapelessRecipe",
            "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "addShapelessRecipe",
            "(Lnet/minecraft/world/item/ItemStack;[Ljava/lang/Object;)V"
        );

        // ModelHandler: generated simple models at runtime when none were in the resource pack.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/client/ModelHandler",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$ModelHandlerCompat"
        );

        // registerModel(item, meta, location): manual model override.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/client/ModelHandler",
            "registerModel",
            "(Lnet/minecraft/item/Item;ILnet/minecraft/util/ResourceLocation;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerModel",
            "(Lnet/minecraft/world/item/Item;ILjava/lang/Object;)V"
        );

        // ModBase: ARL's base mod class wrapping Forge's @SidedProxy system (gone in modern MC).
        transformer.registerClassRedirect(
            "vazkii/autoreglib/base/ModBase",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$ModBaseCompat"
        );

        // ModBase.proxy field (CommonProxy).
        transformer.registerFieldRedirect(
            "vazkii/autoreglib/base/ModBase",
            "proxy",
            "Lvazkii/autoreglib/base/CommonProxy;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "PROXY_COMPAT",
            "Ljava/lang/Object;"
        );

        // NetworkHandler: wrapped SimpleNetworkWrapper with auto-incrementing discriminators.
        transformer.registerClassRedirect(
            "vazkii/autoreglib/network/NetworkHandler",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$NetworkHandlerCompat"
        );

        // register(packetClass, side): now channel.registerPacket() / PayloadRegistrar.
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/network/NetworkHandler",
            "register",
            "(Ljava/lang/Class;Lnet/minecraftforge/fml/relauncher/Side;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerPacket",
            "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.AutoRegLibShim"
        };
    }
}
