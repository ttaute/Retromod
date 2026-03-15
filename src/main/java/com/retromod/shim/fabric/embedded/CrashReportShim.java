/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Shim for CrashReport.getFile() return type change (File -> Path).
 *
 * In older Minecraft versions (<=1.19.x), CrashReport.getFile() returned java.io.File.
 * In newer versions (1.20+), it returns java.nio.file.Path.
 *
 * This shim bridges the gap by calling the Path-returning method and converting to File.
 */
package com.retromod.shim.fabric.embedded;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Shim for net.minecraft.class_128 (CrashReport) method changes.
 *
 * Old: CrashReport.getFile() -> java.io.File  (method_572 in intermediary)
 * New: CrashReport.getFile() -> java.nio.file.Path
 *
 * This provides a static bridge that takes the CrashReport instance,
 * calls the new Path-returning method, and converts to File.
 */
public final class CrashReportShim {

    private static volatile Method getFileAsPathMethod;
    private static volatile boolean resolved = false;

    private CrashReportShim() {}

    /**
     * Bridge for old CrashReport.getFile() -> File.
     * Called when transformed code expects File but the method now returns Path.
     *
     * @param crashReport the CrashReport instance (net.minecraft.class_128)
     * @return java.io.File or null
     */
    public static File getFileAsFile(Object crashReport) {
        try {
            if (!resolved) {
                resolve(crashReport.getClass());
            }

            if (getFileAsPathMethod != null) {
                Object path = getFileAsPathMethod.invoke(crashReport);
                if (path instanceof java.nio.file.Path p) {
                    return p.toFile();
                }
            }
        } catch (Exception e) {
            // Fallback: return null (NEC handles null files gracefully)
        }
        return null;
    }

    private static synchronized void resolve(Class<?> crashReportClass) {
        if (resolved) return;
        resolved = true;

        try {
            // Find method_572 that returns Path
            for (Method m : crashReportClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 0
                    && java.nio.file.Path.class.isAssignableFrom(m.getReturnType())) {
                    // Check if this is the getFile method (returns Path, no params)
                    // In intermediary it's still called method_572
                    if (m.getName().equals("method_572") || m.getName().equals("getFile")) {
                        getFileAsPathMethod = m;
                        getFileAsPathMethod.setAccessible(true);
                        break;
                    }
                }
            }

            // Fallback: find ANY no-arg method returning Path
            if (getFileAsPathMethod == null) {
                for (Method m : crashReportClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0
                        && java.nio.file.Path.class.isAssignableFrom(m.getReturnType())) {
                        getFileAsPathMethod = m;
                        getFileAsPathMethod.setAccessible(true);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail — will return null
        }
    }
}
