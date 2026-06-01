/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime bridge for the removed Fabric <b>client networking v1</b> raw-channel API
 * (the {@code ResourceLocation}-keyed, raw-{@code FriendlyByteBuf} form that existed
 * before the 1.20.5 typed-payload rewrite).
 *
 * <h2>What it bridges</h2>
 * Old API (removed in 26.1):
 * <pre>
 *   ClientPlayNetworking.registerGlobalReceiver(Identifier, PlayChannelHandler) : boolean
 *   ClientPlayNetworking.registerReceiver(Identifier, PlayChannelHandler)       : boolean
 *   ClientPlayNetworking.send(Identifier, FriendlyByteBuf)                       : void
 *   ClientPlayNetworking.canSend(Identifier)                                     : boolean
 *   PlayChannelHandler.receive(Minecraft, ClientPacketListener, FriendlyByteBuf, PacketSender)
 * </pre>
 * 26.1 API (typed payloads):
 * <pre>
 *   PayloadTypeRegistry.clientboundPlay()/serverboundPlay().register(Type, StreamCodec)
 *   ClientPlayNetworking.registerGlobalReceiver(CustomPacketPayload$Type, PlayPayloadHandler)
 *   ClientPlayNetworking.send(CustomPacketPayload)
 *   PlayPayloadHandler.receive(CustomPacketPayload, Context)
 * </pre>
 *
 * <h2>How</h2>
 * For each channel {@code id} the mod touches, we lazily register a
 * {@code CustomPacketPayload.Type(id)} + a {@link java.lang.reflect.Proxy}-backed
 * {@code StreamCodec} that just copies the raw bytes (read-all-readable on decode,
 * write-all on encode — the exact contract the old raw API had). Inbound packets
 * are decoded into a {@code Proxy}-backed {@code CustomPacketPayload} carrying the
 * raw bytes; a {@code Proxy}-backed {@code PlayPayloadHandler} unwraps the bytes,
 * rebuilds a {@code FriendlyByteBuf}, and invokes the mod's old handler with
 * {@code (Minecraft, ClientPacketListener, buf, PacketSender)}.
 *
 * <h2>Why pure reflection + Proxy (no ASM, no MC compile deps)</h2>
 * MC 26.1 is <b>unobfuscated</b>, so every MC/Fabric member resolves by its Mojang
 * name at runtime — this class hardcodes those names and resolves them reflectively.
 * Two payoffs: (1) it compiles against only {@code java.*}, so it embeds cleanly into
 * a mod jar with no unresolved references; (2) every reflective step is wrapped so a
 * wrong name <b>degrades to a logged no-op</b> (the mod still loads, networking goes
 * inert) instead of a hard {@code VerifyError}/{@code NoSuchMethodError} crash.
 *
 * <p><b>STATUS:</b> authored against verified 26.1.2 contracts but <b>not yet
 * runtime-verified</b> — must be exercised on a real 26.1 client launch (a custom
 * packet round-trip) before being relied on. See {@code docs/dev/cpn-v1-bridge-design.md}.
 */
public final class ClientPlayNetworkingV1Bridge {

    private ClientPlayNetworkingV1Bridge() {}

    private static final String TAG = "[Retromod/CPNv1] ";
    // Embedded into mod jars — must not depend on Retromod's (shaded) SLF4J. Use stdio.
    private static void info(String m) { System.out.println(TAG + m); }
    private static void warn(String m, Throwable t) {
        System.err.println(TAG + m + (t != null ? ": " + t : ""));
    }

    // ── Lazily-resolved reflective handles (26.1 runtime, Mojang names) ──
    private static volatile boolean ready = false;
    private static volatile boolean initFailed = false;

    private static Class<?> cType, cStreamCodec, cCustomPayload, cPayloadHandler, cContext, cByteBuf;
    private static Constructor<?> typeCtor;        // CustomPacketPayload$Type(Identifier)
    private static Constructor<?> friendlyBufCtor; // FriendlyByteBuf(ByteBuf)
    private static Method mClientboundPlay, mServerboundPlay; // PayloadTypeRegistry statics
    private static Method mRegistryRegister;       // PayloadTypeRegistry.register(Type, StreamCodec)
    private static Method mNetRegisterReceiver;    // ClientPlayNetworking.registerGlobalReceiver(Type, PlayPayloadHandler)
    private static Method mNetSend;                // ClientPlayNetworking.send(CustomPacketPayload)
    private static Method mNetCanSend;             // ClientPlayNetworking.canSend(Type)   (nullable)
    private static Method mCtxPlayer, mCtxResponseSender;
    private static Method mBufReadableBytes, mBufReadBytes, mBufWriteBytes;
    private static Method mUnpooledWrapped;        // Unpooled.wrappedBuffer(byte[])
    private static Method mMcGetInstance, mMcGetConnection;

