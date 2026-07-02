/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the AccessWidener -> NeoForge/Forge AccessTransformer conversion (#92).
 * On a NeoForge/Forge target the mod's widened MC access must be preserved as a
 * real META-INF/accesstransformer.cfg rather than silently dropped.
 */
class AccessWidenerToAtConverterTest {

    @Test
    @DisplayName("class/method/field directives map to the right AT modifiers and dotted names")
    void convertsCoreModifiers() {
        String aw = String.join("\n",
                "accessWidener v2 named",
                "accessible class net/minecraft/world/entity/Entity",
                "extendable class net/minecraft/world/level/block/Block",
                "accessible method net/minecraft/world/entity/Entity move (I)V",
                "extendable method net/minecraft/world/entity/Entity tick ()V",
                "accessible field net/minecraft/world/entity/Entity level Lnet/minecraft/world/level/Level;",
                "mutable field net/minecraft/world/entity/Entity yaw F");

        String at = AccessWidenerToAtConverter.convert(aw);

        // Header line dropped, dotted class names, correct modifier table.
        assertTrue(at.contains("public net.minecraft.world.entity.Entity\n"),
                "accessible class -> public: " + at);
        assertTrue(at.contains("public-f net.minecraft.world.level.block.Block\n"),
                "extendable class -> public-f (stay subclassable): " + at);
        // method joins name+descriptor, no space between them.
        assertTrue(at.contains("public net.minecraft.world.entity.Entity move(I)V\n"),
                "accessible method -> public name+desc: " + at);
        assertTrue(at.contains("protected-f net.minecraft.world.entity.Entity tick()V\n"),
                "extendable method -> protected-f (stay overridable): " + at);
        // field by name only, no descriptor.
        assertTrue(at.contains("public net.minecraft.world.entity.Entity level\n"),
                "accessible field -> public name-only: " + at);
        assertTrue(at.contains("public-f net.minecraft.world.entity.Entity yaw\n"),
                "mutable field -> public-f (writable): " + at);

        // No AW-format leftovers.
        assertFalse(at.contains("accessWidener"), "AW header must be gone");
        assertFalse(at.contains("accessible "), "no AW verbs may survive: " + at);
        assertFalse(at.contains("/"), "internal names must be dotted: " + at);
    }

    @Test
    @DisplayName("transitive- prefix and # comments are handled")
    void handlesTransitiveAndComments() {
        String aw = String.join("\n",
                "accessWidener v2 named",
                "# a leading comment",
                "transitive-accessible class net/minecraft/Foo   # trailing comment",
                "",
                "accessible method net/minecraft/Foo bar ()V");

        String at = AccessWidenerToAtConverter.convert(aw);
        assertTrue(at.contains("public net.minecraft.Foo\n"), at);
        assertTrue(at.contains("public net.minecraft.Foo bar()V\n"), at);
        assertFalse(at.contains("transitive"), "transitive- prefix must be stripped: " + at);
        assertFalse(at.contains("trailing comment"), "trailing comment must be stripped: " + at);
    }

    @Test
    @DisplayName("no translatable directives yields empty output (nothing to emit)")
    void headerOnlyIsEmpty() {
        assertEquals("", AccessWidenerToAtConverter.convert("accessWidener v2 named\n"));
        assertEquals("", AccessWidenerToAtConverter.convert(""));
        assertEquals("", AccessWidenerToAtConverter.convert(null));
    }

    @Test
    @DisplayName("an intermediary-namespace AW is refused (no unresolvable AT emitted)")
    void refusesIntermediaryNamespace() {
        // This converter does no name remapping, so an intermediary AW would emit an AT
        // referencing classes absent on NeoForge/Forge. It must return empty so the caller
        // just deletes the AW (as it did before AT generation existed).
        String aw = String.join("\n",
                "accessWidener v2 intermediary",
                "accessible class net/minecraft/class_1297",
                "accessible method net/minecraft/class_1297 method_5773 ()V");
        assertEquals("", AccessWidenerToAtConverter.convert(aw),
                "an intermediary-namespace AW must not be converted");
    }

    @Test
    @DisplayName("a member directive implicitly widens its owning class (AW semantics)")
    void memberDirectiveWidensOwnerImplicitly() {
        // Foo has no explicit class directive, only a member one; AW makes the owner public.
        String aw = String.join("\n",
                "accessWidener v2 named",
                "accessible method net/minecraft/Foo bar ()V",
                "mutable field net/minecraft/Foo baz I");
        String at = AccessWidenerToAtConverter.convert(aw);

        assertTrue(at.contains("public net.minecraft.Foo #"),
                "member owner gets an implicit `public <owner>` line: " + at);
        // Exactly one owner line even though two members share the owner (de-duplicated).
        long ownerLines = at.lines().filter(l -> l.startsWith("public net.minecraft.Foo #")).count();
        assertEquals(1, ownerLines, "implicit owner-widening is de-duplicated: " + at);
    }

    @Test
    @DisplayName("an explicit class directive suppresses the implicit owner line")
    void explicitClassDirectiveSuppressesImplicitOwner() {
        // Block is widened extendable (public-f) explicitly AND has a member; the explicit
        // class line must stand and no lower `public Block` implicit line may be added.
        String aw = String.join("\n",
                "accessWidener v2 named",
                "extendable class net/minecraft/Block",
                "accessible method net/minecraft/Block tick ()V");
        String at = AccessWidenerToAtConverter.convert(aw);

        assertTrue(at.contains("public-f net.minecraft.Block\n"),
                "the explicit class directive is preserved: " + at);
        assertFalse(at.contains("public net.minecraft.Block #"),
                "no implicit owner line when an explicit class directive exists: " + at);
    }
}
