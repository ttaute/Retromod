/*
 * Retromod - compile-time stub of net.minecraftforge.forgespi.locating.IModProvider.
 * Copyright (c) 2026 Bownlux. MIT License.
 *
 * Compile-only; stripped from the production jar. Faithful to Forge forgespi 8.0.0
 * (MC 26.x) - all four abstract methods are declared so RetromodForgeModLocator
 * satisfies the real interface at runtime.
 */
package net.minecraftforge.forgespi.locating;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public interface IModProvider {
    String name();

    void scanFile(IModFile modFile, Consumer<Path> pathConsumer);

    void initArguments(Map<String, ?> arguments);

    boolean isValid(IModFile modFile);
}
