/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import com.retromod.core.*;
import com.retromod.embedder.*;
import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.shim.ShimRegistry;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Ahead-of-Time (AOT) Compiler for RetroMod.
 * 
 * Instead of transforming bytecode at runtime (JIT), this compiler:
 * 1. Pre-transforms all classes in a mod JAR
 * 2. Embeds required shims for removed APIs
 * 3. Saves the transformed JAR to a cache or inside the original
 * 4. Falls back to JIT only for obfuscated code that can't be analyzed
 * 
 * Benefits:
 * - Faster subsequent launches (no runtime transformation)
 * - Can validate transformations before running
 * - Can detect compatibility issues early
 * - Transformed mods can be distributed
 */
public class AotCompiler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-aot");
    
    // Cache directory for pre-compiled mods
    private static final Path AOT_CACHE_DIR = Path.of("config/retromod/aot-cache");
    
    // Manifest key indicating this JAR was AOT compiled
    private static final String AOT_MANIFEST_KEY = "RetroMod-AOT-Version";
    
    // Current AOT compiler version (bump when shims change)
    private static final String AOT_VERSION = "1.0.0-beta.1";
    
    private final ShimRegistry shimRegistry;
    private final RetroModTransformer transformer;
    private final ModVersionDetector versionDetector;
    private final ApiEmbedder apiEmbedder;
    private final String targetMcVersion;
    
    // Statistics
    private int classesTransformed = 0;
    private int classesSkipped = 0;
    private int classesObfuscated = 0;
    
    public AotCompiler(ShimRegistry shimRegistry, String targetMcVersion) {
        this.shimRegistry = shimRegistry;
        this.transformer = RetroModTransformer.getInstance();
        this.versionDetector = new ModVersionDetector();
        this.apiEmbedder = new ApiEmbedder();
        this.targetMcVersion = targetMcVersion;
        
        try {
            Files.createDirectories(AOT_CACHE_DIR);
        } catch (IOException e) {
            LOGGER.warn("Could not create AOT cache directory", e);
        }
    }
    
    /**
     * Process a mod JAR using AOT compilation.
     * 
     * @param modJar Path to the mod JAR
     * @return Path to the AOT-compiled JAR (may be cached version)
     */
    public Path compileModAot(Path modJar) throws IOException {
        LOGGER.info("AOT compiling: {}", modJar.getFileName());
        
        // Check if already AOT compiled
        Path cachedJar = getCachedJar(modJar);
        if (cachedJar != null && isValidCache(cachedJar, modJar)) {
            LOGGER.info("Using cached AOT compilation for: {}", modJar.getFileName());
            return cachedJar;
        }
        
        // Analyze the mod
        ModVersionInfo modInfo = versionDetector.detectVersion(modJar);
        if (modInfo == null) {
            LOGGER.warn("Could not analyze mod: {}", modJar.getFileName());
            return modJar;  // Return original
        }
        
        // Check if transformation is needed
        if (!modInfo.needsTransformation(targetMcVersion)) {
            LOGGER.info("Mod {} is already compatible, skipping AOT", modInfo.modId());
            return modJar;
        }
        
        // Create backup before transformation
        backupOriginalMod(modJar);
        
        // Find shim chain
        List<VersionShim> shimChain = shimRegistry.findShimChain(
            modInfo.modLoaderType(),
            modInfo.targetMcVersion(),
            targetMcVersion
        );
        
        if (shimChain.isEmpty()) {
            LOGGER.warn("No shim chain available for {} ({} -> {})", 
                modInfo.modId(), modInfo.targetMcVersion(), targetMcVersion);
            return modJar;
        }
        
        // Register all shims in chain
        for (VersionShim shim : shimChain) {
            LOGGER.debug("Applying shim: {}", shim.getShimName());
            shim.registerRedirects(transformer);
        }
        
        // Perform AOT compilation
        Path outputJar = AOT_CACHE_DIR.resolve(
            modJar.getFileName().toString().replace(".jar", "-aot.jar")
        );
        
        compileJar(modJar, outputJar, modInfo);
        
        LOGGER.info("AOT compilation complete: {} classes transformed, {} skipped, {} obfuscated (JIT fallback)",
            classesTransformed, classesSkipped, classesObfuscated);
        
        return outputJar;
    }
    
    /**
     * Backup original mod JAR before transformation.
     * Backups are stored in mods/retromod-backups/
     */
    private void backupOriginalMod(Path modJar) {
        try {
            Path backupFolder = modJar.getParent().resolve("retromod-backups");
            Files.createDirectories(backupFolder);
            
            String fileName = modJar.getFileName().toString();
            Path backupPath = backupFolder.resolve(fileName);
            
            // Don't overwrite existing backups
            if (!Files.exists(backupPath)) {
                Files.copy(modJar, backupPath);
                LOGGER.info("Created backup: {}", backupPath);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not create backup for: {}", modJar.getFileName(), e);
        }
    }
    
    /**
     * Compile all mods in a folder using AOT.
     * Returns immediately, compilation happens in background.
     */
    public CompletableFuture<List<Path>> compileAllModsAsync(Path modsFolder) {
        return CompletableFuture.supplyAsync(() -> {
            List<Path> compiled = new ArrayList<>();
            
            try {
                File[] modFiles = modsFolder.toFile().listFiles(
                    (dir, name) -> name.endsWith(".jar") && !name.contains("-aot")
                );
                
                if (modFiles == null) return compiled;
                
                for (File modFile : modFiles) {
                    try {
                        Path result = compileModAot(modFile.toPath());
                        compiled.add(result);
                    } catch (Exception e) {
                        LOGGER.error("Failed to AOT compile: {}", modFile.getName(), e);
                        compiled.add(modFile.toPath());  // Use original
                    }
                }
            } catch (Exception e) {
                LOGGER.error("AOT compilation batch failed", e);
            }
            
            return compiled;
        });
    }
    
    /**
     * Compile all mods synchronously (blocks until complete).
     * Shows progress for UI feedback.
     */
    public List<AotResult> compileAllModsSync(Path modsFolder, ProgressCallback callback) {
        List<AotResult> results = new ArrayList<>();
        
        File[] modFiles = modsFolder.toFile().listFiles(
            (dir, name) -> name.endsWith(".jar") && !name.contains("-aot")
                && !name.startsWith("retromod-")  // Don't AOT-compile RetroMod itself
                && !name.contains("-retromod")     // Already transformed — JIT handles any gaps
        );
        
        if (modFiles == null || modFiles.length == 0) {
            return results;
        }
        
        int total = modFiles.length;
        int current = 0;
        
        for (File modFile : modFiles) {
            current++;
            
            if (callback != null) {
                callback.onProgress(current, total, modFile.getName());
            }
            
            try {
                long startTime = System.currentTimeMillis();
                Path result = compileModAot(modFile.toPath());
                long duration = System.currentTimeMillis() - startTime;
                
                results.add(new AotResult(
                    modFile.toPath(),
                    result,
                    AotResult.Status.SUCCESS,
                    duration,
                    classesTransformed,
                    classesObfuscated
                ));
                
                // Reset counters
                classesTransformed = 0;
                classesSkipped = 0;
                classesObfuscated = 0;
                
            } catch (Exception e) {
                results.add(new AotResult(
                    modFile.toPath(),
                    modFile.toPath(),
                    AotResult.Status.FAILED,
                    0,
                    0,
                    0
                ));
                LOGGER.error("AOT compilation failed: {}", modFile.getName(), e);
            }
        }
        
        return results;
    }
    
    /**
     * Perform JAR compilation with integrity verification and single retry.
     */
    private void compileJar(Path inputJar, Path outputJar, ModVersionInfo modInfo) throws IOException {
        compileJarCore(inputJar, outputJar, modInfo);

        // Verify jar integrity — if corrupted, restore from original and retry once
        if (!verifyJarIntegrity(outputJar)) {
            LOGGER.error("AOT output jar is corrupted! Restoring from original and retrying...");
            compileJarRetry(inputJar, outputJar, modInfo);
        }
    }

    /**
     * Core JAR compilation logic (no verification/retry — called by compileJar and compileJarRetry).
     */
    private void compileJarCore(Path inputJar, Path outputJar, ModVersionInfo modInfo) throws IOException {
        // Collect all classes and analyze them first
        Map<String, byte[]> transformedClasses = new LinkedHashMap<>();
        Map<String, byte[]> originalResources = new LinkedHashMap<>();
        Set<String> obfuscatedClasses = new HashSet<>();
        
        try (JarFile jar = new JarFile(inputJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (entry.isDirectory()) continue;
                
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] data = is.readAllBytes();
                    
                    if (entry.getName().endsWith(".class")) {
                        // Analyze and potentially transform class
                        String className = entry.getName().replace(".class", "");
                        
                        if (shouldTransformClass(className, modInfo)) {
                            if (isObfuscated(data)) {
                                // Mark for JIT fallback
                                obfuscatedClasses.add(className);
                                transformedClasses.put(entry.getName(), data);
                                classesObfuscated++;
                            } else {
                                // AOT transform
                                byte[] transformed = transformClassAot(data, className);
                                transformedClasses.put(entry.getName(), transformed);
                                classesTransformed++;
                            }
                        } else {
                            transformedClasses.put(entry.getName(), data);
                            classesSkipped++;
                        }
                    } else {
                        originalResources.put(entry.getName(), data);
                    }
                }
            }
        }
        
        // Collect embedded shims
        Map<String, byte[]> embeddedShims = collectEmbeddedShims(modInfo);
        
        // Write output JAR
        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputJar.toFile())))) {
            
            // Write manifest with AOT marker
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue(AOT_MANIFEST_KEY, AOT_VERSION);
            manifest.getMainAttributes().putValue("RetroMod-Source-Version", modInfo.targetMcVersion());
            manifest.getMainAttributes().putValue("RetroMod-Target-Version", targetMcVersion);
            manifest.getMainAttributes().putValue("RetroMod-Compiled-Time", String.valueOf(System.currentTimeMillis()));
            manifest.getMainAttributes().putValue("RetroMod-Source-Hash", computeHash(inputJar));
            manifest.getMainAttributes().putValue("RetroMod-Self-Hash", getRetroModSelfHash());
            manifest.getMainAttributes().putValue("RetroMod-Transformed", "true");
            manifest.getMainAttributes().putValue("RetroMod-MixinProcessed", "true");
            
            // Add obfuscated class list for JIT fallback
            if (!obfuscatedClasses.isEmpty()) {
                manifest.getMainAttributes().putValue("RetroMod-JIT-Classes", 
                    String.join(",", obfuscatedClasses));
            }
            
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            manifest.write(jos);
            jos.closeEntry();
            
            // Write transformed classes
            for (Map.Entry<String, byte[]> entry : transformedClasses.entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
            
            // Write original resources, processing mixin configs to strip broken entries
            MixinCompatibilityTransformer mixinTransformer = new MixinCompatibilityTransformer(transformer);
            for (Map.Entry<String, byte[]> entry : originalResources.entrySet()) {
                if (entry.getKey().equals("META-INF/MANIFEST.MF")) continue;  // Already wrote

                byte[] data = entry.getValue();

                // Process mixin config JSON files to strip broken mixin entries
                if (entry.getKey().endsWith(".mixins.json") || entry.getKey().endsWith("mixin.json")) {
                    try {
                        String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                        String transformed = mixinTransformer.transformMixinConfig(json, transformedClasses);
                        if (!transformed.equals(json)) {
                            LOGGER.info("Processed mixin config: {} (stripped broken entries)", entry.getKey());
                            data = transformed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to process mixin config {}: {}", entry.getKey(), e.getMessage());
                    }
                }

                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(data);
                jos.closeEntry();
            }
            
            // Write embedded shims
            for (Map.Entry<String, byte[]> entry : embeddedShims.entrySet()) {
                jos.putNextEntry(new JarEntry("retromod_embedded/" + entry.getKey() + ".class"));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
            
            // Write AOT metadata
            writeAotMetadata(jos, modInfo, obfuscatedClasses);
        }
    }

    /**
     * Verifies that a jar file is not corrupted by reading every entry.
     * Returns true if the jar is valid, false if any entry is corrupt.
     */
    private boolean verifyJarIntegrity(Path jarPath) {
        try (JarFile jf = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            int verified = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                try (InputStream is = jf.getInputStream(entry)) {
                    byte[] data = is.readAllBytes();
                    // Verify class files have correct magic bytes
                    if (entry.getName().endsWith(".class") && data.length >= 4) {
                        if (data[0] != (byte) 0xCA || data[1] != (byte) 0xFE ||
                            data[2] != (byte) 0xBA || data[3] != (byte) 0xBE) {
                            LOGGER.error("Corrupt class file in jar: {} (bad magic bytes)", entry.getName());
                            return false;
                        }
                    }
                    verified++;
                } catch (Exception e) {
                    LOGGER.error("Corrupt entry in jar: {} - {}", entry.getName(), e.getMessage());
                    return false;
                }
            }
            LOGGER.info("Jar integrity verified: {} entries OK", verified);
            return true;
        } catch (Exception e) {
            LOGGER.error("Jar integrity check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Retry compilation after a corruption failure.
     * Does NOT re-verify to avoid infinite recursion (compileJar → retry → compileJar → retry...).
     */
    private void compileJarRetry(Path inputJar, Path outputJar, ModVersionInfo modInfo) throws IOException {
        LOGGER.info("Retrying AOT compilation with fresh copy...");
        Files.deleteIfExists(outputJar);
        try {
            compileJarCore(inputJar, outputJar, modInfo);
            if (!verifyJarIntegrity(outputJar)) {
                LOGGER.error("Retry also produced corrupted jar. Falling back to original jar copy.");
                Files.copy(inputJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Retry compilation failed: {}. Falling back to original jar copy.", e.getMessage());
            Files.copy(inputJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Compile a class using hybrid AOT/JIT approach.
     * Falls back to partial JIT only for specific code regions that can't be analyzed.
     */
    private byte[] transformClassAot(byte[] classBytes, String className) {
        try {
            // Use hybrid compiler for smart partial AOT
            HybridCompiler hybridCompiler = new HybridCompiler(transformer);
            HybridCompiler.HybridCompilationResult result = hybridCompiler.compileClass(classBytes, className);
            
            // Update stats
            if (result.methodsJitOnly() > 0) {
                classesObfuscated++;  // Track classes that needed any JIT
            }
            
            // Log compilation mode breakdown
            if (result.methodsPartialAot() > 0 || result.methodsJitOnly() > 0) {
                LOGGER.debug("Class {} compiled: {} full AOT, {} partial, {} JIT-only methods",
                    className, result.methodsFullyAot(), result.methodsPartialAot(), result.methodsJitOnly());
            }
            
            return result.bytecode();
            
        } catch (Exception e) {
            // Fallback to simple transformation
            LOGGER.warn("Hybrid compilation failed for {}, using simple transform", className);
            return transformClassSimple(classBytes, className);
        }
    }
    
    /**
     * Simple transformation fallback - transforms entire class without analysis.
     */
    private byte[] transformClassSimple(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        // Use SafeClassWriter to handle getCommonSuperClass safely in modded environments
        ClassWriter writer = new SafeAotClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        try {
            ClassVisitor visitor = new AotClassVisitor(Opcodes.ASM9, writer, className);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable e) {
            // Fallback: try with COMPUTE_MAXS only
            ClassWriter fallbackWriter = new SafeAotClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = new AotClassVisitor(Opcodes.ASM9, fallbackWriter, className);
            reader.accept(visitor, 0);
            return fallbackWriter.toByteArray();
        }
    }

    /**
     * Safe ClassWriter that handles getCommonSuperClass without crashing
     * when mod classes aren't available via Class.forName().
     */
    private static class SafeAotClassWriter extends ClassWriter {
        public SafeAotClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Throwable e) {
                // Can't resolve — fall back to java/lang/Object
                return "java/lang/Object";
            }
        }
    }
    
    /**
     * ASM ClassVisitor for AOT transformation.
     * More thorough than JIT - analyzes and rewrites everything.
     */
    private class AotClassVisitor extends ClassVisitor {
        private final String className;
        
        public AotClassVisitor(int api, ClassVisitor classVisitor, String className) {
            super(api, classVisitor);
            this.className = className;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new AotMethodVisitor(api, mv);
        }
        
        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            // Check superclass redirects first (class-to-interface migrations)
            var superRedirect = transformer.getSuperclassRedirects() != null
                ? transformer.getSuperclassRedirects().get(superName) : null;

            String newSuper;
            String[] newInterfaces;

            if (superRedirect != null) {
                LOGGER.debug("AOT: Rewriting superclass of {} from {} to {}",
                        name, superName, superRedirect.newSuperclass());
                newSuper = superRedirect.newSuperclass();
                // Merge existing interfaces with additional ones from redirect
                Set<String> merged = new LinkedHashSet<>();
                if (interfaces != null) {
                    for (String iface : interfaces) {
                        merged.add(transformer.getClassRedirects().getOrDefault(iface, iface));
                    }
                }
                if (superRedirect.addInterfaces() != null) {
                    Collections.addAll(merged, superRedirect.addInterfaces());
                }
                newInterfaces = merged.toArray(new String[0]);
            } else {
                // Standard class redirect on superclass
                newSuper = transformer.getClassRedirects().getOrDefault(superName, superName);
                if (interfaces != null) {
                    newInterfaces = new String[interfaces.length];
                    for (int i = 0; i < interfaces.length; i++) {
                        newInterfaces[i] = transformer.getClassRedirects()
                            .getOrDefault(interfaces[i], interfaces[i]);
                    }
                } else {
                    newInterfaces = interfaces;
                }
            }

            super.visit(version, access, name, signature, newSuper, newInterfaces);
        }
    }
    
    /**
     * ASM MethodVisitor for AOT transformation.
     */
    private class AotMethodVisitor extends MethodVisitor {
        
        public AotMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, 
                String descriptor, boolean isInterface) {
            
            // Check for method redirect
            var key = new RetroModTransformer.MethodKey(owner, name, descriptor);
            var target = transformer.getMethodRedirects().get(key);
            
            if (target != null) {
                // Determine if this is an instance→static redirect
                int redirectOpcode = opcode;
                boolean redirectIsInterface = isInterface;

                if ((opcode == org.objectweb.asm.Opcodes.INVOKEVIRTUAL
                    || opcode == org.objectweb.asm.Opcodes.INVOKEINTERFACE)
                    && target.isInstanceToStatic(descriptor)) {
                    redirectOpcode = org.objectweb.asm.Opcodes.INVOKESTATIC;
                    redirectIsInterface = false;
                }

                super.visitMethodInsn(redirectOpcode, target.owner(), target.name(),
                    target.desc(), redirectIsInterface);
            } else {
                // Check for class redirect
                String newOwner = transformer.getClassRedirects().getOrDefault(owner, owner);
                super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface);
            }
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Check field redirects first (more specific)
            var fieldKey = new RetroModTransformer.FieldKey(owner, name);
            var fieldTarget = transformer.getFieldRedirects().get(fieldKey);
            if (fieldTarget != null) {
                // Check if this is a field-to-method redirect
                if (fieldTarget.newDesc() != null && fieldTarget.newDesc().startsWith("(")) {
                    // Convert field access to static method call
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, fieldTarget.owner(),
                        fieldTarget.name(), fieldTarget.newDesc(), false);
                    LOGGER.trace("AOT: Redirected field {}.{} -> method {}.{}{}",
                            owner, name, fieldTarget.owner(), fieldTarget.name(), fieldTarget.newDesc());
                    return;
                }
                // Standard field-to-field redirect
                String newDesc = (fieldTarget.newDesc() != null) ? fieldTarget.newDesc() : descriptor;
                super.visitFieldInsn(opcode, fieldTarget.owner(), fieldTarget.name(), newDesc);
                return;
            }

            // Fall back to class redirect on owner
            String newOwner = transformer.getClassRedirects().getOrDefault(owner, owner);
            super.visitFieldInsn(opcode, newOwner, name, descriptor);
        }
        
        @Override
        public void visitTypeInsn(int opcode, String type) {
            String newType = transformer.getClassRedirects().getOrDefault(type, type);
            super.visitTypeInsn(opcode, newType);
        }
        
        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type type) {
                if (type.getSort() == Type.OBJECT) {
                    String newName = transformer.getClassRedirects()
                        .getOrDefault(type.getInternalName(), type.getInternalName());
                    super.visitLdcInsn(Type.getObjectType(newName));
                    return;
                }
            }
            super.visitLdcInsn(value);
        }
        
        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            // Handle array type redirects
            String newDesc = redirectDescriptor(descriptor);
            super.visitMultiANewArrayInsn(newDesc, numDimensions);
        }
        
        private String redirectDescriptor(String descriptor) {
            // Simple descriptor redirection
            for (var entry : transformer.getClassRedirects().entrySet()) {
                descriptor = descriptor.replace(
                    "L" + entry.getKey() + ";",
                    "L" + entry.getValue() + ";"
                );
            }
            return descriptor;
        }
    }
    
    /**
     * Check if a class appears to be obfuscated.
     * Obfuscated classes need JIT fallback because we can't predict
     * all code paths statically.
     */
    private boolean isObfuscated(byte[] classBytes) {
        try {
            // OPTIMIZED: Use ClassReader only for class name (constant pool parse)
            // then do lightweight method name scan via SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES
            ClassReader reader = new ClassReader(classBytes);
            String className = reader.getClassName();

            // 1. Very short class names (a, b, aa, ab, etc.)
            String simpleName = className.substring(className.lastIndexOf('/') + 1);
            if (simpleName.length() <= 2 && isAllLowercase(simpleName)) {
                return true;
            }

            // 2. Classes in default package
            if (!className.contains("/")) {
                return true;
            }

            // 3. Check method names (skip code, debug info, and frames for speed)
            final boolean[] hasObfuscatedMethods = {false};
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (name.length() <= 2 && !name.equals("<init>") && !name.equals("<clinit>")) {
                        hasObfuscatedMethods[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            return hasObfuscatedMethods[0];

        } catch (Exception e) {
            return false;
        }
    }

    /** Fast lowercase check without regex allocation */
    private static boolean isAllLowercase(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        return !s.isEmpty();
    }
    
    /**
     * Check if a class should be transformed based on its package.
     */
    private boolean shouldTransformClass(String className, ModVersionInfo modInfo) {
        // Don't transform Minecraft classes
        if (className.startsWith("net/minecraft/") || 
            className.startsWith("com/mojang/")) {
            return false;
        }
        
        // Don't transform mod loader classes
        if (className.startsWith("net/fabricmc/") ||
            className.startsWith("net/minecraftforge/") ||
            className.startsWith("net/neoforged/")) {
            return false;
        }
        
        // Transform if in mod's packages
        for (String pkg : modInfo.modPackages()) {
            if (className.startsWith(pkg.replace('.', '/'))) {
                return true;
            }
        }
        
        // Transform if it's likely mod code
        return true;
    }
    
    /**
     * Collect all shim classes that need to be embedded.
     */
    private Map<String, byte[]> collectEmbeddedShims(ModVersionInfo modInfo) throws IOException {
        Map<String, byte[]> shims = new HashMap<>();
        
        // Get shim chain
        List<VersionShim> shimChain = shimRegistry.findShimChain(
            modInfo.modLoaderType(),
            modInfo.targetMcVersion(),
            targetMcVersion
        );
        
        // Collect shim classes from each shim in chain
        for (VersionShim shim : shimChain) {
            for (String shimClass : shim.getShimClasses()) {
                try {
                    String resourcePath = shimClass.replace('.', '/') + ".class";
                    InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                    if (is != null) {
                        shims.put(shimClass.replace('.', '/'), is.readAllBytes());
                        is.close();
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not load shim class: {}", shimClass);
                }
            }
        }
        
        return shims;
    }
    
    /**
     * Write AOT metadata file to the JAR.
     */
    private void writeAotMetadata(JarOutputStream jos, ModVersionInfo modInfo,
            Set<String> obfuscatedClasses) throws IOException {
        
        StringBuilder sb = new StringBuilder();
        sb.append("# RetroMod AOT Compilation Metadata\n");
        sb.append("aot_version=").append(AOT_VERSION).append("\n");
        sb.append("source_mc_version=").append(modInfo.targetMcVersion()).append("\n");
        sb.append("target_mc_version=").append(targetMcVersion).append("\n");
        sb.append("mod_id=").append(modInfo.modId()).append("\n");
        sb.append("mod_loader=").append(modInfo.modLoaderType()).append("\n");
        sb.append("compiled_time=").append(System.currentTimeMillis()).append("\n");
        sb.append("classes_transformed=").append(classesTransformed).append("\n");
        sb.append("classes_jit_fallback=").append(obfuscatedClasses.size()).append("\n");
        
        if (!obfuscatedClasses.isEmpty()) {
            sb.append("\n# Classes requiring JIT transformation (obfuscated)\n");
            for (String cls : obfuscatedClasses) {
                sb.append("jit_class=").append(cls).append("\n");
            }
        }
        
        jos.putNextEntry(new JarEntry("retromod_aot.properties"));
        jos.write(sb.toString().getBytes());
        jos.closeEntry();
    }
    
    /**
     * Get cached JAR path if it exists.
     */
    private Path getCachedJar(Path originalJar) {
        Path cached = AOT_CACHE_DIR.resolve(
            originalJar.getFileName().toString().replace(".jar", "-aot.jar")
        );
        return Files.exists(cached) ? cached : null;
    }
    
    /**
     * Check if cached JAR is still valid.
     */
    private boolean isValidCache(Path cachedJar, Path originalJar) {
        try (JarFile jar = new JarFile(cachedJar.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return false;

            // Check AOT version
            String aotVersion = manifest.getMainAttributes().getValue(AOT_MANIFEST_KEY);
            if (!AOT_VERSION.equals(aotVersion)) return false;

            // Check RetroMod's own hash — invalidate cache if RetroMod itself was updated
            // (redirect maps or shim code may have changed)
            String cachedRetroModHash = manifest.getMainAttributes().getValue("RetroMod-Self-Hash");
            String currentRetroModHash = getRetroModSelfHash();
            if (cachedRetroModHash != null && !cachedRetroModHash.equals(currentRetroModHash)) {
                LOGGER.info("RetroMod itself was updated — invalidating AOT cache for {}",
                    originalJar.getFileName());
                return false;
            }

            // Check source hash
            String cachedHash = manifest.getMainAttributes().getValue("RetroMod-Source-Hash");
            String currentHash = computeHash(originalJar);

            return cachedHash != null && cachedHash.equals(currentHash);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get a hash of RetroMod's own JAR to detect when RetroMod is updated.
     * This ensures AOT caches are invalidated when redirect maps change.
     */
    private String getRetroModSelfHash() {
        try {
            // Get the location of this class's JAR
            var location = AotCompiler.class.getProtectionDomain().getCodeSource();
            if (location != null && location.getLocation() != null) {
                Path selfJar = Path.of(location.getLocation().toURI());
                if (Files.isRegularFile(selfJar)) {
                    return computeHash(selfJar);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not compute RetroMod self-hash: {}", e.getMessage());
        }
        // Fallback: use AOT_VERSION string as hash (less precise but functional)
        return AOT_VERSION;
    }
    
    /**
     * Compute SHA-256 hash of a file.
     */
    private String computeHash(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                int v = b & 0xFF;
                sb.append(Character.forDigit(v >>> 4, 16));
                sb.append(Character.forDigit(v & 0x0F, 16));
            }
            return sb.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Clear the AOT cache.
     */
    public void clearCache() throws IOException {
        if (Files.exists(AOT_CACHE_DIR)) {
            Files.walk(AOT_CACHE_DIR)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.warn("Could not delete: {}", path);
                    }
                });
        }
        Files.createDirectories(AOT_CACHE_DIR);
    }
    
    // --- Callback interfaces ---
    
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String currentFile);
    }
    
    /**
     * Result of AOT compilation for a single mod.
     */
    public record AotResult(
        Path originalJar,
        Path compiledJar,
        Status status,
        long compilationTimeMs,
        int classesTransformed,
        int classesJitFallback
    ) {
        public enum Status {
            SUCCESS,
            CACHED,
            SKIPPED,
            FAILED
        }
    }
}
