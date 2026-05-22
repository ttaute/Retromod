/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import com.retromod.util.McReflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transformation shim for Forge 1.20.x → NeoForge 1.21.x migration.
 *
 * <p>The redirects this shim registers (Forge package names → NeoForge
 * package names, including {@code @Mod} annotation) are <strong>only correct
 * when the actual runtime is NeoForge</strong>. On a Forge runtime they're
 * actively harmful: they rewrite a Forge mod's
 * {@code @net.minecraftforge.fml.common.Mod} annotation to the NeoForge
 * equivalent, after which Forge can't find {@code @Mod} on any class in
 * the transformed mod and dies with:
 *
 * <pre>
 *   The Mod File &lt;jar&gt; has mods that were not found
 * </pre>
 *
 * <p>So {@link #registerRedirects(RetromodTransformer)} short-circuits unless
 * we're running on NeoForge.
 */
public class Forge_1_20_to_NeoForge_1_21 implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-ForgeNeoMig");
    
    @Override
    public String getShimName() {
        return "Forge 1.20 to NeoForge 1.21";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.20";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge";
    }
    
    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // Cross-loader migration redirects ONLY make sense when the runtime
        // is NeoForge. On a Forge runtime they break @Mod annotation lookup
        // for every transformed mod (see class-level javadoc).
        if (!McReflect.isNeoForge()) {
            LOGGER.debug("Skipping Forge → NeoForge migration redirects (runtime is not NeoForge)");
            return;
        }

        // Core package renames
        transformer.registerClassRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "net/neoforged/neoforge/common/NeoForge"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Mod",
            "net/neoforged/fml/common/Mod"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/SubscribeEvent",
            "net/neoforged/bus/api/SubscribeEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/IEventBus",
            "net/neoforged/bus/api/IEventBus"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/Event",
            "net/neoforged/bus/api/Event"
        );
        
        // Registry classes
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "net/neoforged/neoforge/registries/NeoForgeRegistries"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "net/neoforged/neoforge/registries/DeferredRegister"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/RegistryObject",
            "net/neoforged/neoforge/registries/DeferredHolder"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/IForgeRegistry",
            "net/minecraft/core/Registry"
        );
        
        // Capability system
        transformer.registerClassRedirect(
            "net/minecraftforge/common/capabilities/Capability",
            "net/neoforged/neoforge/capabilities/BlockCapability"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/capabilities/CapabilityManager",
            "net/neoforged/neoforge/capabilities/Capabilities"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/util/LazyOptional",
            "java/util/Optional"
        );
        
        // Config system: ForgeConfigSpec → ModConfigSpec (NeoForge config system rename)
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec",
            "net/neoforged/neoforge/common/ModConfigSpec"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$Builder",
            "net/neoforged/neoforge/common/ModConfigSpec$Builder"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$ConfigValue",
            "net/neoforged/neoforge/common/ModConfigSpec$ConfigValue"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$BooleanValue",
            "net/neoforged/neoforge/common/ModConfigSpec$BooleanValue"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$IntValue",
            "net/neoforged/neoforge/common/ModConfigSpec$IntValue"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$DoubleValue",
            "net/neoforged/neoforge/common/ModConfigSpec$DoubleValue"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$LongValue",
            "net/neoforged/neoforge/common/ModConfigSpec$LongValue"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$EnumValue",
            "net/neoforged/neoforge/common/ModConfigSpec$EnumValue"
        );

        // FML class renames
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "net/neoforged/fml/ModLoadingContext"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/loading/FMLPaths",
            "net/neoforged/fml/loading/FMLPaths"
        );
        
        // Network
        transformer.registerClassRedirect(
            "net/minecraftforge/network/NetworkRegistry",
            "net/neoforged/neoforge/network/registration/PayloadRegistrar"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/network/simple/SimpleChannel",
            "net/neoforged/neoforge/network/handling/IPayloadHandler"
        );
        
        // Client events
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderGuiOverlayEvent",
            "net/neoforged/neoforge/client/event/RenderGuiLayerEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/EntityRenderersEvent",
            "net/neoforged/neoforge/client/event/EntityRenderersEvent"
        );
        
        // Data generation
        transformer.registerClassRedirect(
            "net/minecraftforge/data/event/GatherDataEvent",
            "net/neoforged/neoforge/data/event/GatherDataEvent"
        );
        
        // Field redirects for EVENT_BUS
        transformer.registerFieldRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "EVENT_BUS",
            "net/neoforged/neoforge/common/NeoForge",
            "EVENT_BUS"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.forge.embedded.ForgeRegistriesShim",
            "com.retromod.shim.forge.embedded.CapabilityShim",
            "com.retromod.shim.forge.embedded.NetworkShim"
        };
    }
}
