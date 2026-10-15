# M1 independent review: core transforms (Claude Code)

| | |
|---|---|
| Reviewed commit | `8397c04b55f7d506e8fec79fca120ce297e8ae22` on `m1-core-transforms` (production code from `05c27067f3e9e2480228d0d34e5a2bd64558c889`) |
| Contract | Revision 3: `docs/IMPLEMENTATION-CONTRACT.md`, `CONVENTIONS.md`, `AMENDMENT-1-*`, `AMENDMENT-2-*` |
| Review branch | `review/m1-claude` (adds files only: two test classes and `review/`) |
| Toolchain | Oracle JDK 25.0.4.1 (25.0.4.1+1-LTS-5), Gradle 9.1.0 via the pinned wrapper, Linux x86_64 |
| Recommendation | **Accept the mathematics. Request one minor diagnostic fix (D1) and contract-owner decisions on A1 to A6 before final acceptance.** |

## 1. Summary

- **Integrity: confirmed.** The Revision 3 ZIP matches its approved SHA-256. All 29 entries of its internal
  `SHA256SUMS.txt` pass. All 29 delivered files are byte-identical to the repository, and none of the 97 tests, the
  golden vectors, the docs or the build files changed after the baseline commit `77a1430`.
- **Reproduction: confirmed.** On Java 25 with Gradle 9.1.0, `./gradlew clean test` gives 97 tests, 97 passed,
  0 failed, 0 skipped, and `./gradlew check` succeeds. This matches Codex's report.
- **Mathematical review:** all five classes match the contract's formulas, composition order, conventions, tier
  rules and exception types. Every accuracy figure the contract requires is met in independent measurements, most of
  them by 3 to 4 orders of magnitude. No mathematical defect was found.
- **Findings:** 1 confirmed defect, which only affects an exception message (D1). There are 6
  test/specification ambiguities (A1 to A6), 4 possible numerical improvements outside M1's scope (I1 to I4) and
  5 nonblocking observations (N1 to N5).
- **New tests:** `ReviewAdversarialTest` has 18 tests, all passing. `ReviewFindingsTest` has 7 tests, disabled by
  default. Forced on, all 7 fail against 8397c04, as designed.

## 2. Phase 1: integrity and independent reproduction

Full log: `logs/integrity.txt`.

| Check | Result |
|---|---|
| Remote refs | `main` = `baseline-scaffold` = `870c9fd`, `m1-core-transforms` = `8397c04`, `m1-review-handoff` = `0ec0108` |
| History | linear: `870c9fd` (scaffold), `77a1430` (tests as delivered), `05c2706` (implementation), `8397c04` (evidence), `0ec0108` (handoff). All authored `Codex <codex@local>`, unsigned. |
| Scope of each commit | `77a1430` adds only `src/test/java`; `05c2706` adds only the 5 files in `src/main/java`; `8397c04` adds only `verification/`; `0ec0108` adds only `handoff/` |
| Revision 3 ZIP | `0f79530734458da8a3fd0fc0ab49c836fed0dbb70d815b1813e3e7b56180ed61`, **matches** the approved value |
| Internal `SHA256SUMS.txt` | 29/29 OK. The copy in the ZIP is identical to `verification/input/SHA256SUMS.txt`. |
| Rev3 files vs repository at 8397c04 | 28/28 under `docs/`, `src/`, `tools/` byte-identical, plus `README-FOR-CODEX.md` identical to `verification/input/` |
| Golden `MANIFEST.sha256` | 5/5 OK |
| Tests, docs, tools and build files from `77a1430` to `8397c04` | 0 diff lines |
| Handoff | `handoff/SHA256SUMS.txt` matches the recomputed source ZIP and bundle. `git bundle verify`: complete history. Its heads equal the remote. |
| Gradle wrapper | 9.1.0, distribution checked against the pinned SHA-256 `a17ddd85...c806` by the wrapper |

