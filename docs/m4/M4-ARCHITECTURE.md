# M4 architecture proposal: particle sampling and distribution

Status: **PROPOSED (revision 2.1), not approved** (see `M4-DESIGN-DECISIONS.md`; changes in `M4-R2-CORRECTIONS.md` and
`M4-R21-CORRECTIONS.md`).
- Baseline: verified M3 `a6726c42a20940a6a6d2dd24477e6a642f6b8cbc`.
- GitHub Actions run 38875973066 passed 254/254 tests (M1 125, M2 66, M3 51, M3 review 12) on Java 25 and Gradle 9.1.0,
  with no production dependencies.
- For this proposal, I reran the 254 tests locally against the baseline (`validation/reference-run-summary.txt`). The
  Actions result itself is reported by the owner.

## 1. Purpose and boundary

M4 turns M3 curves into **finite, deterministic sets of sample positions**: where particles should appear. It
decides **no** particle type, colour, timing, network packet or server budget. A later Paper adapter renders the
positions, and a later emission milestone decides when and how many.

```
workshop.vfx.math.core    (M1, unchanged)  Vec3, Quat, Pose, Frame
workshop.vfx.math.time    (M2, unchanged)  Playback, Timing, TimingSample           (not used by M4)
workshop.vfx.math.curve   (M3, unchanged)  Curve, LineSegment, QuadraticBezier, CubicBezier, Spiral, PosedCurve, Easing
workshop.vfx.math.sample  (M4, new)        Endpoints, SampleLimits, ArcLengthTolerance, ArcLengthTable, SampleSet, Sampler
                                           depends on core and curve only
```

A typical effect chains the layers. Each step is a pure function, and the caller holds all state:

```
tick -> M2 TimingSample.progress -> M3 Easing / curve parameters -> M3 Curve
     -> M4 ArcLengthTable.build (once per shape) -> Sampler.uniformDistance / bySpacing -> SampleSet (local)
     -> SampleSet.transformed(M1 Pose of this tick) -> Paper adapter spawns particles at the points
```

## 2. Phase A: scope evaluation

| # | Candidate | Decision | Where |
|---|---|---|---|
| 1 | Uniform parameter sampling | in | `Sampler.uniformParameter` (bit-for-bit formula) |
| 2 | Adaptive arc-length estimation | in | `ArcLengthTable.build` (globally adaptive Gauss-Legendre, DM4-4) |
| 3 | Equal-distance sampling of any `Curve` | in | `Sampler.uniformDistance`, `Sampler.bySpacing` |
| 4 | Constant-speed reparameterisation | in | `ArcLengthTable.parameterAt` / `lengthAt` |
| 5 | Closed-curve seam policy | in | `Endpoints.EXCLUDE_END` |
| 6 | Count and spacing APIs | in | count arguments, `bySpacing`, `countForSpacing` |
| 7 | Determinism with explicit inputs | in | normative operation order (DM4-15) |
| 8 | Degenerate curves | in | zero-length fallback, scaled norm, monotone clamping (DM4-11) |
| 9 | Bounded cost and failure policies | in | `SampleLimits`, work bounds in evaluations (contract 8) |
| 10 | Reusable distributions | in, through `Curve` | no presets; `PosedCurve` and `SampleSet.transformed` |
| | Area and surface distributions | deferred to M4.1 | section 6 |
| | Seeded randomness and jitter | deferred to M4.1 | section 6 |
| | Emission scheduling, particle-density budgets | deferred to the emission milestone | section 6 |

## 3. Phase C: how the example effects use M4 (functional examples only; no presets in M4)

| Effect | Construction |
|---|---|
| **Astral Prism**: constellation connections | each link is a `LineSegment` (constant speed): `countForSpacing(length, d, INCLUDE_END)` then `uniformParameter`. No table is needed. Curved links (`CubicBezier`): `ArcLengthTable.build` once, then `bySpacing(table, d, INCLUDE_END, limits)` |
| Astral Prism: evenly spaced orbital star trails | an orbit `Spiral.arc` or `circle` (constant speed): `uniformParameter(..., EXCLUDE_END)` for a full ring. A moving star at distance s along a static path: `table.parameterAt(s)`, then `curve.point(t)` per tick |
| Astral Prism: controllable trail density | spacing d or count n chosen per intensity setting; `countForSpacing` predicts the particle count before any work |
| **Cloud Weaving**: water spirals around a trident | `Spiral.conicalSpiral(r0, r1, height, turns)` in local coordinates (the radius changes, so the speed varies along the curve). `ArcLengthTable` once, `bySpacing` for constant spacing, `SampleSet.transformed(pose)` each tick with the trident's M1 `Pose` (`Quat.shortestArc(Vec3.Y, axis)`) |
| Cloud Weaving: consistent spacing despite radius changes | equal distance (not equal parameter) by construction. The spacing is `L/m`, within half a sample of d per curve (`EqualDistanceTest.spacing`) |
| Cloud Weaving: moving sample positions | `SampleSet.transformed(pose)`: bit-identical to sampling the posed curve, with no re-integration |
| **Crimson Grace / Full Heal**: healing rings | `Spiral.horizontalClockwiseFromAbove(r)` (Full Heal orientation), `uniformParameter(ring, n, EXCLUDE_END, limits)`: exactly n samples, no seam duplicate |
| Expanding pulses | a new ring radius per tick. Count from `countForSpacing(2 pi r, d, EXCLUDE_END)` (pure, no allocation) or a fixed n, then `uniformParameter`. A radius-0 pulse gives coincident points (no failure) |
| **Future enchantments**: simple vs heavy intensity | two `SampleLimits` and spacings chosen by the effect. Requests above the limit fail fast (`IllegalArgumentException`) rather than silently thinning; `countForSpacing` lets the effect pick a coarser spacing instead |
| Frequently triggered effects | constant-speed shapes avoid tables entirely. Shapes that need a table are built once and reused (immutable, thread-safe) |
| Predictable server performance | every call's work is bounded in curve evaluations (contract 8), independent of input values |

