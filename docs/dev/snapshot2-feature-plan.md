# 1.2.0-snapshot.2 - §A / §B / §C completion plan

Snapshot.2 is the feature-complete snapshot for the 1.2.0 "general update". This is the
implementation plan for the three remaining work areas, grounded in a current-state audit
of the codebase (2026-06-19). Already shipped in snapshot.2: #78 across all loaders, the CF
loader stub, the `ConfigTracker` VerifyError fix, AOT-first guidance, the batch/AOT
class-move fix + Fabric-gated member mappings, and the 26.2 candle/`ColorCollection`
polyfill (§B).

**Sizing legend:** S = hours · M = a day or two · L = multi-day · XL = bigger than one snapshot (ship a mechanism + initial coverage, grow later).

---

## §A - Pre-26.1 Fabric bridge (#55)

**The core problem (verified):** the 20 legacy Fabric shims (`Fabric_1_14_4_to_1_15_2` … `Fabric_1_20_6_to_1_21`) register redirects keyed on **Mojang/Yarn** names, but distributed Fabric mods ship **intermediary** names (`class_/method_/field_XXXX`), so the shims no-op on real mods. On a **26.1+ host** this is harmless (the intermediary→Mojang harvest does the real translation). On a **pre-26.1 Fabric host** the harvest is gated OFF (the host is obfuscated; Fabric does intermediary→obfuscated itself), so genuinely-changed APIs have nothing bridging them. `intermediary-to-mojang.tsv` is version-agnostic (intermediary names are stable), so unchanged content mods already work; only redesigned/removed APIs bite (the classic case: ≤1.16 entity model/renderer, rebuilt in 1.17).

