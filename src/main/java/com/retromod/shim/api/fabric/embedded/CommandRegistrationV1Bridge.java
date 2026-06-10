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
 * Runtime half of the removed Fabric <b>command v1</b> bridge
 * ({@code net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback}).
 *
 * <p>The v1 server-command callback used a 2-arg SAM
 * {@code register(CommandDispatcher dispatcher, boolean dedicated)} and was
 * deleted long before 26.1; the surviving API is the v2 callback with a 3-arg SAM
 * {@code register(CommandDispatcher, CommandBuildContext, Commands$CommandSelection)}.
 * A plain class redirect can't fix this: the mod's handler is a {@code lambda}
 * whose {@code invokedynamic} hard-codes the v1 SAM, so it must keep linking
 * against an interface that still declares that exact 2-arg method
 * (see {@link com.retromod.shim.api.fabric.FabricCommandV1Shim}).</p>
 *
 * <h2>What this class does</h2>
 * The synthetic v1 interface's {@code <clinit>} calls {@link #installEvent(Class)}
 * once, to fill its {@code EVENT} field. We:
 * <ol>
 *   <li>create a real Fabric {@code Event} bound to the synthetic v1 interface
 *       (via {@code EventFactory.createArrayBacked}), so the mod's
 *       {@code EVENT.register(handler)} works natively; and</li>
 *   <li>register one forwarder on the live {@code command/v2} {@code EVENT} that,
 *       whenever the game builds its commands, drives every v1 handler with the
 *       dispatcher and a {@code dedicated} boolean derived from the v2
 *       {@code CommandSelection} ({@code DEDICATED} → {@code true}).</li>
 * </ol>
 *
 * <p>Everything is reflection + {@link Proxy} so this class carries no compile-time
 * dependency on Fabric API, brigadier, or Minecraft — it is embedded raw into each
 * transformed mod jar and resolves the real types from the mod's own classloader at
 * runtime. It fails soft: a reflective miss leaves command callbacks inert (logged)
 * rather than crashing the mod.</p>
 *
 * <p><b>STATUS — authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.145.4+26.1.2} ({@code EventFactory.createArrayBacked(Class,
 * Function)}, {@code Event.invoker()}, {@code Event.register(Object)},
 * {@code command/v2/CommandRegistrationCallback}). A real 26.1 launch that registers
 * a command through a v1 mod is still required.</p>
 */
public final class CommandRegistrationV1Bridge {

    private CommandRegistrationV1Bridge() {}

    private static final String TAG = "[Retromod] CommandRegistrationV1Bridge: ";

    private static final String EVENT_FACTORY = "net.fabricmc.fabric.api.event.EventFactory";
    private static final String V2_CALLBACK   = "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback";

    /**
     * Build the v1 {@code Event} and wire it to the live v2 callback. Called once
     * from the synthetic v1 interface's static initializer.
     *
     * @param v1Type the synthetic v1 {@code CommandRegistrationCallback} interface
     * @return a Fabric {@code Event} bound to {@code v1Type}, or {@code null} if the
     *         core event machinery couldn't be reached (mod's {@code EVENT} is then
     *         null — unavoidable, but rare; EventFactory is core Fabric API)
     */
    public static Object installEvent(Class<?> v1Type) {
        try {
            ClassLoader cl = v1Type.getClassLoader();

            Class<?> eventFactory = Class.forName(EVENT_FACTORY, true, cl);
            Method createArrayBacked = eventFactory.getMethod(
                    "createArrayBacked", Class.class, Function.class);

            // invokerFactory: T[] listeners -> a single T that fans out to each.
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

    /**
     * Register a single v2 callback that replays each invocation onto the v1 event's
     * combined invoker, translating the 3-arg v2 SAM down to the 2-arg v1 SAM.
     */
    private static void wireV2Forwarder(ClassLoader cl, Class<?> v1Type, Object v1Event) throws Exception {
        Class<?> v2Type = Class.forName(V2_CALLBACK, true, cl);
        Object v2Event = v2Type.getField("EVENT").get(null);

        // v1 SAM: register(CommandDispatcher, boolean) — the only 2-arg method.
        Method v1Register = findByNameArity(v1Type, "register", 2);
        // Resolve Event methods against the PUBLIC Event interface, never the
        // runtime class — the impl (fabric.impl.base.event.ArrayBackedEvent) is
        // not public, so Methods looked up on it throw IllegalAccessException
        // even though the members are public (caught in the snapshot.3 in-game
        // pass: "could not wire the v2 forwarder").
        Class<?> eventIface = Class.forName("net.fabricmc.fabric.api.event.Event", false, cl);
        Method invokerM = eventIface.getMethod("invoker");

        Object v2Forwarder = Proxy.newProxyInstance(cl, new Class<?>[]{v2Type}, (proxy, method, args) -> {
            if (isSam(method)) {
                // v2 SAM: register(CommandDispatcher dispatcher, CommandBuildContext ctx,
                //                  Commands$CommandSelection selection)
                Object dispatcher = args[0];
                boolean dedicated = isDedicated(args[args.length - 1]);
                Object v1Invoker = invokerM.invoke(v1Event);
                invokeUnwrapped(v1Register, v1Invoker, new Object[]{dispatcher, dedicated});
                return null;
            }
            return objectMethod(proxy, method, args);
        });

        // Event.register(T) erases to register(Object) — on the public interface.
        eventIface.getMethod("register", Object.class).invoke(v2Event, v2Forwarder);
    }

    /**
     * v1 {@code dedicated} ⇐ the v2 {@code CommandSelection} is {@code DEDICATED}.
     * Keyed on {@code Enum.name()} (final, never overridden) via a plain
     * {@code instanceof java.lang.Enum} — no reflection per invocation, and we never
     * touch the package-private {@code includeDedicated} field (module-locked under
     * {@code net.minecraft}).
     */
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

    /** Invoke and unwrap {@link InvocationTargetException} so a mod's own error surfaces normally. */
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
