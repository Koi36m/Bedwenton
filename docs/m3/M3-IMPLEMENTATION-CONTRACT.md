# M3 Implementation Contract: `workshop.vfx.math.curve` (curves, spirals, easing)

Audience: the engineer implementing the production source (Codex).
Status: **PROPOSED, revision 2. Not authorized for implementation.** Revision 2 corrects review findings A1 (phase
reduction with a large `startTurn`) and A2 (spiral bounds under extrapolation) and clarifies DM3-8; see
`M3-REVISION-2-CORRECTIONS.md`. Two things must happen first:
1. The owner approves this contract together with DM3-1 to DM3-14 (`M3-DESIGN-DECISIONS.md`).
2. M2 is formally accepted.

The tests in `src/test/java/workshop/vfx/math/curve` are the executable form of this contract. Where the two
disagree, stop and report; do not change either silently.

Candidate baseline: M2 revision 2 evidence `e3b79bae1476e549d49b29dcf361e7da50fe67db`. **Nothing in M1 or M2
changes**: no production class, test, golden file, contract or verification record. M3 adds a new package and its
tests.

Java 25. No dependencies. Package `workshop.vfx.math.curve`, which may use `workshop.vfx.math.core` (`Vec3`, `Quat`,
`Pose`, `Frame`) and must not use `workshop.vfx.math.time`. All types are immutable. Lengths are in blocks. Curve
parameters are dimensionless. Periodic phases are in **turns** (DM3-6).

## 1. Architecture

```
M2 time                 M3 curve (this contract)                                   M4 (later)
TimingSample.progress -> Easing.apply(p) -> Curve.point(t) / derivative(t)        sampling, arc length,
                                           LineSegment, QuadraticBezier,           density, emission
                                           CubicBezier, Spiral                     (consumes Curve)
                                           curve.transformed(Pose) -> PosedCurve
M1 core: Vec3, Quat, Pose, Frame (placement, orientation, unit tangents via normalizedOr, transport frames)
```

Curves are pure functions of `t`. They read no clock, keep no state and have no side effects.

## 2. Public API (complete)

```java
public interface Curve {
    Vec3 point(double t);
    Vec3 derivative(double t);           // dP/dt
    Vec3 secondDerivative(double t);     // d^2P/dt^2
    default Curve transformed(Pose pose) { return new PosedCurve(this, pose); }
}
public record LineSegment(Vec3 start, Vec3 end) implements Curve { }
public record QuadraticBezier(Vec3 p0, Vec3 p1, Vec3 p2) implements Curve { }
public record CubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) implements Curve { }
public record Spiral(double startRadius, double endRadius, double rise, double turns, double startTurn) implements Curve {
    public static Spiral circle(double radius);                                   // (r, r, 0, 1, 0)
    public static Spiral arc(double radius, double startTurn, double turns);      // (r, r, 0, turns, startTurn)
    public static Spiral helix(double radius, double rise, double turns);         // (r, r, rise, turns, 0)
    public static Spiral planarSpiral(double r0, double r1, double turns);        // (r0, r1, 0, turns, 0)
    public static Spiral conicalSpiral(double r0, double r1, double rise, double turns); // (r0, r1, rise, turns, 0)
    public static Spiral horizontalCounterClockwiseFromAbove(double radius);      // == circle(radius)
    public static Spiral horizontalClockwiseFromAbove(double radius);             // (r, r, 0, -1, 0)
    public boolean closed();
}
public record PosedCurve(Curve curve, Pose pose) implements Curve { }
public enum Easing {
    LINEAR, SMOOTHSTEP, SMOOTHERSTEP,
    QUAD_IN, QUAD_OUT, QUAD_IN_OUT, CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
    QUART_IN, QUART_OUT, QUART_IN_OUT, QUINT_IN, QUINT_OUT, QUINT_IN_OUT;
    public double apply(double progress);
}
```

No other public types or public members, apart from those records and enums generate. Package-private or private
helpers are allowed. One public type per file, so the seven files are named after the types.

