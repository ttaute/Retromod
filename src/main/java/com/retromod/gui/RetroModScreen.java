/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.gui;

import com.retromod.core.*;
import com.retromod.aot.AotCompiler;
import com.retromod.shim.ShimRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reflection-based mod manager screen for RetroMod.
 *
 * Accessible from the title screen via the Mixin-injected RetroMod button.
 * Uses a Swing file picker for mod selection (OS-native), then transforms
 * the selected mods with full complexity analysis and progress logging.
 *
 * This class uses reflection for all Minecraft interactions because the
 * project is built with Maven (no Minecraft classes on compile classpath).
 * At runtime inside Minecraft, all classes are available.
 *
 * Flow:
 *   1. User clicks RetroMod button on title screen
 *   2. This class opens a native file picker dialog
 *   3. Selected mods are analyzed for complexity
 *   4. If deemed "unlikely to work", user is warned (unless force mode)
 *   5. Remaining mods are transformed and installed to mods/
 *   6. User is told to restart Minecraft
 */
public class RetroModScreen {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Screen");

    private final Object minecraftClient; // MinecraftClient instance
    private final Object parentScreen;    // TitleScreen instance

    public record TransformResult(String modName, Status status, String message) {
        public enum Status { SUCCESS, SKIPPED, FAILED, COMPLEX_WARNING }
    }

    public RetroModScreen(Object client, Object parent) {
        this.minecraftClient = client;
        this.parentScreen = parent;
    }

    /**
     * Open the RetroMod manager — shows a file picker and transforms mods.
     */
    public void open() {
        // Run file picker on background thread to avoid freezing Minecraft
        CompletableFuture.runAsync(() -> {
            try {
                File[] selectedFiles = showFilePicker();

                if (selectedFiles != null && selectedFiles.length > 0) {
                    List<TransformResult> results = transformMods(selectedFiles);
                    showResults(results);
                }
            } catch (Exception e) {
                LOGGER.error("RetroMod manager error", e);
            }
        });
    }

    /**
     * Show native file picker dialog.
     */
    private File[] showFilePicker() throws Exception {
        final File[][] result = {null};

        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("RetroMod — Select Mod JARs to Transform");
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("Minecraft Mods (*.jar)", "jar"));
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

            // Add Full AOT checkbox
            JCheckBox aotCheckbox = new JCheckBox("Full AOT Mode (slower compile, faster gameplay)");
            aotCheckbox.setToolTipText("Pre-compiles ALL mod code for maximum performance.");
            JPanel accessory = new JPanel();
            accessory.setLayout(new BoxLayout(accessory, BoxLayout.Y_AXIS));
            accessory.setBorder(BorderFactory.createTitledBorder("RetroMod Options"));
            accessory.add(aotCheckbox);
            chooser.setAccessory(accessory);

