package com.retromod.shim.api.fabric.embedded;
public class ColorProviderShim {
    public static Object getBlockRegistry() {
        try {
            Class<?> cls = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry");
            return cls.getField("BLOCK").get(null);
        } catch (Exception e) { return null; }
    }
}
