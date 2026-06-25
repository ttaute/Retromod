/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Redirects NEI (CodeChickenLib, Forge 1.4.7-1.12.2) API calls to their JEI equivalents,
 * so NEI addon mods register recipes through JEI.
 */
public class NeiApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "NEI -> JEI API Compatibility";
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
        // NEI's static API class -> a JEI @JeiPlugin
        transformer.registerClassRedirect(
            "codechicken/nei/api/API",
            "com/retromod/shim/api/forge/embedded/NeiShim"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "registerRecipeHandler",
            "(Lcodechicken/nei/recipe/ICraftingHandler;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "registerRecipeHandler",
            "(Ljava/lang/Object;)V"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "registerUsageHandler",
            "(Lcodechicken/nei/recipe/ICraftingHandler;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "registerUsageHandler",
            "(Ljava/lang/Object;)V"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "hideItem",
            "(Lnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "hideItem",
            "(Lnet/minecraft/world/item/ItemStack;)V"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "addItemListEntry",
            "(Lnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "addItemListEntry",
            "(Lnet/minecraft/world/item/ItemStack;)V"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "setGuiOffset",
            "(Ljava/lang/Class;II)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "setGuiOffset",
            "(Ljava/lang/Class;II)V"
        );

        // Recipe handler interfaces -> JEI IRecipeCategory
        transformer.registerClassRedirect(
            "codechicken/nei/recipe/IRecipeHandler",
            "com/retromod/shim/api/forge/embedded/NeiShim$RecipeHandlerCompat"
        );

        transformer.registerClassRedirect(
            "codechicken/nei/recipe/ICraftingHandler",
            "com/retromod/shim/api/forge/embedded/NeiShim$RecipeHandlerCompat"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/IRecipeHandler",
            "getRecipeName",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "getRecipeName",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/IRecipeHandler",
            "numRecipes",
            "()I",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "numRecipes",
            "(Ljava/lang/Object;)I"
        );

        // TemplateRecipeHandler (the base class most NEI addons extend) -> JEI IRecipeCategory
        transformer.registerClassRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "com/retromod/shim/api/forge/embedded/NeiShim$TemplateRecipeHandlerCompat"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "loadCraftingRecipes",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "loadCraftingRecipes",
            "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)V"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "loadUsageRecipes",
            "(Lnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "loadUsageRecipes",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)V"
        );

        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "drawBackground",
            "(I)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "drawBackground",
            "(Ljava/lang/Object;I)V"
        );

        // Item info / description pages -> JEI addItemStackInfo
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "addDescription",
            "(Lnet/minecraft/item/ItemStack;[Ljava/lang/String;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "addDescription",
            "(Lnet/minecraft/world/item/ItemStack;[Ljava/lang/String;)V"
        );

        // Item panel / search bar -> JEI ingredient manager + filter
        transformer.registerClassRedirect(
            "codechicken/nei/ItemList",
            "com/retromod/shim/api/forge/embedded/NeiShim$ItemListCompat"
        );

        transformer.registerClassRedirect(
            "codechicken/nei/SearchField",
            "com/retromod/shim/api/forge/embedded/NeiShim$SearchFieldCompat"
        );

        // Container GUI overlay adapter -> JEI IGuiContainerHandler
        transformer.registerClassRedirect(
            "codechicken/nei/api/INEIGuiAdapter",
            "com/retromod/shim/api/forge/embedded/NeiShim$GuiAdapterCompat"
        );

        // CodeChickenLib GuiDraw -> vanilla GuiGraphics (1.20+)
        transformer.registerClassRedirect(
            "codechicken/lib/gui/GuiDraw",
            "com/retromod/shim/api/forge/embedded/NeiShim$GuiDrawCompat"
        );

        transformer.registerMethodRedirect(
            "codechicken/lib/gui/GuiDraw",
            "drawTexturedModalRect",
            "(IIIIII)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "drawTexturedModalRect",
            "(IIIIII)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.NeiShim"
        };
    }
}
