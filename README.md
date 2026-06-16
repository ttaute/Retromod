# Retromod

> Run older Minecraft mods on newer versions through bytecode transformation and API shimming.

[![Java 25+](https://img.shields.io/badge/Java-25+-blue.svg)](https://adoptium.net/)
[![Minecraft 26.1 | 26.2](https://img.shields.io/badge/Minecraft-26.1%20%7C%2026.2-green.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.1.0--rc.1-blueviolet.svg)]()

**Made by the developers of [RevivalSMP.net](https://revivalsmp.net)**

---

## TL;DR

- **What it is** — a drop-in mod (and a CLI) that rewrites old mods' bytecode at load time — renamed methods, moved classes, removed APIs, broken mixins, version-range rejections — so they run on modern Minecraft. **Fabric, NeoForge, Forge.**
- **Versions** — translates mods built for **1.12.2 and up** onto a host running **MC 1.20 → 26.2** (Fabric/NeoForge reach 26.2; Forge tops out at 26.1 until its 26.2 loader ships).
- **Install (Fabric)** — put the jar in `mods/`, launch once, drop old mods in `retromod-input/`, launch, restart. **Forge/NeoForge:** jar + old mods both in `mods/`, launch, restart.
- **MC 26.2 → use OpenGL.** Vulkan is 26.2's new default and breaks old mods' OpenGL rendering; Retromod auto-selects OpenGL for you (Video Settings → Graphics API → OpenGL to confirm).
- **Status** — 1.0.0 is stable; **1.1.0-rc.1** is the current release candidate (26.x coverage). Keep backups of your mod JARs.
- **Won't work for** deep-integration / rendering mods (Create, OptiFine, …) — [why](docs/incompatible-mods.md).
- **Help** — [Troubleshooting](docs/troubleshooting.md) · [FAQ](docs/faq.md) · [Compatibility](COMPATIBILITY.md) · [Changelog](CHANGELOG.md) · [Issues](https://github.com/Bownlux/Retromod/issues)

> **Fabric is the primary loader** — most coverage and the fastest fixes land there. NeoForge is close behind; Forge works but lags (SRG-obfuscated Forge mods need extra handling still on the roadmap). Picking a loader fresh? Pick Fabric.

---

## Quick Start

### Fabric

1. Put `retromod-1.1.0-rc.1+<your-mc>.jar` in `mods/`.
2. Launch once and close it — this creates the `retromod-input/` folder.
3. Drop your old mods into `retromod-input/` (directly, **not** its `processed/` subfolder).
4. Launch again — Retromod converts them and shows a restart prompt.
5. Restart — done.

Fabric checks mod versions *before* Retromod runs, so old mods can't go straight into `mods/` — `retromod-input/` is the inbox Retromod converts from. (One-shot via CLI: `java -jar retromod-cli.jar prepare ~/.minecraft --aot`.)

### Forge / NeoForge

1. Put Retromod in `mods/`; drop old mods in `mods/` too (or `retromod-input/`).
2. Launch — Retromod converts incompatible mods and patches their metadata; restart when prompted. Originals are backed up to `mods/retromod-backups/`.

**Uninstall:** remove `retromod-*.jar` from `mods/`; if needed, restore originals from `mods/retromod-backups/` or `retromod-input/processed/`.

There's no `.exe`/`.app`/installer — Retromod is a Minecraft mod, loaded by your mod loader like any other. To transform mods *outside* the game (server prep, batch), use the [CLI](#cli-tool).

---

## Supported Versions

> **Primary target: MC 26.1+.** Retromod is one codebase — the whole pipeline (iterative transform loop, verifier, mixin passes, reflection remap, …) works identically on 1.20, 1.21.x, and 26.x. New shims, polyfills, and testing prioritize 26.1+ (the first un-obfuscated MC), but pre-26.1 still works and gets every core fix.

- **Host MC** (where Retromod runs) must be **1.20+**.
- **Source MC** (what the mod was built for) is walked through chainable shims to your host in one pass; in-between versions resolve to the nearest milestone via fuzzy matching.

| Loader | Host MC | Translates mods from | Maturity |
|---|---|---|---|
| **Fabric** | 1.20 → 26.2 | 1.14.4+ | 1.16.5+ stable · 1.14–1.15 experimental |
| **NeoForge** | 1.20.1 → 26.2 | 1.20.1+ | Stable |
| **Forge** | 1.20 → 26.1 | 1.12.2+ | 1.16.5+ stable · 1.12–1.15 experimental |

> Forge has no MC 26.2 build yet, so its host tops out at 26.1 until one ships (the Fabric/NeoForge 26.2 jars are already built).

> **On MC 26.2, use the OpenGL renderer.** 26.2 makes the new **Vulkan** backend the default, but translated old mods that do their own OpenGL rendering can glitch, render nothing, or crash on Vulkan — they need the still-present OpenGL backend. Retromod sets `graphicsApi:opengl` automatically the first time it runs on a 26.2+ client (it won't override a backend you picked yourself); to set or confirm it manually, use **Video Settings → Graphics API → OpenGL**. Opt out with `-Dretromod.graphics.noPreference=true`. (No-op on macOS, which runs OpenGL-over-Metal anyway. OpenGL is expected to be removed in 26.3 — see the [roadmap](ROADMAP.md).)

Experimental chains (1.12.2–1.15.2) cross enormous API breaks like The Flattening — many mods work, but expect rough edges. Stable chains (1.16.5+) translate cleanly for the vast majority of mods.

---

## Key Features

- **534+ bytecode redirects** (class / method / field / constructor / accessor), **170+ version shims** (1.12.2 → 26.2), and **72+ polyfills** that reimplement removed APIs against modern equivalents (so old mods *work*, not just load).
- **Intermediary → Mojang mapping** for 26.1+ — 8,800+ intermediary names composed with 600+ 26.1 package moves — plus reflection-string remapping and bridge-method synthesis.
- **Smart mixin compatibility** — relocates broken targets and soft-fails bad injections so one bad mixin can't crash the game.
- **34+ modding APIs handled** (Fabric API, REI/JEI, Cloth Config, Curios/Trinkets, GeckoLib, Architectury, …) + legacy bridges (Baubles→Curios, NEI→JEI, RF→Forge Energy, WAILA→Jade) + version-constraint relaxation for 60+ mod IDs.
- **Hybrid AOT + JIT** with a persistent cache: a brief multi-core burst at first launch (`-Dretromod.parallelism=N`), near-zero impact afterward.
- **Diagnostics** — per-mod reference verifier, cross-mod gap report, a 0–100 compatibility score, and `devhelp` for mod authors.
- **Offline by default** — no network access, telemetry, or update checks unless you explicitly opt in.
- **In-game UI** — a title-screen gear button opens mod management + settings.
- **One JAR, broad runtime** — bytecode targets Java 17 (runs on 17 / 21 / 25); each per-MC jar declares the Java level MC itself needs (26.x → Java 25). x86_64 + ARM64.

---

## Documentation

| Doc | What's in it |
|---|---|
| **[Compatibility list](COMPATIBILITY.md)** | Which mods load after transformation, by source MC version, plus a recommended test setup. |
| **[Mods That Can't Be Translated](docs/incompatible-mods.md)** | The realistic "no" list — Create, OptiFine, rendering frameworks — and the rules behind it. |
| **[Troubleshooting](docs/troubleshooting.md)** | Common crashes and confusions (restart, Java errors, mixins, NeoForge metadata, 26.2 rendering) with fixes. |
| **[FAQ](docs/faq.md)** | Safety, servers, modpacks, dependency handling, "will it work for my mod?", which renderer on 26.2. |
| **[Installation](docs/installation.md)** | Per-launcher setup (Prism, MultiMC, the vanilla launcher). |
| **[API Compatibility](API_COMPATIBILITY.md)** · [Config](docs/config.md) · [CLI](docs/cli.md) · [Architecture](docs/architecture.md) | The shimmed APIs, every config flag, the CLI reference, and how the pipeline works. |
| **[Changelog](CHANGELOG.md)** · [Roadmap](ROADMAP.md) | What shipped, and what's planned. |

Mod won't convert, or a question not answered? [Open an issue](https://github.com/Bownlux/Retromod/issues) or start a [discussion](https://github.com/Bownlux/Retromod/discussions).

---

## CLI Tool

A standalone CLI transforms mods outside the game — ideal for servers, batch prep, and analysis (zero in-game overhead):

```bash
retromod analyze mymod.jar        # compatibility check (also: score)
retromod aot mymod.jar            # pre-transform (AOT)
retromod batch mods/ --aot        # whole folder
retromod prepare ~/.minecraft --aot
retromod devhelp mymod.jar 26.1   # what API changes a mod author needs
```

Also runnable as `java -jar retromod-*-cli.jar <command>`, or as a Java agent (`-javaagent:retromod-agent.jar`). Use the CLI for pre-transformed/server setups; use the in-game mod for a drop-in experience (they compose — pre-transform with the CLI, keep the mod as a JIT safety net). Full reference: [docs/cli](docs/cli.md).

---

## Building

Build with **Java 25** (so ASM 9.8 can read MC 26.1's class format); the output targets Java 17, so the *same* jar runs on Java 17 / 21 / 25 when translating onto older Minecraft.

```bash
git clone https://github.com/Bownlux/Retromod.git && cd Retromod
./build.sh         # macOS / Linux  (build.bat on Windows)  → dist/
./build-all.sh     # JARs for every MC version × loader
```

`mvn clean package` does the same directly. Retromod doesn't compile against Minecraft (it's pure ASM/bytecode), so there's no Loom/Gradle setup — the project intentionally uses **Maven**, with a single `main` branch + release tags (one JAR has to translate every supported version, so per-version branches would mean N-way merges for every change).

**Quilt:** use the **Fabric** jar — Quilt runs Fabric mods natively, so there's no separate Quilt build. Quilt-native mods (`quilt.mod.json`) are still translated when staged in `retromod-input/`.

---

## For Mod Developers

- **Opt out of transformation:** add an empty `src/main/resources/META-INF/retromod-opt-out` to your mod. Retromod logs a skip and copies the JAR verbatim (users can override with `-Dretromod.honorOptOut=false`).
- **Detect Retromod:** `FabricLoader.getInstance().isModLoaded("retromod")`.
- **See what to migrate:** `retromod devhelp your-mod.jar 26.1` lists every rename/field change you'd need.
- **Add a shim or polyfill:** see [Contributing](#contributing).

---

## Known Limitations

- **Deep-integration / rendering mods don't translate** — Create (ships Flywheel's native GL), OptiFine, Applied Energistics 2, Tinkers' Construct, rendering frameworks. Bytecode rewriting can't bridge native code or a rebuilt render pipeline; those need an author port. [Full list + rules](docs/incompatible-mods.md).
- **MC 26.2:** use the OpenGL renderer (see above). **26.3** is expected to remove OpenGL entirely — tracked on the [roadmap](ROADMAP.md).
- **Forge → NeoForge migration is experimental** — package renames work; the rewritten capability / networking / registry systems mostly don't.
- **1.12.2–1.15.2 chains are experimental** (The Flattening renamed nearly everything) — the least-reliable part of the project.
- **Can't fix Java-version mismatches** — a mod compiled for newer Java than you have fails with `UnsupportedClassVersionError` before Retromod runs; install the Java your MC needs.
- Already-loaded classes need agent mode; complex non-standard mixins may need manual shim work; resource/data-pack conversion is alpha.

---

## Contributing

- **Shim:** add to `src/main/java/com/retromod/shim/`, register in `META-INF/services/com.retromod.core.VersionShim`, add tests, open a PR.
- **Polyfill:** add a `PolyfillProvider` under `src/main/java/com/retromod/polyfill/` (reimplement the removed API at its original package path), register in `META-INF/services/com.retromod.polyfill.PolyfillProvider`, open a PR.

Adding SRG → Mojang mappings is an [easy first contribution](docs/srg-mappings.md).

---

## Badge for Translated Mods

If you used Retromod to translate your mod, you can add this badge to your mod page:

```
[![Translated with Retromod](https://img.shields.io/badge/Translated_with-Retromod-blue?style=for-the-badge)](https://modrinth.com/mod/retromod)
```

```html
<a href="https://modrinth.com/mod/retromod"><img alt="Translated with Retromod" height="56" src="https://img.shields.io/badge/Translated_with-Retromod-blue?style=for-the-badge"></a>
```

---

## License

[MIT License](LICENSE) — Copyright (c) 2026 Bownlux

## Credits

**Bownlux** (author) · **[RevivalSMP.net](https://revivalsmp.net)** (development) · **[ASM](https://asm.ow2.io/)** · **[FabricMC](https://fabricmc.net/)** · **[NeoForged](https://neoforged.net/)** · **[MinecraftForge](https://minecraftforge.net/)**

**Mapping data:** Retromod's bundled name tables are derived/transformed works composed from Minecraft obfuscation mappings © Mojang Studios / Microsoft (used for development & interoperability), Fabric intermediary ([FabricMC/intermediary](https://github.com/FabricMC/intermediary), CC0-1.0), and Forge SRG ([MinecraftForge/MCPConfig](https://github.com/MinecraftForge/MCPConfig), © Forge Development LLC). They are *not* redistributions of the original mapping files.

*Retromod is not affiliated with, endorsed by, or sponsored by Mojang, Microsoft, FabricMC, NeoForged, or MinecraftForge. Minecraft is a trademark of Mojang Synergies AB.*
