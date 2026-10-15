# M3 revision 2: mathematical correction report

Status: **PROPOSED, revision 2, for owner approval. Not authorized for implementation.** Nothing in this revision is
production code. Base: revision 1, commit `7d0ee2b97f246ba27b19111fa3b585febf0ee0c6` on `m3-spec`, which stays in the
history unchanged. Revision 2 is a new commit on the branch `m3-spec-r2`. M1 and M2 are untouched (section 9).

Scope, per the review: correct finding **A1** (large `startTurn` destroys the exact fractional phase) and finding
**A2** (spiral bounds invalid under extrapolation), clarify **DM3-8**, and keep everything else of revision 1:
- the seven public types;
- Java 25 and pure Java;
- M1 and M2 compatibility;
- `StrictMath` trigonometry and turns as phase units;
- exact Bezier endpoints and polynomial easing;
- the M4 boundary.

Notation: `u = 2^-53` (unit roundoff); `fl(.)` is a rounded operation, `|fl(x op y) - (x op y)| <= u |x op y|`;
`frac(x) = x % 1.0`, plus 1.0 if negative, with 1.0 mapped to 0.0.

## 1. Finding A1: phase reduction with a large `startTurn`

### 1.1 Reproduction (`validation/r2/counterexamples.txt`, Java 25.0.4.1)

Revision 1 recommended `f = frac(frac(turns * t) + startTurn)`. With `startRadius = endRadius = 1`, `rise = 0`,
`turns = 1`, `startTurn = 2^52` and `t = 1/4`, the exact phase is `2^52 + 1/4`, so T3 requires the point (0, 0, -1).

| startTurn | revision 1 f | revision 1 point | revision 2 f | revision 2 point |
|---|---|---|---|---|
| 2^52 | 0.0 | (1, 0, -0) | 0.25 | (-0, 0, -1) |
| -2^52, ±2^53, 2^60, ±MAX_VALUE | 0.0 or -0.0 | (1, 0, -0) | 0.25 | (-0, 0, -1) |
| 2^51 + 0.5 (exact phase frac 0.75) | 0.0 | (1, 0, -0) | 0.75 | (0, 0, 1) |

Root cause: `frac(turns * t)` lies in [0, 1), and adding an unreduced `startTurn` rounds the sum to a multiple of
`ulp(startTurn)`. Every double with `abs(x) >= 2^52` is an integer, so the fraction disappears. Smaller offsets lose
bits too (at 2^40, the sum is rounded to 2^-13 turn). Revision 1 masked this with an `abs(startTurn)` term in its phase
bound, which made that bound vacuous (2π rad at 2^52) while T3 was violated.

### 1.2 Correction

`f = frac(frac(turns * t) + frac(startTurn))` (contract 5, T2, now normative in its structure: both parts are reduced
on their own before they meet).

Lemma 1 (exactness of `frac`). For finite x, `x % 1.0` is exact (IEEE remainder of `fmod` type; M2 fact F5). Hence:
- for `x >= 0`, `frac(x)` is exact;
- for `x < 0`, `x % 1.0` is in (-1, 0] and exact, and adding 1.0 rounds at most once, by at most u. For
  `x % 1.0` in (-1, -1/2], the sum is exact (Sterbenz); a sum rounding to 1.0 is mapped to 0.0, which is congruent.

Lemma 2 (the combination). Let `a = frac(fl(turns t))` and `b = frac(startTurn)`, both in [0, 1). Then `a + b` lies in
[0, 2), so `fl(a + b)` errs by at most `ulp(1)/2 = u`. Then `frac` of a value in [0, 2) is exact (for [1, 2),
`x - 1` is exact by Sterbenz). Then `4 f` and `floor(4 f)` are exact, and so is `4 f - q`, because `4 f` lies in
[q, q + 1) and is a multiple of `ulp(4 f)`.

**T3, exact quarter turns, for every `startTurn` magnitude.** Suppose `startTurn` is a multiple of 1/4, and the exact
product `turns * t` is a multiple of 1/4 with absolute value below 2^51:
- the product is then representable, so `fl(turns t)` is exact;
- both `a` and `b` are in {0, 1/4, 1/2, 3/4}. For negative inputs, `x % 1.0` is in {0, -1/4, -1/2, -3/4} and adding
  1.0 is exact;
