# M4 revision 2.1: corrections after the R2 independent review

Status: PROPOSED, revision 2.1. **Not authorized for implementation.** This revision answers ChatGPT's review of M4 R2
(decision: REVISION REQUIRED). It stops for ChatGPT's final acceptance review.

**What stays the same:**
- the architecture, scope, and the six public types and their signatures;
- M1, M2 and M3, which are untouched;
- the reference data and generator, which are byte-identical to R1 and R2;
- the three R1 fixes (tolerance floor, ratio and linear modes, inversion phases and exits), which are preserved
  unchanged;
- all 54 R2 tests, which are unchanged.

**Review document.** `M4-R2-Independent-Review.md` was **not available** in this session. It was not attached, not on
disk, and not on any branch of the repository.
- F4 and F5 are corrected as the revision request describes them.
- The review's **five minor documentation issues could not be read**. Section 3 lists the documentation corrections I
  found myself while revising the affected sections. **Whichever of the reviewer's five items is not among them
  remains open.** I did not guess at their content.

## 1. F4: half-width underflow

**Problem (reproduced on R2, `validation/r21/findings-r2.txt`):**
- R2 computed the panel rule as `G = h * (W0 v_0 + ...)`, with `h = 0.5 * (b - a)`.
- For a panel of width `MIN_VALUE`, h rounds to 0. Below `2^-1021`, h is subnormal and loses relative precision.
- On `[0, k MIN_VALUE]` every nonzero-width panel has width `MIN_VALUE`. So an ordinary M3 line there had length 0:
  at speed 1, where the true length `MIN_VALUE` is representable, and equally at speed 1e300, where the true length
  4.9e-24 is a normal number.
- `lengthAt(nextUp(t0))` was 0 for the same reason.

**Correction:**
- **Contract 5.2:** the rule multiplies by the width `d = b - a` itself, which is never 0 for `a < b`, with the
  exact half weights `H_i = W_i / 2`. h is used only for the node positions.
- **Contract 10.2:** the absolute underflow term is now `R_w MIN_VALUE / 2`, with `R_w <= 2K` the number of
  nonzero-width half-panel rules in the final panels. It replaces R2's `8 K MIN_VALUE`.
  - The new term is smaller, and the arithmetic now meets it.
  - It is sharp enough to force the exact result where one exists: unit speed on `[0, MIN_VALUE]` has `R_w = 1`, so
    the length must be `MIN_VALUE`.
  - The node-position term gains `2 MIN_VALUE`, for the underflow of h in the positions.
- **Error analysis 3 and 4** prove the per-rule bound `G = G*(1 + theta) + delta`, with `abs(theta) <= 10 eps` and
  `abs(delta) <= MIN_VALUE / 2`. They also re-derive B5 with the new constants: the absolute term drops from
  `144 MIN_VALUE` to `24 MIN_VALUE`, which broadens the conditional part of B5.

**Regression test:** `M4R21RegressionTest.halfWidthUnderflow`.
- Inputs:
  - M3 lines at speeds 1, 3, 2, 3 (a 3D direction), 1e10, 5e299 and 1e300;
  - on `[0, k MIN_VALUE]` for k = 1, 2, 3 and 7, on `[-MIN_VALUE, MIN_VALUE]` and `[-3 MIN_VALUE, 0]`, and on
    `[0, 1e-320]`, `[0, 2^-1060]` and `[0, 1e-310]`.
- Each case is checked against an exact `BigDecimal` length, with the R2.1 allowance. The test derives `R_w` from the
  normative initial partition.
- It also checks `lengthAt(nextUp(t0))`, and that unit speed on `[0, k MIN_VALUE]` gives exactly `k MIN_VALUE`.

