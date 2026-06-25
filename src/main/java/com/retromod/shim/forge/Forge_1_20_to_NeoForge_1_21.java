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
 * Forge 1.20.x to NeoForge 1.21.x migration shim. The redirects are only valid on a NeoForge
 * runtime: on Forge they rewrite a mod's {@code @Mod} annotation to the NeoForge one, after which
 * Forge can't find {@code @Mod} and reports "has mods that were not found", so
 * {@link #registerRedirects(RetromodTransformer)} returns early off NeoForge.
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
        // These only apply on a NeoForge runtime; on Forge they break @Mod lookup (see class javadoc).
        if (!McReflect.isNeoForge()) {
            LOGGER.debug("Skipping Forge → NeoForge migration redirects (runtime is not NeoForge)");
            return;
        }

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
        
        // ForgeRegistries and IForgeRegistry are handled by ForgeRegistryApiShim (field redirects to
        // vanilla Registries ResourceKeys), not class-redirected here: a class redirect would rewrite
        // the GETSTATIC owner before those field redirects could match, and NeoForgeRegistries lacks
        // the BLOCKS/ITEMS/... fields anyway (NoSuchFieldError).
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "net/neoforged/neoforge/registries/DeferredRegister"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/RegistryObject",
            "net/neoforged/neoforge/registries/DeferredHolder"
        );

        // Dist markers: the Dist enum moved from the Forge package to NeoForge. AnnotationPolyfill
        // maps these but doesn't run on the NeoForge path (Fabric + CLI only), so without this a Forge
        // mod using Dist in code hits NoClassDefFoundError. @OnlyIn was deleted; map it to a no-op
        // annotation (metadata only). DistExecutor is supplied as a synthetic by ForgeNeoForgeSynthetics.
        transformer.registerClassRedirect(
            "net/minecraftforge/api/distmarker/Dist",
            "net/neoforged/api/distmarker/Dist"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/api/distmarker/OnlyIn",
            "java/lang/annotation/Retention"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/api/distmarker/OnlyIns",
            "java/lang/annotation/Retention"
        );

        // capabilities
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
        
        // ForgeConfigSpec renamed to ModConfigSpec
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

        // FML
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "net/neoforged/fml/ModLoadingContext"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/loading/FMLPaths",
            "net/neoforged/fml/loading/FMLPaths"
        );
        
        // network
        transformer.registerClassRedirect(
            "net/minecraftforge/network/NetworkRegistry",
            "net/neoforged/neoforge/network/registration/PayloadRegistrar"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/network/simple/SimpleChannel",
            "net/neoforged/neoforge/network/handling/IPayloadHandler"
        );
        
        // client events
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderGuiOverlayEvent",
            "net/neoforged/neoforge/client/event/RenderGuiLayerEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/EntityRenderersEvent",
            "net/neoforged/neoforge/client/event/EntityRenderersEvent"
        );
        
        // data generation
        transformer.registerClassRedirect(
            "net/minecraftforge/data/event/GatherDataEvent",
            "net/neoforged/neoforge/data/event/GatherDataEvent"
        );
        
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
