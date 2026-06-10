/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Runtime half of the removed Fabric <b>{@code HudRenderCallback}</b> bridge.
 * Audit gap: ~6 mods sole-blocked on it.
 *
 * <p>26.1 replaced the single {@code HudRenderCallback.EVENT} with the
 * {@code hud/HudElementRegistry} (named {@code HudElement}s in a layered list).
 * The synthetic {@code HudRenderCallback} interface (injected by
 * {@link com.retromod.shim.api.fabric.FabricHudRenderCallbackShim}) <b>extends</b>
 * the new {@code HudElement} and bridges its SAM with a default method
 * ({@code extractRenderState} → {@code onHudRender}) — so every registered v1
 * listener, and the event's combined invoker, <i>is</i> a {@code HudElement}.
 *
 * <p>That makes this bridge tiny: build a real array-backed {@code Event} for the
 * mod's {@code EVENT.register(...)} calls, then hand the event's invoker to
 * {@code HudElementRegistry.addLast(...)} once. The registry calls
 * {@code extractRenderState}, the default method forwards to {@code onHudRender},
 * and every v1 listener fires — no per-frame reflection.
 *
 * <p>All reflection happens once at install; fails soft (logged, HUD overlays
 * inert) if the registry can't be reached.</p>
 *
 * <p><b>STATUS — authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.145.4+26.1.2} ({@code HudElementRegistry.addLast(Identifier,
 * HudElement)}) and {@code minecraft-26.1.2} ({@code Identifier.fromNamespaceAndPath}).</p>
 */
public final class HudRenderCallbackBridge {

    private HudRenderCallbackBridge() {}

    private static final String TAG = "[Retromod] HudRenderCallbackBridge: ";

    private static final String EVENT_FACTORY = "net.fabricmc.fabric.api.event.EventFactory";
    private static final String REGISTRY      = "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry";
    private static final String HUD_ELEMENT   = "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement";
    private static final String IDENTIFIER    = "net.minecraft.resources.Identifier";

    /**
     * Build the v1 {@code Event} and register its combined invoker as a single
     * {@code HudElement} layer. Called once from the synthetic's {@code <clinit>}.
     */
    public static Object installEvent(Class<?> v1Type) {
        try {
            ClassLoader cl = v1Type.getClassLoader();

            Class<?> eventFactory = Class.forName(EVENT_FACTORY, true, cl);
            Method createArrayBacked = eventFactory.getMethod("createArrayBacked", Class.class, Function.class);

            final Method sam = sam(v1Type); // onHudRender — resolved once
            Function<Object, Object> invokerFactory = (listenersObj) -> {
                final Object[] listeners = (Object[]) listenersObj;
                return java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{v1Type}, (proxy, method, args) -> {
                    // The registry calls extractRenderState; the synthetic's default
                    // forwards it to onHudRender, which lands here for the proxy —
                    // fan out to every registered v1 listener.
                    if (method.getDeclaringClass().isInterface()
                            && !"equals".equals(method.getName())
                            && !"hashCode".equals(method.getName())
                            && !"toString".equals(method.getName())) {
                        for (Object l : listeners) {
                            try {
                                sam.invoke(l, args);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                throw e.getCause() != null ? e.getCause() : e;
                            }
                        }
                        return null;
                    }
                    switch (method.getName()) {
                        case "equals":   return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        default:         return "RetromodHudRenderProxy";
                    }
                });
            };
            Object event = createArrayBacked.invoke(null, v1Type, invokerFactory);

            try {
                // HudElementRegistry.addLast(Identifier, HudElement) with the combined
                // invoker — it implements the synthetic, which extends HudElement.
                Class<?> registry = Class.forName(REGISTRY, true, cl);
                Class<?> hudElement = Class.forName(HUD_ELEMENT, false, cl);
                Class<?> identifier = Class.forName(IDENTIFIER, false, cl);
                Object id = identifier.getMethod("fromNamespaceAndPath", String.class, String.class)
                        .invoke(null, "retromod", "legacy_hud_render");
                // invoker() via the PUBLIC Event interface — the runtime class
                // (fabric.impl ArrayBackedEvent) is not public.
                Object invoker = Class.forName("net.fabricmc.fabric.api.event.Event", false, cl)
                        .getMethod("invoker").invoke(event);
                registry.getMethod("addLast", identifier, hudElement).invoke(null, id, invoker);
            } catch (Throwable t) {
                System.out.println(TAG + "could not attach to HudElementRegistry (" + t + "); "
                        + "v1 HUD callbacks will register but won't render.");
            }
            return event;
        } catch (Throwable t) {
            System.out.println(TAG + "could not create the v1 event (" + t + "); "
                    + "HudRenderCallback.EVENT will be null.");
            return null;
        }
    }

    private static Method sam(Class<?> declared) {
        for (Method m : declared.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) return m;
        }
        throw new IllegalStateException("no SAM on " + declared.getName());
    }
}