## 3. Placement: `PosedCurve` and `Curve.transformed`

- Constructor: `curve` and `pose` must not be null (`IllegalArgumentException`). `transformed(null)` therefore throws
  too.
- `point(t) = pose.toWorld(curve.point(t))`.
- `derivative(t) = pose.rotation().rotate(curve.derivative(t)).mul(pose.scale())`, and the same for
  `secondDerivative`. Derivatives transform as scaled directions, never as points.
- Parameter validation is the inner curve's.
- Orientation by heading: `Quat.fromFrame(Frame.fromHeading(forward, up))`. Rotating circles: a new `Pose` per
  evaluation. M1 conventions apply unchanged.

## 4. `LineSegment`, `QuadraticBezier`, `CubicBezier`

- Constructors: every control point non-null and finite (`Vec3.isFinite`), else `IllegalArgumentException`.
- Every method rejects non-finite `t` with `IllegalArgumentException`. Finite t outside [0, 1] extrapolates the
  polynomial (DM3-2).
- **Definitions** (Bernstein form, normative mathematics):
  - `B(t) = sum_{i=0..n} C(n,i) (1-t)^(n-i) t^i P_i`, with n = 1 (line), 2 or 3;
  - `B'(t) = n sum C(n-1,i) (1-t)^(n-1-i) t^i (P_{i+1} - P_i)`;
  - `B''(t) = n(n-1) sum C(n-2,i) (1-t)^(n-2-i) t^i (P_{i+2} - 2P_{i+1} + P_i)`;
  - a line has `B'' = 0`.
- **Recommended algorithm** (DM3-8): de Casteljau with `lerp(a, b, t) = (1 - t) a + t b` (M1 `Vec3.lerp`), applied
  to the control points, their first differences (times n) and their second differences (times n(n-1)).
- **Required exact properties:**
  - E1. `point(0)` equals `P0` and `point(1)` equals `Pn` (numerical `==` per component), for every valid input.
  - E2. Small dyadic inputs give exact values: coordinates multiples of 1/16 in [-64, 64], t a multiple of 1/64 in
    [-2, 3]. These are the reference vectors' EXACT rows.
  - E3. A coincident-point curve has derivative exactly zero. Its points equal the point within the section 7 bound,
    and exactly at t = 0 and t = 1 (DM3-8 trade-off). An implementation may add explicit special cases (for example a
    per-component `a == b ? a : (1 - t) a + t b`) that make these points exact everywhere while keeping E1; the tests
    accept both (DM3-8).

## 5. `Spiral` (circle, arc, helix, planar and conical spirals)

**Constructor** (`IllegalArgumentException` otherwise):
- `startRadius` and `endRadius` finite and `>= 0`;
- `rise`, `turns` and `startTurn` finite;
- zero radius and zero turns are valid.

**Definition** in local coordinates (local +Y is the axis):
```
phase(t)  = startTurn + turns * t                         (turns)
theta(t)  = 2 pi phase(t)                                 (radians, mathematical only)
r(t)      = startRadius                if startRadius == endRadius     (exactly constant)
          = (1 - t) startRadius + t endRadius    otherwise            (exact at t = 0 and t = 1; may be < 0 when t is outside [0, 1])
P(t)      = ( r cos theta,  rise t,  -r sin theta )
P'(t)     = (dr cos theta - w r sin theta,  rise,  -dr sin theta - w r cos theta),  dr = endRadius - startRadius, w = 2 pi turns
P''(t)    = (-2 dr w sin theta - w^2 r cos theta,  0,  -2 dr w cos theta + w^2 r sin theta)
```

**Radius evaluation (normative, revision 2).** Let `S(t) = startRadius` if the radii are equal, otherwise
`S(t) = abs(1 - t) startRadius + abs(t) endRadius` (the input scale of the radius; `S(t) = r(t)` on [0, 1]). The
evaluated radius `r~` must satisfy:
- `r~ = startRadius` exactly when the radii are equal, `r~(0) = startRadius` and `r~(1) = endRadius` exactly;
- `abs(r~(t) - r(t)) <= 2^-51 S(t)` for every finite t.

