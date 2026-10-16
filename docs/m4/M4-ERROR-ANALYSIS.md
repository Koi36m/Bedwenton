# M4 error analysis: derivations and evidence

Status: PROPOSED, with the contract. Revision 2.1 rewrote sections 2 and 3 and updated section 4 after the R2 review
(`M4-R21-CORRECTIONS.md`). Revision 2 corrected sections 4, 6 and 7 after the R1 review (see
`M4-R2-CORRECTIONS.md`).

Notation:
- `u = eps = 2^-53` and `eta = Double.MIN_VALUE`;
- `fl(x op y) = (x op y)(1 + d) + e` with `abs(d) <= u` and `abs(e) <= eta/2`, where e is nonzero only for
  underflowing products (M3 amendment A3);
- `L` is the exact length `int_{t0}^{t1} abs(C'(t)) dt`; `L^` is the computed `length()`.

What is **proven** (sections 1 to 5) and what is **validated** on the reference families (section 6) are kept
separate. No quadrature rule bounds the error of an arbitrary black-box integrand.

## 1. Uniform parameters (exact definition)

- `t_k = t0 + fl(fl(t1 - t0) fl(k / den))`, with `t_0 = t0` and `t_(n-1) = t1` for `INCLUDE_END`.
- Error against the real `t0 + (t1 - t0) k / den`: at most `3u abs(t1 - t0) + u abs(t_k)`.
- **Monotone, and never above t1:**
  - `k -> fl(k / den)` is non-decreasing (correct rounding is monotone). Multiplication by the fixed non-negative
    `D = fl(t1 - t0)`, and addition of the fixed t0, preserve order.
  - For every non-forced k, `f = fl(k / den) <= 1 - 2^-20` (since `den <= 2^20`). Hence
    `fl(D f) <= (t1 - t0)(1 + u)^2 (1 - 2^-20) < t1 - t0`, so the exact sum `t0 + fl(D f)` is below t1.
  - Rounding to nearest is monotone and t1 is a double, so the rounded sum is at most t1. The forced last value t1
    is therefore at least every earlier value.
- On [0, 1], `t_k = fl(k / den)` exactly: `0 + 1.0 * x = x`.

## 2. Speed

- **Speed pair (R2.1):** `mu = max abs component` (exact), and `nu~ = sqrt((x/mu)^2 + (y/mu)^2 + (z/mu)^2)`.
  - quotients: relative error u each;
  - squares: 3u;
  - two additions of non-negative terms: 2u more;
  - square root: halves the relative error and adds u.

  Total: `nu~ = nu (1 + d_nu)` with `abs(d_nu) <= 3.5u (1 + O(u))`. A quotient that underflows contributes below
  `eta^2`, which is negligible. `nu` lies in `[1, sqrt(3)]`, because the largest component's ratio is exactly 1.
- **Speed for the Newton slope:** `v~ = fl(mu nu~)`, with relative error at most 4.5u, which the contract states as
  5 eps. It is formed only in 5.6 (it may be `+infinity` there). The panel rule never forms it.
- **No overflow or underflow of squares:** each `(x/mu)^2 <= 1`.
- **Propagation of the curve's own error:** the computed derivative differs from the true one by at most `delta_c`
  per component (M3 contract 7.2 with A3). By the triangle inequality,
  `abs(abs(C'~) - abs(C')) <= sqrt(3) delta_c`.
- **Total speed error:** `dv = sqrt(3) delta_c + 5u v`. A subnormal derivative component's own absolute error is part
  of `delta_c` (M3 A3). R2's extra `eta` was for rounding a subnormal speed product; the R2.1 rule never forms that
  product, since `mu` is exact and the ratios are relative.

## 3. Gauss-Legendre panels: rounding (R2.1 evaluation order, contract 5.2)

Write `v_i = mu_i nu~_i` (exact products) for the computed speeds, and `G* = d (H0 v_0 + H1 (v_1 + v_2) + H2 (v_3 +
v_4))` for the exact rule on them, with `d = b - a` exact.

- **Constants:** each X and W is correctly rounded (relative error u). `H_i = W_i / 2` exactly.
  `H0 + 2 H1 + 2 H2 = 1` holds to 2u (generator self-check at 60 digits).
