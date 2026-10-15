# M3 numerical risk assessment

Status: PROPOSED, with the contract.

| # | Risk | Effect | Mitigation | Residual |
|---|---|---|---|---|
| R1 | `Math` trigonometry differs across JVMs and intrinsics | the same effect renders differently on different servers | `StrictMath` only (DM3-7); a source-scan test forbids `Math.sin/cos/...` | slower than intrinsics (not measured); M1's `Quat.axisAngle` still uses `Math` (unchanged, outside M3) |
| R2 | Radian phases drift and seams do not close | visible gaps and jitter after long runs | turns with exact reduction (DM3-6) | the phase rounding of `turns * t` still grows with magnitude; practical domain `abs(turns t) <= 2^20` |
| R3 | Bezier extrapolation | errors and values grow like `(abs(1-t) + abs(t))^n` | bounds stated; no clamping (callers clamp or ease) | large t gives large coordinates, which is IEEE behaviour |
| R4 | Cancellation in second derivatives (`P2 - 2P1 + P0`) | relative error large when the true value is small | bound stated in terms of `max abs(P)`, not of the result | the curvature of nearly straight Beziers is noisy |
| R5 | Zero derivative (coincident handles, zero radius, zero turns) | undefined tangent direction | raw derivative returned; callers use `normalizedOr(fallback)` | the caller must choose a fallback |
| R6 | Smoothstep and smootherstep are not exactly monotone in floating point | a 1e-16 backward step | documented G5 (monotone within 2^-49); split evaluation | invisible in rendering; do not use easing outputs for exact ordering decisions |
| R7 | Constancy of degenerate Beziers is not exact | a 1-ulp wobble of a "stationary" curve | documented E3 | none visible |
| R8 | The open `Curve` interface allows user implementations without these guarantees | inconsistent quality | the contract binds only the M3 types; M4 should not assume more than the interface | documentation |
| R9 | Pose placement at world-border coordinates (3e7) | absolute rounding of about 1e-8 blocks | M1 accuracy (tested to 1e-7 at the border) | none visible |
| R10 | Unit confusion (turns vs radians) | wrong angles by a factor of 2π | names say "turn"; the M1 radian APIs are untouched | a review item for adapters |
| R11 | `closed()` for a zero-radius spiral is true | callers treating it as a ring | documented in the contract | none |
| R12 | Floating-point `startTurn` with many bits | seams exact, but the start angle rounds when added to the reduced fraction | `T4` holds regardless; `startTurn` enters `dphi` | negligible |

## Deferred improvements

| Item | Proposed home |
|---|---|
| centripetal Catmull-Rom splines and piecewise `Path` with continuity rules | M3.1 |
| arc length, constant-speed reparameterization, uniform-distance sampling | M4 |
| curvature and Frenet or rotation-minimizing frames APIs | M4 or motion |
| ellipses and non-uniform scale | after an M1 extension (non-uniform `Pose`), if ever |
| overshooting easings (back, elastic) and sine easings | only on demand, as a separate non-monotone category |
| performance benchmark of `StrictMath` trigonometry | M4, when sampling volumes are known |
