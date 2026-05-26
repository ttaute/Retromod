/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
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

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Env");

    private static Boolean isClient = null;
    private static Boolean isHeadless = null;
    private static Boolean isDedicatedServer = null;

    // Architecture and OS (cached on first access)
    private static String cpuArch = null;
    private static String osName = null;
    private static RenderingBackend renderingBackend = null;

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

        // Default: OpenGL. Minecraft still uses OpenGL as its only backend
        // through 26.1.x — Mojang has not shipped Vulkan, Metal, or DirectX
        // as of this writing. We check several signal classes that identify
        // "a modern MC render pipeline is running" in order of specificity:
        //
        //   1.21.x and older: com.mojang.blaze3d.platform.GlStateManager
        //   26.1+:            com.mojang.blaze3d.platform.GlStateManager was
        //                     removed in the rendering refactor; the new
        //                     entry point is com.mojang.blaze3d.systems.RenderSystem
        //                     which is present on every 26.1 client.
        //
        // Either one existing → OPENGL. If neither exists we really are in
        // an environment without a known MC rendering surface (dedicated server
        // minus the SOFTWARE branch above, or an unsupported future version).
        for (String glSignal : new String[]{
                "com.mojang.blaze3d.platform.GlStateManager",   // 1.8 – 1.21.x
                "com.mojang.blaze3d.systems.RenderSystem",       // 1.15+ incl 26.1
                "com.mojang.blaze3d.opengl.GlStateManager"       // possible 26.1 relocation
        }) {
            try {
                Class.forName(glSignal);
                return RenderingBackend.OPENGL;
            } catch (ClassNotFoundException ignored) {}
        }

        return RenderingBackend.UNKNOWN;
    }

    // =========================================================================
    //  Client / Server Detection (private)
    // =========================================================================

    private static boolean detectClient() {
        // Existence-only probes (initialize=false) — see classExists(): detection
        // must never trigger a Minecraft bootstrap class's <clinit>.
        if (classExists("net.minecraft.client.MinecraftClient")   // Yarn (dev)
                || classExists("net.minecraft.client.Minecraft")  // Mojang (26.1+, NeoForge)
                || classExists("net.minecraft.class_310")) {      // intermediary — pre-26.1 Fabric runtime
            return true;
        }
        // Fallback: if we have a display, we're almost certainly a client.
        return !detectHeadless();
    }

    private static boolean detectDedicatedServer() {
        // The Minecraft runtime ships a MERGED jar, so server classes — even
        // net.minecraft.server.dedicated.DedicatedServer and MinecraftServer — are
        // present on a CLIENT too. Their presence is therefore NOT a reliable
        // "this is a dedicated server" signal. The reliable signal is the ABSENCE
        // of a client class.
        boolean clientPresent = classExists("net.minecraft.client.MinecraftClient")
                || classExists("net.minecraft.client.Minecraft")
                || classExists("net.minecraft.class_310"); // intermediary — pre-26.1 Fabric client
        if (clientPresent) {
            return false; // integrated server on a client — not dedicated
        }
        // No client class present → if a server class is present, it's dedicated.
        if (classExists("net.minecraft.server.MinecraftServer")
                || classExists("net.minecraft.server.dedicated.DedicatedServer")) {
            return true;
        }
        // Fallback: if headless, probably a server.
        return detectHeadless();
    }

    /**
     * Test whether a class is present <em>without initializing it</em>.
     *
     * <p><b>Why this exists (#46):</b> the single-arg {@link Class#forName(String)}
     * <em>initializes</em> the class. Probing a Minecraft bootstrap class such as
     * {@code net.minecraft.server.MinecraftServer} that way during mod
     * construction forced its {@code <clinit>} to run far too early. With a mod
     * that mixins into those classes (Legacy4J mixins {@code MinecraftServer} and
     * {@code LevelSettings}), that cascaded into {@code wily.legacy.client.PackAlbum.<clinit>},
     * which reads {@code Minecraft.getInstance().gameDirectory} before the client
     * singleton exists → {@link NullPointerException}. Retromod's mere presence
     * then crashed an otherwise-working mod. Detection must observe, never trigger
     * static initialization — hence the three-arg form with {@code initialize = false}.
     */
    static boolean classExists(String name) {
        try {
            ClassLoader cl = EnvironmentDetector.class.getClassLoader();
            if (cl == null) {
                cl = Thread.currentThread().getContextClassLoader();
            }
            Class.forName(name, false, cl);
            return true;
        } catch (Throwable ignored) {
            // ClassNotFoundException (absent) or any LinkageError — treat as absent.
            return false;
        }
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
