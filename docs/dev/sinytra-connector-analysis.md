# Adopting from Sinytra Connector: A Techniques Report for Retromod

**Scope:** what to learn from Sinytra Connector (MIT) for Retromod's old-version → new-version and Forge → NeoForge translation. Connector solves a *narrower* problem than Retromod (Fabric → NeoForge on one modern MC version), so this report separates the mechanical techniques that transfer cleanly from the architecture that only partially applies. All recommendations are clean-room re-implementations against Retromod's own classes; see the licensing note at the end.

**Verification status.** Every load-bearing claim below was spot-checked against both trees. Confirmed against Retromod source: name-only member remap in `RetromodTransformer` (lines 872-917), the AW-drop comment and `stripClassTweakers` (`FabricModTransformer` lines 1131-1169), the refmap `data.official` duplication (line 918), only `IModFileCandidateLocator` registered as an SPI, no cache on `ForgeModTransformer.transformMod`, and the AOT cache folding `AOT_VERSION` + self-hash + source-hash (`AotCompiler.isValidCache` lines 700-719). Confirmed against Connector source: `IntermediateMapping.extendedMappings`/`getMappingKey`/`comp_` prefix, `AccessWidenerTransformer` AT emission with the `public-f`/`protected-f` table, `ConnectorLocator implements IDependencyLocator` at `LOWEST_SYSTEM_PRIORITY` with `PLACEHOLDER_PROPERTY`, `TransformerUtil.getCached`'s `version,sha256(input)` `.input` sidecar, and the licensing headers (Connector MIT © 2023 Su5eD; `FabricMixinBootstrap` Apache-2.0/FabricMC; `PackageTracker` cpw BootstrapLauncher LGPL v3). **Two claims from the draft were corrected after verification** (FuzzyMethodResolver wiring and `comp_` coverage) and are called out inline.

---

## 1. Executive summary: the highest-leverage adoptions

Ranked by (leverage ÷ effort), grounded in what Retromod's code actually does today:

1. **Descriptor-qualified fallback for ambiguous intermediary/SRG member names.** Retromod's `RetromodTransformer.mapMethodName`/`mapFieldName` (verified, lines 872-917) look members up **by name only** (`intermediaryMethodNames.get(name)`), guarded solely by a `method_`/`comp_`/`field_` prefix, with owner and descriptor ignored. Any intermediary short-name the offline harvest couldn't make globally unique is silently last-writer-wins → a wrong rename that surfaces as `NoSuchMethodError`/`VerifyError`. Connector's `IntermediateMapping` (`extendedMappings`, `getMappingKey`, verified) demotes colliding names into a `name+descriptor` map and falls back to it. Retromod already has `descriptor` in the method signature and a `FuzzyMethodResolver` MC-jar index in reach. **High leverage, medium effort. This is the single best transfer.**

2. **AccessWidener → NeoForge AccessTransformer conversion (instead of dropping it).** Retromod's `FabricModTransformer.remapAccessWidener` remaps the AW names but then `stripClassTweakers` **deletes the declaration** (verified: the comment at lines 1131-1134 says "the mod loses class-opening but loads"). Connector's `AccessWidenerTransformer` (verified) emits a real `META-INF/accesstransformer.cfg` from an AW→AT modifier table (`ACCESSIBLE→public`, `EXTENDABLE→public-f`/`protected-f`, `MUTABLE→public-f`), gated on `!containsAT`. This is a concrete capability gap Retromod currently gives up on, and it's directly relevant to the Forge → NeoForge track where mods legitimately need widened MC members. **High leverage, medium effort.**

3. **Transform inside a NeoForge `IDependencyLocator` at `LOWEST_SYSTEM_PRIORITY`, eliminating the mandatory restart.** Retromod today transforms at `RetromodNeoForge.<init>` (mod-constructor time, *after* the module layer is built), so it must write to disk and prompt a restart (`RestartPrompt.markPending`, `recommendAotFlow`). Retromod registers only `IModFileCandidateLocator` (discovery-only; verified as the sole SPI file). Connector's `ConnectorLocator` (verified `IDependencyLocator`, `LOWEST_SYSTEM_PRIORITY`) transforms *inside* the scan pass and `pipeline.addModFile()`s the result, so the loader never sees the untransformed jar and no restart is needed. **High leverage, high effort, the biggest UX win for the NeoForge path, and the prerequisite for fixing module-layer issues before the layer is built.**

4. **Two-key cache invalidation for the runtime transform path.** The AOT path *already* folds `AOT_VERSION` + self-hash + source-hash (verified, `AotCompiler.isValidCache` lines 700-719), so the historical pitfall #4 gap is largely closed there. But the **runtime Forge/NeoForge in-place path (`ForgeModTransformer.transformMod`) has no cache at all** (verified: no cache/sha256/isUpToDate references in that file) and re-extracts + re-transforms every launch. Adopt Connector's `TransformerUtil.getCached` sidecar scheme (`version,sha256(input)`, delete both output+sidecar on mismatch) for that path. **High leverage, low effort.**

