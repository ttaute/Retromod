#!/usr/bin/env python3
"""
Resolve dangling Fabric-intermediary member ids to their Mojang names for a given MC version, by
composing that version's INTERMEDIARY tiny (obf <-> intermediary) with Mojang's PROGUARD mappings
(mojang -> obf). Use it to fix "intermediary version skew": the bundled intermediary-to-mojang.tsv
is harvested at the current (unobfuscated) target, but an OLDER mod references the OLDER-era id for
any member Mojang RENAMED since (or whose enclosing class was renamed, which re-mints the member
ids). Those older ids are absent from the harvest and are left dangling.

WHY IT IS SAFE: Fabric intermediary ids are append-only and never reused, so an id absent from the
current map can be given its older-version mapping with zero risk of clobbering a different member.
Always confirm 0 conflicts (an id already in the tsv mapping to a DIFFERENT name would be a red flag)
and that each resolved Mojang name still exists on the target jar (else it is a genuine removal, not
skew, and mapping it does not help).

USAGE:
  1. Download the two sources for the mod's MC version <V>:
       intermediary tiny:  https://maven.fabricmc.net/net/fabricmc/intermediary/<V>/intermediary-<V>-v2.jar
                           (unzip -> mappings/mappings.tiny)
       Mojang proguard:    version_manifest_v2.json -> <V>.json -> downloads.client_mappings.url
  2. Feed the dangling ids (method_NNN / field_NNN, one per line) on stdin:
       python3 harvest-intermediary-skew.py mappings.tiny client.txt < dangling_ids.txt
     Dangling ids come from a corpus link-check: `retromod score <dir> --json out.json`, then collect
     every method_/field_ token still present in the missing-members of the transformed jars.
  3. Cross-check each resolved name against the TARGET jar (javap) before adding: keep only the ones
     whose name still exists there; append `METHOD\t<id>\t<name>` / `FIELD\t<id>\t<name>` to
     src/main/resources/intermediary-to-mojang.tsv (co-located with the other skew fixes).

This is the tool behind the FastColor$ARGB32/ABGR32 -> ARGB color-helper skew fix (1.3.0-snapshot.2).
"""
import sys, re


def parse_tiny(path):
    """intermediary-id -> (kind 'm'/'f', obfClass, obfName, obfDesc)."""
    inter, cur = {}, None
    with open(path) as f:
        next(f)
        for line in f:
            p = line.rstrip("\n").split("\t")
            if p[0] == "c":
                cur = p[1]
            elif len(p) >= 5 and p[0] == "" and p[1] in ("m", "f"):
                inter[p[4]] = (p[1], cur, p[3], p[2])  # id -> (kind, obfClass, obfName, obfDesc)
    return inter


def parse_proguard(path):
    """(obfClass,obfName,obfDesc)->mojName for methods; plus mojClass->obfClass for desc remap."""
    moj2obf, lines = {}, []
    cur = None
    with open(path) as f:
        for line in f:
            if line.startswith("#"):
                continue
            if not line.startswith("    ") and "->" in line:
                l, r = line.split("->")
                cur = (l.strip().replace(".", "/"), r.strip().rstrip(":").replace(".", "/"))
                moj2obf[cur[0]] = cur[1]
            elif line.startswith("    ") and "->" in line and cur:
                body, obfn = line.strip().rsplit("->", 1)
                body = re.sub(r"^\d+:\d+:", "", body.strip())
                lines.append((cur[1], body, obfn.strip()))

    def obf_type(t):
        t = t.strip(); arr = ""
        while t.endswith("[]"):
            arr, t = arr + "[", t[:-2]
        prim = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S",
                "int": "I", "long": "J", "float": "F", "double": "D"}
        if t in prim:
            return arr + prim[t]
        i = t.replace(".", "/")
        return arr + "L" + moj2obf.get(i, i) + ";"

    methods, fields = {}, {}
    for obfc, body, obfn in lines:
        if "(" in body:
            m = re.match(r"(\S+)\s+([^(]+)\((.*)\)", body)
            if not m:
                continue
            ret, name, params = m.groups()
            pl = [p for p in params.split(",") if p.strip()]
            desc = "(" + "".join(obf_type(p) for p in pl) + ")" + obf_type(ret)
            methods[(obfc, obfn, desc)] = name
        else:
            m = re.match(r"\S+\s+(\S+)", body)
            if m:
                fields[(obfc, obfn)] = m.group(1)
    return methods, fields


def main():
    inter = parse_tiny(sys.argv[1])
    methods, fields = parse_proguard(sys.argv[2])
    ids = [l.strip() for l in sys.stdin if l.strip()]
    resolved = 0
    for iid in ids:
        if iid not in inter:
            print(f"{iid}\t<not in this version's intermediary>")
            continue
        kind, obfc, obfn, obfd = inter[iid]
        name = methods.get((obfc, obfn, obfd)) if kind == "m" else fields.get((obfc, obfn))
        tag = "METHOD" if kind == "m" else "FIELD"
        if name:
            print(f"{tag}\t{iid}\t{name}")
            resolved += 1
        else:
            print(f"{iid}\t<obf {obfc}.{obfn}{obfd if kind=='m' else ''} unmatched in proguard>")
    print(f"# resolved {resolved}/{len(ids)}", file=sys.stderr)


if __name__ == "__main__":
    main()
