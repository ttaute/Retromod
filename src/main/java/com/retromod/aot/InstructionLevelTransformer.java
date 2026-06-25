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
 * Per-instruction partial AOT: statically transformable instructions are rewritten in place,
 * only the ones that need runtime handling (reflection, obfuscated targets, unknown invokedynamic)
 * get wrapped for JIT, and everything else is left alone.
 */
public class InstructionLevelTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-insn");

    private final RetromodTransformer transformer;

    private int instructionsAot = 0;
    private int instructionsJit = 0;
    private int instructionsUnchanged = 0;
    
    public InstructionLevelTransformer(RetromodTransformer transformer) {
        this.transformer = transformer;
    }
    
    /** Rewrites a method instruction by instruction. The result flags whether anything changed. */
    public TransformResult transformMethod(MethodNode method, String className) {
        TransformResult result = new TransformResult();

        if (method.instructions == null || method.instructions.size() == 0) {
            return result;
        }

        InsnList newInstructions = new InsnList();

        ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
        while (iterator.hasNext()) {
            AbstractInsnNode insn = iterator.next();

            InstructionDecision decision = analyzeInstruction(insn, method, className);

            switch (decision.action) {
                case AOT_TRANSFORM -> {
                    AbstractInsnNode transformed = transformInstructionAot(insn, decision);
                    newInstructions.add(transformed);
                    instructionsAot++;
                    result.aotCount++;
                    result.modified = true;
                }

                case JIT_WRAP -> {
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
                    newInstructions.add(insn.clone(null));
                    instructionsUnchanged++;
                    result.unchangedCount++;
                }
            }
        }

        if (result.modified) {
            method.instructions.clear();
            method.instructions.add(newInstructions);
        }

        return result;
    }

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
        if (isReflectionApi(insn)) {
            return new InstructionDecision(
                TransformAction.JIT_WRAP,
                "Reflection API: " + insn.owner + "." + insn.name
            );
        }

        var key = new RetromodTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        if (transformer.getMethodRedirects().containsKey(key)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }

        if (transformer.getClassRedirects().containsKey(insn.owner)) {
            return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
        }

        if (isObfuscatedTarget(insn.owner, insn.name)) {
            return new InstructionDecision(
                TransformAction.JIT_WRAP,
                "Obfuscated target: " + insn.owner + "." + insn.name
            );
        }
        
        return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
    }
    
    private InstructionDecision analyzeFieldInsn(FieldInsnNode insn) {
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

        if (isKnownBootstrap(bsm)) {
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
                }
            }
            
            if (needsTransform) {
                return new InstructionDecision(TransformAction.AOT_TRANSFORM, null);
            }
            return new InstructionDecision(TransformAction.KEEP_UNCHANGED, null);
        }

        return new InstructionDecision(
            TransformAction.JIT_WRAP,
            "Unknown invokedynamic bootstrap: " + bsm.getOwner() + "." + bsm.getName()
        );
    }

    private boolean isReflectionApi(MethodInsnNode insn) {
        String owner = insn.owner;
        String name = insn.name;

        if (owner.equals("java/lang/Class")) {
            return name.equals("getMethod") || name.equals("getDeclaredMethod") ||
                   name.equals("getField") || name.equals("getDeclaredField") ||
                   name.equals("getConstructor") || name.equals("getDeclaredConstructor") ||
                   name.equals("forName") || name.equals("newInstance");
        }

        if (owner.equals("java/lang/reflect/Method")) {
            return name.equals("invoke");
        }

        if (owner.equals("java/lang/reflect/Field")) {
            return name.equals("get") || name.equals("set") ||
                   name.equals("getInt") || name.equals("setInt") ||
                   name.equals("getLong") || name.equals("setLong") ||
                   name.equals("getBoolean") || name.equals("setBoolean");
        }

        if (owner.equals("java/lang/reflect/Constructor")) {
            return name.equals("newInstance");
        }

        if (owner.startsWith("java/lang/invoke/")) {
            return true;
        }

        return false;
    }

    private boolean isObfuscatedTarget(String owner, String name) {
        if (name.length() <= 2 && !isCommonShortName(name)) {
            return true;
        }

        if (!owner.contains("/")) {
            return true;
        }

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
    
    private AbstractInsnNode transformInstructionAot(AbstractInsnNode insn,
            InstructionDecision decision) {

        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                MethodInsnNode transformed = new MethodInsnNode(
                    methodInsn.getOpcode(),
                    methodInsn.owner,
                    methodInsn.name,
                    methodInsn.desc,
                    methodInsn.itf
                );

                var key = new RetromodTransformer.MethodKey(
                    transformed.owner, transformed.name, transformed.desc
                );
                var target = transformer.getMethodRedirects().get(key);
                
                if (target != null) {
                    transformed.owner = target.owner();
                    transformed.name = target.name();
                    transformed.desc = target.desc();
                } else {
                    String newOwner = transformer.getClassRedirects().get(transformed.owner);
                    if (newOwner != null) {
                        transformed.owner = newOwner;
                    }
                }
                
                return transformed;
            }
            
            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                String newOwner = transformer.getClassRedirects()
                    .getOrDefault(fieldInsn.owner, fieldInsn.owner);
                
                return new FieldInsnNode(
                    fieldInsn.getOpcode(),
                    newOwner,
                    fieldInsn.name,
                    fieldInsn.desc
                );
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
    
    private InvokeDynamicInsnNode transformInvokeDynamic(InvokeDynamicInsnNode insn) {
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
    
    private InsnList wrapInstructionForJit(AbstractInsnNode insn,
            InstructionDecision decision, MethodNode method) {

        InsnList result = new InsnList();

        if (insn instanceof MethodInsnNode methodInsn && isReflectionApi(methodInsn)) {
            result.add(wrapReflectionCall(methodInsn));
            return result;
        }

        // Tag the original instruction with a marker JitRuntime picks up when the class loads.
        result.add(new LdcInsnNode("__RETROMOD_JIT__" + decision.reason));
        result.add(new InsnNode(Opcodes.POP));
        result.add(insn.clone(null));

        return result;
    }

    /** Redirects a reflection API call through ReflectionRemapper. */
    private InsnList wrapReflectionCall(MethodInsnNode insn) {
        InsnList result = new InsnList();

        String owner = insn.owner;
        String name = insn.name;

        if (owner.equals("java/lang/Class") && name.equals("getMethod")) {
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
        
        result.add(insn.clone(null));
        return result;
    }
    
    private String redirectDescriptor(String descriptor) {
        String result = descriptor;
        for (var entry : transformer.getClassRedirects().entrySet()) {
            result = result.replace(
                "L" + entry.getKey() + ";",
                "L" + entry.getValue() + ";"
            );
        }
        return result;
    }
    
    public TransformStats getStats() {
        return new TransformStats(instructionsAot, instructionsJit, instructionsUnchanged);
    }

    public void resetStats() {
        instructionsAot = 0;
        instructionsJit = 0;
        instructionsUnchanged = 0;
    }

    public enum TransformAction {
        AOT_TRANSFORM,
        JIT_WRAP,
        KEEP_UNCHANGED
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
