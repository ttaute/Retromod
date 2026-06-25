/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric.embedded;

import com.retromod.util.McReflect;

import java.lang.reflect.Method;

/**
 * Replaces {@code new Identifier(ns, path)} with the static factories MC moved to when the
 * constructor went protected in 1.21. McReflect resolves the class across yarn/mojang namespaces.
 */
public final class IdentifierShim {

    private static Method ofMethod;
    private static Method parseMethod;
    private static Class<?> identifierClass;

    static {
        try {
            identifierClass = McReflect.findClass(
                "net.minecraft.resources.Identifier",      // mojang 26.1+ (renamed from ResourceLocation)
                "net.minecraft.util.Identifier",           // yarn
                "net.minecraft.resources.ResourceLocation" // mojang pre-26.1
            );

            if (identifierClass == null) {
                throw new ClassNotFoundException(
                    "Could not find Identifier/ResourceLocation class on any namespace");
            }

            ofMethod = McReflect.findMethod(identifierClass,
                new Class[]{String.class, String.class},
                "of", "fromNamespaceAndPath");

            if (ofMethod == null) {
                throw new NoSuchMethodException(
                    "Could not find Identifier.of or ResourceLocation.fromNamespaceAndPath");
            }

            parseMethod = McReflect.findMethod(identifierClass,
                new Class[]{String.class},
                "of", "parse");

        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to initialize IdentifierShim", e);
        }
    }
    
    private IdentifierShim() {
    }

    /** Replaces {@code new Identifier(namespace, path)}. */
    public static Object of(String namespace, String path) {
        try {
            return ofMethod.invoke(null, namespace, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Identifier: " + namespace + ":" + path, e);
        }
    }
    
    /** Replaces {@code new Identifier("namespace:path")}. */
    public static Object of(String id) {
        try {
            return parseMethod.invoke(null, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Identifier: " + id, e);
        }
    }
    
    /** Replaces {@code new Identifier("minecraft", path)}. */
    public static Object ofVanilla(String path) {
        return of("minecraft", path);
    }

    /** Parses an Identifier, returning null on failure. */
    public static Object tryParse(String id) {
        try {
            Method tryParseMethod = identifierClass.getMethod("tryParse", String.class);
            return tryParseMethod.invoke(null, id);
        } catch (NoSuchMethodException e) {
            try {
                return of(id);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isValid(String id) {
        return tryParse(id) != null;
    }
}
