#!/usr/bin/env python3
"""Harvest a validated 1.12.2 (MCP) -> 1.20.x (Mojang) class-move table.

The bundled forge-1.12.2-class-moves.tsv targets 26.1 names, but the reported
1.12.2 hosts (#103/#108/#117) run 1.20.1, where some 26.1 targets don't exist
yet (fine, same name pre-26.1) and some 1.20.1 classes were REMOVED by 26.1 and
so could never appear in the 26.1 table (SwordItem, DiggerItem, UseAnim, ...).

Strategy: start from the 26.1 table's 1.12.2 names, retarget each against the
REAL 1.20.1 class set (from Mojang's official ProGuard mappings, since the
1.20.1 jar itself is obfuscated), falling back through the reversed 26.1
class-move table for names 26.1 changed. Then add curated entries for the
26.1-removed families. Every emitted target is verified to exist in 1.20.1.

Usage: harvest-1.12.2-class-moves-1201.py <existing-26.1-tsv> <mojang-1.20.1-client.txt>
       <mojang-class-moves-26.1.tsv> <out-tsv>
"""
import sys

SRC_TSV, MOJANG_TXT, MOVES_26_1, OUT = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

# 1.20.1 class set (internal names) from the ProGuard mappings' left-hand side
mc1201 = set()
with open(MOJANG_TXT, encoding="utf-8") as f:
    for line in f:
        if line.startswith("#") or line.startswith(" ") or "->" not in line:
            continue
        name = line.split(" -> ")[0].strip().replace(".", "/")
        mc1201.add(name)
print(f"1.20.1 class set: {len(mc1201)} classes")

def exists(c):
    return c in mc1201

# reversed 26.1 package-reorg moves: 26.1 name -> pre-26.1 name
rev_26_1 = {}
with open(MOVES_26_1, encoding="utf-8") as f:
    for line in f:
        if line.startswith("#") or "\t" not in line:
            continue
        old, new = line.rstrip("\n").split("\t")[:2]
        rev_26_1[new] = old

rows = []
dropped = []
with open(SRC_TSV, encoding="utf-8") as f:
    for line in f:
        if line.startswith("#") or "\t" not in line:
            continue
        old, target = line.rstrip("\n").split("\t")[:2]
        if exists(target):
            rows.append((old, target))
        elif target in rev_26_1 and exists(rev_26_1[target]):
            rows.append((old, rev_26_1[target]))
        else:
            dropped.append((old, target))

# 26.1-removed families that still exist on 1.20.1 (the whole point of this table).
# Every target is validated below like the rest.
CURATED_1201 = {
    "net/minecraft/item/ItemSword": "net/minecraft/world/item/SwordItem",
    "net/minecraft/item/ItemTool": "net/minecraft/world/item/DiggerItem",
    "net/minecraft/item/ItemSpade": "net/minecraft/world/item/ShovelItem",
    "net/minecraft/item/ItemPickaxe": "net/minecraft/world/item/PickaxeItem",
    "net/minecraft/item/ItemAxe": "net/minecraft/world/item/AxeItem",
    "net/minecraft/item/ItemHoe": "net/minecraft/world/item/HoeItem",
    "net/minecraft/item/ItemArmor": "net/minecraft/world/item/ArmorItem",
    "net/minecraft/item/EnumAction": "net/minecraft/world/item/UseAnim",
    "net/minecraft/item/ItemArmor$ArmorMaterial": "net/minecraft/world/item/ArmorMaterials",
    "net/minecraft/item/Item$ToolMaterial": "net/minecraft/world/item/Tiers",
    "net/minecraft/block/material/Material": "net/minecraft/world/level/material/Material",
    "net/minecraft/block/material/MapColor": "net/minecraft/world/level/material/MapColor",
    "net/minecraft/block/SoundType": "net/minecraft/world/level/block/SoundType",
    "net/minecraft/creativetab/CreativeTabs": "net/minecraft/world/item/CreativeModeTab",
}
existing_olds = {o for o, _ in rows}
for old, target in sorted(CURATED_1201.items()):
    if old in existing_olds:
        continue  # the derived row already covers it
    if exists(target):
        rows.append((old, target))
    else:
        dropped.append((old, target))

rows.sort()
with open(OUT, "w", encoding="utf-8") as f:
    f.write("# Retromod 1.12.2 (MCP) -> 1.20.x (Mojang) class moves."
            " Targets validated vs Mojang's official 1.20.1 mappings.\n")
    f.write("# Derived from forge-1.12.2-class-moves.tsv (26.1 targets) by"
            " scripts/harvest-1.12.2-class-moves-1201.py; plus the 26.1-removed"
            " families that still exist on 1.20.x.\n")
    for old, target in rows:
        f.write(f"{old}\t{target}\n")

print(f"emitted {len(rows)} rows -> {OUT}")
if dropped:
    print(f"dropped {len(dropped)} (no valid 1.20.1 target):")
    for old, target in dropped:
        print(f"  {old} -> {target}")
