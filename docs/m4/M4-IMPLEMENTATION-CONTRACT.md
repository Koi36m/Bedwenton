# M4 Implementation Contract: `workshop.vfx.math.sample` (particle sampling and distribution)

Audience: the engineer implementing the production source (Codex).

Status: **PROPOSED, revision 2.1. Not authorized for implementation.** The owner approves this contract with DM4-1 to
DM4-17 (`M4-DESIGN-DECISIONS.md`) after ChatGPT's independent review. Until then nothing in
`src/main/java/workshop/vfx/math/sample` may be written.

Revision 2 corrects the three blocking findings of the R1 review (tolerance floor, 5.3 and B5; finite partial
integrals, 5.5; inversion progress, exits and work, 5.6 and 8). Every change is listed in `M4-R2-CORRECTIONS.md`.

Revision 2.1 corrects the two findings of the R2 review: the half-width underflow (F4) and the weighted-sum overflow
(F5). It does so by a new normative evaluation order for the panel rule (5.1 speed pairs, 5.2), with the guarantees of
5.5 and 10.2 restated to match. See `M4-R21-CORRECTIONS.md`.

Baseline: the verified M3 baseline `a6726c42a20940a6a6d2dd24477e6a642f6b8cbc` (M3 production
`d76258f343e28a8acb43f3ad8ba776adcabdd575`, 254/254 tests). **Nothing in M1, M2 or M3 changes**: no production class,
test, reference data, contract or verification record. M4 adds a package, its tests and its reference data.

The tests in `src/test/java/workshop/vfx/math/sample` are the executable form of this contract. Where the two disagree,
stop and report; do not change either silently.

Requirement words:
- **Normative** requirements must hold bit for bit or exactly as stated.
- **Validated** accuracy statements are acceptance criteria measured on the reference families. They are not proofs
  for arbitrary curves (section 10.3).

## 1. Architecture

```
M3 curve (unchanged)                 M4 sample (this contract)                                  later (Paper adapter, emission)
Curve, PosedCurve, Spiral, Bezier -> Sampler.uniformParameter            -> SampleSet ------> render particles
                                    ArcLengthTable.build -> Sampler.uniformDistance / bySpacing  (positions, parameters)
M1 core: Vec3, Pose (placement of sample sets: SampleSet.transformed(pose))
```

- Package `workshop.vfx.math.sample`. It may use `workshop.vfx.math.core` and `workshop.vfx.math.curve`. It must not
  use `workshop.vfx.math.time`, Minecraft, Paper, Bukkit, particles, rendering, networking or clocks.
- Java 25, no dependencies.
- Results are pure functions of the arguments, and all public types are immutable.

## 2. Public API (complete)

```java
public enum Endpoints { INCLUDE_END, EXCLUDE_END }

public record SampleLimits(int maxSamples, int maxEvaluations) {
    public static final int MAX_SAMPLES_CEILING = 1 << 20;      // 1,048,576
    public static final int MIN_EVALUATIONS = 256;
    public static final int MAX_EVALUATIONS_CEILING = 1 << 24;  // 16,777,216
}

public record ArcLengthTolerance(double absolute, double relative) {
    public static final double MIN_RELATIVE = 0x1p-40;
    public static final double MAX_RELATIVE = 0x1p-3;
}

public final class ArcLengthTable {
    public static ArcLengthTable build(Curve curve, ArcLengthTolerance tolerance, SampleLimits limits);   // t in [0, 1]
    public static ArcLengthTable build(Curve curve, double t0, double t1, ArcLengthTolerance tolerance, SampleLimits limits);
    public Curve curve();
    public double startParameter();
    public double endParameter();
    public double length();
    public double effectiveTolerance();
    public double errorEstimate();
    public boolean converged();
    public int panelCount();
    public int evaluations();
    public double lengthAt(double t);
    public double parameterAt(double distance);
}

public final class SampleSet {
    public int size();
    public double parameter(int index);
    public Vec3 point(int index);
    public double[] parameters();          // a new copy on every call
    public List<Vec3> points();            // unmodifiable
    public SampleSet transformed(Pose pose);
}

public final class Sampler {
    public static SampleSet uniformParameter(Curve curve, int count, Endpoints endpoints, SampleLimits limits);        // [0, 1]
    public static SampleSet uniformParameter(Curve curve, double t0, double t1, int count, Endpoints endpoints, SampleLimits limits);
    public static SampleSet uniformDistance(ArcLengthTable table, int count, Endpoints endpoints, SampleLimits limits);
    public static SampleSet bySpacing(ArcLengthTable table, double spacing, Endpoints endpoints, SampleLimits limits);
    public static long countForSpacing(double length, double spacing, Endpoints endpoints);
}
```

