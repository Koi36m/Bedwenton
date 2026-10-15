# M1 Test Specification

Framework: JUnit Jupiter 5.12.2, plain `@Test` methods only (no parameterized tests, no Mockito).
Determinism: every random test uses `java.util.SplittableRandom` with a fixed seed (20261014 and offsets).
Default tolerance 1e-12; stricter or looser values are stated per test and are part of the contract.
**Revision 3:** there are **97** test methods in 11 test classes plus 2 helper files under `workshop.vfx.math.core` (93 run without JOML, 4 need JOML). Revision 1 had 75; revision 2 added `NumericPolicyTest` (13); revision 3 adds `PolicyClarificationTest` (9). All earlier test files and all golden files are byte-identical to revision 1 (hashes in `AMENDMENT-1-NUMERIC-POLICY.md` and `AMENDMENT-2-CLARIFICATIONS.md`).

## 1. Inventory

| Class | Tests | Purpose |
|---|---|---|
| `ConventionsTest` | 10 | Right-handedness, +Y rotation versus clockwise-from-above (top-down map view), axis rotation table, multiplication order, pose and frame reference examples, Full Heal ring formula |
| `Vec3Test` | 9 | Arithmetic, cross-product hand values, 10,000-sample algebraic identities, lerp, `normalizedOr` ordinary / degenerate / extreme magnitudes |
| `QuatTest` | 17 | Golden rotation, composition and slerp (about 5,000 oracle rows); axisAngle validation; inverse/associativity; degenerate quaternions; `angleTo`; `shortestArc` including antiparallel; slerp speed, shortest path, near-parallel; nlerp; `toFrame`/`fromFrame` round trip incl. 180-degree cases; Euler order; 1,000,000-composition drift |
| `PoseTest` | 12 | Golden composition (1,500) and large coordinates (500); identity; round trips; order and associativity; five-deep chain; inverse; `directionToWorld`; constructor validation and normalization; world-edge round trip; float spacing documentation |
| `FrameTest` | 11 | Constructor validation (left-handed rejected); toWorld/toLocal; `alignY`/`alignZ` hand frames; 10,000-sample properties; degeneracy including the 1e-8 hint threshold; `fromHeading` compass table; left-handed negative control; transport twist with naive-flip negative control; transport robustness |
| `M0RegressionTest` | 6 | Audit findings that apply to M1 (section 3) |
| `MathUtilTest` | 4 | `clamp`, `approxEquals`, `LENGTH_EPS` |
| `PolicyClarificationTest` **[R3]** | 9 | Clarification A: `normalizedOr` returns the caller's fallback unchanged (same object; NaN, infinite, zero, non-unit fallbacks) and its guarantees are conditional on the fallback; `clamp` with infinite bounds, NaN to `lo`, always within bounds; `approxEquals` with infinities. Clarification B: per-operand domains for `Vec3.mul`/`lerp` scalars (1e100), `Quat.mul`/`dot` components, `Pose` edges, `slerp`/`nlerp` parameter (1e3, extrapolation, no clamping), `axisAngle` any finite angle and axis |
| `NumericPolicyTest` **[R2]** | 13 | Numerical policy of contract section 0A: IEEE overflow and NaN propagation in raw arithmetic; guaranteed finite results and homogeneity up to magnitude 1e100 for `Vec3`, `Quat.rotate`, `Frame`, `Pose`; `Pose.toWorld`/`toLocal` stay IEEE; `Pose.then`/`inverse` throw `ArithmeticException` for scale overflow, scale underflow to zero, translation overflow, subnormal reciprocal (and accept positive subnormals); constructors still throw `IllegalArgumentException`; totality fuzz of `normalizedOr`, `Quat.normalized`/`inverse`, `shortestArc` and the four frame factories over hundreds of special values (NaN, infinities, zero, subnormals, `MAX_VALUE`) |
| `BoundaryTest` | 2 | No Paper/Bukkit/Minecraft/JOML types exposed by core classes; Paper absent from the classpath |
| `JomlOracleTest` | 4 | Agreement with JOML: rotate, mul, slerp; records JOML's `rotationXYZ` order |
| `TestSupport`, `JomlOracle` | helpers | Independent oracles; `JomlOracle` is the only file that imports JOML |

