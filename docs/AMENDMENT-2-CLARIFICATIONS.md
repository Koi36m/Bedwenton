# Amendment 2: clarifications A and B (contract revision 3)

Status: prepared by Claude at your request, for Codex implementation after your acceptance.
Scope: two clarifications only. **No redesign.** No production code, no existing plugin touched. All golden
vectors and all revision-2 tests are unchanged byte for byte; one new test class is added.

## 1. Clarification A: fallbacks and `clamp`

**The conflict.** Revision 2 said Tier 2 operations return a finite, valid result for every `double` input, yet
`Vec3.normalizedOr(fallback)` returns the caller's fallback unchanged, and the fallback is not validated. A NaN or
infinite fallback therefore produces a non-finite result, and a non-unit fallback produces a non-unit result.
`MathUtil.clamp` has the same shape: with an infinite bound (`clamp(+inf, 0, +inf)`, `clamp(NaN, -inf, 1)`) the
result is infinite. Both observations are correct; the unconditional wording overclaimed.

**Resolution (original API behaviour is not changed):**

| Item | Clarified guarantee |
|---|---|
| `normalizedOr`, fallback path | Returns the caller's fallback **unchanged: same object, not validated, not normalized, not copied** (unchanged from revision 1) |
| `normalizedOr`, finiteness | A finite result on the fallback path requires the **caller** to supply a finite fallback; a NaN or infinite fallback comes back as supplied |
| `normalizedOr`, unit length | A unit result on the fallback path requires the caller to supply a **unit** fallback; a non-unit fallback comes back non-unit |
| `normalizedOr`, usable direction | Finite input with largest component at least 1e-12 always gives a finite **unit** vector, whatever the fallback contains |
| Other Tier 2 operations | Choose their own documented fallback (`IDENTITY`, `WORLD`, previous frame, a world axis); always finite and valid, no caller value involved |
| `clamp` | Result always lies in `[lo, hi]`; NaN value maps to `lo` (even if `lo` is infinite); bounds may be infinite; `lo`/`hi` NaN or `lo > hi` throws. Finite whenever the selected value or bound is finite; with finite bounds always finite |
| `approxEquals` | Unchanged formula; documented consequence: equal infinities compare false (`inf - inf` is NaN) |

Tier 2 is now worded "total in their operands" with these two qualifications; the tier table row is adjusted.

## 2. Clarification B: supported domain per operand

Revision 2 stated 1e100 for vector components and pose scales only. Revision 3 defines the guaranteed domain for
**every operand of every guaranteed operation** (full table in contract section 0A):

| Operation | Domain |
|---|---|
| `Vec3` add, sub, negate, dot, cross, lengthSquared, length, distance | components at most 1e100 |
| `Vec3.mul(s)` | components at most 1e100, finite `s` with `abs(s)` at most 1e100 |
| `Vec3.lerp(to, t)` | components at most 1e100, finite `t` with `abs(t)` at most 1e100 |
| `Quat.mul`, `dot` | components at most 1e100 |
| `Quat.rotate(v)` | finite unit quaternion; `v` components at most 1e100 |
| `Frame.toWorld`, `toLocal` | vector components at most 1e100 |
| `Pose.toWorld`, `toLocal`, `directionToWorld` | translation components at most 1e100, scale in [1e-100, 1e100], vector components at most 1e100 |
| `Pose.then`, `inverse` | both poses inside the same ranges; never throws; result scale in [1e-200, 1e200] |
| `Quat.slerp`, `nlerp` | finite unit quaternions, `abs(t)` at most **1e3** |
| `Quat.angleTo`, `toFrame` | finite unit quaternions |
| `Quat.axisAngle`, Euler helper | **any** finite angle; any finite axis with largest component at least 1e-12 (no upper bound) |
| `clamp` | any value; non-NaN bounds, `lo <= hi` |

Rules stated explicitly: guarantees are for **single operations**; a result may leave the domain (1e100 times 1e100
is 1e200) and the next operation is judged afresh. **Outside the table nothing is claimed and nothing is rejected,
clamped or saturated: plain IEEE-754 applies.**