Shape rules:
- There are no other public types or public members, apart from what records and enums generate. `toString` may be
  overridden.
- `ArcLengthTable`, `SampleSet` and `Sampler` are final, with no public or protected constructors.
- One public type per file. Package-private helpers are allowed.
- `SampleSet` and `ArcLengthTable` need not override `equals` or `hashCode`.

## 3. Common rules

- **Null:** any null argument gives `IllegalArgumentException`.
- **Parameter ranges:** `t0` and `t1` must be finite, `t0 <= t1`, and `t1 - t0` finite. Otherwise
  `IllegalArgumentException`.
  - `t0 == t1` is valid (zero length, all samples at `t0`).
  - Ranges outside [0, 1] are valid; they extrapolate the curve (M3 DM3-2).
  - The overloads without a range use `t0 = 0`, `t1 = 1`.
- **Counts:** a count must lie in `[0, limits.maxSamples()]`, else `IllegalArgumentException`. The check happens
  **before** any curve evaluation or allocation proportional to the count.
- **Endpoints:**
  - `INCLUDE_END` (open paths) places samples at fractions `k / (n - 1)`, so the first sample is at the start and
    the last at the end.
  - `EXCLUDE_END` (closed loops) places them at `k / n`: the start is included and the end is not, so a closed curve
    gets no duplicate seam sample.
  - The policy is explicit and never inferred from the curve (`Spiral.closed()` is not consulted).
- **Count 0** gives an empty `SampleSet`. **Count 1** gives one sample at `t0` for both policies.
- **Curve evaluation:**
  - Sampling calls `curve.point(t)` exactly once per sample, in increasing index order. The resulting `Vec3` is
    stored as returned.
  - `ArcLengthTable.build`, `lengthAt` and `parameterAt` call only `curve.derivative(t)`; never `point` or
    `secondDerivative`.
  - No result is cached across calls.

## 4. Uniform parameter sampling (normative, bit for bit)

`uniformParameter(curve, t0, t1, n, endpoints, limits)`:
- `den = n - 1` for `INCLUDE_END`, `den = n` for `EXCLUDE_END`.
- `t_0 = t0`: the argument itself, so the sign of a zero is kept.
- For `INCLUDE_END` and `n >= 2`, `t_(n-1) = t1`.
- Every other `t_k = t0 + (t1 - t0) * ((double) k / den)`, evaluated exactly in this order with Java double
  arithmetic.
- Points are `p_k = curve.point(t_k)`. `derivative` and `secondDerivative` are never called.

Consequences, checked by the tests:
- On [0, 1] the parameters are the correctly rounded `k/(n-1)` or `k/n`.
- The parameters are non-decreasing.
- The points equal `curve.point(t_k)` bit for bit.

For constant-speed curves (lines, circles, arcs, helices) uniform parameters are already equally spaced in distance,
up to rounding (DM4-3); they need no `ArcLengthTable`.

## 5. `ArcLengthTable` (normative structure)

### 5.1 Inputs and failures

`build(curve, t0, t1, tolerance, limits)` validates its arguments (section 3), then measures the length of `curve` on
`[t0, t1]`.

**Speed pair (R2.1).** For a derivative `(x, y, z) = curve.derivative(t)`:
- If any component is NaN or infinite, `ArithmeticException`.
- `mu = max(abs(x), abs(y), abs(z))`. If `mu == 0`, then `nu = 0`. Otherwise
  `nu = Math.sqrt((x/mu)^2 + (y/mu)^2 + (z/mu)^2)`, with the quotients and squares formed first and the squares
  summed in the order x, y, z.
- The speed is `v(t) = mu * nu`, with `1 <= nu <= sqrt(3)` up to rounding when `mu > 0`.
  - The panel rule (5.2) uses the pair and **never forms the product**, so speeds above `MAX_VALUE` (components above
    `MAX_VALUE / sqrt(3)`) are handled exactly like others.
  - The product `mu * nu` is formed only for the Newton slope of 5.6. It may round to `+infinity` there, which the
    safeguard handles: the candidate `t - f / infinity = t` is not strictly inside the bracket, so the midpoint is
    used.
  - The relative error of `nu` is at most 3.5 eps, and that of `mu * nu` at most 4.5 eps. `mu` is exact.

**Failures:**
- If the length is not finite, `ArithmeticException`.
- Exceptions thrown by `curve` propagate unchanged.
- Non-convergence is **not** an exception; it is reported by `converged()`.

