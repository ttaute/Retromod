# Design RFC: 26.x GUI 2D-transform migration (PoseStack to Matrix3x2fStack)

Status: Phase 0 shipped (1.3.0-snapshot.2, immediate-chain peephole); Phases 1-3 proposed.
Owner: unassigned. Target: a dedicated 1.x minor.

> **Update (Phase 0 shipped).** The immediate `guiGraphics.pose().pushPose()`/`.popPose()` peephole
> is implemented in `com.retromod.shim.common.Gui2DTransformMigration` and wired into the CLI
> `transformJar` and the runtime `ForgeModTransformer` (NeoForge/Forge, gated 26.1+). It turned out
> the dominant push/pop pattern is the IMMEDIATE chain (the `pose()` call adjacent to the void op,
> result consumed on the spot), which is a self-contained two-instruction peephole and needs NONE of
> the dataflow below - no taint, no local retyping. Verified on Jade (16 of 20 push/popPose migrated;
> 0 new ASM CheckClassAdapter errors). The dataflow engine below is still required for the STORED
> pattern (`PoseStack ps = guiGraphics.pose(); ps.pushPose()`, seen in AppleSkin/JEI) and for the
> arg-carrying ops (`translate`/`scale`/`mulPose`), which Phase 0 deliberately leaves untouched.
>
> **Calibration for the stored-pattern phase (measured on the corpus).** AppleSkin's overlay uses
> only `pushPose`/`popPose`/`translate`/`scale` (2/2/2/2) with no `last()` or `mulPose`, so it is
> FULLY coverable by Phases 1 below with zero text-path work. JEI is mostly coverable
> (`popPose` 24 / `pushPose` 22 / `translate` 17 / `scale` 4) with a small `last()` tail (3 sites)
> that stays on the Phase 2 text-render cluster. So the stored-pattern + `translate`/`scale` slice
> (no `mulPose`, no `last()`) is the high-value first increment: it clears AppleSkin entirely and the
> bulk of JEI.
>
> **Implementation notes from a spike (do not repeat these mistakes).** A stored-pattern engine was
> prototyped on `SourceInterpreter` + a taint fixpoint and then withdrawn unshipped, because the
> transform moves pixels and cannot be visually verified in this (headless) environment - shipping it
> without an in-game screenshot pass would be irresponsible. The spike surfaced two non-obvious traps
> that the real implementation must handle, and which make this bigger than it looks:
>
> 1. **`SourceInterpreter` local sources point at the `astore`, not the `pose()` call.** For a stored
>    stack, `frame.getLocal(n).insns` at an `aload n` is the set of `astore` instructions that reach
>    that point, NOT the originating `pose()` call. So a taint fixpoint has to trace two hops: an
>    `astore` "stores pose" iff its stored value's producers are all pose; a local "holds pose" iff
>    every reaching `astore` stores pose; an `aload` produces pose iff its local holds pose. You need
>    a `Map<producer-insn, Set<pose()-call>>` ("poseSrc") carried through the fixpoint, because the
>    thing you ultimately RETYPE (`pose()`'s return descriptor) is the `pose()` call, and the receiver
>    of an op is an `aload`/`dup`, several hops removed from it. Retyping the immediate producer
>    (the `aload`) is meaningless (it has no descriptor) - a bug the first draft had.
> 2. **Escapes must poison the `pose()` SOURCE, not the immediate producer.** If any pose-tainted
>    value is consumed by a non-handled use (passed to any other method as receiver-or-arg, stored to
>    a field, returned, array-stored), the originating `pose()` call(s) it derives from must be marked
>    un-migratable and left as a real 3D `PoseStack`, otherwise the escape site gets a `Matrix3x2fStack`
>    where a `PoseStack` is required. A `pose()` value is only legal as the RECEIVER of a handled op;
>    everything else is an escape.
>
> The safety net is real and worth stating: re-emit under `COMPUTE_FRAMES` and return the ORIGINAL
> bytes if it throws, plus a corpus `CheckClassAdapter.verify` gate. Together these guarantee the pass
> can only emit frame-valid bytecode or no-op - it cannot ship structurally broken classes. What they
> do NOT guarantee is pixel-correctness, which is exactly why the in-game screenshot pass (step 3 of
> Verification strategy) is the true acceptance gate and why this slice was not shipped blind.

## TL;DR

26.x split GUI rendering off the 3D `PoseStack` onto a 2D `org.joml.Matrix3x2fStack`, and
renamed `GuiGraphics` to `GuiGraphicsExtractor`. A 1.21.1 GUI mod that does
`guiGraphics.pose().pushPose()` / `translate()` / `scale()` / `mulPose()` no longer links, but
`PoseStack` still exists for 3D world rendering, so a type-blind `PoseStack -> Matrix3x2fStack`
redirect would corrupt 3D code. Doing this correctly needs a small **intraprocedural dataflow
pass** that taints the `PoseStack` values which originate from `GuiGraphics(Extractor).pose()`
and rewrites only those to the 2D API. This is a multi-day pass, not a redirect-table entry. A
naive accessor-insertion redirect was tried in the corpus-audit work and reverted: it produced a
double `pose()` on the real Jade mod because the receiver is the pose()-derived stack, not the
GuiGraphics.

## Background: what 26.x changed

Verified against the local 26.1 and 26.2 jars:

- `net/minecraft/client/gui/GuiGraphics` was renamed to `GuiGraphicsExtractor` (already handled by
  a class redirect in `Common_1_21_11_to_26_1_ClassMoves`).
- `GuiGraphics.pose()` used to return `com/mojang/blaze3d/vertex/PoseStack` (the 3D stack). On 26.x,
  `GuiGraphicsExtractor.pose()` returns `org/joml/Matrix3x2fStack` (a 2D transform stack).
- `PoseStack` STILL EXISTS on 26.2 (used by world/entity rendering). It is not going away.
- The 2D stack's op surface differs by name and arity:
  - push/pop: `PoseStack.pushPose()V` / `popPose()V` -> `Matrix3x2fStack.pushMatrix()` / `popMatrix()`
    (both return the stack fluently, so the void call becomes a call + POP).
  - translate/scale: `PoseStack.translate(FFF)V` / `scale(FFF)V` (3D) -> `Matrix3x2f.translate(FF)` /
    `scale(FF)` (2D: drop the z arg; the fluent return is popped).
  - `PoseStack.translate(DDD)V` -> `translate(FF)` also needs a `double -> float` narrowing on x,y and
    a pop of z.
  - `mulPose(Quaternionf)V` (3D rotation) -> `Matrix3x2f.rotate(F)` (2D: extract the Z-axis angle; see
    Risks - this is a GUI-common approximation, not a general 3D->2D map).
  - `last()` / `last().pose()` (extracting a `Matrix4f` for text/vertex draw) has no direct 2D analogue;
    it feeds the text-rendering path, a separate sub-cluster.

## The core problem: PoseStack is now overloaded

The bytecode type `com/mojang/blaze3d/vertex/PoseStack` now means two different things:

1. A **2D GUI stack**, when the value came from `GuiGraphics(Extractor).pose()`.
2. The **3D world stack**, everywhere else (entity/level rendering, still a real `PoseStack`).

A redirect keyed on `(owner=PoseStack, name, desc)` cannot tell them apart, so it would rewrite 3D
world-render calls too and corrupt them. The distinction is a **dataflow property of each value**,
not of the type. That is why this is not a redirect and needs an analysis pass.

## Current state (already broken, independent of the reverted attempt)

Today the transform leaves GUI pose usage mis-resolved: with no explicit `PoseStack -> *` move, the
fuzzy method resolver guesses `GuiGraphics.pose()`'s new form and picks a wrong descriptor
(observed: `GuiGraphicsExtractor.pose()` retyped to return `GuiGraphicsExtractor`), and the downstream
`pushPose()/translate()/...` calls stay unresolved. So GUI mods that apply pose transforms are
already broken on 26.2. This RFC replaces that with a correct, targeted migration.

## Design: intraprocedural "2D-PoseStack" taint pass

A new analysis pass (sits alongside the existing frame passes, gated to 1.21.5+/26.x, both loaders;
Fabric via the intermediary names of the same members).

### Sources (a value becomes tainted "2D")

- The return value of `GuiGraphics(Extractor).pose()`  (intermediary: the same method on Fabric).
- Optional later: a `PoseStack` parameter of a recognized GUI-render override whose caller is known
  to pass the 2D stack. Deferred to Phase 3 (interprocedural); Phase 0-2 are intraprocedural only.

### Propagation (within one method body)

Standard forward dataflow over the operand stack + locals:

- `astore n` of a tainted value taints local n; `aload n` of a tainted local yields a tainted value.
- `dup`/`dup_x1`/... copy taint.
- A fluent op on a tainted receiver (e.g. `pushMatrix()` returns the same stack) yields a tainted
  value again.
- Merge at control-flow joins: a value is tainted only if tainted on all in-edges (conservative;
  a mixed merge is treated as untainted and left as 3D, which is safe - it just declines to migrate).

Use ASM's `Analyzer` with a custom `Interpreter`/`Value` carrying a one-bit taint, or reuse the
existing frame infrastructure. Intraprocedural keeps it tractable and covers the overwhelmingly
common case (a GUI method grabs `guiGraphics.pose()` into a local and transforms it inline).

### Rewrite (only on tainted receivers)

When a call site's receiver is a tainted 2D `PoseStack`, rewrite per the op-mapping table below:
change the owner to `Matrix3x2fStack`/`Matrix3x2f`, rename the method, transform the args
(drop trailing z, narrow `double->float` where needed), and pop the now-fluent return if the old
method was void. Also retype the backing local variable and any `checkcast`/field descriptor to
`Matrix3x2fStack` so verification holds. Untainted `PoseStack` values are emitted unchanged.

### Op-mapping table (tainted PoseStack -> Matrix3x2fStack)

| old (PoseStack, 3D)        | new (2D)                                   | arg transform            | return |
|----------------------------|--------------------------------------------|--------------------------|--------|
| `pushPose()V`              | `Matrix3x2fStack.pushMatrix()`             | none                     | POP    |
| `popPose()V`               | `Matrix3x2fStack.popMatrix()`              | none                     | POP    |
| `translate(FFF)V`          | `Matrix3x2f.translate(FF)`                 | drop z                   | POP    |
| `translate(DDD)V`          | `Matrix3x2f.translate(FF)`                 | d2f on x,y; drop z       | POP    |
| `scale(FFF)V`              | `Matrix3x2f.scale(FF)`                     | drop z                   | POP    |
| `mulPose(Quaternionf)V`    | `Matrix3x2f.rotate(F)` via a Z-angle helper| quat -> Z angle (lossy)  | POP    |
| `last()`/`last().pose()`   | text-render path (Phase 2 sub-cluster)     | n/a                      | n/a    |

## Scope boundaries / non-goals

- This RFC is the **pose-stack** track. The audit also surfaced a parallel, largely-mechanical
  cluster: the `GuiGraphics` DRAWING API signature changes (`blit`, `text`/`drawString`, `setColor`,
  `flush`, ~30 methods on `GuiGraphicsExtractor`). Those have a fixed `GuiGraphics` receiver (no
  dataflow needed) and are better handled with ordinary method redirects / re-signatures in a
  separate, parallel track. Do not fold them in here.
- 3D world/entity `PoseStack` usage is explicitly out of scope and must remain untouched.

## Risks and known limits

- **Interprocedural gaps.** A mod that stashes the 2D stack in a field or passes it across methods
  won't be tainted by the intraprocedural pass; those call sites stay unmigrated (documented partial
  coverage, not a crash - they remain unresolved exactly as today).
