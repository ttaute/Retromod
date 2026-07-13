/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.3.0 track A: re-signature a mixin {@code @Inject} handler when the target MC method GAINED a
 * leading parameter the handler can ignore. Canonical case (#69, Revamped Phantoms): the 1.21.5
 * refactor changed {@code LivingEntity.doHurtTarget(Entity)} to {@code doHurtTarget(ServerLevel,
 * Entity)}, so a handler capturing {@code (Entity, CallbackInfoReturnable)} no longer matches the
 * target's parameter prefix and injection fails ("Scanned 0 target(s)").
 *
 * <p>The fix inserts the new leading param(s) into the handler descriptor and shifts every
 * local-variable slot in the body up by the inserted width, so the handler captures
 * {@code (ServerLevel, Entity, CallbackInfoReturnable)} - the ignored {@code ServerLevel} makes it a
 * valid prefix of the new target again, and the body logic is unchanged.
 *
 * <p><b>Safety:</b> this only DROPS stale stack-map frames; the caller MUST re-emit with
 * {@code COMPUTE_FRAMES} inside a try/catch and fall back to the un-re-signatured bytes if frame
 * computation throws, so a slot-shift bug can never ship a {@code VerifyError}-crashing handler. It
 * also declines conservatively: only for an explicit {@link #SIGNATURE_CHANGES} table entry, only
 * for a well-formed {@code @Inject} shape, and never when the captured params carry parameter
 * annotations ({@code @Local}/{@code @Coerce}), which frame verification would not catch.
 *
 * <p><b>Selector forms:</b> a <em>bare-name</em> selector ({@code method="doHurtTarget"}) is repaired
 * by re-signaturing the handler alone (Mixin scans by name, finds the new target, and the handler is
 * now a valid prefix again). A <em>name+descriptor</em> selector
 * ({@code method="doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z"}) also has its selector
 * descriptor rewritten to the new target signature (see {@link #rewriteSelectorDescriptor}), so both
 * forms resolve.
 */
public final class MixinHandlerResignature {

    private MixinHandlerResignature() {}

    /** A parameter inserted at {@code paramIndex} (0-based, over the descriptor params). */
    public record ParamInsert(int paramIndex, String typeDescriptor) {}

    private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CALLBACK_INFO_RETURNABLE = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    /**
     * A known signature change. {@code acceptableFirstParams} is the OWNER GUARD: the set of Mojang
     * first-parameter type descriptors the changed method actually takes. A bare-name match is only
     * accepted when the handler's first captured param is one of these (or is unverifiable - a
     * primitive, a non-MC type, or an intermediary name), so a same-named but UNCHANGED method on a
     * different class (e.g. {@code Mob.mobInteract} vs the changed {@code PiglinAi.mobInteract}) is
     * not wrongly re-signatured. {@code null} means no guard (permissive).
     */
    private record SigChange(Set<String> acceptableFirstParams, List<ParamInsert> inserts) {}

    /** Target method (bare Mojang name) -> its signature change. */
    private static final Map<String, SigChange> SIGNATURE_CHANGES = new HashMap<>();
    static {
        // The 1.21.5 "world/level threading" refactor prepended a ServerLevel to many
        // LivingEntity/Entity/Mob/Player methods (same name, leading ServerLevel@0). Harvested by
        // diffing the official 1.21.1 Mojang mappings against the 26.2 jar. A handler that captures
        // the old target params (e.g. the DamageSource on actuallyHurt) is otherwise no longer a
        // valid parameter prefix of the new target and fails to inject ("Scanned 0 target(s)"). Each
        // entry carries the acceptable first-param type(s) as an owner guard (see SigChange). Only
        // methods whose OLD signature had >=1 capturable param are listed (0-arg-old ones need no
        // re-signature: an empty capture is already a valid prefix). doHurtTarget is the #69 case.
        ParamInsert sl = new ParamInsert(0, "Lnet/minecraft/server/level/ServerLevel;");
        String DAMAGE = "Lnet/minecraft/world/damagesource/DamageSource;";
        String ENTITY = "Lnet/minecraft/world/entity/Entity;";
        String ITEMSTACK = "Lnet/minecraft/world/item/ItemStack;";
        // Core LivingEntity/Entity/Mob/Player methods (harvest pass 1).
        reg("doHurtTarget", sl, ENTITY);                                       // #69
        reg("actuallyHurt", sl, DAMAGE);
        reg("isInvulnerableTo", sl, DAMAGE);
        reg("dropExperience", sl, ENTITY);
        reg("dropFromLootTable", sl, DAMAGE);
        reg("triggerOnDeathMobEffects", sl, "Lnet/minecraft/world/entity/Entity$RemovalReason;");
        reg("spawnAtLocation", sl, ITEMSTACK, "Lnet/minecraft/world/level/ItemLike;");
        reg("pickUpItem", sl, "Lnet/minecraft/world/entity/item/ItemEntity;",
                "Lnet/minecraft/world/entity/Mob;", "Lnet/minecraft/world/entity/monster/piglin/Piglin;");
        reg("wantsToPickUp", sl, ITEMSTACK);
        reg("equipItemIfPossible", sl, ITEMSTACK);
        reg("dropPreservedEquipment", sl, "Ljava/util/function/Predicate;");
        reg("isPreventingPlayerRest", sl, "Lnet/minecraft/world/entity/player/Player;");
        // Entity-specific methods (harvest pass 2, broad 734-class scan). Lambdas, generic names
        // (hurt/destroy/mobInteract/...), and niche AI-framework statics (PiglinAi/Sensor/Raid) excluded.
        reg("tickLeash", sl, ENTITY);
        reg("playerDied", sl, "Lnet/minecraft/world/entity/player/Player;");
        reg("handleAirSupply", sl);   // first param is int (primitive): no object guard needed
        reg("onStopAttacking", sl, "Lnet/minecraft/world/entity/animal/axolotl/Axolotl;");
        reg("hurtAndThrowTarget", sl, "Lnet/minecraft/world/entity/LivingEntity;");
        reg("checkWalls", sl, "Lnet/minecraft/world/phys/AABB;");
        reg("onCrystalDestroyed", sl, "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;");
        reg("onDestroyedBy", sl, DAMAGE);
    }

    /** Restricting registration: the handler's Mojang-MC first captured param must be in {@code acceptableFirstParams}. */
    private static void reg(String name, ParamInsert insert, String... acceptableFirstParams) {
        SIGNATURE_CHANGES.put(name, new SigChange(Set.of(acceptableFirstParams), List.of(insert)));
    }

    /**
     * Register a target method's leading-parameter insertion(s) with NO owner guard (permissive).
     * Used by tests and external callers; the harvested entity entries use the guarded {@link #reg}.
     */
    public static void register(String targetMethodName, ParamInsert... inserts) {
        SIGNATURE_CHANGES.put(targetMethodName, new SigChange(null, List.of(inserts)));
    }

    /**
     * The parameter insertions for the target of {@code method}'s {@code @Inject}, or {@code null}
     * if the method is not an {@code @Inject} or its target has no known signature change.
     */
    static List<ParamInsert> injectSignatureChange(MethodNode method) {
        AnnotationNode inject = annotationOf(method, INJECT_DESC);
        if (inject == null || inject.values == null) return null;
        for (int i = 0; i + 1 < inject.values.size(); i += 2) {
            if (!"method".equals(inject.values.get(i))) continue;
            Object v = inject.values.get(i + 1);
            List<String> targets = new ArrayList<>();
            if (v instanceof List<?> l) { for (Object o : l) if (o instanceof String s) targets.add(s); }
            else if (v instanceof String s) targets.add(s);
            for (String t : targets) {
                SigChange sc = SIGNATURE_CHANGES.get(bareName(t));
                if (sc == null) continue;
                // Owner guard: skip a bare-name match whose captured params show the handler is
                // targeting a same-named but UNCHANGED method on another class.
                if (sc.acceptableFirstParams() != null
                        && !firstCapturedParamMatches(method, sc.acceptableFirstParams())) {
                    continue;
                }
                return sc.inserts();
            }
        }
        return null;
    }

    /**
     * Whether {@code method}'s first captured target-param (the arg before the CallbackInfo trailer)
     * is consistent with the changed method. Enforced only when that param is a genuine Mojang MC
     * type; permissive for a primitive/array, a non-MC type, or an intermediary ({@code class_N},
     * Fabric pre-remap) first param, none of which can be checked here (and on Fabric the intermediary
     * selector already pins the exact method).
     */
    private static boolean firstCapturedParamMatches(MethodNode method, Set<String> acceptable) {
        Type[] args = Type.getArgumentTypes(method.desc);
        int cb = callbackIndex(args);
        if (cb <= 0) return true;                         // no captured param before CallbackInfo
        Type first = args[0];
        if (first.getSort() != Type.OBJECT) return true;  // primitive/array first param
        String desc = first.getDescriptor();
        if (!isMojangMcType(desc)) return true;           // intermediary / java.* / mod type
        return acceptable.contains(desc);
    }

    /** A Mojang official MC type ({@code Lnet/minecraft/...}), excluding a Fabric intermediary {@code class_N}. */
    private static boolean isMojangMcType(String descriptor) {
        return descriptor.startsWith("Lnet/minecraft/") && !descriptor.contains("/class_");
    }

    /**
     * Whether {@code method} (an {@code @Inject} handler) captures at least one target parameter
     * before the {@code CallbackInfo} trailer. When it captures none, insertParams cannot and will
     * not re-signature it (an empty capture is already a valid prefix), so the caller must own the
     * desc-qualified selector rewrite instead of deferring it to insertParams.
     */
    private static boolean injectHandlerCapturesParams(MethodNode method) {
        if (method.desc == null) return false;
        return callbackIndex(Type.getArgumentTypes(method.desc)) > 0;
    }

    /** Index of the {@code CallbackInfo}/{@code CallbackInfoReturnable} trailer in {@code args}, or -1. */
    static int callbackIndex(Type[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].getSort() == Type.OBJECT) {
                String in = args[i].getInternalName();
                if (CALLBACK_INFO.equals(in) || CALLBACK_INFO_RETURNABLE.equals(in)) return i;
            }
        }
        return -1;
    }

    private static AnnotationNode annotationOf(MethodNode m, String desc) {
        for (List<AnnotationNode> anns : List.of(
                m.visibleAnnotations != null ? m.visibleAnnotations : List.<AnnotationNode>of(),
                m.invisibleAnnotations != null ? m.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode a : anns) if (desc.equals(a.desc)) return a;
        }
        return null;
    }

    /** "Lowner;name(desc)ret" / "name(desc)ret" / "name" -> bare "name". */
    static String bareName(String selector) {
        String s = selector;
        int paren = s.indexOf('(');
        if (paren >= 0) s = s.substring(0, paren);
        int semi = s.lastIndexOf(';');            // drop a leading "Lowner;" if present
        if (semi >= 0) s = s.substring(semi + 1);
        return s;
    }

    /**
     * Insert the given leading parameters into an {@code @Inject} handler and shift its body slots.
     * Mutates {@code handler} in place (dropping stale frames); returns {@code true} if applied.
     * Returns {@code false} (unchanged) when not applicable, so the caller keeps the original.
     */
    static boolean insertParams(MethodNode handler, List<ParamInsert> inserts) {
        if (inserts == null || inserts.isEmpty() || handler.desc == null) return false;

        Type[] args = Type.getArgumentTypes(handler.desc);
        Type ret = Type.getReturnType(handler.desc);

        // Find the CallbackInfo(Returnable) trailer; captured target params are the args before it.
        int cbIndex = callbackIndex(args);
        if (cbIndex < 0) return false;                     // not a standard @Inject shape

        for (ParamInsert ins : inserts) {                  // every insert must land in the captured region
            if (ins.paramIndex() < 0 || ins.paramIndex() > cbIndex) return false;
        }
        boolean anyBeforeCi = false;                       // handler must capture something that changed
        for (ParamInsert ins : inserts) if (ins.paramIndex() < cbIndex) { anyBeforeCi = true; break; }
        if (!anyBeforeCi) return false;

        // Decline if any parameter carries an annotation (@Local/@Coerce/@Share): insertRawParams
        // shifts slots and the LVT but not the param-annotation arrays, so an inserted leading param
        // leaves a @Local on the wrong index -> InvalidInjectionException (which COMPUTE_FRAMES can't
        // catch). Guard the full width, since @Local sits past the CallbackInfo trailer.
        if (hasParamAnnotations(handler.visibleParameterAnnotations, Integer.MAX_VALUE - 1)
                || hasParamAnnotations(handler.invisibleParameterAnnotations, Integer.MAX_VALUE - 1)) {
            return false;
        }

        if (!insertRawParams(handler, inserts)) return false;

        // A desc-qualified @Inject selector ("...doHurtTarget(L..Entity;)Z") still names the OLD target
        // signature; Mixin would scan 0 targets even after the handler is re-signatured. Rewrite the
        // selector descriptor to the new target signature too (a bare-name selector needs no change).
        rewriteInjectSelectors(handler);
        return true;
    }

    /**
     * Core insertion, no shape guards: insert params at the given absolute descriptor indexes,
     * shift every body slot and LVT entry, drop stale frames, rebuild the descriptor. The CALLER
     * is responsible for shape validation and for re-emitting with {@code COMPUTE_FRAMES}.
     */
    static boolean insertRawParams(MethodNode handler, List<ParamInsert> inserts) {
        if (inserts == null || inserts.isEmpty() || handler.desc == null) return false;
        Type[] args = Type.getArgumentTypes(handler.desc);
        Type ret = Type.getReturnType(handler.desc);
        for (ParamInsert ins : inserts) {
            if (ins.paramIndex() < 0 || ins.paramIndex() > args.length) return false;
        }

        boolean isStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
        int[] paramSlot = new int[args.length + 1];
        paramSlot[0] = isStatic ? 0 : 1;                   // slot 0 is `this` on an instance method
        for (int i = 0; i < args.length; i++) paramSlot[i + 1] = paramSlot[i] + args[i].getSize();

        List<ParamInsert> sorted = new ArrayList<>(inserts);
        sorted.sort(Comparator.comparingInt(ParamInsert::paramIndex));
        int[] insSlot = new int[sorted.size()], insWidth = new int[sorted.size()];
        for (int k = 0; k < sorted.size(); k++) {
            insSlot[k] = paramSlot[sorted.get(k).paramIndex()];
            insWidth[k] = Type.getType(sorted.get(k).typeDescriptor()).getSize();
        }

        // Shift every local-variable access and LVT entry; drop stale frames.
        List<AbstractInsnNode> frames = new ArrayList<>();
        for (AbstractInsnNode insn = handler.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof VarInsnNode v) v.var += shiftFor(v.var, insSlot, insWidth);
            else if (insn instanceof IincInsnNode ii) ii.var += shiftFor(ii.var, insSlot, insWidth);
            else if (insn instanceof FrameNode) frames.add(insn);
        }
        for (AbstractInsnNode f : frames) handler.instructions.remove(f);
        if (handler.localVariables != null) {
            for (LocalVariableNode lv : handler.localVariables) lv.index += shiftFor(lv.index, insSlot, insWidth);
        }

        // Rebuild the descriptor (insert high index first so lower indices stay valid).
        List<Type> newArgs = new ArrayList<>(Arrays.asList(args));
        for (int k = sorted.size() - 1; k >= 0; k--) {
            newArgs.add(sorted.get(k).paramIndex(), Type.getType(sorted.get(k).typeDescriptor()));
        }
        handler.desc = Type.getMethodDescriptor(ret, newArgs.toArray(new Type[0]));

        handler.parameters = null;                         // drop optional MethodParameters (names) to avoid a count mismatch
        int totalWidth = 0; for (int w : insWidth) totalWidth += w;
        handler.maxLocals += totalWidth;                   // COMPUTE_FRAMES recomputes, but keep it consistent
        return true;
    }

    /**
     * Rewrite each desc-qualified {@code @Inject method=} selector whose target has a known signature
     * change, inserting the new parameter(s) into the selector's descriptor so it matches the modern
     * (re-signatured) target. Bare-name selectors are left untouched (Mixin resolves those by name).
     */
    private static void rewriteInjectSelectors(MethodNode handler) {
        AnnotationNode inject = annotationOf(handler, INJECT_DESC);
        if (inject == null || inject.values == null) return;
        for (int i = 0; i + 1 < inject.values.size(); i += 2) {
            if (!"method".equals(inject.values.get(i))) continue;
            Object v = inject.values.get(i + 1);
            if (v instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> l = (List<Object>) v;
                for (int j = 0; j < l.size(); j++) {
                    if (l.get(j) instanceof String s) {
                        String r = rewriteSelectorDescriptor(s);
                        if (r != null) l.set(j, r);
                    }
                }
            } else if (v instanceof String s) {
                String r = rewriteSelectorDescriptor(s);
                if (r != null) inject.values.set(i + 1, r);
            }
        }
    }

    /**
     * Insert a table target's new leading param(s) into a {@code name(desc)ret} selector, returning
     * the rewritten selector, or {@code null} when it should be left as-is (bare name, unknown target,
     * malformed descriptor, or an out-of-range insert index).
     */
    static String rewriteSelectorDescriptor(String selector) {
        int paren = selector.indexOf('(');
        if (paren < 0) return null;                        // bare name: resolved by name, no rewrite
        SigChange sc = SIGNATURE_CHANGES.get(bareName(selector));
        if (sc == null) return null;
        List<ParamInsert> inserts = sc.inserts();
        String head = selector.substring(0, paren);        // "Lowner;name" or "name"
        // Owner scope: only VANILLA methods were re-signatured by the 1.21.5 refactor. A mod's own
        // class can declare a method with the same name and the old vanilla signature (a copied
        // idiom); its call sites still use the old descriptor, so rewriting a selector aimed at a
        // non-vanilla owner would break a working mixin.
        int semi = head.lastIndexOf(';');
        if (semi >= 0) {
            String owner = head.substring(0, semi);        // "Lnet/minecraft/..." expected
            if (!owner.startsWith("Lnet/minecraft/")) return null;
        }
        String methodDesc = selector.substring(paren);     // "(params)ret"
        Type[] args;
        Type ret;
        try {
            args = Type.getArgumentTypes(methodDesc);
            ret = Type.getReturnType(methodDesc);
        } catch (RuntimeException e) {
            return null;                                    // not a valid descriptor
        }
        // Only rewrite a descriptor that is verifiably the OLD signature. Two guards:
        // (a) idempotence: if the param at an insert position already IS the inserted type, the
        //     selector is already new-form (rewriting again would double-insert, e.g.
        //     (ServerLevel,ServerLevel,Entity)Z - a broken selector worse than the input);
        // (b) owner guard: when the change knows its old first-param types, the selector's first
        //     param must be one of them, so a same-named method with different params is untouched.
        for (ParamInsert ins : inserts) {
            int idx = ins.paramIndex();
            if (idx < args.length && args[idx].getDescriptor().equals(ins.typeDescriptor())) {
                return null;                                // already new-form
            }
        }
        Set<String> acceptable = sc.acceptableFirstParams();
        if (acceptable != null && !acceptable.isEmpty()) {
            if (args.length == 0 || !acceptable.contains(args[0].getDescriptor())) {
                return null;                                // not the known old signature
            }
        }
        List<Type> newArgs = new ArrayList<>(Arrays.asList(args));
        List<ParamInsert> sorted = new ArrayList<>(inserts);
        sorted.sort(Comparator.comparingInt(ParamInsert::paramIndex));
        for (int k = sorted.size() - 1; k >= 0; k--) {
            int idx = sorted.get(k).paramIndex();
            if (idx < 0 || idx > newArgs.size()) return null;
            newArgs.add(idx, Type.getType(sorted.get(k).typeDescriptor()));
        }
        return head + Type.getMethodDescriptor(ret, newArgs.toArray(new Type[0]));
    }

    /**
     * Injector annotations whose HANDLER signature does not mirror the {@code @At} target call's
     * argument list, so rewriting their {@code @At target} descriptor is safe on its own:
     * {@code @Inject} (handler mirrors the CONTAINING method), {@code @ModifyReturnValue} /
     * {@code @ModifyExpressionValue} (handler mirrors the expression's RETURN type, which the
     * table's leading-param inserts never change). For {@code @Redirect}/{@code @WrapOperation}/
     * {@code @ModifyArg(s)}-style injectors the handler mirrors the call's arguments, so rewriting
     * only the {@code @At} target would turn a soft "injection point not found" into a hard
     * {@code InvalidInjectionException} handler mismatch; those need a paired handler re-signature
     * (not yet built) and are skipped here.
     */
    private static final Set<String> AT_DRIFT_SAFE = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;");

    /**
     * Rewrite signature-drifted selectors (#69) in {@code method}'s injector annotations: the
     * {@code method=} selector and any {@code @At(INVOKE, target=...)} injection point (recursing
     * into {@code slice}), where the old-descriptor call site no longer exists on 26.x even though
     * the handler body is fine. Only descriptors passing {@link #rewriteSelectorDescriptor}'s
     * old-signature + idempotence guards are touched; the caller gates on a 1.21.5+ host.
     */
    public static boolean rewriteAnnotationDrift(MethodNode method) {
        boolean modified = false;
        for (List<AnnotationNode> anns : List.of(
                method.visibleAnnotations != null ? method.visibleAnnotations : List.<AnnotationNode>of(),
                method.invisibleAnnotations != null ? method.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode a : anns) {
                if (a.desc != null && (a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                        || a.desc.startsWith("Lcom/llamalad7/mixinextras/"))) {
                    // For @Inject, skip the top-level method= selector when the handler captures params:
                    // insertParams re-signatures and rewrites it itself, and rewriting eagerly would ship
                    // a new-form selector on a handler insertParams later declined (a @Local one). A
                    // zero-capture @Inject never reaches insertParams, so its selector must rewrite here.
                    // @ModifyReturnValue/@ModifyExpressionValue don't use insertParams: always rewrite.
                    boolean skipTopLevel = INJECT_DESC.equals(a.desc) && injectHandlerCapturesParams(method);
                    modified |= driftWalk(a, AT_DRIFT_SAFE.contains(a.desc), false, skipTopLevel);
                }
            }
        }
        return modified;
    }

    /**
     * Recursive walker: rewrite "method"/"target" selector strings, descend into nested annotations.
     * Inside an {@code @At} node the "target" key names the injection-point CALL, whose rewrite is
     * gated by {@code allowAtRewrite}; at the top level "method"/"target" name the CONTAINING method,
     * safe for every injector type.
     */
    private static boolean driftWalk(AnnotationNode a, boolean allowAtRewrite, boolean insideAt,
                                     boolean skipTopLevelSelector) {
        if (a.values == null) return false;
        boolean modified = false;
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            String key = (String) a.values.get(i);
            Object v = a.values.get(i + 1);
            // At the top level (insideAt=false) "method"/"target" name the CONTAINING method; a
            // @Inject skips those (skipTopLevelSelector, see rewriteAnnotationDrift). Inside an @At
            // node the "target" names the injection-point CALL, gated by allowAtRewrite.
            boolean selectorKey = ("method".equals(key) || "target".equals(key))
                    && (insideAt ? allowAtRewrite : !skipTopLevelSelector);
            if (selectorKey && v instanceof String s) {
                String r = rewriteSelectorDescriptor(s);
                if (r != null) { a.values.set(i + 1, r); modified = true; }
            } else if (v instanceof List<?> l) {
                List<Object> list = castList(l);
                for (int j = 0; j < list.size(); j++) {
                    Object o = list.get(j);
                    if (selectorKey && o instanceof String s) {
                        String r = rewriteSelectorDescriptor(s);
                        if (r != null) { list.set(j, r); modified = true; }
                    } else if (o instanceof AnnotationNode nested) {
                        modified |= driftWalk(nested, allowAtRewrite, insideAt || isAtNode(nested), skipTopLevelSelector);
                    }
                }
            } else if (v instanceof AnnotationNode nested) {
                modified |= driftWalk(nested, allowAtRewrite, insideAt || isAtNode(nested), skipTopLevelSelector);
            }
        }
        return modified;
    }

    private static boolean isAtNode(AnnotationNode a) {
        return "Lorg/spongepowered/asm/mixin/injection/At;".equals(a.desc);
    }

    /**
     * Injectors whose handler MIRRORS the {@code @At} target call's argument list: repairing a
     * drifted call site needs the paired handler re-signature below, not just the target rewrite.
     */
    private static final Set<String> CALL_MIRRORING = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");

    /**
     * A detected-but-not-yet-applied drift repair. Detection is mutation-free (safe in the
     * collection loop); {@code apply()} mutates and MUST run inside the caller's guarded
     * COMPUTE_FRAMES re-emit so any failure falls back to the pre-mutation snapshot.
     */
    interface DriftRepair {
        boolean apply();
    }

    /**
     * A drifted {@code @Redirect}/{@code @WrapOperation} call-site repair, detected but not yet
     * applied: rewrite {@code at.values[valueIndex]} to {@code newTarget} AND insert
     * {@code handlerInserts} into the handler (whose params mirror the call's receiver + args).
     */
    record RedirectDrift(MethodNode handler, AnnotationNode at, int valueIndex,
                         String newTarget, List<ParamInsert> handlerInserts) implements DriftRepair {

        /** Apply both halves. The caller re-emits with {@code COMPUTE_FRAMES} and owns the fallback. */
        @Override
        public boolean apply() {
            if (!insertRawParams(handler, handlerInserts)) return false;
            at.values.set(valueIndex, newTarget);          // only after the handler side succeeded
            return true;
        }
    }

    /**
     * A drifted {@code @Overwrite} repair: the method's name+descriptor must match a vanilla method
     * to overwrite, and the 1.21.5 refactor changed that descriptor, so the overwrite fails with a
     * hard {@code InvalidOverwriteException} (no {@code require} net applies to @Overwrite at all -
     * the truly "not even soft-failed" class). Re-signaturing the method (the inserted param is
     * unused by the body) makes it overwrite the modern method again.
     */
    record OverwriteDrift(MethodNode method, List<ParamInsert> inserts) implements DriftRepair {
        @Override
        public boolean apply() {
            return insertRawParams(method, inserts);
        }
    }

    private static final String OVERWRITE_DESC = "Lorg/spongepowered/asm/mixin/Overwrite;";

    /**
     * Detect a drifted {@code @Overwrite}: the method's own name is in the signature-change table,
     * its params match the KNOWN old signature (a non-empty first-param guard is required, so a
     * same-named mod method can't false-positive), and it is not already new-form. Mutation-free.
     */
    static List<DriftRepair> detectOverwriteDrift(MethodNode method) {
        AnnotationNode ow = annotationOf(method, OVERWRITE_DESC);
        if (ow == null) return List.of();
        SigChange sc = SIGNATURE_CHANGES.get(method.name);
        if (sc == null) return List.of();
        Set<String> acceptable = sc.acceptableFirstParams();
        if (acceptable == null || acceptable.isEmpty()) return List.of();  // need the strong guard here
        Type[] args = Type.getArgumentTypes(method.desc);
        if (args.length == 0 || !acceptable.contains(args[0].getDescriptor())) return List.of();
        for (ParamInsert ins : sc.inserts()) {                             // idempotence
            int idx = ins.paramIndex();
            if (idx < args.length && args[idx].getDescriptor().equals(ins.typeDescriptor())) return List.of();
        }
        if (hasParamAnnotations(method.visibleParameterAnnotations, Integer.MAX_VALUE - 1)
                || hasParamAnnotations(method.invisibleParameterAnnotations, Integer.MAX_VALUE - 1)) {
            return List.of();
        }
        return List.of(new OverwriteDrift(method, sc.inserts()));
    }

    /**
     * Detect drifted {@code @Redirect}/{@code @WrapOperation} call sites on {@code method} (the
     * "not even soft-failed" class: on a require-carrying NeoForge mixin this is a hard
     * {@code InvalidInjectionException}; elsewhere the feature goes silently inert). Detection is
     * mutation-free; the caller applies inside its guarded re-emit. Declines conservatively when
     * the handler's params don't provably mirror the call (receiver+args prefix for a virtual call,
     * args prefix for a static call) or when the handler carries ANY parameter annotations
     * (MixinExtras sugar arrays would misalign on insert).
     */
    static List<RedirectDrift> detectRedirectDrift(MethodNode method) {
        List<RedirectDrift> out = new ArrayList<>();
        for (List<AnnotationNode> anns : List.of(
                method.visibleAnnotations != null ? method.visibleAnnotations : List.<AnnotationNode>of(),
                method.invisibleAnnotations != null ? method.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode a : anns) {
                if (!CALL_MIRRORING.contains(a.desc) || a.values == null) continue;
                for (int i = 0; i + 1 < a.values.size(); i += 2) {
                    if (!"at".equals(a.values.get(i))) continue;
                    Object v = a.values.get(i + 1);
                    List<AnnotationNode> ats = new ArrayList<>();
                    if (v instanceof AnnotationNode one) ats.add(one);
                    else if (v instanceof List<?> l) { for (Object o : l) if (o instanceof AnnotationNode n) ats.add(n); }
                    // @Redirect/@WrapOperation take a SINGLE @At, so at most one drift repair per
                    // handler; stop at the first so a hypothetical multi-@At can't double-insert params.
                    for (AnnotationNode at : ats) {
                        RedirectDrift d = detectOneAt(method, at);
                        if (d != null) { out.add(d); break; }
                    }
                }
            }
        }
        return out;
    }

    private static RedirectDrift detectOneAt(MethodNode handler, AnnotationNode at) {
        if (at.values == null) return null;
        for (int j = 0; j + 1 < at.values.size(); j += 2) {
            if (!"target".equals(at.values.get(j)) || !(at.values.get(j + 1) instanceof String target)) continue;
            String newTarget = rewriteSelectorDescriptor(target);
            if (newTarget == null) return null;                        // not drifted (or guarded off)
            SigChange sc = SIGNATURE_CHANGES.get(bareName(target));
            if (sc == null) return null;
            // Handler params must provably mirror the OLD call: [receiver, oldArgs...] or [oldArgs...].
            int paren = target.indexOf('(');
            String head = target.substring(0, paren);
            int semi = head.lastIndexOf(';');
            String ownerDesc = semi >= 0 ? head.substring(0, semi + 1) : null;   // "Lowner;"
            Type[] oldArgs;
            try {
                oldArgs = Type.getArgumentTypes(target.substring(paren));
            } catch (RuntimeException e) {
                return null;
            }
            Type[] hArgs = Type.getArgumentTypes(handler.desc);
            int receiverOffset;
            if (ownerDesc != null && hArgs.length >= 1 + oldArgs.length
                    && hArgs[0].getDescriptor().equals(ownerDesc)
                    && argsMatch(hArgs, 1, oldArgs)) {
                receiverOffset = 1;                                    // virtual call: receiver captured
            } else if (hArgs.length >= oldArgs.length && argsMatch(hArgs, 0, oldArgs)) {
                receiverOffset = 0;                                    // static call
            } else {
                return null;                                           // shape not provable: leave alone
            }
            if (hasParamAnnotations(handler.visibleParameterAnnotations, Integer.MAX_VALUE - 1)
                    || hasParamAnnotations(handler.invisibleParameterAnnotations, Integer.MAX_VALUE - 1)) {
                return null;                                           // sugar arrays would misalign
            }
            List<ParamInsert> shifted = new ArrayList<>();
            for (ParamInsert ins : sc.inserts()) {
                shifted.add(new ParamInsert(ins.paramIndex() + receiverOffset, ins.typeDescriptor()));
            }
            return new RedirectDrift(handler, at, j + 1, newTarget, shifted);
        }
        return null;
    }

    private static boolean argsMatch(Type[] handlerArgs, int offset, Type[] callArgs) {
        for (int i = 0; i < callArgs.length; i++) {
            if (!handlerArgs[offset + i].getDescriptor().equals(callArgs[i].getDescriptor())) return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(List<?> l) {
        return (List<Object>) l;
    }

    private static boolean hasParamAnnotations(List<AnnotationNode>[] arr, int uptoInclusive) {
        if (arr == null) return false;
        for (int i = 0; i <= uptoInclusive && i < arr.length; i++) if (arr[i] != null && !arr[i].isEmpty()) return true;
        return false;
    }

    /** Total inserted width at slots at-or-below {@code slot} (the inserted param pushes it up). */
    private static int shiftFor(int slot, int[] insSlot, int[] insWidth) {
        int sh = 0;
        for (int k = 0; k < insSlot.length; k++) if (insSlot[k] <= slot) sh += insWidth[k];
        return sh;
    }
}
