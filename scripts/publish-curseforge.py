#!/usr/bin/env python3
"""
Publish every dist/ JAR to CurseForge in one shot.

Retromod ships one JAR per loader × MC version (dist/<Loader>/<MC>/retromod-*.jar),
and on CurseForge each must be uploaded as its own file tagged with that exact MC
version + loader. Doing it by hand on CF's site is ~40 minutes; this does it in one
run.

It loops dist/{Fabric,Forge,NeoForge}/<MC>/retromod-*.jar, resolves CurseForge's
*numeric* game-version IDs (the annoying part of the CF upload API), and POSTs each
JAR with the right MC-version + loader tags.

Environment:
  CF_API_TOKEN   CurseForge author upload-API token   (required)
  CF_PROJECT_ID  numeric CurseForge project id          (required)

Usage:
  publish-curseforge.py --version 1.1.0 --release-type release [--changelog-file CHANGELOG.md] [--dist dist] [--dry-run]

ALWAYS run with --dry-run first: it hits only the read-only game-versions endpoint,
validates the token/project, and prints exactly what it WOULD upload — no files sent.

CurseForge version tags lag real MC releases, so a JAR whose MC version CF doesn't
list yet (e.g. a brand-new 26.2) is SKIPPED with a warning rather than failing the run.
"""
import argparse
import glob
import json
import os
import re
import sys
import time

try:
    import requests
except ImportError:
    sys.exit("ERROR: this script needs `requests` (pip install requests).")

API = "https://minecraft.curseforge.com"
LOADER_DIRS = [("Fabric", "fabric"), ("Forge", "forge"), ("NeoForge", "neoforge")]


def load_game_versions(token):
    """Map CF's game-version + modloader NAMES to their numeric IDs."""
    r = requests.get(API + "/api/game/versions", headers={"X-Api-Token": token}, timeout=30)
    if r.status_code != 200:
        sys.exit(f"ERROR: could not fetch CF game versions (HTTP {r.status_code}). "
                 f"Check CF_API_TOKEN. Body: {r.text[:200]}")
    mc, loaders = {}, {}
    for v in r.json():
        name = (v.get("name") or "").strip()
        vid = v.get("id")
        if name.lower() in ("fabric", "forge", "neoforge", "quilt"):
            loaders[name.lower()] = vid
        elif re.fullmatch(r"\d+\.\d+(\.\d+)?", name):  # "1.20.1", "26.2"
            mc[name] = vid
    return mc, loaders


def extract_changelog(path):
    """Pull the first `## [...]` section body out of a Keep-a-Changelog file."""
    if not path or not os.path.exists(path):
        return "See https://github.com/Bownlux/Retromod/blob/main/CHANGELOG.md"
    text = open(path, encoding="utf-8").read()
    m = re.search(r"^## .*?\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    return (m.group(1).strip() if m else text.strip()) or "Release."


def upload(project_id, token, jar, game_version_ids, display_name, changelog, release_type):
    meta = {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": display_name,
        "gameVersions": game_version_ids,
        "releaseType": release_type,
    }
    with open(jar, "rb") as fh:
        r = requests.post(
            f"{API}/api/projects/{project_id}/upload-file",
            headers={"X-Api-Token": token},
            data={"metadata": json.dumps(meta)},
            files={"file": (os.path.basename(jar), fh, "application/java-archive")},
            timeout=180,
        )
    base = os.path.basename(jar)
    if r.status_code == 200:
        try:
            print(f"  OK   {base} -> file {r.json().get('id')}")
            return True
        except ValueError:
            # 200 but not the expected {"id": ...} JSON. Almost always means the
            # request never reached the upload handler — e.g. a wrong/non-numeric
            # CF_PROJECT_ID routed to an HTML error or redirect page. Surface the
            # body instead of crashing on r.json().
            print(f"  FAIL {base}  HTTP 200 but unexpected (non-JSON) body — "
                  f"is CF_PROJECT_ID the numeric id? Body: {r.text[:200]!r}")
            return False
    print(f"  FAIL {base}  HTTP {r.status_code}: {r.text[:300]}")
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True, help="version string, e.g. 1.1.0")
    ap.add_argument("--release-type", default="release", choices=["release", "beta", "alpha"])
    ap.add_argument("--changelog-file", default="CHANGELOG.md")
    ap.add_argument("--dist", default="dist")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    token = os.environ.get("CF_API_TOKEN")
    project = os.environ.get("CF_PROJECT_ID")
    if not token or not project:
        sys.exit("ERROR: set CF_API_TOKEN and CF_PROJECT_ID in the environment.")
    project = project.strip()
    if not project.isdigit():
        sys.exit(
            f"ERROR: CF_PROJECT_ID must be the NUMERIC project id (e.g. 1234567), not the "
            f"slug/name '{project}'. CurseForge's upload API only accepts the number.\n"
            f"       Find it on your project page: CurseForge -> your project -> the "
            f"'About Project' box in the right sidebar shows 'Project ID: <number>'.\n"
            f"       Then set the repo variable CF_PROJECT_ID to that number "
            f"(Settings -> Secrets and variables -> Actions -> Variables)."
        )

    mc_map, loader_map = load_game_versions(token)
    changelog = extract_changelog(args.changelog_file)
    print(f"CF knows {len(mc_map)} MC versions, loaders: {sorted(loader_map)}")
    print(f"Mode: {'DRY RUN (no uploads)' if args.dry_run else 'LIVE upload'} | "
          f"version={args.version} type={args.release_type}\n")

    uploaded = skipped = failed = 0
    for loader_dir, loader_name in LOADER_DIRS:
        for jar in sorted(glob.glob(f"{args.dist}/{loader_dir}/*/retromod-*.jar")):
            mcver = os.path.basename(os.path.dirname(jar))
            base = os.path.basename(jar)
            if loader_name not in loader_map:
                print(f"  SKIP {base}: CF has no '{loader_name}' loader tag"); skipped += 1; continue
            if mcver not in mc_map:
                print(f"  SKIP {base}: CF has no '{mcver}' game version yet"); skipped += 1; continue
            gv = [mc_map[mcver], loader_map[loader_name]]
            name = f"Retromod {args.version} ({loader_dir} {mcver})"
            if args.dry_run:
                print(f"  DRY  {base}  gameVersions={gv}  \"{name}\""); uploaded += 1
            else:
                ok = upload(project, token, jar, gv, name, changelog, args.release_type)
                uploaded += ok; failed += (not ok)
                time.sleep(2)  # be gentle with CF's rate limit

    print(f"\nSummary: {uploaded} {'planned' if args.dry_run else 'uploaded'}, "
          f"{skipped} skipped, {failed} failed.")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