5. **Tag transformed mods with a runtime membership predicate.** Every runtime fixup in Connector gates on `ConnectorEarlyLoader.isConnectorMod(...)`, "did *we* bring this mod in." Retromod stamps a `retromod_transformed` Fabric custom-value **and** a `Retromod-Transformed` manifest attribute, but **only on the Fabric path** (`FabricModTransformer` lines 1279/1426; the Forge/NeoForge path stamps neither), and there is **no runtime predicate** (`isTransformedMod`) anywhere (verified: grep empty). CLAUDE.md pitfalls #14/#46 are exactly about Retromod's presence never altering *native* mods. Any future runtime registry/network helper (§2.5, several Forge→NeoForge items) needs this guard first. **High leverage, low effort, a small enabling primitive.**

---

## 2. Per-dimension findings

### 2.1 Mappings (intermediary/SRG ↔ Mojang)

**Connector:** delegates entirely to the live Fabric loader running on the NeoForge host, `JarTransformInstance` calls `FabricLoaderImpl.INSTANCE.getMappingResolver().getCurrentMap("intermediary")` returning an srgutils `IMappingFile`. Pinned to one MC version (`versionMc=1.21.1`), so IDs always match. Application is three-tier: `IntermediateMapping.get()` builds a flat name→name map *plus* a descriptor-qualified `extendedMappings` for collisions (verified); `OptimizedRenamingTransformer.MixinAwareEnhancedRemapper` tries the flat map then falls back to FART's hierarchy-aware `EnhancedRemapper`; `MappingAwareReferenceMapper` remaps refmaps. Unresolved names fall through to identity, no fuzzy matching.

**Retromod:** bundles a static `intermediary-to-mojang.tsv` (121k lines, 1.21.4-pinned) loaded into three flat name-keyed maps by `IntermediaryToMojangMapper`. The actual rename in `RetromodTransformer.mapMethodName`/`mapFieldName` is **name-only, no descriptor fallback** (verified). 

- **Correction to the draft (FuzzyMethodResolver wiring):** the draft claimed `FuzzyMethodResolver` is wired *only* into the `AutoFixEngine` last-resort pass. That is **wrong**. `FuzzyMethodResolver` **is** consulted inside the primary `RetromodTransformer` bytecode-visitor path (verified at lines 2084, 2219, 2372), gated to `net/minecraft/` and `com/mojang/` owners and tried *after* `PatternHeuristics` (line 2071). `AutoFixEngine` (lines 294/362) is a *second, separate* consumer. What is genuinely true and still the point: the fuzzy/descriptor-aware resolution runs **only in that instruction-visitor fallback for MC-owned members**, not in the flat `mapMethodName`/`mapFieldName` name-lookup that the `ClassRemapper` uses for the intermediary tables, so ordinary intermediary member remap gets no descriptor disambiguation. The recommendation (fold a descriptor/hierarchy fallback into the flat member lookup) stands; the "only in AutoFixEngine" framing was inaccurate and has been dropped.

- **Correction to the draft (`comp_` coverage):** the draft said `IntermediaryToMojangMapper` "has no `comp_` handling." Verified nuance: the **bytecode** remapper *does* handle `comp_` (`RetromodTransformer.mapMethodName` line 875 keys on `method_`/`comp_`). What is missing is `comp_` in the **string-level** remap in `IntermediaryToMojangMapper`, its regexes are `class_\d+` / `field_\d+` / `method_\d+` only (verified, lines 152-154), with no `comp_` pattern. So record-component names in **refmaps and reflection/LDC strings** are not string-remapped, even though bytecode method refs to them are. The task is correctly scoped to "add `comp_` to the string-remap regexes," not "add `comp_` handling" wholesale.

**Adoptable:**

