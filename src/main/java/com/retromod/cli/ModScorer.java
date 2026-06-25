/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.cli;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.RetromodTransformer.*;
import com.retromod.embedder.ModVersionInfo;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Scores a mod JAR for compatibility with the target MC version (26.1).
 *
 * Auto-detects the loader (Fabric/Forge/NeoForge) from JAR metadata, scans every
 * .class with ASM for external references, then checks each one against an index
 * built from the MC client JAR, loader APIs, and Retromod's registered redirects.
 */
public class ModScorer {

    public enum ModLoader {
        FABRIC("fabric"),
        FORGE("forge"),
        NEOFORGE("neoforge"),
        UNKNOWN("unknown");

        private final String id;

        ModLoader(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return switch (this) {
                case FABRIC -> "Fabric";
                case FORGE -> "Forge";
                case NEOFORGE -> "NeoForge";
                case UNKNOWN -> "Unknown";
            };
        }
    }

    // Index of what exists in the target MC version.
    private final Set<String> mcClasses = new HashSet<>(8000);
    private final Map<String, Set<String>> mcMethods = new HashMap<>(8000);  // class -> "name desc"
    private final Map<String, Set<String>> mcFields = new HashMap<>(8000);   // class -> "name"
    private final Map<String, String> mcSuperclasses = new HashMap<>(8000);
    private final Map<String, String[]> mcInterfaces = new HashMap<>(8000);

    private final Map<MethodKey, MethodTarget> methodRedirects;
    private final Map<String, String> classRedirects;
    private final Map<FieldKey, FieldTarget> fieldRedirects;

    // Packages shipped by MC/JVM/loaders: references to them are never missing, so skip them.
    private static final String[] LIBRARY_PREFIXES = {
        "java/", "javax/", "jdk/", "sun/",
        "com/google/gson/", "com/google/common/",
        "org/slf4j/", "org/apache/logging/",
        "org/apache/commons/", "org/apache/maven/",
        "org/objectweb/asm/", "org/lwjgl/",
        "io/netty/", "com/mojang/logging/",
        "com/mojang/brigadier/", "com/mojang/authlib/",
        "com/mojang/datafixers/", "com/mojang/serialization/",
        "com/mojang/math/", "it/unimi/dsi/fastutil/",
        "org/joml/",
        // mod loader internals
        "net/fabricmc/loader/", "net/fabricmc/api/",
        "cpw/mods/", "net/minecraftforge/fml/",
        "net/neoforged/fml/",
        "com/retromod/",
    };

    private static final String[] FABRIC_API_PREFIXES = {
        "net/fabricmc/fabric/api/",
        "net/fabricmc/fabric/impl/",
        "net/fabricmc/fabric/mixin/",
    };

    private static final String[] FORGE_API_PREFIXES = {
        "net/minecraftforge/common/",
        "net/minecraftforge/client/",
        "net/minecraftforge/server/",
        "net/minecraftforge/event/",
        "net/minecraftforge/eventbus/",
        "net/minecraftforge/registries/",
        "net/minecraftforge/network/",
        "net/minecraftforge/items/",
        "net/minecraftforge/fluids/",
        "net/minecraftforge/energy/",
        "net/minecraftforge/data/",
        "net/minecraftforge/resource/",
    };

    private static final String[] NEOFORGE_API_PREFIXES = {
        "net/neoforged/neoforge/",
        "net/neoforged/bus/",
        "net/neoforged/api/",
        "net/neoforged/neoforgespi/",
    };

    private final ScoreResult result = new ScoreResult();

    public ModScorer(RetromodTransformer transformer) {
        this.methodRedirects = transformer.getMethodRedirects();
        this.classRedirects = transformer.getClassRedirects();
        this.fieldRedirects = transformer.getFieldRedirects();
    }

