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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural checks for {@link FabricEntityModelLayerShim} (the 18-mod
 * {@code EntityModelLayerRegistry} provider bridge). Verifies the synthetic holder +
 * provider SAM bytecode and the redirects; the reflective wrap needs a 26.1 launch.
 */
class FabricEntityModelLayerShimTest {

    private static final String PROVIDER = "com/retromod/generated/legacymodellayer/TexturedModelDataProvider";

    @BeforeAll
    static void pinHostTo26_1() {
        // The shim self-gates to 26.1+ hosts (pre-26.1, EntityModelLayerRegistry is
        // still alive) — pin the detected host so registerRedirects runs in tests.
        RetromodVersion.TARGET_MC_VERSION = "26.1";
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    @Test
    @DisplayName("holder keeps registerModelLayer(ModelLayerLocation, provider) static, no <clinit>")
    void holderShape() {
        ClassNode cn = read(FabricEntityModelLayerShim.generateHolder());
        assertEquals(0, cn.access & Opcodes.ACC_INTERFACE);
        MethodNode reg = cn.methods.stream().filter(m -> m.name.equals("registerModelLayer"))
                .findFirst().orElse(null);
        assertNotNull(reg, "holder must keep registerModelLayer");
        assertTrue((reg.access & Opcodes.ACC_STATIC) != 0);
        assertEquals("(Lnet/minecraft/client/model/geom/ModelLayerLocation;L" + PROVIDER + ";)V", reg.desc);
        assertFalse(cn.methods.stream().anyMatch(m -> m.name.equals("<clinit>")),
                "no static fields → no static initializer");
    }

    @Test
    @DisplayName("provider SAM keeps createModelData with the (unchanged) LayerDefinition return")
    void providerSam() {
        ClassNode cn = read(FabricEntityModelLayerShim.generateProvider());
        assertTrue((cn.access & Opcodes.ACC_INTERFACE) != 0);
        MethodNode sam = cn.methods.stream()
                .filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0).findFirst().orElse(null);
        assertNotNull(sam);
        assertEquals("createModelData", sam.name, "SAM name must stay createModelData so the lambda links");
        assertEquals("()Lnet/minecraft/client/model/geom/builders/LayerDefinition;", sam.desc,
                "return is LayerDefinition (Yarn TexturedModelData = Mojang LayerDefinition, same class)");
    }

    @Test
    @DisplayName("shim redirects EntityModelLayerRegistry + provider onto the synthetics")
    void redirects() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        new FabricEntityModelLayerShim().registerRedirects(t);
        var cr = t.getClassRedirects();
        assertEquals("com/retromod/generated/legacymodellayer/EntityModelLayerRegistry",
                cr.get("net/fabricmc/fabric/api/client/rendering/v1/EntityModelLayerRegistry"));
        assertEquals(PROVIDER,
                cr.get("net/fabricmc/fabric/api/client/rendering/v1/EntityModelLayerRegistry$TexturedModelDataProvider"));
    }
}
