# test-mods

The Retromod regression test mod, one standalone Gradle project per loader. Each builds a small mod
whose `TestRunner` executes a suite of `Test` cases at init and prints pass/fail per case to the
launch log. When a user-reported bug is fixed, a regression case is added here so it can't silently
come back (see `CLAUDE.md` -> "Per-issue regression process").

| Project | Loader | Notes |
|---------|--------|-------|
| `retromod-test-mod/` | Fabric | The primary suite. Test cases live in `src/main/java/com/retromod/testmod/tests/`. |
| `retromod-test-mod-forge/` | Forge | Loader-specific cases. |
| `retromod-test-mod-neoforge/` | NeoForge | Loader-specific cases. |

These are Gradle projects (the root Retromod build is Maven). Their Gradle wrapper/build files are
intentionally not tracked (the root `.gitignore` ignores `build.gradle` / `gradlew` / etc. repo-wide
"project uses Maven, not Gradle"), so only sources + resources are versioned. `rootProject.name` is
unchanged by the move into `test-mods/`, so built jars keep their names
(`retromod-test-mod-<ver>.jar`, ...).

Build one:

```bash
cd test-mods/retromod-test-mod && ./gradlew build      # Fabric
cd test-mods/retromod-test-mod-neoforge && ./gradlew build
```

Then run it through Retromod (transform to the target MC) and launch per the acceptance steps in
`CLAUDE.md`: a passing SUMMARY at init is not a passing launch, so confirm the game outlives init
(window/title-screen log lines, no new `crash-reports/` entry).
