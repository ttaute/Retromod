/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of the removed TinyV2Factory class.
 *
 * NEC (Not Enough Crashes) uses TinyV2Factory.visit() to parse Yarn
 * mapping files for stack trace deobfuscation. This delegates to
 * TinyMappingFactory's real TinyV2 parser implementation.
 */
package net.fabricmc.mapping.reader.v2;

import java.io.BufferedReader;

/**
 * Reimplementation of TinyV2Factory that delegates to TinyMappingFactory.
 *
 * TinyV2Factory was a convenience class in the old Fabric mapping reader API
 * that specifically handled TinyV2 format files. It's functionally equivalent
 * to TinyMappingFactory.load() for V2 files.
 *
 * NEC calls: TinyV2Factory.visit(BufferedReader, TinyVisitor)
 */
public final class TinyV2Factory {

    private TinyV2Factory() {}

    /**
     * Parses a TinyV2 mapping file and invokes the visitor callbacks.
     * This is the method NEC calls for stack trace deobfuscation.
     *
     * @param reader  a BufferedReader positioned at the start of a TinyV2 file
     * @param visitor the visitor to receive mapping data
     */
    public static void visit(BufferedReader reader, TinyVisitor visitor) {
        TinyMappingFactory.load(reader, visitor);
    }
}
