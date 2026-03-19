/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.item;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for item class renames from The Flattening (1.13+).
 *
 * Pre-flattening mods reference item classes in {@code net.minecraft.item}
 * with legacy names (e.g., ItemBlock, ItemSword, ItemFood). These were renamed
 * and relocated to {@code net.minecraft.world.item} in Mojang mappings.
 *
 * Also covers ItemStack method renames from the NBT-to-component migration:
 * - getTagCompound/setTagCompound/hasTagCompound (pre-1.20.5 NBT API)
 *   redirected to getTag/setTag/hasTag (pre-component system equivalents)
 */
public class ItemPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Item Class Renames";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/item/ItemBlock",
            "net/minecraft/item/ItemFood",
            "net/minecraft/item/ItemTool",
            "net/minecraft/item/ItemSword",
            "net/minecraft/item/ItemPickaxe",
            "net/minecraft/item/ItemAxe",
            "net/minecraft/item/ItemSpade",
            "net/minecraft/item/ItemHoe",
            "net/minecraft/item/ItemArmor",
            "net/minecraft/item/ItemBow",
            "net/minecraft/item/ItemPotion",
            "net/minecraft/item/ItemBucket",
            "net/minecraft/item/ItemDye",
            "net/minecraft/item/ItemEnderPearl",
            "net/minecraft/item/ItemRecord",
            "net/minecraft/item/ItemSkull",
            "net/minecraft/item/ItemStack"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed - pure class and method redirects
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // =====================================================================
        // Item class renames: net.minecraft.item.* -> net.minecraft.world.item.*
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemBlock",
            "net/minecraft/world/item/BlockItem");

        // ItemFood -> Item (food properties moved to Item.Properties in 1.13+)
        transformer.registerClassRedirect(
            "net/minecraft/item/ItemFood",
            "net/minecraft/world/item/Item");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemTool",
            "net/minecraft/world/item/DiggerItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemSword",
            "net/minecraft/world/item/SwordItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemPickaxe",
            "net/minecraft/world/item/PickaxeItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemAxe",
            "net/minecraft/world/item/AxeItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemSpade",
            "net/minecraft/world/item/ShovelItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemHoe",
            "net/minecraft/world/item/HoeItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemArmor",
            "net/minecraft/world/item/ArmorItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemBow",
            "net/minecraft/world/item/BowItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemPotion",
            "net/minecraft/world/item/PotionItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemBucket",
            "net/minecraft/world/item/BucketItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemDye",
            "net/minecraft/world/item/DyeItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemEnderPearl",
            "net/minecraft/world/item/EnderpearlItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemRecord",
            "net/minecraft/world/item/RecordItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemSkull",
            "net/minecraft/world/item/StandingAndWallBlockItem");

        transformer.registerClassRedirect(
            "net/minecraft/item/ItemStack",
            "net/minecraft/world/item/ItemStack");

        // =====================================================================
        // ItemStack method renames: pre-1.20.5 NBT API -> intermediate API
        // getTagCompound/setTagCompound/hasTagCompound were the very old names;
        // getTag/setTag/hasTag are the intermediate names before the 1.20.5
        // component system. The class redirect above handles the package move,
        // these method redirects handle the method name changes.
        // =====================================================================

        // getTagCompound() -> getTag() (pre-component system)
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "getTagCompound",
            "()Lnet/minecraft/nbt/NBTTagCompound;",
            "net/minecraft/world/item/ItemStack", "getTag",
            "()Lnet/minecraft/nbt/CompoundTag;");

        // setTagCompound(NBTTagCompound) -> setTag(CompoundTag) (pre-component system)
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "setTagCompound",
            "(Lnet/minecraft/nbt/NBTTagCompound;)V",
            "net/minecraft/world/item/ItemStack", "setTag",
            "(Lnet/minecraft/nbt/CompoundTag;)V");

        // hasTagCompound() -> hasTag() (pre-component system)
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "hasTagCompound",
            "()Z",
            "net/minecraft/world/item/ItemStack", "hasTag",
            "()Z");

        // getItem() -> getItem() (same name, but return type hierarchy changed;
        // the class redirect handles the package, this ensures correct resolution)
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "getItem",
            "()Lnet/minecraft/item/Item;",
            "net/minecraft/world/item/ItemStack", "getItem",
            "()Lnet/minecraft/world/item/Item;");
    }
}