    private void indexJar(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.isDirectory()) continue;
                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    String[] currentClass = new String[1];
                    cr.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int version, int access, String name, String sig,
                                String superName, String[] interfaces) {
                            currentClass[0] = name;
                            mcClasses.add(name);
                            if (superName != null) mcSuperclasses.put(name, superName);
                            if (interfaces != null) mcInterfaces.put(name, interfaces);
                        }
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc,
                                String sig, String[] exceptions) {
                            mcMethods.computeIfAbsent(currentClass[0], k -> new HashSet<>())
                                .add(name + desc);
                            return null;
                        }
                        @Override
                        public FieldVisitor visitField(int access, String name, String desc,
                                String sig, Object value) {
                            mcFields.computeIfAbsent(currentClass[0], k -> new HashSet<>())
                                .add(name);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                } catch (Exception e) {
                    // skip unreadable class files
                }
            }
        }
    }

    public void loadMcJar(Path mcJarPath) throws IOException {
        indexJar(mcJarPath);
    }

    /** Indexes the Fabric API JAR plus any nested JARs under META-INF/jars/. */
    public void loadFabricApiJar(Path fabricApiPath) throws IOException {
        indexJar(fabricApiPath);

        try (JarFile jar = new JarFile(fabricApiPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/jars/") && entry.getName().endsWith(".jar")) {
                    Path temp = Files.createTempFile("retromod-fapi-", ".jar");
                    try {
                        try (InputStream is = jar.getInputStream(entry);
                             OutputStream os = Files.newOutputStream(temp)) {
                            is.transferTo(os);
                        }
                        indexJar(temp);
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
            }
        }
    }

    /** Indexes a Forge or NeoForge library JAR (from the launcher's libraries folder). */
    public void loadLoaderJar(Path loaderJarPath) throws IOException {
        indexJar(loaderJarPath);
    }

    /**
     * Detects the mod loader from JAR metadata: fabric.mod.json, neoforge.mods.toml,
     * mods.toml, or mcmod.info, falling back to scanning for loader-specific classes.
     */
    public static ModLoader detectModLoader(Path modJarPath) throws IOException {
        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            if (jar.getJarEntry("fabric.mod.json") != null) {
                return ModLoader.FABRIC;
            }
            if (jar.getJarEntry("META-INF/neoforge.mods.toml") != null) {
                return ModLoader.NEOFORGE;
            }
            if (jar.getJarEntry("META-INF/mods.toml") != null) {
                return ModLoader.FORGE;
            }
            if (jar.getJarEntry("mcmod.info") != null) {
                return ModLoader.FORGE;
            }

            // no metadata: fall back to scanning for loader-specific classes
            Enumeration<JarEntry> entries = jar.entries();
            boolean hasForgeRefs = false;
            boolean hasNeoForgeRefs = false;
            boolean hasFabricRefs = false;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("net/minecraftforge/")) hasForgeRefs = true;
                if (name.startsWith("net/neoforged/")) hasNeoForgeRefs = true;
                if (name.startsWith("net/fabricmc/")) hasFabricRefs = true;
            }

            if (hasNeoForgeRefs) return ModLoader.NEOFORGE;
            if (hasForgeRefs) return ModLoader.FORGE;
            if (hasFabricRefs) return ModLoader.FABRIC;
        }
        return ModLoader.UNKNOWN;
    }

    /**
     * Scores a mod JAR for compatibility. The loader comes from {@code modInfo} when
     * known, otherwise it is detected from the JAR.
     *
     * @param modInfo detected mod version info (may be null)
     */
    public ScoreResult analyze(Path modJarPath, ModVersionInfo modInfo) throws IOException {
        ModLoader loader;
        if (modInfo != null && modInfo.modLoaderType() != null) {
            loader = switch (modInfo.modLoaderType()) {
                case "fabric" -> ModLoader.FABRIC;
                case "forge" -> ModLoader.FORGE;
                case "neoforge" -> ModLoader.NEOFORGE;
                default -> detectModLoader(modJarPath);
            };
        } else {
            loader = detectModLoader(modJarPath);
        }
        result.detectedLoader = loader;

        Set<String> modPackages = new HashSet<>();
        if (modInfo != null && modInfo.modPackages() != null) {
            modPackages.addAll(modInfo.modPackages());
        }

        // first pass: every class the mod ships, so we can exclude mod-internal refs later
        Set<String> modClasses = new HashSet<>();
        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace(".class", "");
                    modClasses.add(className);
                }
            }
        }

        // second pass: scan bytecode for external references
        Set<String> referencedClasses = new LinkedHashSet<>();
        Map<String, Set<String>> referencedMethods = new LinkedHashMap<>();  // owner -> "name desc"
        Map<String, Set<String>> referencedFields = new LinkedHashMap<>();   // owner -> "name"
        List<String> mixinTargets = new ArrayList<>();
        List<String> loaderSpecificFindings = new ArrayList<>();

        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            mixinTargets.addAll(extractMixinTargetsForLoader(jar, loader));
            collectLoaderSpecificFindings(jar, loader, loaderSpecificFindings);

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;

                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ReferenceCollector collector = new ReferenceCollector(
                            modClasses, referencedClasses, referencedMethods, referencedFields);
                    cr.accept(collector, ClassReader.SKIP_DEBUG);
                } catch (Exception e) {
                    // skip unreadable classes
                }
            }
        }

        // score class references
        int totalClasses = 0;
        int resolvableClasses = 0;
        int redirectedClasses = 0;
        List<String> missingClasses = new ArrayList<>();

        for (String cls : referencedClasses) {
            if (isModInternal(cls, modClasses) || isLibrary(cls)) continue;
            totalClasses++;

            if (mcClasses.contains(cls) || isLoaderApiClass(cls, loader)) {
                resolvableClasses++;
            } else if (classRedirects.containsKey(cls)) {
                resolvableClasses++;
                redirectedClasses++;
            } else {
                missingClasses.add(cls);
            }
        }

        // score method references
        int totalMethods = 0;
        int resolvableMethods = 0;
        int redirectedMethods = 0;
        List<String> missingMethods = new ArrayList<>();

        for (var entry : referencedMethods.entrySet()) {
            String owner = entry.getKey();
            if (isModInternal(owner, modClasses) || isLibrary(owner)) continue;

            for (String nameDesc : entry.getValue()) {
                totalMethods++;

                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                if (isMethodInHierarchy(resolvedOwner, nameDesc)
                        || isMethodInHierarchy(owner, nameDesc)) {
                    resolvableMethods++;
                    continue;
                }

                int descStart = nameDesc.indexOf('(');
                String name = nameDesc.substring(0, descStart);
                String desc = nameDesc.substring(descStart);
                MethodKey key = new MethodKey(owner, name, desc);
                if (methodRedirects.containsKey(key)) {
                    resolvableMethods++;
                    redirectedMethods++;
                    continue;
                }

                MethodKey resolvedKey = new MethodKey(resolvedOwner, name, desc);
                if (methodRedirects.containsKey(resolvedKey)) {
                    resolvableMethods++;
                    redirectedMethods++;
                    continue;
                }

                // loader API methods come from a polyfill at runtime
                if (isLoaderApiClass(owner, loader)) {
                    resolvableMethods++;
                    continue;
                }

                missingMethods.add(owner.replace('/', '.') + "." + name + desc);
            }
        }

        // score field references
        int totalFields = 0;
        int resolvableFields = 0;
        int redirectedFields = 0;
        List<String> missingFields = new ArrayList<>();

        for (var entry : referencedFields.entrySet()) {
            String owner = entry.getKey();
            if (isModInternal(owner, modClasses) || isLibrary(owner)) continue;

            for (String fieldName : entry.getValue()) {
                totalFields++;

                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                if (isFieldInHierarchy(resolvedOwner, fieldName)
                        || isFieldInHierarchy(owner, fieldName)) {
                    resolvableFields++;
                    continue;
                }

                FieldKey key = new FieldKey(owner, fieldName);
                if (fieldRedirects.containsKey(key)) {
                    resolvableFields++;
                    redirectedFields++;
                    continue;
                }

                FieldKey resolvedKey = new FieldKey(resolvedOwner, fieldName);
                if (fieldRedirects.containsKey(resolvedKey)) {
                    resolvableFields++;
                    redirectedFields++;
                    continue;
                }

                if (isLoaderApiClass(owner, loader)) {
                    resolvableFields++;
                    continue;
                }

                missingFields.add(owner.replace('/', '.') + "." + fieldName);
            }
        }

        // score mixin targets
        int totalMixins = mixinTargets.size();
        int validMixins = 0;
        List<String> brokenMixins = new ArrayList<>();

        for (String target : mixinTargets) {
            String internal = target.replace('.', '/');
            String resolvedTarget = classRedirects.getOrDefault(internal, internal);
            if (mcClasses.contains(resolvedTarget) || mcClasses.contains(internal)) {
                validMixins++;
            } else {
                brokenMixins.add(target);
            }
        }

        // each category: percent of references that resolve (exist in MC, have a
        // redirect/polyfill, or belong to a loader API)
        double classScore = totalClasses > 0
                ? (double) resolvableClasses / totalClasses * 100 : 100;
        double methodScore = totalMethods > 0
                ? (double) (resolvableMethods) / totalMethods * 100 : 100;
        double fieldScore = totalFields > 0
                ? (double) (resolvableFields) / totalFields * 100 : 100;
        double mixinScore = totalMixins > 0
                ? (double) validMixins / totalMixins * 100 : 100;

        // methods break most often, so they carry the most weight; mixins the least
        double overallScore = classScore * 0.3 + methodScore * 0.4 + fieldScore * 0.2 + mixinScore * 0.1;

        result.overallScore = (int) Math.round(overallScore);
        result.classScore = (int) Math.round(classScore);
        result.methodScore = (int) Math.round(methodScore);
        result.fieldScore = (int) Math.round(fieldScore);
        result.mixinScore = (int) Math.round(mixinScore);

        result.totalClasses = totalClasses;
        result.resolvableClasses = resolvableClasses;
        result.redirectedClasses = redirectedClasses;
        result.totalMethods = totalMethods;
        result.resolvableMethods = resolvableMethods;
        result.redirectedMethods = redirectedMethods;
        result.totalFields = totalFields;
        result.resolvableFields = resolvableFields;
        result.redirectedFields = redirectedFields;
        result.totalMixins = totalMixins;
        result.validMixins = validMixins;

        result.missingClasses = missingClasses;
        result.missingMethods = missingMethods;
        result.missingFields = missingFields;
        result.brokenMixins = brokenMixins;
        result.loaderSpecificFindings = loaderSpecificFindings;

        return result;
    }

    /** Extracts mixin targets; each loader keeps its mixin config in a different place. */
    private List<String> extractMixinTargetsForLoader(JarFile jar, ModLoader loader) {
        List<String> targets = new ArrayList<>();

        switch (loader) {
            case FABRIC -> {
                JarEntry fabricJson = jar.getJarEntry("fabric.mod.json");
                if (fabricJson != null) {
                    targets.addAll(extractMixinTargets(jar, fabricJson));
                }
            }
            case FORGE -> targets.addAll(extractForgeMixinTargets(jar));
            case NEOFORGE -> targets.addAll(extractNeoForgeMixinTargets(jar));
            default -> {
                JarEntry fabricJson = jar.getJarEntry("fabric.mod.json");
                if (fabricJson != null) {
                    targets.addAll(extractMixinTargets(jar, fabricJson));
                }
                targets.addAll(extractForgeMixinTargets(jar));
                targets.addAll(extractNeoForgeMixinTargets(jar));
            }
        }
        return targets;
    }

    /** Forge mixin configs come from the MANIFEST MixinConfigs attribute, mods.toml, or root JSON. */
    private List<String> extractForgeMixinTargets(JarFile jar) {
        List<String> targets = new ArrayList<>();
        List<String> mixinConfigs = new ArrayList<>();

        try {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mixinConfigsAttr = manifest.getMainAttributes().getValue("MixinConfigs");
                if (mixinConfigsAttr != null) {
                    for (String config : mixinConfigsAttr.split(",")) {
                        mixinConfigs.add(config.trim());
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }

        JarEntry modsToml = jar.getJarEntry("META-INF/mods.toml");
        if (modsToml != null) {
            try (InputStream is = jar.getInputStream(modsToml)) {
                String toml = new String(is.readAllBytes());
                extractMixinConfigsFromToml(toml, mixinConfigs);
            } catch (IOException e) {
                // ignore
            }
        }

        scanForMixinJsonConfigs(jar, mixinConfigs);

        for (String configName : mixinConfigs) {
            JarEntry configEntry = jar.getJarEntry(configName);
            if (configEntry == null) continue;
            try (InputStream cis = jar.getInputStream(configEntry)) {
                String configJson = new String(cis.readAllBytes());
                targets.addAll(extractTargetsFromMixinConfig(configJson));
            } catch (IOException e) {
                // ignore
            }
        }
        return targets;
    }

    /** Same as Forge but reads neoforge.mods.toml. */
    private List<String> extractNeoForgeMixinTargets(JarFile jar) {
        List<String> targets = new ArrayList<>();
        List<String> mixinConfigs = new ArrayList<>();

        try {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mixinConfigsAttr = manifest.getMainAttributes().getValue("MixinConfigs");
                if (mixinConfigsAttr != null) {
                    for (String config : mixinConfigsAttr.split(",")) {
                        mixinConfigs.add(config.trim());
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }

        JarEntry neoforgeToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
        if (neoforgeToml != null) {
            try (InputStream is = jar.getInputStream(neoforgeToml)) {
                String toml = new String(is.readAllBytes());
                extractMixinConfigsFromToml(toml, mixinConfigs);
            } catch (IOException e) {
                // ignore
            }
        }

        scanForMixinJsonConfigs(jar, mixinConfigs);

        for (String configName : mixinConfigs) {
            JarEntry configEntry = jar.getJarEntry(configName);
            if (configEntry == null) continue;
            try (InputStream cis = jar.getInputStream(configEntry)) {
                String configJson = new String(cis.readAllBytes());
                targets.addAll(extractTargetsFromMixinConfig(configJson));
            } catch (IOException e) {
                // ignore
            }
        }
        return targets;
    }

    /** Pulls config = "..." values out of [[mixins]] TOML sections. */
    private void extractMixinConfigsFromToml(String toml, List<String> mixinConfigs) {
        int idx = 0;
        while ((idx = toml.indexOf("[[mixins]]", idx)) != -1) {
            idx += "[[mixins]]".length();
            int nextSection = toml.indexOf("[[", idx);
            String section = nextSection >= 0 ? toml.substring(idx, nextSection) : toml.substring(idx);
            int configIdx = section.indexOf("config");
            if (configIdx >= 0) {
                int eqIdx = section.indexOf('=', configIdx);
                if (eqIdx >= 0) {
                    int qStart = section.indexOf('"', eqIdx);
                    if (qStart >= 0) {
                        int qEnd = section.indexOf('"', qStart + 1);
                        if (qEnd >= 0) {
                            mixinConfigs.add(section.substring(qStart + 1, qEnd));
                        }
                    }
                }
            }
        }
    }

    /** Picks up root-level *.mixins.json files not already listed. */
    private void scanForMixinJsonConfigs(JarFile jar, List<String> mixinConfigs) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.contains("/") && name.endsWith(".mixins.json")) {
                if (!mixinConfigs.contains(name)) {
                    mixinConfigs.add(name);
                }
            }
        }
    }

    /**
     * Records loader-specific API usage for the report: @SubscribeEvent, ForgeRegistries,
     * Event.register(), the capability system, and so on.
     */
    private void collectLoaderSpecificFindings(JarFile jar, ModLoader loader,
            List<String> findings) {
        Set<String> forgePatterns = new LinkedHashSet<>();
        Set<String> neoforgePatterns = new LinkedHashSet<>();
        Set<String> fabricPatterns = new LinkedHashSet<>();

        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;

            try (InputStream is = jar.getInputStream(entry)) {
                ClassReader cr = new ClassReader(is);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName,
                                    String mDesc, boolean isInterface) {
                                if (owner.startsWith("net/minecraftforge/registries/ForgeRegistries")) {
                                    forgePatterns.add("ForgeRegistries usage");
                                }
                                if (owner.equals("net/minecraftforge/common/MinecraftForge") &&
                                        mName.equals("EVENT_BUS")) {
                                    forgePatterns.add("MinecraftForge.EVENT_BUS");
                                }
                                if (owner.startsWith("net/neoforged/neoforge/common/NeoForge")) {
                                    neoforgePatterns.add("NeoForge common API");
                                }
                                if (owner.contains("neoforged") && owner.contains("capabilit")) {
                                    neoforgePatterns.add("NeoForge capability system");
                                }
                                if (owner.startsWith("net/fabricmc/fabric/api/") &&
                                        mName.equals("register")) {
                                    fabricPatterns.add("Fabric Event.register()");
                                }
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fName,
                                    String fDesc) {
                                if (owner.startsWith("net/minecraftforge/registries/ForgeRegistries")) {
                                    forgePatterns.add("ForgeRegistries." + fName);
                                }
                                if (owner.startsWith("net/neoforged/neoforge/registries/NeoForgeRegistries")) {
                                    neoforgePatterns.add("NeoForgeRegistries." + fName);
                                }
                            }
                        };
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                            forgePatterns.add("@SubscribeEvent annotation");
                        }
                        if (desc.equals("Lnet/minecraftforge/fml/common/Mod;")) {
                            forgePatterns.add("@Mod annotation");
                        }
                        if (desc.contains("EventBusSubscriber")) {
                            forgePatterns.add("@EventBusSubscriber annotation");
                        }
                        if (desc.equals("Lnet/neoforged/bus/api/SubscribeEvent;")) {
                            neoforgePatterns.add("@SubscribeEvent annotation (NeoForge)");
                        }
                        if (desc.equals("Lnet/neoforged/fml/common/Mod;")) {
                            neoforgePatterns.add("@Mod annotation (NeoForge)");
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

                is.reset();
            } catch (Exception e) {
                // skip unreadable classes
            }
        }

        // second pass: the visitAnnotation above is class-level only, so catch method-level ones here
        entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;

            try (InputStream is = jar.getInputStream(entry)) {
                ClassReader cr = new ClassReader(is);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String aDesc, boolean visible) {
                                if (aDesc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                                    forgePatterns.add("@SubscribeEvent annotation");
                                }
                                if (aDesc.equals("Lnet/neoforged/bus/api/SubscribeEvent;")) {
                                    neoforgePatterns.add("@SubscribeEvent annotation (NeoForge)");
                                }
                                return null;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG);
            } catch (Exception e) {
                // skip
            }
        }

        switch (loader) {
            case FORGE -> {
                if (!forgePatterns.isEmpty()) {
                    findings.add("Forge API usage detected:");
                    findings.addAll(forgePatterns.stream().map(s -> "  - " + s).toList());
                }
            }
            case NEOFORGE -> {
                if (!neoforgePatterns.isEmpty()) {
                    findings.add("NeoForge API usage detected:");
                    findings.addAll(neoforgePatterns.stream().map(s -> "  - " + s).toList());
                }
            }
            case FABRIC -> {
                if (!fabricPatterns.isEmpty()) {
                    findings.add("Fabric API usage detected:");
                    findings.addAll(fabricPatterns.stream().map(s -> "  - " + s).toList());
                }
            }
            default -> {
                if (!forgePatterns.isEmpty()) {
                    findings.add("Forge API usage detected:");
                    findings.addAll(forgePatterns.stream().map(s -> "  - " + s).toList());
                }
                if (!neoforgePatterns.isEmpty()) {
                    findings.add("NeoForge API usage detected:");
                    findings.addAll(neoforgePatterns.stream().map(s -> "  - " + s).toList());
                }
                if (!fabricPatterns.isEmpty()) {
                    findings.add("Fabric API usage detected:");
                    findings.addAll(fabricPatterns.stream().map(s -> "  - " + s).toList());
                }
            }
        }
    }

    /** Reads Fabric mixin targets via the mixin config names listed in fabric.mod.json. */
    private List<String> extractMixinTargets(JarFile jar, JarEntry fabricJsonEntry) {
        List<String> targets = new ArrayList<>();
        try (InputStream is = jar.getInputStream(fabricJsonEntry)) {
            String json = new String(is.readAllBytes());
            List<String> mixinConfigs = new ArrayList<>();
            int idx = 0;
            while ((idx = json.indexOf("\"mixins\"", idx)) != -1) {
                int arrStart = json.indexOf('[', idx);
                int arrEnd = json.indexOf(']', arrStart);
                if (arrStart == -1 || arrEnd == -1) break;
                String arr = json.substring(arrStart + 1, arrEnd);
                int si = 0;
                while ((si = arr.indexOf('"', si)) != -1) {
                    int ei = arr.indexOf('"', si + 1);
                    if (ei == -1) break;
                    String configName = arr.substring(si + 1, ei);
                    if (configName.endsWith(".json") || configName.endsWith(".mixins.json")) {
                        mixinConfigs.add(configName);
                    } else if (!configName.isEmpty() && !configName.contains(":")) {
                        // bare config name
                        mixinConfigs.add(configName);
                    }
                    si = ei + 1;
                }
                idx = arrEnd;
            }

            for (String configName : mixinConfigs) {
                JarEntry configEntry = jar.getJarEntry(configName);
                if (configEntry == null) continue;
                try (InputStream cis = jar.getInputStream(configEntry)) {
                    String configJson = new String(cis.readAllBytes());
                    targets.addAll(extractTargetsFromMixinConfig(configJson));
                }
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return targets;
    }

    /** Reads the "package" prefix plus the "mixins"/"client"/"server" class arrays. */
    private List<String> extractTargetsFromMixinConfig(String json) {
        List<String> targets = new ArrayList<>();

        String pkg = extractJsonString(json, "package");

        for (String section : new String[]{"mixins", "client", "server"}) {
            List<String> classNames = extractJsonStringArray(json, section);
            for (String cls : classNames) {
                String fullName = pkg != null ? pkg + "." + cls : cls;
                // tracks the mixin class itself; resolving its @Mixin target is future work
                targets.add(fullName);
            }
        }

        return targets;
    }

    /** Minimal JSON string-value reader (no JSON library needed). */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx == -1) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart == -1) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /** Minimal JSON string-array reader. */
    private List<String> extractJsonStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return result;
        int arrStart = json.indexOf('[', idx);
        if (arrStart == -1) return result;
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd == -1) return result;
        String arr = json.substring(arrStart + 1, arrEnd);
        int si = 0;
        while ((si = arr.indexOf('"', si)) != -1) {
            int ei = arr.indexOf('"', si + 1);
            if (ei == -1) break;
            result.add(arr.substring(si + 1, ei));
            si = ei + 1;
        }
        return result;
    }

    /** True if the method exists on the class or anywhere up its superclass/interface chain. */
    private boolean isMethodInHierarchy(String className, String nameDesc) {
        Set<String> visited = new HashSet<>();
        String current = className;
        while (current != null && visited.add(current)) {
            Set<String> methods = mcMethods.get(current);
            if (methods != null && methods.contains(nameDesc)) {
                return true;
            }
            String[] ifaces = mcInterfaces.get(current);
            if (ifaces != null) {
                for (String iface : ifaces) {
                    if (isMethodInHierarchy(iface, nameDesc)) return true;
                }
            }
            current = mcSuperclasses.get(current);
        }
        return false;
    }

    /** True if the field exists on the class or any superclass. */
    private boolean isFieldInHierarchy(String className, String fieldName) {
        Set<String> visited = new HashSet<>();
        String current = className;
        while (current != null && visited.add(current)) {
            Set<String> fields = mcFields.get(current);
            if (fields != null && fields.contains(fieldName)) {
                return true;
            }
            current = mcSuperclasses.get(current);
        }
        return false;
    }

    private boolean isModInternal(String className, Set<String> modClasses) {
        return modClasses.contains(className);
    }

    private boolean isLibrary(String className) {
        for (String prefix : LIBRARY_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    /** True if the class belongs to the detected loader's API (provided at runtime). */
    private boolean isLoaderApiClass(String className, ModLoader loader) {
        return switch (loader) {
            case FABRIC -> isFabricApiClass(className);
            case FORGE -> isForgeApiClass(className);
            case NEOFORGE -> isNeoForgeApiClass(className);
            case UNKNOWN -> isFabricApiClass(className) ||
                    isForgeApiClass(className) || isNeoForgeApiClass(className);
        };
    }

    private boolean isFabricApiClass(String className) {
        for (String prefix : FABRIC_API_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isForgeApiClass(String className) {
        for (String prefix : FORGE_API_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isNeoForgeApiClass(String className) {
        for (String prefix : NEOFORGE_API_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * ASM visitor that records every class/method/field a mod class references, for later
     * checking against the target MC index. Mod-internal refs (tracked in modClasses) are
     * excluded from scoring.
     */
    private static class ReferenceCollector extends ClassVisitor {
        private final Set<String> modClasses;
        private final Set<String> referencedClasses;
        private final Map<String, Set<String>> referencedMethods;
        private final Map<String, Set<String>> referencedFields;

        ReferenceCollector(Set<String> modClasses,
                Set<String> referencedClasses,
                Map<String, Set<String>> referencedMethods,
                Map<String, Set<String>> referencedFields) {
            super(Opcodes.ASM9);
            this.modClasses = modClasses;
            this.referencedClasses = referencedClasses;
            this.referencedMethods = referencedMethods;
            this.referencedFields = referencedFields;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            if (superName != null) addClassRef(superName);
            if (interfaces != null) {
                for (String iface : interfaces) addClassRef(iface);
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
            addTypesFromDescriptor(desc);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                    addClassRef(owner);
                    referencedMethods.computeIfAbsent(owner, k -> new LinkedHashSet<>())
                            .add(name + desc);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    addClassRef(owner);
                    referencedFields.computeIfAbsent(owner, k -> new LinkedHashSet<>())
                            .add(name);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    if (type != null && !type.startsWith("[")) {
                        addClassRef(type);
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type t && t.getSort() == Type.OBJECT) {
                        addClassRef(t.getInternalName());
                    }
                }
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc,
                String signature, Object value) {
            addTypesFromDescriptor(desc);
            return null;
        }

        private void addClassRef(String internalName) {
            if (internalName != null && !internalName.startsWith("[")) {
                referencedClasses.add(internalName);
            }
        }

        private void addTypesFromDescriptor(String desc) {
            try {
                Type type = Type.getType(desc);
                addTypeRef(type);
                if (type.getSort() == Type.METHOD) {
                    for (Type arg : type.getArgumentTypes()) addTypeRef(arg);
                    addTypeRef(type.getReturnType());
                }
            } catch (Exception e) {
                // ignore malformed descriptors
            }
        }

        private void addTypeRef(Type type) {
            if (type.getSort() == Type.OBJECT) {
                addClassRef(type.getInternalName());
            } else if (type.getSort() == Type.ARRAY) {
                addTypeRef(type.getElementType());
            }
        }
    }

    /** Compatibility results for one mod. Scores are 0-100; higher is better. */
    public static class ScoreResult {
        public ModLoader detectedLoader = ModLoader.UNKNOWN;
        public int overallScore;
        public int classScore, methodScore, fieldScore, mixinScore;
        public int totalClasses, resolvableClasses, redirectedClasses;
        public int totalMethods, resolvableMethods, redirectedMethods;
        public int totalFields, resolvableFields, redirectedFields;
        public int totalMixins, validMixins;
        public List<String> missingClasses = List.of();
        public List<String> missingMethods = List.of();
        public List<String> missingFields = List.of();
        public List<String> brokenMixins = List.of();
        public List<String> loaderSpecificFindings = List.of();

        public String getVerdict() {
            if (overallScore >= 90) return "EXCELLENT -- mod should work perfectly";
            if (overallScore >= 75) return "GOOD -- most features will work";
            if (overallScore >= 50) return "FAIR -- some features may break";
            if (overallScore >= 25) return "POOR -- significant issues expected";
            return "INCOMPATIBLE -- major rework needed";
        }

        public String getVerdictIcon() {
            if (overallScore >= 75) return "OK";
            if (overallScore >= 50) return "WARN";
            return "FAIL";
        }
    }
}
