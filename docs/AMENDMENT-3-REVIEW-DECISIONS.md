# Amendment 3: decisions after the independent M1 review (contract revision 4, APPROVED)

Status: **approved by the project owner on 2026-10-15** (decisions D1 and A1 to A6), with one editorial
clarification: section 3.7 now states the order of the hint checks explicitly. The order matches the existing
behaviour and acceptance tests, so the clarification changes no mathematics. Prepared by Claude Code from
`review/M1-CLAUDE-REVIEW.md` and the owner's decisions. The [R4] text of section 3 is applied to
`docs/IMPLEMENTATION-CONTRACT.md` in its own commit.

**Historical baseline.** Revision 3 stays the historical baseline. It is the approved archive
`verification/input/minecraft-vfx-math-M1-handoff-rev3.zip` (SHA-256
`0f79530734458da8a3fd0fc0ab49c836fed0dbb70d815b1813e3e7b56180ed61`), whose internal `SHA256SUMS.txt` still
verifies, and it is in Git history at `8397c04` (`git show 8397c04:docs/IMPLEMENTATION-CONTRACT.md`). The 97
Revision 3 tests and the golden vectors are not modified by Revision 4.

Scope: one implementation error (D1), six clarifications (A1 to A6), no redesign. The 97 Revision 3 tests and the
golden vectors are unchanged. I1 to I4 and N1 to N5 stay outside M1.

## 1. Classification

| Item | Kind | Code change required by this amendment | Why |
|---|---|---|---|
| D1 | **Implementation error** | yes: `Pose` check order | The implementation reports the wrong reason. Revision 3 asks that the message name the reason. |
| A1 | Specification ambiguity | no | The implementation already meets the clarified tolerance |
| A2 | Specification ambiguity | no | The implementation follows the literal formula, and the clarified rule accepts it |
| A3 | Specification ambiguity, resolved toward a behaviour change | yes: `Quat.axisAngle` null check | Revision 3 never said what a null axis does, so throwing NPE today is not an error. The decision makes it IAE. |
| A4 | Specification ambiguity | no | The implementation already uses the clarified rule |
| A5 | Specification ambiguity | no | The implementation already uses the clarified rule |
| A6 | Specification defect (two clauses cannot both hold) | no | The bound is corrected; the implementation already meets the new one |

The only production changes this amendment requires are the two in **D1** and **A3**. A trial application of
the minimal version of both (not committed, not delivered) passes all 125 tests and `check` on Java 25. See
section 5.

## 2. Decisions, with the reasoning behind each

**D1. Agree with the owner's preference, with a wider scope than the review reported.**
The misattribution also exists in `Pose.then`. When both the scale product and the translation overflow (for
example scale `1e200` composed with scale `1e200` and translation `(1e200, 0, 0)`), `then` currently says
"translation". Rule: check the scale first. A non-representable scale (infinite, zero, or `1/s` overflowing) is the
root cause, and it also makes the translation non-finite in `inverse`. The set of inputs that throw, and the
exception type, stay exactly the same, so the mathematical behaviour is unchanged.

**A1. Agree that the clause should be a tolerance, with a tighter figure than the frame-wide 1e-12.**
The clause exists so that the new `y` is *set* from `newY`, never *rotated onto* it. Rotating `prev.y` by the
shortest arc has errors up to about 1e-6 near antiparallel inputs (`shortestArc` table). A tolerance of 1e-15 per
component keeps that intent, while the frame-wide 1e-12 would accept a rotated `y` away from antiparallel. 1e-15 is
achievable with margin: a correctly scaled normalization is accurate to about 1 ulp (2.2e-16 measured), and at most
two of them happen in sequence (4.04e-16 measured over 10^6 random inputs). The bitwise reading is rejected because
section 0, rule 5 makes tests tolerance-based.

**A2. Agree.** `inverse` is defined as `normalized().conjugate()`. On the fallback path that is
`IDENTITY.conjugate() = (1, -0.0, -0.0, -0.0)`, which is the identity rotation. Requiring `equals(IDENTITY)` would
force a special case in `inverse` that gains nothing mathematically. Stating that record equality is structural
(it also tells `q` and `-q` apart) prevents the same confusion elsewhere.