| Technique | How to adapt | Area | Leverage / Effort |
|---|---|---|---|
| **Descriptor-qualified fallback map (`extendedMappings`)** | When two intermediary/SRG names collide to different Mojang names, demote both into a `name+desc` map; have the flat `mapMethodName`/`mapFieldName` try name-only, then owner-aware name+desc. The descriptor is already a parameter. | pre-26.1 Fabric / 1.12.2 SRG / mixins | **High / Medium** |
| **Two-tier remap: flat hot-path + hierarchy/descriptor fallback in one remapper** | Fold the `FuzzyMethodResolver`-backed hierarchy fallback (already used in the instruction visitor) directly into the flat `mapMethodName`/`mapFieldName` table lookup, so inherited-member and `@Shadow`/`@Accessor` refs resolve inline in the `ClassRemapper` pass too. | pre-26.1 Fabric / mixins | Medium / Medium |
| **Delegate to the host loader's live mapping resolver** | On a *pre-26.1 Fabric host*, Retromod runs inside Fabric loader too; `getMappingResolver().getCurrentMap` gives a version-correct map for that exact host, fixing the 1.21.4-reassignment gap (pitfall #17) for the on-host path. **Only helps in-process Fabric, not offline CLI/AOT**, and yields intermediary↔named-for-that-host, not intermediary→26.1-Mojang. | pre-26.1 Fabric / loader | High / High |
| **`comp_` record-component prefix in the string-remap regexes** | Verified: `IntermediaryToMojangMapper`'s `remapString`/`remapDescriptor` patterns cover `class_`/`field_`/`method_` but not `comp_`. Add it so 1.20.5+ record refs in refmaps/reflection strings are covered (the bytecode path already handles `comp_`). | pre-26.1 Fabric / mixins | Medium / Low |
| **`IMappingFile` (srgutils) as the canonical in-memory model** | Adopt the *pattern* (owner-scoped, descriptor-aware, invertible lookups with a class provider resolving inherited members) rather than hand-rolled flat HashMaps. Retromod already pulls srgutils transitively. | general | Medium / High |
| **Widen non-private MC members to public post-remap** | *(surfaced from the raw findings, not in the draft body)* Connector's `ConnectorPreLaunchPlugin.processClassWithFlags` widens MC members after remap so cross-package accessor moves don't throw `IllegalAccessError`. Retromod's 26.1 class-moves repackage similarly; a targeted widen-on-move for repackaged MC classes would pre-empt a class of access errors. | Forge→NeoForge / general | Low / Low |

---

### 2.2 Mixins

**Connector:** three layers. (1) `MappingAwareReferenceMapper` parses each refmap ref into owner/name/desc via two regexes and remaps each part through the `IMappingFile` (exhaustive, no give-up path); `RefmapRemapper.remapRefmapInPlace` **renames the `data` namespace key** `intermediary→mojang`. (2) `MixinPatchTransformer` + the hand-curated `MixinPatches` catalog, a builder DSL (`modifyTarget`/`modifyInjectionPoint`/`extractMixin`/`modifyMixinType`/`disable`/`transform`) that repairs injection points, retargets onto NeoForge extension interfaces, converts injector kinds, and rewrites handler bodies. Note: `MixinPatches` leans on an external `org.sinytra.adapter` library (Patcher/MethodPatch/MixinClassGenerator) that is **not in the cloned repo**; adopting the full mechanism means depending on or reimplementing that engine, which is a large effort beyond the cited files. (3) `FabricMixinBootstrap.MixinConfigDecorator` tags each config with its inferred Fabric compatibility level. **`FabricMixinBootstrap` carries an Apache-2.0 / FabricMC header** (verified), a *different* attribution from Connector's MIT.

**Retromod:** shallower. `FabricModTransformer.remapRefmap` does flat string-substitution and **adds** a parallel `data.official` section rather than renaming the key (verified, line 918); `stripBrokenRefmapEntries` deletes leftovers (a give-up path Connector never needs). `MixinCompatibilityTransformer` re-points `@Mixin`/`@Inject` targets by name; `MixinTargetRedirector` is an older hardcoded guessed-rename table. `MixinBlocklist` soft-fails by stripping handlers. **There is no mechanism to re-target an injection point, convert injector kinds, or re-type a handler**, exactly the gap CLAUDE.md pitfall #15 documents (`CompoundTag→ValueOutput`).

**Adoptable:**

| Technique | How to adapt | Area | Leverage / Effort |
|---|---|---|---|
| **Parse refmap refs into (owner, name, desc); remap each part** | Pre-index `{name+desc→node}` for methods, `{name→node}` for fields; remap owner + name + descriptor separately. Makes refmap remap exhaustive for overloads and eliminates `stripBrokenRefmapEntries`. | pre-26.1 Fabric / mixins | **High / Medium** |
| **Rename the refmap `data` namespace KEY** instead of adding a parallel section | On 26.1 the runtime expects mojang-named refmap lookups; a stale `intermediary` section can mislead the resolver. Replace the key rather than duplicating (Retromod currently adds `data.official` beside `data.intermediary`). | pre-26.1 Fabric / mixins | Medium / Low |
| **Declarative `MethodPatch`-style repair DSL** | The missing tier between name-redirect and blocklist soft-fail: `targetClass/targetMethod/targetInjectionPoint` + `modifyTarget/modifyInjectionPoint/modifyMixinType/disable/transform`. **Version-key the catalog and populate from real failing-mod data.** Directly addresses pitfall #15. Note the heavy-lifting AST engine (`org.sinytra.adapter`) is out-of-repo and would need reimplementing. | pre-26.1 Fabric / Forge→NeoForge / mixins | **High / High** |
| **`extractMixin`: re-host an injection onto a loader extension method** | For Forge→NeoForge, several Forge patch points became NeoForge extension interfaces (`IBlockExtension`, `CommonHooks`). Retarget a mod's `@Redirect`/`@Inject` onto the NeoForge equivalent rather than blocklist-stripping. Relevant to snapshot-7 Track 1. | Forge→NeoForge / mixins | Medium / High |
| **Scan ALL classes for `@Mixin`, not only declared configs** | `isMixinClass` already exists in both Retromod mixin transformers; add a whole-jar scan so runtime-generated-config mods are covered. | general / mixins | Low / Low |
| **Decorate configs with inferred Fabric compat level** | Only where Retromod drives mixin-config registration (not on a genuine Fabric host). **Apache-2.0 / FabricMC attribution required, not MIT.** | pre-26.1 Fabric / mixins | Medium / Medium |