- `a + b` is exact, so f is a multiple of 1/4, `4 f - q = 0`, and the residual angle is 0;
- `StrictMath.cos(0) = 1` and `StrictMath.sin(0) = 0` exactly, and the quadrant table yields cos and sin in {0, ±1}.

Every double with `abs(x) >= 2^51` is a multiple of 1/2, and hence of 1/4. So large integers, `±2^52`, `±2^60` and
`±Double.MAX_VALUE` all qualify. The 2^51 limit on the product remains necessary: above it, a multiple of 1/4 need not
be representable.

**T4, exact seams.** If `closed()` holds, `turns` is a non-zero integer. For integer-valued k with `turns * k` finite,
the exact product is an integer:
- below 2^53 it is representable;
- at or above 2^52 every double is an integer, so its rounding is an integer too.

So `a = 0` (or -0.0, which behaves identically). Then `f = frac(0 + b) = b`, the same as at k = 0. The radius
(constant) and the height (`rise = 0`) do not depend on k, so `point(k) == point(0)` and `derivative(k) ==
derivative(0)`. Revision 1 limited T4 to `abs(turns k) < 2^53`; the limit is unnecessary.

**T5, period invariance (new).** For `x, y >= 0` with `x - y` an integer, `frac(x) == frac(y)` (Lemma 1, both exact),
so the whole evaluation is identical. For negative values, `frac` may round once (`frac(-x)` computes `1 - x`, which
need not be representable, while the fraction of a congruent non-negative value is exact), so congruent negative
offsets agree only within the bound. The tests check both.

**Phase error bound.** Error of the reduced phase f against the exact phase `startTurn + turns t`, in turns:

| Source | At most |
|---|---|
| `fl(turns * t)` | `u abs(turns t)` |
| `frac` of a negative product | u |
| `frac` of a negative `startTurn` | u |
| `fl(a + b)` | u |
| `(4 f - q) * fl(pi / 2)`: rounding of pi/2 and of the product, at most `pi u` rad | u / 2 |
| total | `u (abs(turns t) + 3.5) <= 2^-52 (abs(turns t) + 2)` |

Hence `dphi = 2 pi 2^-52 (abs(turns t) + 2)` radians. The `abs(startTurn)` term is gone. Revision 1's
`2 pi 2^-52 (abs(turns t) + abs(startTurn) + 2)` was an upper bound for its own reduction, but that reduction violated
T3. The `StrictMath` error (below 1 ulp) is part of the 2^-49 / 2^-48 / 2^-47 coefficients (section 2.3).

**Precision limits (contract 7.2).**
- The magnitude of `startTurn` costs no evaluation accuracy.
- A double can only carry a fractional part that is a multiple of `ulp(startTurn)`: 2^-32 turn at 2^20, 1/4 turn at
  2^50, and none from 2^52 on. This is quantization of the input, and the result is exact for the double that was
  passed. Callers that accumulate phases should keep `startTurn` in [0, 1).
- For `abs(turns t) >= 2^52`, `fl(turns t)` is an integer and t no longer moves the phase. This is outside the
  practical domain of 2^20.

**Rejected alternatives**, each shown to fail by a regression test and a mutant (section 7):
- `frac(turns t + startTurn)`, the naive sum;
- `frac(turns t + frac(startTurn))`, with the product unreduced. Just below 2^51, `turns t = 2^51 - 1/4` plus
  `b = 1/2` rounds to 2^51, which gives quarter 0 instead of 1.

## 2. Finding A2: spiral bounds under extrapolation

### 2.1 Reproduction

Take `startRadius = 1`, `endRadius = 1.2` (the double `1.1999999999999999555910790149937...`), `rise = 0`, `turns = 0`,
`startTurn = 0` and `t = -5`:
- the exact radius is `6 - 5 * 1.2 = 2^-52 = 2.220446049250313e-16` (`BigDecimal`);
- `(1 - t) r0 + t r1` gives `fl(6 + fl(-5.999999999999999778)) = fl(6 - 6) = 0`;
- revision 1's bound was `2^-49 R + R dphi` with `R = abs(r(t))`, about 1.0e-30 for the exact R. The revision 1 test
  computed R with the same floating-point lerp, which gave 0, so its tolerance was `ulp(2^-52) = 4.9e-32`;
