# Retromod Technical Capabilities

## What Can Be Done vs What Cannot

This document explains the technical possibilities and limitations of mod transformation.

---

## TIER 1: Definitely Possible (We Do This Now)

### 1.1 Public API Redirects ✅

**What it is:** Minecraft has public APIs that mods use. When these change names, we redirect.

```java
// OLD (1.20.4)
world.setBlockState(pos, state);

// NEW (1.21.11)
world.setBlock(pos, state);

// RETROMOD: Intercepts "setBlockState" → redirects to "setBlock"
```

**How we do it:** ASM bytecode transformation scans for method calls and rewrites them.

### 1.2 Class Package Moves ✅

**What it is:** Classes get moved to different packages between versions.

```java
// OLD
import net.minecraft.util.math.BlockPos;

// NEW
import net.minecraft.core.BlockPos;

// RETROMOD: Rewrites all references
```

**How we do it:** Replace class references throughout the bytecode.

### 1.3 Simple Mixin Target Redirects ✅

**What it is:** Mixin annotations target methods by name. We can change those names.

```java
// OLD MIXIN
@Inject(method = "breakBlock", at = @At("HEAD"))

// NEW MIXIN (after Retromod)
@Inject(method = "destroyBlock", at = @At("HEAD"))
```

**How we do it:** Transform annotation values in the Mixin class bytecode.

---

## TIER 2: Possible But Complex ⚠️

### 2.1 Mixin "Middle Man" Interceptor ✅

Retromod sits between old Mixin targets and new MC code, translating calls:

```
OLD MIXIN CODE                    RETROMOD                         MINECRAFT
                                  (Middle Man)
@Inject(method="oldMethod")  →   Intercepts      →    Actually calls newMethod()
                                  Translates
                                  Redirects
```

**What it handles:**
- Method name changes (`breakBlock` → `destroyBlock`)
- Class target changes (`World` → `Level`)
- Method signature changes (different parameters)

**Limitation:** If the new method has COMPLETELY different logic (not just renamed), we can't help.

### 2.2 Internal Class Deobfuscation + Translation ✅

Mods are developed against mapped names (not obfuscated), so obfuscation itself isn't the problem:

```
OBFUSCATION IS NOT THE PROBLEM:
─────────────────────────────────────────────────────────
Minecraft source:  class World { void setBlock() }
                         ↓ (Mojang obfuscates)
Minecraft JAR:     class abc { void def() }
                         ↓ (Mappings exist)
Mappings:          abc = World, def = setBlock
                         ↓ (Mod loaders apply mappings)
What mods see:     class World { void setBlock() }

Mods are developed against MAPPED names, not obfuscated!
─────────────────────────────────────────────────────────

THE REAL PROBLEM IS STRUCTURAL CHANGES:
─────────────────────────────────────────────────────────
1.20.4: class RenderChunk {
            void rebuild(int x, int y, int z) { }
        }

1.21.11: class SectionCompiler {  // Different name!
             void compile(int x, int y, int z, boolean flag, Layer layer) {
             }                    // Different parameters!
         }

Even with perfect deobfuscation, the CODE STRUCTURE changed!
─────────────────────────────────────────────────────────
```

**What we CAN do:**

| Change Type | Fixable? | How |
|-------------|----------|-----|
| Class renamed | ✅ Yes | Class redirect mapping |
| Method renamed | ✅ Yes | Method redirect mapping |
| Package moved | ✅ Yes | Package redirect mapping |
| Parameter added | ⚠️ Sometimes | Provide default value |
| Parameter removed | ⚠️ Sometimes | Ignore extra param |
| Parameter type changed | ⚠️ Hard | Type conversion shim |
| Method split into two | ❌ Hard | Need custom logic |
| Method merged | ❌ Hard | Need custom logic |
| Logic completely changed | ❌ No | Can't auto-translate logic |

**Implementation approach:**

```java
// A comprehensive mapping database:
MAPPINGS_1_20_4_TO_1_21_11 = {
    "net/minecraft/client/renderer/chunk/RenderChunk": {
        "newName": "net/minecraft/client/renderer/chunk/SectionCompiler",
        "methods": {
            "rebuild(III)V": {
                "newName": "compile",
                "newDesc": "(IIIZL...Layer;)V",
                "parameterMapping": [0, 1, 2, "false", "Layer.SOLID"]
            }
        }
    }
}
```

This is buildable but requires:
1. Analyzing diffs between MC versions
2. Manual verification of semantic equivalence
3. Large mapping database (could be community-contributed)

### 2.3 AOT Pre-computation of All Transforms ✅

Do all the heavy work ahead of time and cache it:

```
FIRST RUN (slow):
1. Analyze mod bytecode
2. Detect all API calls
3. Compute all redirects
4. Transform Mixin targets
5. Save transformed JAR + mapping cache

SUBSEQUENT RUNS (fast):
1. Load cached transformed JAR
2. Done!
```

AOT infrastructure is already in place and can be enhanced for Mixin redirects.

---

## TIER 3: Very Hard But Theoretically Possible 🔧

### 3.1 Signature-Changing Method Transforms

