# RetroMod

> Run older Minecraft mods on newer versions through bytecode transformation and API shimming.

[![Java 25+](https://img.shields.io/badge/Java-25+-blue.svg)](https://adoptium.net/)
[![Minecraft 26.1](https://img.shields.io/badge/Minecraft-26.1-green.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Beta](https://img.shields.io/badge/Status-Beta-orange.svg)]()

**Made by the developers of [RevivalSMP.net](https://revivalsmp.net)**

> **This project is in beta (v1.0.0-beta.1).** The core pipeline is stable and tested; the transformer still has gaps for deep-integration mods (rendering replacement, heavy mixin mods) that we'll close in follow-up releases. Works well on the common case. Keep backups of your mod JARs — RetroMod writes transformed copies alongside the originals, but anything that touches mod files warrants a backup. Please report issues on [GitHub Issues](https://github.com/Bownlux/RetroMod/issues).

RetroMod is a drop-in Minecraft mod that transforms older mod bytecode at load time — rewriting renamed methods, redirecting removed APIs, and patching Mixin targets — so old mods just work. Supports **Fabric**, **NeoForge**, and **Forge** with version shims covering Minecraft 1.12.2 all the way through 26.1.

> **26.1 Update:** Mojang removed all code obfuscation in Minecraft 26.1. RetroMod automatically maps old intermediary names (`class_XXXX`, `method_XXXX`, `field_XXXX`) to Mojang's official human-readable names, so mods built for 1.21.x and earlier work seamlessly on 26.1+.

---

## Quick Start

### Fabric

1. Download `retromod-1.0.0-beta.1.jar` and put it in `mods/`
2. Launch Minecraft once, then close it — this creates the `retromod-input/` folder and a config that lets old mods load
3. Put your old mods in the `retromod-input/` folder (in your `.minecraft` directory)
4. Launch again — RetroMod transforms them and shows a restart popup
5. Restart one more time — done, your old mods work!

> **Why not just drop old mods in `mods/`?** Fabric checks mod versions before RetroMod can run. If you put an old mod directly in `mods/`, Fabric rejects it and crashes (exit code 255). The `retromod-input/` folder lets RetroMod transform the mod first, then move it to `mods/` with the correct version info.
>
> **Alternative:** Use the CLI to prep everything in one step: `java -jar retromod-cli.jar prepare ~/.minecraft --aot`

### Forge / NeoForge

1. Put RetroMod in `mods/`
2. Drop your old mods in `mods/` too (or in `retromod-input/` if you prefer)
3. Launch the game — RetroMod transforms incompatible mods and updates their `mods.toml`
4. Restart when prompted — done!

> Originals are backed up to `mods/retromod-backups/`.

### Uninstalling

1. Remove `retromod-*.jar` from `mods/`
2. If you want original (untransformed) mods back, restore them from `mods/retromod-backups/` or `retromod-input/processed/`

> Transformed mods have updated bytecode and version info, so they may still work without RetroMod. But if anything breaks, restore the originals.

---

## Key Features

### Transformation pipeline
- **534+ bytecode redirects** — class, method, field, constructor, and field-to-accessor
- **145+ version shims** covering every MC release from 1.12.2 (Java 8 era) through 26.1
- **72+ polyfills** reimplement removed APIs using modern equivalents
- **Iterative transform loop** — catches chained redirects (A → B → C) that single-pass visitors miss; runs up to 5 passes until bytecode stabilizes
- **Intermediary → Mojang name mapping** — 26.1 removed all code obfuscation; RetroMod automatically composes 8,800+ intermediary class names with 600+ 26.1 package moves in a single hop
- **Reflection string remapping** — rewrites MC-typed strings at `Class.forName()`, `getDeclaredMethod()`, `MethodHandles.Lookup.findVirtual()`, and other reflection sinks
- **Bridge method synthesis** — generates compatibility methods for mods whose overrides would otherwise be orphaned
- **Smart mixin compatibility** — relocates broken targets, strips broken injections, downgrades `CAPTURE_FAILHARD` → `CAPTURE_FAILSOFT` so one bad mixin can't crash the game
- **Font & rendering bridge** — old `Font.draw` / `drawShadow` / `RenderSystem` calls bridged to 26.1's new APIs
- **Runtime version spoofer** — defeats stale in-code version-range checks (REI rejecting Cloth Config, etc.) by returning a compatible version to the asking mod

### Performance & concurrency
- **Multi-core parallel transforms** — brief all-cores burst during pre-launch (Fabric/Forge/NeoForge) and CLI batch flows; tunable via `-Dretromod.parallelism=N`
- **Hybrid AOT + JIT** — pre-transforms mods at first launch, JIT fallback for edge cases; AOT cache survives between launches
- **Pattern matcher with class-shape fingerprinting** — five patterns (BlockEntity-like, DeferredRegister holder, Forge event listener, Mixin target resolver, ApiUsage fingerprint) identify what mod classes do for future shim targeting

### Diagnostics & reporting
- **Reference verifier** — scans transformed bytecode against the MC symbol index and reports unresolved references per mod
- **Cross-mod gap report** (`retromod gaps`) — aggregates unresolved references across a whole mods folder, ranked by frequency, so you can see which missing shims would unblock the most mods
- **Compatibility score** — CLI (`retromod score <mod.jar>`) reports a 0–100 compatibility percentage before you try loading a mod
- **`retromod devhelp`** — for mod developers, shows every API change you'd need to make to update your mod

### Security
- **Opt-in AutoFix** — crash-log parsing is gated behind `-Dretromod.autoFix=true` so malicious mods can't poison the redirect table via log-line injection
- **Zip-bomb protection** — extracts archives with bounded byte counters, not the attacker-controlled `entry.getSize()` field
- **Zip-slip guards** on both input reads and output writes
- **Symlink guard** on config file writes
- **Per-entry read caps** — no unbounded `readAllBytes()` on mod JAR contents

### Mod-loader + API coverage
- **Multi-loader** — Fabric, NeoForge, Forge (including experimental Forge → NeoForge migration for simple mods)
- **34+ modding APIs handled** — Fabric API, Mod Menu, Cloth Config, REI, JEI, Curios, Trinkets, Cardinal Components, GeckoLib, Architectury, Create, Patchouli, YACL, Jade/WAILA, Sodium, Iris, EMI, owo-lib, LibGui, Forge Config API Port, and more
- **Legacy API shims** — Baubles → Curios, NEI → JEI, Thermal/RF → Forge Energy, old WAILA → Jade, LibBlockAttributes → Transfer API
- **API version relaxation** — 60+ mod IDs whose version constraints get automatically loosened so dep checks don't block loading
- **API embedding** — removed APIs bundled as shim classes directly into mod JARs when needed

### In-game UI
- **Title-screen button** — RetroMod gear icon in the top-right of the main menu, opens the mod-management screen
- **Main screen** — lists your transformed mods, shows status, opens input/backup folders
- **Settings screen** — toggle feature flags (verify, reflection remap, pattern matching, spoofing)
- **Cross-loader button injection** — Fabric `ScreenEvents.AFTER_INIT`, NeoForge `ScreenEvent.Init.Post`, Forge event bus

### Platform & architecture
- **Java 25** compile + runtime target (ASM 9.8+ for class file v69 support)
- **Multi-architecture** — x86_64, ARM64 / Apple Silicon
- **Rendering future-proofing** — detection hooks for Vulkan, Metal, and DirectX transitions

## Supported Versions

Shims are **chainable** — a 1.12.2 Forge mod can run on 26.1 by applying each shim in sequence. **All intermediate versions** (1.16.2, 1.14.1, 1.15.1, etc.) are supported via fuzzy version matching — mods targeting any version within a range are automatically handled.

| Loader | Shims | Range | Per-chain maturity |
|--------|-------|-------|--------------------|
| **Fabric** | 32 | 1.14 → ... → 26.1 → 26.1 | 1.16.5+: Stable, 1.14–1.15: Experimental |
| **NeoForge** | 18 | 1.20.1 → ... → 26.1 → 26.1 | Stable |
| **Forge** | 28 | 1.12.2 → ... → 1.20 → 1.21 (NeoForge*) → ... → 26.1 → 26.1 | 1.16.5+: Stable, 1.12–1.15: Experimental |

> **Fuzzy version matching:** If a mod targets an intermediate version like 1.16.2 or 1.14.3, RetroMod automatically resolves it to the nearest milestone shim. This means every MC version from 1.12.2 to 26.1 is supported, even versions without their own dedicated shim.

> **Experimental chains (1.12.2–1.15.2):** These cover massive Minecraft changes like "The Flattening" (1.12→1.13) where every block ID, entity name, and NBT class was renamed. The shim chain works for many mods but is harder to make 100% reliable across that gap — use at your own risk and report what doesn't work.
>
> **Stable chains (1.16.5+):** Production-tested across our test suite. The vast majority of mods translate cleanly. Backups are still recommended out of habit.

## API Compatibility

RetroMod supports **34+ popular modding APIs** — both actively maintained and legacy/unmaintained. See [API_COMPATIBILITY.md](API_COMPATIBILITY.md) for the full list.

Highlights: Fabric API, Mod Menu, Cloth Config, REI, Trinkets, JEI, Curios, GeckoLib, Architectury, Create, and legacy APIs like Baubles, NEI, Thermal/RF, and old WAILA. When a mod uses an unmaintained API, RetroMod embeds a compatibility shim that bridges old API calls to their modern equivalent.

### API Version Relaxation

When a mod declares it requires a specific API version — for example, `"cloth-config2": ">=6.0.0 <7.0.0"` — the mod loader (Fabric/Forge/NeoForge) will block the mod from loading if the installed API version doesn't match, even though RetroMod can handle the API differences. RetroMod fixes this by automatically relaxing those version constraints during transformation.

This covers **60+ known API mod IDs** including all variants of Cloth Config, Mod Menu, REI, Trinkets, Cardinal Components, GeckoLib, JEI, Curios, and many more. For unknown API dependencies with strict upper-bound version ranges, RetroMod also relaxes those as a safety net.

> **Example:** Your friend's mod requires Cloth Config 6.x, but you have Cloth Config 11.x installed. Without RetroMod, Fabric immediately crashes with a dependency error. With RetroMod, the version constraint is relaxed to `"*"` and the bytecode is transformed to work with the newer API — the mod loads and runs normally.

## Resource Pack & Data Pack Conversion (Alpha)

RetroMod can also transform **resource packs** and **data packs** for version compatibility. It automatically:
- Updates `pack.mcmeta` pack format numbers
- Renames old texture paths (e.g., `textures/blocks/` → `textures/block/` from The Flattening)
- Adjusts data pack namespaces for version changes

> **This feature is in alpha.** It works for simple resource/data packs, but complex packs with custom shaders, model overrides, or heavy use of predicates may not convert correctly. Always keep backups of your original packs.

---

## Building

Requires **Java 25+** and **Maven 3.8+**.

### Easy Build (Recommended)

```bash
git clone https://github.com/Bownlux/RetroMod.git
cd RetroMod
./build.sh          # macOS / Linux
build.bat           # Windows
```

This builds everything and puts the output in `dist/`:

| File | What it's for |
|------|---------------|
| `retromod-*-cli.jar` | CLI tool (standalone, all dependencies included) |
| `retromod-*-fabric.jar` | Fabric mod — drop in `mods/` |
| `retromod-*-neoforge.jar` | NeoForge mod — drop in `mods/` |

> There's also `build-all.sh` / `build-all.bat` which builds JARs for **every** MC version × every loader.

### Maven Build

```bash
mvn clean package    # Build everything + create dist/ with all JARs
```

This is what `build.sh` / `build.bat` use internally. Output goes to both `target/` (raw JARs) and `dist/` (ready-to-use JARs).

### Why Maven, not Gradle?

Almost every Minecraft mod on the planet uses Gradle (via Fabric Loom / NeoGradle / ForgeGradle), so this is the first question people usually ask. Short answer: **I prefer Maven.** I find it builds faster on my machine, I hit fewer mysterious "you need to invalidate the Gradle cache and re-import" issues with it, and the configuration is declarative XML I can read top-to-bottom in one sitting instead of a Groovy/Kotlin DSL where half the behavior comes from plugin conventions I have to go look up.

RetroMod is also a bit unusual for a mod — it **doesn't compile against Minecraft at all.** It operates on bytecode reflectively and via ASM. So all the reasons you'd normally reach for Loom (deobfuscation mappings, dev-time MC classpath, intermediary remapping at build time) don't apply here. Maven gives me plain-Java dependencies (ASM, Gson, SLF4J, Fabric Loader as `provided`) and a couple of `maven-jar-plugin` executions for the different build classifiers. That's the whole story — no plugin pipeline to debug when something goes wrong.

None of this is a judgment call on Gradle. Gradle's great at what it's designed for, and Loom specifically is the right tool for the normal "I'm writing a mod against MC" workflow. I'm just not in that workflow, and Maven is a better fit for the shape of this project. If you want to fork the build to Gradle, I dont want to have this use graddle though, But — the dependencies and classifier-JAR wiring are all in `pom.xml` in one place, so it's mainly easy.

---

## CLI Tool

RetroMod includes a standalone CLI for transforming mods outside of Minecraft. The build outputs a `retromod` wrapper script so you don't need to type `java -jar ...` every time.

### Setup

After building, the `dist/` folder contains `retromod` (macOS/Linux) and `retromod.bat` (Windows). Add it to your PATH:

```bash
# macOS / Linux
export PATH="$PATH:/path/to/retromod/dist"

# Windows (PowerShell)
$env:PATH += ";C:\path\to\retromod\dist"
```

Or just run it from the dist directory directly.

### Commands

```bash
# Analyze a mod's compatibility
retromod analyze mymod.jar

# AOT compile a mod (recommended)
retromod aot mymod.jar

# Batch process all mods in a folder
retromod batch mods/ --aot

# Full prep: overrides + transform everything
retromod prepare ~/.minecraft --aot

# Transform a legacy mod (1.8-1.20.x) to 1.21.x
retromod legacy oldmod-1.12.2.jar

# Show API differences between versions
retromod diff fabric 1.21.8 1.21.9

# Developer migration helper — shows what API changes to make
retromod devhelp mymod-1.21.4.jar 26.1
```

> You can also use `java -jar retromod-*-cli.jar <command>` directly if you prefer.

### Java Agent Mode

Transform classes at runtime instead of pre-transforming JARs:

```bash
java -javaagent:retromod-agent.jar -jar minecraft.jar
```

---

## CLI vs Mod — Which Should You Use?

RetroMod comes in two forms: a **standalone CLI tool** and an **in-game mod**. Both do the same bytecode transformations, but they work differently.

| | CLI Tool | In-Game Mod |
|---|---------|-------------|
| **How it works** | You run it manually before launching Minecraft. Transforms mods ahead-of-time (AOT). | Drops into your `mods/` folder. Transforms mods automatically on first launch. |
| **When transforms happen** | Before Minecraft starts | During Minecraft's loading phase |
| **Runtime overhead** | None — mods are already transformed | Minimal — AOT on first launch, JIT fallback for edge cases |
| **Ease of use** | Requires command-line knowledge | Just drop it in `mods/` |
| **Server-friendly** | Great for prepping mods before deploying to a server | Works on servers too, but first launch takes longer |
| **Batch processing** | Yes — `batch` and `prepare` commands | No — one instance at a time |
| **Debugging** | `analyze`, `diff`, `devhelp` commands | Config-based (`log_level`, `dump_bytecode`) |

### When to use the CLI

- You want **zero runtime overhead** — all transforms happen before the game starts
- You're setting up a **server** and want everything pre-transformed
- You want to **analyze or debug** a mod before loading it
- You're a **mod developer** using `devhelp` to see what API changes to make

### When to use the Mod

- You want a **simple drop-in experience** — no command line needed
- You want RetroMod to **automatically detect and transform** old mods
- You don't mind a slightly longer first launch while transforms are compiled
- You want the **JIT fallback** for edge cases that AOT might miss

> **You can use both.** Pre-transform heavy mods with the CLI, then use the in-game mod as a safety net for anything the CLI missed.

---

## Performance Impact

RetroMod's bytecode transformations have a cost. Here's what to expect:

### During Transformation (First Launch / CLI)

| What | Impact | Duration |
|------|--------|----------|
| **AOT compilation** | High CPU, moderate RAM (+200–500 MB) | One-time — 5–30 seconds per mod depending on size |
| **Shim embedding** | Moderate CPU | One-time — adds shim classes to mod JARs |
| **Mixin target rewriting** | Moderate CPU | One-time — scans and rewrites annotation targets |

The first launch with the in-game mod will be noticeably slower (30 seconds to a few minutes depending on how many mods you have). After that, **everything is cached** and future launches are normal speed.

> **Tip:** Use the CLI's `batch` or `prepare` command to pre-transform all mods. This way, Minecraft launches at normal speed from the first boot.

### During Gameplay

| Mode | Impact | Notes |
|------|--------|-------|
| **AOT (pre-transformed)** | No impact | Mods are already rewritten — same as running native mods |
| **JIT fallback** | Minor CPU spikes | Only triggers when a class is loaded that AOT missed. Rare — typically <1% of classes. Each JIT transform takes 1–10 ms. |
| **Reflection remapping** | Negligible | Intercepts `Class.forName()` / `Method.invoke()` calls — adds microseconds per call |

For most users, **gameplay performance is identical to running native mods.** The JIT fallback is rare and brief. If you notice stutters, pre-transform with the CLI to eliminate all JIT hits.

### Memory Usage

- **RetroMod itself:** ~10–20 MB of additional memory for the shim registry and transformation engine
- **Embedded shims:** Each transformed mod grows by 50–200 KB (the shim classes added to the JAR)
- **JIT cache:** If JIT triggers, transformed classes are cached in memory — typically <5 MB total

> **Bottom line:** After the first launch, RetroMod has virtually no performance impact. If you want zero overhead, use the CLI to pre-transform everything.

### Parallel transformation & diagnostics

RetroMod uses **all available CPU cores** during the batch transform phase. On a 500-class mod JAR this is the difference between ~2.5 seconds of single-threaded work and ~350 ms of parallel work — a brief all-cores burst then immediate idle. This is exactly what you want for a one-off batch task: get the work done fast, free the machine.

**Diagnostic passes that run by default** (post-transform):

| Pass | What it does | Off-switch |
|------|--------------|------------|
| Reference verifier | Scans every transformed class for references to MC APIs that no longer exist; produces a gap report | `-Dretromod.verifyTransforms=false` |
| Pattern matcher | Detects common class shapes (event listeners, registry holders, mixins, block entities) and builds an API-usage fingerprint per class | `-Dretromod.matchPatterns=false` |
| Bridge synthesis | Adds bridge methods to mod classes whose overrides would otherwise be orphaned by MC method renames | `-Dretromod.synthesizeBridges=false` |

> ⚠️ **Low-end machine guidance.** If your machine has **less than 4 GB of available RAM** AND you're transforming a **very large mod collection** (100+ mods or mods with very large class counts), the deep `ApiUsageFingerprintPattern` and the parallel executor can briefly use several hundred MB of RAM while it works. If you see the game or shell stuttering during pre-launch:
> - Turn off the pattern matcher: add `-Dretromod.matchPatterns=false` to your JVM flags.
> - Or cap the parallel work: `-Dretromod.parallelism=2` limits RetroMod to 2 worker threads (default is all cores).
> - Or turn off verification too: `-Dretromod.verifyTransforms=false`.
>
> These flags leave the core transformation pipeline (iterative loop, reflection remap) running at full speed — they only disable the extra diagnostic passes. Mods still get transformed correctly; you just don't get the per-mod gap report.

### JIT path (Java Agent mode)

Some diagnostics are batch-only by design. When RetroMod runs as a Java Agent (transforming classes one at a time as the JVM loads them), the iterative loop and reflection remapping run, but the reference verifier, pattern matcher, and parallelization do not apply — the agent sees one class at a time with no "whole mod" context. Users who want the full diagnostic report should run the CLI (`retromod gaps`) or use pre-launch batch transformation.

---

## Polyfill System (New)

RetroMod's shims handle API **renames and relocations** — when a method or class was moved or renamed between versions. But sometimes APIs are **completely removed** with no direct replacement. When that happens, mods crash with `ClassNotFoundException`, `NoSuchMethodError`, or mixin hierarchy failures.

The **polyfill system** solves this by re-implementing removed APIs using their modern equivalents. These polyfills provide the original API surface but delegate to the new code under the hood, so mods that depend on removed APIs still **work correctly** — not just load. Polyfills are loaded automatically via ServiceLoader and can be toggled per-category in config.

### What's Covered

RetroMod ships with **72+ polyfill reimplementations** across 10 providers covering every major modding ecosystem:

| Category | Examples | Fixes |
|----------|----------|-------|
| **Fabric Loader** | `TinyVisitor`, `TinyV2Visitor`, `TinyMappingFactory`, `TinyMetadata` | Not Enough Crashes crash (`ClassNotFoundException`) |
| **Fabric API** | Removed Fabric API modules and classes | Mods using deprecated Fabric API surfaces |
| **Minecraft Vanilla** | `Material`, `LiteralText`, removed screen classes (`class_5500`) | No Chat Reports mixin failure, mods using removed vanilla APIs |
| **Mixin Targets** | Removed MC classes used as mixin targets | Mixin hierarchy crashes when target classes are deleted |
| **Forge** | `SidedProxy`, `RegistryObject`, `MinecraftForge`, `ICapabilityProvider`, `LazyOptional` | Legacy Forge mods referencing the old capability/registry system |
| **NeoForge** | `ItemStackHandler`, `ComponentItemHandler`, `RenderHighlightEvent`, `javax.annotation.*` | NeoForge mods using removed transfer API and annotation classes |
| **Baubles** | `IBauble`, `BaubleType`, `BaublesApi`, capability classes | 1.7.10–1.12.2 mods using Baubles (replaced by Curios) |
| **NEI** | `API`, `IRecipeHandler`, `TemplateRecipeHandler`, `ItemList` | 1.4.7–1.12.2 mods using Not Enough Items (replaced by JEI) |
| **CoFH / Redstone Flux** | `IEnergyConnection`, `IEnergyHandler`, `IEnergyReceiver`, `IEnergyProvider` | Mods using the old Thermal/RF energy API (replaced by Forge Energy) |
| **WAILA** | `IWailaPlugin`, `IWailaRegistrar`, `IWailaDataProvider` | Mods using What Am I Looking At (replaced by Jade) |

### Superclass Redirects

When Minecraft changes a **class to an interface** (e.g., `Explosion` became an interface in newer versions), mods that `extend Explosion` break because you can't extend an interface. RetroMod's polyfill system includes a **superclass redirect** mechanism that rewrites the class hierarchy at load time — changing the superclass to a bridge class and adding the new interface, so the mod loads correctly.

### Configuration

Polyfills are **enabled by default**. You can toggle the entire system or individual categories in `config/retromod/config.json`:

```json
{
  "polyfills_enabled": true,
  "polyfill_categories": {
    "fabric_api": true,
    "rendering": true,
    "entity": true,
    "mixin_targets": true,
    "minecraft_vanilla": true,
    "forge": true,
    "neoforge": true,
    "thirdparty": true
  }
}
```

> **Note:** Polyfills reimplement removed APIs by delegating to their modern equivalents. Old API calls are intercepted and bridged to the new code, so mods that depend on them still function correctly. In rare cases where an API was removed with no replacement at all, a minimal fallback is provided.

---

## Configuration

Auto-generated on first launch at `config/retromod/config.json`:

```json
{
  "use_aot": true,
  "use_hybrid": true,
  "instruction_level_granularity": true,
  "transform_mixins": true,
  "transform_refmaps": true,
  "remap_reflection": true,
  "polyfills_enabled": true,
  "log_level": "INFO",
  "log_transformations": false,
  "target_mc_version": "auto",
  "debug": false,
  "dump_bytecode": false,
  "force_translate_complex": false
}
```

| Option | Default | Description |
|--------|---------|-------------|
| `use_aot` | `true` | Pre-transform mods ahead-of-time on first launch |
| `use_hybrid` | `true` | Use hybrid AOT/JIT mode (JIT fills in what AOT misses) |
| `transform_mixins` | `true` | Rewrite Mixin annotation targets for version compatibility |
| `remap_reflection` | `true` | Intercept reflection calls (Class.forName, Method.invoke) and remap class names |
| `polyfills_enabled` | `true` | Enable the polyfill system — reimplements removed APIs using modern equivalents so old mods work correctly |
| `force_translate_complex` | `false` | Force-translate mods that RetroMod deems "unlikely to work" (high complexity score). Enable this if a mod was skipped and you want to try it anyway. |
| `log_level` | `"INFO"` | Logging verbosity: `ERROR`, `WARN`, `INFO`, `DEBUG` |
| `dump_bytecode` | `false` | Dump transformed bytecode to disk for debugging |

---

## How It Works

```
MOD JAR (old version)
  ↓
CLASS ANALYSIS — detect loader type, MC version, scan bytecode
  ↓
SHIM CHAIN — find path: e.g. 1.21 → 1.21.1 → ... → 26.1
  ↓
BYTECODE TRANSFORMATION — AOT, partial AOT, or JIT
  ↓
POLYFILL INJECTION — reimplement removed APIs (Fabric, Forge, NeoForge, vanilla, third-party)
  ↓
SUPERCLASS REDIRECTS — rewrite class hierarchy for class→interface migrations
  ↓
OUTPUT — rewritten bytecode, embedded API shims, polyfill reimplementations, updated Mixins
```

### Core Components

| Component | Description |
|-----------|-------------|
| `RetroModTransformer` | ASM-based bytecode transformer with SafeClassWriter |
| `HybridTransformationEngine` | AOT/JIT engine with fallback |
| `ShimRegistry` | BFS-based shim chain finder |
| `MixinCompatibilityTransformer` | Transforms Mixin annotation targets |
| `ModVersionDetector` | Reads mod metadata from loader-specific files |
| `PolyfillRegistry` | ServiceLoader-based registry for removed API polyfills |
| `RenderingBackendShim` | Future-proof rendering compat (OpenGL/Vulkan/Metal) |

---

## For Mod Developers

### Detecting RetroMod

```java
boolean retroModPresent = FabricLoader.getInstance().isModLoaded("retromod");
```

### Migration Helper

Use `devhelp` to see exactly what API changes you need when updating your mod:

```bash
java -jar retromod-cli.jar devhelp your-mod.jar 26.1
```

This scans your mod, finds the version gap, and prints a list of every class rename, method rename, and field change you need to make.

### Adding a Version Shim

```java
public class Fabric_X_to_Y implements VersionShim {
    @Override public String getSourceVersion() { return "X"; }
    @Override public String getTargetVersion() { return "Y"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerMethodRedirect(
            "old/Owner", "oldMethod", "(args)return",
            "new/Owner", "newMethod", "(args)return"
        );
    }
}
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `NoSuchMethodError` at runtime | Run `retromod analyze yourmod.jar` to check compatibility |
| Mod loads but features broken | Some APIs were removed without replacement — check shim tables |
| Stale/broken transforms | Run `retromod clean` to clear AOT cache |
| Crash with "error code 1" and no text | Check `config/retromod/crash-log.txt` — RetroMod now writes crash details there. Also check your launcher's logs (`logs/latest.log`). |
| Fabric crashes with "mod requires cloth-config 6.x" (or similar API version) | RetroMod handles this — put the mod in `retromod-input/` so it gets transformed. If dropped directly in `mods/`, Fabric checks versions before RetroMod can fix them. |
| Mod was skipped as "unlikely to work" | Set `"force_translate_complex": true` in config.json to override |
| Debug logging | Set `"log_level": "DEBUG"` and `"dump_bytecode": true` in config.json |

## Known Limitations

> **Beta notice:** RetroMod is in beta (v1.0.0-beta.1). The core pipeline is stable, but the transformer has known gaps for deep-integration mods (rendering replacement, heavy mixin mods, some cross-version library deps) that we'll close in follow-up releases. The majority of mods translate cleanly, but unusual or extremely complex mods may still surface issues — please report them. Backups are recommended whenever you use any tool that modifies mod JARs.
>
> **Experimental notice (1.12.2–1.15.2):** The shim chain across these very old versions is the hardest part of the project to make 100% reliable. The API changes were enormous (The Flattening alone renamed hundreds of classes). Many mods do work, but expect more rough edges here than elsewhere. Simple mods have the best chance of translating cleanly.

1. Cannot transform already-loaded classes without Java Agent mode
2. Complex Mixins may need manual shim updates for non-standard patterns
3. **Experimental:** Legacy mods (1.12.2–1.15.2) may be unstable — especially mods crossing "The Flattening" (1.12→1.13) which renamed every block, entity, and NBT class. The shim chain works for many mods here but is harder to make 100% reliable across that gap.
4. **Forge→NeoForge migration is experimental.** RetroMod can remap basic package names (`net.minecraftforge.*` → `net.neoforged.*`), but NeoForge has diverged significantly from Forge — the capability system, networking, and many internal APIs were completely rewritten, not just renamed. Simple Forge mods may work on NeoForge, but complex mods that deeply use Forge's systems will likely not work.
5. Cross-loader mods (Forge mod on Fabric) are not supported
6. Rendering backend shims activate only when MC actually switches backends
7. Very old mods using Java 8 reflection patterns may need additional shim work
8. **RetroMod cannot fix Java version mismatches.** If a mod was compiled for a newer Java version than you have installed (e.g., a mod requiring Java 25 on a Java 21 system), the JVM will refuse to load it with `UnsupportedClassVersionError` before RetroMod can do anything. RetroMod transforms Minecraft API changes, not Java version differences. You need the correct Java version installed.
9. **Mod configs** generally work, but if a mod's config system changed between versions (e.g., old Forge `.cfg` → new `.toml`), you may need to delete the old config file and let the mod regenerate it
10. **Complex mods may be skipped.** RetroMod analyzes each mod's complexity (reflection usage, ASM manipulation, coremods, NMS access, etc.) and warns you if a mod is "unlikely to work." If a mod was skipped, set `"force_translate_complex": true` in `config/retromod/config.json` to force it. The CLI's `aot` command also supports `--force` for this.
11. **Resource/data pack conversion is alpha.** Simple packs work, but packs with custom shaders, model predicates, or heavy overlays may not convert correctly.

## Contributing

**Adding a shim:** Fork → add shim in `src/main/java/com/retromod/shim/` → register in `META-INF/services/com.retromod.core.VersionShim` → add tests → PR.

**Adding a polyfill:** Fork → create a `PolyfillProvider` in `src/main/java/com/retromod/polyfill/` → add reimplementation classes at the original package path that delegate to modern APIs → register in `META-INF/services/com.retromod.polyfill.PolyfillProvider` → PR.

## Badge for Translated Mods

If you used RetroMod to translate your mod to a newer version, you can optionally add this badge to your mod page:

**Markdown:**
```
[![Translated with RetroMod](https://img.shields.io/badge/Translated_with-RetroMod-blue?style=for-the-badge)]
```

**HTML:**
```html
<a href="https://modrinth.com/mod/retromod"><img alt="Translated with RetroMod" height="56" src="https://img.shields.io/badge/Translated_with-RetroMod-blue?style=for-the-badge"></a>
```

## License

[MIT License](LICENSE) — Copyright (c) 2026 RevivalSMP

## Credits

**Bownlux** (author) · **[RevivalSMP.net](https://revivalsmp.net)** (development) · **[ASM](https://asm.ow2.io/)** · **[FabricMC](https://fabricmc.net/)** · **[NeoForged](https://neoforged.net/)** · **[MinecraftForge](https://minecraftforge.net/)**

*RetroMod is not affiliated with Mojang, Microsoft, FabricMC, NeoForged, or MinecraftForge.*
