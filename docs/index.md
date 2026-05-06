---
title: Home
layout: default
nav_order: 1
description: "Run old Minecraft mods on new Minecraft versions. Bytecode-level compatibility layer for Fabric, NeoForge, and Forge."
permalink: /
---

# RetroMod

Run old Minecraft mods on new Minecraft versions. It transforms mod bytecode at load time so a mod built for, say, 1.16.5 can run on 26.1 without recompiling anything or asking the original author to update.

It works on Fabric, NeoForge, and Forge. The shim chain covers MC 1.12.2 (the Java 8 floor) through 26.1.

<div class="retromod-hero-cta" style="margin: 1.5em 0;">
  <a class="retromod-btn retromod-btn-primary" href="https://github.com/Bownlux/RetroMod/releases">Download</a>
  <a class="retromod-btn retromod-btn-secondary" href="https://github.com/Bownlux/RetroMod">Source on GitHub</a>
  <a class="retromod-btn retromod-btn-ghost" href="{{ '/installation' | relative_url }}">Install guide →</a>
</div>

> **Heads up — this is a beta.** The pipeline works for the common case and for most simple-to-moderate mods. Deep-integration mods (rendering replacement, heavy mixin mods, mods with cross-version library quirks) still surface bugs in the transformer that I'm working through. If a mod doesn't work, please [file an issue with the log](https://github.com/Bownlux/RetroMod/issues) — that's the fastest way to make the next version handle it.

## What it actually does

When you launch Minecraft with RetroMod installed, it scans the `retromod-input/` folder for old mod JARs. For each one it:

1. Reads the bytecode and figures out which MC version it was built for.
2. Walks it through a chain of small per-version "shims" that rewrite class names, method signatures, and field references step by step until the bytecode targets the MC version you're running.
3. Patches the mod's metadata (`fabric.mod.json` / `mods.toml`) so the loader stops complaining about version ranges.
4. Writes the transformed JAR into your `mods/` folder. Original is backed up to `retromod-backups/`.

Next time you launch, the transformed mod loads like any other. There's an in-game button on the title screen for the mod-management screen, but most people never need it — the whole thing is supposed to be invisible once it's set up.

The CLI tool can do the same thing outside Minecraft (useful for batch-processing whole mod folders or checking compatibility before launch), but it's a power-user thing — see the [CLI page]({{ '/cli' | relative_url }}) if you want it.

## What it doesn't do

Honest list, because the README features section can't be the whole story:

- **Very deep-integration mods will probably never work, no matter how mature RetroMod gets.** The clearest example is **Create**. Mods like Create touch thousands of MC API points across the entire surface — rendering, physics, networking, world generation, animation — and ship their own rendering library (Flywheel) that includes platform-specific shader and GL code. RetroMod can rewrite Java bytecode for renamed classes and shifted method signatures, but it cannot translate native code, and it cannot synthesize new mixin injection points where the underlying bytecode has been entirely rewritten. Other mods in this category: heavy tech mods like Applied Energistics 2, Tinkers' Construct, IndustrialCraft, and any mod that meaningfully replaces MC's rendering pipeline. If your modpack is built around one of those, RetroMod is not the right tool — you'll need the actual mod author to ship a port for the new MC version.
- **Mods that completely replace MC's rendering pipeline** (Sodium, Iris) often have mixins targeting specific bytecode positions that have moved. The transformer can rename a method, but it can't synthesize an injection point that's been rewritten away. These are the hardest cases and often need per-mod hand work.
- **Mods that lock to specific cross-version library versions** (Cardinal Components 5.x package vs 6.x package, for example) can hit conflicts that no general-purpose shim resolves. The fix is usually to install the right library version standalone.
- **Constructor signature changes** in MC's API are sometimes silently broken — RetroMod's redirect table doesn't yet cover every constructor that's gained or lost a parameter between versions. Working through these as the gap report surfaces them.
- **Mods that authors have explicitly opted out of transformation** are skipped on purpose. See the [contributing guide]({{ '/contributing' | relative_url }}) for how that works.

If you're using mostly content mods, QoL mods, equipment-slot mods, recipe viewers, and the like — most of those translate cleanly. If you're trying to get a heavily-modded shaderpack-and-50-mods setup running with Create at its core, expect rough edges and probably broken renderers.

## A note on the version cutoff

Active development targets **MC 26.1+**. Builds for earlier host MC versions (1.20, 1.21.x) still get the same core pipeline improvements — it's all one codebase — but new shim entries, new API coverage, and testing time go to the 26.1+ branch. MC 26.1 was the first Minecraft release without code obfuscation, and that's a big enough inflection point for a mod-translation tool that splitting effort across both worlds didn't make sense. Old MC users aren't abandoned; they're just on a slower track for new compatibility coverage.

## Pages

The wiki is small but covers the things people ask about most.

- [Installation]({{ '/installation' | relative_url }}) — get it running on your launcher.
- [Configuration]({{ '/config' | relative_url }}) — every option, defaults, and when to flip them.
- [In-game UI]({{ '/gui' | relative_url }}) — the title-screen button and the screens behind it.
- [CLI tool]({{ '/cli' | relative_url }}) — for power users who want to transform mods outside MC.
- [Verify transforms]({{ '/verify-transforms' | relative_url }}) — the gap report and what it tells you.
- [Troubleshooting]({{ '/troubleshooting' | relative_url }}) — common error messages, what they mean, what to do.
- [FAQ]({{ '/faq' | relative_url }}) — safety, servers, modpacks, commercial use, the legal stuff.
- [Architecture]({{ '/architecture' | relative_url }}) — for people reading the source.
- [Technical]({{ '/technical' | relative_url }}) — the security model, fork policy, and how the transformer works inside.
- [Authenticity]({{ '/authenticity' | relative_url }}) — how to tell an official build from a tampered one.
- [Contributing]({{ '/contributing' | relative_url }}) — how to add shims and polyfills, and the mod-author opt-out mechanism.
- [Adding SRG Mappings]({{ '/srg-mappings' | relative_url }}) — fill gaps in the SRG → Mojang dictionary so more old Forge mods load. Easy first PR.
- [Mods That Can't Be Translated]({{ '/incompatible-mods' | relative_url }}) — the realistic "no" list. Specific mods (Create, OptiFine, AE2, …) and the general rules that put them there.

## Why I made this

I run a public Minecraft server on [Revival Hosting](https://revivalsmp.net) — To run mods at all, the server has to stay on whichever MC version the mod loadout actually supports, and that's rarely the latest version Mojang has shipped. Two problems come from that:

1. Even when every mod I want does agree on a single version, the agreed-on version is usually something like 1.21.1 — not 26.1.2. That's a year's worth of MC features (new blocks, new mobs, gameplay changes) the server doesn't get, just because the mods aren't there yet.
2. Worse, sometimes the mods don't even agree with each other. One mod I want only works on 1.21.7, another only on 1.21.1, and no single MC version runs both. Whichever isn't actively maintained is the one I have to drop.

RetroMod fixes both. By translating mod bytecode forward to whatever MC version is actually running, the version on the mod's box stops mattering. The 1.21.1 mod and the 1.21.7 mod can both live in the same `mods/` folder on a 26.1.2 server, and the server finally moves to the newest MC. Most of the actual API changes between versions are mechanical — a class moved, a method got renamed, a field became a getter — and that's the kind of work a transformer can do without bothering the original author.

It's MIT licensed. If you fork it for your own server, modify it for a use case I haven't thought about, or want to contribute back — all welcome. Issues and PRs both work.

— Bownlux ([RevivalSMP.net](https://revivalsmp.net))