The lerp `(1 - t) startRadius + t endRadius` meets both (proof in `M3-REVISION-2-CORRECTIONS.md`, section 2). The
form `startRadius + t (endRadius - startRadius)` does not: it is not exact at t = 1, and near t = 1 with
`startRadius >> endRadius` its error is unbounded relative to S. Under extrapolation, `r(t)` may cancel to a value far
below its rounding error, which is bounded relative to S and not relative to `abs(r(t))` (finding A2: `r0 = 1`,
`r1 = 1.2`, `t = -5` gives the exact radius 2^-52, and the lerp gives 0, within `2^-51 * 12`).

**Orientation (M1 CONVENTIONS section 5, binding).** With positive `turns` the curve runs counter-clockwise about
local +Y viewed from above: the circle starts at local +X, and +90 degrees (t = 1/4 for one turn) reaches local -Z.
`horizontalCounterClockwiseFromAbove(r)` reaches `(0, 0, -r)` at t = 1/4, and `horizontalClockwiseFromAbove(r)`
reaches `(0, 0, r)`. Full Heal's ring `(r cos th, h, r sin th)` is `horizontalClockwiseFromAbove(r)` at
`t = th / 2 pi`, raised by h. Astral Prism's positive rotation about +Y is `horizontalCounterClockwiseFromAbove`.

**Trigonometry (normative requirements, DM3-6 and DM3-7):**
- T1. Only `StrictMath.sin` and `StrictMath.cos` may be called; no other transcendental function.
- T2. Exact phase reduction (normative; corrected in revision 2, finding A1). `startTurn` and the product
  `turns * t` are each reduced to [0, 1) on their own, and only then combined:
  - `frac(x) = x % 1.0`, plus 1.0 if negative; a result of exactly 1.0 becomes 0.0. `x % 1.0` is exact, so `frac` is
    exact for `x >= 0` and rounds at most once (by at most 2^-53 turn) for `x < 0`;
  - `a = frac(turns * t)`, `b = frac(startTurn)`, `f = frac(a + b)`. Since `a + b` lies in [0, 2), its one rounding
    is at most 2^-53 turn and the final `frac` is exact;
  - quadrant `q = floor(4 f)`, residual `x = (4 f - q) * (pi / 2)` (`4 f - q` is exact);
  - `(cos, sin) = (c, s)`, `(-s, c)`, `(-c, -s)` or `(s, -c)` for q = 0 to 3, with `c = StrictMath.cos(x)` and
    `s = StrictMath.sin(x)`.

  Adding `startTurn` to anything before reducing it is **not** conforming: revision 1's
  `frac(frac(turns * t) + startTurn)` rounds `frac(turns * t)` away once `abs(startTurn) >= 2^52` (counterexample:
  `startTurn = 2^52`, `turns = 1`, `t = 1/4` gives (1, 0, 0) instead of (0, 0, -1)). Adding the unreduced product
  `turns * t + frac(startTurn)` is not conforming either (it breaks T3 just below 2^51). Any other operation order
  must give the same `f` bit for bit.
- T3. Exact quarter turns. If `startTurn` is a multiple of 1/4 of **any** magnitude (every double with
  `abs(x) >= 2^51` is one), and the exact product `turns * t` is a multiple of 1/4 with `abs(turns * t) < 2^51` (it is
  then representable), the cosine and sine are exactly 0 or ±1. The point is then exactly `(r c, rise t, -(r s))`
  with c and s in {0, ±1} and `rise t` the rounded product.
- T4. Exact seams. If `closed()` is true, then for every integer-valued double k for which `turns * k` is finite,
  `point(k)` equals `point(0)` and `derivative(k)` equals `derivative(0)` (numerical `==` per component), for every
  `startTurn`. (`turns * k` is exact below 2^53, and every double of magnitude at least 2^52 is an integer, so
  `frac(turns * k) = 0`.)
