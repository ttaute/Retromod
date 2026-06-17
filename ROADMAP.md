# Retromod Roadmap

> ⚠️ **Everything in this document is provisional and can change at any time.** Nothing here is a promise or a commitment — items may be reordered, rescoped, deferred, or dropped entirely as real mods, real reports, and new Minecraft versions dictate. Don't treat anything below as guaranteed.
>
> Forward-looking plans only. Anything already shipped lives in the [changelog](CHANGELOG.md), not here.
> No fixed dates — Retromod ships a release when it's ready, not on a calendar.
>
> Last updated: 2026-06-16 · at **v1.1.0-rc.1**

---

## Where we are

**Retromod 1.0.0 is stable; 1.1.0 is on the snapshot track (currently snapshot.4).** It translates old Fabric, NeoForge, and Forge mod bytecode onto **MC 26.1.x** — the maintained target and the first unobfuscated Minecraft line — and is **26.2-ready** (shims, version aliases, and the Fabric 26.2 build target shipped in snapshot.4, verified against the 26.2 release candidate). The shim chain spans **MC 1.12.2 → 26.2**, third parties can extend Retromod through the [Addon API](docs/addons.md), and real-world results live in the community [Compatibility DB](https://bownlux.github.io/Retromod/compatdb/).

For everything that already landed, see the [changelog](CHANGELOG.md). This file is only what's *ahead*.

---

## Release cadence

Minecraft-style:

- **Patch releases** (`1.0.1`, `1.0.2`, …) — bug-fixes only, shipped directly with no pre-release.
- **Minor / major releases** (`1.1.0`, `2.0.0`) — go through `snapshot.N` → `rc.N` → stable.

One theme per release, so a single update never mixes unrelated work.

---

## Build integrity

Official builds embed a SHA-256 of Retromod's own classes; at startup the verifier re-hashes the running jar and reports `VERIFIED` on a match, otherwise it fires a fork notice. This is an **integrity / modification check, not cryptographic anti-tamper** — there's no secret key, so a determined attacker could recompute it. For real verification, compare the jar's SHA-256 against the value published on the releases page (Modrinth / GitHub show one per file). The check is informational and never a gate (MIT); forks and source builds run identically.

---

## 1.1.0 — 26.x coverage & API bridges *(in stabilization)*

**Theme (re-scoped at snapshot.4):** make the maintained 26.x path genuinely broad — measured against the top 5,000 Modrinth mods, driven by real issue reports. This is what the snapshot line actually became, and the results are already shipped: the full-catalog static audit and the fixes it forced, ~20 Fabric API bridges for renamed/removed events (including the renamed-SAM "lambda trap" class), NeoForge redirect corrections verified against real jars, the engine-level fixes the in-game passes caught (auto-devirtualize, superclass rebase, JPMS strips), the Compatibility DB, and first-day **MC 26.2** support.

> The original 1.1.0 theme — old Fabric mods on **pre-26.1** hosts — moved to **1.2.0**, where it gets a full release's attention instead of sharing one. (Decision logged at snapshot.4.)

**Remaining before `rc.1` (= `snapshot.5`, stabilization only — no new feature surface):**

1. **In-game feature verification of the bridge set.** Every reflective event bridge still stamped *“needs in-game verification”* (item-group events, command v1, ServerWorldEvents, EntityModelLayerRegistry, HudRenderCallback, client-networking v1, and the seven renamed-SAM bridges from snapshot.4) gets a test-mod case that actually **registers and fires** the event, not just resolves the classes — then a verified pass on 26.1.2. The soft-fail design means these can't crash, but `rc` should mean *known-working*, not *known-not-crashing*.
2. **Small coverage items already scoped:** the `util/Tuple` polyfill (removed in 26.2, widely used), the **Cardinal Components package bridge** (`dev.onyxstudios.cca` → `org.ladysnake.cca` — unblocks the CCA-dependent ecosystem), and `WorldRenderEvents`' era-layered paths (the class lived at three different package paths across 1.x/1.21.x/26.1; today only the oldest is redirected).
3. **Issue burn-down** — whatever real reports land against snapshot.4.

Then **`rc.1` → `1.1.0` stable.**

---

## 1.2.0 — The general update

**Theme:** the broad catch-all after 1.1.0's 26.x-coverage line — the deep work that redirects can't do, plus the transform-engine fixes and library bridges the 1.1.0 issue stream surfaced. Not one tight theme: the unifying thread is "things a simple name-redirect can't fix." The big pillars (A, B) are the two halves of the redesigned/deleted-API problem; the rest (C) is the accumulated deeper bugs and ecosystem-library work found during 1.1.0.

### C. Transform-engine + library fixes surfaced during 1.1.0
- **forge-config-api-port `ConfigTracker` `VerifyError`.** With the bad `ForgeConfigSpec` redirect removed in 1.1.0-rc.1, forgeconfigapiport's config-*loading* path next fails verification: `net/neoforged/fml/config/ConfigTracker.loadConfig` builds an exception whose `Throwable` arg is typed `Object` after transform — a `VerifyError`. Pre-existing (1.1.0 just got far enough to reveal it), no obvious redirect-to-Object culprit, so it needs a decompile-and-diff of the transformed vs original bytecode. Potentially broad — `ConfigTracker` is core to the port, which a large number of ported mods bundle (CoroUtil/Watut, #94 follow-up).
- **Recurse transform + metadata-patching into jar-in-jar dependencies (NeoForge/Forge)** (#95) — see below.
- **Process-first (AOT) transform for NeoForge** (#95) — see below.

### A. Pre-26.1 Fabric bridge (#55)

**Why:** on a 26.1+ host the intermediary→Mojang translation carries old Fabric mods. On a **pre-26.1** host that remap is (correctly) off — the runtime exposes Minecraft under *intermediary* names — and the legacy Fabric shims were authored in the wrong namespace (MCP/Yarn), so they never fire on real distributed mods. Simple content mods still work because intermediary names are stable; anything touching an API the game **changed, removed, or redesigned** breaks. The clearest case: custom entity models/renderers from ≤1.16 (the model system was rebuilt in 1.17).

**Head start carried over from the 1.1.0 snapshots:**
- The **pre-1.17 model bridge** exists and is verified on 26.x hosts (concrete model bases, the render path, superclass rebase so mixins survive — #70).
- The break analysis is done: it's **signature/owner changes, not renamed names** (Earth2Java recon, pinned call-by-call).

**Remaining (the release's actual work):**
1. **Abstract-base model synthetics** (`class_4592/3/5` — the AgeableListModel family) so that class of mods loads at all.
2. **Host-namespace-aware redirect tables** — the same bridge keyed on intermediary names for pre-26.1 hosts (remap off) and Mojang names on 26.x (post-remap). The 26.x table exists; the intermediary one doesn't.
3. **Re-author the legacy Fabric shims** (1.14–1.20) in intermediary namespace — separating genuine breakages from the spurious MCP/Yarn renames they currently contain.
4. **Descriptor-aware gap report** on pre-26.1 Fabric hosts (name-presence is useless there — every name resolves; the *descriptors* don't).
5. **Batch-test real old Fabric content mods on a pre-26.1 instance** (e.g. 1.20.1) — the theme's actual acceptance test.

**Guardrails:** every bridge gated on `isUnobfuscatedTarget(host) == false && loader == fabric`; a regression test asserts the 26.1 Fabric output is byte-identical before/after, so the maintained path can't regress.

### B. Deep-API-change polyfills

The mods that **construct and load far** but die on an API a later version *redesigned or deleted* — not renamed. These need hand-written polyfills that reshape old calls onto the new API. Known targets (see [Mods That Can't Be Translated](docs/incompatible-mods.md)):

- **Armor-layer rendering** — `ArmorMaterial$Layer` → `EquipmentClientInfo`, a different API, not a rename (#51).
- **`DeferredSpawnEggItem`** — deleted by NeoForge; there's no class to redirect to (#52).
- **Forge mod on a NeoForge host** — a 1.20.1 Forge mod on modern NeoForge is the full Forge→NeoForge migration (registries → `BuiltInRegistries`, the mod-event-bus idiom, events, data gen). Retromod already gets it *scanned*; loading it is the deep work. The embed-an-ASM-bridge approach is scoped in `CLAUDE.md` (#85, and the #87 follow-up).
- **Process-first (AOT) transform for NeoForge.** On NeoForge the runtime transform runs at mod-constructor time — *after* the module layer is built — so module-layer failures (split packages, skipped Forge-named jars) crash before Retromod can act (#95). Making the offline/AOT path (`retromod batch`/`prepare`, the Full AOT cache) the recommended NeoForge flow — transformed jars in `mods/` before the loader scans — removes that whole "too late" class. Pairs naturally with the JiJ-recursion work below.
- **Recurse transform + metadata-patching into jar-in-jar dependencies (NeoForge/Forge).** Retromod patches a NeoForge mod's own metadata (the `mods.toml`→`neoforge.mods.toml` promotion, #42) and remaps its bytecode, but doesn't descend into the `META-INF/jars/` libraries it bundles. So a mod that JiJ-bundles a *Forge* build of its library (e.g. Cracker's Wither Storm bundling `crackerslib-forge`) leaves that nested lib un-promoted/un-translated → it's skipped → the mod reports the library "missing," and users who then add a standalone copy hit a JPMS split-package crash (#95). The fix is to apply the same per-loader processing recursively to JiJ'd jars — though for a Forge-built nested lib it also needs the Forge→NeoForge work above, so the two are entangled.
- **The 26.2 render-API removals** — `MultiBufferSource` (one of the most referenced types in modding), the old `Tesselator`/`VertexFormat` vertex layer, and `TicketType.create` (1.18-era chunk-loading mods, e.g. Chunky). Redirects can't cover deletions; these need adapter polyfills.

---

## Rendering & the OpenGL → Vulkan transition (26.2 → 26.3)

MC **26.2** added a Vulkan rendering backend (`com.mojang.blaze3d.vulkan`) alongside the existing OpenGL one (`com.mojang.blaze3d.opengl`) and made **Vulkan the default**, chosen via `net.minecraft.client.PreferredGraphicsApi` from `options.txt`'s `graphicsApi` key. MC **26.3 is expected to remove OpenGL entirely** (Apple has deprecated desktop GL; the wider ecosystem is moving to Vulkan/Metal). This section is the standing strategy for that transition.

**The non-goal, stated plainly:** Retromod will **not** attempt to translate arbitrary OpenGL bytecode into Vulkan. That is writing a GL driver in a transformer (GL is a huge stateful machine; Vulkan is explicit pipelines/descriptor-sets/SPIR-V) — infeasible and not how the problem is solved anywhere. The industry answer is a GL-on-Vulkan driver (**Zink**) and, on Apple, Vulkan-on-Metal (**MoltenVK**) — driver-level, not per-mod. Importantly, **Zink does *not* help on 26.3**: once Mojang removes MC's GL backend, MC is no longer a GL app, so a single mod's GL calls have no MC pipeline to ride and can't share MC's Vulkan swapchain. So there is no general rescue for raw-GL mods on a GL-less runtime.

**What "OpenGL code" means matters.** Most modern mods use Minecraft's *own* backend-agnostic render API (`RenderSystem`, `RenderPipeline`, `VertexConsumer`, render layers) — these already run on Vulkan with zero work. Only mods making **raw** GL calls (`org.lwjgl.opengl.GL11.*`), removed imperative state, immediate-mode geometry, or hand-bound GLSL are affected.

**VulkanMod as a forecast.** [VulkanMod](https://github.com/xCollateral/VulkanMod) (LGPL-3.0) is a third-party mod that reimplements *Minecraft's own* rendering on Vulkan — i.e. what Mojang now does natively in 26.2, so it's not a forward path for us (redundant/conflicting on 26.2+, and we can't absorb LGPL into MIT — reference only). But it is a **live preview of GL-less Minecraft**: the mods that break under VulkanMod today are the mods that will break on 26.3. We seed the "Vulkan-incompatible" list from its known breakage and use it to prioritize.

### Shipped in 1.1.0-rc.1
- **Tier 0 — prefer the OpenGL backend for old mods (the 26.2-window fix).** On a 26.2+ client, `GraphicsBackendCompat` writes `graphicsApi:opengl` to `options.txt` so translated old mods' GL rendering keeps working — **non-destructive** (only when the user hasn't chosen a backend; an explicit `vulkan` choice is respected with a warning) and opt-out via `-Dretromod.graphics.noPreference=true`. This buys time: it makes 26.3, not 26.2, the cliff.
- **Tier 2 — neutralize removed imperative render-state calls (soft-fail).** The `RenderSystem` state setters (`enableBlend`/`blendFunc`/`depthMask`/…) were deleted in the blaze3d GpuDevice/RenderPipeline refactor (gone by 1.21.11) with no method to redirect to. `RemovedRenderStateNeutralize` + the transformer's `registerRemovedMethodNeutralize` drop those calls (pop args, push default return) so old mods **load and run** instead of `NoSuchMethodError`-ing — the state is inert (soft-fail), not translated.

### The 26.3 plan (when OpenGL is removed)
| Bucket | Mods | Outcome |
|---|---|---|
| **A. Modern render API** | RenderSystem / VertexConsumer / render layers | ✅ Native on Mojang's Vulkan, no work. |
| **B. Removed-state / mechanical** | imperative GL state | ⚙️ Soft-fail neutralization (Tier 2), grown from reports. |
| **C. Raw GL / custom shaders / immediate-mode** | custom renderers, `GL11.*`, `glBegin/glEnd`, `Tesselator` | ❌ Hard boundary — hand-port (deep, per-mod) or list as Vulkan-incompatible. Same boundary VulkanMod hits. |

Concrete 26.3 work: grow the Tier-2 neutralization set from real reports; build adapter polyfills for the highest-value deletions where a *semantic* shim is possible (e.g. immediate-mode `Tesselator`/`VertexFormat` → modern `MeshData`/`BufferBuilder`, scoped under §B above); maintain the seeded Vulkan-incompatible list; and document the boundary honestly so users aren't promised a translator.

#### Candidate architecture (evaluate when 26.3 ships): re-add the OpenGL backend, run it on Zink where needed

The honest answer for "OpenGL is gone" is **not** to translate GL→Vulkan call-by-call (that's writing a GL driver, infeasible — see the non-goal above) and **not** to bundle a "GL→Vulkan translator mod" (no such thing exists; the one renderer mod that does, VulkanMod, replaces MC's renderer and breaks the same mods — see [Rendering & the OpenGL → Vulkan transition](#rendering--the-opengl--vulkan-transition-262--263)). The workable inversion is to **give the game back the OpenGL path Mojang removed.** "OpenGL" here is two separable layers:

```
MC rendering (RenderSystem / GpuDevice)
  └─ (1) MC's OpenGL backend  ── the Java classes com.mojang.blaze3d.opengl.*  (26.3 deletes these)
        └─ LWJGL GL bindings
              └─ (2) libGL  ── the native driver that runs glXxx on the GPU
                    • normal PC: the system OpenGL driver (still present for years after MC drops its backend)
                    • GL-less machine (Apple post-GL; Vulkan-only Linux): Zink → Vulkan → (Mac) MoltenVK → Metal
```

**Piece 1 — re-add MC's OpenGL backend as a polyfill (the big win, Retromod-shaped, pure Java).** Re-embed 26.2's `com.mojang.blaze3d.opengl` backend classes (≈64) so a 26.3 client can still select OpenGL, then let Tier 0 pick it. Once the backend is back, the **whole game** renders through GL again, so old GL-using mods get a real GL context. For the **majority of users this is the entire fix** — Windows/Linux keep a system OpenGL driver long after Mojang drops MC's backend, so Piece 2 isn't needed there. **Key risk:** this links *only if* 26.3 keeps the `GpuDeviceBackend` SPI those classes implement stable; if the SPI is refactored alongside the GL removal, the re-added backend won't compile against it. Undecidable until 26.3's render SPI is visible — "decide on arrival."

**Piece 2 — Zink (+ MoltenVK on Mac) as the native libGL, for GL-less machines only.** Needed only where the OS has *no* OpenGL at all (Apple after GL removal, Vulkan-only Linux). Because Piece 1 makes the whole game a GL app again, a GL-on-Vulkan driver like **Zink** works here as a normal libGL (this is the standard "GL app on a Vulkan-only box" stack; PojavLauncher does exactly this) — the earlier worry that "Zink can't help one mod inside a Vulkan host" doesn't apply once the *entire* renderer is GL. Licensing is shippable: **Zink/Mesa is MIT, MoltenVK is Apache-2.0** (unlike VulkanMod's LGPL). The real costs:
- **Native + per-platform + heavy** — Mesa/Zink for Win/Linux/Mac × x64/arm64, plus MoltenVK on Mac (Mac stack stacks two layers: GL→Zink→Vulkan→MoltenVK→Metal). Tens of MB and a cross-platform native maintenance/debug surface.
- **It's a launcher/agent layer, not a mod feature.** LWJGL picks its `libGL` very early in `Minecraft.<init>`, before a NeoForge mod constructor runs (the same too-late timing Tier 0 hit). Pointing LWJGL at Zink means setting `org.lwjgl.opengl.libname` *before* GL initializes — i.e. **premain (Retromod's Java-agent mode) or the launcher**, not normal mod init. So Zink wants to be delivered via the agent/launcher path, not baked into the mod jar.
- **Performance** — several stacked translation layers; fine for content mods, not ideal for perf-sensitive setups.

**Phasing.** (1) re-add the GL backend first — pure-Java polyfill, covers most users, gated on the 26.3 SPI; (2) Zink/MoltenVK as a *separate, optional* native layer for GL-less machines, delivered via agent/launcher. This is effectively a mini GL-compat distribution — **1.3.0+-scale**, and nothing here is buildable until 26.3's render SPI is actually visible, so it stays designed-not-built for now.

> Follow-up cleanup: the old speculative `RenderingBackendShim` / `RenderingCompat` scaffolding (unregistered, pointed at never-shipped symbols like `VulkanStateManager`) is superseded by the above and should be removed; `EnvironmentDetector`'s backend detection should be re-pointed at the real 26.2 API (`PreferredGraphicsApi` / `RenderSystem.getBackendDescription()`).

---

## 1.3.0 — Mixin translation

**Theme:** stop *soft-failing* mixins and actually translate them. Today, when a mod's mixin targets an MC method whose signature changed, or uses MixinExtras inner annotations against renamed classes, Retromod can only **strip** the handler (the feature goes inert) via the blocklist. This release makes those mixins work:

- **Re-signature `@Inject` / `@Redirect` handlers** when the target MC method's parameters changed — e.g. `CompoundTag` → `ValueOutput`/`ValueInput` (the 1.21.5 ValueIO refactor, #48). Rewrite the captured types (and, where mechanical, the handler body) instead of stripping.
- **Rewrite MixinExtras inner targets** — the `target = "net/minecraft/…"` strings inside `@ModifyExpressionValue` / `@WrapOperation`, and `@Shadow` field names — against the host's renamed/moved classes (this inner-target rewriter is currently incomplete).
- Shrink the mixin blocklist as these become real translations rather than soft-fails.

---

## Ongoing (any release)

- **More shims, polyfills, and mappings** — the long tail of per-mod and per-version fixes. Much of this can now live in community **[addons](docs/addons.md)** instead of core.
- **SRG → Mojang dictionary** growth, so more old Forge mods load — an [easy first contribution](docs/srg-mappings.md).
- **New MC versions on release day** — the 26.2 prep set the pattern: harvest the real client jars (`scripts/harvest-mc-diff.py`), verify, ship the shim before the release lands.

---

## Later / not started

- **Plugin support** (Bukkit / Spigot / Paper / Purpur). Plugins aren't mods — different APIs (the Bukkit API and NMS) and a different translation problem. A real future direction, but not started. See the [FAQ](docs/faq.md).

---

## How to help

- **Report how mods run** — the [Compatibility DB](https://bownlux.github.io/Retromod/compatdb/) has a form that pre-fills everything; a bot turns it into a database entry.
- **Write an addon** — ship shims or polyfills for specific mods: [Writing an Addon](docs/addons.md).
- **Add SRG mappings** — [the easiest first contribution](docs/srg-mappings.md).
- **File issues with the full `latest.log`** — that's what turns a broken mod into the next fix.

---

*Plans change with what real mods need. Shipped history is in the [changelog](CHANGELOG.md).*
*Maintained by Bownlux.*
