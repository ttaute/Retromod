/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/**
 * Runtime half of the removed Fabric command v1 bridge
 * ({@code net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback}).
 *
 * <p>v1's callback used a 2-arg SAM {@code register(CommandDispatcher, boolean dedicated)};
 * the surviving v2 callback uses a 3-arg SAM. A class redirect can't fix it: the mod's handler
 * is a lambda whose {@code invokedynamic} hard-codes the v1 SAM, so it must keep linking against
 * an interface declaring that 2-arg method (see {@link com.retromod.shim.api.fabric.FabricCommandV1Shim}).
 *
 * <p>The synthetic v1 interface's {@code <clinit>} calls {@link #installEvent(Class)} once to fill
 * its {@code EVENT} field: builds a Fabric {@code Event} bound to the v1 interface and registers one
 * forwarder on the v2 {@code EVENT} that drives every v1 handler when commands are built.
 *
 * <p>All reflection + {@link Proxy}, so no compile-time dependency on Fabric API, brigadier, or
 * Minecraft; embedded raw into each transformed mod jar and resolves types from the mod's own
 * classloader. A reflective miss logs and leaves command callbacks inert. Checked against
 * {@code fabric-api-0.145.4+26.1.2}.
 */
public final class CommandRegistrationV1Bridge {

    private CommandRegistrationV1Bridge() {}

    private static final String TAG = "[Retromod] CommandRegistrationV1Bridge: ";

    private static final String EVENT_FACTORY = "net.fabricmc.fabric.api.event.EventFactory";
    private static final String V2_CALLBACK   = "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback";

    /**
     * Build the v1 {@code Event} and wire it to the v2 callback. Called once from the synthetic v1
     * interface's static initializer. Returns the {@code Event} bound to {@code v1Type}, or {@code null}
     * if the event machinery couldn't be reached.
     */
    public static Object installEvent(Class<?> v1Type) {
        try {
            ClassLoader cl = v1Type.getClassLoader();

            Class<?> eventFactory = Class.forName(EVENT_FACTORY, true, cl);
            Method createArrayBacked = eventFactory.getMethod(
                    "createArrayBacked", Class.class, Function.class);

            // T[] listeners -> one T that fans out to each
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

            Object v1Event = createArrayBacked.invoke(null, v1Type, invokerFactory);

            try {
                wireV2Forwarder(cl, v1Type, v1Event);
            } catch (Throwable t) {
                System.out.println(TAG + "could not wire the v2 forwarder (" + t + "); "
                        + "v1 command callbacks will register but won't fire.");
            }
            return v1Event;
        } catch (Throwable t) {
            System.out.println(TAG + "could not create the v1 event (" + t + "); "
                    + "CommandRegistrationCallback.EVENT will be null.");
            return null;
        }
    }

    /** v2 callback that replays onto the v1 event's invoker, dropping the 3-arg v2 SAM to the 2-arg v1 SAM. */
    private static void wireV2Forwarder(ClassLoader cl, Class<?> v1Type, Object v1Event) throws Exception {
        Class<?> v2Type = Class.forName(V2_CALLBACK, true, cl);
        Object v2Event = v2Type.getField("EVENT").get(null);

        Method v1Register = findByNameArity(v1Type, "register", 2);
        // Resolve against the public Event interface; the impl (ArrayBackedEvent) isn't public,
        // so lookups on it throw IllegalAccessException even though the members are public.
        Class<?> eventIface = Class.forName("net.fabricmc.fabric.api.event.Event", false, cl);
        Method invokerM = eventIface.getMethod("invoker");

        Object v2Forwarder = Proxy.newProxyInstance(cl, new Class<?>[]{v2Type}, (proxy, method, args) -> {
            if (isSam(method)) {
                // v2 SAM: register(dispatcher, buildContext, commandSelection)
                Object dispatcher = args[0];
                boolean dedicated = isDedicated(args[args.length - 1]);
                Object v1Invoker = invokerM.invoke(v1Event);
                invokeUnwrapped(v1Register, v1Invoker, new Object[]{dispatcher, dedicated});
                return null;
            }
            return objectMethod(proxy, method, args);
        });

        eventIface.getMethod("register", Object.class).invoke(v2Event, v2Forwarder);
    }

    // Keyed on Enum.name() rather than the package-private includeDedicated field
    // (module-locked under net.minecraft).
    private static boolean isDedicated(Object selection) {
        return selection instanceof Enum<?> e && "DEDICATED".equals(e.name());
    }

    /** A proxied interface's single abstract method: anything that isn't an Object method. */
    private static boolean isSam(Method m) {
        String n = m.getName();
        return m.getDeclaringClass().isInterface()
                && !("equals".equals(n) && m.getParameterCount() == 1)
                && !"hashCode".equals(n)
                && !"toString".equals(n);
    }

    private static Method findByNameArity(Class<?> type, String name, int arity) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        throw new IllegalStateException("no " + name + "/" + arity + " on " + type.getName());
    }

    /** Invoke, unwrapping {@link InvocationTargetException} so the mod's own error surfaces. */
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
            case "toString": return "RetromodCommandV1Proxy@" + Integer.toHexString(System.identityHashCode(proxy));
            default:         return null;
        }
    }
}