### 5.2 Panel rule (5-point Gauss-Legendre)

For `a <= b`, `G(a, b)` is computed in exactly this order (R2.1):
1. `d = b - a`, `h = 0.5 * d`, `c = a + h`.
2. Five derivative evaluations, in the order `c`, `c - h*X1`, `c + h*X1`, `c - h*X2`, `c + h*X2`, giving the speed
   pairs `(mu_0, nu_0)` to `(mu_4, nu_4)` of 5.1. The node positions use h; **the value of G does not**.
3. `M = max(mu_0, ..., mu_4)`. If `M == 0`, `G = 0`.
4. `r_i = (mu_i / M) * nu_i` for i = 0 to 4.
5. `S = H0 * r_0 + H1 * (r_1 + r_2) + H2 * (r_3 + r_4)`, with the half weights `H_i = W_i / 2` (exact: the literals
   below with the exponent reduced by one).
6. `P = d * S`.
   - If `P >= Double.MIN_NORMAL` (`2^-1022`), then `G = P * M`.
   - Otherwise, with `Q = M * S`: `G = d * Q` if `Q <= MAX_VALUE`, else `G = (M * d) * S`.

Why this order (F4, F5):
- **F4.** R2 computed `G = h * (W0 v_0 + ...)`. For a width `d = MIN_VALUE`, `h = 0.5 * d` rounds to 0. Below
  `2^-1021`, h is subnormal and loses relative precision. On `[0, k MIN_VALUE]` every nonzero-width panel has width
  `MIN_VALUE`, so an M3 line there had length 0, even at speed 1e300 (true length 4.9e-24). R2.1 multiplies by the
  width d itself, which is never 0 for `a < b`, and halves the weights instead, which is exact.
- **F5.** R2's weighted sum overflowed for speeds near `MAX_VALUE`, even when `G` is finite (speed 1e308 on a width
  of 1e-300 gives 1e8). With `h == 0` the overflow became `0 * infinity = NaN`. R2.1 scales every speed by the largest
  component M, so `r_i` lies in `[0, sqrt(3)]` and `S` in `[H2, sqrt(3)]` up to rounding.
- **Choice of product order.**
  - `P * M` is used when `d * S` is a normal number. It can overflow only if G does (up to rounding).
  - Otherwise d is tiny: `d < 2^-1022 / S <= 2^-1022 / H2 < 2^-1018`.
    - If `Q = M * S` is finite, `d * Q` cannot overflow. When Q itself underflows, the exact G is far below
      `MIN_VALUE / 2`, so the result 0 is within the error bound below.
    - If `M * S` overflows, then `S > 1`, so `d < 2^-1022`. Also `M > MAX_VALUE / 2`. So `M * d` lies in
      `[4.4e-16, 4)`, a normal number, and `(M * d) * S` is accurate.
  - `P` itself never overflows, because every width passed to G is at most `(t1 - t0) / 16 <= MAX_VALUE / 16` and
    `S <= 2`.

**Guarantees of G (normative).** For `a <= b` finite with `b - a <= MAX_VALUE / 16` and finite derivatives:
- `G` is never NaN, and `G >= 0`. No `0 * infinity` can arise: in every product, `d > 0` when `a < b`, `S >= H2 > 0`
  and `M > 0`. With `d == 0`, every branch gives 0.
- **No spurious overflow or underflow:** `G = +infinity` only if the exact rule value (on the computed pairs) exceeds
  `MAX_VALUE / (1 + 10 eps)`.
- **Error:** `G = G*(1 + theta) + delta`, where `G*` is the exact rule applied to the exact speeds `mu_i * nu_i`,
  `abs(theta) <= 10 eps`, and `abs(delta) <= MIN_VALUE / 2` (only when G is subnormal). Proof:
  `M4-ERROR-ANALYSIS.md` 3. In particular, a nonzero exact value of at least `MIN_VALUE` never becomes 0.

The constants are the correctly rounded values of the closed forms, computed at 60 digits in the generator's self
check:

| Name | Closed form | Value |
|---|---|---|
| X1 | `(1/3) sqrt(5 - 2 sqrt(10/7))` | `0x1.13b23fd99b705p-1` |
| X2 | `(1/3) sqrt(5 + 2 sqrt(10/7))` | `0x1.cff6ce0533a69p-1` |
| W0 | `128/225` | `0x1.23456789abcdfp-1` |
| W1 | `(322 + 13 sqrt(70))/900` | `0x1.ea1da25ae415bp-2` |
| W2 | `(322 - 13 sqrt(70))/900` | `0x1.e539ec36e038cp-3` |
| H0, H1, H2 | `W0/2`, `W1/2`, `W2/2` | `0x1.23456789abcdfp-2`, `0x1.ea1da25ae415bp-3`, `0x1.e539ec36e038cp-4` |

