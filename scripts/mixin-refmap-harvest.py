#!/usr/bin/env python3
"""Harvest mixin refmaps from mod jars into a per-mixin-class member inventory.

This is the no-JDK, no-bytecode cross-check path for the mixin discovery tooling
(the Java `mixin-scan` command reads the actual class annotations; this reads only
the JSON refmaps the mixin compiler already baked into each jar). Pure JSON, so it
runs anywhere a JDK is not available and gives an independent second opinion on
which MC members a mod's mixins reference.

Usage:
    python3 scripts/mixin-refmap-harvest.py <dir-or-jar-or-refmap.json> [<more>...] [--json <out>]

  - Directories are recursed for *.jar.
  - Jars are opened and every entry whose name ends with `refmap.json`
    (e.g. `wynnventory-refmap.json`) is parsed.
  - A refmap.json file may also be passed directly (handy for loose refmaps).

Refmap schema (confirmed against real mod refmaps):
  {
    "mappings": {                       # per-mixin-class human->intermediary refs
      "pkg/MixinFoo": {
        "someMethod": "Lnet/minecraft/class_1043;method_4524()V",
        "someField":  "Lnet/minecraft/class_640;field_XXXXX:Ljava/util/Map;"
      }
    },
    "data": {                           # optional, per-env copies of the same shape
      "named:intermediary": { "pkg/MixinFoo": { ... } }
    }
  }

Output: a per-mixin-class inventory of referenced members (methods + fields), as
JSON (to stdout, or to a file with `--json <out>`, or `--json -` for stdout), plus
a human summary (jars scanned, refmaps found, total mixin classes, total referenced
members) printed to stderr.
"""
import argparse
import json
import os
import sys
import zipfile


def parse_ref(ref):
    """Parse an intermediary ref `Lowner;name(args)ret` or `Lowner;name:desc`.

    Returns (kind, owner, name, desc). kind is "method", "field", or "unknown".
    Falls back gracefully on anything that is not shaped like a member ref.
    """
    if not isinstance(ref, str) or not ref:
        return ("unknown", "", "", "")
    body = ref
    owner = ""
    if body.startswith("L") and ";" in body:
        semi = body.index(";")
        owner = body[1:semi]
        body = body[semi + 1:]
    # body is now name + descriptor
    if "(" in body:
        name = body[:body.index("(")]
        desc = body[body.index("("):]
        return ("method", owner, name, desc)
    if ":" in body:
        name, desc = body.split(":", 1)
        return ("field", owner, name, desc)
    # bare name, no descriptor: treat as a field-ish reference
    return ("field", owner, body, "")


def collect_class_maps(refmap):
    """Yield (mixinClass, {humanRef: intermediaryRef}) from mappings + every data env.

    The same mixin class often appears in both `mappings` and each `data` env; the
    caller dedupes members by (humanRef, ref).
    """
    if not isinstance(refmap, dict):
        return
    top = refmap.get("mappings")
    if isinstance(top, dict):
        for mixin_class, members in top.items():
            if isinstance(members, dict):
                yield mixin_class, members
    data = refmap.get("data")
    if isinstance(data, dict):
        for env, env_map in data.items():
            if not isinstance(env_map, dict):
                continue
            for mixin_class, members in env_map.items():
                if isinstance(members, dict):
                    yield mixin_class, members


def build_inventory(mixin_maps):
    """Merge (mixinClass, memberMap) pairs into a deduped per-class member list."""
    by_class = {}
    for mixin_class, members in mixin_maps:
        bucket = by_class.setdefault(mixin_class, {})
        for human_ref, ref in members.items():
            key = (human_ref, ref if isinstance(ref, str) else json.dumps(ref))
            if key in bucket:
                continue
            kind, owner, name, desc = parse_ref(ref)
            bucket[key] = {
                "humanRef": human_ref,
                "ref": ref,
                "kind": kind,
                "owner": owner,
                "name": name,
                "desc": desc,
            }
    out = []
    for mixin_class in sorted(by_class):
        members = sorted(
            by_class[mixin_class].values(),
            key=lambda m: (m["humanRef"], m["ref"] if isinstance(m["ref"], str) else ""),
        )
        out.append({"mixinClass": mixin_class, "members": members})
    return out


