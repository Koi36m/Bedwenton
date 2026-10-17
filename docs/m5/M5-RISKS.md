# M5 numerical and performance risks

Status: PROPOSED, with the contract.

No universal performance target is asserted. The guarantees are **work bounds** (contract 9): deterministic,
input-independent and testable. Timings in `validation/performance.txt` were measured on the review container with a
warm JIT. They are indicative only, not acceptance criteria.

## 1. Numerical risks

| # | Risk | Effect | Mitigation in M5 | Residual |
|---|---|---|---|---|
| N1 | `Math.sin`/`cos` differ by 1 ulp across JVMs | servers or shards render slightly different positions; bit-for-bit tests become flaky | spin quaternions use `StrictMath` after turn reduction (contract 5.2); `Math` trigonometry is forbidden by a source scan | M1 `Quat.axisAngle` keeps using `Math`; M5 never calls it |
| N2 | Large spin angles lose precision | wobbling orbits after many turns | turns are reduced modulo 1 per operand before the trigonometry (M3 technique); a spin is per pass, so long-running loops do not accumulate angle | the error grows linearly with `abs(turns)` per pass; capped at 2^20 turns per pass |
| N3 | Huge anchor coordinates | precision of eps times the magnitude (about 4e-9 blocks at 3e7) | none needed for visuals; documented | below one client pixel |
| N4 | Scale products overflow or underflow in long chains | `Infinity` or 0 positions | step scales in `[1e-9, 1e9]`; M1 rejects unrepresentable products (`ArithmeticException`); a non-finite world point gives `ArithmeticException`; no partial frames | the caller must catch and drop the effect |
| N5 | Signed zeros | adapters comparing bits | contract 10.2: the sign of zero is not significant | none |
| N6 | Reveal or visibility decisions near boundaries | off-by-one particle counts between implementations | decisions are exact comparisons on M2 values, and normative; the fixtures avoid ambiguous boundaries, which the generator asserts | none for conforming implementations |
| N7 | M2 `FOREVER` loops at about 9.2e18 passes report AFTER | looping layers disappear | documented (conventions 3) | unreachable in practice (at least 1.5e10 years) |
| N8 | Quaternion normalization in `Pose` | each composition renormalizes | deterministic; within the validated tolerance | none |

## 2. Performance and resource risks

| # | Risk | Effect on a Paper server | Mitigation in M5 | Residual / owner of the rest |
|---|---|---|---|---|
| P1 | Particle-count explosion | client lag, network load | hard per-scene ceilings (`maxPointsPerFrame <= 2^20`, `maxLayers <= 4096`) checked at construction; frames can never exceed them | 2^20 particles per frame is still far too many for players: **per-tick, per-player and per-world budgets are P2 policy**. Effects should set small `SceneLimits` (the demos use 64 to 256) |
| P2 | Per-frame curve evaluation (arc-length tables every tick) | CPU spikes | `evaluate` makes **0** curve calls; shapes are built once with M4 (`BudgetTest.noCurveWork`) | callers must cache scenes; rebuilding a scene costs M4 sampling |
| P3 | Allocation churn | GC pressure with many effects | packed `double[]` batches, 24 bytes per point (DM5-2 measurement); O(layers x steps) small objects | one `Vec3` temporary per point inside M1 `toWorld`, often removed by escape analysis |
| P4 | Deep motion chains | CPU per layer | at most 16 steps, so at most 16 `Pose.then` and 17 M2 samples per layer | none |
| P5 | Many simultaneous effects | CPU | per-call cost is linear in emitted points plus layers x steps (`validation/performance.txt`) | the number of active effects is P2 policy |
| P6 | Thread safety | async preparation | immutable scenes and frames; no statics; 8-thread test | the anchor snapshot must be taken on the owning thread (adapter) |
| P7 | Non-terminating loops | server hang | every loop is bounded by layers, steps or points | none |
| P8 | Memory for one frame | heap | at most `3 * 2^20` doubles (24 MiB) per frame at the ceiling; at the demos' limits, at most 6 KiB | effects should use small limits |
| P9 | Desynchronization between servers or replays | visibly different effects | normative operation order; bit-identical results (`M5DeterminismTest`) | user-supplied anchors must be identical too |

## 3. Measurable acceptance criteria (instead of speed targets)

1. Every work bound of contract 9 holds: 0 curve calls during `evaluate`, and point counts bounded by
   `maxPointsPerFrame` (`BudgetTest`, `FrameContractTest.counts`).
2. Construction rejects over-budget scenes before any frame exists (`BudgetTest.preflight`).
3. Frames are bit-identical under repetition and concurrency (`M5DeterminismTest`).
4. Optionally, after implementation: a JMH benchmark of the four example scenes on the target server, recorded as
   information, not as a gate.
