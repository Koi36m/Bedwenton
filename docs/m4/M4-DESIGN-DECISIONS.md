# M4 design decisions: particle sampling and distribution

Status: **PROPOSED (revision 2.1), not approved.** The contract and tests implement the **recommended** option of each
decision. Owner approval follows ChatGPT's independent review. Revision 2 changes DM4-5, DM4-6 and DM4-17 only, as
required by the R1 review's findings (`M4-R2-CORRECTIONS.md`). Revision 2.1 changes only the arithmetic of the panel
rule (DM4-17; `M4-R21-CORRECTIONS.md`). No decision was replaced.

Baseline: verified M3 `a6726c42a20940a6a6d2dd24477e6a642f6b8cbc` (254/254 tests; M3 production `d76258f`).

Notation:
- **[fact]**: checked by a test, probe or derivation (cited).
- **[pref]**: a design judgement.

Evidence: `validation/evidence.txt` (reference families) and `validation/design-probe.txt` (algorithm comparisons).

## Summary

| ID | Decision | Recommended | Main alternatives |
|---|---|---|---|
| DM4-1 | Scope | uniform-parameter and equal-distance sampling on curves, an arc-length table, seam policy, explicit limits | also area/surface distributions, randomness, emission scheduling |
| DM4-2 | Package and dependencies | `workshop.vfx.math.sample` using `core` and `curve` only | merge into `curve`; depend on `time` |
| DM4-3 | Uniform parameter vs uniform distance | both, as separate calls; distance needs an explicit table | distance only; parameter only |
| DM4-4 | Arc-length quadrature | globally adaptive 5-point Gauss-Legendre with a bisection error estimate | fixed-step chords; adaptive Simpson; Gauss-Kronrod; Romberg; depth-first refinement |
| DM4-5 | Tolerance | `tau = max(absolute, relative * initial length estimate, 1024 MIN_VALUE)`; reported, not thrown | absolute only; relative only; tolerance per panel |
| DM4-6 | Arc-length inversion | per-panel normalised Gauss primitive (ratio mode, linear fallback) with Newton for at most 16 iterations, then bisection (64-iteration cap; better bracket end on exhaustion) | dense lookup with linear interpolation; pure bisection; ODE reparameterisation; Newton guarded by a halving test |
| DM4-7 | Reuse | immutable `ArcLengthTable` built once; `SampleSet.transformed(Pose)` for moving effects | rebuild per tick; lazy per-call integration |
| DM4-8 | Seams and endpoints | explicit `Endpoints.INCLUDE_END` / `EXCLUDE_END` | infer from `Spiral.closed()`; always include both ends |
| DM4-9 | Counts | 0 empty, 1 start, reject above `maxSamples`; spacing count by round half up; saturating pre-check | clamp silently; ceil or floor of L / d |
| DM4-10 | Limits and budgets | explicit `SampleLimits` (samples, evaluations) with hard ceilings; never exceeded | time limits; implicit defaults; unbounded |
| DM4-11 | Degenerate geometry | scaled speed norm; zero length falls back to uniform parameters; stationary parts by monotone clamping | throw on zero length; special cusp handling |
| DM4-12 | Arbitrary `Curve` implementations | accepted; non-finite gives `ArithmeticException`; curve exceptions propagate; work bounded | only M3 types; catch and wrap |
| DM4-13 | Parameter ranges | any finite `t0 <= t1` (extrapolation allowed), default [0, 1] | [0, 1] only; reversed ranges |
| DM4-14 | Result representation | eager immutable `SampleSet` (parameters + points) | lazy iterators, streams, callbacks; float arrays |
| DM4-15 | Determinism and oracles | normative operation order (bit-identical across implementations); tests against independent oracles | bounds only, any algorithm |
| DM4-16 | Randomness, area distributions, emission, budgets | deferred (M4.1 / emission milestone) | in M4 |
| DM4-17 | Signed zero, subnormal, overflow | kept, scaled norm, `ArithmeticException`, saturation | flush, clamp |

