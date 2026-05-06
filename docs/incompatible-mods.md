---
title: Mods That Can't Be Translated
nav_order: 14
---

# Mods That Can't Be Translated

RetroMod can shim a renamed method, redirect a moved class, polyfill a removed API, and patch a mod's metadata so a stricter loader stops rejecting it. What it cannot do is rewrite something that isn't there to be rewritten — or run a translation pass on bytes that aren't Java bytecode in the first place.

This page is the realistic "no" list. If your mod is here, or matches one of the general patterns at the bottom, RetroMod is not the right tool — only the mod's original author can ship a working port.

## The general rule

If a mod uses any of the following, **no automated bytecode tool — RetroMod or otherwise — can translate it**. There's no realistic path to making it work without the mod author writing a manual port:

- **Native code.** JNI/JNA bindings, `.so` / `.dll` / `.dylib` files bundled in the JAR, or shared libraries downloaded on first launch. Bytecode translation can't touch native binaries — they target a fixed ABI built against a fixed C/C++/Rust API on the other side.
- **A custom rendering pipeline.** Mods that replace MC's renderer with their own (Flywheel, Iris's shader pipeline, mods that ship custom Vulkan/Metal/OpenGL code). The rendering surface changes shape between MC versions in ways that aren't expressible as a redirect — entire pipeline stages get rewritten.
- **Their own bytecode transformer.** Coremods that ship an ASM transformer of their own, applied at class-load time, against bytecode positions in MC that have since moved. RetroMod can't second-guess another transformer's intent — and even if it could, the moved positions wouldn't match anymore.
- **A custom or modified Mixin framework.** Standard Mixin we can work with. Forks of Mixin, or proprietary mixin-like systems, depend on private internals that aren't part of any compatibility surface.
- **Direct GPU buffer manipulation tied to a specific MC version.** Mods that reach into MC's vertex buffers, command queues, or shader programs at byte-level offsets. Any of those that change between versions break in ways no transformer can patch.
- **Closed-source / proprietary distribution.** Mods that ship obfuscated and forbid redistribution (OptiFine being the canonical example) can't be transformed without violating their license — and most of them are also coremods, so even setting the license aside, see point 3.

If a mod hits any of those, the answer is "you need the original author to port it." There is no faster path.

## Specific mods that will never work

This is the obvious list. Everything here matches one or more of the general rules above. Other mods that match those rules belong here too — these are just the ones people ask about most.

### Deep-integration / coremod-class

| Mod | Why |
|---|---|
| **Create** | Ships [Flywheel](https://github.com/Jozufozu/Flywheel) — its own GL-level rendering library — and replaces large parts of MC's animation, networking, and contraption simulation. Internal API surface is also rewritten between MC versions, not renamed. |
| **Applied Energistics 2 (AE2)** | Decades of deep MC integration, custom networking protocol, historically a coremod, and the channel/quantum systems get re-architected between versions. |
| **Tinkers' Construct** | Replaces the tool and material system at the JSON-schema level *and* the bytecode level. The schema changes between versions are larger than any redirect table can cover. |
| **IndustrialCraft / IC2** | Very deep MC integration, coremod heritage, energy network internals are tied to MC's tick scheduler in ways that break on every major MC update. |
| **Thaumcraft** | Magic systems tied to MC's internal aspect/research data structures, which change between versions. The mod has been redesigned multiple times and its older incarnations are tied to specific MC internals. |
| **Botania** *(deep mixin variants)* | Heavily mixin-based with injection points tied to specific bytecode offsets. Some Botania features may load, but full-functionality compatibility is the same problem as Sodium/Iris below — only worse. |

### Rendering replacement / shader

| Mod | Why |
|---|---|
| **OptiFine** | Proprietary, closed-source, ships as a Forge coremod. Even ignoring the license, the coremod transforms wouldn't survive translation. |
| **Sodium**, **Iris Shaders**, **Embeddium** | These technically *load* via RetroMod (they appear on [COMPATIBILITY.md](../COMPATIBILITY.md) with the `*` caveat), but their mixin injections target specific bytecode offsets in MC's renderer — when those offsets move between versions, no transformer can synthesize an injection point that wasn't there. Expect partial functionality at best. |
| **Flywheel** | The rendering library Create depends on. Same shape problem as above — custom GL pipeline. |
| **ImmediatelyFast** and similar rendering-pipeline replacements | Same pattern — they wrap MC's rendering at a level that doesn't translate. |

### Loaders (these aren't mods, but people ask)

Forge, NeoForge, Fabric Loader, and Quilt Loader are **mod loaders**, not mods. RetroMod runs *on top of* them; it doesn't translate them. If you want a different loader, install that loader directly.

## Mods that load but are likely broken in-game

A separate category from the "never works" list above. These mods get through RetroMod's pipeline and the loader accepts them, but their actual gameplay features depend on bytecode positions that have moved. They're worth listing because people see them on the [COMPATIBILITY.md](../COMPATIBILITY.md) success list and assume they fully work:

- **Sodium / Iris / Embeddium / other heavy mixin mods.** Load fine, render features may glitch, crash on specific scene complexity, or silently no-op.
- **Mods with `@Inject` on local-variable captures** (`LocalCapture.CAPTURE_FAILHARD` patterns). Standard Mixin will refuse the injection if local types changed; RetroMod downgrades these to soft-fail so you don't crash, but the feature the mixin was implementing won't run.
- **Mods that hardcode network packet IDs.** RetroMod can rewrite class references but not protocol numbers — version-specific packet handlers usually need an actual port.

If a mod from this category is critical to your setup, file an issue describing what specifically isn't working — that's the kind of feedback that drives the next round of shim/polyfill work.

## "But X works for me!"

Possible. The lines above are about reliability, not impossibility-in-every-individual-case. Some Create features have loaded for individual users; some Sodium configurations work on some hardware; some AE2 networks function until the first quantum bridge. None of those mean the mod is *generally* compatible. They mean you got lucky with the specific subset of the mod you exercised.

The honest framing is: if you depend on one of these mods working correctly across a long play session with friends, treat it as broken until the mod ships a real port for your MC version. RetroMod is not going to close that gap.

## Reporting and disagreements

If you think something on the "never works" list actually does work cleanly with RetroMod, please [file an issue](https://github.com/Bownlux/RetroMod/issues) with:

- The mod name and exact version
- The MC version you transformed *from* and the MC version you transformed *to*
- Your `latest.log` from a launch where the mod loaded successfully
- A short description of which features you actually exercised in-game (not "it loaded" — what did you click, place, or see render correctly?)

Conversely, if a mod *not* on this list keeps failing for you and you suspect it should be, file an issue — adding more mods to this page based on real reports is one of the better forms of contribution.
