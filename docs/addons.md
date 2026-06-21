---
title: Writing an Addon
layout: default
nav_order: 12.5
description: "Extend Retromod with your own shims, polyfills, and per-mod fixes, shipped as a separate mod."
---

# Writing a Retromod Addon

Retromod handles the common cases in core, but there's a long tail of *per-mod* and *per-modpack* compatibility fixes that don't belong in the main JAR. An **addon** lets you ship those yourself: a normal mod that bundles extra shims, polyfills, or per-mod fixes, which Retromod picks up at launch the same way it loads its own. (Think *Sinytra Connector Extra*, but for Retromod.)

Addons are **independent mods under your own license**. You don't need anyone's permission to write one, and Retromod's MIT license doesn't reach into your code.

## The extension points (Addon API v1)

Two code interfaces and two config files. All four are a **stable public API**: across the 1.x line we'll only *add* to them (new methods arrive as `default`s), never break an existing signature.

### 1. `VersionShim`: add transforms

`com.retromod.core.VersionShim`. A shim registers class/method/field redirects for a version transition. Use it to carry a specific mod (or a whole version hop) that core doesn't cover yet.

```java
package com.example.myaddon;

import com.retromod.core.VersionShim;
import com.retromod.core.RetromodTransformer;

public class MyModFix implements VersionShim {
    public String getShimName()      { return "MyAddon: ExampleMod fix"; }
    public String getSourceVersion() { return "1.20.1"; }
    public String getTargetVersion() { return "1.21"; }
    public String getModLoaderType() { return "fabric"; } // or "neoforge" / "forge"

    public void registerRedirects(RetromodTransformer t) {
        // A class that moved packages:
        t.registerClassRedirect(
            "net/minecraft/old/pkg/Thing",
            "net/minecraft/new/pkg/Thing");
        // A method that was renamed (owner, name, descriptor -> new owner, name, descriptor):
        t.registerMethodRedirect(
            "net/minecraft/world/item/Item", "oldName", "()V",
            "net/minecraft/world/item/Item", "newName", "()V");
    }
}
```

### 2. `PolyfillProvider`: reimplement a removed API

`com.retromod.polyfill.PolyfillProvider`. When a class was *deleted* (not renamed), a polyfill ships a replacement class and points references at it. Its `getCategory()` gates it in `config.json`.

```java
public class MyPolyfill implements PolyfillProvider {
    public String getName()              { return "MyAddon: OldThing polyfill"; }
    public String getCategory()          { return "my_addon"; }
    public String[] getRemovedClasses()  { return new String[]{ "net/minecraft/old/Thing" }; }
    public String[] getPolyfillClasses() { return new String[]{ "com.example.myaddon.ThingShim" }; }
    public void registerPolyfills(RetromodTransformer t) {
        // Wire references to your embedded replacement class.
    }
}
```

### 3 & 4. Config files (no code)

- **`config/retromod/mixin-blocklist.json`** soft-fails a specific mixin handler that crashes on the target MC, so the mod still loads with just that feature inert. Format in [Troubleshooting]({{ '/troubleshooting' | relative_url }}).
- **`config/retromod/config.json`** toggles polyfill categories and transform options.

These two are loader-agnostic and need no Java, which is handy for modpack authors.

## How Retromod finds your addon

Retromod loads shims and polyfills via `ServiceLoader` at startup, so an addon is just a mod that ships **your classes plus a service file listing them**:

```
META-INF/services/com.retromod.core.VersionShim
    com.example.myaddon.MyModFix

META-INF/services/com.retromod.polyfill.PolyfillProvider
    com.example.myaddon.MyPolyfill
```

Install your addon alongside Retromod and it's discovered automatically, no registration call. Discovery is confirmed on **Fabric**, where Retromod's loader sees every installed mod. On **NeoForge / Forge**, ship your addon as a normal mod; if a service isn't picked up, please [open an issue](https://github.com/Bownlux/Retromod/issues). Making discovery solid across all three loaders is something I'm still working on.

> **Depend on the API, not the internals.** Compile against `VersionShim`, `PolyfillProvider`, and the documented `register…Redirect` methods of `RetromodTransformer`. Treat everything else as private and subject to change.

## Licensing: can your code go into official Retromod?

Short version: **you keep your license. If it's permissive, we can adopt your fix into core with credit.**

- Your addon is your own project under **whatever license you choose**.
- If you release it under a **permissive license (MIT, Apache-2.0)**, that license already lets everyone, including official Retromod, reuse your code *with attribution*. So a great per-mod fix in a permissive addon can be pulled into core, with credit, without any extra paperwork.
- If you'd rather it stay separate, use a **copyleft license** (GPL/LGPL): we won't fold copyleft code into the MIT core, so it stays yours.
- Direct contributions (PRs to the Retromod repo) are under Retromod's **MIT** license.

No CLA, no signing. *(This is the project's policy, not legal advice - for anything binding, ask a lawyer.)*

## See also

- [Contributing]({{ '/contributing' | relative_url }}): add a shim or polyfill to core, plus the mod-author opt-out marker.
- [Adding SRG Mappings]({{ '/srg-mappings' | relative_url }}): the easiest first contribution.
