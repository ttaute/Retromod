/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * This is the core bytecode transformer that intercepts class loading
 * and rewrites method calls to maintain compatibility across versions.
 */
package com.retromod.core;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core ASM bytecode transformer that rewrites class/method/field references at load time.
 *
 * <h2>What it does</h2>
 * When old mods reference Minecraft classes, methods, or fields that have been renamed,
 * moved, or removed in newer versions, this transformer rewrites that bytecode so the
 * references point to the correct modern targets. It handles:
 * <ol>
 *   <li><b>Method renames</b> (e.g., {@code getWorld} -> {@code getEntityWorld})</li>
 *   <li><b>Method removals</b> (redirect to embedded shim bridge methods)</li>
 *   <li><b>Class relocations</b> (package changes between MC versions)</li>
 *   <li><b>Signature changes</b> (parameter/return type modifications)</li>
 *   <li><b>Constructor-to-factory</b> ({@code new Foo(args)} -> {@code Foo.create(args)})</li>
 *   <li><b>Field accessor wrapping</b> (public field -> getter/setter method)</li>
 *   <li><b>Intermediary name remapping</b> ({@code method_XXXX} -> Mojang official names)</li>
 * </ol>
 *
 * <h2>ASM visitor chain</h2>
 * The transformation pipeline uses a chain of ASM visitors:
 * <pre>
 *   ClassReader
 *     -> ClassRemapper (handles class renames + intermediary->Mojang name mapping)
 *       -> RetromodClassVisitor (handles method/field/constructor redirects, superclass rewrites)
 *         -> ClassWriter (outputs the final bytecode)
 * </pre>
 * The ClassRemapper runs FIRST so that by the time RetromodClassVisitor sees method calls,
 * all class names are already in their Mojang-official form. This is why classRedirects feed
 * into the Remapper (bulk class rename) while methodRedirects are checked manually in
 * RetromodClassVisitor (they need owner+name+descriptor matching, not just name mapping).
 *
 * <h2>Thread safety</h2>
 * All redirect maps use {@link ConcurrentHashMap} because shims register redirects from
 * ServiceLoader threads while the transformer may already be processing classes.
 *
 * <p><b>IMPORTANT:</b> This class must NOT reference {@code Retromod} directly (which
 * implements ModInitializer) because the transformer is also used by the standalone CLI
 * where Fabric classes are not on the classpath.</p>
 */