**Second test:** `M4R21RegressionTest.subnormalSpeedLongRange`, added for mutation survivor M50 (always the fallback
product order). Lines with subnormal components such as `(q, q, 0)` on [0, 1000] and [-7, 9] must stay within the
allowance without a per-parameter `MIN_VALUE` term.
- R2.1 drops that term from `dv` (contract 10.2). It covered rounding of a subnormal speed product, which the R2.1
  rule never forms.
- The allowance is therefore tighter than R2's.
- The fallback order rounds `M S` while it is subnormal, and that rounding error is then multiplied by the width.

## 2. F5: weighted-sum overflow

**Problem (R2):**
- `W1 (v_1 + v_2)` overflows for speeds above about `MAX_VALUE / 2`, even when G is finite. For example, speed 1e308
  on a width of 1e-300 gives `G = 1e8`.
- `build` then threw `ArithmeticException` ("length is not finite") for finite lengths.
- With `h = 0`, the overflow became `0 * infinity = NaN`. On a **finite** table whose speed is 1e308 near `t = 0`,
  `lengthAt(MIN_VALUE)` returned NaN.
- R2's contract 5.5 justified "G never NaN" by "a sum of non-negative terms times `h > 0`", which was false on both
  counts.

**Correction:**
- **Contract 5.1, speed pairs.** The speed is carried as `(mu, nu)`: the largest absolute component, and
  `sqrt(sum of squared ratios)`, which lies in `[1, sqrt(3)]`. The panel rule never forms the product `mu nu`, so even
  speeds above `MAX_VALUE` (components up to `MAX_VALUE`) are handled. Only the Newton slope of 5.6 forms it; there,
  `+infinity` is rejected by the existing safeguard.
- **Contract 5.2, panel rule.**
  - The ratios `r_i = (mu_i / M) nu_i`, with M the largest `mu_i`, lie in `[0, sqrt(3)]`.
  - `S = H0 r_0 + H1 (r_1 + r_2) + H2 (r_3 + r_4)` lies in `[H2, sqrt(3)]`.
  - G is then `(d S) M` when `d S` is normal. Otherwise it is `d (M S)`, or `(M d) S` when `M S` overflows.
- **Proven:**
  - G is never NaN.
  - G overflows only when the exact rule value exceeds `MAX_VALUE / (1 + 10 eps)`.
  - No intermediate underflow loses more than `MIN_VALUE / 2`.
  - `d S` itself cannot overflow, because every width is at most `MAX_VALUE / 16`.

  Proofs: contract 5.2 and error analysis 3. The guarantee of contract 5.5 (`F_k` never NaN) now rests on 5.2 and
  holds.

**Regression tests (`M4R21RegressionTest`):**

| Test | What it checks |
|---|---|
| `weightedSumOverflow` | Lines with speeds 1e308, `sqrt(2)` 1e308, `sqrt(3) MAX/2`, `sqrt(2) MAX` and `sqrt(3) MAX` (above `MAX_VALUE`), on ranges from `[0, MIN_VALUE]` to `[0, 1e-10]` and `[1, nextUp(1)]`. The length is finite, converged, and within the allowance against BigDecimal. A circle of radius 1e306 on `[0, 1e-300]` matches `2 pi r d` to 1e-12 |
| `finiteTableNoNaN` | A finite table (length about 1) whose speed is `sqrt(2)` 1e308 or `sqrt(2) MAX` only for `t <= 1e-300`. `lengthAt` at `MIN_VALUE`, 2 `MIN_VALUE`, 1e-320 and up to 0.01 is finite and within `[0, length()]`, and `lengthAt(MIN_VALUE)` is within the ratio-mode range of `MIN_VALUE` times the speed. `parameterAt` is finite on a grid. R2 returned NaN |
| `panelRuleRange` | 3,000 random lines (fixed seed) with components from `2^-1000` to `MAX_VALUE`, widths from `MIN_VALUE` to about 2e3 and offsets over the whole exponent range, with exact lengths below `MAX_VALUE / 4`. All are finite, converged and within the allowance. This exercises all three product orders |

