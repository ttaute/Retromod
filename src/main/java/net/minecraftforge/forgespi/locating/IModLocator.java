/*
 * Retromod - compile-time stub of net.minecraftforge.forgespi.locating.IModLocator.
 * Copyright (c) 2026 Bownlux. MIT License.
 *
 * Compile-only; stripped from the production jar. This is the Forge SPI Retromod's
 * mod-locator implements so Forge discovers jars from mods/Retromod/ (#78). The real
 * interface's scanMods() returns List<IModLocator.ModFileOrException>; generic
 * erasure makes the raw List here descriptor-identical, so we avoid stubbing the
 * record too. The real interface + ModsFolderLocator are provided by Forge at
 * runtime (RetromodForgeModLocator delegates to the latter reflectively).
 */
package net.minecraftforge.forgespi.locating;

import java.util.List;

public interface IModLocator extends IModProvider {
    List<?> scanMods();
}