---

### 2.3 Loading (discovery SPI, timing, JiJ, split-package, caching)

**Connector:** transforms *inside* FML's discovery phase via four layered SPI hooks, `ITransformationService` (earliest), `IModFileCandidateLocator` (fabric-loader virtual jar), `IDependencyLocator` (`ConnectorLocator`, the main event, at `LOWEST_SYSTEM_PRIORITY` so it sees all already-loaded mods; verified), `ICoreMod`. `scanMods` → discovers Fabric jars, recursively extracts JiJ (`discoverNestedJarsRecursive` → `prepareNestedJar`, `parent$child` naming, `parentToChildren` multimap), runs Fabric's real `ModResolver`, transforms multithreaded, merges split packages, wraps as real `IModFile`s. Placeholder mods forced to version `0.0` so FML's `UniqueModListBuilder` prefers the transformed copy. Cache: `version + sha256(input)` sidecar (verified).

**Retromod:** later model, transforms at `@Mod` constructor time then requires a restart. `RetromodModLocator` is discovery-only `IModFileCandidateLocator` (verified sole SPI). No cross-mod dependency solver (just relaxes constraints). JiJ metadata patched in place. Split-package handled by point fixes (`stripMixinSyntheticPackage` #87, filename sanitize #47, per-mod `SyntheticEmbedder`). No runtime cache on the in-place path (verified).

**Adoptable:**

| Technique | How to adapt | Area | Leverage / Effort |
|---|---|---|---|
| **Transform in an `IDependencyLocator` at `LOWEST_SYSTEM_PRIORITY`** | Move NeoForge transform out of `RetromodNeoForge.<init>` into `scanMods`; build the transformed `IModFile` like `createConnectorModFile`. Eliminates the mandatory restart; lets Retromod fix module-layer issues before the layer builds, which `recommendAotFlow` admits it currently can't. | loader (NeoForge only) | **High / High** |
| **Placeholder-version tiebreak** | Force the original's `IModInfo` version to `0.0` (Connector's `PLACEHOLDER_PROPERTY`, verified) so `UniqueModListBuilder` discards it in favor of the transformed copy, both coexist in one launch, no restart, no file-move. | loader (NeoForge only) | High / Medium |
| **Two-key cache (transformer version + input sha256) on the runtime path** | Copy `TransformerUtil.getCached`/`CacheFile` for `ForgeModTransformer.transformMod`, which currently has no cache. | general | **High / Low** |
| **Recursive JiJ extraction into a graph** (`parent$child`, multimap) | Gives Retromod a real JiJ graph so a nested Fabric-API/registration lib can be transformed and re-fed as its own candidate, and duplicate JiJ deps de-duplicated. Strengthens #71. | pre-26.1 Fabric | Medium / Medium |
| **Reject jars already carrying target-loader metadata at the locator boundary** | `isFabricModJar` skips jars with `neoforge.mods.toml` unless a placeholder marker is present. A cheap same-loader/same-metadata guard complements the `-retromod` filename filter and #84 same-minor guard. | loader | Medium / Low |
| **Global split-package merger + virtual jar** | A systematic package-ownership pass (vs today's point fixes) plus a `DummyVirtualJar` to satisfy deleted API modules. **NeoForge-only** (needs cpw `UnionFileSystem`/module-layer APIs). | Forge→NeoForge (NeoForge only) | Medium / High |
| **Real cross-mod dep resolution via Fabric `ModResolver`** | Seed with synthetic host/builtin candidates to detect missing deps up front. Global aliasing pattern for mapping modids. **Fabric-side only.** | pre-26.1 Fabric | Medium / High |

---

### 2.4 Metadata

**Connector:** never rewrites on-disk manifests, parses `fabric.mod.json` once and synthesizes an in-memory NeoForge `IModFileInfo` (`FabricModMetadataParser.createForgeMetadata` builds a NightConfig `Config`). Normalizes ids/versions for JPMS (`-`→`_`, reserved-keyword suffix, `+`→`_`, version must match `^\d+.*`). `displayTest = IGNORE_ALL_VERSION`/`IGNORE_SERVER_VERSION` for side-only mods (verified). Converts AccessWidener → AccessTransformer at transform time. Entrypoints stay in metadata and are invoked by an embedded Fabric Loader.

**Retromod:** opposite, keeps each mod on its native loader and rewrites the manifest **text** (regex). `FabricModTransformer` pins versions and strips things; `ForgeModTransformer.updateMinecraftVersionRange` walks TOML line-by-line; `generateTomlFromMcmodInfo` (#79) synthesizes a fresh TOML by string concatenation. Verified step sequence in `transformMod`: `makeMixinConfigsNonFatal → stripAccessWideners → stripMixinSyntheticPackage → SyntheticEmbedder.embed → generateTomlFromMcmodInfo → updateModsToml ×2 → promoteToNeoForgeToml → patchJarInJarMetadata → ModDataMigrator → repackageJar`.

**Adoptable:**

| Technique | How to adapt | Area | Leverage / Effort |
|---|---|---|---|
| **AccessWidener → AccessTransformer emission** (instead of `stripClassTweakers` dropping it) | Emit `META-INF/accesstransformer.cfg` with the AW→AT modifier table (class `ACCESSIBLE→public`/`EXTENDABLE→public-f`; method `ACCESSIBLE→public`/`EXTENDABLE→protected-f`; field `ACCESSIBLE→public`/`MUTABLE→public-f`, all verified in `AccessWidenerTransformer`), replicate implicit owner-widening, gate on `!containsAT`. Retromod already remaps the AW names, this is the "last mile." | Forge→NeoForge / pre-26.1 Fabric / mixins | **High / Medium** |
| **`displayTest = IGNORE_ALL_VERSION / IGNORE_SERVER_VERSION` for side-only mods** | Add to the generated/patched TOML when `ModEnvironmentDetector.isServerOnly` (env signal already exists). Stops a translated client/server-only mod failing NeoForge's version handshake. | Forge→NeoForge / metadata | Medium / Low |
| **Exhaustive mixin-config jar-scan** | After reading declared configs, also union all `*.mixins.json`/`mixins.*.json` jar entries. | mixins | Medium / Low |
| **id/version normalization for JPMS + FML** (`-`→`_`, reserved-keyword suffix, `+`→`_`, leading-digit version) | Apply where Retromod *writes new* metadata, especially `generateTomlFromMcmodInfo`, which copies the mcmod.info modid verbatim. Complements filename sanitization (#16). | 1.12.2 / Forge→NeoForge / metadata | Medium / Low |
| **Structured TOML writer (NightConfig) for *synthesized* TOML** | Use NightConfig (already on NeoForge's classpath) only for `generateTomlFromMcmodInfo`; keep the line-by-line regex for *in-place patching* where preserving author formatting matters. Removes the pitfall #3 escaping fragility. | 1.12.2 / metadata | Medium / Medium |
| **Preserve authors/URL/logo/credits into generated metadata, URL-validated** | `generateTomlFromMcmodInfo` currently drops them; extract and emit with a validity check. | 1.12.2 / metadata | Low / Low |

---

### 2.5 Registry / lifecycle / Fabric-API bridging

**Connector:** never rewrites registration bytecode. Keeps Fabric's imperative `Registry.register` model and reshapes the *host* at runtime via mixins during a controlled `ConnectorLoader.isLoading()` window: `BuiltInRegistriesMixin` @Redirects `freeze()` to a no-op; `EntityDataSerializersRegistry` does an unfreeze/register/refreeze bracket via `MappedRegistryAccessor`; `EntityDataSerializersMixin` reroutes at the vanilla reject site; `LazyEntityAttributes` placeholder-then-backpatch; `NetworkRegistryMixin` de-collapses namespaced payloads. Ships the **real** Forgified Fabric API as a located mod.

**Retromod:** 100% static bytecode rewriting, no `.unfreeze`/`RegisterEvent`/`invokeEntrypoints` in `src/main/java`. Forge→NeoForge registry migration is redirect tables (`ForgeRegistryApiShim`, `Forge_1_20_to_NeoForge_1_21`); after rewrite, NeoForge's own `DeferredRegister`/`RegisterEvent` does the actual registration. Fabric API is ~26 hand-written reflective proxy bridges.

**Adoptable:**

| Technique | How to adapt | Area | Leverage / Effort |
|---|---|---|---|
| **Runtime membership predicate** (`isConnectorMod` analog) | Stamp a manifest attribute/package marker during transform (already done for Fabric via `Retromod-Transformed`; **extend to the Forge/NeoForge path**) and expose `Retromod.isTransformedMod(...)`. **Prerequisite for any runtime helper** and directly serves pitfalls #14/#46. | general | **High / Low** |
| **Controlled loading-state flag** (`isLoading()`) | A static `Retromod.isMigrating` window around migrated-mod construction; gate any registry-relaxation helper on it. The prerequisite for the freeze/reroute techniques below. | Forge→NeoForge / loader | High / Medium |
| **Freeze/unfreeze/refreeze bracket for late registration** | For NeoForge-only registries a migrated mod writes after freeze (entity data serializers, POI, attributes), the one piece redirect tables *cannot* supply, since the value otherwise never registers. **NeoForge runtime only.** | Forge→NeoForge / mixins | Medium / Medium |
| **Reroute-at-the-reject-site** | @Inject at the exact host guard that rejects a modded registration and reroute instead of failing. | Forge→NeoForge / mixins | Medium / Medium |
| **Modded-payload namespace de-collapsing** for networking | Guard so a migrated mod's payload under `minecraft:`/reused namespace isn't collapsed, gated on the membership predicate. | Forge→NeoForge / pre-26.1 Fabric | Medium / Medium |
| **Typed `@Accessor`/`@Invoker` mixins instead of reflection** | *(surfaced from raw findings)* Where Retromod pokes host internals reflectively (`ForgeRegistriesShim` getField/getMethod) on a mixin-capable NeoForge host, typed accessor mixins fail at load rather than silently at runtime. | mixins / Forge→NeoForge | Low / Medium |
| **Ship/locate the real API as a mod** vs hand-written proxies | Inject a synthetic loader/library mod file so the module system resolves the API. Only *partially* transfers, Retromod spans ~120 versions with no single forgified-fabric-api artifact. | pre-26.1 Fabric / loader | Medium / High |

---

### 2.6 Pipeline / coremods

**Connector:** declarative FART `Renamer.builder().add(step)...` chain per jar, multithreaded; `cleanupEnvironment` clears Mixin's static `ClassInfo.cache` between batches. Runs Forge-style JS coremods + Java `ICoreMod`s against the **clean MC classpath**; ships `ConnectorCoremods` that patches the *host* MC to restore shapes old mods expect (re-adds `KeyMapping.MAP`, injects a `CreativeModeTab` ctor overload, re-adds synthetic backing fields). `SplitPackageMerger` reassigns duplicated packages to a single owner; `ForgeModPackageFilter` strips duplicates from other mods' live filesystems.

**Retromod:** hand-written imperative step list in `transformMod`, parallel per-*class* within a jar, sequential across jars. No runtime cache on the in-place path. Does **not** execute or translate coremods (`ModComplexityAnalyzer` only *detects* them; pitfall #12 lists them untranslatable). No general split-package merger.

**Adoptable:**

| Technique | How to adapt | Area | Leverage / Effort |
|---|---|---|---|
| **Cache key = transformer version + host + source hash, sidecar-based** | Fold into the runtime path (AOT already does this). | general | **High / Low** |
| **Post-transform cross-jar split-package merger** (+ exclude packages already on live module layers) | Generalizes the ad-hoc #87/#9/#63 fixes into one systematic pass. **NeoForge-only.** | Forge→NeoForge (NeoForge only) | High / High |
| **Declarative ordered transform-step chain** | Refactor `transformMod`'s inline sequence into composable `JarStep` objects, explicit ordering, individually testable, shareable across loaders. | general | Medium / Medium |
| **Host-side `ICoreMod` layer** to restore removed MC members/overloads | Register a NeoForge `ICoreMod` that injects a missing member/overload back into the host class, the reverse of mod-side redirect tables, unlocking cases currently deferred as untranslatable. **Prefer the public `ICoreMod` SPI, not Connector's reflective ModLauncher bootstrap.** | Forge→NeoForge (NeoForge only) | Medium / High |
| **Per-item error isolation → structured failure report** | Collect and surface per-mod failures (like `ModLoadingIssue` + startup notifications) instead of silently dropping. | general | Medium / Low |
| **Cross-jar shared-state cleanup between batches** | Audit that per-jar mutable state on `RetromodTransformer.getInstance()` (metrics, reflection remapper caches) is reset between jars, as Connector does for `ClassInfo.cache`. | general | Low / Medium |
| **Reference-gate / fast-copy library jars** | Skip heavy steps when a cheap pre-scan finds nothing relevant; fast-copy non-mod libs in JiJ trees. | general | Low / Low |

---

## 3. Ranked adoption backlog (actionable)

Ordered high-leverage/low-effort first. Each is scoped to become a task.

**Tier A, do first (high leverage, low/small effort)**

1. **Runtime cache for `ForgeModTransformer.transformMod`.** Add a `version,sha256(input)` sidecar (mirror the AOT key: Retromod version + loader/dist + source hash). Skip re-extract/re-transform when up to date; delete output+sidecar on mismatch. *(loader/general, Low)*
2. **Runtime membership predicate `Retromod.isTransformedMod(...)` + stamp the Forge/NeoForge path.** The Fabric path already stamps `Retromod-Transformed`; extend the stamp to `ForgeModTransformer` and expose one predicate. Enabling primitive for all runtime helpers; satisfies pitfalls #14/#46. *(general, Low)*
3. **`comp_` record-component prefix** in `IntermediaryToMojangMapper`'s string-remap regexes (verified missing there; the bytecode path already covers it). *(pre-26.1 Fabric, Low)*
4. **`displayTest` for side-only mods** in `updateModsToml`/`generateTomlFromMcmodInfo`, driven by `ModEnvironmentDetector`. *(Forge→NeoForge, Low)*
5. **id/version JPMS normalization** in `generateTomlFromMcmodInfo` (modid `-`→`_`, reserved-keyword suffix, version `+`→`_` and `^\d+.*` fallback). *(1.12.2, Low)*
6. **Reject already-native jars at the locator boundary** (same-loader/same-metadata guard). *(loader, Low)*
7. **Rename the refmap `data` namespace key** instead of adding a parallel `data.official` section. *(mixins, Low)*

**Tier B, high-value, medium effort**

8. **Descriptor-qualified fallback in the flat `mapMethodName`/`mapFieldName`.** Build a `name+desc` collision map at harvest/load; try name-only then owner+name+desc. *(the #1 correctness win, Medium)*
9. **Parse refmap refs into owner/name/desc and remap each part** through the mapping; retire `stripBrokenRefmapEntries`. *(mixins, Medium)*
10. **AccessWidener → AccessTransformer emission** (replace `stripClassTweakers`'s give-up), gated on `!containsAT`. *(Forge→NeoForge/Fabric, Medium)*
11. **Structured TOML writer (NightConfig) for synthesized TOML only.** *(1.12.2, Medium)*
12. **Exhaustive `@Mixin`/mixin-config jar-scan.** *(mixins, Low/Medium)*
13. **Two-tier remapper: fold the existing `FuzzyMethodResolver` hierarchy fallback into the flat member-lookup path** (today it runs only in the instruction-visitor fallback for MC-owned members, not the intermediary-table lookup). *(pre-26.1 Fabric, Medium)*

**Tier C, strategic, high effort (schedule against 1.1.0 / snapshot-7+)**

14. **NeoForge `IDependencyLocator` discovery-time transform** + **placeholder-version tiebreak**, eliminates the restart, enables pre-layer module fixes. *(loader, NeoForge only, High)*
15. **Declarative `MethodPatch`-style mixin-repair DSL**, version-keyed, data-driven from real failing mods (pitfall #15). Note the AST engine (`org.sinytra.adapter`) is out-of-repo and must be reimplemented. *(mixins, High)*
16. **Global cross-jar split-package merger** (NeoForge only). *(Forge→NeoForge, High)*
17. **Host-side `ICoreMod` restore-shape layer** via the public SPI (NeoForge only). *(Forge→NeoForge, High)*
18. **Loading-state flag + freeze/unfreeze/refreeze bracket + reroute-at-reject-site** for the registries redirect tables can't fix (data serializers/POI/attributes). *(Forge→NeoForge, NeoForge only, Medium/High, depends on #2, #14)*
19. **Refactor `transformMod` into a composable `JarStep` chain.** *(general, Medium)*

---

## 4. What the draft analysis MISSED

Five items worth flagging that the per-dimension bodies under-surfaced:

- **Post-remap member-widening (`ConnectorPreLaunchPlugin.processClassWithFlags`).** Connector widens all non-private MC members to public after cross-namespace remap so that package-layout differences don't cause `IllegalAccessError`. Retromod's 26.1 class-moves repackage MC classes similarly, so a targeted widen-on-move is a cheap pre-emptive fix. Now in the §2.1 table (Low/Low).
- **The `org.sinytra.adapter` dependency for the mixin-repair DSL.** The draft's Tier-C "declarative repair DSL" understated cost: Connector's `MixinPatches` delegates the actual AST rewriting to an external library not present in the clone. Retromod would have to build that engine, not just the catalog. Now noted in §2.2 and backlog #15.
- **Typed accessor/invoker mixins over reflection.** Where Retromod already reaches host internals reflectively on a NeoForge host, Connector's `@Accessor`/`@Invoker` pattern fails fast at load. Added to §2.5.
- **The draft mis-stated FuzzyMethodResolver's wiring.** It IS in the primary transformer path (not only `AutoFixEngine`). Corrected in §2.1; the recommendation is unchanged but the rationale is now accurate.
- **`comp_` is partially handled, not absent.** The bytecode remapper covers `comp_`; only the string/refmap regexes don't. The task is narrower than the draft implied. Corrected in §2.1 and backlog #3.

**A Retromod area that would benefit but has no Connector analog:** the offline CLI/AOT path is where Retromod is most exposed (no host loader, no live mappings, cross-version intermediary reassignment), and Connector, being single-version and always-on-host, offers nothing for it. The descriptor-fallback map (#8) is the one item that helps the offline path, which is another reason it ranks first among the correctness wins.

---

## 5. What does NOT transfer (and why)

Connector is **one-directional, single-namespace, single-MC-version**: intermediary/Yarn Fabric source → Mojang-named NeoForge runtime, pinned to MC 1.21.1, on a live NeoForge host with a Mixin environment. Retromod is **many-to-many** (Fabric/Forge/NeoForge sources; MC 1.12.2 → 26.x targets; host-version-gated shims; plus an offline CLI/AOT path with *no* host MC on the classpath).

- **The live-mapping-resolver delegation only helps Retromod's in-process pre-26.1 Fabric path**, never CLI/AOT, and even there yields intermediary↔named-for-that-host, not intermediary→26.1-Mojang. The bundled-TSV + fuzzy-fallback model is the *right* answer for Retromod's offline and cross-version cases.
- **The `IDependencyLocator`/`LOWEST_SYSTEM_PRIORITY` discovery-time-transform, `SplitPackageMerger`, `ForgeModPackageFilter`, and host-side `ICoreMod` are NeoForge-only.** They depend on cpw `SecureJar`/`UnionFileSystem`/module-layer APIs and the FML SPI. Fabric's Knot loader has no JPMS modules; Forge's `ModDirTransformerDiscoverer` claims service-declaring jars onto the transformer layer (exactly why `RetromodForgeModLocator` is deliberately unregistered); the offline CLI has no module layers at all. Any adaptation must be host-gated to NeoForge and must **not** run in the offline path.
- **The `MixinPatches` catalog *contents* do not transfer**, they hardcode one MC version's Yarn method names and inlining decisions. Only the *technique* (a declarative repair DSL) transfers, version-keyed and data-driven.
- **Connector's registry runtime-mixin machinery is mostly unnecessary for Retromod's Forge→NeoForge common path.** Forge's `DeferredRegister`/`RegisterEvent` model is the *same family* as NeoForge's, so a static redirect table genuinely suffices for ordinary block/item/entity registration. The freeze-bypass is only needed for the residue the host actively rejects/freezes (data serializers, POI, attributes, namespaced payloads), that residue is what's worth adopting, not the whole apparatus.
- **The dependency-resolution and entrypoint-invocation machinery is irrelevant to Retromod's same-loader flows** (Fabric-on-Fabric, Forge-on-Forge/NeoForge). It only matters if Retromod adds a true cross-loader Fabric→NeoForge mode, out of current scope.
- **Connector leans on `sun.misc.Unsafe`/`IMPL_LOOKUP` and private-API reflection** (`ImmediateWindowHandler.provider`/`LaunchPluginHandler.plugins` VarHandle swaps, the `EarlyJSCoremodTransformer` reflective ModLauncher bootstrap). Treat these as fragile version-specific hacks. Adopt the *architecture*; prefer public SPIs.
- **The AOT cache-versioning "gap" is largely already closed** in Retromod (verified: `AotCompiler` folds `AOT_VERSION` + self-hash + source-hash, lines 700-719). The real remaining gap is the *runtime in-place path having no cache*.
- **Connector's whole "map fabric.mod.json → in-memory NeoForge IModFileInfo + invoke Fabric entrypoints via embedded loader" architecture is unnecessary** for Retromod, which keeps mods on their native loader. Only the narrow mechanical pieces (AW→AT table, exhaustive mixin scan, `displayTest`, id/version normalization) transfer.

---

## 6. Licensing note

**Sinytra Connector is MIT-licensed** (verified: `LICENSE`, MIT, Copyright (c) 2023 Su5eD). Retromod is also MIT (headers: "Copyright (c) 2026 Bownlux. MIT License."), so the two are cleanly compatible.

- **Learning from the architecture and re-implementing the techniques against Retromod's own classes is unrestricted**, the recommended approach for every item.
- **If any Connector code is copied verbatim or closely derived** (e.g. the `IntermediateMapping` duplicate-name promotion, the `TransformerUtil.getCached` sidecar-hash scheme, the `SplitPackageMerger` merge algorithm, the `AccessWidenerTransformer` AW→AT mapping table, `ConnectorFabricModMetadata` normalization, the placeholder-version tiebreak), Retromod must retain **Connector's MIT copyright and permission notice** for those portions and credit Sinytra Connector (a `NOTICE` entry or per-file attribution comment).
- **Two distinct further attributions apply to specific files** (both verified in-tree) and must be preserved if their mechanism is adapted:
  - `service/FabricMixinBootstrap.java` carries an **Apache-2.0 / FabricMC** header, reuse requires the Apache-2.0 header + FabricMC attribution, not Connector's MIT.
  - `PackageTracker` carries a **cpw BootstrapLauncher LGPL v3** header, a stronger copyleft constraint; **re-implement it independently** rather than copying.
- **Recommendation:** prefer clean-room re-implementations with an attribution comment noting the technique came from Sinytra Connector. Only carry the MIT/Apache/LGPL notices where code is actually lifted.

---

**Relevant Retromod files for the top tasks (repo-relative):**
- `src/main/java/com/retromod/core/RetromodTransformer.java` (name-only member remap, lines 872-917; FuzzyMethodResolver fallback in the instruction visitor, lines 2084/2219/2372)
- `src/main/java/com/retromod/core/ForgeModTransformer.java` (`transformMod` step sequence; no runtime cache; `generateTomlFromMcmodInfo`)
- `src/main/java/com/retromod/core/FabricModTransformer.java` (`stripClassTweakers` lines 1131-1169; refmap `data.official` line 918; `Retromod-Transformed` stamp lines 1279/1426)
- `src/main/java/com/retromod/mapping/IntermediaryToMojangMapper.java` (string-remap regexes, lines 152-154; missing `comp_`)
- `src/main/java/com/retromod/aot/AotCompiler.java` (cache key, lines 700-719)
- `docs/dev/snapshot-7-plan.md` (Forge→NeoForge Track 1)