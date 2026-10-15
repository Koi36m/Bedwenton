# M3 revision 2, amendment A3: subnormal underflow and overflow limits

Status: **PROPOSED amendment to revision 2, for final review. Not authorized for implementation.** No production code.
Base: revision 2, commit `37152118093df38c95bc392e966bb23b4a435dd9` on `m3-spec-r2`, which stays in the history
unchanged. The amendment is one new commit on `m3-spec-r2-a3`.

What it keeps: the approved A1, A2 and DM3-8 decisions, the seven public types, every revision 2 test, all reference
vectors and every relative part of the bounds.

Notation:
- `u = 2^-53`;
- `eta = Double.MIN_VALUE = 2^-1074`, written q in examples;
- S, `dr`, `w` and `dphi` as in contract 7.2.

## 1. Finding A3 and reproduction (`validation/a3/underflow-probe.txt`, Java 25.0.4.1)

| Case | Exact | Throwaway reference | Revision 2 bound |
|---|---|---|---|
| radius, `r0 = q`, `r1 = 2q`, `t = 1/2`, `turns = 0` | 1.5 q | 1 q (`0.5 * q` is a tie and rounds to 0) | `2^-51 S` = 6.7e-16 q; point bound 6.8e-15 q |
| line `(q,0,0)` to `(2q,0,0)` at `t = 1/2` (**same defect in the Bezier/line bounds**) | 1.5 q | 1 q | `2^-49 G max abs(P)` = 3.6e-15 q |
| derivative z, same radii, `turns = 1000` (phase 500 turns) | -9424.78 q | -6283 q | about 1e-11 q |
| second derivative x, same radii, `turns = 1000` | -59,217,626 q | -39,478,418 q | about 1e-7 q |
| subnormal `turns = 7q`, `r0 = r1 = 1e100`, `startTurn = 1/4`, `t = 1`: derivative x | -2.17301e-222 | -2.17389e-222 (`fl(2 pi * 7q) = 44 q`, not 43.98 q) | 1.4e-236 (error 8.7e-226) |
| tolerance arithmetic | `2^-49 * q` | `0.0` in plain double arithmetic | the revision 2 helper also computed `S = q` instead of 1.5 q |

The first two rows are **impossible for every implementation**: no double lies within 0.5 q of 1.5 q. The
remaining rows show that the radius underflow is amplified by `w` and `w^2`, and that a subnormal `turns` makes `w`
itself inaccurate by up to 50%.

Overflow (for T4): with `turns = 2^600` and `k = 2^500`, `turns * k` is infinite, and the reference returns NaN
coordinates for `point(k)`. With `turns = 2^600`, `secondDerivative(0)` already overflows, because `w^2 r` does.

## 2. Floating-point model with gradual underflow

Revision 2 used the model `fl(x op y) = (x op y)(1 + d)` with `abs(d) <= u`. With subnormals (IEEE-754 gradual
underflow; Java has no flush-to-zero):
- **Multiplication and division:** `fl(x y) = x y (1 + d) + e`, with `abs(d) <= u`, `abs(e) <= eta / 2` and `d e = 0`.
  The term e appears only when the result is subnormal or underflows to zero.
- **Addition and subtraction:** `fl(x + y) = (x + y)(1 + d)`. A sum whose result is subnormal is exact (both operands
  are multiples of eta, and so is their sum). The relative model therefore stays valid.
- **`StrictMath.sin` and `StrictMath.cos`:** error below 1 ulp of the result, so at most `2u abs(v) + eta` (ulp is eta
  for a subnormal result).
- **`x % 1.0`, `floor`, scaling by 2 and by 4:** exact.

Every new error term is therefore an absolute multiple of eta, carried through later operations.

## 3. Assessment of each item

### 3.1 Radius interpolation

Lemma 3' (replaces Lemma 3 of the revision 2 report). For `r0 != r1`, with `r~ = fl(fl(fl(1 - t) r0) + fl(t r1))`,
`abs(r~ - r) <= 2^-51 S + 2 eta`.

Proof. As in revision 2, but now `A = (1 - t) r0 (1 + d1)(1 + d2) + e2` and `B = t r1 (1 + d3) + e3`, with
`abs(e2), abs(e3) <= eta / 2`; `fl(1 - t)` does not underflow, since `abs(1 - t)` is 0 or at least 2^-53. Then
`abs(r~ - r) <= (1 + u)((2u + u^2) abs(1 - t) r0 + u abs(t) r1 + abs(e2) + abs(e3)) + u S`, which is at most
`(3u + 3u^2 + u^3) S + (1 + u) eta`, and that is below `2^-51 S + 2 eta`. ∎

The exact cases are unaffected: at t = 0 and t = 1 the products are `1 * r0`, `0 * r1` or `0 * r0`, `1 * r1`, which
are exact; equal radii use the special case. In the A3 example the error is 0.5 q, within the 2 q allowed.

### 3.2 Phase and trigonometry

