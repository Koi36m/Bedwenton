# M3 design decisions: curves and geometry paths

Status: **PROPOSED, not approved.** The contract and tests implement the **recommended** option of each decision.
Every recommendation is for owner review (and for independent review by ChatGPT). Nothing here is approved.

Candidate baseline: M2 revision 2 evidence `e3b79bae1476e549d49b29dcf361e7da50fe67db` (production
`15f6564b2580d3722b625c9be96e57f2580c81a3`). M2's formal owner acceptance is a separate checkpoint and must be
confirmed before M3 production work starts.

Notation: **[fact]** is a mathematical or platform fact, checked by a test or probe (cited). **[pref]** is a design
judgement.

## Summary

| ID | Decision | Recommended | Main alternative |
|---|---|---|---|
| DM3-1 | M3 scope | line, quadratic and cubic Bezier, one circular family (circle, arc, helix, planar and conical spiral), pose placement, easing | also Catmull-Rom splines and piecewise paths |
| DM3-2 | Parameter domain | every curve on t in [0, 1]; extrapolation by the defining formula; non-finite t rejected | natural parameters per curve (angle, arc length) |
| DM3-3 | Curve abstraction | open interface `Curve` with `point`, `derivative`, `secondDerivative`; immutable records | sealed hierarchy; or a single function object |
| DM3-4 | Placement | curves defined in local coordinates, placed with M1 `Pose` through `PosedCurve` | each curve stores its own center and frame |
| DM3-5 | Circular curves | one `Spiral` record with named factories | separate `Circle`, `Arc`, `Helix`, `Spiral` types |
| DM3-6 | Angle unit for periodic curves | **turns**, with exact phase reduction | radians, like M1 |
| DM3-7 | Trigonometry | `StrictMath` | `java.lang.Math` |
| DM3-8 | Bezier evaluation | de Casteljau with M1-style lerp (exact endpoints) | Bernstein sum; power basis (Horner) |
| DM3-9 | Tangents and frames | raw derivatives only; unit tangent and frames through M1 (`normalizedOr`, `Frame.transportY`) | unit-tangent, normal and Frenet APIs in M3 |
| DM3-10 | Arc length | none in M3; M4 owns reparameterization and sampling | arc-length tables in M3 |
| DM3-11 | Easing | enum of 15 polynomial easings; input clamped to [0, 1]; NaN rejected | open functional interface; overshooting and trigonometric easings |
| DM3-12 | Determinism and oracles | reproducible per implementation across JVMs; exact classes bitwise; others within proven bounds against independent high-precision oracles | a bitwise replay of a prescribed algorithm (as M2) |
| DM3-13 | Dependencies | `curve` depends on `core` only, not on `time` | curves take `TimingSample` directly |
| DM3-14 | Curvature and higher-order quantities | provide `secondDerivative`; curvature formula documented, not an API | a curvature API with degeneracy rules |

## DM3-1. Scope of the minimal M3

| Feature | Proposal | Reason |
|---|---|---|
| Line segment | **in** | trivial, and needed for beams and trails |
| Circle and arc, any 3D orientation | **in** (`Spiral` + `Pose`) | magic circles, orbits; required by M1 CONVENTIONS section 5 |
| Helix | **in** (`Spiral`) | trident spirals, energy streams |
| Planar and conical spirals | **in** (`Spiral`) | charging effects |
| Quadratic and cubic Bezier | **in** | particle trails, custom artifact paths |
| Catmull-Rom (or another interpolating spline) | **deferred to M3.1** | needs a parameterization decision (uniform, centripetal or chordal), piecewise indexing, closed loops and continuity rules. Uniform Catmull-Rom can produce cusps and self-intersections with uneven spacing (Yuksel et al. 2011); centripetal fixes that but needs knot spacing. Worth its own review |
| Piecewise paths and composition | **deferred to M3.1** | the same piecewise machinery; continuity guarantees depend on the spline choice |
| Easing | **in** | M2 progress needs shaping now |

[pref] The deferred items do not change the M3 interfaces: a future `Path` and `CatmullRom` would be `Curve`
implementations. Constellation tracing can use joined cubic Beziers in the meantime (C1 joining is tested).

