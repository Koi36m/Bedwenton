# M3 conventions and supported domains

Status: PROPOSED, with the contract. M1's `docs/CONVENTIONS.md` and the M2 conventions remain in force unchanged.

## 1. Coordinates and orientation (inherited from M1)

- World axes: +X east, +Y up, +Z south. The basis is right-handed (`X x Y = Z`). Rotations are active and follow the
  right-hand rule.
- **Circles (M1 CONVENTIONS section 5, binding):**
  - A circle with normal n runs counter-clockwise about n.
  - Starting at +X with normal +Y, counter-clockwise from above reaches **-Z** at +90 degrees, and clockwise from
    above reaches **+Z**.
  - M3 realizes this exactly (DM3-6): local +Y is the spiral axis, the phase starts at local +X, and
    `P = (r cos theta, rise t, -r sin theta)`.
- Frames: right-handed `(right, up, back)`. To face a heading, use `Quat.fromFrame(Frame.fromHeading(forward, up))`.

## 2. Units

| Quantity | Unit |
|---|---|
| positions, radii, rise, control points | blocks |
| curve parameter t | dimensionless; [0, 1] is the curve, outside is extrapolation |
| spiral phase, `turns`, `startTurn` | **turns** (1 turn = 2π rad = 360 degrees) |
| easing input and output | dimensionless, [0, 1] |
| derivatives | blocks per unit t (first), blocks per unit t squared (second) |

Radians remain M1's unit for rotation APIs. Converting is `turns = radians / (2 * Math.PI)`. Degrees appear nowhere in
the API.

## 3. Supported domains

| Item | Guaranteed | Note |
|---|---|---|
| control points, radii, rise | finite, components up to 1e100 | larger values follow IEEE (may overflow) |
| t (Bezier, line) | finite, `abs(t) <= 1e3` for the stated bounds | the bounds scale with `(abs(1 - t) + abs(t))^n` |
| t, turns (spiral) | finite; `abs(turns * t) <= 2^20` for sub-nanoradian phase | the phase uncertainty grows like `2^-52 abs(turns t)` turns; exact seams up to `abs(turns * k) < 2^53` |
| startTurn | finite; small magnitudes preferred | enters the phase uncertainty |
| easing input | any non-NaN double | clamped to [0, 1] |
| poses | M1 domain (translation up to 1e100, scale in [1e-100, 1e100]) | M1 contract |

## 4. Facts used by the design (each checked)

| # | Fact | Evidence |
|---|---|---|
| F1 | `sin(fl(2π)) = -2.4e-16`, not 0; a radian circle does not close exactly | probe; `SpiralTest.largeTurnCounts` |
| F2 | After 2^20 turns, a radian quarter-turn point is off by about 5e-10 (`cos(2π (2^20 + 1/4))`) | probe; `SpiralTest.largeTurnCounts` |
| F3 | `x % 1.0` is exact (IEEE remainder, Java `%`) | M2 fact F5; generator checks |
| F4 | `StrictMath.sin/cos` are bit-for-bit fdlibm on every JVM; `Math.sin/cos` may differ (1 ulp); on Oracle 25.0.4.1 they differ in 3.4% of random arguments | Javadoc of `StrictMath`; probe (670,833 of 20,000,000) |
| F5 | The de Casteljau lerp `(1 - t) a + t b` gives exact endpoints, but not exact constancy (`0.7*3 + 0.3*3 = 2.9999999999999996`) | `BezierTest` |
| F6 | Power easings by repeated multiplication are exactly monotone; smoothstep and smootherstep are not, under the usual evaluation orders (decreases up to 1.1e-16 direct, 5.6e-17 split; direct smootherstep exceeds 1 and errs by 19 ulp) | probe; `EasingTest.monotonicity` |
| F7 | All easings, split-evaluated, err by at most 3.8 x 2^-53 against exact rationals | probe; `EasingTest.rangeAndAccuracy` |
| F8 | Uniform scale only (M1 `Pose`), so ellipses are not expressible by placement | M1 contract section 5 |
