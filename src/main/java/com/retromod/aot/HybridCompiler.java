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

import java.util.*;

/**
 * Hybrid AOT/JIT compiler. Analyzes each method on its own: fully analyzable methods are
 * AOT-compiled, the rest get their JIT-requiring instructions (reflection, dynamic proxies,
 * unknown-bootstrap invokedynamic, obfuscated bodies) marked for runtime transformation,
 * instead of dropping a whole class to JIT.
 */
public class HybridCompiler {

    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-hybrid");

    private static final String JIT_REQUIRED_MARKER = "Lcom/retromod/aot/JitRequired;";

    private int methodsFullyAot = 0;
    private int methodsPartialAot = 0;
    private int methodsJitOnly = 0;
    private int regionsMarkedForJit = 0;
    
    private final RetromodTransformer transformer;
    
    public HybridCompiler(RetromodTransformer transformer) {
        this.transformer = transformer;
    }
    
    /** Returns the transformed bytecode with JIT markers where needed. */
    public HybridCompilationResult compileClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        
        List<MethodAnalysis> methodAnalyses = new ArrayList<>();
        boolean classModified = false;

        for (MethodNode method : classNode.methods) {
            MethodAnalysis analysis = analyzeMethod(classNode.name, method);
            methodAnalyses.add(analysis);

            switch (analysis.compilationMode) {
                case FULL_AOT:
                    boolean modified = transformMethodAot(method);
                    if (modified) classModified = true;
                    methodsFullyAot++;
                    break;

                case PARTIAL_AOT:
                    transformMethodPartial(method, analysis);
                    classModified = true;
                    methodsPartialAot++;
                    break;

                case JIT_ONLY:
                    markMethodForJit(method);
                    classModified = true;
                    methodsJitOnly++;
                    break;
            }
        }

