---
name: debug-crash
description: Debug a Minecraft crash caused by a mod that Retromod transformed. Use when a mod crashes after transformation and you need to find and fix the root cause.
argument-hint: "no args needed - reads latest.log automatically"
---

# Debug Crash

Diagnose and fix crashes caused by transformed mods.

## Step 1: Read the Crash Log

```bash
# macOS
cat ~/Library/Application\ Support/minecraft/logs/latest.log | tail -200

# Also check crash reports
ls ~/Library/Application\ Support/minecraft/crash-reports/
cat ~/Library/Application\ Support/minecraft/crash-reports/crash-*.txt | tail -100
```

## Step 2: Identify the Error Type

### `NoSuchMethodError: <class>.<method>`
**Cause:** A method was renamed/removed and Retromod didn't have a redirect for it.
**Fix:** Add a method redirect to the appropriate version shim:
```java
transformer.registerMethodRedirect(
    "owner/Class", "oldMethod", "(descriptor)V",
    "owner/Class", "newMethod", "(descriptor)V"
);
```

### `NoClassDefFoundError: <class>`
**Cause:** A class was removed or relocated.
**Fix options:**
1. Add a class redirect in the version shim
2. Create a polyfill stub if the class was completely removed
3. Check if intermediary→Mojang mapping is missing for this class

### `ClassNotFoundException: <class>`
**Cause:** Same as above but triggered by reflection/Class.forName().
**Fix:** Same as NoClassDefFoundError. Also check reflection remapping in RetromodTransformer.

### `AbstractMethodError`
**Cause:** An interface gained a new abstract method that the mod doesn't implement.
**Fix:** The polyfill system may need a bridge class, or the method needs a default implementation.

### `IncompatibleClassChangeError`
**Cause:** A class changed to an interface (or vice versa).
**Fix:** Use `registerSuperclassRedirect()` in the polyfill provider.

### `Missing or unsupported mandatory dependencies`
**Cause:** Mod metadata has version constraints that reject the current MC version.
**Fix:** Check `ForgeModTransformer.updateMinecraftVersionRange()` or `FabricModTransformer.updateVersionRequirements()`.

### `MixinApplyError` / `InvalidMixinException`
**Cause:** Mixin target class or method no longer exists.
**Fix:** Check `MixinCompatibilityTransformer` and `MixinTargetRedirector`. The target redirect table may be missing an entry.

### `UnsupportedClassVersionError`
**Cause:** Mod was compiled for a newer Java version than what's running.
**Fix:** Cannot be fixed by Retromod. The user needs to install the correct Java version. MC 26.1 requires Java 25.

## Step 3: Enable Debug Logging

Edit `config/retromod/config.json`:
```json
{
    "log_level": "DEBUG",
    "dump_bytecode": true,
    "log_transformations": true
}
```

This creates bytecode dumps in `config/retromod/bytecode-dump/` showing exactly what Retromod transformed.

## Step 4: Analyze the Mod

```bash
mvn -f pom.xml exec:java -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="analyze '/path/to/mod.jar'" -q
```

Check:
- Complexity score (>100 = likely problems)
- API dependencies
- Risk factors (coremods, ASM manipulation, NMS access)

## Step 5: Check Shim Coverage

```bash
mvn -f pom.xml exec:java -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="shims" -q
```

Verify the shim chain exists from the mod's source version to the target version.

## Step 6: Apply Fix

1. **Missing redirect** → Add to appropriate version shim
2. **Missing polyfill** → Create new polyfill provider (use `add-polyfill` skill)
3. **Missing mapping** → Add to IntermediaryToMojangMapper (use `mapping-work` skill)
4. **Metadata issue** → Fix in ForgeModTransformer or FabricModTransformer (use `mod-loader-compat` skill)
5. **Mixin issue** → Add target redirect to MixinTargetRedirector

## Key Debugging Files
- Crash log: `logs/latest.log`
- Crash reports: `crash-reports/crash-*.txt`
- Retromod crash log: `config/retromod/crash-log.txt`
- Bytecode dumps: `config/retromod/bytecode-dump/`
- AOT cache: `config/retromod/aot-cache/`
