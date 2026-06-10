/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.gui;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for GUI/Screen class renames and Container/Menu API changes.
 *
 * Covers three major refactoring waves:
 * 1. The Flattening (1.13): GuiScreen → Screen, GuiButton → Button, etc.
 *    All client GUI classes moved from net.minecraft.client.gui to
 *    net.minecraft.client.gui.screens and net.minecraft.client.gui.components.
 * 2. Container → Menu rename (1.17): All inventory container classes moved
 *    from net.minecraft.inventory to net.minecraft.world.inventory and
 *    were renamed from Container* to *Menu.
 * 3. Fabric ScreenHandler method renames (1.19.3): transferSlot → quickMoveStack.
 *
 * Also provides an embedded ScaledResolutionShim for the ScaledResolution class
 * that was removed in 1.14 (replaced by Window).
 */
public class GuiPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft GUI/Screen API Changes";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // GUI screen classes (pre-Flattening names)
            "net/minecraft/client/gui/GuiScreen",
            "net/minecraft/client/gui/GuiButton",
            "net/minecraft/client/gui/GuiTextField",
            "net/minecraft/client/gui/GuiLabel",
            "net/minecraft/client/gui/GuiSlot",
            "net/minecraft/client/gui/ScaledResolution",
            "net/minecraft/client/gui/GuiIngame",
            "net/minecraft/client/gui/FontRenderer",
            "net/minecraft/client/gui/GuiMainMenu",
            "net/minecraft/client/gui/GuiGameOver",
            "net/minecraft/client/gui/GuiOptions",
            "net/minecraft/client/gui/GuiChat",
            "net/minecraft/client/gui/GuiNewChat",
            "net/minecraft/client/gui/GuiDisconnected",
            "net/minecraft/client/gui/GuiMultiplayer",

            // Inventory GUI screens
            "net/minecraft/client/gui/inventory/GuiContainer",
            "net/minecraft/client/gui/inventory/GuiChest",
            "net/minecraft/client/gui/inventory/GuiCrafting",
            "net/minecraft/client/gui/inventory/GuiFurnace",

            // Renderer (removed)
            "net/minecraft/client/renderer/InventoryEffectRenderer",

            // Container classes (pre-1.17 names)
            "net/minecraft/inventory/Container",
            "net/minecraft/inventory/ContainerChest",
            "net/minecraft/inventory/ContainerWorkbench",
            "net/minecraft/inventory/ContainerFurnace",
            "net/minecraft/inventory/ContainerRepair",
            "net/minecraft/inventory/ContainerEnchantment",
            "net/minecraft/inventory/ContainerBrewingStand",
            "net/minecraft/inventory/ContainerPlayer"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "com.retromod.polyfill.minecraft.gui.embedded.ScaledResolutionShim"
        };
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // Register embedded shim classes so they get injected into transformed mod JARs
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }

        // =====================================================================
        // GUI Screen class redirects (The Flattening, 1.13+)
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiScreen",
            "net/minecraft/client/gui/screens/Screen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiButton",
            "net/minecraft/client/gui/components/Button");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiTextField",
            "net/minecraft/client/gui/components/EditBox");
        // GuiLabel was removed with no direct equivalent — redirect to Button as closest match
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiLabel",
            "net/minecraft/client/gui/components/Button");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiSlot",
            "net/minecraft/client/gui/components/ObjectSelectionList");

        // ScaledResolution removed in 1.14 — redirect to embedded shim
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/ScaledResolution",
            "com/retromod/polyfill/minecraft/gui/embedded/ScaledResolutionShim");

        // HUD and font
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiIngame",
            "net/minecraft/client/gui/Gui");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/FontRenderer",
            "net/minecraft/client/gui/Font");

        // NO redirect for "net/minecraft/client/gui/Gui" — the name is ERA-AMBIGUOUS.
        // In 1.12 MCP it was the static drawing helper (drawTexturedModalRect, …),
        // but in Mojang official mappings (1.14.4+) the SAME FQN is the HUD class
        // (the GuiIngame successor that mods mixin into constantly). The old
        // Gui→GuiComponent redirect chained with GuiComponent→GuiGraphicsExtractor
        // and hijacked every modern HUD reference: AppleSkin's InGameHudMixin had
        // its @Mixin target rewritten class_329→Gui→…→GuiGraphicsExtractor while
        // its @Inject selector still said Gui → InvalidInjectionException, HUD dead
        // (caught in the snapshot.3 in-game pass). A 1.12 drawing-helper reference
        // now resolves to the modern Gui class instead — semantically wrong for
        // pre-1.13 mods, but those are below Retromod's supported floor, and the
        // modern meaning must win.

        // InventoryEffectRenderer was removed — redirect to AbstractContainerScreen
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/InventoryEffectRenderer",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen");

        // Specific screen classes
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiMainMenu",
            "net/minecraft/client/gui/screens/TitleScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiGameOver",
            "net/minecraft/client/gui/screens/DeathScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiOptions",
            "net/minecraft/client/gui/screens/OptionsScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiChat",
            "net/minecraft/client/gui/screens/ChatScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiNewChat",
            "net/minecraft/client/gui/components/ChatComponent");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiDisconnected",
            "net/minecraft/client/gui/screens/DisconnectedScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiMultiplayer",
            "net/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen");

        // =====================================================================
        // Inventory GUI screen redirects
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiContainer",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiChest",
            "net/minecraft/client/gui/screens/inventory/ContainerScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiCrafting",
            "net/minecraft/client/gui/screens/inventory/CraftingScreen");
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiFurnace",
            "net/minecraft/client/gui/screens/inventory/FurnaceScreen");

        // =====================================================================
        // Container → Menu class redirects (1.17+)
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraft/inventory/Container",
            "net/minecraft/world/inventory/AbstractContainerMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerChest",
            "net/minecraft/world/inventory/ChestMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerWorkbench",
            "net/minecraft/world/inventory/CraftingMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerFurnace",
            "net/minecraft/world/inventory/FurnaceMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerRepair",
            "net/minecraft/world/inventory/AnvilMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerEnchantment",
            "net/minecraft/world/inventory/EnchantmentMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerBrewingStand",
            "net/minecraft/world/inventory/BrewingStandMenu");
        transformer.registerClassRedirect(
            "net/minecraft/inventory/ContainerPlayer",
            "net/minecraft/world/inventory/InventoryMenu");

        // =====================================================================
        // Fabric ScreenHandler method rename (1.19.3)
        // =====================================================================

        // transferSlot(PlayerEntity, int) → quickMoveStack(Player, int)
        transformer.registerMethodRedirect(
            "net/minecraft/world/inventory/AbstractContainerMenu",
            "transferSlot",
            "(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            "net/minecraft/world/inventory/AbstractContainerMenu",
            "quickMoveStack",
            "(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"
        );
    }
}
