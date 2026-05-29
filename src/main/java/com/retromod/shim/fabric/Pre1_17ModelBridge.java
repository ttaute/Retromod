/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-1.17 entity-model bridge (Fabric, pre-26.1 hosts, intermediary namespace).
 *
 * <h2>The problem</h2>
 * Minecraft rebuilt the entity-model system in 1.17. A Fabric mod built for ≤1.16
 * constructs its models with the OLD <i>mutable, self-building</i> idiom:
 * <pre>
 *   ModelPart head = new ModelPart(this, texU, texV);  // class_630.&lt;init&gt;(class_3879,II)V
 *   head.setTextureOffset(u, v);                        // method_2850 texOffs
 *   head.addBox(x,y,z,w,h,d, delta);                    // method_2856 addBox
 *   head.setRotationPoint(x,y,z);                       // method_2851 setPos
 * </pre>
 * In 1.17+, {@code ModelPart} ({@code class_630}) no longer self-constructs — its only
 * ctor is {@code (List<Cube>, Map<String,ModelPart>)} and box-building moved to
 * {@code CubeListBuilder}. The names all still exist; the signatures/owners changed.
 *
 * <h2>The bridge (layers 1+3+2)</h2>
 * Synthetic {@code LegacyModelPart extends class_630} (layer 1), injected per-mod. The
 * old construction calls are de-virtualized to its static methods. Recorders now
 * <b>actually store</b> cubes/transforms (layer 2 — this build), and the synthetic
 * <b>overrides {@code method_22699}</b> (the int-color {@code render}) to draw the
 * recorded cubes via the {@code VertexConsumer} chain. Per-base {@code LegacyModelBase_*}
 * synthetics (layer 3) bridge each vanilla model base's {@code super()} call onto its
 * modern {@code (ModelPart)} ctor with a forgiving {@code EMPTY_ROOT}.
 *
 * <h2>ASM constraints</h2>
 * Retromod doesn't compile against Minecraft, so any class extending {@code class_630}
 * is ASM-emitted and injected. The render override has loops (over boxes and children),
 * so the writer uses {@code COMPUTE_FRAMES} — with a {@code getCommonSuperClass}
 * override that falls back to {@code Object} for unresolvable types so the generator
 * still runs in unit tests without Minecraft on the classpath.
 *
 * <h2>Gating</h2>
 * Registered only from the Fabric pre-launch path when the host is <b>pre-26.1</b>
 * (intermediary runtime). On a 26.x host the runtime is Mojang-named, {@code class_630}
 * doesn't exist, and the intermediary→Mojang remap renames these calls first — so the
 * Mojang-namespace variant is a separate (future) registration.
 */
