# snapshot.3 audit targets

From the full 5000-mod compatibility audit (snapshot.2 jar, corrected 26.1.2 manifest).
Overall: 2658/5000 = **53.2%** translate clean. 2283 MC_GAP, 751 SKIP (libs/incompatible), 59 transform-fail.

The list below is the top sole-blockers (fix one class → that many mods turn clean),
each tagged with whether it's a safe redirect or needs a real bridge. The recurring
trap: a removed/renamed Fabric API **functional interface** can't be fixed with a
class redirect, because a mod's lambda hardcodes the old SAM via `invokedynamic` and
the metafactory crashes if the target interface's SAM differs. Those need a synthetic
interface that keeps the old SAM plus an adapter onto the new API (the
ClientPlayNetworking pattern) and must be verified with an in-game launch.

## Verified this pass

| Class (old) | mods | 26.1 reality | Verdict |
|---|---|---|---|
| `command/v1/CommandRegistrationCallback` | 38 | exists as `command/v2/...` but the SAM changed `register(CommandDispatcher, boolean)` → `register(CommandDispatcher, CommandBuildContext, Commands$CommandSelection)` | **NOT a plain redirect.** False win. Needs a synthetic v1 interface (2-arg SAM) + adapter that registers a v2 callback ignoring the new context arg. Bridge + in-game verify. |
| `itemgroup/v1/ItemGroupEvents$ModifyEntries` | 83 | renamed to `creativetab/v1/CreativeModeTabEvents$ModifyOutput`; functional interface, SAM differs | **NOT a plain redirect.** False win (lambda trap). Needs synthetic SAM bridge + in-game verify. |
| `client/networking/v1/ClientPlayNetworking$PlayChannelHandler` | 59 | removed raw-packet API | Already scoped (task #51): synthetic SAM + reflective bridge, needs in-game verify. |

## Fixed in snapshot.3 (this pass)

Verified against the real **fabric-api 0.145.4+26.1.2** (JIJ-extracted, javap'd), not just class-name presence.

| Class | mods (sole) | What it actually was | Action |
|---|---|---|---|
| `ClientTickEvents$EndWorldTick` (+ `$StartWorldTick`, `ServerTickEvents` pair) | ~10 | 26.1 renamed the nested tick SAMs `$*WorldTick`→`$*LevelTick`; the SAM method (`onStartTick`/`onEndTick`) is **unchanged**, only the `ClientWorld`/`ServerWorld`→`Level` param (which the harvest remaps). The redirect existed only in the 1.16.5→1.17 shim, whose chain a 1.19-1.21 mod never hits. | **Safe inner redirect added to the 26.1 Fabric shim.** Not a lambda trap (SAM name identical). Shipped. |
| `client/command/v2/ClientCommandManager` | 10 | Renamed to `ClientCommands` (same static `literal`/`argument`/`getActiveDispatcher`). | **Already redirected** (`Fabric_1_21_11_to_26_1:500`). The earlier "still to classify" entry was stale frequency data, not a real gap. |
| `command/v1/CommandRegistrationCallback` | 38 | v1 2-arg `register(CommandDispatcher, boolean)` → v2 3-arg + `CommandSelection`. | **BRIDGE BUILT this pass** (`FabricCommandV1Shim` + `CommandRegistrationV1Bridge`): synthetic v1 interface (SAM + `EVENT`), `<clinit>` wires `EVENT` to v2 reflectively, `dedicated ← selection==DEDICATED`. Bytecode tested; reflective wiring needs a launch. |
| `itemgroup/v1/ItemGroupEvents$ModifyEntries` (+ holder, `ModifyEntriesAll`) | 83 | SAM `modifyEntries`→`modifyOutput`, holder `modifyEntriesEvent`→`modifyOutputEvent`, entries mutators `addAfter`/`addBefore`→`insertAfter`/`insertBefore`. | **BRIDGE BUILT this pass** (`FabricItemGroupEventsShim` + `ItemGroupEventsBridge`): synthetic holder + 2 SAM interfaces wired to `creativetab/v1` reflectively, + 24 method renames (descriptors verbatim from fabric-api 0.145.4). Bytecode **and** the class-redirect↔method-rename composition tested (transform test); reflective event wiring needs a launch. |

**Mechanism note (important for the next pass):** `registerClassRedirect("A","B")` maps the exact name `A` only. It does **not** propagate to `A$Inner`. Most remaining audit blockers are *inner* SAMs whose *outer* is already redirected (e.g. `ServerWorldEvents`→`ServerLevelEvents` is shipped, but `ServerWorldEvents$Load` is not). So each inner needs its own entry **and** a SAM check: same SAM method name → safe inner redirect; renamed SAM → lambda trap → bridge.

## Classified this pass: confirmed lambda traps (need a bridge, NOT a redirect)

| Class | mods (sole) | SAM evidence (javap, fabric-api 0.145.4) | Bridge difficulty |
|---|---|---|---|
| `itemgroup/v1/ItemGroupEvents$ModifyEntries` | 83 | `ItemGroupEvents` gone; `CreativeModeTabEvents.modifyOutputEvent(...)`; SAM `ModifyEntries.modifyEntries(FabricItemGroupEntries)` → `ModifyOutput.modifyOutput(FabricCreativeModeTabOutput)`. Both the event method AND the lambda's parameter object changed. | **Hard:** the lambda body calls methods on the changed parameter object, so the synthetic `FabricItemGroupEntries` must adapt onto `FabricCreativeModeTabOutput`. |
| `command/v1/CommandRegistrationCallback` | 38 | v1 SAM `register(CommandDispatcher, boolean)`; v2 SAM `register(CommandDispatcher<CommandSourceStack>, CommandBuildContext, Commands$CommandSelection)`. | **Clean:** v1 lambda body uses only the stable brigadier `CommandDispatcher` + a boolean, so no parameter-object adaptation. Synthetic v1 interface (2-arg SAM + `EVENT`) whose `<clinit>` wires a v2 forwarder; `boolean dedicated` ← `selection == DEDICATED`. Best next bridge to build. |
| `client/networking/v1/ClientPlayNetworking$PlayChannelHandler` | 59 | raw-packet API removed | task #51, written, needs in-game launch. |
| `event/lifecycle/v1/ServerWorldEvents$Load` (+ `$Unload`) | 21 | holder + SAM renamed `onWorldLoad`/`onWorldUnload` → `onLevelLoad`/`onLevelUnload`; only param change is `ServerWorld`→`ServerLevel` (harvest). | **BRIDGE BUILT this pass** (`FabricServerWorldEventsShim` + `ServerWorldEventsBridge`): synthetic holder (LOAD/UNLOAD) + `$Load`/`$Unload` SAMs wired to `ServerLevelEvents` 1:1. Replaced the old straight redirect. Bytecode tested; reflective wiring needs a launch. |
| `client/rendering/v1/EntityModelLayerRegistry$TexturedModelDataProvider` | 18 | provider SAM renamed `createModelData` → `createLayerDefinition`. **NOT a return-type change:** Yarn `TexturedModelData` = Mojang `LayerDefinition` = intermediary `class_5607` (verified in the mapping), so the harvest fixes the body. (My earlier "hard, return-type change" note was wrong.) | **BRIDGE BUILT this pass** (`FabricEntityModelLayerShim` + `EntityModelLayerRegistryBridge`): synthetic holder + `TexturedModelDataProvider` SAM; `registerModelLayer` wraps the old provider as the new one (return-forwarding). Replaced the old straight redirect. Bytecode tested; reflective wrap needs a launch. |

## Still to classify (next pass)

| Class | mods (sole) | Note |
|---|---|---|
| `ColorProviderRegistry` | 13 | successor is `BlockColorRegistry` (static methods) but old idiom is `ColorProviderRegistry.BLOCK.register(...)` (field + virtual): structural change, likely a small bridge not a redirect |
| `BlockEntityType$Builder` | 13 | MC vanilla: `BlockEntityType.Builder.of(...)` factory shape changed in 1.21.x; check |
| `ScreenEvents$AfterRender` / `HudRenderCallback` | 7 / 6 | event SAMs; `HudRenderCallback` is gone from the host entirely, so find successor before classifying |
| `FuelRegistry` | 8 | `FuelRegistryEvents`→`FuelValueEvents` shipped; check the bare `FuelRegistry` successor (`FuelValues`?) |
| `FabricModelPredicateProviderRegistry` | 8 | registry, check |
| `FabricStructureBuilder` | 5 | removed builder |
| `ConventionalItemTags` | 5 | `tag/convention/v1`→`v2`; tag fields, but some were renamed in the v1→v2 move, so a blanket redirect risks `NoSuchFieldError` and it needs per-field entries |

## Deep removals (NOT fixable by redirect: documented, deferred)

`InteractionResultHolder` (170 mods, folded into sealed `InteractionResult`),
`MobType` (131, getter removed), `ItemRenderer` (128, render-pipeline rewrite),
`ArmorItem`/`Tier` (constructor/type-kind changes), `TextureSheetParticle`,
`BakedModel`, `World` (intermediary leftover). These need the affected mod feature
re-authored. They pass a static class check but crash at runtime, so a stub is a
false win. See incompatible-mods.md.

## Research pass (Fable 5 session): full classification

Sources: the **official Fabric API 26.1 migration map** (139 class entries at
`docs.fabricmc.net/develop/porting/fabric-api` + its XML) and a **NeoForge 1.21.11↔26.1.x
branch diff**, each claim re-verified locally against the real jars
(fabric-api 0.92.7 / 0.141.1 / 0.145.4; neoforge-26.1.2.60-beta-universal;
minecraft-26.1.2-client) before anything shipped.

### Shipped this pass
| Item | mods | What |
|---|---|---|
| HudRenderCallback | 6 | **extends+default bridge** (synthetic extends `hud/HudElement`, default `extractRenderState`→`onHudRender`, invoker attached via `HudElementRegistry.addLast`), gated 26.1+ |
| ConventionalTags v1→v2 | 5 | 6 holder class redirects + 19 verified field renames; v2-presence-probed gate |
| Particle API renames | - | `ParticleFactoryRegistry`(+inner, SAM `create` unchanged = safe), `FabricSpriteProvider`→`FabricSpriteSet`, `ParticleRendererRegistry`→`ParticleGroupRegistry`, `FabricBlockStateParticleEffect`→`FabricBlockParticleOption` |
| Misc Fabric renames | - | `AtlasSourceRegistry`→`SpriteSourceRegistry`, `ComponentTooltipAppenderRegistry`→`ItemComponentTooltipProviderRegistry`, `PointOfInterestHelper`→`PoiHelper`, `FabricTrackedDataRegistry`→`FabricEntityDataRegistry`, `FabricGameRuleVisitor`→`FabricGameRuleTypeVisitor` |
| NeoForge fixes | - | **Deleted 5 broken redirects** (IItemHandler/IFluidHandler/IEnergyStorage prefix-drops pointed at nonexistent/wrong classes: old interfaces SURVIVE in 26.1; ForgeSpawnEggItem both-sides-gone); added `BlockEvent$BreakEvent`→`level/block/BreakBlockEvent`, `RenderLevelStageEvent` `$AfterEntities`→`$AfterOpaqueFeatures` + `$AfterParticles`→`$AfterTranslucentParticles`; moved the #82 RecipesUpdated redirect to the 1.21.3→1.21.4 shim where the rename actually happened |

### ⚠ Latent lambda traps in EXISTING redirects (migration map says these SAMs renamed: each needs the extends+default treatment or a verified-stable-SAM check; the plain class redirect alone link-crashes lambdas at runtime)
`ClientWorldEvents→ClientLevelEvents` (SAM `afterWorldChange`→`afterLevelChange`),
`ServerEntityWorldChangeEvents→ServerEntityLevelChangeEvents` (`afterChangeWorld`→`afterChangeLevel`),
`LivingEntityFeatureRendererRegistrationCallback` (`registerRenderers`→`registerLayers`),
`DrawItemStackOverlayCallback→RenderItemDecorationsCallback` (SAM renamed),
`WorldRenderEvents→LevelRenderEvents` (9 inner SAMs, check each),
`ServerChunkEvents$LevelTypeChange→$FullChunkStatusChange` (`onChunkLevelTypeChange`→`onFullChunkStatusChange`),
plus not-yet-registered: `TooltipComponentCallback→ClientTooltipComponentCallback` (`getComponent`→`getClientComponent`),
`LandPathNodeTypesRegistry→LandPathTypeRegistry` (`getPathNodeType`→`getPathType`),
`KeyBindingHelper` method `registerKeyBinding`→`registerKeyMapping` (class redirect exists; method redirect needs checking).
**The extends+default synthetic (ScreenEvents/HudRenderCallback pattern) is the right fix for all of these:** pure ASM, no reflection.

### ⚠ Name-reuse hazard: do NOT naively redirect
`recipe/v1`: `FabricRecipeManager`→`FabricRecipeAccess` **and** `FabricServerRecipeManager`→`FabricRecipeManager`.
The multi-pass transformer would chain `FabricServerRecipeManager`→`FabricRecipeManager`→`FabricRecipeAccess` (wrong).
Needs single-pass/ordered handling.

### Deferred (precise reasons)
- **ColorProviderRegistry (13)**: replaced, not renamed. Old `BLOCK.register(BlockColor, blocks…)` (field+virtual) → static `BlockColorRegistry.register(List<BlockTintSource>, blocks…)`; the provider TYPE changed, so a bridge must adapt `BlockColor`→`BlockTintSource`. Bounded bridge, needs BlockTintSource contract study.
- **BlockEntityType$Builder (13)**: removed in 26.1 vanilla; ctor is private. Path: synthetic Builder delegating to `FabricBlockEntityTypeBuilder` (present in 0.145.4).
- **FuelRegistry (8)**: 1.20.1-era interface (`INSTANCE.add(item, ticks)`); 26.1 = `FuelValueEvents.BUILD` callback. Bridge = queue adds, replay in a BUILD listener.
- **FabricModelPredicateProviderRegistry (8), FabricStructureBuilder (5)**: removed before 1.21.11, successors are full subsystem reworks → deep list.
- **NeoForge `VillagerTradesEvent`/`WandererTradesEvent`/`BasicItemListing`**: removed in 26.1, replaced by data-driven `villager_trade` JSON, not bridgeable by redirect; deep list.
- **NeoForge `LivingDamageEvent$Post.getNewDamage`**: rename to `getInflictedDamage` landed in 26.1.2.**70**-beta; the current host line (.60) still has `getNewDamage`, so do not redirect until hosts move.

### Audit measurement note
The audit counts a mod's post-transform refs against `host-classes-all.txt`, which does
NOT include Retromod's **synthetic** classes, so every synthetic-backed fix
(ScreenEvents `$AfterRender`, the five SAM bridges, the model bridge…) still shows as
a "gap". Next audit run: append the registered synthetic names to the manifest, or the
clean% will keep undercounting.

## Honest read

The biggest sole-blocker numbers (ItemGroupEvents 83, ClientPlayNetworking 59,
CommandRegistrationCallback 38) are all **functional-interface bridges**, not
redirects. Each is a real, bounded piece of work in the ClientPlayNetworking mold,
and each needs an in-game launch to confirm. Snapshot.3 is therefore a handful of
careful SAM bridges, not a redirect table. Realistically ~150-180 mods of headroom
(53% → low 60s%) if all the interface bridges land and verify.

## In-game pass round 2 findings (world-load testing)
- **Datapack worldgen is a hard limitation**: mods shipping 1.20.x datapack worldgen
  (biomes/features/carvers JSON) fail 26.1 registry load entirely, which blocks ALL world
  load/create while installed (spawn-mod). Bytecode transforms can't fix data; a
  datapack-format migration subsystem (DFU-style) is its own future project.
- **FabricEntityTypeBuilder inner gap**: `$Mob`/`$Living` are NOT covered by the outer
  redirect (inner-class rule again) → `VerifyError: FabricEntityTypeBuilder$Mob not
  assignable to EntityType$Builder` in entity registration (spawn-mod, caught by the
  entrypoint guard as non-fatal). Fix = inner redirects + method-shape mapping
  (spawnGroup/disableSummon/… → vanilla Builder equivalents). Next coding item.
- **Era-ambiguous FQN hazard** (fixed): a legacy-era redirect keyed on a name that a
  MODERN class also uses gets applied through the multi-pass chain to modern
  references (1.12 `Gui`→`GuiComponent` hijacked the modern HUD `Gui`). Rule: never
  register a redirect whose OLD name exists in current Mojang mappings.
