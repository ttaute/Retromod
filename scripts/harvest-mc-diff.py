#!/usr/bin/env python3
"""Harvest class moves/renames between two unobfuscated MC jars (26.1+).

Usage:
    python3 scripts/harvest-mc-diff.py <old-client.jar> <new-client.jar> <outdir>

Parses every class file (constant pool + member tables, no deps needed),
then matches classes that disappeared from <old> against classes that
appeared in <new>:

  1. same SIMPLE name, member fingerprints overlap  -> package MOVE (high conf)
  2. different name, near-identical fingerprint     -> RENAME (needs eyeball)
  3. no match                                       -> REMOVED (polyfill/bridge)

Member fingerprints normalize descriptors by reducing net/minecraft and
com/mojang class refs to their simple names, so a move's ripple through
other classes' signatures doesn't break matching.

Outputs (in <outdir>):
  moves.txt      old/internal/Name -> new/internal/Name   (incl. inner classes)
  renames.txt    candidate renames with similarity scores - VERIFY before use
  removed.txt    public classes gone with no successor
  added.txt      public classes that are genuinely new
"""
import sys, struct, zipfile, re
from collections import defaultdict

PKG = ("net/minecraft/", "com/mojang/")


def parse_class(data):
    """Return (this_name, super_name, access, methods, fields) - members as (name, desc)."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return None
    n_cp = struct.unpack(">H", data[8:10])[0]
    cp, i, idx = {}, 10, 1
    while idx < n_cp:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack(">H", data[i + 1:i + 3])[0]
            cp[idx] = data[i + 3:i + 3 + ln].decode("utf-8", "replace")
            i += 3 + ln
        elif tag == 7 or tag == 8 or tag == 16 or tag == 19 or tag == 20:
            cp[idx] = struct.unpack(">H", data[i + 1:i + 3])[0]
            i += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            i += 5
        elif tag in (3, 4):
            i += 5
        elif tag in (5, 6):
            i += 9
            idx += 1  # longs/doubles take two slots
        elif tag == 15:
            i += 4
        else:
            return None  # unknown tag - bail
        idx += 1
    access, this_i, super_i, n_ifc = struct.unpack(">HHHH", data[i:i + 8])
    i += 8 + 2 * n_ifc

    def utf(ci):
        v = cp.get(ci)
        return cp.get(v, "") if isinstance(v, int) else ""

    this_name = utf(this_i)
    super_name = utf(super_i) if super_i else ""

    def members():
        nonlocal i
        out = []
        (count,) = struct.unpack(">H", data[i:i + 2]); i += 2
        for _ in range(count):
            _acc, ni, di, n_attr = struct.unpack(">HHHH", data[i:i + 8]); i += 8
            for _ in range(n_attr):
                _ai, alen = struct.unpack(">HI", data[i:i + 6]); i += 6 + alen
            nm, dsc = cp.get(ni, ""), cp.get(di, "")
            if isinstance(nm, str) and isinstance(dsc, str):
                out.append((nm, dsc))
        return out

    fields = members()
    methods = members()
    return this_name, super_name, access, methods, fields


def simplify(desc):
    """Reduce MC class refs in a descriptor to simple names: moves don't break matching."""
    return re.sub(r"L(?:net/minecraft|com/mojang)/[\w/$]*?([\w$]+);", r"L~\1;", desc)


def load(jar_path):
    classes = {}
    with zipfile.ZipFile(jar_path) as z:
        for name in z.namelist():
            if not name.endswith(".class") or not name.startswith(PKG):
                continue
            p = parse_class(z.read(name))
            if p is None:
                continue
            this_name, super_name, access, methods, fields = p
            fp = frozenset(
                [("M", n, simplify(d)) for n, d in methods if n not in ("<clinit>",)]
                + [("F", n, simplify(d)) for n, d in fields]
            )
            classes[this_name] = {
                "fp": fp,
                "super": super_name.rsplit("/", 1)[-1],
                "public": bool(access & 0x0001),
            }
    return classes


def main():
    old_jar, new_jar, outdir = sys.argv[1], sys.argv[2], sys.argv[3]
    import os
    os.makedirs(outdir, exist_ok=True)

    old = load(old_jar)
    new = load(new_jar)
    removed = {k: v for k, v in old.items() if k not in new}
    added = {k: v for k, v in new.items() if k not in old}
    print(f"old={len(old)} new={len(new)} removed={len(removed)} added={len(added)}")

    by_simple = defaultdict(list)
    for k in added:
        by_simple[k.rsplit("/", 1)[-1].rsplit("$", 1)[-1]].append(k)

    moves, renames, gone = [], [], []
    matched_added = set()

    def sim(a, b):
        u = len(a | b)
        return (len(a & b) / u) if u else 0.0

    # Pass 1: same simple name -> package move
    for k, v in sorted(removed.items()):
        simple = k.rsplit("/", 1)[-1].rsplit("$", 1)[-1]
        cands = [c for c in by_simple.get(simple, []) if c not in matched_added]
        best, best_s = None, 0.0
        for c in cands:
            s = sim(v["fp"], new[c]["fp"])
            if s > best_s:
                best, best_s = c, s
        if best and (best_s >= 0.5 or (best_s >= 0.25 and len(v["fp"]) <= 4)):
            moves.append((k, best, best_s, v["public"]))
            matched_added.add(best)

    moved_old = {m[0] for m in moves}

    # Pass 2: renamed classes - fingerprint-only match, strict threshold
    for k, v in sorted(removed.items()):
        if k in moved_old or len(v["fp"]) < 5:
            continue
        best, best_s, second = None, 0.0, 0.0
        for c, cv in added.items():
            if c in matched_added:
                continue
            s = sim(v["fp"], cv["fp"])
            if s > best_s:
                best, best_s, second = c, s, best_s
            elif s > second:
                second = s
        if best and best_s >= 0.7 and best_s - second >= 0.2:
            renames.append((k, best, best_s, v["public"]))
            matched_added.add(best)
        else:
            gone.append((k, v["public"]))

    for k, v in sorted(removed.items()):
        if k not in moved_old and not any(r[0] == k for r in renames) and not any(g[0] == k for g in gone):
            gone.append((k, v["public"]))

    with open(f"{outdir}/moves.txt", "w") as f:
        for o, n, s, pub in sorted(moves):
            f.write(f"{o} -> {n}  [{s:.2f}]{' PUBLIC' if pub else ''}\n")
    with open(f"{outdir}/renames.txt", "w") as f:
        for o, n, s, pub in sorted(renames, key=lambda r: -r[2]):
            f.write(f"{o} -> {n}  [{s:.2f}]{' PUBLIC' if pub else ''}\n")
    with open(f"{outdir}/removed.txt", "w") as f:
        for k, pub in sorted(gone):
            if pub:
                f.write(f"{k}\n")
    with open(f"{outdir}/added.txt", "w") as f:
        for k in sorted(added):
            if k not in matched_added and added[k]["public"]:
                f.write(f"{k}\n")

    print(f"moves={len(moves)} rename-candidates={len(renames)} "
          f"removed-public={sum(1 for _, p in gone if p)}")


if __name__ == "__main__":
    main()
