/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.common;

import com.retromod.core.RetromodTransformer;
import org.objectweb.asm.Opcodes;

/**
 * Loader-agnostic Mojang->Mojang vanilla class moves/renames for the 1.21.11 -> 26.1 jump (#64).
 *
 * <p>Scope is vanilla {@code net/minecraft/**} and {@code com/mojang/blaze3d/**} only; loader-API
 * renames live in each loader's shim. Called from {@code Fabric_1_21_11_to_26_1} and
 * {@code NeoForge_1_21_11_to_26_1}.</p>
 */
public final class Common_1_21_11_to_26_1_ClassMoves {

    private Common_1_21_11_to_26_1_ClassMoves() {}

    public static void register(RetromodTransformer transformer) {
        // 26.1 removed the single-arg is() overloads from BlockState/ItemStack/FluidState
        // (NoSuchMethodError on block placement, tag checks, item comparisons - the Macaw's
        // Bridge_Block.onPlace crash). Bridged by a per-mod synthetic.
        IsOverloadBridgeSynthetic.register(transformer);

        // GuiGraphics -> GuiGraphicsExtractor
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiGraphics",
            "net/minecraft/client/gui/GuiGraphicsExtractor");

        // RenderType + RenderTypes moved into a rendertype sub-package.
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderType",
            "net/minecraft/client/renderer/rendertype/RenderType");
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/RenderTypes",
            "net/minecraft/client/renderer/rendertype/RenderTypes");

        // BlockAndTintGetter became client-only and moved to client/renderer/block/.
        transformer.registerClassRedirect(
            "net/minecraft/world/level/BlockAndTintGetter",
            "net/minecraft/client/renderer/block/BlockAndTintGetter");

        // ItemNameBlockItem folded into BlockItem in 26.x; same (Block, Item.Properties)
        // ctor, so an extending mod loads. Place-time custom naming is lost.
        transformer.registerClassRedirect(
            "net/minecraft/world/item/ItemNameBlockItem",
            "net/minecraft/world/item/BlockItem");

        // ResourceKey.location() -> identifier() in 26.x. A method rename, not a redirect:
        // the call site is a method reference (ResourceKey::location in Resourceful Lib's
        // ExtraByteCodecs) that the direct-call pass can't reach.
        transformer.registerMethodRename(
            "net/minecraft/resources/ResourceKey", "location", "identifier");

        // ChunkPos(long) ctor removed in 26.x; rewrite to the static ChunkPos.unpack(long).
        transformer.registerConstructorRedirect(
            "net/minecraft/world/level/ChunkPos", "(J)V",
            "net/minecraft/world/level/ChunkPos", "unpack",
            "(J)Lnet/minecraft/world/level/ChunkPos;");

        // Painting + PaintingVariant moved into an entity/decoration/painting sub-package in 26.1
        // (verified: absent at the old path, present at the new path on 26.1 and 26.2). Fixes any mod
        // referencing them AND the @Mixin(Painting) target of Deeper and Darker's PaintingMixin (#28).
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/decoration/Painting",
            "net/minecraft/world/entity/decoration/painting/Painting");
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/decoration/PaintingVariant",
            "net/minecraft/world/entity/decoration/painting/PaintingVariant");

        // Husk moved into an entity/monster/zombie sub-package in 26.1 (verified: old monster/Husk
        // absent, monster/zombie/Husk present on 26.1 and 26.2).
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/monster/Husk",
            "net/minecraft/world/entity/monster/zombie/Husk");

        // MobSpawnType was renamed to EntitySpawnReason in 26.1 (verified: MobSpawnType absent,
        // EntitySpawnReason present on 26.1). Very common in mob-spawn checkXxxSpawnRules signatures.
        transformer.registerClassRedirect(
            "net/minecraft/world/entity/MobSpawnType",
            "net/minecraft/world/entity/EntitySpawnReason");

        // Neutralize the imperative RenderSystem state setters deleted in the blaze3d
        // GpuDevice/RenderPipeline refactor (Forge wires this directly instead).
        RemovedRenderStateNeutralize.register(transformer);

        registerRegistryValueGetterRename(transformer);
        registerClientAccessorRenames26_1(transformer);
        registerCorpus26xDescriptorAdaptations(transformer);
    }

    /**
     * Vanilla client accessor method renames that landed by 26.1 (verified: 26.1 already has the new
     * name, the old one is gone; corpus-mined from the top-40 NeoForge 1.21.1 mods). Each is an
     * owner+descriptor-scoped rename, so a generic name like {@code getPosition} only rewrites the one
     * overload on the one class:
     * <ul>
     *   <li>{@code Minecraft.getTimer():DeltaTracker} -&gt; {@code getDeltaTracker()} (11 mods)</li>
     *   <li>{@code Camera.getPosition():Vec3} -&gt; {@code position()} (9 mods)</li>
     * </ul>
     * Also called from {@code Forge_1_21_11_to_26_1} so Forge client mods get them. (Fabric mods
     * reference these by intermediary name and go through the intermediary-to-Mojang map instead.)
     */
    public static void registerClientAccessorRenames26_1(RetromodTransformer transformer) {
        transformer.registerMethodRedirect(
                "net/minecraft/client/Minecraft", "getTimer", "()Lnet/minecraft/client/DeltaTracker;",
                "net/minecraft/client/Minecraft", "getDeltaTracker", "()Lnet/minecraft/client/DeltaTracker;");
        transformer.registerMethodRedirect(
                "net/minecraft/client/Camera", "getPosition", "()Lnet/minecraft/world/phys/Vec3;",
                "net/minecraft/client/Camera", "position", "()Lnet/minecraft/world/phys/Vec3;");

        // JOML const-interface widening: the vertex API's Matrix4f parameter became the immutable
        // Matrix4fc interface by 26.1 (verified present on 26.1+26.2). A 1.21.1 mod links against the
        // concrete Matrix4f overload, which no longer exists; the concrete Matrix4f IS-A Matrix4fc, so
        // rewriting just the parameter descriptor keeps the call valid. addVertex(Matrix4f,FFF) is the
        // hottest of the cluster (VertexConsumer 12 mods, BufferBuilder 7). BufferBuilder inherits the
        // widened default from VertexConsumer, so the same-owner rewrite resolves fine.
        transformer.registerMethodRedirect(
                "com/mojang/blaze3d/vertex/VertexConsumer", "addVertex",
                "(Lorg/joml/Matrix4f;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                "com/mojang/blaze3d/vertex/VertexConsumer", "addVertex",
                "(Lorg/joml/Matrix4fc;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;");
        transformer.registerMethodRedirect(
                "com/mojang/blaze3d/vertex/BufferBuilder", "addVertex",
                "(Lorg/joml/Matrix4f;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                "com/mojang/blaze3d/vertex/BufferBuilder", "addVertex",
                "(Lorg/joml/Matrix4fc;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;");
    }

    /**
     * 26.1 renamed the Registry <em>value</em> getter {@code get(Identifier)} to
     * {@code getValue(Identifier)} across the registry hierarchy (verified on the 26.1 and 26.2 jars:
     * {@code getValue(Identifier)} returns {@code T}, while {@code get(Identifier)} now returns
     * {@code Optional<Holder.Reference<T>>}, a different method). A 1.21.1 mod compiled against the
     * old value getter (e.g. {@code BuiltInRegistries.SOUND_EVENT.get(id)}) links against
     * {@code get(Identifier)Ljava/lang/Object;}, which no longer exists, so it dies at construct time
     * with {@code NoSuchMethodError: DefaultedRegistry.get(Identifier)} (YUNG's Better Strongholds,
     * verified). The surviving {@code get(Identifier)} returns {@code Optional}, so the fuzzy resolver
     * leaves the call alone (it "resolves" by name+params) - an explicit descriptor-scoped redirect is
     * required. Keyed on the post-class-remap {@code Identifier} descriptor (ClassRemapper renames
     * {@code ResourceLocation -> Identifier} before the method-redirect lookup runs), scoped to the
     * value-returning ({@code )Ljava/lang/Object;}) overload so the {@code Optional}-returning
     * {@code get} is never touched. Registered for every registry owner a mod might reference by static
     * type. Also called from {@code Forge_1_21_11_to_26_1} so Forge mods get it too.
     */
    /**
     * Corpus-mined 26.x descriptor adaptations (top-50 Fabric+NeoForge 1.21.1 audit): vanilla methods
     * that still exist on 26.1 but changed a primitive type or lost their static form, so a 1.21.1
     * mod links against a descriptor that is gone. Each is owner+descriptor-scoped (a still-present
     * overload sharing the name is untouched) and verified against the 26.1 AND 26.2 jars (old
     * signature gone, new present). Also reached by Forge client mods via
     * {@code Forge_1_21_11_to_26_1}; Fabric mods arrive here after the intermediary->Mojang remap has
     * already produced these Mojang names.
     * <ul>
     *   <li>{@code Mth.cos(F)F}/{@code Mth.sin(F)F} widened their arg to {@code (D)F} (16 mods): F2D
     *       the single arg (the last value pushed) and call the double overload.</li>
     *   <li>{@code Window.getGuiScale()D} narrowed its return to {@code ()I} (16 mods): call it, then
     *       I2D the int back to the double the caller expects.</li>
     *   <li>{@code SoundManager.play(SoundInstance)V} now returns a {@code SoundEngine$PlayResult}
     *       (24 mods): call the returning form, then POP the result the void call never had.</li>
     *   <li>{@code CompoundTag.getList(String,int)ListTag} dropped its type-hint int for
     *       {@code getListOrEmpty(String)ListTag} (15 mods): drop the trailing int, rename.</li>
     *   <li>{@code Screen.hasControlDown()/hasShiftDown()/hasAltDown()} moved from static helpers on
     *       {@code Screen} to instance methods on {@code Minecraft} (24 mods): re-express as
     *       {@code Minecraft.getInstance().hasX()}.</li>
     * </ul>
     */
    public static void registerCorpus26xDescriptorAdaptations(RetromodTransformer t) {
        // Mth.cos/sin: (F)F -> (D)F, widen the single arg (F2D).
        t.registerConvertingRedirect("net/minecraft/util/Mth", "cos", "(F)F",
                "net/minecraft/util/Mth", "cos", "(D)F", Opcodes.F2D, 0);
        t.registerConvertingRedirect("net/minecraft/util/Mth", "sin", "(F)F",
                "net/minecraft/util/Mth", "sin", "(D)F", Opcodes.F2D, 0);
        // Window.getGuiScale: ()D -> ()I, widen the result (I2D).
        t.registerConvertingRedirect("com/mojang/blaze3d/platform/Window", "getGuiScale", "()D",
                "com/mojang/blaze3d/platform/Window", "getGuiScale", "()I", 0, Opcodes.I2D);
        // SoundManager.play: (SoundInstance)V -> ()PlayResult, discard the new result (POP).
        t.registerConvertingRedirect(
                "net/minecraft/client/sounds/SoundManager", "play",
                "(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
                "net/minecraft/client/sounds/SoundManager", "play",
                "(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
                0, Opcodes.POP);
        // CompoundTag.getList(String,int) -> getListOrEmpty(String): drop the type-hint int.
        t.registerArgDropMethodRedirect(
                "net/minecraft/nbt/CompoundTag", "getList",
                "(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;",
                "net/minecraft/nbt/CompoundTag", "getListOrEmpty",
                "(Ljava/lang/String;)Lnet/minecraft/nbt/ListTag;");
        // Screen.hasControlDown/hasShiftDown/hasAltDown static -> Minecraft.getInstance().hasX().
        for (String m : new String[]{"hasControlDown", "hasShiftDown", "hasAltDown"}) {
            t.registerSingletonStaticRedirect(
                    "net/minecraft/client/gui/screens/Screen", m, "()Z",
                    "net/minecraft/client/Minecraft", "getInstance",
                    "()Lnet/minecraft/client/Minecraft;", m, "()Z");
        }

        registerNbtApiAdaptations26x(t);
        registerTextEventBridges26x(t);
    }

    /**
     * {@code ClickEvent}/{@code HoverEvent} constructor bridges for the 1.21.5 text-component rework
     * (14+19 mods across the corpus audits; text interactions are ubiquitous). Both became sealed
     * INTERFACES with per-action record subtypes, so the old {@code new ClickEvent(Action,String)} /
     * {@code new HoverEvent(Action,Object)} constructors are gone. A constructor-to-factory redirect
     * rewrites those {@code new}s to {@link com.retromod.polyfill.minecraft.RetroTextEvents}, which
     * dispatches on the action to the right subtype (the dispatch the old constructor did internally).
     * The factory is registered as a synthetic so the Forge/NeoForge per-mod embedder relocates a
     * JPMS-split-package-safe copy into any mod that references it (Fabric injects it directly). Gated
     * 26.1+ by the caller (these are interfaces on every 26.1+ host, so the old constructor is
     * genuinely gone; on a pre-1.21.5 host the redirect must NOT fire, and this shim never runs there).
     */
    public static void registerTextEventBridges26x(RetromodTransformer t) {
        ensureSyntheticRegistered(t, "com/retromod/polyfill/minecraft/RetroTextEvents");
        t.registerConstructorRedirect(
                "net/minecraft/network/chat/ClickEvent",
                "(Lnet/minecraft/network/chat/ClickEvent$Action;Ljava/lang/String;)V",
                "com/retromod/polyfill/minecraft/RetroTextEvents", "clickEvent",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;");
        t.registerConstructorRedirect(
                "net/minecraft/network/chat/HoverEvent",
                "(Lnet/minecraft/network/chat/HoverEvent$Action;Ljava/lang/Object;)V",
                "com/retromod/polyfill/minecraft/RetroTextEvents", "hoverEvent",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    }

    /** Register a Retromod class as an embeddable synthetic (idempotent; best-effort). */
    private static void ensureSyntheticRegistered(RetromodTransformer t, String internalName) {
        if (t == null || t.getSyntheticClasses().containsKey(internalName)) return;
        try (java.io.InputStream in = Common_1_21_11_to_26_1_ClassMoves.class.getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (in != null) t.registerSyntheticClass(internalName, in.readAllBytes());
        } catch (Throwable ignored) {
            // best-effort: the resource is Retromod's own class; a miss just means the ctor redirect
            // resolves against the jar-resident copy on Fabric (and no-ops where the class is absent)
        }
    }

    /**
     * The 1.21.5 NBT read refactor (verified landed by 26.1: old signature gone, new present on the
     * 26.1 AND 26.2 jars). A 1.20.1/1.21.1 mod's save/load code links against the pre-refactor NBT
     * accessors, so these break broadly across content mods on a 26.x host (top-60 Fabric 1.20.1 audit
     * frequencies noted). Each is owner+descriptor-scoped, so the co-existing {@code Optional}-returning
     * overloads (e.g. the new {@code getCompound(String):Optional}) are never touched.
     * <ul>
     *   <li>{@code CompoundTag.contains(String,int)Z} dropped its tag-type-hint int for
     *       {@code contains(String)Z} (12 mods): drop the trailing int.</li>
     *   <li>{@code CompoundTag.getCompound(String)CompoundTag} -> {@code getCompoundOrEmpty(String)}
     *       and {@code ListTag.getCompound(int)CompoundTag} -> {@code getCompoundOrEmpty(int)} (10+9
     *       mods): plain renames (the plain getters now return {@code Optional}).</li>
     *   <li>{@code CompoundTag.remove(String)V} now returns the removed {@code Tag} (9 mods): call it,
     *       then POP the result the void call never had.</li>
     *   <li>{@code TagParser.parseTag(String)CompoundTag} -> {@code parseCompoundFully(String)} (9
     *       mods): plain rename (same params/return; the checked exception is not enforced in
     *       bytecode).</li>
     * </ul>
     */
    public static void registerNbtApiAdaptations26x(RetromodTransformer t) {
        // CompoundTag.contains(String,int) -> contains(String): drop the tag-type-hint int.
        t.registerArgDropMethodRedirect("net/minecraft/nbt/CompoundTag", "contains",
                "(Ljava/lang/String;I)Z", "net/minecraft/nbt/CompoundTag", "contains", "(Ljava/lang/String;)Z");
        // getCompound -> getCompoundOrEmpty (the plain getter now returns Optional). Descriptor-scoped
        // to the CompoundTag-returning form so the new Optional overload is untouched.
        t.registerMethodRedirect("net/minecraft/nbt/CompoundTag", "getCompound",
                "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;",
                "net/minecraft/nbt/CompoundTag", "getCompoundOrEmpty",
                "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;");
        t.registerMethodRedirect("net/minecraft/nbt/ListTag", "getCompound",
                "(I)Lnet/minecraft/nbt/CompoundTag;",
                "net/minecraft/nbt/ListTag", "getCompoundOrEmpty", "(I)Lnet/minecraft/nbt/CompoundTag;");
        // CompoundTag.remove(String)V -> remove(String)Tag, discard the now-returned removed tag (POP).
        t.registerConvertingRedirect("net/minecraft/nbt/CompoundTag", "remove", "(Ljava/lang/String;)V",
                "net/minecraft/nbt/CompoundTag", "remove", "(Ljava/lang/String;)Lnet/minecraft/nbt/Tag;",
                0, Opcodes.POP);
        // TagParser.parseTag -> parseCompoundFully (same params/return; checked exn not enforced in bytecode).
        t.registerMethodRedirect("net/minecraft/nbt/TagParser", "parseTag",
                "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;",
                "net/minecraft/nbt/TagParser", "parseCompoundFully",
                "(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;");
    }

    public static void registerRegistryValueGetterRename(RetromodTransformer transformer) {
        String getIdDesc = "(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;";
        for (String owner : new String[]{
                "net/minecraft/core/Registry",
                "net/minecraft/core/DefaultedRegistry",
                "net/minecraft/core/MappedRegistry",
                "net/minecraft/core/DefaultedMappedRegistry"}) {
            transformer.registerMethodRedirect(owner, "get", getIdDesc, owner, "getValue", getIdDesc);
        }
    }
}
