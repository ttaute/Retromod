/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Polyfill for removed Fabric API classes.
 *
 * The Fabric API has deprecated and removed several builder and
 * registry classes across versions. This provider registers stub
 * implementations so older mods referencing these classes do not
 * crash with ClassNotFoundException at startup.
 *
 * Covered removals:
 * - FabricItemGroupBuilder (replaced by ItemGroup.Builder in newer Fabric API)
 * - FabricBlockEntityTypeBuilder (moved/renamed in newer Fabric API)
 * - BlockEntityRendererRegistry / EntityRendererRegistry (registration API changes)
 * - FabricDimensions (dimension API overhaul)
 * - MaterialFinder / RenderMaterial (rendering API changes)
 * - LootTableLoadingCallback (loot table API rework)
 * - FabricItemSettings (replaced by Item.Settings in newer Fabric API)
 * - FabricMineableTags (fabric-mining-level-api-v1 removed in 26.1)
 */
public class FabricApiPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Fabric API Removed Classes";
    }

    @Override
    public String getCategory() {
        return "fabric_api";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/fabricmc/fabric/api/itemgroup/v1/FabricItemGroupBuilder",
            "net/fabricmc/fabric/api/object/builder/v1/block/entity/FabricBlockEntityTypeBuilder",
            "net/fabricmc/fabric/api/client/rendering/v1/BlockEntityRendererRegistry",
            "net/fabricmc/fabric/api/client/rendering/v1/EntityRendererRegistry",
            "net/fabricmc/fabric/api/dimension/v1/FabricDimensions",
            "net/fabricmc/fabric/api/renderer/v1/material/MaterialFinder",
            "net/fabricmc/fabric/api/renderer/v1/material/RenderMaterial",
            "net/fabricmc/fabric/api/loot/v2/LootTableLoadingCallback",
            "net/fabricmc/fabric/api/item/v1/FabricItemSettings",
            // Removed in 26.1 — fabric-mining-level-api-v1 deprecated and removed
            "net/fabricmc/fabric/api/mininglevel/v1/FabricMineableTags"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        // Stubs removed from net.fabricmc.* packages to avoid JPMS
        // split-package conflicts. Fabric API classes are handled via
        // class redirects to embedded shims in com.retromod.shim.api.fabric.embedded/
        return new String[]{
            "com.retromod.shim.api.fabric.embedded.FabricItemGroupBuilderShim",
            "com.retromod.shim.api.fabric.embedded.TextShim",
            "com.retromod.shim.api.fabric.embedded.ScreenEventsShim"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // Register class redirects from removed Fabric API classes to our shims.
        // The bytecode transformer rewrites old mod references before loading.
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/itemgroup/FabricItemGroupBuilder",
            "com/retromod/shim/api/fabric/embedded/FabricItemGroupBuilderShim"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/item/v1/FabricItemSettings",
            "com/retromod/shim/api/fabric/embedded/FabricItemGroupBuilderShim"
        );

        // FabricMineableTags: generate class with ASM (needs TagKey-typed fields
        // that can't be compiled from Java source since MC isn't on the classpath).
        // NO class redirect needed — the synthetic class uses the original class name.
        byte[] mineableTagsBytes = generateFabricMineableTagsClass();
        transformer.registerSyntheticClass(
            "net/fabricmc/fabric/api/mininglevel/v1/FabricMineableTags",
            mineableTagsBytes);

        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }

    /**
     * Generate the FabricMineableTags class using ASM.
     *
     * This class has public static final TagKey<Block> fields for each mineable tag.
     * We must generate it with ASM because the field type (TagKey) is an MC class
     * not available at compile time.
     *
     * The static initializer creates each TagKey via:
     *   TagKey.create(Registries.BLOCK, Identifier.of(namespace, path))
     */
    private static byte[] generateFabricMineableTagsClass() {
        String className = "net/fabricmc/fabric/api/mininglevel/v1/FabricMineableTags";
        String tagKeyDesc = "Lnet/minecraft/tags/TagKey;";
        String tagKeyInternal = "net/minecraft/tags/TagKey";
        String resourceKeyInternal = "net/minecraft/resources/ResourceKey";
        String identifierInternal = "net/minecraft/resources/Identifier";
        String registriesInternal = "net/minecraft/core/registries/Registries";

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
            className, null, "java/lang/Object", null);

        // Tag fields: SWORD_MINEABLE, AXE_MINEABLE, HOE_MINEABLE, etc.
        String[][] tags = {
            {"SWORD_MINEABLE",   "minecraft", "mineable/sword"},
            {"AXE_MINEABLE",     "minecraft", "mineable/axe"},
            {"HOE_MINEABLE",     "minecraft", "mineable/hoe"},
            {"PICKAXE_MINEABLE", "minecraft", "mineable/pickaxe"},
            {"SHOVEL_MINEABLE",  "minecraft", "mineable/shovel"},
            {"SHEARS_MINEABLE",  "fabric",    "mineable/shears"},
        };

        for (String[] tag : tags) {
            cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                tag[0], tagKeyDesc, null, null
            ).visitEnd();
        }

        // Static initializer: create TagKey instances
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        for (String[] tag : tags) {
            // Registries.BLOCK (ResourceKey<Registry<Block>>)
            mv.visitFieldInsn(Opcodes.GETSTATIC, registriesInternal, "BLOCK",
                "L" + resourceKeyInternal + ";");

            // Identifier.fromNamespaceAndPath(namespace, path)
            mv.visitLdcInsn(tag[1]);  // namespace
            mv.visitLdcInsn(tag[2]);  // path
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, identifierInternal, "fromNamespaceAndPath",
                "(Ljava/lang/String;Ljava/lang/String;)L" + identifierInternal + ";", false);

            // TagKey.create(ResourceKey, Identifier) -> TagKey
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, tagKeyInternal, "create",
                "(L" + resourceKeyInternal + ";L" + identifierInternal + ";)L" + tagKeyInternal + ";",
                false);

            // Store to static field
            mv.visitFieldInsn(Opcodes.PUTSTATIC, className, tag[0], tagKeyDesc);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 0);
        mv.visitEnd();

        // Default constructor
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
