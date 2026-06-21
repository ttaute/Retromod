---
name: add-version-shim
description: Create a new version shim to add support for transforming mods between two Minecraft versions. Use when adding support for a new MC version or filling gaps in the shim chain.
argument-hint: "source-version target-version loader (e.g. 1.21.11 26.1 fabric)"
---

# Add Version Shim

Creates a new `VersionShim` implementation that maps API changes between two Minecraft versions for a specific mod loader.

## Steps

1. **Identify the version gap** - Check `src/main/java/com/retromod/shim/` for existing shims. Find what source→target versions are missing. Use `ShimRegistry` BFS to verify no chain already exists.

2. **Research API changes** - Compare the two MC versions to find:
   - Renamed classes (e.g. `net/minecraft/class_1234` → `net/minecraft/world/entity/Entity`)
   - Renamed methods (e.g. `method_5678` → `getBlockState`)
   - Renamed fields (e.g. `field_9012` → `STONE`)
   - Moved classes (package relocations)
   - Changed method signatures (descriptor changes)

3. **Create the shim file** at `src/main/java/com/retromod/shim/<loader>/`:
   ```
   <Loader>_<source>_to_<target>.java
   ```
   Use underscores for dots in versions (e.g. `Fabric_1_21_10_to_26_1.java`).

4. **Implement `VersionShim` interface**:
   ```java
   public class <ClassName> implements VersionShim {
       @Override public String getShimName() { return "<Loader> <source> → <target>"; }
       @Override public String getSourceVersion() { return "<source>"; }
       @Override public String getTargetVersion() { return "<target>"; }
       @Override public String getModLoaderType() { return "<loader>"; } // "fabric", "neoforge", "forge", or "common"

       @Override
       public void registerRedirects(RetromodTransformer transformer) {
           // Class redirects
           transformer.registerClassRedirect("old/class/Name", "new/class/Name");
           // Method redirects
           transformer.registerMethodRedirect(
               "owner/Class", "oldMethod", "(Larg;)Lreturn;",
               "owner/Class", "newMethod", "(Larg;)Lreturn;"
           );
           // Field redirects
           transformer.registerFieldRedirect("owner/Class", "oldField", "owner/Class", "newField");
       }
   }
   ```

5. **Register in ServiceLoader** - Add the full class name to:
   ```
   src/main/resources/META-INF/services/com.retromod.core.VersionShim
   ```

6. **Add version aliases** - If the new version has sub-versions (e.g. 26.1.0, 26.1.1), add aliases in `ShimRegistry.java`:
   ```java
   VERSION_ALIASES.put("26.1.0", "26.1");
   ```

7. **Add tests** - Create test in `src/test/java/com/retromod/` verifying:
   - Shim registers correctly
   - ShimRegistry BFS finds a chain through the new shim
   - Key redirects work (class, method, field)

## Key Files
- Shim implementations: `src/main/java/com/retromod/shim/<loader>/`
- ShimRegistry: `src/main/java/com/retromod/shim/ShimRegistry.java`
- ServiceLoader registration: `src/main/resources/META-INF/services/com.retromod.core.VersionShim`
- VersionShim interface: `src/main/java/com/retromod/core/VersionShim.java`
- Tests: `src/test/java/com/retromod/RetromodTest.java`

## Naming Conventions
- Fabric intermediary names: `class_XXXX`, `method_XXXX`, `field_XXXX`
- Mojang official names (26.1+): Human-readable (e.g. `Entity`, `getBlockState`)
- NeoForge already uses Mojang names since 1.17
- Forge uses SRG names (e.g. `func_XXXXX`, `field_XXXXX`) up to 1.20

## Important Notes
- For 26.1+ shims, use `IntermediaryToMojangMapper` for Fabric intermediary→Mojang mappings
- NeoForge 26.1 shims mainly need API-level changes (not name remapping)
- Shims are chainable - a mod going 1.16.5→26.1 will traverse ALL intermediate shims via BFS
