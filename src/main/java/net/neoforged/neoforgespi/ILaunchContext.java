/*
 * Retromod — compile-time stub of net.neoforged.neoforgespi.ILaunchContext.
 * Copyright (c) 2026 Bownlux. MIT License.
 *
 * Compile-only; the REAL interface is provided by NeoForge at runtime and this
 * stub's .class is stripped from the production jar (see the maven-jar-plugin
 * <excludes>). Only the one method RetromodModLocator actually calls is
 * declared — the real interface has more, but invokeinterface resolves against
 * the real type at runtime, so a subset stub is sufficient and safe.
 */
package net.neoforged.neoforgespi;

import java.nio.file.Path;

public interface ILaunchContext {
    Path gameDirectory();
}
