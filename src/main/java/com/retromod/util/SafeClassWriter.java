/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/**
 * {@link ClassWriter} that safely handles {@link #getCommonSuperClass} in
 * modded Minecraft environments.
 *
 * <h3>The problem</h3>
 * <p>ASM's default {@code getCommonSuperClass} implementation uses
 * {@link Class#forName(String)} to resolve the two type names against the
 * current classloader. That works fine for standard JDK classes but fails
 * in two common Retromod situations:
 *
 * <ul>
 *   <li><b>AOT compilation off the game thread.</b> When Retromod compiles a
 *       mod's bytecode outside of Minecraft (CLI mode, or pre-launch on a
 *       background thread), MC's classloader isn't reachable. Any reference
 *       to a {@code net.minecraft.*} class throws
 *       {@link ClassNotFoundException}, which ASM wraps in
 *       {@link TypeNotPresentException} and re-throws — aborting the whole
 *       compilation.</li>
 *   <li><b>Intermediary names remapped to Mojang names not on the classpath.</b>
 *       Retromod rewrites class names mid-transform; the resulting bytecode
 *       can reference target-MC classes that the source-MC classpath
 *       doesn't contain. Same failure mode.</li>
 * </ul>
 *
 * <h3>The fix</h3>
 * <p>Catch any throwable from the superclass call and return
 * {@code "java/lang/Object"} — the universal common-superclass answer.
 * This is occasionally less optimal than the "real" answer (the resulting
 * stack-map frames are wider than they could be), but it's always
 * <em>correct</em> from a bytecode-verifier standpoint, and the size
 * overhead is negligible against losing the entire AOT pass.
 *
 * <h3>Use whenever</h3>
 * <p>any {@code ClassWriter} is constructed with {@link #COMPUTE_FRAMES}
 * or {@link #COMPUTE_MAXS} on bytecode that may reference MC classes.
 * Crash report that motivated the extraction: "AOT compilation failed:
 * journeymap-fabric-26.1.2-6.0.0-beta.78.jar / TypeNotPresentException:
 * Type net/minecraft/client/gui/GuiGraphics not present".
 */
public class SafeClassWriter extends ClassWriter {

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
            // Includes TypeNotPresentException, ClassNotFoundException,
            // NoClassDefFoundError. java/lang/Object is always a valid
            // (if conservative) common superclass.
            return "java/lang/Object";
        }
    }
}
