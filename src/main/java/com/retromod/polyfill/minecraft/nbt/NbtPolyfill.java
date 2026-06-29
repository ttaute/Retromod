/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.nbt;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Redirects the legacy NBTTag* class and method names to their modern
 * Mojang-mapped equivalents (CompoundTag, ListTag, contains, putInt, ...),
 * renamed during the 1.13 Flattening and later updates.
 */
public class NbtPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft NBT API Renames";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/nbt/NBTTagCompound",
            "net/minecraft/nbt/NBTTagList",
            "net/minecraft/nbt/NBTTagString",
            "net/minecraft/nbt/NBTTagInt",
            "net/minecraft/nbt/NBTTagFloat",
            "net/minecraft/nbt/NBTTagDouble",
            "net/minecraft/nbt/NBTTagLong",
            "net/minecraft/nbt/NBTTagShort",
            "net/minecraft/nbt/NBTTagByte",
            "net/minecraft/nbt/NBTTagByteArray",
            "net/minecraft/nbt/NBTTagIntArray",
            "net/minecraft/nbt/NBTBase",
            "net/minecraft/nbt/NBTUtil",
            "net/minecraft/nbt/CompressedStreamTools"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // pure redirects, no stub implementations
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagCompound", "net/minecraft/nbt/CompoundTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagList", "net/minecraft/nbt/ListTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagString", "net/minecraft/nbt/StringTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagInt", "net/minecraft/nbt/IntTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagFloat", "net/minecraft/nbt/FloatTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagDouble", "net/minecraft/nbt/DoubleTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagLong", "net/minecraft/nbt/LongTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagShort", "net/minecraft/nbt/ShortTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagByte", "net/minecraft/nbt/ByteTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagByteArray", "net/minecraft/nbt/ByteArrayTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagIntArray", "net/minecraft/nbt/IntArrayTag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTBase", "net/minecraft/nbt/Tag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTUtil", "net/minecraft/nbt/NbtUtils");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/CompressedStreamTools", "net/minecraft/nbt/NbtIo");

        // method redirects keyed on the post-rename owner CompoundTag
        String compound = "net/minecraft/nbt/CompoundTag";
        transformer.registerMethodRedirect(
            compound, "hasKey", "(Ljava/lang/String;)Z",
            compound, "contains", "(Ljava/lang/String;)Z");
        transformer.registerMethodRedirect(
            compound, "setTag", "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)V",
            compound, "put", "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;");
        transformer.registerMethodRedirect(
            compound, "getCompoundTag", "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;",
            compound, "getCompound", "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;");
        transformer.registerMethodRedirect(
            compound, "getTagList", "(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;",
            compound, "getList", "(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;");
        transformer.registerMethodRedirect(
            compound, "setString", "(Ljava/lang/String;Ljava/lang/String;)V",
            compound, "putString", "(Ljava/lang/String;Ljava/lang/String;)V");
        transformer.registerMethodRedirect(
            compound, "setInteger", "(Ljava/lang/String;I)V",
            compound, "putInt", "(Ljava/lang/String;I)V");
        transformer.registerMethodRedirect(
            compound, "setFloat", "(Ljava/lang/String;F)V",
            compound, "putFloat", "(Ljava/lang/String;F)V");
        transformer.registerMethodRedirect(
            compound, "setDouble", "(Ljava/lang/String;D)V",
            compound, "putDouble", "(Ljava/lang/String;D)V");
        transformer.registerMethodRedirect(
            compound, "setBoolean", "(Ljava/lang/String;Z)V",
            compound, "putBoolean", "(Ljava/lang/String;Z)V");
        transformer.registerMethodRedirect(
            compound, "getInteger", "(Ljava/lang/String;)I",
            compound, "getInt", "(Ljava/lang/String;)I");

        String list = "net/minecraft/nbt/ListTag";
        transformer.registerMethodRedirect(
            list, "tagCount", "()I",
            list, "size", "()I");
        transformer.registerMethodRedirect(
            list, "getCompoundTagAt", "(I)Lnet/minecraft/nbt/CompoundTag;",
            list, "getCompound", "(I)Lnet/minecraft/nbt/CompoundTag;");
    }
}
