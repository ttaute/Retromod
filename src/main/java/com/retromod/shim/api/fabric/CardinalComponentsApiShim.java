/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Cardinal Components API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/**
 * Cardinal Components API compatibility shim.
 * 
 * Cardinal Components provides a capability-like system for Fabric mods.
 * Similar to Forge Capabilities but designed for Fabric.
 * 
 * API changes:
 * - v2.x -> v3.x: Component registration changes
 * - v3.x -> v4.x: Sync system changes
 * - v4.x -> v5.x: Entity components changes
 * - v5.x -> v6.x+: Further API refinements
 */
public class CardinalComponentsApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Cardinal Components API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "2.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "6.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Full dev.onyxstudios.cca → org.ladysnake.cca package move (CCA 5→6),
        // harvested from the real jars. The hand-curated entries below cover the
        // famous classes with their API-change method/field redirects; this
        // covers the rest of the public api/ surface as plain class moves.
        registerOnyxToLadysnakePackageMoves(transformer);

        // ============================================================
        // PACKAGE CHANGES
        // ============================================================

        // Old: nerdhub.cardinal.components
        // New: dev.onyxstudios.cca (then org.ladysnake.cca)
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/ComponentType",
            "org/ladysnake/cca/api/v3/component/ComponentKey"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/component/ComponentKey",
            "org/ladysnake/cca/api/v3/component/ComponentKey"
        );
        
        // ============================================================
        // COMPONENT INTERFACE CHANGES
        // ============================================================
        
        // Old: Component interface
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/Component",
            "org/ladysnake/cca/api/v3/component/Component"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/component/Component",
            "org/ladysnake/cca/api/v3/component/Component"
        );
        
        // Syncable component
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/component/SyncedComponent",
            "org/ladysnake/cca/api/v3/component/sync/AutoSyncedComponent"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/component/sync/AutoSyncedComponent",
            "org/ladysnake/cca/api/v3/component/sync/AutoSyncedComponent"
        );
        
        // ============================================================
        // COMPONENT REGISTRY CHANGES
        // ============================================================
        
        // Old: ComponentRegistry.INSTANCE
        // New: ComponentRegistryV3.INSTANCE
        transformer.registerFieldRedirect(
            "nerdhub/cardinal/components/api/ComponentRegistry",
            "INSTANCE",
            "Lnerdhub/cardinal/components/api/ComponentRegistry;",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "getRegistry",
            "()Ljava/lang/Object;"
        );
        
        transformer.registerFieldRedirect(
            "dev/onyxstudios/cca/api/v3/component/ComponentRegistry",
            "INSTANCE",
            "Ldev/onyxstudios/cca/api/v3/component/ComponentRegistry;",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "getRegistry",
            "()Ljava/lang/Object;"
        );
        
        // ============================================================
        // COMPONENT ACCESS CHANGES
        // ============================================================
        
        // Old: ComponentType.get(provider)
        // New: ComponentKey.get(provider)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/ComponentType",
            "get",
            "(Ljava/lang/Object;)Lnerdhub/cardinal/components/api/Component;",
            "org/ladysnake/cca/api/v3/component/ComponentKey",
            "get",
            "(Ljava/lang/Object;)Lorg/ladysnake/cca/api/v3/component/Component;"
        );
        
        // Old: ComponentType.maybeGet(provider)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/ComponentType",
            "maybeGet",
            "(Ljava/lang/Object;)Ljava/util/Optional;",
            "org/ladysnake/cca/api/v3/component/ComponentKey",
            "maybeGet",
            "(Ljava/lang/Object;)Ljava/util/Optional;"
        );
        
        // ============================================================
        // ENTITY COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/EntityComponents",
            "org/ladysnake/cca/api/v3/entity/EntityComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/entity/EntityComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/entity/EntityComponentFactoryRegistry"
        );
        
        // ============================================================
        // WORLD COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/WorldComponents",
            "org/ladysnake/cca/api/v3/world/WorldComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/world/WorldComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/world/WorldComponentFactoryRegistry"
        );
        
        // ============================================================
        // CHUNK COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/ChunkComponents",
            "org/ladysnake/cca/api/v3/chunk/ChunkComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/chunk/ChunkComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/chunk/ChunkComponentFactoryRegistry"
        );
        
        // ============================================================
        // ITEM COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/ItemComponents",
            "org/ladysnake/cca/api/v3/item/ItemComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/item/ItemComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/item/ItemComponentFactoryRegistry"
        );
        
        // ============================================================
        // NBT SERIALIZATION CHANGES
        // ============================================================
        
        // Old: Component.fromTag(tag)
        // New: Component.readFromNbt(tag, registryLookup)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/Component",
            "fromTag",
            "(Lnet/minecraft/nbt/CompoundTag;)V",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "readFromNbt",
            "(Ljava/lang/Object;Lnet/minecraft/nbt/CompoundTag;)V"
        );
        
        // Old: Component.toTag(tag)
        // New: Component.writeToNbt(tag, registryLookup)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/Component",
            "toTag",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "writeToNbt",
            "(Ljava/lang/Object;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"
        );
    }
    

    /**
     * The bulk dev.onyxstudios.cca → org.ladysnake.cca package move (CCA 5.x→6.x),
     * harvested by sub-path match from the real jars (5.2.3 vs 6.1.3) - every
     * public {@code api/} class that kept its sub-path across the rename
     * (verified present in both; inner classes get explicit entries since the
     * ASM remapper matches exact names). These are plain class moves; the
     * curated method/field redirects in {@link #registerRedirects} handle the
     * classes whose API <i>also</i> changed.
     *
     * <p>Deliberately NOT redirected - removed outright in CCA 6 (no successor;
     * a mod using these needs the 6.x API, beyond a redirect): the item-component
     * family ({@code api/v3/item/ItemComponent}, {@code ItemComponentFactoryRegistry},
     * {@code CcaNbtType}, {@code ItemTagInvalidationListener} - CCA 6 dropped item
     * components in favour of vanilla data components), {@code api/v3/entity/PlayerComponent}
     * and {@code PlayerCopyCallback}, and the {@code api/v3/block/util/Sided*Compound}
     * helpers. CCA-internal ({@code internal/}, {@code mixin/}) classes are skipped:
     * mods don't reference them and they churn between versions.
     */
    private void registerOnyxToLadysnakePackageMoves(RetromodTransformer t) {
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/block/BlockComponentFactoryRegistry",
                "org/ladysnake/cca/api/v3/block/BlockComponentFactoryRegistry");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/block/BlockComponentFactoryRegistry$Registration",
                "org/ladysnake/cca/api/v3/block/BlockComponentFactoryRegistry$Registration");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/block/BlockComponentInitializer",
                "org/ladysnake/cca/api/v3/block/BlockComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/block/BlockComponents",
                "org/ladysnake/cca/api/v3/block/BlockComponents");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/block/BlockEntitySyncAroundCallback",
                "org/ladysnake/cca/api/v3/block/BlockEntitySyncAroundCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/block/BlockEntitySyncCallback",
                "org/ladysnake/cca/api/v3/block/BlockEntitySyncCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/chunk/ChunkComponentInitializer",
                "org/ladysnake/cca/api/v3/chunk/ChunkComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/chunk/ChunkSyncCallback",
                "org/ladysnake/cca/api/v3/chunk/ChunkSyncCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentAccess",
                "org/ladysnake/cca/api/v3/component/ComponentAccess");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentContainer",
                "org/ladysnake/cca/api/v3/component/ComponentContainer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentContainer$Factory",
                "org/ladysnake/cca/api/v3/component/ComponentContainer$Factory");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentContainer$Factory$Builder",
                "org/ladysnake/cca/api/v3/component/ComponentContainer$Factory$Builder");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentFactory",
                "org/ladysnake/cca/api/v3/component/ComponentFactory");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentProvider",
                "org/ladysnake/cca/api/v3/component/ComponentProvider");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentRegistryV3",
                "org/ladysnake/cca/api/v3/component/ComponentRegistryV3");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/ComponentV3",
                "org/ladysnake/cca/api/v3/component/ComponentV3");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/CopyableComponent",
                "org/ladysnake/cca/api/v3/component/CopyableComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/StaticComponentInitializer",
                "org/ladysnake/cca/api/v3/component/StaticComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/TransientComponent",
                "org/ladysnake/cca/api/v3/component/TransientComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/TransientComponent$SimpleImpl",
                "org/ladysnake/cca/api/v3/component/TransientComponent$SimpleImpl");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/load/ClientLoadAwareComponent",
                "org/ladysnake/cca/api/v3/component/load/ClientLoadAwareComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/load/ClientUnloadAwareComponent",
                "org/ladysnake/cca/api/v3/component/load/ClientUnloadAwareComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/load/ServerLoadAwareComponent",
                "org/ladysnake/cca/api/v3/component/load/ServerLoadAwareComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/load/ServerUnloadAwareComponent",
                "org/ladysnake/cca/api/v3/component/load/ServerUnloadAwareComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/sync/ComponentPacketWriter",
                "org/ladysnake/cca/api/v3/component/sync/ComponentPacketWriter");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/sync/PlayerSyncPredicate",
                "org/ladysnake/cca/api/v3/component/sync/PlayerSyncPredicate");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/tick/ClientTickingComponent",
                "org/ladysnake/cca/api/v3/component/tick/ClientTickingComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/tick/CommonTickingComponent",
                "org/ladysnake/cca/api/v3/component/tick/CommonTickingComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/component/tick/ServerTickingComponent",
                "org/ladysnake/cca/api/v3/component/tick/ServerTickingComponent");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/entity/EntityComponentFactoryRegistry$Registration",
                "org/ladysnake/cca/api/v3/entity/EntityComponentFactoryRegistry$Registration");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/entity/EntityComponentInitializer",
                "org/ladysnake/cca/api/v3/entity/EntityComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/entity/PlayerSyncCallback",
                "org/ladysnake/cca/api/v3/entity/PlayerSyncCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/entity/RespawnCopyStrategy",
                "org/ladysnake/cca/api/v3/entity/RespawnCopyStrategy");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/entity/TrackingStartCallback",
                "org/ladysnake/cca/api/v3/entity/TrackingStartCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/item/ItemComponentInitializer",
                "org/ladysnake/cca/api/v3/item/ItemComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/level/LevelComponentFactoryRegistry",
                "org/ladysnake/cca/api/v3/level/LevelComponentFactoryRegistry");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/level/LevelComponentInitializer",
                "org/ladysnake/cca/api/v3/level/LevelComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/level/LevelComponents",
                "org/ladysnake/cca/api/v3/level/LevelComponents");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/ScoreboardComponentFactoryRegistry",
                "org/ladysnake/cca/api/v3/scoreboard/ScoreboardComponentFactoryRegistry");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/ScoreboardComponentFactoryV2",
                "org/ladysnake/cca/api/v3/scoreboard/ScoreboardComponentFactoryV2");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/ScoreboardComponentInitializer",
                "org/ladysnake/cca/api/v3/scoreboard/ScoreboardComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/ScoreboardSyncCallback",
                "org/ladysnake/cca/api/v3/scoreboard/ScoreboardSyncCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/TeamAddCallback",
                "org/ladysnake/cca/api/v3/scoreboard/TeamAddCallback");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/TeamComponentFactory",
                "org/ladysnake/cca/api/v3/scoreboard/TeamComponentFactory");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/scoreboard/TeamComponentFactoryV2",
                "org/ladysnake/cca/api/v3/scoreboard/TeamComponentFactoryV2");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/util/MethodsReturnNonnullByDefault",
                "org/ladysnake/cca/api/v3/util/MethodsReturnNonnullByDefault");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/util/NbtSerializable",
                "org/ladysnake/cca/api/v3/util/NbtSerializable");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/world/WorldComponentInitializer",
                "org/ladysnake/cca/api/v3/world/WorldComponentInitializer");
        t.registerClassRedirect("dev/onyxstudios/cca/api/v3/world/WorldSyncCallback",
                "org/ladysnake/cca/api/v3/world/WorldSyncCallback");
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.CardinalShim"
        };
    }
}
