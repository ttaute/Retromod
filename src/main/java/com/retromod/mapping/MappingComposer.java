/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * MAPPING COMPOSER
 *
 * Composes two mapping files to produce a direct intermediary → Mojang official mapping:
 *   1. Intermediary mapping (TinyV2): obfuscated → intermediary
 *   2. Mojang official mapping (ProGuard): readable → obfuscated (reversed)
 *
 * By composing: intermediary → obfuscated → Mojang official
 */
package com.retromod.mapping;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Composes Fabric intermediary mappings with Mojang official mappings
 * to produce a direct intermediary → Mojang official mapping file.
 */
public class MappingComposer {

    /**
     * Compose intermediary and Mojang mapping files into a single TinyV2 output.
     *
     * @param intermediaryPath TinyV2 file mapping obfuscated → intermediary
     * @param mojangPath ProGuard file mapping readable → obfuscated
     * @param outputPath Where to write the composed TinyV2 (intermediary → official)
     */
    public static void compose(Path intermediaryPath, Path mojangPath, Path outputPath)
            throws IOException {

        // Step 1: Load obfuscated → intermediary from TinyV2
        Map<String, String> obfToIntermediary = new HashMap<>();       // class
        Map<String, String> obfMethodToIntermediary = new HashMap<>();  // owner.name.desc → intermediary name
        Map<String, String> obfFieldToIntermediary = new HashMap<>();   // owner.name → intermediary name

        loadTinyV2(intermediaryPath, obfToIntermediary, obfMethodToIntermediary, obfFieldToIntermediary);

        // Step 2: Load obfuscated → official from ProGuard (reversed)
        Map<String, String> obfToOfficial = new HashMap<>();
        Map<String, String> obfMethodToOfficial = new HashMap<>();
        Map<String, String> obfFieldToOfficial = new HashMap<>();

        loadProGuard(mojangPath, obfToOfficial, obfMethodToOfficial, obfFieldToOfficial);

        // Step 3: Compose: intermediary → obfuscated → official
        // For each obfuscated name, if it has both an intermediary AND an official mapping,
        // create intermediary → official

        Map<String, String> intermediaryToOfficial = new HashMap<>();
        Map<String, String[]> methodMappings = new HashMap<>();  // intermediary key → [owner, desc, officialName]
        Map<String, String[]> fieldMappings = new HashMap<>();   // intermediary key → [owner, officialName]

        // Compose class mappings
        for (Map.Entry<String, String> entry : obfToIntermediary.entrySet()) {
            String obf = entry.getKey();
            String intermediary = entry.getValue();
            String official = obfToOfficial.get(obf);
            if (official != null && !intermediary.equals(official)) {
                intermediaryToOfficial.put(intermediary, official);
            }
        }

        // Compose method mappings
        for (Map.Entry<String, String> entry : obfMethodToIntermediary.entrySet()) {
            String obfKey = entry.getKey();
            String intermediaryName = entry.getValue();
            String officialName = obfMethodToOfficial.get(obfKey);
            if (officialName != null && !intermediaryName.equals(officialName)) {
                // Parse key: owner.name.desc
                int firstDot = obfKey.indexOf('.');
                int secondDot = obfKey.indexOf('.', firstDot + 1);
                if (firstDot > 0 && secondDot > 0) {
                    String obfOwner = obfKey.substring(0, firstDot);
                    String desc = obfKey.substring(secondDot + 1);
                    String intermediaryOwner = obfToIntermediary.getOrDefault(obfOwner, obfOwner);
                    methodMappings.put(intermediaryOwner + "." + intermediaryName + "." + desc,
                            new String[]{intermediaryOwner, desc, officialName});
                }
            }
        }

        // Compose field mappings
        for (Map.Entry<String, String> entry : obfFieldToIntermediary.entrySet()) {
            String obfKey = entry.getKey();
            String intermediaryName = entry.getValue();
            String officialName = obfFieldToOfficial.get(obfKey);
            if (officialName != null && !intermediaryName.equals(officialName)) {
                int dot = obfKey.indexOf('.');
                if (dot > 0) {
                    String obfOwner = obfKey.substring(0, dot);
                    String intermediaryOwner = obfToIntermediary.getOrDefault(obfOwner, obfOwner);
                    fieldMappings.put(intermediaryOwner + "." + intermediaryName,
                            new String[]{intermediaryOwner, officialName});
                }
            }
        }

        // Step 4: Write output TinyV2
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("tiny\t2\t0\tintermediary\tofficial\n");
            writer.write("# Generated by RetroMod MappingComposer\n");
            writer.write("# Intermediary → Mojang Official mappings\n");

            // Sort classes for deterministic output
            List<String> sortedClasses = new ArrayList<>(intermediaryToOfficial.keySet());
            Collections.sort(sortedClasses);

            for (String intermediaryClass : sortedClasses) {
                String officialClass = intermediaryToOfficial.get(intermediaryClass);
                writer.write("c\t" + intermediaryClass + "\t" + officialClass + "\n");

                // Write methods for this class
                String classPrefix = intermediaryClass + ".";
                for (Map.Entry<String, String[]> mEntry : methodMappings.entrySet()) {
                    if (mEntry.getKey().startsWith(classPrefix)) {
                        String[] val = mEntry.getValue();
                        String methodName = mEntry.getKey().substring(classPrefix.length());
                        int descDot = methodName.indexOf('.');
                        if (descDot > 0) {
                            String name = methodName.substring(0, descDot);
                            String desc = methodName.substring(descDot + 1);
                            writer.write("\tm\t" + desc + "\t" + name + "\t" + val[2] + "\n");
                        }
                    }
                }

                // Write fields for this class
                for (Map.Entry<String, String[]> fEntry : fieldMappings.entrySet()) {
                    if (fEntry.getKey().startsWith(classPrefix)) {
                        String fieldName = fEntry.getKey().substring(classPrefix.length());
                        String[] val = fEntry.getValue();
                        writer.write("\tf\t\t" + fieldName + "\t" + val[1] + "\n");
                    }
                }
            }
        }