## 4. Public API (summary; normative in the contract, section 2)

| Type | Role |
|---|---|
| `Endpoints` | `INCLUDE_END` (open path), `EXCLUDE_END` (closed loop, no seam duplicate) |
| `SampleLimits(maxSamples, maxEvaluations)` | explicit computational limits with hard ceilings |
| `ArcLengthTolerance(absolute, relative)` | the requested accuracy of lengths and spacing |
| `ArcLengthTable` | immutable, reusable arc-length measurement and inversion for one curve and range |
| `SampleSet` | immutable result: parameters and points; `transformed(Pose)` |
| `Sampler` | static entry points: `uniformParameter`, `uniformDistance`, `bySpacing`, `countForSpacing` |

## 5. Reuse of M1 and M3

- Positions come from M3 `Curve.point`, and speeds from `Curve.derivative`. M4 never differentiates numerically.
- Placement uses M1 `Pose.toWorld` (through `SampleSet.transformed`, or `Curve.transformed` before sampling).
- Tangents and frames for oriented particles use `curve.derivative(t_k)` with M1 `normalizedOr` and
  `Frame.transportY`. M4 adds no frame API.
- No M1, M2 or M3 file changes, and the M3 public API is unchanged.

## 6. Scope boundary and deferred improvements

| Item | Proposed home | Reason |
|---|---|---|
| Area and surface distributions: disk (area-preserving), annulus, sphere (Fibonacci lattice), cylinder band | M4.1 | different mathematics and oracles (area preservation, discrepancy) |
| Seeded randomness and jitter (positions, phases) | M4.1 | needs a contract-specified generator (SplitMix64 from its published definition) and per-instance seed derivation |
| Multi-segment paths with continuous spacing across joints | M4.1, after M3.1 paths | depends on M3.1 piecewise `Path` |
| Centred sampling (`(k + 1/2)/n`), reversed direction | M4.1 if requested | trivial additions; no current use case |
| Emission scheduling (rates, bursts, lifetimes) | emission milestone | combines M2 time with M4 samples |
| Particle-density budgets per tick, player or server | emission milestone or Paper adapter | server policy, not geometry |
| Combined evaluation (point, tangent and distance in one call) | M4.1 if profiling shows need | API addition; M3 recomputes trigonometry per method (M3 review N-5) |
| Paper / Bukkit adapter | separate integration milestone | must stay outside the math engine |

## 7. Deliverables of this proposal

| Item | Location |
|---|---|
| design decisions DM4-1 to DM4-17 | `docs/m4/M4-DESIGN-DECISIONS.md` |
| implementation contract (API, normative algorithm, guarantees) | `docs/m4/M4-IMPLEMENTATION-CONTRACT.md` |
| numerical conventions and supported domains | `docs/m4/M4-CONVENTIONS.md` |
| error-bound derivations and evidence | `docs/m4/M4-ERROR-ANALYSIS.md` |
| performance and resource risks | `docs/m4/M4-RISKS.md` |
| test specification and acceptance criteria | `docs/m4/M4-TEST-SPECIFICATION.md` |
| Codex handoff guide (for after approval) | `docs/m4/README-FOR-CODEX-M4.md` |
| independent tests | `src/test/java/workshop/vfx/math/sample/` (59 tests in 10 classes, plus a helper) |
| R2 corrections after the R1 review | `docs/m4/M4-R2-CORRECTIONS.md` |
| R2.1 corrections after the R2 review | `docs/m4/M4-R21-CORRECTIONS.md` |
| reference data and generator | `src/test/resources/m4/reference/` (3,064 rows), `tools/generate_m4_reference_vectors.py` |
| validation evidence | `docs/m4/validation/` (R2 and R2.1 probes and before/after evidence in `validation/r2/` and `validation/r21/`) |
