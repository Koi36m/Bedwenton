# M4 performance and resource-limit risks

Status: PROPOSED, with the contract (revision 2.1).

No universal performance target is asserted. Timings depend on hardware, JIT state and server load. The guarantees
are **work bounds in curve evaluations** (contract section 8): deterministic, input-independent and testable.

Timings in `validation/evidence.txt` were measured with a warm JIT on the review container. They are indicative
only, not acceptance criteria.

| # | Risk | Effect on a Paper server | Mitigation in M4 | Residual / owner of the rest |
|---|---|---|---|---|
| P1 | Particle-count explosion (tiny spacing, huge count, a unit mistake such as spacing in pixels instead of blocks) | millions of particles, tick stall, client lag | `maxSamples` (max 2^20) checked before work; `bySpacing` rejects; `countForSpacing` saturates and lets the effect check first | the effect must choose sane limits per trigger frequency (emission milestone) |
| P2 | Expensive tables rebuilt every tick | at least 240 derivative evaluations (Spiral: StrictMath trig each) per tick per effect | immutable, reusable `ArcLengthTable`; `SampleSet.transformed(Pose)` moves sets without re-integration (DM4-7) | callers must cache tables per shape; the adapter should key caches by shape parameters, not by M3 record `equals` (M3 review N-4: signed zeros) |
| P3 | Adversarial or very oscillatory curves | build hits the budget | budget never exceeded; global refinement spends it where the error is; `converged()` reports failure | the effect decides whether unconverged spacing is acceptable |
| P4 | Per-sample inversion cost | at most 336 derivative evaluations per equal-distance sample (measured mean 13.8, max 29; adversarial speeds reach 336) | Newton for at most 16 iterations, then bisection, with a 64-iteration cap (proven bracket progress, contract 5.6) | for frequently triggered effects, prefer uniform parameters on constant-speed curves (no inversion) |
| P5 | Memory | O(maxEvaluations) per table; O(n) per sample set | ceilings 2^24 and 2^20; tables are small (about 16 to 30 panels for typical curves, 40 bytes each) | the adapter controls how many tables are kept |
| P6 | Allocation churn | one `Vec3` per sample per call | sample once, transform per tick (still one `Vec3` per point); allocation-free orbits via `parameterAt` plus `Curve.point` | Vec3 is an M1 record; value types are out of scope |
| P7 | Thread safety | async workers on Paper | immutable results; no static mutable state; concurrency test | user `Curve` implementations must be thread-safe if shared |
| P8 | Non-terminating floating-point loops | server hang | every loop has a fixed bound (panels, 50 depth, 64 iterations, n samples); no loop advances a float until a condition; inversion results are defined for every exit (contract 5.6 P4) | none |
| P9 | Non-deterministic results across servers | desynchronized visuals between shards | normative operation order; Java strict IEEE; no transcendental functions in M4 (M3's StrictMath is portable) | a user `Curve` using `Math.sin` is the user's portability risk |
| P10 | Tolerance misunderstanding | loose tolerance gives visibly uneven spacing; very tight wastes budget | explicit `ArcLengthTolerance`; effective tau and estimate reported; documented summation floor | effect designers choose 1e-4 to 1e-6 relative for visuals |
| P11 | Large parameter ranges (extrapolation, many spiral turns) | phase precision loss (M3), more panels | domain recommendation `abs(t) <= 1e3`; M3 bounds | callers should restructure long spirals into shorter ranges if needed |
| P12 | Repository size of reference data | `uniform.csv` 2,310 rows, about 180 KB | generator regenerates byte-identically; manifest | none |

## Measurable acceptance criteria (instead of speed targets)

1. Every work bound of contract section 8 holds, measured by counting curve calls (`M4AdversarialTest`).
2. All 336 reference lengths converge at relative tolerance 2^-30 within the default test budget, with achieved
   error at most tau plus allowance (`M4ReferenceVectorTest`).
3. A table for any M3 curve with a smooth speed converges in at most 4,096 evaluations at relative tolerance 1e-6.
   This was met by every reference row; the maximum observed was 540 at 1e-12.
4. Rejection paths (count above the limit, invalid spacing) evaluate the curve 0 times.
5. Optionally, after implementation: a JMH benchmark on the target server hardware, recorded as information, not as a
   gate. Suggested cases are a table for a conical spiral, 128 equal-distance samples, and 128 uniform-parameter
   samples.
