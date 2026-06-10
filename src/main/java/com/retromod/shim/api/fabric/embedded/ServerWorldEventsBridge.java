/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/**
 * Runtime half of the removed Fabric <b>{@code ServerWorldEvents}</b> bridge
 * (server-level load/unload). Audit gap: ~21 mods sole-blocked on
 * {@code ServerWorldEvents$Load}.
 *
 * <p>26.1 renamed the holder ({@code ServerWorldEvents} → {@code ServerLevelEvents})
 * <i>and</i> the SAM methods ({@code onWorldLoad}/{@code onWorldUnload} →
 * {@code onLevelLoad}/{@code onLevelUnload}). The outer rename alone is a lambda trap:
 * a mod's {@code (server, world) -> …} handler hard-codes {@code onWorldLoad}, which a
 * class redirect onto {@code ServerLevelEvents$Load} can't satisfy. The parameter
 * types only went {@code ServerWorld → ServerLevel} (the harvest remaps that in the
 * lambda), so the SAM bodies need no adaptation — the forwarder just replays the v2
 * call onto the v1 invoker 1:1.</p>
 *
 * <p>All reflection + {@link Proxy}, embedded raw into each mod jar; fails soft
 * (logged, inert) on a reflective miss. Self-contained on purpose — the generic
 * event-wiring here mirrors {@link ItemGroupEventsBridge} but is duplicated so each
 * bridge embeds independently (no cross-class embed dependency) and a fault stays
 * isolated to one bridge until the in-game pass verifies them.</p>
 *
 * <p><b>STATUS — authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.141.1} (old) and {@code 0.145.4+26.1.2} (new). A 26.1 launch
 * that loads/unloads a level with such a mod is still required.</p>
 */
public final class ServerWorldEventsBridge {

    private ServerWorldEventsBridge() {}

    private static final String TAG = "[Retromod] ServerWorldEventsBridge: ";

    private static final String SERVER_LEVEL_EVENTS = "net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents";
    private static final String EVENT_FACTORY       = "net.fabricmc.fabric.api.event.EventFactory";

    private static final String SYNTH_LOAD   = "com.retromod.generated.legacylifecycle.ServerWorldLoad";
    private static final String SYNTH_UNLOAD = "com.retromod.generated.legacylifecycle.ServerWorldUnload";
    private static final String V2_LOAD   = SERVER_LEVEL_EVENTS + "$Load";
    private static final String V2_UNLOAD = SERVER_LEVEL_EVENTS + "$Unload";

    private static ClassLoader cl() {
        return ServerWorldEventsBridge.class.getClassLoader();
    }

    /** v1 {@code ServerWorldEvents.LOAD} → a {@code Event<Load>} wired to {@code ServerLevelEvents.LOAD}. */
    public static Object installLoad() {
        return install("LOAD", SYNTH_LOAD, V2_LOAD);
    }

    /** v1 {@code ServerWorldEvents.UNLOAD} → a {@code Event<Unload>} wired to {@code ServerLevelEvents.UNLOAD}. */
    public static Object installUnload() {
        return install("UNLOAD", SYNTH_UNLOAD, V2_UNLOAD);
    }

    private static Object install(String v2Field, String v1ClassName, String v2SamClassName) {
        try {
            Class<?> sle = Class.forName(SERVER_LEVEL_EVENTS, true, cl());
            Object v2Event = sle.getField(v2Field).get(null);
            return wire(v1ClassName, v2SamClassName, v2Event);
        } catch (Throwable t) {
            System.out.println(TAG + v2Field + " wiring failed (" + t + "); that lifecycle event is inert.");
            return createEmpty(v1ClassName);
        }
    }

    /**
     * Build a v1 {@code Event} bound to {@code v1ClassName} and register a forwarder on
     * {@code v2Event} (SAM type {@code v2SamClassName}) that replays each call onto the
     * v1 invoker's SAM with the same arguments (1:1 — only the method name differs).
     */
    private static Object wire(String v1ClassName, String v2SamClassName, Object v2Event) throws Exception {
        ClassLoader cl = cl();
        Class<?> v1Type = Class.forName(v1ClassName, false, cl);
        Class<?> v2SamType = Class.forName(v2SamClassName, false, cl);

        Object v1Event = createArrayBacked(v1Type);
        // Resolve Event methods on the PUBLIC interface — the runtime class
        // (fabric.impl ArrayBackedEvent) is not public and throws
        // IllegalAccessException for lookups against it.
        Class<?> eventIface = Class.forName("net.fabricmc.fabric.api.event.Event", false, cl);
        final Method invokerM = eventIface.getMethod("invoker");
        final Method v1Sam = sam(v1Type);

        Object forwarder = Proxy.newProxyInstance(cl, new Class<?>[]{v2SamType}, (proxy, method, args) -> {
            if (isSam(method)) {
                Object v1Invoker = invokerM.invoke(v1Event);
                invokeUnwrapped(v1Sam, v1Invoker, args);
                return null;
            }
            return objectMethod(proxy, method, args);
        });

        eventIface.getMethod("register", Object.class).invoke(v2Event, forwarder);
        return v1Event;
    }

    private static Object createEmpty(String v1ClassName) {
        try {
            return createArrayBacked(Class.forName(v1ClassName, false, cl()));
        } catch (Throwable t) {
            System.out.println(TAG + "could not create fallback event for " + v1ClassName + " (" + t + ").");
            return null;
        }
    }

    private static Object createArrayBacked(Class<?> v1Type) throws Exception {
        ClassLoader cl = cl();
        Class<?> eventFactory = Class.forName(EVENT_FACTORY, true, cl);
        Method createArrayBacked = eventFactory.getMethod("createArrayBacked", Class.class, Function.class);
        Function<Object, Object> invokerFactory = (listenersObj) -> {
            final Object[] listeners = (Object[]) listenersObj;
            return Proxy.newProxyInstance(cl, new Class<?>[]{v1Type}, (proxy, method, args) -> {
                if (isSam(method)) {
                    for (Object listener : listeners) {
                        invokeUnwrapped(method, listener, args);
                    }
                    return null;
                }
                return objectMethod(proxy, method, args);
            });
        };
        return createArrayBacked.invoke(null, v1Type, invokerFactory);
    }

    private static Method sam(Class<?> declared) {
        for (Method m : declared.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && isSam(m)) return m;
        }
        for (Method m : declared.getMethods()) {
            if (isSam(m)) return m;
        }
        throw new IllegalStateException("no SAM on " + declared.getName());
    }

    private static boolean isSam(Method m) {
        String n = m.getName();
        return m.getDeclaringClass().isInterface()
                && !("equals".equals(n) && m.getParameterCount() == 1)
                && !"hashCode".equals(n)
                && !"toString".equals(n);
    }

    private static void invokeUnwrapped(Method m, Object target, Object[] args) throws Throwable {
        try {
            m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":   return proxy == (args == null ? null : args[0]);
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "RetromodServerWorldEventsProxy@" + Integer.toHexString(System.identityHashCode(proxy));
            default:         return null;
        }
    }
}
