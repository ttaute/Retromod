# RetroMod Compatibility

> **RetroMod v1.0.0-beta.1** | **Target: Minecraft 1.21.11** | **Fabric Loader 0.18.4**

RetroMod is designed to work with **any** Fabric mod from MC 1.14 through 1.21.11. It applies bytecode transformations, method redirects, class renames, polyfills, and smart mixin processing automatically — no per-mod configuration needed.

**The mods listed below are ones we've specifically tested.** They are not the only mods that work. If a mod isn't listed here, it may still work fine — try it and [let us know](https://github.com/Bownlux/MC-RetroMod/issues)!

---

## How It Works

RetroMod covers MC **1.14 through 1.21.11** with:
- **109 version shims** with 317+ method redirects and 486+ class redirects
- **72+ polyfill stubs** for completely removed APIs
- **34+ modding API** transformations (Fabric API, JEI, GeckoLib, Architectury, etc.)
- **Smart mixin compatibility** — relocates targets, partially strips broken methods, preserves working ones

Every mod goes through the same transformation pipeline. The list below just shows what we've verified.

---

## Tested Mods

These mods were tested with RetroMod. Mods from all versions are supported — these are just the ones we've confirmed.

### 1.16.5 (10 version hops)

| Mod | Type | Modrinth |
|-----|------|----------|
| Cloth Config 4.17.101 | Config Library | [Link](https://modrinth.com/mod/cloth-config) |
| Lithium 0.6.6 | Server Optimization | [Link](https://modrinth.com/mod/lithium) |
| LambDynamicLights 1.3.4 | Dynamic Lighting | [Link](https://modrinth.com/mod/lambdynamiclights) |

### 1.17.1 (9 version hops)

| Mod | Type | Modrinth |
|-----|------|----------|
| Lithium 0.7.5 | Server Optimization | [Link](https://modrinth.com/mod/lithium) |
| Mod Menu 2.0.17 | Mod List UI | [Link](https://modrinth.com/mod/modmenu) |
| LambDynamicLights 2.1.0 | Dynamic Lighting | [Link](https://modrinth.com/mod/lambdynamiclights) |

### 1.18.2 (8 version hops)

| Mod | Type | Modrinth |
|-----|------|----------|
| Sodium 0.4.1 | Rendering | [Link](https://modrinth.com/mod/sodium) |
| Lithium 0.10.3 | Server Optimization | [Link](https://modrinth.com/mod/lithium) |
| Mod Menu 3.2.5 | Mod List UI | [Link](https://modrinth.com/mod/modmenu) |
| AppleSkin 2.5.1 | Food Info HUD | [Link](https://modrinth.com/mod/appleskin) |
| Iris Shaders 1.6.11 | Shader Support | [Link](https://modrinth.com/mod/iris) |

### 1.19–1.19.4

| Mod | MC | Type | Modrinth |
|-----|-----|------|----------|
| Not Enough Crashes 4.1.6 | 1.19 | Crash Recovery | [Link](https://modrinth.com/mod/notenoughcrashes) |
| Continuity 2.0.2 | 1.19 | Connected Textures | [Link](https://modrinth.com/mod/continuity) |
| AppleSkin 2.4.1 | 1.19 | Food Info HUD | [Link](https://modrinth.com/mod/appleskin) |
| CIT Resewn 1.1.2 | 1.19.2 | Custom Item Textures | [Link](https://modrinth.com/mod/cit-resewn) |
| Architectury API 6.6.92 | 1.19.2 | Cross-loader API | [Link](https://modrinth.com/mod/architectury-api) |
| BetterF3 6.0.3 | 1.19.4 | Debug Screen | [Link](https://modrinth.com/mod/betterf3) |
| Mouse Tweaks 2.24 | 1.19.4 | Inventory QoL | [Link](https://modrinth.com/mod/mouse-tweaks) |
| Continuity 3.0.0 | 1.19.4 | Connected Textures | [Link](https://modrinth.com/mod/continuity) |

### 1.20–1.20.6

| Mod | MC | Type | Modrinth |
|-----|-----|------|----------|
| Sodium 0.5.13 | 1.20.1 | Rendering | [Link](https://modrinth.com/mod/sodium) |
| Iris Shaders 1.7.6 | 1.20.1 | Shader Support | [Link](https://modrinth.com/mod/iris) |
| Jade 11.13.1 | 1.20.1 | Tooltip Info | [Link](https://modrinth.com/mod/jade) |
| FerriteCore 6.0.1 | 1.20.1 | Memory Optimization | [Link](https://modrinth.com/mod/ferrite-core) |
| Debugify 2.0 | 1.20.1 | Bug Fixes | [Link](https://modrinth.com/mod/debugify) |
| Orbital Railgun 1.1 | 1.20.1 | Weapon Mod | [Link](https://modrinth.com/mod/orbital-railgun) |
| No Chat Reports 2.6.1 | 1.20.4 | Privacy | [Link](https://modrinth.com/mod/no-chat-reports) |
| Zoomify 2.15.2 | 1.20.4 | Zoom | [Link](https://modrinth.com/mod/zoomify) |
| Dynamic FPS 3.11.4 | 1.20.5 | FPS Management | [Link](https://modrinth.com/mod/dynamic-fps) |
| Jade 14.2.4 | 1.20.6 | Tooltip Info | [Link](https://modrinth.com/mod/jade) |
| REI 15.0.787 | 1.20.6 | Recipe Viewer | [Link](https://modrinth.com/mod/rei) |

### 1.21–1.21.4

| Mod | MC | Type | Modrinth |
|-----|-----|------|----------|
| Sodium 0.6.13 | 1.21.1 | Rendering | [Link](https://modrinth.com/mod/sodium) |
| FerriteCore 7.0.0 | 1.21 | Memory Optimization | [Link](https://modrinth.com/mod/ferrite-core) |
| Debugify 1.0 | 1.21 | Bug Fixes | [Link](https://modrinth.com/mod/debugify) |
| Simple Voice Chat 2.6.12 | 1.21.1 | Voice Chat | [Link](https://modrinth.com/mod/simple-voice-chat) |
| Capes 1.5.5 | 1.21 | Cosmetic Capes | [Link](https://modrinth.com/mod/capes) |
| Carry On 2.3.0.29 | 1.21.4 | Pick Up Blocks | [Link](https://modrinth.com/mod/carry-on) |

---

## Mods That Need Extra Dependencies

Some mods require additional libraries that aren't bundled with RetroMod:

| Mod | Needs |
|-----|-------|
| LambDynamicLights | SpruceUI |
| Light Overlay | Architectury API |
| Zoomify | Fabric Language Kotlin + YACL |
| Litematica | malilib |

---

## Known Limitations

1. **Mixin-heavy mods** (Sodium, Iris) may have runtime issues beyond bytecode redirection
2. **Very old mods** (1.14–1.15) span the most API changes and are more likely to have issues
3. **Kotlin mods** need Fabric Language Kotlin installed separately
4. **Closed-source / obfuscated mods** (Essential, OptiFine) have limited support

---

## Not Working? Report It

If a mod doesn't work with RetroMod, [open an issue](https://github.com/Bownlux/MC-RetroMod/issues) with:
- The mod name and version
- The Minecraft version it was built for
- The crash log or error message

We're actively expanding compatibility. Your report helps us improve RetroMod for everyone.

---

*[Modrinth](https://modrinth.com/mod/retromod) | [GitHub](https://github.com/Bownlux/MC-RetroMod)*
*Made by the Developers of [revivalsmp.net](https://revivalsmp.net)*
