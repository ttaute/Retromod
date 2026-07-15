/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.polyfill.minecraft;

/**
 * 1.3.0 (top-50/60 corpus audits, 14+ mods): bridge the {@code ClickEvent}/{@code HoverEvent}
 * constructor breaks across the 1.21.5 text-component rework. Pre-1.21.5 a mod wrote
 * {@code new ClickEvent(ClickEvent.Action, String)}; on 26.x {@code ClickEvent} is a sealed
 * INTERFACE whose per-action record subtypes ({@code RunCommand}, {@code OpenUrl}, {@code ChangePage},
 * ...) each take a TYPED value, so the old constructor is gone and a plain class-move/redirect can't
 * express it. A {@code new ClickEvent(action, value)} is instead rewritten to
 * {@code RetroTextEvents.clickEvent(action, value)} (a constructor-to-factory redirect), which
 * dispatches on the action enum to the right subtype - exactly the dispatch the old constructor did
 * internally, so this is correct by construction.
 *
 * <p><b>Zero compile-time Minecraft dependency</b> (Retromod builds without MC, and a per-mod embedded
 * copy - Forge/NeoForge JPMS split-package safety - must load with only what the mod's module sees):
 * every MC type is reached reflectively and results are typed {@code Object}; the constructor-to-factory
 * redirect appends a {@code CHECKCAST} to the original type.
 *
 * <p><b>Fail-safe:</b> an unknown/unmappable action, an unparseable value, or any reflection failure
 * yields {@code null} (the redirect's {@code CHECKCAST null} passes), i.e. that one click/hover goes
 * inert rather than crashing construction - the same soft-fail posture as the rest of Retromod. The
 * common actions a distributed mod actually uses (open-url, run/suggest-command, copy, change-page,
 * open-file) all map exactly.
 */
public final class RetroTextEvents {

    private RetroTextEvents() {}

    private static final String CLICK = "net.minecraft.network.chat.ClickEvent$";
    private static final String HOVER = "net.minecraft.network.chat.HoverEvent$";

    /**
     * Old {@code new ClickEvent(ClickEvent.Action, String)} -> the matching 26.x subtype record.
     * {@code action} is the {@code ClickEvent.Action} enum (reached as {@link Enum}); {@code value}
     * is the legacy single string.
     */
    public static Object clickEvent(Object action, String value) {
        String name = enumName(action);
        if (name == null) return null;
        try {
            switch (name) {
                case "OPEN_URL":          return newInst(CLICK + "OpenUrl", java.net.URI.class, java.net.URI.create(value));
                case "RUN_COMMAND":       return newInst(CLICK + "RunCommand", String.class, value);
                case "SUGGEST_COMMAND":   return newInst(CLICK + "SuggestCommand", String.class, value);
                case "COPY_TO_CLIPBOARD": return newInst(CLICK + "CopyToClipboard", String.class, value);
                case "CHANGE_PAGE":       return newInst(CLICK + "ChangePage", int.class, Integer.parseInt(value.trim()));
                case "OPEN_FILE":         return newInst(CLICK + "OpenFile", java.io.File.class, new java.io.File(value));
                default:                  return null; // CUSTOM / SHOW_DIALOG: not expressible from (action, String)
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Old {@code new HoverEvent(HoverEvent.Action, Object)} -> the matching 26.x subtype. Only
     * {@code SHOW_TEXT} maps from a legacy {@code (action, Object)} pair without the removed
     * serializer plumbing (its value was a {@code Component}); {@code SHOW_ITEM}/{@code SHOW_ENTITY}
     * took now-restructured value objects, so they fall through to the inert {@code null}.
     */
    public static Object hoverEvent(Object action, Object value) {
        String name = enumName(action);
        if (name == null) return null;
        try {
            if ("SHOW_TEXT".equals(name)) {
                return newInst(HOVER + "ShowText", Class.forName("net.minecraft.network.chat.Component"), value);
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String enumName(Object action) {
        try {
            return action instanceof Enum<?> ? ((Enum<?>) action).name() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object newInst(String cls, Class<?> paramType, Object arg) throws Exception {
        Class<?> c = Class.forName(cls);
        java.lang.reflect.Constructor<?> ctor = c.getConstructor(paramType);
        ctor.setAccessible(true);
        return ctor.newInstance(arg);
    }
}
