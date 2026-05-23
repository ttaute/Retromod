# Retromod - Claude Development Guide

Retromod transforms older Minecraft mod bytecode so old mods work on newer MC versions. It supports Fabric, NeoForge, and Forge with 145 version shims and 30 polyfill providers (328 redirects).

**Repository:** https://github.com/Bownlux/Retromod.git

## Critical Context

- **Target MC version:** 26.1 (Mojang removed ALL code obfuscation)
- **Java:** Built WITH Java 25 (we need the modern compiler to use ASM 9.8 features that read MC 26.1's class file format), but bytecode targets `--release 17` so the SAME JAR runs on Java 17, 21, and 25 — broad runtime compat is the entire point. `build-all.sh` sets the per-MC `"java"` constraint in fabric.mod.json based on what each MC version itself needs: `>=17` for MC 1.20–1.20.4, `>=21` for MC 1.20.5–1.21.x, **`>=25` for MC 26.x** (MC 26.1's own class files are Java 25 bytecode, so a Java 21 JVM can't load them). Don't accidentally bump `<release>` higher — that locks out MC 1.20.x users on Java 17. ASM 9.8 itself runs on Java 8+; it just READS class files up to v69 (Java 25), which has nothing to do with what bytecode WE emit.
- **Intermediary names are dead in 26.1+.** All `class_XXXX`, `method_XXXX`, `field_XXXX` must map to Mojang official names.
- **NeoForge already uses Mojang names** since 1.17 — NeoForge mods mainly need metadata patching, not name remapping.
- **Fabric mods use intermediary names** — they need full intermediary→Mojang remapping for 26.1+.
- **ALL old shims (including pre-1.20.1) must stay.** People still translate 1.16.5, 1.14.4 mods. Shims are NOT separate build targets — they're all part of one build.
- **`Retromod.TARGET_MC_VERSION`** is auto-detected at runtime from the mod loader. NEVER hardcode version strings like `"1.21.11"` — always use `Retromod.TARGET_MC_VERSION`.

## Architecture

```
src/main/java/com/retromod/
├── core/           Core runtime: Retromod, RetromodTransformer, version detectors, mod transformers
├── cli/            CLI tool: RetromodCli (analyze, batch, aot, shims, etc.)
├── aot/            AOT compiler: AotCompiler, HybridCompiler, FullAotCompiler
├── shim/           Version shims by loader (fabric/, neoforge/, forge/, api/)
│   └── ShimRegistry.java   BFS-based shim chain finder with version aliases
├── mapping/        IntermediaryToMojangMapper, MappingComposer (26.1 core feature)
├── mixin/          Mixin compatibility: MixinCompatibilityTransformer, MixinTargetRedirector
├── polyfill/       Removed API reimplementations (72+ polyfills across 10 providers)
├── embedder/       API embedding into mod JARs, ModVersionInfo record
├── resources/      Resource/data pack transforms
├── gui/            In-game GUI (title screen button, file picker, restart popup)
├── agent/          Java Agent mode (premain/agentmain)
├── legacy/         Legacy version support utilities
├── compat/         Compatibility layer
├── archive/        Archive handling
├── util/           Utilities
└── virtual/        Virtual filesystem/classes
```

## Build Commands

```bash
# Quick build (skip tests)
mvn package -q -DskipTests -Dexec.skip=true

# Full build with tests
mvn package -Dexec.skip=true

# Run tests only
mvn test -Dexec.skip=true

# Lite build (1.20+ only, smaller JAR, no legacy/third-party polyfills)
mvn package -P lite -DskipTests -Dexec.skip=true

# Run CLI command (deps not bundled in JAR, must use mvn exec)
mvn exec:java -Dexec.mainClass="com.retromod.cli.RetromodCli" -Dexec.args="<command>" -q
```

**Important:** Always pass `-Dexec.skip=true` during build to prevent Maven from running the CLI entrypoint.

Output JAR: `target/retromod-1.0.0-beta.10.jar`

## Deploy to Minecraft

```bash
cp target/retromod-1.0.0-beta.10.jar ~/Library/Application\ Support/minecraft/mods/retromod-1.0.0-beta.10+26.1.jar
```

Game directory (macOS): `~/Library/Application Support/minecraft/`

## Key Files

| File | Purpose |
|------|---------|
| `core/Retromod.java` | Main Fabric ModInitializer, `TARGET_MC_VERSION` auto-detection |
| `core/RetromodPreLaunch.java` | Fabric pre-launch (runs BEFORE mod scan, transforms mods) |
| `core/RetromodNeoForge.java` | NeoForge entry point (transforms at constructor time) |
| `core/RetromodForge.java` | Forge entry point |
| `core/RetromodTransformer.java` | ASM bytecode transformer (class/method/field redirects) |
| `core/FabricModTransformer.java` | Patches fabric.mod.json version constraints |
| `core/ForgeModTransformer.java` | Patches mods.toml/neoforge.mods.toml version constraints |
| `core/ModVersionDetector.java` | Reads mod MC version from loader-specific metadata |
| `mapping/IntermediaryToMojangMapper.java` | ~230 intermediary→Mojang name mappings |
| `mapping/MappingComposer.java` | Generates mapping files from TinyV2 + ProGuard sources |
| `shim/ShimRegistry.java` | BFS chain finder with version aliases |
| `cli/RetromodCli.java` | CLI tool (`TARGET_MC_VERSION = "26.1"`) |
| `embedder/ModVersionInfo.java` | Record with `needsTransformation()` version comparison |
| `aot/AotCompiler.java` | AOT compilation with metadata patching for 26.1+ |

## How Mod Transformation Works

1. **Fabric:** `RetromodPreLaunch` runs before Fabric scans `mods/`. Transforms mods from `retromod-input/`, patches `fabric.mod.json`, moves to `mods/`. User restarts. Old mods CANNOT go directly in `mods/` — Fabric rejects them.

2. **NeoForge/Forge:** `RetromodNeoForge`/`RetromodForge` constructor transforms from `retromod-input/` AND in-place in `mods/`. Patches `mods.toml`. Backs up originals to `retromod-backups/`.

3. **CLI batch:** `RetromodCli.batchCommand()` processes all JARs in a folder. For 26.1+ targets, ALL mods get metadata patching via `patchModMetadata()` post-processing step, even if bytecode doesn't need transformation.

## Version Constraint Patching

**This is critical for 26.1.** Old mods have version ranges like `[1.21,1.21.1)` which reject MC 26.1.

- **Fabric:** Replace `"minecraft"` with exact target version, relax fabricloader/fabric-api to `"*"`
- **NeoForge/Forge:** Widen minecraft `versionRange` to `[<lower>,)`, make non-core deps `type="optional"`, handle both bracket ranges and bare versions
- The `ForgeModTransformer.updateMinecraftVersionRange()` method processes TOML line-by-line tracking `[[dependencies.modid]]` blocks

## ServiceLoader Registration

Shims and polyfills are discovered via ServiceLoader:
- `META-INF/services/com.retromod.core.VersionShim` — 145 entries
- `META-INF/services/com.retromod.polyfill.PolyfillProvider` — 55 entries

When adding a new shim or polyfill, ALWAYS register it in the corresponding services file.

## Testing

- **Framework:** JUnit 5 (Jupiter)
- **Test file:** `src/test/java/com/retromod/RetromodTest.java`
- **Run:** `mvn test -Dexec.skip=true`

## CI/CD

- **File:** `.github/workflows/ci.yml`
- **Java:** 25
- **Features:** Break-glass bypass (push within 5 min of revert skips tests), auto-revert failed pushes, auto-close failed PRs, CI-passed labels
- **Important:** The linter/CI may revert changes it doesn't like. If that happens, check what was reverted and fix accordingly.

## Common Pitfalls

1. **Don't hardcode `"1.21.11"` anywhere.** Use `Retromod.TARGET_MC_VERSION`. The linter has reverted this multiple times.

2. **Don't delete old shims.** Every shim from 1.12.2 onwards must stay — people translate ancient mods.

3. **TOML parsing is fragile.** The simple TOML parser can't handle `[[dependencies.modid]]` array-of-tables properly (entries overwrite each other). Use `extractMcVersionFromToml()` regex approach instead.

4. **AOT cache invalidation.** The AOT cache checks source mod hash but NOT Retromod version. After changing Retromod's transform logic, delete `config/retromod/aot-cache/` to force re-compilation.

5. **`needsTransformation()` returns false for null targetMcVersion.** If `ModVersionDetector` can't read the version, the mod gets skipped. For 26.1+, the batch command has a separate post-processing step to patch metadata even for "compatible" mods.

6. **Fabric is strictest.** Fabric checks mod versions BEFORE Retromod runs. That's why `retromod-input/` exists — mods get transformed there first, then moved to `mods/`.

7. **The JAR doesn't bundle dependencies.** You can't run `java -jar retromod.jar` directly for CLI — use `mvn exec:java` instead.

8. **Loader entry points must not reference another loader's classes.** `RetromodNeoForge`/`RetromodForge` load on NeoForge/Forge; if they touch a Fabric-only class — e.g. `RetromodPreLaunch implements net.fabricmc...PreLaunchEntrypoint` — the JVM drags that interface in and crashes at load with `NoClassDefFoundError`, *even with no mods* (#40). Same story for `Retromod implements ModInitializer`. Put loader-agnostic shared helpers on `RetromodVersion` (no loader supertype). `LoaderIsolationTest` scans the compiled entry points' constant pools and fails if they reference `net/fabricmc/` etc.

9. **Gate shims by host version.** Register a shim only when `!RetromodVersion.mcVersionExceeds(shim.getTargetVersion(), host)` (i.e. target ≤ host), in all three entry points. The 1.21.11→26.1 shim renames API classes (Fabric `ScreenEvents$BeforeRender`→`BeforeExtract`, NeoForge `IItemHandler`→`ItemHandler`, …); applied on a pre-26.1 host it rewrites mods to 26.1-only names → `NoClassDefFoundError`/`VerifyError` (#21/#31/#32/#35/#38). Unlike the intermediary remap, API names are identical in mod and runtime, so this bites on Fabric too. The intermediary→Mojang remap is separately gated on `RetromodVersion.isUnobfuscatedTarget(host)` (26.1+ only, #21/#29).

10. **NeoForge 1.20.1 mods need the toml RENAMED, not just patched.** NeoForge 1.20.2+ reads `META-INF/neoforge.mods.toml`; a 1.20.1 (Neo)Forge mod ships only `META-INF/mods.toml` and NeoForge SKIPS it at scan time ("is for Minecraft Forge or an older version of NeoForge") *before bytecode runs* (#42 — and the real cause of #38, which was wrongly blamed on the shim gate). `ForgeModTransformer.promoteToNeoForgeToml` (gated on `McReflect.isNeoForge()` + target ≥ 1.20.2) renames it, relaxes top-level `loaderVersion` to `[1,)`, and repoints the `forge` loader dependency → `neoforge` (NeoForge has no `forge` mod). Forge hosts keep `mods.toml`.

11. **Versioning: bump when the published build is buggy; fix-in-place while unpublished.** Once a beta is published and a report lands against it, ship the fix as a NEW beta so reporters get a distinguishable build. A bump touches ALL of: `pom.xml`, `build-all.sh` `VERSION`, the hardcoded strings (`RetromodCli.VERSION`, `AotCompiler.AOT_VERSION`, `RetromodPreLaunch` banner, `RetromodClient` handshake write, `SafeCrashHandler`), a new `CHANGELOG.md` section (keep the old one), and the `1.0.0-beta.N` refs in CLAUDE.md + docs. While a beta is still unpublished, just fix in place — no bump.

12. **Heavy/coremod mods can't be translated.** Create (ships Flywheel — custom GL renderer + coremods), Flywheel, Veil (rendering framework), and similar deep-integration/rendering mods are on [Mods That Can't Be Translated](docs/incompatible-mods.md). They fail with coremod/`getLoadingModList`/`VerifyError`/`CancellationException`-teardown symptoms regardless of metadata fixes (#25/#43). Don't chase these as transform bugs — confirm the mod list against the incompatible list first.

13. **A 1.20.1 *Forge* mod on a *NeoForge* host is a 1.1.0 feature — don't chase it in beta.** NeoForge 1.20.1 was its first release and still shared Forge's API; NeoForge replaced all of it in 1.20.2+. So a 1.20.1 Forge mod uses APIs modern NeoForge threw out — `ForgeRegistries`/`IForgeRegistry`, `DeferredRegister.create(IForgeRegistry,…)`, `FMLJavaModLoadingContext.get().getModEventBus()`, `net.minecraftforge.*` packages — and translating it onto NeoForge 26.1.x is the *entire* Forge→NeoForge migration (registries → `BuiltInRegistries`/`Registries` `ResourceKey`s with new `create` signatures; mod-event-bus idiom; events; data gen), i.e. Sinytra-Connector-scale, not a redirect table. beta.9's toml promotion (#11/#42) only gets such a mod *scanned*; it then crashes at construct time (e.g. `NoClassDefFoundError: …/FMLJavaModLoadingContext`, then `NoSuchFieldError: NeoForgeRegistries … BLOCKS`). **Decision (Twigs, this session): defer to Retromod 1.1.0; documented as a known limitation; Forge mods go on a Forge host for now.** Known-good 1.1.0 building block, verified against NeoForge 26.1.2's `loader-11.0.13.jar`: `FMLJavaModLoadingContext.get().getModEventBus()` ↦ embed an ASM bridge **at the original class name** (no redirect — there's no equivalent NeoForge class) whose `getModEventBus()` is `ModLoadingContext.get().getActiveContainer().getEventBus()`; embed it via `RetromodTransformer.registerSyntheticClass` **only into mods that reference it** (NeoForge loads each mod as a JPMS module → embedding the same package into 2+ mods is a split-package crash). The NeoForge jar path (`ForgeModTransformer.repackageJar`) does **not** embed synthetics today — that wiring is part of the 1.1.0 work.

14. **Retromod's startup probes must never *initialize* a Minecraft class.** `EnvironmentDetector` (client/server/headless detection, called from the loader entry-point constructors) probed for MC classes with the single-arg `Class.forName(name)` — which **initializes** the class. During mod construction that forced `net.minecraft.server.MinecraftServer.<clinit>` to run far too early; with a mod that mixins those classes (Legacy4J mixins `MinecraftServer` + `LevelSettings`) it cascaded into `wily.legacy.client.PackAlbum.<clinit>` reading `Minecraft.getInstance().gameDirectory` before the client singleton existed → NPE. Net effect: **Retromod's mere presence crashed an otherwise-fine, NATIVE (un-transformed) mod** (#46 — Legacy4J on NeoForge 1.21.11; the mod declares `versionRange "[1.21.11]"` so Retromod correctly *skips* transforming it, yet still broke it). Fix: probe with `Class.forName(name, false, loader)` (`initialize=false`) via `EnvironmentDetector.classExists` — observe, never initialize. Watch the **merged-jar trap**: the MC runtime jar contains server classes (even `net.minecraft.server.dedicated.DedicatedServer`) on a *client* too, so dedicated-server detection must key on the **absence of a client class**, not the presence of a server one. Diagnosed with a 3-mod minimal repro (Retromod + Legacy4J + Factory API): crashed *with* Retromod, launched *without* it.

15. **Mixin `@Inject` handler signatures aren't re-typed when an MC method's params change — and the crash misleads.** The mixin pass rewrites the `@Mixin`/`@Inject` *target* (so it resolves to the right MC method), but NOT the handler method's captured parameter types or body. So when MC changes a method's *signature* (not a rename), Mixin rejects the injection: `InvalidInjectionException: Invalid descriptor … Expected (NewType) but found (OldType)`. Canonical case: `Entity.addAdditionalSaveData`/`readAdditionalSaveData` `CompoundTag` → `ValueOutput`/`ValueInput` (1.21.5 ValueIO/codec refactor) — a 1.21.1 mod's save-data `@Inject` dies on 1.21.11 (#47/#48, Darker Depths + Luminous Nether). Can't be a rename fix: `ValueOutput` has a different API than `CompoundTag`, so the handler body needs rewriting (future work). **The trap:** the `InvalidInjectionException` is only a WARN/construction error early in the log; the *visible* crash is a downstream `NullPointerException` (e.g. `ModelManager.reload` → `PreparableReloadListener$SharedState.get`) because NeoForge enters a **"broken mod state"** and skips client registration events. Always scroll up to the first `Mixin apply … failed` + `Failed to wait for future Mod Construction, N errors found`. Do NOT misattribute to a third-party API — **GeckoLib loaded fine in #47/#48** (verified in our own log; the user's first guess was GeckoLib, the log said otherwise). Soft-fail today via the mixin blocklist (strips the handler → mod loads, that feature inert); proper re-signaturing is deferred.

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| ASM | 9.8 | Bytecode manipulation (9.8 required for Java 25) |
| Gson | 2.10.1 | JSON parsing |
| SLF4J | 2.0.9 | Logging |
| JUnit 5 | 5.10.1 | Testing |
| Mixin | 0.8.5 | Mixin API (provided) |
| Fabric Loader | 0.16.10 | Fabric API (provided) |

## Skills

Development skills are in `.claude/skills/`:

| Skill | Use when... |
|-------|-------------|
| `add-version-shim` | Adding support for a new MC version transition |
| `add-polyfill` | Reimplementing a removed API using modern equivalents |
| `mapping-work` | Working with intermediary/Mojang/SRG name mappings |
| `mod-loader-compat` | Fixing Fabric/NeoForge/Forge loading issues |
| `test-mod-transform` | Testing mod transforms end-to-end |
| `debug-crash` | Diagnosing crashes from transformed mods |
| `build-and-deploy` | Building and deploying to Minecraft |
| `modrinth-api` | Downloading test mods from Modrinth |
