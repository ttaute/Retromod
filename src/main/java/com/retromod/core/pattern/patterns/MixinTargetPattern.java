/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects <a href="https://github.com/SpongePowered/Mixin">Mixin</a> classes and,
 * where the MC index is available, reports which of their targets still exist in
 * the target MC version.
 *
 * <p>Mixins resolve targets at runtime, so a renamed or removed MC class produces
 * a "mod loads but does nothing" bug with no compile-time signal. This pattern
 * extracts each {@code @Mixin} class's declared targets so the gap report can flag
 * the ones now pointing at missing types.</p>
 *
 * <p>Match confidence is 1.0 for any {@code @Mixin}-annotated class. Metadata holds
 * {@code targetClasses} (the targets from the {@code value} array), and when the MC
 * index is available {@code targetsResolved}, {@code targetsMissing}, and the
 * {@code missingTargets} list.</p>
 *
 * <p>Only the {@code value} array is read; the {@code targets} string-array form
 * ({@code @Mixin(targets = {"net.minecraft.X"})}) is out of scope for now.</p>
 */
public final class MixinTargetPattern implements ClassPattern {

    private static final String MIXIN_ANNOTATION_DESC =
            "Lorg/spongepowered/asm/mixin/Mixin;";

    @Override
    public String name() { return "MixinTarget"; }

    @Override
    public String description() {
        return "Mixin classes and whether their targets still exist in MC";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.visibleAnnotations == null) return null;

        AnnotationNode mixinAnnotation = null;
        for (AnnotationNode ann : cls.visibleAnnotations) {
            if (MIXIN_ANNOTATION_DESC.equals(ann.desc)) {
                mixinAnnotation = ann;
                break;
            }
        }
        if (mixinAnnotation == null) return null;

        List<String> targetClassNames = extractMixinValueArray(mixinAnnotation);
        if (targetClassNames.isEmpty()) {
            // @Mixin with no value= is usually the targets= form, which we don't read.
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("note", "targets-not-extractable");
            return new PatternMatch(name(), cls.name, 1.0, metadata);
        }

        int resolved = 0;
        int missing = 0;
        List<String> missingTargets = new ArrayList<>();
        if (ctx.mcIndex().isAvailable()) {
            for (String target : targetClassNames) {
                if (ctx.mcIndex().hasClass(target)) {
                    resolved++;
                } else {
                    missing++;
                    missingTargets.add(target);
                }
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetClasses", String.join(",", targetClassNames));
        if (ctx.mcIndex().isAvailable()) {
            metadata.put("targetsResolved", resolved);
            metadata.put("targetsMissing", missing);
            if (missing > 0) {
                metadata.put("missingTargets", String.join(",", missingTargets));
            }
        }
        return new PatternMatch(name(), cls.name, 1.0, metadata);
    }

    /**
     * Pulls the JVM internal names from a {@code @Mixin(value = {X.class, Y.class})}
     * annotation. Returns an empty list when {@code value} is absent or malformed.
     */
    private static List<String> extractMixinValueArray(AnnotationNode ann) {
        if (ann.values == null) return List.of();
        // values is a flat list of alternating (name, value) pairs; find "value".
        for (int i = 0; i + 1 < ann.values.size(); i += 2) {
            Object key = ann.values.get(i);
            Object val = ann.values.get(i + 1);
            if ("value".equals(key) && val instanceof List<?> list) {
                List<String> out = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Type t) {
                        if (t.getSort() == Type.OBJECT) {
                            out.add(t.getInternalName());
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
