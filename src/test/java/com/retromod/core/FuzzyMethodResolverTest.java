/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stack-safety guard for the fuzzy method resolver (#51): a name+arity match whose
 * parameter type changed incompatibly between MC versions must not be auto-applied,
 * since rewriting the call would emit bytecode that fails verification (VerifyError).
 * Case in point: {@code AnimationUtils.swingWeaponDown(...,Mob,...)} becoming
 * {@code (...,HumanoidArm,...)} in 1.21.11.
 */
class FuzzyMethodResolverTest {

    // No indexed JAR, so the class hierarchy is empty and isAncestor() always
    // returns false: the conservative "can't prove assignable" path.
    private final FuzzyMethodResolver r = new FuzzyMethodResolver();

    @Test
    @DisplayName("isTypeAssignable: identical / Object / primitive rules")
    void typeAssignableBasics() {
        assertTrue(r.isTypeAssignable("Lfoo/Bar;", "Lfoo/Bar;"), "identical reference types");
        assertTrue(r.isTypeAssignable("F", "F"), "identical primitives");
        assertTrue(r.isTypeAssignable("Lfoo/Bar;", "Ljava/lang/Object;"), "anything -> Object");
        assertFalse(r.isTypeAssignable("F", "I"), "different primitives are not stack-equal");
        assertFalse(r.isTypeAssignable("Lfoo/Bar;", "F"), "reference vs primitive");
        assertFalse(r.isTypeAssignable("F", "Lfoo/Bar;"), "primitive vs reference");
    }

    @Test
    @DisplayName("isTypeAssignable: unrelated reference types are NOT assignable (#51 Mob vs HumanoidArm)")
    void typeAssignableUnrelated() {
        assertFalse(r.isTypeAssignable(
                "Lnet/minecraft/world/entity/Mob;",
                "Lnet/minecraft/world/entity/HumanoidArm;"),
                "Mob is not a HumanoidArm - redirect would VerifyError");
    }

    @Test
    @DisplayName("isRedirectStackSafe: swingWeaponDown(...,Mob,...) -> (...,HumanoidArm,...) is suppressed")
    void redirectUnsafeOnIncompatibleParam() {
        String modelPart = "Lnet/minecraft/client/model/geom/ModelPart;";
        List<String> origParams = List.of(modelPart, modelPart,
                "Lnet/minecraft/world/entity/Mob;", "F", "F");
        String candDescriptor = "(" + modelPart + modelPart
                + "Lnet/minecraft/world/entity/HumanoidArm;FF)V";
        assertFalse(r.isRedirectStackSafe(origParams, "V", candDescriptor),
                "incompatible 3rd param must make the redirect unsafe");
    }

    @Test
    @DisplayName("isRedirectStackSafe: identical signature is safe; differing arity is not")
    void redirectSafeAndArity() {
        String modelPart = "Lnet/minecraft/client/model/geom/ModelPart;";
        List<String> sameParams = List.of(modelPart, "F");
        assertTrue(r.isRedirectStackSafe(sameParams, "V", "(" + modelPart + "F)V"),
                "identical signature is stack-safe");
        assertFalse(r.isRedirectStackSafe(sameParams, "V", "(" + modelPart + "FF)V"),
                "extra parameter (arity change) is unsafe");
    }
}
