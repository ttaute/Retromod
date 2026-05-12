---
name: add-polyfill
description: Create a polyfill provider that re-implements a removed API so old mods still work. Use when mods crash with ClassNotFoundException or NoSuchMethodError for completely removed classes.
argument-hint: "api-name (e.g. baubles, nei, old-fabric-api-module)"
---

# Add Polyfill Provider

Creates a `PolyfillProvider` that re-implements removed APIs using modern equivalents so old mods actually work, not just load without crashing.

## When to Use

- Mod crashes with `ClassNotFoundException` for a class that no longer exists
- Mod crashes with `NoSuchMethodError` for a method with no replacement
- A mod loader API module was entirely removed (e.g. old Fabric API modules)
- A third-party mod API was discontinued (e.g. Baubles → Curios, NEI → JEI)

## Steps

1. **Identify the removed API** — Find which classes/methods were removed and what (if anything) replaced them. Check crash logs for the exact class names and packages.

2. **Create polyfill classes** — For each removed class, create a reimplementation at the ORIGINAL package path that delegates to modern equivalents:
   ```java
   // At the original package path so ClassNotFoundException is resolved
   package the.original.package;

   /**
    * Polyfill for removed class — delegates to modern API.
    */
   public class RemovedClass {
       // Reimplement methods using the modern equivalent API
       public void removedMethod() {
           // Delegate to the new API that replaced this
           ModernApi.doTheThing();
       }
       public Object getData() {
           // Bridge to the modern data source
           return ModernDataProvider.get();
       }
       public boolean isAvailable() { return true; }
   }
   ```

3. **Create the PolyfillProvider** at `src/main/java/com/retromod/polyfill/`:
   ```java
   public class MyApiPolyfillProvider implements PolyfillProvider {
       @Override public String getName() { return "My API Polyfill"; }
       @Override public String getCategory() { return "thirdparty"; } // or "fabric_api", "forge", "neoforge", "minecraft_vanilla"

       @Override
       public List<String> getRemovedClasses() {
           return List.of(
               "the/original/package/RemovedClass",
               "the/original/package/AnotherRemoved"
           );
       }

       @Override
       public List<String> getPolyfillClasses() {
           return List.of(
               "com/retromod/polyfill/stubs/myapi/RemovedClass",
               "com/retromod/polyfill/stubs/myapi/AnotherRemoved"
           );
       }

       @Override
       public void registerPolyfills(RetromodTransformer transformer) {
           transformer.registerClassRedirect(
               "the/original/package/RemovedClass",
               "com/retromod/polyfill/stubs/myapi/RemovedClass"
           );
       }
   }
   ```

4. **Register in ServiceLoader** — Add to:
   ```
   src/main/resources/META-INF/services/com.retromod.polyfill.PolyfillProvider
   ```

5. **Handle interfaces vs classes** — If the removed API used interfaces:
   - Keep the interface with empty default methods
   - If a class became an interface, use `registerSuperclassRedirect()`

6. **Test** — Verify the polyfill loads and the crash is resolved.

## Key Files
- Polyfill providers: `src/main/java/com/retromod/polyfill/`
- Stub implementations: `src/main/java/com/retromod/polyfill/stubs/`
- PolyfillProvider interface: `src/main/java/com/retromod/polyfill/PolyfillProvider.java`
- ServiceLoader: `src/main/resources/META-INF/services/com.retromod.polyfill.PolyfillProvider`
- Config categories: `config/retromod/config.json` → `polyfill_categories`

## Categories
- `fabric_api` — Removed Fabric API modules
- `minecraft_vanilla` — Removed vanilla MC classes (Material, LiteralText, etc.)
- `mixin_targets` — Removed MC classes used as Mixin targets
- `forge` — Legacy Forge APIs (SidedProxy, RegistryObject, capabilities)
- `neoforge` — Removed NeoForge APIs
- `thirdparty` — Third-party mod APIs (Baubles, NEI, CoFH, WAILA)
- `rendering` — Removed rendering APIs
- `entity` — Removed entity APIs

## Important Notes
- Polyfills should delegate to modern equivalent APIs wherever possible — the goal is mods that WORK, not just load
- Only fall back to no-op returns when there is genuinely no modern equivalent
- Users can toggle polyfill categories in config.json
- There are currently 72+ polyfill reimplementations across 10 providers
