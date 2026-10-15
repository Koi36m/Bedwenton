# Amendment 1: numerical policy (contract revision 2)

> **Partly superseded by Amendment 2 (revision 3).** Two statements below are refined: the Tier 2 guarantee is
> conditional on a caller-supplied fallback being finite (and `clamp` bounds being finite), and the "supported
> magnitude 1e100" is now defined per operand, including scalar multipliers, interpolation parameters, angles and
> axes. See `AMENDMENT-2-CLARIFICATIONS.md`. The rest of this amendment stands unchanged.

Status: proposed by Codex (finding) and Claude (independent review), for your approval before Codex implements.
Scope: narrow. Only the numerical-policy clauses of `IMPLEMENTATION-CONTRACT.md` and one new test class change.
Golden vectors and all revision-1 tests are untouched. No production code and no existing plugin was touched.

## 1. The finding, independently confirmed

Revision 1 (contract section 0, rule 1) promised that evaluation methods "never return NaN or infinity for finite
input". That cannot hold for IEEE-754 arithmetic, as Codex reported. Checked numerically:

| Case | Result |
|---|---|
| `Double.MAX_VALUE * 2` (`Vec3(MAX,0,0).mul(2)`) | `+Infinity` |
| `3 * (1.4e154)^2` (`lengthSquared`, so naive `length`, of `(1.4e154,0,0)`) | `Infinity`, although the exact length 1.4e154 is representable |
| scale product `1e200 * 1e200` | `Infinity`, so `Pose.then` of two valid poses can be invalid |
| scale product `1e-200 * 1e-200` | `0.0` (underflow), also invalid |
| scale product `1e-160 * 1e-160` | `1e-320` (positive subnormal, representable) |
| `1 / 1e-310` | `Infinity` (so `Pose.inverse` can fail) |
| `1 / Double.MAX_VALUE` | `5.56e-309` (positive subnormal, representable) |

**Other clauses with the same overclaim, found in review:** `Pose.toWorld`, `toLocal`, `Frame.toWorld`, `toLocal`
(raw arithmetic that can overflow); `Quat.slerp`/`nlerp`/`angleTo` (valid only for unit quaternions); `Quat.rotate`
(assumes unit); the policy line in `ARCHITECTURE.md`; and, outside this package, Part 3.4 of the M0 document
("finite input always yields finite output"). The M0 document stays unmodified as the audited baseline; this
amendment supersedes that sentence and will be recorded in the decision-record document.

## 2. Assessment of your proposed policy

| # | Your proposal | Assessment | Refinement adopted |
|---|---|---|---|
| 1 | Raw vector and quaternion arithmetic follows IEEE-754, overflow where unavoidable | Agree | Name the members (Tier 1), pin overflow, NaN propagation and "no exception, no saturation" with tests. `length`/`distance` stay plain IEEE (see 4.5) |
| 2 | Validated types reject invalid or non-representable states | Agree | `Pose.then`/`inverse` check the result and throw **`ArithmeticException`**; constructors keep `IllegalArgumentException` (see 3) |
| 3 | Operations with degeneracy fallbacks keep their guarantees | Agree, and strengthen | Tier 2: finite valid result for **every** `double` input, not only finite input (already tested for several cases; now exhaustive fuzz) |
| 4 | Finite results for "ordinary supported input ranges" | Agree, but "ordinary" must be a number | Supported magnitude **1e100** (Vec3 components, Pose scale in [1e-100, 1e100]), derived from overflow bounds, tested at the bound |
| 5 | No arbitrary clipping or saturation | Agree | Added mutation tests that fail if any operation saturates or replaces NaN |

## 3. Final policy (full text: contract section 0A)

- **Tier 1, raw arithmetic:** IEEE-754. Overflow gives infinity, NaN propagates, nothing throws, nothing is clamped.
- **Tier 2, degeneracy operations (`normalizedOr`, `Quat.normalized`/`inverse`/`shortestArc`/`fromFrame`, frame
  factories, `clamp` value):** total, finite and valid for every `double` input.
- **Tier 3, validated types:** invalid input throws `IllegalArgumentException`; a derived `Pose` that is not
  representable throws `ArithmeticException`; never repaired.
- **Tier 4, domain-restricted (`slerp`, `nlerp`, `angleTo`, `toFrame`):** unit finite quaternions in, correct
  results out; unspecified outside; no throw (except `toFrame` via `Frame` invariants).
