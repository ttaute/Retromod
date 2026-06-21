# Retromod Test Mod

A small, deliberately-old Minecraft mod whose only purpose is to exercise
Retromod's transformation pipeline and report which features still work after
translation.

## Why this exists

Testing Retromod by hunting down third-party mods (modmenu 3.0.1, sodium 0.4,
etc.) is painful: those mods are hard to find, half of them depend on libraries
that themselves need translating, and a failure could mean the mod's bug, the
library's bug, or Retromod's bug. Hard to bisect.

This test mod is the opposite. The source is small, controlled, and writes
**one log line per test**, so a `grep '\[Retromod-Test\]'` of the launch log
tells you exactly which transformer paths are still working. When Retromod
breaks, we know which test broke and what code triggered it.

## Design

- **Compiled against an old MC version on purpose** - the whole point is that
  Retromod has to translate it forward to whatever MC version the user is on.
  First iteration targets MC **1.20.1 Fabric** (clean Yarn mappings, well-supported
  toolchain, big enough gap to 26.1 to exercise most of the redirect surface).
- **One test = one observable behavior**. Each test logs
  `[Retromod-Test] N (description): success` or `[Retromod-Test] N (description): fail: <reason>`.
- **Loader-agnostic test logic.** The tests themselves don't know which loader
  they're running on. Loader-specific entrypoints are thin wrappers that call
  the same `TestRunner.runAll()`.
- **One `RUN_ID` per launch** stamped at the top of each test line, so a single
  log file with multiple launches is still easy to bisect.

## Build

```bash
cd retromod-test-mod
./gradlew build
# output: build/libs/retromod-test-mod-<version>.jar
```

## Why does the Test Mod use Gradle when Retromod itself uses Maven?

Two answers - a practical one, and a "this is supposed to look representative" one.

### The practical one

Retromod's main project doesn't compile against Minecraft. It works on bytecode reflectively and via ASM, so its dependencies are plain library JARs (ASM, Gson, SLF4J, Fabric Loader as `provided`). Maven handles that fine because it's just normal-Java dependency resolution.

The test mod is the opposite - it **must** compile against Minecraft. Doing that means resolving:

- The MC client JAR for a specific version
- The matching mappings (Yarn, Mojang, or whatever you target)
- The deobfuscated/remapped artifacts
- The Loader classpath for dev-time
- A working `runClient` task that boots MC with your mod loaded

The MC modding ecosystem standardized on **Gradle plus a loader-specific plugin** to do all of that: Fabric Loom for Fabric, ForgeGradle for Forge, ModDevGradle (or NeoGradle) for NeoForge. These plugins handle MC artifact resolution, mapping deobfuscation, runtime spawning, and the dev-environment classpath transparently. They're tested across thousands of mods and know how to talk to Mojang's Piston servers, the Fabric maven, the Forge files server, etc.

There's no Maven equivalent. You can hand-roll all of that with `maven-dependency-plugin`, custom Mojo executions, and a lot of XML - but you'd be reinventing what Loom already does correctly in 30 seconds, and you'd own all the bug fixes for the rest of time. Not a great tradeoff.