## DM4-1. Scope of the minimal M4

| Candidate | Proposal | Reason |
|---|---|---|
| 1. Uniform parameter sampling | **in** | trivial and exact. It is the right tool for constant-speed curves (rings, arcs, helices, lines) |
| 2. Adaptive arc-length estimation | **in** | needed for even spacing on Beziers and radius-changing spirals |
| 3. Equal-distance sampling of any `Curve` | **in** | constellation links, trails, spirals around a trident |
| 4. Constant-speed reparameterisation | **in**, as `ArcLengthTable.parameterAt` | the same machinery; animations move samples along a path by distance |
| 5. Closed-curve seam policy | **in** (`Endpoints`) | healing rings need exactly one sample per seam |
| 6. Count and spacing APIs | **in** | effects specify density as count or as spacing |
| 7. Determinism with explicit inputs | **in** | multiplayer servers must agree with themselves |
| 8. Degenerate curves | **in** (policies) | collapsed pulses at radius 0, coincident Bezier handles |
| 9. Bounded cost | **in** (`SampleLimits`) | adversarial or mistaken inputs must not stall a tick |
| 10. Reusable distributions for circles, spirals and Beziers | **in**, through the `Curve` abstraction | no presets: any `Curve` works, including `PosedCurve` |
| Area and surface distributions (disk, sphere, Fibonacci sphere) | **deferred to M4.1** | different mathematics (area-preserving maps, low-discrepancy sets); no current use case needs them in M4 |
| Seeded randomness and jitter | **deferred to M4.1** | needs a specified, version-stable generator (DM4-16) |
| Emission scheduling (rates over time), particle budgets per tick or server | **deferred to the emission milestone** | combines M2 time with M4 samples; budgets are server policy, not geometry |
| Multi-segment paths with continuous spacing | **deferred to M4.1 with M3.1 paths** | depends on M3.1 piecewise paths |

[pref] Six public types cover the minimal scope. Every deferral can be added later without changing them.

## DM4-2. Package and dependencies

- **Recommended:** `workshop.vfx.math.sample`, depending on `core` (`Vec3`, `Pose`) and `curve` (`Curve`). No
  dependency on `time`. A source scan enforces this (`M4BoundaryTest`).
- **Alternatives:**
  - put sampling in `curve`: this would mix geometry with numerical integration and resource policy;
  - take M2 `TimingSample`: animation time is the caller's (an M3-style decision, DM3-13).

## DM4-3. Uniform parameter vs uniform distance

| | Uniform parameter | Uniform distance |
|---|---|---|
| definition | `t_k = t0 + (t1 - t0) k / den` | `L(t0, t_k) = L k / den` |
| cost | n point evaluations, no derivative | a table (at least 240 derivative evaluations) plus about 14 derivative evaluations per sample (`evidence.txt`, max 29) |
| exactness | bit-for-bit formula; exact endpoints | within the table tolerance; exact endpoints |
| spacing on constant-speed curves | equal, up to rounding | equal |
| spacing on Beziers and conical spirals | uneven (bunches where the speed is low) | even |

- **Recommended:** both, as separate explicit calls. Distance sampling requires a table the caller builds explicitly,
  so its cost is visible and reusable.
- [fact] On a circle, uniform-parameter chords are equal to 1e-13 (`UniformParameterTest.constantSpeedEquality`).
  Healing rings and expanding pulses therefore need no table: `countForSpacing(2 pi r, d, EXCLUDE_END)` then
  `uniformParameter`.

## DM4-4. Arc-length quadrature

Candidates, compared on the reference families (`validation/design-probe.txt`, `validation/evidence.txt`):

