#!/usr/bin/env python3
"""Cross-join a mixin scan against a harvest-mc-diff outdir to PREDICT breaks.

Usage:
    python3 scripts/mixin-crossjoin.py --scan scan.json --diff <diff-outdir> [--resig <file>] [--tsv]

Consumes:
  - scan.json          from `retromod mixin-scan --json` (Component A schema)
  - <diff-outdir>      from `scripts/harvest-mc-diff.py`, containing:
        moves.txt      old/internal/Name -> new/internal/Name  [0.87] PUBLIC
        renames.txt    same line format (verify before use)
        removed.txt    one internal class name per line
        added.txt      one internal class name per line (unused here)
  - --resig <file>     optional newline list of target METHOD names known to have
                       signature drift (e.g. grep of MixinHandlerResignature, or a
                       hand file). One name per line, # comments and blanks skipped.

For each scan record and each of its targetClasses:
  - target class in removed.txt          -> PREDICTED-BREAK, reason "target class
                                            REMOVED", mechanism "blocklist or polyfill"
  - target class in moves/renames        -> PREDICTED-BREAK, reason "class MOVED/RENAMED
                                            old -> new", mechanism "class-redirect"
  - target method in --resig set         -> flag "METHOD SIGNATURE DRIFT", mechanism
                                            "resignature"

The predicted-break worklist is ranked by corpus frequency (distinct jars). Columns:
  freq, targetClass, targetMethod, injector, reason, suggestedMechanism, exampleJars

Stdlib only (json, sys, os, argparse, collections).
"""
import argparse
import json
import os
import sys
from collections import OrderedDict

MAX_EXAMPLE_JARS = 3


