/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * owo-lib compatibility shim: config (0.8 -> 0.9), UI components (0.9 -> 0.11), registration (0.11 -> 0.12).
 */
public class OwoLibApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "owo-lib API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "0.8.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "0.12.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "io/wispforest/owo/registration/RegistryHelper",
            "io/wispforest/owo/registration/reflect/AutoRegistryContainer"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/itemgroup/OwoItemGroup",
            "io/wispforest/owo/itemgroup/OwoItemGroup"
        );
        
        transformer.registerMethodRedirect(
            "io/wispforest/owo/itemgroup/OwoItemGroup",
            "builder",
            "(Lnet/minecraft/util/Identifier;Ljava/util/function/Supplier;)Lio/wispforest/owo/itemgroup/OwoItemGroup$Builder;",
            "com/retromod/shim/api/fabric/embedded/OwoShim",
            "itemGroupBuilder",
            "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/config/ConfigWrapper",
            "io/wispforest/owo/config/ConfigWrapper"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/config/Option",
            "io/wispforest/owo/config/Option"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/config/annotation/Config",
            "io/wispforest/owo/config/annotation/Config"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/config/annotation/Modmenu",
            "io/wispforest/owo/config/annotation/Modmenu"
        );

        // Component moved package; layouts/widgets kept in place
        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/component/Component",
            "io/wispforest/owo/ui/core/Component"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/base/BaseComponent",
            "io/wispforest/owo/ui/core/Component"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/container/FlowLayout",
            "io/wispforest/owo/ui/container/FlowLayout"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/container/GridLayout",
            "io/wispforest/owo/ui/container/GridLayout"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/container/ScrollContainer",
            "io/wispforest/owo/ui/container/ScrollContainer"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/component/ButtonComponent",
            "io/wispforest/owo/ui/component/ButtonComponent"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/component/LabelComponent",
            "io/wispforest/owo/ui/component/LabelComponent"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/component/TextBoxComponent",
            "io/wispforest/owo/ui/component/TextBoxComponent"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/base/BaseOwoScreen",
            "io/wispforest/owo/ui/base/BaseOwoScreen"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/ui/base/BaseOwoHandledScreen",
            "io/wispforest/owo/ui/base/BaseOwoHandledScreen"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/network/OwoNetChannel",
            "io/wispforest/owo/network/OwoNetChannel"
        );

        transformer.registerMethodRedirect(
            "io/wispforest/owo/network/OwoNetChannel",
            "registerServerbound",
            "(Ljava/lang/Class;Lio/wispforest/owo/network/OwoNetChannel$ServerHandler;)V",
            "com/retromod/shim/api/fabric/embedded/OwoShim",
            "registerServerbound",
            "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)V"
        );
        
        transformer.registerMethodRedirect(
            "io/wispforest/owo/network/OwoNetChannel",
            "registerClientbound",
            "(Ljava/lang/Class;Lio/wispforest/owo/network/OwoNetChannel$ClientHandler;)V",
            "com/retromod/shim/api/fabric/embedded/OwoShim",
            "registerClientbound",
            "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)V"
        );

        // serialization: Endec extracted to its own io.wispforest.endec module
        transformer.registerClassRedirect(
            "io/wispforest/owo/serialization/Endec",
            "io/wispforest/endec/Endec"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/serialization/endec/BuiltInEndecs",
            "io/wispforest/endec/impl/BuiltInEndecs"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/particles/ClientParticles",
            "io/wispforest/owo/particles/ClientParticles"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/util/Observable",
            "io/wispforest/owo/util/Observable"
        );

        transformer.registerClassRedirect(
            "io/wispforest/owo/util/Pond",
            "io/wispforest/owo/util/pond/OwoBlockEntityExtension"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.OwoShim"
        };
    }
}
