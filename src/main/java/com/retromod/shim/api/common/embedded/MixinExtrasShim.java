/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.common.embedded;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Compatibility stand-in for old MixinExtras bootstrap/plugin/version classes.
 *
 * <p>{@link com.retromod.shim.api.common.MixinExtrasApiShim} rewrites references to
 * the old MixinExtras APIs onto this class - {@code MixinExtrasBootstrap.init()} →
 * {@link #noopInit()}, {@code MixinExtrasVersion.getVersion()} → {@link #getVersion()},
 * and the {@code MixinExtrasPlugin}/{@code MixinExtrasService}/{@code MixinExtrasVersion}
 * <em>types</em> → the inner {@code *Compat} classes. Modern loaders bundle and
 * auto-initialize MixinExtras, so the behaviour these once provided is now handled by
 * the platform; the shim only has to exist and be harmless.
 *
 * <p><b>Why this file exists (#60):</b> those redirect <em>targets were never created</em>
 * - the redirects pointed at a class that didn't exist in any build, so when an old mod
 * (e.g. one that explicitly called {@code MixinExtrasBootstrap.init()} from its mixin
 * plugin) was transformed, the rewritten call hit a {@link ClassNotFoundException}. This
 * provides the missing class. It lives in Retromod's own jar (resolved via the classpath
 * on Fabric, and via Retromod's module on NeoForge/Forge); it is not relocated.
 */
public final class MixinExtrasShim {

    private MixinExtrasShim() {}

    /** Old mods called {@code MixinExtrasBootstrap.init()} from their mixin plugin; modern
     *  loaders initialize MixinExtras automatically, so this is a no-op. */
    public static void noopInit() {
        // intentionally empty - MixinExtras is bootstrapped by the platform now
    }

    /** Stand-in for {@code MixinExtrasVersion.getVersion()} - report a recent bundled
     *  version so old compatibility checks pass. */
    public static String getVersion() {
        return "0.5.0";
    }

    /**
     * Stands in for the old {@code MixinExtrasPlugin} (an {@link IMixinConfigPlugin} a mod
     * might still name in its mixin config). All methods are inert: it claims no mixins and
     * applies nothing, so it loads cleanly and does no harm.
     */
    public static final class PluginCompat implements IMixinConfigPlugin {
        @Override public void onLoad(String mixinPackage) { }
        @Override public String getRefMapperConfig() { return null; }
        @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
        @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
        @Override public List<String> getMixins() { return Collections.emptyList(); }
        @Override public void preApply(String targetClassName, ClassNode targetClass,
                                       String mixinClassName, IMixinInfo mixinInfo) { }
        @Override public void postApply(String targetClassName, ClassNode targetClass,
                                        String mixinClassName, IMixinInfo mixinInfo) { }
    }

    /** Stand-in for the internal {@code MixinExtrasService} - existence is all old code needs. */
    public static final class ServiceCompat { }

    /** Stand-in for {@code MixinExtrasVersion} - existence plus a version string. */
    public static final class VersionCompat {
        public static String getVersion() {
            return MixinExtrasShim.getVersion();
        }
    }
}
