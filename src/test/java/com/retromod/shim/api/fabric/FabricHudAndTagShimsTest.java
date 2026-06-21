/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks for the two newest 26.1 compatibility shims:
 * {@link FabricHudRenderCallbackShim} (extends + default-bridge synthetic) and
 * {@link FabricConventionTagsShim} (v1 → v2 class + field-rename redirects).
 */
class FabricHudAndTagShimsTest {

    private static final String SAM_DESC =
            "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";

    @BeforeAll
    static void pinHostTo26_1() {
        RetromodVersion.TARGET_MC_VERSION = "26.1";
    }

    @Test
    @DisplayName("Hud synthetic: extends HudElement, old SAM is the only abstract, default forwards")
    void hudSyntheticShape() {
        ClassNode cn = new ClassNode();
        new ClassReader(FabricHudRenderCallbackShim.generateInterface()).accept(cn, 0);

        assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0);
        assertTrue(cn.interfaces.contains("net/fabricmc/fabric/api/client/rendering/v1/hud/HudElement"),
                "must extend the new HudElement so listeners/invoker ARE HudElements");

        long abstracts = cn.methods.stream().filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).count();
        assertEquals(1, abstracts, "exactly one abstract method - must stay a functional interface");
        MethodNode sam = cn.methods.stream().filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).findFirst().orElseThrow();
        assertEquals("onHudRender", sam.name, "old SAM name so v1 lambdas keep linking");
        assertEquals(SAM_DESC, sam.desc);

        MethodNode def = cn.methods.stream().filter(m -> m.name.equals("extractRenderState")).findFirst().orElse(null);
        assertNotNull(def, "must bridge the new SAM with a default method");
        assertEquals(0, def.access & Opcodes.ACC_ABSTRACT, "extractRenderState must be a DEFAULT method");
        boolean forwards = false;
        for (AbstractInsnNode insn : def.instructions) {
            if (insn instanceof MethodInsnNode m && m.name.equals("onHudRender") && m.desc.equals(SAM_DESC)) {
                forwards = true;
            }
        }
        assertTrue(forwards, "the default method must forward to onHudRender");
        assertTrue(cn.fields.stream().anyMatch(f -> f.name.equals("EVENT")), "mods read HudRenderCallback.EVENT");
        assertTrue(cn.methods.stream().anyMatch(m -> m.name.equals("<clinit>")), "EVENT needs a static initializer");
    }

    @Test
    @DisplayName("Hud shim redirects the old class onto the synthetic (26.1 host)")
    void hudRedirect() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricHudRenderCallbackShim().registerRedirects(t);
        assertEquals("com/retromod/generated/legacyhud/HudRenderCallback",
                t.getClassRedirects().get("net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback"));
    }

    @Test
    @DisplayName("convention tags: all six v1 holders redirect to v2 + renamed fields mapped")
    void conventionTags() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricConventionTagsShim().registerRedirects(t);
        var cr = t.getClassRedirects();

        for (String holder : new String[]{"ConventionalItemTags", "ConventionalBlockTags",
                "ConventionalBiomeTags", "ConventionalEnchantmentTags",
                "ConventionalEntityTypeTags", "ConventionalFluidTags"}) {
            assertEquals("net/fabricmc/fabric/api/tag/convention/v2/" + holder,
                    cr.get("net/fabricmc/fabric/api/tag/convention/v1/" + holder),
                    holder + " v1 must redirect to v2");
        }

        // Renamed fields are keyed on the V2 owner (post-ClassRemapper form).
        var fr = t.getFieldRedirects();
        String v2 = "net/fabricmc/fabric/api/tag/convention/v2/ConventionalItemTags";
        var shears = fr.get(new RetromodTransformer.FieldKey(v2, "SHEARS"));
        assertNotNull(shears, "SHEARS must be field-redirected");
        assertEquals("SHEAR_TOOLS", shears.name());
        var rawOres = fr.get(new RetromodTransformer.FieldKey(v2, "RAW_ORES"));
        assertNotNull(rawOres);
        assertEquals("RAW_MATERIALS", rawOres.name());
    }
}
