/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.tag;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the Tag -> TagKey migration (1.18.2).
 *
 * In 1.18.2, Minecraft's tag system was completely overhauled. The old
 * {@code net.minecraft.tag} package with Tag, TagGroup, TagManager, and
 * ServerTagManagerHolder was replaced by the registry-based TagKey system
 * in {@code net.minecraft.tags}. The concrete tag classes (BlockTags,
 * ItemTags, etc.) were relocated but kept similar names.
 *
 * Covered changes:
 * - Tag (replaced by TagKey in 1.18.2)
 * - BlockTags, ItemTags, FluidTags, EntityTypeTags (package relocation)
 * - TagGroup (removed, replaced by registry-based tag lookup)
 * - TagManager (removed, replaced by registry-based tag system)
 * - ServerTagManagerHolder (removed, tags are now part of registries)
 */
public class TagPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Tag System Changes";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // Core tag interface replaced by TagKey
            "net/minecraft/tag/Tag",
            "net/minecraft/tag/Tag$Identified",
            "net/minecraft/tag/Tag$Named",

            // Tag collection classes (package relocation)
            "net/minecraft/tag/BlockTags",
            "net/minecraft/tag/ItemTags",
            "net/minecraft/tag/FluidTags",
            "net/minecraft/tag/EntityTypeTags",

            // Removed entirely in 1.18.2+ (registry-based tag system)
            "net/minecraft/tag/TagGroup",
            "net/minecraft/tag/TagManager",
            "net/minecraft/tag/ServerTagManagerHolder"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed — class redirects handle relocations,
        // and the removed management classes have no direct equivalent.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // =====================================================================
        // Tag -> TagKey migration (1.18.2)
        // The Tag interface was replaced by TagKey<T> which is a lightweight
        // registry key reference rather than a holder of tag entries.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/tag/Tag",
            "net/minecraft/tags/TagKey");

        transformer.registerClassRedirect(
            "net/minecraft/tag/Tag$Identified",
            "net/minecraft/tags/TagKey");

        transformer.registerClassRedirect(
            "net/minecraft/tag/Tag$Named",
            "net/minecraft/tags/TagKey");

        // =====================================================================
        // Tag collection classes: net.minecraft.tag.* -> net.minecraft.tags.*
        // These classes were relocated but kept similar structure.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/tag/BlockTags",
            "net/minecraft/tags/BlockTags");

        transformer.registerClassRedirect(
            "net/minecraft/tag/ItemTags",
            "net/minecraft/tags/ItemTags");

        transformer.registerClassRedirect(
            "net/minecraft/tag/FluidTags",
            "net/minecraft/tags/FluidTags");

        transformer.registerClassRedirect(
            "net/minecraft/tag/EntityTypeTags",
            "net/minecraft/tags/EntityTypeTags");

        // =====================================================================
        // Removed tag management classes
        // TagGroup, TagManager, and ServerTagManagerHolder were removed when
        // tags became part of the registry system. There are no direct
        // replacements — mods need to use Registry.getOrCreateTag() or
        // BuiltInRegistries to look up tags. We redirect to TagKey as a
        // best-effort to prevent ClassNotFoundException.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/tag/TagGroup",
            "net/minecraft/tags/TagKey");

        transformer.registerClassRedirect(
            "net/minecraft/tag/TagManager",
            "net/minecraft/tags/TagKey");

        transformer.registerClassRedirect(
            "net/minecraft/tag/ServerTagManagerHolder",
            "net/minecraft/tags/TagKey");

        // =====================================================================
        // Method redirects for common tag usage patterns
        // Old: Tag.contains(entry) -> TagKey is just a key, need registry lookup
        // Old: BlockTags.getTagGroup() -> removed, use registry
        // =====================================================================

        // Tag.contains(T) was a direct membership check. In the new system,
        // you check via Holder#is(TagKey). This method redirect catches the
        // old pattern at the bytecode level.
        transformer.registerMethodRedirect(
            "net/minecraft/tag/Tag", "contains",
            "(Ljava/lang/Object;)Z",
            "net/minecraft/tags/TagKey", "equals",
            "(Ljava/lang/Object;)Z");
    }
}
