/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.19.4 to run on 1.20.
 * Handles the removal of the Material system and sign block entity rework.
 */
public class Fabric_1_19_4_to_1_20 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19.4 to 1.20"; }
    @Override public String getSourceVersion() { return "1.19.4"; }
    @Override public String getTargetVersion() { return "1.20"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Material system removed
        transformer.registerMethodRedirect(
            "net/minecraft/block/AbstractBlock$Settings", "of",
            "(Lnet/minecraft/block/Material;)Lnet/minecraft/block/AbstractBlock$Settings;",
            "net/minecraft/block/AbstractBlock$Settings", "create",
            "()Lnet/minecraft/block/AbstractBlock$Settings;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/block/AbstractBlock$Settings", "of",
            "(Lnet/minecraft/block/Material;Lnet/minecraft/block/MapColor;)Lnet/minecraft/block/AbstractBlock$Settings;",
            "com/retromod/shim/fabric/embedded/MaterialShim", "createSettings",
            "(Ljava/lang/Object;Lnet/minecraft/block/MapColor;)Lnet/minecraft/block/AbstractBlock$Settings;"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/Material",
            "com/retromod/shim/fabric/embedded/MaterialShim"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/MaterialColor",
            "net/minecraft/block/MapColor"
        );
        // Sign changes
        transformer.registerMethodRedirect(
            "net/minecraft/block/entity/SignBlockEntity", "getTextOnRow",
            "(I)Lnet/minecraft/text/Text;",
            "com/retromod/shim/fabric/embedded/SignShim", "getTextOnRow",
            "(Ljava/lang/Object;I)Lnet/minecraft/text/Text;"
        );

        // GuiComponent merged into GuiGraphics (biggest rendering change in 1.20)
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiComponent",
            "net/minecraft/client/gui/GuiGraphics"
        );
        // SuspiciousSandBlock renamed to BrushableBlock (archaeology generalization)
        transformer.registerClassRedirect(
            "net/minecraft/world/level/block/SuspiciousSandBlock",
            "net/minecraft/world/level/block/BrushableBlock"
        );
        // LayerLightEngine renamed to LightEngine
        transformer.registerClassRedirect(
            "net/minecraft/world/level/lighting/LayerLightEngine",
            "net/minecraft/world/level/lighting/LightEngine"
        );
        // MaterialColor renamed to MapColor (Mojang mapping)
        transformer.registerClassRedirect(
            "net/minecraft/world/level/material/MaterialColor",
            "net/minecraft/world/level/block/MapColor"
        );
        // Entity.outOfWorld() renamed to fellOutOfWorld()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "outOfWorld",
            "()V",
            "net/minecraft/world/entity/Entity", "fellOutOfWorld",
            "()V"
        );
        // Entity.checkOutOfWorld() renamed to checkBelowWorld()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "checkOutOfWorld",
            "()V",
            "net/minecraft/world/entity/Entity", "checkBelowWorld",
            "()V"
        );
        // ServerPlayer.getLevel() renamed to serverLevel()
        transformer.registerMethodRedirect(
            "net/minecraft/server/level/ServerPlayer", "getLevel",
            "()Lnet/minecraft/server/level/ServerLevel;",
            "net/minecraft/server/level/ServerPlayer", "serverLevel",
            "()Lnet/minecraft/server/level/ServerLevel;"
        );
        // ItemStack.sameItem() renamed to isSameItem()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/ItemStack", "sameItem",
            "(Lnet/minecraft/world/item/ItemStack;)Z",
            "net/minecraft/world/item/ItemStack", "isSameItem",
            "(Lnet/minecraft/world/item/ItemStack;)Z"
        );
        // LivingEntity.wasKilled() renamed to killedEntity()
        // NOTE: descriptor needs verification - depends on exact method signature
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/LivingEntity", "wasKilled",
            "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V",
            "net/minecraft/world/entity/LivingEntity", "killedEntity",
            "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
