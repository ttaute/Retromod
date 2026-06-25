/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Fabric Shield Lib API -> Modern Rendering Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Redirects FabricShieldLib (CrimsonDawn45, Fabric 1.16-1.19, unmaintained since the
 * 1.19 rendering overhaul) onto the embedded {@code FabricShieldLibShim} compat shapes.
 */
public class FabricShieldLibApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "Fabric Shield Lib -> Modern Rendering Compatibility";
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
        return "fabric";
    }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldItem",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$FabricShieldItemCompat"
        );

        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldItem",
            "<init>",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "createShieldItem",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricBannerShieldItem",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$FabricBannerShieldItemCompat"
        );

        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricBannerShieldItem",
            "<init>",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "createBannerShieldItem",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)Ljava/lang/Object;"
        );

        // entity model maps onto vanilla ShieldModel (1.20+)
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldEntityModel",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ShieldModelCompat"
        );

        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldEntityModel",
            "<init>",
            "(Lnet/minecraft/client/model/ModelPart;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "createShieldModel",
            "(Lnet/minecraft/client/model/ModelPart;)Ljava/lang/Object;"
        );

        // lib initializers (client + common)
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLibClient",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ClientInitCompat"
        );

        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLib",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$CommonInitCompat"
        );

        // render registration (model layer + texture)
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLibClient",
            "registerShieldModelLayer",
            "(Lnet/minecraft/item/Item;Lnet/minecraft/client/render/entity/model/EntityModelLayer;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "registerShieldModelLayer",
            "(Lnet/minecraft/item/Item;Ljava/lang/Object;)V"
        );

        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLibClient",
            "registerShieldTexture",
            "(Lnet/minecraft/item/Item;Lnet/minecraft/util/Identifier;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "registerShieldTexture",
            "(Lnet/minecraft/item/Item;Lnet/minecraft/util/Identifier;)V"
        );

        // FabricShieldLib.isShield(stack)
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLib",
            "isShield",
            "(Lnet/minecraft/item/ItemStack;)Z",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "isShield",
            "(Lnet/minecraft/item/ItemStack;)Z"
        );

        // shield event callbacks
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldBlockCallback",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ShieldBlockCallbackCompat"
        );

        transformer.registerFieldRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldBlockCallback",
            "EVENT",
            "Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "SHIELD_BLOCK_EVENT",
            "Ljava/lang/Object;"
        );

        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldDisabledCallback",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ShieldDisabledCallbackCompat"
        );

        transformer.registerFieldRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldDisabledCallback",
            "EVENT",
            "Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "SHIELD_DISABLED_EVENT",
            "Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.FabricShieldLibShim"
        };
    }
}