- **Width:** `fl(b - a) = d (1 + d_0)` with `abs(d_0) <= u`. It is exact when a and b are within a factor 2
  (Sterbenz) or both subnormal, and never 0 for `a < b` (gradual underflow).
- **Ratios:** `r_i = fl(fl(mu_i / M) nu~_i) = (v_i / M)(1 + 2u') + e_i`. Here `e_i` is nonzero only when
  `mu_i / M < 2^-1021`, with `abs(e_i) <= 1.5 eta`.
- **Weighted sum:** `S~ = (sum H_i r_i)(1 + t_S)` with `abs(t_S) <= 4u`. The terms are non-negative, three
  products, two inner sums and two outer sums; products by `H >= 0.118` of `r >= 2^-1019` do not underflow. The
  index of M has `r = nu~ >= 1`, so `S >= H2 > 0.118`. The absolute terms `e_i` and the possible underflows
  (`<= 3.5 eta` in total) are therefore relative errors below `30 eta`, which is negligible against u.
- **Products (one of three orders):**
  - `fl(fl(d S~) M)` when `fl(d S~) >= 2^-1022`;
  - `fl(d fl(M S~))` when `M S~ <= MAX_VALUE`;
  - `fl(fl(M d) S~)` otherwise.

  Each multiplication adds a relative error u, except an underflow of the final product, which adds an absolute
  error of at most `eta / 2`.
  - In the second order, `fl(M S~)` can underflow (M tiny). But then the exact `G* = d M S < 2^-1018 2^-1022` lies
    far below `eta / 2`, the computed result is 0, and the error is `G* < eta / 2`.
  - In the third order, `S > 1` and `d < 2^-1022`, so `M d` lies in `[4.4e-16, 4)`: normal, with no underflow.
- **Total:** `G~ = G* (1 + theta) + delta`, with `abs(theta) <= u + 2u + 4u + 2u + 30 eta <= 10u` and
  `abs(delta) <= eta / 2`. The delta is nonzero only when `G~` is subnormal or 0.
- **Overflow:** the first two orders overflow only if `G* (1 + theta) > MAX_VALUE`. The third cannot overflow in
  `M d`. `P = fl(d S~)` cannot overflow, since `d <= MAX_VALUE / 16` and `S~ <= 2`. So `G~ = +infinity` only if
  `G* > MAX_VALUE / (1 + 10u)`.
- **NaN:** impossible. Every operand is finite and non-negative, and the only possible infinities are products of
  positive finite numbers. No `0 * infinity` arises: M and S are positive, and `d = 0` occurs only in the second
  order, where `Q` is finite (or in the third order, `(M 0) S = 0`).
- **Speed errors:** they enter linearly, `abs(G* - G(v)) <= (b - a) dv`.
- **Node positions:** `c +- h X` is computed with error at most `3u T + 2 eta` (`T = max(abs(t0), abs(t1))`; `2 eta`
  for an underflowing h and `h X`). The speed is sampled off-node by that much, which costs at most
  `(b - a)(3u T + 2 eta) max abs(v')`. Since `abs(v') <= abs(C'')`, the contract states
  `(4u T + 2 eta) max abs(C'')`.
- **R2 (for the record, findings F4 and F5).** R2 computed `h (W0 v_0 + ...)`.
  - **F4:** `h = 0.5 d` rounds to 0 for `d = eta`, and loses relative precision for `d < 2^-1021`. So a panel's value
    could be 0, or wrong by 100%, at any speed: an M3 line on `[0, eta]` at speed 1e300 had length 0 instead of
    4.9e-24 (`validation/r21/findings-r2.txt`).
  - **F5:** the sum `W1 (v_1 + v_2)` overflows for speeds above about `MAX_VALUE / 2`, even when `h * sum` is finite.
    With `h = 0` it gave NaN. Examples:
    - `ArithmeticException` for a speed-1e308 line on `[0, 1e-300]`, whose length is 1e8;
    - NaN from `lengthAt(MIN_VALUE)` for a finite table whose speed is 1e308 near `t = 0`.

## 4. Length summation

- `L^` is the left-to-right sum of K non-negative panel values. Each rounding is relative to the partial sum, so the
  summation error is at most `(K - 1) u L^`.
