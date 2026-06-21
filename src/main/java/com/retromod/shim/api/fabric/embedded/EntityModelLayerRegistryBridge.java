/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Runtime half of the removed Fabric <b>{@code EntityModelLayerRegistry}</b> bridge
 * (entity model-layer registration). Audit gap: ~18 mods sole-blocked on
 * {@code EntityModelLayerRegistry$TexturedModelDataProvider}.
 *
 * <p>26.1's {@code ModelLayerRegistry} renamed the provider SAM
 * ({@code TexturedModelDataProvider.createModelData} →
 * {@code TexturedLayerDefinitionProvider.createLayerDefinition}) - a lambda trap -
 * but the <i>return type is the same class</i>: Yarn {@code TexturedModelData} is
 * Mojang {@code LayerDefinition} (intermediary {@code class_5607}), which the harvest
 * already remaps in the lambda. So this is a pure SAM-name rename on a provider; the
 * wrapper just calls the old SAM and returns its {@code LayerDefinition}.</p>
 *
 * <p>Unlike the event bridges, this is a <b>provider</b> (returns a value, no
 * registered listener list): {@code registerModelLayer} wraps the mod's old provider
 * in a new one and forwards once to {@code ModelLayerRegistry}.</p>
 *
 * <p>All reflection + {@link Proxy}, embedded raw into the mod jar; fails soft on a
 * reflective miss (the model layer simply isn't registered, logged).</p>
 *
 * <p><b>STATUS - authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.141.1} / {@code 0.145.4+26.1.2}. A 26.1 client launch with such
 * a mod (a custom entity model) is still required.</p>
 */
public final class EntityModelLayerRegistryBridge {

    private EntityModelLayerRegistryBridge() {}

    private static final String TAG = "[Retromod] EntityModelLayerRegistryBridge: ";

    private static final String MLR = "net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry";
    private static final String NEW_PROVIDER = MLR + "$TexturedLayerDefinitionProvider";
    private static final String SYNTH_PROVIDER = "com.retromod.generated.legacymodellayer.TexturedModelDataProvider";
    private static final String MODEL_LAYER_LOCATION = "net.minecraft.client.model.geom.ModelLayerLocation";

    private static ClassLoader cl() {
        return EntityModelLayerRegistryBridge.class.getClassLoader();
    }

    /**
     * v1 {@code EntityModelLayerRegistry.registerModelLayer(loc, oldProvider)} →
     * wrap {@code oldProvider} as a v2 {@code TexturedLayerDefinitionProvider} and
     * register it on {@code ModelLayerRegistry}.
     */
    public static void registerModelLayer(Object loc, Object oldProvider) {
        try {
            ClassLoader cl = cl();
            Class<?> mlr = Class.forName(MLR, true, cl);
            Class<?> newProviderType = Class.forName(NEW_PROVIDER, false, cl);
            Class<?> oldProviderType = Class.forName(SYNTH_PROVIDER, false, cl);
            Class<?> locType = Class.forName(MODEL_LAYER_LOCATION, false, cl);

            final Method oldSam = sam(oldProviderType); // createModelData() : LayerDefinition

            Object wrapped = Proxy.newProxyInstance(cl, new Class<?>[]{newProviderType}, (proxy, method, args) -> {
                if (isSam(method)) {
                    // new createLayerDefinition() → old createModelData(); same LayerDefinition class.
                    return invokeReturning(oldSam, oldProvider);
                }
                return objectMethod(proxy, method, args);
            });

            mlr.getMethod("registerModelLayer", locType, newProviderType).invoke(null, loc, wrapped);
        } catch (Throwable t) {
            System.out.println(TAG + "registerModelLayer failed (" + t + "); this model layer is not registered.");
        }
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

    private static Object invokeReturning(Method m, Object target) throws Throwable {
        try {
            return m.invoke(target);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals":   return proxy == (args == null ? null : args[0]);
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "RetromodModelLayerProvider@" + Integer.toHexString(System.identityHashCode(proxy));
            default:         return null;
        }
    }
}
