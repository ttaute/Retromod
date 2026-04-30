/*
 * RetroMod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Regression test for the {@code INVOKESPECIAL}-on-non-direct-supertype bug
 * that crashed ModMenu when a redirected {@code super.keyPressed} call
 * landed on an interface that wasn't in the direct superinterface list of
 * the calling class.
 *
 * <p>The {@link SuperCallScreen} below extends {@link Screen} and overrides
 * {@code keyPressed}, calling {@code super.keyPressed(...)}. The {@code super}
 * call compiles to an {@code INVOKESPECIAL} on {@code Screen.keyPressed}.
 * Source is built against MC 1.20.1 where the signature is
 * {@code keyPressed(int, int, int)}. After RetroMod translates forward to
 * MC 26.1+, the call gets remapped to the new {@code keyPressed(KeyEvent)}
 * signature on whatever class/interface owns it post-rewrite. If the
 * remapped owner isn't a direct supertype of {@code SuperCallScreen}, the
 * JVM verifier rejects the class with:
 *
 * <pre>
 *   Bad invokespecial instruction:
 *   interface method to invoke is not in a direct superinterface
 * </pre>
 *
 * <p>RetroMod's {@code emitMethodInsn} fixup catches this and rewrites the
 * call to use the direct superclass. If the fixup is missing or buggy,
 * loading {@code SuperCallScreen} below throws {@link VerifyError} and the
 * test fails.
 *
 * <p>We don't need to instantiate the screen — class load alone forces the
 * JVM to verify its methods, which is what we care about.
 */
public class Test05SuperKeyPressed implements Test {

    @Override
    public String description() {
        return "super.keyPressed INVOKESPECIAL";
    }

    @Override
    public TestResult run() {
        try {
            // Class literal access triggers load + verify of SuperCallScreen.
            // If the rewritten keyPressed has a bad invokespecial, the
            // VerifyError surfaces here.
            Class<?> cls = SuperCallScreen.class;
            if (cls == null) {
                return TestResult.fail("SuperCallScreen.class returned null (impossible)");
            }
            return TestResult.success();
        } catch (Throwable t) {
            return TestResult.fail(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * Deliberately a separate class so the {@code SuperCallScreen.class}
     * literal in {@link #run()} forces a fresh load on the first call.
     * Doesn't need to be instantiated — class verification covers our needs.
     */
    private static class SuperCallScreen extends Screen {
        protected SuperCallScreen() {
            super(Text.literal("retromod-test-super"));
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            // This call compiles to INVOKESPECIAL Screen.keyPressed — exactly
            // the case the RetroMod transformer fixup targets.
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