| Run (Java 25.0.4.1, Gradle 9.1.0) | Result |
|---|---|
| `clean test`, attempt 1 | build failed before compiling tests: Maven Central returned **HTTP 429** for `junit-jupiter-api-5.12.2.jar` (environment, logged in `logs/baseline-clean-test-attempt1-maven429.txt`) |
| `clean test`, attempt 2, unmodified 97 tests | **97 tests, 97 passed, 0 failed, 0 skipped**, BUILD SUCCESSFUL |
| `check` | BUILD SUCCESSFUL (`verifyNoRuntimeDependencies` passed: production runtime classpath empty) |
| Compiler | `-Xlint:all`: production code compiles with no warnings. The 5 warnings come from the JOML 1.10.9 test jar's class files (`RuntimeVisibleTypeAnnotations ... version 46.0`). |
| `clean test` with review tests | **122 tests: 115 passed, 0 failed, 7 skipped** (the disabled findings) |
| `check` with review tests | BUILD SUCCESSFUL |

Per class (unmodified): Boundary 2, Conventions 10, Frame 11, JomlOracle 4, M0Regression 6, MathUtil 4, NumericPolicy 13,
PolicyClarification 9, Pose 12, Quat 17, Vec3 9; total 97.

**Independence limitation.** The JDK is the same vendor and build that Codex used (Oracle 25.0.4.1), because this
environment's network policy blocks the other JDK distributors (Adoptium, Azul, Corretto, java.net). The review
therefore does not cover differences between JDK vendors. The contract uses only `Math.sqrt` (exact under IEEE) and
tolerance-based `sin`, `cos` and `atan2`, so the risk is low.

## 3. Phase 2: adversarial mathematical review

### 3.1 Line-by-line conformance

| Contract item | Implementation | Verdict |
|---|---|---|
| `MathUtil.clamp`: NaN bound or `lo > hi` throws IAE; NaN value maps to `lo`; infinite bounds allowed | `Double.isNaN(lo) \|\| isNaN(hi) \|\| lo > hi` then `isNaN(v) ? lo : max(lo, min(hi, v))` | conforms |
| `approxEquals = abs(a-b) <= tol` | same | conforms (NaN and `inf - inf` give false) |
| `Vec3.cross` exact formula | identical expression | conforms |
| `Vec3.lerp = (1-t)*this + t*to`, unclamped | `mul(1-t).add(to.mul(t))` | conforms (see N3) |
| `normalizedOr`: largest-component test, scale before squaring, same fallback reference | divides by max abs component, then `mul(1/len)`; returns `fallback` itself | conforms |
| `Quat.mul` Hamilton product, b first | all four components identical to the contract formula | conforms |
| `normalized`: scaled; zero or non-finite gives `IDENTITY` | scaled by max abs component; returns the `IDENTITY` constant | conforms |
| `inverse = normalized().conjugate()` | literal | conforms (see A2) |
| `rotate = v + 2w(u x v) + 2u x (u x v)` | literal | conforms (see A6, I1) |
| `angleTo = 2 atan2(\|vec(r)\|, \|r.w\|)`, `r = conj(this) * o` | literal | conforms |
| `axisAngle`: IAE for non-finite or tiny axis, non-finite angle; `(cos r/2, sin r/2 * unit)` | literal | conforms (null axis: A3) |
| `fromEulerApplyXThenYThenZ = qz * qy * qx` | `axisAngle(Z).mul(axisAngle(Y)).mul(axisAngle(X))` | conforms |
| `shortestArc` half-vector method, `\|s\| < 1e-10` half turn about `unit(a x e)`, `e` least-aligned with ties X, Y, Z | literal; `Frame.leastAligned` implements the tie order | conforms |
| `slerp`: sign flip on `dot < 0`, `theta = 2 atan2(\|b-a\|, \|b+a\|)`, `theta < 1e-8` falls back to nlerp, sine weights, unclamped `t`, normalized | literal | conforms |
| `nlerp`: shortest sign, linear blend, `normalized()` | literal | conforms |
| `toFrame = Frame(rotate X, rotate Y, rotate Z)` | literal | conforms |
| `fromFrame`: Shepperd with frame axes as columns | trace > 0 branch, else largest diagonal; the 4 branches are algebraically checked | conforms |
| `Frame` constructor: finite, unit to 1e-9, pairwise dot <= 1e-9, `\|x cross y - z\| <= 1e-9`, IAE | literal, plus a null check | conforms |
| `alignY`/`alignZ`: normalized hint, 1e-8 perpendicular threshold, least-aligned fallback, two Gram-Schmidt passes, third axis by cross product | `perpendicular()` does both passes; third axis by cross product (then normalized) | conforms (see A4) |
| `fromHeading`: `x = right`, `y = up`, `z = back`, defaults | implemented as `alignZ(-f, up)`; algebraically identical to `right = n(f x u)`, `up = right x f` (verified numerically in `fromHeadingMatchesContractFormula`) | conforms (see A5) |
| `transportY`: shortest-arc carry, re-orthogonalize, `prev` itself for degenerate `newY`, antiparallel valid | `alignY(uy, shortestArc(prev.y, uy).rotate(prev.x))` | conforms to all tested tolerances (see A1) |
| `Pose` constructor: finite translation, finite positive scale, finite nonzero rotation, stores `normalized()` | literal, plus null checks | conforms |
| `Pose.toWorld`, `toLocal` (divides by `scale`, no reciprocal), `directionToWorld` | literal | conforms |
| `then`/`inverse` formulas, checked before construction, `ArithmeticException`, subnormal scale accepted | literal; `checkResult` runs before `new Pose` | conforms (message: D1) |
| Section 0 rules (records, no statics or threads, no platform imports, no public API beyond the contract) | `leastAligned` is package-private; other helpers private; `BoundaryTest` passes | conforms |

