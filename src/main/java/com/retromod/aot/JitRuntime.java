/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import com.retromod.core.RetromodTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transforms @JitRequired-marked classes at load time. HybridCompiler marks the
 * regions it couldn't AOT compile; this picks them up when the class loads.
 */
public class JitRuntime implements ClassFileTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-jit");

    private static final String JIT_REQUIRED_DESC = "Lcom/retromod/aot/JitRequired;";

    private final RetromodTransformer transformer;

    private final Map<String, byte[]> transformedCache = new ConcurrentHashMap<>();

    private int classesTransformed = 0;
    private int methodsTransformed = 0;
    private int regionsTransformed = 0;
    
    public JitRuntime(RetromodTransformer transformer) {
        this.transformer = transformer;
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, 
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, 
            byte[] classfileBuffer) {
        
        if (className == null || classfileBuffer == null) {
            return null;
        }

        if (!hasJitMarkers(classfileBuffer)) {
            return null;
        }
        
        try {
            byte[] result = transformJitMarkedClass(classfileBuffer, className);
            classesTransformed++;
            return result;
        } catch (Exception e) {
            LOGGER.error("JIT transformation failed for: {}", className, e);
            return null;
        }
    }
    
    /** Scans the constant pool for the annotation descriptor before paying for a full parse. */
    private boolean hasJitMarkers(byte[] classBytes) {
        String marker = "com/retromod/aot/JitRequired";
        String content = new String(classBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return content.contains(marker);
    }
    
    private byte[] transformJitMarkedClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        
        boolean modified = false;
        
        for (MethodNode method : classNode.methods) {
            if (hasJitAnnotation(method)) {
                modified |= transformJitMethod(method, classNode.name);
            }
        }
        
        if (!modified) {
            return null;
        }

        removeJitAnnotations(classNode);

        // SafeClassWriter, since JIT can run off-thread where MC classes aren't
        // resolvable via Class.forName and getCommonSuperClass needs the non-throwing
        // fallback. See AotCompiler.transformClassSimple.
        ClassWriter writer = new com.retromod.util.SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }
    
    private boolean hasJitAnnotation(MethodNode method) {
        if (method.visibleAnnotations == null) return false;
        
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (JIT_REQUIRED_DESC.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean transformJitMethod(MethodNode method, String className) {
        LOGGER.debug("JIT transforming: {}.{}", className, method.name);

        AnnotationNode jitAnnotation = getJitAnnotation(method);
        if (jitAnnotation == null) return false;

        boolean fullMethod = isFullMethodJit(jitAnnotation);
        List<Integer> regions = getJitRegions(jitAnnotation);

        boolean modified = false;

        if (fullMethod) {
            modified = transformEntireMethod(method);
            methodsTransformed++;
        } else if (!regions.isEmpty()) {
            modified = transformMethodRegions(method, regions);
            regionsTransformed += regions.size();
        }

        return modified;
    }

    private AnnotationNode getJitAnnotation(MethodNode method) {
        if (method.visibleAnnotations == null) return null;
        
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (JIT_REQUIRED_DESC.equals(annotation.desc)) {
                return annotation;
            }
        }
        return null;
    }
    
    private boolean isFullMethodJit(AnnotationNode annotation) {
        if (annotation.values == null) return true;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            if ("fullMethod".equals(key) && Boolean.TRUE.equals(value)) {
                return true;
            }
        }
        return false;
    }
    
    /** Instruction indices that need JIT transformation. */
    @SuppressWarnings("unchecked")
    private List<Integer> getJitRegions(AnnotationNode annotation) {
        if (annotation.values == null) return Collections.emptyList();
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            if ("regions".equals(key) && value instanceof List<?>) {
                return (List<Integer>) value;
            }
        }
        return Collections.emptyList();
    }
    
    private boolean transformEntireMethod(MethodNode method) {
        boolean modified = false;

        for (AbstractInsnNode insn : method.instructions) {
            modified |= transformInstruction(insn);
        }

        return modified;
    }

    private boolean transformMethodRegions(MethodNode method, List<Integer> regions) {
        boolean modified = false;
        Set<Integer> regionSet = new HashSet<>(regions);

        int index = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (regionSet.contains(index)) {
                modified |= transformInstruction(insn);
            }
            index++;
        }

        return modified;
    }

    private boolean transformInstruction(AbstractInsnNode insn) {
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                return transformMethodInsn(methodInsn);
            }

            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                return transformFieldInsn(fieldInsn);
            }

            case AbstractInsnNode.TYPE_INSN -> {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                return transformTypeInsn(typeInsn);
            }

            case AbstractInsnNode.LDC_INSN -> {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                return transformLdcInsn(ldcInsn);
            }
        }

        return false;
    }

    private boolean transformMethodInsn(MethodInsnNode insn) {
        var key = new RetromodTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        var target = transformer.getMethodRedirects().get(key);

        if (target != null) {
            LOGGER.trace("JIT redirect: {}.{} -> {}.{}",
                insn.owner, insn.name, target.owner(), target.name());
            insn.owner = target.owner();
            insn.name = target.name();
            insn.desc = target.desc();
            return true;
        }

        String newOwner = transformer.getClassRedirects().get(insn.owner);
        if (newOwner != null) {
            insn.owner = newOwner;
            return true;
        }

        if (isReflectiveCall(insn)) {
            return handleReflectiveCall(insn);
        }

        return false;
    }

    private boolean transformFieldInsn(FieldInsnNode insn) {
        String newOwner = transformer.getClassRedirects().get(insn.owner);
        if (newOwner != null) {
            insn.owner = newOwner;
            return true;
        }
        return false;
    }

    private boolean transformTypeInsn(TypeInsnNode insn) {
        String newType = transformer.getClassRedirects().get(insn.desc);
        if (newType != null) {
            insn.desc = newType;
            return true;
        }
        return false;
    }
    
    private boolean transformLdcInsn(LdcInsnNode insn) {
        if (insn.cst instanceof Type type) {
            if (type.getSort() == Type.OBJECT) {
                String newType = transformer.getClassRedirects().get(type.getInternalName());
                if (newType != null) {
                    insn.cst = Type.getObjectType(newType);
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean isReflectiveCall(MethodInsnNode insn) {
        return insn.owner.equals("java/lang/Class") ||
               insn.owner.equals("java/lang/reflect/Method") ||
               insn.owner.equals("java/lang/reflect/Field") ||
               insn.owner.startsWith("java/lang/invoke/");
    }

    // Reflection can't be remapped statically; logging only until a runtime wrapper is wired in.
    private boolean handleReflectiveCall(MethodInsnNode insn) {
        LOGGER.trace("JIT detected reflective call: {}.{}", insn.owner, insn.name);
        return false;
    }

    private void removeJitAnnotations(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations != null) {
                method.visibleAnnotations.removeIf(a -> JIT_REQUIRED_DESC.equals(a.desc));
            }
        }
    }
    
    public JitStats getStats() {
        return new JitStats(classesTransformed, methodsTransformed, regionsTransformed);
    }
    
    public record JitStats(
        int classesTransformed,
        int methodsTransformed,
        int regionsTransformed
    ) {}
}