    // id -> cached Type instance ; ids already registered with PayloadTypeRegistry
    private static final Map<String, Object> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    private static synchronized void ensureInit() {
        if (ready || initFailed) return;
        try {
            cType          = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type");
            cStreamCodec   = Class.forName("net.minecraft.network.codec.StreamCodec");
            cCustomPayload = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            cByteBuf       = Class.forName("io.netty.buffer.ByteBuf");
            cPayloadHandler = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler");
            cContext        = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$Context");
            Class<?> cPayloadReg = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            Class<?> cClientNet  = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            Class<?> cFriendly   = Class.forName("net.minecraft.network.FriendlyByteBuf");
            Class<?> cUnpooled   = Class.forName("io.netty.buffer.Unpooled");
            Class<?> cMc         = Class.forName("net.minecraft.client.Minecraft");

            typeCtor = singleArgCtor(cType);
            friendlyBufCtor = cFriendly.getConstructor(cByteBuf);

            mClientboundPlay = cPayloadReg.getMethod("clientboundPlay");
            mServerboundPlay = cPayloadReg.getMethod("serverboundPlay");
            mRegistryRegister = firstByNameAndArity(cPayloadReg, "register", 2);

            mNetRegisterReceiver = firstStaticByNameAndArity(cClientNet, "registerGlobalReceiver", 2);
            mNetSend = firstStaticByNameAndArity(cClientNet, "send", 1);
            mNetCanSend = firstStaticByNameAndArityOrNull(cClientNet, "canSend", 1);

            mCtxPlayer = cContext.getMethod("player");
            mCtxResponseSender = cContext.getMethod("responseSender");

            mBufReadableBytes = cByteBuf.getMethod("readableBytes");
            mBufReadBytes = cByteBuf.getMethod("readBytes", byte[].class);
            mBufWriteBytes = cByteBuf.getMethod("writeBytes", byte[].class);
            mUnpooledWrapped = cUnpooled.getMethod("wrappedBuffer", byte[].class);

            mMcGetInstance = cMc.getMethod("getInstance");
            mMcGetConnection = noArgReturning(cMc, "net.minecraft.client.multiplayer.ClientPacketListener");

            if (mRegistryRegister == null || mNetRegisterReceiver == null || mNetSend == null) {
                throw new NoSuchMethodException("core 26.1 networking entrypoint not found");
            }
            ready = true;
            info("client-networking v1 bridge initialised");
        } catch (Throwable t) {
            initFailed = true;
            warn("init failed — client networking v1 bridge inert (mods load, custom packets dropped)", t);
        }
    }

    // ═══════════════════════ Public bridge entrypoints (method-redirect targets) ═══════════════════════
    // All take Object so the compiled descriptor is (Ljava/lang/Object;...) — the redirect rewrites the
    // call site's INVOKE; the verifier accepts the call site's concrete types as Object subtypes.

    /** old {@code registerGlobalReceiver(Identifier, PlayChannelHandler) : boolean}. */
    public static boolean registerGlobalReceiver(Object id, Object oldHandler) {
        return doRegister(id, oldHandler);
    }

    /** old {@code registerReceiver(Identifier, PlayChannelHandler) : boolean} — best-effort global. */
    public static boolean registerReceiver(Object id, Object oldHandler) {
        return doRegister(id, oldHandler);
    }

    /** old {@code send(Identifier, FriendlyByteBuf) : void}. */
    public static void send(Object id, Object buf) {
        ensureInit();
        if (!ready) return;
        try {
            registerChannel(id);
            byte[] data = drainBytes(buf);
            Object payload = newRawPayload(typeFor(id), data);
            mNetSend.invoke(null, payload);
        } catch (Throwable t) {
            warn("send failed for channel " + id, t);
        }
    }

