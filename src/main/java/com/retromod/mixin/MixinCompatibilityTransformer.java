/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.mixin;

import com.retromod.core.RetroModTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.*;

/**
 * Mixin Compatibility Layer for RetroMod.
 * 
 * Problem: Mixins target specific methods by name. When Minecraft renames methods
 * (like getWorld -> getEntityWorld), Mixins break because they can't find their targets.
 * 
 * Solution: Transform Mixin configuration and @Inject/@Redirect annotations
 * to use the new method names before the Mixin system processes them.
 * 
 * This handles:
 * 1. @Inject, @Redirect, @ModifyArg, @ModifyVariable annotations
 * 2. @At target descriptors
 * 3. Mixin config JSON files
 * 4. refmap.json remapping
 */
public class MixinCompatibilityTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-mixin");
    
    // Mixin annotation descriptors
    private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG_DESC = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_VAR_DESC = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    private static final String MODIFY_CONST_DESC = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    private static final String MODIFY_ARGS_DESC = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String AT_DESC = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW_DESC = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE_DESC = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR_DESC = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER_DESC = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    
    // Compiled patterns (avoid recompiling on every call)
    private static final Pattern ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"");
    private static final Map<String, Pattern> ARRAY_PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> JSON_STRING_PATTERN_CACHE = new ConcurrentHashMap<>();

    private final RetroModTransformer transformer;

    // Maps old method references to new ones for Mixin targets
    private final Map<String, String> methodTargetRedirects = new HashMap<>();
    
    public MixinCompatibilityTransformer(RetroModTransformer transformer) {
        this.transformer = transformer;
        buildMixinRedirects();
    }
    
    /**
     * Build redirect maps specifically for Mixin annotations.
     * Mixin uses different naming conventions than bytecode.
     */
    private void buildMixinRedirects() {
        // Convert transformer redirects to Mixin format
        for (var entry : transformer.getMethodRedirects().entrySet()) {
            var key = entry.getKey();
            var target = entry.getValue();
            
            // Mixin format: "method" or "Lowner;method(desc)return" 
            String oldRef = key.name();
            String newRef = target.name();
            
            if (!oldRef.equals(newRef)) {
                methodTargetRedirects.put(oldRef, newRef);
                
                // Also add full reference format
                String oldFull = "L" + key.owner() + ";" + key.name() + key.desc();
                String newFull = "L" + target.owner() + ";" + target.name() + target.desc();
                methodTargetRedirects.put(oldFull, newFull);
            }
        }
        
        LOGGER.info("Built {} Mixin target redirects", methodTargetRedirects.size());
    }
    
    /**
     * Transform a Mixin class to update its target references.
     */
    public byte[] transformMixinClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        
        boolean modified = false;
        
        // Check if this is actually a Mixin class
        if (!isMixinClass(classNode)) {
            return classBytes;
        }
        
        LOGGER.debug("Transforming Mixin class: {}", classNode.name);
        
        // Transform class-level annotations (@Mixin targets)
        // @Mixin uses @Retention(CLASS) → check both visible and invisible annotations
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.visibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) {
                    modified |= transformMixinAnnotation(annotation);
                }
            }
        }
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) {
                    modified |= transformMixinAnnotation(annotation);
                }
            }
        }
        
        // Transform method-level annotations
        for (MethodNode method : classNode.methods) {
            modified |= transformMethodAnnotations(method);
        }
        
        // Transform field-level annotations (@Shadow, @Accessor)
        for (FieldNode field : classNode.fields) {
            modified |= transformFieldAnnotations(field);
        }

        // Apply class redirects to ALL bytecode within the mixin class.
        // This is critical: mixin methods get injected into target classes,
        // so any class references in the mixin body (field accesses, type casts,
        // method calls, etc.) must also be rewritten to the new class names.
        Map<String, String> classRedirects = transformer.getClassRedirects();
        if (!classRedirects.isEmpty()) {
            modified |= rewriteMixinBytecode(classNode, classRedirects);
        }

        if (!modified) {
            return classBytes;
        }

        // Write modified class using ClassRemapper for thorough class redirect coverage
        ClassWriter writer = new ClassWriter(0);
        if (!classRedirects.isEmpty()) {
            org.objectweb.asm.commons.Remapper remapper = new org.objectweb.asm.commons.Remapper() {
                @Override
                public String map(String internalName) {
                    return classRedirects.getOrDefault(internalName, internalName);
                }
            };
            org.objectweb.asm.commons.ClassRemapper remappingVisitor =
                new org.objectweb.asm.commons.ClassRemapper(writer, remapper);
            classNode.accept(remappingVisitor);
        } else {
            classNode.accept(writer);
        }
        return writer.toByteArray();
    }

    /**
     * Check mixin bytecode for references to classes that have redirects.
     * Returns true if any references were found (indicating the ClassRemapper
     * will make changes when writing).
     */
    private boolean rewriteMixinBytecode(ClassNode classNode, Map<String, String> classRedirects) {
        boolean hasRedirectable = false;

        // Check superclass
        if (classNode.superName != null && classRedirects.containsKey(classNode.superName)) {
            LOGGER.debug("Mixin {} superclass {} will be redirected to {}",
                classNode.name, classNode.superName, classRedirects.get(classNode.superName));
            hasRedirectable = true;
        }

        // Check interfaces
        if (classNode.interfaces != null) {
            for (String iface : classNode.interfaces) {
                if (classRedirects.containsKey(iface)) {
                    hasRedirectable = true;
                }
            }
        }

        // Check fields
        for (FieldNode field : classNode.fields) {
            if (field.desc != null) {
                for (String oldClass : classRedirects.keySet()) {
                    if (field.desc.contains(oldClass)) {
                        LOGGER.debug("Mixin {} field {}: {} contains redirect target {}",
                            classNode.name, field.name, field.desc, oldClass);
                        hasRedirectable = true;
                    }
                }
            }
        }

        // Check method bodies
        for (MethodNode method : classNode.methods) {
            // Check method descriptor
            if (method.desc != null) {
                for (String oldClass : classRedirects.keySet()) {
                    if (method.desc.contains(oldClass)) {
                        hasRedirectable = true;
                    }
                }
            }

            // Check instructions
            if (method.instructions != null) {
                for (var insn : method.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.TypeInsnNode typeInsn) {
                        if (classRedirects.containsKey(typeInsn.desc)) {
                            hasRedirectable = true;
                        }
                    } else if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsn) {
                        if (classRedirects.containsKey(fieldInsn.owner)) {
                            hasRedirectable = true;
                        }
                        for (String oldClass : classRedirects.keySet()) {
                            if (fieldInsn.desc.contains(oldClass)) {
                                hasRedirectable = true;
                            }
                        }
                    } else if (insn instanceof org.objectweb.asm.tree.MethodInsnNode methodInsn) {
                        if (classRedirects.containsKey(methodInsn.owner)) {
                            hasRedirectable = true;
                        }
                    }
                }
            }
        }

        if (hasRedirectable) {
            LOGGER.info("Mixin class {} has references to redirected classes — rewriting bytecode",
                classNode.name);
        }

        return hasRedirectable;
    }
    
    /**
     * Check if a class is a Mixin.
     * Note: @Mixin has @Retention(CLASS), so it appears in invisibleAnnotations, not visibleAnnotations.
     */
    private boolean isMixinClass(ClassNode classNode) {
        // Check visible annotations first (some compilers/versions may place it here)
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.visibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) {
                    return true;
                }
            }
        }
        // @Mixin uses @Retention(CLASS) → stored as RuntimeInvisibleAnnotation
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Transform @Mixin annotation targets.
     */
    private boolean transformMixinAnnotation(AnnotationNode annotation) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            if ("targets".equals(key) && value instanceof List<?> targets) {
                List<String> newTargets = new ArrayList<>();
                for (Object target : targets) {
                    if (target instanceof String s) {
                        String redirected = redirectClassName(s);
                        newTargets.add(redirected);
                        if (!s.equals(redirected)) {
                            modified = true;
                            LOGGER.debug("Redirected Mixin target: {} -> {}", s, redirected);
                        }
                    } else {
                        newTargets.add(target.toString());
                    }
                }
                annotation.values.set(i + 1, newTargets);
            }
        }
        
        return modified;
    }
    
    /**
     * Transform method annotations (@Inject, @Redirect, etc).
     */
    private boolean transformMethodAnnotations(MethodNode method) {
        boolean modified = false;

        // Mixin annotations use @Retention(CLASS), so they appear in invisibleAnnotations.
        // Some compilers/versions may place them in visibleAnnotations. Check BOTH.
        for (List<AnnotationNode> annotationList : new List[]{
                method.visibleAnnotations, method.invisibleAnnotations}) {
            if (annotationList == null) continue;
            for (AnnotationNode annotation : annotationList) {
                String desc = annotation.desc;

                if (INJECT_DESC.equals(desc) || REDIRECT_DESC.equals(desc) ||
                    MODIFY_ARG_DESC.equals(desc) || MODIFY_VAR_DESC.equals(desc) ||
                    MODIFY_CONST_DESC.equals(desc) || MODIFY_ARGS_DESC.equals(desc)) {
                    modified |= transformInjectionAnnotation(annotation);
                } else if (SHADOW_DESC.equals(desc) || OVERWRITE_DESC.equals(desc)) {
                    modified |= transformShadowAnnotation(annotation, method);
                } else if (ACCESSOR_DESC.equals(desc) || INVOKER_DESC.equals(desc)) {
                    modified |= transformAccessorAnnotation(annotation, method);
                }
            }
        }

        return modified;
    }
    
    /**
     * Transform @Inject, @Redirect, @ModifyArg, @ModifyVariable annotations.
     */
    private boolean transformInjectionAnnotation(AnnotationNode annotation) {
        boolean modified = false;

        if (annotation.values == null) {
            // No values at all — add require=0 so the default doesn't crash
            annotation.values = new ArrayList<>();
            annotation.values.add("require");
            annotation.values.add(0);
            LOGGER.debug("Added require=0 to injection annotation (had no values)");
            return true;
        }

        boolean hasRequire = false;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            // Transform "method" targets
            if ("method".equals(key)) {
                if (value instanceof List<?> methods) {
                    List<String> newMethods = new ArrayList<>();
                    for (Object m : methods) {
                        if (m instanceof String s) {
                            String redirected = redirectMethodTarget(s);
                            newMethods.add(redirected);
                            if (!s.equals(redirected)) {
                                modified = true;
                                LOGGER.debug("Redirected @Inject method: {} -> {}", s, redirected);
                            }
                        }
                    }
                    annotation.values.set(i + 1, newMethods);
                } else if (value instanceof String s) {
                    String redirected = redirectMethodTarget(s);
                    if (!s.equals(redirected)) {
                        annotation.values.set(i + 1, redirected);
                        modified = true;
                    }
                }
            }

            // Force require = 0 on all injection annotations so broken targets
            // don't crash the game. The mixin will simply be skipped silently.
            if ("require".equals(key)) {
                hasRequire = true;
                if (value instanceof Integer intVal && intVal != 0) {
                    annotation.values.set(i + 1, 0);
                    modified = true;
                    LOGGER.debug("Set require=0 on injection annotation (was {})", intVal);
                }
            }

            // Transform @At annotations
            if ("at".equals(key)) {
                if (value instanceof AnnotationNode at) {
                    modified |= transformAtAnnotation(at);
                } else if (value instanceof List<?> ats) {
                    for (Object at : ats) {
                        if (at instanceof AnnotationNode atNode) {
                            modified |= transformAtAnnotation(atNode);
                        }
                    }
                }
            }
        }

        // If no explicit require was found, add require=0 to override config default
        if (!hasRequire) {
            annotation.values.add("require");
            annotation.values.add(0);
            modified = true;
            LOGGER.debug("Added require=0 to injection annotation (was using config default)");
        }

        return modified;
    }
    
    /**
     * Transform @At annotation targets.
     */
    private boolean transformAtAnnotation(AnnotationNode annotation) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            // Transform "target" which is the method/field reference
            if ("target".equals(key) && value instanceof String s) {
                String redirected = redirectMethodTarget(s);
                if (!s.equals(redirected)) {
                    annotation.values.set(i + 1, redirected);
                    modified = true;
                    LOGGER.debug("Redirected @At target: {} -> {}", s, redirected);
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Transform @Shadow and @Overwrite annotations.
     */
    private boolean transformShadowAnnotation(AnnotationNode annotation, MethodNode method) {
        // For @Shadow, the method name itself might need changing
        String oldName = method.name;
        String newName = methodTargetRedirects.get(oldName);
        
        if (newName != null && !newName.equals(oldName)) {
            method.name = newName;
            LOGGER.debug("Renamed @Shadow method: {} -> {}", oldName, newName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Transform @Accessor and @Invoker annotations.
     */
    private boolean transformAccessorAnnotation(AnnotationNode annotation, MethodNode method) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            // Transform "value" which is the target name
            if ("value".equals(key) && value instanceof String s) {
                String redirected = methodTargetRedirects.getOrDefault(s, s);
                if (!s.equals(redirected)) {
                    annotation.values.set(i + 1, redirected);
                    modified = true;
                    LOGGER.debug("Redirected @Accessor/Invoker: {} -> {}", s, redirected);
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Transform field annotations.
     */
    private boolean transformFieldAnnotations(FieldNode field) {
        // Similar to method annotations for @Shadow fields
        // Implementation similar to transformShadowAnnotation
        return false;
    }
    
    /**
     * Redirect a class name if needed.
     */
    private String redirectClassName(String className) {
        // Convert to internal format if needed
        String internal = className.replace('.', '/');
        String redirected = transformer.getClassRedirects().getOrDefault(internal, internal);
        // Convert back to original format
        return className.contains(".") ? redirected.replace('/', '.') : redirected;
    }
    
    /**
     * Redirect a method target reference.
     * Handles various Mixin target formats:
     * - "methodName" (simple)
     * - "methodName(Largs;)Lreturn;" (with descriptor)
     * - "Lowner;methodName(Largs;)Lreturn;" (full reference)
     */
    private String redirectMethodTarget(String target) {
        // Try direct lookup first
        String direct = methodTargetRedirects.get(target);
        if (direct != null) {
            return direct;
        }
        
        // Parse and redirect parts
        // Format: [Lowner;]methodName[(descriptor)]
        
        // Check if it contains owner
        if (target.startsWith("L") && target.contains(";")) {
            int semiIdx = target.indexOf(';');
            String owner = target.substring(1, semiIdx);
            String rest = target.substring(semiIdx + 1);
            
            // Redirect owner
            String newOwner = transformer.getClassRedirects().getOrDefault(owner, owner);
            
            // Extract method name
            int descIdx = rest.indexOf('(');
            String methodName = descIdx >= 0 ? rest.substring(0, descIdx) : rest;
            String desc = descIdx >= 0 ? rest.substring(descIdx) : "";
            
            // Redirect method name
            String newMethod = methodTargetRedirects.getOrDefault(methodName, methodName);
            
            return "L" + newOwner + ";" + newMethod + desc;
        }
        
        // Check if it has a descriptor
        int descIdx = target.indexOf('(');
        if (descIdx >= 0) {
            String methodName = target.substring(0, descIdx);
            String desc = target.substring(descIdx);
            
            String newMethod = methodTargetRedirects.getOrDefault(methodName, methodName);
            return newMethod + desc;
        }
        
        // Simple name - direct lookup
        return methodTargetRedirects.getOrDefault(target, target);
    }
    
    /**
     * Transform a refmap.json file.
     * Refmaps contain mappings from dev names to obfuscated names.
     */
    public String transformRefmap(String refmapJson) {
        // Parse JSON, transform mappings, serialize back
        // This would use Gson to parse and transform
        
        // For each mapping entry, check if the target needs redirection
        // and update accordingly
        
        // This is complex because refmap format varies by Mixin version
        // For now, return unchanged - actual implementation would parse and transform
        
        return refmapJson;
    }
    
    /**
     * Transform a Mixin config JSON file.
     * Strips mixin entries that reference classes with broken targets
     * (removed methods, removed inner classes, etc) that would crash
     * the mixin system during application.
     *
     * @param configJson the mixin config JSON string
     * @param classDataLookup a function to get class bytes by internal name (package/Class),
     *                        or null if class analysis is not available
     * @return the transformed JSON with broken mixins stripped
     */
    public String transformMixinConfig(String configJson, Map<String, byte[]> classDataLookup) {
        if (classDataLookup == null || classDataLookup.isEmpty()) {
            return configJson;
        }

        // Extract the package prefix from the config
        String packagePrefix = extractJsonString(configJson, "package");
        if (packagePrefix == null) {
            return configJson;
        }

        String packagePath = packagePrefix.replace('.', '/');

        // Process "mixins" array (common/server)
        configJson = stripBrokenMixinEntries(configJson, "mixins", packagePath, classDataLookup);

        // Process "client" array
        configJson = stripBrokenMixinEntries(configJson, "client", packagePath, classDataLookup);

        // Process "server" array
        configJson = stripBrokenMixinEntries(configJson, "server", packagePath, classDataLookup);

        return configJson;
    }

    /**
     * Convenience overload for when no class data is available.
     */
    public String transformMixinConfig(String configJson) {
        return configJson;
    }

    /**
     * Process mixin entries: relocate targets where possible, partially strip broken methods,
     * and only fully strip a mixin as a last resort.
     *
     * Three-phase approach per mixin class:
     *   1. RELOCATE — rewrite annotation targets using redirect maps (method renames, class renames)
     *   2. PARTIAL STRIP — if some methods reference removed APIs, remove just those methods
     *   3. FULL STRIP — only if all mixin methods are broken or the class itself can't load
     */
    private String stripBrokenMixinEntries(String json, String arrayKey, String packagePath,
                                            Map<String, byte[]> classDataLookup) {
        // Find the array in JSON: "client": ["entry1", "entry2", ...]
        Pattern arrayPattern = ARRAY_PATTERN_CACHE.computeIfAbsent(arrayKey,
            k -> Pattern.compile("\"" + k + "\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL));

        Matcher matcher = arrayPattern.matcher(json);
        if (!matcher.find()) {
            return json;
        }

        String arrayContent = matcher.group(1);

        // Extract individual entries (uses cached static pattern)
        Matcher entryMatcher = ENTRY_PATTERN.matcher(arrayContent);

        List<String> validEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();
        int relocated = 0;
        int partiallyStripped = 0;

        while (entryMatcher.find()) {
            String mixinClassName = entryMatcher.group(1);
            String fullClassPath = packagePath + "/" + mixinClassName.replace('.', '/');

            byte[] classData = classDataLookup.get(fullClassPath + ".class");
            if (classData == null) {
                classData = classDataLookup.get(fullClassPath);
            }

            if (classData == null) {
                // Can't find class data — keep it and let the mixin system handle it
                validEntries.add(mixinClassName);
                continue;
            }

            // Phase 1: Try to RELOCATE the mixin (rewrite targets using redirect maps)
            byte[] relocatedData = relocateMixinClass(classData, fullClassPath);
            boolean wasRelocated = (relocatedData != classData);

            // Phase 2: Try PARTIAL STRIPPING (remove individual broken methods)
            PartialStripResult stripResult = partialStripMixin(
                wasRelocated ? relocatedData : classData, fullClassPath);

            if (stripResult.allBroken) {
                // Phase 3: FULL STRIP — entire mixin is unsalvageable
                removedEntries.add(mixinClassName);
                LOGGER.warn("Fully stripping mixin '{}' (all targets removed/broken)", mixinClassName);
            } else {
                validEntries.add(mixinClassName);

                // Update the class data in the lookup map so it gets written to the JAR
                byte[] finalData = stripResult.modifiedData != null ? stripResult.modifiedData :
                                   (wasRelocated ? relocatedData : classData);
                classDataLookup.put(fullClassPath + ".class", finalData);

                if (wasRelocated) {
                    relocated++;
                    LOGGER.info("Relocated mixin '{}' targets to new API names", mixinClassName);
                }
                if (stripResult.strippedMethods > 0) {
                    partiallyStripped++;
                    LOGGER.info("Partially stripped mixin '{}': removed {} broken method(s), kept {} working",
                        mixinClassName, stripResult.strippedMethods, stripResult.keptMethods);
                }
            }
        }

        if (removedEntries.isEmpty() && relocated == 0 && partiallyStripped == 0) {
            return json;
        }

        if (relocated > 0) {
            LOGGER.info("Relocated {} mixin(s) in '{}' array to use updated targets", relocated, arrayKey);
        }
        if (partiallyStripped > 0) {
            LOGGER.info("Partially stripped {} mixin(s) in '{}' array (removed broken methods, kept working ones)",
                partiallyStripped, arrayKey);
        }
        if (!removedEntries.isEmpty()) {
            LOGGER.info("Fully stripped {} mixin(s) from '{}' array: {}", removedEntries.size(), arrayKey, removedEntries);
        }

        // Rebuild the array
        StringBuilder newArray = new StringBuilder("\"" + arrayKey + "\": [");
        for (int i = 0; i < validEntries.size(); i++) {
            if (i > 0) newArray.append(",");
            newArray.append("\n    \"").append(validEntries.get(i)).append("\"");
        }
        if (!validEntries.isEmpty()) {
            newArray.append("\n  ");
        }
        newArray.append("]");

        return json.substring(0, matcher.start()) + newArray + json.substring(matcher.end());
    }

    /**
     * Result of partial mixin stripping.
     */
    private record PartialStripResult(
        boolean allBroken,      // true if ALL mixin methods are broken → full strip
        byte[] modifiedData,    // modified class bytes (null if no changes)
        int strippedMethods,    // number of methods removed
        int keptMethods         // number of methods preserved
    ) {}

    /**
     * Relocate a mixin class by rewriting its annotation targets using the redirect maps.
     * This handles method renames, class renames, and descriptor updates.
     * Returns the original data if no relocation was needed.
     */
    private byte[] relocateMixinClass(byte[] classData, String className) {
        try {
            // Use the existing transformMixinClass() which already rewrites
            // @Inject/@Redirect method targets and @At targets using methodTargetRedirects
            byte[] result = transformMixinClass(classData);

            // Also apply class redirects to the mixin's @Mixin target annotations
            // and any type references in method descriptors
            if (result != classData) {
                LOGGER.debug("Relocated mixin targets in {}", className);
            }
            return result;
        } catch (Exception e) {
            LOGGER.debug("Failed to relocate mixin {}: {}", className, e.getMessage());
            return classData;
        }
    }

    /**
     * Partially strip a mixin class — remove individual broken methods while keeping working ones.
     * This preserves partial mod functionality when only some mixin handlers reference removed APIs.
     */
    private PartialStripResult partialStripMixin(byte[] classData, String className) {
        try {
            ClassReader reader = new ClassReader(classData);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            if (!isMixinClass(classNode)) {
                return new PartialStripResult(false, null, 0, classNode.methods.size());
            }

            // Check class-level breakage FIRST — @Shadow fields on removed targets,
            // removed superclass, etc. kill the ENTIRE mixin regardless of methods.
            // The mixin system crashes at class application time if @Shadow can't resolve.
            if (hasClassLevelBreakage(classNode)) {
                LOGGER.debug("Mixin {} has class-level breakage (@Shadow on removed field/class)", className);
                return new PartialStripResult(true, null, 0, 0);
            }

            List<MethodNode> brokenMethods = new ArrayList<>();
            List<MethodNode> workingMethods = new ArrayList<>();

            for (MethodNode method : classNode.methods) {
                // Skip constructors and non-mixin methods
                if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
                    workingMethods.add(method);
                    continue;
                }

                // Check if this specific method has broken references
                if (isMethodBroken(method, classNode)) {
                    brokenMethods.add(method);
                } else {
                    workingMethods.add(method);
                }
            }

            if (brokenMethods.isEmpty()) {
                return new PartialStripResult(false, null, 0, workingMethods.size());
            }

            // Check if ALL mixin handler methods are broken
            long workingHandlers = workingMethods.stream()
                .filter(m -> !m.name.equals("<init>") && !m.name.equals("<clinit>"))
                .count();

            if (workingHandlers == 0) {
                // All handlers are broken — full strip
                return new PartialStripResult(true, null, brokenMethods.size(), 0);
            }

            // Partial strip — remove broken methods, keep working ones
            for (MethodNode broken : brokenMethods) {
                classNode.methods.remove(broken);
                LOGGER.debug("Stripped broken method '{}' from mixin {}", broken.name, className);
            }

            // Write modified class
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            byte[] modifiedData = writer.toByteArray();

            return new PartialStripResult(false, modifiedData, brokenMethods.size(), (int) workingHandlers);

        } catch (Exception e) {
            LOGGER.warn("Failed to partial-strip mixin {}: {}", className, e.getMessage());
            return new PartialStripResult(false, null, 0, 0);
        }
    }

    /**
     * Check if a specific method in a mixin class has broken references.
     */
    private boolean isMethodBroken(MethodNode method, ClassNode classNode) {
        // Check bytecode instructions for removed method/field/class references
        if (method.instructions != null) {
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode methodInsn) {
                    String ref = methodInsn.owner + "." + methodInsn.name;
                    if (isKnownRemovedMethod(ref)) return true;
                } else if (insn instanceof FieldInsnNode fieldInsn) {
                    String ref = fieldInsn.owner + "." + fieldInsn.name;
                    if (isKnownRemovedField(ref)) return true;
                } else if (insn instanceof TypeInsnNode typeInsn) {
                    if (isKnownRemovedClass(typeInsn.desc)) return true;
                }
            }
        }

        // Check mixin annotation targets (visible AND invisible — Mixin uses @Retention(CLASS))
        for (List<AnnotationNode> annotationList : new List[]{
                method.visibleAnnotations, method.invisibleAnnotations}) {
            if (annotationList == null) continue;
            for (AnnotationNode ann : annotationList) {
                if (INJECT_DESC.equals(ann.desc) || REDIRECT_DESC.equals(ann.desc) ||
                    MODIFY_ARG_DESC.equals(ann.desc) || MODIFY_VAR_DESC.equals(ann.desc)) {
                    List<String> targets = extractAnnotationMethodTargets(ann);
                    for (String target : targets) {
                        if (isKnownRemovedMethod("." + target)) return true;
                    }
                    List<String> atTargets = extractAtTargets(ann);
                    for (String atTarget : atTargets) {
                        if (isKnownRemovedMethod(atTarget.replace(";", "."))) return true;
                    }
                }
                // @Overwrite on removed method
                if (OVERWRITE_DESC.equals(ann.desc)) {
                    if (isKnownRemovedMethod("." + method.name)) return true;
                }
            }
        }

        // Check return type references
        if (method.desc != null) {
            int retIdx = method.desc.lastIndexOf(')');
            if (retIdx >= 0) {
                String retType = method.desc.substring(retIdx + 1);
                if (retType.startsWith("L") && retType.endsWith(";")) {
                    String retClass = retType.substring(1, retType.length() - 1);
                    if (isKnownRemovedClass(retClass)) return true;
                }
            }
        }

        return false;
    }

    /**
     * Check for class-level breakage that affects the entire mixin
     * (e.g., superclass removed, @Shadow fields on removed targets).
     */
    private boolean hasClassLevelBreakage(ClassNode classNode) {
        // Check superclass
        if (classNode.superName != null && isKnownRemovedClass(classNode.superName)) {
            return true;
        }

        // Check @Mixin target classes — if the target is removed/became-interface,
        // the entire mixin is broken (crashes during PREPARE phase)
        for (List<AnnotationNode> annList : new List[]{
                classNode.visibleAnnotations, classNode.invisibleAnnotations}) {
            if (annList == null) continue;
            for (AnnotationNode ann : annList) {
                if (MIXIN_DESC.equals(ann.desc) && ann.values != null) {
                    for (int i = 0; i < ann.values.size(); i += 2) {
                        String key = (String) ann.values.get(i);
                        Object value = ann.values.get(i + 1);
                        // "value" contains Type[] of target classes
                        if ("value".equals(key) && value instanceof List<?> targets) {
                            for (Object target : targets) {
                                if (target instanceof org.objectweb.asm.Type type) {
                                    String internalName = type.getInternalName();
                                    if (isKnownRemovedClass(internalName)) return true;
                                }
                            }
                        }
                        // "targets" contains String[] of target class names
                        if ("targets".equals(key) && value instanceof List<?> targets) {
                            for (Object target : targets) {
                                if (target instanceof String s) {
                                    String internal = s.replace('.', '/');
                                    if (isKnownRemovedClass(internal)) return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check @Shadow fields referencing removed types (visible AND invisible)
        for (FieldNode field : classNode.fields) {
            for (List<AnnotationNode> annotationList : new List[]{
                    field.visibleAnnotations, field.invisibleAnnotations}) {
                if (annotationList == null) continue;
                for (AnnotationNode ann : annotationList) {
                    if (SHADOW_DESC.equals(ann.desc)) {
                        if (isKnownRemovedField("." + field.name)) return true;
                        if (field.desc != null && field.desc.startsWith("L") && field.desc.endsWith(";")) {
                            String fieldType = field.desc.substring(1, field.desc.length() - 1);
                            if (isKnownRemovedClass(fieldType)) return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if a mixin class has broken references that would crash the mixin system.
     * Analyzes the class bytecode for references to removed methods, fields, or inner classes.
     * Also checks mixin annotation targets (@Inject method=, @Redirect target=, @Overwrite, @Shadow)
     * against known-removed and known-renamed APIs.
     */
    private boolean isMixinBroken(byte[] classData, String className) {
        try {
            ClassReader reader = new ClassReader(classData);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            if (!isMixinClass(classNode)) {
                return false;
            }

            // Check for references to known-removed classes/methods
            Set<String> referencedClasses = new HashSet<>();
            Set<String> referencedMethods = new HashSet<>();
            Set<String> referencedFields = new HashSet<>();

            // Scan bytecode instructions for class/method/field references
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null) continue;

                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode methodInsn) {
                        referencedMethods.add(methodInsn.owner + "." + methodInsn.name + methodInsn.desc);
                        referencedClasses.add(methodInsn.owner);
                    } else if (insn instanceof FieldInsnNode fieldInsn) {
                        referencedFields.add(fieldInsn.owner + "." + fieldInsn.name);
                        referencedClasses.add(fieldInsn.owner);
                    } else if (insn instanceof TypeInsnNode typeInsn) {
                        referencedClasses.add(typeInsn.desc);
                    }
                }
            }

            // Check @Shadow fields — if they reference return types that are removed inner classes
            for (FieldNode field : classNode.fields) {
                if (field.desc != null && field.desc.startsWith("L") && field.desc.endsWith(";")) {
                    String refClass = field.desc.substring(1, field.desc.length() - 1);
                    referencedClasses.add(refClass);
                }
            }

            // Check method return types and parameter types
            for (MethodNode method : classNode.methods) {
                if (method.desc != null) {
                    int retIdx = method.desc.lastIndexOf(')');
                    if (retIdx >= 0) {
                        String retType = method.desc.substring(retIdx + 1);
                        if (retType.startsWith("L") && retType.endsWith(";")) {
                            String retClass = retType.substring(1, retType.length() - 1);
                            referencedClasses.add(retClass);
                        }
                    }
                }
            }

            // --- Check mixin annotation targets ---
            // This catches @Inject(method="oldMethod"), @Redirect(target="..."),
            // @Overwrite methods with old names, @Shadow fields with old names

            // Check @Overwrite methods — if the method name matches a removed/renamed method
            // Mixin uses @Retention(CLASS) → check both visible AND invisible annotations
            for (MethodNode method : classNode.methods) {
                for (List<AnnotationNode> annList : new List[]{
                        method.visibleAnnotations, method.invisibleAnnotations}) {
                    if (annList == null) continue;
                    for (AnnotationNode ann : annList) {
                        if (OVERWRITE_DESC.equals(ann.desc)) {
                            if (isKnownRemovedMethod("." + method.name)) {
                                LOGGER.debug("Mixin {} has @Overwrite on removed method: {}", className, method.name);
                                return true;
                            }
                        }
                    }
                }
            }

            // Check @Inject/@Redirect method targets from annotations
            for (MethodNode method : classNode.methods) {
                for (List<AnnotationNode> annList : new List[]{
                        method.visibleAnnotations, method.invisibleAnnotations}) {
                    if (annList == null) continue;
                    for (AnnotationNode ann : annList) {
                        if (INJECT_DESC.equals(ann.desc) || REDIRECT_DESC.equals(ann.desc) ||
                            MODIFY_ARG_DESC.equals(ann.desc) || MODIFY_VAR_DESC.equals(ann.desc)) {
                            List<String> targets = extractAnnotationMethodTargets(ann);
                            for (String target : targets) {
                                if (isKnownRemovedMethod("." + target)) {
                                    LOGGER.debug("Mixin {} @Inject/@Redirect targets removed method: {}", className, target);
                                    return true;
                                }
                            }
                            List<String> atTargets = extractAtTargets(ann);
                            for (String atTarget : atTargets) {
                                if (isKnownRemovedMethod(atTarget.replace(";", "."))) {
                                    LOGGER.debug("Mixin {} @At targets removed method: {}", className, atTarget);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            // Check @Shadow fields by name
            for (FieldNode field : classNode.fields) {
                for (List<AnnotationNode> annList : new List[]{
                        field.visibleAnnotations, field.invisibleAnnotations}) {
                    if (annList == null) continue;
                    for (AnnotationNode ann : annList) {
                        if (SHADOW_DESC.equals(ann.desc)) {
                            if (isKnownRemovedField("." + field.name)) {
                                LOGGER.debug("Mixin {} has @Shadow on removed field: {}", className, field.name);
                                return true;
                            }
                        }
                    }
                }
            }

            // --- Check against known-removed registries ---

            for (String refClass : referencedClasses) {
                if (isKnownRemovedClass(refClass)) {
                    LOGGER.debug("Mixin {} references removed class: {}", className, refClass);
                    return true;
                }
            }

            for (String refMethod : referencedMethods) {
                if (isKnownRemovedMethod(refMethod)) {
                    LOGGER.debug("Mixin {} references removed method: {}", className, refMethod);
                    return true;
                }
            }

            for (String refField : referencedFields) {
                if (isKnownRemovedField(refField)) {
                    LOGGER.debug("Mixin {} references removed field: {}", className, refField);
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            LOGGER.warn("Failed to analyze mixin class {}: {}", className, e.getMessage());
            return false;
        }
    }

    /**
     * Extract method target strings from @Inject/@Redirect annotations.
     */
    private List<String> extractAnnotationMethodTargets(AnnotationNode annotation) {
        List<String> targets = new ArrayList<>();
        if (annotation.values == null) return targets;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            if ("method".equals(key)) {
                if (value instanceof List<?> methods) {
                    for (Object m : methods) {
                        if (m instanceof String s) {
                            // Strip descriptor if present: "methodName(Largs;)V" -> "methodName"
                            int descIdx = s.indexOf('(');
                            targets.add(descIdx >= 0 ? s.substring(0, descIdx) : s);
                        }
                    }
                } else if (value instanceof String s) {
                    int descIdx = s.indexOf('(');
                    targets.add(descIdx >= 0 ? s.substring(0, descIdx) : s);
                }
            }
        }
        return targets;
    }

    /**
     * Extract @At target strings from an injection annotation.
     */
    private List<String> extractAtTargets(AnnotationNode annotation) {
        List<String> targets = new ArrayList<>();
        if (annotation.values == null) return targets;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            if ("at".equals(key)) {
                if (value instanceof AnnotationNode at) {
                    String target = extractAtTarget(at);
                    if (target != null) targets.add(target);
                } else if (value instanceof List<?> ats) {
                    for (Object at : ats) {
                        if (at instanceof AnnotationNode atNode) {
                            String target = extractAtTarget(atNode);
                            if (target != null) targets.add(target);
                        }
                    }
                }
            }
        }
        return targets;
    }

    /**
     * Extract the target string from a single @At annotation.
     */
    private String extractAtTarget(AnnotationNode at) {
        if (at.values == null) return null;
        for (int i = 0; i < at.values.size(); i += 2) {
            String key = (String) at.values.get(i);
            if ("target".equals(key) && at.values.get(i + 1) instanceof String s) {
                return s;
            }
        }
        return null;
    }

    // --- Known removed classes and methods registry ---
    // These are classes/methods that were completely removed between MC versions
    // and would cause mixin application to crash

    private static final Set<String> KNOWN_REMOVED_CLASSES = new HashSet<>();
    private static final Set<String> KNOWN_REMOVED_METHODS = new HashSet<>();
    private static final Set<String> KNOWN_REMOVED_FIELDS = new HashSet<>();

    static {
        // BufferBuilder inner class BuiltBuffer - removed in rendering rewrite
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_287$class_7433");

        // ChatOptionsScreen - removed
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_5500");

        // Note: class_442 is TitleScreen — it still exists! Do NOT add it here.
        // SocialInteractionsScreen is class_5522 — it also still exists in 1.21.11.

        // Classes that became interfaces (standard mixins can't target interfaces)
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_284"); // GlUniform/Uniform — was class, now interface in 1.21+

        // VoxelShape internals — heavily restructured between versions.
        // Old mixin optimizations (e.g. Lithium) corrupt these and cause NPEs in block init.
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_249");  // SimpleVoxelShape (internals changed)
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_246");  // FractionalDoubleList (internals changed)
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_255");  // PairList / shape merging (internals changed)

        // Math classes removed in 1.19.3 — Minecraft switched to JOML (org.joml.*).
        // Old MC classes were completely deleted. Sodium 1.16.5 targets these with mixins.
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_1159"); // Matrix4f (replaced by org.joml.Matrix4f)
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_4581"); // Matrix3f (replaced by org.joml.Matrix3f)
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_1158"); // Quaternion (replaced by org.joml.Quaternionf)
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_1160"); // Vec3f (replaced by org.joml.Vector3f)

        // DirectionProperty — removed/merged into EnumProperty (class_2754) in newer versions.
        // Lithium's HopperBlockEntityMixin and PistonBlockEntityMixin reference this in bytecode.
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_2753"); // DirectionProperty (merged into EnumProperty)
    }

    static {
        // MinecraftClient.scheduledTasks Queue - removed
        KNOWN_REMOVED_FIELDS.add("net/minecraft/class_310.field_17404");

        // BufferBuilder.building flag - removed in rendering rewrite
        KNOWN_REMOVED_FIELDS.add("net/minecraft/class_287.field_1556");

        // Mouse.cursorLocked - removed
        KNOWN_REMOVED_FIELDS.add("net/minecraft/class_315.field_1866");
    }

    static {
        // BufferBuilder.end() / build() - removed in rendering rewrite
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_287.method_1326");

        // MinecraftClient removed methods
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_310.method_18858");
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_310.method_16901");

        // MinecraftClient.getFramerateLimit() - removed (Dynamic FPS crash)
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_310.method_16009");

        // ReentrantThreadExecutor.send() - removed
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_4093.method_18858");
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_4093.method_16901");

        // Player.addAdditionalSaveData - removed (Carry On crash)
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_1657.method_5652");

        // Note: method_569 (writeToFile), method_5647 (writeNbt), method_38244 (createNbt)
        // still EXIST but with changed signatures. These are NOT removed — they should be
        // handled by the shim's method redirect system, not stripped.
    }

    /**
     * Register additional known-removed classes. Called during polyfill registration.
     */
    public static void registerRemovedClass(String internalName) {
        KNOWN_REMOVED_CLASSES.add(internalName);
    }

    /**
     * Register additional known-removed methods. Called during shim registration.
     */
    public static void registerRemovedMethod(String ownerAndName) {
        KNOWN_REMOVED_METHODS.add(ownerAndName);
    }

    /**
     * Check if a class is in the KNOWN_REMOVED_CLASSES set.
     * Public for testing purposes.
     */
    public static boolean isKnownRemovedClass(String internalName) {
        return KNOWN_REMOVED_CLASSES.contains(internalName);
    }

    private boolean isKnownRemovedField(String refField) {
        for (String removed : KNOWN_REMOVED_FIELDS) {
            if (refField.equals(removed)) return true;
            // Match ".fieldName" suffix only on a dot boundary to avoid false positives
            if (refField.startsWith(".") && removed.endsWith(refField)) return true;
            if (removed.startsWith(".") && refField.endsWith(removed)) return true;
        }
        return false;
    }

    private boolean isKnownRemovedMethod(String refMethod) {
        // refMethod can be:
        //   "owner.nameDesc" (from bytecode instructions)
        //   ".methodName"    (from annotation targets without owner)
        //   "owner.name"     (exact match)
        for (String removed : KNOWN_REMOVED_METHODS) {
            if (refMethod.equals(removed)) return true;
            // Match ".methodName" suffix only on a dot boundary to avoid false positives
            if (refMethod.startsWith(".") && removed.endsWith(refMethod)) return true;
            if (removed.startsWith(".") && refMethod.endsWith(removed)) return true;
        }
        return false;
    }

    /**
     * Extract a string value from JSON by key name.
     */
    private String extractJsonString(String json, String key) {
        Pattern pattern = JSON_STRING_PATTERN_CACHE.computeIfAbsent(key,
            k -> Pattern.compile("\"" + k + "\"\\s*:\\s*\"([^\"]+)\""));
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
