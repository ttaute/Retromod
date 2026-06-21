/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Pre-1.20.5 {@code Identifier} (ResourceLocation) constructor bridge
 * (Fabric, pre-26.1 hosts, intermediary namespace).
 *
 * <h2>The problem</h2>
 * Up through 1.20.4 you constructed {@code Identifier}/{@code ResourceLocation} with
 * a public constructor:
 * <pre>
 *   new ResourceLocation("earth2java:bed_textures");        // (String)V
 *   new ResourceLocation("earth2java", "bed_textures");      // (String,String)V
 * </pre>
 * 1.20.5 deleted both ctors and routed everything through static factories:
 * {@code ResourceLocation.parse} and {@code ResourceLocation.fromNamespaceAndPath}.
 * Any Fabric mod compiled against ≤1.20.4 still emits {@code INVOKESPECIAL
 * class_2960.<init>(Ljava/lang/String;)V} (etc.), and on a 1.20.5+ host that's a
 * {@code NoSuchMethodError} the instant the call site is touched (Earth2Java's
 * {@code Earth2JavaClientMod.addBedTextureToAtlas} is one of dozens of hits).
 *
 * <h2>Why this is its own bridge (not the existing polyfill)</h2>
 * {@link com.retromod.polyfill.minecraft.registry.RegistryPolyfill} already registers
 * the same ctor → factory redirects, but keyed on the <b>Mojang</b> names
 * ({@code net/minecraft/util/ResourceLocation}, {@code net/minecraft/resources/ResourceLocation}).
 * On a pre-26.1 Fabric host the intermediary→Mojang remap is gated OFF (#21/#29), so
 * the visitor sees {@code class_2960} in the bytecode and never matches those keys.
 * The mapper also installs intermediary-named ctor redirects via
 * {@code IntermediaryToMojangMapper.applyClassMovesOnly}, but those too are
 * 26.1+-gated - they fire under {@code isUnobfuscatedTarget(host)}, i.e. only when
 * the bytecode has already been renamed to Mojang names. So pre-26.1 Fabric was an
 * uncovered gap.
 *
 * <h2>Discovery</h2>
 * The intermediary IDs of {@code parse} / {@code fromNamespaceAndPath} drift between
 * MC versions ({@code method_60654/55} on 1.21.x, different numbers earlier). Rather
 * than ship a per-version table, the bridge reflectively probes the host's
 * {@code class_2960} at registration and looks for the matching signatures:
 * <ul>
 *   <li>static {@code (String) -> class_2960} → that's {@code parse}</li>
 *   <li>static {@code (String, String) -> class_2960} → {@code fromNamespaceAndPath}</li>
 * </ul>
 * The discovered method names are wired into the redirect. Whatever the host's
 * intermediary numbering happens to be, we hit it.
 *
 * <h2>Gating</h2>
 * Wired in alongside the model + InteractionResult bridges (pre-26.1 hosts only).
 * Self-gated as a no-op when either factory is absent - i.e. on a host where the
 * old ctors are still present and the registration would do nothing useful.
 */
public final class Pre1_20_5IdentifierCtorBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String IDENTIFIER = "net/minecraft/class_2960";
    private static final String IDENTIFIER_FQN = "net.minecraft.class_2960";
    private static final String L_IDENTIFIER = "L" + IDENTIFIER + ";";

    private Pre1_20_5IdentifierCtorBridge() {}

    /** Wire constructor → factory redirects, auto-discovering the host's factory names. */
    public static void register(RetromodTransformer transformer) {
        Class<?> identifier;
        try {
            identifier = Class.forName(IDENTIFIER_FQN, false,
                    Pre1_20_5IdentifierCtorBridge.class.getClassLoader());
        } catch (Throwable t) {
            LOGGER.debug("[Retromod] Identifier ctor bridge - class_2960 not on classpath, skipping ({})",
                    t.getClass().getSimpleName());
            return;
        }

        String parseName = findStaticFactory(identifier, String.class);
        String fromNsPathName = findStaticFactory(identifier, String.class, String.class);

        int registered = 0;
        if (parseName != null) {
            transformer.registerConstructorRedirect(
                    IDENTIFIER, "(Ljava/lang/String;)V",
                    IDENTIFIER, parseName,
                    "(Ljava/lang/String;)" + L_IDENTIFIER);
            registered++;
        }
        if (fromNsPathName != null) {
            transformer.registerConstructorRedirect(
                    IDENTIFIER, "(Ljava/lang/String;Ljava/lang/String;)V",
                    IDENTIFIER, fromNsPathName,
                    "(Ljava/lang/String;Ljava/lang/String;)" + L_IDENTIFIER);
            registered++;
        }

        if (registered == 0) {
            LOGGER.info("[Retromod] Identifier ctor bridge - no static (String)→Identifier "
                    + "or (String,String)→Identifier factory on class_2960; host likely still "
                    + "has the public ctors, nothing to redirect");
        } else {
            LOGGER.info("[Retromod] Identifier ctor bridge - wired {} redirect(s): "
                    + "new class_2960({}) → class_2960.{}(...)",
                    registered,
                    fromNsPathName != null ? "(String[,String])" : "String",
                    parseName != null ? parseName : "?");
        }
    }

    /**
     * Find the single public-static method on {@code cls} whose signature is
     * {@code (paramTypes...) -> cls}, returning its name (intermediary or Mojang -
     * whichever the host exposes). Returns null if zero or multiple candidates
     * exist; multiple is a deliberate fail-safe because rewriting to the wrong
     * factory would silently corrupt every Identifier construction at every call
     * site, and a clear startup log is much easier to debug than a half-broken
     * runtime.
     */
    private static String findStaticFactory(Class<?> cls, Class<?>... paramTypes) {
        String match = null;
        for (Method m : cls.getDeclaredMethods()) {
            int mods = m.getModifiers();
            if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods)) continue;
            if (m.getReturnType() != cls) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != paramTypes.length) continue;
            boolean ok = true;
            for (int i = 0; i < p.length; i++) {
                if (p[i] != paramTypes[i]) { ok = false; break; }
            }
            if (!ok) continue;
            if (match != null) {
                LOGGER.warn("[Retromod] Identifier ctor bridge - multiple static factories with the "
                        + "expected signature on class_2960 ({} and {}), refusing to guess - "
                        + "this Identifier ctor will stay broken",
                        match, m.getName());
                return null;
            }
            match = m.getName();
        }
        return match;
    }
}