            int choice = chooser.showOpenDialog(null);
            if (choice == JFileChooser.APPROVE_OPTION) {
                result[0] = chooser.getSelectedFiles();
            }
        });

        return result[0];
    }

    /**
     * Transform selected mod files.
     */
    private List<TransformResult> transformMods(File[] modFiles) {
        List<TransformResult> results = new CopyOnWriteArrayList<>();

        Path gameDir = getGameDir();
        Path modsFolder = gameDir.resolve("mods");
        ModCompatibilityChecker checker = new ModCompatibilityChecker(gameDir);
        ModComplexityAnalyzer complexityAnalyzer = new ModComplexityAnalyzer();
        boolean forceComplex = isForceTranslateEnabled();

        for (File modFile : modFiles) {
            String name = modFile.getName();
            LOGGER.info("Processing: {}", name);

            try {
                Path modPath = modFile.toPath();

                // Complexity check
                ModComplexityAnalyzer.ComplexityReport report = complexityAnalyzer.analyze(modPath);

                if (report.isUnlikelyToWork() && !forceComplex) {
                    LOGGER.warn("Skipped {} — complexity score {} (unlikely to work: {})",
                        name, report.score(), report.reason());
                    results.add(new TransformResult(
                        name,
                        TransformResult.Status.COMPLEX_WARNING,
                        "Unlikely to work: " + report.reason() +
                            ". Enable \"force_translate_complex\" in config to try anyway."
                    ));
                    continue;
                }

                // Analyze
                ModCompatibilityChecker.IncompatibleMod analysis = checker.analyzeJar(modPath);

                if (analysis != null) {
                    Path transformed = checker.transformAndInstall(modPath);
                    results.add(new TransformResult(name, TransformResult.Status.SUCCESS,
                        "Transformed to " + transformed.getFileName()));
                    LOGGER.info("Transformed: {} -> {}", name, transformed.getFileName());
                } else {
                    Files.copy(modPath, modsFolder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    results.add(new TransformResult(name, TransformResult.Status.SKIPPED,
                        "Already compatible, copied"));
                }

            } catch (Exception e) {
                LOGGER.error("Failed to transform {}", name, e);
                results.add(new TransformResult(name, TransformResult.Status.FAILED,
                    e.getClass().getSimpleName() + ": " +
                        (e.getMessage() != null ? e.getMessage() : "Unknown error")));
            }
        }

        return results;
    }

    /**
     * Show transformation results in a dialog.
     */
    private void showResults(List<TransformResult> results) {
        long success = results.stream().filter(r -> r.status() == TransformResult.Status.SUCCESS).count();
        long failed = results.stream().filter(r -> r.status() == TransformResult.Status.FAILED).count();
        long skipped = results.stream().filter(r -> r.status() == TransformResult.Status.SKIPPED).count();
        long complex = results.stream().filter(r -> r.status() == TransformResult.Status.COMPLEX_WARNING).count();

        StringBuilder msg = new StringBuilder();
        msg.append("RetroMod Transformation Complete!\n\n");

        if (success > 0) msg.append("  \u2713 ").append(success).append(" mod(s) transformed successfully\n");
        if (skipped > 0) msg.append("  \u2192 ").append(skipped).append(" mod(s) already compatible\n");
        if (complex > 0) msg.append("  \u26A0 ").append(complex).append(" mod(s) skipped (too complex)\n");
        if (failed > 0) msg.append("  \u2717 ").append(failed).append(" mod(s) failed\n");

        msg.append("\n");

        // List individual results
        for (TransformResult r : results) {
            String prefix = switch (r.status()) {
                case SUCCESS -> "\u2713";
                case SKIPPED -> "\u2192";
                case FAILED -> "\u2717";
                case COMPLEX_WARNING -> "\u26A0";
            };
            msg.append("  ").append(prefix).append(" ").append(r.modName());
            if (r.status() == TransformResult.Status.COMPLEX_WARNING) {
                msg.append("\n    ").append(r.message());
            }
            msg.append("\n");
        }

        if (success > 0) {
            msg.append("\nPlease RESTART Minecraft for changes to take effect.");
        }

        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                null,
                msg.toString(),
                "RetroMod - Results",
                success > 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
            );
        });
    }

    /**
     * Get the game directory.
     */
    private Path getGameDir() {
        try {
            // Try to get from MinecraftClient.runDirectory
            var field = minecraftClient.getClass().getField("runDirectory");
            File dir = (File) field.get(minecraftClient);
            return dir.toPath();
        } catch (Exception e) {
            try {
                // Try FabricLoader
                Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object instance = fabricLoader.getMethod("getInstance").invoke(null);
                return (Path) fabricLoader.getMethod("getGameDir").invoke(instance);
            } catch (Exception e2) {
                return Path.of(".");
            }
        }
    }

    /**
     * Check if force_translate_complex is enabled in config.
     */
    private boolean isForceTranslateEnabled() {
        try {
            Path configPath = Path.of("config/retromod/config.json");
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                return json.contains("\"force_translate_complex\": true") ||
                       json.contains("\"force_translate_complex\":true");
            }
        } catch (Exception e) {
            // Default to false
        }
        return false;
    }
}