- T5. Period invariance in `startTurn` (new in revision 2). For `startTurn` values x and y with `x >= 0`, `y >= 0`
  and `x - y` an integer, the spiral with x and the spiral with y return identical results (`==`) for every t.
  For negative values `frac` may round once, so congruent negative offsets agree within the section 7.2 bounds.
- `closed()` is true exactly when `startRadius == endRadius`, `rise == 0`, `turns != 0` and `turns` is an integer.
  A zero-radius spiral can be "closed"; it is a point.

## 6. `Easing`

**Definitions** (exact, on [0, 1]):

| Easing | f(t) |
|---|---|
| LINEAR | t |
| SMOOTHSTEP | 3t^2 - 2t^3 |
| SMOOTHERSTEP | 6t^5 - 15t^4 + 10t^3 |
| `<P>_IN` | t^n |
| `<P>_OUT` | 1 - (1 - t)^n |
| `<P>_IN_OUT` | 2^(n-1) t^n for t < 1/2; 1 - (2 - 2t)^n / 2 for t >= 1/2 |

with n = 2, 3, 4, 5 for QUAD, CUBIC, QUART, QUINT.

**`apply(p)`:**
- `p` NaN: `IllegalArgumentException`.
- `p <= 0` (including -0.0 and -infinity): +0.0.
- `p >= 1` (including +infinity): 1.0.
- Otherwise `f(p)` with the result clamped to [0, 1].

**Recommended evaluation:**
- power easings by repeated multiplication, `(1 << (n-1)) * t^n`, and `2 - 2t` (exact for t >= 1/2);
- SMOOTHSTEP and SMOOTHERSTEP split at 1/2: `f(t)` for `t <= 1/2`, `1 - f(1 - t)` above.

**Guarantees:**
- G1. `abs(apply(p) - f(clamp(p))) <= 2^-50`.
- G2. Range [0, 1].
- G3. Symmetric easings return exactly 0.5 at 0.5.
- G4. Power easings are exactly monotone (non-decreasing) on doubles.
- G5. SMOOTHSTEP and SMOOTHERSTEP satisfy `apply(b) >= apply(a) - 2^-49` for `a <= b`. This is a consequence of G1;
  exact monotonicity is not achievable with common evaluation orders (DM3-11).
- G6. Symmetry `f(1-t) = 1 - f(t)` and duality `OUT(t) = 1 - IN(1-t)` hold within 4 x 2^-50.
- Continuity: C0 at 1/2 for IN_OUT; SMOOTHSTEP is C1 and SMOOTHERSTEP C2 at both ends.

## 7. Numerical policy

### 7.1 Determinism

- Results depend only on the arguments: no clocks, randomness, threads or mutable statics (`M3BoundaryTest`).
- A given implementation returns bit-identical results on every conforming Java 25 JVM:
  - `+ - * /`, `%`, `Math.floor`, `Math.rint` and `Math.sqrt` are exactly specified;
  - `StrictMath` trigonometry is bit-for-bit fdlibm;
  - Java floating point is strict (JEP 306);
  - `Math.fma`, `java.lang.Math` trigonometry and other non-portable functions are forbidden.
- The contract does **not** fix an operation order beyond the rules above (DM3-12). Different conforming
  implementations may differ within the section 7.2 bounds.

### 7.2 Error bounds (absolute, per component)

Let `G = abs(1 - t) + abs(t)` (= 1 on [0, 1]) and `eps = 2^-53`. The Bezier and line bounds:

| Quantity | Bound |
|---|---|
| Bezier/line point | `2^-49 * G^n * max_j abs(P_j)` |
| Bezier/line derivative | `2^-48 * n * G^(n-1) * max_i abs(P_{i+1} - P_i)` |
| Bezier second derivative | `2^-46 * 4 n(n-1) * G^(n-2) * max_j abs(P_j)` |