## DM3-2. Parameter domain: normalized t in [0, 1]

- **Recommended:** every curve is parameterized on [0, 1]. `point(0)` and `point(1)` are the endpoints. A circle runs
  through `turns` revolutions as t goes from 0 to 1.
- **Alternative:** natural parameters (angle in radians for circles, an arbitrary interval for splines).
- **Outside [0, 1]:** the defining formula continues (the polynomial for Bezier, the phase and radius for spirals),
  with no clamping. Callers clamp or ease; M2 already delivers progress in [0, 1].
- **Non-finite t:** `IllegalArgumentException`, as in M2's `Timing.sample`.

Reasoning:
- [pref] A uniform domain composes directly with M2 (`progress -> easing -> curve`) and gives M4 one sampling
  interface.
- [fact] Extrapolation of Bezier polynomials grows like `(abs(1 - t) + abs(t))^n`, and so do their error bounds
  (contract 7).

## DM3-3. Curve abstraction

- **Recommended:** a public, non-sealed interface `Curve { point; derivative; secondDerivative; default transformed(Pose) }`,
  implemented by immutable records.
- **Sealed hierarchy:** lets switch statements be exhaustive, but blocks user-defined curves, which future VFX need.
- **Single-function object:** no derivatives, so M4 would have to differentiate numerically.

Derivatives are part of the interface because M4 (arc length, constant speed, orientation) needs them, and analytic
derivatives are exact up to rounding, unlike finite differences.

## DM3-4. Placement with M1 `Pose`

- **Recommended:** each curve has a canonical local definition (a spiral around local +Y at the origin; Beziers with
  their own control points). `curve.transformed(pose)` returns `PosedCurve`:
  - `point = pose.toWorld(local point)`;
  - derivatives = `pose.rotation().rotate(local derivative) * pose.scale()`.
- **Alternative:** every curve stores a center, a normal and a reference direction.

Reasoning:
- [pref] This reuses M1's validated `Pose` (translation, unit rotation, uniform scale) with no new orientation type.
  Orientation from headings uses `Frame.fromHeading` and `Quat.fromFrame`.
- Composition `c.transformed(a).transformed(b) == c.transformed(b.then(a))` follows from M1 (tested within
  tolerance).
- [fact] M1 `Pose` allows only uniform scale. Ellipses are therefore not expressible; a non-uniform scale would also
  break the derivative rule. Ellipses are deferred.

## DM3-5. One circular family

- **Recommended:** a single `Spiral(startRadius, endRadius, rise, turns, startTurn)`:
  - radius `r(t)`: linear from `startRadius` to `endRadius` (exactly `startRadius` when the two are equal);
  - height: `rise * t` along local +Y;
  - phase: `startTurn + turns * t` turns.
- Factories: `circle`, `arc`, `helix`, `planarSpiral`, `conicalSpiral`, plus the two names M1 CONVENTIONS section 5
  requires, `horizontalCounterClockwiseFromAbove` and `horizontalClockwiseFromAbove`.
- **Alternative:** separate types.

Reasoning: [pref] one formula, one derivative, one oracle and one set of degeneracy rules instead of four. The cost is
that closedness is a property of the parameters (`closed()`), not of the type.

## DM3-6. Angles in turns with exact reduction

- **Recommended:** periodic curves measure phase in **turns** (1 turn = 2π rad). The fractional part is reduced
  exactly, then the quarter-turn quadrant, before any trigonometry.
- **Alternative:** radians, as M1 does for `axisAngle`.
- [fact] In radians, a full circle does not close exactly: `sin(2π)` gives -2.4e-16 (`2π` is not representable).
  After 2^20 turns a quarter-turn point is off by about 5e-10 (probe in the test specification;
  `SpiralTest.largeTurnCounts`).
- [fact] With turns, `x % 1` is exact, quarter turns give exactly 0 and ±1, and the phase precision does not depend
  on how many whole turns have elapsed. The same idea underlies M2's exact cycle remainder.
- Consequences:
  - M1 CONVENTIONS section 5 checks hold **exactly**: +90 degrees reaches (0, 0, -r) bit for bit.
  - Closed spirals have bitwise-identical seams at every integer t.
  - Callers with radians divide by 2π.