public final class Pre1_17ModelBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");

    private Pre1_17ModelBridge() {}

    // ── Intermediary names (1.21.x-stable) ──────────────────────────────────
    static final String MODEL_PART = "net/minecraft/class_630";
    static final String MODEL = "net/minecraft/class_3879";
    static final String POSE_STACK = "net/minecraft/class_4587";
    static final String POSE_INNER = "net/minecraft/class_4587$class_4665"; // PoseStack.Pose
    static final String VERTEX_CONSUMER = "net/minecraft/class_4588";

    /** The injected synthetic class's internal name. */
    static final String SELF = "com/retromod/generated/LegacyModelPart";

    /** ModelPart's stable 1.17+ constructor: (List<Cube>, Map<String,ModelPart>). */
    private static final String MODEL_PART_CTOR_DESC = "(Ljava/util/List;Ljava/util/Map;)V";

    private static final String L_MP = "L" + MODEL_PART + ";";
    private static final String L_MODEL = "L" + MODEL + ";";
    private static final String L_POSE = "L" + POSE_STACK + ";";
    private static final String L_POSE_INNER = "L" + POSE_INNER + ";";
    private static final String L_VC = "L" + VERTEX_CONSUMER + ";";

    // ModelPart's public mutable transform fields (intermediary IDs from the probe).
    private static final String F_X_ROT = "field_3654";
    private static final String F_Y_ROT = "field_3674";
    private static final String F_Z_ROT = "field_3675";

    // PoseStack / VertexConsumer method IDs (from the layer-2 render-API probe).
    private static final String M_PUSH      = "method_22903"; // pushPose()V
    private static final String M_POP       = "method_22909"; // popPose()V
    private static final String M_TRANSLATE = "method_46416"; // translate(FFF)V
    private static final String M_MUL_POSE  = "method_22907"; // mulPose(Quaternionfc)V
    private static final String M_LAST      = "method_23760"; // last()Pose
    private static final String M_RENDER_INT_COLOR = "method_22699"; // render(Pose, VC, int light, int overlay, int color)V
    private static final String M_ADD_VERTEX_POSE = "method_56824"; // addVertex(Pose, FFF)VC
    private static final String M_SET_COLOR_ARGB = "method_39415"; // setColor(int argb)VC
    private static final String M_SET_UV      = "method_22913"; // setUv(FF)VC
    private static final String M_SET_OVERLAY = "method_60803"; // setOverlay(I)VC
    private static final String M_SET_LIGHT   = "method_22922"; // setLight(I)VC
    private static final String M_SET_NORMAL  = "method_22914"; // setNormal(FFF)VC

    /**
     * Concrete vanilla model bases the mod's models extend, with the OLD (1.16-era)
     * {@code super()} ctor descriptor each one is called with. Each base's modern host
     * ctor is the uniform {@code (ModelPart)} (confirmed by the probe). Row format:
     * {@code {baseInternalName, oldSuperDesc...}}.
     */
    private static final String[][] CONCRETE_BASES = {
        {"net/minecraft/class_560", "()V"},          // CowModel
        {"net/minecraft/class_583", "()V"},          // quadruped-family base
        {"net/minecraft/class_597", "(IFZFFFFI)V"},  // quadruped-family base
        {"net/minecraft/class_601", "()V"},          // SheepModel
        {"net/minecraft/class_620", "(FII)V"},
        {"net/minecraft/class_623", "(FFII)V"},      // ZombieModel
    };

    /**
     * Register the bridge: inject the synthetic and rewrite the old construction
     * call sites to it. Call only on a pre-26.1 Fabric host.
     */
    public static void register(RetromodTransformer transformer) {
        transformer.registerSyntheticClass(SELF, generateLegacyModelPart());

        // ── Constructors: new class_630(...) → LegacyModelPart.create(...) ──
        transformer.registerConstructorRedirect(MODEL_PART, "(" + L_MODEL + "II)V",
                SELF, "create", "(" + L_MODEL + "II)" + L_MP);
        transformer.registerConstructorRedirect(MODEL_PART, "(" + L_MODEL + ")V",
                SELF, "create", "(" + L_MODEL + ")" + L_MP);

        // ── Build/transform/render call sites → static targets on the synthetic ──
        // addBox family
        devirtual(transformer, "method_2856", "(FFFFFFF)V",  "addBox",        "(" + L_MP + "FFFFFFF)V");
        devirtual(transformer, "method_2849", "(FFFFFFFZ)V", "addBoxMirror",  "(" + L_MP + "FFFFFFFZ)V");
        devirtual(transformer, "method_2844", "(FFFFFF)" + L_MP,  "addBoxR",       "(" + L_MP + "FFFFFF)" + L_MP);
        devirtual(transformer, "method_2854", "(FFFFFFZ)" + L_MP, "addBoxRMirror", "(" + L_MP + "FFFFFFZ)" + L_MP);
        // texture offset / size (chainable)
        devirtual(transformer, "method_2850", "(II)" + L_MP, "texOffs",    "(" + L_MP + "II)" + L_MP);
        devirtual(transformer, "method_2853", "(II)" + L_MP, "setTexSize", "(" + L_MP + "II)" + L_MP);
        // transform setters / child wiring
        devirtual(transformer, "method_2851", "(FFF)V",       "setPos",   "(" + L_MP + "FFF)V");
        devirtual(transformer, "method_2845", "(" + L_MP + ")V", "addChild", "(" + L_MP + L_MP + ")V");
        // old RGBA-float render (pre-1.21.2 signature) — delegates to the int-color override
        devirtual(transformer, "method_22699",
                "(" + L_POSE + L_VC + "IIFFFF)V",
                "render",
                "(" + L_MP + L_POSE + L_VC + "IIFFFF)V");

        // ── Layer 3: base-model super() bridges (concrete vanilla bases) ──
        for (String[] spec : CONCRETE_BASES) {
            String base = spec[0];
            String[] oldDescs = new String[spec.length - 1];
            System.arraycopy(spec, 1, oldDescs, 0, oldDescs.length);
            String legacy = "com/retromod/generated/LegacyModelBase_"
                    + base.substring(base.lastIndexOf('_') + 1);
            transformer.registerSyntheticClass(legacy, generateLegacyBase(legacy, base, oldDescs));
            transformer.registerClassRedirect(base, legacy);
        }

        probeHostModelApi();
    }

    private static void devirtual(RetromodTransformer t, String oldName, String oldDesc,
                                  String newName, String newDesc) {
        t.registerMethodRedirect(MODEL_PART, oldName, oldDesc, SELF, newName, newDesc, true);
    }

    // ── Layer-3 host-API probe (diagnostic) ─────────────────────────────────
    //
    // ── PROBE RESULTS — host MC 1.21.11 (captured from a live run) ──────────
    // class_630 (ModelPart):
    //   <init>(Ljava/util/List;Ljava/util/Map;)V          ← layer-1 super() call
    //   getChild  = method_32086(Ljava/lang/String;)Lclass_630;
    //   hasChild  = method_41919(Ljava/lang/String;)Z
    //   render    = method_22699(Lclass_4587;Lclass_4588;III)V    ← layer-2 override target
    // class_3879 (Model): <init>(Lclass_630;Ljava/util/function/Function;)V
    // Concrete bases: <init>(Lclass_630;)V uniformly (class_560/597/601/620/623); class_583 also has (Lclass_630;Function)V.
    // ABSENT on 1.21.11: class_4592/4593/4595 (abstract bases — need synthetic reimpls).
    // Render API (layer 2):
    //   PoseStack class_4587: push=method_22903, pop=method_22909, translate(FFF)=method_46416,
    //     scale(FFF)=method_22905, mulPose(Quaternionfc)=method_22907, last()=method_23760 -> Lclass_4587$class_4665;
    //   VertexConsumer class_4588 (chainable -> class_4588):
    //     addVertex(Pose,FFF)=method_56824, setColor(argb I)=method_39415,
    //     setUv(FF)=method_22913, setOverlay(I)=method_60803, setLight(I)=method_22922,
    //     setNormal(FFF)=method_22914
    // ─────────────────────────────────────────────────────────────────────────

    /** Vanilla model classes seen as `super()` targets in real pre-1.17 mods. */
    private static final String[] PROBE_CLASSES = {
        MODEL_PART,
        MODEL,
        "net/minecraft/class_4592",
        "net/minecraft/class_4593",
        "net/minecraft/class_4595",
        "net/minecraft/class_560",
        "net/minecraft/class_583",
        "net/minecraft/class_597",
        "net/minecraft/class_601",
        "net/minecraft/class_620",
        "net/minecraft/class_623",
    };

    private static void probeHostModelApi() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = Pre1_17ModelBridge.class.getClassLoader();
        LOGGER.info("[Retromod] pre-1.17 model bridge — host model-API probe (layer-3 planning):");
        for (String internal : PROBE_CLASSES) {
            try {
                Class<?> c = Class.forName(internal.replace('/', '.'), false, cl);
                StringBuilder sb = new StringBuilder();
                for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                    sb.append("\n      <init>").append(descriptorOf(ctor.getParameterTypes(), void.class));
                }
                LOGGER.info("    {} ({}){}", internal, c.getName(),
                        sb.length() == 0 ? " — no declared ctors" : sb);
                if (internal.equals(MODEL_PART)) {
                    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        Class<?> r = m.getReturnType();
                        boolean returnsSelf = r.getName().equals(c.getName());
                        boolean takesString = p.length == 1 && p[0] == String.class;
                        boolean rendersLike = p.length >= 2 && p[0].getName().contains("class_4587");
                        if (returnsSelf || takesString || rendersLike) {
                            LOGGER.info("      method {}{}", m.getName(),
                                    descriptorOf(p, r));
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.info("    {} — NOT PRESENT ({})", internal, t.getClass().getSimpleName());
            }
        }
        for (String internal : new String[]{VERTEX_CONSUMER, POSE_STACK, "net/minecraft/class_4597"}) {
            try {
                Class<?> c = Class.forName(internal.replace('/', '.'), false, cl);
                StringBuilder sb = new StringBuilder();
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                        sb.append("\n      ").append(m.getName())
                          .append(descriptorOf(m.getParameterTypes(), m.getReturnType()));
                    }
                }
                LOGGER.info("    {} ({}){}", internal, c.getName(),
                        sb.length() == 0 ? " — no public methods" : sb);
            } catch (Throwable t) {
                LOGGER.info("    {} — NOT PRESENT ({})", internal, t.getClass().getSimpleName());
            }
        }
        // InteractionResult restructure probe — `class_1269.field_5811` (= old enum constant
        // PASS) is gone on 1.21.11, breaking any pre-1.17 mod that returns InteractionResult
        // values from interaction handlers (AutoConfig hit it on Earth2Java). Dump the
        // current shape — superclass/interfaces + every public field/method — so the bridge
        // can be built against the real form (enum → sealed/interface? PASS → static field
        // on a nested class? a factory method?).
        try {
            Class<?> c = Class.forName("net.minecraft.class_1269", false, cl);
            LOGGER.info("    net/minecraft/class_1269 ({}) — InteractionResult shape probe:",
                    c.getName());
            LOGGER.info("      isEnum={} isInterface={} isSealed={} superclass={}",
                    c.isEnum(), c.isInterface(), c.isSealed(),
                    c.getSuperclass() == null ? "none" : c.getSuperclass().getName());
            Class<?>[] ifaces = c.getInterfaces();
            if (ifaces.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (Class<?> i : ifaces) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(i.getName());
                }
                LOGGER.info("      interfaces: {}", sb);
            }
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isPublic(f.getModifiers())) {
                    LOGGER.info("      field {} {} {}",
                            java.lang.reflect.Modifier.toString(f.getModifiers()),
                            f.getName(), typeDesc(f.getType()));
                }
            }
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                    LOGGER.info("      method {} {}{}",
                            java.lang.reflect.Modifier.toString(m.getModifiers()),
                            m.getName(),
                            descriptorOf(m.getParameterTypes(), m.getReturnType()));
                }
            }
            // Nested classes — if PASS moved to a nested sealed type, list them.
            Class<?>[] nested = c.getDeclaredClasses();
            if (nested.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (Class<?> n : nested) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(n.getName());
                }
                LOGGER.info("      nested classes: {}", sb);
            }
        } catch (Throwable t) {
            LOGGER.info("    net/minecraft/class_1269 — NOT PRESENT ({})",
                    t.getClass().getSimpleName());
        }
        LOGGER.info("[Retromod] (end model-API probe)");
    }

    private static String descriptorOf(Class<?>[] params, Class<?> ret) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) sb.append(typeDesc(p));
        sb.append(')').append(typeDesc(ret));
        return sb.toString();
    }

    private static String typeDesc(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + typeDesc(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    // ── Synthetic class generation ──────────────────────────────────────────

    /**
     * Generate {@code com/retromod/generated/LegacyModelPart extends class_630}.
     *
     * <p>The render override has loops so we use {@code COMPUTE_FRAMES}; with a
     * {@code getCommonSuperClass} override that returns {@code Object} for any class we
     * can't resolve, the generator still runs without Minecraft on the classpath (so
     * unit tests work). In-game where {@code class_630} exists, the standard resolution
     * is used.
     */
    static byte[] generateLegacyModelPart() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                SELF, null, MODEL_PART, null);

        // ── Instance fields owning the model's mutable state ──
        cw.visitField(Opcodes.ACC_PUBLIC, "boxes",       "Ljava/util/List;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "children",    "Ljava/util/List;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "currentTexU", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "currentTexV", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "texW",        "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "texH",        "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "posX",        "F", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "posY",        "F", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "posZ",        "F", null, null).visitEnd();

        // Constructors — super(empty,empty) + init our own fields.
        emitBridgingCtor(cw, "(" + L_MODEL + "II)V");
        emitBridgingCtor(cw, "(" + L_MODEL + ")V");
        emitBridgingCtor(cw, "()V");

        // Shared forgiving EMPTY_ROOT (used by layer-3 base bridges).
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "EMPTY_ROOT", L_MP, null, null).visitEnd();
        emitEmptyRootClinit(cw);
        emitForgivingGetChild(cw);
        emitForgivingHasChild(cw);

        // Static factories the constructor redirects target.
        emitFactory(cw, "(" + L_MODEL + "II)" + L_MP, "(" + L_MODEL + "II)V");
        emitFactory(cw, "(" + L_MODEL + ")" + L_MP, "(" + L_MODEL + ")V");

        // ── Recorders (layer 2): de-virtualized statics that capture cube + transform state ──
        emitAddBox(cw, "addBox",        "(" + L_MP + "FFFFFFF)V",   /*numFloats*/7, /*hasMirror*/false, /*returnsSelf*/false);
        emitAddBox(cw, "addBoxMirror",  "(" + L_MP + "FFFFFFFZ)V",  7, true,  false);
        emitAddBox(cw, "addBoxR",       "(" + L_MP + "FFFFFF)" + L_MP,  6, false, true);
        emitAddBox(cw, "addBoxRMirror", "(" + L_MP + "FFFFFFZ)" + L_MP, 6, true,  true);
        emitTwoIntFieldsRetSelf(cw, "texOffs",    "currentTexU", "currentTexV");
        emitTwoIntFieldsRetSelf(cw, "setTexSize", "texW",        "texH");
        emitSetPos(cw);
        emitAddChild(cw);

        // Old RGBA-float render bridge → delegates to the int-color instance override.
        emitStaticRenderBridge(cw);

        // Instance render override (method_22699 int-color) — draws recorded cubes.
        emitInstanceRender(cw);

        // Per-cube + per-vertex helpers used by the instance render.
        emitDrawBox(cw);
        emitEmitVertex(cw);

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * {@code <init>(args...) { super(emptyList, emptyMap); boxes = new ArrayList; children = new ArrayList;
     *  texW = 64; texH = 32; }}
     */
    private static void emitBridgingCtor(ClassWriter cw, String desc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        mv.visitCode();
        // super(new ArrayList(), new HashMap())
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, MODEL_PART, "<init>", MODEL_PART_CTOR_DESC, false);
        // this.boxes = new ArrayList()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, "boxes", "Ljava/util/List;");
        // this.children = new ArrayList()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, "children", "Ljava/util/List;");
        // this.texW = 64; this.texH = 32;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitIntInsn(Opcodes.BIPUSH, 64);
        mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, "texW", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitIntInsn(Opcodes.BIPUSH, 32);
        mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, "texH", "I");
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitFactory(ClassWriter cw, String factoryDesc, String ctorDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "create", factoryDesc, null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, SELF);
        mv.visitInsn(Opcodes.DUP);
        org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(factoryDesc);
        int slot = 0;
        for (org.objectweb.asm.Type a : args) {
            mv.visitVarInsn(a.getOpcode(Opcodes.ILOAD), slot);
            slot += a.getSize();
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, SELF, "<init>", ctorDesc, false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void emitEmptyRootClinit(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, SELF);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, SELF, "<init>", "()V", false);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, SELF, "EMPTY_ROOT", L_MP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** {@code public class_630 getChild(String) { return new LegacyModelPart(); }} */
    private static void emitForgivingGetChild(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "method_32086",
                "(Ljava/lang/String;)" + L_MP, null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, SELF);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, SELF, "<init>", "()V", false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** {@code public boolean hasChild(String) { return true; }} */
    private static void emitForgivingHasChild(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "method_41919",
                "(Ljava/lang/String;)Z", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    // ── Layer-2 recorders ───────────────────────────────────────────────────

    /**
     * Emit an addBox-family recorder. Appends a 10-float spec {@code [x,y,z,w,h,d,delta,
     * mirror, texU, texV]} to {@code self.boxes}, using the current {@code texU/texV} from
     * the receiver.
     *
     * @param numFloats 6 (no delta) or 7 (with delta).
     * @param hasMirror whether an extra {@code Z} arg trails (true → mirror).
     * @param returnsSelf whether the method returns the receiver (chained variants).
     */
    private static void emitAddBox(ClassWriter cw, String name, String desc,
                                   int numFloats, boolean hasMirror, boolean returnsSelf) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, desc, null, null);
        mv.visitCode();
        // float[] arr = new float[10]
        mv.visitIntInsn(Opcodes.BIPUSH, 10);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
        // arr[0..5] = x,y,z,w,h,d
        for (int i = 0; i < 6; i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            mv.visitVarInsn(Opcodes.FLOAD, 1 + i);
            mv.visitInsn(Opcodes.FASTORE);
        }
        // arr[6] = delta (or 0)
        mv.visitInsn(Opcodes.DUP);
        pushInt(mv, 6);
        if (numFloats == 7) mv.visitVarInsn(Opcodes.FLOAD, 7);
        else mv.visitInsn(Opcodes.FCONST_0);
        mv.visitInsn(Opcodes.FASTORE);
        // arr[7] = mirror (as float, 0 or 1)
        mv.visitInsn(Opcodes.DUP);
        pushInt(mv, 7);
        if (hasMirror) {
            mv.visitVarInsn(Opcodes.ILOAD, 1 + numFloats);
            mv.visitInsn(Opcodes.I2F);
        } else {
            mv.visitInsn(Opcodes.FCONST_0);
        }
        mv.visitInsn(Opcodes.FASTORE);
        // arr[8] = currentTexU (as float)
        mv.visitInsn(Opcodes.DUP);
        pushInt(mv, 8);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "currentTexU", "I");
        mv.visitInsn(Opcodes.I2F);
        mv.visitInsn(Opcodes.FASTORE);
        // arr[9] = currentTexV (as float)
        mv.visitInsn(Opcodes.DUP);
        pushInt(mv, 9);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "currentTexV", "I");
        mv.visitInsn(Opcodes.I2F);
        mv.visitInsn(Opcodes.FASTORE);
        // self.boxes.add(arr)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "boxes", "Ljava/util/List;");
        mv.visitInsn(Opcodes.SWAP);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add",
                "(Ljava/lang/Object;)Z", true);
        mv.visitInsn(Opcodes.POP);
        if (returnsSelf) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitInsn(Opcodes.RETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** Two-int setters that return the receiver: {@code self.A = arg1; self.B = arg2; return self;}. */
    private static void emitTwoIntFieldsRetSelf(ClassWriter cw, String name, String fieldA, String fieldB) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, "(" + L_MP + "II)" + L_MP, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, fieldA, "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, fieldB, "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** {@code setPos(self, x, y, z) { self.posX = x; self.posY = y; self.posZ = z; }} */
    private static void emitSetPos(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "setPos", "(" + L_MP + "FFF)V", null, null);
        mv.visitCode();
        String[] f = {"posX", "posY", "posZ"};
        for (int i = 0; i < 3; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
            mv.visitVarInsn(Opcodes.FLOAD, 1 + i);
            mv.visitFieldInsn(Opcodes.PUTFIELD, SELF, f[i], "F");
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** {@code addChild(self, child) { self.children.add(child); }} */
    private static void emitAddChild(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "addChild", "(" + L_MP + L_MP + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.CHECKCAST, SELF);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "children", "Ljava/util/List;");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "add",
                "(Ljava/lang/Object;)Z", true);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Old RGBA-float render call sites → delegate to the int-color instance override
     * with a neutral white color (most legacy callers pass 1,1,1,1; an exact RGBA-to-int
     * conversion is unnecessary for the common case and avoids extra bytecode here).
     */
    private static void emitStaticRenderBridge(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "render", "(" + L_MP + L_POSE + L_VC + "IIFFFF)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0); // self (class_630)
        mv.visitVarInsn(Opcodes.ALOAD, 1); // pose
        mv.visitVarInsn(Opcodes.ALOAD, 2); // vc
        mv.visitVarInsn(Opcodes.ILOAD, 3); // light
        mv.visitVarInsn(Opcodes.ILOAD, 4); // overlay
        mv.visitLdcInsn(0xFFFFFFFF);       // white ARGB
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MODEL_PART, M_RENDER_INT_COLOR,
                "(" + L_POSE + L_VC + "III)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Instance override of {@code class_630.method_22699(class_4587, class_4588, int, int, int)V}
     * (the 1.21.2+ int-color render). Pushes the pose, applies translate + ZYX rotation
     * from this part's pos and inherited rotation fields, iterates {@code boxes} calling
     * {@code drawBox}, iterates {@code children} recursing, then pops.
     */
    private static void emitInstanceRender(ClassWriter cw) {
        // Local layout (instance method, slot 0 = this):
        //   0 this, 1 pose, 2 vc, 3 light, 4 overlay, 5 color,
        //   6 innerPose (Pose), 7 boxesList, 8 i (boxes loop), 9 childrenList, 10 j (children loop)
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, M_RENDER_INT_COLOR,
                "(" + L_POSE + L_VC + "III)V", null, null);
        mv.visitCode();

        // pose.pushPose()
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, POSE_STACK, M_PUSH, "()V", false);

        // pose.translate(posX/16, posY/16, posZ/16)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        for (String f : new String[]{"posX", "posY", "posZ"}) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, SELF, f, "F");
            mv.visitLdcInsn(16.0F);
            mv.visitInsn(Opcodes.FDIV);
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, POSE_STACK, M_TRANSLATE, "(FFF)V", false);

        // pose.mulPose(new Quaternionf().rotationZYX(zRot, yRot, xRot))
        // (rotation fields are inherited from real class_630; the mod sets them via PUTFIELD.)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.NEW, "org/joml/Quaternionf");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/joml/Quaternionf", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, MODEL_PART, F_Z_ROT, "F");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, MODEL_PART, F_Y_ROT, "F");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, MODEL_PART, F_X_ROT, "F");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/joml/Quaternionf", "rotationZYX",
                "(FFF)Lorg/joml/Quaternionf;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, POSE_STACK, M_MUL_POSE,
                "(Lorg/joml/Quaternionfc;)V", false);

        // innerPose = pose.last()
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, POSE_STACK, M_LAST, "()" + L_POSE_INNER, false);
        mv.visitVarInsn(Opcodes.ASTORE, 6);

        // Loop over boxes — for (i=0; i<boxes.size(); i++) drawBox(innerPose, vc, boxes.get(i), texW, texH, color, light, overlay)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "boxes", "Ljava/util/List;");
        mv.visitVarInsn(Opcodes.ASTORE, 7);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 8);
        Label boxLoopStart = new Label(), boxLoopEnd = new Label();
        mv.visitLabel(boxLoopStart);
        mv.visitVarInsn(Opcodes.ILOAD, 8);
        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, boxLoopEnd);
        // drawBox(innerPose, vc, (float[]) boxes.get(i), this.texW, this.texH, color, light, overlay)
        mv.visitVarInsn(Opcodes.ALOAD, 6);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 7);
        mv.visitVarInsn(Opcodes.ILOAD, 8);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get",
                "(I)Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[F");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "texW", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "texH", "I");
        mv.visitVarInsn(Opcodes.ILOAD, 5); // color
        mv.visitVarInsn(Opcodes.ILOAD, 3); // light
        mv.visitVarInsn(Opcodes.ILOAD, 4); // overlay
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, "drawBox",
                "(" + L_POSE_INNER + L_VC + "[FIIIII)V", false);
        mv.visitIincInsn(8, 1);
        mv.visitJumpInsn(Opcodes.GOTO, boxLoopStart);
        mv.visitLabel(boxLoopEnd);

        // Loop over children — for (j=0; j<children.size(); j++) ((class_630)children.get(j)).method_22699(pose, vc, light, overlay, color)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, SELF, "children", "Ljava/util/List;");
        mv.visitVarInsn(Opcodes.ASTORE, 9);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 10);
        Label childLoopStart = new Label(), childLoopEnd = new Label();
        mv.visitLabel(childLoopStart);
        mv.visitVarInsn(Opcodes.ILOAD, 10);
        mv.visitVarInsn(Opcodes.ALOAD, 9);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, childLoopEnd);
        mv.visitVarInsn(Opcodes.ALOAD, 9);
        mv.visitVarInsn(Opcodes.ILOAD, 10);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get",
                "(I)Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, MODEL_PART);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitVarInsn(Opcodes.ILOAD, 4);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MODEL_PART, M_RENDER_INT_COLOR,
                "(" + L_POSE + L_VC + "III)V", false);
        mv.visitIincInsn(10, 1);
        mv.visitJumpInsn(Opcodes.GOTO, childLoopStart);
        mv.visitLabel(childLoopEnd);

        // pose.popPose()
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, POSE_STACK, M_POP, "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * {@code static drawBox(innerPose, vc, box[10], texW, texH, color, light, overlay)} —
     * emit 6 face × 4 vertex calls for a single cube. The 8 corner positions are
     * pre-computed into locals so each face just reloads the 4 corners it needs.
     *
     * <p>UV mapping uses placeholder {@code (0,0)–(1,1)} per face for round 1 — positions
     * + normals are correct so cubes are shaped and lit right; textures will look wrong
     * until the proper old-ModelRenderer T-layout is encoded in a follow-up.
     *
     * <p>Args: {@code (Pose pose, VertexConsumer vc, float[] box, int texW, int texH,
     *          int color, int light, int overlay)}.
     */
    private static void emitDrawBox(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "drawBox",
                "(" + L_POSE_INNER + L_VC + "[FIIIII)V", null, null);
        mv.visitCode();
        // Load box[6] = delta into local 8
        mv.visitVarInsn(Opcodes.ALOAD, 2); pushInt(mv, 6); mv.visitInsn(Opcodes.FALOAD);
        mv.visitVarInsn(Opcodes.FSTORE, 8);
        // x0 = (box[0] - delta) / 16  → local 9
        loadCornerLow(mv, 0, 8); mv.visitVarInsn(Opcodes.FSTORE, 9);
        // x1 = (box[0] + box[3] + delta) / 16  → local 10
        loadCornerHigh(mv, 0, 3, 8); mv.visitVarInsn(Opcodes.FSTORE, 10);
        // y0 = (box[1] - delta) / 16  → local 11
        loadCornerLow(mv, 1, 8); mv.visitVarInsn(Opcodes.FSTORE, 11);
        // y1 = (box[1] + box[4] + delta) / 16 → local 12
        loadCornerHigh(mv, 1, 4, 8); mv.visitVarInsn(Opcodes.FSTORE, 12);
        // z0 = (box[2] - delta) / 16  → local 13
        loadCornerLow(mv, 2, 8); mv.visitVarInsn(Opcodes.FSTORE, 13);
        // z1 = (box[2] + box[5] + delta) / 16 → local 14
        loadCornerHigh(mv, 2, 5, 8); mv.visitVarInsn(Opcodes.FSTORE, 14);

        // Local indices: 9=x0, 10=x1, 11=y0, 12=y1, 13=z0, 14=z1.
        // Face order: DOWN(-Y), UP(+Y), NORTH(-Z), SOUTH(+Z), WEST(-X), EAST(+X).
        // Vertices CCW when viewed from along the face normal (from outside).
        // Round-1 UVs: 0/0, 1/0, 1/1, 0/1 cycling — placeholder.

        // DOWN (-Y, normal 0,-1,0): vertices (x0,y0,z0), (x0,y0,z1), (x1,y0,z1), (x1,y0,z0)
        emitFace(mv,  9,11,13,  9,11,14,  10,11,14,  10,11,13,   0f,-1f,0f);
        // UP   (+Y, normal 0,+1,0): (x0,y1,z1), (x0,y1,z0), (x1,y1,z0), (x1,y1,z1)
        emitFace(mv,  9,12,14,  9,12,13,  10,12,13,  10,12,14,   0f, 1f,0f);
        // NORTH (-Z, normal 0,0,-1): (x1,y0,z0), (x0,y0,z0), (x0,y1,z0), (x1,y1,z0)
        emitFace(mv, 10,11,13,  9,11,13,   9,12,13,  10,12,13,   0f, 0f,-1f);
        // SOUTH (+Z, normal 0,0,+1): (x0,y0,z1), (x1,y0,z1), (x1,y1,z1), (x0,y1,z1)
        emitFace(mv,  9,11,14, 10,11,14,  10,12,14,   9,12,14,   0f, 0f, 1f);
        // WEST  (-X, normal -1,0,0): (x0,y0,z1), (x0,y0,z0), (x0,y1,z0), (x0,y1,z1)
        emitFace(mv,  9,11,14,  9,11,13,   9,12,13,   9,12,14,  -1f, 0f,0f);
        // EAST  (+X, normal +1,0,0): (x1,y0,z0), (x1,y0,z1), (x1,y1,z1), (x1,y1,z0)
        emitFace(mv, 10,11,13, 10,11,14,  10,12,14,  10,12,13,   1f, 0f,0f);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** Push {@code (box[idx] - box[delta]) / 16f}. */
    private static void loadCornerLow(MethodVisitor mv, int idx, int deltaLocal) {
        mv.visitVarInsn(Opcodes.ALOAD, 2); pushInt(mv, idx); mv.visitInsn(Opcodes.FALOAD);
        mv.visitVarInsn(Opcodes.FLOAD, deltaLocal);
        mv.visitInsn(Opcodes.FSUB);
        mv.visitLdcInsn(16.0F);
        mv.visitInsn(Opcodes.FDIV);
    }

    /** Push {@code (box[idx] + box[lenIdx] + box[delta]) / 16f}. */
    private static void loadCornerHigh(MethodVisitor mv, int idx, int lenIdx, int deltaLocal) {
        mv.visitVarInsn(Opcodes.ALOAD, 2); pushInt(mv, idx); mv.visitInsn(Opcodes.FALOAD);
        mv.visitVarInsn(Opcodes.ALOAD, 2); pushInt(mv, lenIdx); mv.visitInsn(Opcodes.FALOAD);
        mv.visitInsn(Opcodes.FADD);
        mv.visitVarInsn(Opcodes.FLOAD, deltaLocal);
        mv.visitInsn(Opcodes.FADD);
        mv.visitLdcInsn(16.0F);
        mv.visitInsn(Opcodes.FDIV);
    }

    /**
     * Emit one face = 4 vertices (CCW from outside). Each vertex takes its (x,y,z)
     * position from the named locals; UVs are placeholder (0,0)/(1,0)/(1,1)/(0,1)
     * and the face normal is applied to all 4.
     */
    private static void emitFace(MethodVisitor mv,
            int ax, int ay, int az,
            int bx, int by, int bz,
            int cx, int cy, int cz,
            int dx, int dy, int dz,
            float nx, float ny, float nz) {
        emitVertexCall(mv, ax, ay, az, 0.0F, 0.0F, nx, ny, nz);
        emitVertexCall(mv, bx, by, bz, 1.0F, 0.0F, nx, ny, nz);
        emitVertexCall(mv, cx, cy, cz, 1.0F, 1.0F, nx, ny, nz);
        emitVertexCall(mv, dx, dy, dz, 0.0F, 1.0F, nx, ny, nz);
    }

    /** Emit an INVOKESTATIC emitVertex(pose, vc, x, y, z, color, u, v, light, overlay, nx, ny, nz). */
    private static void emitVertexCall(MethodVisitor mv,
            int xLocal, int yLocal, int zLocal,
            float u, float v,
            float nx, float ny, float nz) {
        mv.visitVarInsn(Opcodes.ALOAD, 0); // pose
        mv.visitVarInsn(Opcodes.ALOAD, 1); // vc
        mv.visitVarInsn(Opcodes.FLOAD, xLocal);
        mv.visitVarInsn(Opcodes.FLOAD, yLocal);
        mv.visitVarInsn(Opcodes.FLOAD, zLocal);
        mv.visitVarInsn(Opcodes.ILOAD, 5); // color
        mv.visitLdcInsn(u);
        mv.visitLdcInsn(v);
        mv.visitVarInsn(Opcodes.ILOAD, 6); // light
        mv.visitVarInsn(Opcodes.ILOAD, 7); // overlay
        mv.visitLdcInsn(nx);
        mv.visitLdcInsn(ny);
        mv.visitLdcInsn(nz);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, SELF, "emitVertex",
                "(" + L_POSE_INNER + L_VC + "FFFIFFIIFFF)V", false);
    }

    /**
     * {@code static emitVertex(pose, vc, x, y, z, color, u, v, light, overlay, nx, ny, nz)} —
     * the modern VertexConsumer chain: addVertex → setColor → setUv → setOverlay →
     * setLight → setNormal. The chain returns VertexConsumer at each step; the final
     * one is discarded with POP.
     */
    private static void emitEmitVertex(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "emitVertex",
                "(" + L_POSE_INNER + L_VC + "FFFIFFIIFFF)V", null, null);
        mv.visitCode();
        // vc.addVertex(pose, x, y, z)
        mv.visitVarInsn(Opcodes.ALOAD, 1); // vc
        mv.visitVarInsn(Opcodes.ALOAD, 0); // pose
        mv.visitVarInsn(Opcodes.FLOAD, 2); // x
        mv.visitVarInsn(Opcodes.FLOAD, 3); // y
        mv.visitVarInsn(Opcodes.FLOAD, 4); // z
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, VERTEX_CONSUMER, M_ADD_VERTEX_POSE,
                "(" + L_POSE_INNER + "FFF)" + L_VC, true);
        // .setColor(color)
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, VERTEX_CONSUMER, M_SET_COLOR_ARGB,
                "(I)" + L_VC, true);
        // .setUv(u, v)
        mv.visitVarInsn(Opcodes.FLOAD, 6);
        mv.visitVarInsn(Opcodes.FLOAD, 7);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, VERTEX_CONSUMER, M_SET_UV,
                "(FF)" + L_VC, true);
        // .setOverlay(overlay)
        mv.visitVarInsn(Opcodes.ILOAD, 9);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, VERTEX_CONSUMER, M_SET_OVERLAY,
                "(I)" + L_VC, true);
        // .setLight(light)
        mv.visitVarInsn(Opcodes.ILOAD, 8);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, VERTEX_CONSUMER, M_SET_LIGHT,
                "(I)" + L_VC, true);
        // .setNormal(nx, ny, nz)
        mv.visitVarInsn(Opcodes.FLOAD, 10);
        mv.visitVarInsn(Opcodes.FLOAD, 11);
        mv.visitVarInsn(Opcodes.FLOAD, 12);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, VERTEX_CONSUMER, M_SET_NORMAL,
                "(FFF)" + L_VC, true);
        mv.visitInsn(Opcodes.POP); // discard final chain return
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** Push a small non-negative int via the cheapest encoding (ICONST/BIPUSH/SIPUSH). */
    private static void pushInt(MethodVisitor mv, int v) {
        if (v >= -1 && v <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + v);
        } else if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, v);
        } else if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, v);
        } else {
            mv.visitLdcInsn(v);
        }
    }

    /**
     * Generate {@code Legacy<base> extends <base>} for a vanilla model base class whose
     * 1.16-era ctor(s) the mod's models call via {@code super()}. Each old ctor descriptor
     * gets a ctor that ignores its args and calls the host's modern {@code (ModelPart)} ctor
     * with the forgiving {@code EMPTY_ROOT}. A modern {@code (ModelPart)} ctor is also added
     * so a mod constructing the base the new way still works after the class redirect.
     */
    static byte[] generateLegacyBase(String legacyName, String baseInternal, String[] oldDescs) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                legacyName, null, baseInternal, null);

        final String modernDesc = "(" + L_MP + ")V";
        boolean hasModern = false;
        for (String oldDesc : oldDescs) {
            if (oldDesc.equals(modernDesc)) hasModern = true;
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", oldDesc, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETSTATIC, SELF, "EMPTY_ROOT", L_MP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, baseInternal, "<init>", modernDesc, false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        if (!hasModern) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", modernDesc, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, baseInternal, "<init>", modernDesc, false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }
}