### 3.2 Independent measurements

All measured on Java 25.0.4.1 (`logs/probe.txt`, `logs/review-clean-test.txt`). The references are independent
of the production code: 60-digit `BigDecimal` normalization and Gram-Schmidt, Rodrigues' formula, analytic
great-circle positions.

| Property | Inputs | Worst measured | Contract demand |
|---|---|---|---|
| `normalizedOr` vs exact | 400,000 vectors mixing 0, `MIN_VALUE`, subnormals, 1e-13 to `MAX_VALUE` | 2.2e-16 | 1e-12 |
| `Quat.normalized` vs exact | 200,000 such quaternions | 2.2e-16 | 1e-12 |
| Frame factories orthonormality and handedness | 300,000 extreme axes; hints at 0.5x to 2x the 1e-8 threshold; hints scaled by -1e5 | 6.7e-16 | 1e-12 |
| `alignY` x direction vs exact perpendicular | perpendicular fractions 1e-8 to 1 | 2.2 x (1e-16 / fraction): the problem's own conditioning | follows the hint |
| `angleTo` | angles 1e-12 to pi, sign-flipped inputs | 6.7e-16 | 1e-12 (1e-9 to pi - 1e-6) |
| `slerp` action, extrapolation | angles 5e-9 to pi - 1e-12; t in [-1000, 1000] | 9.1e-13 | 1e-9 (`abs(t)` <= 1e3) |
| `nlerp` vs independent formula | t in [-1000, 1000] | 3.3e-16 | exact formula |
| `fromFrame(toFrame(q))` | half turns about random and diagonal axes, pi - 1e-1 to pi - 1e-15, multiples of pi/4 | 1.8e-15 | round trip |
| `Pose` at the world border (`abs(x)`, `abs(z)` <= 3e7) | random rotations, scales 1e-3 to 1e3: then, inverse and toLocal round trips | 4.6e-8 blocks | 1e-7 composed, 1e-6 toLocal |
| `shortestArc` near antiparallel, offset 1e-6 / 1e-7 / 1e-9 | 20,000 random directions each, random input lengths | 7.6e-10 / 7.9e-9 / 7.9e-7 | 1e-9 / 1e-8 / 1e-6 (see N1) |
| `transportY` | 10^6-step walk with 1,000 antiparallel flips | valid at 6.7e-16; `y` within 4.0e-16 of `normalize(newY)` | 1e-12 (see A1) |