**The problem:**
```java
// OLD
void render(MatrixStack matrices, float delta) { }

// NEW
void render(GuiGraphics graphics, int mouseX, float delta) { }
```

**Possible solution:**
```java
// Retromod generates a SHIM:
void render_SHIM(MatrixStack matrices, float delta) {
    // Convert old params to new params
    GuiGraphics graphics = convertMatrixStackToGuiGraphics(matrices);
    int mouseX = 0; // Default value

    // Call real method
    render(graphics, mouseX, delta);
}

// Then redirect old calls to the shim
```

**Status:** Implementable but needs per-method custom logic.

### 3.2 Event System Changes

**The problem:** Minecraft's event system changes between versions.

```java
// OLD (1.20.4)
MinecraftForge.EVENT_BUS.post(new BlockBreakEvent(world, pos, state, player));

// NEW (1.21.11)
NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player, drops));
```

**Possible solution:** Intercept event registration, translate event types.

**Status:** Complex but doable for known events.

### 3.3 Comprehensive Internal Mapping Database

Build a complete database of all internal changes:

```
Sources:
- Yarn mappings (Fabric)
- Mojang mappings (official)
- Parchment mappings (extra documentation)
- Version diff analysis

Database:
{
    "1.20.4 → 1.21.11": {
        "classes": { ... 500+ class mappings ... },
        "methods": { ... 2000+ method mappings ... },
        "fields": { ... 1000+ field mappings ... }
    }
}
```

**Status:** Large undertaking but community could help. Projects like Linkie already have some of this data.

---

## TIER 4: Actually Impossible ❌

### 4.1 Native Code (C++/Rust)

```
Java bytecode:  We can read, analyze, transform
Native code:    Compiled machine code, different for each OS/CPU
                We literally cannot parse it as Java

For example, LWJGL (OpenGL bindings) has native components.
         If a mod uses custom native code, we can't touch it.
```

### 4.2 Shader Code (GLSL/SPIR-V)

```
Java bytecode:  Instructions like INVOKEVIRTUAL, GETFIELD
GLSL:           Completely different syntax and compilation

Example:
    uniform mat4 modelViewMatrix;  // This is GLSL, not Java
    void main() {
        gl_Position = modelViewMatrix * vec4(position, 1.0);
    }

We can't transform this because it's not Java bytecode.
```

**Note:** Shader LOADING code (Java) we can transform. Shader SOURCE (GLSL) we cannot.

### 4.3 Closed Source Mods

```
Open source:   We can see what API calls are made
               We can build compatibility shims

Closed source: We can transform bytecode BUT
               We don't know WHAT the mod is trying to do
               We can't predict all edge cases
               If it breaks, we can't debug it

An example of this is a mod like OptiFine:
         - We can transform its bytecode
         - But it uses many internal MC classes
         - And has integrity checks
         - And has version-specific optimizations
         - We'd need to reverse-engineer the entire mod
```

### 4.4 Integrity Checks

```java
// Some mods do this:
public void init() {
    byte[] myCode = readMyOwnBytecode();
    String hash = sha256(myCode);

    if (!hash.equals(EXPECTED_HASH)) {
        throw new SecurityException("Modified!");
    }
}

// Retromod transforms the bytecode
// Hash changes
// Mod refuses to run
```

**Workaround:** We could patch out the integrity check, but this is ethically questionable.

---

## Summary Table

| Capability | Status | Difficulty | Notes |
|------------|--------|------------|-------|
| Public API redirects | ✅ Done | Easy | Core feature |
| Class package moves | ✅ Done | Easy | Core feature |
| Simple Mixin redirects | ✅ Done | Easy | Core feature |
| Mixin middle-man | ✅ Done | Medium | Intercepts and translates calls |
| Internal class mapping | 🔄 Possible | Hard | Needs large mapping database |
| AOT caching | ✅ Done | Medium | Already implemented |
| Signature changes | 🔄 Possible | Hard | Per-method work |
| Event system changes | 🔄 Possible | Hard | Known events only |
| Native code | ❌ Impossible | - | Different language |
| Shader code | ❌ Impossible | - | Not Java bytecode |
| Closed source (full) | ❌ Impractical | - | Can't predict behavior |

---

## What This Means for Complex Mods

Here's how this breaks down for mods that do more than just use public APIs.

**Example: a mod like Physics Mod**

| Component | Can We Transform? |
|-----------|-------------------|
| Java code | ✅ Yes |
| Mixin injections | ⚠️ With enhanced redirector |
| Internal MC class usage | ⚠️ If we build the mappings |
| Shader code | ❌ No |
| Custom physics engine (if native) | ❌ No |

**Realistic assessment:**
- Simple updates (1.21.4 → 1.21.11): 70% chance with enhanced system
- Major updates (1.20.4 → 1.21.11): 40% chance
- Very old (1.19 → 1.21.11): 20% chance

**Example: a resource pack like Fresh Animations**

Fresh Animations is a **resource pack** (JSON + textures), not Java code:
- ✅ We handle resource pack format changes
- ✅ We update `pack_format`
- ✅ We rename texture paths
- The companion mod (like Entity Model Features) is separate

