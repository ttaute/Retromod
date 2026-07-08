---
title: Home
layout: default
nav_order: 1
description: "Run old Minecraft mods on new Minecraft versions. Bytecode-level compatibility layer for Fabric, NeoForge, and Forge."
permalink: /
---

# Retromod

Run old Minecraft mods on new Minecraft versions. It transforms mod bytecode at load time so a mod built for, say, 1.16.5 can run on 26.1 without recompiling anything or asking the original author to update.

It works on Fabric, NeoForge, and Forge. The shim chain covers MC 1.12.2 (the Java 8 floor) through 26.2.

<div class="retromod-hero-cta" style="margin: 1.5em 0;">
  <a class="retromod-btn retromod-btn-primary" href="https://github.com/Bownlux/Retromod/releases">Download</a>
  <a class="retromod-btn retromod-btn-secondary" href="https://github.com/Bownlux/Retromod">Source on GitHub</a>
  <a class="retromod-btn retromod-btn-ghost" href="{{ '/installation' | relative_url }}">Install guide →</a>
</div>

> **1.1.0 is the current stable release. 1.2.0 is on its second release candidate** (`1.2.0-rc.2`), which carries the newest 26.1 and 26.2 compatibility work. The pipeline handles the common case and most simple-to-moderate mods. What's left is the hard stuff: rendering replacement, heavy mixin mods, and mods built on APIs that were redesigned rather than just renamed. If a mod doesn't work, [file an issue with the log](https://github.com/Bownlux/Retromod/issues). That's the quickest way to get it covered in the next build.

## What it does

When you launch Minecraft with Retromod installed, it scans the `retromod-input/` folder for old mod JARs. For each one it:

1. Reads the bytecode and figures out which MC version it was built for.
2. Walks it through a chain of small per-version "shims" that rewrite class names, method signatures, and field references step by step until the bytecode targets the MC version you're running.
3. Patches the mod's metadata (`fabric.mod.json` / `mods.toml`) so the loader stops complaining about version ranges.
4. Writes the transformed JAR into your `mods/` folder. Original is backed up to `retromod-backups/`.

Next time you launch, the transformed mod loads like any other. There's an in-game button on the title screen for the mod-management screen, but most people never need it. The whole thing is supposed to be invisible once it's set up.

The CLI tool can do the same thing outside Minecraft (useful for batch-processing whole mod folders or checking compatibility before launch), but it's a power-user thing. See the [CLI page]({{ '/cli' | relative_url }}) if you want it. To pre-check a single mod jar with no install at all, drop it on the [Probe]({{ '/probe' | relative_url }}) page: it screens whether the mod is likely to transform, right in your browser.

## What it doesn't do

The other side of that:

- Very deep-integration mods will probably never work, no matter how mature Retromod gets. The clearest example is **Create**. Mods like Create touch thousands of MC API points across the entire surface (rendering, physics, networking, world generation, animation) and ship their own rendering library (Flywheel) that includes platform-specific shader and GL code. Retromod can rewrite Java bytecode for renamed classes and shifted method signatures, but it cannot translate native code, and it cannot synthesize new mixin injection points where the underlying bytecode has been entirely rewritten. Other mods in this category: heavy tech mods like Applied Energistics 2, Tinkers' Construct, IndustrialCraft, and anything that meaningfully replaces MC's rendering pipeline. If your modpack is built around one of those, Retromod is not the right tool, and you'll need the mod author to ship a port for the new MC version.
- Mods that completely replace MC's rendering pipeline (Sodium, Iris) often have mixins targeting specific bytecode positions that have moved. The transformer can rename a method, but it can't synthesize an injection point that's been rewritten away. These are the hardest cases and often need per-mod hand work.
- Mods that lock to specific cross-version library versions (Cardinal Components 5.x package vs 6.x package, for example) can hit conflicts that no general-purpose shim resolves. The fix is usually to install the right library version standalone.
- Constructor signature changes in MC's API are sometimes silently broken. Retromod's redirect table doesn't yet cover every constructor that's gained or lost a parameter between versions; we work through these as the gap report surfaces them.
- Mods whose authors have explicitly opted out of transformation are skipped on purpose. See the [contributing guide]({{ '/contributing' | relative_url }}) for how that works.
- Server software like Paper, Spigot, Bukkit, and Purpur isn't supported. Those platforms run *plugins*, not mods, and the plugin APIs are a different shape from what Retromod is built around. Plugin support is planned but development hasn't started yet. See the [FAQ]({{ '/faq#does-it-work-with-server-software-like-paper--spigot--bukkit--purpur' | relative_url }}) for details.

If you're using mostly content mods, QoL mods, equipment-slot mods, recipe viewers, and the like, most of those translate cleanly. If you're trying to get a heavily-modded shaderpack-and-50-mods setup running with Create at its core, expect rough edges and probably broken renderers.

## A note on the version cutoff

Active development targets **MC 26.1+**. Builds for earlier host MC versions (1.20, 1.21.x) still get the same core pipeline improvements, since it's all one codebase, but new shim entries, new API coverage, and testing time go to the 26.1+ branch. MC 26.1 was the first Minecraft release without code obfuscation, and that's a big enough inflection point for a mod-translation tool that splitting effort across both worlds didn't make sense. Old MC users aren't abandoned; they're just on a slower track for new compatibility coverage.

## Pages

The wiki is small but covers the things people ask about most.

- [Installation]({{ '/installation' | relative_url }}): get it running on your launcher.
- [Configuration]({{ '/config' | relative_url }}): every option, defaults, and when to flip them.
- [In-game UI]({{ '/gui' | relative_url }}): the title-screen button and the screens behind it.
- [CLI tool]({{ '/cli' | relative_url }}): for power users who want to transform mods outside MC.
- [Verify transforms]({{ '/verify-transforms' | relative_url }}): the gap report and what it tells you.
- [Troubleshooting]({{ '/troubleshooting' | relative_url }}): common error messages, what they mean, what to do.
- [FAQ]({{ '/faq' | relative_url }}): safety, servers, modpacks, commercial use, the legal stuff.
- [Architecture]({{ '/architecture' | relative_url }}): for people reading the source.
- [Technical]({{ '/technical' | relative_url }}): the security model, fork policy, and how the transformer works inside.
- [Authenticity]({{ '/authenticity' | relative_url }}): how to tell an official build from a modified one.
- [Contributing]({{ '/contributing' | relative_url }}): how to add shims and polyfills, and the mod-author opt-out mechanism.
- [Writing an Addon]({{ '/addons' | relative_url }}): extend Retromod with your own shims, polyfills, and per-mod fixes, shipped as a separate mod (and the licensing for adopting them into core).
- [Adding SRG Mappings]({{ '/srg-mappings' | relative_url }}): fill gaps in the SRG → Mojang dictionary so more old Forge mods load. Easy first PR.
- [Mods That Can't Be Translated]({{ '/incompatible-mods' | relative_url }}): the realistic "no" list. Specific mods (Create, OptiFine, AE2, …) and the general rules that put them there.