        // SafeClassWriter, not raw ClassWriter: off-thread AOT can't resolve MC classes via
        // Class.forName, so getCommonSuperClass needs a non-throwing fallback (see AotCompiler.transformClassSimple).
        ClassWriter writer = new com.retromod.util.SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] result = writer.toByteArray();
        
        return new HybridCompilationResult(
            result,
            methodsFullyAot,
            methodsPartialAot,
            methodsJitOnly,
            regionsMarkedForJit,
            methodAnalyses
        );
    }
    
    private MethodAnalysis analyzeMethod(String className, MethodNode method) {
        MethodAnalysis analysis = new MethodAnalysis(method.name, method.desc);

        if (method.instructions == null || method.instructions.size() == 0) {
            analysis.compilationMode = CompilationMode.FULL_AOT;
            return analysis;
        }

        for (AbstractInsnNode insn : method.instructions) {
            InstructionAnalysis insnAnalysis = analyzeInstruction(insn);

            if (insnAnalysis.requiresJit) {
                analysis.jitRegions.add(new JitRegion(
                    method.instructions.indexOf(insn),
                    insnAnalysis.reason
                ));
            }

            if (insnAnalysis.isObfuscated) {
                analysis.obfuscatedRegions.add(method.instructions.indexOf(insn));
            }

            if (insnAnalysis.needsTransformation) {
                analysis.transformableInstructions.add(method.instructions.indexOf(insn));
            }
        }

        if (analysis.jitRegions.isEmpty() && analysis.obfuscatedRegions.isEmpty()) {
            analysis.compilationMode = CompilationMode.FULL_AOT;
        } else if (analysis.transformableInstructions.size() > analysis.jitRegions.size() * 2) {
            analysis.compilationMode = CompilationMode.PARTIAL_AOT;
        } else {
            analysis.compilationMode = CompilationMode.JIT_ONLY;
        }

        return analysis;
    }

    private InstructionAnalysis analyzeInstruction(AbstractInsnNode insn) {
        InstructionAnalysis result = new InstructionAnalysis();
        
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                result.needsTransformation = needsMethodRedirect(methodInsn);
                result.requiresJit = isReflectiveCall(methodInsn);
                result.isObfuscated = isObfuscatedReference(methodInsn.owner, methodInsn.name);
                
                if (result.requiresJit) {
                    result.reason = "Reflective method call: " + methodInsn.owner + "." + methodInsn.name;
                }
            }
            
            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                result.needsTransformation = needsFieldRedirect(fieldInsn);
                result.isObfuscated = isObfuscatedReference(fieldInsn.owner, fieldInsn.name);
            }
            
            case AbstractInsnNode.TYPE_INSN -> {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                result.needsTransformation = needsClassRedirect(typeInsn.desc);
                result.isObfuscated = isObfuscatedClassName(typeInsn.desc);
            }
            
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN -> {
                InvokeDynamicInsnNode dynInsn = (InvokeDynamicInsnNode) insn;
                result.requiresJit = !isKnownBootstrapMethod(dynInsn);
                result.reason = "Unknown invokedynamic: " + dynInsn.name;
            }
            
            case AbstractInsnNode.LDC_INSN -> {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                if (ldcInsn.cst instanceof Type type) {
                    result.needsTransformation = needsClassRedirect(type.getInternalName());
                }
            }
        }
        
        return result;
    }
    
    private boolean isReflectiveCall(MethodInsnNode insn) {
        String owner = insn.owner;

        if (owner.equals("java/lang/Class") &&
            (insn.name.equals("getMethod") || insn.name.equals("getDeclaredMethod") ||
             insn.name.equals("getField") || insn.name.equals("getDeclaredField"))) {
            return true;
        }

        if (owner.equals("java/lang/reflect/Method") && insn.name.equals("invoke")) {
            return true;
        }

        if (owner.equals("java/lang/reflect/Field") &&
            (insn.name.equals("get") || insn.name.equals("set"))) {
            return true;
        }

        if (owner.startsWith("java/lang/invoke/MethodHandle")) {
            return true;
        }

        return false;
    }

    private boolean isObfuscatedReference(String owner, String name) {
        // Short names are suspect, unless a common Java convention.
        if (name.length() <= 2 && !name.equals("<init>") && !name.equals("<clinit>")) {
            if (!name.matches("(is|to|of|at|in|on|up|id|op)")) {
                return true;
            }
        }

        return isObfuscatedClassName(owner);
    }

    private boolean isObfuscatedClassName(String className) {
        if (className == null) return false;

        String simpleName = className.substring(className.lastIndexOf('/') + 1);

        if (simpleName.length() <= 2) {
            return true;
        }

        // Proguard-style names (aaa, aab, ...)
        if (simpleName.matches("[a-z]{2,3}")) {
            return true;
        }

        // Default package
        if (!className.contains("/")) {
            return true;
        }

        return false;
    }

    private boolean isKnownBootstrapMethod(InvokeDynamicInsnNode insn) {
        Handle bsm = insn.bsm;
        String owner = bsm.getOwner();

        if (owner.equals("java/lang/invoke/LambdaMetafactory")) {
            return true;
        }

        if (owner.equals("java/lang/invoke/StringConcatFactory")) {
            return true;
        }

        if (owner.equals("java/lang/runtime/ObjectMethods")) {
            return true;
        }

        return false;
    }

    private boolean needsMethodRedirect(MethodInsnNode insn) {
        var key = new RetromodTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        return transformer.getMethodRedirects().containsKey(key);
    }

    private boolean needsFieldRedirect(FieldInsnNode insn) {
        return transformer.getClassRedirects().containsKey(insn.owner);
    }

    private boolean needsClassRedirect(String className) {
        return transformer.getClassRedirects().containsKey(className);
    }

    private boolean transformMethodAot(MethodNode method) {
        boolean modified = false;

        for (AbstractInsnNode insn : method.instructions) {
            switch (insn.getType()) {
                case AbstractInsnNode.METHOD_INSN -> {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    modified |= transformMethodInsn(methodInsn);
                }
                
                case AbstractInsnNode.FIELD_INSN -> {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    modified |= transformFieldInsn(fieldInsn);
                }
                
                case AbstractInsnNode.TYPE_INSN -> {
                    TypeInsnNode typeInsn = (TypeInsnNode) insn;
                    modified |= transformTypeInsn(typeInsn);
                }
                
                case AbstractInsnNode.LDC_INSN -> {
                    LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                    modified |= transformLdcInsn(ldcInsn);
                }
            }
        }
        
        return modified;
    }
    
    /** Partial AOT via the instruction-level transformer, marking specific regions for JIT. */
    private void transformMethodPartial(MethodNode method, MethodAnalysis analysis) {
        InstructionLevelTransformer insnTransformer = new InstructionLevelTransformer(transformer);
        InstructionLevelTransformer.TransformResult result =
            insnTransformer.transformMethod(method, "partial");

        regionsMarkedForJit += result.jitRegions.size();

        if (!result.jitRegions.isEmpty()) {
            LOGGER.debug("Method {} has {} AOT, {} JIT instructions",
                method.name, result.aotCount, result.jitCount);
        }
    }

    /** Annotate the method with the JIT regions; the runtime transformer handles them. */
    private void injectJitHooks(MethodNode method, List<JitRegion> regions) {
        if (regions.isEmpty()) return;

        if (method.visibleAnnotations == null) {
            method.visibleAnnotations = new ArrayList<>();
        }

        AnnotationNode jitAnnotation = new AnnotationNode(JIT_REQUIRED_MARKER);
        jitAnnotation.values = new ArrayList<>();
        jitAnnotation.values.add("regions");

        List<Integer> indices = new ArrayList<>();
        for (JitRegion region : regions) {
            indices.add(region.instructionIndex);
        }
        jitAnnotation.values.add(indices);

        method.visibleAnnotations.add(jitAnnotation);

        LOGGER.debug("Method {} has {} JIT regions", method.name, regions.size());
    }

    private void markMethodForJit(MethodNode method) {
        if (method.visibleAnnotations == null) {
            method.visibleAnnotations = new ArrayList<>();
        }

        AnnotationNode jitAnnotation = new AnnotationNode(JIT_REQUIRED_MARKER);
        jitAnnotation.values = new ArrayList<>();
        jitAnnotation.values.add("fullMethod");
        jitAnnotation.values.add(true);

        method.visibleAnnotations.add(jitAnnotation);
    }

    private boolean transformMethodInsn(MethodInsnNode insn) {
        var key = new RetromodTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        var target = transformer.getMethodRedirects().get(key);

        if (target != null) {
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


    private void transformInstruction(AbstractInsnNode insn) {
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> transformMethodInsn((MethodInsnNode) insn);
            case AbstractInsnNode.FIELD_INSN -> transformFieldInsn((FieldInsnNode) insn);
            case AbstractInsnNode.TYPE_INSN -> transformTypeInsn((TypeInsnNode) insn);
            case AbstractInsnNode.LDC_INSN -> transformLdcInsn((LdcInsnNode) insn);
        }
    }

    public CompilationStats getStats() {
        return new CompilationStats(methodsFullyAot, methodsPartialAot, methodsJitOnly, regionsMarkedForJit);
    }

    public void resetStats() {
        methodsFullyAot = 0;
        methodsPartialAot = 0;
        methodsJitOnly = 0;
        regionsMarkedForJit = 0;
    }

    public enum CompilationMode {
        FULL_AOT,      // Entire method can be AOT compiled
        PARTIAL_AOT,   // Some instructions AOT, some JIT
        JIT_ONLY       // Entire method needs JIT
    }


    public static class MethodAnalysis {
        public final String methodName;
        public final String methodDesc;
        public CompilationMode compilationMode = CompilationMode.FULL_AOT;
        public List<JitRegion> jitRegions = new ArrayList<>();
        public List<Integer> obfuscatedRegions = new ArrayList<>();
        public List<Integer> transformableInstructions = new ArrayList<>();
        
        public MethodAnalysis(String name, String desc) {
            this.methodName = name;
            this.methodDesc = desc;
        }
    }
    
    public static class JitRegion {
        public final int instructionIndex;
        public final String reason;
        
        public JitRegion(int index, String reason) {
            this.instructionIndex = index;
            this.reason = reason;
        }
    }
    
    public static class InstructionAnalysis {
        public boolean needsTransformation = false;
        public boolean requiresJit = false;
        public boolean isObfuscated = false;
        public String reason = null;
    }
    
    public record HybridCompilationResult(
        byte[] bytecode,
        int methodsFullyAot,
        int methodsPartialAot,
        int methodsJitOnly,
        int jitRegions,
        List<MethodAnalysis> methodAnalyses
    ) {}
    
    public record CompilationStats(
        int methodsFullyAot,
        int methodsPartialAot,
        int methodsJitOnly,
        int jitRegions
    ) {}
}