### 3.3 Points the brief asked to prioritise

- **Right-handedness and composition order.** `X.cross(Y) = Z` exactly. `a.mul(b)` applies `b` first. The Euler helper
  equals Rodrigues X, then Y, then Z about fixed world axes (20,000 random triples, 1e-12). +90 degrees about +Y sends
  +X to -Z. `parent.then(child)` matches nested `toWorld` to 1e-7 at the world border.
- **Four tiers.** A fuzz of 100,000 operand sets drawn from NaN, both infinities, -0.0, subnormals, `MIN_NORMAL`, 1e154
  (the threshold where squaring overflows) and `MAX_VALUE`, applied to every Tier 1 and Tier 2 member: no exceptions,
  and every Tier 2 result is valid at 1e-12. Tier 3 and 4 exception types are pinned in `poseExceptionTypesAcrossScaleRange`.
- **IEEE behaviour.** No clamping, saturation or flushing anywhere. Subnormal scale products (1e-320) are accepted
  bit-for-bit.
- **`normalizedOr` fallback identity.** It returns the same reference for every input below `LENGTH_EPS` and every
  non-finite input (checked with `assertSame`).
- **Scaled normalization.** Both `Vec3` and `Quat` divide by the largest component first. They are accurate to
  1 ulp from 1e-12 up to `MAX_VALUE` and with subnormal companion components.
- **`shortestArc` near antiparallel.** Meets the table, with about 21% margin at every row (N1). Between offsets
  1e-10 and 1e-9 the error grows to 8e-6. The contract sets no figure there (I2).
- **`angleTo`, `slerp`, `nlerp` with extrapolation.** Constant angular speed holds, and `slerp(a, b, t)` and
  `slerp(a, -b, t)` are the same rotation.
- **Frames.** Two Gram-Schmidt passes are present and effective: at the 1e-8 threshold the residual
  non-orthogonality is about 1e-16, not 1e-8. Handedness is checked with an independent triple product. The
  parallel-transport walk stays valid.
- **Pose.** Composition and inversion match the formulas. Representability is checked before construction, so no
  `IllegalArgumentException` is reachable from `then` or `inverse` (50,000 random pairs with scales from 1e-320 to
  1e308). A positive subnormal scale is accepted, an underflow to zero throws `ArithmeticException`, and the inverse
  of a subnormal scale throws `ArithmeticException`.
- **Large coordinates.** At Minecraft's world border (2.9999984e7) the worst round-trip error is 4.6e-8 blocks,
  about 20x inside the contract.

## 4. Findings

### 4.1 Confirmed implementation defects

**D1. `Pose.inverse` names the wrong cause when `1/s` overflows. Severity: low; affects the message only.**

- Clause: section 0A, Tier 3: "The exception message should name the operation and the reason (not tested)."
- Smallest input: `new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-310).inverse()`
- Expected: `ArithmeticException` whose message names the **scale** (`1/1e-310` overflows to infinity).
- Actual: `ArithmeticException("Pose.inverse: translation is not representable")`. Here `t = 0 * -inf = NaN`, and
  `checkResult` tests the translation before the scale. The same happens for every subnormal scale whose reciprocal
  overflows, including `1/Double.MAX_VALUE` and `Double.MIN_VALUE`, and for any translation.
- The exception type and the behaviour are correct. The supplied tests are unaffected.
- Suggested fix (not applied):

```diff
     private static void checkResult(String operation, Vec3 t, double s) {
-        if (!t.isFinite()) throw new ArithmeticException(operation + ": translation is not representable");
         if (!Double.isFinite(s) || s <= 0) {
             throw new ArithmeticException(operation + ": scale is not representable");
         }
+        if (!t.isFinite()) throw new ArithmeticException(operation + ": translation is not representable");
     }
```

- Test: `ReviewFindingsTest.d1InverseMessageNamesScale`.

### 4.2 Test and specification ambiguities

Each item needs a decision from the contract owner. The implementation follows a defensible reading in every case.

