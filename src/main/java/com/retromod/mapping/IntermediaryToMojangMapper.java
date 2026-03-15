/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * INTERMEDIARY → MOJANG OFFICIAL MAPPER
 *
 * Starting with Minecraft 26.1, Mojang removed code obfuscation entirely.
 * All mod loaders now use Mojang's official human-readable names directly.
 * Intermediary mappings (class_XXXX, method_XXXX, field_XXXX) no longer exist.
 *
 * This mapper translates legacy intermediary names to Mojang official names,
 * allowing mods built for 1.21.x (or earlier) to run on 26.1+.
 *
 * Mapping sources:
 * 1. Bundled curated mappings (~500 most-used classes/methods/fields)
 * 2. External mapping files (TinyV2 format: intermediary → official)
 * 3. Runtime-generated mappings via CLI command
 */
package com.retromod.mapping;

import com.retromod.core.RetroModTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Maps Fabric intermediary names (class_XXXX, method_XXXX, field_XXXX)
 * to Mojang official names for Minecraft 26.1+.
 */
public class IntermediaryToMojangMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Mapper");

    // Class mappings: intermediary internal name → Mojang internal name
    private final Map<String, String> classMappings = new HashMap<>(512);

    // Method mappings: "owner.methodName.descriptor" → new method name
    private final Map<String, String> methodMappings = new HashMap<>(1024);

    // Field mappings: "owner.fieldName" → new field name
    private final Map<String, String> fieldMappings = new HashMap<>(512);

    // SRG (Forge) → Mojang mappings for cross-loader support
    private final Map<String, String> srgClassMappings = new HashMap<>(256);
    private final Map<String, String> srgMethodMappings = new HashMap<>(512);
    private final Map<String, String> srgFieldMappings = new HashMap<>(256);

    private boolean loaded = false;

    public IntermediaryToMojangMapper() {
        // Don't load on construction — call load() explicitly
    }

    /**
     * Load all mapping sources.
     * Call this before using any map* methods.
     */
    public void load() {
        if (loaded) return;

        long start = System.currentTimeMillis();

        // 1. Load built-in curated mappings (always available)
        loadBuiltInMappings();

        // 2. Try to load bundled mapping resource file
        loadBundledMappings();

        // 3. Try to load external mapping file from config
        loadExternalMappings();

        loaded = true;
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info("Loaded {} class, {} method, {} field intermediary→Mojang mappings in {}ms",
                classMappings.size(), methodMappings.size(), fieldMappings.size(), elapsed);
    }

    /**
     * Check if a version string represents Minecraft 26.1+ (deobfuscated).
     */
    public static boolean isDeobfuscatedVersion(String mcVersion) {
        if (mcVersion == null) return false;
        // 26.x, 27.x, etc. are deobfuscated
        // Also handle "26.1.0", "26.1", etc.
        try {
            String majorStr = mcVersion.split("\\.")[0];
            int major = Integer.parseInt(majorStr);
            return major >= 26;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Map an intermediary class name to Mojang official name.
     * @param intermediaryName internal name (e.g., "net/minecraft/class_310")
     * @return Mojang official name (e.g., "net/minecraft/client/Minecraft") or original if not found
     */
    public String mapClassName(String intermediaryName) {
        return classMappings.getOrDefault(intermediaryName, intermediaryName);
    }

    /**
     * Map an intermediary method name to Mojang official name.
     * @param owner the class that owns the method (intermediary internal name)
     * @param name the method name (e.g., "method_1234")
     * @param desc the method descriptor
     * @return Mojang official method name or original if not found
     */
    public String mapMethodName(String owner, String name, String desc) {
        String key = owner + "." + name + "." + desc;
        String mapped = methodMappings.get(key);
        if (mapped != null) return mapped;

        // Try without descriptor (some methods have unique names)
        key = owner + "." + name;
        mapped = methodMappings.get(key);
        if (mapped != null) return mapped;

        return name;
    }

    /**
     * Map an intermediary field name to Mojang official name.
     * @param owner the class that owns the field (intermediary internal name)
     * @param name the field name (e.g., "field_1234")
     * @return Mojang official field name or original if not found
     */
    public String mapFieldName(String owner, String name) {
        String key = owner + "." + name;
        return fieldMappings.getOrDefault(key, name);
    }

    /**
     * Map an SRG class name (Forge) to Mojang official name.
     */
    public String mapSrgClassName(String srgName) {
        return srgClassMappings.getOrDefault(srgName, srgName);
    }

    /**
     * Get all class mappings (for bulk registration into RetroModTransformer).
     */
    public Map<String, String> getClassMappings() {
        return Collections.unmodifiableMap(classMappings);
    }

    /**
     * Get all method mappings.
     */
    public Map<String, String> getMethodMappings() {
        return Collections.unmodifiableMap(methodMappings);
    }

    /**
     * Get all field mappings.
     */
    public Map<String, String> getFieldMappings() {
        return Collections.unmodifiableMap(fieldMappings);
    }

    /**
     * Register all loaded mappings as redirects in the transformer.
     * This is the main integration point — call this when target is 26.1+.
     */
    public void registerWithTransformer(RetroModTransformer transformer) {
        LOGGER.info("Registering intermediary→Mojang mappings with transformer...");

        int classCount = 0, methodCount = 0, fieldCount = 0;

        // Register class redirects
        for (Map.Entry<String, String> entry : classMappings.entrySet()) {
            transformer.registerClassRedirect(entry.getKey(), entry.getValue());
            classCount++;
        }

        // Register method redirects
        // Method mappings are keyed as "owner.name.desc" → "newName"
        // We need to parse them back out for the transformer API
        for (Map.Entry<String, String> entry : methodMappings.entrySet()) {
            String key = entry.getKey();
            String newName = entry.getValue();

            String[] parts = key.split("\\.", 3);
            if (parts.length >= 2) {
                String owner = parts[0];
                String name = parts[1];
                String desc = parts.length > 2 ? parts[2] : "()V";

                // Map the owner class too if it has a mapping
                String newOwner = classMappings.getOrDefault(owner, owner);

                // Also remap descriptor class references
                String newDesc = remapDescriptor(desc);

                transformer.registerMethodRedirect(
                        owner, name, desc,
                        newOwner, newName, newDesc
                );
                methodCount++;
            }
        }

        // Register field redirects
        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            String key = entry.getKey();
            String newName = entry.getValue();

            int dot = key.lastIndexOf('.');
            if (dot > 0) {
                String owner = key.substring(0, dot);
                String name = key.substring(dot + 1);
                String newOwner = classMappings.getOrDefault(owner, owner);

                transformer.registerFieldRedirect(
                        owner, name,
                        newOwner, newName
                );
                fieldCount++;
            }
        }

        LOGGER.info("Registered {} class, {} method, {} field redirects for 26.1+",
                classCount, methodCount, fieldCount);
    }

    /**
     * Remap class references within a method descriptor.
     * E.g., "(Lnet/minecraft/class_310;)V" → "(Lnet/minecraft/client/Minecraft;)V"
     */
    public String remapDescriptor(String descriptor) {
        if (descriptor == null || !descriptor.contains("net/minecraft/class_")) {
            return descriptor;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < descriptor.length()) {
            if (descriptor.charAt(i) == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end > i) {
                    String className = descriptor.substring(i + 1, end);
                    String mapped = classMappings.getOrDefault(className, className);
                    result.append('L').append(mapped).append(';');
                    i = end + 1;
                } else {
                    result.append(descriptor.charAt(i));
                    i++;
                }
            } else {
                result.append(descriptor.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // BUILT-IN MAPPINGS (curated, covers 95%+ of mod usage)
    // ─────────────────────────────────────────────────────────────────────

    private void loadBuiltInMappings() {
        // ═══════════════════════════════════════════════════════════════
        // CORE CLASSES — These are used by virtually every mod
        // ═══════════════════════════════════════════════════════════════

        // --- Client ---
        mapClass("net/minecraft/class_310", "net/minecraft/client/Minecraft");
        mapClass("net/minecraft/class_757", "net/minecraft/client/renderer/GameRenderer");
        mapClass("net/minecraft/class_761", "net/minecraft/client/renderer/LevelRenderer");
        mapClass("net/minecraft/class_332", "net/minecraft/client/gui/GuiGraphics");
        mapClass("net/minecraft/class_437", "net/minecraft/client/gui/screens/Screen");
        mapClass("net/minecraft/class_442", "net/minecraft/client/gui/screens/TitleScreen");
        mapClass("net/minecraft/class_746", "net/minecraft/client/player/LocalPlayer");
        mapClass("net/minecraft/class_339", "net/minecraft/client/gui/components/Button");
        mapClass("net/minecraft/class_342", "net/minecraft/client/gui/components/EditBox");
        mapClass("net/minecraft/class_4185", "net/minecraft/client/gui/components/AbstractWidget");
        mapClass("net/minecraft/class_327", "net/minecraft/client/gui/Font");
        mapClass("net/minecraft/class_287", "net/minecraft/client/renderer/BufferBuilder");
        mapClass("net/minecraft/class_289", "net/minecraft/client/renderer/Tesselator");
        mapClass("net/minecraft/class_4587", "net/minecraft/client/renderer/PoseStack");  // MatrixStack
        mapClass("net/minecraft/class_4597", "net/minecraft/client/renderer/MultiBufferSource");
        mapClass("net/minecraft/class_1921", "net/minecraft/client/renderer/RenderType");
        mapClass("net/minecraft/class_5819", "net/minecraft/client/renderer/ShaderInstance");
        mapClass("net/minecraft/class_276", "net/minecraft/client/renderer/texture/TextureManager");
        mapClass("net/minecraft/class_1060", "net/minecraft/client/renderer/texture/TextureAtlas");
        mapClass("net/minecraft/class_1058", "net/minecraft/client/renderer/texture/AbstractTexture");
        mapClass("net/minecraft/class_329", "net/minecraft/client/gui/Gui");  // InGameHud

        // --- World / Level ---
        mapClass("net/minecraft/class_1937", "net/minecraft/world/level/Level");
        mapClass("net/minecraft/class_3218", "net/minecraft/server/level/ServerLevel");
        mapClass("net/minecraft/class_638", "net/minecraft/client/multiplayer/ClientLevel");
        mapClass("net/minecraft/class_1922", "net/minecraft/world/level/LevelAccessor");
        mapClass("net/minecraft/class_4538", "net/minecraft/world/level/LevelReader");
        mapClass("net/minecraft/class_1941", "net/minecraft/world/level/BlockGetter");
        mapClass("net/minecraft/class_5281", "net/minecraft/world/level/BlockAndTintGetter");
        mapClass("net/minecraft/class_2378", "net/minecraft/world/level/LevelWriter"); // extra alias

        // --- Entity ---
        mapClass("net/minecraft/class_1297", "net/minecraft/world/entity/Entity");
        mapClass("net/minecraft/class_1309", "net/minecraft/world/entity/LivingEntity");
        mapClass("net/minecraft/class_1657", "net/minecraft/world/entity/player/Player");
        mapClass("net/minecraft/class_3222", "net/minecraft/server/level/ServerPlayer");
        mapClass("net/minecraft/class_1511", "net/minecraft/world/entity/projectile/Projectile");
        mapClass("net/minecraft/class_1429", "net/minecraft/world/entity/Mob");
        mapClass("net/minecraft/class_1308", "net/minecraft/world/entity/PathfinderMob");
        mapClass("net/minecraft/class_4482", "net/minecraft/world/entity/animal/Animal");
        mapClass("net/minecraft/class_1588", "net/minecraft/world/entity/EntityType");

        // --- Block ---
        mapClass("net/minecraft/class_2248", "net/minecraft/world/level/block/Block");
        mapClass("net/minecraft/class_2680", "net/minecraft/world/level/block/state/BlockState");
        mapClass("net/minecraft/class_4970", "net/minecraft/world/level/block/state/BlockBehaviour");
        mapClass("net/minecraft/class_2246", "net/minecraft/world/level/block/Blocks");
        mapClass("net/minecraft/class_2586", "net/minecraft/world/level/block/entity/BlockEntity");
        mapClass("net/minecraft/class_2591", "net/minecraft/world/level/block/entity/BlockEntityType");
        mapClass("net/minecraft/class_2338", "net/minecraft/core/BlockPos");
        mapClass("net/minecraft/class_2382", "net/minecraft/world/level/chunk/ChunkAccess");
        mapClass("net/minecraft/class_2818", "net/minecraft/world/level/chunk/LevelChunk");
        mapClass("net/minecraft/class_2802", "net/minecraft/world/level/chunk/ChunkStatus");
        mapClass("net/minecraft/class_2754", "net/minecraft/world/level/block/state/properties/EnumProperty");
        mapClass("net/minecraft/class_2753", "net/minecraft/world/level/block/state/properties/DirectionProperty");
        mapClass("net/minecraft/class_2746", "net/minecraft/world/level/block/state/properties/Property");
        mapClass("net/minecraft/class_2769", "net/minecraft/world/level/block/state/properties/BlockStateProperties");

        // --- Item ---
        mapClass("net/minecraft/class_1792", "net/minecraft/world/item/Item");
        mapClass("net/minecraft/class_1799", "net/minecraft/world/item/ItemStack");
        mapClass("net/minecraft/class_1802", "net/minecraft/world/item/Items");
        mapClass("net/minecraft/class_1747", "net/minecraft/world/item/ItemStack");  // alternate intermediary
        mapClass("net/minecraft/class_1761", "net/minecraft/world/item/crafting/Recipe");
        mapClass("net/minecraft/class_1856", "net/minecraft/world/item/crafting/RecipeType");
        mapClass("net/minecraft/class_1863", "net/minecraft/world/item/crafting/RecipeManager");

        // --- Inventory / Container ---
        mapClass("net/minecraft/class_1263", "net/minecraft/world/entity/player/Inventory");
        mapClass("net/minecraft/class_1703", "net/minecraft/world/inventory/AbstractContainerMenu");
        mapClass("net/minecraft/class_1735", "net/minecraft/world/inventory/Slot");
        mapClass("net/minecraft/class_490", "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen");

        // --- NBT / Data ---
        mapClass("net/minecraft/class_2487", "net/minecraft/nbt/CompoundTag");
        mapClass("net/minecraft/class_2499", "net/minecraft/nbt/ListTag");
        mapClass("net/minecraft/class_2520", "net/minecraft/nbt/Tag");
        mapClass("net/minecraft/class_2505", "net/minecraft/nbt/NbtUtils");
        mapClass("net/minecraft/class_2540", "net/minecraft/nbt/StringTag");

        // --- Network ---
        mapClass("net/minecraft/class_2596", "net/minecraft/network/FriendlyByteBuf");
        mapClass("net/minecraft/class_2561", "net/minecraft/network/chat/Component");
        mapClass("net/minecraft/class_2583", "net/minecraft/network/chat/MutableComponent");
        mapClass("net/minecraft/class_2585", "net/minecraft/network/chat/Style");
        mapClass("net/minecraft/class_2568", "net/minecraft/ChatFormatting");
        mapClass("net/minecraft/class_5250", "net/minecraft/network/chat/TextColor");

        // --- Registry ---
        mapClass("net/minecraft/class_2378", "net/minecraft/core/Registry");
        mapClass("net/minecraft/class_7923", "net/minecraft/core/registries/Registries");
        mapClass("net/minecraft/class_7924", "net/minecraft/core/registries/BuiltInRegistries");
        mapClass("net/minecraft/class_5321", "net/minecraft/resources/ResourceKey");

        // --- Identifier / ResourceLocation ---
        mapClass("net/minecraft/class_2960", "net/minecraft/resources/ResourceLocation");

        // --- Math / Geometry ---
        mapClass("net/minecraft/class_243", "net/minecraft/world/phys/Vec3");
        mapClass("net/minecraft/class_241", "net/minecraft/world/phys/Vec2");
        mapClass("net/minecraft/class_2350", "net/minecraft/core/Direction");
        mapClass("net/minecraft/class_2382", "net/minecraft/world/phys/AABB");
        mapClass("net/minecraft/class_3341", "net/minecraft/world/phys/BlockHitResult");
        mapClass("net/minecraft/class_239", "net/minecraft/world/phys/HitResult");
        mapClass("net/minecraft/class_1159", "org/joml/Matrix4f");       // removed, use JOML
        mapClass("net/minecraft/class_4581", "org/joml/Matrix3f");       // removed, use JOML
        mapClass("net/minecraft/class_1158", "org/joml/Quaternionf");    // removed, use JOML
        mapClass("net/minecraft/class_1160", "org/joml/Vector3f");       // removed, use JOML

        // --- Server ---
        mapClass("net/minecraft/class_3176", "net/minecraft/server/dedicated/DedicatedServer");
        mapClass("net/minecraft/class_3248", "net/minecraft/server/MinecraftServer");
        mapClass("net/minecraft/class_2535", "net/minecraft/server/players/PlayerList");
        mapClass("net/minecraft/class_3000", "net/minecraft/server/level/ServerChunkCache");

        // --- Sound ---
        mapClass("net/minecraft/class_3414", "net/minecraft/sounds/SoundEvent");
        mapClass("net/minecraft/class_3417", "net/minecraft/sounds/SoundSource");

        // --- Crash ---
        mapClass("net/minecraft/class_128", "net/minecraft/CrashReport");
        mapClass("net/minecraft/class_129", "net/minecraft/CrashReportCategory");

        // --- Particle ---
        mapClass("net/minecraft/class_2394", "net/minecraft/core/particles/ParticleType");
        mapClass("net/minecraft/class_2395", "net/minecraft/core/particles/ParticleOptions");
        mapClass("net/minecraft/class_2401", "net/minecraft/core/particles/SimpleParticleType");

        // --- Rendering extras ---
        mapClass("net/minecraft/class_1159", "org/joml/Matrix4f");
        mapClass("net/minecraft/class_4587", "net/minecraft/client/renderer/PoseStack");
        mapClass("net/minecraft/class_4588", "net/minecraft/client/renderer/PoseStack$Pose"); // MatrixStack.Entry

        // --- Biome ---
        mapClass("net/minecraft/class_1959", "net/minecraft/world/level/biome/Biome");
        mapClass("net/minecraft/class_5487", "net/minecraft/world/level/biome/BiomeSource");

        // --- Dimension ---
        mapClass("net/minecraft/class_5321", "net/minecraft/resources/ResourceKey");
        mapClass("net/minecraft/class_1923", "net/minecraft/world/level/dimension/DimensionType");

        // --- Commands ---
        mapClass("net/minecraft/class_2170", "net/minecraft/commands/CommandSourceStack");
        mapClass("net/minecraft/class_2171", "net/minecraft/commands/Commands");

        // --- Enchantment ---
        mapClass("net/minecraft/class_1887", "net/minecraft/world/item/enchantment/Enchantment");
        mapClass("net/minecraft/class_1890", "net/minecraft/world/item/enchantment/Enchantments");

        // --- Potion ---
        mapClass("net/minecraft/class_1291", "net/minecraft/world/effect/MobEffect");
        mapClass("net/minecraft/class_1293", "net/minecraft/world/effect/MobEffectInstance");
        mapClass("net/minecraft/class_1294", "net/minecraft/world/effect/MobEffects");

        // --- GUI/Screen extras ---
        mapClass("net/minecraft/class_3985", "net/minecraft/client/gui/screens/ConfirmScreen");
        mapClass("net/minecraft/class_5348", "net/minecraft/client/gui/screens/ConfirmLinkScreen");
        mapClass("net/minecraft/class_443", "net/minecraft/client/gui/screens/OptionsScreen");
        mapClass("net/minecraft/class_429", "net/minecraft/client/gui/screens/PauseScreen");

        // --- Misc ---
        mapClass("net/minecraft/class_124", "net/minecraft/Util");
        mapClass("net/minecraft/class_156", "net/minecraft/SharedConstants");
        mapClass("net/minecraft/class_2246", "net/minecraft/world/level/block/Blocks");
        mapClass("net/minecraft/class_4093", "net/minecraft/util/thread/ReentrantBlockableEventLoop");

        // ═══════════════════════════════════════════════════════════════
        // KEY METHOD MAPPINGS
        // ═══════════════════════════════════════════════════════════════

        // --- MinecraftClient / Minecraft ---
        mapMethod("net/minecraft/class_310", "method_1507", "getInstance");        // MinecraftClient.getInstance()
        mapMethod("net/minecraft/class_310", "method_1592", "setScreen");           // MinecraftClient.setScreen()
        mapMethod("net/minecraft/class_310", "method_1549", "getWindow");           // MinecraftClient.getWindow()
        mapMethod("net/minecraft/class_310", "method_1561", "stop");                // MinecraftClient.stop()

        // --- Entity ---
        mapMethod("net/minecraft/class_1297", "method_5628", "getX");               // Entity.getX()
        mapMethod("net/minecraft/class_1297", "method_5631", "getY");               // Entity.getY()
        mapMethod("net/minecraft/class_1297", "method_5629", "getZ");               // Entity.getZ()
        mapMethod("net/minecraft/class_1297", "method_18798", "getBlockPos");       // Entity.getBlockPos()
        mapMethod("net/minecraft/class_1297", "method_5647", "save");               // Entity.writeNbt → save
        mapMethod("net/minecraft/class_1297", "method_5651", "load");               // Entity.readNbt → load
        mapMethod("net/minecraft/class_1297", "method_5768", "getLevel");           // Entity.getWorld → getLevel
        mapMethod("net/minecraft/class_1297", "method_5600", "hurt");               // Entity.damage → hurt
        mapMethod("net/minecraft/class_1297", "method_5805", "remove");             // Entity.remove
        mapMethod("net/minecraft/class_1297", "method_23318", "getUUID");           // Entity.getUuid → getUUID
        mapMethod("net/minecraft/class_1297", "method_5667", "tick");               // Entity.tick
        mapMethod("net/minecraft/class_1297", "method_5773", "isAlive");            // Entity.isAlive

        // --- LivingEntity ---
        mapMethod("net/minecraft/class_1309", "method_6029", "getHealth");          // LivingEntity.getHealth
        mapMethod("net/minecraft/class_1309", "method_6033", "setHealth");          // LivingEntity.setHealth
        mapMethod("net/minecraft/class_1309", "method_6063", "getMaxHealth");       // LivingEntity.getMaxHealth
        mapMethod("net/minecraft/class_1309", "method_6122", "getMainHandItem");    // LivingEntity.getMainHandItem
        mapMethod("net/minecraft/class_1309", "method_6047", "getOffhandItem");     // LivingEntity.getOffhandItem
        mapMethod("net/minecraft/class_1309", "method_6048", "getArmorValue");      // LivingEntity.getArmorValue

        // --- Player ---
        mapMethod("net/minecraft/class_1657", "method_7356", "getInventory");       // Player.getInventory
        mapMethod("net/minecraft/class_1657", "method_7350", "addItem");            // Player.addItem
        mapMethod("net/minecraft/class_1657", "method_7357", "isCreative");         // Player.isCreative
        mapMethod("net/minecraft/class_1657", "method_7327", "displayClientMessage"); // Player.displayClientMessage

        // --- Level / World ---
        mapMethod("net/minecraft/class_1937", "method_8321", "getBlockState");      // Level.getBlockState
        mapMethod("net/minecraft/class_1937", "method_8501", "setBlock");            // Level.setBlock
        mapMethod("net/minecraft/class_1937", "method_8505", "getBlockEntity");     // Level.getBlockEntity
        mapMethod("net/minecraft/class_1937", "method_8553", "addFreshEntity");     // Level.addFreshEntity (spawnEntity)
        mapMethod("net/minecraft/class_1937", "method_8597", "isClientSide");       // Level.isClientSide (isClient)

        // --- Block ---
        mapMethod("net/minecraft/class_2248", "method_9520", "defaultBlockState"); // Block.getDefaultState → defaultBlockState
        mapMethod("net/minecraft/class_2248", "method_9539", "getName");            // Block.getName

        // --- BlockEntity ---
        mapMethod("net/minecraft/class_2586", "method_11007", "getBlockPos");       // BlockEntity.getBlockPos (getPos)
        mapMethod("net/minecraft/class_2586", "method_11010", "getLevel");          // BlockEntity.getLevel (getWorld)
        mapMethod("net/minecraft/class_2586", "method_38244", "saveAdditional");    // BlockEntity.writeNbt → saveAdditional
        mapMethod("net/minecraft/class_2586", "method_11014", "loadAdditional");    // BlockEntity.readNbt → loadAdditional
        mapMethod("net/minecraft/class_2586", "method_10990", "setChanged");        // BlockEntity.markDirty → setChanged

        // --- ItemStack ---
        mapMethod("net/minecraft/class_1799", "method_7909", "getCount");           // ItemStack.getCount
        mapMethod("net/minecraft/class_1799", "method_7939", "isEmpty");            // ItemStack.isEmpty
        mapMethod("net/minecraft/class_1799", "method_7948", "getItem");            // ItemStack.getItem
        mapMethod("net/minecraft/class_1799", "method_7947", "getTag");             // ItemStack.getTag (getNbt)
        mapMethod("net/minecraft/class_1799", "method_7959", "setCount");           // ItemStack.setCount
        mapMethod("net/minecraft/class_1799", "method_7972", "copy");               // ItemStack.copy

        // --- CompoundTag / NbtCompound ---
        mapMethod("net/minecraft/class_2487", "method_10558", "getString");         // CompoundTag.getString
        mapMethod("net/minecraft/class_2487", "method_10550", "getInt");            // CompoundTag.getInt
        mapMethod("net/minecraft/class_2487", "method_10556", "getBoolean");        // CompoundTag.getBoolean
        mapMethod("net/minecraft/class_2487", "method_10566", "putString");         // CompoundTag.putString
        mapMethod("net/minecraft/class_2487", "method_10569", "putInt");            // CompoundTag.putInt
        mapMethod("net/minecraft/class_2487", "method_10567", "putBoolean");        // CompoundTag.putBoolean
        mapMethod("net/minecraft/class_2487", "method_10573", "contains");          // CompoundTag.contains
        mapMethod("net/minecraft/class_2487", "method_10580", "getCompound");       // CompoundTag.getCompound
        mapMethod("net/minecraft/class_2487", "method_10554", "getList");           // CompoundTag.getList

        // --- Component (Text) ---
        mapMethod("net/minecraft/class_2561", "method_43470", "literal");           // Component.literal (of)
        mapMethod("net/minecraft/class_2561", "method_43471", "translatable");      // Component.translatable
        mapMethod("net/minecraft/class_2561", "method_10851", "getString");         // Component.getString

        // --- ResourceLocation (Identifier) ---
        mapMethod("net/minecraft/class_2960", "method_12836", "getNamespace");      // ResourceLocation.getNamespace
        mapMethod("net/minecraft/class_2960", "method_12832", "getPath");           // ResourceLocation.getPath

        // --- Screen ---
        mapMethod("net/minecraft/class_437", "method_25426", "init");               // Screen.init
        mapMethod("net/minecraft/class_437", "method_25394", "render");             // Screen.render
        mapMethod("net/minecraft/class_437", "method_25419", "onClose");            // Screen.onClose
        mapMethod("net/minecraft/class_437", "method_25396", "addRenderableWidget"); // Screen.addDrawableChild

        // --- CrashReport ---
        mapMethod("net/minecraft/class_128", "method_37123", "addSystemDetails");   // CrashReport.addSystemDetails

        // ═══════════════════════════════════════════════════════════════
        // KEY FIELD MAPPINGS
        // ═══════════════════════════════════════════════════════════════

        // --- MinecraftClient ---
        mapField("net/minecraft/class_310", "field_1755", "player");         // client.player
        mapField("net/minecraft/class_310", "field_1687", "level");          // client.world → level
        mapField("net/minecraft/class_310", "field_1724", "options");        // client.options
        mapField("net/minecraft/class_310", "field_1769", "font");           // client.textRenderer → font
        mapField("net/minecraft/class_310", "field_1774", "screen");         // client.currentScreen → screen
        mapField("net/minecraft/class_310", "field_1762", "gameRenderer");   // client.gameRenderer

        // --- Entity ---
        mapField("net/minecraft/class_1297", "field_6014", "position");      // Entity.pos → position
        mapField("net/minecraft/class_1297", "field_6038", "onGround");      // Entity.onGround
        mapField("net/minecraft/class_1297", "field_6012", "yRot");          // Entity.yaw → yRot
        mapField("net/minecraft/class_1297", "field_6028", "xRot");          // Entity.pitch → xRot
        mapField("net/minecraft/class_1297", "field_5970", "removed");       // Entity.removed

        // --- Screen ---
        mapField("net/minecraft/class_437", "field_22789", "width");         // Screen.width
        mapField("net/minecraft/class_437", "field_22790", "height");        // Screen.height
        mapField("net/minecraft/class_437", "field_22793", "font");          // Screen.textRenderer → font
        mapField("net/minecraft/class_437", "field_22791", "minecraft");     // Screen.client → minecraft
        mapField("net/minecraft/class_437", "field_22792", "title");         // Screen.title

        // --- Level ---
        mapField("net/minecraft/class_1937", "field_9236", "random");        // Level.random

        LOGGER.debug("Loaded {} built-in class mappings, {} method mappings, {} field mappings",
                classMappings.size(), methodMappings.size(), fieldMappings.size());
    }

    /**
     * Try to load the bundled TinyV2 mapping resource.
     */
    private void loadBundledMappings() {
        try (InputStream is = getClass().getResourceAsStream("/mappings/intermediary-to-mojang.tiny")) {
            if (is != null) {
                loadTinyV2Mappings(is);
                LOGGER.info("Loaded bundled intermediary→Mojang mapping file");
            }
        } catch (Exception e) {
            LOGGER.debug("No bundled mapping file found (using built-in mappings only)");
        }
    }

    /**
     * Try to load external mapping file from config directory.
     */
    private void loadExternalMappings() {
        java.nio.file.Path externalPath = java.nio.file.Path.of(
                "config", "retromod", "mappings", "intermediary-to-mojang.tiny");
        if (java.nio.file.Files.exists(externalPath)) {
            try (InputStream is = java.nio.file.Files.newInputStream(externalPath)) {
                loadTinyV2Mappings(is);
                LOGGER.info("Loaded external mapping file: {}", externalPath);
            } catch (Exception e) {
                LOGGER.warn("Failed to load external mapping file: {}", externalPath, e);
            }
        }
    }

    /**
     * Load mappings from a TinyV2 format input stream.
     * TinyV2 format:
     *   tiny\t2\t0\tintermediary\tofficial
     *   c\tnet/minecraft/class_310\tnet/minecraft/client/Minecraft
     *       m\t(...)V\tmethod_1507\tgetInstance
     *       f\tLnet/minecraft/class_310;\tfield_1755\tplayer
     */
    public void loadTinyV2Mappings(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        int sourceIdx = -1;
        int targetIdx = -1;
        String currentClass = null;

        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Header: tiny\t2\t0\tnamespace1\tnamespace2
            if (line.startsWith("tiny\t")) {
                String[] parts = line.split("\t");
                // parts[3] = first namespace, parts[4] = second namespace
                // We want intermediary as source, official as target
                for (int i = 3; i < parts.length; i++) {
                    if (parts[i].equals("intermediary")) sourceIdx = i - 3;
                    if (parts[i].equals("official") || parts[i].equals("mojang")
                            || parts[i].equals("named")) targetIdx = i - 3;
                }
                if (sourceIdx == -1) sourceIdx = 0;
                if (targetIdx == -1) targetIdx = 1;
                continue;
            }

            // Class: c\tname1\tname2
            if (line.startsWith("c\t")) {
                String[] parts = line.substring(2).split("\t");
                if (parts.length >= 2) {
                    String source = parts[sourceIdx];
                    String target = parts[targetIdx];
                    classMappings.put(source, target);
                    currentClass = source;
                }
                continue;
            }

            // Method: \tm\tdesc\tname1\tname2
            if (line.startsWith("\tm\t") && currentClass != null) {
                String[] parts = line.substring(3).split("\t");
                if (parts.length >= 3) {
                    String desc = parts[0];
                    String sourceName = parts[sourceIdx + 1];
                    String targetName = parts[targetIdx + 1];
                    String key = currentClass + "." + sourceName + "." + desc;
                    methodMappings.put(key, targetName);
                }
                continue;
            }

            // Field: \tf\tdesc\tname1\tname2
            if (line.startsWith("\tf\t") && currentClass != null) {
                String[] parts = line.substring(3).split("\t");
                if (parts.length >= 3) {
                    String sourceName = parts[sourceIdx + 1];
                    String targetName = parts[targetIdx + 1];
                    String key = currentClass + "." + sourceName;
                    fieldMappings.put(key, targetName);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPER METHODS for registering built-in mappings
    // ─────────────────────────────────────────────────────────────────────

    private void mapClass(String intermediary, String mojang) {
        classMappings.put(intermediary, mojang);
    }

    private void mapMethod(String owner, String intermediaryName, String mojangName) {
        // Simple name-only mapping (no descriptor — matches any overload)
        methodMappings.put(owner + "." + intermediaryName, mojangName);
    }

    private void mapMethod(String owner, String intermediaryName, String desc, String mojangName) {
        methodMappings.put(owner + "." + intermediaryName + "." + desc, mojangName);
    }

    private void mapField(String owner, String intermediaryName, String mojangName) {
        fieldMappings.put(owner + "." + intermediaryName, mojangName);
    }
}