- **A1 - Abstract-base model synthetics (M).** `Pre1_17ModelBridge` Layers 1-3 (synthetic ctor, `method_22699` render override, 6 concrete model bases) are done + verified. Remaining: synthetic reimpls of the **abstract** bases `class_4592` / `class_4593` / `class_4595` (the AgeableListModel family) absent on modern hosts, so that whole class of ≤1.16 mods loads. Extends the existing intermediary-named bridge; highest-value, well-scoped §A piece. Verify: extend `Pre1_17ModelBridgeTest` + an AgeableListModel-using mod on a pre-26.1 Fabric instance.
- **A2 - Re-author the legacy shims in intermediary (XL).** Convert the 20 shims' redirects from Mojang/Yarn to intermediary. Don't hand-port: build a harvest that diffs **Fabric intermediary mappings between adjacent versions** to find members that genuinely changed (different intermediary name across the gap) vs ones that are stable (no redirect needed - the mod already works). Output per-transition intermediary redirect tables; the shims load them. Scope for snapshot.2: the **high-impact transitions** (1.16→1.17 model/render, 1.18→1.19 text/registry, 1.20.x), not all 20. Keep the Mojang-named tables too - they're what an in-game 26.1 boot uses via the harvest. Verify: descriptor-level (A3) + in-game (A4).
- **A3 - Descriptor-aware gap report on pre-26.1 (S-M).** The compat audit checks name-presence, which is meaningless on an intermediary runtime (the names are stable; the descriptors are what don't match). Add a descriptor-comparison mode so the report reflects what actually breaks on a pre-26.1 host. Tooling only.
- **A4 - Batch-test real old Fabric content mods on a pre-26.1 instance (S).** Acceptance test: a handful of real 1.16.5 / 1.18.2 content + model mods transformed onto a 1.20.1 Fabric host. This is the only honest proof §A works.

**§A realistic snapshot.2 scope:** A1 complete + A3 + A4; A2 as a mechanism + the top ~3 transitions (full coverage grows over later builds). Pre-26.1 Fabric is a smaller audience than the 26.1+ headline, so prioritize accordingly.

---

## §B - Deep-API-change polyfills

- **B1 - 26.2 `ColorCollection` block fields (DONE).** `registerStaticFieldAccessor` + 64 dyed-block rules. Shipped this session.
- **B2 - `DeferredSpawnEggItem` synthetic (#52) (M).** NeoForge deleted the class; no redirect target. Embed a synthetic `DeferredSpawnEggItem` **at its original name** (extending the surviving spawn-egg/`DeferredItem` equivalent) via `RetromodTransformer.registerSyntheticClass`, into mods that reference it only (per-mod, to avoid the JPMS split-package trap - same rule as #13/#87). Verify: transform Luminous: Nether; unit test the synthetic loads + constructs.
- **B3 - Armor-layer `EquipmentClientInfo` (#51) (M-L).** `ArmorMaterial$Layer` → `EquipmentClientInfo` is a different contract, not a rename. Build an adapter polyfill that reshapes the old layer-registration/render calls onto the new equipment-model API; also map the sibling `AnimationUtils.swingWeaponDown(…, Mob, …)` → `(…, HumanoidArm, …)`. Verify: transform Illagers Wear Armor; the armor layer renders.
- **B4 - `FMLJavaModLoadingContext` bridge (#85 piece) (M).** ForgeRegistries→NeoForge/`BuiltInRegistries` is already done (`ForgeRegistriesShim` + the two Forge shims). Remaining: embed an ASM bridge at the original `FMLJavaModLoadingContext` name whose `get().getModEventBus()` ↦ `ModLoadingContext.get().getActiveContainer().getEventBus()` (design verified in CLAUDE.md #13), via `registerSyntheticClass` into referencing mods only. This is the next concrete brick of the Forge→NeoForge migration; the full migration stays XL/long-tail. Verify: a 1.20.1 Forge mod gets further into construct on a NeoForge host.
- **B5 - 26.2 render-API removals (XL).** `registerRemovedMethodNeutralize` already soft-fails the void imperative `RenderSystem` setters. The hard removals - `MultiBufferSource` (one of the most-referenced types in modding), the immediate-mode `Tesselator`/`VertexFormat$Mode` layer, `TicketType.create` - are non-void/structural; neutralize can't cover them, they need **semantic adapter polyfills**. Snapshot.2 scope: ship the **smallest-surface** one end-to-end (likely `TicketType.create` - 1.18-era chunk-loaders) to prove the adapter shape, and scope `MultiBufferSource`/`Tesselator`→`MeshData`/`BufferBuilder` as a tracked adapter (too big to fully land in one snapshot). Be honest in docs about the boundary.
- **B6 - Mixin `@Inject` handler re-signaturing (XL).** No mechanism exists today (the mixin pass only does target-remap, blocklist-strip, and removed-class auto-neutralize). Build: detect an `@Inject` whose captured-arg descriptor mismatches the target's new signature, rewrite the handler descriptor + renumber body locals, and **bail to the existing soft-fail when the body uses a dropped/redesigned param** (the Better End Island limit - re-sig only helps when the body still links). Needs a signature-diff source (read the host MC class at runtime, or a curated per-version table). Snapshot.2 scope: build the mechanism + cover the "dropped trailing param, body intact" case; grow coverage as beneficiaries surface. (Better End Island is NOT a beneficiary - it's a full dragon-fight redesign, documented as 26.2-incompatible.)

**§B realistic snapshot.2 scope:** B2 + B4 complete; B3 complete or close; B5/B6 as mechanisms + initial coverage.

---

## §C - JiJ-recursion (#95)

**Goal:** apply Retromod's full bytecode transform + metadata-patching to the libraries a mod bundles in `META-INF/jars/` (Fabric) and `META-INF/jarjar/` (NeoForge), recursively. **Current state (verified):** CLI `transformJar` patches nested-jar *metadata only*; AOT `compileJar` ignores nested jars entirely; Fabric runtime `remapNestedJar` transforms nested bytecode but only the intermediary remap (not the version-chain shims); Forge runtime `patchJarInJarMetadata` is metadata-only. So no pipeline does a *full* nested transform, and nested **Forge** libs never get the `mods.toml`→`neoforge.mods.toml` promotion (#13/#42), so they're skipped at scan.

- **C1 - One recursive nested-jar transform helper (M).** A single `transformNestedJar(byte[], transformer, depth)` that extracts in-memory, runs `transformer.transformClass` on every `.class`, applies the same metadata patching as the top level (fabric.mod.json relax / mixin-config non-fatal / `mods.toml` promotion), then re-zips. Recurses on its own `META-INF/jars/` + `META-INF/jarjar/` with a **depth cap** (e.g. 3) and a visited-set, and soft-fails per-nested-jar (a bad lib can't kill the parent).
- **C2 - Wire it into all four pipelines (M).** Replace the metadata-only `transformNestedJar` (CLI), add a nested pass to `AotCompiler.compileJar`, upgrade Fabric `remapNestedJar` to apply the full transformer (not just intermediary), and add a bytecode+recursion pass to Forge `patchSingleJijJar`. Thread the configured `transformer` through so nested libs get the *same* shim chain + class-moves as the parent.
- **C3 - Forge→NeoForge promotion for nested libs (S-M, entangled with B4).** In `patchSingleJijJar`, after `updateModsToml`, call `promoteToNeoForgeToml` on the extracted nested dir so a bundled 1.20.1 Forge lib (e.g. `crackerslib-forge`) gets scanned on a NeoForge host. Full translation of such a lib still needs the Forge→NeoForge work (B4 +), so this gets it *scanned*; document the boundary.
- **C4 - Safety + tests (S).** Zip-bomb guard (reuse `ZipSecurity`), depth/size caps logged (no silent truncation), and a regression: a synthetic mod bundling a nested jar that references a relocated class (e.g. `EndDragonFight`) - assert the nested class is rewritten after transform.

**§C realistic snapshot.2 scope:** C1-C4 all land; this one is well-bounded (the hooks are known) and high-value (fixes the #95 "library reported missing" class of crash).

---

## Recommended sequencing

Ordered by value ÷ size and by dependency:

1. **§C JiJ-recursion (C1-C4)** - self-contained, high-value, hooks known. Do first.
2. **§B B2 `DeferredSpawnEggItem` + B4 `FMLJavaModLoadingContext`** - both reuse the existing `registerSyntheticClass` per-mod-embed pattern; B4 also unblocks part of C3.
3. **§A A1 abstract-base model synthetics** - extends a proven bridge; the clearest §A win.
4. **§B B3 armor-layer** - medium adapter, real reported mod.
5. **§A A3/A4** - tooling + acceptance test for pre-26.1.
6. **XL items as mechanisms + initial coverage:** B6 `@Inject` re-sig, B5 render removals (TicketType first), A2 shim re-authoring (top transitions). These are explicitly "ship the mechanism, grow coverage" - they won't be 100% in one snapshot, and that's the honest framing for the changelog.

## Verification doctrine (per CLAUDE.md)

Every item: a transform-level JUnit test (host-independent, authoritative) **plus** an end-to-end launch with the real reported mod on a 26.2 (and, for §A, a pre-26.1) instance, confirming the game outlives mod-init (window/title log lines, no new crash-report). Add a regression case to the test mod for the affected loader(s).

## Honest scope statement for the snapshot.2 changelog

Snapshot.2 lands **complete**: §C JiJ-recursion, §B `DeferredSpawnEggItem` + `FMLJavaModLoadingContext` + armor-layer + candle `ColorCollection`, §A abstract-base model synthetics + pre-26.1 gap tooling. Snapshot.2 lands as **mechanism + initial coverage** (grows over the 1.2.0 rc cycle): the `@Inject` re-signaturing engine, the 26.2 render-removal adapters (`TicketType` first; `MultiBufferSource`/`Tesselator` tracked), and the intermediary-namespace legacy-shim re-authoring (top transitions first). Genuinely out of scope (documented as incompatible): full Forge→NeoForge migration of deep mods, and 26.2 dragon-fight-model mods like Better End Island.
