# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.0-beta.x | Yes |

## Reporting a Vulnerability

If you discover a security vulnerability in RetroMod, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please email: **security@revivalsmp.com**

Or use [GitHub's private vulnerability reporting](https://github.com/Bownlux/RetroMod/security/advisories/new).

### What to include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response timeline

- **Acknowledgment:** Within 48 hours
- **Initial assessment:** Within 1 week
- **Fix release:** As soon as possible, depending on severity

## Scope

### In scope

- **Bytecode transformation** — RetroMod rewrites mod bytecode using ASM. Vulnerabilities in the transformation pipeline that could allow arbitrary code execution or class injection are critical.
- **Mod JAR processing** — RetroMod reads, extracts, and rewrites JAR files. Path traversal, zip slip, or malicious JAR handling issues are in scope.
- **AOT cache** — Transformed mods are cached on disk. Cache poisoning or unauthorized cache manipulation is in scope.
- **Mapping files** — RetroMod loads and processes mapping files. Malicious mapping files that could cause unexpected behavior are in scope.
- **Network requests** — RetroMod checks for updates via Modrinth API. Man-in-the-middle or response injection vulnerabilities are in scope.

### Out of scope

- Vulnerabilities in Minecraft itself, Fabric Loader, NeoForge, or Forge
- Vulnerabilities in mods that RetroMod transforms (RetroMod doesn't audit mod code)
- Denial of service via large/malformed mod JARs (RetroMod will just skip them)
- Social engineering attacks

## Security Design

### Bytecode safety

- RetroMod only performs **class/method/field redirects** and **metadata patching** — it does not inject new behavior into mod code
- All bytecode transformations are deterministic and auditable via `dump_bytecode: true` in config
- The transformation pipeline uses ASM's `ClassVisitor` pattern which operates on a structured class representation, not raw bytes

### File handling

- RetroMod writes transformed mods to `mods/` and backs up originals to `retromod-backups/`
- AOT cache is stored in `config/retromod/aot-cache/` with hash-based filenames
- All file operations are confined to the Minecraft game directory

### No remote code execution

- RetroMod does not download or execute code from the internet
- Update checks are read-only API calls to Modrinth
- Mapping files are bundled in the JAR, not fetched at runtime
