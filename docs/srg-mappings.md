---
title: Adding SRG Mappings
nav_order: 13
---

# Adding SRG Mappings

This page is for contributors who want to grow Retromod's coverage of old Forge mods. If a Forge mod crashes with `NoSuchFieldError: f_NNNNN_` or `NoSuchMethodError: m_NNNNNN_`, it's almost certainly a missing entry in the SRG → Mojang dictionary. Adding one is a small, focused PR, and an excellent first contribution.

## What SRG names are

Old Forge mods (anything built with ForgeGradle's `reobfJar` task, basically every Forge mod for MC < 1.20.5) reference Minecraft members by Forge's intermediate "SRG" names rather than Mojang's official names. So instead of `Blocks.STONE`, the bytecode says `Blocks.f_50069_`. Forge's runtime classloader used to remap these to the real names at class-load time.

Forge 64.x dropped that remap layer for MC 26.1+, because Mojang shipped MC 26.1 with no obfuscation at all, so Forge no longer needed an intermediate layer. The side effect is that every reobf'd mod now crashes on first reference to an SRG name.

Retromod takes over: it loads a dictionary of SRG → Mojang member names and applies them via the same `ClassRemapper` pipeline that handles intermediary → Mojang for Fabric mods. The dictionary is intentionally a starter set (full coverage spans tens of thousands of entries) so growing it is exactly the kind of work where contributor help compounds.

## The data file

The dictionary lives at:

```
src/main/resources/retromod/srg-to-mojang.tsv
```

It's a plain tab-separated text file with three columns:

```
KIND<TAB>SRG_NAME<TAB>MOJANG_NAME
```

For example:

```
FIELD	f_50069_	STONE
METHOD	m_237113_	literal
```

`KIND` is `FIELD` or `METHOD`. Lines starting with `#` are comments. Blank lines are ignored. Owner class is intentionally not recorded, since SRG names are unique across the entire MC namespace, so a single mapping is sufficient regardless of which class the field or method lives on.

## How to find a missing entry

Two practical paths, in order of effort:

### 1. Read the crash log

This is by far the easiest path and the one most contributors will use. When a Forge mod crashes on Retromod, the SRG name appears verbatim in the stack trace:

```
java.lang.NoSuchFieldError: f_220832_
    at com.example.coolmod.CoolItems.<clinit>(CoolItems.java:42)
```

That tells you the SRG name (`f_220832_`) and roughly where in the codebase it's used. Now you need the Mojang name.

The fastest way to resolve it: open [Linkie](https://linkie.shedaniel.dev/) or [parchmentmc.org/parchment](https://parchmentmc.org/), select the MC version the mod was built for, and search for the SRG name. Both tools maintain searchable cross-references between SRG, intermediary, and Mojang names.

If Linkie/Parchment doesn't have it, the authoritative source is Forge's MCP CSV (see path 2).

### 2. Bulk-import from Forge's MCP archive

For grabbing many entries at once (e.g. you want to add all `Blocks.*` fields), download Forge's official MCP mapping CSVs:

- Browse [files.minecraftforge.net](https://files.minecraftforge.net/) → "MCP Config" → pick your MC version
- Download the `mcp_config-<version>.zip` from MinecraftForge maven (`de.oceanlabs.mcp:mcp_config:<version>:zip`)
- Inside, `config/joined.tsrg` is the SRG → Mojang mapping

Cross-reference with Mojang's official mappings (the proguard-style file linked from [Mojang's launcher metadata](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)) to get human-readable names.

A `MappingComposer`-style importer for this file is on the roadmap but not yet built. For now, hand-curating the highest-value entries from real crash logs gives the best signal-per-entry ratio.

## Adding the entry

Open `src/main/resources/retromod/srg-to-mojang.tsv`. Find the right section (the file is grouped by what kind of symbol it is: Block fields, Item fields, Component methods, etc.) and add your row. If your entry doesn't fit any existing section, add a new section heading.

Use a real tab character between columns. Many editors have a whitespace mode if you're not sure, or just copy an existing row and replace the names.

```
FIELD	f_220832_	MANGROVE_PLANKS
```

That's the entire change.

## Verify before you PR

Two quick checks save reviewer round-trips:

**Check for duplicate keys.** A given SRG name maps to exactly one Mojang name. Two rows with the same `KIND` + `SRG_NAME` silently overwrite each other (HashMap.put), so duplicates are data-loss bugs. Run:

```bash
awk -F'\t' '/^[A-Z]/ {print $1, $2}' \
  src/main/resources/retromod/srg-to-mojang.tsv | sort | uniq -d
```

The expected output is empty. If any line prints, find and resolve the duplicate.

**Build and check the load log.** Build, deploy, launch any loader (Forge is most natural since SRG is a Forge concern), and confirm the parse log line:

```
Loaded SRG → Mojang mappings: 32 methods, 85 fields (117 parsed, 0 skipped)
```

The "skipped" count should stay at zero. A nonzero skipped count means the loader rejected one of your rows, usually because of a malformed line (wrong column count, unknown KIND, empty name).

If you want to verify a *specific* mapping was applied, add the SRG-baked mod that triggered the original crash to `retromod-input/`, launch, and confirm it no longer crashes on that name. The crash will move on to the next missing SRG name (if any), which is itself a useful signal: that's the next entry to add.

## PR guidelines

- One mod's worth of mappings per PR is fine. "Add 12 SRG mappings needed for Jade 1.20.1" is a great PR title.
- Cite the source mod and MC version in the PR description so reviewers can verify the mappings against the same Forge version you tested with.
- **Don't mass-import without verifying.** A PR that dumps 5,000 untested SRG names from MCP CSV is harder to review than 50 entries you've exercised against a real mod.
- Comments explaining unusual entries are welcome. If you're adding a method that's renamed in a non-obvious way (e.g. the Mojang name doesn't look anything like the obfuscated original), a `# was 'getDisplayName' in 1.19, renamed in 1.20.1` comment helps the next person.

## Edge cases

- **Methods with the same name on different classes.** SRG numbers are globally unique, so this isn't a problem in practice. `m_237113_` is `Component.literal` and *only* `Component.literal`; no other class has that SRG number for an unrelated method.

- **Constructors.** SRG doesn't number constructors (they're always `<init>`). If you see a `NoSuchMethodError` on `<init>`, it's a constructor-signature change, not an SRG remap problem. That's a Version Shim's job (see the `add-version-shim` skill).

- **Generated synthetic methods.** Compiler-generated bridge methods (lambda accessors, switch-on-enum tables) sometimes carry SRG-looking names. They almost never need to be in this dictionary, and if you see `lambda$0$m_NNNNNN_` style names, those resolve fine on their own.

- **MC version drift.** SRG numbers are stable within a MC version but shift between MC versions when symbols are added or removed. The dictionary today is curated against the MC versions most reobf'd mods target (1.20.1 era). If you're working on a much older or newer mod and its SRG numbers don't match what's in the file, that's a sign you're working against a different MC version's SRG namespace, so note the source MC version in your PR.

## Why this matters

The SRG dictionary is one of the highest-leverage places to contribute, because every entry directly unblocks a real mod for real users. If you've ever wanted to run an old Forge mod on the latest MC and watched it crash, this is the file to fix it in.

A small starter set ships with Retromod today. Every contributed entry makes the next person's mod load that much further before hitting the next missing name.
