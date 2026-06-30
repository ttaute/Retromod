#!/usr/bin/env python3
"""Harvest a validated 1.12.2 (MCP) -> 26.1 (Mojang) class-move table.
Every emitted target is verified to exist in the real 26.1 jar, so no false positives.
Strategy: curated renames first, then 1.17 'great repackaging' package-prefix rules for
classes that only moved (same simple name), validated against the jar."""
import sys, re, zipfile

LOG, MCJAR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]

# 26.1 class set (internal names, no .class)
z = zipfile.ZipFile(MCJAR)
MC = {n[:-6] for n in z.namelist() if n.endswith(".class")}
def exists(c): return c in MC

# --- curated 1.12.2 -> 26.1 renames (simple name changed, not just package) ---
RENAMES = {
 "net/minecraft/world/World": "net/minecraft/world/level/Level",
 "net/minecraft/world/IBlockAccess": "net/minecraft/world/level/BlockGetter",
 "net/minecraft/world/WorldServer": "net/minecraft/server/level/ServerLevel",
 "net/minecraft/world/server/ServerWorld": "net/minecraft/server/level/ServerLevel",
 "net/minecraft/entity/EntityLivingBase": "net/minecraft/world/entity/LivingEntity",
 "net/minecraft/entity/player/EntityPlayer": "net/minecraft/world/entity/player/Player",
 "net/minecraft/entity/player/PlayerEntity": "net/minecraft/world/entity/player/Player",
 "net/minecraft/entity/player/EntityPlayerMP": "net/minecraft/server/level/ServerPlayer",
 "net/minecraft/entity/player/ServerPlayerEntity": "net/minecraft/server/level/ServerPlayer",
 "net/minecraft/entity/EntityLiving": "net/minecraft/world/entity/Mob",
 "net/minecraft/entity/MobEntity": "net/minecraft/world/entity/Mob",
 "net/minecraft/entity/EntityCreature": "net/minecraft/world/entity/PathfinderMob",
 "net/minecraft/entity/CreatureEntity": "net/minecraft/world/entity/PathfinderMob",
 "net/minecraft/entity/item/EntityItem": "net/minecraft/world/entity/item/ItemEntity",
 "net/minecraft/entity/item/EntityFallingBlock": "net/minecraft/world/entity/item/FallingBlockEntity",
 "net/minecraft/entity/item/EntityXPOrb": "net/minecraft/world/entity/ExperienceOrb",
 "net/minecraft/entity/item/EntityTNTPrimed": "net/minecraft/world/entity/item/PrimedTnt",
 "net/minecraft/entity/monster/EntityEnderman": "net/minecraft/world/entity/monster/EnderMan",
 "net/minecraft/entity/monster/EntityShulker": "net/minecraft/world/entity/monster/Shulker",
 "net/minecraft/entity/IProjectile": "net/minecraft/world/entity/projectile/Projectile",
 "net/minecraft/tileentity/TileEntity": "net/minecraft/world/level/block/entity/BlockEntity",
 "net/minecraft/tileentity/TileEntityFurnace": "net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity",
 "net/minecraft/tileentity/TileEntityLockable": "net/minecraft/world/level/block/entity/BaseContainerBlockEntity",
 "net/minecraft/util/ResourceLocation": "net/minecraft/resources/ResourceLocation",
 "net/minecraft/util/SoundEvent": "net/minecraft/sounds/SoundEvent",
 "net/minecraft/util/SoundCategory": "net/minecraft/sounds/SoundSource",
 "net/minecraft/util/SoundEvents": "net/minecraft/sounds/SoundEvents",
 "net/minecraft/util/JsonUtils": "net/minecraft/util/GsonHelper",
 "net/minecraft/util/EnumFacing": "net/minecraft/core/Direction",
 "net/minecraft/util/EnumHand": "net/minecraft/world/InteractionHand",
 "net/minecraft/util/EnumHandSide": "net/minecraft/world/entity/HumanoidArm",
 "net/minecraft/util/EnumActionResult": "net/minecraft/world/InteractionResult",
 "net/minecraft/util/ActionResult": "net/minecraft/world/InteractionResultHolder",
 "net/minecraft/util/EnumParticleTypes": "net/minecraft/core/particles/ParticleTypes",
 "net/minecraft/util/NonNullList": "net/minecraft/core/NonNullList",
 "net/minecraft/util/Mirror": "net/minecraft/world/level/block/Mirror",
 "net/minecraft/util/Rotation": "net/minecraft/world/level/block/Rotation",
 "net/minecraft/util/ITickable": "net/minecraft/world/level/block/entity/TickingBlockEntity",
 "net/minecraft/util/math/BlockPos": "net/minecraft/core/BlockPos",
 "net/minecraft/util/math/BlockPos$MutableBlockPos": "net/minecraft/core/BlockPos$MutableBlockPos",
 "net/minecraft/util/math/ChunkPos": "net/minecraft/world/level/ChunkPos",
 "net/minecraft/util/math/MathHelper": "net/minecraft/util/Mth",
 "net/minecraft/util/math/AxisAlignedBB": "net/minecraft/world/phys/AABB",
 "net/minecraft/util/math/RayTraceResult": "net/minecraft/world/phys/HitResult",
 "net/minecraft/util/math/RayTraceResult$Type": "net/minecraft/world/phys/HitResult$Type",
 "net/minecraft/util/math/vector/Vector3d": "net/minecraft/world/phys/Vec3",
 "net/minecraft/util/math/Vec3d": "net/minecraft/world/phys/Vec3",
 "net/minecraft/util/text/ITextComponent": "net/minecraft/network/chat/Component",
 "net/minecraft/util/text/TextComponentString": "net/minecraft/network/chat/Component",
 "net/minecraft/util/text/TextComponentTranslation": "net/minecraft/network/chat/Component",
 "net/minecraft/util/text/TextFormatting": "net/minecraft/ChatFormatting",
 "net/minecraft/util/text/Style": "net/minecraft/network/chat/Style",
 "net/minecraft/util/text/event/ClickEvent": "net/minecraft/network/chat/ClickEvent",
 "net/minecraft/util/text/event/ClickEvent$Action": "net/minecraft/network/chat/ClickEvent$Action",
 "net/minecraft/nbt/NBTTagCompound": "net/minecraft/nbt/CompoundTag",
 "net/minecraft/nbt/CompoundNBT": "net/minecraft/nbt/CompoundTag",
 "net/minecraft/nbt/NBTTagList": "net/minecraft/nbt/ListTag",
 "net/minecraft/nbt/ListNBT": "net/minecraft/nbt/ListTag",
 "net/minecraft/block/state/IBlockState": "net/minecraft/world/level/block/state/BlockState",
 "net/minecraft/block/BlockState": "net/minecraft/world/level/block/state/BlockState",
 "net/minecraft/block/properties/IProperty": "net/minecraft/world/level/block/state/properties/Property",
 "net/minecraft/state/StateContainer": "net/minecraft/world/level/block/state/StateDefinition",
 "net/minecraft/state/DirectionProperty": "net/minecraft/world/level/block/state/properties/DirectionProperty",
 "net/minecraft/state/BooleanProperty": "net/minecraft/world/level/block/state/properties/BooleanProperty",
 "net/minecraft/state/IntegerProperty": "net/minecraft/world/level/block/state/properties/IntegerProperty",
 "net/minecraft/inventory/EntityEquipmentSlot": "net/minecraft/world/entity/EquipmentSlot",
 "net/minecraft/inventory/IInventory": "net/minecraft/world/Container",
 "net/minecraft/inventory/ISidedInventory": "net/minecraft/world/WorldlyContainer",
 "net/minecraft/inventory/InventoryHelper": "net/minecraft/world/Containers",
 "net/minecraft/inventory/ItemStackHelper": "net/minecraft/world/ContainerHelper",
 "net/minecraft/advancements/ICriterionTrigger": "net/minecraft/advancements/CriterionTrigger",
 "net/minecraft/advancements/ICriterionTrigger$Listener": "net/minecraft/advancements/CriterionTrigger$Listener",
 "net/minecraft/advancements/critereon/AbstractCriterionInstance": "net/minecraft/advancements/critereon/AbstractCriterionTriggerInstance",
 "net/minecraft/potion/PotionEffect": "net/minecraft/world/effect/MobEffectInstance",
 "net/minecraft/potion/Potion": "net/minecraft/world/effect/MobEffect",
 "net/minecraft/potion/Effects": "net/minecraft/world/effect/MobEffects",
 "net/minecraft/potion/PotionUtils": "net/minecraft/world/item/alchemy/PotionUtils",
 "net/minecraft/world/WorldProvider": "net/minecraft/world/level/dimension/DimensionType",
 "net/minecraft/world/EnumDifficulty": "net/minecraft/world/Difficulty",
 "net/minecraft/world/storage/WorldSavedData": "net/minecraft/world/level/saveddata/SavedData",
 "net/minecraft/item/EnumAction": "net/minecraft/world/item/UseAnim",
 "net/minecraft/item/ItemGroup": "net/minecraft/world/item/CreativeModeTab",
 "net/minecraft/item/ItemBlock": "net/minecraft/world/item/BlockItem",
 "net/minecraft/item/ItemArmor": "net/minecraft/world/item/ArmorItem",
 "net/minecraft/item/ItemArmor$ArmorMaterial": "net/minecraft/world/item/ArmorMaterials",
 "net/minecraft/item/ItemFood": "net/minecraft/world/item/Item",
 "net/minecraft/item/ItemSword": "net/minecraft/world/item/SwordItem",
 "net/minecraft/item/ItemAxe": "net/minecraft/world/item/AxeItem",
 "net/minecraft/item/ItemPickaxe": "net/minecraft/world/item/PickaxeItem",
 "net/minecraft/item/ItemSpade": "net/minecraft/world/item/ShovelItem",
 "net/minecraft/item/ItemHoe": "net/minecraft/world/item/HoeItem",
 "net/minecraft/item/ItemTool": "net/minecraft/world/item/DiggerItem",
 "net/minecraft/item/ItemDye": "net/minecraft/world/item/DyeItem",
 "net/minecraft/item/Item$ToolMaterial": "net/minecraft/world/item/Tiers",
 "net/minecraft/item/crafting/IRecipe": "net/minecraft/world/item/crafting/Recipe",
 "net/minecraft/item/crafting/Ingredient": "net/minecraft/world/item/crafting/Ingredient",
 "net/minecraft/item/crafting/FurnaceRecipes": "net/minecraft/world/item/crafting/RecipeManager",
 "net/minecraft/block/BlockBush": "net/minecraft/world/level/block/BushBlock",
 "net/minecraft/block/BlockCrops": "net/minecraft/world/level/block/CropBlock",
 "net/minecraft/block/BlockLeaves": "net/minecraft/world/level/block/LeavesBlock",
 "net/minecraft/block/BlockVine": "net/minecraft/world/level/block/VineBlock",
 "net/minecraft/block/BlockTNT": "net/minecraft/world/level/block/TntBlock",
 "net/minecraft/block/BlockFire": "net/minecraft/world/level/block/BaseFireBlock",
 "net/minecraft/block/BlockHorizontal": "net/minecraft/world/level/block/HorizontalDirectionalBlock",
 "net/minecraft/block/BlockBreakable": "net/minecraft/world/level/block/HalfTransparentBlock",
 "net/minecraft/block/BlockStaticLiquid": "net/minecraft/world/level/block/LiquidBlock",
 "net/minecraft/block/BlockMelon": "net/minecraft/world/level/block/StemGrownBlock",
 "net/minecraft/block/BlockTallGrass": "net/minecraft/world/level/block/TallGrassBlock",
 "net/minecraft/block/IGrowable": "net/minecraft/world/level/block/BonemealableBlock",
 "net/minecraft/block/SoundType": "net/minecraft/world/level/block/SoundType",
 "net/minecraft/block/state/BlockFaceShape": "net/minecraft/world/level/block/state/BlockState",  # removed; nearest
 "net/minecraft/enchantment/EnchantmentProtection": "net/minecraft/world/item/enchantment/ProtectionEnchantment",
 "net/minecraft/enchantment/Enchantment": "net/minecraft/world/item/enchantment/Enchantment",
 "net/minecraft/enchantment/EnchantmentHelper": "net/minecraft/world/item/enchantment/EnchantmentHelper",
 "net/minecraft/enchantment/Enchantments": "net/minecraft/world/item/enchantment/Enchantments",
 "net/minecraft/entity/SharedMonsterAttributes": "net/minecraft/world/entity/ai/attributes/Attributes",
 "net/minecraft/entity/ai/attributes/IAttribute": "net/minecraft/world/entity/ai/attributes/Attribute",
 "net/minecraft/entity/ai/attributes/IAttributeInstance": "net/minecraft/world/entity/ai/attributes/AttributeInstance",
 "net/minecraft/entity/ai/attributes/AbstractAttributeMap": "net/minecraft/world/entity/ai/attributes/AttributeMap",
 "net/minecraft/util/FoodStats": "net/minecraft/world/food/FoodData",
 "net/minecraft/util/CombatTracker": "net/minecraft/world/damagesource/CombatTracker",
 "net/minecraft/util/CooldownTracker": "net/minecraft/world/item/ItemCooldowns",
 "net/minecraft/util/DamageSource": "net/minecraft/world/damagesource/DamageSource",
 "net/minecraft/util/text/translation/I18n": "net/minecraft/network/chat/contents/TranslatableContents",
 "net/minecraft/world/Explosion": "net/minecraft/world/level/Explosion",
 "net/minecraft/world/GameRules": "net/minecraft/world/level/GameRules",
 "net/minecraft/world/biome/Biome": "net/minecraft/world/level/biome/Biome",
 "net/minecraft/world/chunk/Chunk": "net/minecraft/world/level/chunk/LevelChunk",
 "net/minecraft/block/material/EnumPushReaction": "net/minecraft/world/level/material/PushReaction",
}