- With the panel errors of section 3, the **rounding part** of the length error is
  `abs(L^ - Q) <= (t1 - t0)(dv + (4u T + 2 eta) max abs(C'')) + (K + 64) u L + R_w eta / 2`, where Q is the exact
  sum of the exact panel rules. This is contract 10.2.
- **Underflow (R2.1).** The length is the sum of the final panels' `I2 = L + R`. Each rule has an absolute error of
  at most `eta / 2` beyond its relative error (section 3). A zero-width rule is exactly 0. Sums of subnormals are
  exact. Hence the term `R_w eta / 2`, with `R_w <= 2K` the number of nonzero-width half-panel rules among the final
  panels. The `I1` values do not enter the length.
  - R2 stated `8 K eta`, from the old order's four underflowing products per rule. That bound was not the problem in
    F4: the old order lost the whole value (`h = 0`), at any speed.
- **Constant speed (lines, circles, arcs, helices):**
  - The 5-point rule integrates a constant exactly: `G = d v (H0 + 2 H1 + 2 H2) = d v`.
  - Therefore `Q = L` and `I2 = I1` up to rounding.
  - **Error estimate (B5 proof).** For one panel of exact value `I`, `I1` and `I2` are computed from the same
    constant. Their relative errors are at most 10u (one rule) and 11u (two rules and a sum).
    - Their absolute underflow errors are at most `eta / 2` and `eta`. The speed pair itself does not underflow:
      `mu` is exact.
    - So `e_k = abs(I2 - I1) <= 21u I + 1.5 eta`, plus `2 (b_k - a_k) dsv` when the computed speed varies by up to
      `dsv` between nodes.
    - Over the 16 initial panels, `sum e <= 24u L + 24 eta + 2 (t1 - t0) dsv`. Here 24u bounds 21u with the rounding
      of `L0` against L. R2's bound under the old order was `144 eta`.
  - **Comparison with tau.** `tau = max(abs, rel L0, 1024 eta) >= max(2^-40 L0, 1024 eta)`, and
    `24u L <= 2^-48.4 L`. When `dsv = 0` (lines):
    - if `2^-40 L0 >= 1024 eta`: `sum e <= (2^-8.4 + 24/1024) tau < tau`;
    - otherwise: `24u L + 24 eta <= (2^-8.4 + 24/1024) 1024 eta < tau`.

    So the 16 initial panels satisfy `E <= tau`. No refinement happens: `converged()` holds and
    `panelCount() == 16`, at every magnitude. This is the unconditional part of B5.
  - With `dsv > 0` (circles and helices: the M3 trigonometry rounds differently at each node), the same holds
    whenever `2 (t1 - t0) dsv + 24u L + 24 eta <= tau`. This is the conditional part of B5. R2 required `144 eta`
    in place of `24 eta`, so the condition is now weaker and the guarantee broader.
  - **R1 gap (finding 1).** Without the floor, `tau = rel * L0` underflows to 0 for subnormal lengths (`2^-40 * 7 eta
    = 0`). Then convergence needs `sum e == 0` exactly, which rounding decides. R1 needed 32 panels for some
    subnormal circles. R1 also computed `L^ = 0` for a `7 eta` line (`h * sum` underflows), which is outside R1's
    allowance `(t1 - t0) dv + 80u L`. R1's B5 argument (`tau >= 2^-40 L` exceeds the panel rounding) ignored both
    the absolute underflow terms and `tau = 0`. R2 and R2.1 still compute `L^ = 0` for that line on [0, 1]:
    each of the 32 half-panel rules has the exact value `7 eta / 32`, which rounds to 0. This is within the allowance
    (`7 eta <= R_w eta / 2 = 16 eta`), and the tests check it. On `[0, eta]` the same line has `R_w = 1`, and R2.1
    computes `7 eta` exactly (F4).
  - **Length error:** `abs(L^ - L) <= (t1 - t0) dv + 80u L + R_w eta / 2`, with `K = 16` and `R_w <= 32`
    (contract 10.2).
  - Measured: the lines and circles of `ArcLengthTableTest`, within 64 eps plus the M3 derivative error. The
    subnormal lines, circles and helices of `M4R2RegressionTest.subnormalConstantSpeed` (lengths from 1 eta to
    `2^40 eta`, and radii from 3 eta to 1e-310) are within the allowance, converged with 16 panels and
    `tau = 1024 eta`.
