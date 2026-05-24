---
title: Changelog
nav_order: 15
description: "Release notes for every Retromod version."
---

# Changelog

All user-facing changes to Retromod. The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions are [semver](https://semver.org/): the `1.0.0-beta.N` series then `1.0.0-rc.N` release candidates lead up to stable 1.0.

## [1.0.0-rc.1] — 2026-05-24

**First release candidate.** The transform pipeline is feature-complete; what remains are documented deep-API-change limitations (below), targeted for 1.1.0. This release is a large correctness pass on the mapping/transform core, driven by the NeoForge 1.21.11 reports (#50–#53).

**NeoForge host-version detection (the keystone fix).** On FancyModLoader 10.x (NeoForge 21.11 / MC 1.21.11) Retromod silently mis-detected the host MC version and fell back to a hardcoded default, which then **gated out every version shim newer than that default** — so core renames like `ResourceLocation`→`Identifier` were never applied, and transformed mods crashed with `NoClassDefFoundError` on classes that had simply been renamed. `RetromodVersion.detectFmlMcVersion()` now reads the version across FML API generations (the 10.x `getCurrent().getVersionInfo()` shape and older static forms) and logs loudly instead of silently falling back. This underlies #50/#51/#52.

**Host-version-aware vanilla class moves (NeoForge/Forge).** The `net/minecraft/*` rename/package-move table is no longer gated coarsely on "26.1+". Each rename is applied on a host only when that host actually has the new class and not the old one — i.e. the rename already happened by that host version. So a 1.21.11 host correctly gets the subset of renames that landed by 1.21.11 (≈800 of them), without the prior hazard of rewriting a working reference to a name that doesn't exist yet. Class existence is queried via the classloader that loaded Minecraft rather than a fragile MC-jar-on-disk search — which also fixed the fuzzy method resolver silently indexing the wrong jar (it had matched a `net/minecraftforge/…` library by substring → 0 classes).

**Much larger, corrected mapping tables.**
- **SRG → Mojang** (Forge reobf names): regenerated from MCPConfig + official Mojang mappings (the same join ForgeGradle performs) — ~53,000 verified entries, replacing a 117-entry starter set in which **56 entries were wrong** (e.g. `getNamespace`/`getPath` swapped; `f_50122_` mislabeled).
- **Intermediary → Mojang** (Fabric): a multi-version method/field harvest (1.16.5 / 1.20.1 / 1.21.11) so a mod built for an older version whose intermediary ids were dropped from newer tiny files still resolves.
- **Vanilla class moves/renames**: grew from 606 to ~900 entries — package reorganizations plus simple-name-*changed* renames paired by member-set similarity (`FarmBlock`→`FarmlandBlock`, `MobSpawnType`→`EntitySpawnReason`, `UseAnim`→`ItemUseAnimation`, `ElytraLayer`→`WingsLayer`, …).

**Fuzzy method resolver — stack-safety guard.** When it redirects a call matched by name + arity, it now refuses the redirect if the original argument/return types aren't stack-compatible with the candidate (e.g. `AnimationUtils.swingWeaponDown(…,Mob,…)` became `(…,HumanoidArm,…)` in 1.21.11). Rewriting such a call emitted bytecode that fails verification — a class-load `VerifyError` that crashes the whole mod — instead of degrading to a far milder, often-never-hit `NoSuchMethodError`. This protects every transformed mod.

**Mod-specific.**
- **#50 Revamped Phantoms** — its `PhantomMixin` `@ModifyReturnValue` on `getDefaultDimensions` couldn't resolve a target on 1.21.11; the handler is self-contained, so it's soft-failed via the mixin blocklist and the mod loads with its goals feature intact.
- **#53 Ultracraft** — all unresolved intermediary names now map (the reported `class_195` and 88 `method_NNNN` gaps are gone); remaining gaps are 26.1 rendering-API removals (a complex, rendering-heavy mod).

**Mapping-data attribution** added to the README (Mojang obfuscation maps; Fabric intermediary, CC0-1.0; Forge MCPConfig) — Retromod's bundled name tables are derived/transformed works, not redistributions of the originals.

> **Known limitations carried into 1.1.0:** #51 (Illagers Wear Armor) and #52 (Luminous: Nether) now construct and load far further than before, but their core feature code uses Minecraft/NeoForge APIs that were *structurally redesigned or removed* (armor-layer rendering → `EquipmentClientInfo`; `DeferredSpawnEggItem` deleted) — not renamed — so they need hand-written polyfills, planned for 1.1.0. See [Mods That Can't Be Translated](docs/incompatible-mods.md).

---

## [1.0.0-beta.10] — 2026-05-23

**Startup-crash hotfix (all loaders).** Retromod's mere *presence* could crash an otherwise-working, **native** mod at launch — even a mod Retromod doesn't transform at all. `EnvironmentDetector` (the client/server/headless probe run from each loader's entry point during mod construction) checked for Minecraft classes with the single-argument `Class.forName(name)`, which *initializes* the class. That forced `net.minecraft.server.MinecraftServer`'s static initializer to run far too early; for a mod that mixins into `MinecraftServer`/`LevelSettings` (e.g. Legacy4J), it cascaded into `wily.legacy.client.PackAlbum.<clinit>` reading `Minecraft.getInstance().gameDirectory` before the client singleton existed → `NullPointerException`, and the mod "failed to load correctly." The probe now uses `Class.forName(name, false, loader)` (`initialize = false`) — it observes class presence without ever triggering a static initializer. Dedicated-server detection was also corrected to key on the *absence of a client class* (Minecraft's merged jar carries server classes on a client too, so their presence isn't a reliable signal). Reported in #46 (Legacy4J on NeoForge 1.21.11), reproduced down to a 3-mod minimal case. **Recommended for everyone** — the bug was loader-agnostic.

**Transformed mods with spaces in their filename couldn't resolve core Minecraft classes (NeoForge/Forge).** A mod that ships no `module-info` / `Automatic-Module-Name` (common for MCreator and small mods) gets its JPMS module name *derived from the jar filename*, and spaces or odd characters there break the module's reads — the transformed mod then dies in its own `<clinit>` with `ClassNotFoundException: net.minecraft.resources.ResourceLocation` (or another core class). Retromod now sanitizes the transformed jar's output filename so the derived module name is always valid. Confirmed by a controlled test: the same mod (Luminous Nether) failed with spaces in the filename and loaded once renamed without them. Reported in #47.

**The mixin soft-fail blocklist now runs on NeoForge/Forge, not just Fabric.** When a mod's mixin `@Inject` targets a Minecraft method whose *signature* changed — the canonical case being `Entity.addAdditionalSaveData` / `readAdditionalSaveData` switching `CompoundTag` → `ValueOutput` / `ValueInput` in the 1.21.5 serialization refactor — Mixin rejects the injection (`InvalidInjectionException: Invalid descriptor … expected ValueOutput … found CompoundTag`), the mod fails to construct, and the loader's "broken mod state" then surfaces a *misleading* downstream `NullPointerException` (e.g. in `ModelManager.reload`). Retromod can't re-type the handler (it's not a rename — `ValueOutput` is a different API), so it strips the offending handler via the mixin blocklist and the mod loads with that one feature inert. That stripping had only ever been wired into the Fabric transform path; it now runs on NeoForge/Forge too. **Caveat:** this cleanly soft-fails a *self-contained* handler, but a mod whose mixin feature is spread across interdependent members — Darker Depths' death-anchor save/load (#48) — strips clean of the injection error yet still may not fully construct. Reported in #48. (*Not* a GeckoLib problem — GeckoLib loaded fine; verified against the reporter's own log.)

---

## [1.0.0-beta.9] — 2026-05-22

**NeoForge hotfix.** 1.20.1 (Neo)Forge mods were silently skipped on NeoForge 1.21.x — "File X is for Minecraft Forge or an older version of NeoForge, and cannot be loaded" — and never loaded, even though Retromod processed them. NeoForge renamed its metadata file from `META-INF/mods.toml` to `META-INF/neoforge.mods.toml` in 1.20.2, and modern NeoForge skips any jar that still has only the old `mods.toml` *at scan time, before bytecode transformation runs*. Retromod patched the toml's contents (version ranges) but never renamed it, so NeoForge rejected the mod before the shims could help. Now, on a NeoForge host (1.20.2+), Retromod promotes `mods.toml` → `neoforge.mods.toml`, relaxes the top-level `loaderVersion` (the FancyModLoader version, which doesn't track the Forge number an old mod declares), and repoints the mod's `forge` loader dependency at `neoforge` (NeoForge has no mod with id `forge`, so the mod would otherwise be rejected with "requires forge … MISSING" right after passing the file-scan stage). NeoForge then accepts the mod and the bytecode shims do their job. **NeoForge-only** — Forge (which still uses `mods.toml`) and Fabric are untouched. Reported in #42 — and this was the real cause of #38, which was wrongly attributed to the shim gate (that never ran, because the mod was skipped first).