- M1 is unaffected; its radian APIs stay as they are. [pref] The deviation from M1's "angles in radians" is limited to
  curve phase parameters, and their names say "turn".

## DM3-7. StrictMath for trigonometry

- **Recommended:** `StrictMath.sin` and `StrictMath.cos`, and no other transcendental function. The M3 boundary test
  scans the sources for `Math.sin`, `Math.cos`, `pow`, `hypot`, `fma` and similar.
- **Alternative:** `java.lang.Math`.
- [fact] The `StrictMath` specification requires results bit-for-bit identical to fdlibm on every JVM. `Math` allows
  any result within 1 ulp and may use intrinsics. On this JVM (Oracle 25.0.4.1) the two disagree in 3.4% of random
  arguments (670,833 of 20,000,000; probe).
- Numerical consequence: the accuracy is the same (fdlibm is within 1 ulp), so only reproducibility changes.
- Cost: possibly slower than the intrinsic. Microbenchmarks are deferred; one `sin` and one `cos` per evaluation.
- M1 still uses `Math` in `Quat.axisAngle`. That is unchanged and outside M3. Curve evaluation calls no M1
  trigonometry; `Pose` uses only `+ - * /`.

## DM3-8. Bezier evaluation: de Casteljau

- **Recommended:** de Casteljau with the lerp `(1 - t) a + t b` (M1 `Vec3.lerp`). Derivatives are de Casteljau on the
  first and second differences.
- **Alternatives:** the Bernstein sum `sum_i C(n,i) (1-t)^(n-i) t^i P_i`, or the power basis evaluated by Horner.
- [fact] de Casteljau forms convex combinations for t in [0, 1], so its rounding error is bounded by a small multiple
  of `eps * max abs(P)`. The power basis can suffer cancellation for clustered control points.
- [fact] With this lerp, endpoints are exact (`point(0) == P0` and `point(1) == Pn` bitwise). C0 joins between
  segments are therefore exact (`BezierTest.exactEndpoints`).
- [fact, trade-off] The same lerp does **not** keep coincident control points exactly constant:
  `(1 - 0.3) * 3 + 0.3 * 3 = 2.9999999999999996`. The form `a + t (b - a)` would keep constants but lose the exact
  endpoint. No single lerp gives both. Endpoint exactness is kept, and constancy holds within the error bound
  (`BezierTest.degenerateGeometry`; M1 review observation I4).

## DM3-9. Tangents and frames

- **Recommended:** curves return raw derivatives only.
  - Unit tangent: `derivative(t).normalizedOr(fallback)`, M1 Tier 2, total, with the caller's fallback.
  - Orientation along a path: M1 `Frame.transportY` (parallel transport, no flips).
- **Alternative:** tangent, normal and Frenet frames in M3.
- [fact] The Frenet frame is undefined where curvature vanishes (lines, inflection points) and flips across them. M1
  already provides twist-free transport. Duplicating either would violate "avoid duplication of M1".

## DM3-10. Arc length and parameterization: M4

| Concept | Where |
|---|---|
| uniform parameter spacing (t = i/n) | M4 sampling (trivial) |
| uniform geometric spacing, constant-speed motion | **M4**: needs arc-length reparameterization |
| exact arc length | closed form for lines and helices only (`sqrt((2π r turns)^2 + rise^2)`); Bezier arc length has no elementary closed form in general (cubic: elliptic integrals) |
| numerical arc length (Gauss-Legendre, adaptive) | **M4**, using M3 `derivative` |

[pref] M3 has no `length()` API, so there is no half-solution with its own error policy. M3 supplies exact analytic
derivatives, which is what numerical arc-length integration needs. Constant helix speed is tested as a property
(`SpiralTest.shapeProperties`).

## DM3-11. Easing

- **Recommended:** `enum Easing` with 15 constants:
  - `LINEAR`, `SMOOTHSTEP`, `SMOOTHERSTEP`;
  - power in, out and in-out for exponents 2 to 5 (`QUAD`, `CUBIC`, `QUART`, `QUINT`).