- the error is 2^-52. Revision 1 therefore rejects a correct evaluation. The bound is invalid; the implementation is
  not at fault.

Root cause:
1. The absolute rounding error of `(1 - t) r0 + t r1` scales with the magnitudes of the terms, not with their
   cancelled sum.
2. The revision 1 test partly copied the algorithm into the tolerance, so it inherited the same cancellation.

### 2.2 Radius lemma

Lemma 3. For finite t and `r0, r1 >= 0` with `r0 != r1`, let `S = abs(1 - t) r0 + abs(t) r1` and
`r~ = fl(fl(fl(1 - t) r0) + fl(t r1))`. Then `abs(r~ - r) <= 2^-51 S`.

Proof. We have `fl(1 - t) = (1 - t)(1 + d1)`, `A = fl(fl(1 - t) r0) = (1 - t) r0 (1 + d1)(1 + d2)`,
`B = fl(t r1) = t r1 (1 + d3)` and `r~ = (A + B)(1 + d4)`, with every `abs(di) <= u`. Then:
- `abs(A - (1 - t) r0) <= (2u + u^2) abs(1 - t) r0`;
- `abs(B - t r1) <= u abs(t) r1`;
- `abs(r~ - (A + B)) <= u abs(A + B) <= u (abs(r) + (2u + u^2) S)`.

Since `abs(r) <= S`, the total is at most `(3u + 3u^2 + u^3) S < 4u S = 2^-51 S`. ∎

Notes:
- On [0, 1] both terms are non-negative, so `S = r(t)`: the lemma is a relative bound and nothing is loosened.
- Exact cases: `r~(0) = r0`, `r~(1) = r1` and equal radii (by the special case). These are normative (contract 5).
- `r0 + t (r1 - r0)` does not satisfy the lemma. Its error is about `u abs(t) abs(r1 - r0)`, and near t = 1 with
  `r0 >> r1`, `abs(t (r1 - r0))` is about r0 while S is about r1. It is also not exact at t = 1. Mutant X06 is now
  detected by `SpiralTest.radiusAccuracyNearEnds` and by the revision 2 reference rows.
- Requiring a correctly rounded `r(t)` would need `fma` or extended-precision arithmetic. `Math.fma` is on the
  forbidden list of contract 7.1 and the source scan (a revision 1 decision that this revision does not change), and
  extended precision is out of proportion for a VFX curve.

### 2.3 Revised bounds (contract 7.2)

Let `S = startRadius` if the radii are equal, else `abs(1 - t) startRadius + abs(t) endRadius`. Let
`dr = abs(endRadius - startRadius)`, `w = 2 pi abs(turns)` and `dphi` as in section 1.2. The cosine and sine
actually used, `c~` and `s~`, differ from the exact values by at most `dphi + u` (the phase error, plus `StrictMath`
below 1 ulp of a value at most 1).

| Quantity | Error budget (multiples of u, then the phase term) | Bound |
|---|---|---|
| point x, z: `r~ c~` | radius 4u S; trigonometry u r; product u; phase `r dphi <= S dphi`; at most 7u S + S dphi | `2^-49 (S + abs(rise t)) + S dphi` (16u) |
| point y: `rise t` | u `abs(rise t)` | included |
| derivative: `dr c~ - w r~ s~` | `dr` u; `w = fl(fl(2 pi) turns)` 2u; radius 4u S; trigonometry u; 3 products 3u; the difference u; at most 14u W | `2^-48 W + W dphi`, with `W = dr + w S + abs(rise)` (32u) |
| second: `-2 dr w s~ - w^2 r~ c~` | `w^2` 5u; radius 4u S; trigonometry u; products 2u; the sum u; at most 13u W2 | `2^-47 W2 + W2 dphi`, with `W2 = 2 dr w + w^2 S` (64u) |

