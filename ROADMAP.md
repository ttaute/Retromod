# Retromod Roadmap

> ⚠️ **Everything in this document is provisional and can change at any time.** Nothing here is a promise or a commitment. Items may be reordered, rescoped, deferred, or dropped entirely as real mods, real reports, and new Minecraft versions dictate. Don't treat anything below as guaranteed.
>
> Forward-looking plans only. Anything already shipped lives in the [changelog](CHANGELOG.md), not here.
> No fixed dates. Retromod ships a release when it's ready, not on a calendar.
>
> Last updated: 2026-06-29 · 1.1.0 shipped · now on **v1.2.0-snapshot.6** (in progress)

---

## Where we are

**Retromod 1.1.0 is the current stable release; 1.2.0 is now in progress on the snapshot track (`1.2.0-snapshot.6`).** It translates old Fabric, NeoForge, and Forge mod bytecode onto **MC 26.1.x and 26.2**, the maintained targets and the first unobfuscated Minecraft line. MC 26.2 ships for Fabric and NeoForge (Forge follows when its 26.2 loader does), with first-round Vulkan compatibility (prefer the OpenGL backend for old mods). The shim chain spans **MC 1.12.2 → 26.2**, third parties can extend Retromod through the [Addon API](docs/addons.md), and real-world results live in the community [Compatibility DB](https://bownlux.github.io/Retromod/compatdb/). **1.2.0 ("the general update") is the active line; its plan is below.**

For everything that already landed, see the [changelog](CHANGELOG.md). This file is only what's *ahead*.

---

## Release cadence

Minecraft-style:

- **Patch releases** (`1.0.1`, `1.0.2`, …): bug-fixes only, shipped directly with no pre-release.
- **Minor / major releases** (`1.1.0`, `2.0.0`): go through `snapshot.N` → `rc.N` → stable.

One theme per release, so a single update never mixes unrelated work.

---

## Build integrity

Official builds embed a SHA-256 of Retromod's own classes; at startup the verifier re-hashes the running jar and reports `VERIFIED` on a match, otherwise it fires a fork notice. This is an **integrity / modification check, not cryptographic anti-tamper**. There's no secret key, so a determined attacker could recompute it. For real verification, compare the jar's SHA-256 against the value published on the releases page (Modrinth / GitHub show one per file). The check is informational and never a gate (MIT); forks and source builds run identically.

---

## 1.1.0 - 26.x coverage & API bridges *(shipped)*

**Shipped as stable 1.1.0** (snapshot.N → rc.1 → stable). Theme: make the maintained 26.x path genuinely broad, measured against the top 5,000 Modrinth mods, driven by real issue reports. It delivered the full-catalog static audit and the fixes it forced, ~20 Fabric API bridges for renamed/removed events (incl. the renamed-SAM "lambda trap" class), NeoForge redirect corrections verified against real jars, the engine-level fixes the in-game passes caught (auto-devirtualize, superclass rebase, JPMS strips), the Compatibility DB, the `Tuple` polyfill + Cardinal Components package move, first-day **MC 26.2** support (Fabric + NeoForge), and the first round of Vulkan compatibility (Tier 0/Tier 2). Full details in the [changelog](CHANGELOG.md).

> The original 1.1.0 theme (old Fabric mods on **pre-26.1** hosts) moved to **1.2.0** (below), where it gets a full release's attention instead of sharing one.

---

## 1.2.0 - The general update

**Theme:** the broad catch-all after 1.1.0's 26.x-coverage line: the deep work that redirects can't do, plus the transform-engine fixes and library bridges the 1.1.0 issue stream surfaced. Not one tight theme: the unifying thread is "things a simple name-redirect can't fix." The big pillars (A, B) are the two halves of the redesigned/deleted-API problem; the rest (C) is the accumulated deeper bugs and ecosystem-library work found during 1.1.0.

### C. Transform-engine + library fixes surfaced during 1.1.0
- **forge-config-api-port `ConfigTracker` `VerifyError`.** ✅ **Fixed in 1.2.0-snapshot.2.** Root cause was *not* a redirect: ASM's `getCommonSuperClass` has to merge the two caught exception types in `ConfigTracker.loadConfig` (`IOException` ∪ night-config `ParsingException`), but `ParsingException` is JiJ-bundled and invisible to the transformer, so Retromod's fallback typed the caught value `Object` where the rethrow needs `Throwable` → `VerifyError`. `RetromodTransformer.commonSuperFallback` now returns `Throwable` when either operand is one (determined by reading class bytes, not `Class.forName`); fixed in both transform writers (`RetromodTransformer`, `FabricModTransformer`), covered by `CommonSuperFallbackTest`, and verified at the bytecode level on a real forge-config-api-port jar (the corrupt `Object` frames are now `Throwable`). Broad in reach: the port is bundled by a large number of ported mods (CoroUtil/Watut, #94 follow-up).
- **Recurse transform + metadata-patching into jar-in-jar dependencies (NeoForge/Forge)** (#95). See below.
- **Process-first (AOT) transform for NeoForge** (#95). ✅ recommendation landed in snapshot.2; see §B.
- **Load Retromod + transformed mods from a non-`mods/` folder: CurseForge-export compat (#78).** CurseForge rejects pack exports that contain jars not on CF, and both Retromod itself and its `*-retromod.jar` outputs live in `mods/`. The fix is a **custom mod-locator** (the Sinytra Connector model) that scans a Retromod-owned folder (`mods/Retromod/`) and feeds those jars into the loader during discovery.
  - **NeoForge:** ✅ **landed in 1.2.0-snapshot.1.** `com.retromod.locator.RetromodModLocator` implements `net.neoforged.neoforgespi.locating.IModFileCandidateLocator`, registered in `META-INF/services/`; FML's early-service scan (which walks `mods/` and recognises that SPI) runs it during discovery and it feeds every jar in `mods/Retromod/` into the pipeline. Compiled against compile-only SPI stubs (excluded from the jar, like the `@Mod` stub) so no NeoForge build dependency is added. Verified against loader 10.x/11.x (MC 1.21.0 → 26.2), covered by `RetromodModLocatorTest`, and ✅ **confirmed in-game on MC 26.2 NeoForge** (the test mod loaded from `mods/Retromod/`, a path the default mods-folder scanner can't see, and ran). (The locator class + service ship in every dist jar, inert on Fabric/Forge since that SPI isn't read there, so the one embedded self-hash stays uniform across loaders.)
  - **Forge:** ✅ **landed in 1.2.0-snapshot.2** (once Forge shipped a 26.2 build, forge 65.x). `com.retromod.locator.RetromodForgeModLocator` implements Forge's *different* SPI (`net.minecraftforge.forgespi.locating.IModLocator`, the `scanMods()` shape, not `IModFileCandidateLocator`), registered as a `mods/`-scanned service (Forge's `ModDirTransformerDiscoverer` walks `mods/` for it, the analogue of NeoForge's early-service scan). Building the jar→`IModFile` goes through Forge internals, so it reflection-delegates to Forge's own `ModsFolderLocator` (fmlloader is an automatic module → `setAccessible` works) and soft-fails to a no-op if that class ever moves, so it can never disrupt Forge discovery. Compile-only forgespi stubs (excluded from the jar) + `RetromodForgeModLocatorTest`; `build-all.sh` now builds Forge `+26.2`. ✅ **Confirmed in-game on Forge 26.2:** Forge's locator list shows "retromod folder locator", it offered the jar from `mods/Retromod/` (a path the normal scanner ignores), and the test mod loaded and passed 7/7.
  - **Fabric:** ✅ **landed in 1.2.0-snapshot.1** (verified against fabric-loader 0.16.14: finders are hardcoded in `FabricLoaderImpl.setup()`, no ServiceLoader hook, and PreLaunch runs after Knot's scan, so no in-place locator is possible). Two paths now ship: **(1, default)** `RetromodPreLaunch.drainReadyModsFolder` moves the loader-ready jars from `mods/Retromod/` into `mods/` and fires the existing one-time restart prompt (works in any launcher incl. CurseForge, no JVM arg); **(2, opt-in)** `-Dfabric.addMods=<gamedir>/mods/Retromod` loads them in-place with no restart, and `fabricAddModsCovers` detects this and skips the drain so the two don't collide. Covered by `RetromodPreLaunchCfFolderTest`, and ✅ **confirmed in-game on MC 26.2 Fabric** (PreLaunch created the folder README, and the test mod was moved `mods/Retromod/` → `mods/`). The premain Java-agent variant (sets `fabric.addMods` before Knot, bundles AOT) is a possible later enhancement.
  - **Distribution: Retromod ships directly to both Modrinth and CurseForge.** The per-loader jars are published to both (CurseForge via the `publish-curseforge.yml` workflow). A CurseForge pack depends on the real Retromod (whose engine already carries the `mods/Retromod/` locators above) and the transformed `*-retromod.jar` outputs ride in `mods/Retromod/` as CF pack **overrides** (arbitrary bundled files, which CF export allows). (An earlier thin "Retromod Loader" stub, a locator-only CF jar with the full engine staying Modrinth-only, was built in snapshot.2 then dropped once Retromod was published to CurseForge directly, which supersedes it.)

### A. Pre-26.1 Fabric bridge (#55)

**Why:** on a 26.1+ host the intermediary→Mojang translation carries old Fabric mods. On a **pre-26.1** host that remap is (correctly) off (the runtime exposes Minecraft under *intermediary* names) and the legacy Fabric shims were authored in the wrong namespace (MCP/Yarn), so they never fire on real distributed mods. Simple content mods still work because intermediary names are stable; anything touching an API the game **changed, removed, or redesigned** breaks. The clearest case: custom entity models/renderers from ≤1.16 (the model system was rebuilt in 1.17).

**Two engines (current architecture).** §A is now two complementary mechanisms, both gated to pre-26.1 Fabric:
- **Generic:** `FuzzyMethodResolver` indexes the host MC jar (methods + fields + class hierarchy) and scores candidates by class/name (Levenshtein, substring, prefix)/param similarity, auto-applying above a strict threshold (85) and logging probable-but-unapplied matches (50-85). This catches *renames/moves the host still has under a similar name*, no per-API hardcoding.
- **Specific:** `Pre<version><API>Bridge` classes for *shape changes the fuzzy resolver can't bridge* (a value/owner/signature change where the name survives). Four exist and are host-introspecting (reflective, so they no-op where the legacy shape is intact): pre-1.17 model (`Pre1_17ModelBridge`, concrete + abstract-base `class_4592/3/5` synthetics, render path, superclass rebase, #70), 1.21.2 `InteractionResult` enum→sealed, 1.20.5 `Identifier` ctor→factory, 1.18.2 `Biome.Category` removal. Registered in `RetromodPreLaunch` under `!isUnobfuscatedTarget`; 20 acceptance tests (synthetic intermediary fixtures) guard the transform logic.

**The next increments are data-driven, not speculative.** A new specific bridge needs *that mod era's* intermediary names: the bundled `intermediary-to-mojang.tsv` is **1.21.4 only**, and intermediary IDs get reassigned across the big refactors (verified: `class_2585` is `PlainTextContents$LiteralContents` in 1.21.4, **not** the pre-1.19 `LiteralText`), so guessing an old-mod bridge from the 1.21.4 table produces a wrong redirect. The correct workflow is: run a real failing pre-26.1 mod on a pre-26.1 instance, let the fuzzy resolver's 50-85 log + the descriptor-aware gap report name the exact unbridged `(owner, name, descriptor)`, then author the bridge against that.

**Remaining (the release's actual work):**
1. **Batch-test real old Fabric content mods on a pre-26.1 instance** (e.g. 1.20.1): the theme's actual acceptance test, and the source of the data that drives 2-3. (Done already: abstract-base model synthetics, #69; the descriptor-aware gap report, #71.)
2. **Per-era intermediary data** for the mod versions under test, so old-mod bridges can be authored correctly (the 1.21.4 table alone is insufficient for pre-1.19 mods).
3. **Author specific bridges** for the shape changes the in-game pass surfaces, and **prune the legacy MCP/Yarn shims** (1.14-1.20) that never fire (dead weight on 26.1, no-ops on pre-26.1) once their genuine breakages are re-expressed as bridges.

**Guardrails:** every bridge gated on `isUnobfuscatedTarget(host) == false && loader == fabric`; a regression test asserts the 26.1 Fabric output is byte-identical before/after, so the maintained path can't regress.

### B. Deep-API-change polyfills

The mods that **construct and load far** but die on an API a later version *redesigned or deleted*, not renamed. These need hand-written polyfills that reshape old calls onto the new API. Known targets (see [Mods That Can't Be Translated](docs/incompatible-mods.md)):

- **Armor-layer rendering**: `ArmorMaterial$Layer` → `EquipmentClientInfo`, a different API, not a rename (#51).
- **`DeferredSpawnEggItem`**: deleted by NeoForge; there's no class to redirect to (#52).
- **Forge mod on a NeoForge host**: a 1.20.1 Forge mod on modern NeoForge is the full Forge→NeoForge migration (registries → `BuiltInRegistries`, the mod-event-bus idiom, events, data gen). Retromod already gets it *scanned*; loading it is the deep work. The embed-an-ASM-bridge approach is scoped in `CLAUDE.md` (#85, and the #87 follow-up).
- **Forge mod on a Forge 26.2 host (EventBus 6→7)** (#85): Forge 26.2 (Forge 65.x) dropped `net.minecraftforge.eventbus.api.IEventBus` for EventBus 7's `BusGroup` + per-type `EventBus<T>`, so every old Forge mod dies at construction on `FMLJavaModLoadingContext.getModEventBus()` (verified: Macaw's Bridges 1.21.1). The whole constructor chain breaks (`getModEventBus`, `IEventBus.addListener`, `DeferredRegister.register(IEventBus)`, `MinecraftForge.EVENT_BUS`), so the fix is an EventBus 6→7 compat layer (a synthetic `IEventBus` embedded per-mod, delegating to `BusGroup`, plus the `@SubscribeEvent` annotation move and `DeferredRegister.register` adaptation), as deep as the Forge→NeoForge case. Until it lands, run old Forge mods on **Forge 26.1.x** (still EventBus 6) rather than 26.2.
- **Process-first (AOT) transform for NeoForge.** ✅ **Guidance landed in 1.2.0-snapshot.2.** On NeoForge the runtime transform runs at mod-constructor time (*after* the module layer is built), so module-layer failures (split packages, skipped Forge-named jars) crash before Retromod can act (#95). When the NeoForge entry transforms mods *in place*, it now recommends running the offline/AOT transform once (`retromod prepare <minecraft-dir>` / `retromod batch mods/`), which processes jars *before* the loader scans, removing that whole "too late" class. The offline tooling already existed (`prepare`/`batch` + the Full AOT cache); this makes it the **recommended** NeoForge flow. *Still future:* auto-preferring a pre-built AOT cache at runtime over the in-place pass. Pairs naturally with the JiJ-recursion work below. **Also fixed in snapshot.2:** the offline transform (`batch` + AOT) now applies the full redirect set (vanilla class moves, loader-matched API shims, and Fabric-gated member mappings), matching the single-mod `transform` and an in-game boot; it previously registered only the shim chain, so AOT-prepped mods silently kept pre-26.x class names (a mixin `@Shadow`/`@Inject` of a relocated class never got remapped).
- **Recurse transform + metadata-patching into jar-in-jar dependencies (NeoForge/Forge).** ✅ **Done in snapshot.2 (§C).** Retromod now recurses its full transform (bytecode rewrite + metadata relaxation) into `META-INF/jars/` (Fabric) and `META-INF/jarjar/` (NeoForge/Forge) libraries, and into jars nested inside those (depth-capped, soft-failing per jar), across the offline `transform`/`batch` + AOT compiler and the Fabric + NeoForge/Forge runtime paths. A bundled Forge-built lib on a NeoForge host now also gets the `mods.toml`→`neoforge.mods.toml` promotion, so it's scanned instead of reported "missing" (#95). Regression-tested (`NestedJarRecursionTest`). *Note:* full Forge→NeoForge **translation** of a nested Forge lib still needs the Forge→NeoForge work above; this gets it scanned + bytecode-relocated, which is the #95 fix.
- **The 26.2 render-API removals**: `MultiBufferSource` (one of the most referenced types in modding) and the old `Tesselator`/`VertexFormat` vertex layer. Redirects can't cover deletions; these need adapter polyfills.
- **Chunk force-loading: `ServerChunkCache.addRegionTicket` (1.18-era chunk mods, e.g. Chunky).** ✅ **Built in 1.2.0-snapshot.6.** 26.1 renamed `addRegionTicket(TicketType, ChunkPos, int, T value)` (4 args) to `addTicketWithRadius(TicketType, ChunkPos, int)` (3 args) **and dropped the value arg**, so an old chunk mod's 4-arg call had no target on 26.1 and crashed worlds on load. Added a generic **arg-dropping method redirect** to the transformer (`registerArgDropMethodRedirect`: pops the dropped trailing value, then calls the shorter renamed method; needed wiring into both the per-class transform guard and the method-visitor wrap guard, and the redirect keys on the post-ClassRemapper Mojang names the visitor sees). Registered the chunk-ticket case in the Fabric 26.1 shim, verified on the real Chunky jar (`FabricWorld` now calls the 3-arg `addTicketWithRadius`), corpus byte-identical. *Remaining:* Chunky also mixins `ThreadedAnvilChunkStorage`, so full Chunky compatibility still needs that checked in-game; and the redirect is Fabric-26.1 only so far (generalize to NeoForge/Forge chunk mods + verify in-game).
- **26.2 removed-field polyfills (block fields → `ColorCollection`).** ✅ **Done in snapshot.2.** 26.2 collapsed the 16 per-color fields of 4 families (candles, candle cakes, shulker boxes, terracotta; 64 `Block` fields: `Blocks.WHITE_CANDLE`, …) into single `Blocks.DYED_<FAMILY>` `ColorCollection<Block>` fields indexed by `DyeColor`. A 1.21.x mod referencing `Blocks.WHITE_CANDLE` hits `NoSuchFieldError` on 26.2; it's a field-access → accessor-call rewrite (`getstatic WHITE_CANDLE` → `DYED_CANDLE.pick(DyeColor.WHITE)` + `checkcast Block`), not a rename, so it needed a new `registerStaticFieldAccessor` transform mechanism. The 26.1→26.2 shim registers all 64; only fires when targeting 26.2 (the fields still exist on 26.1). Found via **YUNG's Extras** (`SwampFeatureProcessor`). *Future:* reuse the same mechanism for other removed-static-field → accessor cases as they surface.
- **Mixin `@Inject` handler re-signaturing for changed target signatures** (#48-class). When MC drops/adds params to a method an old mod `@Inject`s into, Mixin rejects the captured-arg descriptor ("Expected (…) but found (…)"). Today these soft-fail via the blocklist (feature inert) or block construction when the handler is interdependent. A proper fix rewrites the handler descriptor + local indices to the new signature, but it only helps when the handler *body* still links; if the body calls APIs the new version also removed or redesigned, re-sig is not enough. **YUNG's Better End Island is exactly that limit:** its `ServerLevel.<init>` `@Inject` hits the 26.2 param-drop (`ChunkProgressListener` + `RandomSequences`), but the body reconstructs the dragon fight the old way, and 26.2 *redesigned* `EnderDragonFight` wholesale (new record-style constructor, `WorldData.endDragonFightData()` removed, `EndDragonFight$Data` removed). So Better End Island is a deep 26.2 redesign (it works on **26.1**), not a re-sig case. See [Mods That Can't Be Translated](docs/incompatible-mods.md). Re-sig remains a general future mechanism for the cleaner case (descriptor changed, body intact), to be built when a mod that actually benefits surfaces.

### D. 1.12.2 (pre-1.13) Forge baseline (#103/#108)

Old 1.12.2 Forge mods are the largest legacy library, so they are worth a baseline. Scoped precisely from Metallurgy 4 (#103): of its 344 transform issues, **308 are class moves** (90%), 22 methods + 14 fields are 1.12.2 SRG names, and of the 297 missing classes **72 are third-party mod APIs** (Tinkers, JEI, …, optional deps, not Retromod's job), ~73 are **redesigned Minecraft systems** (entity AI Goal/Brain, the `RenderSystem` rendering rewrite, the packet system, worldgen) that no redirect can bridge, and the rest are content classes.

- **Class-move table (done, increment 1).** `src/main/resources/retromod/forge-1.12.2-class-moves.tsv` (117 entries, every target validated against the 26.1 jar) is loaded by `Forge_1_12_2_to_1_13_2`, gated to a 26.1 host, after the chain redirects so its final 26.1 names win. Covers the core content classes (Block/Item/ItemStack/Entity, `World`->`Level`, `BlockPos`->`core`, `ResourceLocation`->`Identifier`, NBT, containers, …). Harvest script: `scripts/harvest-1.12.2-class-moves.py`. Transform-tested (`Forge1122ClassMovesTest`); the Fabric corpus stays byte-identical (correctly scoped).
- **Block/Item Properties-constructor bridge (done, increment 2, #80).** After the class moves, the block/item still has to *construct*: 26.1 made `Block`/`Item` Properties-constructed, so a 1.12.2 `super(Material)` / `super()` has no matching ctor (and `Material` was removed). New transformer mechanisms: a super-ctor **replace** (`registerSuperConstructorReplace`, pop old args + push a default `Properties`) turns `super(Material)` -> `super(BlockBehaviour.Properties.of())`; the insert-defaults path now constructs a real default for `Properties` types so `super()` -> `super(new Item.Properties())`; and a static-field **nuller** (`registerStaticFieldNuller`) neutralizes reads of removed-class constants (`Material.IRON`). Default properties only, but it constructs. Gated to a 26.1 host. Transform-tested (`Forge1122ClassMovesTest`).
- **What's done is transform-level only; 1.12.2 mods do not load in-game yet.** The class-move table and ctor bridge are unit-tested bytecode building blocks, but a 1.12.2 mod won't even be *scanned* by modern Forge/NeoForge, so none of it runs in-game. The in-game prerequisites, in order:
  1. **`mcmod.info` -> `mods.toml`/`neoforge.mods.toml` metadata generation** (the blocker, found while testing snapshot.6). 1.12.2 mods ship only `mcmod.info` (the toml format postdates 1.13); the runtime Forge path doesn't synthesize a toml from it, so the loader skips the mod entirely. `LegacyModSupport` has generation logic but is wired into the CLI only, not the runtime `ForgeModTransformer`. This must land first or nothing else matters.
  2. **The 1.12.2 SRG member namespace** (`func_*`/`field_*` -> 26.1 Mojang, a separate harvest from MCP 1.12.2 with no authoritative cross-version source).
  3. **The 1.12.2 `GameRegistry`/`@EventHandler`/`FMLPreInitializationEvent` registration idiom** (the pre-1.13 Forge lifecycle, distinct from the 1.20.1 Forge->NeoForge migration).
  4. A **1.20.1-target** class-move table (the reported mods run on 1.20.1, where `SwordItem`/`ResourceLocation`/`UseAnim` still exist but 26.1 removed them) which needs a deobfuscated 1.20.1 mapping to validate.
- **Limit:** this baseline carries **simple content mods** (blocks/items/recipes) much further, but the reported complex mods (Metallurgy 4, Scape and Run: Parasites) need the redesigned AI/render/network systems + their third-party deps, i.e. effectively the full Forge 1.12.2->1.20 migration. They stay on [Mods That Can't Be Translated](docs/incompatible-mods.md) for now.

---

## Rendering & the OpenGL → Vulkan transition (26.2 → 26.3)

MC **26.2** added a Vulkan rendering backend (`com.mojang.blaze3d.vulkan`) alongside the existing OpenGL one (`com.mojang.blaze3d.opengl`) and made **Vulkan the default**, chosen via `net.minecraft.client.PreferredGraphicsApi` from `options.txt`'s `preferredGraphicsBackend` key. MC **26.3 is expected to remove OpenGL entirely** (Apple has deprecated desktop GL; the wider ecosystem is moving to Vulkan/Metal). This section is the standing strategy for that transition.

**The non-goal, stated plainly:** Retromod will **not** attempt to translate arbitrary OpenGL bytecode into Vulkan. That is writing a GL driver in a transformer (GL is a huge stateful machine; Vulkan is explicit pipelines/descriptor-sets/SPIR-V): infeasible and not how the problem is solved anywhere. The industry answer is a GL-on-Vulkan driver (**Zink**) and, on Apple, Vulkan-on-Metal (**MoltenVK**): driver-level, not per-mod. Importantly, **Zink does *not* help on 26.3**: once Mojang removes MC's GL backend, MC is no longer a GL app, so a single mod's GL calls have no MC pipeline to ride and can't share MC's Vulkan swapchain. So there is no general rescue for raw-GL mods on a GL-less runtime.

**What "OpenGL code" means matters.** Most modern mods use Minecraft's *own* backend-agnostic render API (`RenderSystem`, `RenderPipeline`, `VertexConsumer`, render layers); these already run on Vulkan with zero work. Only mods making **raw** GL calls (`org.lwjgl.opengl.GL11.*`), removed imperative state, immediate-mode geometry, or hand-bound GLSL are affected.

**VulkanMod as a forecast.** [VulkanMod](https://github.com/xCollateral/VulkanMod) (LGPL-3.0) is a third-party mod that reimplements *Minecraft's own* rendering on Vulkan, i.e. what Mojang now does natively in 26.2, so it's not a forward path for us (redundant/conflicting on 26.2+, and we can't absorb LGPL into MIT, so reference only). But it is a **live preview of GL-less Minecraft**: the mods that break under VulkanMod today are the mods that will break on 26.3. We seed the "Vulkan-incompatible" list from its known breakage and use it to prioritize.

**Sodium 0.9.0 as a reference (not a polyfill source).** Sodium's 26.2 release added **early Vulkan support**, so it drives Minecraft's new `GpuDevice`/`RenderPipeline` abstraction across *both* GL and Vulkan backends, making it a useful **clean-room study of that SPI**, which is exactly what Piece 1 below (re-adding the GL backend) hinges on. Its Vulkan-incompatible-mods list is a second **forecast** of the 26.3 breakage surface (alongside VulkanMod's). Two caveats: (1) it's **reference only**, since Sodium is under the **PolyForm Shield License 1.0.0**, a source-available *noncompete* license (not OSI-open), so we read and learn, we don't lift code; (2) **it is *not* a polyfill source**, since Sodium uses modern render APIs, not the removed ones old mods break on, so diffing Sodium-GL vs Sodium-Vulkan won't surface old-mod gaps. The authoritative "what changed" comes from diffing the **MC jars themselves** (`scripts/harvest-mc-diff.py`), not a third-party renderer.

### Shipped in 1.1.0
- **Tier 0: prefer the OpenGL backend for old mods (the 26.2-window fix).** On a 26.2+ client, `GraphicsBackendCompat` writes `preferredGraphicsBackend:"opengl"` to `options.txt` so translated old mods' GL rendering keeps working. It is **non-destructive** (only when the user hasn't chosen a backend; an explicit `vulkan` choice is respected with a warning) and opt-out via `-Dretromod.graphics.noPreference=true`. This buys time: it makes 26.3, not 26.2, the cliff.
- **Tier 2: neutralize removed imperative render-state calls (soft-fail).** The `RenderSystem` state setters (`enableBlend`/`blendFunc`/`depthMask`/…) were deleted in the blaze3d GpuDevice/RenderPipeline refactor (gone by 1.21.11) with no method to redirect to. `RemovedRenderStateNeutralize` + the transformer's `registerRemovedMethodNeutralize` drop those calls (pop args, push default return) so old mods **load and run** instead of `NoSuchMethodError`-ing; the state is inert (soft-fail), not translated.

### The 26.3 plan (when OpenGL is removed)
| Bucket | Mods | Outcome |
|---|---|---|
| **A. Modern render API** | RenderSystem / VertexConsumer / render layers | ✅ Native on Mojang's Vulkan, no work. |
| **B. Removed-state / mechanical** | imperative GL state | ⚙️ Soft-fail neutralization (Tier 2), grown from reports. |
| **C. Raw GL / custom shaders / immediate-mode** | custom renderers, `GL11.*`, `glBegin/glEnd`, `Tesselator` | ❌ Hard boundary: hand-port (deep, per-mod) or list as Vulkan-incompatible. Same boundary VulkanMod hits. |

Concrete 26.3 work: grow the Tier-2 neutralization set from real reports; build adapter polyfills for the highest-value deletions where a *semantic* shim is possible (e.g. immediate-mode `Tesselator`/`VertexFormat` → modern `MeshData`/`BufferBuilder`, scoped under §B above); maintain the seeded Vulkan-incompatible list; and document the boundary honestly so users aren't promised a translator.

#### Candidate architecture (evaluate when 26.3 ships): re-add the OpenGL backend, run it on Zink where needed

The honest answer for "OpenGL is gone" is **not** to translate GL→Vulkan call-by-call (that's writing a GL driver, infeasible; see the non-goal above) and **not** to bundle a "GL→Vulkan translator mod" (no such thing exists; the one renderer mod that does, VulkanMod, replaces MC's renderer and breaks the same mods; see [Rendering & the OpenGL → Vulkan transition](#rendering--the-opengl--vulkan-transition-262--263)). The workable inversion is to **give the game back the OpenGL path Mojang removed.** "OpenGL" here is two separable layers:

```
MC rendering (RenderSystem / GpuDevice)
  └─ (1) MC's OpenGL backend  ── the Java classes com.mojang.blaze3d.opengl.*  (26.3 deletes these)
        └─ LWJGL GL bindings
              └─ (2) libGL  ── the native driver that runs glXxx on the GPU
                    • normal PC: the system OpenGL driver (still present for years after MC drops its backend)
                    • GL-less machine (Apple post-GL; Vulkan-only Linux): Zink → Vulkan → (Mac) MoltenVK → Metal
```

**Piece 1: re-add MC's OpenGL backend as a polyfill (the big win, Retromod-shaped, pure Java).** Re-embed 26.2's `com.mojang.blaze3d.opengl` backend classes (≈64) so a 26.3 client can still select OpenGL, then let Tier 0 pick it. Once the backend is back, the **whole game** renders through GL again, so old GL-using mods get a real GL context. For the **majority of users this is the entire fix**: Windows/Linux keep a system OpenGL driver long after Mojang drops MC's backend, so Piece 2 isn't needed there. **Key risk:** this links *only if* 26.3 keeps the `GpuDeviceBackend` SPI those classes implement stable; if the SPI is refactored alongside the GL removal, the re-added backend won't compile against it. Undecidable until 26.3's render SPI is visible ("decide on arrival").

**Piece 2: Zink (+ MoltenVK on Mac) as the native libGL, for GL-less machines only.** Needed only where the OS has *no* OpenGL at all (Apple after GL removal, Vulkan-only Linux). Because Piece 1 makes the whole game a GL app again, a GL-on-Vulkan driver like **Zink** works here as a normal libGL (this is the standard "GL app on a Vulkan-only box" stack; PojavLauncher does exactly this): the earlier worry that "Zink can't help one mod inside a Vulkan host" doesn't apply once the *entire* renderer is GL. Licensing is shippable: **Zink/Mesa is MIT, MoltenVK is Apache-2.0** (unlike VulkanMod's LGPL). The real costs:
- **Native + per-platform + heavy:** Mesa/Zink for Win/Linux/Mac × x64/arm64, plus MoltenVK on Mac (Mac stack stacks two layers: GL→Zink→Vulkan→MoltenVK→Metal). Tens of MB and a cross-platform native maintenance/debug surface.
- **It's a launcher/agent layer, not a mod feature.** LWJGL picks its `libGL` very early in `Minecraft.<init>`, before a NeoForge mod constructor runs (the same too-late timing Tier 0 hit). Pointing LWJGL at Zink means setting `org.lwjgl.opengl.libname` *before* GL initializes, i.e. **premain (Retromod's Java-agent mode) or the launcher**, not normal mod init. So Zink wants to be delivered via the agent/launcher path, not baked into the mod jar.
- **Performance:** several stacked translation layers; fine for content mods, not ideal for perf-sensitive setups.

**Phasing.** (1) re-add the GL backend first, a pure-Java polyfill, covers most users, gated on the 26.3 SPI; (2) Zink/MoltenVK as a *separate, optional* native layer for GL-less machines, delivered via agent/launcher. This is effectively a mini GL-compat distribution at **1.3.0+-scale**, and nothing here is buildable until 26.3's render SPI is actually visible, so it stays designed-not-built for now.

> Follow-up cleanup: the old speculative `RenderingBackendShim` / `RenderingCompat` scaffolding (unregistered, pointed at never-shipped symbols like `VulkanStateManager`) is superseded by the above and should be removed; `EnvironmentDetector`'s backend detection should be re-pointed at the real 26.2 API (`PreferredGraphicsApi` / `RenderSystem.getBackendDescription()`).

---

## 1.3.0 - Mixin translation

**Theme:** stop *soft-failing* mixins and actually translate them. Today, when a mod's mixin targets an MC method whose signature changed, or uses MixinExtras inner annotations against renamed classes, Retromod can only **strip** the handler (the feature goes inert) via the blocklist. This release makes those mixins work:

- **Re-signature `@Inject` / `@Redirect` handlers** when the target MC method's parameters changed, e.g. `CompoundTag` → `ValueOutput`/`ValueInput` (the 1.21.5 ValueIO refactor, #48). Rewrite the captured types (and, where mechanical, the handler body) instead of stripping.
- **Rewrite MixinExtras inner targets** (the `target = "net/minecraft/…"` strings inside `@ModifyExpressionValue` / `@WrapOperation`, and `@Shadow` field names) against the host's renamed/moved classes (this inner-target rewriter is currently incomplete).
- Shrink the mixin blocklist as these become real translations rather than soft-fails.

---

## Ongoing (any release)

- **More shims, polyfills, and mappings:** the long tail of per-mod and per-version fixes. Much of this can now live in community **[addons](docs/addons.md)** instead of core.
- **SRG → Mojang dictionary** growth, so more old Forge mods load: an [easy first contribution](docs/srg-mappings.md).
- **New MC versions on release day:** the 26.2 prep set the pattern: harvest the real client jars (`scripts/harvest-mc-diff.py`), verify, ship the shim before the release lands.

---

## Later / not started

- **Plugin support** (Bukkit / Spigot / Paper / Purpur). Plugins aren't mods: different APIs (the Bukkit API and NMS) and a different translation problem. A real future direction, but not started. See the [FAQ](docs/faq.md).

---

## How to help

- **Report how mods run:** the [Compatibility DB](https://bownlux.github.io/Retromod/compatdb/) has a form that pre-fills everything; a bot turns it into a database entry.
- **Write an addon:** ship shims or polyfills for specific mods: [Writing an Addon](docs/addons.md).
- **Add SRG mappings:** [the easiest first contribution](docs/srg-mappings.md).
- **File issues with the full `latest.log`:** that's what turns a broken mod into the next fix.

---

*Plans change with what real mods need. Shipped history is in the [changelog](CHANGELOG.md).*
*Maintained by Bownlux.*