**A1. `transportY`: "new y exactly normalize(newY)".**
The implementation normalizes `newY` and then `alignY` normalizes it again. In 4.1% of random inputs the result
differs from `normalize(newY)` by up to 4 ulp (4.0e-16).

- Smallest input: `Frame.transportY(Frame.WORLD, new Vec3(2, 3, 7)).y()` is `(0.254000254000381, 0.38100038100057143,
  0.8890008890013336)`, while `new Vec3(2, 3, 7).normalizedOr(null)` is `(0.25400025400038095, 0.38100038100057143,
  0.8890008890013334)`.
- `fromHeading`'s `z` has the same double normalization relative to `-normalize(forward)`, but the contract does not
  say "exactly" there.
- Section 0 rule 5 says tests compare with tolerances, and `FrameTest` checks this with 1e-11 or 1e-12. "Exactly" most
  likely means "set to, not rotated onto".
- Decision needed: if bitwise identity is intended, `transportY` must build the frame from the already-normalized `uy`
  without normalizing it again (a small package-private helper). Otherwise reword the clause.
- Test: `a1TransportYIsExactlyNormalizeNewY`.

**A2. The fallback of `Quat.inverse` is `(1, -0.0, -0.0, -0.0)`, not `IDENTITY`.**
`new Quat(Double.NaN, 0, 0, 0).inverse()` returns `IDENTITY.conjugate()`. As a rotation this is the identity, but
`Quat` is a record and compares `double` components with `Double.compare`, so `equals(Quat.IDENTITY)` is false. The
contract both defines `inverse` as `normalized().conjugate()` and says "own fallbacks exactly as specified
(`IDENTITY`)". Decide which wins. Test: `a2InverseFallbackIsIdentity`.

**A3. `null` operands are unspecified.**
The `Frame` and `Pose` constructors reject `null` with `IllegalArgumentException`. But `Quat.axisAngle(null, 1)`, a
Tier 3 method that "throws IllegalArgumentException on invalid input", throws `NullPointerException`. The Tier 2
factories `alignY(null, ..)`, `alignZ(null, ..)`, `fromHeading(null, ..)`, `transportY(f, null)`,
`shortestArc(null, ..)` and `fromFrame(null)` also throw `NullPointerException`. Tier 2 only promises "every double
value of every operand", and `fromHeading` explicitly handles a `null` up vector, so the intent for other nulls is
unclear. Test: `a3AxisAngleNullAxis`, for the Tier 3 case only.

**A4. Hint "zero" versus "below `LENGTH_EPS`".**
Section 4 says an `xHint` (or `yHint`, or `up`) that is "zero/non-finite" falls back. The implementation also falls
back for non-zero hints whose largest component is below 1e-12, using `normalizedOr` semantics.

- Smallest input: `Frame.alignY(Vec3.Z, new Vec3(0, 1e-13, 0))` gives `x = (1, 0, 0)` (the fallback). The literal
  reading gives `x = (0, 1, 0)`.
- Same for `Frame.fromHeading(Vec3.Z, new Vec3(1e-13, 0, 0))`: the literal reading uses the hint +X, the
  implementation uses +Y.
- The axis arguments say "below `LENGTH_EPS`" explicitly, which suggests that is the intent for hints too.
- Test: `a4TinyNonZeroHintIsHonoured`.

**A5. The parallel test in `fromHeading`: raw or normalized cross product?**
Section 4 says "cross product magnitude below 1e-8" right after `right = normalize(forward.cross(upHint))` with raw
operands. The implementation tests the normalized vectors, which is scale-invariant and consistent with the
"(computed from the normalized hint)" wording for `alignY`.

- Smallest input: `Frame.fromHeading(new Vec3(1e-9, 1e-3, 0), Vec3.Y)`. The raw `abs(f x u)` is 1e-9, below the
  threshold, so the literal reading falls back to up hint +Z and gives `y` near (0, 0, 1). The implementation sees a
  sine of 1e-6 and gives `y = (-0.9999999999995, 1e-6, 0)`.
- These are frames 90 degrees apart, so the decision matters for near-vertical headings with short vectors.
- Recommendation: adopt the implementation's normalized reading in the contract text.
- Test: `a5FromHeadingRawCrossThreshold`.