The coefficients are unchanged from revision 1 and leave at least 2x headroom over the budgets. The only changes are
S in place of R (the radius rounding is relative to S) and the removal of the `startTurn` term from dphi. For the A2
counterexample, S = 12, and the bound is `2^-49 * 12 + 12 * 2 pi 2^-51 = 5.5e-14`, against an actual error of
2.2e-16 (ratio 0.004). It stays meaningful: the test asserts that it is below 1e-13.

**Principle (DM3-12):** bounds and test tolerances are functions of the inputs only (S, `abs(turns t)`, `abs(rise)`),
never of a rounded intermediate of the formula under test. The tests compute S from non-negative terms (no
cancellation), with a factor `(1 + 2^-50)` for their own rounding.

## 3. DM3-8 clarification

The trade-off "exact endpoints or exact constancy" holds for **ordinary unbranched** lerp formulas:
- `(1 - t) a + t b`: exact endpoints, but `0.7 * 3 + 0.3 * 3 = 2.9999999999999996`;
- `a + t (b - a)`: exact constants, but `a + 1 * (b - a)` need not equal b.

Explicit special cases give both properties:
- **V1:** `a == b ? a : (1 - t) a + t b`, per component;
- **V2:** `t == 0 ? a : t == 1 ? b : a + t (b - a)`.

The recommendation is unchanged: the plain de Casteljau lerp. E1 to E3 accept both, and the validation ran the whole
suite against V1 and V2 Bezier variants of the reference (section 6). The spiral radius already uses an explicit
special case (`startRadius == endRadius`).

## 4. New and changed tests and data

| Item | Change |
|---|---|
| `SpiralTest.largeStartTurnExactQuarters` | new (A1, T3): the review counterexample; 26 `startTurn` values (`±2^52`, `±2^53`, `2^52 + 1`, `±2^60`, `2^1023`, `±MAX_VALUE`, `2^50 + 1/4`, `2^51 + 1/2`, negative quarters, -0.0, ...) times 12 (turns, t) pairs; products just below 2^51; derivatives within the bound. Oracle: exact `BigDecimal` phase, quadrant from the exact quarter count |
| `SpiralTest.startTurnPeriodInvariance` | new (T5): 20,000 cases, `startTurn = k + f` with k up to 2^52 + 999, bitwise against `startTurn = f`; negative offsets within twice the bound |
| `SpiralTest.nearSeamOffsets` | new (A1): `startTurn` in {`±2^-60`, `±MIN_VALUE`, `±(1 - 2^-53)`, -1e-17, `1 - 2^-30`, `±(2^40 + 1 - 2^-53)`} times 6 (turns, t) pairs, against exact small-angle values (`BigDecimal` phase, 50-digit pi, Taylor terms to theta^4) |
| `SpiralTest.seamsForLargeParameters` | new (T4): integer t up to `±2^60`, `startTurn` up to 2^1000 |
| `SpiralTest.extrapolatedRadiusCancellation` | new (A2): the review counterexample (point and derivative), and the bound below 1e-13; 20,000 cancellation cases `r1 = fl((t - 1) r0 / t)` (±1 ulp, exact radius 0, positive or negative), on quarter phases, with exact `BigDecimal` point, derivative and second derivative (50-digit pi) |
| `SpiralTest.radiusAccuracyNearEnds` | new (radius rule): 20,000 cases with very different radii, t within 2^-10 of the end with the small radius, against the exact `BigDecimal` radius |
| `CurveTestSupport` | `phaseErr(turns, startTurn, t)` becomes `phaseErr(turns, t)`, without the `startTurn` term; new `spiralScale`, `spiralPointTol`, `spiralDerivativeTol` and `spiralSecondTol` (section 2.3 bounds from inputs only) |
| `M3ReferenceVectorTest.spiral` | tolerances from the new helpers (S, not the floating-point `abs(r(t))`); row count asserted (959); display name |
| `tools/generate_m3_reference_vectors.py` | new `spiral_adversarial_rows` with its own seed (20261017), called after the revision 1 rows. The oracle is unchanged (exact rational phase and radius, 60-digit Taylor series) |
| `spiral.csv` | rows 1 to 549 byte-identical to revision 1; 410 rows appended: 152 EXACT (large and negative quarter `startTurn`, products below 2^51) and 258 ROUNDED (60 large fractional `startTurn`, 55 near-seam, 3 for the A2 counterexample, 80 cancellation, 60 near-end radius) |
| `easing.csv`, `bezier.csv` | unchanged (byte-identical) |
| `MANIFEST.sha256` | `spiral.csv` line updated |

