/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.vanilla;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfills for vanilla MC classes removed in <b>26.2</b>.
 *
 * <p><b>Host-gated to 26.2+.</b> Unlike the 26.1-era removals, these classes
 * still EXIST on a 26.1 host. Redirecting them there would point live code at
 * a stub (broken {@code instanceof}/casts against the real class). So both
 * {@link #getRemovedClasses()} and {@link #registerPolyfills} no-op below 26.2.
 *
 * <p>Covered:
 * <ul>
 *   <li>{@code net/minecraft/util/Tuple} (intermediary {@code class_3545}): a
 *       trivial mutable pair, reimplemented by
 *       {@link com.retromod.polyfill.minecraft.embedded.Tuple}. Widely used by
 *       older mods; one of the bigger 26.2 removals after the render layer.</li>
 * </ul>
 *
 * <p>Future 26.2 removals that need <i>adapter</i> polyfills (the render API:
 * {@code MultiBufferSource}, the {@code Tesselator}/{@code VertexFormat} vertex
 * layer) are out of scope here: they're reshaped APIs, not trivial reimplements,
 * and are tracked for 1.3.0 in the roadmap.
 */
public class Minecraft26_2RemovedPolyfill implements PolyfillProvider {

    private static final String EMBEDDED_TUPLE = "com/retromod/polyfill/minecraft/embedded/Tuple";

    /** True only when the host MC is 26.2 or newer (where these classes are gone). */
    private static boolean active() {
        // target >= 26.2  ⟺  "26.2" is NOT strictly greater than target.
        // Guards against 26.1.2 (which is < 26.2 despite the third component).
        return !RetromodVersion.mcVersionExceeds("26.2", RetromodVersion.TARGET_MC_VERSION);
    }

    @Override
    public String getName() {
        return "Minecraft 26.2 Removed APIs";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        if (!active()) return new String[0];
        return new String[]{
            "net/minecraft/util/Tuple",
            "net/minecraft/class_3545"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{ EMBEDDED_TUPLE };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        if (!active()) {
            return; // Tuple still exists on 26.1 and earlier. Do not hijack it.
        }
        // Register BOTH the Mojang name and the intermediary name: the ASM
        // remapper is single-pass, so class_3545→Tuple wouldn't chain into
        // Tuple→polyfill (same rationale as the LazyLoadedValue polyfill).
        transformer.registerClassRedirect("net/minecraft/util/Tuple", EMBEDDED_TUPLE);
        transformer.registerClassRedirect("net/minecraft/class_3545", EMBEDDED_TUPLE);
    }
}
