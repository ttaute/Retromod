---
title: Authenticity
nav_order: 7
---

# Authenticity / build integrity

Retromod includes a check that tells you whether the running build is the **unmodified official one**. It's **purely informational** — Retromod runs regardless of what it finds, and no feature is gated on it. This page explains how it works, what it can and can't promise, and how to actually verify a download.

## How it works

The official build embeds a SHA-256 of Retromod's own compiled classes. At startup, the verifier re-hashes the running JAR's own bytecode and compares:

- **match** → this is the official, unmodified build;
- **mismatch** → the code differs, so a fork notice is logged.

The hash covers only Retromod's own `com/retromod/` classes — not the bundled libraries (ASM, Gson, …), which the per-loader builds strip or relocate — so one value is valid across every official Fabric/NeoForge/Forge dist JAR.

## What this is — and what it isn't

It is an **integrity / modification check**. It reliably catches:

- **accidental corruption** — a truncated or garbled download;
- **casual modification** — a repack or fork that changed the code but didn't update the embedded hash.

It is **not** cryptographic anti-tamper. There is **no secret key**, so a determined attacker who edits the bytecode can simply recompute the embedded hash (or strip the check). Don't read "OFFICIAL" as a guarantee against a malicious actor — read it as "this looks like the unmodified upstream build."

**For real verification, compare hashes out-of-band.** Modrinth and GitHub publish a **SHA-256 for every release file**, shown on the download page. Compare the file you downloaded against that number — it lives on the trusted page, where a tamperer can't change it, which is the part an in-jar hash can't provide.

It is **not**:

- **DRM.** MIT license, fully preserved — fork, rebuild, redistribute, sell, sublicense. Modified builds run identically.
- **A blocker.** Every status below still lets Retromod start, load, and transform mods.

## The statuses

The verifier logs one of these at startup (search your log for `[Retromod] ... Authenticity:`):

### OFFICIAL
The running classes match the embedded official hash — the unmodified upstream build. This is what you get downloading a release straight from the [GitHub releases page](https://github.com/Bownlux/Retromod/releases) (or Modrinth) and not modifying it.

### MODIFIED
The classes differ from the official hash — a fork, a repack, or (occasionally) a corrupted download. Not inherently malicious; just not byte-for-byte the official build. If you didn't expect that, re-check where you got the file and compare its SHA-256 against the official releases page.

### IMPOSTOR
The JAR's manifest doesn't even claim to be Retromod. This is the one to actually worry about — it's the shape of a deliberate attempt to pass something else off as Retromod. Don't run it; grab a fresh copy from GitHub.

### UNKNOWN
Couldn't determine — typically a dev/source build (no hash embedded yet), or it's running from a directory rather than a JAR. Harmless.

## "I got a fork / MODIFIED notice — is my game going to explode?"

No. Lots of builds are legitimately not the official upstream: ones you compiled from source, modpack repacks, third-party launchers that re-bundle mods, and forks. MODIFIED just means "not byte-for-byte the official build." The only status worth immediate attention is IMPOSTOR. When in doubt, compare your file's SHA-256 against the [releases page](https://github.com/Bownlux/Retromod/releases).

## For fork maintainers

Forks are welcome and fully MIT-licensed. If you keep the name "Retromod" in your logs/UI, the fork notice fires automatically when your build's hash doesn't match — no code to add, no flag to flip.

**You don't need permission, and you don't need to contact anyone, to remove or change that notice** — the MIT license already lets you do whatever you want with your fork. We just *ask* that you keep it: it costs nothing, and it's exactly what lets users treat a build that's *silently* missing it as a red flag. And please don't replace it with a fake "official / verified" line — that's the one thing that actually hurts users.

If you've fully rebranded (no "Retromod" in your logs, manifest, or UI), the notice is moot anyway, and the manifest check simply reports your build isn't claiming to be Retromod.

## For contributors

The check lives in `src/main/java/com/retromod/security/SignatureVerifier.java` (the name is historical — it no longer uses JAR signatures). The release step that embeds the hash is documented in `CLAUDE.md` ("Release integrity"). It's a self-contained module; PRs to improve the user messaging are welcome.