| Method | Error estimate | Accuracy at equal cost | Notes |
|---|---|---|---|
| fixed-step chord polyline (n points) | none | worst relative error 6.5e-2 at the cost GL5 needs for 2e-8 (336 rows, rel 1e-7) | always underestimates; no convergence signal |
| adaptive Simpson | Richardson, `O(h^4)` | slower convergence for smooth speeds | 3-point rule, more panels |
| Gauss-Kronrod 7/15 | embedded, `O(h^30)` | excellent | 15 constants to transcribe; no closed form for the Kronrod nodes |
| Romberg | Richardson table | good for smooth speeds, poor near kinks | not local; needs uniform refinement |
| **5-point Gauss-Legendre with bisection estimate** | `abs(G(a,m) + G(m,b) - G(a,b))` | constant speeds exact in 240 evaluations; all 336 reference rows converged with error at most 0.31 tau | 5 constants with closed forms (verified at 60 digits); exact for polynomial speeds up to degree 9 |

Refinement order:
- **Depth-first, left to right, with tolerance per panel** (an earlier scratch variant): under a binding budget it
  spends everything on the leftmost panels. Example: the oscillating curve at 65,536 evaluations ended with an
  estimate of 0.35.
- **Global, largest-error-first:** the same budget gives 7.0e-8, and 81,120 evaluations converge
  (`evidence.txt`).

**Recommended:** globally adaptive 5-point Gauss-Legendre, with a 16-panel initial partition, the largest-error panel
split first (deterministic tie-breaks) and the running sum of estimates compared with tau (contract 5.3). The
initial partition of 16 panels:
- catches features narrower than [t0, t1];
- makes constant-speed curves converge with no refinement;
- fixes the minimum cost at 240 evaluations.

## DM4-5. Tolerance definition

- **Recommended:** `tau = max(absolute, relative * L0, 1024 MIN_VALUE)`, with `L0` the initial 16-panel estimate.
  - R2: the floor `1024 MIN_VALUE` (contract 5.3) prevents `tau = 0` when `relative * L0` underflows for a
    subnormal length. With `tau = 0`, convergence of a constant-speed curve depended on the last bits of the
    rounding (finding 1). It changes nothing for lengths of at least `2^-1022`.
  - Relative tolerance makes the work independent of the curve's scale (a ring of radius 1 and of radius 100 cost
    the same).
  - Absolute tolerance expresses a block-scale requirement, such as 1/16 block.
  - `relative` must lie in [2^-40, 2^-3]. Below 2^-40 rounding dominates (10.2); above 1/8 the estimate means
    nothing.
- **Alternatives:**
  - absolute only: the work explodes for large curves and is wasted on small ones;
  - relative to the final length: circular (unknown up front);
  - tolerance per panel (`tau * width / range`): it over-refines, and under a budget it starves the right-hand
    panels (DM4-4).
- [fact] `converged()` is exactly `errorEstimate() <= tau` (normative B3). Whether the **achieved** error is at most
  tau is validated, not guaranteed (10.3): it held for all 336 x 4 reference cases.
- **Failure policy:** not converging within the budget is not an exception. The table is still usable, and the caller
  decides by `converged()`. A VFX can draw slightly uneven spacing; it must not fail a server tick.

## DM4-6. Arc-length inversion

- **Recommended:**
  - Find the panel by binary search on the cumulative distances.
  - Inside a panel, use the 5-point primitive `F(t) = G(a, t)` scaled by `I2 / I1`, so that `F(b) = I2` and adjacent
    panels join without a jump. R2: this **ratio mode** applies only when `1/2 <= I2 / I1 <= 2`. Otherwise the panel
    is grossly unresolved, and `F` is linear in t (contract 5.5). `F` is then never NaN and always in `[0, I2]`
    (finding 2).
  - Solve `F(t) = r` inside a strictly shrinking bracket. Newton steps (derivative: the speed) are allowed in the
    first 16 iterations, then only bisection. There are at most 64 iterations. On a cap or collapse exit, the
    result is the bracket end with the smaller residual (contract 5.6, finding 3).
  - Stop at a residual of `tau * 2^-10`, also when a bracket end already meets it.