        System.out.println("Composed " + intermediaryToOfficial.size() + " class, "
                + methodMappings.size() + " method, "
                + fieldMappings.size() + " field mappings");
    }

    /**
     * Load TinyV2 mapping file (obfuscated → intermediary).
     */
    private static void loadTinyV2(Path path,
                                    Map<String, String> classMappings,
                                    Map<String, String> methodMappings,
                                    Map<String, String> fieldMappings) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            String currentObfClass = null;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("tiny\t")) continue; // header

                if (line.startsWith("c\t")) {
                    String[] parts = line.substring(2).split("\t");
                    if (parts.length >= 2) {
                        currentObfClass = parts[0]; // obfuscated
                        classMappings.put(parts[0], parts[1]); // obf → intermediary
                    }
                } else if (line.startsWith("\tm\t") && currentObfClass != null) {
                    String[] parts = line.substring(3).split("\t");
                    if (parts.length >= 3) {
                        String desc = parts[0];
                        String obfName = parts[1];
                        String intermediaryName = parts[2];
                        methodMappings.put(currentObfClass + "." + obfName + "." + desc, intermediaryName);
                    }
                } else if (line.startsWith("\tf\t") && currentObfClass != null) {
                    String[] parts = line.substring(3).split("\t");
                    if (parts.length >= 3) {
                        String obfName = parts[1];
                        String intermediaryName = parts[2];
                        fieldMappings.put(currentObfClass + "." + obfName, intermediaryName);
                    }
                }
            }
        }
    }

    /**
     * Load ProGuard mapping file (Mojang official → obfuscated, reversed).
     * ProGuard format:
     *   com.mojang.ClassName -> a:
     *       returnType methodName(params) -> obfName
     *       type fieldName -> obfName
     */
    private static void loadProGuard(Path path,
                                      Map<String, String> classMappings,
                                      Map<String, String> methodMappings,
                                      Map<String, String> fieldMappings) throws IOException {

        Pattern classPattern = Pattern.compile("^(\\S+) -> (\\S+):$");
        Pattern methodPattern = Pattern.compile("^\\s+(?:\\d+:\\d+:)?(\\S+) (\\S+)\\((.*?)\\) -> (\\S+)$");
        Pattern fieldPattern = Pattern.compile("^\\s+(\\S+) (\\S+) -> (\\S+)$");

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            String currentObfClass = null;
            String currentOfficialClass = null;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                Matcher classMatcher = classPattern.matcher(line);
                if (classMatcher.matches()) {
                    currentOfficialClass = classMatcher.group(1).replace('.', '/');
                    currentObfClass = classMatcher.group(2).replace('.', '/');
                    // ProGuard maps official → obfuscated, we want obfuscated → official
                    classMappings.put(currentObfClass, currentOfficialClass);
                    continue;
                }

                if (currentObfClass == null) continue;

                Matcher methodMatcher = methodPattern.matcher(line);
                if (methodMatcher.matches()) {
                    String officialName = methodMatcher.group(2);
                    String obfName = methodMatcher.group(4);
                    String params = methodMatcher.group(3);
                    String returnType = methodMatcher.group(1);

                    String desc = buildDescriptor(params, returnType);
                    methodMappings.put(currentObfClass + "." + obfName + "." + desc, officialName);
                    continue;
                }

                Matcher fieldMatcher = fieldPattern.matcher(line);
                if (fieldMatcher.matches()) {
                    String officialName = fieldMatcher.group(2);
                    String obfName = fieldMatcher.group(3);
                    fieldMappings.put(currentObfClass + "." + obfName, officialName);
                }
            }
        }
    }

    private static String buildDescriptor(String params, String returnType) {
        StringBuilder desc = new StringBuilder("(");
        if (!params.isEmpty()) {
            for (String param : params.split(",")) {
                desc.append(typeToDescriptor(param.trim()));
            }
        }
        desc.append(")");
        desc.append(typeToDescriptor(returnType));
        return desc.toString();
    }

    private static String typeToDescriptor(String type) {
        return switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> {
                if (type.endsWith("[]")) {
                    yield "[" + typeToDescriptor(type.substring(0, type.length() - 2));
                }
                yield "L" + type.replace('.', '/') + ";";
            }
        };
    }
}