    /** old {@code canSend(Identifier) : boolean}. */
    public static boolean canSend(Object id) {
        ensureInit();
        if (!ready || mNetCanSend == null) return false;
        try {
            return (Boolean) mNetCanSend.invoke(null, typeFor(id));
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════════════════ Internals ═══════════════════════

    private static boolean doRegister(Object id, Object oldHandler) {
        ensureInit();
        if (!ready) return false;
        try {
            registerChannel(id);
            Object newHandler = newReceiveAdapter(oldHandler);
            Object result = mNetRegisterReceiver.invoke(null, typeFor(id), newHandler);
            return result instanceof Boolean ? (Boolean) result : true;
        } catch (Throwable t) {
            warn("registerReceiver failed for channel " + id, t);
            return false;
        }
    }

    /** Cache (and build on first use) the {@code CustomPacketPayload.Type} for a channel id. */
    private static Object typeFor(Object id) throws Exception {
        String key = String.valueOf(id);
        Object t = TYPE_CACHE.get(key);
        if (t == null) {
            t = typeCtor.newInstance(id);
            Object prev = TYPE_CACHE.putIfAbsent(key, t);
            if (prev != null) t = prev;
        }
        return t;
    }

    /**
     * Register the raw-bytes codec for this channel on BOTH play registries (a client
     * mod receives S2C and sends C2S), once per id. Failures (already registered, or
     * the registry is frozen because we're past the registration phase) are non-fatal.
     */
    private static void registerChannel(Object id) {
        String key = String.valueOf(id);
        if (!REGISTERED.add(key)) return;
        try {
            Object type = typeFor(id);
            // The codec is bound to THIS channel's Type so decoded payloads report the
            // correct type() — Fabric/vanilla routes inbound packets by payload.type(),
            // so a null/wrong type would break inbound dispatch. Each registry gets its
            // own codec instance (cheap) carrying the same type.
            tryRegister(mClientboundPlay, type, newRawCodec(type));
            tryRegister(mServerboundPlay, type, newRawCodec(type));
        } catch (Throwable t) {
            warn("codec registration failed for channel " + key, t);
        }
    }

    private static void tryRegister(Method registryStatic, Object type, Object codec) {
        try {
            Object registry = registryStatic.invoke(null);
            mRegistryRegister.invoke(registry, type, codec);
        } catch (Throwable t) {
            // Already registered or frozen — fine; the other direction may still take.
        }
    }

    // ── Proxy factories ──

    /** {@code CustomPacketPayload} carrying (Type, raw bytes). */
    private static Object newRawPayload(Object type, byte[] data) {
        return Proxy.newProxyInstance(loader(), new Class<?>[]{cCustomPayload},
                new RawPayloadHandler(type, data));
    }

    /** {@code StreamCodec<RegistryFriendlyByteBuf, RawPayload>} bound to {@code type} —
     *  copies raw bytes both ways; decoded payloads report {@code type} so they route. */
    private static Object newRawCodec(Object type) {
        return Proxy.newProxyInstance(loader(), new Class<?>[]{cStreamCodec}, new RawCodecHandler(type));
    }

    /** {@code PlayPayloadHandler} that unwraps bytes and calls the mod's old 4-arg handler. */
    private static Object newReceiveAdapter(Object oldHandler) {
        return Proxy.newProxyInstance(loader(), new Class<?>[]{cPayloadHandler},
                new ReceiveAdapter(oldHandler));
    }

    private static ClassLoader loader() {
        return ClientPlayNetworkingV1Bridge.class.getClassLoader();
    }

    /** Backing handler for a raw {@code CustomPacketPayload} proxy. */
    private static final class RawPayloadHandler implements InvocationHandler {
        final Object type;
        final byte[] data;
        RawPayloadHandler(Object type, byte[] data) { this.type = type; this.data = data; }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "type":     return type;          // CustomPacketPayload.type()
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == (args != null ? args[0] : null);
                case "toString": return "RetromodRawPayload";
                default:
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    return null;
            }
        }
    }