- [fact]
  - Proven (contract P3): after the 64th iteration the bracket is at most `2^-47` of the panel width, plus 2 ulp.
    R1's claim that "64 bisections reduce any bracket below one ulp, so the loop terminates with ulp accuracy" did
    not hold, because a Newton step only has to land strictly inside the bracket. A speed spike near the root made
    R1 creep through all 64 iterations with a residual of 2.7e-8 (`validation/r2/findings-r1.txt`).
  - The measured cost is 13.8 derivative evaluations on average and 29 at most (20,979 inversions; R1: 14.0 and 29).
  - The contract's bound is 336 (R1: 400).
- **Alternatives:**
  - a dense table with linear interpolation of t(s): an error `O(h^2)` with no estimate, and memory proportional to
    the accuracy;
  - pure bisection: about 180 derivative evaluations per sample at relative 2^-30 (contract 6.3), against 13.8 measured;
  - Newton allowed only after a step that halved the bracket. This is also provable (`2^-32`), but it disables
    Newton after every one-sided step, and the mean cost on the reference spiral rose to about 57 (the validated
    limit is 40) (`validation/r2/inversion-stats.txt`);
  - ODE-based reparameterisation: drift with no bound.
- Monotonicity: samples use `t_k = max(t_(k-1), parameterAt(s_k))`, so the parameters never go backwards, even where
  the speed vanishes (stationary intervals, cusps).

## DM4-7. Reusing preprocessing

- [fact] Arc length is invariant under rotation and translation and scales with uniform scale (M1 `Pose` has only
  uniform scale) (`ArcLengthTableTest.invariances`).