The rule is exact for polynomial speeds up to degree 9, and for constant speeds up to rounding.

A **panel record** holds:
- a, b and its depth;
- `I1 = G(a, b)`;
- with `m = a + 0.5 * (b - a)`: `L = G(a, m)`, `R = G(m, b)`, `I2 = L + R` and the error estimate `e = abs(I2 - I1)`.

Creating a panel record from a known `I1` costs 10 evaluations.

### 5.3 Algorithm (normative, deterministic)

1. **Initial partition.**
   - Nodes `n_k = min(t1, t0 + (t1 - t0) * (k / 16.0))` for k = 0 to 15, and `n_16 = t1`.
   - Compute `I1` of the 16 panels (80 evaluations), then their records (160 evaluations), all left to right.
   - `L0 = sum of the 16 I2`, left to right.
   - If `L0` is not finite, `ArithmeticException`.
2. **Effective tolerance.** `tau = max(max(tolerance.absolute(), tolerance.relative() * L0), TAU_FLOOR)`, with
   `TAU_FLOOR = 1024 * Double.MIN_VALUE` (exact: `0x0.0000000000400p-1022`, about 5.06e-321).
   - The floor exists because `relative * L0` underflows to 0 for subnormal lengths (for example
     `2^-40 * 7 MIN_VALUE`). With `tau = 0`, the rounding of the panel sums (up to about 9 `MIN_VALUE` per panel)
     would make `converged()` depend on the last bits, and refinement would spend the budget on underflow noise.
   - The floor changes nothing for normal lengths: if `L0 >= 2^-1022`, then `relative * L0 >= 2^-1062`, which is more
     than `TAU_FLOOR = 2^-1064`. It relaxes the requested accuracy only for lengths below about `2^-1024` blocks, and
     then only to an absolute 5.06e-321 blocks.
   - The accuracy of constant-speed lengths does not depend on tau. B5 and the 10.2 allowance bound it directly.
3. **Running estimate.** `E = sum of the 16 e`, left to right.
4. **Global refinement.** While `E > tau`, the active set is not empty, and `evaluations + 20 <= limits.maxEvaluations()`:
   1. Remove from the active set the panel P with the largest `e`. Ties go to the smaller `a`, then the smaller `b`
      (`Double.compare` order).
   2. `E = E - e_P`.
   3. If `depth_P == 50`, or the midpoint m of P is not strictly between a and b, P becomes final.
   4. Otherwise create the records of the children `[a, m]` (with `I1 = L_P`) and `[m, b]` (with `I1 = R_P`),
      left child first, each at `depth_P + 1`. Add both to the active set, with `E = (E + e_left) + e_right`.
5. **Finish.** All remaining active panels become final. Sort the final panels by `a`, then by `b`. Then:
   - `length = sum of I2` over the sorted panels, left to right (`length` is `S_K`);
   - cumulative distances `S_0 = 0`, `S_(k+1) = S_k + I2_k`;
   - `errorEstimate = sum of e` in the same order;
   - `converged = (errorEstimate <= tau)`;
   - `evaluations` = the exact number of derivative calls made by `build` (at least 240);
   - `panelCount` = the number of final panels.

### 5.4 Guarantees of `build`

| # | Guarantee | Status |
|---|---|---|
| B1 | `curve.derivative` is called at most `limits.maxEvaluations()` times; `evaluations()` equals the count; `point` and `secondDerivative` are never called | normative |
| B2 | panels: `16 <= panelCount() <= 16 + (maxEvaluations - 240) / 20`. Memory is O(maxEvaluations) | normative |
| B3 | `converged()` holds exactly when `errorEstimate() <= effectiveTolerance()` | normative |
| B4 | `length() >= 0` and finite; `length() == 0` when every speed evaluated is 0 | normative |
| B5 | constant speed: `converged()`, `panelCount() == 16`, and the error is rounding only (10.2). **Unconditional** when the computed speed is the same at every node (lines: a constant derivative), at every magnitude, including subnormal speeds. For curves whose computed speed varies only by rounding (circles, arcs, helices), it holds whenever `2 (t1 - t0) dsv + 24 eps L + 24 MIN_VALUE <= tau` (R2: `144 MIN_VALUE`), with `dsv` the largest difference between two computed speeds on the range (`M4-ERROR-ANALYSIS.md` 4). The tests check it for circles and helices with radii up to 1e3, up to 64 turns and ranges within [-2, 3] (`ArcLengthTableTest.constantSpeedSpirals`), and with radii from `3 MIN_VALUE` to 1e-310 (`M4R2RegressionTest.subnormalConstantSpeed`) | normative (proof in `M4-ERROR-ANALYSIS.md` 4) |
| B6 | for curves whose speed is smooth on `[t0, t1]` (the reference families): `abs(length() - L) <= effectiveTolerance()` plus the rounding allowance of 10.2 | **validated** (10.3) |

