/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.19.3 to run on 1.19.4.
 * Handles the DamageSource rework to use DamageType registry and recipe API changes.
 */
public class Fabric_1_19_3_to_1_19_4 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19.3 to 1.19.4"; }
    @Override public String getSourceVersion() { return "1.19.3"; }
    @Override public String getTargetVersion() { return "1.19.4"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // DamageSource reworked to use DamageType registry
        transformer.registerMethodRedirect(
            "net/minecraft/entity/damage/DamageSource", "<init>",
            "(Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "create",
            "(Ljava/lang/String;)Lnet/minecraft/entity/damage/DamageSource;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "GENERIC",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "GENERIC"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "FALL",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "FALL"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "DROWN",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "DROWN"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "IN_FIRE",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "IN_FIRE"
        );
        // Recipe changes
        transformer.registerMethodRedirect(
            "net/minecraft/recipe/RecipeManager", "getFirstMatch",
            "(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/Optional;",
            "com/retromod/shim/fabric/embedded/RecipeShim", "getFirstMatch",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Optional;"
        );

        // ItemTransforms$TransformType renamed to ItemDisplayContext (1.19.4 display context rework)
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/block/model/ItemTransforms$TransformType",
            "net/minecraft/world/item/ItemDisplayContext"
        );
        // Wearable interface renamed to Equipable
        transformer.registerClassRedirect(
            "net/minecraft/world/item/Wearable",
            "net/minecraft/world/item/Equipable"
        );
        // Biome.isHumid() renamed to hasPrecipitation()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/biome/Biome", "isHumid",
            "()Z",
            "net/minecraft/world/level/biome/Biome", "hasPrecipitation",
            "()Z"
        );
        // AbstractWidget.renderButton() renamed to renderWidget()
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "renderButton",
            "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            "net/minecraft/client/gui/components/AbstractWidget", "renderWidget",
            "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
        );
        // Entity.rideableUnderWater() renamed to dismountsUnderwater()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "rideableUnderWater",
            "()Z",
            "net/minecraft/world/entity/Entity", "dismountsUnderwater",
            "()Z"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
