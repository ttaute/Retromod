/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * This is the core bytecode transformer that intercepts class loading
 * and rewrites method calls to maintain compatibility across versions.
 */
package com.retromod.core;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core transformer that rewrites bytecode at class load time.
 * OPTIMIZED: Uses caching and efficient lookup structures.
 *
 * This handles:
 * 1. Method renames (e.g., getWorld -> getEntityWorld)
 * 2. Method removals (redirect to embedded shims)
 * 3. Class relocations (package changes between versions)
 * 4. Signature changes (parameter/return type modifications)
 *
 * IMPORTANT: This class must NOT reference RetroMod directly (which implements
 * ModInitializer) because the transformer is also used by the standalone CLI
 * where Fabric classes are not on the classpath. Use the local LOGGER instead
 * of LOGGER.
 */
public class RetroModTransformer implements ClassFileTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Transformer");
    private static final RetroModTransformer INSTANCE = new RetroModTransformer();
    
    // Maps: oldOwner/oldName/oldDesc -> newOwner/newName/newDesc
    private final Map<MethodKey, MethodTarget> methodRedirects = new ConcurrentHashMap<>(256);
    
    // Maps: oldClassName -> newClassName (internal names with /)
    private final Map<String, String> classRedirects = new ConcurrentHashMap<>(64);
    
    // Maps: oldFieldOwner/oldFieldName -> newFieldOwner/newFieldName
    private final Map<FieldKey, FieldTarget> fieldRedirects = new ConcurrentHashMap<>(64);

    // Maps: intermediary method/field names to Mojang official names
    // Used by the ClassRemapper to remap method_XXXX and field_XXXX in bytecode
    private final Map<String, String> intermediaryMethodNames = new ConcurrentHashMap<>(40000);
    private final Map<String, String> intermediaryFieldNames = new ConcurrentHashMap<>(40000);

    // Maps: oldSuperclass -> new superclass + interfaces to add
    // Used for class-to-interface migrations (e.g., Explosion became an interface)
    private final Map<String, SuperclassRedirect> superclassRedirects = new ConcurrentHashMap<>(16);

    // Constructor-to-factory redirects: converts `new Foo(args)` to `Foo.factory(args)`
    // Key: className + constructorDesc, Value: static factory method info
    private final Map<ConstructorKey, FactoryTarget> constructorRedirects = new ConcurrentHashMap<>(16);

    // Field accessor redirects: GETFIELD → getter(), PUTFIELD → setter()
    // For fields that became private in newer MC versions but have getter/setter methods
    private final Map<FieldKey, FieldAccessorTarget> fieldAccessorRedirects = new ConcurrentHashMap<>(16);

    // Super constructor descriptor changes: when a super() call uses an old descriptor,
    // replace with new descriptor + extra args to push before the call.
    // Key: ConstructorKey(className, oldDesc), Value: SuperCtorRedirect(newDesc, extraArgsBytecode)
    private final Map<ConstructorKey, SuperCtorRedirect> superCtorRedirects = new ConcurrentHashMap<>(8);

    // Packages that should be transformed (mod packages, not minecraft itself)
    private final Set<String> transformablePackages = ConcurrentHashMap.newKeySet();
    
    // Track which shim classes are embedded and available
    private final Set<String> embeddedShimClasses = ConcurrentHashMap.newKeySet();

    // Synthetic classes generated via ASM (for polyfills that need MC-typed fields)
    private final Map<String, byte[]> syntheticClasses = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Cache the remapper if no class redirects change
    private volatile Remapper cachedRemapper;
    private final AtomicInteger classRedirectsVersion = new AtomicInteger(0);
    
    // OPTIMIZATION: Fast owner lookup cache (reduces hash lookups)
    private final Set<String> methodRedirectOwners = ConcurrentHashMap.newKeySet();
    
    private RetroModTransformer() {
        // Register default shim package as transformable
        transformablePackages.add("com/retromod/shim/");
    }
    
    public static RetroModTransformer getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a method redirect.
     * When legacy code calls oldOwner.oldName(oldDesc), 
     * it will be rewritten to call newOwner.newName(newDesc).
     */
    public void registerMethodRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc) {
        
        MethodKey key = new MethodKey(oldOwner, oldName, oldDesc);
        MethodTarget target = new MethodTarget(newOwner, newName, newDesc);
        methodRedirects.put(key, target);
        methodRedirectOwners.add(oldOwner); // OPTIMIZATION: track owners
        
        LOGGER.debug("Registered method redirect: {}.{}{} -> {}.{}{}",
                oldOwner, oldName, oldDesc, newOwner, newName, newDesc);
    }
    
    /**
     * Register a method redirect that converts instance method to static method.
     * The receiver object becomes the first argument in the static method's descriptor.
     *
     * @param devirtualize if true, changes INVOKEVIRTUAL/INVOKEINTERFACE → INVOKESTATIC
     */
    public void registerMethodRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc,
            boolean devirtualize) {

        MethodKey key = new MethodKey(oldOwner, oldName, oldDesc);
        MethodTarget target = new MethodTarget(newOwner, newName, newDesc, devirtualize);
        methodRedirects.put(key, target);
        methodRedirectOwners.add(oldOwner);
    }

    /**
     * Register a class redirect (for relocated/renamed classes).
     */
    public void registerClassRedirect(String oldClass, String newClass) {
        classRedirects.put(oldClass, newClass);
        classRedirectsVersion.incrementAndGet(); // Invalidate cached remapper
        cachedRemapper = null;
        LOGGER.debug("Registered class redirect: {} -> {}", oldClass, newClass);
    }

    /**
     * Register a constructor-to-factory redirect.
     * Converts `new className(constructorDesc)` → `factoryClass.factoryMethod(factoryDesc)`.
     * The factory method must be static and return the class type.
     */
    public void registerConstructorRedirect(String className, String constructorDesc,
            String factoryClass, String factoryMethod, String factoryDesc) {
        constructorRedirects.put(
            new ConstructorKey(className, constructorDesc),
            new FactoryTarget(factoryClass, factoryMethod, factoryDesc));
        LOGGER.debug("Registered constructor redirect: new {}({}) -> {}.{}{}",
            className, constructorDesc, factoryClass, factoryMethod, factoryDesc);
    }

    /**
     * Register a super constructor descriptor change.
     * When a subclass calls super(oldDesc), the call is redirected to super(newDesc)
     * with an extra static field value pushed onto the stack.
     *
     * Example: Button(int,int,int,int,Component,OnPress) removed in 26.1
     *          → Button(int,int,int,int,Component,OnPress,CreateNarration)
     *          Extra arg: GETSTATIC Button.DEFAULT_NARRATION
     */
    public void registerSuperConstructorRedirect(String className, String oldDesc, String newDesc,
            String extraFieldOwner, String extraFieldName, String extraFieldDesc) {
        superCtorRedirects.put(
            new ConstructorKey(className, oldDesc),
            new SuperCtorRedirect(newDesc, extraFieldOwner, extraFieldName, extraFieldDesc));
        LOGGER.debug("Registered super ctor redirect: {}.{} -> {} + GETSTATIC {}.{}",
            className, oldDesc, newDesc, extraFieldOwner, extraFieldName);
    }

    /**
     * Register intermediary method and field name mappings for bytecode remapping.
     * These are used by the Remapper to translate method_XXXX and field_XXXX names
     * in method calls, field accesses, @Shadow annotations, etc.
     */
    public void registerIntermediaryNameMappings(
            Map<String, String> methodNames, Map<String, String> fieldNames) {
        intermediaryMethodNames.putAll(methodNames);
        intermediaryFieldNames.putAll(fieldNames);
        cachedRemapper = null; // Invalidate cached remapper
        LOGGER.info("Registered {} intermediary method names and {} field names for bytecode remapping",
            methodNames.size(), fieldNames.size());
    }
    
    /**
     * Register a field redirect (field to field).
     */
    public void registerFieldRedirect(
            String oldOwner, String oldName,
            String newOwner, String newName) {
        
        FieldKey key = new FieldKey(oldOwner, oldName);
        FieldTarget target = new FieldTarget(newOwner, newName, null, null);
        fieldRedirects.put(key, target);
    }
    
    /**
     * Register a field redirect with descriptors.
     * If newDesc starts with "(", this is a field-to-method redirect
     * (GETSTATIC/GETFIELD becomes INVOKESTATIC/INVOKEVIRTUAL).
     */
    public void registerFieldRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc) {
        
        FieldKey key = new FieldKey(oldOwner, oldName);
        FieldTarget target = new FieldTarget(newOwner, newName, oldDesc, newDesc);
        fieldRedirects.put(key, target);
        
        LOGGER.debug("Registered field redirect: {}.{} {} -> {}.{} {}",
                oldOwner, oldName, oldDesc, newOwner, newName, newDesc);
    }
    
    /**
     * Register a field accessor redirect: GETFIELD becomes INVOKEVIRTUAL getter,
     * PUTFIELD becomes INVOKEVIRTUAL setter. For fields that became private in newer MC.
     */
    public void registerFieldAccessorRedirect(
            String fieldOwner, String fieldName,
            String getterName, String getterDesc,
            String setterName, String setterDesc) {
        FieldKey key = new FieldKey(fieldOwner, fieldName);
        FieldAccessorTarget target = new FieldAccessorTarget(
            fieldOwner, getterName, getterDesc,
            fieldOwner, setterName, setterDesc
        );
        fieldAccessorRedirects.put(key, target);
        LOGGER.debug("Registered field accessor redirect: {}.{} -> get={}, set={}",
                fieldOwner, fieldName, getterName, setterName);
    }

    /**
     * Mark a package as containing legacy mod code that should be transformed.
     */
    public void addTransformablePackage(String packagePrefix) {
        // Ensure it ends with /
        if (!packagePrefix.endsWith("/")) {
            packagePrefix = packagePrefix + "/";
        }
        transformablePackages.add(packagePrefix);
    }
    
    /**
     * Register an embedded shim class that provides removed API implementations.
     */
    public void registerEmbeddedShim(String className) {
        embeddedShimClasses.add(className);
    }

    /**
     * Register a synthetic class generated via ASM bytecode.
     * These classes are injected into mod JARs during transformation and
     * can have fields/methods with MC-typed signatures that can't be
     * compiled from Java source (since MC isn't on the compile classpath).
     *
     * @param internalName the class internal name (e.g., "com/retromod/polyfill/...")
     * @param classBytes the generated class file bytes
     */
    public void registerSyntheticClass(String internalName, byte[] classBytes) {
        syntheticClasses.put(internalName, classBytes);
        // Also register as embedded shim so the embedder picks it up
        embeddedShimClasses.add(internalName.replace('/', '.'));
        LOGGER.debug("Registered synthetic class: {}", internalName);
    }

    /**
     * Get all registered synthetic classes.
     */
    public Map<String, byte[]> getSyntheticClasses() {
        return Collections.unmodifiableMap(syntheticClasses);
    }

    /**
     * Register a superclass redirect for class-to-interface migrations.
     * When a mod extends oldSuperclass, it will be rewritten to extend newSuperclass
     * and implement the specified interfaces.
     *
     * Example: If Explosion (class_1927) changed from a class to an interface,
     * mods extending it need their superclass changed to a bridge class that
     * implements the new interface.
     */
    public void registerSuperclassRedirect(String oldSuperclass, String newSuperclass, String... addInterfaces) {
        superclassRedirects.put(oldSuperclass, new SuperclassRedirect(newSuperclass, addInterfaces));
        LOGGER.debug("Registered superclass redirect: {} -> {} (+ {} interfaces)",
                oldSuperclass, newSuperclass, addInterfaces.length);
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, 
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, 
            byte[] classfileBuffer) {
        
        if (className == null || classfileBuffer == null) {
            return null;
        }
        
        // Only transform classes in registered packages
        if (!shouldTransform(className)) {
            return null;
        }
        
        // Use the hybrid engine which prefers AOT, falls back to JIT
        // This also handles performance monitoring automatically
        try {
            HybridTransformationEngine hybrid = HybridTransformationEngine.getInstance();
            String modId = guessModFromClass(className);
            return hybrid.transform(className, classfileBuffer, modId);
        } catch (Exception e) {
            LOGGER.error("Failed to transform class: {}", className, e);
            return null;
        }
    }
    
    /**
     * Guess which mod a class belongs to based on package name.
     */
    private String guessModFromClass(String className) {
        // Common mod package patterns
        // e.g., com/example/mymod/... -> mymod
        String[] parts = className.split("/");
        if (parts.length >= 3) {
            return parts[2]; // Usually the mod name is the 3rd part
        }
        return "unknown";
    }
    
    private boolean shouldTransform(String className) {
        for (String prefix : transformablePackages) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Transform a class's bytecode, rewriting method/field/class references.
     * This is the core JIT transformation logic, also used by AOT.
     * OPTIMIZED: Uses cached remapper and efficient lookup structures.
     */
    public byte[] transformClass(byte[] originalBytes, String className) {
        // OPTIMIZATION: Skip if no redirects registered
        if (methodRedirects.isEmpty() && classRedirects.isEmpty() &&
                fieldRedirects.isEmpty() && superclassRedirects.isEmpty()) {
            return originalBytes;
        }


        // Debug: log class redirect count for first few classes
        if (className != null && (className.contains("Mixin") || className.contains("mixin"))) {
            LOGGER.info("transformClass({}) with {} class redirects, {} method redirects",
                className, classRedirects.size(), methodRedirects.size());
        }

        ClassReader reader = new ClassReader(originalBytes);

        // OPTIMIZATION: Use cached remapper if class redirects haven't changed
        Remapper classRemapper = cachedRemapper;
        boolean hasIntermediaryNames = !intermediaryMethodNames.isEmpty() || !intermediaryFieldNames.isEmpty();
        if (classRemapper == null && (!classRedirects.isEmpty() || hasIntermediaryNames)) {
            classRemapper = new Remapper() {
                @Override
                public String map(String internalName) {
                    return classRedirects.getOrDefault(internalName, internalName);
                }

                @Override
                public String mapMethodName(String owner, String name, String descriptor) {
                    // Remap intermediary method names (method_XXXX → Mojang name)
                    if (!intermediaryMethodNames.isEmpty() && name.startsWith("method_")) {
                        String mojang = intermediaryMethodNames.get(name);
                        if (mojang != null) return mojang;
                    }
                    return name;
                }

                @Override
                public String mapFieldName(String owner, String name, String descriptor) {
                    // Remap intermediary field names (field_XXXX → Mojang name)
                    if (!intermediaryFieldNames.isEmpty() && name.startsWith("field_")) {
                        String mojang = intermediaryFieldNames.get(name);
                        if (mojang != null) return mojang;
                    }
                    return name;
                }
            };
            cachedRemapper = classRemapper;
        }

        // IMPORTANT: When using ClassRemapper, do NOT pass the ClassReader to ClassWriter!
        // ClassWriter(reader, flags) copies the constant pool from the reader as an optimization,
        // which means ClassRemapper's name changes don't get reflected in the output.
        // We must create a standalone ClassWriter so ASM builds a fresh constant pool.
        boolean hasClassRemaps = (classRemapper != null);
        ClassWriter writer = hasClassRemaps
            ? new SafeClassWriter(ClassWriter.COMPUTE_FRAMES)
            : new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor visitor = writer;

        // IMPORTANT: RetroModClassVisitor must be INNER (closer to writer) so it sees
        // Mojang names AFTER ClassRemapper has translated intermediary names.
        // Chain: Reader → ClassRemapper (remap) → RetroModClassVisitor (redirect) → Writer
        visitor = new RetroModClassVisitor(Opcodes.ASM9, visitor);

        // Only add remapping visitor if we have class redirects
        if (classRemapper != null) {
            visitor = new ClassRemapper(visitor, classRemapper);
        }

        try {
            // Use EXPAND_FRAMES to properly feed frame data into COMPUTE_FRAMES
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            byte[] result = writer.toByteArray();
            // Deduplicate methods that may have identical name+descriptor after class remapping.
            // This happens when two intermediary class names (e.g., class_316 and class_4064)
            // both map to the same Mojang name, causing return types in descriptors to collapse.
            if (hasClassRemaps) {
                result = deduplicateMethods(result, className);
            }
            return result;
        } catch (Exception e) {
            // Fallback: try with COMPUTE_MAXS and preserve existing frames
            try {
                ClassWriter fallbackWriter = hasClassRemaps
                    ? new SafeClassWriter(ClassWriter.COMPUTE_MAXS)
                    : new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                ClassVisitor fallbackVisitor = fallbackWriter;
                fallbackVisitor = new RetroModClassVisitor(Opcodes.ASM9, fallbackVisitor);
                if (classRemapper != null) {
                    fallbackVisitor = new ClassRemapper(fallbackVisitor, classRemapper);
                }
                // Don't skip frames - preserve existing StackMapTable entries
                reader.accept(fallbackVisitor, 0);
                byte[] result = fallbackWriter.toByteArray();
                if (hasClassRemaps) {
                    result = deduplicateMethods(result, className);
                }
                return result;
            } catch (Exception e2) {
                LOGGER.warn("Transform failed for {}, returning original: {}", className, e2.getMessage());
                return originalBytes;
            }
        }
    }

    /**
     * Remove duplicate methods from a class that were created by class remapping.
     *
     * When two different intermediary class names map to the same Mojang name,
     * methods whose descriptors differ only in those class names end up with
     * identical name+descriptor after remapping. The JVM rejects classes with
     * duplicate methods, so we must keep only one copy.
     *
     * Preference: keep the non-synthetic, non-bridge method when possible.
     */
    private byte[] deduplicateMethods(byte[] classBytes, String className) {
        ClassReader cr = new ClassReader(classBytes);

        // First pass: detect duplicates
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                String key = name + descriptor;
                if (!seen.add(key)) {
                    duplicates.add(key);
                }
                return null; // no need to visit method body
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (duplicates.isEmpty()) {
            return classBytes; // nothing to deduplicate
        }

        LOGGER.info("Deduplicating {} duplicate method(s) in {}: {}",
                duplicates.size(), className, duplicates);

        // Second pass: rebuild class, skipping duplicate methods.
        // For each duplicate signature, prefer the non-synthetic/non-bridge variant.
        // Track which duplicates we have already emitted.
        Set<String> emitted = new HashSet<>();
        // We need two sub-passes for duplicates: first collect access flags, then filter.
        // Collect: for each duplicate key, record the "best" access flags seen.
        Map<String, Integer> bestAccess = new HashMap<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                String key = name + descriptor;
                if (duplicates.contains(key)) {
                    Integer prev = bestAccess.get(key);
                    if (prev == null) {
                        bestAccess.put(key, access);
                    } else {
                        // Prefer non-synthetic, non-bridge
                        boolean prevIsSynthetic = (prev & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                        boolean currIsSynthetic = (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                        if (prevIsSynthetic && !currIsSynthetic) {
                            bestAccess.put(key, access);
                        }
                    }
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        // Now rebuild, keeping only the first method whose access matches "best",
        // or the first occurrence if all have the same flags.
        ClassWriter dedupWriter = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, dedupWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                String key = name + descriptor;
                if (duplicates.contains(key)) {
                    if (emitted.contains(key)) {
                        // Already emitted the preferred copy — skip this one
                        return null;
                    }
                    Integer best = bestAccess.get(key);
                    boolean thisIsBest = (best != null && best == access);
                    boolean thisIsSynthetic = (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                    if (!thisIsBest && !emitted.contains(key)) {
                        // This is not the preferred copy, but we haven't emitted one yet.
                        // If there's a better one coming, skip; otherwise emit.
                        // We can't look ahead, so skip synthetic/bridge if the best isn't synthetic.
                        boolean bestIsSynthetic = best != null && (best & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                        if (thisIsSynthetic && !bestIsSynthetic) {
                            return null; // skip, the better one will come
                        }
                    }
                    emitted.add(key);
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        return dedupWriter.toByteArray();
    }

    /**
     * ClassWriter that safely handles getCommonSuperClass in modded environments.
     * ASM's default implementation uses Class.forName() which fails when classes
     * are loaded by custom classloaders (as in Minecraft mod loaders).
     */
    private static class SafeClassWriter extends ClassWriter {

        public SafeClassWriter(int flags) {
            super(flags);
        }

        public SafeClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Exception | LinkageError e) {
                // In modded MC, classes may not be resolvable via Class.forName().
                // Also handles TypeNotPresentException and NoClassDefFoundError when
                // intermediary names are remapped to Mojang names not on the classpath.
                // java/lang/Object is always a valid common superclass.
                return "java/lang/Object";
            }
        }
    }
    
    /**
     * ASM ClassVisitor that rewrites method calls, field accesses,
     * and superclass references (for class-to-interface migrations).
     */
    // Classes that became interfaces in newer MC/DFU versions.
    // Calls using INVOKEVIRTUAL must be changed to INVOKEINTERFACE.
    private static final Set<String> KNOWN_INTERFACES = Set.of(
        "com/mojang/serialization/DataResult",
        "com/mojang/serialization/DynamicOps",
        "com/mojang/serialization/MapLike",
        "com/mojang/serialization/Lifecycle"
    );

    private class RetroModClassVisitor extends ClassVisitor {

        public RetroModClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            // Filter out self-referential interfaces (caused by class remapping)
            // E.g., ConfigScreenFactory extends io.github.prospector...ConfigScreenFactory
            // After remap, both resolve to com.terraformersmc...ConfigScreenFactory
            if (interfaces != null && interfaces.length > 0) {
                String[] filtered = java.util.Arrays.stream(interfaces)
                    .filter(iface -> !iface.equals(name))
                    .toArray(String[]::new);
                if (filtered.length < interfaces.length) {
                    LOGGER.debug("Removed self-referential interface from {}", name);
                    interfaces = filtered;
                }
            }

            // Check if superclass needs to be rewritten (class-to-interface polyfill)
            if (superName != null && !superclassRedirects.isEmpty()) {
                SuperclassRedirect redirect = superclassRedirects.get(superName);
                if (redirect != null) {
                    LOGGER.debug("Rewriting superclass of {} from {} to {}",
                            name, superName, redirect.newSuperclass());
                    String newSuper = redirect.newSuperclass();
                    String[] newInterfaces = mergeInterfaces(interfaces, redirect.addInterfaces());
                    super.visit(version, access, name, signature, newSuper, newInterfaces);
                    return;
                }
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        private String[] mergeInterfaces(String[] existing, String[] additional) {
            if (additional == null || additional.length == 0) {
                return existing;
            }
            Set<String> merged = new LinkedHashSet<>();
            if (existing != null) {
                Collections.addAll(merged, existing);
            }
            Collections.addAll(merged, additional);
            return merged.toArray(new String[0]);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // After class remapping, some inner/outer class pairs may collapse to the same name.
            // For example: class_5253$class_5254 → ARGB, class_5253 → ARGB
            // This creates an invalid "class is both outer and inner" entry.
            // Check the resolved (post-remap) names and skip self-referential entries.
            if (outerName != null) {
                String resolvedName = classRedirects.getOrDefault(name, name);
                String resolvedOuter = classRedirects.getOrDefault(outerName, outerName);
                if (resolvedName.equals(resolvedOuter)) {
                    LOGGER.debug("Skipping self-referential InnerClass entry: {} of {} (both resolve to {})",
                            name, outerName, resolvedName);
                    return;
                }
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {

            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            // OPTIMIZATION: Only wrap if we have redirects
            if (methodRedirects.isEmpty() && fieldRedirects.isEmpty() && constructorRedirects.isEmpty()
                    && fieldAccessorRedirects.isEmpty()) {
                return mv;
            }
            return new RetroModMethodVisitor(api, mv);
        }
    }
    
    /**
     * ASM MethodVisitor that rewrites individual method invocations.
     * OPTIMIZED: Uses fast owner lookup to skip unrelated calls.
     * Also handles constructor-to-factory redirects (NEW+DUP+INVOKESPECIAL → INVOKESTATIC).
     */
    private class RetroModMethodVisitor extends MethodVisitor {

        // Track pending NEW instructions for constructor→factory redirect
        // When we see NEW for a class with constructor redirects, we delay emitting
        // the NEW+DUP until we see the <init> call to determine if it should be redirected
        private String pendingNewClass = null;
        private boolean pendingDup = false;

        public RetroModMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        private void flushPendingNew() {
            if (pendingNewClass != null) {
                super.visitTypeInsn(Opcodes.NEW, pendingNewClass);
                pendingNewClass = null;
            }
            if (pendingDup) {
                super.visitInsn(Opcodes.DUP);
                pendingDup = false;
            }
        }

        /**
         * Resolve class references in a descriptor through classRedirects.
         * E.g., "(Lclass_3702$class_3703;)V" → "(Lcom/mojang/blaze3d/platform/InputConstants$Type;)V"
         */
        private String resolveDescriptor(String descriptor) {
            if (classRedirects.isEmpty()) return descriptor;
            // Quick check: does descriptor contain any 'L' type references?
            if (descriptor.indexOf('L') < 0) return descriptor;

            StringBuilder sb = new StringBuilder(descriptor.length());
            int i = 0;
            while (i < descriptor.length()) {
                char c = descriptor.charAt(i);
                if (c == 'L') {
                    // Find the closing ';'
                    int end = descriptor.indexOf(';', i);
                    if (end < 0) break;
                    String className = descriptor.substring(i + 1, end);
                    String resolved = classRedirects.getOrDefault(className, className);
                    sb.append('L').append(resolved).append(';');
                    i = end + 1;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        }

        /**
         * Fix INVOKEVIRTUAL → INVOKEINTERFACE for classes that became interfaces.
         * E.g., com.mojang.serialization.DataResult was a class, now an interface in newer DFU.
         */
        private int fixClassToInterfaceOpcode(int opcode, String owner) {
            if (opcode == Opcodes.INVOKEVIRTUAL && KNOWN_INTERFACES.contains(owner)) {
                return Opcodes.INVOKEINTERFACE;
            }
            return opcode;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW) {
                // Flush any previous pending NEW before buffering a new one
                flushPendingNew();
                if (!constructorRedirects.isEmpty()) {
                    // Resolve through classRedirects since we see pre-remap names
                    // (e.g., class_2960) but redirects are registered with post-remap names (Identifier)
                    String resolvedType = classRedirects.getOrDefault(type, type);
                    boolean hasRedirect = constructorRedirects.keySet().stream()
                        .anyMatch(k -> k.className().equals(resolvedType));
                    if (hasRedirect) {
                        pendingNewClass = type;
                        return;
                    }
                }
            }
            // Non-NEW TypeInsns (ANEWARRAY, CHECKCAST, INSTANCEOF, etc.) may appear
            // as part of constructor arguments — don't flush the pending NEW
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.DUP && pendingNewClass != null && !pendingDup) {
                pendingDup = true;
                return;
            }
            // Don't flush — instructions like ICONST_0, ACONST_NULL can be constructor args
            super.visitInsn(opcode);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                String descriptor, boolean isInterface) {

            // Check for constructor→factory redirect
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name) && pendingNewClass != null) {
                // Resolve owner through classRedirects (we see pre-remap names like class_2960)
                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                // Also resolve the descriptor — class refs in the descriptor (e.g., intermediary
                // class_3702$class_3703) need to be mapped to Mojang names (InputConstants$Type)
                // to match constructor redirects registered with Mojang names.
                String resolvedDesc = resolveDescriptor(descriptor);
                ConstructorKey ckey = new ConstructorKey(resolvedOwner, resolvedDesc);
                FactoryTarget factory = constructorRedirects.get(ckey);
                if (factory == null && !resolvedDesc.equals(descriptor)) {
                    // Also try with the original descriptor in case redirect was registered
                    // with pre-remap names
                    ckey = new ConstructorKey(resolvedOwner, descriptor);
                    factory = constructorRedirects.get(ckey);
                }
                if (factory != null) {
                    // Replace NEW+DUP+INVOKESPECIAL with INVOKESTATIC factory
                    String originalClass = pendingNewClass;
                    pendingNewClass = null;
                    pendingDup = false;
                    LOGGER.info("Constructor→factory redirect: new {}({}) -> {}.{}{}",
                            owner, descriptor, factory.factoryClass(), factory.factoryMethod(), factory.factoryDesc());
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, factory.factoryClass(),
                            factory.factoryMethod(), factory.factoryDesc(), false);
                    // If factory returns Object (or a type different from the constructed class),
                    // emit CHECKCAST so the verifier knows the actual type on the stack.
                    // Use the original (pre-remap) class name — ClassRemapper will remap it.
                    String factoryReturnType = factory.factoryDesc().substring(
                            factory.factoryDesc().lastIndexOf(')') + 1);
                    if (factoryReturnType.equals("Ljava/lang/Object;")) {
                        super.visitTypeInsn(Opcodes.CHECKCAST, originalClass);
                    }
                    return;
                }
                // Not a redirect for this descriptor — flush the buffered NEW+DUP and proceed
                flushPendingNew();
            } else if (pendingNewClass != null && opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
                // Different class <init> (nested constructor) — don't flush, let it through
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            } else {
                // Non-<init> method calls are fine as constructor args — don't flush
            }

            // Check for super() constructor descriptor changes (no pending NEW = super call)
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                    && pendingNewClass == null && !superCtorRedirects.isEmpty()) {
                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                String resolvedDesc = resolveDescriptor(descriptor);
                ConstructorKey skey = new ConstructorKey(resolvedOwner, resolvedDesc);
                SuperCtorRedirect scr = superCtorRedirects.get(skey);
                if (scr == null) {
                    skey = new ConstructorKey(resolvedOwner, descriptor);
                    scr = superCtorRedirects.get(skey);
                }
                if (scr != null) {
                    // Push the extra argument(s) before the INVOKESPECIAL
                    if (scr.extraFieldOwner() != null) {
                        super.visitFieldInsn(Opcodes.GETSTATIC,
                            scr.extraFieldOwner(), scr.extraFieldName(), scr.extraFieldDesc());
                    }
                    LOGGER.info("Super ctor redirect: {}.{} -> {}",
                        owner, descriptor, scr.newDesc());
                    super.visitMethodInsn(opcode, owner, name, scr.newDesc(), isInterface);
                    return;
                }
            }

            // OPTIMIZATION: Fast path - skip if owner not in our redirect set
            if (!methodRedirectOwners.contains(owner)) {
                // Still fix class→interface opcode even on fast path
                int fixedOpcode = fixClassToInterfaceOpcode(opcode, owner);
                boolean fixedIsInterface = fixedOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                super.visitMethodInsn(fixedOpcode, owner, name, descriptor, fixedIsInterface);
                return;
            }

            // Check if this method call needs to be redirected
            MethodKey key = new MethodKey(owner, name, descriptor);
            MethodTarget target = methodRedirects.get(key);

            // If not found, try resolving the descriptor through classRedirects
            // (bytecode has intermediary names like class_437 in descriptors,
            // but redirects are registered with Mojang names like Screen)
            if (target == null && !classRedirects.isEmpty()) {
                String resolvedDesc = resolveDescriptor(descriptor);
                if (!resolvedDesc.equals(descriptor)) {
                    MethodKey resolvedKey = new MethodKey(owner, name, resolvedDesc);
                    target = methodRedirects.get(resolvedKey);
                }
            }

            if (target != null) {
                // Redirect the call
                LOGGER.trace("Redirecting {}.{}{} -> {}.{}{}",
                        owner, name, descriptor,
                        target.owner, target.name, target.desc);

                if (target.devirtualize()) {
                    // Instance → static: change opcode and use static descriptor
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, target.owner, target.name,
                            target.desc, false);
                    // If return type changed (e.g., Object vs Either), emit CHECKCAST
                    // to satisfy the verifier's type checking
                    String origReturn = descriptor.substring(descriptor.lastIndexOf(')') + 1);
                    String newReturn = target.desc.substring(target.desc.lastIndexOf(')') + 1);
                    if (!origReturn.equals(newReturn) && origReturn.startsWith("L")) {
                        String origReturnClass = origReturn.substring(1, origReturn.length() - 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, origReturnClass);
                    }
                } else {
                    int fixedOpcode = fixClassToInterfaceOpcode(opcode, target.owner);
                    super.visitMethodInsn(fixedOpcode, target.owner, target.name,
                            target.desc, fixedOpcode == Opcodes.INVOKEINTERFACE);
                    // Emit CHECKCAST when return type changed (e.g., Object vs Event)
                    String origReturn = descriptor.substring(descriptor.lastIndexOf(')') + 1);
                    String newReturn = target.desc.substring(target.desc.lastIndexOf(')') + 1);
                    if (!origReturn.equals(newReturn) && origReturn.startsWith("L")) {
                        String origReturnClass = origReturn.substring(1, origReturn.length() - 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, origReturnClass);
                    }
                }
            } else {
                // No redirect, pass through — but fix class→interface opcode if needed
                int fixedOpcode = fixClassToInterfaceOpcode(opcode, owner);
                // For KNOWN_INTERFACES (classes that became interfaces like DataResult):
                // - INVOKEVIRTUAL → INVOKEINTERFACE (handled by fixClassToInterfaceOpcode)
                // - INVOKESTATIC stays INVOKESTATIC but needs isInterface=true
                boolean fixedIsInterface = fixedOpcode == Opcodes.INVOKEINTERFACE || isInterface
                    || (opcode == Opcodes.INVOKESTATIC && KNOWN_INTERFACES.contains(owner));
                super.visitMethodInsn(fixedOpcode, owner, name, descriptor, fixedIsInterface);
            }
        }
        
        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            // Don't flush — ALOAD/ILOAD etc. are constructor arguments
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitLdcInsn(Object value) {
            // Don't flush — LDC is commonly a constructor argument
            super.visitLdcInsn(value);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Don't flush — field accesses can be constructor arguments
            FieldKey key = new FieldKey(owner, name);

            // Check field-to-accessor redirects first (GETFIELD→getter, PUTFIELD→setter)
            FieldAccessorTarget accessor = fieldAccessorRedirects.get(key);
            if (accessor != null) {
                if (opcode == Opcodes.GETFIELD) {
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            accessor.getterOwner(), accessor.getterName(), accessor.getterDesc(), false);
                    LOGGER.trace("Field accessor redirect: GETFIELD {}.{} -> {}.{}()",
                            owner, name, accessor.getterOwner(), accessor.getterName());
                } else if (opcode == Opcodes.PUTFIELD) {
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            accessor.setterOwner(), accessor.setterName(), accessor.setterDesc(), false);
                    LOGGER.trace("Field accessor redirect: PUTFIELD {}.{} -> {}.{}()",
                            owner, name, accessor.setterOwner(), accessor.setterName());
                } else {
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                }
                return;
            }

            // Check if this field access needs to be redirected
            FieldTarget target = fieldRedirects.get(key);

            if (target != null) {
                // Check if this is a field-to-method redirect
                if (target.newDesc != null && target.newDesc.startsWith("(")) {
                    // Convert GETSTATIC/GETFIELD to INVOKESTATIC
                    // (Field access becomes a static method call that returns the value)
                    int newOpcode = Opcodes.INVOKESTATIC;
                    super.visitMethodInsn(newOpcode, target.owner, target.name, target.newDesc, false);
                    LOGGER.trace("Redirected field {}.{} -> method {}.{}{}",
                            owner, name, target.owner, target.name, target.newDesc);
                } else {
                    // Standard field-to-field redirect
                    // Use new descriptor if provided, otherwise keep original
                    String newDescriptor = (target.newDesc != null) ? target.newDesc : descriptor;
                    super.visitFieldInsn(opcode, target.owner, target.name, newDescriptor);
                }
            } else {
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }
        }
    }
    
    // --- Key/Target record classes ---
    
    public record MethodKey(String owner, String name, String desc) {}
    public record MethodTarget(String owner, String name, String desc, boolean devirtualize) {
        /** Convenience constructor without devirtualize flag */
        public MethodTarget(String owner, String name, String desc) {
            this(owner, name, desc, false);
        }
    }
    public record FieldKey(String owner, String name) {}
    public record FieldTarget(String owner, String name, String oldDesc, String newDesc) {}
    public record FieldAccessorTarget(
        String getterOwner, String getterName, String getterDesc,
        String setterOwner, String setterName, String setterDesc
    ) {}
    public record SuperclassRedirect(String newSuperclass, String[] addInterfaces) {}
    public record ConstructorKey(String className, String constructorDesc) {}
    public record FactoryTarget(String factoryClass, String factoryMethod, String factoryDesc) {}
    /**
     * Describes a super() constructor descriptor change.
     * When a subclass calls super(oldDesc), we redirect to super(newDesc)
     * by pushing extra arguments before the INVOKESPECIAL.
     *
     * @param newDesc the new constructor descriptor
     * @param extraFieldOwner class owning the extra static field to push (or null)
     * @param extraFieldName name of the static field to push as extra arg
     * @param extraFieldDesc descriptor of the static field
     */
    public record SuperCtorRedirect(String newDesc, String extraFieldOwner,
                                     String extraFieldName, String extraFieldDesc) {}
    
    public int getMethodRedirectCount() {
        return methodRedirects.size();
    }
    
    public int getClassRedirectCount() {
        return classRedirects.size();
    }
    
    public int getFieldRedirectCount() {
        return fieldRedirects.size();
    }
    
    public Map<MethodKey, MethodTarget> getMethodRedirects() {
        return Collections.unmodifiableMap(methodRedirects);
    }
    
    public Map<String, String> getClassRedirects() {
        return Collections.unmodifiableMap(classRedirects);
    }

    public Map<FieldKey, FieldTarget> getFieldRedirects() {
        return Collections.unmodifiableMap(fieldRedirects);
    }

    /**
     * Get intermediary method name mappings (method_XXXX → Mojang name).
     */
    public Map<String, String> getIntermediaryMethodNames() {
        return Collections.unmodifiableMap(intermediaryMethodNames);
    }

    /**
     * Get intermediary field name mappings (field_XXXX → Mojang name).
     */
    public Map<String, String> getIntermediaryFieldNames() {
        return Collections.unmodifiableMap(intermediaryFieldNames);
    }
}
