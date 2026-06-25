/*
 * Retromod: Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the constructor-<b>reference</b> rewrite ({@code X::new} -> static factory).
 *
 * <p>The direct {@code new X(...)} form is handled in {@code visitMethodInsn}; this covers the
 * {@code invokedynamic} reference form, where {@code X::new} compiles to a method-handle of kind
 * {@code H_NEWINVOKESPECIAL X.<init>(args)V}. The transformer must swap that for
 * {@code H_INVOKESTATIC factory(args)X} so the lambda links against the surviving factory.
 * Real-world case: Resourceful Lib's {@code ExtraByteCodecs} captures {@code ChunkPos::new},
 * the blocker for Chipped/Handcrafted on 26.x (where the {@code ChunkPos(long)} ctor is gone).</p>
 */
class CtorReferenceRedirectTest {

    private static final String LMF_DESC =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
            + "Ljava/lang/invoke/CallSite;";
    private static final Handle METAFACTORY = new Handle(
            Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", LMF_DESC, false);

    /** A class whose {@code make()} returns a lambda built from a constructor reference. */
    private static byte[] withCtorRef(String internalName, String ctorOwner, String ctorDesc,
                                      String samDesc, String iface, String instantiatedDesc) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "make", "()L" + iface + ";", null, null);
        mv.visitCode();
        mv.visitInvokeDynamicInsn("apply", "()L" + iface + ";", METAFACTORY,
                Type.getMethodType(samDesc),
                new Handle(Opcodes.H_NEWINVOKESPECIAL, ctorOwner, "<init>", ctorDesc, false),
                Type.getMethodType(instantiatedDesc));
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Pull the implementation method handle (the lambda body target) back out of the class. */
    private static Handle implHandle(byte[] clazz) {
        Handle[] out = new Handle[1];
        new ClassReader(clazz).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitInvokeDynamicInsn(String n2, String d2, Handle bsm, Object... args) {
                        for (Object o : args) if (o instanceof Handle h) out[0] = h; // only the impl handle is in args
                    }
                };
            }
        }, 0);
        return out[0];
    }

    @Test
    void constructorReferenceRewrittenToStaticFactory() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        // ChunkPos(long) removed in 26.x -> static factory ChunkPos.unpack(long)
        t.registerConstructorRedirect(
                "net/minecraft/world/level/ChunkPos", "(J)V",
                "net/minecraft/world/level/ChunkPos", "unpack",
                "(J)Lnet/minecraft/world/level/ChunkPos;");

        byte[] fixture = withCtorRef("test/CtorRef", "net/minecraft/world/level/ChunkPos", "(J)V",
                "(J)Ljava/lang/Object;", "java/util/function/LongFunction",
                "(J)Lnet/minecraft/world/level/ChunkPos;");
        assertEquals(Opcodes.H_NEWINVOKESPECIAL, implHandle(fixture).getTag(), "fixture starts as a ctor ref");

        Handle after = implHandle(t.transformClass(fixture, "test/CtorRef"));
        assertNotNull(after, "impl handle should survive transform");
        assertEquals(Opcodes.H_INVOKESTATIC, after.getTag(), "ctor ref must become a static-factory ref");
        assertEquals("net/minecraft/world/level/ChunkPos", after.getOwner());
        assertEquals("unpack", after.getName());
        assertEquals("(J)Lnet/minecraft/world/level/ChunkPos;", after.getDesc());

        t.clearRedirectsForTesting();
    }

    @Test
    void unredirectedCtorReferenceLeftUntouched() {
        RetromodTransformer t = RetromodTransformer.getInstance();
        t.clearRedirectsForTesting();
        t.registerConstructorRedirect(
                "net/minecraft/world/level/ChunkPos", "(J)V",
                "net/minecraft/world/level/ChunkPos", "unpack",
                "(J)Lnet/minecraft/world/level/ChunkPos;");

        // StringBuilder::new has no redirect, so it must stay a ctor reference.
        byte[] fixture = withCtorRef("test/Other", "java/lang/StringBuilder", "()V",
                "()Ljava/lang/Object;", "java/util/function/Supplier", "()Ljava/lang/StringBuilder;");
        Handle after = implHandle(t.transformClass(fixture, "test/Other"));
        assertEquals(Opcodes.H_NEWINVOKESPECIAL, after.getTag(), "unredirected ctor ref must be untouched");
        assertEquals("java/lang/StringBuilder", after.getOwner());

        t.clearRedirectsForTesting();
    }
}