public class RetromodTransformer implements ClassFileTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Transformer");
    private static final RetromodTransformer INSTANCE = new RetromodTransformer();
    
    // ═══════════════════════════════════════════════════════════════════════
    // REDIRECT MAPS - populated by version shims and polyfill providers
    // These are checked during bytecode transformation to rewrite references.
    // All use ConcurrentHashMap for thread-safe registration during class loading.
    // ═══════════════════════════════════════════════════════════════════════

    // Method redirects: when bytecode calls oldOwner.oldName(oldDesc),
    // rewrite to newOwner.newName(newDesc). Checked manually in visitMethodInsn
    // because matching requires owner+name+descriptor (not just name).
    private final Map<MethodKey, MethodTarget> methodRedirects = new ConcurrentHashMap<>(256);

    // Removed-method NEUTRALIZATION (Tier 2 render-state soft-fail). A call to a
    // method that no longer exists on the host is rewritten in place to "discard
    // the args (and receiver), push a default return" instead of being left to
    // crash with NoSuchMethodError. The canonical case: the imperative
    // RenderSystem state setters (enableBlend/blendFunc/depthMask/…) deleted in
    // the 26.x GpuDevice/RenderPipeline refactor - there is no surviving method
    // to redirect *to* (state is declared on immutable pipeline objects now), so
    // the only non-crashing option is to make the dead call inert. The mod LOADS
    // and runs; the neutralized state-call has no effect (soft-fail, same
    // boundary VulkanMod hits). Keyed on exact owner+name+desc so only the dead
    // overload is touched - a live method that happens to share an owner is never
    // neutralized. Owners tracked separately for an O(1) fast-path skip.
    private final Set<MethodKey> neutralizedMethods = ConcurrentHashMap.newKeySet();
    private final Set<String> neutralizedMethodOwners = ConcurrentHashMap.newKeySet();

    // Class redirects: oldClassName -> newClassName (JVM internal names with /).
    // Fed into ASM's ClassRemapper for bulk renaming - this handles class references
    // everywhere in bytecode (type descriptors, signatures, annotations, etc.)
    // without needing to manually visit each location.
    private final Map<String, String> classRedirects = new ConcurrentHashMap<>(64);

    // Field redirects: oldOwner.oldName -> newOwner.newName.
    // Can also redirect a field access to a static method call (field-to-method)
    // when newDesc starts with "(" - used when a field is removed and replaced
    // with a method in newer MC versions.
    private final Map<FieldKey, FieldTarget> fieldRedirects = new ConcurrentHashMap<>(64);

    // Intermediary name mappings: method_XXXX/field_XXXX -> Mojang official names.
    // MC 26.1 removed all obfuscation, so Fabric mods using intermediary names
    // (e.g., method_1234) must be remapped to plain Mojang names (e.g., tick).
    // These are applied by the ClassRemapper's mapMethodName/mapFieldName overrides.
    private final Map<String, String> intermediaryMethodNames = new ConcurrentHashMap<>(40000);
    private final Map<String, String> intermediaryFieldNames = new ConcurrentHashMap<>(40000);

    // SRG name mappings: m_NNNNNN_ / f_NNNNN_ -> Mojang official names.
    // Forge mods compiled with ForgeGradle's reobfJar (pre-1.20.5 or any
    // version where the build still does the SRG conversion) reference
    // members by SRG names like Blocks.f_50069_ instead of Blocks.STONE.
    // Forge 64.x for MC 26.1+ dropped its SRG → Mojang runtime remap layer
    // entirely (since 26.1 has no obfuscation), so those references now
    // hit NoSuchFieldError / NoSuchMethodError on every reobf'd Forge mod.
    // Retromod takes over that remap responsibility via these maps.
    //
    // Same global-key shape as the intermediary maps: SRG names are unique
    // strings independent of the owning class, so a flat name -> Mojang
    // dictionary is sufficient.
    private final Map<String, String> srgMethodNames = new ConcurrentHashMap<>(8000);
    private final Map<String, String> srgFieldNames = new ConcurrentHashMap<>(8000);

    // Vanilla Mojang->Mojang method renames, keyed "owner#name" -> newName. For 26.x
    // method renames on a kept class (e.g. ResourceKey.location -> identifier). Keyed by
    // owner+name (NOT a global dictionary like the intermediary/SRG maps - the names are
    // ordinary words, so the rename must be scoped to the one owner). Applied via the
    // ClassRemapper's mapMethodName, so it rewrites BOTH direct calls AND method
    // references (e.g. ResourceKey::location in a codec), which the manual methodRedirects
    // pass can't reach. Populated only for 26.1+ targets, where the old name is gone.
    private final Map<String, String> mojangMethodRenames = new ConcurrentHashMap<>(16);

    // Superclass redirects: for class-to-interface migrations.
    // When a class becomes an interface in newer MC (e.g., Explosion), mods that
    // extend it need their superclass changed to a bridge class + the interface added.
    private final Map<String, SuperclassRedirect> superclassRedirects = new ConcurrentHashMap<>(16);

    // Constructor-to-factory redirects: converts `new Foo(args)` to `Foo.factory(args)`.
    // Used when constructors are removed and replaced with static factory methods
    // (e.g., new ResourceLocation(s) -> Identifier.parse(s) in 26.1).
    private final Map<ConstructorKey, FactoryTarget> constructorRedirects = new ConcurrentHashMap<>(16);

    // Field accessor redirects: GETFIELD -> getter(), PUTFIELD -> setter().
    // For fields that became private in newer MC but have getter/setter methods.
    private final Map<FieldKey, FieldAccessorTarget> fieldAccessorRedirects = new ConcurrentHashMap<>(16);
    /** GETSTATIC of a removed static field -> a (collection-field + optional enum-arg + accessor-call + cast) sequence. */
    private final Map<FieldKey, StaticFieldAccessor> staticFieldAccessors = new ConcurrentHashMap<>(64);

    // Super constructor descriptor changes: when a parent class constructor gains
    // new required parameters in newer MC. Pushes extra args before INVOKESPECIAL.
    // Example: Button gained a CreateNarration parameter in newer versions.
    private final Map<ConstructorKey, SuperCtorRedirect> superCtorRedirects = new ConcurrentHashMap<>(8);

    // ═══════════════════════════════════════════════════════════════════════
    // TRANSFORMATION CONTROL - determines what gets transformed
    // ═══════════════════════════════════════════════════════════════════════

    // Only classes in these packages get transformed (mod code, not MC itself)
    private final Set<String> transformablePackages = ConcurrentHashMap.newKeySet();

    // Shim classes that are embedded into mod JARs during transformation
    private final Set<String> embeddedShimClasses = ConcurrentHashMap.newKeySet();

    // Synthetic classes generated via ASM bytecode generation (for polyfills
    // that need MC-typed fields/methods which can't be compiled from Java source
    // since MC isn't on the compile classpath)
    private final Map<String, byte[]> syntheticClasses = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // FUZZY RESOLVER - last-resort fallback for unresolved references
    // ═══════════════════════════════════════════════════════════════════════

    // The fuzzy resolver indexes the target MC JAR and scores candidate methods/fields
    // when no hardcoded redirect is found. It will NEVER override a hardcoded redirect.
    // Initialized lazily via initFuzzyResolver() - volatile for thread-safe publication.
    private volatile FuzzyMethodResolver fuzzyResolver;

    // ═══════════════════════════════════════════════════════════════════════
    // PATTERN HEURISTICS - checked BEFORE fuzzy resolver for better performance
    // ═══════════════════════════════════════════════════════════════════════

    // Pattern-based heuristics for resolving method/class/field references that
    // are not in the shim redirect tables. These encode deterministic naming
    // convention shifts (e.g., render* -> extract* in 26.1) and are faster and
    // more reliable than fuzzy matching. Volatile for thread-safe publication.
    private volatile PatternHeuristics patternHeuristics;

    // ═══════════════════════════════════════════════════════════════════════
    // BRIDGE ADAPTER GENERATOR - handles method signature changes
    // ═══════════════════════════════════════════════════════════════════════

    // Generates bridge methods when MC changes a method's parameter types
    // (e.g., mouseClicked(double,double,int) -> mouseClicked(MouseButtonEvent,boolean)).
    // Instantiated per-class in the visitor pipeline, so no shared instance needed here.
    // This field provides a reference for AutoFixEngine to use static lookup methods.

    // ═══════════════════════════════════════════════════════════════════════
    // PERFORMANCE OPTIMIZATIONS
    // ═══════════════════════════════════════════════════════════════════════

    // Cache the ASM Remapper to avoid recreating it for every class.
    // Invalidated when classRedirects or intermediary mappings change.
    private volatile Remapper cachedRemapper;
    private final AtomicInteger classRedirectsVersion = new AtomicInteger(0);

    // Fast-path: set of class owners that have method redirects.
    // In visitMethodInsn, if the call's owner isn't in this set, we skip
    // the more expensive ConcurrentHashMap lookup on methodRedirects.
    private final Set<String> methodRedirectOwners = ConcurrentHashMap.newKeySet();

    // ═══════════════════════════════════════════════════════════════════════
    // ITERATIVE TRANSFORMATION LOOP
    // ═══════════════════════════════════════════════════════════════════════
    //
    // A single transform pass visits every instruction once. If pass N rewrites
    // X.foo() -> Y.bar(), the visitor has already moved past that instruction
    // and won't see that Y.bar() is itself registered for redirection to Z.baz().
    // We loop until the bytecode stabilizes (two consecutive passes produce
    // identical bytes) so chained redirects are all resolved.
    //
    // Cap prevents infinite loops from cyclic redirect chains (e.g., A -> B -> A).
    // Configurable via -Dretromod.transform.maxIterations=N (default 5).

    private static final int MAX_TRANSFORM_ITERATIONS =
        Integer.parseInt(System.getProperty("retromod.transform.maxIterations", "5"));

    /** Total transform passes across all classes. A class needing 3 passes adds 3. */
    private final AtomicInteger totalPassesPerformed = new AtomicInteger();

    /** Classes that stabilized only after 2+ passes (i.e., chained redirects fired). */
    private final AtomicInteger classesNeedingMultiplePasses = new AtomicInteger();

    /** Classes that hit MAX_TRANSFORM_ITERATIONS - possible redirect cycle. */
    private final AtomicInteger classesHittingIterationCap = new AtomicInteger();

    // ═══════════════════════════════════════════════════════════════════════
    // POST-TRANSFORM PIPELINE: REFLECTION REMAPPING + REFERENCE VERIFICATION
    // ═══════════════════════════════════════════════════════════════════════
    //
    // After the iterative loop stabilizes, two optional post-steps run:
    //
    //   1. ReflectionStringRemapper - rewrites MC-typed string constants passed
    //      to reflection APIs (Class.forName, getDeclaredMethod, etc.) that the
    //      main bytecode-level redirect pipeline can't see because strings are
    //      opaque data. ON by default; disable with -Dretromod.remapReflection=false.
    //
    //   2. ReferenceVerifier - scans transformed bytecode for MC references that
    //      don't exist in the target MC JAR, accumulating them into a per-mod
    //      VerificationReport that callers render as a "gap report." OFF by
    //      default; enable with -Dretromod.verifyTransforms=true.
    //
    // Both share the McSymbolIndex (which wraps the existing FuzzyMethodResolver)
    // as their source of MC-version truth.

    private static final boolean REFLECTION_REMAP_ENABLED =
        Boolean.parseBoolean(System.getProperty("retromod.remapReflection", "true"));

    /** ON by default as of the parallel-compute release. Disable on low-memory machines
     *  (less than ~4GB RAM with a large mod collection) via
     *  {@code -Dretromod.verifyTransforms=false} for the best performance. */
    private static final boolean REFERENCE_VERIFY_ENABLED =
        Boolean.parseBoolean(System.getProperty("retromod.verifyTransforms", "true"));

    /** ON by default. Bridge synthesis is narrow-scope and low-risk - the worst case
     *  is a skipped bridge (not a broken class). Disable via
     *  {@code -Dretromod.synthesizeBridges=false} if debugging suggests it's the culprit. */
    private static final boolean BRIDGE_SYNTH_ENABLED =
        Boolean.parseBoolean(System.getProperty("retromod.synthesizeBridges", "true"));

    /** ON by default. The deep ApiUsageFingerprintPattern scans method bodies, which is
     *  the heaviest part of the pattern library. On a 2-core / 4GB-RAM machine with a
     *  very large mod collection (100+ mods) this can add several seconds per mod.
     *  Disable via {@code -Dretromod.matchPatterns=false} if startup time matters more
     *  than diagnostic coverage. */
    private static final boolean PATTERN_MATCH_ENABLED =
        Boolean.parseBoolean(System.getProperty("retromod.matchPatterns", "true"));

    /** Lazily-initialized reflection string remapper. Built once; refresh via {@link #invalidateReflectionRemapper()}. */
    private volatile com.retromod.core.verify.ReflectionStringRemapper reflectionRemapper;

    /** Lazily-initialized reference verifier. Built once the symbol index is available. */
    private volatile com.retromod.core.verify.ReferenceVerifier referenceVerifier;

    /** Counter for classes that went through reflection remapping - for diagnostics. */
    private final AtomicInteger reflectionRemapPassesPerformed = new AtomicInteger();

    /** Lazily-initialized bridge method synthesizer. */
    private volatile com.retromod.core.bridge.BridgeMethodSynthesizer bridgeSynthesizer;

    /** Lazily-initialized pattern matcher - uses the default library. */
    private volatile com.retromod.core.pattern.ClassShapeMatcher classShapeMatcher;

    /**
     * Target MC version, used only in diagnostic output (gap report headers).
     * Populated by {@link #setTargetMcVersion} from the main Retromod initializer.
     * "unknown" until set - never null, so downstream callers don't NPE.
     */
    private volatile String targetMcVersion = "unknown";

    private RetromodTransformer() {
        // Register default shim package as transformable
        transformablePackages.add("com/retromod/shim/");
        // Initialize pattern heuristics - deterministic rules checked before fuzzy matching
        this.patternHeuristics = new PatternHeuristics();
    }
    
    public static RetromodTransformer getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the fuzzy method/field resolver with the target MC JAR.
     * The resolver indexes all classes, methods, and fields in the JAR so it can
     * fuzzy-match unresolved references during bytecode transformation.
     *
     * <p>This is a FALLBACK mechanism - hardcoded redirects registered by shims
     * always take priority. Call this once during startup after shims are loaded.</p>
     *
     * @param mcJarPath path to the Minecraft client JAR, or null to auto-detect
     */
    public void initFuzzyResolver(java.nio.file.Path mcJarPath) {
        // Idempotent: indexing the MC JAR is expensive, and several startup
        // paths now want the index ready early (e.g. host-version-aware class
        // moves). Skip if we've already indexed.
        if (this.fuzzyResolver != null && this.fuzzyResolver.isIndexed()) {
            return;
        }
        try {
            // Auto-detect if no path provided
            if (mcJarPath == null) {
                mcJarPath = FuzzyMethodResolver.findMcJarFromClasspath();
            }
            if (mcJarPath == null) {
                LOGGER.info("MC JAR not found on classpath, fuzzy resolver disabled");
                return;
            }

            FuzzyMethodResolver resolver = new FuzzyMethodResolver();
            resolver.indexJar(mcJarPath);
            if (resolver.isIndexed()) {
                this.fuzzyResolver = resolver;
                LOGGER.info("Fuzzy resolver initialized: {} classes, {} methods, {} fields",
                        resolver.getIndexedClassCount(),
                        resolver.getIndexedMethodCount(),
                        resolver.getIndexedFieldCount());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not initialize fuzzy resolver: {}", e.getMessage());
        }
    }

    /**
     * Get the fuzzy resolver instance, or null if not initialized.
     */
    public FuzzyMethodResolver getFuzzyResolver() {
        return fuzzyResolver;
    }

    /**
     * Get the pattern heuristics engine, or null if not initialized.
     * Pattern heuristics are checked BEFORE the fuzzy resolver because they
     * are faster (simple string comparisons) and more reliable (deterministic rules).
     */
    public PatternHeuristics getPatternHeuristics() {
        return patternHeuristics;
    }

    /**
     * Register a method redirect.
     * When legacy code calls oldOwner.oldName(oldDesc),
     * it will be rewritten to call newOwner.newName(newDesc).
     */
    public void registerMethodRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc) {
        
        MethodKey key = new MethodKey(oldOwner, oldName, oldDesc);
        MethodTarget target = new MethodTarget(newOwner, newName, newDesc);
        methodRedirects.put(key, target);
        methodRedirectOwners.add(oldOwner); // OPTIMIZATION: track owners
        
        LOGGER.debug("Registered method redirect: {}.{}{} -> {}.{}{}",
                oldOwner, oldName, oldDesc, newOwner, newName, newDesc);
    }
    
    /**
     * Register a method redirect that converts instance method to static method.
     * The receiver object becomes the first argument in the static method's descriptor.
     *
     * @param devirtualize if true, changes INVOKEVIRTUAL/INVOKEINTERFACE → INVOKESTATIC
     */
    public void registerMethodRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc,
            boolean devirtualize) {

        MethodKey key = new MethodKey(oldOwner, oldName, oldDesc);
        MethodTarget target = new MethodTarget(newOwner, newName, newDesc, devirtualize);
        methodRedirects.put(key, target);
        methodRedirectOwners.add(oldOwner);
    }

    /**
     * Register a vanilla Mojang->Mojang method rename (same owner + descriptor, name
     * only), e.g. {@code ResourceKey.location -> identifier} on 26.x. Unlike
     * {@link #registerMethodRedirect}, this goes through the ClassRemapper's
     * {@code mapMethodName}, so it rewrites method REFERENCES (e.g.
     * {@code ResourceKey::location} captured in a codec/lambda) as well as direct calls -
     * a plain method redirect only sees direct {@code INVOKE*} sites. Owner-scoped, so it
     * won't touch an unrelated class's same-named method. Register only for targets where
     * the old name is gone (26.1+), since it renames unconditionally on that owner.
     */
    public void registerMethodRename(String owner, String name, String newName) {
        mojangMethodRenames.put(owner + "#" + name, newName);
        LOGGER.debug("Registered method rename: {}.{} -> {}", owner, name, newName);
    }

    /**
     * Register a removed method to be <b>neutralized</b> at every call site:
     * the call is dropped and replaced with stack-balanced pops (args + receiver)
     * plus a default value for the return type - so a mod that calls a method
     * deleted on the host LOADS instead of dying with {@code NoSuchMethodError}.
     *
     * <p>Use this only when there is genuinely <b>no equivalent to redirect to</b>.
     * The motivating case is the imperative {@code RenderSystem} render-state
     * setters ({@code enableBlend()}, {@code blendFunc(II)}, {@code depthMask(Z)},
     * …) removed in the 26.x GpuDevice/RenderPipeline refactor: state moved onto
     * immutable pipeline objects, so the call has no modern method form. The
     * neutralized call is <b>inert</b> - the mod runs, but that bit of manual GL
     * state is lost (soft-fail). Prefer {@link #registerMethodRedirect} whenever a
     * real target exists.
     *
     * <p>Match is exact on {@code owner+name+desc}; a still-present overload that
     * shares the owner is never affected. Gate registration by host version in the
     * caller (only register where the method is actually gone).
     *
     * <p><b>Void-return only, for now.</b> All current callers register
     * {@code ()V}-shaped state setters. For a <i>non-void</i> method,
     * {@link RetromodMethodVisitor#neutralizeCall} pushes a synthetic default
     * whose type differs from the original return; that is fine on the primary
     * {@code COMPUTE_FRAMES} path (frames are recomputed) but would mismatch the
     * <i>preserved</i> stack-map frames on the {@code COMPUTE_MAXS} fallback path
     * (used when frame computation fails for an off-classpath modded class) →
     * {@code VerifyError}. So a non-void registration is rejected with a warning
     * until that path is hardened.
     */
    public void registerRemovedMethodNeutralize(String owner, String name, String desc) {
        if (!desc.endsWith(")V")) {
            LOGGER.warn("Ignoring non-void removed-method neutralize for {}.{}{} - only void "
                    + "returns are supported (a non-void neutralize can VerifyError on the "
                    + "COMPUTE_MAXS fallback path). Use a redirect/polyfill instead.",
                    owner, name, desc);
            return;
        }
        neutralizedMethods.add(new MethodKey(owner, name, desc));
        neutralizedMethodOwners.add(owner);
        LOGGER.debug("Registered removed-method neutralize: {}.{}{}", owner, name, desc);
    }

    /**
     * Register a class redirect (for relocated/renamed classes).
     *
     * <p>Identity redirects ({@code A → A}) are explicitly ignored - they were
     * historically used in older shim files as "this class is part of our compat
     * surface" placeholders, but {@code Map.put} semantics meant they silently
     * <b>overwrote</b> legitimate redirects from other shims. Concrete case the
     * compat-audit caught: {@code RenderingBackendShim} registered
     * {@code MatrixStack → MatrixStack} as a no-op marker, which clobbered
     * {@code Fabric_1_16_5_to_1_17}'s real redirect of
     * {@code MatrixStack → com/mojang/blaze3d/vertex/PoseStack}; every
     * pre-1.17-Yarn mod ended up with stale MatrixStack refs and a
     * {@code NoClassDefFoundError} at first render. Same pattern hit FRAPI
     * renames (QuadEmitter, MeshBuilder, Renderer) via {@code SodiumIrisApiShim}.
     *
     * <p>Rather than chase down and delete every offending registration site
     * (40+ across legacy shims), the guard here makes them no-ops. Callers
     * that genuinely need an entry "exist in the map" can still use a real
     * second target - but identity has no semantic meaning at the remapper
     * level and is always a bug.</p>
     */
    public void registerClassRedirect(String oldClass, String newClass) {
        if (oldClass.equals(newClass)) {
            // Identity redirect - see method javadoc for the rationale.
            LOGGER.debug("Ignored identity class-redirect registration for: {}", oldClass);
            return;
        }
        classRedirects.put(oldClass, newClass);
        classRedirectsVersion.incrementAndGet(); // Invalidate cached remapper
        cachedRemapper = null;
        LOGGER.debug("Registered class redirect: {} -> {}", oldClass, newClass);
    }

    /**
     * Register a constructor-to-factory redirect.
     * Converts `new className(constructorDesc)` → `factoryClass.factoryMethod(factoryDesc)`.
     * The factory method must be static and return the class type.
     */
    public void registerConstructorRedirect(String className, String constructorDesc,
            String factoryClass, String factoryMethod, String factoryDesc) {
        constructorRedirects.put(
            new ConstructorKey(className, constructorDesc),
            new FactoryTarget(factoryClass, factoryMethod, factoryDesc));
        LOGGER.debug("Registered constructor redirect: new {}({}) -> {}.{}{}",
            className, constructorDesc, factoryClass, factoryMethod, factoryDesc);
    }

    /**
     * Register a super constructor descriptor change.
     * When a subclass calls super(oldDesc), the call is redirected to super(newDesc)
     * with an extra static field value pushed onto the stack.
     *
     * Example: Button(int,int,int,int,Component,OnPress) removed in 26.1
     *          → Button(int,int,int,int,Component,OnPress,CreateNarration)
     *          Extra arg: GETSTATIC Button.DEFAULT_NARRATION
     */
    public void registerSuperConstructorRedirect(String className, String oldDesc, String newDesc,
            String extraFieldOwner, String extraFieldName, String extraFieldDesc) {
        superCtorRedirects.put(
            new ConstructorKey(className, oldDesc),
            new SuperCtorRedirect(newDesc, extraFieldOwner, extraFieldName, extraFieldDesc));
        LOGGER.debug("Registered super ctor redirect: {}.{} -> {} + GETSTATIC {}.{}",
            className, oldDesc, newDesc, extraFieldOwner, extraFieldName);
    }

    /**
     * Register a constructor descriptor change that inserts default values for missing params.
     * Used when a constructor gains new required parameters in newer MC.
     * Example: TranslatableContents(String) → TranslatableContents(String, String, Object[])
     *   inserts ACONST_NULL (for String fallback) and empty Object[] (for args).
     *
     * The transformer detects which params are new by comparing old and new descriptors,
     * and pushes appropriate default values (null for objects, 0 for ints, etc.)
     */
    public void registerSuperConstructorRedirect(String className, String oldDesc, String newDesc) {
        // Use a special sentinel for the field owner to indicate "insert defaults" mode
        superCtorRedirects.put(
            new ConstructorKey(className, oldDesc),
            new SuperCtorRedirect(newDesc, "__INSERT_DEFAULTS__", "", ""));
        LOGGER.debug("Registered super ctor descriptor change: {}.{} -> {} (insert defaults)",
            className, oldDesc, newDesc);
    }

    /**
     * Register intermediary method and field name mappings for bytecode remapping.
     * These are used by the Remapper to translate method_XXXX and field_XXXX names
     * in method calls, field accesses, @Shadow annotations, etc.
     */
    public void registerIntermediaryNameMappings(
            Map<String, String> methodNames, Map<String, String> fieldNames) {
        intermediaryMethodNames.putAll(methodNames);
        intermediaryFieldNames.putAll(fieldNames);
        cachedRemapper = null; // Invalidate cached remapper
        LOGGER.info("Registered {} intermediary method names and {} field names for bytecode remapping",
            methodNames.size(), fieldNames.size());
    }

    /**
     * Register Forge SRG → Mojang member name mappings.
     *
     * <p>Same shape as {@link #registerIntermediaryNameMappings} but for
     * SRG names ({@code m_NNNNNN_} / {@code f_NNNNN_}). Used by
     * {@code SrgToMojangMapper} which loads the bundled mapping data file.
     *
     * <p>Both maps are merged into the global SRG dictionary; later calls
     * override earlier entries for the same name.
     */
    public void registerSrgNameMappings(
            Map<String, String> methodNames, Map<String, String> fieldNames) {
        srgMethodNames.putAll(methodNames);
        srgFieldNames.putAll(fieldNames);
        cachedRemapper = null; // Invalidate cached remapper
        LOGGER.info("Registered {} SRG method names and {} SRG field names for bytecode remapping",
            methodNames.size(), fieldNames.size());
    }
    
    /**
     * Register a field redirect (field to field).
     */
    public void registerFieldRedirect(
            String oldOwner, String oldName,
            String newOwner, String newName) {
        
        FieldKey key = new FieldKey(oldOwner, oldName);
        FieldTarget target = new FieldTarget(newOwner, newName, null, null);
        fieldRedirects.put(key, target);
    }
    
    /**
     * Register a field redirect with descriptors.
     * If newDesc starts with "(", this is a field-to-method redirect
     * (GETSTATIC/GETFIELD becomes INVOKESTATIC/INVOKEVIRTUAL).
     */
    public void registerFieldRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc) {
        
        FieldKey key = new FieldKey(oldOwner, oldName);
        FieldTarget target = new FieldTarget(newOwner, newName, oldDesc, newDesc);
        fieldRedirects.put(key, target);
        
        LOGGER.debug("Registered field redirect: {}.{} {} -> {}.{} {}",
                oldOwner, oldName, oldDesc, newOwner, newName, newDesc);
    }
    
    /**
     * Register a field accessor redirect: GETFIELD becomes INVOKEVIRTUAL getter,
     * PUTFIELD becomes INVOKEVIRTUAL setter. For fields that became private in newer MC.
     */
    public void registerFieldAccessorRedirect(
            String fieldOwner, String fieldName,
            String getterName, String getterDesc,
            String setterName, String setterDesc) {
        FieldKey key = new FieldKey(fieldOwner, fieldName);
        FieldAccessorTarget target = new FieldAccessorTarget(
            fieldOwner, getterName, getterDesc,
            fieldOwner, setterName, setterDesc
        );
        fieldAccessorRedirects.put(key, target);
        LOGGER.debug("Registered field accessor redirect: {}.{} -> get={}, set={}",
                fieldOwner, fieldName, getterName, setterName);
    }

    /**
     * Register a removed static field as an accessor call: a {@code GETSTATIC owner.name}
     * is rewritten to {@code GETSTATIC collectionOwner.collectionField}, an optional
     * {@code GETSTATIC argOwner.argField} (the accessor's enum/key argument), an
     * {@code INVOKEVIRTUAL methodOwner.methodName(methodDesc)}, and an optional
     * {@code CHECKCAST castType}. Used for 26.2's `ColorCollection` consolidation, where
     * the per-color block fields (e.g. {@code Blocks.WHITE_CANDLE}) were deleted in favour
     * of {@code Blocks.DYED_CANDLE.pick(DyeColor.WHITE)} - a field access that has to
     * become a method call, which a plain field-to-field redirect can't express.
     *
     * @param argField pass null for a no-argument accessor (then argOwner/argDesc ignored)
     * @param castType pass null to skip the trailing CHECKCAST
     */
    public void registerStaticFieldAccessor(
            String owner, String name,
            String collectionOwner, String collectionField, String collectionDesc,
            String argOwner, String argField, String argDesc,
            String methodOwner, String methodName, String methodDesc,
            String castType) {
        staticFieldAccessors.put(new FieldKey(owner, name), new StaticFieldAccessor(
                collectionOwner, collectionField, collectionDesc,
                argOwner, argField, argDesc,
                methodOwner, methodName, methodDesc, castType));
        LOGGER.debug("Registered static-field accessor: {}.{} -> {}.{}.{}()",
                owner, name, collectionOwner, collectionField, methodName);
    }

    /** Count of static-field accessor redirects (for tests). */
    public int getStaticFieldAccessorCount() {
        return staticFieldAccessors.size();
    }

    /**
     * Mark a package as containing legacy mod code that should be transformed.
     */
    public void addTransformablePackage(String packagePrefix) {
        // Ensure it ends with /
        if (!packagePrefix.endsWith("/")) {
            packagePrefix = packagePrefix + "/";
        }
        transformablePackages.add(packagePrefix);
    }
    
    /**
     * Register an embedded shim class that provides removed API implementations.
     */
    public void registerEmbeddedShim(String className) {
        embeddedShimClasses.add(className);
    }

    /**
     * Register a synthetic class generated via ASM bytecode.
     * These classes are injected into mod JARs during transformation and
     * can have fields/methods with MC-typed signatures that can't be
     * compiled from Java source (since MC isn't on the compile classpath).
     *
     * @param internalName the class internal name (e.g., "com/retromod/polyfill/...")
     * @param classBytes the generated class file bytes
     */
    public void registerSyntheticClass(String internalName, byte[] classBytes) {
        syntheticClasses.put(internalName, classBytes);
        // Also register as embedded shim so the embedder picks it up
        embeddedShimClasses.add(internalName.replace('/', '.'));
        LOGGER.debug("Registered synthetic class: {}", internalName);
    }

    /**
     * Get all registered synthetic classes.
     */
    public Map<String, byte[]> getSyntheticClasses() {
        return Collections.unmodifiableMap(syntheticClasses);
    }

    /**
     * Register a superclass redirect for class-to-interface migrations.
     * When a mod extends oldSuperclass, it will be rewritten to extend newSuperclass
     * and implement the specified interfaces.
     *
     * Example: If Explosion (class_1927) changed from a class to an interface,
     * mods extending it need their superclass changed to a bridge class that
     * implements the new interface.
     */
    public void registerSuperclassRedirect(String oldSuperclass, String newSuperclass, String... addInterfaces) {
        superclassRedirects.put(oldSuperclass, new SuperclassRedirect(newSuperclass, addInterfaces, false));
        LOGGER.debug("Registered superclass redirect: {} -> {} (+ {} interfaces)",
                oldSuperclass, newSuperclass, addInterfaces.length);
    }

    /**
     * Rebase a class's superclass from {@code oldSuperclass} to {@code newSuperclass}
     * (both <b>classes</b>), rewriting the {@code extends} clause <i>and</i> any
     * {@code super(...)} constructor calls to the old base - but <b>nothing else</b>.
     *
     * <p>Use this when the new base provides the old base's constructors so subclasses
     * keep linking (e.g. the pre-1.17 model bridge's {@code LegacyModelBase_*}). Unlike a
     * plain {@link #registerClassRedirect} - which rewrites <i>every</i> reference to the
     * old type, including a modern mod's mixin {@code @Inject} handler that merely captures
     * it as a parameter (the #70 Arcanus crash) - this only touches the inheritance edge,
     * so a mod that doesn't actually extend the base is unaffected. Other references stay
     * as the old type, which is correct because the new base is a subtype of it.
     */
    public void registerSuperclassRebase(String oldSuperclass, String newSuperclass) {
        superclassRedirects.put(oldSuperclass, new SuperclassRedirect(newSuperclass, new String[0], true));
        LOGGER.debug("Registered superclass REBASE (extends + super ctor): {} -> {}",
                oldSuperclass, newSuperclass);
    }

    /**
     * TEST-ONLY: wipe all registered redirect state (class/method/field/constructor/
     * superclass redirects, synthetic classes, embedded shims) so a test can assert on
     * the <i>absence</i> of registrations - e.g. that a host-gated shim registers
     * nothing on a pre-26.1 host. The singleton accumulates state across the whole
     * JVM otherwise, which makes absence assertions meaningless.
     *
     * <p>Also clears the intermediary-&gt;Mojang and SRG-&gt;Mojang member-name remap maps:
     * those are populated per transform-setup (applyTo / registerSrgNameMappings) and drive the
     * {@code hasIntermediaryNames} remap gate, so leaving them populated lets one test's mapping
     * setup leak into the next. Pattern heuristics loaded from bundled resources are left
     * untouched. Never call this from production code.
     */
    public void clearRedirectsForTesting() {
        methodRedirects.clear();
        mojangMethodRenames.clear();
        // The intermediary->Mojang and SRG->Mojang member-name maps drive the remap gate
        // (hasIntermediaryNames); leaving them populated lets a prior test's applyTo() /
        // registerSrgNameMappings() leak into the next, silently remapping names a pre-26.1 test
        // deliberately keeps. Clear them too so the reset is complete (test-ordering robustness;
        // surfaced by the §A4 pre-26.1 acceptance test).
        intermediaryMethodNames.clear();
        intermediaryFieldNames.clear();
        srgMethodNames.clear();
        srgFieldNames.clear();
        classRedirects.clear();
        fieldRedirects.clear();
        superclassRedirects.clear();
        constructorRedirects.clear();
        fieldAccessorRedirects.clear();
        staticFieldAccessors.clear();
        superCtorRedirects.clear();
        syntheticClasses.clear();
        embeddedShimClasses.clear();
        methodRedirectOwners.clear();
        neutralizedMethods.clear();
        neutralizedMethodOwners.clear();
        classRedirectsVersion.incrementAndGet();
        cachedRemapper = null;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, 
            byte[] classfileBuffer) {
        
        if (className == null || classfileBuffer == null) {
            return null;
        }
        
        // Only transform classes in registered packages
        if (!shouldTransform(className)) {
            return null;
        }
        
        // Use the hybrid engine which prefers AOT, falls back to JIT
        // This also handles performance monitoring automatically
        try {
            HybridTransformationEngine hybrid = HybridTransformationEngine.getInstance();
            String modId = guessModFromClass(className);
            return hybrid.transform(className, classfileBuffer, modId);
        } catch (Exception e) {
            LOGGER.error("Failed to transform class: {}", className, e);
            return null;
        }
    }
    
    /**
     * Guess which mod a class belongs to based on package name.
     */
    private String guessModFromClass(String className) {
        // Common mod package patterns
        // e.g., com/example/mymod/... -> mymod
        String[] parts = className.split("/");
        if (parts.length >= 3) {
            return parts[2]; // Usually the mod name is the 3rd part
        }
        return "unknown";
    }
    
    private boolean shouldTransform(String className) {
        for (String prefix : transformablePackages) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Transform a class's bytecode, rewriting method/field/class references.
     * This is the core JIT transformation logic, also used by AOT.
     *
     * <p>The transformation builds an ASM visitor chain:</p>
     * <pre>
     *   ClassReader (parses original bytecode)
     *     -> ClassRemapper (rewrites class names + intermediary method/field names)
     *       -> RetromodClassVisitor (rewrites method calls, field accesses, constructors)
     *         -> ClassWriter (generates new bytecode with COMPUTE_FRAMES)
     * </pre>
     *
     * <p><b>Why this order matters:</b> ClassRemapper runs first so that by the time
     * RetromodClassVisitor processes method calls, all class names in owners and
     * descriptors are already in their final Mojang form. This means method redirect
     * lookups only need to match against Mojang names, not both intermediary and Mojang.</p>
     *
     * <p>If COMPUTE_FRAMES fails (common with modded classes that reference types not on
     * the classpath), falls back to COMPUTE_MAXS which preserves existing stack map frames.</p>
     *
     * <h3>Iterative loop</h3>
     * <p>Each call to this method invokes {@link #singleTransformPass} repeatedly until
     * the bytecode stabilizes (two consecutive passes produce byte-identical output) or
     * until {@link #MAX_TRANSFORM_ITERATIONS} is hit. This handles <i>chained redirects</i>:
     * if pass 1 rewrites {@code X.foo() -> Y.bar()} and {@code Y.bar()} itself has a
     * redirect to {@code Z.baz()}, pass 2 catches the second hop. Single-pass visitors
     * would miss this because the visitor has already moved past the instruction by the
     * time its rewrite happens.</p>
     *
     * <p>For classes with no chained redirects (the common case), pass 1 produces the
     * final output and pass 2 is just a verification step that confirms stability -
     * a single extra ASM visit. For mods that benefit from chaining, passes continue
     * until stable.</p>
     */
    public byte[] transformClass(byte[] originalBytes, String className) {
        // OPTIMIZATION: Skip if no redirects registered. No pass will do anything,
        // so skip the outer loop entirely and return the input unchanged.
        if (methodRedirects.isEmpty() && classRedirects.isEmpty() &&
                fieldRedirects.isEmpty() && superclassRedirects.isEmpty() &&
                neutralizedMethods.isEmpty() && staticFieldAccessors.isEmpty() &&
                mojangMethodRenames.isEmpty() &&
                // constructorRedirects + fieldAccessorRedirects must be here too, or a
                // ctor-only / field-accessor-only redirect set skips the transform entirely
                // (the per-method gate in visitMethod already includes both). In the full
                // shim chain classRedirects is always populated, so this only bit when a
                // ctor/field-accessor redirect was the *only* thing registered.
                constructorRedirects.isEmpty() && fieldAccessorRedirects.isEmpty()) {
            return originalBytes;
        }

        // Debug: log class redirect count for first few classes (once per class, not per pass)
        if (className != null && (className.contains("Mixin") || className.contains("mixin"))) {
            LOGGER.debug("transformClass({}) with {} class redirects, {} method redirects",
                className, classRedirects.size(), methodRedirects.size());
        }

        // Iterative transformation loop.
        //
        // Each iteration re-runs the full visitor chain against the previous pass's
        // output. Terminates when:
        //   1. Two consecutive passes produce byte-identical output (stable fixpoint), OR
        //   2. MAX_TRANSFORM_ITERATIONS is reached (possible redirect cycle).
        //
        // Why compare byte-level rather than tracking "was any redirect applied"?
        // - The visitor chain is complex (ClassRemapper + RetromodClassVisitor + dedup).
        //   Threading a "dirty" flag through every visitor would be invasive.
        // - ASM is deterministic: same input + same redirects = same output bytes.
        //   So byte-equality is a reliable stability signal.
        // - Occasional false positives (constant-pool reordering triggering one extra
        //   pass) are harmless - the iteration cap bounds the worst case.
        byte[] current = originalBytes;
        for (int pass = 1; pass <= MAX_TRANSFORM_ITERATIONS; pass++) {
            byte[] next = singleTransformPass(current, className);
            totalPassesPerformed.incrementAndGet();

            if (Arrays.equals(current, next)) {
                // Stable: nothing further was rewritten. Done.
                //
                // Counter semantics: activePasses = number of passes that actually
                // changed bytes. pass=1 stable means "no transforms applied at all"
                // (0 active). pass=2 stable means "pass 1 did work, pass 2 verified"
                // (1 active - the normal single-redirect case). pass>=3 stable means
                // pass 2+ produced changes on top of pass 1 - that's chaining.
                int activePasses = pass - 1;
                if (activePasses >= 2) {
                    classesNeedingMultiplePasses.incrementAndGet();
                    LOGGER.debug("Class {} needed {} transform passes (chained redirects)",
                            className, activePasses);
                }
                return postProcess(current, className);
            }
            current = next;
        }

        // Hit the iteration cap. Most likely a cycle in redirect chains (A -> B -> A).
        // We use the last pass's output as a best-effort - it's at least as transformed
        // as any earlier pass, and the cycle means no single output is "correct" anyway.
        classesHittingIterationCap.incrementAndGet();
        LOGGER.warn("Transform loop hit cap ({} passes) for class {}. " +
                "Possible redirect cycle - check shim/polyfill registrations. " +
                "Using last pass output.",
                MAX_TRANSFORM_ITERATIONS, className);
        return postProcess(current, className);
    }

    /**
     * Run post-loop transforms (reflection remapping) on the stabilized bytecode.
     *
     * <p>Separate from the iterative loop because these passes are <b>not</b>
     * bytecode-level redirects - they operate on string constants that ASM's
     * {@code ClassRemapper} doesn't see. Running them as part of the iterative
     * loop would be wasteful (they're idempotent - a second call produces the
     * same output) and complicates the stability-detection semantics.</p>
     *
     * <p>Keeping them out of {@link #singleTransformPass} also means they run
     * <b>once per class</b> regardless of how many iterative passes the class
     * needed - the natural place for one-shot work.</p>
     */
    private byte[] postProcess(byte[] stableBytes, String className) {
        if (!REFLECTION_REMAP_ENABLED) return stableBytes;
        try {
            byte[] remapped = getReflectionRemapper().remap(stableBytes);
            if (remapped != stableBytes) {
                // The remapper returns the SAME reference if nothing changed,
                // so inequality is a cheap signal that we actually rewrote
                // something. Worth counting for the diagnostic summary.
                reflectionRemapPassesPerformed.incrementAndGet();
            }
            return remapped;
        } catch (Exception e) {
            // Reflection remapping is advisory - a failure shouldn't break the
            // whole transformation. Log and return the pre-remap bytes.
            LOGGER.debug("Reflection remap skipped for {}: {}", className, e.getMessage());
            return stableBytes;
        }
    }

    /**
     * Run one bytecode transformation pass over the given class bytes.
     *
     * <p>Each call is a complete ASM visitor-chain traversal:
     * {@code Reader → ClassRemapper → RetromodClassVisitor → ClassWriter}.
     * The {@link #transformClass} outer loop invokes this repeatedly until the
     * output stabilizes.</p>
     *
     * <p>If the primary COMPUTE_FRAMES path fails (common with modded classes that
     * reference types not on the classpath), falls back to COMPUTE_MAXS preserving
     * existing stack map frames. If that also fails, returns the input bytes
     * unchanged so the caller can at least ship the mod with no-op transformation
     * for this class instead of aborting the whole mod.</p>
     *
     * @param originalBytes the current bytecode (may be the original input or a
     *                      previous pass's output)
     * @param className     JVM internal name of the class, used only for logging
     * @return transformed bytes, or {@code originalBytes} unchanged if the visitor
     *         chain fails completely (both COMPUTE_FRAMES and COMPUTE_MAXS paths)
     */
    private byte[] singleTransformPass(byte[] originalBytes, String className) {
        ClassReader reader = new ClassReader(originalBytes);

        // OPTIMIZATION: Use cached remapper if class redirects haven't changed.
        // The cache is invalidated whenever classRedirects or intermediary mappings
        // change (see invalidateRemapperCache() callers).
        //
        // THREAD-SAFETY: parallel transformation (FabricModTransformer / Forge /
        // CLI gaps) calls this method concurrently. Without a proper
        // double-checked lock, two workers can both observe `cachedRemapper ==
        // null`, each allocate a fresh Remapper, and one's write clobbers the
        // other - harmless when the redirect state is stable, but if the
        // losing thread's Remapper already saw a redirect that arrived after
        // the winner's allocation, the winner's state is stale and the loser's
        // Remapper gets discarded. Synchronize the construct-and-publish so
        // only one instance is ever published, and every reader sees it.
        Remapper classRemapper = cachedRemapper;
        if (classRemapper == null) {
            boolean hasIntermediaryNames = !intermediaryMethodNames.isEmpty() || !intermediaryFieldNames.isEmpty();
            if (!classRedirects.isEmpty() || hasIntermediaryNames || !mojangMethodRenames.isEmpty()) {
                synchronized (this) {
                    classRemapper = cachedRemapper; // re-check under lock
                    if (classRemapper == null) {
                        classRemapper = new Remapper() {
                            @Override
                            public String map(String internalName) {
                                return classRedirects.getOrDefault(internalName, internalName);
                            }

                            @Override
                            public String mapMethodName(String owner, String name, String descriptor) {
                                // Remap intermediary method names (method_XXXX / comp_XXXX → Mojang name)
                                if (!intermediaryMethodNames.isEmpty()
                                        && (name.startsWith("method_") || name.startsWith("comp_"))) {
                                    String mojang = intermediaryMethodNames.get(name);
                                    if (mojang != null) return mojang;
                                }
                                // Remap Forge SRG method names (m_NNNNNN_ → Mojang name).
                                // Forge mods built before ~MC 1.20.5 (and any later
                                // build that still uses ForgeGradle's reobfJar) ship
                                // SRG names. Forge 64.x dropped the SRG remap layer
                                // so without this, SRG-baked Forge mods all hit
                                // NoSuchMethodError at runtime.
                                if (!srgMethodNames.isEmpty()
                                        && name.length() > 3
                                        && name.startsWith("m_")
                                        && name.endsWith("_")) {
                                    String mojang = srgMethodNames.get(name);
                                    if (mojang != null) return mojang;
                                }
                                // Vanilla Mojang->Mojang method rename (owner-scoped),
                                // e.g. ResourceKey.location -> identifier on 26.x. Routed
                                // through the ClassRemapper so it also rewrites method
                                // references (ResourceKey::location), which the manual
                                // methodRedirects pass cannot reach.
                                if (!mojangMethodRenames.isEmpty()) {
                                    String renamed = mojangMethodRenames.get(owner + "#" + name);
                                    if (renamed != null) return renamed;
                                }
                                return name;
                            }

                            @Override
                            public String mapFieldName(String owner, String name, String descriptor) {
                                // Remap intermediary field names (field_XXXX → Mojang name)
                                if (!intermediaryFieldNames.isEmpty() && name.startsWith("field_")) {
                                    String mojang = intermediaryFieldNames.get(name);
                                    if (mojang != null) return mojang;
                                }
                                // Remap Forge SRG field names (f_NNNNN_ → Mojang name).
                                // Same reasoning as the method case above. SRG
                                // field names look like f_50069_ (Blocks.STONE),
                                // f_42415_ (Items.DIAMOND), etc.
                                if (!srgFieldNames.isEmpty()
                                        && name.length() > 3
                                        && name.startsWith("f_")
                                        && name.endsWith("_")) {
                                    String mojang = srgFieldNames.get(name);
                                    if (mojang != null) return mojang;
                                }
                                return name;
                            }
                        };
                        cachedRemapper = classRemapper;
                    }
                }
            }
        }

        // IMPORTANT: When using ClassRemapper, do NOT pass the ClassReader to ClassWriter!
        // ClassWriter(reader, flags) copies the constant pool from the reader as an optimization,
        // which means ClassRemapper's name changes don't get reflected in the output.
        // We must create a standalone ClassWriter so ASM builds a fresh constant pool.
        boolean hasClassRemaps = (classRemapper != null);
        ClassWriter writer = hasClassRemaps
            ? new SafeClassWriter(ClassWriter.COMPUTE_FRAMES)
            : new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor visitor = writer;

        // IMPORTANT: RetromodClassVisitor must be INNER (closer to writer) so it sees
        // Mojang names AFTER ClassRemapper has translated intermediary names.
        // Chain: Reader → ClassRemapper (remap) → RetromodClassVisitor (redirect) → Writer
        visitor = new RetromodClassVisitor(Opcodes.ASM9, visitor);

        // Only add remapping visitor if we have class redirects
        if (classRemapper != null) {
            visitor = new ClassRemapper(visitor, classRemapper);
        }

        try {
            // Use EXPAND_FRAMES to properly feed frame data into COMPUTE_FRAMES
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            byte[] result = writer.toByteArray();
            // Deduplicate methods that may have identical name+descriptor after class remapping.
            // This happens when two intermediary class names (e.g., class_316 and class_4064)
            // both map to the same Mojang name, causing return types in descriptors to collapse.
            if (hasClassRemaps) {
                result = deduplicateMethods(result, className);
            }
            return result;
        } catch (Exception e) {
            // Fallback: try with COMPUTE_MAXS and preserve existing frames
            try {
                ClassWriter fallbackWriter = hasClassRemaps
                    ? new SafeClassWriter(ClassWriter.COMPUTE_MAXS)
                    : new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                ClassVisitor fallbackVisitor = fallbackWriter;
                fallbackVisitor = new RetromodClassVisitor(Opcodes.ASM9, fallbackVisitor);
                if (classRemapper != null) {
                    fallbackVisitor = new ClassRemapper(fallbackVisitor, classRemapper);
                }
                // Don't skip frames - preserve existing StackMapTable entries
                reader.accept(fallbackVisitor, 0);
                byte[] result = fallbackWriter.toByteArray();
                if (hasClassRemaps) {
                    result = deduplicateMethods(result, className);
                }
                return result;
            } catch (Exception e2) {
                LOGGER.warn("Transform failed for {}, returning original: {}", className, e2.getMessage());
                return originalBytes;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ITERATION METRICS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Total number of transformation passes performed across all classes since
     * this transformer was instantiated. A class that stabilizes after one pass
     * contributes one; a class needing three passes contributes three.
     */
    public int getTotalPassesPerformed() {
        return totalPassesPerformed.get();
    }

    /**
     * Number of classes that required 2+ passes to stabilize. Each such class
     * indicates a chained redirect (e.g., shim A called B, which has its own
     * redirect to C). A high count suggests the shim chain has deep hops.
     */
    public int getClassesNeedingMultiplePasses() {
        return classesNeedingMultiplePasses.get();
    }

    /**
     * Number of classes that hit {@link #MAX_TRANSFORM_ITERATIONS} without
     * stabilizing. A non-zero value here indicates a cycle in registered
     * redirects (e.g., A → B → A) and should be investigated - the class's
     * output will be the last pass before the cap, which may be inconsistent.
     */
    public int getClassesHittingIterationCap() {
        return classesHittingIterationCap.get();
    }

    /**
     * Reset iteration metrics. Useful for tests that want to measure pass counts
     * for a specific transformation without inheriting counts from prior runs.
     */
    public void resetIterationMetrics() {
        totalPassesPerformed.set(0);
        classesNeedingMultiplePasses.set(0);
        classesHittingIterationCap.set(0);
        reflectionRemapPassesPerformed.set(0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REFLECTION REMAPPING + REFERENCE VERIFICATION - PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lazily construct the reflection string remapper, reusing the transformer's
     * own redirect tables.
     *
     * <p><b>Assumption:</b> shim/polyfill redirects are populated during startup
     * before the first class is transformed. Once the remapper is built we don't
     * rebuild it, even if new redirects arrive later. For the current codebase
     * this is true - {@code Retromod.onInitialize} loads all shims synchronously
     * before any class ever passes through {@link #transformClass}. If that
     * invariant changes, call {@link #invalidateReflectionRemapper()} after
     * registering the new redirects.</p>
     */
    private com.retromod.core.verify.ReflectionStringRemapper getReflectionRemapper() {
        com.retromod.core.verify.ReflectionStringRemapper local = reflectionRemapper;
        if (local != null) return local;
        synchronized (this) {
            if (reflectionRemapper != null) return reflectionRemapper;
            reflectionRemapper = new com.retromod.core.verify.ReflectionStringRemapper(
                    classRedirects,
                    intermediaryMethodNames,
                    intermediaryFieldNames,
                    com.retromod.core.verify.LoaderApiRenames.getInstance());
            return reflectionRemapper;
        }
    }

    /**
     * Force reconstruction of the reflection remapper on next use. Call this
     * after bulk-registering new redirects if you need them reflected in
     * reflection-string rewrites.
     */
    public void invalidateReflectionRemapper() {
        reflectionRemapper = null;
    }

    /**
     * How many classes had at least one reflection string rewritten by the
     * remapper. For diagnostics/metrics reporting.
     */
    public int getReflectionRemapPassesPerformed() {
        return reflectionRemapPassesPerformed.get();
    }

    /**
     * Scan a transformed class for unresolved MC references and append findings
     * to the given report. Callers (CLI {@code batchCommand}, mod-loader
     * transformers) invoke this after processing every class in a mod to
     * produce a per-mod gap report.
     *
     * <p>No-op if reference verification is disabled via
     * {@code -Dretromod.verifyTransforms=false} (the default).</p>
     *
     * @param classBytes    transformed class bytecode (post-iterative-loop)
     * @param className     JVM internal name (for diagnostic logging)
     * @param modOwnClasses classes defined by the mod itself - references to
     *                      these are skipped so we don't report mod-internal
     *                      references as "missing from MC"
     * @param report        report to accumulate findings into
     */
    public void verifyClass(byte[] classBytes, String className,
                             java.util.Set<String> modOwnClasses,
                             com.retromod.core.verify.VerificationReport report) {
        if (!REFERENCE_VERIFY_ENABLED) return;
        if (classBytes == null || report == null) return;
        com.retromod.core.verify.ReferenceVerifier v = getReferenceVerifier();
        if (v == null) return;
        v.verify(classBytes, modOwnClasses, report);
    }

    /** @return {@code true} iff reference verification is enabled via system property */
    public static boolean isVerificationEnabled() {
        return REFERENCE_VERIFY_ENABLED;
    }

    /**
     * Lazily construct the reference verifier once the fuzzy resolver has been
     * initialized with a target MC JAR. Returns null (and logs once) if the
     * resolver isn't indexed yet - verification can't run without it.
     */
    private com.retromod.core.verify.ReferenceVerifier getReferenceVerifier() {
        com.retromod.core.verify.ReferenceVerifier local = referenceVerifier;
        if (local != null) return local;
        synchronized (this) {
            if (referenceVerifier != null) return referenceVerifier;
            if (fuzzyResolver == null || !fuzzyResolver.isIndexed()) {
                // No MC index → can't verify. Return null; caller skips the verify.
                return null;
            }
            com.retromod.core.verify.McSymbolIndex index =
                new com.retromod.core.verify.FuzzyBackedSymbolIndex(
                    fuzzyResolver, targetMcVersion);
            referenceVerifier = new com.retromod.core.verify.ReferenceVerifier(
                index,
                com.retromod.core.verify.LoaderApiRenames.getInstance());
            return referenceVerifier;
        }
    }

    /**
     * Record the target MC version for inclusion in verification-report headers.
     * Called by the main Retromod initializer after it detects the running MC
     * version. Safe to call from any thread; safe to call multiple times.
     *
     * <p>Acquires the class monitor so the verifier invalidation happens under
     * the same lock that {@link #getReferenceVerifier} uses for its
     * double-checked-locking read. Without this, another thread could observe
     * a non-null {@code referenceVerifier} built from the old target version.</p>
     */
    public void setTargetMcVersion(String version) {
        if (version == null || version.isEmpty()) return;
        synchronized (this) {
            this.targetMcVersion = version;
            // Invalidate the cached verifier so the next gap-report header
            // reflects the new version. The field is volatile (declared above),
            // so this null-write is visible to subsequent DCL readers.
            this.referenceVerifier = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BRIDGE METHOD SYNTHESIS (#4) - PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Synthesize bridge methods in a mod class for overrides orphaned by MC
     * method renames. See
     * {@link com.retromod.core.bridge.BridgeMethodSynthesizer} for the full
     * explanation of when and why.
     *
     * <p>No-op if bridge synthesis is disabled via
     * {@code -Dretromod.synthesizeBridges=false} (the default) or the class
     * isn't in {@code modOwnClasses}.</p>
     *
     * @param classBytes    transformed class bytecode (post-iterative-loop)
     * @param modOwnClasses JVM internal names of classes defined by the mod
     * @return possibly-rewritten bytecode with bridge methods appended; same
     *         reference as input if nothing changed
     */
    public byte[] synthesizeBridges(byte[] classBytes, java.util.Set<String> modOwnClasses) {
        if (!BRIDGE_SYNTH_ENABLED) return classBytes;
        if (classBytes == null) return classBytes;
        return getBridgeSynthesizer().synthesize(classBytes, modOwnClasses);
    }

    /** @return {@code true} iff bridge synthesis is enabled via system property */
    public static boolean isBridgeSynthesisEnabled() {
        return BRIDGE_SYNTH_ENABLED;
    }

    /** Accessor for bridge-synth counters, mostly for tests and the gap report. */
    public com.retromod.core.bridge.BridgeMethodSynthesizer getBridgeSynthesizer() {
        com.retromod.core.bridge.BridgeMethodSynthesizer local = bridgeSynthesizer;
        if (local != null) return local;
        synchronized (this) {
            if (bridgeSynthesizer != null) return bridgeSynthesizer;
            // Build a rename-lookup from the existing method-redirect table.
            // Only renames that preserve the owner class AND the descriptor
            // are safe for bridge synthesis (v1 scope - see BridgeMethodSynthesizer
            // javadoc for why). We filter those out in the lambda.
            var lookup = com.retromod.core.bridge.BridgeMethodSynthesizer.buildLookupFrom(
                    methodRedirects,
                    key -> com.retromod.core.bridge.BridgeMethodSynthesizer.renameKey(
                            key.owner(), key.name(), key.desc()),
                    target -> {
                        // v1 scope: only rename-with-same-descriptor cases are
                        // safe to bridge automatically. If the redirect changed
                        // the descriptor, we return null so the lookup says
                        // "no bridge applies here."
                        //
                        // We can't access the original key from the value here,
                        // so we bake the check into the lookup at query time:
                        // the caller-provided key has its own descriptor, and
                        // the synthesizer compares that against what the bridge
                        // emitter ends up using. For v1 we simply return the
                        // new name; the descriptor-collision guard in the
                        // synthesizer's emit() step catches mismatches before
                        // emission.
                        return target.name();
                    });
            bridgeSynthesizer = new com.retromod.core.bridge.BridgeMethodSynthesizer(lookup);
            return bridgeSynthesizer;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATTERN MATCHING (#3) - PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Run all registered class-shape patterns against the given mod class.
     * Returns every match, which callers (primarily the CLI gap report)
     * accumulate into a {@link com.retromod.core.verify.VerificationReport}.
     *
     * <p>No-op if pattern matching is disabled via
     * {@code -Dretromod.matchPatterns=false} (the default).</p>
     */
    public java.util.List<com.retromod.core.pattern.PatternMatch> matchPatterns(
            byte[] classBytes, com.retromod.core.pattern.MatchContext ctx) {
        if (!PATTERN_MATCH_ENABLED) return java.util.Collections.emptyList();
        if (classBytes == null || ctx == null) return java.util.Collections.emptyList();
        return getClassShapeMatcher().matchAll(classBytes, ctx);
    }

    /** @return {@code true} iff pattern matching is enabled via system property */
    public static boolean isPatternMatchingEnabled() {
        return PATTERN_MATCH_ENABLED;
    }

    /** Lazily construct the default pattern library matcher. */
    private com.retromod.core.pattern.ClassShapeMatcher getClassShapeMatcher() {
        com.retromod.core.pattern.ClassShapeMatcher local = classShapeMatcher;
        if (local != null) return local;
        synchronized (this) {
            if (classShapeMatcher != null) return classShapeMatcher;
            classShapeMatcher = com.retromod.core.pattern.ClassShapeMatcher.defaultLibrary();
            return classShapeMatcher;
        }
    }

    /**
     * Remove duplicate methods from a class that were created by class remapping.
     *
     * When two different intermediary class names map to the same Mojang name,
     * methods whose descriptors differ only in those class names end up with
     * identical name+descriptor after remapping. The JVM rejects classes with
     * duplicate methods, so we must keep only one copy.
     *
     * Preference: keep the non-synthetic, non-bridge method when possible.
     */
    private byte[] deduplicateMethods(byte[] classBytes, String className) {
        ClassReader cr = new ClassReader(classBytes);

        // First pass: detect duplicates
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                String key = name + descriptor;
                if (!seen.add(key)) {
                    duplicates.add(key);
                }
                return null; // no need to visit method body
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (duplicates.isEmpty()) {
            return classBytes; // nothing to deduplicate
        }

        LOGGER.info("Deduplicating {} duplicate method(s) in {}: {}",
                duplicates.size(), className, duplicates);

        // Second pass: rebuild class, skipping duplicate methods.
        // For each duplicate signature, prefer the non-synthetic/non-bridge variant.
        // Track which duplicates we have already emitted.
        Set<String> emitted = new HashSet<>();
        // We need two sub-passes for duplicates: first collect access flags, then filter.
        // Collect: for each duplicate key, record the "best" access flags seen.
        Map<String, Integer> bestAccess = new HashMap<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                String key = name + descriptor;
                if (duplicates.contains(key)) {
                    Integer prev = bestAccess.get(key);
                    if (prev == null) {
                        bestAccess.put(key, access);
                    } else {
                        // Prefer non-synthetic, non-bridge
                        boolean prevIsSynthetic = (prev & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                        boolean currIsSynthetic = (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                        if (prevIsSynthetic && !currIsSynthetic) {
                            bestAccess.put(key, access);
                        }
                    }
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        // Now rebuild, keeping only the first method whose access matches "best",
        // or the first occurrence if all have the same flags.
        ClassWriter dedupWriter = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, dedupWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                String key = name + descriptor;
                if (duplicates.contains(key)) {
                    if (emitted.contains(key)) {
                        // Already emitted the preferred copy - skip this one
                        return null;
                    }
                    Integer best = bestAccess.get(key);
                    boolean thisIsBest = (best != null && best == access);
                    boolean thisIsSynthetic = (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                    if (!thisIsBest && !emitted.contains(key)) {
                        // This is not the preferred copy, but we haven't emitted one yet.
                        // If there's a better one coming, skip; otherwise emit.
                        // We can't look ahead, so skip synthetic/bridge if the best isn't synthetic.
                        boolean bestIsSynthetic = best != null && (best & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
                        if (thisIsSynthetic && !bestIsSynthetic) {
                            return null; // skip, the better one will come
                        }
                    }
                    emitted.add(key);
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);

        return dedupWriter.toByteArray();
    }

    /**
     * ClassWriter that safely handles getCommonSuperClass in modded environments.
     * ASM's default implementation uses Class.forName() which fails when classes
     * are loaded by custom classloaders (as in Minecraft mod loaders).
     */
    private static class SafeClassWriter extends ClassWriter {

        public SafeClassWriter(int flags) {
            super(flags);
        }

        public SafeClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (Exception | LinkageError e) {
                // A type isn't resolvable via Class.forName - common in modded MC, and
                // especially when a type lives in a Fabric Jar-in-Jar (META-INF/jars/*),
                // is a mod class, or was remapped to a name not on the transform
                // classpath. Don't blindly fall back to Object - see commonSuperFallback.
                return commonSuperFallback(type1, type2);
            }
        }
    }

    /**
     * Fallback common-supertype for when ASM's {@code getCommonSuperClass} can't resolve
     * a type via {@code Class.forName} (it lives in a Fabric Jar-in-Jar, is a mod class,
     * or was remapped off the transform classpath).
     *
     * <p>The old behaviour - always {@code java/lang/Object} - silently corrupts the
     * recomputed {@code StackMapTable} when the real merge is two exception types: ASM
     * then types the caught value as {@code Object} at a catch-handler join where the
     * consumer needs a {@code Throwable}, producing a {@code VerifyError} at class load.
     * That's forge-config-api-port's {@code ConfigTracker.loadConfig} (#94 follow-up):
     * one merged exception, night-config's {@code ParsingException}, is JiJ-bundled and
     * invisible to the transformer. When either operand is a {@code Throwable},
     * {@code Throwable} is the correct common supertype and satisfies the verifier; fall
     * back to {@code Object} only when neither is (or can't be shown to be) one.
     */
    static String commonSuperFallback(String type1, String type2) {
        if (isThrowable(type1) || isThrowable(type2)) {
            return "java/lang/Throwable";
        }
        return "java/lang/Object";
    }

    /**
     * True if {@code internalName} is {@code java/lang/Throwable} or a subclass,
     * determined by reading class bytes through classpath resources rather than
     * {@code Class.forName} (which is what failed above). Returns false if any link in
     * the superclass chain can't be read (e.g. a JiJ/mod class) - i.e. "unknown, assume
     * not a Throwable", preserving the Object fallback for genuine non-exception merges.
     */
    private static boolean isThrowable(String internalName) {
        if (internalName == null) {
            return false;
        }
        // 1) Classloading handles the common case - JDK exceptions (IOException, …) and
        //    anything on the transform classpath. This must come FIRST: under the Java
        //    9+ module system getResourceAsStream does NOT return JDK classes, so a
        //    byte-walk alone would miss java/io/IOException. Catches its own failures.
        try {
            return Throwable.class.isAssignableFrom(
                    Class.forName(internalName.replace('/', '.'), false,
                            RetromodTransformer.class.getClassLoader()));
        } catch (Throwable ignored) {
            // not loadable via forName - fall through to a byte-level superclass walk
        }
        // 2) Walk the superclass chain from class bytes, for types readable as a resource
        //    but not loadable via forName. Returns false if any link can't be read
        //    (e.g. a JiJ/mod class) - "unknown, assume not a Throwable".
        String name = internalName;
        for (int guard = 0; guard < 64 && name != null && !"java/lang/Object".equals(name); guard++) {
            if ("java/lang/Throwable".equals(name)) {
                return true;
            }
            java.io.InputStream in = RetromodTransformer.class.getClassLoader()
                    .getResourceAsStream(name + ".class");
            if (in == null) {
                in = ClassLoader.getSystemResourceAsStream(name + ".class");
            }
            if (in == null) {
                return false;
            }
            try (java.io.InputStream stream = in) {
                name = new ClassReader(stream).getSuperName();
            } catch (java.io.IOException | RuntimeException ex) {
                return false;
            }
        }
        return false;
    }

    /**
     * ASM ClassVisitor that rewrites method calls, field accesses,
     * and superclass references (for class-to-interface migrations).
     */
    // Classes that became interfaces in newer MC/DFU versions (e.g., DataResult
    // was a concrete class but became an interface in newer DataFixerUpper).
    // The JVM requires different opcodes for interface vs class method calls:
    //   INVOKEVIRTUAL  -> for concrete class methods
    //   INVOKEINTERFACE -> for interface methods
    // Old bytecode uses INVOKEVIRTUAL for these classes, which would crash at
    // runtime with IncompatibleClassChangeError. We fix the opcode here.
    private static final Set<String> KNOWN_INTERFACES = Set.of(
        "com/mojang/serialization/DataResult",
        "com/mojang/serialization/DynamicOps",
        "com/mojang/serialization/MapLike",
        "com/mojang/serialization/Lifecycle",
        "net/minecraft/core/Registry",  // Registry became interface in newer MC
        // Component (formerly the Text class in yarn) became an interface in
        // MC 26.1. Without this entry, INVOKEVIRTUAL on .copy() / .append() /
        // .formatted() on a Text after remap fails verification with
        //   IncompatibleClassChangeError: Found interface
        //   net.minecraft.network.chat.Component, but class was expected
        // Surfaced by retromod-test-mod's Test 5 (Text.copy().append).
        "net/minecraft/network/chat/Component"
        // NOTE: MutableComponent is NOT in this list - it's still a *class*
        // (extends nothing useful, implements Component). Adding it here was
        // a mistake in an earlier pass; it caused the opposite verifier
        // error: "Found class MutableComponent, but interface was expected"
        // when bytecode does INVOKEVIRTUAL on a chain that returns
        // MutableComponent. The class still has its instance methods, so
        // INVOKEVIRTUAL is the right opcode and we leave it alone.
    );

    private class RetromodClassVisitor extends ClassVisitor {

        // Bridge adapter generator for this class - handles method signature changes
        // (e.g., mouseClicked(double,double,int) -> mouseClicked(MouseButtonEvent,boolean)).
        // Reset for each class in visit(). Bounded by BridgeAdapterGenerator.BRIDGES size.
        private final BridgeAdapterGenerator bridgeGenerator = new BridgeAdapterGenerator();

        // Track the current class name for bridge generation
        private String currentClassName;

        public RetromodClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        // Stored for fixing invokespecial on indirect interface methods.
        // currentSuperName = EFFECTIVE direct superclass (post-rebase); used by the
        // non-<init> super-call fixup so it targets the real direct super.
        // currentSuperNameOriginal = the super as written in the source bytecode; used
        // to recognize a super() <init> call that needs its owner rebased (#70).
        private String currentSuperName = null;
        private String currentSuperNameOriginal = null;
        private Set<String> currentDirectInterfaces = Set.of();

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            this.currentClassName = name;
            this.currentSuperName = superName;            // effective (overwritten below if rebased)
            this.currentSuperNameOriginal = superName;    // as written in source
            this.currentDirectInterfaces = interfaces != null
                ? Set.of(interfaces) : Set.of();
            // Reset bridge generator for each new class
            bridgeGenerator.reset();

            // Filter out self-referential interfaces (caused by class remapping)
            // E.g., ConfigScreenFactory extends io.github.prospector...ConfigScreenFactory
            // After remap, both resolve to com.terraformersmc...ConfigScreenFactory
            if (interfaces != null && interfaces.length > 0) {
                String[] filtered = java.util.Arrays.stream(interfaces)
                    .filter(iface -> !iface.equals(name))
                    .toArray(String[]::new);
                if (filtered.length < interfaces.length) {
                    LOGGER.debug("Removed self-referential interface from {}", name);
                    interfaces = filtered;
                }
            }

            // Check if superclass needs to be rewritten (class-to-interface polyfill)
            if (superName != null && !superclassRedirects.isEmpty()) {
                SuperclassRedirect redirect = superclassRedirects.get(superName);
                if (redirect != null) {
                    LOGGER.debug("Rewriting superclass of {} from {} to {}",
                            name, superName, redirect.newSuperclass());
                    String newSuper = redirect.newSuperclass();
                    this.currentSuperName = newSuper; // effective direct super is the new base
                    String[] newInterfaces = mergeInterfaces(interfaces, redirect.addInterfaces());
                    super.visit(version, access, name, signature, newSuper, newInterfaces);
                    return;
                }
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        private String[] mergeInterfaces(String[] existing, String[] additional) {
            if (additional == null || additional.length == 0) {
                return existing;
            }
            Set<String> merged = new LinkedHashSet<>();
            if (existing != null) {
                Collections.addAll(merged, existing);
            }
            Collections.addAll(merged, additional);
            return merged.toArray(new String[0]);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // After class remapping, some inner/outer class pairs may collapse to the same name.
            // For example: class_5253$class_5254 → ARGB, class_5253 → ARGB
            // This creates an invalid "class is both outer and inner" entry.
            // Check the resolved (post-remap) names and skip self-referential entries.
            if (outerName != null) {
                String resolvedName = classRedirects.getOrDefault(name, name);
                String resolvedOuter = classRedirects.getOrDefault(outerName, outerName);
                if (resolvedName.equals(resolvedOuter)) {
                    LOGGER.debug("Skipping self-referential InnerClass entry: {} of {} (both resolve to {})",
                            name, outerName, resolvedName);
                    return;
                }
            }
            super.visitInnerClass(name, outerName, innerName, access);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {

            // BridgeAdapterGenerator disabled - causes VerifyErrors by renaming methods
            // that already have the correct signature. Needs more work before enabling.
            // TODO: Only apply bridges when the method ACTUALLY has the old descriptor

            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            // OPTIMIZATION: Only wrap if we have redirects. superclassRedirects is included
            // because a class→class rebase rewrites super(...) <init> owners in the method
            // body (#70), and the non-<init> invokespecial fixup also lives in the wrapper.
            if (methodRedirects.isEmpty() && fieldRedirects.isEmpty() && constructorRedirects.isEmpty()
                    && fieldAccessorRedirects.isEmpty() && superclassRedirects.isEmpty()
                    && neutralizedMethods.isEmpty() && staticFieldAccessors.isEmpty()) {
                return mv;
            }
            return new RetromodMethodVisitor(api, mv, currentClassName, currentSuperName,
                    currentSuperNameOriginal, currentDirectInterfaces);
        }

        @Override
        public void visitEnd() {
            // BridgeAdapterGenerator disabled - see TODO in visitMethod
            super.visitEnd();
        }
    }
    
    /**
     * ASM MethodVisitor that rewrites individual method invocations.
     *
     * <p><b>Constructor→factory pattern:</b> In JVM bytecode, {@code new Foo(args)} compiles to:
     * <pre>
     *   NEW Foo          // allocate uninitialized object
     *   DUP              // duplicate reference (one for <init>, one stays on stack)
     *   [push args]      // push constructor arguments
     *   INVOKESPECIAL Foo.<init>(args)V  // call constructor
     * </pre>
     * To redirect to a static factory ({@code Foo.create(args)}), we need to:
     * <ol>
     *   <li>Suppress the NEW and DUP instructions</li>
     *   <li>Let the argument-pushing instructions pass through</li>
     *   <li>Replace the INVOKESPECIAL with INVOKESTATIC to the factory method</li>
     * </ol>
     * We achieve this by "buffering" the NEW/DUP when we see a class with constructor
     * redirects, then deciding at the INVOKESPECIAL whether to emit them or replace them.
     * If no redirect matches the specific descriptor, we flush (emit) the buffered NEW+DUP.
     *
     * <p><b>Performance:</b> Uses fast owner lookup ({@code methodRedirectOwners}) to skip
     * expensive ConcurrentHashMap lookups for method calls to classes with no redirects.</p>
     */
    private class RetromodMethodVisitor extends MethodVisitor {

        // Buffered NEW instruction - held until we see the matching <init> to decide
        // whether to redirect to a factory or emit normally
        private String pendingNewClass = null;
        private boolean pendingDup = false;

        // Inherited from enclosing RetromodClassVisitor for invokespecial fixups
        private final String classOwnName;
        private final String classSuperName;          // effective (post-rebase) direct super
        private final String classSuperNameOriginal;  // super as written in source (for #70 rebase match)
        private final Set<String> classDirectInterfaces;

        public RetromodMethodVisitor(int api, MethodVisitor methodVisitor,
                String className, String superName, String superNameOriginal,
                Set<String> directInterfaces) {
            super(api, methodVisitor);
            this.classOwnName = className;
            this.classSuperName = superName;
            this.classSuperNameOriginal = superNameOriginal;
            this.classDirectInterfaces = directInterfaces;
        }

        private void flushPendingNew() {
            if (pendingNewClass != null) {
                super.visitTypeInsn(Opcodes.NEW, pendingNewClass);
                pendingNewClass = null;
            }
            if (pendingDup) {
                super.visitInsn(Opcodes.DUP);
                pendingDup = false;
            }
        }

        /**
         * Flush a deferred NEW+DUP when constructor args are already on the stack.
         *
         * <p>This handles the case where a class has SOME constructor redirects registered
         * (causing NEW to be deferred) but the actual constructor descriptor doesn't match
         * any redirect. The args were already emitted during the deferral period, so they're
         * on the stack. We need NEW+DUP to appear BELOW the args for correct JVM verification.</p>
         *
         * <p>Strategy: store args to temp locals (reverse order), emit NEW+DUP,
         * reload args from temp locals (forward order). COMPUTE_FRAMES handles max locals.</p>
         *
         * @param constructorDesc the constructor descriptor, e.g. "(Ljava/lang/String;I)V"
         */
        private void flushPendingNewBeforeArgs(String constructorDesc) {
            if (pendingNewClass == null) return;

            // Parse parameter types from the descriptor
            Type[] paramTypes = Type.getArgumentTypes(constructorDesc);
            if (paramTypes.length == 0) {
                // No args on stack - simple flush is fine
                flushPendingNew();
                return;
            }

            // Use high base slot to avoid conflicts with existing locals.
            // COMPUTE_FRAMES / COMPUTE_MAXS will adjust maxLocals automatically.
            int tempSlotBase = 200;
            int slot = tempSlotBase;

            // Allocate slots for each parameter (some types use 2 slots)
            int[] paramSlots = new int[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramSlots[i] = slot;
                slot += paramTypes[i].getSize(); // 1 for int/float/object, 2 for long/double
            }

            // Store args from stack to temp locals (reverse order - top of stack first)
            for (int i = paramTypes.length - 1; i >= 0; i--) {
                super.visitVarInsn(paramTypes[i].getOpcode(Opcodes.ISTORE), paramSlots[i]);
            }

            // Now the stack is clear of args - emit the deferred NEW + DUP
            super.visitTypeInsn(Opcodes.NEW, pendingNewClass);
            pendingNewClass = null;
            if (pendingDup) {
                super.visitInsn(Opcodes.DUP);
                pendingDup = false;
            }

            // Reload args from temp locals (forward order)
            for (int i = 0; i < paramTypes.length; i++) {
                super.visitVarInsn(paramTypes[i].getOpcode(Opcodes.ILOAD), paramSlots[i]);
            }

            // Stack is now: [..., uninit, uninit, arg1, arg2, ..., argN]
            // which is the correct order for INVOKESPECIAL <init>
        }

        /**
         * Push default values for parameter types described in a JVM descriptor fragment.
         * Used when a constructor gains new parameters - we insert defaults for them.
         * E.g., "Ljava/lang/String;[Ljava/lang/Object;" → push ACONST_NULL, then empty Object[]
         */
        private void pushDefaultsForDescriptor(String paramFragment) {
            int i = 0;
            while (i < paramFragment.length()) {
                char c = paramFragment.charAt(i);
                switch (c) {
                    case 'L' -> {
                        // Object type → push null
                        super.visitInsn(Opcodes.ACONST_NULL);
                        int end = paramFragment.indexOf(';', i);
                        if (end < 0) return; // malformed (no ';'): indexOf+1 would reset i to 0 and spin forever emitting ACONST_NULL
                        i = end + 1;
                    }
                    case '[' -> {
                        // Array type → push empty array
                        i++; // skip '['
                        if (i < paramFragment.length() && paramFragment.charAt(i) == 'L') {
                            // Object array: push ICONST_0 + ANEWARRAY
                            int end = paramFragment.indexOf(';', i);
                            if (end < 0) return; // malformed
                            String elementType = paramFragment.substring(i + 1, end);
                            super.visitInsn(Opcodes.ICONST_0);
                            super.visitTypeInsn(Opcodes.ANEWARRAY, elementType);
                            i = end + 1;
                        } else {
                            // Primitive array: push ICONST_0 + NEWARRAY
                            super.visitInsn(Opcodes.ICONST_0);
                            super.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT); // default to int[]
                            i++;
                        }
                    }
                    case 'I' -> { super.visitInsn(Opcodes.ICONST_0); i++; }
                    case 'J' -> { super.visitInsn(Opcodes.LCONST_0); i++; }
                    case 'F' -> { super.visitInsn(Opcodes.FCONST_0); i++; }
                    case 'D' -> { super.visitInsn(Opcodes.DCONST_0); i++; }
                    case 'Z' -> { super.visitInsn(Opcodes.ICONST_0); i++; } // false
                    case 'B', 'C', 'S' -> { super.visitInsn(Opcodes.ICONST_0); i++; }
                    default -> i++; // skip unknown
                }
            }
        }

        /**
         * Neutralize a call to a removed method: emit NO call, instead discard
         * the arguments (and the receiver for instance calls) and push a default
         * value for the return type. Turns a would-be {@code NoSuchMethodError}
         * into a silent no-op so the mod loads (soft-fail - the call's side
         * effect is lost). Only ever applied to methods that genuinely don't
         * exist on the host, registered via {@link #registerRemovedMethodNeutralize}.
         *
         * <p>These are plain pops/pushes inside a single basic block - no new
         * branches - so no stack-map frames are introduced; {@code COMPUTE_MAXS}
         * absorbs the (net non-increasing) stack delta.
         */
        private void neutralizeCall(int opcode, String descriptor) {
            Type methodType = Type.getMethodType(descriptor);
            Type[] argTypes = methodType.getArgumentTypes();
            // Pop arguments off the stack, last-pushed first.
            for (int i = argTypes.length - 1; i >= 0; i--) {
                super.visitInsn(argTypes[i].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
            }
            // Instance calls (virtual/special/interface) also have the receiver
            // below the args; static calls don't. The objectref is always 1 slot.
            if (opcode != Opcodes.INVOKESTATIC) {
                super.visitInsn(Opcodes.POP);
            }
            // Push a default for the return type so consumers of the result (and
            // the verifier) stay balanced.
            Type ret = methodType.getReturnType();
            switch (ret.getSort()) {
                case Type.VOID -> { /* nothing to push */ }
                case Type.OBJECT, Type.ARRAY -> super.visitInsn(Opcodes.ACONST_NULL);
                case Type.LONG -> super.visitInsn(Opcodes.LCONST_0);
                case Type.FLOAT -> super.visitInsn(Opcodes.FCONST_0);
                case Type.DOUBLE -> super.visitInsn(Opcodes.DCONST_0);
                default -> super.visitInsn(Opcodes.ICONST_0); // int/boolean/byte/char/short
            }
        }

        /**
         * Resolve class references in a descriptor through classRedirects.
         * E.g., "(Lclass_3702$class_3703;)V" → "(Lcom/mojang/blaze3d/platform/InputConstants$Type;)V"
         */
        private String resolveDescriptor(String descriptor) {
            if (classRedirects.isEmpty()) return descriptor;
            // Quick check: does descriptor contain any 'L' type references?
            if (descriptor.indexOf('L') < 0) return descriptor;

            StringBuilder sb = new StringBuilder(descriptor.length());
            int i = 0;
            while (i < descriptor.length()) {
                char c = descriptor.charAt(i);
                if (c == 'L') {
                    // Find the closing ';'
                    int end = descriptor.indexOf(';', i);
                    if (end < 0) break;
                    String className = descriptor.substring(i + 1, end);
                    String resolved = classRedirects.getOrDefault(className, className);
                    sb.append('L').append(resolved).append(';');
                    i = end + 1;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        }

        /**
         * Fix INVOKEVIRTUAL → INVOKEINTERFACE for classes that became interfaces.
         * E.g., com.mojang.serialization.DataResult was a class, now an interface in newer DFU.
         */
        private int fixClassToInterfaceOpcode(int opcode, String owner) {
            if (opcode == Opcodes.INVOKEVIRTUAL && KNOWN_INTERFACES.contains(owner)) {
                return Opcodes.INVOKEINTERFACE;
            }
            return opcode;
        }

        /**
         * Emit a method instruction, applying the invokespecial-on-non-direct-supertype
         * fixup if needed.
         *
         * <p>The JVM verifier requires that {@code INVOKESPECIAL} on a non-{@code <init>}
         * method target a <i>direct</i> supertype of the calling class - either the
         * direct superclass, a directly-declared superinterface, or {@code this} class
         * itself (for private methods). Anything else fails verification with:
         *
         * <pre>
         *   Bad invokespecial instruction:
         *   interface method to invoke is not in a direct superinterface.
         * </pre>
         *
         * <p>That can happen after a method redirect changes the owner. Example:
         * ModMenu's {@code ModListWidget.keyPressed(III)Z} was a normal super-call to
         * its concrete superclass. We redirected it to the new {@code keyPressed(KeyEvent)Z}
         * signature whose owner is now {@code GuiEventListener} (an interface that
         * {@code ModListWidget} only implements <i>indirectly</i>, through several
         * superclasses). The resulting INVOKESPECIAL fails verification.
         *
         * <p>Fix: rewrite the owner to {@link #classSuperName} so the JVM does a normal
         * class-hierarchy method resolution starting from the direct superclass. That
         * walk finds the inherited default method exactly the same way a regular
         * super-call does, but without the direct-superinterface restriction.
         *
         * <p>Constructor calls ({@code <init>}), private-method calls on {@code this},
         * and legitimate super/interface calls pass through unchanged.
         */
        private void emitMethodInsn(int opcode, String owner, String name,
                String descriptor, boolean isInterface) {
            if (opcode == Opcodes.INVOKESPECIAL
                    && !"<init>".equals(name)
                    && classSuperName != null
                    && !owner.equals(classSuperName)
                    && !owner.equals(classOwnName)
                    && !classDirectInterfaces.contains(owner)) {
                LOGGER.debug("Fixing invokespecial on non-direct supertype: {}.{}{} → super {}",
                        owner, name, descriptor, classSuperName);
                super.visitMethodInsn(Opcodes.INVOKESPECIAL, classSuperName, name,
                        descriptor, false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW) {
                // Flush any previous pending NEW before buffering a new one
                flushPendingNew();
                if (!constructorRedirects.isEmpty()) {
                    // Resolve through classRedirects since we see pre-remap names
                    // (e.g., class_2960) but redirects are registered with post-remap names (Identifier)
                    String resolvedType = classRedirects.getOrDefault(type, type);
                    // BUG: This creates a Stream on every NEW instruction in every class.
                    // Should use a pre-built Set<String> of class names with constructor redirects
                    // (similar to methodRedirectOwners) for O(1) lookup instead of O(n) scan.
                    boolean hasRedirect = constructorRedirects.keySet().stream()
                        .anyMatch(k -> k.className().equals(resolvedType));
                    if (hasRedirect) {
                        pendingNewClass = type;
                        return;
                    }
                }
            }
            // Non-NEW TypeInsns (ANEWARRAY, CHECKCAST, INSTANCEOF, etc.) may appear
            // as part of constructor arguments - don't flush the pending NEW
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.DUP && pendingNewClass != null && !pendingDup) {
                pendingDup = true;
                return;
            }
            // Don't flush - instructions like ICONST_0, ACONST_NULL can be constructor args
            super.visitInsn(opcode);
        }

        /**
         * Rewrites a constructor reference ({@code X::new}) whose constructor has a
         * registered constructor→factory redirect, so it points at the static factory.
         *
         * <p>{@code X::new} compiles to an {@code invokedynamic} whose implementation method
         * handle is {@code H_NEWINVOKESPECIAL X.<init>(args)V}; LambdaMetafactory adapts that
         * to the SAM {@code (args)->X}. Swapping it for {@code H_INVOKESTATIC factory(args)X}
         * yields the identical SAM, so the lambda links against the factory instead of the
         * removed constructor. This is the reference-form analogue of the direct
         * {@code new X(...)} rewrite in {@link #visitMethodInsn} (which only sees
         * {@code NEW}+{@code INVOKESPECIAL}, not the {@code invokedynamic} handle). Without it,
         * codecs that capture {@code ChunkPos::new} (Resourceful Lib's {@code ExtraByteCodecs})
         * still link to the deleted {@code ChunkPos(long)} ctor on 26.x. Same Mojang-named
         * lookup as the direct path, so it's effective wherever that one is.</p>
         */
        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (!constructorRedirects.isEmpty() && bootstrapMethodArguments != null) {
                for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                    if (!(bootstrapMethodArguments[i] instanceof org.objectweb.asm.Handle h)) continue;
                    if (h.getTag() != Opcodes.H_NEWINVOKESPECIAL || !"<init>".equals(h.getName())) continue;
                    String resolvedOwner = classRedirects.getOrDefault(h.getOwner(), h.getOwner());
                    String resolvedDesc = resolveDescriptor(h.getDesc());
                    FactoryTarget factory = constructorRedirects.get(new ConstructorKey(resolvedOwner, resolvedDesc));
                    if (factory == null && !resolvedDesc.equals(h.getDesc())) {
                        factory = constructorRedirects.get(new ConstructorKey(resolvedOwner, h.getDesc()));
                    }
                    if (factory != null) {
                        bootstrapMethodArguments[i] = new org.objectweb.asm.Handle(
                                Opcodes.H_INVOKESTATIC,
                                factory.factoryClass(), factory.factoryMethod(), factory.factoryDesc(),
                                false);
                        LOGGER.info("Constructor-reference redirect: {}::new -> {}.{}{}",
                                h.getOwner(), factory.factoryClass(), factory.factoryMethod(), factory.factoryDesc());
                    }
                }
            }
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }

        /**
         * Intercepts every method call instruction in the bytecode.
         *
         * <p>Processing order:</p>
         * <ol>
         *   <li><b>Constructor→factory:</b> If we have a pending NEW and this is an
         *       INVOKESPECIAL &lt;init&gt;, check if the constructor should be replaced
         *       with a static factory method (e.g., {@code new Identifier(s)} ->
         *       {@code Identifier.parse(s)}). The NEW+DUP are suppressed and replaced
         *       with INVOKESTATIC.</li>
         *   <li><b>Super constructor changes:</b> If this is a super() call (INVOKESPECIAL
         *       &lt;init&gt; without pending NEW), check if the parent class gained new
         *       required parameters. Push extra args before the call.</li>
         *   <li><b>Fast-path skip:</b> If the call's owner class has no registered method
         *       redirects, pass through immediately (only fixing class→interface opcode).</li>
         *   <li><b>Method redirect lookup:</b> Match against (owner, name, descriptor).
         *       If not found, try resolving intermediary class names in the descriptor
         *       since redirects are registered with Mojang names but bytecode may still
         *       have intermediary names in descriptors.</li>
         *   <li><b>Devirtualize:</b> If the redirect has devirtualize=true, change
         *       INVOKEVIRTUAL to INVOKESTATIC (the receiver becomes the first arg).</li>
         * </ol>
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                String descriptor, boolean isInterface) {

            // Check for constructor→factory redirect
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name) && pendingNewClass != null) {
                // Resolve owner through classRedirects (we see pre-remap names like class_2960)
                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                // Also resolve the descriptor - class refs in the descriptor (e.g., intermediary
                // class_3702$class_3703) need to be mapped to Mojang names (InputConstants$Type)
                // to match constructor redirects registered with Mojang names.
                String resolvedDesc = resolveDescriptor(descriptor);
                ConstructorKey ckey = new ConstructorKey(resolvedOwner, resolvedDesc);
                FactoryTarget factory = constructorRedirects.get(ckey);
                if (factory == null && !resolvedDesc.equals(descriptor)) {
                    // Also try with the original descriptor in case redirect was registered
                    // with pre-remap names
                    ckey = new ConstructorKey(resolvedOwner, descriptor);
                    factory = constructorRedirects.get(ckey);
                }
                if (factory != null) {
                    // Replace NEW+DUP+INVOKESPECIAL with INVOKESTATIC factory
                    String originalClass = pendingNewClass;
                    pendingNewClass = null;
                    pendingDup = false;
                    LOGGER.info("Constructor→factory redirect: new {}({}) -> {}.{}{}",
                            owner, descriptor, factory.factoryClass(), factory.factoryMethod(), factory.factoryDesc());
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, factory.factoryClass(),
                            factory.factoryMethod(), factory.factoryDesc(), false);
                    // If factory returns Object but the original class is specific,
                    // emit CHECKCAST to satisfy the JVM verifier.
                    // We cast to the ORIGINAL class - the factory must return a compatible type.
                    String factoryReturnType = factory.factoryDesc().substring(
                            factory.factoryDesc().lastIndexOf(')') + 1);
                    if (factoryReturnType.equals("Ljava/lang/Object;")) {
                        super.visitTypeInsn(Opcodes.CHECKCAST, originalClass);
                    }
                    return;
                }
                // Not a redirect for this descriptor - flush the buffered NEW+DUP.
                // Args are already on the stack (emitted during deferral), so we need
                // to reorder: store args → emit NEW+DUP → reload args.
                flushPendingNewBeforeArgs(descriptor);
            } else if (pendingNewClass != null && opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
                // Different class <init> (nested constructor) - don't flush, let it through
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            } else {
                // Non-<init> method calls are fine as constructor args - don't flush
            }

            // Check for super() constructor descriptor changes (no pending NEW = super call)
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                    && pendingNewClass == null && !superCtorRedirects.isEmpty()) {
                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                String resolvedDesc = resolveDescriptor(descriptor);
                ConstructorKey skey = new ConstructorKey(resolvedOwner, resolvedDesc);
                SuperCtorRedirect scr = superCtorRedirects.get(skey);
                if (scr == null) {
                    skey = new ConstructorKey(resolvedOwner, descriptor);
                    scr = superCtorRedirects.get(skey);
                }
                if (scr != null) {
                    if ("__INSERT_DEFAULTS__".equals(scr.extraFieldOwner())) {
                        // Insert default values for new parameters.
                        // Compare old and new descriptors to determine what's missing.
                        // For each new parameter type: push null (Object), 0 (int), etc.
                        String newParams = scr.newDesc().substring(1, scr.newDesc().indexOf(')'));
                        String oldParams = descriptor.substring(1, descriptor.indexOf(')'));
                        // Find the extra params that are in newDesc but not oldDesc
                        // Simple approach: parse parameter types from both
                        String extra = newParams.substring(oldParams.length());
                        pushDefaultsForDescriptor(extra);
                    } else if (scr.extraFieldOwner() != null) {
                        // Push extra argument from a static field
                        super.visitFieldInsn(Opcodes.GETSTATIC,
                            scr.extraFieldOwner(), scr.extraFieldName(), scr.extraFieldDesc());
                    }
                    LOGGER.info("Super ctor redirect: {}.{} -> {}",
                        owner, descriptor, scr.newDesc());
                    super.visitMethodInsn(opcode, owner, name, scr.newDesc(), isInterface);
                    return;
                }
            }

            // #70: rebased superclass - rewrite the super(...) <init> owner to the new base.
            // Scoped to the DIRECT super() call (owner == the class's original superclass,
            // and pendingNewClass == null so it's not a `new`), so it never touches other
            // references to the base type (e.g. a mixin @Inject handler capturing it as a
            // param - the Arcanus crash). Only fires for class→class rebases that opted in.
            if (opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)
                    && pendingNewClass == null
                    && classSuperNameOriginal != null && owner.equals(classSuperNameOriginal)
                    && !superclassRedirects.isEmpty()) {
                SuperclassRedirect rebase = superclassRedirects.get(owner);
                if (rebase != null && rebase.rewriteSuperCtor()) {
                    LOGGER.debug("Rebased super() owner: {}.<init>{} -> {}",
                            owner, descriptor, rebase.newSuperclass());
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, rebase.newSuperclass(),
                            name, descriptor, false);
                    return;
                }
            }

            // Removed-method NEUTRALIZATION (soft-fail): if this exact call
            // targets a method deleted on the host (e.g. a removed RenderSystem
            // state setter), drop it - pop the args (and receiver, for instance
            // calls) and push a default return - so the mod loads instead of
            // hitting NoSuchMethodError. Checked before the redirect fast-path
            // because neutralized owners are tracked in their own set.
            if (!neutralizedMethods.isEmpty()
                    && neutralizedMethodOwners.contains(owner)
                    && neutralizedMethods.contains(new MethodKey(owner, name, descriptor))) {
                LOGGER.trace("Neutralizing removed-method call {}.{}{}", owner, name, descriptor);
                neutralizeCall(opcode, descriptor);
                return;
            }

            // OPTIMIZATION: Fast path - skip if owner not in our redirect set.
            // But still try pattern heuristics and fuzzy resolution for unresolved
            // references, since they cover the entire MC API surface.
            if (!methodRedirectOwners.contains(owner)) {
                // Try pattern heuristics FIRST - faster and more reliable than fuzzy
                PatternHeuristics patterns = patternHeuristics;
                if (patterns != null) {
                    PatternHeuristics.PatternResult patternMatch = patterns.resolveMethod(owner, name, descriptor);
                    if (patternMatch != null && patternMatch.confidence() >= 0.6) {
                        int patternOpcode = fixClassToInterfaceOpcode(opcode, patternMatch.newOwner());
                        boolean patternIsInterface = patternOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                        emitMethodInsn(patternOpcode, patternMatch.newOwner(), patternMatch.newName(),
                                patternMatch.newDescriptor(), patternIsInterface);
                        return;
                    }
                }

                // Try fuzzy resolution if available (only for MC classes)
                FuzzyMethodResolver resolver = fuzzyResolver;
                if (resolver != null && resolver.isIndexed()
                        && (owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/"))) {
                    FuzzyMethodResolver.MethodInfo fuzzyMatch =
                            resolver.resolveMethod(owner, name, descriptor);
                    if (fuzzyMatch != null) {
                        LOGGER.debug("[Retromod-Fuzzy] Resolved {}.{}{} -> {}.{}{} (confidence: {}%)",
                                owner, name, descriptor,
                                fuzzyMatch.owner(), fuzzyMatch.name(), fuzzyMatch.descriptor(),
                                fuzzyMatch.score());
                        int fuzzyOpcode = fixClassToInterfaceOpcode(opcode, fuzzyMatch.owner());
                        boolean fuzzyIsInterface = fuzzyOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                        emitMethodInsn(fuzzyOpcode, fuzzyMatch.owner(), fuzzyMatch.name(),
                                fuzzyMatch.descriptor(), fuzzyIsInterface);
                        return;
                    }
                }

                // Still fix class→interface opcode even on fast path
                int fixedOpcode = fixClassToInterfaceOpcode(opcode, owner);
                boolean fixedIsInterface = fixedOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                emitMethodInsn(fixedOpcode, owner, name, descriptor, fixedIsInterface);
                return;
            }

            // Check if this method call needs to be redirected
            MethodKey key = new MethodKey(owner, name, descriptor);
            MethodTarget target = methodRedirects.get(key);

            // If not found, try resolving the descriptor through classRedirects
            // (bytecode has intermediary names like class_437 in descriptors,
            // but redirects are registered with Mojang names like Screen)
            if (target == null && !classRedirects.isEmpty()) {
                String resolvedDesc = resolveDescriptor(descriptor);
                if (!resolvedDesc.equals(descriptor)) {
                    MethodKey resolvedKey = new MethodKey(owner, name, resolvedDesc);
                    target = methodRedirects.get(resolvedKey);
                }
            }

            if (target != null) {
                // Redirect the call
                LOGGER.trace("Redirecting {}.{}{} -> {}.{}{}",
                        owner, name, descriptor,
                        target.owner, target.name, target.desc);

                // AUTO-DEVIRTUALIZE: an instance call whose redirect target takes
                // exactly one extra parameter can only mean "receiver becomes arg 0"
                // - an instance call cannot grow a parameter any other way (nothing
                // here pushes extra args). Dozens of bridge redirects were registered
                // through the 6-arg form without the devirtualize flag; emitting them
                // as INVOKEVIRTUAL pops receiver+arg where only the receiver was
                // pushed → stack underflow → ASM Frame.merge AIOOBE when Mixin
                // recomputes frames over the broken handler (the spawn-mod
                // MobBucketItemMixin bootstrap crash). Deciding at emit time fixes
                // every such registration at once, past and future.
                boolean devirt = target.devirtualize();
                if (!devirt
                        && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)
                        && org.objectweb.asm.Type.getArgumentTypes(target.desc()).length
                           == org.objectweb.asm.Type.getArgumentTypes(descriptor).length + 1) {
                    LOGGER.debug("Auto-devirtualizing redirect {}.{}{} -> {}.{}{} (receiver-as-arg0 shape)",
                            owner, name, descriptor, target.owner, target.name, target.desc);
                    devirt = true;
                }

                if (devirt) {
                    // Instance → static: change opcode and use static descriptor.
                    // INVOKESTATIC is never INVOKESPECIAL, so the helper is a no-op
                    // here, but use it for consistency with the other emission sites.
                    emitMethodInsn(Opcodes.INVOKESTATIC, target.owner, target.name,
                            target.desc, false);
                    // If return type changed (e.g., Object vs Either), emit CHECKCAST
                    // to satisfy the verifier's type checking
                    String origReturn = descriptor.substring(descriptor.lastIndexOf(')') + 1);
                    String newReturn = target.desc.substring(target.desc.lastIndexOf(')') + 1);
                    if (!origReturn.equals(newReturn) && origReturn.startsWith("L")) {
                        String origReturnClass = origReturn.substring(1, origReturn.length() - 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, origReturnClass);
                    }
                } else {
                    int fixedOpcode = fixClassToInterfaceOpcode(opcode, target.owner);
                    // Preserve isInterface from the original call so an interface
                    // INVOKESPECIAL stays an interface methodref; emitMethodInsn
                    // applies the direct-supertype fixup if the new owner isn't a
                    // direct supertype of the calling class.
                    boolean targetIsInterface = fixedOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                    emitMethodInsn(fixedOpcode, target.owner, target.name,
                            target.desc, targetIsInterface);
                    // Emit CHECKCAST when return type changed (e.g., Object vs Event)
                    String origReturn = descriptor.substring(descriptor.lastIndexOf(')') + 1);
                    String newReturn = target.desc.substring(target.desc.lastIndexOf(')') + 1);
                    if (!origReturn.equals(newReturn) && origReturn.startsWith("L")) {
                        String origReturnClass = origReturn.substring(1, origReturn.length() - 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, origReturnClass);
                    }
                }
            } else {
                // No hardcoded redirect found - try pattern heuristics first (fast, deterministic),
                // then fuzzy resolver as last resort (slower, probabilistic).
                // Neither will EVER override a hardcoded redirect (we only get here if none matched).

                // Pattern heuristics: deterministic naming convention rules (e.g., render* -> extract*)
                PatternHeuristics patterns = patternHeuristics;
                if (patterns != null) {
                    PatternHeuristics.PatternResult patternMatch = patterns.resolveMethod(owner, name, descriptor);
                    if (patternMatch != null && patternMatch.confidence() >= 0.6) {
                        LOGGER.debug("[Retromod-Pattern] Resolved {}.{}{} -> {}.{}{} (rule: {}, confidence: {})",
                                owner, name, descriptor,
                                patternMatch.newOwner(), patternMatch.newName(), patternMatch.newDescriptor(),
                                patternMatch.rule(), patternMatch.confidence());
                        int patternOpcode = fixClassToInterfaceOpcode(opcode, patternMatch.newOwner());
                        boolean patternIsInterface = patternOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                        emitMethodInsn(patternOpcode, patternMatch.newOwner(), patternMatch.newName(),
                                patternMatch.newDescriptor(), patternIsInterface);
                        return;
                    }
                }

                // Fuzzy resolver: scans the target MC JAR to find probable matches
                // based on class, name similarity, and parameter type matching.
                FuzzyMethodResolver resolver = fuzzyResolver;
                if (resolver != null && resolver.isIndexed()) {
                    FuzzyMethodResolver.MethodInfo fuzzyMatch =
                            resolver.resolveMethod(owner, name, descriptor);
                    if (fuzzyMatch != null) {
                        // Fuzzy match found with confidence >= 70% - apply the redirect
                        LOGGER.debug("[Retromod-Fuzzy] Resolved {}.{}{} -> {}.{}{} (confidence: {}%)",
                                owner, name, descriptor,
                                fuzzyMatch.owner(), fuzzyMatch.name(), fuzzyMatch.descriptor(),
                                fuzzyMatch.score());
                        int fuzzyOpcode = fixClassToInterfaceOpcode(opcode, fuzzyMatch.owner());
                        boolean fuzzyIsInterface = fuzzyOpcode == Opcodes.INVOKEINTERFACE || isInterface;
                        emitMethodInsn(fuzzyOpcode, fuzzyMatch.owner(), fuzzyMatch.name(),
                                fuzzyMatch.descriptor(), fuzzyIsInterface);
                        return;
                    }
                }

                // No redirect and no fuzzy match - pass through with opcode fixup.
                // The invokespecial-on-non-direct-supertype fixup is handled by
                // emitMethodInsn() - see its javadoc for the full reasoning.
                int fixedOpcode = fixClassToInterfaceOpcode(opcode, owner);

                // For KNOWN_INTERFACES (classes that became interfaces like DataResult):
                // - INVOKEVIRTUAL → INVOKEINTERFACE (handled by fixClassToInterfaceOpcode)
                // - INVOKESTATIC stays INVOKESTATIC but needs isInterface=true
                boolean fixedIsInterface = fixedOpcode == Opcodes.INVOKEINTERFACE || isInterface
                    || (opcode == Opcodes.INVOKESTATIC && KNOWN_INTERFACES.contains(owner));
                emitMethodInsn(fixedOpcode, owner, name, descriptor, fixedIsInterface);
            }
        }
        
        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            // Don't flush - ALOAD/ILOAD etc. are constructor arguments
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitLdcInsn(Object value) {
            // Don't flush - LDC is commonly a constructor argument
            super.visitLdcInsn(value);
        }

        /**
         * Intercepts every field access instruction (GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC).
         *
         * <p>Checks three redirect types in order:</p>
         * <ol>
         *   <li><b>Field accessor redirect:</b> The field became private in newer MC,
         *       so GETFIELD is replaced with INVOKEVIRTUAL getter() and PUTFIELD with
         *       INVOKEVIRTUAL setter(). This preserves the same stack behavior.</li>
         *   <li><b>Field-to-method redirect:</b> The field was removed entirely and
         *       replaced with a static method. Detected when newDesc starts with "(".
         *       GETSTATIC/GETFIELD becomes INVOKESTATIC.</li>
         *   <li><b>Field-to-field redirect:</b> The field was simply renamed or moved
         *       to a different class. The opcode stays the same.</li>
         * </ol>
         */
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Don't flush - field accesses can be constructor arguments
            FieldKey key = new FieldKey(owner, name);

            // Removed static field -> accessor-call sequence (26.2 ColorCollection:
            // Blocks.WHITE_CANDLE -> Blocks.DYED_CANDLE.pick(DyeColor.WHITE), cast Block).
            if (opcode == Opcodes.GETSTATIC) {
                StaticFieldAccessor sfa = staticFieldAccessors.get(key);
                if (sfa != null) {
                    super.visitFieldInsn(Opcodes.GETSTATIC,
                            sfa.collectionOwner(), sfa.collectionField(), sfa.collectionDesc());
                    if (sfa.argField() != null) {
                        super.visitFieldInsn(Opcodes.GETSTATIC,
                                sfa.argOwner(), sfa.argField(), sfa.argDesc());
                    }
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            sfa.methodOwner(), sfa.methodName(), sfa.methodDesc(), false);
                    if (sfa.castType() != null) {
                        super.visitTypeInsn(Opcodes.CHECKCAST, sfa.castType());
                    }
                    LOGGER.trace("Static-field accessor redirect: {}.{} -> {}.{}.{}()",
                            owner, name, sfa.collectionOwner(), sfa.collectionField(), sfa.methodName());
                    return;
                }
            }

            // Check field-to-accessor redirects first (GETFIELD→getter, PUTFIELD→setter)
            FieldAccessorTarget accessor = fieldAccessorRedirects.get(key);
            if (accessor != null) {
                if (opcode == Opcodes.GETFIELD) {
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            accessor.getterOwner(), accessor.getterName(), accessor.getterDesc(), false);
                    LOGGER.trace("Field accessor redirect: GETFIELD {}.{} -> {}.{}()",
                            owner, name, accessor.getterOwner(), accessor.getterName());
                } else if (opcode == Opcodes.PUTFIELD) {
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            accessor.setterOwner(), accessor.setterName(), accessor.setterDesc(), false);
                    LOGGER.trace("Field accessor redirect: PUTFIELD {}.{} -> {}.{}()",
                            owner, name, accessor.setterOwner(), accessor.setterName());
                } else {
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                }
                return;
            }

            // Check if this field access needs to be redirected
            FieldTarget target = fieldRedirects.get(key);

            if (target != null) {
                // Check if this is a field-to-method redirect
                if (target.newDesc != null && target.newDesc.startsWith("(")) {
                    // Convert GETSTATIC/GETFIELD to INVOKESTATIC
                    // (Field access becomes a static method call that returns the value)
                    int newOpcode = Opcodes.INVOKESTATIC;
                    super.visitMethodInsn(newOpcode, target.owner, target.name, target.newDesc, false);
                    LOGGER.trace("Redirected field {}.{} -> method {}.{}{}",
                            owner, name, target.owner, target.name, target.newDesc);
                    // If the polyfill method returns a wider type than the
                    // original field (e.g. Object vs Enchantment), the JVM
                    // verifier needs a CHECKCAST to satisfy the type expected
                    // at the use site. Skipping this emit causes
                    //   VerifyError: Bad type on operand stack
                    // when the next instruction tries to use the result as
                    // the original field's type. This mirrors the same
                    // CHECKCAST emit pattern used in method-to-method redirects.
                    String newReturn = target.newDesc.substring(target.newDesc.lastIndexOf(')') + 1);
                    if (descriptor != null && descriptor.startsWith("L")
                            && !descriptor.equals(newReturn)) {
                        String origClass = descriptor.substring(1, descriptor.length() - 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, origClass);
                    }
                } else {
                    // Standard field-to-field redirect
                    // Use new descriptor if provided, otherwise keep original
                    String newDescriptor = (target.newDesc != null) ? target.newDesc : descriptor;
                    super.visitFieldInsn(opcode, target.owner, target.name, newDescriptor);
                }
            } else {
                // No hardcoded field redirect - try fuzzy resolver as last resort.
                FuzzyMethodResolver resolver = fuzzyResolver;
                if (resolver != null && resolver.isIndexed()) {
                    FuzzyMethodResolver.FieldInfo fuzzyMatch =
                            resolver.resolveField(owner, name, descriptor);
                    if (fuzzyMatch != null) {
                        LOGGER.debug("[Retromod-Fuzzy] Resolved field {}.{} {} -> {}.{} {} (confidence: {}%)",
                                owner, name, descriptor,
                                fuzzyMatch.owner(), fuzzyMatch.name(), fuzzyMatch.descriptor(),
                                fuzzyMatch.score());
                        super.visitFieldInsn(opcode, fuzzyMatch.owner(), fuzzyMatch.name(),
                                fuzzyMatch.descriptor());
                        return;
                    }
                }

                // No redirect and no fuzzy match - pass through unchanged
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // KEY/TARGET RECORDS - used as map keys and values for redirect lookups.
    // Records give us free equals()/hashCode() which is critical for HashMap
    // performance since these are looked up on every method/field instruction.
    // ═══════════════════════════════════════════════════════════════════════

    /** Lookup key for method redirects: matches (owner class, method name, descriptor). */
    public record MethodKey(String owner, String name, String desc) {}
    /** Target of a method redirect. If devirtualize=true, instance calls become static. */
    public record MethodTarget(String owner, String name, String desc, boolean devirtualize) {
        /** Convenience constructor without devirtualize flag */
        public MethodTarget(String owner, String name, String desc) {
            this(owner, name, desc, false);
        }
    }
    /** Lookup key for field redirects: matches (owner class, field name). */
    public record FieldKey(String owner, String name) {}
    /** Target of a field redirect. If newDesc starts with "(", this is a field-to-method redirect. */
    public record FieldTarget(String owner, String name, String oldDesc, String newDesc) {}
    /** Target of a field accessor redirect: field access becomes getter/setter method call. */
    public record FieldAccessorTarget(
        String getterOwner, String getterName, String getterDesc,
        String setterOwner, String setterName, String setterDesc
    ) {}
    /**
     * A removed static field rewritten to an accessor call. GETSTATIC of the removed
     * field becomes: GETSTATIC collectionOwner.collectionField, optional GETSTATIC
     * argOwner.argField, INVOKEVIRTUAL methodOwner.methodName(methodDesc), optional
     * CHECKCAST castType. (argField/castType may be null.)
     */
    public record StaticFieldAccessor(
        String collectionOwner, String collectionField, String collectionDesc,
        String argOwner, String argField, String argDesc,
        String methodOwner, String methodName, String methodDesc,
        String castType
    ) {}
    /** Superclass rewrite: changes extends + adds interface implementations. */
    public record SuperclassRedirect(String newSuperclass, String[] addInterfaces, boolean rewriteSuperCtor) {}
    /** Lookup key for constructor redirects: matches (class being constructed, constructor descriptor). */
    public record ConstructorKey(String className, String constructorDesc) {}
    /** Target of a constructor→factory redirect: the static method to call instead. */
    public record FactoryTarget(String factoryClass, String factoryMethod, String factoryDesc) {}
    /**
     * Describes a super() constructor descriptor change.
     * When a subclass calls super(oldDesc), we redirect to super(newDesc)
     * by pushing extra arguments before the INVOKESPECIAL.
     *
     * @param newDesc the new constructor descriptor
     * @param extraFieldOwner class owning the extra static field to push (or null)
     * @param extraFieldName name of the static field to push as extra arg
     * @param extraFieldDesc descriptor of the static field
     */
    public record SuperCtorRedirect(String newDesc, String extraFieldOwner,
                                     String extraFieldName, String extraFieldDesc) {}
    
    public int getMethodRedirectCount() {
        return methodRedirects.size();
    }
    
    public int getClassRedirectCount() {
        return classRedirects.size();
    }
    
    public int getFieldRedirectCount() {
        return fieldRedirects.size();
    }
    
    public Map<MethodKey, MethodTarget> getMethodRedirects() {
        return Collections.unmodifiableMap(methodRedirects);
    }
    
    public Map<String, String> getClassRedirects() {
        return Collections.unmodifiableMap(classRedirects);
    }

    public Map<FieldKey, FieldTarget> getFieldRedirects() {
        return Collections.unmodifiableMap(fieldRedirects);
    }

    /**
     * Get intermediary method name mappings (method_XXXX → Mojang name).
     */
    public Map<String, String> getIntermediaryMethodNames() {
        return Collections.unmodifiableMap(intermediaryMethodNames);
    }

    /**
     * Get intermediary field name mappings (field_XXXX → Mojang name).
     */
    public Map<String, String> getIntermediaryFieldNames() {
        return Collections.unmodifiableMap(intermediaryFieldNames);
    }
}
