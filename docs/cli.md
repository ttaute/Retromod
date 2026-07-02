---
title: CLI tool
nav_order: 5
---

# CLI tool

Retromod ships with a command-line tool (`RetromodCli`) for transforming mods outside Minecraft. It's handy for scripting, CI pipelines, bulk modpack prep, and poking at what Retromod would do to a mod without actually booting the game.

## Running the CLI

One quirk: **the published JAR doesn't bundle its dependencies**, so `java -jar retromod.jar ...` won't work. You run the CLI through Maven instead, from a checkout of the repo.

```bash
git clone https://github.com/Bownlux/Retromod.git
cd Retromod
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="<command> <args>" \
  -q
```

The `-q` flag keeps Maven quiet so you only see Retromod's output. Every example below assumes you're in the repo root.

## Commands

### `transform`: transform a single mod

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="transform path/to/mod.jar" -q
```

Transforms `mod.jar` to target the current `TARGET_MC_VERSION` (26.1 by default; override with `--target <version>`). The transformed copy is written next to the original as `mod-transformed.jar` (or wherever `--output <jar>` points); the original is left untouched.

Output: `path/to/mod-transformed.jar` (transformed); `path/to/mod.jar` (original, untouched).

### `transform --verify`: transform and verify

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="transform path/to/mod.jar --verify" -q
```

Same as `transform`, but after transformation runs the [verifier]({{ '/verify-transforms' | relative_url }}) and writes a report to `config/retromod/verify-reports/`. Non-zero exit if verification finds unresolved references.

### `batch`: transform every mod in a folder

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="batch /path/to/mods" -q
```

Iterates every `.jar` in the given folder, transforming each one. For targets at 26.1+ this also runs the metadata-only patch on mods that don't need bytecode rewrites, so version constraints get relaxed regardless.

Pass `--verify` to verify each mod after transforming:

```bash
-Dexec.args="batch /path/to/mods --verify"
```

### `aot`: AOT-compile a folder of mods

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="aot /path/to/mods" -q
```

Runs the full AOT compiler over every mod in the folder, writing cache entries into `config/retromod/aot-cache/`. Next time Minecraft launches with Retromod, those mods load from the cache without being transformed again.

Useful for bulk-preparing a modpack on a faster machine before shipping it out.

### `embed`: bake removed APIs into a mod JAR

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="embed path/to/mod.jar" -q
```

Scans `mod.jar` for calls into loader APIs that have since been removed and, if any are found, extracts the archived implementations of those APIs, relocates them under a `retromod_embedded/` package, and rewrites the mod's calls to use the embedded copies. The result is written next to the original as `mod-retromod.jar`, so the mod carries the removed APIs with it instead of needing them from the loader. If the mod uses no removed APIs, nothing is embedded and no output jar is produced.

### `diff`: show API differences between two versions

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="diff fabric 1.21.1 26.1" -q
```

Shows the API differences between two MC versions for a loader: `diff <loader> <version1> [version2]` (the target defaults to the CLI's `TARGET_MC_VERSION` if omitted) walks the shim chain between the two versions and prints each shim's method/class redirect counts and embedded helper classes, without inspecting or writing any mod. For a per-mod preview, run `transform`: it writes to a separate `-transformed.jar`, so the original is untouched.

### `shims`: list registered shims

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="shims" -q
```

Prints every registered version shim, grouped by loader (Fabric / NeoForge / Forge / API), with `fromVersion → toVersion` and the shim name, plus a total shim count at the end. Useful for confirming a shim is actually loaded.

Abbreviated output:

```
┌─ FABRIC ────────────────────────────────────────
│  1.20.1 → 1.20.2  (Fabric 1.20.1 to 1.20.2)
│  1.20.2 → 1.20.3  (Fabric 1.20.2 to 1.20.3)
│  ...
└──────────────────────────────────────────────────────

Total: 143 shims registered
```

## Flags

### `--verify`

Works with `transform` and `batch`. Runs the verifier after each transformation and writes a report. Non-zero exit code if any verification misses are found.

### `-q` (Maven quiet mode)

Not a Retromod flag, but worth mentioning. Pass `-q` to Maven to suppress its progress output. Without it you'll see Maven's standard blah about downloading dependencies and running the exec plugin.

## Scripting tips

- Every command returns a non-zero exit on error, so you can chain with `&&` or check `$?` in shell scripts.
- `batch` logs one line per mod to stdout, so it's easy to `grep` for specific mod IDs or failures.
- Verify reports are plain text; parse them with whatever you like.

## See also

- [Verify Transforms]({{ '/verify-transforms' | relative_url }})
- [Config reference]({{ '/config' | relative_url }})
