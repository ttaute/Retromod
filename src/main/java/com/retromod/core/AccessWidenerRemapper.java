/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import com.retromod.mapping.IntermediaryToMojangMapper;

/**
 * Remaps a Fabric access widener / classTweaker file from the {@code intermediary} namespace to
 * {@code official} (Mojang) for a 26.1+ deployment. Single source of truth shared by the runtime
 * ({@link FabricModTransformer}) and offline ({@code RetromodCli} batch / AOT / nested-jar) paths.
 *
 * <p><b>Why it matters:</b> 26.1+ dropped obfuscation, so Fabric's runtime namespace is
 * {@code official}. A distributed mod ships its access widener in the {@code intermediary} namespace
 * ({@code accessWidener v1 intermediary}), and Fabric's classTweaker reader REJECTS a namespace that
 * doesn't match the runtime, throwing {@code ClassTweakerFormatException} during
 * {@code FabricLoaderImpl.loadClassTweakers} - BEFORE any mixin/mod construction, so it crashes the
 * game with no crash-report. This bites nested (jar-in-jar) libraries too (e.g. cloth-config bundled
 * inside another mod). Remapping the header namespace and every {@code class_/method_/field_} token to
 * Mojang names makes the file load.
 */
public final class AccessWidenerRemapper {

    private AccessWidenerRemapper() {}

    /**
     * Remap {@code content} from the intermediary namespace to official, or return it unchanged if it
     * is not an intermediary access widener. The header line's namespace becomes {@code official} and
     * every body line's intermediary tokens are mapped to Mojang names. Never throws; on any failure
     * the original content is returned (fail-safe: the caller then relies on the metadata-level
     * classTweaker strip so the game still loads).
     */
    public static String remapToOfficial(String content, IntermediaryToMojangMapper mapper) {
        try {
            if (content == null || !content.contains("intermediary")) return content;
            String[] lines = content.split("\n", -1);
            StringBuilder patched = new StringBuilder(content.length() + 16);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                String out;
                if (i == 0) {
                    // header: "accessWidener v1 intermediary" -> "... official" (tabs/spaces preserved)
                    out = line.replace("intermediary", "official");
                } else if (line.isEmpty() || line.startsWith("#")) {
                    out = line;
                } else {
                    out = mapper.remapString(line);
                }
                patched.append(out);
                if (i < lines.length - 1) patched.append("\n");
            }
            return patched.toString();
        } catch (Throwable t) {
            return content;
        }
    }
}