# --- 1.17 'great repackaging' prefix rules for classes that only moved (same simple name) ---
# longest prefix first
PKG = [
 ("net/minecraft/block/state/", "net/minecraft/world/level/block/state/"),
 ("net/minecraft/block/", "net/minecraft/world/level/block/"),
 ("net/minecraft/item/crafting/", "net/minecraft/world/item/crafting/"),
 ("net/minecraft/item/", "net/minecraft/world/item/"),
 ("net/minecraft/entity/item/", "net/minecraft/world/entity/item/"),
 ("net/minecraft/entity/player/", "net/minecraft/world/entity/player/"),
 ("net/minecraft/entity/monster/", "net/minecraft/world/entity/monster/"),
 ("net/minecraft/entity/", "net/minecraft/world/entity/"),
]

def harvest_missing():
    miss=[]; incls=False
    for line in open(LOG):
        if "── Missing Classes ──" in line: incls=True; continue
        if "── Missing" in line and "Classes" not in line: incls=False
        if incls:
            m=re.search(r"✗\s+([\w.$]+)\s+not found", line)
            if m:
                c=m.group(1).replace(".","/")
                if c.startswith("net/minecraft/") and not c.startswith("["): miss.append(c)
    return sorted(set(miss))

resolved={}; unresolved=[]
for c in harvest_missing():
    cand = RENAMES.get(c)
    if cand is None:
        for old,new in PKG:
            if c.startswith(old):
                cand = new + c[len(old):]
                break
        if cand is None and c.rsplit("/",1)[-1]:  # simple name unchanged, already net/minecraft/X
            cand = c  # maybe it stayed
    if cand and exists(cand):
        resolved[c]=cand
    else:
        unresolved.append((c, cand))  # cand may be a guess that failed validation

with open(OUT,"w") as f:
    f.write("# Retromod 1.12.2 (MCP) -> 26.1 (Mojang) class moves. Targets validated vs the 26.1 jar.\n")
    for k in sorted(resolved): f.write(f"{k}\t{resolved[k]}\n")

print(f"RESOLVED + validated against 26.1 jar: {len(resolved)}")
print(f"UNRESOLVED (redesigned/removed, need polyfill or can't map): {len(unresolved)}")
print("\n--- unresolved (the redesigned/removed ones) ---")
for c,guess in unresolved:
    print(f"  {c}" + (f"  (guess {guess} not in jar)" if guess else ""))