**A6. The `rotate` domain conflicts with the Tier 1 accuracy row.**
The domain table admits quaternions with "norm within 1e-12 of 1". Section 6 promises Tier 1 accuracy of 1e-13 times
the operand magnitudes. For `abs(q) = k`, the mandated formula returns `v + k^2 (Rv - v)`, an error of up to
`2 (k^2 - 1) abs(v)`, which is about 4e-12 `abs(v)` (measured worst over 200,000 random cases: 3.99e-12 `abs(v)`).

- Smallest input: `new Quat(0, 0, 0, 1 + 1e-12).rotate(new Vec3(1, 0, 0))` gives `x = -1.0000000000040004`, an error
  of 4.0e-12, more than 1e-13.
- No implementation of the mandated formula can meet both clauses. Either tighten the domain (norm within about
  1e-14 of 1) or state the accuracy as `O(abs(q)^2 - 1)` relative.
- Test: `a6RotateAccuracyForNearUnitQuaternion`.

### 4.3 Numerical improvements outside M1's agreed scope

- **I1. Intermediate overflow in `rotate`.** `2u x (u x v)` overflows for `abs(v)` above `MAX_VALUE/2`
  (8.988e307) even though the rotated vector is representable. For example,
  `axisAngle(Z, pi).rotate((MAX, 0, 0))` gives `(-inf, 2.2e292, 0)`, while the exact result is about `(-MAX, 0, 0)`.
  As a result `new Pose((MAX, 0, 0), halfTurnZ, 1).inverse()` throws `ArithmeticException` for a representable
  inverse. This is far outside the 1e100 domain, so the contract claims nothing there. It could be fixed by scaling
  in `rotate`, at some cost.
- **I2. Accuracy just above the antiparallel threshold.** The half-vector error grows like about 8e-16 / offset:
  it reaches 4e-6 at offset 2e-10 and 8e-6 at 1.01e-10. The half-turn fallback below 1e-10 has error equal to the
  offset itself, at most 1e-10. The contract fixes no figure between 1e-10 and 1e-9. The error comes from rounding
  in `normalize(from) + normalize(to)`, which loses relative precision as the sum cancels. Whether another
  formulation does better was not investigated. If a figure is wanted for this range, it has to be specified first.
- **I3. `length` and `distance` overflow above 1.3e154.** The contract keeps these as plain IEEE (approved,
  "revisit during curve work"). Noted for M2.
- **I4. `lerp` at large `t`.** `(1-t)a + tb` loses the constant sequence: `(1,1,1).lerp((1,1,1), 1e16) = (0, 0, 0)`.
  This is within the contract's 1e-13 x `abs(t) * abs(a)` bound. The form `a + t(b - a)` would keep it, but would
  lose the exact endpoint at `t = 1`. Noted for curve work.

### 4.4 Nonblocking observations

- **N1.** The `shortestArc` table rows are met with only about 21% margin (worst 7.6e-10, 7.9e-9 and 7.9e-7
  against 1e-9, 1e-8 and 1e-6). This is inherent to the recommended algorithm, about 7.5 x eps / offset, not a
  defect. The supplied `QuatTest.shortestArcDegenerate` passes, but any future change to `normalizedOr` that adds
  even 1 ulp of error would show up here first.
- **N2.** The `Pose` and `Frame` constructors reject `null` axes with `IllegalArgumentException` (good), which goes
  beyond the contract text.
- **N3.** `alignY` and `alignZ` normalize the third axis after the cross product (`x.cross(y).normalizedOr(..)`). The
  contract says only `z = x.cross(y)`. This is harmless: x and y are already unit and orthogonal to about 1e-16,
  and all frame checks pass at 6.7e-16.
- **N4.** The JOML test jar causes 5 `-Xlint:classfile` warnings (class files at version 46). These are not in
  production code.
- **N5.** Codex's commits are unsigned and authored `Codex <codex@local>`. Integrity here rests on the SHA-256 chain
  above, not on signatures.

