/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill;

import com.retromod.core.RetromodTransformer;

/**
 * Re-implements removed APIs by providing class implementations, where VersionShims
 * only redirect existing calls. Lets old mods find classes/methods that no longer
 * exist in the current Minecraft version.
 *
 * <p>Discovered via ServiceLoader, toggled per-category in config/retromod/config.json.
 *
 * <p><b>Public Addon API.</b> A stable extension point for third-party addons (see
 * {@code docs/addons.md}). Across the 1.x line new methods are added only as
 * {@code default}s; existing signatures won't break.
 */
public interface PolyfillProvider {

    /**
     * @return Human-readable name (e.g., "Fabric TinyMapping API")
     */
    String getName();

    /**
     * @return Category for config toggling (e.g., "fabric_api", "rendering", "mixin_targets")
     */
    String getCategory();

    /**
     * @return Internal names of the removed classes this polyfill replaces
     */
    String[] getRemovedClasses();

    /**
     * @return Fully qualified names of embedded polyfill classes shipped in the JAR
     */
    String[] getPolyfillClasses();

    /**
     * Register class/method/field and superclass redirects to wire up the polyfill.
     *
     * @param transformer The transformer to register redirects with
     */
    void registerPolyfills(RetromodTransformer transformer);
}
