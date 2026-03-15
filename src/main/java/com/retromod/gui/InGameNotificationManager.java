/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.gui;

import com.retromod.util.McReflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Queues notifications during PreLaunch and displays them as in-game
 * Minecraft screens when the title screen opens.
 *
 * Flow:
 *   1. During PreLaunch, call {@link #queue(String, String)} to add notifications
 *   2. When the title screen loads, {@link #showPending()} is called by
 *      {@link TitleScreenButtonInjector} to display all queued notifications
 *   3. Notifications are shown one at a time as MC ConfirmScreen dialogs
 *      (rendered inside the game window, NOT as Swing popups)
 */
public final class InGameNotificationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Notify");

    /**
     * A queued notification with a title and message body.
     */
    public record Notification(String title, String message) {}

    // Thread-safe queue — PreLaunch may run on a different thread than rendering
    private static final CopyOnWriteArrayList<Notification> pendingNotifications = new CopyOnWriteArrayList<>();

    // Prevent showing the same notifications twice
    private static volatile boolean shown = false;

    private InGameNotificationManager() {}

    /**
     * Queue a notification for display when the title screen opens.
     * Safe to call from any thread (including PreLaunch).
     *
     * @param title   the notification title (shown as screen title)
     * @param message the notification body (supports newlines)
     */
    public static void queue(String title, String message) {
        pendingNotifications.add(new Notification(title, message));
        // Reset shown flag so new notifications will be displayed
        shown = false;
        LOGGER.debug("Queued in-game notification: {}", title);
    }

    /**
     * Check if there are pending notifications that haven't been shown yet.
     */
    public static boolean hasPending() {
        return !pendingNotifications.isEmpty();
    }

    /**
     * Show all pending notifications as in-game screens.
     * Called by TitleScreenButtonInjector when the title screen loads.
     * Shows notifications as a chain — each "OK" opens the next one.
     *
     * IMPORTANT: This must be called on the Minecraft render thread.
     * If called from a background thread, it schedules itself on MC's main thread.
     */
    public static void showPending() {
        if (!hasPending()) return;

        // Try to schedule on MC's main thread if we're not already on it
        if (scheduleOnMainThread(() -> showPendingInternal())) {
            return; // Successfully scheduled
        }

        // If scheduling failed, try directly (we might already be on the render thread)
        showPendingInternal();
    }

    private static void showPendingInternal() {
        if (!hasPending()) return;

        // Take a snapshot of pending notifications
        List<Notification> toShow = new ArrayList<>(pendingNotifications);
        pendingNotifications.clear();

        LOGGER.info("Showing {} in-game notification(s)", toShow.size());

        // Show the first notification; its callback shows the next, etc.
        if (!showNotificationChain(toShow, 0)) {
            // All notifications failed to display — log them and allow retry next time
            for (Notification n : toShow) {
                LOGGER.info("[RetroMod] {}: {}", n.title(), n.message());
            }
        } else {
            // Successfully displayed — prevent showing again
            shown = true;
        }
    }

    /**
     * Try to schedule a runnable on MC's main thread via MinecraftClient.execute().
     * Returns true if successfully scheduled.
     */
    private static boolean scheduleOnMainThread(Runnable task) {
        try {
            Class<?> mcClass = McReflect.findClass(
                "net.minecraft.client.MinecraftClient",
                "net.minecraft.client.Minecraft",
                "net.minecraft.class_310"
            );
            if (mcClass == null) return false;

            Method getInstance = McReflect.findMethod(mcClass, "getInstance");
            Object client = getInstance != null ? getInstance.invoke(null) : null;
            if (client == null) return false;

            // MinecraftClient.execute(Runnable) — schedules on render thread
            Method execute = McReflect.findMethod(mcClass,
                new Class[]{Runnable.class}, "execute", "tell");
            if (execute != null) {
                execute.invoke(client, task);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not schedule on main thread: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Show notification at index, with callback to show the next one.
     * @return true if at least one notification was successfully shown as a screen
     */
    private static boolean showNotificationChain(List<Notification> notifications, int index) {
        if (index >= notifications.size()) return false;

        Notification notification = notifications.get(index);

        // Try to show as an in-game MC screen
        if (showAsMinecraftScreen(notification, () -> {
            // When this notification is dismissed, show the next one
            showNotificationChain(notifications, index + 1);
        })) {
            return true; // Success — at least this one displayed
        }

        // Fallback: log it and try the next one
        LOGGER.info("[RetroMod] {}: {}", notification.title(), notification.message());
        return showNotificationChain(notifications, index + 1);
    }

    /**
     * Show a notification as a Minecraft ConfirmScreen via reflection.
     * Returns true if successful, false if we need to fallback.
     */
    private static boolean showAsMinecraftScreen(Notification notification, Runnable onDismiss) {
        try {
            // Resolve MC classes (yarn / mojang / intermediary names)
            Class<?> screenClass = McReflect.findClass(
                "net.minecraft.client.gui.screen.Screen",
                "net.minecraft.client.gui.screens.Screen",
                "net.minecraft.class_437"
            );
            Class<?> textClass = McReflect.findClass(
                "net.minecraft.text.Text",
                "net.minecraft.network.chat.Component",
                "net.minecraft.class_2561"
            );
            Class<?> mcClass = McReflect.findClass(
                "net.minecraft.client.MinecraftClient",
                "net.minecraft.client.Minecraft",
                "net.minecraft.class_310"
            );

            if (screenClass == null || textClass == null || mcClass == null) {
                return false;
            }

            // Get MC client instance
            Method getInstance = McReflect.findMethod(mcClass, "getInstance");
            Object client = getInstance != null ? getInstance.invoke(null) : null;
            if (client == null) return false;

            // Get the current screen (title screen) so we can restore it after dismiss
            Method getScreen = McReflect.findMethod(mcClass, new Class[]{}, "currentScreen");
            // Fallback to field access
            Object currentScreen = McReflect.getField(client, mcClass, "currentScreen", "screen");

            // Create Text objects
            Object titleText = createText(textClass, notification.title());
            // Split message into lines for better display
            Object messageText = createText(textClass, notification.message());

            if (titleText == null || messageText == null) return false;

            // Find BooleanConsumer for ConfirmScreen callback
            Class<?> boolConsumerClass = null;
            try {
                boolConsumerClass = Class.forName("it.unimi.dsi.fastutil.booleans.BooleanConsumer");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("BooleanConsumer not found, trying alternatives");
            }

            // Try ConfirmScreen approach
            Class<?> confirmScreenClass = McReflect.findClass(
                "net.minecraft.client.gui.screen.ConfirmScreen",
                "net.minecraft.client.gui.screens.ConfirmLinkScreen",
                "net.minecraft.class_3985"
            );

            // setScreen method
            Method setScreen = McReflect.findMethod(mcClass,
                new Class[]{screenClass}, "setScreen");
            if (setScreen == null) return false;

            final Method setScreenFinal = setScreen;
            final Object clientFinal = client;
            final Object restoreScreen = currentScreen;

            if (confirmScreenClass != null && boolConsumerClass != null) {
                // Create BooleanConsumer callback that restores the title screen
                Object callback = Proxy.newProxyInstance(
                    boolConsumerClass.getClassLoader(),
                    new Class<?>[]{boolConsumerClass},
                    (proxy, method, args) -> {
                        if (method.getParameterCount() == 1 && args != null) {
                            // Restore the title screen (or show next notification)
                            try {
                                setScreenFinal.invoke(clientFinal, restoreScreen);
                            } catch (Exception ex) {
                                LOGGER.debug("Failed to restore screen: {}", ex.getMessage());
                            }
                            if (onDismiss != null) onDismiss.run();
                        }
                        return null;
                    }
                );

                // Try ConfirmScreen(BooleanConsumer, Text, Text)
                try {
                    Constructor<?> ctor = confirmScreenClass.getConstructor(
                        boolConsumerClass, textClass, textClass);
                    Object screen = ctor.newInstance(callback, titleText, messageText);
                    setScreen.invoke(client, screen);
                    LOGGER.info("Showing in-game notification: {}", notification.title());
                    return true;
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("ConfirmScreen(BoolConsumer,Text,Text) not found: {}", e.getMessage());
                }
            }

            // Fallback: try NoticeScreen
            Class<?> noticeScreenClass = McReflect.findClass(
                "net.minecraft.client.gui.screen.NoticeScreen",
                "net.minecraft.client.gui.screens.AlertScreen",
                "net.minecraft.class_410"
            );
            if (noticeScreenClass != null) {
                Runnable onClose = () -> {
                    try {
                        setScreenFinal.invoke(clientFinal, restoreScreen);
                    } catch (Exception ex) {
                        LOGGER.debug("Failed to restore screen: {}", ex.getMessage());
                    }
                    if (onDismiss != null) onDismiss.run();
                };

                Object buttonText = createText(textClass, "OK");

                // Try NoticeScreen(Runnable, Text, Text, Text, boolean)
                try {
                    Constructor<?> ctor = noticeScreenClass.getConstructor(
                        Runnable.class, textClass, textClass, textClass, boolean.class);
                    Object screen = ctor.newInstance(onClose, titleText, messageText, buttonText, false);
                    setScreen.invoke(client, screen);
                    LOGGER.info("Showing in-game notice: {}", notification.title());
                    return true;
                } catch (NoSuchMethodException e) {
                    // Try simpler constructor
                    try {
                        Constructor<?> ctor = noticeScreenClass.getConstructor(
                            Runnable.class, textClass, textClass);
                        Object screen = ctor.newInstance(onClose, titleText, messageText);
                        setScreen.invoke(client, screen);
                        LOGGER.info("Showing in-game notice: {}", notification.title());
                        return true;
                    } catch (NoSuchMethodException e2) {
                        LOGGER.debug("NoticeScreen constructors not found");
                    }
                }
            }

            return false;

        } catch (Exception e) {
            LOGGER.warn("Failed to show in-game notification: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a Text/Component literal via reflection.
     */
    private static Object createText(Class<?> textClass, String text) {
        try {
            Method literal = McReflect.findMethod(textClass,
                new Class[]{String.class}, "literal");
            if (literal != null) {
                return literal.invoke(null, text);
            }

            Method of = McReflect.findMethod(textClass,
                new Class[]{String.class}, "of");
            if (of != null) {
                return of.invoke(null, text);
            }

            // Pre-1.19.2: LiteralText
            Class<?> literalTextClass = McReflect.findClass(
                "net.minecraft.text.LiteralText",
                "net.minecraft.network.chat.TextComponent"
            );
            if (literalTextClass != null) {
                return literalTextClass.getConstructor(String.class).newInstance(text);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not create Text object: {}", e.getMessage());
        }
        return null;
    }
}