## 5. Phase 3: new tests

Both files are in `src/test/java/workshop/vfx/math/core/`, use the existing `TestSupport` read-only, and change no
existing file.

`ReviewAdversarialTest` (tag `review-adversarial`, 18 tests, all pass, about 13 s):
`runsOnJava25`, `normalizedOrAgainstExactReference`, `quatNormalizedAgainstExactReference`, `tierTotalityFuzz`,
`frameFactoriesAdversarial`, `alignYUsesPerpendicularComponentOfHint`, `fromHeadingMatchesContractFormula`,
`transportYLongWalk`, `shortestArcNearAntiparallel`, `angleToAccuracy`,
`slerpExtrapolationAgainstAnalyticGreatCircle`, `slerpConstantSpeedAndSignInvariance`,
`nlerpMatchesIndependentFormula`, `fromFrameHalfTurnsAndNearHalfTurns`, `rotateAtDomainEdge`,
`poseNearWorldBorder`, `poseExceptionTypesAcrossScaleRange`, `eulerAgainstRodrigues`.

`ReviewFindingsTest` (tag `review-finding`, 7 tests, class-level `@Disabled`): D1 and A1 to A6, each asserting the
reading described above. Forced on with the init script in `REPRODUCE-REVIEW.md`, the result is 7 of 7 failing
against 8397c04 (`logs/review-findings-enabled.txt`). Actual failure messages:

| Test | Actual |
|---|---|
| D1 | `message was: Pose.inverse: translation is not representable` |
| A1 | expected `z=0.8890008890013334`, was `z=0.8890008890013336` (and `x` 1 ulp off) |
| A2 | expected `Quat[w=1.0, x=0.0, y=0.0, z=0.0]`, was `Quat[w=1.0, x=-0.0, y=-0.0, z=-0.0]` |
| A3 | expected `IllegalArgumentException`, was `NullPointerException` |
| A4 | x was `(1, 0, 0)`, expected to follow the hint +Y |
| A5 | y was `(-0.9999999999995, 1e-6, 0)`, expected about +Z under the literal reading |
| A6 | rotate gave `-1.0000000000040004`, expected `-1` within 1e-13 |

For each A item, delete the test if it is resolved in favour of the implementation, or enable it and fix the code if
it is resolved the other way. D1 needs only the 2-line change in 4.1.

## 6. Recommendation

**Accept the M1 mathematics; request changes of minor scope.** No mathematical defect was found. Every contract
formula, convention, tier rule and accuracy figure is met, mostly by orders of magnitude. Before final acceptance:

1. Codex fixes D1 (message order) and reruns the 97 supplied tests plus `ReviewAdversarialTest`.
2. The contract owner decides A1 to A6. A5 and A6 are the ones with practical effect: A5 can choose frames 90
   degrees apart for short near-vertical headings, and A6 makes a clause impossible to meet. Any resulting code
   changes are re-verified with the tests above.
3. I1 to I4 are optional and can be deferred to the milestones where curves arrive.

## 7. Addendum: decision round

The owner's decisions on D1 and A1 to A6 are drafted in `docs/AMENDMENT-3-REVIEW-DECISIONS.md` (contract
revision 4, **proposed**). That round changed the review's test files:

- `ReviewFindingsTest` (7 disabled tests) is replaced by `Amendment3AcceptanceTest` (10 enabled tests). The
  amendment's section 4 maps each finding to its permanent test.
- `ReviewAdversarialTest` tolerances are corrected to the contract's figures. 17 assertions had used 1e-15 or 1e-14
  where the contract promises 1e-12, contradicting this report's statement that every assertion follows from a
  contract clause.
- D1 also affects `Pose.then` (both scale and translation overflow), not only `Pose.inverse`.

Current result on 8397c04: 125 tests, 122 passed, **3 failed by design** (`d1InverseScaleFailureNamesScale`,
`d1ThenScaleFailureNamesScale`, `a3NullAxisIsIllegalArgument`). These are the acceptance criteria for Codex's D1 and
A3 fixes. The counts in sections 1 and 5 above describe the first review round.