### 5.5 `lengthAt(t)`

- `t` must lie in `[t0, t1]`, else `IllegalArgumentException` (NaN included).
- `lengthAt(t0) == 0` and `lengthAt(t1) == length()` exactly.
- Otherwise let k be the largest panel index with `a_k <= t`. Then `lengthAt(t) = min(length(), S_k + F_k(t))`,
  where `F_k(t)` is defined by the first matching rule:
  1. `t <= a_k`: 0;
  2. `t >= b_k`: `I2_k`;
  3. `I2_k == 0`: 0;
  4. **ratio mode**, when `I1_k > 0 && I2_k <= 2 * I1_k && I1_k <= 2 * I2_k` (that is, `1/2 <= I2_k / I1_k <= 2`,
     tested without dividing): `min(I2_k, G(a_k, t) * rho_k)`, with `rho_k = I2_k / I1_k` computed first;
  5. otherwise, **linear mode**: `I2_k * ((t - a_k) / (b_k - a_k))`.
- **Guarantees (normative):** `F_k(t)` is never NaN and lies in `[0, I2_k]`, for every finite table.
  - Ratio mode: `rho_k` lies in `[1/2, 2]`, so it is finite and positive. `G(a_k, t)` is non-negative, possibly
    `+infinity`, never NaN (5.2, R2.1). R2 argued "a sum of non-negative terms times `h > 0`", but h is 0 when
    `t - a_k < 2 MIN_VALUE`, and the sum could overflow: finding 5. The product is therefore non-negative or
    `+infinity`, and the `min` makes it at most `I2_k`. The R1 formula `G * (I2 / I1)` could overflow
    (`I2 / I1 = infinity`) or form `0 * infinity = NaN` (finding 2).
  - Linear mode: `0 < t - a_k <= b_k - a_k` after rounding (rounding is monotone), so the fraction lies in `(0, 1]`.
- **When linear mode occurs:** only in panels whose estimate is grossly unresolved. Outside `[1/2, 2]`,
  `e_k = abs(I2_k - I1_k) >= I2_k / 2`; with `I1_k == 0`, `e_k = I2_k`. So in a **converged** table, linear mode
  occurs only in panels with `I2_k <= 2 tau`, where its error is at most `2 tau`. For the reference families, no
  panel is in linear mode (measured: 0 of 21,876 panels in the 1,344 reference tables,
  `validation/r2/linear-mode.txt`).
- At most 5 derivative evaluations (none in linear mode). The result lies in `[0, length()]`.

### 5.6 `parameterAt(distance)`