`fl(turns * t)` and the residual-angle product `(4 f - q) * (pi / 2)` can underflow, by at most `eta / 2` turn or
radian. The constant term of `dphi` (`2 pi * 2^-51`, about 2.8e-15 rad) covers this many times over, so `dphi` is
unchanged. Subnormal angles are handled by fdlibm (`sin x = x`, `cos x = 1` for tiny x) within the 1-ulp model above.

### 3.3 Position

`x~ = fl(r~ c~)`, and the same for z with s.

| Source | At most |
|---|---|
| radius | `(2^-51 S + 2 eta) abs(c~)` |
| trigonometry | `r (2u + eta + dphi)`; `r eta <= u S`, so this is relative |
| the final product | `u S + eta / 2` |
| `y = fl(rise t)` | `u abs(rise t) + eta / 2` |

The absolute total is at most 2.5 eta, so the bound becomes `2^-49 (S + abs(rise t)) + S dphi + 4 eta`. The A3
counterexample has error 0.5 q against a bound of about 4 q.

### 3.4 First derivative

`fl(fl(dr~ c~) - fl(fl(w~ r~) s~))`, with `w~ = fl(fl(2 pi) turns)`.

| Absolute source | At most |
|---|---|
| radius `2 eta` times `w` | `2 w eta` |
| underflow of `w~` (subnormal `turns`) times `r~` | `(eta / 2) S` |
| three products | `3 eta / 2` |

The terms `dr eta`, `w S eta` and similar are at most u times a relative term, and are absorbed. The bound becomes
`2^-48 W + W dphi + 4 (1 + w + S) eta`. The S part is necessary: in the subnormal-`turns` example the error is
8.7e-226, and the new bound 2.0e-223 (ratio 0.004), while the revision 2 bound is 1.4e-236. The `w` part is
necessary: in the `turns = 1000` example the error is 3,142 q, against a bound of 25,137 q.

### 3.5 Second derivative

`fl(-fl(k~ s~) - fl(w2r~ c~))`, with `k~ = fl(2 dr~ w~)` and `w2r~ = fl(fl(w~ w~) r~)`.

| Absolute source | At most |
|---|---|
| radius `2 eta` times `w^2` | `2 w^2 eta` |
| underflow of `w~` in `k~` | `dr eta` |
| underflow of `w~` in `w~^2`, times r | `w S eta` |
| underflow of `fl(w~ w~)`, times r | `S eta / 2` |
| products | `2 eta` |

The bound becomes `2^-47 W2 + W2 dphi + 4 (1 + dr + S + w S + w^2) eta`. In the `turns = 1000` example the error is
19,739,208 q, against a bound of 157.9 million q (ratio 0.125).

The relative budgets of revision 2 (section 2.3 there) are unchanged, except that the trigonometric error is now
counted as 2u instead of u (1 ulp of a value below 1/2 can be 2u relative). The totals are 8u, 15u and 15u, against
the coefficients 16u, 32u and 64u.

### 3.6 Lines and Beziers (same defect)

The de Casteljau lerp `fl(fl((1 - t) a) + fl(t b))` has an absolute error of at most `eta` per level, and each later
level multiplies existing errors by at most G. After n levels the absolute part is at most
`eta (1 + G + ... + G^(n-1)) <= n G^(n-1) eta`.

The derivative applies n - 1 levels to the differences, then multiplies by n. The second derivative of a cubic uses
one lerp of second differences times 6; a quadratic uses an exact combination.

The contract states generous absolute terms:
- point: `2 n G^n eta`;
- derivative: `2 n^2 G^n eta`;
- second derivative: `2 n^3 G^n eta`.

They cover Bernstein-sum evaluations as well, since a product chain that underflows is bounded by eta times the
same factors. The relative parts are unchanged. Easing is unaffected: its bound `2^-50` is already absolute, and
products of powers stay monotone under underflow.

### 3.7 The tolerances themselves

Computing `2^-49 S` or `S` in plain double arithmetic can round **down** (to 0 for subnormal S, and `0.5 q` to 0
inside S). A test would then be stricter than the contract.

Correction: every tolerance in `CurveTestSupport` is computed with upward-rounded arithmetic:
- for non-negative a and b, `nextUp(fl(a op b))` is at least the exact `a op b`, also for subnormal results;
- `pi` is replaced by `nextUp(Math.PI)`;
- `Math.pow` is replaced by repeated upward multiplication;
- a zero factor gives an exact 0.

`M3UnderflowTest.toleranceHelpersAreUpperBounds` checks, for 6,912 spiral input combinations and 108 Bezier
combinations including subnormal, zero and 1e100 inputs, two things:
- every helper is at least the contract bound evaluated exactly in `BigDecimal` with pi rounded up;
- it is at most `bound (1 + 2^-20) + 64 amp eta`, so it is not vacuous. Here amp is `(1 + w)^2` for spirals and
  `(1 + G)^3` for Beziers: subnormal intermediates are quantized to multiples of eta before the amplification.

Restricted domain alternative (not chosen): dropping the absolute terms would need every input and intermediate to
stay normal. That cannot be expressed simply in terms of the inputs (t itself may be tiny), and absolute terms are
valid everywhere.

## 4. Overflow and the exactness guarantees (T3 to T5)

