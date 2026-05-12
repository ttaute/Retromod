/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.annotation;

import com.retromod.core.RetromodTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for annotation changes in Minecraft 26.1 and across mod loaders.
 *
 * Minecraft 26.1 migrated from javax.annotation (JSR 305) to jspecify
 * annotations for nullability. Additionally, Google Guava's VisibleForTesting
 * was replaced by the JetBrains equivalent.
 *
 * For mod loader annotations:
 * - Forge's @OnlyIn was removed in NeoForge (dist markers handled differently)
 * - Forge's Dist enum moved to the NeoForge package
 *
 * Covered changes:
 * - javax.annotation.Nullable -> org.jspecify.annotations.Nullable
 * - javax.annotation.Nonnull -> org.jspecify.annotations.NonNull
 * - com.google.common.annotations.VisibleForTesting -> org.jetbrains.annotations.VisibleForTesting
 * - net.minecraftforge.api.distmarker.OnlyIn -> removed (NeoForge doesn't need it)
 * - net.minecraftforge.api.distmarker.Dist -> net.neoforged.api.distmarker.Dist
 */
public class AnnotationPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Annotation Migration (JSR 305 -> JSpecify, Forge -> NeoForge)";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            // JSR 305 nullability annotations replaced by jspecify
            "javax/annotation/Nullable",
            "javax/annotation/Nonnull",

            // Guava annotation replaced by JetBrains
            "com/google/common/annotations/VisibleForTesting",

            // Forge dist marker annotations (removed/relocated in NeoForge)
            "net/minecraftforge/api/distmarker/OnlyIn",
            "net/minecraftforge/api/distmarker/Dist"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // No embedded stubs needed — pure annotation class redirects.
        // The replacement annotation libraries (jspecify, jetbrains-annotations)
        // are bundled with Minecraft 26.1.
        return new String[]{};
    }

    @Override
    public void registerPolyfills(RetromodTransformer transformer) {
        // =====================================================================
        // JSR 305 -> JSpecify migration (Minecraft 26.1)
        // Mojang moved from javax.annotation (FindBugs/JSR 305) nullability
        // annotations to the newer JSpecify standard annotations. JSpecify is
        // bundled with MC 26.1.
        // =====================================================================

        transformer.registerClassRedirect(
            "javax/annotation/Nullable",
            "org/jspecify/annotations/Nullable");

        transformer.registerClassRedirect(
            "javax/annotation/Nonnull",
            "org/jspecify/annotations/NonNull");

        // javax.annotation.concurrent.Immutable is NOT redirected — it still
        // exists in the javax.annotation package and is unaffected by the
        // jspecify migration.

        // =====================================================================
        // Guava VisibleForTesting -> JetBrains VisibleForTesting
        // Minecraft 26.1 uses the JetBrains annotation instead of Guava's.
        // Both have identical semantics — marks methods that are more visible
        // than necessary solely for testing purposes.
        // =====================================================================

        transformer.registerClassRedirect(
            "com/google/common/annotations/VisibleForTesting",
            "org/jetbrains/annotations/VisibleForTesting");

        // =====================================================================
        // Forge @OnlyIn / Dist -> NeoForge equivalents
        //
        // @OnlyIn was Forge's side-stripping annotation (similar to @Environment
        // in Fabric). NeoForge removed @OnlyIn entirely — the annotation is
        // simply stripped from bytecode. We redirect to java.lang.annotation.
        // Retention as a harmless no-op annotation that won't cause CNFE.
        //
        // Dist enum was relocated from net.minecraftforge to net.neoforged.
        // =====================================================================

        // @OnlyIn -> harmless no-op (annotation is stripped, not enforced)
        transformer.registerClassRedirect(
            "net/minecraftforge/api/distmarker/OnlyIn",
            "java/lang/annotation/Retention");

        // Dist enum relocation
        transformer.registerClassRedirect(
            "net/minecraftforge/api/distmarker/Dist",
            "net/neoforged/api/distmarker/Dist");

        // =====================================================================
        // Additional Forge annotation relocations
        // These annotations moved from net.minecraftforge to net.neoforged
        // during the Forge -> NeoForge transition.
        // =====================================================================

        transformer.registerClassRedirect(
            "net/minecraftforge/api/distmarker/OnlyIns",
            "java/lang/annotation/Retention");

        // Handle the common case where mods import Nullable/Nonnull via
        // the org.jetbrains.annotations path (some mods use JetBrains
        // annotations directly). These already exist in 26.1 so no redirect
        // is needed, but we handle the edu.umd.cs.findbugs path which some
        // old Forge mods used.
        transformer.registerClassRedirect(
            "edu/umd/cs/findbugs/annotations/Nullable",
            "org/jspecify/annotations/Nullable");

        transformer.registerClassRedirect(
            "edu/umd/cs/findbugs/annotations/NonNull",
            "org/jspecify/annotations/NonNull");

        transformer.registerClassRedirect(
            "edu/umd/cs/findbugs/annotations/SuppressFBWarnings",
            "java/lang/SuppressWarnings");
    }
}
