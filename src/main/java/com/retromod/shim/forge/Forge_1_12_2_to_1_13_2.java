/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;

/** The Flattening: 1.13 rewrote the block/item ID, registry, and command systems. */
public class Forge_1_12_2_to_1_13_2 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.12.2 to 1.13.2"; }
    @Override public String getSourceVersion() { return "1.12.2"; }
    @Override public String getTargetVersion() { return "1.13.2"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // init.* classes moved into their type packages
        transformer.registerClassRedirect(
            "net/minecraft/init/Blocks",
            "net/minecraft/block/Blocks"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/Items",
            "net/minecraft/item/Items"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/SoundEvents",
            "net/minecraft/util/SoundEvents"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/Enchantments",
            "net/minecraft/enchantment/Enchantments"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/Biomes",
            "net/minecraft/world/biome/Biomes"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/PotionTypes",
            "net/minecraft/potion/Potions"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/MobEffects",
            "net/minecraft/potion/Effects"
        );

        transformer.registerClassRedirect(
            "net/minecraft/block/state/IBlockState",
            "net/minecraft/block/BlockState"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/state/BlockStateContainer",
            "net/minecraft/state/StateContainer"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyInteger",
            "net/minecraft/state/IntegerProperty"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyBool",
            "net/minecraft/state/BooleanProperty"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyEnum",
            "net/minecraft/state/EnumProperty"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyDirection",
            "net/minecraft/state/DirectionProperty"
        );

        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/entity/player/PlayerEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayerMP",
            "net/minecraft/entity/player/ServerPlayerEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityLivingBase",
            "net/minecraft/entity/LivingEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityCreature",
            "net/minecraft/entity/CreatureEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/monster/EntityMob",
            "net/minecraft/entity/monster/MonsterEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/passive/EntityAnimal",
            "net/minecraft/entity/passive/AnimalEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/item/EntityItem",
            "net/minecraft/entity/item/ItemEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/projectile/EntityArrow",
            "net/minecraft/entity/projectile/ArrowEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityLiving",
            "net/minecraft/entity/MobEntity"
        );

        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagCompound",
            "net/minecraft/nbt/CompoundNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagList",
            "net/minecraft/nbt/ListNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagString",
            "net/minecraft/nbt/StringNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagInt",
            "net/minecraft/nbt/IntNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagByte",
            "net/minecraft/nbt/ByteNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTBase",
            "net/minecraft/nbt/INBT"
        );

        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiScreen",
            "net/minecraft/client/gui/screen/Screen"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiButton",
            "net/minecraft/client/gui/widget/button/Button"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiTextField",
            "net/minecraft/client/gui/widget/TextFieldWidget"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiContainer",
            "net/minecraft/client/gui/screen/inventory/ContainerScreen"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiChest",
            "net/minecraft/client/gui/screen/inventory/ChestScreen"
        );

        transformer.registerClassRedirect(
            "net/minecraft/inventory/Container",
            "net/minecraft/inventory/container/Container"
        );
        transformer.registerClassRedirect(
            "net/minecraft/inventory/Slot",
            "net/minecraft/inventory/container/Slot"
        );
        transformer.registerClassRedirect(
            "net/minecraft/creativetab/CreativeTabs",
            "net/minecraft/item/ItemGroup"
        );

        transformer.registerClassRedirect(
            "net/minecraft/world/WorldServer",
            "net/minecraft/world/server/ServerWorld"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/chunk/Chunk",
            "net/minecraft/world/chunk/Chunk"
        );

        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketChat",
            "net/minecraft/network/play/server/SChatPacket"
        );

        // GameRegistry.register split into typed register calls
        transformer.registerMethodRedirect(
            "net/minecraftforge/fml/common/registry/GameRegistry", "register",
            "(Lnet/minecraft/item/Item;)V",
            "com/retromod/shim/forge/embedded/FlatteningShim", "registerItem",
            "(Ljava/lang/Object;)V"
        );
        transformer.registerMethodRedirect(
            "net/minecraftforge/fml/common/registry/GameRegistry", "register",
            "(Lnet/minecraft/block/Block;)V",
            "com/retromod/shim/forge/embedded/FlatteningShim", "registerBlock",
            "(Ljava/lang/Object;)V"
        );

        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Mod",
            "net/minecraftforge/fml/common/Mod"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Mod$EventHandler",
            "net/minecraftforge/fml/common/Mod$EventBusSubscriber"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLInitializationEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLPreInitializationEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLPostInitializationEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLLoadCompleteEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLServerStartingEvent",
            "net/minecraftforge/fml/event/server/FMLServerStartingEvent"
        );

        // SidedProxy gone in 1.13+
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/SidedProxy",
            "com/retromod/shim/forge/embedded/SidedProxyShim"
        );

        // Client sound interface: the 1.17 repackaging moved client/audio/ISound to
        // client/resources/sounds/SoundInstance (same name on 1.20.1 and 26.1). Ungated: the target
        // exists on every supported host (1.18+). The Betweenlands hit this as its only gap (#113).
        transformer.registerClassRedirect(
            "net/minecraft/client/audio/ISound",
            "net/minecraft/client/resources/sounds/SoundInstance"
        );

        // 1.12.2 -> 26.1 class-move baseline (data-driven). 308 of the 344 issues on a real
        // 1.12.2 mod (Metallurgy 4, #103) are class moves; this table is the dominant fix. Called
        // last so its final 26.1 names win over the intermediate 1.13 names above. Gated to a 26.1+
        // host inside the loader, since the targets are 26.1 names.
        loadDirectClassMoves(transformer);

        // The pre-1.13 FML lifecycle: @Mod(modid=...) rewrite + @Mod.EventHandler wiring +
        // FML*InitializationEvent stand-ins. Without it the mod is scanned (mcmod.info toml
        // generation) but modern FML finds no mod id and never calls its setup.
        Forge1122LifecycleSynthetics.register(transformer);
    }

    private static final String CLASS_MOVES_RESOURCE = "/retromod/forge-1.12.2-class-moves.tsv";
    private static final String CLASS_MOVES_1201_RESOURCE =
            "/retromod/forge-1.12.2-class-moves-1201.tsv";

    /**
     * Load the bundled 1.12.2 (MCP) class-move table matching the host: on a 26.1+ host the
     * 26.1-target table (every target validated against the 26.1 jar at harvest time), on a
     * 1.20.x host the 1.20.x-target variant (validated against Mojang's official 1.20.1
     * mappings; it also carries the families 26.1 removed but 1.20.x still has, e.g.
     * {@code ItemSword -> SwordItem}, {@code EnumAction -> UseAnim}). 1.21.x hosts get
     * neither for now: no table has been validated against those jars, and a wrong redirect
     * is worse than none. Additive and soft-failing either way: if the resource is missing
     * or a line is malformed, the hardcoded chain redirects still apply.
     */
    void loadDirectClassMoves(RetromodTransformer transformer) {
        String host = com.retromod.core.RetromodVersion.TARGET_MC_VERSION;
        String resource;
        if (com.retromod.core.RetromodVersion.isUnobfuscatedTarget(host)) {
            resource = CLASS_MOVES_RESOURCE;
        } else if (!com.retromod.core.RetromodVersion.mcVersionExceeds(host, "1.20.6")) {
            // 1.20 - 1.20.6 host (Retromod hosts start at 1.20): the reported 1.12.2 hosts
            // (#103/#108/#117 all ran 1.20.1)
            resource = CLASS_MOVES_1201_RESOURCE;
        } else {
            return; // 1.21.x host: no validated table yet
        }
        try (java.io.InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) return;
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.charAt(0) == '#') continue;
                    int t = line.indexOf('\t');
                    if (t <= 0) continue;
                    transformer.registerClassRedirect(line.substring(0, t).trim(), line.substring(t + 1).trim());
                }
            }
        } catch (Exception e) {
            // additive; the hardcoded chain redirects still apply
        }

        // Block/Item Properties-constructor bridge. MC 1.20 already removed Material and made
        // Block/Item Properties-constructed (verified against Mojang's official 1.20.1 mappings:
        // Material is absent, BlockBehaviour$Properties.of() and Item$Properties() are present,
        // same shapes as 26.1), so the same bridge serves both table hosts. Item: super() ->
        // super(new Item.Properties()). Block: super(Material) -> super(BlockBehaviour.Properties.of())
        // (the Material GETSTATIC is nulled below and popped by the replace bridge). Default
        // properties only (material-specific settings are lost), but the block/item constructs.
        transformer.registerSuperConstructorRedirect(
            "net/minecraft/world/item/Item", "()V",
            "(Lnet/minecraft/world/item/Item$Properties;)V");
        transformer.registerSuperConstructorReplace(
            "net/minecraft/world/level/block/Block",
            "(Lnet/minecraft/block/material/Material;)V",
            "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V");
        // Material is gone on every table host; null its static-constant reads (e.g. Material.IRON)
        // so the super(Material.X) read doesn't fault before the replace bridge pops it.
        transformer.registerStaticFieldNuller("net/minecraft/block/material/Material");
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.forge.embedded.FlatteningShim",
            "com.retromod.shim.forge.embedded.SidedProxyShim"
        };
    }
}
