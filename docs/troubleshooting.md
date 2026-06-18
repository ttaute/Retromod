---
title: Troubleshooting
nav_order: 8
---

# Troubleshooting

This page covers the most common ways Retromod surprises people, and how to fix them. If your problem isn't here, [open an issue](https://github.com/Bownlux/Retromod/issues) and attach your `latest.log` plus the contents of `config/retromod/verify-reports/` for the mod you're struggling with.

> **First, two things that account for most reports:** (1) Retromod converts your mods on one launch and loads them on the **next**, so you have to restart (see right below). (2) Some mods (Create, OptiFine, big rendering mods) **can't be translated at all**; see [My mod crashes no matter what](#my-mod-crashes-no-matter-what-i-do). Check those two before digging deeper.

## I added mods but nothing loaded. Do I need to restart?

**Yes. Retromod converts on one launch and loads on the next.** This catches almost everyone the first time, so rule it out before anything else.

What happens:

1. You put old mods in `retromod-input/` (or pick them with the in-game file picker).
2. On the **next launch**, Retromod transforms them, patches their metadata, and moves the finished JARs into `mods/`.
3. The loader already scanned `mods/` before that move, so the converted mods aren't active *this* launch. Retromod shows a **"converted N mod(s) — the game needs to restart to load them"** prompt on the title screen.
4. **Restart the game.** The converted mods are now in `mods/` and load normally.

So a brand-new mod takes **two launches**: one to convert, one to run. After that it loads every time and isn't re-converted.

- The prompt is controlled by `restart_prompt` in `config/retromod/config.json` (default `true`). Setting it `false` doesn't skip conversion; you just have to remember to restart yourself.
- **On Fabric, old mods can't go straight into `mods/`.** Fabric validates mod metadata *before* Retromod runs and rejects the old mod outright, so it never gets a chance to convert. `retromod-input/` is the inbox Retromod reads from, so always stage old mods there on Fabric. (NeoForge and Forge can also convert a mod in place in `mods/`, backing the original up to `retromod-backups/`, but `retromod-input/` is the reliable path on every loader.)

## MC 26.2: graphics glitches, invisible models, or render crashes — use OpenGL, not Vulkan

**On MC 26.2, set your renderer to OpenGL.** 26.2 added a Vulkan rendering backend and made **Vulkan the default**. Translated old mods that do their own OpenGL-era rendering (custom renderers, raw GL, immediate-mode draws) can render wrong, draw nothing, or crash on Vulkan — but they work on the OpenGL backend, which 26.2 still ships.

How to select it:

- **In game:** Options → Video Settings → **Graphics API → OpenGL**, then restart if prompted.
- **Or by file:** set `preferredGraphicsBackend:"opengl"` in `options.txt` (in your game/instance folder).

**Retromod already does this for you** on a 26.2+ client: the first time it runs it writes `preferredGraphicsBackend:"opengl"` to `options.txt` — but only if you haven't picked a backend yourself, and it leaves an explicit Vulkan choice alone (with a warning in the log). So most people are on OpenGL automatically; the steps above are for when you switched to Vulkan, want to confirm, or are on NeoForge (where the preference takes effect on the **next** launch, like mod conversion).

