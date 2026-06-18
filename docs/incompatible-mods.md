---
title: Mods That Can't Be Translated
nav_order: 14
---

# Mods That Can't Be Translated

Retromod can shim a renamed method, redirect a moved class, polyfill a removed API, and patch a mod's metadata so a stricter loader stops rejecting it. What it can't do is rewrite something that isn't there to be rewritten, or run a translation pass on bytes that aren't Java bytecode in the first place.

This page is the "no" list. If your mod is here, or matches one of the general patterns at the bottom, Retromod is not the right tool, and only the mod's original author can ship a working port.

## The general rule

If a mod uses any of the following, **no automated bytecode tool can translate it** (Retromod or otherwise). There's no realistic path to making it work without the mod author writing a manual port:

- **Native code.** JNI/JNA bindings, `.so` / `.dll` / `.dylib` files bundled in the JAR, or shared libraries downloaded on first launch. Bytecode translation can't touch native binaries; they target a fixed ABI built against a fixed C/C++/Rust API on the other side.
- **A custom rendering pipeline.** Mods that replace MC's renderer with their own (Flywheel, Iris's shader pipeline, mods that ship custom Vulkan/Metal/OpenGL code). The rendering surface changes shape between MC versions in ways that aren't expressible as a redirect, with entire pipeline stages getting rewritten.
- **Their own bytecode transformer.** Coremods that ship an ASM transformer of their own, applied at class-load time, against bytecode positions in MC that have since moved. Retromod can't second-guess another transformer's intent, and even if it could, the moved positions wouldn't match anymore.
- **A custom or modified Mixin framework.** Standard Mixin we can work with. Forks of Mixin, or proprietary mixin-like systems, depend on private internals that aren't part of any compatibility surface.
- **Direct GPU buffer manipulation tied to a specific MC version.** Mods that reach into MC's vertex buffers, command queues, or shader programs at byte-level offsets. Any of those that change between versions break in ways no transformer can patch.
- **Closed-source / proprietary distribution.** Mods that ship obfuscated and forbid redistribution (OptiFine being the canonical example) can't be transformed without violating their license. Most of them are also coremods, so even setting the license aside, see point 3.

If a mod hits any of those, you need the original author to port it. There is no faster path.

## Mixins into reworked vanilla methods (feature won't work, but the mod loads)

This one isn't really a property of the mod, it's a property of the gap you're crossing, and it's a *degraded-feature* problem rather than a hard incompatibility. A mod that uses `@Inject` / `@Redirect` / `@ModifyArg` to hook a **vanilla** Minecraft method only works if that method still has the shape the mod compiled against. Retromod can rename a method and move a class, but it can't re-point an injector when Mojang reworked the method itself (changed its parameter count, reordered its arguments, or swapped a parameter type), because the injection target the mod describes no longer exists to be matched.

When this happens the loader reports `InvalidInjectionException: Invalid descriptor` and skips that one injection. Retromod treats these as non-fatal, so the mod still loads and only the specific feature that mixin implemented stops working.

