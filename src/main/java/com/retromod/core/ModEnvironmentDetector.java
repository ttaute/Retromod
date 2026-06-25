/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Detects whether a mod is server-only, client-only, or both. A server-only mod transformed on
 * the server lets clients join without Retromod, so server admins can run old mods without
 * requiring players to install anything.
 */
public class ModEnvironmentDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-EnvDetect");

    public enum ModEnvironment {
        /** Runs on both client and server */
        BOTH("*"),
        /** Runs only on the client */
        CLIENT("client"),
        /** Runs only on the server */
        SERVER("server"),
        /** Unknown: treat as BOTH for safety */
        UNKNOWN("*");

        private final String fabricValue;

        ModEnvironment(String fabricValue) {
            this.fabricValue = fabricValue;
        }

        public String getFabricValue() {
            return fabricValue;
        }
    }

    public static ModEnvironment detectEnvironment(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {

            ZipEntry fabricEntry = jar.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                try (InputStream is = jar.getInputStream(fabricEntry)) {
                    String content = new String(is.readAllBytes());
                    return parseFabricEnvironment(content);
                }
            }

            ZipEntry forgeEntry = jar.getEntry("META-INF/mods.toml");
            if (forgeEntry == null) {
                forgeEntry = jar.getEntry("META-INF/neoforge.mods.toml");
            }
            if (forgeEntry != null) {
                try (InputStream is = jar.getInputStream(forgeEntry)) {
                    String content = new String(is.readAllBytes());
                    return parseForgeEnvironment(content);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("Could not detect environment for: {}", jarPath.getFileName());
        }

        return ModEnvironment.UNKNOWN;
    }

    private static ModEnvironment parseFabricEnvironment(String json) {
        Pattern pattern = Pattern.compile("\"environment\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String env = matcher.group(1).toLowerCase();
            return switch (env) {
                case "client" -> ModEnvironment.CLIENT;
                case "server" -> ModEnvironment.SERVER;
                case "*" -> ModEnvironment.BOTH;
                default -> ModEnvironment.UNKNOWN;
            };
        }

        return ModEnvironment.UNKNOWN;
    }

    private static ModEnvironment parseForgeEnvironment(String toml) {
        Pattern pattern = Pattern.compile("side\\s*=\\s*\"?(BOTH|CLIENT|SERVER|DEDICATED_SERVER)\"?",
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(toml);

        if (matcher.find()) {
            String side = matcher.group(1).toUpperCase();
            return switch (side) {
                case "CLIENT" -> ModEnvironment.CLIENT;
                case "SERVER", "DEDICATED_SERVER" -> ModEnvironment.SERVER;
                case "BOTH" -> ModEnvironment.BOTH;
                default -> ModEnvironment.UNKNOWN;
            };
        }

        return ModEnvironment.UNKNOWN;
    }

    /** False means the mod can run on one side without the other needing Retromod. */
    public static boolean requiresBothSides(Path jarPath) {
        ModEnvironment env = detectEnvironment(jarPath);
        return env == ModEnvironment.BOTH || env == ModEnvironment.UNKNOWN;
    }

    public static boolean isServerOnly(Path jarPath) {
        return detectEnvironment(jarPath) == ModEnvironment.SERVER;
    }

    public static boolean isClientOnly(Path jarPath) {
        return detectEnvironment(jarPath) == ModEnvironment.CLIENT;
    }

    public static void logModEnvironment(Path jarPath) {
        ModEnvironment env = detectEnvironment(jarPath);
        String fileName = jarPath.getFileName().toString();

        switch (env) {
            case SERVER -> {
                LOGGER.info("  {} is SERVER-ONLY", fileName);
                LOGGER.info("    → Clients don't need Retromod installed!");
            }
            case CLIENT -> {
                LOGGER.info("  {} is CLIENT-ONLY", fileName);
                LOGGER.info("    → Server doesn't need this mod");
            }
            case BOTH -> {
                LOGGER.info("  {} runs on BOTH sides", fileName);
            }
            default -> {
                LOGGER.debug("  {} has unknown environment (treating as BOTH)", fileName);
            }
        }
    }

    public static String getEnvironmentDescription(ModEnvironment env) {
        return switch (env) {
            case SERVER -> "Server-only mod - clients don't need Retromod!";
            case CLIENT -> "Client-only mod - only install on your client";
            case BOTH -> "Runs on both sides";
            case UNKNOWN -> "Unknown (treating as both sides)";
        };
    }
}