For spirals (revision 2, finding A2), with the radius scale `S` of section 5 (`S = startRadius` if the radii are
equal, otherwise `abs(1 - t) startRadius + abs(t) endRadius`), `dr = abs(endRadius - startRadius)`,
`w = 2 pi abs(turns)` and the phase uncertainty `dphi = 2 pi * 2^-52 * (abs(turns * t) + 2)` radians (from rounding
`turns * t`, at most two roundings of `frac` on negative values, the sum of the reduced parts and the conversion to
radians; there is no `startTurn` term because T2 reduces it exactly):

| Quantity | Bound |
|---|---|
| Spiral point | `2^-49 (S + abs(rise t)) + S dphi` |
| Spiral derivative | `2^-48 W + W dphi`, with `W = dr + w S + abs(rise)` |
| Spiral second derivative | `2^-47 W2 + W2 dphi`, with `W2 = 2 dr w + w^2 S` |
| Easing | `2^-50` |

- On [0, 1], `S = abs(r(t))`, so these bounds are the revision 1 bounds there. Only extrapolated t, where
  `(1 - t) startRadius + t endRadius` can cancel, gets the larger scale. Revision 1 used `R = abs(r(t))`, which is
  invalid there: the A2 counterexample has an error of 2^-52 against a revision 1 bound of about 1.0e-30.
- Bounds and test tolerances are functions of the inputs only (S, `abs(turns t)`, `abs(rise)`), never of a rounded
  intermediate of the evaluated formula.
- `PosedCurve` adds M1 `Pose` rounding (relative order `10 eps`); it is tested by metamorphic composition.
- The bounds are rigorous by standard rounding analysis and leave headroom. The measured worst cases are recorded in
  the test specification.

**Precision limits (revision 2):**
- [fact] The phase term makes precision depend on `abs(turns * t)`: at 2^20 turns the angular uncertainty is about
  1.5e-9 rad. **Practical domain:** `abs(turns * t) <= 2^20` for sub-nanoradian phase. For `abs(turns * t) >= 2^52`
  the rounded product is an integer and t no longer moves the phase.
- [fact] The magnitude of `startTurn` costs no evaluation accuracy (T2 reduces it exactly). Its **representable**
  fraction is limited, though: a double `startTurn` carries its fractional part only to a multiple of `ulp(startTurn)`,
  so 2^-32 turn at 2^20, 1/4 turn at 2^50 and integers only from 2^52 on. That is input quantization, not evaluation
  error: the result is exact for the double that was passed. Callers that accumulate phases should keep `startTurn`
  reduced to [0, 1).
- [fact] Under extrapolation, the radius has an absolute error up to `2^-51 S`. Where `r(t)` cancels to a value
  of that order, its relative error is unbounded (A2). This is inherent in evaluating `(1 - t) r0 + t r1` in double
  precision without `fma` (forbidden by section 7.1) or extended-precision arithmetic.
- Coordinates up to 1e100, and `abs(t) <= 1e3` for extrapolation, keep every intermediate finite.

### 7.3 Degeneracy

There is no exception for degenerate geometry:
- zero radius gives points on the axis;
- coincident control points give a zero derivative;
- zero turns give no rotation.

Unit tangents come from `derivative(t).normalizedOr(fallback)` (M1); a zero derivative yields the caller's fallback.
Orientation along a curve comes from M1 `Frame.transportY` (DM3-9).

## 8. Exceptions

| Condition | Exception |
|---|---|
| null or non-finite control point; negative or non-finite radius; non-finite rise, turns or startTurn; null curve or pose; non-finite `t`; NaN easing progress | `IllegalArgumentException` |
| anything else | none: extreme finite inputs follow IEEE-754 (for example extrapolation to `t = 1e300` may give infinity) |

## 9. Out of scope (see `M3-ARCHITECTURE.md` section 4)

The following are excluded:
- Catmull-Rom and other interpolating splines;
- piecewise paths;
- arc length and constant-speed reparameterization;
- sampling, density and particle emission (M4);
- curvature and Frenet APIs;
- ellipses and non-uniform scale;
- overshooting or trigonometric easings;
- any Paper, Bukkit or Minecraft adapter;
- any change to M1 or M2.
