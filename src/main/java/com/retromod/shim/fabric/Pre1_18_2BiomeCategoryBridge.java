/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-1.18.2 {@code Biome.Category} bridge (Fabric, pre-26.1 hosts, intermediary
 * namespace).
 *
 * <h2>The problem</h2>
 * {@code Biome.Category} ({@code class_1959$class_1961}) — an enum of biome "kinds"
 * (FOREST, DESERT, NETHER, …) used to register custom mob spawns — was deleted in
 * 1.18.2 when biome categorization moved to data-driven tags. Every pre-1.18.2
 * mod that registers spawns via category — Earth2Java's {@code BiomeSpawnHelper}
 * is the canonical case — dies in its own {@code <clinit>} with
 * {@code NoClassDefFoundError: class_1959$class_1961} the instant it touches one
 * of the enum constants, before the mod's onInitialize even runs. The companion
 * accessor, {@code class_1959.method_8688()} (Biome.getCategory), is also gone
 * and fires {@code NoSuchMethodError} from the same helpers.
 *
 * <h2>The bridge</h2>
 * Inject a stand-in enum {@code com/retromod/generated/LegacyBiomeCategory} with
 * one static field per legacy intermediary ID ({@code field_9354 … field_9370}),
 * and class-redirect every reference to {@code class_1959$class_1961} onto it.
 * Bonus: the synthetic also publishes a static
 * {@code getCategory(Lclass_1959;)LLegacyBiomeCategory;} that
 * {@code class_1959.method_8688()} is rewritten to call (devirtualized so the
 * receiver becomes the first arg) — returning {@code field_9363} (the slot that
 * was NONE on 1.16-era yarn).
 *
 * <p>The functional outcome is intentionally inert: every real biome on a 1.18.2+
 * host comes back as the synthetic "none", so category-keyed spawn arrays end up
 * empty and the mod's mobs don't get added to vanilla spawn pools. That's the
 * right floor — the mod LOADS (every other feature still works; players can
 * <code>/summon</code> the mobs, custom items/blocks/recipes/textures all run
 * untouched), and the alternative is "Retromod crashes the mod at startup",
 * which is what was happening. A future pass could synthesize a tag-based
 * spawn-injection bridge, but that's a much larger rewrite of the mod's spawn
 * logic and well beyond what a name-translation layer can do.</p>
 *
 * <h2>Gating</h2>
 * Wired alongside the model + InteractionResult + Identifier-ctor bridges
 * (pre-26.1 Fabric hosts only). Self-gates as a no-op when {@code class_1959}
 * itself isn't on the classpath, so unit tests (no MC) don't register anything.
 */
