/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.thirdparty;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Bridge polyfill for Trinkets 2.x -> 3.x API changes (MIT licensed).
 *
 * Trinkets is the most popular accessory/equipment slot mod for Fabric.
 * Between Trinkets 2.x (MC 1.16-1.17) and Trinkets 3.x (MC 1.18+),
 * several classes were renamed or reorganized:
 *
 * <ul>
 *   <li>{@code TrinketSlot} -> {@code SlotType} (slot definition class)</li>
 *   <li>{@code TrinketComponent} interface method signatures changed</li>
 *   <li>{@code TrinketSlots} -> {@code SlotGroup} (slot grouping)</li>
 *   <li>{@code AbstractTrinket} -> {@code TrinketItem} (base trinket item)</li>
 * </ul>
 *
 * This bridge does NOT bundle Trinkets. The user installs Trinkets 3.x normally;
 * Retromod only redirects old 2.x class references to their 3.x equivalents.
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
            // Classes renamed/removed in Trinkets 3.x
            "dev/emi/trinkets/api/TrinketSlot",
            "dev/emi/trinkets/api/TrinketSlots",
            "dev/emi/trinkets/api/AbstractTrinket",
            "dev/emi/trinkets/api/TrinketSlotCenter"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed; pure class redirects to Trinkets 3.x
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // Trinkets 2.x -> 3.x class renames.
        // The package stays dev.emi.trinkets.api but class names changed.
        // Only REMOVED/RENAMED classes are redirected here.

        // TrinketSlot -> SlotType (slot definition, defines what can go in a slot)
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/TrinketSlot",
            "dev/emi/trinkets/api/SlotType");

        // TrinketSlots -> SlotGroup (grouping of slots, e.g. "head", "chest")
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/TrinketSlots",
            "dev/emi/trinkets/api/SlotGroup");

        // AbstractTrinket -> TrinketItem (base class for trinket items)
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/AbstractTrinket",
            "dev/emi/trinkets/api/TrinketItem");

        // TrinketSlotCenter removed, closest equivalent is SlotGroup
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/TrinketSlotCenter",
            "dev/emi/trinkets/api/SlotGroup");
    }
}
