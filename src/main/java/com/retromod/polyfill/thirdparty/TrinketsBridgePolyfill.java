/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.thirdparty;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Bridges Trinkets 2.x (MC 1.16-1.17) class names to their Trinkets 3.x (MC 1.18+) equivalents.
 *
 * Does not bundle Trinkets: the user installs Trinkets 3.x, and Retromod redirects the old
 * 2.x class references to the renamed 3.x classes.
 */
public class TrinketsBridgePolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Trinkets 2.x -> 3.x Bridge";
    }

    @Override
    public String getCategory() {
        return "thirdparty";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "dev/emi/trinkets/api/TrinketSlot",
            "dev/emi/trinkets/api/TrinketSlots",
            "dev/emi/trinkets/api/AbstractTrinket",
            "dev/emi/trinkets/api/TrinketSlotCenter"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // no embedded stubs; class redirects only
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // package stays dev.emi.trinkets.api; only the renamed classes are redirected
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/TrinketSlot",
            "dev/emi/trinkets/api/SlotType");

        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/TrinketSlots",
            "dev/emi/trinkets/api/SlotGroup");

        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/AbstractTrinket",
            "dev/emi/trinkets/api/TrinketItem");

        // TrinketSlotCenter removed, closest equivalent is SlotGroup
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/TrinketSlotCenter",
            "dev/emi/trinkets/api/SlotGroup");
    }
}
