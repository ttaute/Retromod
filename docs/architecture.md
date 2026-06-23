---
title: Architecture
nav_order: 11
---

# Architecture

A high-level tour of how Retromod turns an old mod JAR into one that loads on new Minecraft. This is aimed at potential contributors. If you're just trying to get a mod to work, [Troubleshooting]({{ '/troubleshooting' | relative_url }}) is probably a better starting point.

## The big picture

```
mod.jar (old)
   │
   ▼
┌──────────────────────┐
│ ModVersionDetector   │  read loader metadata → source MC version
└──────────────────────┘
   │
   ▼
┌──────────────────────┐
│ ShimRegistry         │  BFS: source → target, find shim chain
└──────────────────────┘
   │
   ▼
┌──────────────────────┐
│ RetromodTransformer  │  ASM visitor chain applies each shim's redirects
└──────────────────────┘
   │
   ▼
┌──────────────────────────┐
│ IntermediaryToMojangMapper │  remap class_XXXX → net.minecraft.RealName
└──────────────────────────┘
   │
   ▼
┌──────────────────────┐
│ PolyfillProvider(s)  │  inject replacements for removed APIs
└──────────────────────┘
   │
   ▼
┌──────────────────────┐
│ {Fabric,Forge}Mod    │  patch fabric.mod.json / mods.toml
│ Transformer          │
└──────────────────────┘
   │
   ▼
mod.jar (transformed, ready to load)
```

Each box is a composable stage. The [CLI]({{ '/cli' | relative_url }}) exposes the whole pipeline; the in-game runtime runs the same pipeline on JARs it finds in `retromod-input/` and `mods/`.

## Code layout

```
src/main/java/com/retromod/
├── core/       main transformer, version detectors, mod transformers
├── cli/        RetromodCli - command-line entry point
├── aot/        AOT compiler - caches transformed mods
├── shim/       version shims organized by loader (fabric/ neoforge/ forge/ api/)
├── mapping/    IntermediaryToMojangMapper, MappingComposer
├── mixin/      MixinCompatibilityTransformer, MixinTargetRedirector
├── polyfill/   72+ polyfills across 10 providers
├── embedder/   embeds Retromod runtime into a mod JAR
├── resources/  resource pack / data pack transforms
├── gui/        in-game GUI (title screen button, settings screen, file picker)
├── security/   SignatureVerifier
├── agent/      Java Agent mode (premain / agentmain)
└── legacy/, compat/, archive/, util/, virtual/
```

## Stage-by-stage

### 1. `ModVersionDetector`

Given a mod JAR, read the loader-specific metadata (`fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`) and extract:

- source MC version
- mod ID
- declared dependencies
- loader type

If any of these can't be read (malformed JSON, missing file), the mod is flagged and skipped with a log line. The batch pipeline has a post-processing pass that can still patch version metadata even for mods with an unknown source version, so 26.1 targets don't strand mods over missing version info.

### 2. `ShimRegistry`

The heart of the transformation path. Version shims are small classes that declare:

- `fromVersion` (e.g. `"1.20.1"`)
- `toVersion` (e.g. `"1.20.2"`)
- a set of class/method/field redirects

They're registered via `META-INF/services/com.retromod.core.VersionShim`. For a given source → target pair, `ShimRegistry` runs a breadth-first search over the shim graph to find the shortest valid chain. For 1.16.5 → 26.1.2 that might be 8-10 hops, each hop a different shim applying its own batch of redirects.

Version aliases (e.g. "1.20" matches "1.20.0") are handled by the registry so shim authors don't have to declare every point release.

### 3. `RetromodTransformer`

Takes the shim chain from step 2 and applies it. Internally it's an ASM visitor chain:

```
ClassReader → ClassRemapper → RetromodClassVisitor → ClassWriter
                    │                    │
                    │                    └── instruction-level rewrites
                    │                        (if instruction_level_granularity)
                    │
                    └── uses mapping composed from all shims in the chain
```

