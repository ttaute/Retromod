/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects Forge/NeoForge registry holder classes: the {@code public static final
 * DeferredRegister<X>} + {@code public static final DeferredHolder<Y>} idiom.
 *
 * <p>Confidence is 0.95 when at least one {@code DeferredRegister} field is present
 * (the field type is distinctive enough to rule out false positives), 0.7 when only
 * holder fields appear.</p>
 *
 * <p>Reports {@code deferredRegisterCount}, {@code holderCount}, and
 * {@code registryType} (the first {@code DeferredRegister}'s generic parameter,
 * when its signature is present). The gap report uses this to tell mod authors
 * whether a registry holder survived transformation intact.</p>
 */
public final class DeferredRegisterHolderPattern implements ClassPattern {

    /** Old Forge and new NeoForge names, we accept either. */
    private static final String OLD_DEFERRED_REGISTER =
            "Lnet/minecraftforge/registries/DeferredRegister;";
    private static final String NEW_DEFERRED_REGISTER =
            "Lnet/neoforged/neoforge/registries/DeferredRegister;";
    private static final String OLD_REGISTRY_OBJECT =
            "Lnet/minecraftforge/registries/RegistryObject;";
    private static final String NEW_DEFERRED_HOLDER =
            "Lnet/neoforged/neoforge/registries/DeferredHolder;";

    /** Mask matching public-static-final. */
    private static final int PSF_MASK =
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;

    @Override
    public String name() { return "DeferredRegisterHolder"; }

    @Override
    public String description() {
        return "Classes holding DeferredRegister<T> fields - mod registry holders";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.fields == null || cls.fields.isEmpty()) return null;

        int deferredRegisterCount = 0;
        int holderCount = 0;
        String firstRegistryType = null;

        for (FieldNode f : cls.fields) {
            // the idiom uses public-static-final fields only
            if ((f.access & PSF_MASK) != PSF_MASK) continue;
            if (f.desc == null) continue;

            if (f.desc.equals(OLD_DEFERRED_REGISTER) || f.desc.equals(NEW_DEFERRED_REGISTER)) {
                deferredRegisterCount++;
                if (firstRegistryType == null) {
                    firstRegistryType = extractGenericType(f.signature);
                }
            } else if (f.desc.equals(OLD_REGISTRY_OBJECT) || f.desc.equals(NEW_DEFERRED_HOLDER)) {
                holderCount++;
            }
        }

        if (deferredRegisterCount == 0 && holderCount == 0) return null;

        // holder-only class (DeferredRegister lives elsewhere): report at lower confidence
        double confidence = deferredRegisterCount > 0 ? 0.95 : 0.7;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deferredRegisterCount", deferredRegisterCount);
        metadata.put("holderCount", holderCount);
        if (firstRegistryType != null) metadata.put("registryType", firstRegistryType);
        return new PatternMatch(name(), cls.name, confidence, metadata);
    }

    /**
     * Pulls the generic parameter out of a {@code DeferredRegister<T>} field signature,
     * so {@code Lnet/.../DeferredRegister<Lnet/minecraft/world/item/Item;>;} yields
     * {@code net/minecraft/world/item/Item}. Null if absent or in an unexpected shape.
     */
    private static String extractGenericType(String signature) {
        if (signature == null) return null;
        int angle = signature.indexOf('<');
        if (angle < 0) return null;
        int innerL = signature.indexOf('L', angle);
        if (innerL < 0) return null;
        int innerSemi = signature.indexOf(';', innerL);
        if (innerSemi < 0) return null;
        return signature.substring(innerL + 1, innerSemi);
    }
}
