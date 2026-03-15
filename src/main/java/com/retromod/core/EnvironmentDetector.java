/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 RevivalSMP. Licensed under MIT.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;

/**
 * Utility to detect the runtime environment.
 *
 * Detects:
 * - Client vs Dedicated Server
 * - Headless (no GUI) vs GUI available
 * - CPU architecture (x86_64, aarch64/ARM, etc.)
 * - Operating system (Windows, macOS, Linux)
 * - Rendering backend (OpenGL, Vulkan, Metal)
 * - Which features to enable/disable
 */
public class EnvironmentDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Env");

    private static volatile Boolean isClient = null;
    private static volatile Boolean isHeadless = null;
    private static volatile Boolean isDedicatedServer = null;

    // Architecture and OS (cached on first access)
    private static volatile String cpuArch = null;
    private static volatile String osName = null;
    private static volatile RenderingBackend renderingBackend = null;

    /**
     * CPU architecture family.
     */
    public enum CpuArch {
        X86_64,     // Intel/AMD 64-bit (most desktops and servers)
        X86,        // Intel/AMD 32-bit (legacy)
        AARCH64,    // ARM 64-bit (Apple Silicon, Raspberry Pi 4+, some servers)
        ARM,        // ARM 32-bit (older Raspberry Pi, some embedded)
        RISCV64,    // RISC-V 64-bit (emerging)
        UNKNOWN
    }

    /**
     * Operating system family.
     */
    public enum OsFamily {
        WINDOWS,
        MACOS,
        LINUX,
        UNKNOWN
    }

    /**
     * Rendering backend used by Minecraft / LWJGL.
     */
    public enum RenderingBackend {
        OPENGL,     // Current Minecraft default
        VULKAN,     // Future Minecraft (rumored/planned)
        METAL,      // macOS native (possible via MoltenVK or direct)
        DIRECTX,    // Windows native (possible future)
        SOFTWARE,   // Fallback / headless
        UNKNOWN
    }

    // =========================================================================
    //  Client / Server Detection
    // =========================================================================

    /**
     * Check if running on a client (has rendering, GUI).
     */
    public static boolean isClient() {
        if (isClient == null) {
            isClient = detectClient();
        }
        return isClient;
    }

    /**
     * Check if running on a dedicated server (no client, no GUI).
     */
    public static boolean isDedicatedServer() {
        if (isDedicatedServer == null) {
            isDedicatedServer = detectDedicatedServer();
        }
        return isDedicatedServer;
    }

    /**
     * Check if running in headless mode (no display available).
     */
    public static boolean isHeadless() {
        if (isHeadless == null) {
            isHeadless = detectHeadless();
        }
        return isHeadless;
    }

    /**
     * Check if GUI dialogs can be shown.
     */
    public static boolean canShowGui() {
        return isClient() && !isHeadless();
    }

    // =========================================================================
    //  Architecture Detection
    // =========================================================================

    /**
     * Get the CPU architecture family.
     */
    public static CpuArch getCpuArch() {
        String arch = getCpuArchString();
        return switch (arch) {
            case "amd64", "x86_64" -> CpuArch.X86_64;
            case "x86", "i386", "i486", "i586", "i686" -> CpuArch.X86;
            case "aarch64", "arm64" -> CpuArch.AARCH64;
            case "arm", "armv7l", "armhf" -> CpuArch.ARM;
            case "riscv64" -> CpuArch.RISCV64;
            default -> CpuArch.UNKNOWN;
        };
    }

    /**
     * Get the raw CPU architecture string from the JVM.
     */
    public static String getCpuArchString() {
        if (cpuArch == null) {
            cpuArch = System.getProperty("os.arch", "unknown").toLowerCase();
        }
        return cpuArch;
    }

    /**
     * Check if running on ARM (includes both 32-bit and 64-bit ARM).
     */
    public static boolean isArm() {
        CpuArch arch = getCpuArch();
        return arch == CpuArch.AARCH64 || arch == CpuArch.ARM;
    }

    /**
     * Check if running on Apple Silicon (M1/M2/M3/M4).
     */
    public static boolean isAppleSilicon() {
        return getCpuArch() == CpuArch.AARCH64 && getOsFamily() == OsFamily.MACOS;
    }

    // =========================================================================
    //  OS Detection
    // =========================================================================

    /**
     * Get the operating system family.
     */
    public static OsFamily getOsFamily() {
        String os = getOsString();
        if (os.contains("win")) return OsFamily.WINDOWS;
        if (os.contains("mac") || os.contains("darwin")) return OsFamily.MACOS;
        if (os.contains("linux") || os.contains("nix") || os.contains("nux")) return OsFamily.LINUX;
        return OsFamily.UNKNOWN;
    }

    /**
     * Get the raw OS name string.
     */
    public static String getOsString() {
        if (osName == null) {
            osName = System.getProperty("os.name", "unknown").toLowerCase();
        }
        return osName;
    }

    // =========================================================================
    //  Rendering Backend Detection
    // =========================================================================

    /**
     * Get the rendering backend being used.
     * Detects Vulkan, Metal, OpenGL, or fallback.
     */
    public static RenderingBackend getRenderingBackend() {
        if (renderingBackend == null) {
            renderingBackend = detectRenderingBackend();
        }
        return renderingBackend;
    }

    /**
     * Check if Minecraft is using Vulkan rendering.
     */
    public static boolean isVulkan() {
        return getRenderingBackend() == RenderingBackend.VULKAN;
    }

    /**
     * Check if Minecraft is using Metal rendering (macOS).
     */
    public static boolean isMetal() {
        return getRenderingBackend() == RenderingBackend.METAL;
    }

    /**
     * Check if Minecraft is using OpenGL rendering (current default).
     */
    public static boolean isOpenGL() {
        return getRenderingBackend() == RenderingBackend.OPENGL;
    }

    private static RenderingBackend detectRenderingBackend() {
        // Dedicated servers don't use rendering
        if (isDedicatedServer()) {
            return RenderingBackend.SOFTWARE;
        }

        // Check system property (Minecraft may set this in future)
        String backendProp = System.getProperty("minecraft.rendering.backend", "");
        if (!backendProp.isEmpty()) {
            return switch (backendProp.toLowerCase()) {
                case "vulkan", "vk" -> RenderingBackend.VULKAN;
                case "metal", "mtl" -> RenderingBackend.METAL;
                case "opengl", "gl" -> RenderingBackend.OPENGL;
                case "directx", "dx", "d3d" -> RenderingBackend.DIRECTX;
                default -> RenderingBackend.UNKNOWN;
            };
        }

        // Check LWJGL for Vulkan support
        try {
            // If LWJGL Vulkan classes are loadable AND being used, Vulkan is active
            Class.forName("org.lwjgl.vulkan.VK10");
            // Check if Minecraft's GlStateManager equivalent uses Vulkan
            // Future MC versions may have a VkStateManager or similar
            try {
                Class.forName("com.mojang.blaze3d.platform.VulkanStateManager");
                return RenderingBackend.VULKAN;
            } catch (ClassNotFoundException ignored) {}
            try {
                Class.forName("net.minecraft.client.render.VulkanRenderer");
                return RenderingBackend.VULKAN;
            } catch (ClassNotFoundException ignored) {}
        } catch (ClassNotFoundException ignored) {
            // LWJGL Vulkan not available
        }

        // Check for Metal (macOS via MoltenVK or direct Metal)
        if (getOsFamily() == OsFamily.MACOS) {
            try {
                // Future: Minecraft might use Metal directly on macOS
                Class.forName("com.mojang.blaze3d.platform.MetalStateManager");
                return RenderingBackend.METAL;
            } catch (ClassNotFoundException ignored) {}

            // Check for MoltenVK (Vulkan-over-Metal translation layer)
            String vulkanICD = System.getenv("VK_ICD_FILENAMES");
            if (vulkanICD != null && vulkanICD.contains("MoltenVK")) {
                return RenderingBackend.VULKAN; // Vulkan via MoltenVK
            }
        }

        // Check for DirectX (Windows only, future)
        if (getOsFamily() == OsFamily.WINDOWS) {
            try {
                Class.forName("com.mojang.blaze3d.platform.DirectXStateManager");
                return RenderingBackend.DIRECTX;
            } catch (ClassNotFoundException ignored) {}
        }

        // Default: OpenGL (current Minecraft default as of 1.21.x)
        try {
            Class.forName("com.mojang.blaze3d.platform.GlStateManager");
            return RenderingBackend.OPENGL;
        } catch (ClassNotFoundException ignored) {}

        return RenderingBackend.UNKNOWN;
    }

    // =========================================================================
    //  Client / Server Detection (private)
    // =========================================================================

    /**
     * Check if we're running on Minecraft 26.1+ (deobfuscated, Mojang official names).
     * On 26.1+, intermediary names (class_XXXX) don't exist — only Mojang names.
     */
    public static boolean is26Plus() {
        // If Mojang-named MinecraftClient class exists but intermediary doesn't,
        // we're on 26.1+ where obfuscation was removed
        try {
            Class.forName("net.minecraft.client.Minecraft");
            try {
                Class.forName("net.minecraft.class_310");
                return false; // Both exist — pre-26.1 with Mojang mappings (NeoForge)
            } catch (ClassNotFoundException e) {
                return true; // Mojang name exists, intermediary doesn't — 26.1+
            }
        } catch (ClassNotFoundException e) {
            // Try server-side detection
            try {
                Class.forName("net.minecraft.server.dedicated.DedicatedServer");
                try {
                    Class.forName("net.minecraft.class_3176");
                    return false; // Pre-26.1
                } catch (ClassNotFoundException e2) {
                    return true; // 26.1+
                }
            } catch (ClassNotFoundException e2) {
                return false; // Can't determine
            }
        }
    }

    private static boolean detectClient() {
        // Check for client-specific classes
        // Priority: Mojang (26.1+ and NeoForge) → Yarn (Fabric dev) → Intermediary (legacy Fabric prod)
        String[] clientClasses = {
            "net.minecraft.client.Minecraft",        // Mojang official (26.1+, NeoForge, Forge)
            "net.minecraft.client.MinecraftClient",  // Yarn (Fabric dev, legacy)
            "net.minecraft.class_310"                // Intermediary (legacy Fabric prod, pre-26.1)
        };
        for (String className : clientClasses) {
            try {
                Class.forName(className);
                return true;
            } catch (ClassNotFoundException ignored) {}
        }

        // Fallback: check if we have a display
        return !detectHeadless();
    }

    private static boolean detectDedicatedServer() {
        // Check for dedicated server classes
        // Priority: Mojang (26.1+) → Yarn (legacy Fabric dev) → Intermediary (legacy Fabric prod)
        String[] serverClasses = {
            "net.minecraft.server.dedicated.DedicatedServer",              // Mojang (26.1+, Forge, NeoForge)
            "net.minecraft.server.dedicated.MinecraftDedicatedServer",     // Yarn (legacy Fabric dev)
            "net.minecraft.class_3176"                                     // Intermediary (legacy Fabric prod)
        };
        for (String className : serverClasses) {
            try {
                Class.forName(className);
                return true;
            } catch (ClassNotFoundException ignored) {}
        }

        // Check MinecraftServer exists but client doesn't → dedicated server
        String[] serverBaseClasses = {
            "net.minecraft.server.MinecraftServer",  // Mojang (26.1+)
            "net.minecraft.class_3248",              // Intermediary (legacy)
            "net.minecraft.class_3176"               // Intermediary alt (legacy)
        };
        for (String serverClass : serverBaseClasses) {
            try {
                Class.forName(serverClass);
                // Server class found — check if client is absent
                if (!detectClient()) {
                    return true;
                }
                return false; // Both exist → integrated server (client)
            } catch (ClassNotFoundException ignored) {}
        }

        // Fallback: if headless, probably a server
        return detectHeadless();
    }

    private static boolean detectHeadless() {
        // Check Java's headless mode
        if (GraphicsEnvironment.isHeadless()) {
            return true;
        }

        // Check system property
        String headless = System.getProperty("java.awt.headless");
        if ("true".equalsIgnoreCase(headless)) {
            return true;
        }

        // Check if DISPLAY is set (Linux)
        String display = System.getenv("DISPLAY");
        if (System.getProperty("os.name", "").toLowerCase().contains("linux")) {
            if (display == null || display.isEmpty()) {
                return true;
            }
        }

        // Try to check if we can create a window
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.getDefaultScreenDevice();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    // =========================================================================
    //  Logging
    // =========================================================================

    /**
     * Log the full detected environment.
     */
    public static void logEnvironment() {
        LOGGER.info("Environment: {} (headless: {})",
            isDedicatedServer() ? "Dedicated Server" : "Client",
            isHeadless());
        LOGGER.info("  OS: {} ({})", getOsFamily(), getOsString());
        LOGGER.info("  CPU: {} ({})", getCpuArch(), getCpuArchString());
        if (isAppleSilicon()) {
            LOGGER.info("  Apple Silicon detected");
        }
        if (is26Plus()) {
            LOGGER.info("  Minecraft 26.1+ detected (deobfuscated, Mojang official names)");
        } else {
            LOGGER.info("  Pre-26.1 Minecraft (obfuscated, intermediary names)");
        }
        if (!isDedicatedServer()) {
            LOGGER.info("  Rendering: {}", getRenderingBackend());
        }
    }

    /**
     * Force set the environment (for testing or when auto-detection fails).
     */
    public static void setEnvironment(boolean client, boolean headless) {
        isClient = client;
        isHeadless = headless;
        isDedicatedServer = !client;
    }
}