## 3. Documentation corrections (self-identified; the review's five minor items were not available)

| # | Where | Correction |
|---|---|---|
| D1 | contract 5.1 | R2's note claimed that an infinite speed (components above `MAX_VALUE / sqrt(3)`) makes "the length not finite". That was false for short ranges. Replaced by the speed-pair definition, and by where the product is formed (5.6 only) |
| D2 | contract 5.5 | The "G never NaN" justification (`h > 0`) was false; it now cites 5.2 |
| D3 | contract 10.2, error analysis 3 | The node-position term ignored the underflow of h and `h X`. It is now `(4 eps T + 2 MIN_VALUE) max abs(C'')` |
| D4 | error analysis 2 | The speed error is split into `nu` (3.5 eps) and the product (4.5 eps, Newton only). `dv` includes `MIN_VALUE` explicitly |
| D5 | contract B5, error analysis 4 | The absolute constant of the conditional B5 condition is now `24 MIN_VALUE` (R2: `144 MIN_VALUE`), derived for the R2.1 order |
| D6 | contract 10.2, error analysis 2 | `dv` no longer contains `MIN_VALUE`: R2.1 never rounds a subnormal speed product. A subnormal derivative component's own error stays in the M3 `delta_c` |
| D7 | test specification, README, architecture, conventions (F10), design decisions (DM4-17) | Counts (59 M4 tests in 10 classes, 313 in total), the evaluation order note for Codex, and the R2.1 facts |

## 4. Validation

- **Tests:** M4 has 59 test methods in 10 classes. R2.1 added `M4R21RegressionTest`, with 4 tests. All 54 R2 tests
  are unchanged; no test was removed or edited.
- **Full suite on Java 25.0.4.1, against the temporary R2.1 reference implementation (not delivered):** 313 tests,
  313 passed (M1 125, M2 66, M3 51, M3 review 12, M4 59). `check` succeeds (`validation/reference-run-summary.txt`).
- **Before and after** (`validation/r21/before-after-tests.txt`):
  - against the R2 reference: 54/59, failing exactly the 5 R2.1 tests;
  - against R1: 48/59;
  - against R2.1: 59/59.
- **Reference data:** unchanged. Regeneration is byte-identical, and `MANIFEST.sha256` verifies.
  - Over 1,344 reference tables (336 rows at four tolerances), R2.1 changes lengths by at most 7.9e-16 relative, and
    no panel count (`validation/r21/r2-vs-r21-reference-values.txt`).
  - Every evidence figure is unchanged: worst achieved error 0.31 tau; inversion mean 13.8 and max 29 evaluations
    (`validation/evidence.txt`).
- **Error bounds revalidated:**
  - per rule: contract 5.2 and error analysis 3;
  - length: contract 10.2 and error analysis 4, with the B5 proof redone;
  - inversion (error analysis 7): unaffected, since it uses only `F_k` and its range.
- **Mutation analysis** (`validation/mutation-results.txt`): 55 mutants (8 of them new in R2.1), with 50 killed,
  4 equivalent (M20, M33, M38, M45) and 1 tolerance-equivalent (M13). Every R2.1 mutant is killed. The survivor of
  the first run (M50) is killed by `subnormalSpeedLongRange`.

## 5. Remaining limitations (unchanged in kind)

- Quadrature accuracy for arbitrary curves is validated, not proven (contract 10.3).
- Lengths of the order of `R_w MIN_VALUE` or below can lose up to `MIN_VALUE / 2` per rule. A length spread over many
  panels can therefore round to 0 (for example, a `7 MIN_VALUE` line on [0, 1]). This is within the allowance.
- Speeds whose length overflows still give `ArithmeticException`. This happens only when the exact rule value exceeds
  `MAX_VALUE / (1 + 10 eps)`.
- The tolerance-equivalent mutant M13 (the partial integral not scaled by `rho`) remains an item for the
  implementation review.
