---
name: mod-loader-compat
description: Work on mod loader compatibility - Fabric, NeoForge, Forge runtime integration, version constraint relaxation, and mod metadata patching. Use when fixing mod loading issues, version rejection errors, or adding support for a new loader version.
argument-hint: "loader issue (e.g. neoforge version-range-too-strict, fabric dependency-rejected)"
---

# Mod Loader Compatibility

Handles how Retromod integrates with each mod loader's runtime and patches mod metadata so old mods can load on newer versions.

## Architecture

Retromod runs as a mod on each loader and intercepts mod loading:

### Fabric
- **PreLaunch** (`RetromodPreLaunch.java`) - Runs BEFORE Fabric scans `mods/`. Transforms mods in `retromod-input/`, patches `fabric.mod.json`, moves to `mods/`.
- **Main** (`Retromod.java`) - Runs after mod scan. Initializes shims, AOT, polyfills.
- Fabric is strictest - it rejects mods with wrong version constraints BEFORE Retromod can run. That's why `retromod-input/` exists.

### NeoForge
- **Constructor** (`RetromodNeoForge.java`) - Transforms from `retromod-input/` AND in-place in `mods/`. Patches `mods.toml`/`neoforge.mods.toml`.
- NeoForge is more lenient - mods can go directly in `mods/` and Retromod transforms them.

### Forge
- **Constructor** (`RetromodForge.java`) - Same pattern as NeoForge but also handles Forge→NeoForge class migration.

## Key Files

| File | Purpose |
|------|---------|
| `RetromodPreLaunch.java` | Fabric pre-launch hook (earliest possible) |
| `Retromod.java` | Main Fabric initializer, auto-detects `TARGET_MC_VERSION` |
| `RetromodNeoForge.java` | NeoForge entry point |
| `RetromodForge.java` | Forge entry point |
| `FabricModTransformer.java` | Patches `fabric.mod.json` - version constraints, API deps |
| `ForgeModTransformer.java` | Patches `mods.toml`/`neoforge.mods.toml` |
| `ModVersionDetector.java` | Reads mod version from loader-specific metadata |
| `fabric.mod.json` | Retromod's own Fabric metadata |
| `META-INF/neoforge.mods.toml` | Retromod's own NeoForge metadata |
| `META-INF/mods.toml` | Retromod's own Forge metadata |

## Version Constraint Patching

### Fabric (`FabricModTransformer`)
- Sets `"minecraft"` to exact target version
- Relaxes `"fabricloader"` to `>=0.14.0`
- Sets `"fabric-api"` and `"fabric"` to `"*"`
- Relaxes 60+ shimmed API mod IDs to `"*"`
- Moves blocking deps from `"depends"` to `"suggests"`

### NeoForge/Forge (`ForgeModTransformer`)
- Updates minecraft `versionRange` to `[<target>,)`
- Relaxes forge/neoforge version range to `[0,)`
- For 26.1+: relaxes ALL dependency version constraints to `[0,)` so they accept any version
- Only falls back to `type="optional"` / `mandatory=false` if the dep mod has no polyfill and isn't installed
- Handles both bracket ranges (`[1.21,1.21.1)`) and bare versions (`"1.21.8"`)

## Common Issues

### "Mod requires minecraft X but found Y"
- The mod's metadata has a version constraint that doesn't include the current MC version
- Fix: Ensure `ForgeModTransformer.updateMinecraftVersionRange()` or `FabricModTransformer.updateVersionRequirements()` is catching the format

### "Missing mandatory dependency: balm/cloth-config/etc"
- A mod requires another mod that isn't installed, or the installed version is rejected due to version constraints
- Fix: Relax the dependency's version constraint to accept any version (`"*"` for Fabric, `[0,)` for NeoForge/Forge) so it works across MC versions
- If the dep mod genuinely isn't installed and has no polyfill, THEN make it optional as a fallback
- Check if the dep is in `SHIMMED_API_MOD_IDS` set - shimmed deps get their versions relaxed automatically

### "Mod detected as already compatible but crashes"
- `needsTransformation()` returns false (null target version or same version)
- Fix: Check `ModVersionDetector` - it may not be parsing the metadata correctly
- For 26.1+: even "compatible" mods need metadata patching

### Version detection returns null
- The TOML parser can't find the minecraft dependency
- Common cause: `[[dependencies.modid]]` array-of-tables with multiple entries overwriting each other
- Fix: Use `extractMcVersionFromToml()` regex approach instead of simple TOML parser

## Testing

1. Build Retromod: `mvn package -DskipTests`
2. Put old mods in `retromod-input/` in the game directory
3. Launch the game - check `logs/latest.log` for Retromod messages
4. Look for "Transformed X mod(s)" or error messages
5. If a mod fails to load, check the exact error and trace it to the metadata patching code

## Important Notes
- `Retromod.TARGET_MC_VERSION` is auto-detected at runtime from the mod loader
- All hardcoded version strings should use `Retromod.TARGET_MC_VERSION` instead
- The `retromod-input/` folder pattern is required for Fabric (strict version checking)
- NeoForge/Forge can transform mods in-place in `mods/` with backups