## 2. Test-run rules

1. **Never change an expected value to obtain a pass.** If a test fails, first decide whether the
   implementation or the test is wrong, and report the evidence. A test correction is allowed only when you can
   show, with an independent derivation, that the expectation itself was wrong; it must be listed in the
   verification report with the derivation, and Claude reviews it before M1 is accepted.
2. The only exception to "do not touch tests" without prior review is **compile-level** repair (an import, a
   JOML method name in `JomlOracle.java`, a missing accessor name). Record every such edit, with a diff, in
   the verification report. If a repair needs more than a rename (different semantics), stop and report.
3. Use tags to run subsets: `m0-regression` and `joml-oracle` are `@Tag`s.
4. Printed `[M1 ... report]` lines (drift, transport twist, JOML Euler order) must be copied into the report.
   They are measurements, not assertions.

## 3. M0 audit findings and their M1 regression tests

| Audit item | Test |
|---|---|
| 1. Circle orientation conflict (Full Heal clockwise, Astral counter-clockwise) | `M0RegressionTest.circleOrientationConflict`, `ConventionsTest.fullHealRingFormulaIsClockwiseFromAbove`, `positiveRotationAndClockwiseFromAboveAreOpposite` |
| 2. Left-handed (right, up, forward) anchor basis | `M0RegressionTest.anchorBasisHandedness`, `FrameTest.leftHandedNegativeControl`, `FrameTest.fromHeadingCompass` |
| Euler order (Astral X, then Y, then Z; JOML order unknown) | `M0RegressionTest.eulerOrderIsPinned`, `QuatTest.eulerApplyOrder`, `JomlOracleTest.jomlEulerOrderIsPinned` |
| Zero-vector fallback (Full Heal `outward`) | `Vec3Test.normalizeDegenerate`, `Vec3Test.outwardFallbackPattern`, `M0RegressionTest.zeroOffsetFallback` |
| Configuration rejected when non-finite (Astral `Spec`) | `PoseTest.validation`, `M0RegressionTest.configurationRejectsNonFinite` |
| Large coordinates | `PoseTest.goldenLargeCoordinates`, `worldEdgeRoundTrip`, `floatSpacingDocumentation`, `M0RegressionTest.largeCoordinatePrecision` |
| Robustness list from the review (degenerate vectors, antiparallel rotation, NaN) | `Vec3Test.normalizeDegenerate`, `QuatTest.shortestArcDegenerate`, `QuatTest.normalizeDegenerate`, `FrameTest.alignDegenerate`, `fromHeadingDegenerate`, `transportRobustness` |

## 4. Forward regressions (not in M1; recorded so they are not lost)

| M0 finding | Regression test to write | Milestone |
|---|---|---|
| 4. Astral truncates time inside continuous functions | Continuity across integer ticks at tau and tau + 1e-6 with a truncating negative control (their own check only used integer age + 0.01, which cannot see it) | M2/M3 |
| 3. Full Heal beads uneven by about 4.4 times | Arc-length spacing ratio near 1 on Full Heal's default spiral (turns 1.05, extent 6.48); uniform-in-u as failing control | M4 |
| Beads move when density changes | Slot position independent of density; active sets nest | M4 |
| 5. `beamPoint` offset not perpendicular | Offset dot edge direction is about 0; endpoints exact | M5 |
| 6. Edge pops at the length limit | Weight and points continuous across the limit | M5 |
| Skipped-tick cue loss | Each timeline event fires exactly once when ticks are skipped | M2 |
| 7. Mixed time sources | Clock tests with injected fake time | M2 |
| ParticleEmitter disc area and direction | Fraction inside half the radius is 0.25; direction derives from the emitted point | M4 |
| Circle factories | `horizontalCounterClockwiseFromAbove` reaches -Z, `horizontalClockwiseFromAbove` reaches +Z at 90 degrees | M3 |