- `distance` must lie in `[0, length()]`, else `IllegalArgumentException` (NaN included).
- `parameterAt(0) == t0` (the same double), and `parameterAt(length()) == t1`.
- Otherwise, with `M_k = max(abs(a_k), abs(b_k))` and `tolInv = tau * 2^-10`:
  1. **Panel.** k is the smallest panel index with `S_(k+1) >= distance`, and `r = min(distance - S_k, I2_k)`. (The
     clamp matters because `S_(k+1)` is the rounded `S_k + I2_k`, so `distance - S_k` can exceed `I2_k` by
     rounding.)
  2. If `I2_k == 0` or `r <= 0`, the result is `a_k`.
  3. **Bracket.** `lo = a_k`, `hi = b_k`, with residuals `f_lo = -r` and `f_hi = I2_k - r`. These follow from
     `F_k(a_k) = 0` and `F_k(b_k) = I2_k` (5.5) and need no evaluation. The **better end** of the bracket is `lo` if
     `abs(f_lo) < abs(f_hi)`, else `hi` (ties go to `hi`).
  4. **Ends within the tolerance.** If `min(r, f_hi) <= tolInv`, return the better end (no evaluation).
  5. **Mode.** Ratio or linear mode of panel k (5.5). In ratio mode, `rho_k = I2_k / I1_k`. In every mode,
     `sigma_k = I2_k / (b_k - a_k)`.
  6. **Initial iterate.** `t = lo + (hi - lo) * (r / I2_k)`. If it is not strictly inside (`lo < t < hi` fails),
     `t = lo + 0.5 * (hi - lo)`. If that is not strictly inside either, return the better end.
  7. **Iterations** `i = 0, 1, ..., 63`, in this order:
     1. `f = F_k(t) - r` (5 derivative evaluations in ratio mode, none in linear mode);
     2. if `f == 0`, return t;
     3. if `f < 0`, set `lo = t` and `f_lo = f`; otherwise set `hi = t` and `f_hi = f`;
     4. if `abs(f) <= tolInv`, return t;
     5. if `i == 63`, stop (**cap exit**);
     6. **Newton candidate, only while `i < 16`:** the slope is `v(t) * rho_k` in ratio mode (one derivative
        evaluation) and `sigma_k` in linear mode. If the slope is `> 0`, the candidate is `t - f / slope`. From
        `i = 16` on there is no candidate, and the speed is not evaluated;
     7. if there is no candidate, or it is not strictly inside `(lo, hi)` (NaN included), the candidate is the
        midpoint `lo + 0.5 * (hi - lo)` of the updated bracket;
     8. if the midpoint is not strictly inside either, stop (**collapse exit**);
     9. `t` = the candidate.
  8. After a cap or collapse exit, return the **better end** of the final bracket.

**Guarantees of `parameterAt` (normative):**

| # | Guarantee |
|---|---|
| P1 | The result lies in `[a_k, b_k]`, a subset of `[t0, t1]`. It is a deterministic function of the table and `distance`. `point` is never called |
| P2 | **Bracket invariant:** `f_lo < 0 < f_hi` from step 4 on. (If `r <= tolInv` or `f_hi <= tolInv`, step 4 returns, and `tolInv >= MIN_VALUE > 0`.) Every evaluated iterate is strictly inside the current bracket, so every completed iteration strictly shrinks it. Under deterministic curves the bracket sequence is bit-reproducible |
| P3 | **Bracket progress:** the iterates evaluated at `i = 17` to `63` are midpoints, because Newton candidates are formed only for `i < 16`. Each midpoint at least halves the bracket, up to rounding. So, whenever the loop reaches the cap, the final bracket width is `W <= 2^-47 (b_k - a_k)(1 + 2^-46) + 2 ulp(M_k)`. At a collapse exit `W <= 2 ulp(M_k)`. Both satisfy the same bound (proof: `M4-ERROR-ANALYSIS.md` 7) |
| P4 | **Exits:** after an `f == 0` or tolerance exit (steps 4, 7.2, 7.4), `abs(F_k(t) - r) <= tolInv`. After a cap or collapse exit, the result is the end of a bracket of width at most W (P3) at which `F_k - r` changes sign, and it is the end with the smaller residual (ties: `hi`). If `F_k` is continuous on the bracket, a root of `F_k = r` lies within W of the result. **No residual bound** is claimed at these exits: `F_k` may jump (a speed discontinuous at the scale of the Gauss nodes), so `abs(F_k(t) - r)` can stay above tolInv |
| P5 | **Work:** at most 64 evaluations of `F_k` (5 derivative calls each) plus 16 speed evaluations, so **at most 336 derivative evaluations**. None in linear mode. At most 64 iterations; no loop depends on a floating-point condition alone |

Not guaranteed: convergence to `tolInv` within 64 iterations for arbitrary curves. **R1 corrections:**
- R1 claimed that 64 iterations shrink any bracket by `2^-64` and reach ulp accuracy. That was unsupported: a Newton
  step strictly inside the bracket may shrink it by as little as one ulp, which is the "Newton creep" of finding 3.
  P3 is the replacement, and it is proven.
- R1 did not specify the result after 64 iterations; P4 now does.
- At a collapse, R1 returned the end where `F_k >= r`; R2 returns the better end, for both exits.

## 6. Equal-distance sampling

### 6.1 `uniformDistance(table, n, endpoints, limits)` (normative structure)

- Validation as in section 3.
- If `table.length() == 0`, the result is identical to
  `uniformParameter(table.curve(), t0, t1, n, endpoints, limits)` (collapsed curve: all points coincide).
- Otherwise, with den as in section 4:
  - `t_0 = t0`;
  - for `INCLUDE_END` and `n >= 2`, `t_(n-1) = t1`;
  - every other `t_k = max(t_(k-1), table.parameterAt(table.length() * ((double) k / den)))`.

  The parameters are non-decreasing by construction, even for stationary intervals, cusps and adversarial curves.
