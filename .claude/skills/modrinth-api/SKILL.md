---
name: modrinth-api
description: Search, download, and work with mods from Modrinth. Use when downloading test mods, checking mod compatibility, or finding mods for a specific MC version and loader.
argument-hint: "search-query loader mc-version (e.g. sodium fabric 1.21.1, jei neoforge)"
---

# Modrinth API

Work with the Modrinth API to search for, download, and analyze mods.

## API Base URL
```
https://api.modrinth.com/v2
```

## Search for Mods

```bash
# Search by name with loader filter
curl -s 'https://api.modrinth.com/v2/search?query=<name>&facets=%5B%5B"project_type:mod"%5D,%5B"categories:<loader>"%5D%5D&limit=5'

# Parse results
curl -s '<url>' | python3 -c "
import sys, json
data = json.load(sys.stdin)
for h in data['hits']:
    print(h['slug'], h['title'], h['downloads'])
"
```

## Get Mod Versions

```bash
# Get versions for a specific loader
curl -s 'https://api.modrinth.com/v2/project/<slug>/version?loaders=%5B"<loader>"%5D&limit=10'

# Parse versions
curl -s '<url>' | python3 -c "
import sys, json
versions = json.load(sys.stdin)
for v in versions[:10]:
    print(v['version_number'], v['game_versions'], v['files'][0]['filename'])
"
```

## Download a Mod

```bash
# Get download URL from version ID
curl -s 'https://api.modrinth.com/v2/version/<version-id>' | python3 -c "
import sys, json
v = json.load(sys.stdin)
for f in v['files']:
    print(f['filename'], f['url'])
"

# Download
curl -sL -o output.jar "<download-url>"
```

## Common Facets

```
# Filter by loader
"categories:fabric"
"categories:neoforge"
"categories:forge"

# Filter by MC version
"versions:1.21.1"
"versions:26.1"

# Filter by project type
"project_type:mod"
"project_type:modpack"
"project_type:resourcepack"
```

## Good Test Mods by Loader

### Fabric
- sodium, lithium, iris, modmenu, cloth-config, fabric-api
- appleskin, mousetweaks, dynamic-fps, nochatreports

### NeoForge
- jei, jade, waystones, xaeros-minimap
- create, curios, architectury

### Both
- voicechat, notenoughcrashes, e4mc

## URL Encoding

Facets use JSON array format, URL-encoded:
- `[["a"],["b"]]` → `%5B%5B"a"%5D,%5B"b"%5D%5D`
- Loaders: `%5B"fabric"%5D` → `["fabric"]`

## Rate Limits
- No authentication needed for public endpoints
- Be respectful - don't spam the API
- Cache responses when possible