So: Maven for Retromod proper (because it doesn't touch MC), Gradle for the test mod (because it does). Both are right for their own scope.

### The "looks representative" one

The test mod is supposed to behave like a normal Minecraft mod, because Retromod has to be able to translate normal Minecraft mods. If the test mod were built with some bespoke Maven setup, its bytecode might end up subtly different from what comes out of a typical Loom build - different mapping conventions, different inner-class shapes, different access widening, different mixin layouts - and any Retromod bug we ship would conveniently miss the actual mods people care about.

Building the test mod with the same toolchain real mods use (Gradle + Loom) gives us bytecode that looks exactly like the bytecode in the wild. When the test mod surfaces a bug, that bug is in territory that affects real users, not in a corner of the build space only this project visits.

### Bonus

The same Gradle source tree extends naturally to multi-loader builds (a Forge + NeoForge entry point sub-project sharing the test logic) using sibling modules and the `loom-quiltflower` / `architectury` ecosystem if we ever go that route. Same trick is hard to do cleanly in Maven without a lot of profile gymnastics.

## Run

1. Drop `retromod-test-mod-<version>.jar` into your `.minecraft/retromod-input/`
   on a 26.1 Fabric instance with Retromod installed.
2. Launch - Retromod transforms it, restart popup appears.
3. Restart.
4. `grep '\[Retromod-Test\]' logs/latest.log` to see results.

Expected output (current suite is 250 tests across 19 categories):

```
[Retromod-Test] RUN_ID=ab12cd34  (250 tests)
[Retromod-Test] 1 (mod loaded): success
[Retromod-Test] 2 (Text.literal): success
[Retromod-Test] 3 (Text.translatable): success
...
[Retromod-Test] 250 (super.keyPressed INVOKESPECIAL): success
[Retromod-Test] SUMMARY: 250/250 passed
```

A failure looks like:

```
[Retromod-Test] 5 (Text.copy().append()): fail: IncompatibleClassChangeError: ...
[Retromod-Test] SUMMARY: 249/250 passed (1 failed)
```

## Test categories

Each category lives in its own class under `tests/`. Adding a test to an
existing category is one line of `new SimpleTest(...)`. Adding a new category
is a class with a static `all()` method, plus one line in `TestRunner.buildSuite()`.

| Category | Count | What it covers |
|---|---|---|
| `BasicTests` | 1 | Mod entrypoint reached |
| `TextTests` | 5 | `Text` / `Component` - `literal`, `translatable`, `empty`, `copy().append()`, `formatted` |
| `IdentifierTests` | 3 | `Identifier` / `ResourceLocation` - 2-arg ctor, 1-arg colon form, `tryParse` |
| `RegistryTests` | 5 | `Registries.BLOCK`/`ITEM`/`ENTITY_TYPE`/`FLUID`/`SOUND_EVENT` lookups |
| `BlockItemTests` | 8 | Sample `Blocks` and `Items`, `BlockState`, `ItemStack` |
| `BlockTests` | 38 | Wide coverage of `Blocks.*` static fields |
| `ItemTests` | 32 | Wide coverage of `Items.*` static fields |
| `EntityTypeTests` | 26 | `EntityType.*` static fields |
| `EnchantmentTests` | 16 | `Enchantments.*` (registry-keyed in newer MC) |
| `StatusEffectTests` | 19 | `StatusEffects.*` / `MobEffects.*` |
| `EnumTests` | 35 | `Direction`, `Hand`, `EquipmentSlot`, `ActionResult`, `Formatting`, `GameMode`, `Difficulty` |
| `MathTests` | 7 | `BlockPos`, `Vec3d`, `Direction`, `Box` |
| `NbtTests` | 4 | `NbtCompound`, `NbtList` round-trips |
| `SoundParticleTests` | 4 | `SoundEvents.*`, `ParticleTypes.*` |
| `GuiTests` | 3 | `MinecraftClient`, `ButtonWidget.builder` factory chain |
| `LoaderTests` | 4 | Fabric Loader API |
| `CodecTests` | 6 | DFU `Codec`, `JsonOps`, encode round-trip |
| `TagTests` | 9 | `BlockTags.*`, `ItemTags.*`, `TagKey.id()` |
| `MiscApiTests` | 24 | `Util`, `MathHelper`, `Random`, `SoundCategory`, additional sounds/particles |
| `Test05SuperKeyPressed` | 1 | INVOKESPECIAL super-call on remapped method (the ModMenu bug) |
| **Total** | **250** | |

The standalone `Test05SuperKeyPressed` lives outside the category list because
it needs its own inner `Screen` subclass to exercise the bytecode path. Tests
that need their own supporting classes always go in their own file; tests
whose body is a few lines of API call go in the appropriate category file as
a `new SimpleTest("description", () -> { ... })` entry.

## Roadmap

The first iteration is intentionally narrow so it's easy to verify and extend.

- [x] **v0.1**: Fabric, MC 1.20.1 source → translates to 26.1+. ~5 tests.
- [x] **v0.2**: ~45 tests across 11 categories.
- [x] **v0.2.1**: Three lifecycle phases (`init` / `client-started` / `world-join`), 250 tests. Currently 250/252 passing - only known failures are Test 5 (`getString()` toString format) and Test 14 (SOUND_EVENT descriptor mismatch, documented).
- [~] **v0.3 (in progress)**: Forge and NeoForge entry-point classes + manifest files added. Build wiring (separate per-loader Gradle subprojects with ForgeGradle / ModDevGradle plugins) is the remaining piece - see "Multi-loader build" below.
- [ ] **v0.4**: Multi-version source (1.16.5, 1.18.2, 1.20.1, 1.21.1) - separate sub-projects sharing a common test interface. Each one exercises a different translation distance.
- [ ] **v0.5**: Mixin compatibility tests (a `@Mixin` with `@Inject` at HEAD of a known method).
- [ ] **v0.6**: Polyfill coverage tests (call into specific removed APIs we know we polyfill).
- [ ] **v0.7**: World/player/entity tests that need a fully-loaded world.

## Multi-loader build (v0.3 work-in-progress)

What's done:

- `src/main/java/com/retromod/testmod/forge/RetromodTestModForge.java` - `@Mod`-annotated entry point, calls `TestRunner.runImmediate()` from its constructor.
- `src/main/java/com/retromod/testmod/neoforge/RetromodTestModNeoForge.java` - same shape, NeoForge variant.
- `src/main/java/net/minecraftforge/fml/common/Mod.java` - compile-time stub for Forge's `@Mod` annotation. Real Forge's class shadows this at runtime.
- `src/main/java/net/neoforged/fml/common/Mod.java` - same trick for NeoForge.
- `src/main/resources/META-INF/mods.toml` - Forge mod manifest.
- `src/main/resources/META-INF/neoforge.mods.toml` - NeoForge mod manifest.

What's not done (and why):

The current `build.gradle` produces a Fabric JAR via Loom. Producing Forge and NeoForge JARs from the same source needs each loader's own Gradle plugin (ForgeGradle for Forge, ModDevGradle / NeoGradle for NeoForge), which can't co-exist in a single Loom-rooted project. The standard fix is one of:

1. **Architectury / Architectury Loom** - a multi-loader plugin that runs a "common" subproject + per-loader subprojects. Substantial restructure, but the conventional MC-mod-multi-loader path.
2. **Three sibling Gradle projects** at `retromod-test-mod/fabric/`, `retromod-test-mod/forge/`, `retromod-test-mod/neoforge/`, each with its own toolchain, all referencing a shared `common/` source set. Less plugin coupling than Architectury but more boilerplate.

Either approach moves the existing Fabric content into a `fabric/` subproject and shares the test logic via `common/`. The Forge/NeoForge entry-point classes already in this project are written against compile-time stubs so they can move to their own subprojects without rewrites.

Until the multi-loader build is wired, the Forge/NeoForge entry-point classes serve as documentation of the runtime shape - anyone setting up the per-loader builds can copy them into the right subprojects.

## Adding a test

1. Either add a `new SimpleTest("description", () -> ...)` entry to the appropriate category class, or write a standalone `Test` implementor if you need supporting classes.
2. If standalone, register it in `TestRunner.buildImmediateSuite()` (or `buildClientStartedSuite()` / `buildWorldJoinSuite()` if it's deferred).
3. Update the category table above.
