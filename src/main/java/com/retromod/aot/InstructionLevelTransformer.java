/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import com.retromod.core.RetroModTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Instruction-Level Transformer for maximum granularity in partial AOT.
 * 
 * Instead of marking entire methods for JIT, this transformer:
 * 1. Analyzes each instruction individually
 * 2. AOT transforms instructions that are fully analyzable
 * 3. Wraps ONLY the specific instructions that need JIT in runtime hooks
 * 4. Preserves stack state across the transition
 * 
 * Example:
 * Original code:
 *   entity.getWorld()           // Needs redirect
 *   entity.reflectiveCall()     // Can't statically analyze (reflection)
 *   entity.getName()            // Simple call, no redirect needed
 * 
 * After transformation:
 *   entity.getEntityWorld()     // AOT transformed
 *   JitBridge.invoke(entity, "reflectiveCall")  // JIT wrapper
 *   entity.getName()            // Unchanged
 * 
 * This provides the speed of AOT for most code while maintaining
 * correctness for dynamic code, at instruction-level granularity.
 */
public class InstructionLevelTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-insn");
    
    private final RetroModTransformer transformer;
    
    // Statistics
    private int instructionsAot = 0;
    private int instructionsJit = 0;
    private int instructionsUnchanged = 0;
    
    public InstructionLevelTransformer(RetroModTransformer transformer) {
        this.transformer = transformer;
    }
    
    /**
     * Transform a method with instruction-level granularity.
     * Returns true if any modifications were made.
     */
    public TransformResult transformMethod(MethodNode method, String className) {
        TransformResult result = new TransformResult();
        
        if (method.instructions == null || method.instructions.size() == 0) {
            return result;
        }
        
        InsnList newInstructions = new InsnList();
        
        // Process each instruction
        ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
        while (iterator.hasNext()) {
            AbstractInsnNode insn = iterator.next();
            
            InstructionDecision decision = analyzeInstruction(insn, method, className);
            
            switch (decision.action) {
                case AOT_TRANSFORM -> {
                    // Transform at compile time
                    AbstractInsnNode transformed = transformInstructionAot(insn, decision);
                    newInstructions.add(transformed);
                    instructionsAot++;
                    result.aotCount++;
                    result.modified = true;
                }
                
                case JIT_WRAP -> {
                    // Wrap in JIT bridge for runtime transformation
                    InsnList wrapped = wrapInstructionForJit(insn, decision, method);
                    newInstructions.add(wrapped);
                    instructionsJit++;
                    result.jitCount++;
                    result.modified = true;
                    result.jitRegions.add(new JitRegionInfo(
                        method.instructions.indexOf(insn),
                        decision.reason
                    ));
                }
                
                case KEEP_UNCHANGED -> {
                    // No transformation needed
                    newInstructions.add(insn.clone(null));
                    instructionsUnchanged++;
                    result.unchangedCount++;
                }
            }
        }
        
        // Replace method instructions
        if (result.modified) {
            method.instructions.clear();
            method.instructions.add(newInstructions);
        }
        
        return result;
    }
    
    /**
     * Analyze a single instruction and decide how to handle it.
     */
    private InstructionDecision analyzeInstruction(AbstractInsnNode insn, 
            MethodNode method, String className) {
        
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                return analyzeMethodInsn((MethodInsnNode) insn);
            }
            
            case AbstractInsnNode.FIELD_INSN -> {
                return analyzeFieldInsn((FieldInsnNode) insn);
            }
            
            case AbstractInsnNode.TYPE_INSN -> {
                return analyzeTypeInsn((TypeInsnNode) insn);
            }
            
            case AbstractInsnNode.LDC_INSN -> {
                return analyzeLdcInsn((LdcInsnNode) insn);
            }
            
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN -> {
                return analyzeInvokeDynamic((InvokeDynamicInsnNode) insn);
            }
            
            default -> {
                return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
            }
        }
    }
    
    private InstructionDecision analyzeMethodInsn(MethodInsnNode insn) {
        // Check if this is a reflection call
        if (isReflectionApi(insn)) {
            return new InstructionDecision(
                TransformAction.JIT_WRAP,
                "Reflection API: " + insn.owner + "." + insn.name
            );
        }
        
        // Check if method needs redirect
        var key = new RetroModTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        if (transformer.getMethodRedirects().containsKey(key)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }
        
        // Check if class needs redirect
        if (transformer.getClassRedirects().containsKey(insn.owner)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }
        
        // Check for obfuscated target
        if (isObfuscatedTarget(insn.owner, insn.name)) {
            return new InstructionDecision(
                TransformAction.JIT_WRAP,
                "Obfuscated target: " + insn.owner + "." + insn.name
            );
        }
        
        return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
    }
    
    private InstructionDecision analyzeFieldInsn(FieldInsnNode insn) {
        // Check actual field redirects first
        var fieldKey = new RetroModTransformer.FieldKey(insn.owner, insn.name);
        if (transformer.getFieldRedirects().containsKey(fieldKey)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }

        if (transformer.getClassRedirects().containsKey(insn.owner)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }

        if (isObfuscatedTarget(insn.owner, insn.name)) {
            return new InstructionDecision(
                TransformAction.JIT_WRAP,
                "Obfuscated field: " + insn.owner + "." + insn.name
            );
        }

        return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
    }
    
    private InstructionDecision analyzeTypeInsn(TypeInsnNode insn) {
        if (transformer.getClassRedirects().containsKey(insn.desc)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }
        
        return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
    }
    
    private InstructionDecision analyzeLdcInsn(LdcInsnNode insn) {
        if (insn.cst instanceof Type type) {
            if (type.getSort() == Type.OBJECT) {
                if (transformer.getClassRedirects().containsKey(type.getInternalName())) {
                    return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
                }
            }
        }
        
        return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
    }
    
    private InstructionDecision analyzeInvokeDynamic(InvokeDynamicInsnNode insn) {
        Handle bsm = insn.bsm;
        
        // Known safe bootstrap methods
        if (isKnownBootstrap(bsm)) {
            // Check if any arguments need transformation
            boolean needsTransform = false;
            for (Object arg : insn.bsmArgs) {
                if (arg instanceof Type type && type.getSort() == Type.OBJECT) {
                    if (transformer.getClassRedirects().containsKey(type.getInternalName())) {
                        needsTransform = true;
                        break;
                    }
                }
                if (arg instanceof Handle handle) {
                    if (transformer.getClassRedirects().containsKey(handle.getOwner())) {
                        needsTransform = true;
                        break;
                    }
                    // Also check if the Handle's method needs a redirect
                    var handleKey = new RetroModTransformer.MethodKey(handle.getOwner(), handle.getName(), handle.getDesc());
                    if (transformer.getMethodRedirects().containsKey(handleKey)) {
                        needsTransform = true;
                        break;
                    }
                }
            }
            
            if (needsTransform) {
                return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
            }
            return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
        }
        
        // Unknown bootstrap - needs JIT
        return new InstructionDecision(
            TransformAction.JIT_WRAP,
            "Unknown invokedynamic bootstrap: " + bsm.getOwner() + "." + bsm.getName()
        );
    }
    
    private boolean isReflectionApi(MethodInsnNode insn) {
        String owner = insn.owner;
        String name = insn.name;
        
        // Class reflection methods
        if (owner.equals("java/lang/Class")) {
            return name.equals("getMethod") || name.equals("getDeclaredMethod") ||
                   name.equals("getField") || name.equals("getDeclaredField") ||
                   name.equals("getConstructor") || name.equals("getDeclaredConstructor") ||
                   name.equals("forName") || name.equals("newInstance");
        }
        
        // Method invocation
        if (owner.equals("java/lang/reflect/Method")) {
            return name.equals("invoke");
        }
        
        // Field access
        if (owner.equals("java/lang/reflect/Field")) {
            return name.equals("get") || name.equals("set") ||
                   name.equals("getInt") || name.equals("setInt") ||
                   name.equals("getLong") || name.equals("setLong") ||
                   name.equals("getBoolean") || name.equals("setBoolean");
        }
        
        // Constructor invocation
        if (owner.equals("java/lang/reflect/Constructor")) {
            return name.equals("newInstance");
        }
        
        // Method handles
        if (owner.startsWith("java/lang/invoke/")) {
            return true;
        }
        
        return false;
    }
    
    private boolean isObfuscatedTarget(String owner, String name) {
        // Very short names (except common ones)
        if (name.length() <= 2 && !isCommonShortName(name)) {
            return true;
        }
        
        // Default package classes
        if (!owner.contains("/")) {
            return true;
        }
        
        // Proguard-style names
        if (name.matches("[a-z]{1,3}") || owner.matches(".*\\/[a-z]{1,2}$")) {
            return true;
        }
        
        return false;
    }
    
    private boolean isCommonShortName(String name) {
        return Set.of(
            "of", "to", "as", "is", "in", "at", "on", "up", "id", "op",
            "get", "set", "add", "run", "put", "pop", "max", "min", "sum"
        ).contains(name);
    }
    
    private boolean isKnownBootstrap(Handle bsm) {
        String owner = bsm.getOwner();
        return owner.equals("java/lang/invoke/LambdaMetafactory") ||
               owner.equals("java/lang/invoke/StringConcatFactory") ||
               owner.equals("java/lang/runtime/ObjectMethods") ||
               owner.equals("java/lang/runtime/SwitchBootstraps");
    }
    
    /**
     * Transform an instruction at AOT time.
     */
    private AbstractInsnNode transformInstructionAot(AbstractInsnNode insn, 
            InstructionDecision decision) {
        
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;

                // Apply redirects
                var key = new RetroModTransformer.MethodKey(
                    methodInsn.owner, methodInsn.name, methodInsn.desc
                );
                var target = transformer.getMethodRedirects().get(key);

                if (target != null) {
                    // Determine correct opcode (instance→static redirect)
                    int newOpcode = methodInsn.getOpcode();
                    boolean newItf = methodInsn.itf;
                    if ((newOpcode == Opcodes.INVOKEVIRTUAL || newOpcode == Opcodes.INVOKEINTERFACE)
                            && target.isInstanceToStatic(methodInsn.desc)) {
                        newOpcode = Opcodes.INVOKESTATIC;
                        newItf = false;
                    }
                    return new MethodInsnNode(newOpcode, target.owner(), target.name(), target.desc(), newItf);
                } else {
                    String newOwner = transformer.getClassRedirects().getOrDefault(methodInsn.owner, methodInsn.owner);
                    return new MethodInsnNode(methodInsn.getOpcode(), newOwner, methodInsn.name, methodInsn.desc, methodInsn.itf);
                }
            }

            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;

                // Check field redirects first (more specific)
                var fieldKey = new RetroModTransformer.FieldKey(fieldInsn.owner, fieldInsn.name);
                var fieldTarget = transformer.getFieldRedirects().get(fieldKey);
                if (fieldTarget != null) {
                    // Check if this is a field-to-method redirect
                    if (fieldTarget.newDesc() != null && fieldTarget.newDesc().startsWith("(")) {
                        // Convert field access to method call
                        return new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            fieldTarget.owner(),
                            fieldTarget.name(),
                            fieldTarget.newDesc(),
                            false
                        );
                    }
                    String desc = (fieldTarget.newDesc() != null) ? fieldTarget.newDesc() : fieldInsn.desc;
                    return new FieldInsnNode(fieldInsn.getOpcode(), fieldTarget.owner(), fieldTarget.name(), desc);
                }

                // Fall back to class redirect on owner
                String newOwner = transformer.getClassRedirects()
                    .getOrDefault(fieldInsn.owner, fieldInsn.owner);
                return new FieldInsnNode(fieldInsn.getOpcode(), newOwner, fieldInsn.name, fieldInsn.desc);
            }
            
            case AbstractInsnNode.TYPE_INSN -> {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                String newType = transformer.getClassRedirects()
                    .getOrDefault(typeInsn.desc, typeInsn.desc);
                
                return new TypeInsnNode(typeInsn.getOpcode(), newType);
            }
            
            case AbstractInsnNode.LDC_INSN -> {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                
                if (ldcInsn.cst instanceof Type type && type.getSort() == Type.OBJECT) {
                    String newType = transformer.getClassRedirects()
                        .get(type.getInternalName());
                    
                    if (newType != null) {
                        return new LdcInsnNode(Type.getObjectType(newType));
                    }
                }
                
                return ldcInsn.clone(null);
            }
            
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN -> {
                InvokeDynamicInsnNode dynInsn = (InvokeDynamicInsnNode) insn;
                return transformInvokeDynamic(dynInsn);
            }
            
            default -> {
                return insn.clone(null);
            }
        }
    }
    
    /**
     * Transform an invokedynamic instruction.
     */
    private InvokeDynamicInsnNode transformInvokeDynamic(InvokeDynamicInsnNode insn) {
        // Transform bootstrap method arguments
        Object[] newArgs = new Object[insn.bsmArgs.length];
        boolean changed = false;
        
        for (int i = 0; i < insn.bsmArgs.length; i++) {
            Object arg = insn.bsmArgs[i];
            
            if (arg instanceof Type type && type.getSort() == Type.OBJECT) {
                String newType = transformer.getClassRedirects().get(type.getInternalName());
                if (newType != null) {
                    newArgs[i] = Type.getObjectType(newType);
                    changed = true;
                    continue;
                }
            }
            
            if (arg instanceof Type type && type.getSort() == Type.METHOD) {
                String desc = type.getDescriptor();
                String newDesc = redirectDescriptor(desc);
                if (!desc.equals(newDesc)) {
                    newArgs[i] = Type.getMethodType(newDesc);
                    changed = true;
                    continue;
                }
            }
            
            if (arg instanceof Handle handle) {
                // Check for method redirect on this Handle
                var handleKey = new RetroModTransformer.MethodKey(handle.getOwner(), handle.getName(), handle.getDesc());
                var handleTarget = transformer.getMethodRedirects().get(handleKey);
                if (handleTarget != null) {
                    // Determine Handle tag: if instance→static, change to H_INVOKESTATIC
                    int newTag = handle.getTag();
                    if ((newTag == Opcodes.H_INVOKEVIRTUAL || newTag == Opcodes.H_INVOKEINTERFACE)
                            && handleTarget.isInstanceToStatic(handle.getDesc())) {
                        newTag = Opcodes.H_INVOKESTATIC;
                    }
                    newArgs[i] = new Handle(
                        newTag,
                        handleTarget.owner(),
                        handleTarget.name(),
                        handleTarget.desc(),
                        newTag == Opcodes.H_INVOKEINTERFACE
                    );
                    changed = true;
                    continue;
                }

                // Fall back to class redirect on owner
                String newOwner = transformer.getClassRedirects().get(handle.getOwner());
                if (newOwner != null) {
                    newArgs[i] = new Handle(
                        handle.getTag(),
                        newOwner,
                        handle.getName(),
                        handle.getDesc(),
                        handle.isInterface()
                    );
                    changed = true;
                    continue;
                }
            }
            
            newArgs[i] = arg;
        }
        
        if (changed) {
            return new InvokeDynamicInsnNode(
                insn.name,
                redirectDescriptor(insn.desc),
                insn.bsm,
                newArgs
            );
        }
        
        return (InvokeDynamicInsnNode) insn.clone(null);
    }
    
    /**
     * Wrap an instruction in a JIT bridge for runtime transformation.
     */
    private InsnList wrapInstructionForJit(AbstractInsnNode insn, 
            InstructionDecision decision, MethodNode method) {
        
        InsnList result = new InsnList();
        
        // For reflection calls, we inject a call to ReflectionRemapper
        if (insn instanceof MethodInsnNode methodInsn && isReflectionApi(methodInsn)) {
            result.add(wrapReflectionCall(methodInsn));
            return result;
        }
        
        // For other JIT cases, we add a marker and keep the original instruction
        // The JIT runtime will transform it when the class loads
        
        // Add marker annotation data (stored in local variable table)
        // This is detected by JitRuntime
        result.add(new LdcInsnNode("__RETROMOD_JIT__" + decision.reason));
        result.add(new InsnNode(Opcodes.POP));
        
        // Add the original instruction
        result.add(insn.clone(null));
        
        return result;
    }
    
    /**
     * Wrap a reflection API call to use ReflectionRemapper.
     */
    private InsnList wrapReflectionCall(MethodInsnNode insn) {
        InsnList result = new InsnList();
        
        String owner = insn.owner;
        String name = insn.name;
        
        // Replace Class.getMethod(name, types) with ReflectionRemapper.getMethod(class, name, types)
        if (owner.equals("java/lang/Class") && name.equals("getMethod")) {
            // Stack: [Class, String, Class[]]
            // We need to call ReflectionRemapper.getMethod(Class, String, Class[])
            result.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/retromod/core/ReflectionRemapper",
                "getMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                false
            ));
            return result;
        }
        
        if (owner.equals("java/lang/Class") && name.equals("getDeclaredMethod")) {
            result.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/retromod/core/ReflectionRemapper",
                "getDeclaredMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                false
            ));
            return result;
        }
        
        if (owner.equals("java/lang/Class") && name.equals("getField")) {
            result.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/retromod/core/ReflectionRemapper",
                "getField",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;",
                false
            ));
            return result;
        }
        
        if (owner.equals("java/lang/Class") && name.equals("forName")) {
            if (insn.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                result.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/retromod/core/ReflectionRemapper",
                    "forName",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    false
                ));
                return result;
            }
        }
        
        // For other reflection calls, keep original (they'll work at runtime)
        result.add(insn.clone(null));
        return result;
    }
    
    /** Pattern matching class type references in descriptors (e.g., Lcom/example/Foo;) */
    private static final java.util.regex.Pattern CLASS_TYPE_IN_DESC =
        java.util.regex.Pattern.compile("L([^;]+);");

    private String redirectDescriptor(String descriptor) {
        Map<String, String> redirects = transformer.getClassRedirects();
        if (redirects.isEmpty()) return descriptor;

        // Single-pass replacement using regex (avoids N×M String.replace calls)
        java.util.regex.Matcher m = CLASS_TYPE_IN_DESC.matcher(descriptor);
        StringBuilder sb = null;
        while (m.find()) {
            String className = m.group(1);
            String redirect = redirects.get(className);
            if (redirect != null) {
                if (sb == null) sb = new StringBuilder(descriptor.length());
                m.appendReplacement(sb, "L" + redirect + ";");
            }
        }
        if (sb == null) return descriptor; // No changes
        m.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * Get transformation statistics.
     */
    public TransformStats getStats() {
        return new TransformStats(instructionsAot, instructionsJit, instructionsUnchanged);
    }
    
    public void resetStats() {
        instructionsAot = 0;
        instructionsJit = 0;
        instructionsUnchanged = 0;
    }
    
    // --- Inner types ---
    
    public enum TransformAction {
        AOT_TRANSFORM,   // Transform at compile time
        JIT_WRAP,        // Wrap for runtime transformation
        KEEP_UNCHANGED   // No transformation needed
    }
    
    public record InstructionDecision(TransformAction action, String reason) {}
    
    public record TransformStats(int aot, int jit, int unchanged) {}
    
    public record JitRegionInfo(int instructionIndex, String reason) {}
    
    public static class TransformResult {
        public boolean modified = false;
        public int aotCount = 0;
        public int jitCount = 0;
        public int unchangedCount = 0;
        public List<JitRegionInfo> jitRegions = new ArrayList<>();
    }
}
