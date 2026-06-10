/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Runtime half of the removed Fabric <b>item-group events v1</b> bridge
 * ({@code net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents}). Top compat-audit
 * gap: ~83 mods sole-blocked on {@code ItemGroupEvents$ModifyEntries}.
 *
 * <p>v1 fired {@code ModifyEntries.modifyEntries(FabricItemGroupEntries)} (and the
 * 2-arg {@code ModifyEntriesAll}); the surviving 26.1 API is
 * {@code CreativeModeTabEvents} with {@code ModifyOutput.modifyOutput(
 * FabricCreativeModeTabOutput)}. The method name on the SAM changed
 * ({@code modifyEntries} → {@code modifyOutput}), which makes it a lambda trap —
 * a class redirect would crash {@code LambdaMetafactory}. The parameter object also
 * renamed ({@code FabricItemGroupEntries} → {@code FabricCreativeModeTabOutput}),
 * but those carry the <i>same</i> instance; the only divergence in the lambda body
 * is {@code addAfter}/{@code addBefore} → {@code insertAfter}/{@code insertBefore},
 * handled by method redirects in {@link com.retromod.shim.api.fabric.FabricItemGroupEventsShim}.</p>
 *
 * <p>So the SAM bodies need no adaptation — the forwarder just replays the v2 SAM's
 * arguments onto the v1 invoker (same {@code CreativeModeTab}/output objects, only
 * the method name differs). One generic {@link #wire} serves both the per-key event
 * and the all-tabs event.</p>
 *
 * <p>All reflection + {@link Proxy}, no compile-time Fabric/MC dependency; embedded
 * raw into each transformed mod jar. Fails soft: a reflective miss leaves the event
 * registrable but inert (logged) rather than crashing the mod.</p>
 *
 * <p><b>STATUS — authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.145.4+26.1.2}. A 26.1 launch that adds an item to a creative
 * tab through a v1 mod is still required.</p>
 */
public final class ItemGroupEventsBridge {

    private ItemGroupEventsBridge() {}

    private static final String TAG = "[Retromod] ItemGroupEventsBridge: ";

    private static final String CREATIVE_TAB_EVENTS = "net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents";
    private static final String EVENT_FACTORY       = "net.fabricmc.fabric.api.event.EventFactory";
    private static final String RESOURCE_KEY        = "net.minecraft.resources.ResourceKey";

    // Synthetic v1 SAM interfaces (injected by the shim) + their v2 counterparts.
    private static final String SYNTH_MODIFY_ENTRIES     = "com.retromod.generated.legacyitemgroup.ModifyEntries";
    private static final String SYNTH_MODIFY_ENTRIES_ALL = "com.retromod.generated.legacyitemgroup.ModifyEntriesAll";
    private static final String V2_MODIFY_OUTPUT     = CREATIVE_TAB_EVENTS + "$ModifyOutput";
    private static final String V2_MODIFY_OUTPUT_ALL = CREATIVE_TAB_EVENTS + "$ModifyOutputAll";

    /** Per-{@code ResourceKey} v1 events, so repeated {@code modifyEntriesEvent(key)} calls share one. */
    private static final ConcurrentHashMap<Object, Object> PER_KEY = new ConcurrentHashMap<>();

    private static ClassLoader cl() {
        return ItemGroupEventsBridge.class.getClassLoader();
    }

    /**
     * v1 {@code ItemGroupEvents.modifyEntriesEvent(key)} → a v1 {@code Event<ModifyEntries>}
     * wired to {@code CreativeModeTabEvents.modifyOutputEvent(key)}.
     *
     * <p>{@code computeIfAbsent} (not get-then-putIfAbsent): {@code wire} has a side
     * effect — it registers a forwarder on the live v2 event — so a lost race would
     * leave a duplicate forwarder behind and fire every handler twice per tab build.
     * computeIfAbsent runs the wiring at most once per key.</p>
     */
    public static Object modifyEntriesEvent(Object resourceKey) {
        return PER_KEY.computeIfAbsent(resourceKey, key -> {
            try {
                Class<?> cmte = Class.forName(CREATIVE_TAB_EVENTS, true, cl());
                Class<?> keyType = Class.forName(RESOURCE_KEY, false, cl());
                Object v2Event = cmte.getMethod("modifyOutputEvent", keyType).invoke(null, key);
                return wire(SYNTH_MODIFY_ENTRIES, V2_MODIFY_OUTPUT, v2Event);
            } catch (Throwable t) {
                System.out.println(TAG + "modifyEntriesEvent wiring failed (" + t + "); entries inert for this tab.");
                return createEmpty(SYNTH_MODIFY_ENTRIES);
            }
        });
    }

    /**
     * v1 {@code ItemGroupEvents.MODIFY_ENTRIES_ALL} → a v1 {@code Event<ModifyEntriesAll>}
     * wired to {@code CreativeModeTabEvents.MODIFY_OUTPUT_ALL}. Called once from the
     * synthetic holder's {@code <clinit>}.
     */
    public static Object installModifyAll() {
        try {
            Class<?> cmte = Class.forName(CREATIVE_TAB_EVENTS, true, cl());
            Object v2Event = cmte.getField("MODIFY_OUTPUT_ALL").get(null);
            return wire(SYNTH_MODIFY_ENTRIES_ALL, V2_MODIFY_OUTPUT_ALL, v2Event);
        } catch (Throwable t) {
            System.out.println(TAG + "MODIFY_ENTRIES_ALL wiring failed (" + t + "); all-tabs entries inert.");
            return createEmpty(SYNTH_MODIFY_ENTRIES_ALL);
        }
    }

    /**
     * Build a v1 {@code Event} bound to {@code v1ClassName} and register a forwarder
     * on {@code v2Event} (of SAM type {@code v2SamClassName}) that replays each call
     * onto the v1 invoker's SAM with the same arguments.
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
        final Method v1Sam = sam(v1Type); // resolved once — fires on every tab build

        Object forwarder = Proxy.newProxyInstance(cl, new Class<?>[]{v2SamType}, (proxy, method, args) -> {
            if (isSam(method)) {
                Object v1Invoker = invokerM.invoke(v1Event);   // an instance of v1Type
                invokeUnwrapped(v1Sam, v1Invoker, args);        // same args: ModifyOutput→ModifyEntries 1:1
                return null;
            }
            return objectMethod(proxy, method, args);
        });

        eventIface.getMethod("register", Object.class).invoke(v2Event, forwarder);
        return v1Event;
    }

    /** Create an array-backed v1 {@code Event} whose invoker fans out to each registered listener. */
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

    /** The single abstract method declared on {@code declared} (the functional interface). */
    private static Method sam(Class<?> declared) {
        for (Method m : declared.getMethods()) {
            if ((m.getModifiers() & java.lang.reflect.Modifier.ABSTRACT) != 0 && isSam(m)) return m;
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
            case "toString": return "RetromodItemGroupV1Proxy@" + Integer.toHexString(System.identityHashCode(proxy));
            default:         return null;
        }
    }
}