## 5. Pre-handoff checks performed by the author (not a substitute for the Java 25 run)

Claude has no Java 25, Gradle, JUnit or JOML in its environment. What was done instead, and what it proves:

- **Revision 3 update:** the throwaway implementation needed no change. **93 of 93** runnable test methods pass on Java 21 (shim runner, `-Xlint:all`, no warnings). Eleven further deliberate bugs aimed at the new tests were all detected: `normalizedOr` copying or normalizing the fallback; `clamp` rejecting infinite bounds or mapping NaN to the upper bound; `slerp`, `nlerp` and `Vec3.lerp` clamping `t`; `axisAngle` rejecting huge axes; `Vec3.mul` saturating; `approxEquals` treating equal infinities as equal; `Pose.toLocal` saturating. With the earlier 23, all 34 injected bugs are detected.
- **Revision 2 update:** the throwaway implementation was amended to the revision-2 contract (`Pose.then` and
  `inverse` check representability and throw `ArithmeticException`). Result: **84 of 84** runnable test methods
  pass (the 71 revision-1 tests plus the 13 new ones), compiled with `-Xlint:all` and zero warnings on Java 21.
  Eleven additional deliberate bugs aimed at the new tests were injected and all were detected: derived pose
  returning IDENTITY instead of throwing; derived scale saturating to `MAX_VALUE`; `Vec3.mul` saturating;
  `Vec3.sub` replacing NaN with 0; `normalizedOr` without its non-finite check; `Quat.normalized` without its
  non-finite check; `then`/`inverse` throwing `IllegalArgumentException`; `shortestArc` returning a zero
  quaternion for degenerate input; `Pose.toWorld` saturating at 1e300; `transportY` returning WORLD for a
  degenerate axis; `then` skipping the translation-overflow check. The 12 revision-1 mutants were rerun against
  the revision-2 test set and are all still detected (23 of 23 in total).
- The test sources (all except the 4 JOML tests and `JomlOracle.java`) were compiled with `-Xlint:all`
  with zero warnings using the Java 21 compiler module, against a **minimal assertion shim** (annotations and
  `Assertions` only) and a **throwaway reference implementation written by the author** from this contract.
  The throwaway implementation is deliberately **not shipped**, so that the production code is
  independently written.
- Revision-1 result: 71 of 71 runnable test methods passed against the throwaway implementation (Java 21, shim runner).
- Twelve deliberate bugs ("mutants") were injected into the throwaway implementation one at a time:
  `mul` operand order swapped; `rotate` cross-term sign flipped; `slerp` without shortest-path sign;
  `Pose.then` using the wrong rotation; `Pose.inverse` ignoring scale; `normalizedOr` threshold removed;
  `angleTo` via `acos`; `fromHeading` with `up x forward`; antiparallel branch removed; Euler helper in the
  wrong order; `alignY` hint threshold removed; `transportY` replaced by `alignY`. All twelve were detected by
  at least one test (the first run missed the threshold mutant, so `FrameTest.alignDegenerate` was extended).
- One test expectation was corrected before handoff: `ConventionsTest.axisRotationTable` first expected
  `Quat.axisAngle(X, -90 degrees).rotate(Z) = (0,-1,0)`. The right value is `(0, 1, 0)`; checked by
  Rodrigues' formula by hand (k x v = (1,0,0) x (0,0,1) = (0,-1,0), times sin(-90 degrees) = -1) and by the
  surviving +90 degree case.
- **Not verified:** compilation under Java 25 and JUnit 5.12.2; the `JomlOracle` calls against the real JOML API
  (written from memory of the API); Gradle wiring; any result that depends on the Codex implementation.
