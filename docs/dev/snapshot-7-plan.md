# Retromod 1.2.0-snapshot.7 plan: the Forge family "big update"

**Status: DRAFT for review.** This is a proposal, not yet started. The goal is to turn the Forge-side story from "gets scanned" into "a real mod actually loads and works."

## The headline goal (definition of done)

A **simple Forge content mod** (blocks, items, recipes, creative tab, basic events) that targets 1.20.1 through 1.21.x **loads and its content registers and appears in-game** on a modern **NeoForge 26.x** host. Complex entity / rendering / networking mods (big boss, dragon, or storm mods) are explicitly the long tail and out of scope for the first pass.

We measure success against a fixed **acceptance set** of 2-3 real, small, dependency-free Forge content mods (chosen up front), verified in-game by you. Each transform piece also gets a host-independent JUnit test.

## Two parallel tracks

snapshot.7 is really two Forge-family migrations that happen to land in the same cycle. They share the embedding mechanism but are otherwise independent.

- **Track 1 (headline): Forge mod -> NeoForge 26.x.** The cross-loader migration (registries, event bus, events, lifecycle).
- **Track 2: Forge mod -> Forge 26.2 (#85).** The Forge-internal EventBus 6->7 break. Smaller and self-contained, good as a "warmup" because it isolates the event-bus piece that Track 1 also needs.

Recommendation: do **Track 2 first** (it is smaller, fully verifiable, and produces the reusable `IEventBus`/event-bus bridge that Track 1 reuses), then Track 1.

---

## Track 2: Forge 26.2 EventBus 6 -> 7 (#85)

**Problem.** Forge 26.2 (Forge 65.x) dropped `net.minecraftforge.eventbus.api.IEventBus` for EventBus 7's `BusGroup` + per-type `EventBus<T>`. Every old Forge mod dies at construction on `FMLJavaModLoadingContext.getModEventBus()`.

**Pieces (in order):**

1. **Synthetic `IEventBus`** (embedded per-mod via `SyntheticEmbedder`, split-package-safe). Implements the EventBus 6 surface the mods use: `register(Object)`, `addListener(Consumer<T>)`, `addListener(Class<T>, Consumer<T>)`, `post(Event)`, `unregister(...)`. Internally it holds a `BusGroup` and lazily maps each event type to an `EventBus<T>` via `EventBus.create(BusGroup, eventClass)`.
2. **`@SubscribeEvent` move.** Class-redirect the old annotation `net/minecraftforge/eventbus/api/SubscribeEvent` -> the new `net/minecraftforge/eventbus/api/listener/SubscribeEvent`, so `BusGroup.register(lookup, obj)` recognizes the handlers.
3. **`getModEventBus()` redirect.** `FMLJavaModLoadingContext.getModEventBus()` -> a bridge that wraps `getModBusGroup()` in the synthetic `IEventBus`. Same for `MinecraftForge.EVENT_BUS` (the game bus).
4. **`DeferredRegister.register(IEventBus)` adaptation.** On 26.2 this method's signature changed; the synthetic exposes its backing `BusGroup` so the redirect can call the new `register(...)`.

**Risk / unknowns:** the `addListener(Consumer<T>)` type-inference (EventBus 6 extracted `T` from the lambda; we replicate that or require the `Class<T>` overload). Pin all signatures against `eventbus-7.0.1.jar` + Forge 65.x at build time.

**Acceptance:** Macaw's Bridges 1.21.1 constructs without `NoSuchMethodError` on Forge 26.2; its blocks/items register; transform-level test on a synthetic `@Mod` constructor.

---

## Track 1: Forge mod -> NeoForge 26.x (the headline)

NeoForge 1.20.2+ replaced Forge's APIs wholesale. This is the bulk of the work. Built in dependency order so each phase unblocks the next.

### Phase 1 - Construct + register (the spine; gets a content mod loading)

1. **Registry migration (highest leverage).** This is what actually registers blocks/items, so it unblocks the most.
   - `DeferredRegister.create(ForgeRegistries.BLOCKS, modid)` -> `DeferredRegister.create(Registries.BLOCK ResourceKey, modid)` (and ITEMS, BLOCK_ENTITY_TYPE, ENTITY_TYPE, etc.). The `create` overload + the registry-key argument both change.
   - `ForgeRegistries.X` field reads -> the `BuiltInRegistries.X` / `Registries.X` equivalent.
   - `RegistryObject<T>` / `@ObjectHolder` -> `DeferredHolder<R, T>` (NeoForge's holder type). Method shape (`.get()`) is similar; the type and `register` return change.
2. **Mod event bus.** `FMLJavaModLoadingContext.get().getModEventBus()` -> NeoForge's constructor-injected `IEventBus` (NeoForge kept an `IEventBus`-style bus at `net.neoforged.bus.api`). The existing B4 synthetic already does this delegation (verified against NeoForge 26.1.2's `loader-11.0.13.jar`); extend + confirm on 26.x.
3. **Events: registration + annotations.** `@SubscribeEvent` / `@Mod.EventBusSubscriber` -> NeoForge equivalents; event package move `net/minecraftforge/event/**` + `net/minecraftforge/fml/event/**` -> `net/neoforged/neoforge/event/**` + `net/neoforged/fml/event/**`. The big one: `RegisterEvent` (NeoForge registers via a single `RegisterEvent` rather than per-type events in some idioms).
4. **Lifecycle events.** `FMLCommonSetupEvent`, `FMLClientSetupEvent`, etc. -> NeoForge package names (mostly renames once the package move table exists).

**Phase 1 acceptance:** the acceptance-set mods construct, and a `/give` of one of their items works (item is registered). Blocks place. No creative-tab visibility required yet.

### Phase 2 - Make it feel complete

5. **Creative tabs.** `CreativeModeTab` registration + the `BuildCreativeModeTabContentsEvent` idiom (changed between Forge and NeoForge).
6. **Capabilities** (deep, only if an acceptance mod needs it). Forge's `LazyOptional` / `Capability` / `@CapabilityInject` was fully rewritten in 1.20.x into the attachment + `getCapability(BlockCapability, ...)` model. This is a semantic bridge, not renames.
7. **Networking** (deep, only if needed). `SimpleChannel` / `SimpleImpl` -> NeoForge's payload-based `register*Payload` model. Also a semantic bridge.
8. **Data gen** (usually not needed at runtime; skip unless an acceptance mod runs datagen on load).

### Phase 1/2 mechanism

- **Renames** (packages, registry fields, event classes): the existing class/method/field redirect tables, harvested into a `forge-1.20-to-neoforge.tsv` (validated against the NeoForge 26.x jar, same discipline as the 1.12.2 table).
- **Deleted classes** (e.g. `DeferredSpawnEggItem`, B2): the `SyntheticEmbedder` per-mod pattern.
- **Signature/shape changes** (registry `create`, holders, capabilities, networking): hand-written semantic bridges (synthesized helper classes the rewritten calls route through).

---

## Recommended build order (what I'd do first)

1. **Track 2** (Forge 26.2 EventBus 6->7). Smallest, fully verifiable, produces the reusable event-bus bridge.
2. **Track 1 Phase 1.1: registry migration.** Highest leverage; nothing registers without it.
3. **Track 1 Phase 1.2-1.4: event bus + events + lifecycle.** Gets construction firing.
4. **Re-assess with the acceptance set.** Add creative tabs (2.5) for visibility. Only build capabilities/networking (2.6-2.7) if an acceptance mod actually needs them, driven by its real crash, not speculatively.

This stays incremental and data-driven: each phase is shippable and verifiable on its own, and we never build a deep bridge (capabilities, networking) until a real mod proves it is needed.

## Scope decision for you

How big should snapshot.7 be?

- **Option A (focused, recommended):** Track 2 + Track 1 Phases 1-2 for **content mods only**. Shippable, verifiable, clear story ("simple Forge mods now load on NeoForge 26.x / Forge 26.2"). Capabilities/networking deferred to snapshot.8 unless an acceptance mod forces them.
- **Option B (Forge family + 1.12.2):** Option A plus the 1.12.2 in-game stack (#79: `mcmod.info` -> toml metadata gen, then SRG members). Bigger, but rounds out "old Forge mods" end to end.
- **Option C (everything):** Option B plus §A pre-26.1 Fabric (#78) and the YungsApi worldgen work. Largest; risks sprawl and a long unreleasable snapshot.

I recommend **A**: a focused, genuinely-shippable Forge-family update, with B/C items as their own later snapshots.

## Open questions for your review

1. Scope: A, B, or C?
2. Acceptance set: which 2-3 real Forge content mods do we hold ourselves to? (I can pick small, dependency-free 1.20.1 + 1.21.1 ones.)
3. Target host for Track 1: NeoForge 26.1.2 (more stable) or 26.2 (newest) as the primary acceptance host?

---

## Option C groundwork (analysis outcome, snapshot.7)

Option B (Forge -> NeoForge content pipeline) is verified in-game: the Macaw's fully load on NeoForge 26.2 (scan -> construct -> register -> ColorCollection -> resources -> main menu). The remaining Option C features (§A pre-26.1 Fabric #78, YungsApi worldgen) were analyzed. Both are **data-driven** and were NOT implemented blind (guessing produces wrong bridges); their designs + data-gathering plans are recorded here.

### C1. YungsApi worldgen (`StructureProcessorType` registry-shape)

**Hypothesis (unverified):** 26.2 changed the `Registries.STRUCTURE_PROCESSOR` element type from `StructureProcessorType<?>` to `MapCodec<? extends StructureProcessor>`, so a YUNG's-style mod that registers a custom `StructureProcessorType` fails. YUNG's likely also registers custom placement-modifier / feature / structure types, each a separate potential break.

**Why not implemented now:** nothing exists in the tree to extend (no proven pattern), the registry-key type change is asserted but not verified against real 26.2 class files, and a naive `MapCodec` proxy can pass a unit test yet fail datapack deserialization in-game (the class of bug only a real mod on a real server surfaces). Pitfall #18: worldgen breaks cascade and are only visible on a **headless dedicated server**, and a co-loaded strict-JSON crash routinely masquerades as a different mod's failure.

**Data-gathering plan (the actual work item):** (1) pick one failing YUNG's consumer that registers a custom `StructureProcessorType`; (2) transform it and run on a **headless 26.2 dedicated server** (`./run.sh nogui`), capture the exact exception; (3) `javap` the real 26.2 `Registries.STRUCTURE_PROCESSOR` + `StructureProcessorType` to confirm/refute the `MapCodec` claim; (4) only then author the bridge (SyntheticEmbedder-style per-processor-type accessors + `register` interception), gated target >= 26.2, shared loaders, with an in-game round-trip test (register -> datapack finds it -> structure generates) as the acceptance gate.

### C2. §A pre-26.1 Fabric bridge (#55)

**Existing infra (real):** two engines - `FuzzyMethodResolver` (generic host-introspecting scorer, auto-applies name matches >= 85, logs probable 50-84 for review) + four `Pre*Bridge` classes (host-introspecting specific shape-change handlers), wired in `RetromodPreLaunch` under the `!isUnobfuscatedTarget` gate, 20 acceptance tests.

**Why new bridges are not implemented now:** authoring a specific bridge needs *that mod era's* intermediary names; the bundled `intermediary-to-mojang.tsv` is 1.21.4-only and intermediary IDs get reassigned across the big refactors, so guessing from the 1.21.4 table produces a wrong redirect (verified: `class_2585` is 1.21.4 `PlainTextContents$LiteralContents`, not pre-1.19 `LiteralText`).

**Code-determinable groundwork (buildable without guessing, follow-up):** (1) structured fuzzy-log capture (collect the 50-84 matches per mod into a machine-readable `(owner, name, descriptor, score)` list for offline bridge authoring); (2) per-MC-version intermediary mapping structure (`resources/intermediary/<mc>.tsv`, selector by target, fallback to the bundled 1.21.4); (3) a `Pre*Bridge` skeleton generator from a gap-report `BAD_SIGNATURE` row. **Bridge tables themselves stay data-driven:** run a real failing pre-26.1 Fabric mod on a pre-26.1 host, capture the fuzzy 50-84 log + the descriptor-aware gap report, then author the bridge against the exact unbridged `(owner, name, descriptor)`.
