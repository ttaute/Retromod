/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual NeoForge changes documented at:
 * https://neoforged.net/news/21.11release/
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for NeoForge mods built for 1.21.10 to run on 1.21.11.
 * 
 * 1.21.11 is the LAST obfuscated Minecraft version!
 * 
 * Major changes addressed:
 * - ResourceLocation renamed to Identifier (Mojang change)
 * - javax.annotation.Nullable -> org.jspecify.annotations.Nullable
 * - RenderTypes class (previously RenderType methods)
 * - Baked quad changes (light_emission key)
 */
public class NeoForge_1_21_10_to_1_21_11 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "NeoForge 1.21.10 to 1.21.11";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.10";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.11";
    }
    
    @Override
    public String getModLoaderType() {
        return "neoforge";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        
        // ============================================================
        // RESOURCELOCATION -> IDENTIFIER
        // Mojang renamed ResourceLocation to Identifier
        // ============================================================
        
        // Class redirect for the type itself
        transformer.registerClassRedirect(
            "net/minecraft/resources/ResourceLocation",
            "net/minecraft/resources/Identifier"
        );

        // ============================================================
        // LOOTCONTEXTPARAMSET -> CONTEXTKEYSET  (#51)
        // The SINGULAR LootContextParamSet was renamed AND moved out of the
        // loot/parameters package to net.minecraft.util.context.ContextKeySet
        // by 1.21.11 (verified: 1.21.11 has util/context/ContextKeySet[$Builder]
        // and no loot/parameters/LootContextParamSet). The PLURAL
        // LootContextParamSets stays put — exact-name redirects below don't
        // touch it. Illagers Wear Armor's IWALootTables.<clinit> referenced
        // LootContextParamSet$Builder and died with ClassNotFoundException
        // because this rename wasn't mapped (it crashed only because the host
        // was also being misdetected, gating out this whole shim; both are now
        // fixed — detection in RetromodNeoForge, the rename here).
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/world/level/storage/loot/parameters/LootContextParamSet",
            "net/minecraft/util/context/ContextKeySet"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/storage/loot/parameters/LootContextParamSet$Builder",
            "net/minecraft/util/context/ContextKeySet$Builder"
        );

        // Static method redirects
        transformer.registerMethodRedirect(
            "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            "net/minecraft/resources/Identifier", "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/resources/ResourceLocation", "parse",
            "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            "net/minecraft/resources/Identifier", "parse",
            "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/resources/ResourceLocation", "tryParse",
            "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            "net/minecraft/resources/Identifier", "tryParse",
            "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
        );
        
        // ============================================================
        // JSPECIFY ANNOTATIONS
        // javax.annotation.Nullable -> org.jspecify.annotations.Nullable
        // ============================================================
        
        // These are compile-time annotations, but some mods might reference them
        transformer.registerClassRedirect(
            "javax/annotation/Nullable",
            "org/jspecify/annotations/Nullable"
        );
        
        transformer.registerClassRedirect(
            "javax/annotation/Nonnull",
            "org/jspecify/annotations/NonNull"
        );
        
        // ============================================================
        // RENDER TYPES CLASS
        // RenderType methods -> RenderTypes class
        // ============================================================
        
        // Vanilla render types moved from RenderType to RenderTypes
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/RenderType", "solid",
            "()Lnet/minecraft/client/renderer/RenderType;",
            "net/minecraft/client/renderer/RenderTypes", "solid",
            "()Lnet/minecraft/client/renderer/RenderType;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/RenderType", "cutout",
            "()Lnet/minecraft/client/renderer/RenderType;",
            "net/minecraft/client/renderer/RenderTypes", "cutout",
            "()Lnet/minecraft/client/renderer/RenderType;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/RenderType", "cutoutMipped",
            "()Lnet/minecraft/client/renderer/RenderType;",
            "net/minecraft/client/renderer/RenderTypes", "cutoutMipped",
            "()Lnet/minecraft/client/renderer/RenderType;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/RenderType", "translucent",
            "()Lnet/minecraft/client/renderer/RenderType;",
            "net/minecraft/client/renderer/RenderTypes", "translucent",
            "()Lnet/minecraft/client/renderer/RenderType;"
        );
        
        // ============================================================
        // BAKED QUAD CHANGES
        // block_light/sky_light -> light_emission
        // ============================================================
        
        // This affects model JSON, not code directly
        // But some mods programmatically create quads
        
        // No direct code redirects needed - this is data format change
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            // Most changes are direct class/method renames
            // JSpecify is a direct replacement
        };
    }
}