**Why 1e3 for `slerp` and `nlerp`.** The extrapolation weights contain `sin(t * theta)`; with `theta` up to pi the
angle argument reaches about 3e3, whose rounding error (about 5e-13 rad) keeps the 1e-9 action accuracy of the
tests. At `abs(t)` near 1e100 the argument has no meaningful precision, so no accuracy is claimed there.

**Why `axisAngle` has no upper bound.** The axis is normalized internally by the same scaled routine as
`normalizedOr`, so a `MAX_VALUE`-sized axis is as usable as a unit one; Java's `Math.sin` and `Math.cos` reduce
huge finite angles exactly, so the result is the rotation by that exact angle value.

## 3. Change log (every change)

| ID | Where | Revision 2 | Revision 3 | Reason | Tests |
|---|---|---|---|---|---|
| D1 | Contract header | revision 2 note | adds revision 3 note | traceability | none |
| D2 | Contract 0A, Tier 2 paragraph | "finite, valid result for every double input" | "valid result for every double value of every operand" plus the two qualifications (caller fallback, `clamp`) | the claim conflicted with caller-supplied fallbacks and infinite bounds | `normalizedOrPreservesCallerFallback`, `fallbackGuaranteesAreConditionalOnTheCallersFallback`, `clampWithInfiniteBounds` |
| D3 | Contract 0A, tier table row 2 | "finite and valid, always" | valid always; finite unless the caller's fallback or an infinite clamp bound is not | same | same |
| D4 | Contract 0A, "Supported magnitude" | components and pose scale only | renamed "Supported domain", per-operand table, single-operation rule, IEEE outside | scalar multipliers, interpolation parameters, angles and axes were unspecified | `scalarMultiplierAndInterpolationDomain`, `quaternionComponentDomain`, `poseDomainEdges`, `interpolationParameterDomain`, `axisAngleDomain` |
| D5 | Contract 0A, Tier 4 | "finite t" | slerp/nlerp domain `abs(t)` at most 1e3 | accuracy depends on `abs(t) * theta` | `interpolationParameterDomain` |
| D6 | Contract section 1 | clamp: NaN to `lo`, infinite value clamps | adds: infinite bounds allowed, result finite only if selected value is; `approxEquals` equal infinities false, never throws | same | `clampWithInfiniteBounds`, `approxEqualsWithInfinities` |
| D7 | Contract section 2 `normalizedOr` | fallback "not validated or normalized" | adds "returned as the same reference, not copied; finiteness and unit length are the caller's responsibility" | same | `normalizedOrPreservesCallerFallback` |
| D8 | Contract section 6 | two [R2] accuracy rows | rows now reference the domain table | consistency | none |
| D9 | `ARCHITECTURE.md` policy line | "total for every input" | "total for every operand value (a caller-supplied fallback is returned unchanged)" | same | none |
| D10 | `AMENDMENT-1-...md` | no note | adds a "partly superseded" banner; nothing else edited | history stays readable | none |
| D11 | README, test specification, report template | 88 tests | 97 tests; revision 3 pointers; report section 4B | consistency | none |

**Unchanged:** algorithms, thresholds (1e-12, 1e-10, 1e-8), conventions, reference vectors, golden files, the
exception policy of revision 2 (`ArithmeticException` for derived poses), and the plain-IEEE decision for
`length` and `distance` (approved, revisit during curve work).

## 4. Test changes

One new class, `PolicyClarificationTest` (9 tests, tag `numeric-policy`). No existing test was edited.

| Test | Pins |
|---|---|
| `normalizedOrPreservesCallerFallback` | same object returned for degenerate input, for NaN, infinite, zero, non-unit and huge fallbacks; a usable direction ignores the fallback |
| `fallbackGuaranteesAreConditionalOnTheCallersFallback` | finite fallback gives finite, not normalized; unit fallback gives unit; NaN or infinite fallback comes back non-finite |
| `clampWithInfiniteBounds` | infinite bounds accepted; NaN maps to `lo` even if infinite; always within bounds over a sweep; invalid ranges throw |
| `approxEqualsWithInfinities` | equal infinities false; infinite tolerance true for finite values; NaN tolerance false |
| `scalarMultiplierAndInterpolationDomain` | `mul(s)` and `lerp(t)` finite for scalars up to 1e100 and components up to 1e100 (corner included); overflow outside the domain, no saturation |
| `quaternionComponentDomain` | `Quat.mul`, `dot` finite for components up to 1e100; hand-checked scalar part; overflow beyond |
| `poseDomainEdges` | `toLocal`, `toWorld`, `directionToWorld`, `then`, `inverse` at the edges (scale 1e-100 with world components 1e100, scale 1e100, composed scales near 1e200 and 1e-200) |
| `interpolationParameterDomain` | slerp action accurate to 1e-9 and nlerp exact for `abs(t)` up to 1e3 including extrapolation |
| `axisAngleDomain` | any finite angle (up to `MAX_VALUE`) and any finite axis (up to `MAX_VALUE`, down to 1e-12) give a unit quaternion; invalid inputs still throw |