**A3. Agree for Tier 3. Challenge any extension to the other tiers.**
For Tier 3, `IllegalArgumentException` matches the existing constructors, which already reject null with IAE. Do
**not** extend the Tier 2 totality guarantee to null references. Totality is about `double` values. Making every
Tier 2 operation null-tolerant would add API surface for no mathematical gain, and the documented hint parameters
already cover the useful cases (A4). Other null operands stay unspecified, and callers must not pass them.

**A4. Agree, and state the boundary exactly.**
A hint is *absent* when it is `null`, non-finite, or its largest absolute component is below `LENGTH_EPS`. This is
the same predicate that `normalizedOr` and the axis arguments already use, so one rule covers every input
direction. A hint whose largest component equals `LENGTH_EPS` is present. The absence test is applied to the hint
as supplied, before normalization. The 1e-8 threshold applies afterwards, only to the perpendicular part of the
normalized hint (a present hint, or the +Y that `fromHeading` substitutes for an absent `up`). See section 3.7 for
the exact order.

**A5. Agree. Choose the normalized (sine-of-angle) reading and define `fromHeading` through `alignZ`.**
The literal raw-cross-product reading depends on the input lengths: `fromHeading(f, u)` and `fromHeading(1000 f, u)`
could then differ by 90 degrees. That contradicts the "input lengths are ignored" principle used everywhere else and
the explicit "(computed from the normalized hint)" wording of `alignY`. Defining `fromHeading(forward, up)` as
`alignZ(-forward, effectiveUp)` makes the fallback order and handedness unambiguous. The two definitions are
algebraically identical: `right = n(f x u)`, `up = right x f`, `back = -f`.

**A6. Agree. Choose an exact bound over a tighter domain.**
`rotate` is quadratic in the components of `q`. For `q = k q_hat`, with `q_hat` a unit quaternion, the mandated
formula returns exactly `v + k^2 (R v - v)` in real arithmetic, where `R` is the rotation of `q_hat`. The deviation
from the rotation is `(k^2 - 1)(R v - v)`, and `abs(R v - v) <= 2 abs(v)`. So the achievable, mathematically exact
bound is `(2 abs(k^2 - 1) + 1e-13) abs(v)`. That is at most `4.1e-12 abs(v)` inside the domain, and about
`1e-13 abs(v)` for normalized quaternions. Shrinking the domain to `abs(k - 1) <= 1e-14` instead would push
ordinary caller-normalized quaternions outside the guarantee. The finite-result guarantee is kept: inside the domain
every intermediate is a few times 1e100 at most, far below overflow.

## 3. Contract amendments (exact text)

Apply to `docs/IMPLEMENTATION-CONTRACT.md` on approval, marking each change **[R4]**. Add a revision note to the
header in the style of the R3 note. Nothing else changes.

**3.1 Section 0A, Tier 3, first bullet.** Replace

> - Invalid **inputs** throw `IllegalArgumentException`: `Frame` constructor, `Pose` constructor,
>   `Quat.axisAngle`, `Quat.fromEulerApplyXThenYThenZ` (via `axisAngle`), `MathUtil.clamp` (range).

with

> - Invalid **inputs** throw `IllegalArgumentException`: `Frame` constructor, `Pose` constructor,
>   `Quat.axisAngle`, `Quat.fromEulerApplyXThenYThenZ` (via `axisAngle`), `MathUtil.clamp` (range). **[R4]** A
>   `null` argument to any of these is an invalid input. Outside Tier 3, `null` is accepted only where a clause
>   says so (the hints of section 4). Any other `null` operand is outside the API, and its behaviour is
>   unspecified.

**3.2 Section 0A, Tier 3, third bullet.** Replace

> - Never clamp, saturate or substitute a default. The exception message should name the operation and the
>   reason (not tested).

with