26.1 reworked a lot of these. Some real examples from a single mod (Caverns & Chasms, 1.21.1 → 26.1, [#24](https://github.com/Bownlux/Retromod/issues/24)):

- `AbstractFurnaceBlockEntity.burn` gained `RegistryAccess`, a `RecipeHolder`, and the block-entity itself as parameters.
- `BlockBehaviour.updateShape` had its arguments reordered and `LevelReader` / `ScheduledTickAccess` / `RandomSource` inserted.
- The player NBT-save hook now writes to a `ValueOutput` instead of a `CompoundTag`.
- The `FallingBlockEntity` fall-damage hook changed a `float` parameter to a `double`.

There's no general fix for the injection itself: the mod's injector was written for a method body 26.1 deleted, so that hook stays inactive. The shorter the version jump, the less often this bites (1.21.1 → 1.21.8 reworks far fewer methods than 1.21.1 → 26.1).

What hard-crashes, and what Retromod fixes, is when the same mod also references a **removed class**. Caverns & Chasms pulled the deleted `DirectionProperty` into `Blocks.<clinit>` during bootstrap, and that `NoClassDefFoundError` killed the game before any of the soft-failed mixins mattered. Retromod polyfills `DirectionProperty` (redirecting it to the surviving `EnumProperty`), so the mod now boots. The reworked-method mixins above still soft-fail, so those particular tweaks won't apply, but the game runs.

### MixinExtras `@Local` captures, and the mixin blocklist

A nastier variant: MixinExtras' `@WrapOperation` / `@ModifyExpressionValue` with a `@Local` capture. Standard Mixin's `@Inject` can be downgraded to fail-soft, but a `@Local` that no longer binds (because the vanilla method's local-variable layout changed) makes MixinExtras emit an **invalid bridge method**, which the JVM rejects with `VerifyError: Bad local variable type` *at class-load*. That's fatal, before any soft-fail can run. Deeper & Darker's `PaintingItemMixin` hit this on 26.1: it wraps `ItemStack.shrink` inside `HangingEntityItem.useOn` and captures the `Level` local, which moved when 26.1 restructured `useOn` ([#28](https://github.com/Bownlux/Retromod/issues/28)).

This can't be auto-detected safely. The local usually still exists, just at a different slot, so a heuristic would strip working mixins too. Instead Retromod ships a curated **mixin blocklist** (`retromod/mixin-blocklist.json`) that surgically removes the specific crashing handler so the framework never processes it: the mod boots and only that one feature is inert. You can extend it for your own mods via `config/retromod/mixin-blocklist.json`:

```json
{
  "blocked": [
    { "mixin": "com/example/mod/mixin/SomeMixin", "methods": ["handlerThatCrashes"] }
  ]
}
```

Omit `methods` to disable every injector on the class. If a mod crashes with `VerifyError: Bad local variable type` pointing at a `wrapOperation$…$mixinextras$bridge` method, that mixin handler is the culprit. Add it here (and please file an issue so it can join the curated list).

## Specific mods that will never work

This is the obvious list. Everything here matches one or more of the general rules above. Other mods that match those rules belong here too; these are just the ones people ask about most.

### Deep-integration / coremod-class

| Mod | Why |
|---|---|
| **Create** *(and its add-ons)* | Ships [Flywheel](https://github.com/Jozufozu/Flywheel), its own GL-level rendering library, and replaces large parts of MC's animation, networking, and contraption simulation. Internal API surface is also rewritten between MC versions, not renamed. Add-ons (**Create Aeronautics**, Create: Offroad, Create: Simulated, Ponder, …) build directly on Create's contraption/rendering internals and inherit the same incompatibility. |
| **Applied Energistics 2 (AE2)** | Decades of deep MC integration, custom networking protocol, historically a coremod, and the channel/quantum systems get re-architected between versions. |
| **Tinkers' Construct** | Replaces the tool and material system at the JSON-schema level *and* the bytecode level. The schema changes between versions are larger than any redirect table can cover. |
| **IndustrialCraft / IC2** | Very deep MC integration, coremod heritage, energy network internals are tied to MC's tick scheduler in ways that break on every major MC update. |
| **Thaumcraft** | Magic systems tied to MC's internal aspect/research data structures, which change between versions. The mod has been redesigned multiple times and its older incarnations are tied to specific MC internals. |
| **Botania** *(deep mixin variants)* | Heavily mixin-based with injection points tied to specific bytecode offsets. Some Botania features may load, but full-functionality compatibility is the same problem as Sodium/Iris below, only worse. |
| **AsyncParticles** | Wraps the Mixin *service itself* (a custom `IClassBytecodeProvider` redirect plus a class-adjuster that generates conditional mixins at load time) and `@Overwrite`s particle-engine internals whose signatures shift between MC versions. The game runs with it installed, but its own machinery cancels most of its mixins on a non-matching host and the core `@Overwrite` can't find its target — so the optimization (and its config) is inert. An `@Overwrite` body *is* the replacement code; no redirect table can rewrite it for a changed method. (#63) |

### Rendering replacement / shader

| Mod | Why |
|---|---|
| **OptiFine** | Proprietary, closed-source, ships as a Forge coremod. Even ignoring the license, the coremod transforms wouldn't survive translation. |
| **Sodium**, **Iris Shaders**, **Embeddium** | These technically *load* via Retromod (they appear on [COMPATIBILITY.md](../COMPATIBILITY.md) with the `*` caveat), but their mixin injections target specific bytecode offsets in MC's renderer. When those offsets move between versions, no transformer can synthesize an injection point that wasn't there. Expect partial functionality at best. |
| **Flywheel** | The rendering library Create depends on. Same shape problem as above: a custom GL pipeline. |
| **Veil** | A rendering framework (custom render pipeline, shaders, post-processing) that other mods build on. Same shape problem as the GL pipelines above, where the render surface it hooks is rewritten between MC versions, not renamed. |
| **Sable** *(and Sable Companion)* | Built on top of **Veil**, so it inherits Veil's custom-pipeline incompatibility. |
| **ImmediatelyFast** and similar rendering-pipeline replacements | Same pattern: they wrap MC's rendering at a level that doesn't translate. |

### Loaders (these aren't mods, but people ask)

Forge, NeoForge, Fabric Loader, and Quilt Loader are **mod loaders**, not mods. Retromod runs *on top of* them; it doesn't translate them. If you want a different loader, install that loader directly.

## Old Forge mods on NeoForge: not yet (planned for 1.2.0)

A 1.20.1-era **Forge** mod will not currently load on a modern **NeoForge** host. This isn't a "never," it's a "not yet."

NeoForge 1.20.1 was its very first release, forked from Forge and still sharing essentially all of Forge's API: `ForgeRegistries` / `IForgeRegistry`, the `DeferredRegister.create(IForgeRegistry, …)` signature, `FMLJavaModLoadingContext.get().getModEventBus()`, the `net.minecraftforge.*` package names, and so on. NeoForge then **replaced all of that** in 1.20.2+: renamed packages, restructured the registry system (vanilla registries moved to `BuiltInRegistries`, `IForgeRegistry` removed), made the mod event bus a constructor parameter, and reworked events and data generation. So a 1.20.1 Forge mod is, in API terms, *practically a Forge mod*, and translating it onto NeoForge 26.1.x means redoing the entire Forge→NeoForge migration, not a handful of class renames. Retromod gets these mods *scanned* by NeoForge (the metadata promotion in beta.9, #42), but the bytecode-level cross-loader translation is **scheduled for Retromod 1.2.0**, not the rc line.

**What to do today:** run a Forge mod on a **Forge** host. On Forge 26.1.x those same APIs still exist natively, so it's a within-loader version bump (which Retromod handles) rather than a cross-loader rewrite. NeoForge mods translate to NeoForge fine; this limitation is specifically *Forge mod → NeoForge host*.

## Mods built on structurally-redesigned APIs: not yet (planned for 1.2.0)

Some mods now **construct and load much further** than the old missing-class crash (Retromod's renames carry them past startup and several render layers) but their *core feature* is built on a Minecraft or loader API that a later version didn't *rename*, it **redesigned or deleted**. Retromod can rename a class or remap a method; it can't reshape a mod's calls onto a differently-shaped API. These need hand-written **polyfills** (reimplement the old API on top of the new one), which is **1.2.0** work.

| Mod | Where it hits the wall |
|---|---|
| **Illagers Wear Armor** (#51) | Loads past construction and several render layers, then fails on `ArmorMaterial$Layer`, which 1.21.x removed in favor of `EquipmentClientInfo` (a *different* API, not a rename). It also calls `AnimationUtils.swingWeaponDown(…, Mob, …)`, which became `(…, HumanoidArm, …)`; Retromod now leaves that call alone rather than emit a `VerifyError`, so it degrades to a broken attack animation instead of a crash. |
| **Luminous: Nether** (#52) | An MCreator mod that registers spawn eggs via NeoForge's `DeferredSpawnEggItem`, which NeoForge **deleted** in 21.11. There's no class to redirect to. |
| **Island Generator** (#62) | A 1.15.2 custom-world-generation mod that registers a world type through `net.minecraft.world.WorldType`, which Mojang **deleted in 1.16** when world generation was rewritten (custom world types became the registry/preset system, then fully data-driven in 1.18). It fails at mod construction with `NoClassDefFoundError: net/minecraft/world/WorldType` — there's no class to redirect to, and the mod's *entire* feature is that world type, so even a soft-fail stub wouldn't make the island generation actually work. Custom worldgen from ≤1.15.2 is the deepest version of this case. (The "Missing License Information" error originally reported in #62 is a separate bug that *is* fixed.) |
| **Rings of Ascension** (#98) | A 1.20.4 NeoForge mod that builds a **custom enchantment-glint render type** from `RenderStateShard.RENDERTYPE_GLINT_DIRECT_SHADER` (a `RenderStateShard$ShaderStateShard`). The 1.21 shader rework **removed the entire `ShaderStateShard` system** (replaced by the new `RenderPipeline`/`ShaderProgram` model), so on a 1.21.1 host it dies at client render-init with `NoSuchFieldError: …RENDERTYPE_GLINT_DIRECT_SHADER`. Not a rename — the field's *type* is gone — so no redirect is possible, and a soft-fail stub is pointless because the custom glint *is* the mod's whole feature. (Earlier reported under #92 as a `ResourceLocation` constructor crash, which **is** fixed in 1.1.0 — the mod now gets all the way to rendering before hitting this.) Needs the glint hand-ported to the new render pipeline, or a 1.21.1-native build of the mod. |

**The tell:** the crash is a `NoClassDefFoundError` / `VerifyError` on a vanilla or loader class whose replacement has a *different shape*: `EquipmentClientInfo` vs `ArmorMaterial$Layer`, `HumanoidArm` vs `Mob`, or simply gone (`DeferredSpawnEggItem`). If the replacement were just a rename, Retromod would already handle it (it now maps ~900 vanilla renames per host). This is distinct from the deep-integration mods above: these are otherwise-simple content mods that happen to touch one redesigned API.

## Architectury mods: load, but content silently doesn't register (#71)

Architectury-API mods (the ones with a `common` module plus `.fabric`/`.neoforge` platform packages, and an `architectury.common.json`) register their blocks/items/etc. through Architectury's `DeferredRegister` / `RegistrySupplier`, with platform-specific glue injected at build time (`architectury_inject_*`). Architectury's API and that injected glue are **redesigned between Architectury versions**, and Architectury is tightly bound to the host MC version (you install the 26.1 Architectury on a 26.1 host, etc.).

So a mod built against, say, 1.20.1 Architectury, translated onto a host running 1.21.1+ Architectury, **loads without crashing** (the `dev/architectury/*` classes are present) but its registration calls land on a differently-shaped registrar. The items register to nothing, and in-game it looks like the mod isn't installed at all (the #71 symptom: *"the game launches correctly, but there is no items in game, like it isn't installed"*). This is the same class of problem as the deep-integration frameworks above, where the registration surface is reshaped rather than renamed, except the mod fails *quietly* instead of crashing.

**The tell:** the mod is an Architectury mod (check for `architectury.common.json` in the jar, or a `dev.architectury` dependency), it boots with no errors, and its content is simply absent. Retromod can't bridge this today; the fix would be a version-spanning Architectury registration shim, which is framework-scale work. Rubinated Nether (#71) is the canonical report.

## Old Fabric mods on a pre-26.1 host: most work, some don't (bridge in 1.1.0)

This one is **loader- and host-specific**: it applies only to **Fabric** mods running on a **pre-26.1** host (e.g. a 1.16.5 mod on a 1.20.1 Fabric server). On a 26.1+ host it doesn't apply, since there the full intermediary→Mojang translation runs and old Fabric mods are handled normally.

Fabric mods ship in **intermediary** names (`class_1297`, `method_5773`), which Mojang keeps *stable across versions*. On a pre-26.1 host Retromod (correctly) leaves those names as-is, so a mod works as long as the APIs it touches **didn't change** between its version and the host. In practice **most simple content mods work untouched**: register a few items/blocks/entities, call common methods, and the names still resolve.

Where it breaks is a mod that uses an API the game **changed or removed** in between: a method whose signature changed, a deleted class, or a **redesigned** subsystem. The clearest example is a mod with a **custom entity renderer/model written for 1.16.x or earlier**. Minecraft reworked the entire entity model/render system in 1.17 (the old `EntityModel`/`ModelPart` constructors gave way to the layer / `TexturedModelData` system), so that client code references APIs that no longer exist on a 1.20.1 runtime and crashes. That's a structural redesign, not a rename, and Retromod can't bridge it by name-mapping alone.

**What to do today:** run old Fabric mods on a **26.1 host** (the maintained path), where Retromod's full translation applies. **Targeted bridging for pre-26.1 Fabric hosts is planned for 1.1.0**, reimplementing the redesigned subsystems (starting with the pre-1.17 entity model system) as polyfills.

## Mods that load but are likely broken in-game

A separate category from the "never works" list above. These mods get through Retromod's pipeline and the loader accepts them, but their actual gameplay features depend on bytecode positions that have moved. They're worth listing because people see them on the [COMPATIBILITY.md](../COMPATIBILITY.md) success list and assume they fully work:

- **Sodium / Iris / Embeddium / other heavy mixin mods.** Load fine, render features may glitch, crash on specific scene complexity, or silently no-op.
- **Mods with `@Inject` on local-variable captures** (`LocalCapture.CAPTURE_FAILHARD` patterns). Standard Mixin will refuse the injection if local types changed; Retromod downgrades these to soft-fail so you don't crash, but the feature the mixin was implementing won't run.
- **Mods whose mixins target a Minecraft method whose *signature* changed.** For example, `Entity.addAdditionalSaveData(CompoundTag)` → `(ValueOutput)` in the 1.21.5 serialization refactor. Mixin refuses the injection (`InvalidInjectionException: Invalid descriptor … Expected … but found …`) and the mod fails to construct. Retromod rewrites the mixin *target* but not the handler's captured parameter types/body, and these aren't renames, so it isn't auto-translated yet. Watch out: it usually surfaces as a *misleading* downstream `NullPointerException` (e.g. in `ModelManager.reload`) after the loader enters a "broken mod state", when the real error is the earlier `InvalidInjectionException`. Blocklisting the offending handler lets the mod load with that one feature inert.
- **Mods that hardcode network packet IDs.** Retromod can rewrite class references but not protocol numbers, so version-specific packet handlers usually need an actual port.

If a mod from this category is critical to your setup, file an issue describing what specifically isn't working. That's the kind of feedback that drives the next round of shim/polyfill work.

## "But X works for me!"

Possible. The lines above are about reliability, not impossibility in every individual case. Some Create features have loaded for individual users, some Sodium configurations work on some hardware, and some AE2 networks function until the first quantum bridge. None of those mean the mod is *generally* compatible. They mean you got lucky with the specific subset of the mod you exercised.

So if you depend on one of these mods working correctly across a long play session with friends, treat it as broken until the mod ships a real port for your MC version. Retromod is not going to close that gap.

## Reporting and disagreements

If you think something on the "never works" list does work cleanly with Retromod, please [file an issue](https://github.com/Bownlux/Retromod/issues) with:

- The mod name and exact version
- The MC version you transformed *from* and the MC version you transformed *to*
- Your `latest.log` from a launch where the mod loaded successfully
- A short description of which features you exercised in-game (not "it loaded", but what did you click, place, or see render correctly?)

Conversely, if a mod *not* on this list keeps failing for you and you suspect it should be, file an issue. Adding more mods to this page based on real reports is one of the better forms of contribution.