- `ClassReader` parses the input `.class`.
- `ClassRemapper` handles the bulk of name rewriting (classes, methods, fields, signatures).
- `RetromodClassVisitor` does the fine-grained stuff: individual instruction rewrites, mixin annotation fixups, and reflection call rewrites.
- `ClassWriter` emits the result.

For mixin code specifically, `MixinCompatibilityTransformer` and `MixinTargetRedirector` run a second pass to rewrite `@Inject`/`@Redirect` targets and refmap contents.

### 4. `IntermediaryToMojangMapper`

26.1 was the first fully unobfuscated Minecraft release, so Mojang shipped real names. But Fabric mods were compiled against intermediary names (`class_1234`, `method_5678`). This mapper holds ~230 intermediary → Mojang mappings and composes them with TinyV2 and ProGuard mapping files via `MappingComposer`.

Every visitor in the chain consults the mapper when it encounters an intermediary name.

### 5. Polyfills

When a mod uses an API that was removed with no direct replacement, a polyfill provider reimplements it using modern equivalents. Polyfills live in `src/main/java/com/retromod/polyfill/` and register via `META-INF/services/com.retromod.polyfill.PolyfillProvider`.

When the transformer encounters a reference to a removed class/method, it checks for a polyfill. If one exists, the reference is rewritten to point at the polyfill's implementation. If not, verification will flag the missing reference later.

### 6. Mod metadata patching

Old mods declare version ranges that reject modern Minecraft out of the box. For example:

```toml
[[dependencies.examplemod]]
    modId = "minecraft"
    versionRange = "[1.21,1.21.1)"
```

`FabricModTransformer` and `ForgeModTransformer` patch these:

- **Fabric:** replace `"minecraft"` constraint with the exact target version, relax `fabricloader`/`fabric-api` to `"*"`.
- **NeoForge/Forge:** widen minecraft `versionRange` to `[<lower>,)`, demote non-core deps to `type="optional"`, handle both bracket ranges and bare versions.

TOML parsing is careful here because the standard TOML library doesn't round-trip `[[array.of.tables]]` nicely. Instead, `ForgeModTransformer.updateMinecraftVersionRange()` processes the file line-by-line, tracking the current `[[dependencies.modid]]` block.

### 7. Output

The transformed JAR is written with a deterministic file structure: original class files replaced with transformed versions, metadata files rewritten in place, mixin refmaps updated, polyfill classes injected where needed.

If AOT is on, a serialized cache entry is also written to `config/retromod/aot-cache/` keyed by the source mod's hash.

## Runtime entry points

- **Fabric:** `RetromodPreLaunch` runs as a Fabric PreLaunchEntrypoint. It fires *before* Fabric's mod scan, which lets it transform mods in `retromod-input/` and move them into `mods/` before Fabric decides to reject them.
- **NeoForge:** `RetromodNeoForge` runs at mod construction time. Less control over ordering than Fabric's pre-launch, but NeoForge is friendlier about mod version checks.
- **Forge:** `RetromodForge`, which is the same shape as the NeoForge entry point.
- **Java Agent:** `com.retromod.agent` exposes `premain`/`agentmain` for running Retromod as a `-javaagent`, outside any specific loader.

## Verification

After transformation, [Verify Transforms]({{ '/verify-transforms' | relative_url }}) walks the output JAR and checks every reference to Minecraft classes against the actual MC JAR on disk. Missing classes/methods/fields are written to `config/retromod/verify-reports/<modid>.txt`.

## Testing

- `src/test/java/com/retromod/RetromodTest.java`: JUnit 5 test suite.
- Run with `mvn test -Dexec.skip=true`.
- Integration tests cover shim chain resolution, mapping composition, metadata patching, and verification.

## If you want to contribute

Most contributions fall into one of a few buckets, each with a dedicated skill in `.claude/skills/`:

- Adding a new version shim: `add-version-shim`
- Adding a polyfill for a removed API: `add-polyfill`
- Mapping work (intermediary ↔ Mojang ↔ SRG): `mapping-work`
- Debugging a crash caused by transformation: `debug-crash`
- Loader compatibility (Fabric/NeoForge/Forge): `mod-loader-compat`

See [Contributing]({{ '/contributing' | relative_url }}) for the workflow.