Totals: 44 M3 tests (revision 1: 38) and 235 with M1 and M2 (revision 1: 229). The reference data has 2,759 rows
(836 EXACT, 1,923 ROUNDED).

## 5. Before / after evidence (`validation/r2/before-after.txt`)

| Run | Tests | Reference implementation | Data | Result |
|---|---|---|---|---|
| B3: revision 1 as delivered | revision 1 | revision 1 | revision 1 | 38 / 38 pass: neither fault was detected |
| B1: A1 before | revision 2 | revision 1 (`frac(frac(turns t) + startTurn)`) | revision 2 | **3 fail**: the A1 counterexample (`largeStartTurnExactQuarters`), T5 (`startTurnPeriodInvariance`: 2^50 + 1 against 1.0), `spiral.csv` (first EXACT row with `startTurn` 2^52) |
| B2: A2 before | revision 1 | revision 2 | revision 2 | **1 fails**: `M3ReferenceVectorTest.spiral` rejects the correct value of the A2 counterexample row (exact `(2^-52, 0, 0)`) |
| after | revision 2 | revision 2 | revision 2 | **44 / 44 pass** (235 / 235 with M1 and M2, clean run) |

Over the data, the bound-ratio probe (`validation/r2/ProbeBoundRatiosR2.java`) reports:
- revision 1 reference: 79 EXACT mismatches, and errors up to 3.8e14 times the revision 2 bound on the large
  `startTurn` rows;
- revision 1 tolerance: rejects 49 ROUNDED rows that the revision 2 reference evaluates correctly (all cancellation
  rows).

## 6. Fresh validation against a throwaway reference implementation

The revision 2 reference differs from the revision 1 reference in one line, `Spiral.cs`:
`frac(turns * t) + frac(startTurn)`. Like revision 1, it is kept outside the repository and is not delivered.
Results are in `validation/r2/reference-run-summary.txt`:
- clean `clean test`, then `check`, Java 25.0.4.1, Gradle 9.1.0: **235 / 235 passed** (M1 125, M2 66, M3 44), `check` successful;
- worst error / revision 2 bound over all ROUNDED rows: spiral point 0.417, derivative 0.417, second derivative 0.426 (revision 2 rows alone: 0.221, 0.224, 0.225); easing 0.159; Bezier 0.130, 0.112, 0.012 (unchanged data). Every bound keeps at least 2.3x headroom, and no EXACT row mismatches;
- DM3-8 variants V1 and V2 (Bezier special cases), with the revision 2 suite: 44 / 44 for both, and both keep a constant cubic exactly constant at all 100,001 sampled t, where the plain lerp misses at 44,284 (`validation/r2/dm3-8-variants.txt`).

## 7. Mutation check (`validation/r2/mutation-results.txt`)

The 25 revision 1 mutants (X01 to X25) were rerun against the revision 2 reference, with X01's pattern adapted to
the new reduction line. Six new mutants were added:

| Mutant | Fault | Result |
|---|---|---|
| X26 | **A1 original fault**: revision 1 reduction `frac(frac(turns t) + startTurn)` | detected: 3 tests (`M3ReferenceVectorTest.spiral`, `largeStartTurnExactQuarters`, `startTurnPeriodInvariance`) |
| X27 | naive reduction `frac(turns t + startTurn)` | detected: 5 tests |
| X28 | unreduced product `frac(turns t + frac(startTurn))` | detected: 4 tests (including the products just below 2^51) |
| X29 | the sum of the reduced parts is not reduced again | detected: 4 tests |
| T01 | **A2 original fault** (test side): tolerances from the floating-point `abs(r(t))` instead of S | detected: 2 tests (`M3ReferenceVectorTest.spiral`, `extrapolatedRadiusCancellation`) reject the correct reference |
| T02 | control: phase term of the bound 4096x too tight (the bounds are not vacuous) | detected: 1 test (`M3ReferenceVectorTest.spiral`) |