- **Limit of tight tolerances:** K can reach `16 + (2^24 - 240)/20`, about 840,000, so `K u` can exceed `2^-40`. The
  smallest meaningful relative tolerance is about `(K + 64) u`. The contract keeps `MIN_RELATIVE = 2^-40` and
  documents the summation limit, rather than imposing compensated summation.

## 5. Quadrature error and the meaning of `converged()`

- For a speed with 10 continuous derivatives on [a, b] (width w), the 5-point rule's error is
  `E(w) = w^11 (5!)^4 / (11 (10!)^3) v^(10)(xi)`, about `w^11 v^(10) / 1.25e12`.
- The refined `I2` (two half-panels) has error about `E(w) / 1024`. So
  `e = abs(I2 - I1) = abs(E_I1 - E_I2)`, about `1023/1024` of `abs(E_I1)`, roughly **1000 times** the error of the
  accepted `I2`.
- The estimate is therefore conservative for smooth speeds, and `converged()` (`sum e <= tau`) implies an achieved
  error far below tau.
- **Near a kink** (cusp: `abs(C')` has a corner) or a stationary point, the rule is only `O(w^2)` on the panel
  containing it. I1 and I2 then differ by about 4x their errors, so the estimate is conservative by only about 4/3.
  Global refinement isolates the kink in a small panel.
- **What is guaranteed:**
  - B3: `converged()` exactly when `errorEstimate() <= tau`;
  - B1: never more than the budget;
  - termination: at most `(maxEvaluations - 240)/20` splits, each panel split at most 50 times, and every loop has a
    fixed bound.
- **What is not guaranteed:** that the true error is at most tau for an arbitrary curve. An integrand oscillating
  exactly at the panel scale can fool any finite rule: aliasing. The reference families are smooth or have isolated
  kinks.

## 6. Validation on the reference families (`validation/evidence.txt`)

| Measurement | Result |
|---|---|
| 336 reference lengths (lines, circles, helices, planar and conical spirals, quadratic and cubic Beziers, 4 cusps off-node, stationary points, collapsed curves, extrapolated ranges, magnitudes 1e-6 to 1e100) at relative tolerances 1e-4, 1e-7, 2^-30, 1e-12 | **all converged; no row exceeds tau; worst achieved error / tau = 0.31** (the collinear-reversal quadratic, a kink at t = 2/3) |
| evaluations per table | mean 240 to 259, max 540 (at 1e-12) |
| the same cost spent on chords (one chord point per GL5 evaluation), rel 1e-7 | worst relative error **6.5e-2** for chords against **2.0e-8** for the table |
| inversion: 20,979 `parameterAt` calls on the reference curves at 2^-30 | R2: mean **13.8** derivative evaluations, max **29** (contract limit 336). R1: mean 14.0, max 29 |
| oscillating user curve (200 periods), rel 1e-9 | budget 256: not converged (estimate 0.94, honest); 65,536: estimate 7e-8; 2^20: converged with 81,120 evaluations |

## 7. Inversion error and termination (R2)

**Exits.** `parameterAt(s)` returns t in the bracket of panel k (contract 5.6):
- after a tolerance or `f == 0` exit, `abs(F_k(t) - r) <= tau 2^-10`;
- after a cap or collapse exit, t is the end with the smaller residual of a final bracket, of width W, across which
  `F_k - r` changes sign.

**Distance error** (tolerance exits):
`abs(L(t0, t) - s) <= abs(S_k - L(t0, a_k)) + abs(F_k(t) - L(a_k, t)) + tau 2^-10`.
- The first term is the table error up to panel k.
- The second is the 5-point rule on `[a_k, t]` inside an accepted panel, scaled by `rho_k` in ratio mode. It is
  within `e_k` plus the rule's error on a sub-panel. In linear mode, which in a converged table occurs only when
  `I2_k <= 2 tau` (contract 5.5), it is at most `I2_k`.
- Both are bounded empirically by about tau for the reference families. The test bound is `2 tau + allowance` for
  distances, and `(3 tau + allowance) / v_min` for parameters. Both held on all 216 inverse rows and all
  equal-distance sets (`EqualDistanceTest`, `M4ReferenceVectorTest.inversions`).

**Bracket progress (proof of contract P3).**
- Write `W_i` for the bracket width after iteration i (step 7.3), and `W_(-1) = b_k - a_k`.
- Every iterate lies strictly inside the current bracket (steps 6 to 8), so `W_i < W_(i-1)`: the bracket strictly
  shrinks.
