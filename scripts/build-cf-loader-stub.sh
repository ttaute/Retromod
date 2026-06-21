#!/usr/bin/env bash
#
# Build the standalone CurseForge "Retromod Loader" stub jar (#78).
#
# The stub is the ONLY CurseForge-hosted jar a CF modpack puts in mods/. It carries
# just the two tiny loader hooks that pull everything else in from mods/Retromod/
# (where the pack ships the real Retromod + the transformed *-retromod.jar outputs as
# CF "overrides"):
#   - NeoForge: com.retromod.locator.RetromodModLocator  (IModFileCandidateLocator,
#               registered via META-INF/services) - loads mods/Retromod/ IN PLACE.
#   - Fabric:   com.retromod.locator.RetromodLoaderPreLaunch (preLaunch entrypoint) -
#               MOVES mods/Retromod/ jars into mods/, then a one-time restart.
# No transform engine, no bundled deps - SLF4J is provided by the loader at runtime.
#
# The REAL Retromod stays on Modrinth and is NOT uploaded to CurseForge; this stub is
# the only thing that goes to CF. (See ROADMAP §C #78, "Distribution: Option B".)
#
# Usage:  scripts/build-cf-loader-stub.sh [version]
#   version defaults to build-all.sh's VERSION. Requires target/classes (compile first).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-$(grep -m1 '^VERSION=' build-all.sh | cut -d'"' -f2)}"
[ -n "$VERSION" ] || { echo "ERROR: could not determine version (pass it as arg 1)"; exit 1; }

LOC=target/classes/com/retromod/locator
if [ ! -f "$LOC/RetromodModLocator.class" ] || [ ! -f "$LOC/RetromodForgeModLocator.class" ] || [ ! -f "$LOC/RetromodLoaderPreLaunch.class" ]; then
  echo "ERROR: $LOC/*.class missing - compile first:"
  echo "       mvn -q -DskipTests -Dexec.skip=true compile"
  exit 1
fi

OUT="dist/CurseForge/retromod-loader-${VERSION}.jar"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/com/retromod/locator" "$STAGE/META-INF/services"

# the loader hooks (+ any inner/synthetic .class siblings, just in case):
#   RetromodModLocator       - NeoForge IModFileCandidateLocator
#   RetromodForgeModLocator  - Forge IModLocator
#   RetromodLoaderPreLaunch  - Fabric drain pre-launch
cp "$LOC"/RetromodModLocator*.class "$STAGE/com/retromod/locator/"
cp "$LOC"/RetromodForgeModLocator*.class "$STAGE/com/retromod/locator/"
cp "$LOC"/RetromodLoaderPreLaunch*.class "$STAGE/com/retromod/locator/"

# Loader service registrations (verbatim from the main resources): NeoForge + Forge.
cp src/main/resources/META-INF/services/net.neoforged.neoforgespi.locating.IModFileCandidateLocator \
   "$STAGE/META-INF/services/"
cp src/main/resources/META-INF/services/net.minecraftforge.forgespi.locating.IModLocator \
   "$STAGE/META-INF/services/"

cat > "$STAGE/fabric.mod.json" <<EOF
{
  "schemaVersion": 1,
  "id": "retromod_loader",
  "version": "${VERSION}",
  "name": "Retromod Loader",
  "description": "Companion stub for CurseForge modpacks: loads Retromod (hosted on Modrinth) and its translated mods from mods/Retromod/.",
  "authors": ["Bownlux"],
  "contact": { "homepage": "https://modrinth.com/mod/retromod", "sources": "https://github.com/Bownlux/Retromod", "issues": "https://github.com/Bownlux/Retromod/issues" },
  "license": "MIT",
  "environment": "*",
  "entrypoints": { "preLaunch": ["com.retromod.locator.RetromodLoaderPreLaunch"] },
  "depends": { "fabricloader": ">=0.16.0" }
}
EOF

# lowcodefml: the jar has no @Mod class - the locator runs as an early SERVICE, found
# by FML's mods/ service scan regardless of the mod entry. Version-agnostic (no
# minecraft/java dependency): the stub only moves/locates files.
cat > "$STAGE/META-INF/neoforge.mods.toml" <<EOF
modLoader="lowcodefml"
loaderVersion="[1,)"
license="MIT"
issueTrackerURL="https://github.com/Bownlux/Retromod/issues"
[[mods]]
modId="retromod_loader"
version="${VERSION}"
displayName="Retromod Loader"
description='''Companion stub for CurseForge modpacks: loads Retromod (hosted on Modrinth) and its translated mods from mods/Retromod/.'''
EOF

# Forge reads META-INF/mods.toml (NeoForge reads neoforge.mods.toml). The lowcodefml
# metadata is shared between the two, so the same content serves both - without it,
# Forge would flag the jar as an invalid mod file even though the locator service runs.
cp "$STAGE/META-INF/neoforge.mods.toml" "$STAGE/META-INF/mods.toml"

mkdir -p dist/CurseForge
rm -f "$OUT"
jar cf "$OUT" -C "$STAGE" .
echo "wrote $OUT ($(du -h "$OUT" | cut -f1))"
echo "--- contents ---"
unzip -l "$OUT"
