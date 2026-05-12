/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.19.2 to 1.19.3 shim - Registry and Creative Tab rework.
 * The registry system was restructured from Registry to BuiltInRegistries.
 * Creative mode tabs were completely overhauled with a new builder pattern.
 * Forge registry events were also restructured, and GUI rendering moved
 * from PoseStack to GuiGraphics.
 */
public class Forge_1_19_2_to_1_19_3 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.19.2 to 1.19.3"; }
    @Override public String getSourceVersion() { return "1.19.2"; }
    @Override public String getTargetVersion() { return "1.19.3"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // INTENTIONALLY NOT REMAPPING net.minecraft.core.Registry.
        //
        // Earlier this shim had:
        //
        //     transformer.registerClassRedirect(
        //         "net/minecraft/core/Registry",
        //         "net/minecraft/core/registries/BuiltInRegistries"
        //     );
        //
        // The intent was to migrate static-field accesses like
        // Registry.SOUND_EVENT (where SOUND_EVENT lived on the Registry
        // class pre-1.19.3) to BuiltInRegistries.SOUND_EVENT (where it
        // lives now). That migration is real, but blanket-renaming the
        // CLASS was the wrong way to express it: the Registry class still
        // exists in modern MC, and remapping all references to it to
        // BuiltInRegistries breaks every TYPE use (e.g. a field declared
        // as Registry<SoundEvent> ends up as BuiltInRegistries<SoundEvent>,
        // which doesn't exist).
        //
        // Symptom (surfaced by retromod-test-mod's Test 14):
        //   NoSuchFieldError: Class net.minecraft.core.registries.BuiltInRegistries
        //   does not have member field 'BuiltInRegistries SOUND_EVENT'
        //
        // The right way to express "static field moved" is field-by-field
        // FieldRedirects. The handful of statics that need migrating
        // (BLOCK, ITEM, ENTITY_TYPE, FLUID, SOUND_EVENT, ...) should each
        // get their own redirect; that's a separate piece of work.

        transformer.registerMethodRedirect(
            "net/minecraft/world/item/CreativeModeTab", "builder",
            "(Lnet/minecraft/world/item/CreativeModeTab$Row;I)Lnet/minecraft/world/item/CreativeModeTab$Builder;",
            "com/retromod/shim/forge/embedded/CreativeTabShim", "builder",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );
        // Forge registry event changes
        transformer.registerClassRedirect(
            "net/minecraftforge/event/RegistryEvent",
            "net/minecraftforge/registries/RegisterEvent"
        );
        transformer.registerMethodRedirect(
            "net/minecraftforge/client/event/RenderGuiOverlayEvent", "getMatrixStack",
            "()Lcom/mojang/blaze3d/vertex/PoseStack;",
            "com/retromod/shim/forge/embedded/RenderShim", "getGuiGraphics",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Widget interface renamed to Renderable in 1.19.3
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/components/Widget",
            "net/minecraft/client/gui/components/Renderable"
        );

        // Loot table provider classes renamed to sub-provider pattern
        transformer.registerClassRedirect(
            "net/minecraft/data/loot/BlockLoot",
            "net/minecraft/data/loot/BlockLootSubProvider"
        );
        transformer.registerClassRedirect(
            "net/minecraft/data/loot/EntityLoot",
            "net/minecraft/data/loot/EntityLootSubProvider"
        );

        // ForgeRegistries field renames in 1.19.3
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries", "CONTAINERS",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraftforge/registries/ForgeRegistries", "MENU_TYPES",
            "Lnet/minecraftforge/registries/IForgeRegistry;"
        );
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries", "BLOCK_ENTITIES",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraftforge/registries/ForgeRegistries", "BLOCK_ENTITY_TYPES",
            "Lnet/minecraftforge/registries/IForgeRegistry;"
        );
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries", "ENTITIES",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraftforge/registries/ForgeRegistries", "ENTITY_TYPES",
            "Lnet/minecraftforge/registries/IForgeRegistry;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