- **Recommended:**
  - Build the table once for the local curve, and sample once.
  - For a moving effect (a spiral following a trident's pose each tick), call `SampleSet.transformed(pose)`. Its
    points are bit for bit `curve.transformed(pose).point(t_k)` (`SampleSetTest`), at the cost of one `Pose.toWorld`
    per point: no curve evaluation and no integration.
  - Moving samples **along** a static path (orbiting stars) use `table.parameterAt(s)` per particle per tick, at
    about 14 derivative evaluations each.
- **Alternatives:**
  - rebuild every tick: at least 240 evaluations per tick for nothing;
  - lazy integration on each query: unbounded repeated work.

## DM4-8. Seams and endpoints

- **Recommended:** an explicit `Endpoints` argument.
  - `INCLUDE_END` gives fractions `k/(n-1)`, both ends included (open paths, constellation links).
  - `EXCLUDE_END` gives fractions `k/n`, with no seam duplicate (closed loops: healing rings, orbits).
- **Alternatives:**
  - infer from `Spiral.closed()`: works only for spirals (Bezier loops and user curves have no `closed()`), and
    hides the policy;
  - always include both ends: duplicates the seam particle on every ring (double brightness at one point).
- [fact] `EXCLUDE_END` on a circle puts the last sample one spacing before the seam (`EqualDistanceTest`).
  `INCLUDE_END` repeats the seam point within 1e-12.

## DM4-9. Counts

| Input | Result | Why |
|---|---|---|
| count 0 | empty set | valid "nothing to draw" |
| count 1 | one sample at t0 (both policies) | the start is the natural representative; consistent with sample 0 of larger counts |
| count < 0 or > `maxSamples` | `IllegalArgumentException`, before any evaluation | silent clamping would hide a bug and change the density without notice |
| spacing d | `m = round-half-up(fl(L / d))` intervals; `INCLUDE_END: m + 1`, `EXCLUDE_END: max(1, m)` | nearest count keeps the realised spacing `L/m` closest to d. Exactly specified by an explicit floor rule, which equals Java's `Math.round` for `0 <= q < 2^62` (mutation M20 confirmed the equivalence) |
| zero length with spacing | one sample | no duplicate points on a collapsed pulse |
| huge requests | `countForSpacing` saturates at `Long.MAX_VALUE`; `bySpacing` rejects above `maxSamples` before allocating | prevents accidental particle explosions (spacing 1e-300) |

Rounding alternatives:
- `ceil`: spacing at most d, always denser, more particles;
- `floor`: always sparser, gaps at the end.

[pref] Callers wanting a conservative budget check `countForSpacing` first (pure, no allocation) and choose a coarser
spacing.

## DM4-10. Limits and budgets

- **Recommended:** `SampleLimits(maxSamples, maxEvaluations)` is an explicit argument of every call that can grow.
  - Hard ceilings: 2^20 samples and 2^24 evaluations.
  - Minimum budget: 256 evaluations, covering the 240 of the initial pass.
  - There is **no default instance**. Effects must choose: for example, a frequently triggered enchantment
    `(256, 4096)`, or a heavy one-off spell `(4096, 65536)`. Those values are examples, not recommendations; see
    `M4-RISKS.md`.
- **Guarantees (normative):** the work bounds of contract section 8 hold in **evaluation counts**, which are
  deterministic, rather than in time, which varies by machine.
- **Alternatives:**
  - wall-clock deadlines: non-deterministic;
  - implicit global defaults: hidden policy, and mutable if configurable;
  - no limits: one wrong spacing stalls a server tick.

## DM4-11. Degenerate geometry

- **Speed.** A scaled norm `m sqrt((x/m)^2 + ...)`. [fact] The naive `sqrt(x^2 + y^2 + z^2)` overflows for
  components above 1e154 and underflows to 0 below 1e-162. The scaled norm is accurate from subnormal to 1e308
  (`M4AdversarialTest.magnitudes`).
- **Zero length** (collapsed curves, radius-0 pulses, coincident Bezier points):
  - `length() == 0`, converged;
  - `uniformDistance` falls back to uniform parameters (every point is the same anyway);
  - `bySpacing` returns one sample.
- **Stationary points and cusps:** the speed has a zero or a kink there. Adaptive refinement isolates it, and all
  cusp rows converge (the kink at 5/32, 13/64 and 21/32, not on an initial node). Inversion is bracketed, and the
  sample parameters are clamped monotone.
- **Alternatives:**
  - throwing on zero length: breaks pulses that start at radius 0;
  - explicit cusp detection: needs second derivatives and thresholds, and adaptive refinement already handles cusps.

## DM4-12. Arbitrary `Curve` implementations

- **Recommended:** accept any `Curve`, including user implementations and `PosedCurve`.
  - Non-finite derivative components give `ArithmeticException`, raised as soon as one is evaluated.
  - Exceptions from the curve propagate unchanged.
  - Every call's work is bounded (contract 8).
- **Not guaranteed for user curves:** accuracy (a discontinuous or wildly oscillating speed may not converge;
  `converged()` reports it) and determinism (a curve that is not a pure function makes results arbitrary, but still
  bounded).
- **Alternatives:**
  - restrict to M3 types: blocks custom effects;
  - catch and wrap curve exceptions: hides user bugs.

## DM4-13. Parameter ranges

- **Recommended:** any finite `t0 <= t1` with a finite width, defaulting to [0, 1].
  - Extrapolation follows the curve (M3 DM3-2): sample a Bezier slightly beyond its ends, or several turns of a
    spiral by range.
  - `t0 == t1` is valid (zero length).
- **Alternatives:**
  - [0, 1] only: forces callers to build sub-curves;
  - reversed ranges: callers can reverse the arrays; a direction flag is deferred (N).

## DM4-14. Result representation

- **Recommended:** an eager, immutable `SampleSet` holding parameters (copied out) and points (`Vec3`, immutable).
  - Allocation is O(n), bounded by `maxSamples`.
  - Parameters let callers evaluate tangents (`curve.derivative(t_k)`) or colours along the path.
- **Alternatives:**
  - lazy iterators or streams: work is deferred into rendering code, and the evaluation count becomes caller
    dependent;
  - callbacks: same, and harder to test;
  - raw float arrays: lose `Vec3` and precision.
- Allocation-free per-tick use (orbits) is available through `ArcLengthTable.parameterAt` plus `Curve.point`.

## DM4-15. Determinism and test oracles

- **Recommended:**
  - (a) The contract fixes the operation order (sections 4 to 6), so every conforming implementation is bit
    identical on every JVM. This is stronger than M3's per-implementation determinism, because sampling feeds
    multiplayer-visible particle positions, and the algorithm is small enough to fix completely.
  - (b) The tests do **not** encode the algorithm. They check exact properties and independent oracles:
    - closed forms (lines, constant-speed spirals);
    - the asinh antiderivative for quadratic Beziers and spirals;
    - a 20-point Gauss-Legendre oracle at 40 digits for cubics (1e-28), checked against the closed forms;
    - BigDecimal statements of the normative formulas;
    - metamorphic relations: pose invariance, additivity, density;
    - bitwise repeatability.
- **Alternative:** bounds only, any algorithm. Weaker determinism, and evaluation counts would no longer be
  predictable.

## DM4-16. Randomness, area distributions, emission, budgets (deferred)

- **Randomness or jitter:** deferred to M4.1. It requires a generator whose output is fixed by the contract.
  - `java.util.Random` is specified, but statistically weak.
  - `SplittableRandom`'s algorithm is not a specification guarantee.
  - M4.1 should specify its own small generator (for example SplitMix64, written from its published definition) and
    seed derivation per effect instance.
- **Area and surface distributions:** deferred to M4.1 (area-preserving disk maps, Fibonacci spheres, ring bands),
  with their own oracles.
- **Emission scheduling and density budgets:** deferred to the emission milestone. They combine M2 time with M4
  sampling and server policy (particles per tick per player), which is not geometry.

## DM4-17. Signed zero, subnormal values, underflow and overflow

- **Signed zero:** the first parameter is `t0` itself, so `-0.0` is kept (`parameterAt(0)` and sample 0). Other
  parameters follow IEEE arithmetic.
- **Subnormal values:**
  - geometry down to subnormal coordinates has accurate lengths (scaled norm);
  - subnormal speeds contribute absolute errors of a few `MIN_VALUE` (M3 A3): at most `MIN_VALUE / 2` per half-panel
    rule of nonzero width (contract 10.2, R2.1; R2 stated `8 K MIN_VALUE`);
  - R2.1: the panel rule multiplies by the width itself, never by a half-width that can underflow to 0. It scales
    the speeds by their largest component, so no intermediate overflows unless the length does (contract 5.2,
    findings F4 and F5);
  - the effective tolerance never underflows below `1024 MIN_VALUE` (DM4-5, R2).
- **Overflow:**
  - a non-finite derivative or length gives `ArithmeticException`;
  - a range whose width overflows gives `IllegalArgumentException`;
  - counts saturate.
- **Alternatives:** flushing subnormals or clamping infinities would hide errors and break the M3 A3 accuracy model.

## What changes if the owner picks an alternative

| If | Revise |
|---|---|
| DM4-1 adds randomness or area distributions | new contract sections, generator, and oracles for distributions (statistical tests with fixed seeds) |
| DM4-4 uses Gauss-Kronrod or chords | contract 5.2 and 5.3, B5, the evaluation-count tests; the reference data stays |
| DM4-9 clamps instead of rejecting | `UniformParameterTest.validation`, `M4AdversarialTest.explosionGuard`, `EqualDistanceTest.spacingGuards` |
| DM4-10 adds a default `SampleLimits` | one constant, plus API-shape test changes |
| DM4-15 relaxes to bounds only | delete the normative order; tests unaffected except B5 (`panelCount == 16`) and the evaluation counts |