- Points as in section 3.
- Work: n calls of `point`, at most `336 n` calls of `derivative`.

### 6.2 Spacing

`countForSpacing(length, spacing, endpoints)` (normative, exact):
- `length` must be finite and `>= 0`; `spacing` finite and `> 0`; otherwise `IllegalArgumentException`.
- `q = length / spacing`. If `q >= 2^62`, the result is `Long.MAX_VALUE` (saturating).
- `m = (long) Math.floor(q)`; if `q - m >= 0.5`, `m = m + 1` (round half up). For `0 <= q < 2^62` this equals Java's
  `Math.round(q)` (specified as "closest long, ties toward positive infinity"); the explicit form keeps the rule
  self-contained.
- The result is `m + 1` for `INCLUDE_END` and `max(1, m)` for `EXCLUDE_END`.

`bySpacing(table, spacing, endpoints, limits)`:
- `n = countForSpacing(table.length(), spacing, endpoints)`. If `n > limits.maxSamples()`, `IllegalArgumentException`
  **before** any evaluation.
- Otherwise the result is `uniformDistance(table, (int) n, endpoints, limits)`.
- The realised spacing is `length / m`, the requested spacing rounded to fit the curve exactly. A zero-length table
  gives one sample.

### 6.3 Accuracy of equal-distance samples

- **Exact:** first and last parameters (6.1), monotonicity, counts.
- **Validated** (10.3): for converged tables of the reference families, `abs(L(t0, t_k) - s_k) <= 2 tau` plus the
  rounding allowance of 10.2.
- **Validated cost:** on smooth reference curves at relative tolerance 2^-30, the Newton steps keep the mean below 40
  derivative evaluations per sample. Measured with R2: mean 13.8, max 29, over 20,979 inversions
  (`validation/evidence.txt`). Pure bisection would need about 180.

## 7. `SampleSet`

- **Immutability:** it is immutable. `parameters()` returns a new array on each call; `points()` is unmodifiable;
  later changes to returned arrays do not affect the set.
- **Access:** `parameter(i)` and `point(i)` throw `IndexOutOfBoundsException` outside `[0, size())`.
- **`transformed(pose)`:** a new set with the same parameters and points `pose.toWorld(p_k)`, bit for bit equal to
  `curve.transformed(pose).point(t_k)`. Null gives `IllegalArgumentException`. No curve is evaluated.

  Use: measure and sample a local curve once, then move it every tick with M1 `Pose` (DM4-7).

## 8. Limits and budgets

- **`SampleLimits`:** `1 <= maxSamples <= 2^20` and `256 <= maxEvaluations <= 2^24`, else
  `IllegalArgumentException`. There is no default instance: callers choose limits per effect.
- **`ArcLengthTolerance`:** `absolute` finite and `>= 0`; `relative` in `[2^-40, 2^-3]`, else
  `IllegalArgumentException`.
  - For a purely absolute tolerance, use `relative = 2^-40`.
  - Below 2^-40, rounding dominates, so tighter values are meaningless.

**Work bounds (normative)**, in curve evaluations per call:

| Call | `point` | `derivative` |
|---|---|---|
| `uniformParameter(n)` | n | 0 |
| `ArcLengthTable.build` | 0 | at most `maxEvaluations` |
| `lengthAt` | 0 | at most 5 |
| `parameterAt` | 0 | at most 336 (5.6 P5) |
| `uniformDistance(n)`, `bySpacing` | n | at most `336 n` |
| `transformed`, `countForSpacing` | 0 | 0 |

Allocation is O(n) for sample sets and O(panelCount) for tables. Every loop has a fixed iteration bound, and no loop
is driven by a floating-point accumulation.

## 9. Exceptions

| Condition | Exception |
|---|---|
| null argument; non-finite or reversed range; range width overflow; count outside `[0, maxSamples]`; spacing count above `maxSamples`; invalid limits or tolerance; `lengthAt` or `parameterAt` outside its domain; `countForSpacing` with invalid input | `IllegalArgumentException` |
| non-finite derivative component during `build`, `lengthAt` or `parameterAt`; non-finite length | `ArithmeticException` |
| exception thrown by the curve | propagates unchanged |
| non-convergence within the budget | none: `converged()` is false and `errorEstimate()` is reported |

## 10. Numerical policy

### 10.1 Determinism

