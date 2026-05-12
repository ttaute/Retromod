/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the removed net.fabricmc.mapping.reader.v2 package.
 *
 * This API was part of Fabric Loader's TinyV2 mapping reader
 * and was removed in newer Fabric Loader versions. Mods like
 * Not Enough Crashes used TinyVisitor for stack trace deobfuscation.
 *
 * The polyfill provides stub interfaces so the classes can be found,
 * preventing ClassNotFoundException at startup. The actual mapping
 * reading functionality is not replicated (stubs are no-ops).
 */
public class TinyMappingPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Fabric TinyMapping Reader API";
    }

    @Override
    public String getCategory() {
        return "fabric_api";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/fabricmc/mapping/reader/v2/TinyVisitor",
            "net/fabricmc/mapping/reader/v2/TinyV2Visitor",
            "net/fabricmc/mapping/reader/v2/TinyMappingFactory",
            "net/fabricmc/mapping/reader/v2/TinyMetadataConsumer"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // Stubs relocated to com.retromod.polyfill.fabric.embedded to avoid
        // JPMS split-package conflicts on 26.1+
        return new String[]{
            "com.retromod.polyfill.fabric.embedded.TinyVisitor",
            "com.retromod.polyfill.fabric.embedded.TinyV2Visitor",
            "com.retromod.polyfill.fabric.embedded.TinyMappingFactory"
        };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // Register class redirects from removed Fabric mapping classes
        // to our relocated stubs in com.retromod.polyfill.fabric.embedded
        transformer.registerClassRedirect(
            "net/fabricmc/mapping/reader/v2/TinyVisitor",
            "com/retromod/polyfill/fabric/embedded/TinyVisitor"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/mapping/reader/v2/TinyV2Visitor",
            "com/retromod/polyfill/fabric/embedded/TinyV2Visitor"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/mapping/reader/v2/TinyMappingFactory",
            "com/retromod/polyfill/fabric/embedded/TinyMappingFactory"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/mapping/reader/v2/TinyMetadata",
            "com/retromod/polyfill/fabric/embedded/TinyVisitor$TinyMetadata"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/mapping/reader/v2/MappingGetter",
            "com/retromod/polyfill/fabric/embedded/TinyVisitor$MappingGetter"
        );

        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
