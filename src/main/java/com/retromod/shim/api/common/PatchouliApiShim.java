/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** Patchouli (in-game guidebook) API shim: bridges book/template/multiblock API changes. */
public class PatchouliApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Patchouli API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "common";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI",
            "instance",
            "Lvazkii/patchouli/api/PatchouliAPI$IPatchouliAPI;",
            "vazkii/patchouli/api/PatchouliAPI",
            "get",
            "()Lvazkii/patchouli/api/PatchouliAPI$IPatchouliAPI;"
        );

        // openBookGUI dropped its player arg and became client-side only
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "openBookGUI",
            "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceLocation;)V",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "openBookGUI",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        );

        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "registerMultiblock",
            "(Lnet/minecraft/resources/ResourceLocation;Lvazkii/patchouli/api/IMultiblock;)Lvazkii/patchouli/api/IMultiblock;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "registerMultiblock",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "makeMultiblock",
            "([Ljava/lang/String;[Ljava/lang/Object;)Lvazkii/patchouli/api/IMultiblock;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "makeMultiblock",
            "(Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "registerTemplateAsBuiltin",
            "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "registerTemplate",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/Supplier;)V"
        );

        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "getBook",
            "(Lnet/minecraft/resources/ResourceLocation;)Lvazkii/patchouli/api/IBook;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "getBook",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "vazkii/patchouli/api/IStateMatcher",
            "vazkii/patchouli/api/IStateMatcher"
        );

        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "predicateMatcher",
            "(Lnet/minecraft/world/level/block/Block;Ljava/util/function/Predicate;)Lvazkii/patchouli/api/IStateMatcher;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "predicateMatcher",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/Predicate;)Ljava/lang/Object;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.PatchouliShim"
        };
    }
}