> **Known limitation (NeoForge):** this gets a *1.20.1 Forge* mod *scanned* by NeoForge, but loading it fully is a separate, much larger job. 1.20.1 was NeoForge's first release, when it still shared Forge's API (`ForgeRegistries`/`IForgeRegistry`, the old `DeferredRegister.create(IForgeRegistry, …)` signature, `FMLJavaModLoadingContext`, …); NeoForge then replaced all of that in 1.20.2+. Translating such a mod onto modern NeoForge means redoing the whole Forge→NeoForge API migration (registries, the mod-event-bus idiom, events, data gen), which is **planned for 1.1.0**, not the beta line. For now a 1.20.1 Forge mod is best run on the **Forge** host, where those APIs still exist natively. See [Mods That Can't Be Translated](docs/incompatible-mods.md).

---

## [1.0.0-beta.8] — 2026-05-21

**NeoForge / Forge hotfix.** beta.7 crashed on launch on NeoForge and Forge — *even with no mods* — with `NoClassDefFoundError: net/fabricmc/loader/api/entrypoint/PreLaunchEntrypoint` ("Retromod has failed to load correctly"). beta.7's shim-version gate had the NeoForge/Forge entry points call a version-compare helper that lived on `RetromodPreLaunch`, and that class `implements` the Fabric-only `PreLaunchEntrypoint`; loading it on a non-Fabric loader can't resolve that interface, so mod construction aborted. The version-math helpers moved to the loader-agnostic `RetromodVersion` (which exists precisely so entry points don't drag in another loader's classes), so the NeoForge/Forge paths no longer touch any Fabric type. **Fabric beta.7 is unaffected** — only NeoForge and Forge need this build. Reported in #40; #41 was downstream (mods couldn't convert while Retromod itself failed to load).

---

## [1.0.0-beta.7] — 2026-05-21

A focused follow-up to beta.6. Several reporters on beta.6 still hit "old mod won't load on a pre-26.1 host" crashes: beta.6 gated the intermediary→Mojang *remap*, but still applied 26.1 *shims* (which rename API classes) on older hosts. This finishes that fix on every loader, handles the private `ResourceLocation` constructor on 1.21.x, and adds an in-game restart prompt. **If you're on beta.6 and translating onto a pre-26.1 host, upgrade.** (Translating onto 26.1 is unchanged.)

### Fixed

- **Pre-26.1 hosts crash on 26.1-only API names** (Fabric: `NoClassDefFoundError: net/fabricmc/fabric/api/client/screen/v1/ScreenEvents$BeforeExtract`, `…/menu/v1/ExtendedMenuProvider`, `VerifyError` against `…/LevelRenderContext`; NeoForge: 26.1 names like `ItemHandler`/`FluidHandler`). Companion to beta.6's remap gate: Retromod registered **every** version shim regardless of host, so the `1.21.11→26.1` shim — which renames API classes (Fabric `BeforeRender`→`BeforeExtract`, `WorldRenderContext`→`LevelRenderContext`; NeoForge `IItemHandler`→`ItemHandler`) — ran on 1.21.8/1.21.1 hosts and rewrote mods to names that only exist in 26.1. Unlike the intermediary remap, API names are identical in the mod and the runtime, so these renames bite even on Fabric. Shims are now gated **on every loader** (Fabric, NeoForge, Forge): a shim is registered only if its target version is ≤ the host. Reported on Fabric 1.21.8 (Puzzles Lib #31, Tidal #32), Fabric 1.21.1 (Arcanus #35), and NeoForge 1.21.1 (#38). (On a 26.1 host every shim still applies — unchanged.)
- **Fabric: `IllegalAccessError` calling the private `ResourceLocation(String, String)` constructor on 1.21.x.** That two-arg constructor became private in 1.21, so a mod built for ≤1.20 (`new ResourceLocation(ns, path)`) crashes at bootstrap on a 1.21.x host. The `1.20.6→1.21` shim already rewrote it to `ResourceLocation.fromNamespaceAndPath`, but only under the Mojang name — on a pre-26.1 Fabric host the bytecode keeps the intermediary `class_2960` (the remap is off), so the redirect missed. Added the intermediary-keyed variant (`class_2960` → `method_60655`). Reported against Haema and Rubinated Nether on 1.21.1 (#36, #37).

### Added

- **In-game restart prompt.** When Retromod converts mods during launch, it now shows a Yes/No prompt on the title screen — "converted N mod(s); the game needs to restart to load them" — instead of only logging it. Clicking Yes cleanly closes the client so you can relaunch from your launcher (true in-process relaunch isn't attempted; it can't be done reliably across launchers). Works on all loaders, declining returns to the title screen, and it's gated by `restart_prompt` in `config/retromod/config.json` (default on; set `false` to suppress). Requested in #33.

---

## [1.0.0-beta.6] — 2026-05-20

A JPMS-conflict hotfix for `javax.annotation` (same family as the Gson/ASM conflicts), a correctness fix for Fabric mods running on a **pre-26.1 host** (Retromod was over-translating them to 26.1 names that don't exist on older Minecraft), a polyfill for the removed `DirectionProperty` block-state class, and a mixin blocklist that turns a class of fatal MixinExtras crashes into inert features.

### Fixed

- **Crash on launch from a MixinExtras `@WrapOperation`/`@Local` that can't bind to a reworked vanilla method (`VerifyError: Bad local variable type`).** When a mod captures a `@Local` from a vanilla method whose body was restructured in 26.1, MixinExtras resolves the local to the wrong slot and emits an invalid bridge method that the JVM rejects at class-load — fatal, before any soft-fail can run. We can't safely auto-detect this (the local often still exists, just at a different slot, so a naive check would strip working mixins), so Retromod now ships a curated **mixin blocklist** (`retromod/mixin-blocklist.json`, extendable via `config/retromod/mixin-blocklist.json`) that surgically removes the offending handler so the framework never processes it — the mod loads with that one feature inert. Seeded with Deeper & Darker's `PaintingItemMixin#deeperdarker$decrementStackOnServer`, which wraps `ItemStack.shrink` in `HangingEntityItem.useOn` and crashed bootstrap on Fabric 26.1.2 (#28).
- **Crash on launch with `NoClassDefFoundError: net/minecraft/world/level/block/state/properties/DirectionProperty`.** 26.1 removed the `DirectionProperty` class — what used to be a subclass of `EnumProperty<Direction>` is now just `EnumProperty.create(name, Direction.class)`. A mod that references the removed type (Caverns & Chasms pulled it into `Blocks.<clinit>` via a mixin and killed bootstrap on NeoForge) crashes the game. Retromod now polyfills it: the type redirects to the surviving `EnumProperty`, and the four old `DirectionProperty.create(...)` factories are bridged to supply the `Direction.class` argument the modern API needs. Reported on NeoForge 26.1.2 (#24). Note: a mod can clear this crash and still have *other* features that don't work if it `@Inject`s into vanilla methods 26.1 reworked — those injections soft-fail (non-fatal). See [Mods That Can't Be Translated]({{ '/incompatible-mods' | relative_url }}).
- **Fabric: old mods crash on a pre-26.1 host with `ClassNotFoundException` for 26.1-only names** (`net.minecraft.resources.Identifier`, `net.minecraft.core.particles.ParticleType`, `net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.network.chat.Component`, …). Retromod applied its intermediary→Mojang remap **unconditionally**, but that translation is only valid for MC 26.1+ (the first unobfuscated release). On any earlier host the Fabric runtime still exposes Minecraft under intermediary names (`class_XXXX`), so a mod built for, say, 1.21.1 already matches the runtime — rewriting its references to 26.1 Mojang names produces classes that don't exist yet, killing the mod at load. The remap (bytecode **and** mixin-config/refmap metadata) is now gated on the host actually being 26.1+. Mods translated for a pre-26.1 host keep their working intermediary names. Reported on 1.21.8 Fabric (#21) and against several 1.21.1 mods (#29). **If you're translating onto 26.1 nothing changes** — that path is byte-for-byte identical.
- **Forge + NeoForge crash on launch: `Modules jsr305 and retromod export package javax.annotation to module mixin_synthetic`.** We bundle `javax/annotation/{Nullable,Nonnull}` polyfill stubs so old mods referencing those annotations resolve. But Forge and NeoForge bundle Guava, which transitively provides `jsr305` as a JPMS module that also exports `javax.annotation` — and strict JPMS refuses two modules exporting the same package. Reported by @epiktechno on NeoForge 1.21.1 + Forge 1.20.1 (#20). The stub can't be relocated (a polyfill only works at the real package name), so `build-all.sh` now strips `javax/annotation/` from the Forge and NeoForge per-loader mod JARs — jsr305 provides the real ones there. Fabric keeps the stub (no JPMS enforcement, so no conflict; it stays as a harmless fallback) and so does the standalone CLI.

### Note

This is the third package in the bundled-dependency-vs-loader-module conflict family (Gson → relocate, ASM → strip, javax.annotation → strip on Forge/NeoForge). The remaining bundled polyfill packages (baubles/api, cofh/api, codechicken/nei, mcp/mobius/waila) are stubs for ancient 1.7–1.12 mods that are essentially never present as JPMS modules on modern MC, so they don't collide — left in place so the polyfill still works for the rare user translating one of those.

---

## [1.0.0-beta.5] — 2026-05-19

Hotfix for ASM classloader conflicts that beta.4 introduced on Fabric and NeoForge. **Everyone on beta.4 should upgrade** — beta.4 crashes on launch on Fabric and NeoForge.

### Fixed

- **Fabric + NeoForge crash on launch with ASM `LinkageError` / loader-constraint violation.** beta.4 un-relocated ASM to fix a Forge problem (the `EventSubclassTransformer` issue, #12), but that made the bundled ASM collide with the loader's own ASM: Fabric and NeoForge each bundle ASM and load `org.objectweb.asm.tree.ClassNode` in their classloader, so our duplicate copy in the mod classloader is a second class with the same name → `LinkageError`. Reported by @Derpgamer22 / @Rivmo / @LwkySad (#18, NeoForge) and @berwulf / @benio1394 (#19, Fabric).
- **Root cause, properly fixed this time:** the mod JAR no longer bundles ASM at all. Every mod loader (Fabric, Forge, NeoForge) provides its own ASM and exposes it to mods, so Retromod just uses the loader's copy — exactly one ASM per runtime, no relocation, no duplicate, no conflict on any loader. `build-all.sh` strips `org/objectweb/asm/` from each per-loader mod JAR. The standalone CLI keeps its bundled ASM (it has no loader to borrow one from). This resolves the beta.3-vs-beta.4 flip-flop where relocating broke Forge and not-relocating broke Fabric/NeoForge — neither was right; the answer was to not ship ASM in the mod at all.

### Note

If you tested beta.4 and it crashed on Fabric/NeoForge, that's this bug. beta.5 is the fix. The classtweaker, NeoForge-loaderVersion, and Forge-AOT fixes from beta.4 are all still here — beta.5 only adds the ASM-stripping on top.

---

## [1.0.0-beta.4] — 2026-05-19

Bug-fix release closing out the wave of beta.2/beta.3 issue reports from the early-Modrinth-publish users. Three independent fixes; everyone running beta.2 or beta.3 should upgrade.

### Fixed

- **Forge: AOT crash with `Could not find parent com/retromod/shaded/asm/signature/SignatureVisitor`.** Beta.3's ASM relocation broke Forge's `EventSubclassTransformer`, which runs on the system `AppClassLoader` and looked up the relocated ASM classes by string — but those classes only existed in Retromod's mod-classloader. Every Forge transform pass failed. **Un-relocated ASM** (kept Gson, SLF4J, and toml4j relocated — those are what actually conflict with Forge's JPMS module resolver). Reported by @No11290 on issue #12.
- **NeoForge: `needs language provider java:26.1 or above to load, we have found 11.0`.** `loaderVersion` in `neoforge.mods.toml` is the **FancyModLoader** version, not the NeoForge version. Our `build-all.sh` was setting it to the MC-version-based number (`[26.1,)` for MC 26.1, etc.) — which doesn't match anything because FML versions don't track MC versions (FML 11.x ships with MC 26.1 NeoForge). Changed to permissive `[1,)`; the actual NeoForge version constraint is in the `[[dependencies.retromod]] modId = "neoforge"` block and is unchanged. Reported by @Derpgamer22 (#9) and @maksmanus (#10).
- **Fabric: `Failed to read classTweaker file from mod X / Namespace mismatch` crash on launch.** Mods built for one MC era ship classtweaker / accesswidener files with a namespace header (`intermediary` or `official`) that doesn't match the host MC's runtime namespace. The existing remapper only handled `intermediary → official` (so a 1.21.x mod on a 26.1 runtime worked); the reverse direction silently failed and Fabric Loader crashed at launch with `ClassTweakerFormatException`. New behavior: strip the `classTweakers` and `accessWidener` declarations from the mod's `fabric.mod.json` during transformation. The mod loses whatever class-opening the classtweaker provided (some mixin targets may now fail to find their hooks), but the mod *loads* instead of killing the game. That's the correct tradeoff for old-mod-on-new-MC. Reported across issues #11, #13, #14, #15, #16, #17 against satin, azurelib, tidal, dsurround, rubinated_nether, immersive_aircraft, comforts, moonlight, supplementaries.

### Notes for the issue reporters

- **#9, #10 (NeoForge)** — fixed by the loaderVersion change. Re-download beta.4 and the JPMS error goes away.
- **#11 (4 mods on Fabric 26.1.2)** — three of those mods (immersive_aircraft, comforts, moonlight, supplementaries) likely had classtweaker dependencies via their depend chain; the strip should unblock them. Worth re-testing.
- **#12 (1.12.2 mods on 1.20.1 Forge)** — the ASM unrelocation should fix the SignatureVisitor crash; 1.12.2 → 1.20.1 is still a massive hop, so individual content mods may have other issues, but at least the AOT pipeline runs to completion.
- **#13, #14, #15, #16, #17** — directly fixed by the classtweaker strip.

---

## [1.0.0-beta.3] — 2026-05-18

Single-purpose hotfix release. **Only difference from beta.2 is the shaded-dependency relocation** — no other code changes, no new features.

### Fixed

- **Forge 1.20.1+ "Modules retromod and com.google.gson export package X to module minecraft" crash on launch.** The shaded fat JAR bundled Gson, SLF4J, and toml4j without relocation, so Forge's strict JPMS module resolver saw two modules claiming to export the same package to `minecraft` and refused to start. Every bundled dependency is now relocated under `com.retromod.shaded.*` (ASM was already done, this round adds Gson, SLF4J, and toml4j). Fabric/Quilt/NeoForge builds weren't crashing but received the same relocation for consistency and future-proofing.

If you're on beta.2 and the mod loads for you fine, beta.3 is bytes-different but behavior-identical — no urgency to upgrade. If you're on beta.2 and you hit the JPMS crash above (Forge 1.20.x specifically), beta.3 is the fix.

---

## [1.0.0-beta.2] — 2026-05-17

This release focused on **broadening the Java + loader-version range so the right Retromod build is actually loadable on every MC version we publish**, plus carrying the security/quality improvements from the past few weeks.

### Fixed

- **Java requirement now matches each MC version.** Retromod's compiled bytecode targets Java 17 (was Java 25 in beta.1, which silently required all users to be on Java 25 regardless of MC version). The same JAR runs on Java 17, 21, and 25 — `build-all.sh` now declares the appropriate per-MC `"java"` constraint in `fabric.mod.json`: `>=17` for MC 1.20–1.20.4, `>=21` for MC 1.20.5–1.21.x, `>=25` for MC 26.x. This directly fixes the GitHub issue where MC 1.20.1 + Java 17 was rejected ([reported on the issue tracker](https://github.com/Bownlux/Retromod/issues)).
- **Forge `loaderVersion` is now correct per MC version.** beta.1 hardcoded `[52,)` which silently rejected every Forge build for MC 1.20.x (Forge 47–50). The MC 1.20.1/1.20.2/1.20.4 Forge builds couldn't actually load on the user's Forge. `build-all.sh` now maps MC → Forge loader version (e.g. MC 1.20.1 → Forge 47).
- **NeoForge `loaderVersion` is now correct per MC version.** Same pattern — beta.1 had a hardcoded `[4,)` minimum that didn't match NeoForge's actual versioning (`20.2.x` for MC 1.20.2, `21.0.x` for MC 1.21, etc.). Now per-MC.
- **`issueTrackerURL` in build-all.sh** pointed at the old `MC-Retromod` repo name in Forge/NeoForge metadata. Now correctly points at `Retromod`.
- **Duplicate "Made by the Developers of revivalsmp.net" line** in Forge and NeoForge mod descriptions (sandwich top + bottom) is now a single line at the end, matching the Fabric description.

### Added

- **`quilt.mod.json`** so Quilt Loader sees Retromod as a first-class Quilt mod (icon and metadata in Quilt's mod list, instead of being shown as a Fabric-mod-loaded-via-compat). Same Fabric JAR, no separate "Quilt build" needed — Quilt Loader runs Fabric mods natively.
- **SRG → Mojang member-name remap** for old reobf'd Forge mods. Forge 64.x dropped the SRG remap layer for MC 26.1; Retromod now provides the dictionary (~120 starter entries; expandable via `src/main/resources/retromod/srg-to-mojang.tsv` — see [Adding SRG Mappings](https://bownlux.github.io/Retromod/srg-mappings) in the docs).
- **CHANGELOG.md** (this file).
- **Documentation:** new pages for "Mods That Can't Be Translated" and "Adding SRG Mappings", new FAQ entries about Quilt support and Retromod's offline-by-default network policy.

### Security

- **AutoFix engine opt-in flag is now honored on Forge and NeoForge entry points too.** beta.1 gated the crash-log parser behind `-Dretromod.autoFix=true` on Fabric only; the Forge/NeoForge entry points ran it unconditionally. A malicious mod that emitted a crafted log line through SLF4J could have caused Retromod to register attacker-chosen method/field redirects in the shared transformer. Fixed across all three loader entry points.
- **JIJ (Jar-in-Jar) extraction** in `FabricModTransformer.processNestedJiJJar` is now bounded against zip-bomb-style nested archives. Previously, an inner JAR that lied about its compressed-vs-decompressed size could expand to arbitrary size during transform.
- **Output JAR write paths** in `AotCompiler` and `ApiEmbedder` now validate entry names via `ZipSecurity.safeEntryName(...)` to stop input-side zip-slip payloads from propagating into output JARs (where a downstream tool extracting them would have inherited the vulnerability).
- **Resource-pack and data-pack extraction** in `ResourcePackTransformer`, `DataPackTransformer`, and `QuiltModTransformer` now use the bounded `ZipSecurity.copyBounded` helper instead of unbounded `Files.copy(InputStream, Path)`.
- **`SignatureVerifier` no longer uses XOR-obfuscated strings.** The fork-detection notice template was previously stored as a XOR-encoded byte array and decoded at runtime — a textbook malware-scanner pattern even though the content was benign. Replaced with a plain string constant; the soft anti-tamper trade-off wasn't worth the scanner flag.
- **`Premain-Class` / `Agent-Class` / `Can-Redefine-Classes` manifest entries** are no longer in the main mod JAR or the shaded CLI fat JAR — only in the dedicated `-agent.jar` classifier (which exists for explicit `-javaagent` use). The mod JAR no longer advertises JVM-class-hijack capability it doesn't use.
- **All three `Runtime.exec(new String[]{"xdg-open", url})` browser-launch fallbacks** are removed. `Desktop.isDesktopSupported()` is the primary path on every system Minecraft runs on; if it fails, users now get a dialog with the URL to copy instead of a process-exec fallback. The codebase has zero `Runtime.exec` calls now.

### Changed

- **Project is named "Retromod" (capital R only)** throughout the codebase, docs, and metadata. The previous "RetroMod" (capital R + capital M) casing was a typo on my part that propagated for a while. All class names, comments, log output, READMEs, and docs are now consistent. The GitHub repo and Modrinth project have been renamed to match.
- **Network policy is offline by default.** Retromod never reaches the internet on its own — no telemetry, no auto-update checks, no silent downloads. Optional Modrinth native-version lookup is gated behind a config flag; the CLI's `archive download` command prompts before any HTTP request.
- **`fabric.mod.json` and `quilt.mod.json` now use `${project.version}`** via Maven resource filtering, so future version bumps only need to touch `pom.xml`.
- **Per-loader output JARs from `build-all.sh` are now properly metadata-stripped:** the Fabric build contains `fabric.mod.json` AND `quilt.mod.json` (so it works on both Fabric and Quilt loaders); the Forge build contains only `mods.toml`; the NeoForge build contains only `neoforge.mods.toml`. No more "multi-loader fat JAR" being shipped where one loader's mod scanner might trip over another loader's metadata.

### Known limitations (unchanged from beta.1)

- Deep-integration mods (Create, Applied Energistics 2, Tinkers' Construct, IndustrialCraft, OptiFine, etc.) will not work — see the [Mods That Can't Be Translated](https://bownlux.github.io/Retromod/incompatible-mods) page for the full list and the general "if a mod uses X" rules.
- Server software (Paper, Spigot, Bukkit, Purpur) is not supported — Retromod targets the *mod loaders*, not server plugin platforms. Plugin support is planned but development has not started yet.

---

## [1.0.0-beta.1] — 2026-04

Initial public Modrinth release. Core transformation pipeline (145 version shims, 30 polyfill providers, intermediary→Mojang mapping, mixin compatibility, AOT compiler), CLI tool, in-game GUI, multi-loader (Fabric / NeoForge / Forge) support.