def is_refmap_name(name):
    return name.rsplit("/", 1)[-1].endswith("refmap.json")


def harvest_jar(jar_path):
    """Return a list of {refmap, mixinClasses:[...]} for every refmap in the jar."""
    results = []
    try:
        with zipfile.ZipFile(jar_path) as z:
            for entry in z.namelist():
                if entry.endswith("/") or not is_refmap_name(entry):
                    continue
                try:
                    refmap = json.loads(z.read(entry).decode("utf-8", "replace"))
                except (ValueError, KeyError):
                    continue
                inventory = build_inventory(collect_class_maps(refmap))
                results.append({"refmap": entry, "mixinClasses": inventory})
    except (zipfile.BadZipFile, OSError):
        return None
    return results


def harvest_loose_refmap(path):
    try:
        with open(path, encoding="utf-8") as f:
            refmap = json.load(f)
    except (ValueError, OSError):
        return None
    basename = os.path.basename(path)
    inventory = build_inventory(collect_class_maps(refmap))
    return [{"refmap": basename, "mixinClasses": inventory}]


def iter_jars(root):
    """Yield jar paths under a directory (recursive), sorted for determinism."""
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(".jar"):
                yield os.path.join(dirpath, name)


def main():
    ap = argparse.ArgumentParser(
        description="Harvest mixin refmaps from mod jars into a member inventory."
    )
    ap.add_argument("paths", nargs="+", help="jars, dirs (recursed for *.jar), or refmap.json files")
    ap.add_argument("--json", dest="out", help="write JSON here ('-' for stdout); default stdout")
    args = ap.parse_args()

    # Expand inputs into (source-jar-label, harvest-result) records.
    refmaps = []          # list of {jar, refmap, mixinClasses}
    jars_scanned = 0
    jars_skipped = 0

    def add_source(label, harvested):
        nonlocal jars_skipped
        if harvested is None:
            jars_skipped += 1
            return False
        for r in harvested:
            refmaps.append({"jar": label, "refmap": r["refmap"], "mixinClasses": r["mixinClasses"]})
        return True

    for path in args.paths:
        if os.path.isdir(path):
            for jar in iter_jars(path):
                jars_scanned += 1
                add_source(os.path.basename(jar), harvest_jar(jar))
        elif path.endswith(".jar"):
            jars_scanned += 1
            add_source(os.path.basename(path), harvest_jar(path))
        elif is_refmap_name(path):
            add_source(os.path.basename(path), harvest_loose_refmap(path))
        else:
            print(f"skip (not a jar, dir, or refmap.json): {path}", file=sys.stderr)

    total_classes = sum(len(r["mixinClasses"]) for r in refmaps)
    total_members = sum(
        len(mc["members"]) for r in refmaps for mc in r["mixinClasses"]
    )

    doc = {
        "jarsScanned": jars_scanned,
        "jarsSkipped": jars_skipped,
        "refmapsFound": len(refmaps),
        "totalMixinClasses": total_classes,
        "totalReferencedMembers": total_members,
        "refmaps": refmaps,
    }

    payload = json.dumps(doc, indent=2)
    if args.out and args.out != "-":
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(payload + "\n")
    else:
        print(payload)

    # Human summary always to stderr, so JSON on stdout stays pipeable.
    print(
        f"jars scanned: {jars_scanned} (skipped {jars_skipped}), "
        f"refmaps found: {len(refmaps)}, "
        f"mixin classes: {total_classes}, "
        f"referenced members: {total_members}",
        file=sys.stderr,
    )
    for r in refmaps:
        print(
            f"  {r['jar']} :: {r['refmap']}  "
            f"({len(r['mixinClasses'])} classes, "
            f"{sum(len(mc['members']) for mc in r['mixinClasses'])} members)",
            file=sys.stderr,
        )


if __name__ == "__main__":
    main()