- **Supported magnitude 1e100:** a testable guarantee, not a clamp. Justification: products of two operands at most
  1e200, sums of three such products at most 3e201, scale products in [1e-200, 1e200] (above the smallest normal
  2.2e-308), reciprocals in [1e-100, 1e100], all far from the 1.8e308 overflow limit.

**Why `ArithmeticException` for derived-pose failures and `IllegalArgumentException` for bad inputs.** For
`then`/`inverse` the arguments are individually valid, so blaming the argument would be misleading; the
failure is an arithmetic condition of the result. Two exception types also let adapters distinguish
"bad configuration" from "numeric range exceeded". The cost is one extra exception type in the tests. If you
prefer a single type (`IllegalArgumentException`), it is a mechanical change in `NumericPolicyTest` and contract
section 5 only.

## 4. Alternatives considered and rejected

1. **Saturating arithmetic** (clamp to `MAX_VALUE`): hides errors, breaks algebraic identities, has no
   mathematical justification. Rejected (your point 5).
2. **Exceptions from raw arithmetic:** adds branches to hot paths, contradicts IEEE semantics callers expect, and
   would make `isFinite()` pointless. Rejected.
3. **Restrict the `Pose` constructor to a bounded scale range** (for example a normal-double range): does not
   remove translation overflow (`t' = -s' R^-1 t` can still overflow) and adds an arbitrary bound. Rejected;
   positive subnormal scales stay valid (`then`/`inverse` accept them; `inverse` of `1e-310` throws because `1/s`
   overflows).
4. **A single exception type for all failures:** simpler, but misattributes blame (section 3). Cheap to switch if you prefer.
5. **Make `length`/`distance` overflow-free (scaling or hypot):** mathematically nicer, but not needed by any M1
   consumer, adds cost and a second code path; `normalizedOr` already handles extreme magnitudes. Deferred, not
   rejected: it can be added later without breaking the contract because it only widens the finite range.

## 5. Change log (every specification change)

| ID | Where | Revision 1 | Revision 2 | Reason | Tests |
|---|---|---|---|---|---|
| C1 | Contract header | no revision note | revision 2 marker and pointer to this file | traceability | none |
| C2 | Contract section 0, rule 1 | "Evaluation methods never throw for finite input and never return NaN or infinity for finite input" | withdrawn, replaced by pointer to section 0A | statement false for IEEE arithmetic | none |
| C3 | Contract new section 0A | absent | four tiers, member assignment, exception types, no clamping, supported magnitude 1e100, summary table | resolves the contradiction | `NumericPolicyTest` |
| C4 | Contract section 2 (`Vec3`) | no tier statement | all members Tier 1 except `normalizedOr` (Tier 2) | clarity | `rawArithmeticOverflowsToInfinity`, `supportedMagnitudeVectors`, `normalizedOrIsTotal` |
| C5 | Contract section 3 (`Quat`) | no tier statement | tiers assigned; `rotate` is Tier 1 with a unit precondition | clarity | `quaternionNormalizationIsTotal`, `shortestArcIsTotal` |
| C6 | Contract section 4 (`Frame`) | factories must return valid frames | factories valid and non-throwing for **every** `double` input; `toWorld`/`toLocal` Tier 1 | strengthens, matches existing tests | `frameFactoriesAreTotal` |
| C7 | Contract section 5 (`Pose`), `then`/`inverse` | formulas only | representability check, `ArithmeticException`, subnormal accepted, zero rejected, never clamp, never reach the constructor with invalid values | resolves overflow/underflow | `poseThenRejects...` (2), `poseInverseRejects...` (1) |
| C8 | Contract section 5, `toWorld`/`toLocal`/`directionToWorld` | implied finite | Tier 1: may return infinity, never throw | honest semantics | `poseEvaluationFollowsIeee` |
| C9 | Contract section 6 | accuracy table | two rows added (supported magnitude, Tier 2 accuracy) | testable bounds | `supportedMagnitudePose` |
| C10 | `ARCHITECTURE.md` | "Validate at construction, total at evaluation: no NaN propagation" | tiered policy statement | same overclaim | none |
| C11 | README, test specification, verification template | 75 tests | 88 tests; amendment pointer; section 4A of the report template | consistency | none |

**Unchanged:** all conventions (`CONVENTIONS.md`), reference vectors, golden files, the 75 revision-1 tests, algorithms
and accuracy requirements of sections 1 to 4, and every fallback rule (thresholds 1e-12, 1e-10, 1e-8).