> - Never clamp, saturate or substitute a default. The exception message names the operation and the reason.
>   **[R4]** For `Pose.then` and `Pose.inverse` the reason is the first failing check in the order of section 5
>   (scale before translation), and the message contains the word `scale` or `translation` accordingly. Only those
>   words and the operation name are tested.

**3.3 Section 0A, supported-domain table, `Quat.rotate(v)` row.** Replace

> | `Quat.rotate(v)` | quaternion finite and unit (norm within 1e-12 of 1); `v` components at most 1e100 |

with

> | `Quat.rotate(v)` | quaternion finite with norm `k` within 1e-12 of 1; `v` components at most 1e100. **[R4]** Result finite; accuracy as in section 6 (it depends on `k`) |

**3.4 Section 3, `axisAngle`.** Replace "Throws `IllegalArgumentException` when the axis is non-finite, has magnitude
below `LENGTH_EPS`, or `radians` is not finite." with

> Throws `IllegalArgumentException` when the axis is `null` **[R4]**, non-finite, or has magnitude below
> `LENGTH_EPS`, or when `radians` is not finite.

**3.5 Section 3, `inverse`.** Replace "**`inverse`**: `normalized().conjugate()` (these are rotations)." with

> **`inverse`**: `normalized().conjugate()` (these are rotations). **[R4]** For a zero or non-finite quaternion this
> is `IDENTITY.conjugate() = (1, -0.0, -0.0, -0.0)`, the identity rotation. Record `equals` on `Quat` and `Vec3` is
> structural (`Double.compare` per component). It distinguishes `0.0` from `-0.0` and `q` from `-q`, and is
> **not** a test of rotational or numerical equality. Specified values, including fallbacks, are numerical: `-0.0`
> and `0.0` are equal. Only `normalized()`, `shortestArc` and the Frame factories promise to return the `IDENTITY` or
> `WORLD` *constant*.

**3.6 Section 3, `rotate`.** Append to the bullet:

> **[R4]** For a quaternion of norm `k`, the formula returns exactly `v + k^2 (R v - v)` in real arithmetic, where
> `R` is the rotation of `q / k`. Inside the domain the result is finite and lies within
> `(2 abs(k^2 - 1) + 1e-13) abs(v)` of `R v` (at most about `4.1e-12 abs(v)`).

**3.7 Section 4, hints.** Insert before the `alignY` bullet:

> - **[R4] Hint processing order.** A hint argument (`xHint`, `yHint`, `up`) is evaluated in exactly this order,
>   after the axis itself has been normalized:
>   1. **Absence, on the hint as supplied (unnormalized).** The hint is *absent* when it is `null`, has a
>      non-finite component, or its largest absolute component is below `LENGTH_EPS` (the `normalizedOr` predicate).
>      A hint whose largest component is exactly `LENGTH_EPS` is present. An absent `xHint` or `yHint` is replaced by
>      the fallback hint (the world axis least aligned with the axis, ties X, Y, Z). An absent `up` in `fromHeading`
>      is replaced by +Y. In the bullets below, "zero/non-finite" means "absent".
>   2. **Normalization.** A present hint is normalized. After this step the hint's length plays no further part;
>      step 1 is the only place where it matters.
>   3. **Parallel test, on the normalized hint.** The component of the normalized hint perpendicular to the
>      normalized axis is computed. If its length is below **1e-8**, the hint counts as parallel and is replaced by
>      the fallback hint of step 1. This includes the +Y substituted for an absent `up`. The fallback hint itself
>      is never parallel, because its perpendicular component is at least `sqrt(2/3)`.

**3.8 Section 4, `fromHeading`.** Replace from "Defaults:" to the end of the bullet with

