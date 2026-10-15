# Conventions (binding for the whole engine)

These conventions are executable: `ConventionsTest`, `FrameTest`, `M0RegressionTest` and the golden vectors
check every statement in the tables below.

## 1. World axes and handedness

| Axis | Minecraft meaning | Vector |
|---|---|---|
| +X | east | (1, 0, 0) |
| +Y | up | (0, 1, 0) |
| +Z | south | (0, 0, 1) |

The three axes form a **right-handed** basis: `east x up = south` (that is `X.cross(Y) = Z`). Every
transformation in the engine uses the right-hand rule, so all textbook formulas apply unchanged.
The engine's math core does not know about yaw, pitch, or any Minecraft API. Converting a player's yaw/pitch
to a heading vector is an adapter concern.

## 2. Rotation direction

All rotations are **active** (they move vectors, not coordinate axes) and follow the right-hand rule:
a positive angle about an axis turns counter-clockwise when you look down that axis from its positive end
toward the origin.

**Viewed from above** means: an observer high above looking straight down along -Y, with north (-Z) at the top
of the picture and east (+X) at the right, like a map. In that picture a world point `(x, y, z)` appears at
screen position `(sx, sy) = (x, -z)`. `ConventionsTest` uses exactly this mapping.

| Rotation of the point that starts at +X (east) | Result | Looks like, viewed from above |
|---|---|---|
| +90 degrees about **+Y** (positive right-handed rotation) | `(0, 0, -1)` = -Z (north) | **counter-clockwise** |
| -90 degrees about +Y, equivalently +90 degrees about **-Y** | `(0, 0, +1)` = +Z (south) | **clockwise** |

So "positive rotation about +Y" and "clockwise viewed from above" are **opposite senses**. Do not conflate
them. For a circle that starts at +X:

- counter-clockwise from above passes through -Z: `(cos t, 0, -sin t)`, which is `Quat.axisAngle(Y, t)` applied to `(r,0,0)`;
- clockwise from above passes through +Z: `(cos t, 0, +sin t)`, which is `Quat.axisAngle(-Y, t)` applied to `(r,0,0)`.

Audit item 1: Full Heal 0.2.0's `ring(r, theta, h) = (r cos t, h, r sin t)` is the **clockwise-from-above**
sense. Astral Prism 0.2.0's `rotate` about +Y is the **counter-clockwise** sense.

Other axis rotations by +90 degrees (all verified in `ConventionsTest`):
about +X: `Y -> Z`, `Z -> -Y`; about +Y: `Z -> X`, `X -> -Z`; about +Z: `X -> Y`, `Y -> -X`.

## 3. Composition order

- `a.mul(b)` applies **b first, then a**. With `qx` and `qz` being +90 degrees about X and Z and `v = (1,0,0)`:
  `qz.mul(qx).rotate(v) = (0,1,0)` (X first), `qx.mul(qz).rotate(v) = (0,0,1)` (Z first).
- `parent.then(child)` expresses the child in the parent's space: `parent.then(child).toWorld(p)
  == parent.toWorld(child.toWorld(p))`.
- Euler import: `Quat.fromEulerApplyXThenYThenZ` rotates about **fixed world axes**, X first, then Y, then Z.
  This is the order Astral Prism 0.2.0 uses. (Whether JOML's `rotationXYZ` applies X first or Z first is
  recorded by `JomlOracleTest.jomlEulerOrderIsPinned`; do not assume it.)

## 4. Frames (anchors and orientation)

A `Frame` is a right-handed orthonormal triple `(x, y, z)`. For things that face a direction, the axes are
`(right, up, back)`:

- `back = -forward`, `right = forward x up`, `up = right x forward`, so `right x up = back`.
- Facing south (+Z) with +Y up: right = west `(-1,0,0)`, back = north `(0,0,-1)`.
- Facing east (+X): right = south `(0,0,1)`. Facing north (-Z): right = east `(1,0,0)`.
  Facing west (-X): right = north `(0,0,-1)`.
- `Frame.WORLD` = `(X, Y, Z)` corresponds to facing -Z (north) with +Y up.

**Never** map local x to right, y to up and z to forward. That basis is a reflection (determinant -1): local
rotations then appear mirrored in the world (audit item 2, Astral Prism 0.2.0). The `(right, up, forward)` trap
is covered by a negative-control test (`FrameTest.leftHandedNegativeControl`).

## 5. Forward requirements for milestone M3 (circles)

M1 contains no circle type. When circles arrive in M3 they must satisfy these conventions:

- A circle is defined by an orthonormal pair `(u, v)` with normal `n = u x v`; angle increases
  counter-clockwise about `n`.
- Named horizontal factories must exist and must pass these checks, starting at +X with the circle normal
  along +Y:
  `horizontalCounterClockwiseFromAbove`: angle +90 degrees reaches **-Z**;
  `horizontalClockwiseFromAbove`: angle +90 degrees reaches **+Z**.
- The Full Heal ring must be reproducible as `horizontalClockwiseFromAbove`.

## 6. Units and precision

Lengths in blocks, angles in radians, double precision throughout. Local-space math is done in doubles and
the anchor translation is added last; renderer-specific float conversion happens only in adapters.
Floats near the Minecraft world edge (about 30 million blocks) are spaced **2 blocks** apart and near 1,000,000
blocks they are spaced 0.0625 blocks apart (checked in `PoseTest.floatSpacingDocumentation`).
