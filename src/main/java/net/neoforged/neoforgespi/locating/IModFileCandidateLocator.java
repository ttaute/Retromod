/*
 * Retromod - compile-time stub of
 * net.neoforged.neoforgespi.locating.IModFileCandidateLocator.
 * Copyright (c) 2026 Bownlux. MIT License.
 *
 * Compile-only; stripped from the production jar. This is the SPI Retromod's
 * mod-locator implements so NeoForge will discover jars from a Retromod-owned
 * folder (mods/Retromod/) - the CurseForge-export compat path (#78). The real
 * interface is provided by NeoForge at runtime; RetromodModLocator is registered
 * via META-INF/services so FML's early-service scan picks it up.
 */
package net.neoforged.neoforgespi.locating;

import net.neoforged.neoforgespi.ILaunchContext;

public interface IModFileCandidateLocator extends IOrderedProvider {
    void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline);
}
