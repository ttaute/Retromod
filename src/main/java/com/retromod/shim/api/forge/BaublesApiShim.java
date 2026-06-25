/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Baubles API -> Curios API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Redirects the dead Baubles API (Forge 1.7.10-1.12.2) to Curios. */
public class BaublesApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "Baubles -> Curios API Compatibility";
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
        return "forge";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "baubles/api/BaublesApi",
            "com/retromod/shim/api/forge/embedded/BaublesShim"
        );

        transformer.registerMethodRedirect(
            "baubles/api/BaublesApi",
            "getBaubles",
            "(Lnet/minecraft/entity/player/EntityPlayer;)Lbaubles/api/cap/IBaublesItemHandler;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getBaubles",
            "(Lnet/minecraft/world/entity/player/Player;)Ljava/lang/Object;"
        );

        // alternate accessor for the same inventory
        transformer.registerMethodRedirect(
            "baubles/api/BaublesApi",
            "getBaublesHandler",
            "(Lnet/minecraft/entity/player/EntityPlayer;)Lbaubles/api/cap/IBaublesItemHandler;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getBaublesHandler",
            "(Lnet/minecraft/world/entity/player/Player;)Ljava/lang/Object;"
        );

        // IBauble -> ICurio
        transformer.registerClassRedirect(
            "baubles/api/IBauble",
            "top/theillusivec4/curios/api/type/capability/ICurio"
        );

        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "onWornTick",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "onWornTick",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"
        );

        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "onEquipped",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "onEquipped",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"
        );

        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "onUnequipped",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "onUnequipped",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"
        );

        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "canEquip",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "canEquip",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"
        );

        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "canUnequip",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "canUnequip",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"
        );

        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "getBaubleType",
            "(Lnet/minecraft/item/ItemStack;)Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getBaubleType",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)Ljava/lang/Object;"
        );

        // BaubleType enum -> Curios string slot types ("necklace", "ring", ...)
        transformer.registerClassRedirect(
            "baubles/api/BaubleType",
            "com/retromod/shim/api/forge/embedded/BaublesShim$BaubleTypeCompat"
        );

        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "AMULET",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "AMULET",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "RING",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "RING",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "BELT",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "BELT",
            "Ljava/lang/Object;"
        );

        // no direct equivalent; map TRINKET to charm
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "TRINKET",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "TRINKET",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "HEAD",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "HEAD",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "BODY",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "BODY",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "CHARM",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "CHARM",
            "Ljava/lang/Object;"
        );

        // IBaublesItemHandler -> Curios stacks handler
        transformer.registerClassRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "com/retromod/shim/api/forge/embedded/BaublesShim$BaublesItemHandlerCompat"
        );

        transformer.registerMethodRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "getStackInSlot",
            "(I)Lnet/minecraft/item/ItemStack;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getStackInSlot",
            "(Ljava/lang/Object;I)Lnet/minecraft/world/item/ItemStack;"
        );

        transformer.registerMethodRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "setStackInSlot",
            "(ILnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "setStackInSlot",
            "(Ljava/lang/Object;ILnet/minecraft/world/item/ItemStack;)V"
        );

        transformer.registerMethodRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "getSlots",
            "()I",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getSlots",
            "(Ljava/lang/Object;)I"
        );

        // capability tokens
        transformer.registerFieldRedirect(
            "baubles/api/cap/BaublesCapabilities",
            "CAPABILITY_BAUBLES",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "CAPABILITY_CURIOS",
            "Ljava/lang/Object;"
        );

        transformer.registerFieldRedirect(
            "baubles/api/cap/BaublesCapabilities",
            "CAPABILITY_ITEM_BAUBLE",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "CAPABILITY_CURIO_ITEM",
            "Ljava/lang/Object;"
        );

        // IRenderBauble -> ICurioRenderer
        transformer.registerClassRedirect(
            "baubles/api/render/IRenderBauble",
            "top/theillusivec4/curios/api/client/ICurioRenderer"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.BaublesShim"
        };
    }
}