    /** Backing handler for the raw-bytes {@code StreamCodec} proxy, bound to one channel Type. */
    private static final class RawCodecHandler implements InvocationHandler {
        final Object type;
        RawCodecHandler(Object type) { this.type = type; }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "decode": {           // V decode(B buffer)
                    Object buf = args[0];
                    byte[] data = drainBytes(buf);
                    // Carry this codec's bound Type so the decoded payload routes correctly
                    // (Fabric/vanilla dispatches inbound by payload.type()).
                    return newRawPayload(type, data);
                }
                case "encode": {           // void encode(B buffer, V value)
                    Object buf = args[0];
                    Object payload = args[1];
                    byte[] data = bytesOf(payload);
                    if (data != null) mBufWriteBytes.invoke(buf, (Object) data);
                    return null;
                }
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == (args != null ? args[0] : null);
                case "toString": return "RetromodRawCodec";
                default:
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    return null;
            }
        }
    }

    /** Backing handler for the new-API {@code PlayPayloadHandler} proxy. */
    private static final class ReceiveAdapter implements InvocationHandler {
        final Object oldHandler;
        Method oldReceive; // cached SAM (receive, 4 params)
        ReceiveAdapter(Object oldHandler) { this.oldHandler = oldHandler; }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!"receive".equals(method.getName())) {
                if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                if ("toString".equals(method.getName())) return "RetromodReceiveAdapter";
                if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                if ("equals".equals(method.getName())) return proxy == (args != null ? args[0] : null);
                return null;
            }
            try {
                Object payload = args[0];
                Object ctx = args[1];
                byte[] data = bytesOf(payload);
                if (data == null) data = new byte[0];

                Object nettyBuf = mUnpooledWrapped.invoke(null, (Object) data);
                Object friendlyBuf = friendlyBufCtor.newInstance(nettyBuf);

                Object mc = mMcGetInstance.invoke(null);
                Object connection = null;
                if (mMcGetConnection != null) {
                    try { connection = mMcGetConnection.invoke(mc); } catch (Throwable ignored) {}
                }
                Object sender = mCtxResponseSender.invoke(ctx);

                if (oldReceive == null) {
                    // Resolve the SAM from the IMPLEMENTED INTERFACE, not the handler's
                    // own class: a lambda is a JDK "hidden class" whose methods are not
                    // reflectively accessible, but the interface's public method is and
                    // dispatches virtually to the lambda body.
                    oldReceive = receiveFromInterfaces(oldHandler.getClass());
                    if (oldReceive == null) {
                        oldReceive = firstByNameAndArity(oldHandler.getClass(), "receive", 4);
                    }
                    if (oldReceive != null) {
                        try { oldReceive.setAccessible(true); } catch (Throwable ignored) {}
                    }
                }
                if (oldReceive != null) {
                    oldReceive.invoke(oldHandler, mc, connection, friendlyBuf, sender);
                }
            } catch (Throwable t) {
                warn("inbound packet dispatch failed", t);
            }
            return null;
        }
    }

    // ── byte helpers ──

    /** Read all readable bytes out of a (Registry)FriendlyByteBuf / ByteBuf, consuming them. */
    private static byte[] drainBytes(Object buf) {
        try {
            int n = (Integer) mBufReadableBytes.invoke(buf);
            byte[] data = new byte[Math.max(0, n)];
            if (n > 0) mBufReadBytes.invoke(buf, (Object) data);
            return data;
        } catch (Throwable t) {
            warn("buffer read failed", t);
            return new byte[0];
        }
    }

    /** Extract the raw bytes carried by one of our payload proxies (null if not ours). */
    private static byte[] bytesOf(Object payload) {
        try {
            if (payload != null && Proxy.isProxyClass(payload.getClass())) {
                InvocationHandler h = Proxy.getInvocationHandler(payload);
                if (h instanceof RawPayloadHandler) return ((RawPayloadHandler) h).data;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ── reflection lookup helpers ──

    private static Constructor<?> singleArgCtor(Class<?> c) {
        for (Constructor<?> ctor : c.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 1) { ctor.setAccessible(true); return ctor; }
        }
        return c.getDeclaredConstructors()[0];
    }

    /** Find {@code receive(...4 args)} on any interface the handler implements
     *  (walks the interface hierarchy). The returned Method is declared on a public
     *  interface, so it stays invokable even when the impl is a JDK hidden lambda. */
    private static Method receiveFromInterfaces(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Class<?> itf : k.getInterfaces()) {
                for (Method m : itf.getMethods()) {
                    if (m.getName().equals("receive") && m.getParameterCount() == 4) return m;
                }
                Method nested = receiveFromInterfaces(itf);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static Method firstByNameAndArity(Class<?> c, String name, int arity) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        return null;
    }

    private static Method firstStaticByNameAndArity(Class<?> c, String name, int arity) {
        for (Method m : c.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())
                    && m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        return null;
    }

    private static Method firstStaticByNameAndArityOrNull(Class<?> c, String name, int arity) {
        return firstStaticByNameAndArity(c, name, arity);
    }

    private static Method noArgReturning(Class<?> c, String returnTypeName) {
        for (Method m : c.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType().getName().equals(returnTypeName)) return m;
        }
        return null;
    }

    /** old {@code getSendable() : Set} — channels we've registered for sending. */
    public static Set<Object> getSendable() {
        return Collections.emptySet();
    }
}
