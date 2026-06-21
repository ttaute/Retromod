---
title: Technical
nav_order: 9
---

# How Retromod Works

Retromod is a bytecode transformer. When the game launches, Retromod rewrites old mod JARs so they target the current Minecraft API instead of the one they were built for. No source changes, no recompilation. The mod author never has to do anything.

This page explains the technical side at a level that should be useful if you're curious, want to contribute, or are evaluating whether Retromod is safe to run.

## The pipeline

```
┌──────────────────┐
│  Old mod JAR     │   e.g. mymod-1.21.1.jar, targets MC 1.21.1
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ ModVersionDetect │   reads fabric.mod.json / mods.toml
│                  │   extracts modId + target MC version
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  ShimRegistry    │   BFS search: find a chain of version shims
│                  │   1.21.1 → 1.21.2 → … → 26.1
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  RetromodXformer │   For each class in the JAR:
│                  │   - apply each shim's class/method/field redirects
│                  │   - IntermediaryToMojangMapper renames method_XXXX etc.
│                  │   - inject polyfills for removed APIs
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Metadata patcher │   Relax version constraints in fabric.mod.json /
│                  │   mods.toml so the mod is no longer rejected by
│                  │   Fabric/Forge version checks
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ TransformVerifier│   (optional) Scan the output JAR for references
│                  │   that don't exist in the current MC; write a
│                  │   report to config/retromod/verify-reports/
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Transformed JAR  │   mymod-1.21.1-retromod.jar, targets MC 26.1
└──────────────────┘
```

## The ASM visitor chain