## 6. Test changes

One new class, `NumericPolicyTest` (13 tests, tag `numeric-policy`); no revision-1 test was edited:

| Test | Pins |
|---|---|
| `rawArithmeticOverflowsToInfinity` | `MAX_VALUE * 2` is `+Infinity` in `Vec3.mul`, `add`, `sub`, `dot`, `lengthSquared`, `Quat.mul`; no exception, neighbouring components unaffected |
| `rawArithmeticPropagatesNaNAndInfinity` | `inf - inf`, `inf * 0`, NaN propagation; no sanitizing |
| `supportedMagnitudeVectors` | add, sub, mul, negate, lerp, cross, dot, length, distance, `Quat.rotate`, `Frame.toWorld`/`toLocal` finite and homogeneous at 1e100, plus the exact bound corner |
| `supportedMagnitudePose` | `Pose` with scale 1e100 or 1e-100 and translations/points at 1e100: `toWorld`, `toLocal`, round trips, `then` (scale 1e200 stays finite), `inverse` |
| `poseEvaluationFollowsIeee` | `Pose.toWorld`/`toLocal`, `Frame.toWorld` overflow to infinity without throwing |
| `poseThenRejectsNonRepresentableScale` | overflow and underflow-to-zero throw `ArithmeticException`; subnormal product accepted |
| `poseThenRejectsNonRepresentableTranslation` | translation overflow throws; cancelling case succeeds |
| `poseInverseRejectsNonRepresentableResult` | `1e-310` and overflowing translation throw; `1/MAX_VALUE` accepted |
| `constructorsStillThrowIllegalArgument` | input validation unchanged |
| `normalizedOrIsTotal` | fallback object exactly for degenerate input, otherwise unit; hundreds of special vectors |
| `quaternionNormalizationIsTotal` | all 14,641 combinations of 11 special values: finite unit result, IDENTITY for zero/non-finite |
| `shortestArcIsTotal` | thousands of special pairs: unit result, IDENTITY when degenerate, correct mapping otherwise |
| `frameFactoriesAreTotal` | `alignY`, `alignZ`, `fromHeading`, `transportY` always valid; WORLD or previous frame for a degenerate axis |

**Preserved byte-for-byte** (first 16 hex digits of the revision-1 SHA-256; full values in `SHA256SUMS.txt`):

| File | SHA-256 |
|---|---|
| `src/test/java/workshop/vfx/math/core/BoundaryTest.java` | `0b621ed615ee3895...` |
| `src/test/java/workshop/vfx/math/core/ConventionsTest.java` | `7f4b86a5f9d46576...` |
| `src/test/java/workshop/vfx/math/core/FrameTest.java` | `7035967c7980d9c7...` |
| `src/test/java/workshop/vfx/math/core/JomlOracle.java` | `f542b893db448815...` |
| `src/test/java/workshop/vfx/math/core/JomlOracleTest.java` | `de45b8b82e2f5e32...` |
| `src/test/java/workshop/vfx/math/core/M0RegressionTest.java` | `0ee3e6ebb117a404...` |
| `src/test/java/workshop/vfx/math/core/MathUtilTest.java` | `ad7e7ee1aa4317a1...` |
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

## 7. What a revision-1-style implementation will see

An implementation that passed all revision-1 tests but (a) lets `Pose.then`/`inverse` throw `IllegalArgumentException`
or construct invalid poses, (b) clamps or sanitizes anywhere, or (c) is not total for NaN/infinite inputs of the
degeneracy operations will fail `NumericPolicyTest`. Nothing else in the production contract changes.

## 8. Verification status of this amendment

- Checked here: the throwaway reference implementation (not shipped) was amended and **84 of 84** runnable tests
  passed on Java 21 with a minimal assertion shim; 23 deliberate bugs (12 earlier, 11 new) were all detected.
- **Not verified:** anything on Java 25, Gradle, real JUnit, real JOML, or Codex's implementation. M1 is not
  verified until Codex's Java 25 run (88 tests) and my review are complete.

## 9. Decisions requested from you

1. Approve the tiered policy as written (or amend it).
2. Exception type for derived-pose failures: `ArithmeticException` (recommended) or `IllegalArgumentException`.
3. Supported magnitude 1e100 (recommended) or another bound.
4. Confirm that `length`/`distance` remain plain IEEE in M1 (overflow-free versions deferred).
