/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Full reimplementation of the removed TinyMappingFactory.
 * Delegates to the main polyfill at net.fabricmc.mapping.reader.v2.TinyMappingFactory.
 */
package com.retromod.polyfill.fabric.embedded;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Embedded copy of the TinyMappingFactory reimplementation.
 * Provides real TinyV2 mapping file parsing for NEC stack trace deobfuscation.
 */
public final class TinyMappingFactory {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private TinyMappingFactory() {}

    public static void load(BufferedReader reader, TinyVisitor visitor) {
        try {
            loadV2(reader, visitor);
        } catch (IOException e) {
            LOGGER.warning("[RetroMod] TinyMappingFactory: failed to parse: " + e.getMessage());
        }
    }

    public static void loadWithDetection(BufferedReader reader, TinyVisitor visitor) {
        try {
            reader.mark(4096);
            String firstLine = reader.readLine();
            reader.reset();
            if (firstLine != null && firstLine.startsWith("tiny\t2\t")) {
                loadV2(reader, visitor);
            } else {
                loadV2(reader, visitor);
            }
        } catch (IOException e) {
            LOGGER.warning("[RetroMod] TinyMappingFactory: detection failed: " + e.getMessage());
        }
    }

    private static void loadV2(BufferedReader reader, TinyVisitor visitor) throws IOException {
        String line = reader.readLine();
        if (line == null) return;

        String[] headerParts = line.split("\t");
        if (headerParts.length < 5 || !headerParts[0].equals("tiny")) return;

        int majorVersion, minorVersion;
        try {
            majorVersion = Integer.parseInt(headerParts[1]);
            minorVersion = Integer.parseInt(headerParts[2]);
        } catch (NumberFormatException e) { return; }

        List<String> namespaces = new ArrayList<>();
        for (int i = 3; i < headerParts.length; i++) namespaces.add(headerParts[i]);

        final int nsCount = namespaces.size();
        final int majV = majorVersion, minV = minorVersion;
        final List<String> nsList = namespaces;
        final Map<String, String> properties = new HashMap<>();

        TinyVisitor.TinyMetadata metadata = new TinyVisitor.TinyMetadata() {
            @Override public int getMajorVersion() { return majV; }
            @Override public int getMinorVersion() { return minV; }
            @Override public List<String> getNamespaces() { return nsList; }
            @Override public Map<String, String> getProperties() { return properties; }
        };

        visitor.start(metadata);

        int currentDepth = 0;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) continue;
            int depth = 0;
            while (depth < line.length() && line.charAt(depth) == '\t') depth++;
            String content = line.substring(depth);
            String[] parts = content.split("\t");
            if (parts.length == 0) continue;

            if (depth < currentDepth) {
                visitor.pop(currentDepth - depth);
                currentDepth = depth;
            }

            String type = parts[0];
            switch (type) {
                case "c":
                    if (depth == 0 && parts.length >= 1 + nsCount) {
                        visitor.pushClass(createGetter(parts, 1, nsCount));
                        currentDepth = depth + 1;
                    } else if (depth > 0) {
                        visitor.pushComment(parts.length > 1 ? parts[1] : "");
                    }
                    break;
                case "f":
                    if (depth == 1 && parts.length >= 2 + nsCount) {
                        visitor.pushField(createGetter(parts, 2, nsCount), parts[1]);
                        currentDepth = depth + 1;
                    }
                    break;
                case "m":
                    if (depth == 1 && parts.length >= 2 + nsCount) {
                        visitor.pushMethod(createGetter(parts, 2, nsCount), parts[1]);
                        currentDepth = depth + 1;
                    }
                    break;
                case "p":
                    if (depth == 2 && parts.length >= 2 + nsCount) {
                        try {
                            int lvIdx = Integer.parseInt(parts[1]);
                            visitor.pushParameter(createGetter(parts, 2, nsCount), lvIdx);
                            currentDepth = depth + 1;
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                case "v":
                    if (depth == 2 && parts.length >= 4 + nsCount) {
                        try {
                            visitor.pushLocalVariable(createGetter(parts, 4, nsCount),
                                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                            currentDepth = depth + 1;
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
            }
        }
        if (currentDepth > 0) visitor.pop(currentDepth);
    }

    private static TinyVisitor.MappingGetter createGetter(String[] parts, int offset, int count) {
        final String[] names = new String[count];
        for (int i = 0; i < count && offset + i < parts.length; i++) {
            names[i] = parts[offset + i];
        }
        return new TinyVisitor.MappingGetter() {
            @Override public String get(int namespace) {
                return (namespace >= 0 && namespace < names.length && names[namespace] != null)
                    ? names[namespace] : (names.length > 0 ? names[0] : "");
            }
            @Override public String getRawName(int namespace) { return get(namespace); }
            @Override public String[] getAllNames() { return names.clone(); }
        };
    }
}
