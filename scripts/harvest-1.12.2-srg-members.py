#!/usr/bin/env python3
"""Harvest a validated 1.12.2 SRG (func_/field_) -> readable-name member table.

Inputs are the MCPBot 1.12.2 stable CSV exports (methods.csv / fields.csv, columns
searge,name,side,desc) and Mojang's official 1.20.1 ProGuard mappings. The MCP
community names largely coincide with Mojang's official names for the members that
survived, so the join is: SRG -> MCP name, kept ONLY when that name exists as a
member of the right kind somewhere in the official 1.20.1 mappings. That existence
filter drops members whose Mojang name diverged (a wrong rename is worse than none:
an unmapped func_ fails exactly the same way, but a wrongly mapped one can silently
call the wrong thing).

There is no authoritative cross-version SRG-1.12.2 -> Mojang source; this is the
documented data-driven best effort (see docs/srg-mappings.md).

Usage: harvest-1.12.2-srg-members.py <methods.csv> <fields.csv> <mojang-1.20.1-client.txt> <out-tsv>
"""
import csv
import sys

METHODS, FIELDS, MOJANG_TXT, OUT = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]

# member-name sets from the official mappings (left-hand, deobfuscated side)
mojang_methods = set()
mojang_fields = set()
with open(MOJANG_TXT, encoding="utf-8") as f:
    for line in f:
        if not line.startswith("    ") or "->" not in line:
            continue
        left = line.strip().split(" -> ")[0]
        if "(" in left:  # method: "int getId(net.minecraft.x.Y)" possibly with "12:13:" prefix
            sig = left.split("(")[0]
            name = sig.split(" ")[-1].split(":")[-1]
            mojang_methods.add(name)
        else:  # field: "int someField"
            name = left.split(" ")[-1]
            mojang_fields.add(name)
print(f"1.20.1 official member names: {len(mojang_methods)} methods, {len(mojang_fields)} fields")

def harvest(csv_path, kind, valid_names):
    rows, dropped = [], 0
    with open(csv_path, encoding="utf-8") as f:
        for rec in csv.DictReader(f):
            srg, name = rec["searge"].strip(), rec["name"].strip()
            if not srg or not name or srg == name:
                continue
            if "<" in name or " " in name:
                continue
            if name in valid_names:
                rows.append((kind, srg, name))
            else:
                dropped += 1
    return rows, dropped

m_rows, m_drop = harvest(METHODS, "METHOD", mojang_methods)
f_rows, f_drop = harvest(FIELDS, "FIELD", mojang_fields)

with open(OUT, "w", encoding="utf-8") as f:
    f.write("# Retromod 1.12.2 SRG (func_/field_) -> readable member names.\n")
    f.write("# Source: MCPBot 1.12.2 stable CSVs, filtered to names that exist in Mojang's\n")
    f.write("# official 1.20.1 mappings (see scripts/harvest-1.12.2-srg-members.py).\n")
    f.write("# Same 3-column format as srg-to-mojang.tsv: KIND\\tSRG\\tNAME.\n")
    for kind, srg, name in sorted(f_rows) + sorted(m_rows):
        f.write(f"{kind}\t{srg}\t{name}\n")

print(f"emitted {len(m_rows)} methods (+{m_drop} dropped) and {len(f_rows)} fields (+{f_drop} dropped) -> {OUT}")