The contract now says:
- **T4:** for every integer-valued k for which the **floating-point** product `turns * k` is finite (and
  `derivative(0)` is finite), the seam is exact. If the product overflows, nothing is promised about the value.
- **T3 to T5 and every `==` guarantee:** they hold only when the compared quantities are finite and no intermediate
  overflows. Otherwise IEEE-754 applies (infinity or NaN; NaN is not equal to itself), and the only promise is that
  no exception is thrown (section 8).
- **Domain** (7.2): coordinates and radii up to 1e100, `abs(turns) <= 1e50` and `abs(t) <= 1e3` keep every
  intermediate finite.

`M3UnderflowTest.seamsUpToOverflow` checks exact seams at the limit:
- `turns = 3`, `k = 2^1021`;
- `turns = 1`, `k = MAX_VALUE`;
- `turns = 5`, `k` just below `MAX_VALUE / 5`;
- `turns = 2^600`, `k = 2^423`.

It checks only "no exception" when `turns * k` overflows.

## 5. Tests (new class `M3UnderflowTest`, 7 tests; oracles exact `BigDecimal`, errors measured exactly)

| Test | Covers |
|---|---|
| `a3Counterexample` | the review counterexample. Proves the revision 2 radius, spiral-point and line bounds are unsatisfiable (no double within 0.5 q of 1.5 q). Checks the radius rule `2^-51 S + 2 eta` and the amended point and line bounds |
| `radiusUnderflowAmplifiedByW` | radius underflow times `w` and `w^2`: the review case with `turns = ±1000`, plus 3,000 random tiny radii with up to 20,000 turns |
| `subnormalRadii` | 6,000 cases: radii, `rise` and products from a few q to around `MIN_NORMAL`, extrapolated and tiny t, on exact quarter phases |
| `subnormalTurns` | 5,000 cases: subnormal and near-normal `turns` with radii up to 1e100, small-angle series oracle |
| `bezierSubnormalControlPoints` | 6,000 lines, quadratics and cubics with subnormal coordinates against the exact Bernstein form |
| `toleranceHelpersAreUpperBounds` | item 5 (section 3.7) |
| `seamsUpToOverflow` | T4 clarification (section 4) |

Changed, not added:
- `CurveTestSupport`: the bound helpers keep their signatures and gain the absolute terms and upward rounding;
  `phaseErr` is upward-rounded.
- `M3ReferenceVectorTest.bezier`: the difference scale is `nextUp(abs difference)` instead of `* (1 + 2^-52)`, so it
  is a guaranteed upper bound.
- Tolerances grow by the absolute terms and by a few ulps from upward rounding. Nothing else changes.

Unchanged: every other test, all reference data and the manifest, the generator, M1 and M2.

## 6. Evidence

- **The original bound is rejected.** `a3Counterexample` proves that the revision 2 bounds cannot be met by any
  implementation in the A3 case. Mutation T03 (the revision 2 point bound restored) and T06 (the revision 2 line
  bound restored) make the suite reject the reference. T04, T05 and T08 show that each new term and the upward S are
  needed (`validation/a3/mutation-results.txt`).
- **Throwaway reference** (the revision 2 reference, unchanged): **242 / 242 passed** (M1 125, M2 66, M3 51) on a fresh clean run, and `check` succeeded (`validation/a3/reference-run-summary.txt`).
- **Mutation:** **37 / 37 detected**: X01 to X29 (production), T01 and T02 (revision 2 test-side, retargeted), and T03 to T08 (A3: each absolute term, plain-double tolerances, plain-double S). T03 and T06 are caught by the exact `BigDecimal` checks; upward rounding in the subnormal range quantizes tolerances to multiples of eta, so comparison with the reference alone would not catch them.

## 7. Modifications relative to revision 2

| File | Modification |
|---|---|
| `docs/m3/M3-AMENDMENT-A3.md` | new: this report |
| `docs/m3/M3-IMPLEMENTATION-CONTRACT.md` | status; radius rule `+ 2 MIN_VALUE`; T4 wording (floating-point product finite); new "no overflow" clause for T3 to T5; 7.2 absolute terms for Bezier and spiral bounds, underflow model, tolerance computation rule, precision limits and the finiteness domain |
| `docs/m3/M3-DESIGN-DECISIONS.md` | DM3-12: amendment A3 note |
| `docs/m3/M3-CONVENTIONS.md`, `M3-RISKS.md`, `M3-TEST-SPECIFICATION.md`, `M3-ARCHITECTURE.md`, `README-FOR-CODEX-M3.md` | facts F13 to F15, risk R14, counts (51 M3 tests, 242 in all), A3 references |
| `docs/m3/validation/a3/` | new: probe source and output, reference run, mutation results |
| `src/test/java/workshop/vfx/math/curve/CurveTestSupport.java` | upward-rounded tolerance helpers with absolute terms |
| `src/test/java/workshop/vfx/math/curve/M3ReferenceVectorTest.java` | one line: difference scale via `nextUp` |
| `src/test/java/workshop/vfx/math/curve/M3UnderflowTest.java` | new, 7 tests |
