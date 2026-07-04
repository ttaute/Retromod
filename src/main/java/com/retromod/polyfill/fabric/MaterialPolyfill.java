/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.polyfill.fabric;

/**
 * Stand-in for the removed {@code Material} class ({@code net.minecraft.class_3614}) on
 * pre-26.1 Fabric hosts. Minecraft 1.20 deleted the Material system outright, so ANY
 * reference from a pre-1.20 mod ({@code anewarray}, {@code getstatic}, a method signature)
 * dies {@code NoClassDefFoundError} at class load, killing the whole class (found live,
 * round 7 of the snapshot.8 acceptance pass: Collective's {@code GlobalVariables.<clinit>}
 * builds a {@code List<Material>} of surface materials).
 *
 * <p>{@link com.retromod.shim.fabric.Pre1_20MaterialBridge} class-redirects
 * {@code class_3614} here, so every reference resolves against a REAL class on the knot
 * classpath (Fabric has no module boundaries; the same pattern as the FabricBlockSettings
 * bridge). The 44 material constants (harvested from the 1.16.5 intermediary mappings) are
 * distinct singletons, so identity comparisons and {@code List.contains} behave.
 *
 * <p><b>Staged semantics:</b> {@code BlockState.getMaterial()} ({@code method_26207}) is
 * devirtualized to {@link #fromState}, which currently returns a dedicated {@link #UNKNOWN}
 * instance that equals no constant: material-membership checks quietly report false (the
 * feature no-ops) instead of crashing. A curated block-to-material table can upgrade this
 * later without touching the mods again. The boolean surface uses the conservative defaults
 * below; {@code method_15798}/{@code method_15803} (piston behavior / map color, return
 * types that can't be declared here since this class compiles without Minecraft) are
 * redirected by the bridge to {@link #nullMaterialProperty} at the call site.
 */
public final class MaterialPolyfill {

    // The 44 material constants of 1.16.5 (intermediary field ids, so a remapped
    // GETSTATIC resolves directly against this class).
    public static final MaterialPolyfill field_15913 = new MaterialPolyfill("field_15913");
    public static final MaterialPolyfill field_15914 = new MaterialPolyfill("field_15914");
    public static final MaterialPolyfill field_15915 = new MaterialPolyfill("field_15915");
    public static final MaterialPolyfill field_15916 = new MaterialPolyfill("field_15916");
    public static final MaterialPolyfill field_15917 = new MaterialPolyfill("field_15917");
    public static final MaterialPolyfill field_15918 = new MaterialPolyfill("field_15918");
    public static final MaterialPolyfill field_15919 = new MaterialPolyfill("field_15919");
    public static final MaterialPolyfill field_15920 = new MaterialPolyfill("field_15920");
    public static final MaterialPolyfill field_15921 = new MaterialPolyfill("field_15921");
    public static final MaterialPolyfill field_15922 = new MaterialPolyfill("field_15922");
    public static final MaterialPolyfill field_15923 = new MaterialPolyfill("field_15923");
    public static final MaterialPolyfill field_15924 = new MaterialPolyfill("field_15924");
    public static final MaterialPolyfill field_15925 = new MaterialPolyfill("field_15925");
    public static final MaterialPolyfill field_15926 = new MaterialPolyfill("field_15926");
    public static final MaterialPolyfill field_15927 = new MaterialPolyfill("field_15927");
    public static final MaterialPolyfill field_15928 = new MaterialPolyfill("field_15928");
    public static final MaterialPolyfill field_15930 = new MaterialPolyfill("field_15930");
    public static final MaterialPolyfill field_15931 = new MaterialPolyfill("field_15931");
    public static final MaterialPolyfill field_15932 = new MaterialPolyfill("field_15932");
    public static final MaterialPolyfill field_15933 = new MaterialPolyfill("field_15933");
    public static final MaterialPolyfill field_15934 = new MaterialPolyfill("field_15934");
    public static final MaterialPolyfill field_15935 = new MaterialPolyfill("field_15935");
    public static final MaterialPolyfill field_15936 = new MaterialPolyfill("field_15936");
    public static final MaterialPolyfill field_15937 = new MaterialPolyfill("field_15937");
    public static final MaterialPolyfill field_15938 = new MaterialPolyfill("field_15938");
    public static final MaterialPolyfill field_15941 = new MaterialPolyfill("field_15941");
    public static final MaterialPolyfill field_15942 = new MaterialPolyfill("field_15942");
    public static final MaterialPolyfill field_15943 = new MaterialPolyfill("field_15943");
    public static final MaterialPolyfill field_15945 = new MaterialPolyfill("field_15945");
    public static final MaterialPolyfill field_15946 = new MaterialPolyfill("field_15946");
    public static final MaterialPolyfill field_15947 = new MaterialPolyfill("field_15947");
    public static final MaterialPolyfill field_15948 = new MaterialPolyfill("field_15948");
    public static final MaterialPolyfill field_15949 = new MaterialPolyfill("field_15949");
    public static final MaterialPolyfill field_15952 = new MaterialPolyfill("field_15952");
    public static final MaterialPolyfill field_15953 = new MaterialPolyfill("field_15953");
    public static final MaterialPolyfill field_15954 = new MaterialPolyfill("field_15954");
    public static final MaterialPolyfill field_15955 = new MaterialPolyfill("field_15955");
    public static final MaterialPolyfill field_15956 = new MaterialPolyfill("field_15956");
    public static final MaterialPolyfill field_15957 = new MaterialPolyfill("field_15957");
    public static final MaterialPolyfill field_15958 = new MaterialPolyfill("field_15958");
    public static final MaterialPolyfill field_15959 = new MaterialPolyfill("field_15959");
    public static final MaterialPolyfill field_17008 = new MaterialPolyfill("field_17008");
    public static final MaterialPolyfill field_22223 = new MaterialPolyfill("field_22223");
    public static final MaterialPolyfill field_26708 = new MaterialPolyfill("field_26708");

    /** What {@link #fromState} returns: identical to no constant, so membership checks no-op. */
    public static final MaterialPolyfill UNKNOWN = new MaterialPolyfill("unknown");

    private final String name;

    private MaterialPolyfill(String name) {
        this.name = name;
    }

    // The boolean surface of 1.16.5 Material (intermediary ids). Conservative defaults:
    // most materials are solid, block movement, and are neither liquid nor replaceable.
    public boolean method_15797() { return false; } // isLiquid
    public boolean method_15799() { return true; }  // isSolid
    public boolean method_15800() { return true; }  // blocksMovement
    public boolean method_15801() { return false; } // isBurnable
    public boolean method_15802() { return true; }  // blocksLight
    public boolean method_15804() { return false; } // isReplaceable

    /**
     * Devirtualized target of {@code BlockState.getMaterial()} ({@code method_26207}).
     * Staged: no block-to-material mapping yet, every state reports {@link #UNKNOWN}.
     */
    public static MaterialPolyfill fromState(Object blockState) {
        return UNKNOWN;
    }

    /**
     * Devirtualized target for the Material methods whose 1.16.5 return types
     * ({@code class_3619} piston behavior, {@code class_3620} color) can't be declared in
     * plain Java here. The redirect machinery CHECKCASTs the null back to the original
     * return type at the call site, which passes for null without loading the class.
     */
    public static Object nullMaterialProperty(Object self) {
        return null;
    }

    @Override
    public String toString() {
        return "MaterialPolyfill[" + name + "]";
    }
}