def load_diff(outdir):
    """Return (moved: old->new dict, removed: set). moves + renames merge into moved."""
    moved = {}
    removed = set()

    def parse_pairs(fname):
        path = os.path.join(outdir, fname)
        if not os.path.exists(path):
            return
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "->" not in line:
                    continue
                left, right = line.split("->", 1)
                old = left.strip()
                # right side: "new/internal/Name  [0.87] PUBLIC" - new is the first token
                new = right.strip().split()[0] if right.strip() else ""
                if old and new:
                    moved[old] = new

    parse_pairs("moves.txt")
    parse_pairs("renames.txt")

    rem_path = os.path.join(outdir, "removed.txt")
    if os.path.exists(rem_path):
        with open(rem_path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    removed.add(line)

    return moved, removed


def load_resig(path):
    """Return a set of method names known to have signature drift."""
    names = set()
    if not path:
        return names
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                names.add(line)
    return names


def selector_method_name(sel):
    """Reduce a member selector to its bare method name.

    Handles "Lnet/minecraft/Foo;bar(I)V", "net/minecraft/Foo.bar(I)V", "bar()V", "bar".
    """
    if not sel:
        return ""
    s = sel
    if "(" in s:
        s = s[:s.index("(")]
    for sep in (";", ".", "/"):
        if sep in s:
            s = s.rsplit(sep, 1)[-1]
    return s.strip()


def load_records(scan_path):
    with open(scan_path, encoding="utf-8") as f:
        data = json.load(f)
    return data.get("records", [])


def build_worklist(records, moved, removed, resig):
    """Cluster predicted breaks. Key: (targetClass, targetMethod, injector, reason,
    mechanism). Value tracks the set of distinct jars (the frequency)."""
    groups = OrderedDict()

    def add(target_class, target_method, injector, reason, mechanism, jar):
        key = (target_class, target_method, injector, reason, mechanism)
        entry = groups.get(key)
        if entry is None:
            entry = {
                "targetClass": target_class,
                "targetMethod": target_method,
                "injector": injector,
                "reason": reason,
                "mechanism": mechanism,
                "jars": OrderedDict(),  # ordered set of jar basenames
            }
            groups[key] = entry
        entry["jars"][jar] = None

    for rec in records:
        jar = os.path.basename(rec.get("jar") or "")
        injector = rec.get("injector") or ""
        selectors = rec.get("targetSelectors") or []
        method_names = [selector_method_name(s) for s in selectors]
        first_method = method_names[0] if method_names else ""

        for target_class in (rec.get("targetClasses") or []):
            if target_class in removed:
                add(target_class, first_method, injector,
                    "target class REMOVED", "blocklist or polyfill", jar)
            elif target_class in moved:
                add(target_class, first_method, injector,
                    "class MOVED/RENAMED %s -> %s" % (target_class, moved[target_class]),
                    "class-redirect", jar)

            # Signature drift is independent of the class move/removal check.
            drift = next((m for m in method_names if m and m in resig), "")
            if drift:
                add(target_class, drift, injector,
                    "METHOD SIGNATURE DRIFT", "resignature", jar)

    rows = list(groups.values())
    for r in rows:
        r["freq"] = len(r["jars"])
        r["exampleJars"] = list(r["jars"].keys())
    # Rank by distinct-jar frequency, then targetClass, then targetMethod.
    rows.sort(key=lambda r: (-r["freq"], r["targetClass"], r["targetMethod"]))
    return rows


def emit_tsv(rows, out):
    cols = ["freq", "targetClass", "targetMethod", "injector",
            "reason", "suggestedMechanism", "exampleJars"]
    out.write("\t".join(cols) + "\n")
    for r in rows:
        out.write("\t".join([
            str(r["freq"]),
            r["targetClass"],
            r["targetMethod"],
            r["injector"],
            r["reason"],
            r["mechanism"],
            ",".join(r["exampleJars"][:MAX_EXAMPLE_JARS]),
        ]) + "\n")


def emit_table(rows, out):
    if not rows:
        out.write("No predicted breaks. Every scanned mixin target survives the diff.\n")
        return
    headers = ["FREQ", "TARGET CLASS", "TARGET METHOD", "INJECTOR",
               "REASON", "MECHANISM", "EXAMPLE JARS"]

    def cells(r):
        return [
            str(r["freq"]),
            r["targetClass"],
            r["targetMethod"] or "-",
            r["injector"] or "-",
            r["reason"],
            r["mechanism"],
            ", ".join(r["exampleJars"][:MAX_EXAMPLE_JARS]),
        ]

    widths = [len(h) for h in headers]
    for r in rows:
        for i, c in enumerate(cells(r)):
            widths[i] = max(widths[i], len(c))

    def fmt(vals):
        return "  ".join(v.ljust(widths[i]) for i, v in enumerate(vals))

    out.write(fmt(headers) + "\n")
    out.write("  ".join("-" * w for w in widths) + "\n")
    for r in rows:
        out.write(fmt(cells(r)) + "\n")
    out.write("\n%d predicted break(s) across %d distinct jar(s).\n"
              % (len(rows), len({j for r in rows for j in r["exampleJars"]})))


def main():
    ap = argparse.ArgumentParser(
        description="Predict mixin breaks by cross-joining a scan against an MC diff.")
    ap.add_argument("--scan", required=True, help="scan.json from mixin-scan")
    ap.add_argument("--diff", required=True, help="harvest-mc-diff output directory")
    ap.add_argument("--resig", help="newline list of drifting target method names")
    ap.add_argument("--tsv", action="store_true", help="machine-readable TSV output")
    args = ap.parse_args()

    if not os.path.isdir(args.diff):
        ap.error("--diff must be a harvest-mc-diff output directory: %s" % args.diff)

    moved, removed = load_diff(args.diff)
    resig = load_resig(args.resig)
    records = load_records(args.scan)
    rows = build_worklist(records, moved, removed, resig)

    if args.tsv:
        emit_tsv(rows, sys.stdout)
    else:
        emit_table(rows, sys.stdout)


if __name__ == "__main__":
    main()
