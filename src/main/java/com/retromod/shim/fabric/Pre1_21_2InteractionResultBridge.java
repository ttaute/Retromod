/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Pre-1.21.2 {@code InteractionResult} field-descriptor bridge
 * (Fabric, pre-26.1 hosts, intermediary namespace).
 *
 * <h2>The problem</h2>
 * Up to 1.21.1, {@code class_1269} (InteractionResult) was a plain enum and
 * {@code field_5811} (PASS) / {@code field_5812} / {@code field_5814} / etc. were
 * static fields with declared type {@code Lclass_1269;}. In 1.21.2 it was rebuilt
 * as a <b>sealed interface</b> with nested case types — {@code field_5811} now has
 * declared type {@code Lclass_1269$class_9859;} (a sealed implementor), and the
 * other constant fields point at sibling nested types.
 *
 * <p>The names survive, so the intermediary→Mojang remap doesn't notice. But every
 * pre-1.21.2 Fabric mod compiled with {@code GETSTATIC class_1269.field_5811 : Lclass_1269;}
 * dies on a 1.21.2+ host with {@code NoSuchFieldError} — the JVM's field-resolution
 * key is the full {@code (owner, name, descriptor)} triple, and the descriptor no
 * longer matches. AutoConfig hits this trying to populate config-screen defaults
 * (Earth2Java's {@code ConfigClassHandler}); cascades into a startup crash.
 *
 * <h2>The fix</h2>
 * Rewrite the GETSTATIC's descriptor to whatever the host field actually declares.
 * The result on the stack is a {@code class_1269$class_9859} (etc.), which IS-A
 * {@code class_1269} (it's a sealed implementor of the interface), so any downstream
 * use that expects {@code Lclass_1269;} verifies cleanly without a CHECKCAST — the
 * JVM verifier accepts subtype assignment for free.
 *
 * <h2>Discovery</h2>
 * At registration we reflectively walk the host's {@code class_1269} declared fields
 * and register a redirect for every public-static-final field whose actual type is a
 * nested type of {@code class_1269}. That auto-handles {@code field_5811/5812/5814/
 * 21466/52422/52423} (and any future siblings) without us having to hardcode the
 * intermediary IDs of the nested case types — they shift between MC versions and
 * the next snapshot would invalidate the table.
 *
 * <h2>Gating</h2>
 * Two safeties: (1) {@link RetromodPreLaunch} only calls {@code register} on pre-26.1
 * hosts (intermediary namespace — on 26.x the mapper would remap {@code class_1269}
 * to {@code InteractionResult} first and we'd need a separately-keyed registration),
 * and (2) {@code register} itself probes the host and registers <b>nothing</b> unless
 * the descriptor change is actually present — so on a pre-1.21.2 host where the field
 * is still {@code Lclass_1269;} we are a no-op, which is the right thing (rewriting
 * there would BREAK working mods).
 */
public final class Pre1_21_2InteractionResultBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /** Intermediary internal name for InteractionResult. */
    private static final String INTERACTION_RESULT = "net/minecraft/class_1269";

    /** Fully-qualified form for {@code Class.forName}. */
    private static final String INTERACTION_RESULT_FQN = "net.minecraft.class_1269";

    /** The descriptor any pre-1.21.2 mod compiled the constants with. */
    private static final String OLD_DESC = "L" + INTERACTION_RESULT + ";";

    private Pre1_21_2InteractionResultBridge() {}

    /**
     * Register field-descriptor rewrites for every static InteractionResult constant
     * whose actual type on the host is a nested case type. No-op on hosts where the
     * fields still have their original {@code Lclass_1269;} descriptor — and no-op
     * (with a one-line debug log) when {@code class_1269} isn't on the classpath at
     * all (unit tests, etc.).
     */
    public static void register(RetromodTransformer transformer) {
        Class<?> ir;
        try {
            ir = Class.forName(INTERACTION_RESULT_FQN, false,
                    Pre1_21_2InteractionResultBridge.class.getClassLoader());
        } catch (Throwable t) {
            LOGGER.debug("[Retromod] InteractionResult bridge — class_1269 not on classpath, skipping ({})",
                    t.getClass().getSimpleName());
            return;
        }

        int registered = 0;
        StringBuilder summary = new StringBuilder();
        for (Field f : ir.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (!(Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods))) {
                continue;
            }
            Class<?> fieldType = f.getType();
            // Only act on fields whose declared type differs from class_1269 itself.
            // Same-type fields (Lclass_1269;) need no rewrite — the original bytecode
            // already resolves cleanly, and rewriting would break a pre-1.21.2 host.
            if (fieldType == ir) continue;

            // Only rewrite when the field's actual type is a class_1269 SUBTYPE.
            // Anything else (e.g. an unrelated helper field that happens to be
            // public-static-final) we leave alone — the safe thing.
            if (!ir.isAssignableFrom(fieldType)) continue;

            String actualDesc = "L" + fieldType.getName().replace('.', '/') + ";";
            transformer.registerFieldRedirect(
                    INTERACTION_RESULT, f.getName(), OLD_DESC,
                    INTERACTION_RESULT, f.getName(), actualDesc);
            registered++;
            if (summary.length() > 0) summary.append(", ");
            summary.append(f.getName()).append("→").append(actualDesc);
        }

        if (registered == 0) {
            LOGGER.info("[Retromod] InteractionResult bridge — host's class_1269 has the legacy "
                    + "shape (all constants typed Lclass_1269;), nothing to rewrite");
        } else {
            LOGGER.info("[Retromod] InteractionResult bridge — registered {} descriptor rewrite(s): {}",
                    registered, summary);
        }
    }
}
