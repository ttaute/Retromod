/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.nbt;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for NBT (Named Binary Tag) class and method renames.
 *
 * The NBT API was completely renamed during the Flattening (1.13) and
 * subsequent updates. Old Forge mods use the legacy NBTTag* naming
 * convention, while modern Minecraft uses shorter Mojang-mapped names
 * (CompoundTag, ListTag, etc.).
 *
 * In addition to class renames, several frequently-used methods were
 * also renamed (e.g. hasKey -> contains, setInteger -> putInt). This
 * provider handles both class-level and method-level redirects.
 *
 * Covers:
 * - All NBTTag* class renames to modern Tag equivalents
 * - NBTBase -> Tag, NBTUtil -> NbtUtils, CompressedStreamTools -> NbtIo
 * - Method renames on CompoundTag (hasKey, setTag, getCompoundTag, etc.)
 * - Method renames on ListTag (tagCount, getCompoundTagAt)
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
        // Pure redirects — no stub implementations needed.
        // All old NBT classes and methods map to modern equivalents.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // ---- Class redirects ----

        // Tag type classes
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

        // Base type and utility classes
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTBase", "net/minecraft/nbt/Tag");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTUtil", "net/minecraft/nbt/NbtUtils");
        transformer.registerClassRedirect(
            "net/minecraft/nbt/CompressedStreamTools", "net/minecraft/nbt/NbtIo");

        // ---- Method redirects on CompoundTag (formerly NBTTagCompound) ----
        // After class redirect, the owner is already CompoundTag, so we redirect
        // method names on the new owner.

        String compound = "net/minecraft/nbt/CompoundTag";

        // hasKey(String) -> contains(String)
        transformer.registerMethodRedirect(
            compound, "hasKey", "(Ljava/lang/String;)Z",
            compound, "contains", "(Ljava/lang/String;)Z");

        // setTag(String, Tag) -> put(String, Tag)
        transformer.registerMethodRedirect(
            compound, "setTag", "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)V",
            compound, "put", "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;");

        // getCompoundTag(String) -> getCompound(String)
        transformer.registerMethodRedirect(
            compound, "getCompoundTag", "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;",
            compound, "getCompound", "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;");

        // getTagList(String, int) -> getList(String, int)
        transformer.registerMethodRedirect(
            compound, "getTagList", "(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;",
            compound, "getList", "(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;");

        // setString(String, String) -> putString(String, String)
        transformer.registerMethodRedirect(
            compound, "setString", "(Ljava/lang/String;Ljava/lang/String;)V",
            compound, "putString", "(Ljava/lang/String;Ljava/lang/String;)V");

        // setInteger(String, int) -> putInt(String, int)
        transformer.registerMethodRedirect(
            compound, "setInteger", "(Ljava/lang/String;I)V",
            compound, "putInt", "(Ljava/lang/String;I)V");

        // setFloat(String, float) -> putFloat(String, float)
        transformer.registerMethodRedirect(
            compound, "setFloat", "(Ljava/lang/String;F)V",
            compound, "putFloat", "(Ljava/lang/String;F)V");

        // setDouble(String, double) -> putDouble(String, double)
        transformer.registerMethodRedirect(
            compound, "setDouble", "(Ljava/lang/String;D)V",
            compound, "putDouble", "(Ljava/lang/String;D)V");

        // setBoolean(String, boolean) -> putBoolean(String, boolean)
        transformer.registerMethodRedirect(
            compound, "setBoolean", "(Ljava/lang/String;Z)V",
            compound, "putBoolean", "(Ljava/lang/String;Z)V");

        // getInteger(String) -> getInt(String)
        transformer.registerMethodRedirect(
            compound, "getInteger", "(Ljava/lang/String;)I",
            compound, "getInt", "(Ljava/lang/String;)I");

        // ---- Method redirects on ListTag (formerly NBTTagList) ----

        String list = "net/minecraft/nbt/ListTag";

        // tagCount() -> size()
        transformer.registerMethodRedirect(
            list, "tagCount", "()I",
            list, "size", "()I");

        // getCompoundTagAt(int) -> getCompound(int)
        transformer.registerMethodRedirect(
            list, "getCompoundTagAt", "(I)Lnet/minecraft/nbt/CompoundTag;",
            list, "getCompound", "(I)Lnet/minecraft/nbt/CompoundTag;");
    }
}
