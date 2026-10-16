# M4 numerical conventions and supported domains

Status: PROPOSED, with the contract (revision 2.1). The M1 conventions (`docs/CONVENTIONS.md`), M2 conventions and M3 conventions
(`docs/m3/M3-CONVENTIONS.md`) remain in force unchanged.

## 1. Coordinates and orientation (inherited)

- World axes: +X east, +Y up, +Z south. The basis is right-handed (`X x Y = Z`).
- M4 never changes orientation: sample points are `Curve.point(t)` as M3 defines them. So:
  - a positive-turn spiral runs counter-clockwise from above;
  - `horizontalClockwiseFromAbove` is the Full Heal ring.
- Placement uses M1 `Pose.toWorld`: rotation, uniform scale, then translation.

## 2. Units

| Quantity | Unit |
|---|---|
| sample points, lengths, spacing, absolute tolerance | blocks |
| curve parameter t, `t0`, `t1` | dimensionless (M3: [0, 1] is the curve, outside is extrapolation) |
| distance along the curve (`lengthAt`, `parameterAt`) | blocks from `t0` |
| relative tolerance | dimensionless fraction of the length |
| budgets | number of `curve.derivative` calls (`maxEvaluations`), number of samples (`maxSamples`) |

## 3. Sampling conventions

- **Order:** samples are ordered by non-decreasing parameter. Index 0 is at `t0`.
- **Endpoints:** `INCLUDE_END` covers both ends (open paths), and `EXCLUDE_END` omits `t1` (closed loops: no seam
  duplicate).
- **Counts:** n = 0 gives an empty set; n = 1 gives `t0` only.
- **Spacing to count:** round half up of `fl(L / d)` intervals (contract 6.2). The realised spacing is `L / m`.
- **Uniform parameters** are a fixed formula (contract 4), bit for bit.
- **Equal-distance samples** are equal in arc length within the table tolerance (validated), not in chord length.
  Chords are slightly shorter on curved paths; that difference is geometry, not error.

## 4. Supported domains

| Item | Guaranteed | Note |
|---|---|---|
| curve coordinates | finite, magnitudes from subnormal to 1e100 | M3 domain; scaled speed norm (no squares overflow or underflow). User curves may have derivative components up to `MAX_VALUE`: the panel rule overflows only if the length does (R2.1) |
| speeds | finite | a non-finite derivative component gives `ArithmeticException` |
| parameter range | finite `t0 <= t1`, finite width; `abs(t) <= 1e3` recommended | larger ranges follow M3 extrapolation (spiral phase precision degrades with `abs(turns t)`) |
| length | finite and `>= 0` | non-finite gives `ArithmeticException` |
| counts | `0..maxSamples`, `maxSamples <= 2^20` | larger requests are rejected before work |
| budgets | `256..2^24` derivative evaluations per table | the initial pass needs 240 |
| relative tolerance | `2^-40..2^-3` | the effective minimum is about `(K + 64) eps` for K panels (summation limit) |
| absolute tolerance | finite, `>= 0` | the effective tolerance is at least `1024 MIN_VALUE` (R2 floor, contract 5.3) |

## 5. Separation of numerical accuracy and visual quality

- **Numerical guarantees:**
  - exact endpoints, counts, monotonicity and bit-for-bit uniform parameters;
  - work bounds;
  - `converged()` means the error estimate is at most tau;
  - validated achieved error at most tau on the reference families.
- **Visual quality** (density, flicker, overdraw, perceptual evenness) is **not** an M4 guarantee. It depends on
  particle type, size, client settings and timing:
  - Even arc-length spacing is a geometric property.
  - Whether 0.25-block spacing looks continuous is a rendering decision, for the adapter and the effect designer.
  - M4 offers spacing and count controls, plus `countForSpacing` to predict counts before any work.

## 6. Facts used by the design (each checked)

| # | Fact | Evidence |
|---|---|---|
| F1 | The 5-point Gauss-Legendre constants are the correctly rounded closed forms; the weights sum to 2 at 60 digits | generator self-check; contract 5.2 |
| F2 | Constant-speed curves are measured exactly (rounding only) in 240 evaluations with 16 panels | `ArcLengthTableTest.lines`, `constantSpeedSpirals` |
| F3 | At equal cost, chords err by up to 6.5e-2 and the adaptive table by up to 2.0e-8 (336 rows, rel 1e-7) | `validation/evidence.txt` |
| F4 | Depth-first refinement starves the right-hand panels under a budget; global refinement does not (estimate 0.35 vs 7e-8 at 65,536 evaluations) | `validation/design-probe.txt`, `evidence.txt` |
| F5 | `floor(nextDown(0.5) + 0.5) == 1` (the naive rounding), while round half up (and Java's `Math.round`) give 0 | `counts.csv` row; probe in `validation/mutation-results.txt` (M20 equivalent) |
| F6 | The naive norm `sqrt(x^2 + y^2 + z^2)` overflows above 1e154 and underflows below 1e-162 | `M4AdversarialTest.magnitudes`; mutant M11 |
| F7 | Inversion needs 13.8 derivative evaluations on average and at most 29 on the reference curves (R2; R1: 14.0 and 29) | `evidence.txt` |
| F8 | An exact-quarter circle sampled with 2,000 equal-distance samples on t in [-1000, 1000] lands on integer parameters within 1e-6 | `M4AdversarialTest.magnitudes` |
| F9 | R1 had `tau = 0` for subnormal lengths, so rounding decided convergence (32 panels for some subnormal circles). It produced NaN in `lengthAt` for panels with `I2/I1` overflowing, or with `I1 = 0` and a large `I2`. It crept through all 64 Newton iterations (384 evaluations) on a speed spike. R2 fixes all three: 16 panels and `tau = 1024 MIN_VALUE`; finite linear-mode values; tolerance exits within 286 evaluations | `validation/r2/findings-r1.txt`, `findings-r2.txt`; `M4R2RegressionTest` |
| F10 | R2 lost every panel of width `MIN_VALUE` (half-width 0: an M3 line on `[0, MIN_VALUE]` had length 0, even at speed 1e300). It threw `ArithmeticException` for finite lengths with speeds near 1e308, and returned NaN from `lengthAt(MIN_VALUE)` on a finite table. R2.1 measures all of these within the allowance; unit speed on `[0, k MIN_VALUE]` is exact | `validation/r21/findings-r2.txt`, `findings-r21.txt`; `M4R21RegressionTest` |