- To stop Retromod touching the setting, add `-Dretromod.graphics.noPreference=true` to your JVM args.
- On macOS this is moot — Minecraft already runs OpenGL-over-Metal there (no native Vulkan).
- **Heads up for later:** OpenGL is expected to be removed in **MC 26.3**. When that happens this lever goes away, and mods doing raw OpenGL will need deeper work; see the [roadmap](https://github.com/Bownlux/Retromod/blob/main/ROADMAP.md). On 26.2, OpenGL is the right choice today.

## My mod doesn't load

1. **Check `retromod-input/` and `retromod-backups/`.** If your mod is sitting in `retromod-input/` after a restart, Retromod didn't process it; check the log for a Retromod error on that filename. If it's in `mods/` but also in `retromod-backups/`, transformation ran at least once, so look for runtime errors instead.

2. **Check `config/retromod/verify-reports/<modid>.txt`.** If the report lists missing classes or methods, Retromod didn't find a mapping or shim for something the mod uses. See [Verify Transforms]({{ '/verify-transforms' | relative_url }}) for how to read the report, then file an issue or add the mapping yourself.

3. **Check your Fabric Loader version.** Retromod targets the version of Fabric Loader bundled with the current target MC. If your loader is older, the mod's patched metadata (which declares `fabricloader: "*"`) is fine, but Fabric might still reject something else. Update to the latest loader for your MC version.

4. **Confirm the mod is actually in the right folder.** On macOS, instance-based launchers (Prism, MultiMC, ATLauncher) each have their own `mods/` folder. Make sure you dropped the JAR in the right one. See [Installation]({{ '/installation' | relative_url }}).

## My mod loads but crashes immediately

1. **Enable debug logging.** Set `"debug": true` and `"log_level": "DEBUG"` in `config/retromod/config.json`. Restart. The next crash will have a much more detailed Retromod trace in `latest.log`.

2. **Enable bytecode dumps.** Set `"dump_bytecode": true`. This writes every transformed class to `config/retromod/bytecode-dumps/`. Open the class the crash points to (usually in the stack trace) with a decompiler like [Vineflower](https://github.com/Vineflower/vineflower) or CFR and see what actually came out of the transformer.

3. **Check the mod's mixins.** If the crash mentions `org.spongepowered.asm.mixin`, a mixin probably failed to apply. Disable `transform_mixins` temporarily to see whether the mod even attempts to load without mixin rewriting. If it does, you know the issue's in the mixin pass.

4. **Look for `NoSuchMethodError` / `NoClassDefFoundError`.** These mean a reference didn't get remapped or polyfilled. If the verify report for this mod is empty, the reference is probably inside a mixin target or a reflective call, so try turning on `remap_reflection` if it's off.

## My mod crashes no matter what I do

Some mods can't be translated by *any* bytecode tool, and no config change will fix them. If your crashing mod is one of these, it's expected and not a Retromod bug to chase:

- **Create** (and add-ons like Create: Aeronautics), **Flywheel**, **Veil**, and **Sable** ship their own GL rendering pipelines and coremod-level internals. Tell-tale symptoms: `getLoadingModList`, `VerifyError`, a coremod error, or a `CancellationException` during teardown.
- **OptiFine**, a closed-source Forge coremod.
- **Sodium / Iris / Embeddium** and similar deep rendering/mixin mods often load but glitch or crash on specific scenes.
- **Applied Energistics 2, Tinkers' Construct, IndustrialCraft, Thaumcraft** carry decades of deep MC integration that gets re-architected between versions.

The full list, with the "if a mod does X, it won't translate" rules behind it, is at **[Mods That Can't Be Translated]({{ '/incompatible-mods' | relative_url }})**. Check your mod set against it *before* filing a transform bug; one incompatible mod in the loadout can take the whole launch down, and the fix is to remove that mod, not to debug Retromod.

## "You are using a Retromod Fork" / authenticity warning

Harmless in most cases. Retromod doesn't block anything based on authenticity status; see [Authenticity]({{ '/authenticity' | relative_url }}) for the full breakdown. The short version:

- **VERIFIED** means the running code matches the embedded release hash (unchanged from what was published). Nothing to do.
- **MODIFIED** is a fork, a repack, a re-bundling launcher, or a corrupted download. Fine if you trust where you got it; if not, re-download and compare its SHA-256 against the [official releases page](https://github.com/Bownlux/Retromod/releases).
- **IMPOSTOR** means the JAR doesn't even claim to be Retromod. If you didn't expect that, delete it and grab a fresh copy.

## Verify reports missing methods or classes

The verifier found references in the transformed mod that don't resolve against current Minecraft. Two things to try:

1. **Look at the referenced class.** If it starts with `net/minecraft/class_`, `method_`, or `field_`, it's an intermediary name that Retromod doesn't know how to remap. This needs an entry in `mapping/IntermediaryToMojangMapper.java`.

2. **Look at the source MC version in the report header.** If the mod is 1.16.5 and the missing class is very old, a shim between 1.16 and 1.20 might be absent. Run `shims` in the [CLI]({{ '/cli' | relative_url }}) to see what's registered.

Either way, [file an issue](https://github.com/Bownlux/Retromod/issues) with the report attached. These reports are exactly the input needed to add the missing mapping or shim, so they're useful to the project.

## Java version errors

The Java version you need depends on which MC version you're running, **not** on Retromod itself. Retromod's bytecode targets Java 17, so the same JAR runs on Java 17, 21, and 25. But MC itself has its own Java floor that grows over time:

| Your Minecraft version | Java you need |
|---|---|
| MC 1.20 – 1.20.4 | Java 17 |
| MC 1.20.5 – 1.21.x | Java 21 |
| MC 26.1+ | Java 25 |

Retromod's per-MC build declares the matching Java requirement, so the loader will refuse with a friendly error if you have the wrong one.

### "Mod 'Retromod' requires version 21 or later of 'OpenJDK 64-Bit Server VM' (java)"

(Or `version 25 or later`.) Fabric Loader is enforcing the Java requirement for your MC version, meaning you have an older Java than your MC needs. Install the right Java for your MC version from [Adoptium](https://adoptium.net/), then:

- **Prism / MultiMC / ATLauncher:** Right-click the instance → **Edit** → **Settings** → **Java** tab → uncheck "Use system Java" → point at the new Java install
- **Vanilla launcher:** Edit the installation profile → **More Options** → **Java Executable** → point at the new install

**A common misconception:** MC 1.20.1 *can* run on Java 21 or 25. Vanilla MC is forward-compatible; it was built against Java 17 but loads fine on later versions. The Fabric error message is about Retromod's declared requirement, not a Minecraft limitation.

### "UnsupportedClassVersionError: class file version 69.0"

This is the JVM rejecting bytecode that's newer than it knows how to load. Class file version 69 = Java 25, so this means a class file built for Java 25 is being loaded by a Java 21 (or older) JVM.

If you're on MC 26.1+ and seeing this, you need to install **Java 25**. MC 26.1's own class files are Java 25 bytecode, so even though Retromod itself is Java 17 bytecode, MC won't load on Java 21.

If you're on MC 1.20.x or 1.21.x and seeing this, something on your classpath is Java 25 bytecode that shouldn't be, usually a mod that requires a newer MC and got installed by mistake. Check `latest.log` for the class name in the error; it'll tell you which mod.

## "I'm on Quilt, which build do I install?"

The **Fabric** build. Drop `retromod-<version>+<MC>-fabric.jar` into your Quilt instance's `mods/` folder. There's no separate "Quilt build"; Quilt Loader runs Fabric mods natively, so the Fabric JAR works on Quilt unchanged. You'll need Quilted Fabric API (QFAPI) installed, which Quilt instances typically already have.

Retromod has a `quilt.mod.json` baked into the Fabric JAR specifically so Quilt's mod list shows it with a proper icon and metadata instead of as "a Fabric mod loaded via compat." Same JAR, no extra step. See the [Quilt FAQ entry]({{ '/faq#does-it-work-with-quilt' | relative_url }}) for more.

## "NoSuchFieldError: f_NNNNN_" or "NoSuchMethodError: m_NNNNNN_" on a Forge mod

These are Forge SRG names, short numeric identifiers that old Forge mods (anything built with ForgeGradle's `reobfJar` task, basically every Forge mod for MC < 1.20.5) use to reference Minecraft members. Forge's runtime used to remap them back to real names; Forge 64.x for MC 26.1+ dropped that remap layer, so reobf'd mods crash on first reference.

Retromod ships a dictionary that handles the common ones (~120 entries covering Block/Item statics and the highest-frequency Component/ResourceLocation methods), but it's an explicitly-incomplete starter set. The full SRG namespace has tens of thousands of entries.

If you hit one of these errors:

1. **Confirm it's an SRG-name pattern:** field starts with `f_`, method starts with `m_`, both end with an underscore and contain only digits in the middle. If yours doesn't match that pattern, it's a different problem; see "Verify reports missing methods" above.
2. **Note the exact name** from the crash log (e.g. `f_220832_`).
3. Add it to Retromod's dictionary. See [Adding SRG Mappings]({{ '/srg-mappings' | relative_url }}) for the one-line PR workflow. Looking up the Mojang name via [Linkie](https://linkie.shedaniel.dev/) takes ~30 seconds.
4. Or file an issue with the name if you'd rather not PR, and we'll add it.

## Mixin failures: "@ModifyExpressionValue ... target class 'X' is not supported" / "@Shadow field gui was not located"

Common with mods built for MC versions close to but not exactly matching your host (e.g. a 1.21.9 mod on 26.1.2). What's happening:

Retromod rewrites the *outer* `@Mixin(targets = ...)` annotation to point at the renamed class. That part works, and you'll see the rewritten target in the error message (e.g. `target net.minecraft.client.gui.GuiGraphicsExtractor`). But the mod also uses MixinExtras-style inner annotations like `@ModifyExpressionValue(target = "net/minecraft/client/gui/Gui")` and `@Shadow` field references against the old class, and Retromod doesn't yet rewrite the `target = "..."` strings nested inside those, or the field-name on `@Shadow`. So the mixin processor sees a target spec that's now invalid and refuses to apply the injection.

This is a **known Retromod gap** (the mixin-extras inner-target rewriter is incomplete). Please file an issue with your `latest.log` if you hit it. We're tracking it for a future release.

**Workarounds in the meantime:**

1. **Use a more recent version of the mod, if one exists for your MC version.** A mod built natively against 26.1+ won't have this problem at all, since there's nothing for Retromod to rewrite.
2. **Use an older host MC version** that's closer to the mod's source version. A 1.21.9 mod will translate to 1.21.10 or 1.21.11 much more reliably than to 26.1.2 (small hop = fewer renamed internals to bridge).
3. **Some affected mods still partially work.** The failed mixin only disables one feature, not the whole mod, so check whether the missing feature is something you actually use. The `Mixin apply for mod X failed` line in the log is a WARN, not always fatal; the fatal crash comes when the mod's `@Inject` was load-bearing for its `<clinit>`.

The relevant known-broken mods so far: anything with a name like "CustomHUD" / "BetterHUD" / "BetterF3" on MC versions much newer than the mod was built for. Anything that ships its own mixins into MC's GUI internals is in this category, since the GUI surface changes shape often.

## Crash: "VerifyError: Bad local variable type" pointing at a `…mixinextras$bridge` method

A specific, common mixin crash: a `VerifyError` (often *"Bad local variable type"*) naming a generated method like `…$wrapOperation$…$mixinextras$bridge` or `…$modifyExpressionValue$…`. This is a MixinExtras `@WrapOperation` / `@ModifyExpressionValue` / `@Local` handler whose generated bridge assumed a vanilla method's local-variable layout that changed on your host MC. The captured `@Local` resolves to the wrong slot, so the bridge is invalid, and there's no safe automatic rewrite for it.

Retromod already soft-fails many mixin problems (one bad mixin disables one feature instead of crashing the game). For the fatal ones it can't auto-handle, there's a **blocklist** that surgically removes the offending handler so the mod still loads with just that one feature inert:

- A curated list ships inside Retromod (`/retromod/mixin-blocklist.json`).
- You extend it with your own `config/retromod/mixin-blocklist.json` (entries from both files merge):

  ```json
  {
    "blocked": [
      { "mixin": "com/example/mod/mixin/SomeMixin", "methods": ["someMixin$badHandler"] }
    ]
  }
  ```

  `mixin` is the mixin class (`/`- or `.`-separated). List the specific handler method(s) in `methods` to strip just those (preferred); omit `methods` to disable every injector on that class. The crash log names both the mixin class and the `…$mixinextras$bridge` handler.

Please [file an issue](https://github.com/Bownlux/Retromod/issues) with your log if you hit one. It can join the curated list so nobody else has to blocklist it by hand.

## Crash: "InvalidInjectionException: Invalid descriptor … Expected (X) but found (Y)"

A mod fails during construction with a mixin error shaped like:

```
Mixin apply for mod <mod> failed <mod>.mixins.json:SomeMixin -> net.minecraft.…:
InvalidInjectionException: Invalid descriptor on …@Inject::handler(…OldType…)!
  Expected (…NewType…) but found (…OldType…)
```

This happens when the mod's `@Inject` / `@Redirect` / `@ModifyArg` handler **captures a parameter whose type Minecraft changed** between the version the mod was built for and your host. The canonical case: `Entity.addAdditionalSaveData` / `readAdditionalSaveData` switched from `CompoundTag` to `ValueOutput` / `ValueInput` in the 1.21.5 serialization ("ValueIO" / codec) refactor, so a 1.21.1 mod's `@Inject(method = "addAdditionalSaveData")` still captures `CompoundTag`, and Mixin refuses to apply it on 1.21.11.

Retromod rewrites the mixin's **target** (it points at the right MC method), but it does **not** yet rewrite the handler's **captured parameter types or body**. It can't be a simple rename either, because `ValueOutput` has a different API shape than `CompoundTag` (the handler body would have to be rewritten, not just re-typed). This is a **known limitation**, and it's independent of any third-party library: GeckoLib and friends load fine, this is purely an MC-internal signature change.

**The confusing part is a misleading NPE downstream.** When a mod fails to construct, NeoForge marks the load as a **"broken mod state"** and silently skips every later client registration event (models, sprites, reload listeners). The game then crashes much later with something like a `NullPointerException` in `ModelManager.reload` / `PreparableReloadListener$SharedState.get`, which has *nothing* to do with the real cause. If you see that NPE, scroll **up** in the log for the first `Mixin apply for mod … failed` / `InvalidInjectionException` and a line like `Failed to wait for future Mod Construction, N errors found`. *That* earlier error is the one to fix.

**Workarounds:**
1. **Use a build of the mod made for your MC version** if one exists, since there's nothing to translate then.
2. **Blocklist the offending handler** (see the section above) so the mod loads with just that one feature inert. Stripping a save-data `@Inject` usually only loses some persisted extra data, not the whole mod. This works cleanly when the handler is *self-contained*; if the mixin's feature is spread across interdependent members (a `@Unique` field + interface + helper lambdas), stripping removes the injection error but the mod may still not construct, in which case it genuinely needs a build for your MC version.
3. File an issue with the log so the specific signature change can be added to Retromod's mixin-translation table.

## Crash: a transformed mod fails to construct with "ClassNotFoundException: net.minecraft.resources.ResourceLocation"

If a mod Retromod transformed dies during construction with `NoClassDefFoundError` → `ClassNotFoundException` for a **core Minecraft class that obviously exists** (commonly `net.minecraft.resources.ResourceLocation`), thrown from the mod's own `<clinit>` via NeoForge's `ModuleClassLoader`:

```
Failed to create mod instance. ModID: <mod>
java.lang.NoClassDefFoundError: net/minecraft/resources/ResourceLocation
Caused by: java.lang.ClassNotFoundException: net.minecraft.resources.ResourceLocation
  at net.neoforged.fml.classloading.ModuleClassLoader.loadClass(...)
```

…the class isn't missing at all, it's a **JPMS module-resolution** problem. A mod that ships no `module-info` or `Automatic-Module-Name` (MCreator output and many small mods) gets its module name *derived from the jar filename*, and spaces or other odd characters in that name break the module's ability to read core Minecraft.

**Fixed in beta.10.** Retromod now sanitizes the transformed jar's output filename so the derived module name is always valid. **Update to beta.10 or later.** (Workaround on older builds: rename the transformed jar in `mods/` to remove spaces and special characters.) NeoForge/Forge only; Fabric is unaffected. Reported in #47 (Luminous Nether).

## Forge: "needs language provider javafml:X or above" (e.g. javafml:52)

`javafml` is Forge's Java-mod language provider, and the number after the colon is the **Forge loader version**, not a separate library. So `javafml:52` means "Forge 52.x or later"; `javafml:47` means "Forge 47.x or later". Each MC version ships a specific Forge loader version: MC 1.20.1 → Forge 47, MC 1.21 → Forge 51, MC 1.21.1 → Forge 52, MC 26.1+ → Forge 64+.

**If you saw "javafml:52 or above to load We have found 47" on MC 1.20.1 with Retromod 1.0.0-beta.1:** that's a real bug in beta.1. The Forge `mods.toml` was hardcoded to demand Forge 52+ regardless of MC version, so the MC 1.20.x Forge builds couldn't load on the Forge that ships with those MC versions. **Fixed in beta.2.** Update to beta.2 (or later) and the error goes away.

**If you saw this on beta.2+ for a different MC version:** that's unexpected. The per-MC loaderVersion table in `build-all.sh` should match every Forge release. Please [open an issue](https://github.com/Bownlux/Retromod/issues) with your exact MC version and Forge loader version so we can fix the table.

## Forge 1.20.x: "Modules retromod and com.google.gson export package X to module minecraft"

The Java module system (JPMS), which Forge 1.20.1+ enforces strictly, refused to load because two modules both claim to export the same package to MC. This happens when Retromod's shaded JAR bundles a library (Gson, SLF4J, etc.) that Forge also bundles: both copies look like valid exporters and the resolver refuses to pick one.

**Fixed in builds after beta.2.** The fix is build-side: every bundled dependency is now relocated under `com.retromod.shaded.*` so it can't collide with Forge's modules. If you're on the broken beta.2 build that hit this, grab a newer build.

If you see this on a build that *should* be relocated, the relocation table in `pom.xml`'s `maven-shade-plugin` config might be missing a dependency. Open an issue with the exact package name from the error message and we'll add it.

## "Modules X and Y export package Z" naming *two non-Retromod mods* (a duplicate library)

If the two modules in the error are **both mod/library names** — e.g. *"Modules crackerslib.forge and crackerslib export package nonamecrackers2.crackerslib.client.config to module entityculling"* — you have **two copies of the same library** installed, and Java's module system refuses to load when both export the same package. This is almost always a setup issue, not a Retromod bug, and it crashes at the module-layer build *before* Retromod even runs, so Retromod can't paper over it.

The usual cause: a mod **bundles** a library inside itself (jar-in-jar — you'll see a log line like *"Found library file crackerslib-forge-….jar [parent: witherstormmod-….jar, locator: jarinjar]"*) **and** you also installed that library **standalone** in `mods/`. Often you added the standalone copy because an earlier launch complained the library was "missing."

**Fix: remove the standalone copy** of the duplicated library from `mods/` — the bundled one is already provided by the mod that ships it. Look at the two module names in the error: the one *with* a suffix like `.forge` is typically the bundled copy; remove the other (plain) standalone jar, or vice-versa, leaving exactly one.

If removing the duplicate leaves you with the *original* "library missing" crash, that's the real problem — the bundled library is a Forge build inside a NeoForge mod (or a jar-in-jar dependency Retromod hasn't translated). Translating Forge-API libraries onto NeoForge and recursing metadata-patching into jar-in-jar dependencies are both tracked for a future release; for now those mods are most reliable on a matching **Forge** instance. (#95)

## NeoForge: my old (1.20.1) mod doesn't appear at all

If a 1.20.1 (Neo)Forge mod simply isn't in the mod list (often with a log line like *"File X is for Minecraft Forge or an older version of NeoForge, and cannot be loaded"*), NeoForge skipped it at scan time, *before* Retromod's transforms run. NeoForge 1.20.2 renamed its metadata file from `META-INF/mods.toml` to `META-INF/neoforge.mods.toml`, and modern NeoForge ignores any jar that still ships only the old `mods.toml`.

**Fixed in beta.9.** On a NeoForge host, Retromod now promotes `mods.toml` → `neoforge.mods.toml` (and relaxes the loader-version and dependency), so the mod gets recognized. **Update to beta.9 or later.**

## NeoForge: a 1.20.1 *Forge* mod loads, then crashes (IForgeRegistry / FMLJavaModLoadingContext)

If a 1.20.1 mod gets scanned but then crashes during mod construction with `NoClassDefFoundError: …/FMLJavaModLoadingContext`, `NoSuchFieldError: NeoForgeRegistries … BLOCKS`, or similar registry errors, this is a **known limitation**, not a bug to chase.

1.20.1 was NeoForge's first release, when it still shared Forge's API (`ForgeRegistries`/`IForgeRegistry`, the old `DeferredRegister.create(IForgeRegistry, …)` signature, `FMLJavaModLoadingContext`, `net.minecraftforge.*` packages). NeoForge replaced all of it in 1.20.2+, so a 1.20.1 *Forge* mod uses APIs modern NeoForge no longer has. Translating it onto NeoForge is the entire Forge→NeoForge migration, which is **planned for Retromod 1.2.0**, not the rc line.

**What to do today:** run a Forge mod on a **Forge** host. On Forge 26.1.x those APIs still exist natively, so it's a within-loader version bump rather than a cross-loader rewrite. (NeoForge mods translate onto NeoForge fine; this is specifically *Forge mod → NeoForge host*.) Details: [Mods That Can't Be Translated]({{ '/incompatible-mods' | relative_url }}).

## Pre-26.1 host: old mod crashes on a 26.1-only name (`BeforeExtract`, `LevelRenderContext`, `ItemHandler`)

If you're translating onto a **pre-26.1** host (1.21.8, 1.21.1, etc.) and an old mod crashes with a `NoClassDefFoundError` / `VerifyError` naming a 26.1-only API (`ScreenEvents$BeforeExtract`, `WorldRenderContext`→`LevelRenderContext`, NeoForge `ItemHandler`/`FluidHandler`), early betas applied the 26.1 renaming shims regardless of host. **Fixed in beta.7.** Shims are now gated so a shim only runs when its target version is ≤ your host. **Update to beta.7 or later.** (Translating onto a 26.1 host was never affected.)

## "java.util.zip.ZipException: duplicate entry"

Some mods (especially older Forge mods that JIJ-bundled Fabric API modules into a Forge package, or mods built with legacy bundler toolchains) ship JARs whose central directory lists the same entry twice. Retromod's transformer used to crash mid-write on the second occurrence; fixed in **beta.2**.

If you see this on beta.1, the workaround is to update to beta.2 (or later). On beta.2+, Retromod logs a `Skipping duplicate JAR entry from source: ...` warning, keeps the first copy, and continues, so the transform succeeds.

## Retromod button is missing from the title screen

The mod didn't finish initializing. Check `latest.log` for `Retromod` messages; there'll usually be an exception explaining what went wrong. Common causes:

- A **conflicting mod** that also modifies the title screen. Retromod uses a mixin for the button; if another mod clobbers the same spot, one of them loses.
- **Wrong game directory**: the JAR is in the right folder for a different Minecraft instance than the one you're launching.
- **Fabric API missing**. Retromod requires Fabric API on Fabric installs.

## Game loads but transformed mods aren't being picked up

Make sure they're in `mods/`, not `retromod-input/`. `retromod-input/` is the inbox: after Retromod runs, successfully-transformed mods move to `mods/`. If your mod is stuck in `retromod-input/`, transformation failed, so check the log.

## AOT cache seems stale

After updating Retromod, delete `config/retromod/aot-cache/`. The cache's hash includes the source mod but not the Retromod version, so an old cache can produce old output even with a new Retromod JAR. Deleting the folder forces a fresh compile.

## Nothing helps, time to file a bug

Collect:

1. `latest.log` from your Minecraft game directory.
2. The relevant verify report (if any) from `config/retromod/verify-reports/`.
3. The mod JAR you're trying to load, or a link to it.
4. Your `config/retromod/config.json`.
5. Your Minecraft version, loader (Fabric / NeoForge / Forge), and loader version.

Attach to a [new issue](https://github.com/Bownlux/Retromod/issues/new). That set of files is enough to reproduce almost every bug.
