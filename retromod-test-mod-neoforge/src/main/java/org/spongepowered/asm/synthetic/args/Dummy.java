/*
 * Retromod Test Mod (NeoForge)
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package org.spongepowered.asm.synthetic.args;

/**
 * Replica of the placeholder class 1.20.1-era Forge mods (Blueprint 7.x and
 * mods built from its template) ship so their module exports Mixin's
 * runtime-generated args package. On NeoForge 1.20.2+ the host's
 * {@code mixin_synthetic} module owns this package, so a mod jar still
 * containing it fails JPMS module resolution for the whole layer (#87).
 *
 * <p>This test mod ships it ON PURPOSE: Retromod's transform must strip
 * {@code org/spongepowered/asm/synthetic/} from the jar, and the in-game
 * check asserts this class is no longer findable afterwards.
 */
public final class Dummy {
    private Dummy() {}
}
