/*
 * Retromod Test Mod
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.testmod.tests;

import com.retromod.testmod.SimpleTest;
import com.retromod.testmod.Test;
import com.retromod.testmod.TestResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Client-side GUI APIs. {@code MinecraftClient}/{@code Minecraft} is the
 * client singleton; {@code ButtonWidget.builder} is the modern factory for
 * standard buttons. {@code Screen} subclasses are tested elsewhere
 * (see {@code Test05SuperKeyPressed}) because they need their own class.
 */
public final class GuiTests {

    private GuiTests() {}

    public static List<Test> all() {
        return List.of(
            new SimpleTest("MinecraftClient.getInstance()", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                return mc != null
                    ? TestResult.success()
                    : TestResult.fail("null - too early in client init?");
            }),
            new SimpleTest("ButtonWidget.builder(text, onPress)", () -> {
                Object builder = ButtonWidget.builder(Text.literal("test"), b -> {});
                return builder != null
                    ? TestResult.success()
                    : TestResult.fail("null");
            }),
            new SimpleTest("ButtonWidget.builder().dimensions().build()", () -> {
                ButtonWidget b = ButtonWidget.builder(Text.literal("test"), btn -> {})
                        .dimensions(0, 0, 80, 20)
                        .build();
                if (b == null) return TestResult.fail("null");
                if (b.getWidth() != 80) return TestResult.fail("width=" + b.getWidth());
                return TestResult.success();
            })
        );
    }
}
