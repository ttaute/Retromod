/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Cloth Config API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Cloth Config API shim (v4.x through v11.x): bridges Builder/entry-builder API changes. */
public class ClothConfigApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Cloth Config API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "4.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "11.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // old package was me.shedaniel.clothconfig, now clothconfig2
        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig/api/ConfigBuilder",
            "me/shedaniel/clothconfig2/api/ConfigBuilder"
        );

        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig/api/ConfigCategory",
            "me/shedaniel/clothconfig2/api/ConfigCategory"
        );

        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig/api/ConfigEntryBuilder",
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder"
        );

        // v6.x (MC 1.17-1.19) -> v11.x
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigBuilder",
            "getOrCreateCategory",
            "(Lnet/minecraft/text/Text;)Lme/shedaniel/clothconfig2/api/ConfigCategory;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "getOrCreateCategoryCompat",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;)Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "me/shedaniel/clothconfig2/gui/entries/SubCategoryListEntry$Builder",
            "me/shedaniel/clothconfig2/impl/builders/SubCategoryBuilder"
        );

        // setTooltipSupplier was removed in later versions
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/AbstractConfigListEntry",
            "setTooltipSupplier",
            "(Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setTooltipSupplierCompat",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;)V"
        );

        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/AbstractConfigListEntry",
            "setErrorSupplier",
            "(Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setErrorSupplierCompat",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;)V"
        );

        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigBuilder",
            "setSavingRunnable",
            "(Ljava/lang/Runnable;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setSavingRunnableCompat",
            "(Ljava/lang/Object;Ljava/lang/Runnable;)V"
        );

        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigBuilder",
            "create",
            "()Lme/shedaniel/clothconfig2/api/ConfigBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "createBuilder",
            "()Ljava/lang/Object;"
        );

        // startStrField was renamed to startTextField
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startStrField",
            "(Lnet/minecraft/text/Text;Ljava/lang/String;)Lme/shedaniel/clothconfig2/impl/builders/StringFieldBuilder;",
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startTextField",
            "(Lnet/minecraft/text/Text;Ljava/lang/String;)Lme/shedaniel/clothconfig2/impl/builders/StringFieldBuilder;"
        );

        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startBooleanToggle",
            "(Lnet/minecraft/text/Text;Z)Lme/shedaniel/clothconfig2/impl/builders/BooleanToggleBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "startBooleanToggle",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;Z)Ljava/lang/Object;"
        );

        // setTooltip went from Optional to varargs
        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/AbstractConfigListEntry",
            "setTooltip",
            "(Ljava/util/Optional;)V",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "setTooltipCompat",
            "(Ljava/lang/Object;Ljava/util/Optional;)V"
        );

        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startDropdownMenu",
            "(Lnet/minecraft/text/Text;Ljava/lang/Object;)Lme/shedaniel/clothconfig2/impl/builders/DropdownMenuBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "startDropdownMenu",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "me/shedaniel/clothconfig2/api/ConfigEntryBuilder",
            "startColorField",
            "(Lnet/minecraft/text/Text;I)Lme/shedaniel/clothconfig2/impl/builders/ColorFieldBuilder;",
            "com/retromod/shim/api/fabric/embedded/ClothConfigShim",
            "startColorField",
            "(Ljava/lang/Object;Lnet/minecraft/text/Text;I)Ljava/lang/Object;"
        );

        // very old mods constructed LiteralText/TranslatableText directly
        transformer.registerClassRedirect(
            "net/minecraft/text/LiteralText",
            "com/retromod/shim/api/fabric/embedded/TextShim$LiteralTextShim"
        );
        
        transformer.registerClassRedirect(
            "net/minecraft/text/TranslatableText",
            "com/retromod/shim/api/fabric/embedded/TextShim$TranslatableTextShim"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.ClothConfigShim",
            "com.retromod.shim.api.fabric.embedded.TextShim"
        };
    }
}
