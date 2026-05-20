---
title: FAQ
nav_order: 10
---

# FAQ

Frequently asked questions about Retromod. If your question isn't here, check the other docs or [open an issue](https://github.com/Bownlux/Retromod/issues).

## Is Retromod safe?

Yes, within the usual caveats for running any third-party mod:

- **The code is open source** under the [MIT license](https://github.com/Bownlux/Retromod/blob/main/LICENSE). You can read every line before running it.
- **Releases are signed.** Retromod includes a [signature verifier]({{ '/authenticity' | relative_url }}) that distinguishes official builds from forks or tampered copies.
- **It modifies bytecode, nothing else.** Retromod doesn't talk to the network, doesn't touch files outside your game directory, and doesn't collect telemetry.
- **Transformation is in-process.** The transformer runs inside Minecraft's JVM at mod load time; there's no separate daemon, no autostart, no system integration.

The one meaningful caveat: Retromod *does* modify other mods' bytecode. If one of those mods does something shady, Retromod can't know — it just rewrites the names. Install mods from sources you trust.

## Does Retromod need internet access?

No — and it won't reach for it on its own. Retromod is **offline by default** as a safety choice. With factory settings, the mod runtime makes zero outbound network calls: no silent downloads, no telemetry, no update checks, no analytics, no error reporting. It works fine on an air-gapped machine.

There are two optional features that *can* use the network if you turn them on:

- **Modrinth native-version lookup** — checks whether a mod you're about to transform already has a version that supports your MC natively, so you can grab the official build instead. Off by default; enable by setting `check_for_native_versions: true` in `config/retromod/config.json`.
- **CLI API-archive download** — `retromod archive download <loader> <mc-version>` fetches an old Fabric / NeoForge / Forge API JAR from Maven so the embedder can pull classes out of it. Interactive — prompts you with the source URL before any HTTP request and won't proceed without a `y`. Skip the prompt for scripts with `--yes`.

That's the entire surface. Anything else you see in the network panel during a Retromod-using session is coming from Minecraft itself, your mod loader, or one of the mods being transformed — not from Retromod.

## Why MIT license?

A few reasons:

- **It's what modding culture expects.** Most Fabric and NeoForge mods are MIT or similarly permissive. Making Retromod compatible with the ecosystem was the priority.
- **Modpack authors can ship it without asking.** No attribution clause to trip up, no viral license to worry about.
- **Forks and derivatives are fine.** If somebody wants to build a different mod-compatibility layer on top of Retromod's shim engine, go for it.

The signature verifier exists precisely because the license is permissive — it lets users tell the original from derivatives without restricting anyone's right to make derivatives.

## Does it work on servers?

Yes. Server-side-only mods work exactly the same way client-side mods do — Retromod transforms their bytecode, patches their metadata, and the server loads them.

One nicety: **if a mod is server-only, clients connecting to the server don't need Retromod themselves.** The server handles the transformation; clients just see a vanilla-looking server that happens to have some extra behavior. Install Retromod on the server, leave clients alone.

Mods that have both client and server components need Retromod on both sides.

## Does it work with server software like Paper / Spigot / Bukkit / Purpur?

**Not yet.** Retromod targets the *mod loaders* — Fabric, NeoForge, and Forge. Server software like Paper, Spigot, Bukkit, and Purpur runs **plugins**, not mods, and the plugin format and APIs (Bukkit API, NMS) are a different shape from the mod APIs Retromod is built around. Dropping Retromod into a Paper server's `plugins/` folder won't do anything.

**Plugin support is planned for the future**, but development on it has not started yet. The current focus is closing out beta on the mod-loader side first; once that's stable, plugin support is the next major project. There's no committed timeline yet.

If you run a Paper/Spigot/Bukkit/Purpur server and want this, opening a [GitHub issue](https://github.com/Bownlux/Retromod/issues) describing your specific use case is the most useful way to weigh in on the priority.

## Does it work with modpacks?

Yes, and modpacks are actually one of the best use cases. Drop Retromod into the pack's `mods/` folder (or configure your pack launcher to include it), put your old mods in `retromod-input/`, and on first launch everything gets transformed and moved to `mods/`.

For CurseForge / Modrinth / ATLauncher packs specifically: include Retromod as a regular mod dependency, and add a post-install step (if your launcher supports it) that copies old mods into `retromod-input/` instead of `mods/`. Or skip the auto-install and tell users to use the in-game file picker.

## How does Retromod handle a mod's dependencies? Old version or new?

Translate the **whole set together**, and use the dependency version the mod was built against — not a new one.

Retromod transforms a mod's bytecode (and the bytecode of any library it bundles via Jar-in-Jar) so it runs on the target MC. It does **not** swap a mod's dependency for a newer release. So:

- **Bundled (JiJ) dependencies** are handled automatically — they ride along inside the mod JAR and get transformed with it.
- **Separate library mods** (Cardinal Components, Architectury, Cloth Config, etc.) should be run through Retromod too: put the **old** version — the one your mods were built for — into `retromod-input/` alongside them. Retromod translates that old library up to the target MC.

What you should *not* do is install a brand-new native build of a library next to old mods that depend on it. New major versions often rename packages or change their API, and the old mod is still calling the old one. The classic example is Cardinal Components: the project moved from `dev.onyxstudios.cca` (CCA 5.x and earlier) to `org.ladysnake.cca` (CCA 6.x). A mod built for the old API references `dev.onyxstudios.cca.*`; dropping in a modern `org.ladysnake` CCA next to it doesn't satisfy that — and if the mod *also* bundles its own old copy, you now have two CCAs fighting. Translate the old CCA with Retromod and remove the standalone modern one.

Rule of thumb: if mod A was released for MC 1.20.1, gather A **and its dependencies as they existed for 1.20.1**, drop them all in `retromod-input/` together, and let Retromod move the whole set forward at once.

## Can I commercialize a modpack using Retromod?

Yes. MIT license, no strings attached. You can:

- Bundle Retromod in a paid modpack.
- Include it in a donation-required server pack.
- Redistribute it under whatever branding your pack uses.
- Modify it for your needs.

You have to preserve the copyright notice and MIT license text somewhere in the distribution — that's the only requirement. A `LICENSE` file alongside the JAR, or a mention in the pack's credits, is enough.

## What Minecraft versions are supported?

- **Target:** Minecraft 26.1.2. This is the version your transformed mods run *on*.
- **Source:** mods built for 1.12.2 and newer. That's the version the mod was originally released for.

So: you can take a Fabric mod built for 1.20.1 and run it on 26.1.2. You can take a mod built for 1.16.5 and run it on 26.1.2. 1.12.2 is the oldest source we currently support.

Older source versions (pre-1.12.2) aren't supported because the mod ecosystem shifts fundamentally around that point — Forge API differences, mixin maturity, etc. That's future work.

Newer target versions will be added as Minecraft releases them. Retromod is built around 26.1 specifically because it's the first fully unobfuscated MC release, which makes the mapping work straightforward.

## Why does verification say "unsigned"?

Because the current beta builds aren't signed. Signing infrastructure is on the roadmap for the 1.0 release. In the meantime, UNSIGNED is expected on every build you see — it's not an error or a warning about your JAR specifically, it's just the current state of the project.

See [Authenticity]({{ '/authenticity' | relative_url }}) for the full breakdown of what each status means.

## Does Retromod work with OptiFine / Sodium / Iris?

Partial compatibility. Sodium and Iris are mostly fine — they target modern MC APIs, which Retromod doesn't interfere with. OptiFine is trickier because it transforms the game in ways that overlap with what Retromod does; there's a dedicated `compat/OptiFineCompat.java` that handles some of the friction, but YMMV. Test with your specific rendering mod combo before committing to it.

## Will it work for [specific mod]?

The honest answer: try it and see. Simple content-only mods (items, blocks, recipes) almost always work. Mods that hook MC's internals for rendering, physics, or networking are hit-or-miss. Mods with extensive reflection or dynamic classloading may need `remap_reflection` enabled (it's on by default).

The best way to find out is to drop the mod in `retromod-input/`, launch, and check `config/retromod/verify-reports/` for that mod. If verification passes, odds are good. If it doesn't, the report tells you what's missing.

## Is there a Discord / Matrix / forum?

Not yet. Right now all discussion happens on the [GitHub issue tracker](https://github.com/Bownlux/Retromod/issues). For bugs and feature requests, open an issue. For general questions, open a [discussion](https://github.com/Bownlux/Retromod/discussions).

## Can I contribute?

Please do. See [Contributing]({{ '/contributing' | relative_url }}) for the workflow and the set of skills (under `.claude/skills/`) that walk you through common tasks like adding a shim or a polyfill.