> **[R4]** Defaults and degeneracy are defined by
> `fromHeading(forward, up) = alignZ(-forward, up is absent ? +Y : up)`. That is: `forward` absent returns `WORLD`
> ("facing -Z"). An absent `up` means +Y. `forward` and `up` count as parallel when the sine of the angle between
> them, `abs(normalize(forward) x normalize(up))`, is below 1e-8. The input lengths never matter. In that case the up
> hint is the least-aligned world axis of `forward` (ties X, Y, Z). Handedness: `right x up = back`. Worked examples
> (tolerance 1e-12):
>
> | `forward` | `up` | `x` (right) | `y` (up) | `z` (back) | Why |
> |---|---|---|---|---|---|
> | (0, 0, 1) | (0, 1, 0) | (-1, 0, 0) | (0, 1, 0) | (0, 0, -1) | facing south |
> | (0, 1, 0) | (0, 1, 0) | (0, 0, -1) | (1, 0, 0) | (0, -1, 0) | parallel: fallback up +X |
> | (1e-9, 1e-3, 0) | (0, 1, 0) | (0, 0, 1) | (-0.9999999999995, 9.999999999995e-7, 0) | (-9.999999999995e-7, -0.9999999999995, 0) | sine 1e-6: hint honoured |
> | (1e-3, 1e3, 0) | (0, 7, 0) | same as previous row | | | lengths ignored |
> | (1e-9, 1, 0) | (0, 1, 0) | (1, -1e-9, 0) | (0, 0, 1) | (-1e-9, -1, 0) | sine 1e-9: fallback up +Z |
> | (0, 0, 1) | (1e-13, 0, 0) | (-1, 0, 0) | (0, 1, 0) | (0, 0, -1) | up absent: +Y |
> | (0, 0, 1) | (1e-12, 0, 0) | (0, 1, 0) | (1, 0, 0) | (0, 0, -1) | up present |

**3.9 Section 4, `transportY`.** Replace "(new `y` exactly `normalize(newY)`)" with

> (new `y` equals `normalize(newY)` to 1e-15 per component **[R4]**. It is set from `newY`, never obtained by
> rotating `prev.y`.)

**3.10 Section 5, representability check.** After "every component of `t` is finite, and `s` is finite and strictly
greater than 0." insert

> **[R4]** The scale is checked first, so the exception names the scale whenever the scale is not representable,
> including when that also made `t` non-finite (`inverse` of a subnormal scale). The set of inputs that throw is
> unchanged.

**3.11 Section 6, Tier 1 row.** Replace "finite; absolute error at most 1e-13 times the operand magnitudes involved"
with

> finite; absolute error at most 1e-13 times the operand magnitudes involved, **[R4]** except `Quat.rotate`, whose
> bound for a quaternion of norm `k` is `(2 abs(k^2 - 1) + 1e-13) abs(v)` (section 3)

## 4. Disposition of the seven disabled finding tests

`ReviewFindingsTest` is deleted. Its seven tests are replaced by permanent regression tests in
`Amendment3AcceptanceTest` (tag `amendment-3`, 10 tests, enabled).

| Finding test | Becomes permanent? | Expectation | Permanent test(s) | On 8397c04 |
|---|---|---|---|---|
| D1 `d1InverseMessageNamesScale` | yes | unchanged in substance, extended to `then` and to the translation-only case | `d1InverseScaleFailureNamesScale`, `d1ThenScaleFailureNamesScale`, `d1TranslationFailureNamesTranslation` | **fail, fail**, pass |
| A1 `a1TransportYIsExactlyNormalizeNewY` | yes | **adjusted**: bitwise becomes 1e-15 per component | `a1TransportYAxisTolerance` | pass |
| A2 `a2InverseFallbackIsIdentity` | yes | **adjusted**: record `equals` becomes numerical identity rotation; also pins that `equals` is structural | `a2DegenerateInverseIsIdentityRotation` | pass |
| A3 `a3AxisAngleNullAxis` | yes | unchanged (IAE), extended to the Tier 3 constructors | `a3NullAxisIsIllegalArgument` | **fail** |
| A4 `a4TinyNonZeroHintIsHonoured` | yes | **inverted**: a tiny hint is absent; exactly `LENGTH_EPS` is present | `a4TinyHintsAreAbsent` | pass |
| A5 `a5FromHeadingRawCrossThreshold` | yes | **inverted**: normalized rule, worked examples, scale invariance, equivalence with `alignZ` | `a5FromHeadingExamples`, `a5FromHeadingIsScaleInvariant` | pass |
| A6 `a6RotateAccuracyForNearUnitQuaternion` | yes | **adjusted**: 1e-13 becomes `(2 abs(k^2 - 1) + 1e-13) abs(v)`, plus finiteness | `a6RotateBoundForNearUnitQuaternions` | pass |