Overall: **31 / 31 detected**: X01 to X25 still 25 / 25, X26 to X29 4 / 4, T01 and T02 2 / 2.

## 8. Modifications relative to revision 1 (complete)

| File | Modification |
|---|---|
| `docs/m3/M3-REVISION-2-CORRECTIONS.md` | new: this report |
| `docs/m3/M3-IMPLEMENTATION-CONTRACT.md` | status revision 2. Section 4: E3 note (special cases allowed). Section 5: r(t) may be negative under extrapolation; new normative radius rule (exact cases, `2^-51 S`); T2 rewritten (separate exact reduction, non-conforming forms named); T3 for any `startTurn` magnitude; T4 for every integer t; new T5. Section 7.2: bounds with S, dphi without `startTurn`, inputs-only principle, precision limits |
| `docs/m3/M3-DESIGN-DECISIONS.md` | status; summary rows DM3-6, DM3-8 and DM3-12; DM3-6 correction (A1); DM3-8 clarification; DM3-12 correction (A2) and principle |
| `docs/m3/M3-CONVENTIONS.md` | status; domain rows for `startTurn` and the extrapolated radius; facts F9 to F12 |
| `docs/m3/M3-RISKS.md` | status; R2 and R12 revised; R13 new |
| `docs/m3/M3-TEST-SPECIFICATION.md` | counts; inventory; revision 2 validation figures; acceptance criteria (235 tests) |
| `docs/m3/M3-ARCHITECTURE.md` | status; counts; revision 2 report and evidence rows |
| `docs/m3/README-FOR-CODEX-M3.md` | T2 to T5 and the radius rule; 235 tests |
| `docs/m3/validation/r2/` | new: counterexample probe and output, before/after, reference run summary, bound ratios, mutation results, probe sources |
| `src/test/java/workshop/vfx/math/curve/CurveTestSupport.java` | `phaseErr` without `startTurn`; spiral bound helpers with S |
| `src/test/java/workshop/vfx/math/curve/M3ReferenceVectorTest.java` | `spiral()` tolerances and row count |
| `src/test/java/workshop/vfx/math/curve/SpiralTest.java` | 6 new tests (section 4); the 9 revision 1 tests are unchanged |
| `tools/generate_m3_reference_vectors.py` | `spiral_adversarial_rows` |
| `src/test/resources/m3/reference/spiral.csv`, `MANIFEST.sha256` | 410 appended rows; manifest line |

Every other file is byte-identical to revision 1, including `BezierTest`, `EasingTest`, `PosedCurveTest`,
`M3DeterminismTest`, `M3BoundaryTest`, `easing.csv`, `bezier.csv` and `docs/m3/validation/*` (the revision 1
evidence is kept as history).

Expectation changes, every one listed:
1. Spiral reference tolerances use S instead of the computed `abs(r(t))`. On [0, 1] they are equal; under
   extrapolation the tolerance grows to the proven bound.
2. dphi drops the `abs(startTurn)` term. This is stricter.

No EXACT expectation of revision 1 changed, and no revision 1 row or test was removed.

## 9. Unchanged

- M1 and M2 production, tests, golden data, contracts and verification history. The check
  `git diff e3b79ba -- src/main/java/workshop/vfx/math/core src/main/java/workshop/vfx/math/time src/test/java/workshop/vfx/math/core src/test/java/workshop/vfx/math/time src/test/resources/m2 docs/m2 verification review`
  is empty.
- DM3-1 to DM3-5, DM3-7, DM3-9 to DM3-11, DM3-13 and DM3-14.
- The public API (seven types, signatures and factories).

## 10. For approval

1. A1 correction: T2 structure, T3 without a `startTurn` limit, T4 extension, and the new T5.
2. A2 correction: bounds relative to S, and the normative radius rule `2^-51 S` with exact cases.
3. DM3-8 clarification: special cases allowed, with the recommendation unchanged.
4. After approval, and M2's formal acceptance, the package can go to Codex. This revision does not declare M3
   implementation-ready.
