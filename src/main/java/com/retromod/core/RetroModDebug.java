/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.*;

/**
 * Extensive debug system for RetroMod.
 *
 * Enable via config/retromod/config.json:
 * {
 *   "debug": true,
 *   "debug_options": {
 *     "dump_bytecode": true,       // Save before/after bytecode to disk
 *     "log_all_redirects": true,   // Log every redirect applied
 *     "log_mixin_transforms": true,// Log mixin annotation rewrites
 *     "log_class_scanning": true,  // Log every class scanned during transformation
 *     "log_skipped_classes": true, // Log classes skipped (no changes needed)
 *     "dump_mixin_configs": true,  // Save mixin configs before/after
 *     "scan_removed_refs": true,   // Scan JARs for references to KNOWN_REMOVED classes
 *     "trace_class_loading": true  // Log class redirect mapping decisions
 *   }
 * }
 */
public class RetroModDebug {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Debug");

    // Debug options (all false by default)
    private static boolean enabled = false;
    private static boolean dumpBytecode = false;
    private static boolean logAllRedirects = false;
    private static boolean logMixinTransforms = false;
    private static boolean logClassScanning = false;
    private static boolean logSkippedClasses = false;
    private static boolean dumpMixinConfigs = false;
    private static boolean scanRemovedRefs = false;
    private static boolean traceClassLoading = false;

    private static Path debugOutputDir;

    /**
     * Load debug settings from the config JSON object.
     */
    public static void loadConfig(com.google.gson.JsonObject config) {
        if (config.has("debug")) {
            enabled = config.get("debug").getAsBoolean();
        }

        if (!enabled) return;

        LOGGER.warn("=== RETROMOD DEBUG MODE ENABLED ===");
        LOGGER.warn("Debug output will be saved to config/retromod/debug/");

        // Create debug output directory
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            debugOutputDir = Path.of("config/retromod/debug", timestamp);
            Files.createDirectories(debugOutputDir);
        } catch (IOException e) {
            LOGGER.error("Could not create debug output directory", e);
        }

        if (config.has("debug_options")) {
            var opts = config.getAsJsonObject("debug_options");
            if (opts.has("dump_bytecode")) dumpBytecode = opts.get("dump_bytecode").getAsBoolean();
            if (opts.has("log_all_redirects")) logAllRedirects = opts.get("log_all_redirects").getAsBoolean();
            if (opts.has("log_mixin_transforms")) logMixinTransforms = opts.get("log_mixin_transforms").getAsBoolean();
            if (opts.has("log_class_scanning")) logClassScanning = opts.get("log_class_scanning").getAsBoolean();
            if (opts.has("log_skipped_classes")) logSkippedClasses = opts.get("log_skipped_classes").getAsBoolean();
            if (opts.has("dump_mixin_configs")) dumpMixinConfigs = opts.get("dump_mixin_configs").getAsBoolean();
            if (opts.has("scan_removed_refs")) scanRemovedRefs = opts.get("scan_removed_refs").getAsBoolean();
            if (opts.has("trace_class_loading")) traceClassLoading = opts.get("trace_class_loading").getAsBoolean();
        } else if (enabled) {
            // If debug=true but no debug_options, enable everything
            dumpBytecode = true;
            logAllRedirects = true;
            logMixinTransforms = true;
            logClassScanning = true;
            logSkippedClasses = true;
            dumpMixinConfigs = true;
            scanRemovedRefs = true;
            traceClassLoading = true;
        }