- Results depend only on the arguments and on the curve's results.
- Only `+ - * /`, `Math.sqrt`, `abs`, `min`, `max`, `floor` and comparisons are used: all exactly specified in Java.
  No transcendental function, `fma`, randomness, clock, thread or mutable static.
- Sections 4, 5.2 to 5.6, 6.1 and 6.2 fix every operation and its order. Conforming implementations therefore give
  bit-identical results, on every Java 25 JVM, for deterministic curves.
- Tables and sample sets may be shared between threads. Concurrent use of a table requires the curve to be safe to
  call concurrently, which all M3 curves are.

### 10.2 Rounding allowance (normative for exact cases)

Let:
- `dv` bound the absolute error of the computed speed on the range: `sqrt(3)` times the M3 per-component derivative
  bound (M3 contract 7.2 with A3, which already includes subnormal components), plus 5 eps of the speed. R2 added
  `MIN_VALUE` here for a rounded subnormal speed. R2.1 never rounds the product `mu * nu` in the rule, so that term is
  gone: a tighter allowance, which M4R21RegressionTest checks;
- `T = max(abs(t0), abs(t1))` and `K = panelCount()`;
- `R_w` = the number of half-panel rules of nonzero width among the final panels (`G(a, m)` with `m > a`, and
  `G(m, b)` with `b > m`), so `R_w <= 2K`.

Then:
- **Constant-speed curves:** `abs(length() - L) <= (t1 - t0) * dv + (K + 64) eps L + R_w MIN_VALUE / 2`.
- **In general:** the rounding part of the error is at most
  `(t1 - t0) * (dv + (4 eps T + 2 MIN_VALUE) max abs(C''))` (the last term: rounded node positions, including the
  underflow of h) `+ (K + 64) eps L + R_w MIN_VALUE / 2`. It adds to the quadrature part of 10.3.
- The `R_w MIN_VALUE / 2` term (R2.1) is the absolute underflow of the final product of each rule that enters the
  length. Zero-width rules are exact (0). History:
  - R1 had no such term (finding 1).
  - R2 had `8 K MIN_VALUE`, which still admitted F4, because the arithmetic lost far more than that when the
    speed was large.
  - R2.1's term is smaller (at most `K MIN_VALUE`), and the arithmetic now meets it. For example, a unit-speed line
    on `[0, MIN_VALUE]` has `R_w = 1`, so its length must be exactly `MIN_VALUE`.
- The `K eps L` term comes from the plain left-to-right summation. It stays below `2^-40 L` only up to about 8,000
  panels. Very tight relative tolerances with huge budgets are therefore limited by summation, as documented in
  `M4-ERROR-ANALYSIS.md`.

`M4-ERROR-ANALYSIS.md` derives these bounds.

### 10.3 Quadrature accuracy (validated, not proven)

No quadrature rule can bound the error of an arbitrary black-box integrand, so `converged()` asserts only that the
**estimate** `errorEstimate()` is at most `tau` (B3). For the reference families:
- lines, circles, helices, planar and conical spirals;
- quadratic and cubic Beziers, including cusps, stationary points, extrapolated ranges and magnitudes from 1e-3 to
  1e100;

the acceptance criterion is achieved error at most `tau` plus the 10.2 allowance (B6, 6.3). Measured: worst achieved
error 0.31 tau over 336 lengths at four tolerances (`validation/evidence.txt`).

### 10.4 Domains

| Item | Supported | Note |
|---|---|---|
| curve coordinates | up to 1e100; subnormal allowed | the scaled norm avoids squares overflowing or underflowing. Derivatives are accepted up to `MAX_VALUE` per component: the panel rule never overflows unless the length does (5.2, R2.1) |
| parameter range | finite, `t0 <= t1`, `abs(t) <= 1e3` recommended | extrapolation follows the curve |
| lengths | finite | a non-finite length gives `ArithmeticException` |
| counts | up to `maxSamples <= 2^20` | |
| budgets | 256 to 2^24 derivative evaluations | the initial pass uses 240 |
| tolerance | relative `2^-40` to `2^-3`, absolute any finite value `>= 0` | the effective tolerance is at least `TAU_FLOOR = 1024 MIN_VALUE` (5.3) |

## 11. Out of scope

Deferred: see `M4-ARCHITECTURE.md` section 6.
- surface and area distributions (disks, spheres, rings);
- randomness and jitter;
- emission scheduling and particle-density budgets per server or tick;
- multi-segment paths with continuous spacing;
- per-sample tangents or frames (callers use `curve.derivative(t_k)` and M1 `Frame.transportY`);
- any Paper, Bukkit or Minecraft adapter;
- any change to M1, M2 or M3.