Bytecode transformation uses [ASM](https://asm.ow2.io/). For each `.class` file:

```
ClassReader  →  ClassRemapper  →  RetromodClassVisitor  →  ClassWriter
              (outer - renames)    (inner - redirects)
```

`ClassRemapper` runs first and handles name translation (intermediary → Mojang). `RetromodClassVisitor` then sees the already-renamed names and applies shim redirects: method signature changes, constructor deferrals, polyfill injections. The final `ClassWriter` produces the new bytecode.

## Shims and polyfills

A **shim** is a pair of rules: "in mods targeting MC version X, when you see call `Foo.bar()`, rewrite it to `Foo.baz()` (or to `Polyfill.bar(Foo)`)." Shims are chained so a 1.16 mod can be walked up through 1.17, 1.18, 1.19, 1.20, 1.21 to 26.1.

A **polyfill** is a reimplementation of a removed API using current MC primitives. When the shim chain says "this method was removed in 1.20," the polyfill provides a drop-in replacement so old code continues to compile.

Retromod currently ships 145 version shims and 30 polyfill providers covering 328 redirects.

## Security model

Retromod reads and writes JARs on disk. That's a privileged operation, so the codebase is careful about:

- **Zip slip / path traversal:** every JAR entry goes through `ZipSecurity.safeEntryName()` before being written to disk or another archive.
- **Zip bombs:** `ZipSecurity.safeReadAllBytes(is, max)` counts actual bytes read, not header-declared sizes, so a crafted archive that lies about entry sizes can't blow up memory.
- **Arbitrary class loading:** `Class.forName()` is only ever called on class names harvested from user JARs with `initialize=false` and Retromod's own classloader. No user-supplied code is executed during transformation.
- **File deletion:** only within managed directories (`mods/`, `retromod-input/`, `retromod-backups/`, `config/retromod/`). No user files outside those paths are ever touched.

## Authenticity & forks

### This is a security feature, not an anti-fork feature

First, **I'm a big fan of open source.** Given two apps with the same features, one open source and one closed, I'll pick open source every time, and Retromod exists as open source for exactly that reason. Forks are welcome. You can rebuild it, modify it, redistribute it, or base a new project on it. The MIT license grants all of that and nothing in this section takes it back.

The signature-check and fork-notice system is here for **one reason only**: some bad actors edit open-source code to add malware and then redistribute it under the original name to trick users into trusting it. That's a real problem that's happened to lots of popular projects. The verifier lets honest users tell "this is the actual Retromod I downloaded from the official repo" apart from "this is a file labeled Retromod from a sketchy site." The goal is security, not keeping people from forking.

If Retromod is ever discontinued, all of this comes out. A verifier that fires forever on a dead project would just be noise on every legitimate fork that inherits the codebase. In that scenario the fork-notice text, the cert-check, and the hardened logging come out together, and whatever fork ends up as the community continuation just works.

Retromod is MIT-licensed. Forks are allowed, encouraged, and legally fine. The license grants that right without any conditions beyond keeping the license text.

There's a practical problem, though: **if a malicious actor publishes a JAR calling itself "Retromod" and the user downloads it from somewhere other than the official repo, we can't technically tell that apart from a legitimate fork.** Both show up the same way to our verification code: code that doesn't match the official build hash.

### How Retromod handles this

Official builds embed a SHA-256 of Retromod's own classes. At startup, `SignatureVerifier` re-hashes the running JAR and compares:

| Status | Meaning |
|--------|---------|
| `VERIFIED` | Bytecode matches the embedded release hash: unchanged from the published build. (A hash match isn't proof of provenance - there's no secret key - so the status says "verified," not "official.") |
| `MODIFIED` | Bytecode differs from the release hash: a fork, a repack, or a corrupted download. |
| `IMPOSTOR` | The manifest doesn't even claim to be Retromod. |
| `UNKNOWN` | Can't tell: a dev/source build (no hash embedded), or not running from a JAR. |

This is an integrity check, **not** cryptographic anti-tamper. There's no secret key, so a determined attacker can recompute the embedded hash. It catches accidental corruption and casual modification; for real verification, users compare the JAR's SHA-256 against the value published on the releases page.

The published build emits a normal startup line like:

> `[Retromod] ✓ Authenticity: VERIFIED - Bytecode matches the published release hash`

For any other status (`MODIFIED`, `IMPOSTOR`) the verifier **automatically** emits:

> `You are using a Retromod Fork. If this was advertised as the official Retromod, this is NOT official! Check github.com/Bownlux/Retromod for the real thing.`

The official build **never** emits this line. If the hash matches, it's the unmodified build.

### I'm making a fork, what do I do?

Forks are fine, encouraged, and fully MIT-licensed. If you keep the name "Retromod" in your fork's logs/UI, the fork notice fires automatically: no code you need to add, no flag to flip. That's the safe default. Every build whose code doesn't match the official hash announces itself, and a silent "Retromod" is an obvious red flag for users.

**There's no customizable version on purpose.** If forks could pass arbitrary text, a malicious build could log something like "Official Retromod v1.2.3, verified build" and users would have no way to tell. The fixed string plainly says "this is NOT official," which defeats that impersonation regardless of what the attacker calls themselves.

The notice deliberately points users to the GitHub repo rather than an email address, otherwise a single inbox gets flooded with "please verify this JAR" requests from people who could just compare hashes themselves.
If you've renamed your project entirely (no "Retromod" branding in your logs, manifest, or UI), the announcement isn't necessary. The `Implementation-Title` manifest check will report `IMPOSTOR` if your manifest doesn't claim to be Retromod, which is accurate for "a build that isn't claiming to be Retromod."

### Can I drop the notice from my fork?

**You don't need permission, and you don't need to contact anyone.** The MIT license already lets you change or remove anything in your fork. Adding an approval requirement wouldn't be enforceable anyway, and gatekeeping it isn't the point.

What we *ask*, as a community norm rather than a rule, is that you **keep the notice**. It costs nothing, and it's the whole reason it works: if honest forks keep it, a build that's *missing* it stands out as a red flag, which is what protects users from malware wearing the Retromod name. So remove it if you truly must, but please don't, and never replace it with a fake "official / verified" line.

If you've fully rebranded (no "Retromod" in your logs, manifest, or UI), the notice is moot anyway. See above.

### For users

If your logs contain a fork-notice line and you thought you downloaded the official Retromod:

1. **Check where you downloaded it from.** The official source is [github.com/Bownlux/Retromod](https://github.com/Bownlux/Retromod). If the JAR came from somewhere else, delete it and grab a fresh copy from the official repo.
2. If it came from a reputable modpack or fork you trust, the notice is expected. It just means the JAR isn't the official upstream build, so no action needed.
3. If it came from a site you don't recognize, treat it as suspicious. It could be malware using the Retromod name.

If you don't see any fork notice AND the authenticity log line isn't `VERIFIED`, that's also a red flag: a legitimate fork maintainer should have added the notice.

## The `Implementation-Title` check

Separately from signature checking, `SignatureVerifier` also reads the JAR's manifest. Official releases set `Implementation-Title: Retromod`. If a JAR claims a different `Implementation-Title`, we report `IMPOSTOR`: it's not even pretending to be Retromod.

This catches the trivial case of "someone renamed retromod.jar to something malicious.jar" and prevents some self-inflicted confusion.

## How to verify a download

The in-jar check is informational and can't defend against a determined attacker (there's no secret key). For real verification, compare the file's SHA-256 against the value published on the [GitHub releases page](https://github.com/Bownlux/Retromod/releases). Modrinth shows one per file too:

```bash
sha256sum retromod-1.1.0+26.1.jar       # Linux
shasum -a 256 retromod-1.1.0+26.1.jar   # macOS
```

If it matches the published hash, you have the exact official file. That reference lives out-of-band on the trusted page, where a tamperer can't change it. That's the guarantee an embedded self-hash can't make on its own.

## Further reading

- [Architecture]({{ '/architecture' | relative_url }}): higher-level walkthrough of the codebase
- [Authenticity]({{ '/authenticity' | relative_url }}): what the different signature statuses mean
- [Contributing]({{ '/contributing' | relative_url }}): how to contribute shims, polyfills, and fixes
