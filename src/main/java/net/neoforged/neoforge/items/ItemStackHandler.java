/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Reimplementation of NeoForge's ItemStackHandler.
 * Actually stores items using MC's ItemStack via reflection,
 * providing real inventory functionality.
 */
package net.neoforged.neoforge.items;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reimplementation of ItemStackHandler that actually stores and manages
 * ItemStack objects via reflection.
 *
 * This provides real inventory slot functionality — items can be inserted,
 * extracted, queried, and slot limits are enforced. All ItemStack operations
 * use reflection since MC classes aren't on the compile classpath.
 */
public class ItemStackHandler {

    private static final Logger LOGGER = Logger.getLogger("RetroMod");

    private static volatile boolean reflectionInitialized = false;
    private static volatile Class<?> itemStackClass;
    private static volatile Object emptyStack;
    private static volatile Method getCountMethod;
    private static volatile Method getMaxCountMethod;
    private static volatile Method copyMethod;
    private static volatile Method setCountMethod;
    private static volatile Method isEmptyMethod;
    private static volatile Method isOfMethod; // areItemsEqual / isOf

    protected int size;
    protected Object[] stacks; // ItemStack[]

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            itemStackClass = Class.forName("net.minecraft.item.ItemStack");
            // Get ItemStack.EMPTY
            try {
                Field emptyField = itemStackClass.getField("EMPTY");
                emptyStack = emptyField.get(null);
            } catch (Exception e) {
                // Create empty stack via constructor
                try {
                    Class<?> itemClass = Class.forName("net.minecraft.item.Items");
                    Object airItem = itemClass.getField("AIR").get(null);
                    Constructor<?> ctor = itemStackClass.getConstructor(Class.forName("net.minecraft.item.Item"));
                    emptyStack = ctor.newInstance(airItem);
                } catch (Exception ignored) {}
            }
            try { getCountMethod = itemStackClass.getMethod("getCount"); } catch (Exception ignored) {}
            try { getMaxCountMethod = itemStackClass.getMethod("getMaxCount"); } catch (Exception ignored) {}
            try { copyMethod = itemStackClass.getMethod("copy"); } catch (Exception ignored) {}
            try { setCountMethod = itemStackClass.getMethod("setCount", int.class); } catch (Exception ignored) {}
            try { isEmptyMethod = itemStackClass.getMethod("isEmpty"); } catch (Exception ignored) {}
            try { isOfMethod = itemStackClass.getMethod("isOf", Class.forName("net.minecraft.item.Item")); } catch (Exception ignored) {}
            LOGGER.fine("[RetroMod] ItemStackHandler: using real ItemStack storage");
        } catch (Exception e) {
            LOGGER.fine("[RetroMod] ItemStackHandler: ItemStack class not found, basic mode");
        }
    }

    public ItemStackHandler() {
        this(1);
    }

    public ItemStackHandler(int size) {
        if (!reflectionInitialized) initReflection();
        this.size = size;
        this.stacks = new Object[size];
        // Fill with empty stacks
        for (int i = 0; i < size; i++) {
            stacks[i] = emptyStack; // May be null if reflection failed
        }
    }

    public int getSlots() {
        return size;
    }

    /**
     * Returns the ItemStack in the given slot.
     */
    public Object getStackInSlot(int slot) {
        if (slot < 0 || slot >= size) return emptyStack;
        return stacks[slot] != null ? stacks[slot] : emptyStack;
    }

    /**
     * Inserts an ItemStack into the given slot. Returns the remainder.
     */
    public Object insertItem(int slot, Object stack, boolean simulate) {
        if (slot < 0 || slot >= size || stack == null || isStackEmpty(stack)) {
            return emptyStack;
        }

        Object existing = stacks[slot];
        int limit = getSlotLimit(slot);

        if (existing == null || isStackEmpty(existing)) {
            // Empty slot — insert up to limit
            int count = getStackCount(stack);
            int maxStack = getStackMaxCount(stack);
            int toInsert = Math.min(count, Math.min(limit, maxStack));

            if (!simulate) {
                stacks[slot] = copyStack(stack);
                setStackCount(stacks[slot], toInsert);
                onContentsChanged(slot);
            }

            if (toInsert >= count) {
                return emptyStack;
            } else {
                Object remainder = copyStack(stack);
                setStackCount(remainder, count - toInsert);
                return remainder;
            }
        } else if (canStacksWith(existing, stack)) {
            // Same item — merge
            int existingCount = getStackCount(existing);
            int insertCount = getStackCount(stack);
            int maxStack = getStackMaxCount(existing);
            int maxInsert = Math.min(limit, maxStack) - existingCount;

            if (maxInsert <= 0) return stack; // Full

            int toInsert = Math.min(insertCount, maxInsert);

            if (!simulate) {
                setStackCount(existing, existingCount + toInsert);
                onContentsChanged(slot);
            }

            if (toInsert >= insertCount) {
                return emptyStack;
            } else {
                Object remainder = copyStack(stack);
                setStackCount(remainder, insertCount - toInsert);
                return remainder;
            }
        }

        return stack; // Incompatible items
    }

    /**
     * Extracts up to the given amount from the slot.
     */
    public Object extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= size || amount <= 0) return emptyStack;

        Object existing = stacks[slot];
        if (existing == null || isStackEmpty(existing)) return emptyStack;

        int existingCount = getStackCount(existing);
        int toExtract = Math.min(amount, existingCount);

        if (toExtract <= 0) return emptyStack;

        Object result = copyStack(existing);
        setStackCount(result, toExtract);

        if (!simulate) {
            if (toExtract >= existingCount) {
                stacks[slot] = emptyStack;
            } else {
                setStackCount(existing, existingCount - toExtract);
            }
            onContentsChanged(slot);
        }

        return result;
    }

    public int getSlotLimit(int slot) {
        return 64;
    }

    public boolean isItemValid(int slot, Object stack) {
        return true;
    }

    public void setStackInSlot(int slot, Object stack) {
        if (slot >= 0 && slot < size) {
            stacks[slot] = stack;
            onContentsChanged(slot);
        }
    }

    protected void onContentsChanged(int slot) {
        // Override point for subclasses
    }

    // --- Helper methods using reflection ---

    private boolean isStackEmpty(Object stack) {
        if (stack == null) return true;
        if (stack == emptyStack) return true;
        if (isEmptyMethod != null) {
            try { return (Boolean) isEmptyMethod.invoke(stack); } catch (Exception ignored) {}
        }
        return false;
    }

    private int getStackCount(Object stack) {
        if (stack == null) return 0;
        if (getCountMethod != null) {
            try { return (Integer) getCountMethod.invoke(stack); } catch (Exception ignored) {}
        }
        return 0;
    }

    private int getStackMaxCount(Object stack) {
        if (stack == null) return 64;
        if (getMaxCountMethod != null) {
            try { return (Integer) getMaxCountMethod.invoke(stack); } catch (Exception ignored) {}
        }
        return 64;
    }

    private Object copyStack(Object stack) {
        if (stack == null) return emptyStack;
        if (copyMethod != null) {
            try { return copyMethod.invoke(stack); } catch (Exception ignored) {}
        }
        return stack;
    }

    private void setStackCount(Object stack, int count) {
        if (stack == null || setCountMethod == null) return;
        try { setCountMethod.invoke(stack, count); } catch (Exception ignored) {}
    }

    private boolean canStacksWith(Object a, Object b) {
        if (a == null || b == null) return false;
        // Try ItemStack.areItemsEqual or similar
        try {
            Method canCombine = itemStackClass.getMethod("canCombine", itemStackClass, itemStackClass);
            return (Boolean) canCombine.invoke(null, a, b);
        } catch (Exception ignored) {}
        try {
            Method areEqual = itemStackClass.getMethod("areItemsEqual", itemStackClass, itemStackClass);
            return (Boolean) areEqual.invoke(null, a, b);
        } catch (Exception ignored) {}
        // Fallback: compare item types
        try {
            Method getItem = itemStackClass.getMethod("getItem");
            return getItem.invoke(a).equals(getItem.invoke(b));
        } catch (Exception ignored) {}
        return false;
    }
}