public final class Pre1_18_2BiomeCategoryBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    /** Original intermediary internal name of {@code Biome.Category}. */
    private static final String BIOME_CATEGORY = "net/minecraft/class_1959$class_1961";

    /** Original intermediary internal name of {@code Biome}. */
    private static final String BIOME = "net/minecraft/class_1959";

    /** Original intermediary name of {@code Biome.getCategory}. */
    private static final String GET_CATEGORY = "method_8688";

    /** Our stand-in enum's internal name. */
    private static final String SELF = "com/retromod/generated/LegacyBiomeCategory";

    private static final String L_SELF = "L" + SELF + ";";
    private static final String L_BIOME = "L" + BIOME + ";";

    /**
     * Every intermediary field ID for {@code Biome.Category} that we generate as a
     * constant. {@code field_9354 .. field_9370} is the full set from 1.16-era yarn
     * (BEACH, DESERT, EXTREME_HILLS, FOREST, ICY, JUNGLE, MESA, MUSHROOM, NETHER,
     * NONE, OCEAN, PLAINS, RIVER, SAVANNA, SWAMP, TAIGA, THEEND). We include all 17
     * even though Earth2Java's {@code BiomeSpawnHelper} only references 14 — the
     * cost is negligible, and any other pre-1.18.2 mod with a different subset of
     * categories then works for free.
     */
    private static final String[] CATEGORY_FIELDS = {
        "field_9354", "field_9355", "field_9356", "field_9357", "field_9358",
        "field_9359", "field_9360", "field_9361", "field_9362", "field_9363",
        "field_9364", "field_9365", "field_9366", "field_9367", "field_9368",
        "field_9369", "field_9370",
    };

    /** The synthetic constant we return from {@code getCategory(Biome)} — slot of NONE on 1.16 yarn. */
    private static final String NONE_FIELD = "field_9363";

    private Pre1_18_2BiomeCategoryBridge() {}

    /** Wire the synthetic + class redirect + Biome.getCategory rewrite. */
    public static void register(RetromodTransformer transformer) {
        // Probe class_1959 (Biome) on the host. If it isn't even there, we're in a
        // unit test or some headless tool — bail with a debug log, no redirects.
        try {
            Class.forName("net." + "minecraft.class_1959", false,
                    Pre1_18_2BiomeCategoryBridge.class.getClassLoader());
        } catch (Throwable t) {
            LOGGER.debug("[Retromod] Biome.Category bridge — class_1959 not on classpath, skipping ({})",
                    t.getClass().getSimpleName());
            return;
        }

        transformer.registerSyntheticClass(SELF, generateLegacyBiomeCategory());
        transformer.registerClassRedirect(BIOME_CATEGORY, SELF);

        // Biome.getCategory() — gone on host. Rewrite to our static, devirtualized so
        // the original `aload biome; invokevirtual method_8688()` becomes `aload biome;
        // invokestatic LegacyBiomeCategory.getCategory(Biome)`. The receiver type
        // (`class_1959`) is the real Biome on the host, so no stub needed.
        //
        // Descriptor: after the class redirect for BIOME_CATEGORY, the original return
        // type `Lclass_1959$class_1961;` is rewritten to `LLegacyBiomeCategory;` in the
        // call site, so the redirect key has to match that POST-remap form.
        transformer.registerMethodRedirect(
                BIOME, GET_CATEGORY, "()" + L_SELF,
                SELF, "getCategory", "(" + L_BIOME + ")" + L_SELF,
                true);

        LOGGER.info("[Retromod] Biome.Category bridge — injected stand-in enum {} ({} constants), "
                + "class-redirected class_1959$class_1961, and rewrote Biome.method_8688() → getCategory()",
                SELF, CATEGORY_FIELDS.length);
    }

    /**
     * Generate an enum with one public static final field per legacy category ID.
     * Shape mirrors what {@code javac} emits for a plain enum: private {@code (String,int)}
     * ctor, synthetic {@code $VALUES} array, {@code values()} / {@code valueOf(String)}
     * helpers, and a {@code <clinit>} that constructs each constant and stuffs it into
     * the array. Plus our extra {@code static getCategory(Biome)} that returns NONE —
     * the swap-in for the deleted host accessor.
     */
    static byte[] generateLegacyBiomeCategory() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String t1, String t2) {
                // The synthetic only mentions Enum subclasses + Object — but the
                // generator runs in unit tests without MC on the classpath, so fall
                // back to Object whenever the resolver fails (same trick as the
                // pre-1.17 model bridge).
                try { return super.getCommonSuperClass(t1, t2); }
                catch (Throwable t) { return "java/lang/Object"; }
            }
        };

        cw.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM,
                SELF,
                "Ljava/lang/Enum<" + L_SELF + ">;",
                "java/lang/Enum",
                null);

        // Public static final fields, one per category constant.
        for (String field : CATEGORY_FIELDS) {
            cw.visitField(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                    field, L_SELF, null, null
            ).visitEnd();
        }

        // Synthetic private static final $VALUES array.
        cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                "$VALUES", "[" + L_SELF, null, null
        ).visitEnd();

        // Private constructor: Enum.<init>(String name, int ordinal).
        MethodVisitor ctor = cw.visitMethod(
                Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitVarInsn(Opcodes.ILOAD, 2);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>",
                "(Ljava/lang/String;I)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        // public static T[] values() { return $VALUES.clone(); }
        MethodVisitor values = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[" + L_SELF, null, null);
        values.visitCode();
        values.visitFieldInsn(Opcodes.GETSTATIC, SELF, "$VALUES", "[" + L_SELF);
        values.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[" + L_SELF, "clone",
                "()Ljava/lang/Object;", false);
        values.visitTypeInsn(Opcodes.CHECKCAST, "[" + L_SELF);
        values.visitInsn(Opcodes.ARETURN);
        values.visitMaxs(0, 0);
        values.visitEnd();

        // public static T valueOf(String) { return (T) Enum.valueOf(T.class, name); }
        MethodVisitor valueOf = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf",
                "(Ljava/lang/String;)" + L_SELF, null, null);
        valueOf.visitCode();
        valueOf.visitLdcInsn(org.objectweb.asm.Type.getType(L_SELF));
        valueOf.visitVarInsn(Opcodes.ALOAD, 0);
        valueOf.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        valueOf.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        valueOf.visitInsn(Opcodes.ARETURN);
        valueOf.visitMaxs(0, 0);
        valueOf.visitEnd();

        // public static LegacyBiomeCategory getCategory(Biome) { return field_9363; }
        // The Biome receiver is unused — every category-keyed lookup on a 1.18.2+ host
        // collapses to NONE, which makes the mod's spawn arrays come back empty. See
        // the class javadoc for why that's the right floor here.
        MethodVisitor getCat = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getCategory",
                "(" + L_BIOME + ")" + L_SELF, null, null);
        getCat.visitCode();
        getCat.visitFieldInsn(Opcodes.GETSTATIC, SELF, NONE_FIELD, L_SELF);
        getCat.visitInsn(Opcodes.ARETURN);
        getCat.visitMaxs(0, 0);
        getCat.visitEnd();

        // <clinit>: new <constant>; populate $VALUES.
        MethodVisitor clinit = cw.visitMethod(
                Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();
        for (int i = 0; i < CATEGORY_FIELDS.length; i++) {
            String field = CATEGORY_FIELDS[i];
            clinit.visitTypeInsn(Opcodes.NEW, SELF);
            clinit.visitInsn(Opcodes.DUP);
            clinit.visitLdcInsn(field);
            pushInt(clinit, i);
            clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, SELF, "<init>",
                    "(Ljava/lang/String;I)V", false);
            clinit.visitFieldInsn(Opcodes.PUTSTATIC, SELF, field, L_SELF);
        }
        pushInt(clinit, CATEGORY_FIELDS.length);
        clinit.visitTypeInsn(Opcodes.ANEWARRAY, SELF);
        for (int i = 0; i < CATEGORY_FIELDS.length; i++) {
            clinit.visitInsn(Opcodes.DUP);
            pushInt(clinit, i);
            clinit.visitFieldInsn(Opcodes.GETSTATIC, SELF, CATEGORY_FIELDS[i], L_SELF);
            clinit.visitInsn(Opcodes.AASTORE);
        }
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, SELF, "$VALUES", "[" + L_SELF);
        clinit.visitInsn(Opcodes.RETURN);
        clinit.visitMaxs(0, 0);
        clinit.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Pick the smallest-encoding int push for n (ICONST_0..5, BIPUSH, SIPUSH, LDC). */
    private static void pushInt(MethodVisitor mv, int n) {
        if (n >= -1 && n <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + n);
        } else if (n >= Byte.MIN_VALUE && n <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, n);
        } else if (n >= Short.MIN_VALUE && n <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, n);
        } else {
            mv.visitLdcInsn(n);
        }
    }
}