- `apply(p)`: NaN gives `IllegalArgumentException`; `p <= 0` gives +0.0 and `p >= 1` gives 1.0 (clamping);
  otherwise the polynomial, with the output clamped to [0, 1].
- **Excluded:**
  - overshooting easings (back, elastic): they leave [0, 1] and are not monotone;
  - sine and exponential easings: transcendental, with no shape the polynomials lack;
  - a user-defined functional interface: callers can write any `DoubleUnaryOperator`.
- [fact] Power easings evaluated by repeated multiplication are exactly monotone on doubles (proof: compositions of
  monotone, correctly rounded operations; `EasingTest.monotonicity`).
- [fact] Smoothstep and smootherstep are **not** exactly monotone in floating point under any of the common
  evaluation orders. Direct `t*t*(3-2t)` has 16,411 adjacent-double decreases in the probe windows; split
  (symmetric) evaluation has 3,328, all at most 5.6e-17. Direct smootherstep is also inaccurate near 1, with 19 ulp
  error and values slightly above 1.
- So the contract recommends split evaluation and guarantees accuracy within 2^-50 absolute. That implies
  monotonicity up to 2^-49, a documented approximate guarantee, not exact monotonicity.
- [pref] Clamping inputs, rather than extrapolating, is right for easing: `smoothstep(2) = -4` is meaningless as an
  animation value.

## DM3-12. Determinism and test oracles

- **Recommended:**
  - (a) Determinism means the same implementation gives identical results on every JVM. That is guaranteed by IEEE
    `+ - * /`, `%` and `sqrt`, `StrictMath` trigonometry, Java's strict floating point and no `fma`.
  - (b) The contract fixes formulas and required properties, but not a bit-exact operation sequence.
  - (c) Tests compare **exactly** only where the exact value is representable and every intermediate of any
    reasonable evaluation is exact: small dyadic inputs, quarter turns and endpoints. Elsewhere they compare within
    proven error bounds against **independent** oracles: exact rationals, the Bernstein form, 60-digit Taylor series,
    quaternion rotation and finite differences. The oracles never copy the recommended algorithm.
- **Alternative:** M2-style bitwise golden vectors from a replay of a prescribed algorithm.
- [pref] The brief asks not to impose bitwise expectations on transcendental results without a portable guarantee,
  and not to copy the production algorithm into the oracle. `StrictMath` *is* portable, but bitwise expectations
  would still pin one evaluation order. That buys cross-implementation identity, which M3 does not need, at the cost
  of independence.

## DM3-13. Dependencies

- **Recommended:** `workshop.vfx.math.curve` depends on `workshop.vfx.math.core` only. Easing takes a `double`, so
  `TimingSample.progress()` feeds it without coupling.
- **Alternative:** overloads taking `TimingSample`.
- [pref] This keeps M3 testable without time types and avoids a dependency cycle risk in later milestones. The tests
  still check composition with M2 (`EasingTest.composesWithM2Progress`).

## DM3-14. Curvature

- **Recommended:** expose `secondDerivative` and document the curvature formula
  `kappa = abs(p' x p'') / abs(p')^3`. The test computes it as an oracle (circle: 1/r). No curvature API.
- **Alternative:** a curvature method, which needs a policy for `abs(p') = 0` (cusps, coincident points).
- [pref] No M3 use case consumes curvature directly. M4 or motion can add it with a degeneracy policy.

## What changes if the owner picks an alternative

| If | Revise |
|---|---|
| DM3-1 includes Catmull-Rom and paths | add a contract section, oracles (Barry-Goldman pyramid), tests; about 30% more scope |
| DM3-2 uses natural parameters | every test that states t in [0, 1]; the spiral reference vectors |
| DM3-5 uses separate types | API and boundary tests; the formulas are unchanged |
| DM3-6 uses radians | the exact quarter-turn, seam and large-turn tests become tolerance tests; the spiral reference vectors are regenerated with radian inputs |
| DM3-7 uses `Math` | remove the StrictMath boundary rule; tolerances are unchanged |
| DM3-11 extrapolates | the clamping tests in `EasingTest` |
| DM3-12 is bitwise | add a replay generator and bitwise golden data, M2-style |
