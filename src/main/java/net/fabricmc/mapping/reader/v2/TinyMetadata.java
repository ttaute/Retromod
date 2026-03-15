/*
 * RetroMod Polyfill - Stub for removed Fabric Mapping API
 * Copyright (c) 2026 Bownlux
 */
package net.fabricmc.mapping.reader.v2;

import java.util.List;
import java.util.Map;

/**
 * Stub replacement for the removed TinyMetadata interface.
 */
public interface TinyMetadata {
    default int getMajorVersion() { return 2; }
    default int getMinorVersion() { return 0; }
    default List<String> getNamespaces() { return List.of(); }
    default Map<String, String> getProperties() { return Map.of(); }

    /**
     * Returns the column index for the given namespace name.
     * NEC calls this with "intermediary" and "named" to find which
     * columns in the mapping file correspond to those namespaces.
     */
    default int index(String namespace) {
        List<String> ns = getNamespaces();
        for (int i = 0; i < ns.size(); i++) {
            if (ns.get(i).equals(namespace)) return i;
        }
        return -1;
    }
}