`ReviewAdversarialTest` (18 tests) also stays permanent. In this round its tolerances were corrected down to the
contract's own figures (section 6).

## 5. Acceptance criteria for Codex

Codex implements D1 and A3 on top of `review/m1-claude` (or applies the test files to `m1-core-transforms`).
Acceptance requires all of the following:

1. **Integrity.** `git diff 77a1430 -- src/test/resources tools` is empty. The 13 Revision 3 test sources
   under `src/test/java` are byte-identical (their entries in the Revision 3 `SHA256SUMS.txt` still verify). Under
   `docs/`, the only differences from Revision 3 are this file and, after approval, the [R4] text of section 3 in
   `IMPLEMENTATION-CONTRACT.md`, applied in its own commit.
2. **Production diff.** The diff of `src/main` against `05c2706` is limited to the D1 check order (`Pose`) and the
   A3 null check (`Quat.axisAngle`). Any other production change must be justified separately.
   Illustrative minimal change (trial-verified, not committed):

   ```diff
   --- Pose.java  checkResult
   -        if (!t.isFinite()) throw new ArithmeticException(operation + ": translation is not representable");
            if (!Double.isFinite(s) || s <= 0) {
                throw new ArithmeticException(operation + ": scale is not representable");
            }
   +        if (!t.isFinite()) throw new ArithmeticException(operation + ": translation is not representable");
   --- Quat.java  axisAngle
   -        Vec3 unit = axis.normalizedOr(null);
   +        Vec3 unit = axis == null ? null : axis.normalizedOr(null);
   ```
3. **Java 25 run.** With `JAVA_HOME` pointing to a JDK 25 and the Gradle 9.1.0 wrapper:
   `./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test` reports **125 tests,
   125 passed, 0 failed, 0 skipped**: the original 97, `ReviewAdversarialTest` 18, `Amendment3AcceptanceTest` 10.
   `ReviewAdversarialTest.runsOnJava25` passes. `./gradlew check` succeeds.
4. **Before the fix (reference point).** On 8397c04 with these tests, exactly three tests fail:
   `d1InverseScaleFailureNamesScale`, `d1ThenScaleFailureNamesScale` and `a3NullAxisIsIllegalArgument`.
5. **Evidence.** Logs as in `verification/`: versions, both Gradle runs, JUnit XML, and the commit hashes.

M1 is **not** finally accepted by this amendment. Final acceptance follows Codex's verified run and the owner's
decision.

## 6. Corrections to the review itself (found in this round)

- `ReviewAdversarialTest` claimed that every assertion follows from a contract clause, but 17 assertions used 1e-15
  or 1e-14 where the contract promises 1e-12. They passed, but as permanent acceptance tests they would have rejected
  valid implementations. They are corrected to 1e-12. Two derived tolerances remain and are justified inline: the
  alignY x-axis conditioning bound `1e-12 + 1e-14 / eps`, and world-border round trips in world units.
- D1 was reported for `inverse` only; `then` has the same misattribution (section 2).
- While writing the acceptance tests, the literal `9.999999999999999e-13` turned out to round to the same double as
  `1e-12`. The test uses `Math.nextDown(LENGTH_EPS)` instead.

## 7. Outside M1 (unchanged)

I1 (overflow inside `rotate` above `MAX_VALUE/2`), I2 (no accuracy figure for `shortestArc` offsets between 1e-10
and 1e-9), I3 (`length` overflow above 1.3e154) and I4 (`lerp` constancy at huge `t`) do not block correctness
inside the supported domain and stay deferred. N1 to N5 need no action.