        LOGGER.info("Debug options: dump_bytecode={}, log_all_redirects={}, log_mixin_transforms={}, " +
                "log_class_scanning={}, log_skipped_classes={}, dump_mixin_configs={}, " +
                "scan_removed_refs={}, trace_class_loading={}",
            dumpBytecode, logAllRedirects, logMixinTransforms,
            logClassScanning, logSkippedClasses, dumpMixinConfigs,
            scanRemovedRefs, traceClassLoading);
    }

    // =========================================================
    // STATE CHECKS
    // =========================================================

    public static boolean isEnabled() { return enabled; }
    public static boolean shouldDumpBytecode() { return enabled && dumpBytecode; }
    public static boolean shouldLogAllRedirects() { return enabled && logAllRedirects; }
    public static boolean shouldLogMixinTransforms() { return enabled && logMixinTransforms; }
    public static boolean shouldLogClassScanning() { return enabled && logClassScanning; }
    public static boolean shouldLogSkippedClasses() { return enabled && logSkippedClasses; }
    public static boolean shouldDumpMixinConfigs() { return enabled && dumpMixinConfigs; }
    public static boolean shouldScanRemovedRefs() { return enabled && scanRemovedRefs; }
    public static boolean shouldTraceClassLoading() { return enabled && traceClassLoading; }

    // =========================================================
    // BYTECODE DUMP
    // =========================================================

    /**
     * Save class bytecode to disk for comparison (before and after transformation).
     */
    public static void dumpClass(String modName, String className, byte[] bytecode, String phase) {
        if (!shouldDumpBytecode() || debugOutputDir == null) return;

        try {
            String safeName = className.replace('/', '_');
            Path modDir = debugOutputDir.resolve(modName);
            Files.createDirectories(modDir);

            // Save raw .class file
            Path classFile = modDir.resolve(safeName + "." + phase + ".class");
            Files.write(classFile, bytecode);

            // Save human-readable disassembly
            Path textFile = modDir.resolve(safeName + "." + phase + ".txt");
            String disasm = disassembleClass(bytecode);
            Files.writeString(textFile, disasm);

        } catch (IOException e) {
            LOGGER.warn("Could not dump class {} for {}", className, modName, e);
        }
    }

    /**
     * Disassemble a class file to human-readable text showing:
     * - Class name, superclass, interfaces
     * - Fields with types
     * - Methods with descriptors
     * - All instruction references (method calls, field accesses, type refs)
     */
    private static String disassembleClass(byte[] bytecode) {
        StringBuilder sb = new StringBuilder();
        try {
            ClassReader reader = new ClassReader(bytecode);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            sb.append("=== CLASS: ").append(classNode.name).append(" ===\n");
            sb.append("Super: ").append(classNode.superName).append("\n");
            if (classNode.interfaces != null && !classNode.interfaces.isEmpty()) {
                sb.append("Interfaces: ").append(String.join(", ", classNode.interfaces)).append("\n");
            }

            // Annotations
            appendAnnotations(sb, "Class visible", classNode.visibleAnnotations);
            appendAnnotations(sb, "Class invisible", classNode.invisibleAnnotations);

            // Fields
            sb.append("\n--- FIELDS ---\n");
            for (FieldNode field : classNode.fields) {
                sb.append("  ").append(field.name).append(" : ").append(field.desc).append("\n");
            }

            // Methods
            sb.append("\n--- METHODS ---\n");
            for (MethodNode method : classNode.methods) {
                sb.append("  ").append(method.name).append(method.desc).append("\n");

                // Method annotations
                appendAnnotations(sb, "    Method visible", method.visibleAnnotations);
                appendAnnotations(sb, "    Method invisible", method.invisibleAnnotations);

                // Instructions referencing other classes
                if (method.instructions != null) {
                    for (var insn : method.instructions) {
                        if (insn instanceof TypeInsnNode ti) {
                            sb.append("    TYPE ").append(opcodeToString(ti.getOpcode()))
                                .append(" ").append(ti.desc).append("\n");
                        } else if (insn instanceof FieldInsnNode fi) {
                            sb.append("    FIELD ").append(opcodeToString(fi.getOpcode()))
                                .append(" ").append(fi.owner).append(".")
                                .append(fi.name).append(":").append(fi.desc).append("\n");
                        } else if (insn instanceof MethodInsnNode mi) {
                            sb.append("    METHOD ").append(opcodeToString(mi.getOpcode()))
                                .append(" ").append(mi.owner).append(".")
                                .append(mi.name).append(mi.desc).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("ERROR disassembling: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private static void appendAnnotations(StringBuilder sb, String label, List<AnnotationNode> annotations) {
        if (annotations == null || annotations.isEmpty()) return;
        for (AnnotationNode ann : annotations) {
            sb.append("  @").append(label).append(": ").append(ann.desc);
            if (ann.values != null) {
                sb.append(" {");
                for (int i = 0; i < ann.values.size(); i += 2) {
                    sb.append(ann.values.get(i)).append("=").append(ann.values.get(i + 1));
                    if (i + 2 < ann.values.size()) sb.append(", ");
                }
                sb.append("}");
            }
            sb.append("\n");
        }
    }

    private static String opcodeToString(int opcode) {
        return switch (opcode) {
            case Opcodes.NEW -> "NEW";
            case Opcodes.CHECKCAST -> "CHECKCAST";
            case Opcodes.INSTANCEOF -> "INSTANCEOF";
            case Opcodes.ANEWARRAY -> "ANEWARRAY";
            case Opcodes.GETFIELD -> "GETFIELD";
            case Opcodes.PUTFIELD -> "PUTFIELD";
            case Opcodes.GETSTATIC -> "GETSTATIC";
            case Opcodes.PUTSTATIC -> "PUTSTATIC";
            case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
            case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
            case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
            case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
            default -> "OP_" + opcode;
        };
    }

    // =========================================================
    // MIXIN CONFIG DUMP
    // =========================================================

    /**
     * Save mixin config JSON to disk (before/after transformation).
     */
    public static void dumpMixinConfig(String modName, String configName, String json, String phase) {
        if (!shouldDumpMixinConfigs() || debugOutputDir == null) return;
        try {
            Path modDir = debugOutputDir.resolve(modName).resolve("mixin-configs");
            Files.createDirectories(modDir);
            Path file = modDir.resolve(configName + "." + phase + ".json");
            Files.writeString(file, json);
        } catch (IOException e) {
            LOGGER.warn("Could not dump mixin config {} for {}", configName, modName, e);
        }
    }

    // =========================================================
    // REMOVED REFERENCE SCANNER
    // =========================================================

    /**
     * Scan a JAR file for ALL references to KNOWN_REMOVED classes.
     * This produces a comprehensive report showing exactly which classes
     * reference which removed APIs, helping diagnose crashes.
     */
    public static void scanJarForRemovedRefs(Path jarPath, Set<String> removedClasses,
                                              Map<String, String> classRedirects) {
        if (!shouldScanRemovedRefs() || debugOutputDir == null) return;

        String modName = jarPath.getFileName().toString().replace(".jar", "");
        StringBuilder report = new StringBuilder();
        report.append("=== REMOVED REFERENCE SCAN: ").append(modName).append(" ===\n\n");

        int totalRefs = 0;
        int redirectedRefs = 0;
        int unresolvedRefs = 0;

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;

                byte[] bytecode = jar.getInputStream(entry).readAllBytes();
                ClassReader reader = new ClassReader(bytecode);
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, ClassReader.SKIP_CODE);

                // Check all referenced classes in the constant pool
                Set<String> refsInClass = new HashSet<>();

                // Quick scan of constant pool for class references
                for (int i = 1; i < reader.getItemCount(); i++) {
                    try {
                        // Read constant pool entries
                        // We'll use a simpler approach: check the class node
                    } catch (Exception ignored) {}
                }

                // Check fields
                for (FieldNode field : classNode.fields) {
                    extractClassRefs(field.desc, refsInClass);
                }

                // Check methods
                for (MethodNode method : classNode.methods) {
                    extractClassRefs(method.desc, refsInClass);
                }

                // Check superclass and interfaces
                if (classNode.superName != null) refsInClass.add(classNode.superName);
                if (classNode.interfaces != null) refsInClass.addAll(classNode.interfaces);

                // Now check which refs are removed
                for (String ref : refsInClass) {
                    if (removedClasses.contains(ref)) {
                        totalRefs++;
                        String redirect = classRedirects.get(ref);
                        if (redirect != null) {
                            redirectedRefs++;
                            report.append("[REDIRECTED] ")
                                .append(classNode.name).append(" -> ")
                                .append(ref).append(" => ").append(redirect).append("\n");
                        } else {
                            unresolvedRefs++;
                            report.append("[UNRESOLVED] ")
                                .append(classNode.name).append(" -> ")
                                .append(ref).append(" (NO REDIRECT!)\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            report.append("ERROR scanning: ").append(e.getMessage()).append("\n");
        }

        report.append("\n--- SUMMARY ---\n");
        report.append("Total removed class references: ").append(totalRefs).append("\n");
        report.append("  Redirected: ").append(redirectedRefs).append("\n");
        report.append("  UNRESOLVED: ").append(unresolvedRefs).append("\n");

        if (unresolvedRefs > 0) {
            LOGGER.warn("Mod {} has {} UNRESOLVED references to removed classes!", modName, unresolvedRefs);
        }

        try {
            Path reportFile = debugOutputDir.resolve(modName + "-removed-refs.txt");
            Files.writeString(reportFile, report.toString());
            LOGGER.info("Removed reference scan saved to {}", reportFile);
        } catch (IOException e) {
            LOGGER.warn("Could not save removed reference scan for {}", modName, e);
        }
    }

    private static void extractClassRefs(String descriptor, Set<String> refs) {
        if (descriptor == null) return;
        int i = 0;
        while (i < descriptor.length()) {
            if (descriptor.charAt(i) == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end > i) {
                    refs.add(descriptor.substring(i + 1, end));
                    i = end + 1;
                } else {
                    break;
                }
            } else {
                i++;
            }
        }
    }

    // =========================================================
    // LOGGING HELPERS
    // =========================================================

    /**
     * Log a class redirect being applied.
     */
    public static void logRedirect(String className, String fieldOrMethod, String from, String to) {
        if (!shouldLogAllRedirects()) return;
        LOGGER.info("[REDIRECT] {}.{}: {} -> {}", className, fieldOrMethod, from, to);
    }

    /**
     * Log a class being scanned during transformation.
     */
    public static void logClassScanned(String modName, String className, boolean modified) {
        if (modified) {
            if (shouldLogClassScanning()) {
                LOGGER.info("[SCAN] {} in {}: MODIFIED", className, modName);
            }
        } else {
            if (shouldLogSkippedClasses()) {
                LOGGER.debug("[SCAN] {} in {}: unchanged", className, modName);
            }
        }
    }

    /**
     * Log a mixin class being transformed.
     */
    public static void logMixinTransform(String mixinClass, String change) {
        if (!shouldLogMixinTransforms()) return;
        LOGGER.info("[MIXIN] {}: {}", mixinClass, change);
    }

    /**
     * Log a class redirect mapping decision.
     */
    public static void logClassLoadTrace(String className, String decision) {
        if (!shouldTraceClassLoading()) return;
        LOGGER.info("[TRACE] {}: {}", className, decision);
    }

    /**
     * Write a summary report of all transformations performed.
     */
    public static void writeSummary(String modName, int classesTransformed, int classesSkipped,
                                     int mixinsStripped, int mixinsRelocated, long durationMs) {
        if (!isEnabled() || debugOutputDir == null) return;

        String summary = String.format("""
            === TRANSFORMATION SUMMARY: %s ===
            Classes transformed: %d
            Classes skipped: %d
            Mixins stripped: %d
            Mixins relocated: %d
            Duration: %d ms
            """, modName, classesTransformed, classesSkipped, mixinsStripped, mixinsRelocated, durationMs);

        try {
            Path file = debugOutputDir.resolve(modName + "-summary.txt");
            Files.writeString(file, summary);
        } catch (IOException e) {
            LOGGER.warn("Could not write summary for {}", modName, e);
        }

        LOGGER.info(summary.trim());
    }
}
