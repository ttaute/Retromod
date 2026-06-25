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
 * Stand-in for old MixinExtras bootstrap/plugin/version classes that modern loaders bundle
 * and auto-initialize. {@link com.retromod.shim.api.common.MixinExtrasApiShim} rewrites
 * references to the old APIs onto this class and its inner {@code *Compat} types. The original
 * redirect targets were never created (#60), so {@code MixinExtrasBootstrap.init()} threw
 * {@link ClassNotFoundException}. Ships in Retromod's jar, not relocated.
 */
public final class MixinExtrasShim {

    private MixinExtrasShim() {}

    /** {@code MixinExtrasBootstrap.init()} target; the platform bootstraps MixinExtras now. */
    public static void noopInit() {
    }

    /** {@code MixinExtrasVersion.getVersion()} target; reports a recent version so old checks pass. */
    public static String getVersion() {
        return "0.5.0";
    }

    /** Stand-in for {@code MixinExtrasPlugin}: claims no mixins, applies nothing. */
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

    /** Stand-in for the internal {@code MixinExtrasService}; old code only needs it to exist. */
    public static final class ServiceCompat { }


    /** Stand-in for {@code MixinExtrasVersion}. */
    public static final class VersionCompat {
        public static String getVersion() {
            return MixinExtrasShim.getVersion();
        }
    }
}
