/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds an "API usage fingerprint" by walking every instruction in every
 * method and counting which MC packages the class touches and how often.
 * Catches classes that use MC APIs indirectly (helpers, reflection, generic
 * signatures with bodies full of {@code INVOKE*} into {@code net/minecraft/*})
 * that signature/annotation patterns miss, at a per-class cost proportional to
 * instruction count.
 *
 * <p>Matches (confidence 0.6) for any mod class invoking at least one
 * {@code net/minecraft/*} or {@code com/mojang/*} method or field. The match
 * carries the fingerprint metadata: {@code totalMcRefs} (total method, field,
 * and type references), {@code topSubpackages} (top 5 most-used MC subpackages),
 * and {@code suspiciousRefs} (references into version-unstable areas listed in
 * {@link #SUSPICIOUS_PREFIXES}).</p>
 */
public final class ApiUsageFingerprintPattern implements ClassPattern {

    /** Namespaces counted as MC API usage; anything else is mod or JDK code. */
    private static final String[] MC_NAMESPACES = {
            "net/minecraft/", "com/mojang/"
    };

    /** API areas that are unstable across MC versions, so high-risk after a transform. */
    private static final String[] SUSPICIOUS_PREFIXES = {
            "net/minecraft/client/renderer/",   // rendering internals
            "net/minecraft/client/gui/screens/",// GUI internals
            "net/minecraft/server/network/",    // packet handling
            "com/mojang/blaze3d/"               // GL state
    };

    /** Minimum references to a namespace before we include it in the top-N output. */
    private static final int MIN_REFS_TO_REPORT = 3;

    @Override
    public String name() { return "ApiUsageFingerprint"; }

    @Override
    public String description() {
        return "Which Minecraft API areas does this class touch most heavily?";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.methods == null || cls.methods.isEmpty()) return null;

        // count per sub-namespace; TreeMap gives deterministic top-N iteration
        Map<String, Integer> counts = new TreeMap<>();
        int total = 0;
        int suspicious = 0;

        for (MethodNode method : cls.methods) {
            InsnList insns = method.instructions;
            if (insns == null) continue;

            AbstractInsnNode cur = insns.getFirst();
            while (cur != null) {
                String ref = extractReferenceOwner(cur);
                if (ref != null && isMcNamespace(ref)) {
                    String bucket = reduceToSubNamespace(ref);
                    counts.merge(bucket, 1, Integer::sum);
                    total++;
                    if (isSuspicious(ref)) suspicious++;
                }
                cur = cur.getNext();
            }
        }

        if (total == 0) return null;

        // top 5 namespaces by count, sorted for stable output
        String topBreakdown = counts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REFS_TO_REPORT)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> e.getKey() + ":" + e.getValue())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("totalMcRefs", total);
        if (!topBreakdown.isEmpty()) metadata.put("topSubpackages", topBreakdown);
        if (suspicious > 0) metadata.put("suspiciousRefs", suspicious);

        // low confidence: "uses MC API" is weak on its own, the value is the breakdown
        return new PatternMatch(name(), cls.name, 0.6, metadata);
    }

    /**
     * Owner class (internal name) of an instruction that references another
     * class, or null for instructions that reference none (loads, arithmetic).
     * {@link TypeInsnNode} descriptors come in bare ({@code CHECKCAST}) and
     * array ({@code ANEWARRAY}) forms, both normalized via {@link #normalizeTypeRef}.
     */
    private static String extractReferenceOwner(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode m) return m.owner;
        if (insn instanceof FieldInsnNode f) return f.owner;
        if (insn instanceof TypeInsnNode t) return normalizeTypeRef(t.desc);
        return null;
    }

    /**
     * Bare internal name of the class a {@link TypeInsnNode} descriptor refers
     * to, or null for a primitive-array type.
     */
    private static String normalizeTypeRef(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        if (desc.charAt(0) == '[') {
            Type elem = Type.getType(desc).getElementType();
            return elem.getSort() == Type.OBJECT ? elem.getInternalName() : null;
        }
        return desc;
    }

    private static boolean isMcNamespace(String name) {
        for (String ns : MC_NAMESPACES) {
            if (name.startsWith(ns)) return true;
        }
        return false;
    }

    private static boolean isSuspicious(String name) {
        for (String pre : SUSPICIOUS_PREFIXES) {
            if (name.startsWith(pre)) return true;
        }
        return false;
    }

    /**
     * Reduce a fully-qualified class reference to its "sub-namespace" so we
     * can aggregate. For {@code net/minecraft/world/level/block/Blocks} we
     * return {@code net/minecraft/world/level/block}. Gives us a meaningful
     * histogram without being overwhelmed by individual class counts.
     */
    private static String reduceToSubNamespace(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash > 0 ? internalName.substring(0, lastSlash) : internalName;
    }
}
