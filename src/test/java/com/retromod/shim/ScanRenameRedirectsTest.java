/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.3.0 corpus-scan renames: same-descriptor method renames surfaced by scanning 19 real mod jars'
 * mixin selectors against the 26.2 jar, each verified by hand against both the 26.1-snapshot-10 and
 * 26.2 jars to place them in the correct shim epoch. These are exactly the "Scanned 0 target(s)" /
 * NoSuchMethodError class of breakage.
 */
class ScanRenameRedirectsTest {

    @AfterEach
    void reset() {
        RetromodTransformer.getInstance().clearRedirectsForTesting();
    }

    private static boolean hasRedirect(RetromodTransformer t, String owner, String name, String desc,
                                       String newName) {
        return t.getMethodRedirects().entrySet().stream().anyMatch(e ->
                e.getKey().owner().equals(owner) && e.getKey().name().equals(name)
                        && e.getKey().desc().equals(desc) && e.getValue().name().equals(newName));
    }

    @Test
    @DisplayName("26.1 epoch: GameNarrator.sayNow -> saySystemNow and FriendlyByteBuf.readJsonWithCodec -> readLenientJsonWithCodec")
    void renames261() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        new com.retromod.shim.fabric.Fabric_1_21_11_to_26_1().registerRedirects(t);
        assertTrue(hasRedirect(t, "net/minecraft/client/GameNarrator", "sayNow",
                        "(Lnet/minecraft/network/chat/Component;)V", "saySystemNow"),
                "sayNow was renamed at 26.1 (verified absent in 26.1-snapshot-10)");
        assertTrue(hasRedirect(t, "net/minecraft/network/FriendlyByteBuf", "readJsonWithCodec",
                        "(Lcom/mojang/serialization/Codec;)Ljava/lang/Object;", "readLenientJsonWithCodec"),
                "readJsonWithCodec was renamed at 26.1");
    }

    @Test
    @DisplayName("26.2 epoch: Minecraft.setScreen -> setScreenAndShow (setScreen still exists on 26.1)")
    void renames262() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        com.retromod.shim.common.Mc26_1To26_2CoreMoves.register(t);
        assertTrue(hasRedirect(t, "net/minecraft/client/Minecraft", "setScreen",
                        "(Lnet/minecraft/client/gui/screens/Screen;)V", "setScreenAndShow"),
                "setScreen was removed at 26.2, so the rename lives in the 26.1->26.2 shim");
        assertTrue(hasRedirect(t, "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen",
                        "renderLabels", "(Lnet/minecraft/client/gui/GuiGraphics;II)V", "extractLabels"),
                "renderLabels was renamed to extractLabels at 26.2 (#137 Apollo's AnvilScreenMixin)");
    }

    @Test
    @DisplayName("#50: FlyingMob.getDefaultDimensions re-owners to Mob (Fabric intermediary + NeoForge Mojang)")
    void flyingMobOwnerAlias() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        new com.retromod.shim.fabric.Fabric_1_21_11_to_26_1().registerRedirects(t);
        assertTrue(t.getMethodRedirects().entrySet().stream().anyMatch(e ->
                        e.getKey().owner().equals("net/minecraft/class_1307")
                                && e.getKey().name().equals("method_55694")
                                && e.getValue().owner().equals("net/minecraft/world/entity/Mob")
                                && e.getValue().name().equals("getDefaultDimensions")),
                "Fabric shim re-owners the deleted FlyingMob(class_1307).getDefaultDimensions to Mojang Mob");

        t.clearRedirectsForTesting();
        new com.retromod.shim.neoforge.NeoForge_1_21_11_to_26_1().registerRedirects(t);
        assertTrue(t.getMethodRedirects().entrySet().stream().anyMatch(e ->
                        e.getKey().owner().equals("net/minecraft/world/entity/FlyingMob")
                                && e.getKey().name().equals("getDefaultDimensions")
                                && e.getValue().owner().equals("net/minecraft/world/entity/Mob")),
                "NeoForge shim re-owners FlyingMob.getDefaultDimensions to Mob");
    }

    @Test
    @DisplayName("26.1 class moves: Painting/PaintingVariant/Husk sub-packages + MobSpawnType->EntitySpawnReason")
    void classMoves261() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        com.retromod.shim.common.Common_1_21_11_to_26_1_ClassMoves.register(t);
        var cr = t.getClassRedirects();
        assertEquals("net/minecraft/world/entity/decoration/painting/Painting",
                cr.get("net/minecraft/world/entity/decoration/Painting"), "Painting sub-package move");
        assertEquals("net/minecraft/world/entity/decoration/painting/PaintingVariant",
                cr.get("net/minecraft/world/entity/decoration/PaintingVariant"), "PaintingVariant sub-package move");
        assertEquals("net/minecraft/world/entity/monster/zombie/Husk",
                cr.get("net/minecraft/world/entity/monster/Husk"), "Husk sub-package move");
        assertEquals("net/minecraft/world/entity/EntitySpawnReason",
                cr.get("net/minecraft/world/entity/MobSpawnType"), "MobSpawnType renamed to EntitySpawnReason");
    }
}
