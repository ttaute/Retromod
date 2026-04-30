/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.polyfill.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Polyfill for the MC 1.21+ "registry-key migration" of vanilla content
 * holders.
 *
 * <p>Older MC versions had {@code Enchantments.SHARPNESS} typed as
 * {@code Enchantment} — a direct reference to the actual instance. In MC
 * 1.21+ the same field name still exists but its type changed to
 * {@code ResourceKey<Enchantment>} (just a registry key, not the value).
 * The same migration happened to {@code MobEffects.*} (= yarn
 * {@code StatusEffects.*}). Mods compiled against the old shape do
 * {@code GETSTATIC Enchantments.SHARPNESS:Enchantment} and crash with
 * {@code NoSuchFieldError} on the new MC.
 *
 * <p>Fix: register field-to-method redirects so the bytecode call becomes
 * {@code INVOKESTATIC RegistryRefLookup.SHARPNESS():Enchantment}. The
 * static method does the runtime lookup through {@code BuiltInRegistries}
 * and returns the actual {@code Enchantment} the mod expected. Result is
 * cached after the first call so subsequent accesses are a hash-map hit.
 *
 * <p>Implementation uses reflection because RetroMod doesn't compile against
 * Minecraft. Each lookup method delegates to the generic
 * {@link #lookup(String, String)} helper which:
 * <ol>
 *   <li>Resolves {@code BuiltInRegistries} via {@link Class#forName}</li>
 *   <li>Reads the static field for the chosen registry
 *       ({@code ENCHANTMENT}, {@code MOB_EFFECT})</li>
 *   <li>Builds a {@code ResourceLocation} for {@code minecraft:<name>}</li>
 *   <li>Calls {@code Registry.get(ResourceLocation)} (or {@code .getValue}
 *       on newer MC) and returns the result</li>
 * </ol>
 *
 * <p>The transformer's CHECKCAST emit on field-to-method redirects converts
 * the {@code Object} return type back to whatever the original field's type
 * was, so the consuming bytecode verifies cleanly.
 *
 * <p>Adding a new entry is one method here plus one
 * {@code registerFieldRedirect} call in the appropriate shim class — see
 * {@code Fabric_1_21_11_to_26_1.java} for examples.
 */
public final class RegistryRefLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-RegistryLookup");

    private static final String ENCHANTMENT_REGISTRY = "ENCHANTMENT";
    private static final String MOB_EFFECT_REGISTRY  = "MOB_EFFECT";

    /** Cache: "<registryName>:<entryName>" -> resolved value (or NULL_SENTINEL on miss). */
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    private static final Object NULL_SENTINEL = new Object();

    /** Resolved BuiltInRegistries class, cached after first successful lookup. */
    private static volatile Class<?> builtInRegistriesClass;
    /** Resolved ResourceLocation class, cached after first successful lookup. */
    private static volatile Class<?> resourceLocationClass;
    /** Cached registry instances keyed by registry-field-name. */
    private static final Map<String, Object> REGISTRIES = new ConcurrentHashMap<>();

    private RegistryRefLookup() {}

    // =====================================================================
    // ENCHANTMENTS — public static methods that return Object (CHECKCAST'd
    // to Enchantment by the transformer's field-to-method redirect).
    // =====================================================================

    public static Object SHARPNESS()           { return enchant("sharpness"); }
    public static Object SMITE()               { return enchant("smite"); }
    public static Object BANE_OF_ARTHROPODS()  { return enchant("bane_of_arthropods"); }
    public static Object KNOCKBACK()           { return enchant("knockback"); }
    public static Object FIRE_ASPECT()         { return enchant("fire_aspect"); }
    public static Object LOOTING()             { return enchant("looting"); }
    public static Object SWEEPING_EDGE()       { return enchant("sweeping_edge"); }
    public static Object PROTECTION()          { return enchant("protection"); }
    public static Object FIRE_PROTECTION()     { return enchant("fire_protection"); }
    public static Object FEATHER_FALLING()     { return enchant("feather_falling"); }
    public static Object BLAST_PROTECTION()    { return enchant("blast_protection"); }
    public static Object PROJECTILE_PROTECTION() { return enchant("projectile_protection"); }
    public static Object RESPIRATION()         { return enchant("respiration"); }
    public static Object AQUA_AFFINITY()       { return enchant("aqua_affinity"); }
    public static Object THORNS()              { return enchant("thorns"); }
    public static Object DEPTH_STRIDER()       { return enchant("depth_strider"); }
    public static Object FROST_WALKER()        { return enchant("frost_walker"); }
    public static Object BINDING_CURSE()       { return enchant("binding_curse"); }
    public static Object SOUL_SPEED()          { return enchant("soul_speed"); }
    public static Object SWIFT_SNEAK()         { return enchant("swift_sneak"); }
    public static Object EFFICIENCY()          { return enchant("efficiency"); }
    public static Object SILK_TOUCH()          { return enchant("silk_touch"); }
    public static Object UNBREAKING()          { return enchant("unbreaking"); }
    public static Object FORTUNE()             { return enchant("fortune"); }
    public static Object POWER()               { return enchant("power"); }
    public static Object PUNCH()               { return enchant("punch"); }
    public static Object FLAME()               { return enchant("flame"); }
    public static Object INFINITY()            { return enchant("infinity"); }
    public static Object LUCK_OF_THE_SEA()     { return enchant("luck_of_the_sea"); }
    public static Object LURE()                { return enchant("lure"); }
    public static Object LOYALTY()             { return enchant("loyalty"); }
    public static Object IMPALING()            { return enchant("impaling"); }
    public static Object RIPTIDE()             { return enchant("riptide"); }
    public static Object CHANNELING()          { return enchant("channeling"); }
    public static Object MULTISHOT()           { return enchant("multishot"); }
    public static Object QUICK_CHARGE()        { return enchant("quick_charge"); }
    public static Object PIERCING()            { return enchant("piercing"); }
    public static Object MENDING()             { return enchant("mending"); }
    public static Object VANISHING_CURSE()     { return enchant("vanishing_curse"); }

    // =====================================================================
    // STATUS EFFECTS / MOB EFFECTS
    // (yarn name on the left as the comment, mojang ID on the right)
    // =====================================================================

    public static Object SPEED()               { return mobEffect("speed"); }              // yarn SPEED
    public static Object SLOWNESS()            { return mobEffect("slowness"); }
    public static Object HASTE()               { return mobEffect("haste"); }              // yarn HASTE
    public static Object MINING_FATIGUE()      { return mobEffect("mining_fatigue"); }
    public static Object STRENGTH()            { return mobEffect("strength"); }           // yarn STRENGTH
    public static Object INSTANT_HEALTH()      { return mobEffect("instant_health"); }
    public static Object INSTANT_DAMAGE()      { return mobEffect("instant_damage"); }
    public static Object JUMP_BOOST()          { return mobEffect("jump_boost"); }
    public static Object NAUSEA()              { return mobEffect("nausea"); }
    public static Object REGENERATION()        { return mobEffect("regeneration"); }
    public static Object RESISTANCE()          { return mobEffect("resistance"); }
    public static Object FIRE_RESISTANCE()     { return mobEffect("fire_resistance"); }
    public static Object WATER_BREATHING()     { return mobEffect("water_breathing"); }
    public static Object INVISIBILITY()        { return mobEffect("invisibility"); }
    public static Object BLINDNESS()           { return mobEffect("blindness"); }
    public static Object NIGHT_VISION()        { return mobEffect("night_vision"); }
    public static Object HUNGER()              { return mobEffect("hunger"); }
    public static Object WEAKNESS()            { return mobEffect("weakness"); }
    public static Object POISON()              { return mobEffect("poison"); }
    public static Object WITHER()              { return mobEffect("wither"); }
    public static Object HEALTH_BOOST()        { return mobEffect("health_boost"); }
    public static Object ABSORPTION()          { return mobEffect("absorption"); }
    public static Object SATURATION()          { return mobEffect("saturation"); }
    public static Object GLOWING()             { return mobEffect("glowing"); }
    public static Object LEVITATION()          { return mobEffect("levitation"); }
    public static Object LUCK()                { return mobEffect("luck"); }
    public static Object UNLUCK()              { return mobEffect("unluck"); }
    public static Object SLOW_FALLING()        { return mobEffect("slow_falling"); }
    public static Object CONDUIT_POWER()       { return mobEffect("conduit_power"); }
    public static Object DOLPHINS_GRACE()      { return mobEffect("dolphins_grace"); }
    public static Object BAD_OMEN()            { return mobEffect("bad_omen"); }
    public static Object HERO_OF_THE_VILLAGE() { return mobEffect("hero_of_the_village"); }
    public static Object DARKNESS()            { return mobEffect("darkness"); }

    // =====================================================================
    // INTERNALS
    // =====================================================================

    private static Object enchant(String entryName) {
        return lookup(ENCHANTMENT_REGISTRY, entryName);
    }

    private static Object mobEffect(String entryName) {
        return lookup(MOB_EFFECT_REGISTRY, entryName);
    }

    /**
     * Generic registry lookup. Cached. Returns {@code null} on lookup miss
     * (mirrors {@code Registry.get} behavior on miss). The transformer
     * emits a {@code CHECKCAST} after this call which would NPE on a real
     * miss anyway, so callers can rely on non-null on success.
     */
    private static Object lookup(String registryName, String entryName) {
        String cacheKey = registryName + ":" + entryName;
        Object cached = CACHE.get(cacheKey);
        if (cached == NULL_SENTINEL) return null;
        if (cached != null) return cached;

        Object value = doLookup(registryName, entryName);
        CACHE.put(cacheKey, value != null ? value : NULL_SENTINEL);
        return value;
    }

    private static Object doLookup(String registryName, String entryName) {
        Class<?> rl;
        try {
            rl = resourceLocationClass;
            if (rl == null) {
                rl = Class.forName("net.minecraft.resources.ResourceLocation");
                resourceLocationClass = rl;
            }
        } catch (Throwable t) {
            return null;
        }

        Object id = buildResourceLocation(rl, entryName);
        if (id == null) return null;

        // Path 1: static registry on BuiltInRegistries — works for things
        // that stayed hardcoded (Block, Item, EntityType, SoundEvent, etc.).
        Object value = lookupViaBuiltInRegistries(registryName, rl, id);
        if (value != null) return value;

        // Path 2: dynamic registry via the client's RegistryAccess. In MC
        // 1.21+ enchantments are data-driven; in 1.21.6+ mob effects are
        // data-driven too. They're not on BuiltInRegistries anymore — they
        // live in the dynamic registry that's populated when the client
        // connects to a server (or loads a world). At plain client-init
        // time this returns null because no connection/world exists yet,
        // but real mod code that runs during gameplay will hit the cache
        // miss once and then succeed for every subsequent call.
        return lookupViaDynamicRegistry(registryName, rl, id);
    }

    private static Object lookupViaBuiltInRegistries(String registryName, Class<?> rl, Object id) {
        try {
            Class<?> bir = builtInRegistriesClass;
            if (bir == null) {
                bir = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                builtInRegistriesClass = bir;
            }
            Object registry = REGISTRIES.computeIfAbsent("BIR:" + registryName, k -> {
                try {
                    return builtInRegistriesClass.getField(registryName).get(null);
                } catch (Throwable t) {
                    return NULL_SENTINEL;
                }
            });
            if (registry == NULL_SENTINEL || registry == null) return null;

            Method getter = findRegistryGetter(registry.getClass(), rl);
            if (getter == null) return null;
            return getter.invoke(registry, id);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object lookupViaDynamicRegistry(String registryName, Class<?> rl, Object id) {
        try {
            // Get the Minecraft client singleton.
            Class<?> mcCls = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcCls.getMethod("getInstance").invoke(null);
            if (mc == null) return null;

            // Try level().registryAccess() first (works once a world loads).
            Object registryAccess = null;
            try {
                Object level = mcCls.getMethod("level").invoke(mc);
                if (level != null) {
                    Method ra = level.getClass().getMethod("registryAccess");
                    registryAccess = ra.invoke(level);
                }
            } catch (Throwable ignored) {}

            // Fall back to getConnection().registryAccess() (works as soon as
            // the client has connected to a server).
            if (registryAccess == null) {
                try {
                    Object conn = mcCls.getMethod("getConnection").invoke(mc);
                    if (conn != null) {
                        registryAccess = conn.getClass().getMethod("registryAccess").invoke(conn);
                    }
                } catch (Throwable ignored) {}
            }

            if (registryAccess == null) return null;

            // Get the ResourceKey<? extends Registry<?>> for this registry.
            Class<?> registriesCls = Class.forName("net.minecraft.core.registries.Registries");
            Object registryKey;
            try {
                registryKey = registriesCls.getField(registryName).get(null);
            } catch (Throwable t) {
                LOGGER.debug("Registries.{} not found (data-driven registry key missing)", registryName);
                return null;
            }
            if (registryKey == null) return null;

            // registryAccess.lookup(ResourceKey) -> Optional<HolderLookup.RegistryLookup<T>>
            // Or registryAccess.registryOrThrow(ResourceKey) -> Registry<T> on older
            // RegistryAccess shapes. Try the modern API first.
            Object registry = invokeOptional(registryAccess, "lookup", registryKey);
            if (registry == null) {
                registry = invokeUnwrap(registryAccess, "registryOrThrow", registryKey);
            }
            if (registry == null) return null;

            // Now do the value lookup. HolderLookup has getValue(ResourceLocation)
            // or get(ResourceKey<T>); Registry has getValue/get(ResourceLocation).
            Method getter = findRegistryGetter(registry.getClass(), rl);
            if (getter != null) {
                Object result = getter.invoke(registry, id);
                // If we got a Holder, unwrap to its value.
                return unwrapHolder(result);
            }
            return null;
        } catch (Throwable t) {
            LOGGER.debug("Dynamic-registry lookup failed for {}.{}: {}",
                    registryName, id, t.getMessage());
            return null;
        }
    }

    /** Try a method that returns Optional&lt;T&gt;; unwrap if present. */
    private static Object invokeOptional(Object receiver, String name, Object arg) {
        try {
            for (Method m : receiver.getClass().getMethods()) {
                if (!name.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isInstance(arg)) continue;
                Object r = m.invoke(receiver, arg);
                if (r instanceof java.util.Optional<?> opt) {
                    return opt.isPresent() ? opt.get() : null;
                }
                return r;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Try a method that returns T directly (or throws if absent). */
    private static Object invokeUnwrap(Object receiver, String name, Object arg) {
        try {
            for (Method m : receiver.getClass().getMethods()) {
                if (!name.equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isInstance(arg)) continue;
                return m.invoke(receiver, arg);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** If {@code value} is a {@code Holder} or {@code Holder.Reference}, return its value. */
    private static Object unwrapHolder(Object value) {
        if (value == null) return null;
        try {
            // Holder.value() returns the underlying T
            Method valueMethod = value.getClass().getMethod("value");
            return valueMethod.invoke(value);
        } catch (Throwable ignored) {
            return value;
        }
    }

    private static Object buildResourceLocation(Class<?> rl, String path) {
        // Newer MC: ResourceLocation.parse("minecraft:<path>")
        try {
            Method parse = rl.getMethod("parse", String.class);
            return parse.invoke(null, "minecraft:" + path);
        } catch (Throwable ignored) {}
        // Older MC: ResourceLocation.fromNamespaceAndPath("minecraft", path)
        try {
            Method fromNs = rl.getMethod("fromNamespaceAndPath", String.class, String.class);
            return fromNs.invoke(null, "minecraft", path);
        } catch (Throwable ignored) {}
        // Oldest fallback: the public constructor (deprecated in newer MC)
        try {
            return rl.getConstructor(String.class, String.class)
                     .newInstance("minecraft", path);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method findRegistryGetter(Class<?> registryClass, Class<?> rl) {
        // Newer MC: getValue(ResourceLocation) -> T
        for (Method m : registryClass.getMethods()) {
            if ("getValue".equals(m.getName())
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == rl) {
                return m;
            }
        }
        // Older MC: get(ResourceLocation) -> T (or Optional<T> in some versions)
        for (Method m : registryClass.getMethods()) {
            if ("get".equals(m.getName())
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == rl) {
                return m;
            }
        }
        return null;
    }
}
