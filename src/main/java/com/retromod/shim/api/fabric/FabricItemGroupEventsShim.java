/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetromodTransformer;
import com.retromod.core.VersionShim;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the removed Fabric <b>item-group events v1</b> API
 * ({@code net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents}) - adding items to
 * creative tabs - onto the surviving {@code creativetab/v1/CreativeModeTabEvents}.
 * This is the single biggest functional-interface gap in the compat audit:
 * ~83 mods are sole-blocked on {@code ItemGroupEvents$ModifyEntries}.
 *
 * <h2>Why a redirect alone is a false win</h2>
 * The v1 SAM is {@code ModifyEntries.modifyEntries(FabricItemGroupEntries)}; the v2
 * one is {@code ModifyOutput.modifyOutput(FabricCreativeModeTabOutput)}. The
 * <i>method name</i> changed, so a class redirect of the inner interface would
 * crash {@code LambdaMetafactory} (the mod's lambda hard-codes {@code modifyEntries}).
 * And the registration entrypoint renamed too: {@code modifyEntriesEvent(key)} →
 * {@code modifyOutputEvent(key)}. So both the holder and the SAM must be kept alive.
 *
 * <h2>Three moving parts</h2>
 * <ol>
 *   <li><b>Synthetic holder + SAM interfaces.</b> A synthetic {@code ItemGroupEvents}
 *       carrying {@code modifyEntriesEvent(ResourceKey)} and the
 *       {@code MODIFY_ENTRIES_ALL} field; synthetic {@code ModifyEntries} /
 *       {@code ModifyEntriesAll} interfaces preserving the old SAM names with the
 *       (redirected) {@code FabricCreativeModeTabOutput} parameter. The old names
 *       are redirected onto these. The holder routes everything through
 *       {@link com.retromod.shim.api.fabric.embedded.ItemGroupEventsBridge}, which
 *       wires each v1 event to its live v2 counterpart by reflection.</li>
 *   <li><b>Parameter-object method renames.</b> The lambda body calls
 *       {@code addAfter}/{@code addBefore} on the entries object; on
 *       {@code FabricCreativeModeTabOutput} (the existing class-redirect target of
 *       {@code FabricItemGroupEntries}) those are now {@code insertAfter}/
 *       {@code insertBefore}. We rename all 12 overloads of each - descriptors taken
 *       verbatim from {@code fabric-api-0.145.4} so a 1:1 rename is exact. The
 *       {@code prepend}/getter methods kept their names and ride the class redirect.</li>
 * </ol>
 *
 * <p>{@code FabricItemGroupEntries → FabricCreativeModeTabOutput} itself is the
 * version shim's job ({@code Fabric_1_21_11_to_26_1}); this shim assumes it and only
 * renames the methods on the new owner.</p>
 *
 * <p><b>STATUS - authored, not yet runtime-verified.</b> Contracts checked against
 * {@code fabric-api-0.141.1+1.21.11} (old) and {@code 0.145.4+26.1.2} (new). A 26.1
 * launch that adds an item to a creative tab through a v1 mod is still required.</p>
 */
public class FabricItemGroupEventsShim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private static final String GEN = "com/retromod/generated/legacyitemgroup/";
    private static final String HOLDER            = GEN + "ItemGroupEvents";
    private static final String MODIFY_ENTRIES    = GEN + "ModifyEntries";
    private static final String MODIFY_ENTRIES_ALL = GEN + "ModifyEntriesAll";
    private static final String BRIDGE = "com/retromod/shim/api/fabric/embedded/ItemGroupEventsBridge";

    private static final String OLD_OUTER       = "net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents";
    private static final String OLD_OUTER_CLIENT = "net/fabricmc/fabric/api/client/itemgroup/v1/ItemGroupEvents";

    private static final String EVENT   = "net/fabricmc/fabric/api/event/Event";
    private static final String L_EVENT = "L" + EVENT + ";";
    private static final String OUTPUT  = "net/fabricmc/fabric/api/creativetab/v1/FabricCreativeModeTabOutput";
    private static final String CREATIVE_TAB = "net/minecraft/world/item/CreativeModeTab";
    private static final String RESOURCE_KEY = "net/minecraft/resources/ResourceKey";

    private static final String MODIFY_ENTRIES_DESC     = "(L" + OUTPUT + ";)V";
    private static final String MODIFY_ENTRIES_ALL_DESC = "(L" + CREATIVE_TAB + ";L" + OUTPUT + ";)V";
    private static final String MODIFY_ENTRIES_EVENT_DESC = "(L" + RESOURCE_KEY + ";)" + L_EVENT;

    /**
     * The 12 entry-insertion overload descriptors, in Mojang form (the post-harvest
     * shape the call site has by the time method redirects run). addAfter/addBefore
     * share this exact set with insertAfter/insertBefore (a pure rename). Verified
     * 1:1 against fabric-api-0.145.4.
     */
    private static final String[] INSERT_DESCS = {
        "(Ljava/util/function/Predicate;Ljava/util/Collection;Ljava/util/List;)V",
        "(Ljava/util/function/Predicate;Ljava/util/Collection;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
        "(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;)V",
        "(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Ljava/util/List;)V",
        "(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
        "(Lnet/minecraft/world/item/ItemStack;[Lnet/minecraft/world/item/ItemStack;)V",
        "(Lnet/minecraft/world/item/ItemStack;[Lnet/minecraft/world/level/ItemLike;)V",
        "(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;)V",
        "(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;Ljava/util/List;)V",
        "(Lnet/minecraft/world/level/ItemLike;Ljava/util/Collection;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
        "(Lnet/minecraft/world/level/ItemLike;[Lnet/minecraft/world/item/ItemStack;)V",
        "(Lnet/minecraft/world/level/ItemLike;[Lnet/minecraft/world/level/ItemLike;)V",
    };

    @Override public String getShimName() { return "Fabric item-group events v1 (ItemGroupEvents) bridge"; }
    @Override public String getSourceVersion() { return "0.40.0"; }
    @Override public String getTargetVersion() { return "0.145.0"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetromodTransformer transformer) {
        // 26.1+ hosts ONLY (pitfall #9). On a pre-26.1 host ItemGroupEvents is still
        // ALIVE in the Fabric API - redirecting it to our synthetic would hijack a
        // working API and wire it to creativetab/v1, which doesn't exist there. The
        // synthetics also declare Mojang-named MC types (ResourceKey, CreativeModeTab)
        // that don't resolve on an intermediary runtime.
        if (!com.retromod.core.RetromodVersion.isUnobfuscatedTarget(
                com.retromod.core.RetromodVersion.TARGET_MC_VERSION)) {
            LOGGER.debug("[Retromod] item-group events v1 bridge skipped (host {} < 26.1 - old API still present)",
                    com.retromod.core.RetromodVersion.TARGET_MC_VERSION);
            return;
        }

        // (0) Embed the reflective bridge (java.* only).
        transformer.registerEmbeddedShim(BRIDGE.replace('/', '.'));

        // (1) Keep the holder + the two SAM interfaces alive, redirect old names onto them.
        transformer.registerSyntheticClass(HOLDER, generateHolder());
        transformer.registerSyntheticClass(MODIFY_ENTRIES, generateModifyEntries());
        transformer.registerSyntheticClass(MODIFY_ENTRIES_ALL, generateModifyEntriesAll());

        for (String outer : new String[]{OLD_OUTER, OLD_OUTER_CLIENT}) {
            transformer.registerClassRedirect(outer, HOLDER);
            transformer.registerClassRedirect(outer + "$ModifyEntries", MODIFY_ENTRIES);
            transformer.registerClassRedirect(outer + "$ModifyEntriesAll", MODIFY_ENTRIES_ALL);
        }

        // (2) Rename the entries-object methods on the (already class-redirected) output.
        //     addAfter → insertAfter, addBefore → insertBefore, per exact overload.
        for (String desc : INSERT_DESCS) {
            transformer.registerMethodRedirect(OUTPUT, "addAfter", desc, OUTPUT, "insertAfter", desc);
            transformer.registerMethodRedirect(OUTPUT, "addBefore", desc, OUTPUT, "insertBefore", desc);
        }

        LOGGER.info("[Retromod] Fabric item-group events v1 bridge - kept ItemGroupEvents holder + "
                + "ModifyEntries/ModifyEntriesAll SAMs wired to creativetab/v1, renamed "
                + "addAfter/addBefore→insertAfter/insertBefore (STATUS: needs in-game verification)");
    }

    /**
     * Synthetic {@code ItemGroupEvents} holder: {@code modifyEntriesEvent(ResourceKey)}
     * forwarding to the bridge, plus the {@code MODIFY_ENTRIES_ALL} field set in
     * {@code <clinit>}.
     */
    static byte[] generateHolder() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
                HOLDER, null, "java/lang/Object", null);

        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "MODIFY_ENTRIES_ALL", L_EVENT, null, null).visitEnd();

        // public static Event modifyEntriesEvent(ResourceKey key) {
        //     return (Event) ItemGroupEventsBridge.modifyEntriesEvent(key);
        // }
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "modifyEntriesEvent", MODIFY_ENTRIES_EVENT_DESC, null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "modifyEntriesEvent",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        m.visitTypeInsn(Opcodes.CHECKCAST, EVENT);
        m.visitInsn(Opcodes.ARETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();

        // static { MODIFY_ENTRIES_ALL = (Event) ItemGroupEventsBridge.installModifyAll(); }
        MethodVisitor clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        clinit.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "installModifyAll",
                "()Ljava/lang/Object;", false);
        clinit.visitTypeInsn(Opcodes.CHECKCAST, EVENT);
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, HOLDER, "MODIFY_ENTRIES_ALL", L_EVENT);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Synthetic {@code ModifyEntries}: SAM {@code modifyEntries(FabricCreativeModeTabOutput)}. */
    static byte[] generateModifyEntries() {
        return samInterface(MODIFY_ENTRIES, "modifyEntries", MODIFY_ENTRIES_DESC);
    }

    /** Synthetic {@code ModifyEntriesAll}: SAM {@code modifyEntries(CreativeModeTab, FabricCreativeModeTabOutput)}. */
    static byte[] generateModifyEntriesAll() {
        return samInterface(MODIFY_ENTRIES_ALL, "modifyEntries", MODIFY_ENTRIES_ALL_DESC);
    }

    private static byte[] samInterface(String internalName, String samName, String samDesc) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
                internalName, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, samName, samDesc, null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