- For `i >= 16`, no Newton candidate is formed. The iterate evaluated at iteration `i + 1` is then the computed
  midpoint `m = fl(lo + fl(0.5 fl(hi - lo)))` of the bracket after iteration i. This covers iterations 17 to 63:
  47 midpoints.
- **One midpoint step.** Let `W = hi - lo` (exact), `M = M_k`, and `u = 2^-53`.
  - `fl(hi - lo) = W (1 + d1)` with `abs(d1) <= u`; halving is exact.
  - The sum rounds by at most `u abs(m) <= u M`.
  - So `abs(m - (lo + W/2)) <= u W/2 + u M`, and the new width, `hi - m` or `m - lo`, is at most
    `W (1 + u)/2 + u M`.
- **Iterating 47 times** from `W_16 <= b_k - a_k`:
  `W_63 <= 2^-47 (1 + u)^47 (b_k - a_k) + u M sum_j ((1 + u)/2)^j
  <= 2^-47 (1 + 2^-46)(b_k - a_k) + 2 ulp(M)`, using `2 u M (1 + 2u) <= 2 ulp(M)`.
- **Collapse.** The computed midpoint fails to be strictly inside only if `W <= 2 ulp(M)` (otherwise
  `0.5 fl(W) > ulp(M) >= ` the rounding of the sum).
- Hence the bound of P3 holds at every non-early exit.

**What R1 claimed and why it was wrong (finding 3).** R1 said that each iteration "either takes a Newton step
strictly inside the bracket or bisects, so 64 iterations shrink any finite bracket by `2^-64`". A Newton step only
has to land strictly inside. When the speed seen by Newton is much larger than the slope of `F_k` (a spike of the
speed near the root, invisible to the quadrature nodes), every step moves t by a tiny amount, and the bracket
shrinks by far less than half. The probe `validation/r2/findings-r1.txt` shows R1 exhausting all 64 iterations
(384 evaluations), with a residual of 2.7e-8, against a tolerance of 1e-15. R1 also did not specify the result
after the 64th iteration.

**Why the Newton phase is limited, not guarded by a halving test.** A first R2 draft allowed a Newton step only if
the previous step had at least halved the bracket. This also proves geometric progress (`W_(i+2) <= W_i / 2`), but
it disables Newton after every one-sided Newton step. One-sided convergence is the normal case for a convex `F_k`.
On the conical spiral of `M4AdversarialTest.newtonEfficiency`, that draft cost a mean of 56.7 evaluations per
sample, and the validated criterion (a mean of at most 40) failed. With the 16-iteration Newton phase, the mean is
17.7 for the same setup. No smooth-curve measurement needed more than 7 iterations. The phase also gives the
stronger bound `2^-47`. Both measurements
are in `validation/r2/inversion-stats.txt`.

**Measured** (`validation/r2/inversion-stats.txt`), against the R2 reference implementation:
- smooth curves at relative 1e-6 to 1e-12: at most 7 iterations and 41 evaluations; no cap or collapse exit;
- Newton creep (a 1e6 speed spike within 1e-7 of the root): tolerance exits after at most 54 iterations
  (286 evaluations), with t within 7e-16 of the exact inverse;
- a speed that is discontinuous at the scale of the nodes (bit 11 of t): 830 collapse exits, 1 cap exit, at most
  336 evaluations.

## 8. Spacing counts (exact)

- `q = fl(L / d)`; `m = floor(q)`, plus 1 if `q - m >= 1/2`.
- `q - floor(q)` is exact (for `q >= 1` by Sterbenz's lemma; for `q < 1` trivially), so the half-up decision is
  exact for the computed q.
- Java specifies `Math.round(q)` as the closest long with ties toward positive infinity, which is the same rule for
  `0 <= q < 2^62`. Even at `q = nextDown(0.5)` it returns 0 (Java 7 and later), so mutant M20 is equivalent.
  - The naive `floor(q + 0.5)` would differ there: `q + 0.5` rounds to 1.
  - `counts.csv` contains this case, so an implementation using `floor(q + 0.5)` is rejected.
- Saturation at `2^62` keeps `(long)` conversion exact and avoids overflow.
