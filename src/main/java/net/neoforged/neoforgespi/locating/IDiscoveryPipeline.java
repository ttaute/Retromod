/*
 * Retromod - compile-time stub of
 * net.neoforged.neoforgespi.locating.IDiscoveryPipeline.
 * Copyright (c) 2026 Bownlux. MIT License.
 *
 * Compile-only; stripped from the production jar. Only the single-path addPath
 * overload RetromodModLocator calls is declared. In the real interface this is
 * a default method (a convenience wrapper over addPath(List, ...)); declaring it
 * abstract here is fine because invokeinterface resolves to the real default at
 * runtime. The erased descriptor - (Path, ModFileDiscoveryAttributes,
 * IncompatibleFileReporting) -> Optional - matches NeoForge exactly.
 */
package net.neoforged.neoforgespi.locating;

import java.nio.file.Path;
import java.util.Optional;

public interface IDiscoveryPipeline {
    Optional<?> addPath(Path path, ModFileDiscoveryAttributes attributes, IncompatibleFileReporting reporting);
}