- **`mulPose` is lossy.** A general 3D quaternion has no 2D rotation; only Z-axis rotations map. GUI
  rotations are almost always Z, so extract the Z angle and accept the approximation; guard by only
  migrating `mulPose` when the value is tainted 2D.
- **Verification is the gate, and it is visual.** Pose transforms move pixels. Frame-valid bytecode
  is necessary but not sufficient; the real gate is a screenshot/behaviour check in-game.
- **A wrong taint would corrupt 3D.** Mitigated by construction: sources are GUI-only, merges are
  conservative (untainted-on-doubt), and 3D `PoseStack` values are never a source.

## Verification strategy

1. Unit tests: the taint interpreter on synthetic methods (source -> local -> op; a control-flow
   merge; a decoy 3D `PoseStack` param that must stay untouched).
2. Real-mod transform + `CheckClassAdapter.verify` on the output (catches frame/stack errors without
   a running MC).
3. Headless load, then in-game on Jade / JEI / AppleSkin: confirm their HUD/overlays render in the
   right place (before/after screenshots). This is the true acceptance gate.

## Phasing and rough effort

- **Phase 0 (~1-2 days):** the taint engine (source = `pose()`, intraprocedural propagation, local
  retyping) + the no-arg ops (`pushPose`/`popPose`). Smallest end-to-end slice; verifiable on Jade.
- **Phase 1 (~1 day):** `translate`/`scale` (drop z, d2f).
- **Phase 2 (~1-2 days):** `mulPose` Z-angle helper + the `last()`/`Matrix4f` text-render path.
- **Phase 3 (optional):** interprocedural (field/param flow) if real mods need it.

Total: a multi-day dedicated project. The pass is the bulk; each op after the first is incremental.

## Alternatives considered and rejected

- **Blanket `PoseStack -> Matrix3x2fStack` class redirect.** Corrupts 3D world rendering. Rejected.
- **Accessor-insertion redirect** (`x.pushPose()` -> `x.pose().pushMatrix()`). Tried and reverted:
  the real receiver is already the pose()-derived stack, so it double-applies `pose()`. Rejected.
- **A Matrix3x2f-backed `PoseStack` polyfill** (duck-type both APIs). You cannot substitute the real
  `PoseStack` class (MC's own 3D code references it), so per-value substitution still needs the
  dataflow to know which values to swap. Rejected as a standalone approach.
- **Coarse heuristic** ("any `PoseStack` inside a `Screen` subclass is 2D"). Too coarse: GUIs render
  real 3D entities too (inventory mob previews use a genuine 3D `PoseStack`). Rejected.
