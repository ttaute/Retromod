/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.mapping;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * 26.1 renamed {@code ClientTooltipComponent.renderText/renderImage} to
 * {@code extractText/extractImage} (the render methods took {@code GuiGraphics}, which itself
 * became {@code GuiGraphicsExtractor}). A distributed 1.21.x Fabric mod overrides them under the
 * intermediary ids {@code method_32665}/{@code method_32666}; the intermediary->Mojang table was
 * 1.21.4-based and mapped those to the OLD names, so on a 26.1+ host the mod's overrides were
 * renamed to {@code renderText/renderImage} and no longer overrode the interface's abstract-ish
 * default methods. The vanilla empty defaults then ran, so the mod's custom tooltip drew nothing
 * (Apollo's Enchantment Rebalance: enchanted books showed no enchantment lines, #119 follow-up).
 * The fix corrects the two intermediary-id mappings to the 26.1 names.
 */
class ClientTooltipRenderRenameTest {

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    @Test
    @DisplayName("intermediary table maps method_32665/method_32666 to extractText/extractImage")
    void mappingUsesRenamedNames() {
        IntermediaryToMojangMapper m = IntermediaryToMojangMapper.getInstance();
        assertEquals("extractText", m.mapMethod("method_32665"),
                "ClientTooltipComponent render text method must map to the 26.1 name");
        assertEquals("extractImage", m.mapMethod("method_32666"),
                "ClientTooltipComponent render image method must map to the 26.1 name");
    }

    /** A ClientTooltipComponent impl overriding the two render methods by intermediary id. */
    private static byte[] tooltipImplClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, ACC_PUBLIC, "test/CustomTooltip", null, "java/lang/Object",
                new String[]{"net/minecraft/class_5684"}); // ClientTooltipComponent (intermediary)
        // method_32665(class_332 gui, class_327 font, int, int) = renderText -> extractText
        MethodVisitor a = cw.visitMethod(ACC_PUBLIC, "method_32665",
                "(Lnet/minecraft/class_332;Lnet/minecraft/class_327;II)V", null, null);
        a.visitCode(); a.visitInsn(RETURN); a.visitMaxs(0, 0); a.visitEnd();
        // method_32666(class_327 font, int,int,int,int, class_332 gui) = renderImage -> extractImage
        MethodVisitor b = cw.visitMethod(ACC_PUBLIC, "method_32666",
                "(Lnet/minecraft/class_327;IIIILnet/minecraft/class_332;)V", null, null);
        b.visitCode(); b.visitInsn(RETURN); b.visitMaxs(0, 0); b.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("an intermediary ClientTooltipComponent override transforms to extractText/extractImage")
    void overrideRemapsToRenamedMethods() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        // mirror the runtime 26.1+ path exactly (class redirects + method/field maps + moves)
        IntermediaryToMojangMapper.applyTo(t);

        byte[] out = t.transformClass(tooltipImplClass(), "test/CustomTooltip.class");
        assertNotNull(out);

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        boolean sawExtractText = false, sawExtractImage = false, sawStaleName = false;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("extractText")) sawExtractText = true;
            if (mn.name.equals("extractImage")) sawExtractImage = true;
            if (mn.name.equals("renderText") || mn.name.equals("renderImage")
                    || mn.name.startsWith("method_")) sawStaleName = true;
        }
        assertTrue(sawExtractText, "render-text override must become extractText (26.1 name)");
        assertTrue(sawExtractImage, "render-image override must become extractImage (26.1 name)");
        assertFalse(sawStaleName, "no stale renderText/renderImage/method_ name may survive");
    }
}
