# M3 architecture proposal: curves and geometry paths

Status: **PROPOSED, revision 2, not approved** (see `M3-DESIGN-DECISIONS.md` and `M3-REVISION-2-CORRECTIONS.md`). Candidate baseline: M2 revision 2 evidence
`e3b79bae1476e549d49b29dcf361e7da50fe67db`, on which the 191 M1 and M2 tests pass on Java 25.0.4.1 and Gradle 9.1.0
(re-run independently for this proposal). M2's formal acceptance must precede M3 production work.

## 1. Layers

```
workshop.vfx.math.core   (M1, unchanged)  Vec3, Quat, Pose, Frame, MathUtil
workshop.vfx.math.time   (M2, unchanged)  TimePoint, Playback, Timing, TimingSample, ...
workshop.vfx.math.curve  (M3, new)        Curve, LineSegment, QuadraticBezier, CubicBezier, Spiral, PosedCurve, Easing
                                          depends on core only; never on time (DM3-13)
```

A typical effect chains the layers. Each arrow is a pure function, and the caller holds all state:

```
tick -> Playback.localTicks -> Timing.sample -> progress -> Easing.apply -> Curve.point(t)
                                                                    Curve.transformed(Pose(center, rotation(time), scale))
```

## 2. How the example effects map to M3

| Effect | M3 construction |
|---|---|
| rotating magic circle | `Spiral.circle(r).transformed(new Pose(center, Quat.axisAngle(Vec3.Y, angle(time)), 1))` |
| orbiting stars | several `Spiral.arc(r, k / n, 1)` sharing one pose; the phase offset is in turns |
| spiral around a charging trident | `Spiral.conicalSpiral(r0, r1, height, turns)` posed along the trident axis (`Quat.shortestArc(Vec3.Y, axis)`) |
| helical energy stream | `Spiral.helix(r, length, turns)` posed along the stream |
| constellation tracing | joined `CubicBezier` segments with mirrored handles (C1; `BezierTest`); smoother splines in M3.1 |
| Bezier particle trail | `QuadraticBezier` or `CubicBezier`; the particle placement belongs to M4 |
| activation and disappearance | `Easing` on `TimingSample.progress` driving radius, scale or curve t |
| artifact or enchantment paths | any `Curve`, user implementations included (the interface is open) |

No spell-specific presets are part of M3.

## 3. Reuse of M1 and M2

- Placement and orientation: M1 `Pose`, `Quat`, `Frame`. There are no new orientation types.
- Unit tangents: M1 `Vec3.normalizedOr` (total, with the caller's fallback).
- Frames along curves: M1 `Frame.transportY` (twist-free).
- Animation time: M2 `TimingSample.progress` is a `double` in [0, 1], consumed by `Easing.apply` and `Curve.point`.
- M3 adds no copies of M1 or M2 functionality, and changes no M1 or M2 file.

## 4. M3 / M4 boundary

| Concern | M3 (this proposal) | M4 (later) |
|---|---|---|
| geometry: position, first and second derivative at t | yes | consumes it |
| exact analytic speed (lines, helices) | as properties (tested) | uses them |
| arc length, arc-length tables, constant-speed reparameterization | no | yes (numerical integration of `derivative`) |
| uniform-t sampling | no (trivial for callers) | yes, with density rules |
| uniform-distance sampling, particle spacing, emission density | no | yes |
| particle emission, distribution, randomness | no | M4 or later; never in the math core with hidden state |
| orientation of emitted objects along a path | M1 `transportY` + M3 derivatives | M4 policy |
| Catmull-Rom, piecewise paths | deferred to M3.1 | consumes them as `Curve` |

## 5. Deliverables in this proposal

| Item | Location |
|---|---|
| design decisions DM3-1 to DM3-14 | `docs/m3/M3-DESIGN-DECISIONS.md` |
| implementation contract | `docs/m3/M3-IMPLEMENTATION-CONTRACT.md` |
| conventions and supported domains | `docs/m3/M3-CONVENTIONS.md` |
| numerical risk assessment | `docs/m3/M3-RISKS.md` |
| test specification and acceptance criteria | `docs/m3/M3-TEST-SPECIFICATION.md` |
| Codex instructions (for after approval) | `docs/m3/README-FOR-CODEX-M3.md` |
| independent tests | `src/test/java/workshop/vfx/math/curve/` (44 tests in 7 classes, plus a helper) |
| reference data and generator | `src/test/resources/m3/reference/` (2,759 rows), `tools/generate_m3_reference_vectors.py` |
| validation evidence | `docs/m3/validation/` (revision 1) and `docs/m3/validation/r2/` (revision 2) |
| revision 2 correction report | `docs/m3/M3-REVISION-2-CORRECTIONS.md` |
