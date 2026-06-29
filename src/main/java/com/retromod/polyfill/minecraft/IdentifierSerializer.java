/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill for ResourceLocation$Serializer, removed in MC 26.1 when Mojang moved
 * from Gson type adapters to Codecs. Old mods (Jade, etc.) register it as a Gson
 * TypeAdapter for ResourceLocation/Identifier.
 */
package com.retromod.polyfill.minecraft;

import com.google.gson.*;
import java.lang.reflect.Type;

/**
 * Gson serializer/deserializer for Identifier (formerly ResourceLocation),
 * replacing the removed ResourceLocation$Serializer. Format is "namespace:path".
 */
public class IdentifierSerializer implements JsonDeserializer<Object>, JsonSerializer<Object> {

    @Override
    public Object deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonPrimitive()) {
            String str = json.getAsString();
            try {
                Class<?> identifierClass = findIdentifierClass();
                // parse(String), then of(String), then the single-String constructor
                try {
                    var parseMethod = identifierClass.getMethod("parse", String.class);
                    return parseMethod.invoke(null, str);
                } catch (NoSuchMethodException e1) {
                    try {
                        var ofMethod = identifierClass.getMethod("of", String.class);
                        return ofMethod.invoke(null, str);
                    } catch (NoSuchMethodException e2) {
                        return identifierClass.getConstructor(String.class).newInstance(str);
                    }
                }
            } catch (Exception e) {
                throw new JsonParseException("Failed to deserialize Identifier: " + str, e);
            }
        }
        throw new JsonParseException("Expected string for Identifier, got: " + json);
    }

    @Override
    public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    private static Class<?> findIdentifierClass() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.resources.Identifier");
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName("net.minecraft.resources.ResourceLocation");
            } catch (ClassNotFoundException e2) {
                return Class.forName("net.minecraft.util.Identifier");
            }
        }
    }
}