**Preserved byte-for-byte** (first 16 hex digits of the SHA-256 shared by revisions 2 and 3):

| File | SHA-256 |
|---|---|
| `src/test/java/workshop/vfx/math/core/BoundaryTest.java` | `0b621ed615ee3895...` |
| `src/test/java/workshop/vfx/math/core/ConventionsTest.java` | `7f4b86a5f9d46576...` |
| `src/test/java/workshop/vfx/math/core/FrameTest.java` | `7035967c7980d9c7...` |
| `src/test/java/workshop/vfx/math/core/JomlOracle.java` | `f542b893db448815...` |
| `src/test/java/workshop/vfx/math/core/JomlOracleTest.java` | `de45b8b82e2f5e32...` |
| `src/test/java/workshop/vfx/math/core/M0RegressionTest.java` | `0ee3e6ebb117a404...` |
| `src/test/java/workshop/vfx/math/core/MathUtilTest.java` | `ad7e7ee1aa4317a1...` |
| `src/test/java/workshop/vfx/math/core/NumericPolicyTest.java` | `b8b9165707573f9d...` |
| `src/test/java/workshop/vfx/math/core/PoseTest.java` | `58062fdbdd226245...` |
| `src/test/java/workshop/vfx/math/core/QuatTest.java` | `d823d53407e64956...` |
| `src/test/java/workshop/vfx/math/core/TestSupport.java` | `c0ec0b5e2885d666...` |
| `src/test/java/workshop/vfx/math/core/Vec3Test.java` | `b0c7fadb3c1b8419...` |
| `src/test/resources/golden/MANIFEST.sha256` | `6055149ffc538a50...` |
| `src/test/resources/golden/compose_cases.csv` | `25a7b60a6741948f...` |
| `src/test/resources/golden/pose_cases.csv` | `a6afd0965f28c8c0...` |
| `src/test/resources/golden/pose_large_cases.csv` | `ae10a0ef5079fa58...` |
| `src/test/resources/golden/rotation_cases.csv` | `b35b814e83589f4a...` |
| `src/test/resources/golden/slerp_cases.csv` | `5c5b744508ed6a8c...` |
| `tools/generate_golden_vectors.py` | `984d3895bb63cc61...` |

## 5. Verification status

- Checked here: the throwaway reference implementation (not shipped; it needed no change for revision 3) passed
  **93 of 93** runnable tests on Java 21 with a minimal assertion shim, compiled with `-Xlint:all` and no
  warnings. 11 new deliberate bugs were all detected: `normalizedOr` copying or normalizing the fallback;
  `clamp` rejecting infinite bounds or mapping NaN to the upper bound; `slerp`, `nlerp` or `lerp` clamping `t`;
  `axisAngle` rejecting huge axes; `Vec3.mul` saturating; `approxEquals` treating equal infinities as equal;
  `Pose.toLocal` saturating. Together with the 23 earlier ones, 34 of 34 injected bugs are detected.
- **Not verified:** anything on Java 25, Gradle, real JUnit, real JOML, or Codex's implementation. Total test
  methods are now 97 (93 plus the 4 JOML tests). M1 is not verified until Codex's Java 25 run and my review.

## 6. Notes for Codex

- If you have not started: implement against revision 3 directly.
- If you started from revision 2: no production change should be needed for A (it documents existing behaviour);
  check that `normalizedOr` returns the **same reference**, that `clamp` accepts infinite bounds, that `slerp` and
  `nlerp` do **not** clamp `t`, and that `Vec3.mul`/`lerp` do not clamp. Add `PolicyClarificationTest.java`
  and replace the changed documents listed in `README-FOR-CODEX.md`.
