/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge.embedded;

import java.lang.reflect.Method;

/**
 * Bridges old IItemHandler calls onto the NeoForge 21.9 Transfer API, where IItemHandler
 * became ResourceHandler&lt;ItemResource&gt;. Each method reflectively tries the old API,
 * then falls back to the new one.
 */
public final class IItemHandlerShim {

    private IItemHandlerShim() {
    }
    
    /** Slot count, from either the old IItemHandler or the new ResourceHandler. */
    public static int getSlots(Object handler) {
        try {
            Method getSlotsMethod = findMethod(handler.getClass(), "getSlots");
            if (getSlotsMethod != null) {
                return (int) getSlotsMethod.invoke(handler);
            }

            Method slotCountMethod = findMethod(handler.getClass(), "getSlotCount", "slotCount", "size");
            if (slotCountMethod != null) {
                return (int) slotCountMethod.invoke(handler);
            }

            throw new RuntimeException("Cannot determine slot count for handler type: " + handler.getClass());

        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to get slot count", e);
        }
    }
    
    /** ItemStack in a slot. The new API returns an ItemResource we convert back to a stack. */
    public static Object getStackInSlot(Object handler, int slot) {
        try {
            Method getStackMethod = findMethod(handler.getClass(), "getStackInSlot");
            if (getStackMethod != null) {
                return getStackMethod.invoke(handler, slot);
            }

            Method getResourceMethod = findMethod(handler.getClass(), "getResource", "getStack");
            if (getResourceMethod != null) {
                Object resource = getResourceMethod.invoke(handler, slot);
                return resourceToStack(resource, handler, slot);
            }

            throw new RuntimeException("Cannot get stack for handler type: " + handler.getClass());

        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to get stack in slot " + slot, e);
        }
    }
    
    /** Insert an ItemStack into a slot. */
    public static Object insertItem(Object handler, int slot, Object stack, boolean simulate) {
        try {
            Method insertMethod = handler.getClass().getMethod(
                "insertItem", int.class, stack.getClass(), boolean.class
            );
            return insertMethod.invoke(handler, slot, stack, simulate);

        } catch (NoSuchMethodException e) {
            try {
                return insertItemNewApi(handler, slot, stack, simulate);
            } catch (Exception ex) {
                throw new RuntimeException("Retromod: Failed to insert item", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to insert item", e);
        }
    }
    
    /** Extract an ItemStack from a slot. */
    public static Object extractItem(Object handler, int slot, int amount, boolean simulate) {
        try {
            Method extractMethod = handler.getClass().getMethod(
                "extractItem", int.class, int.class, boolean.class
            );
            return extractMethod.invoke(handler, slot, amount, simulate);

        } catch (NoSuchMethodException e) {
            try {
                return extractItemNewApi(handler, slot, amount, simulate);
            } catch (Exception ex) {
                throw new RuntimeException("Retromod: Failed to extract item", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Retromod: Failed to extract item", e);
        }
    }
    
    /** Slot limit, defaulting to a standard stack of 64 when neither API exposes it. */
    public static int getSlotLimit(Object handler, int slot) {
        try {
            Method limitMethod = findMethod(handler.getClass(), "getSlotLimit", "getMaxStackSize");
            if (limitMethod != null) {
                if (limitMethod.getParameterCount() == 1) {
                    return (int) limitMethod.invoke(handler, slot);
                } else {
                    return (int) limitMethod.invoke(handler);
                }
            }
            return 64;

        } catch (Exception e) {
            return 64;
        }
    }

    /** Whether an ItemStack is valid for a slot, defaulting to true. */
    public static boolean isItemValid(Object handler, int slot, Object stack) {
        try {
            Method validMethod = findMethod(handler.getClass(), "isItemValid");
            if (validMethod != null) {
                return (boolean) validMethod.invoke(handler, slot, stack);
            }
            return true;

        } catch (Exception e) {
            return true;
        }
    }

    private static Object resourceToStack(Object resource, Object handler, int slot) throws Exception {
        if (resource == null) {
            return getEmptyStack();
        }

        if (resource.getClass().getSimpleName().contains("ItemStack")) {
            return resource;
        }

        // ItemResource has no count; pull the item from it and the count from the handler.
        Method itemMethod = findMethod(resource.getClass(), "item", "getItem");
        if (itemMethod != null) {
            Object item = itemMethod.invoke(resource);

            Method countMethod = findMethod(handler.getClass(), "getAmount", "getCount");
            int count = 1;
            if (countMethod != null) {
                if (countMethod.getParameterCount() == 1) {
                    count = ((Number) countMethod.invoke(handler, slot)).intValue();
                } else {
                    count = ((Number) countMethod.invoke(handler)).intValue();
                }
            }

            return createStack(item, count);
        }

        return getEmptyStack();
    }

    private static Object insertItemNewApi(Object handler, int slot, Object stack, boolean simulate)
            throws Exception {
        Method[] methods = handler.getClass().getMethods();
        for (Method m : methods) {
            if (m.getName().contains("insert") || m.getName().contains("Insert")) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length >= 2) {
                    try {
                        if (params.length == 2) {
                            return m.invoke(handler, stack, simulate ? 0 : 1);
                        } else if (params.length == 3) {
                            return m.invoke(handler, slot, stack, simulate);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return stack;
    }

    private static Object extractItemNewApi(Object handler, int slot, int amount, boolean simulate)
            throws Exception {
        Method[] methods = handler.getClass().getMethods();
        for (Method m : methods) {
            if (m.getName().contains("extract") || m.getName().contains("Extract")) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length >= 2) {
                    try {
                        if (params.length == 2) {
                            return m.invoke(handler, amount, simulate);
                        } else if (params.length == 3) {
                            return m.invoke(handler, slot, amount, simulate);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        
        return getEmptyStack();
    }
    
    private static Object getEmptyStack() throws Exception {
        Class<?> stackClass = Class.forName("net.minecraft.world.item.ItemStack");
        return stackClass.getField("EMPTY").get(null);
    }
    
    private static Object createStack(Object item, int count) throws Exception {
        Class<?> stackClass = Class.forName("net.minecraft.world.item.ItemStack");
        return stackClass.getConstructor(item.getClass(), int.class).newInstance(item, count);
    }
    
    private static Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
        }
        return null;
    }
}
