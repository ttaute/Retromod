/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Scans transformed mod bytecode and reports any reference to an MC API that
 * doesn't exist in the target MC version.
 *
 * <p>Runs after the iterative transform loop and reflection remapping, so what
 * it reports is the references the automatic fixes couldn't handle: the gaps
 * that need manual work (new shims, new polyfills, or a decision that this mod
 * can't be supported).
 *
 * <p>Only {@code net/minecraft/**} and {@code com/mojang/**} references are
 * considered. Loader-API references are checked against {@link LoaderApiRenames}
 * (a reference matching a known-removed loader class is flagged). References to
 * the mod's own classes are skipped via the {@code modOwnClasses} set passed to
 * {@link #verify}, otherwise they would all show up as missing.
 *
 * <p>Scanned: class refs in {@code NEW}/{@code CHECKCAST}/{@code INSTANCEOF}/
 * {@code ANEWARRAY}/{@code MULTIANEWARRAY}, method and field descriptors, member
 * refs in the {@code INVOKE*}/{@code GETFIELD}/{@code PUTFIELD}/{@code GETSTATIC}/
 * {@code PUTSTATIC} instructions, and a class's superclass and interfaces.
 * Generic signatures, annotations, and invokedynamic bootstrap handles are not
 * scanned: higher false-positive rate, lower diagnostic value.
 */
public final class ReferenceVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Verifier");

    private final McSymbolIndex index;
    private final LoaderApiRenames loaderRenames;
    private final int maxSuggestions;

    /**
     * @param index           the MC symbol index to check references against
     * @param loaderRenames   curated loader-API rename table for spotting
     *                        references to known-removed loader classes
     * @param maxSuggestions  cap on fuzzy suggestions per unresolved ref (0 disables)
     */
    public ReferenceVerifier(McSymbolIndex index, LoaderApiRenames loaderRenames, int maxSuggestions) {
        this.index = index;
        this.loaderRenames = loaderRenames;
        this.maxSuggestions = Math.max(0, maxSuggestions);
    }

    /** Convenience constructor with default of 3 suggestions per ref. */
    public ReferenceVerifier(McSymbolIndex index, LoaderApiRenames loaderRenames) {
        this(index, loaderRenames, 3);
    }

    /**
     * Scan one transformed class and append any unresolved references to the
     * given report. Safe to call many times against the same report; it
     * accumulates.
     *
     * @param classBytes     transformed class bytecode (post-iterative-loop)
     * @param modOwnClasses  JVM internal names of classes defined by the mod
     *                       itself; references to these are skipped since they
     *                       obviously won't be in the MC index
     * @param report         report to append findings to
     */
    public void verify(byte[] classBytes, Set<String> modOwnClasses, VerificationReport report) {
        if (classBytes == null || classBytes.length == 0 || report == null) return;
        if (!index.isAvailable()) {
            // No index means we can't check anything; log it so a skip doesn't read as "all clear".
            LOGGER.debug("Skipping verification - McSymbolIndex not available");
            return;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(new VerifyingClassVisitor(report,
                    modOwnClasses == null ? Set.of() : modOwnClasses),
                    ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            // Malformed class or verifier bug: log and move on, don't fail the transform.
            LOGGER.debug("Verification failed for a class ({}); skipping", e.getMessage());
        }
    }

    /** True if this internal name is an MC or Mojang shared-library reference worth verifying. */
    private static boolean isMcRef(String internalName) {
        if (internalName == null) return false;
        return internalName.startsWith("net/minecraft/")
            || internalName.startsWith("com/mojang/");
    }

    /** True if this is a loader-API reference, checked against the rename table, not the MC index. */
    private static boolean isLoaderRef(String internalName) {
        if (internalName == null) return false;
        return internalName.startsWith("net/fabricmc/")
            || internalName.startsWith("net/neoforged/")
            || internalName.startsWith("net/minecraftforge/");
    }

    /** Component class name of an object/array type, or null for primitives (nothing to verify). */
    private static String unwrapObjectType(Type t) {
        if (t == null) return null;
        int sort = t.getSort();
        if (sort == Type.OBJECT) return t.getInternalName();
        if (sort == Type.ARRAY) return unwrapObjectType(t.getElementType());
        return null;
    }

    private final class VerifyingClassVisitor extends ClassVisitor {
        private final VerificationReport report;
        private final Set<String> modOwnClasses;
        /** Class currently being visited, used to annotate source location. */
        private String currentClass = "<unknown>";

        VerifyingClassVisitor(VerificationReport report, Set<String> modOwnClasses) {
            super(Opcodes.ASM9);
            this.report = report;
            this.modOwnClasses = modOwnClasses;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.currentClass = name;
            // Extending a removed MC type or implementing a removed interface is the common crash case.
            checkClassRef(superName, name, "<class>", -1);
            if (interfaces != null) {
                for (String iface : interfaces) {
                    checkClassRef(iface, name, "<class>", -1);
                }
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                        String signature, Object value) {
            // A field whose type was removed (e.g. Lnet/minecraft/world/World;) is an unresolved class.
            Type fieldType = Type.getType(descriptor);
            checkClassRef(unwrapObjectType(fieldType), currentClass, "<field:" + name + ">", -1);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            Type methodType = Type.getMethodType(descriptor);
            for (Type paramType : methodType.getArgumentTypes()) {
                checkClassRef(unwrapObjectType(paramType), currentClass,
                        "<signature:" + name + ">", -1);
            }
            checkClassRef(unwrapObjectType(methodType.getReturnType()), currentClass,
                    "<signature:" + name + ">", -1);
            // Declared exceptions can be MC-package Throwables too.
            if (exceptions != null) {
                for (String ex : exceptions) {
                    checkClassRef(ex, currentClass, "<signature:" + name + ">", -1);
                }
            }
            return new VerifyingMethodVisitor(name);
        }

        private final class VerifyingMethodVisitor extends MethodVisitor {
            private final String methodName;
            /** Last line number seen, attached to any refs that follow. */
            private int currentLine = -1;

            VerifyingMethodVisitor(String methodName) {
                super(Opcodes.ASM9);
                this.methodName = methodName;
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                this.currentLine = line;
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                // type is either an internal class name or an array descriptor; normalize to a class.
                String cls = type;
                if (cls != null && cls.startsWith("[")) {
                    Type arrayType = Type.getType(cls);
                    cls = unwrapObjectType(arrayType);
                }
                checkClassRef(cls, currentClass, methodName, currentLine);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                         String descriptor, boolean isInterface) {
                checkMethodRef(owner, name, descriptor, currentClass, methodName, currentLine);
                // A call to an existing method whose parameter type was removed is still broken.
                checkDescriptorClassRefs(descriptor, methodName);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                checkFieldRef(owner, name, descriptor, currentClass, methodName, currentLine);
                Type fieldType = Type.getType(descriptor);
                checkClassRef(unwrapObjectType(fieldType), currentClass, methodName, currentLine);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                                Object... bootstrapMethodArguments) {
                // Check the call-site type only; the bootstrap handle is usually the
                // lambda metafactory (MC-unrelated) and recursive to analyse.
                checkDescriptorClassRefs(descriptor, methodName);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                Type arrayType = Type.getType(descriptor);
                checkClassRef(unwrapObjectType(arrayType), currentClass, methodName, currentLine);
            }

            private void checkDescriptorClassRefs(String descriptor, String methodName) {
                Type methodType = Type.getMethodType(descriptor);
                for (Type arg : methodType.getArgumentTypes()) {
                    checkClassRef(unwrapObjectType(arg), currentClass, methodName, currentLine);
                }
                checkClassRef(unwrapObjectType(methodType.getReturnType()),
                        currentClass, methodName, currentLine);
            }
        }

        // reference checking shared by all visit* methods

        private void checkClassRef(String internalName, String srcClass,
                                    String srcMethod, int line) {
            if (internalName == null) return;
            if (modOwnClasses.contains(internalName)) return;

            if (isMcRef(internalName)) {
                if (!index.hasClass(internalName)) {
                    report.add(new UnresolvedReference(
                            UnresolvedReference.Kind.MISSING_CLASS,
                            internalName, "", "",
                            srcClass, srcMethod, line,
                            index.suggestClassAlternatives(internalName, maxSuggestions)));
                }
            } else if (isLoaderRef(internalName)) {
                // Flag only if the rename table marks it removed or renamed. A class in
                // both buckets (data-entry mistake) yields one entry: removed wins.
                if (loaderRenames.isRemoved(internalName)) {
                    report.add(new UnresolvedReference(
                            UnresolvedReference.Kind.MISSING_CLASS,
                            internalName, "", "",
                            srcClass, srcMethod, line,
                            java.util.List.of()));
                } else {
                    String newName = loaderRenames.getClassRename(internalName);
                    if (newName != null) {
                        // The old name is gone but the replacement is known; flag it so the
                        // author sees the reference still needs rewriting (classRedirects
                        // should have handled it upstream).
                        report.add(new UnresolvedReference(
                                UnresolvedReference.Kind.MISSING_CLASS,
                                internalName, "", "",
                                srcClass, srcMethod, line,
                                java.util.List.of(newName)));
                    }
                }
            }
            // unrecognized namespace (mod's own deps, JDK, etc.): skip
        }

        private void checkMethodRef(String owner, String name, String descriptor,
                                     String srcClass, String srcMethod, int line) {
            if (owner == null) return;
            if (modOwnClasses.contains(owner)) return;
            // INVOKEVIRTUAL on an array (e.g. .clone()) targets a synthetic array type, not MC.
            if (owner.startsWith("[")) return;

            if (!isMcRef(owner)) return;

            // A missing owner is already covered by the class-level report; skip the
            // member-level entry to avoid pages of redundant findings.
            if (!index.hasClass(owner)) {
                return;
            }

            if (!index.hasMethod(owner, name, descriptor)) {
                // If the name survives under a different descriptor the signature changed
                // (BAD_SIGNATURE), distinct from a rename/removal (MISSING_METHOD). This is the
                // pitfall-#17 case where names survive but descriptors change (1.17 model rebuild),
                // so a name-keyed shim won't fire.
                UnresolvedReference.Kind kind = index.hasMethodName(owner, name)
                        ? UnresolvedReference.Kind.BAD_SIGNATURE
                        : UnresolvedReference.Kind.MISSING_METHOD;
                report.add(new UnresolvedReference(
                        kind,
                        owner, name, descriptor,
                        srcClass, srcMethod, line,
                        toStringSuggestions(index.suggestMethodAlternatives(
                                owner, name, descriptor, maxSuggestions))));
            }
        }

        private void checkFieldRef(String owner, String name, String descriptor,
                                    String srcClass, String srcMethod, int line) {
            if (owner == null || !isMcRef(owner)) return;
            if (modOwnClasses.contains(owner)) return;
            if (!index.hasClass(owner)) return; // covered by class-level report

            if (!index.hasField(owner, name, descriptor)) {
                // A same-named field whose type changed is BAD_SIGNATURE (e.g. class_1269.field_5811
                // after the 1.21.2 sealed-interface rebuild), distinct from removal (MISSING_FIELD).
                UnresolvedReference.Kind kind = index.hasFieldName(owner, name)
                        ? UnresolvedReference.Kind.BAD_SIGNATURE
                        : UnresolvedReference.Kind.MISSING_FIELD;
                report.add(new UnresolvedReference(
                        kind,
                        owner, name, descriptor,
                        srcClass, srcMethod, line,
                        java.util.List.of()));
                // No field-alternative suggestions: the fuzzy resolver's field matching
                // targets redirects, not user-facing suggestions.
            }
        }

        private java.util.List<String> toStringSuggestions(
                java.util.List<McSymbolIndex.MemberSignature> sigs) {
            if (sigs == null || sigs.isEmpty()) return java.util.List.of();
            java.util.List<String> out = new java.util.ArrayList<>(sigs.size());
            for (McSymbolIndex.MemberSignature s : sigs) out.add(s.prettyPrint());
            return out;
        }
    }
}
