# M1 Implementation Contract: `workshop.vfx.math.core`

Audience: the engineer implementing the production source (Codex or any other).
**Revision 2 (Amendment 1): numerical policy.** Changed clauses are marked **[R2]** and listed, with reasons, in
`AMENDMENT-1-NUMERIC-POLICY.md`.
**Revision 3 (Amendment 2): two clarifications, no redesign.** Marked **[R3]**, listed in
`AMENDMENT-2-CLARIFICATIONS.md`: (A) caller-supplied fallbacks and `clamp` with infinite bounds; (B) the supported
domain of every operand, including scalar multipliers, interpolation parameters, angles and axes.
**Revision 4 (Amendment 3): decisions after the independent M1 review, no redesign.** Marked **[R4]**, listed in
`AMENDMENT-3-REVIEW-DECISIONS.md`: exception attribution in `Pose` (D1); tolerances, equality, null, hint and
`fromHeading` clarifications (A1 to A5); a corrected `rotate` accuracy bound (A6). Executable form:
`Amendment3AcceptanceTest`. Revision 3 remains the historical baseline (archive and Git history).
Clauses that are not marked are unchanged since revision 1.
Status: specification. The tests in `src/test/java` are the executable form of this contract. Where this
document and a test disagree, stop and report the disagreement; do not change either silently.

Package: `workshop.vfx.math.core`. Java 25. No dependencies. All types are immutable.
Numbers are `double`. Units: lengths in blocks, angles in radians.
Conventions (axes, handedness, rotation direction) are in `CONVENTIONS.md` and are binding.

## 0. Global rules

1. **[R2] Numerical policy: see section 0A.** Revision 1 said evaluation methods "never return NaN or infinity
   for finite input". That is impossible for IEEE-754 arithmetic (`Double.MAX_VALUE * 2` overflows) and is
   withdrawn. Section 0A replaces it with four tiers.
2. `Vec3` and `Quat` are plain records and do **not** validate (they may hold NaN or infinity). Use
   `isFinite()` or the validating consumers.
3. No allocation-avoidance tricks in M1. Correctness first. Records are fine.
4. No `Math.random`, no static mutable state, no threads, no I/O. Thread-safe by immutability.
5. Use `Math.sqrt`, `Math.sin`, `Math.cos`, `Math.atan2`. Do not use `StrictMath` unless you want to; tests
   use tolerances, never bit-exact comparisons (except where a test states `0.0` for hand-exact values).
6. No import of `org.bukkit`, `io.papermc`, `net.minecraft`, `org.joml` in production code.

## 0A. Numerical policy **[R2]**

All arithmetic is Java `double` (IEEE-754 binary64, round-to-nearest-even). No operation clamps, saturates,
sanitizes NaN, or flushes values to zero on its own initiative. Every member of the API belongs to exactly one tier
(only `MathUtil.clamp` is split: its value argument is Tier 2, its range arguments are Tier 3):

**Tier 1: raw arithmetic follows IEEE-754.** Overflow gives infinity, NaN and infinity propagate by IEEE rules,
gradual underflow applies, and **nothing throws**. Inside the *supported domain* (below) results are finite and
accurate; outside it the result is whatever IEEE arithmetic produces, and callers who care test `isFinite()`.
Members: `Vec3.add, sub, mul, negate, dot, cross, lengthSquared, length, distance, lerp`;
`Quat.mul, conjugate, dot, rotate`; `Frame.toWorld, toLocal`; `Pose.toWorld, toLocal, directionToWorld`.
(`length`, `lengthSquared` and `distance` are plain IEEE: they overflow when a component exceeds about 1.3e154
even if the exact length is representable. Use `normalizedOr` for extreme magnitudes.)

**Tier 2: degeneracy operations are total in their operands.** They have documented fallbacks. For **every**
`double` value of **every operand** (NaN, infinities, zero, subnormals, `Double.MAX_VALUE`) they return a valid
result and never throw (`MathUtil.clamp` throws only for an invalid range argument). Members: `Vec3.normalizedOr`,
`Quat.normalized`, `Quat.inverse`, `Quat.shortestArc`, `Quat.fromFrame`, `Frame.alignY`, `alignZ`, `fromHeading`,
`transportY`, `MathUtil.clamp`. The accuracy and fallback rules in sections 1 to 4 apply to all inputs.
**[R3] Two qualifications, stated so that no guarantee is stronger than the API allows:**
- **Caller-supplied fallback.** `Vec3.normalizedOr(fallback)` is the only operation that takes a fallback *value*.
  On the fallback path it returns the **caller's `fallback` unchanged: the same object, not validated, not
  normalized, not copied.** Therefore a finite result on that path requires the caller to supply a finite
  fallback, and a unit-length result on that path requires a unit-length fallback. On the non-fallback path
  (finite input whose largest component is at least `LENGTH_EPS`) the result is always a finite unit vector,
  whatever the fallback contains. Every other Tier 2 operation chooses its own fallback from the documented rules
  (`IDENTITY`, `WORLD`, the previous frame, a world axis), which are always finite and valid.
- **`MathUtil.clamp`.** The result always lies in `[lo, hi]` (a NaN value maps to `lo`). Bounds may be infinite
  (`lo` and `hi` must not be NaN, and `lo <= hi`). The result is finite whenever the value or bound it selects is
  finite, so `clamp` can return an infinity: `clamp(+inf, 0, +inf) = +inf`, `clamp(NaN, -inf, 1) = -inf`.
  With finite bounds the result is always finite.

**Tier 3: validated types reject, never repair.**
- Invalid **inputs** throw `IllegalArgumentException`: `Frame` constructor, `Pose` constructor,
  `Quat.axisAngle`, `Quat.fromEulerApplyXThenYThenZ` (via `axisAngle`), `MathUtil.clamp` (range). **[R4]** A
  `null` argument to any of these is an invalid input. Outside Tier 3, `null` is accepted only where a clause
  says so (the hints of section 4). Any other `null` operand is outside the API, and its behaviour is
  unspecified.
- A **derived** `Pose` that is not representable throws `ArithmeticException`: `Pose.then` and `Pose.inverse`
  (section 5). The inputs were valid, so this is an arithmetic condition, not an argument error.
- Never clamp, saturate or substitute a default. The exception message names the operation and the reason.
  **[R4]** For `Pose.then` and `Pose.inverse` the reason is the first failing check in the order of section 5
  (scale before translation), and the message contains the word `scale` or `translation` accordingly. Only those
  words and the operation name are tested.

**Tier 4: domain-restricted operations.** `Quat.slerp`, `nlerp`, `angleTo` and `toFrame` require finite **unit**
quaternions (and finite `t`, within the domain table for `slerp` and `nlerp`). For such inputs the results are finite and accurate as specified. Outside the domain
the result is unspecified (it may be non-finite). They must not throw, with one exception: `toFrame()` on a
non-unit or non-finite quaternion may throw `IllegalArgumentException` from the `Frame` invariants.
`Quat.rotate` is Tier 1 with the same unit-quaternion precondition: for a non-unit quaternion it returns the plain
IEEE result of its formula (a scaled rotation) and never throws.

**Supported domain (testable guarantee, not a clamp).** **[R3]** The guarantee is defined **per operand** in the
table below. For a *single* operation whose operands are all inside their domain, the result is finite and has the
accuracy of section 6 relative to the operand magnitudes; `Pose.then` and `Pose.inverse` do not throw. The result
may itself lie outside the domain (`mul` of 1e100 by 1e100 is 1e200); feeding it to a later operation is a new
operation, judged against the table again. **Nothing is claimed for operands outside the table, and nothing is
rejected, clamped or saturated there: plain IEEE-754 applies** (for example `Vec3(1e100,0,0).mul(1e300)` overflows
to infinity). Justification: products of two operands of magnitude at most 1e100 are at most 1e200, sums of three
such products at most 3e201, scale products lie in [1e-200, 1e200] (above the smallest normal double 2.2e-308),
reciprocals in [1e-100, 1e100]; all are far from overflow (1.8e308) and underflow.

| Operation | Operands inside the guaranteed domain |
|---|---|
| `Vec3.add, sub, negate, dot, cross, lengthSquared, length, distance` | every component magnitude at most 1e100 |
| `Vec3.mul(s)` | components at most 1e100 **and** finite scalar `s` with `abs(s)` at most 1e100 |
| `Vec3.lerp(to, t)` | components at most 1e100 **and** finite `t` with `abs(t)` at most 1e100 |
| `Quat.mul, dot` | every component magnitude at most 1e100 (unit quaternions included) |
| `Quat.rotate(v)` | quaternion finite with norm `k` within 1e-12 of 1; `v` components at most 1e100. **[R4]** Result finite; accuracy as in section 6 (it depends on `k`) |
| `Frame.toWorld, toLocal` | vector components at most 1e100 (frame axes are unit by construction) |
| `Pose.toWorld, toLocal, directionToWorld` | pose translation components at most 1e100, scale in [1e-100, 1e100]; vector components at most 1e100 |
| `Pose.then, inverse` | both poses with translation components at most 1e100 and scale in [1e-100, 1e100]; never throws; result scale in [1e-200, 1e200] |
| `Quat.slerp, nlerp` | finite unit quaternions; finite `t` with `abs(t)` at most **1e3** (angle accuracy degrades as `abs(t) * theta` grows) |
| `Quat.angleTo, toFrame` | finite unit quaternions |
| `Quat.axisAngle`, `fromEulerApplyXThenYThenZ` | **any** finite angle (the rotation by that exact value); axis finite with largest component at least `LENGTH_EPS`, **no upper magnitude bound** (normalized internally) |
| `MathUtil.clamp` | any value; bounds not NaN with `lo <= hi` (infinite bounds allowed, see Tier 2) |

| Tier | Throws | Result for finite input | Result for non-finite input |
|---|---|---|---|
| 1 raw | never | IEEE (finite when every operand is inside the supported domain) | IEEE propagation |
| 2 total | never (see `clamp`) | valid, always; finite unless the **caller's** fallback (`normalizedOr`) or an infinite `clamp` bound is | valid, always (own fallbacks); same caller-supplied exceptions |
| 3 validated | `IllegalArgumentException` (input) or `ArithmeticException` (derived) | valid object or exception, never a repaired one | exception |
| 4 domain | never (except `toFrame`) | correct inside the domain | unspecified |

## 1. `MathUtil`

```java
public final class MathUtil {
    public static final double LENGTH_EPS = 1e-12;
    public static double clamp(double v, double lo, double hi);
    public static boolean approxEquals(double a, double b, double tol);
}
```
- `clamp`: `lo` or `hi` NaN, or `lo > hi` throws `IllegalArgumentException`. `v` NaN returns `lo` (even when `lo`
  is infinite). Infinite `v` clamps to the nearest bound. **[R3]** Infinite bounds are permitted, so the result is
  guaranteed to lie in `[lo, hi]` but is finite only if the value or bound it selects is finite (section 0A).
- `approxEquals`: `Math.abs(a - b) <= tol`; false when either value is NaN. **[R3]** Because `inf - inf` is NaN,
  two equal infinities compare **false** whatever `tol` is; `approxEquals(1, 2, +inf)` is true. Never throws.

## 2. `Vec3`

```java
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO, X, Y, Z;               // (0,0,0) (1,0,0) (0,1,0) (0,0,1)
    public Vec3 add(Vec3 o);  public Vec3 sub(Vec3 o);
    public Vec3 mul(double s);  public Vec3 negate();
    public double dot(Vec3 o);  public Vec3 cross(Vec3 o);   // right-handed: X.cross(Y) = Z exactly
    public double lengthSquared();  public double length();  public double distance(Vec3 o);
    public Vec3 lerp(Vec3 to, double t);                    // (1-t)*this + t*to ; t NOT clamped
    public boolean isFinite();
    public Vec3 normalizedOr(Vec3 fallback);
}
```
`normalizedOr(fallback)` returns **the `fallback` object itself** (same reference) when the vector is not
finite or its magnitude is below `LENGTH_EPS` (1e-12). Otherwise it returns a unit vector accurate to 1e-12
for every finite input whose largest absolute component is at least 1e-12, including magnitudes up to 1e300.
That means squares of components may overflow: **scale by the largest absolute component before squaring**.
Magnitude is judged on the largest absolute component, so `(1e-13,0,0)` and `(1e-300,...)` give the fallback
while `(1e-11,0,0)` and `(0,0,3e-12)` do not. `fallback` is not validated or normalized. **[R3]** It is returned as the **same reference** on the fallback
path (not copied), so its finiteness and unit length are the caller's responsibility (section 0A, Tier 2).

**[R2]** All members except `normalizedOr` are Tier 1 (plain IEEE, see section 0A). `normalizedOr` is Tier 2
(total).

`cross` must be exactly `(y*o.z - z*o.y, z*o.x - x*o.z, x*o.y - y*o.x)` (tests compare hand values with
tolerance 0.0).

## 3. `Quat`

Hamilton quaternion `(w, x, y, z)`, active rotations. **[R2]** Tiers: `mul`, `conjugate`, `dot`, `rotate` Tier 1;
`normalized`, `inverse`, `shortestArc`, `fromFrame` Tier 2 (total); `axisAngle`, `fromEulerApplyXThenYThenZ`
Tier 3 (`IllegalArgumentException`); `slerp`, `nlerp`, `angleTo`, `toFrame` Tier 4 (unit finite inputs). `a.mul(b)` is the Hamilton product: **b is applied
first, then a.**

```java
public record Quat(double w, double x, double y, double z) {
    public static final Quat IDENTITY;                              // (1,0,0,0)
    public static Quat axisAngle(Vec3 axis, double radians);
    public static Quat fromEulerApplyXThenYThenZ(double rx, double ry, double rz);
    public static Quat shortestArc(Vec3 from, Vec3 to);
    public static Quat slerp(Quat a, Quat b, double t);
    public static Quat nlerp(Quat a, Quat b, double t);
    public static Quat fromFrame(Frame f);
    public Quat mul(Quat o);  public Quat conjugate();  public Quat inverse();
    public Quat normalized();  public double dot(Quat o);  public double angleTo(Quat o);
    public Vec3 rotate(Vec3 v);
    public Frame toFrame();
}
```

- **`axisAngle(axis, radians)`**: axis is normalized internally. Throws `IllegalArgumentException` when the
  axis is `null` **[R4]**, non-finite, or has magnitude below `LENGTH_EPS`, or when `radians` is not finite.
  Result is `(cos(r/2), sin(r/2) * unitAxis)`.
- **`fromEulerApplyXThenYThenZ(rx, ry, rz)`**: equals `qz.mul(qy).mul(qx)` where `qx`, `qy`, `qz` are
  `axisAngle` about +X, +Y, +Z. Rotation about **fixed world axes**: X first, then Y, then Z. This is an
  import helper for the audited Astral Prism order; no other Euler helper exists in M1.
- **`mul`**: raw Hamilton product, **no renormalization**.
  `(w1,x1,y1,z1)*(w2,x2,y2,z2) = (w1w2 - x1x2 - y1y2 - z1z2,  w1x2 + x1w2 + y1z2 - z1y2,
  w1y2 - x1z2 + y1w2 + z1x2,  w1z2 + x1y2 - y1x2 + z1w2)`.
- **`conjugate`**: `(w, -x, -y, -z)`.
- **`normalized`**: unit quaternion; zero, or any non-finite component, returns `IDENTITY`. Must be accurate
  for components like `1e200` and `3e-200` (scale by the largest absolute component first).
- **`inverse`**: `normalized().conjugate()` (these are rotations). **[R4]** For a zero or non-finite quaternion this
  is `IDENTITY.conjugate() = (1, -0.0, -0.0, -0.0)`, the identity rotation. Record `equals` on `Quat` and `Vec3` is
  structural (`Double.compare` per component). It distinguishes `0.0` from `-0.0` and `q` from `-q`, and is
  **not** a test of rotational or numerical equality. Specified values, including fallbacks, are numerical: `-0.0`
  and `0.0` are equal. Only `normalized()`, `shortestArc` and the Frame factories promise to return the `IDENTITY` or
  `WORLD` *constant*.
- **`dot`**: 4-component dot product.
- **`rotate(v)`**: assumes `this` is a unit quaternion (callers normalize). Use
  `v + 2w(u x v) + 2 u x (u x v)` with `u = (x, y, z)`; it is cheaper and more accurate than building `q v q*`.
  **[R4]** For a quaternion of norm `k`, the formula returns exactly `v + k^2 (R v - v)` in real arithmetic, where
  `R` is the rotation of `q / k`. Inside the domain the result is finite and lies within
  `(2 abs(k^2 - 1) + 1e-13) abs(v)` of `R v` (at most about `4.1e-12 abs(v)`).
- **`angleTo(o)`**: rotation angle in `[0, pi]` between two rotations, sign-insensitive (`q` and `-q` give 0).
  Must be **atan2-based**, not `acos`-based: with `r = this.conjugate().mul(o)`,
  `angle = 2 * atan2(|vector part of r|, |r.w|)`. Tests require 1e-12 absolute accuracy for angles from
  1e-9 up to `pi - 1e-6`.
- **`shortestArc(from, to)`**: unit quaternion rotating `normalize(from)` onto `normalize(to)` by the smallest
  angle. Input lengths are ignored. If either input is non-finite or below `LENGTH_EPS`, return `IDENTITY`.
  Recommended algorithm (the half-vector method, numerically better than `w = 1 + a.b`):
  `a = unit(from)`, `b = unit(to)`, `s = a + b`.
  If `|s| < 1e-10` (antiparallel): choose `e` = the world axis with the smallest `|component|` of `a`
  (ties broken in the order X, Y, Z), `axis = unit(a x e)`, return `(0, axis)` (a half turn).
  Otherwise `h = s/|s|`, return `normalize( (a.h, a x h) )`.
  Required accuracy is in the test (`QuatTest.shortestArcDegenerate`): error at most 1e-9 for offsets of 1e-6
  or more from antiparallel, 1e-8 at 1e-7, and 1e-6 at 1e-9.
- **`slerp(a, b, t)`**: shortest path (if `a.dot(b) < 0`, use `-b`). `t` is **not clamped**; extrapolation
  (`t < 0`, `t > 1`) continues along the same great circle at constant angular speed. Compute the angle as
  `theta = 2 * atan2(|b - a|, |b + a|)` (stable for tiny and near-pi angles). If `theta < 1e-8`, fall back to
  `nlerp`. Otherwise the weights are `sin((1-t) theta)/sin(theta)` and `sin(t theta)/sin(theta)`. Result is
  normalized. `slerp(a, a, t)` returns the same rotation.
- **`nlerp(a, b, t)`**: shortest-sign linear interpolation, then `normalized()`.
- **`toFrame()`**: `new Frame(rotate(X), rotate(Y), rotate(Z))`, requires `this` unit.
- **`fromFrame(f)`**: rotation whose `toFrame()` is `f`, via the standard branch-selecting (Shepperd)
  matrix-to-quaternion method so that **180-degree rotations about any axis work** (all four branches are
  exercised by `QuatTest.frameRoundTrip`). The frame columns are the images of X, Y, Z. Result normalized.

## 4. `Frame`

Right-handed orthonormal basis, stored as three world-space unit vectors: `x`, `y`, `z`.
**[R2]** The constructor is Tier 3. The four factories are Tier 2: for **every** `double` input (including NaN,
infinities, zero, subnormals, `Double.MAX_VALUE`) they return a valid frame (the rules below choose the fallback)
and never throw. `toWorld` and `toLocal` are Tier 1.

```java
public record Frame(Vec3 x, Vec3 y, Vec3 z) {
    public static final Frame WORLD;                       // (X, Y, Z)
    public static Frame alignY(Vec3 y, Vec3 xHint);
    public static Frame alignZ(Vec3 z, Vec3 yHint);
    public static Frame fromHeading(Vec3 forward, Vec3 up);
    public static Frame transportY(Frame prev, Vec3 newY);
    public Vec3 toWorld(Vec3 local);                       // x*lx + y*ly + z*lz
    public Vec3 toLocal(Vec3 world);                       // (world.x, world.y, world.z) dotted onto the axes
}
```
- **Constructor validation** (throws `IllegalArgumentException`): every axis finite; each length within 1e-9
  of 1; pairwise `|dot| <= 1e-9`; `|x.cross(y) - z| <= 1e-9` (right-handed). A left-handed triple such as
  `(X, Y, -Z)` must be rejected.
- **Every factory must return a frame that passes this validation at 1e-12** (tests re-check at 1e-12 with
  an independent triple-product test). Reach this by orthogonalizing **twice** (two Gram-Schmidt passes)
  and deriving the third axis by a cross product of the first two.
- **[R4] Hint processing order.** A hint argument (`xHint`, `yHint`, `up`) is evaluated in exactly this order,
  after the axis itself has been normalized:
  1. **Absence, on the hint as supplied (unnormalized).** The hint is *absent* when it is `null`, has a
     non-finite component, or its largest absolute component is below `LENGTH_EPS` (the `normalizedOr` predicate).
     A hint whose largest component is exactly `LENGTH_EPS` is present. An absent `xHint` or `yHint` is replaced by
     the fallback hint (the world axis least aligned with the axis, ties X, Y, Z). An absent `up` in `fromHeading`
     is replaced by +Y. In the bullets below, "zero/non-finite" means "absent".
  2. **Normalization.** A present hint is normalized. After this step the hint's length plays no further part;
     step 1 is the only place where it matters.
  3. **Parallel test, on the normalized hint.** The component of the normalized hint perpendicular to the
     normalized axis is computed. If its length is below **1e-8**, the hint counts as parallel and is replaced by
     the fallback hint of step 1. This includes the +Y substituted for an absent `up`. The fallback hint itself
     is never parallel, because its perpendicular component is at least `sqrt(2/3)`.
- **`alignY(y, xHint)`**: `y` axis = `normalize(y)`. If `y` is non-finite or below `LENGTH_EPS`, return
  `WORLD`. `x` axis = the component of `xHint` perpendicular to `y`, normalized. If `xHint` is
  zero/non-finite, or that perpendicular component (computed from the *normalized* hint) has length below
  **1e-8**, use the fallback hint instead: the world axis with the smallest `|component|` of `y`, ties broken
  in the order X, Y, Z. `z = x.cross(y)`.
- **`alignZ(z, yHint)`**: same idea with the roles shifted: `z = normalize(z)`; `y` = component of `yHint`
  perpendicular to `z` (same fallback rules and threshold); `x = y.cross(z)`. Same `WORLD` fallback.
- **`fromHeading(forward, up)`**: returns `x = right`, `y = up`, `z = back` where `back = -normalize(forward)`.
  `right = normalize(forward.cross(upHint))`, and `y = right.cross(forward)`.
  **[R4]** Defaults and degeneracy are defined by
  `fromHeading(forward, up) = alignZ(-forward, up is absent ? +Y : up)`. That is: `forward` absent returns
  `WORLD` ("facing -Z"). An absent `up` means +Y. `forward` and `up` count as parallel when the sine of the angle
  between them, `abs(normalize(forward) x normalize(up))`, is below 1e-8. The input lengths never matter. In that
  case the up hint is the least-aligned world axis of `forward` (ties X, Y, Z). Handedness: `right x up = back`.
  Worked examples (tolerance 1e-12):

  | `forward` | `up` | `x` (right) | `y` (up) | `z` (back) | Why |
  |---|---|---|---|---|---|
  | (0, 0, 1) | (0, 1, 0) | (-1, 0, 0) | (0, 1, 0) | (0, 0, -1) | facing south |
  | (0, 1, 0) | (0, 1, 0) | (0, 0, -1) | (1, 0, 0) | (0, -1, 0) | parallel: fallback up +X |
  | (1e-9, 1e-3, 0) | (0, 1, 0) | (0, 0, 1) | (-0.9999999999995, 9.999999999995e-7, 0) | (-9.999999999995e-7, -0.9999999999995, 0) | sine 1e-6: hint honoured |
  | (1e-3, 1e3, 0) | (0, 7, 0) | same as previous row | | | lengths ignored |
  | (1e-9, 1, 0) | (0, 1, 0) | (1, -1e-9, 0) | (0, 0, 1) | (-1e-9, -1, 0) | sine 1e-9: fallback up +Z |
  | (0, 0, 1) | (1e-13, 0, 0) | (-1, 0, 0) | (0, 1, 0) | (0, 0, -1) | up absent: +Y |
  | (0, 0, 1) | (1e-12, 0, 0) | (0, 1, 0) | (1, 0, 0) | (0, 0, -1) | up present |
- **`transportY(prev, newY)`**: carries `prev` to the new Y axis by the **shortest-arc rotation** from
  `prev.y` to `normalize(newY)` and returns the re-orthogonalized frame (new `y` equals
  `normalize(newY)` to 1e-15 per component **[R4]**. It is set from `newY`, never obtained by rotating `prev.y`.)
  Zero or non-finite `newY` returns `prev` (the same object). Antiparallel `newY` must still return a valid,
  finite frame (the half-turn axis rule of `shortestArc`).
- `Frame` has no hidden state and does not normalize in its constructor.

## 5. `Pose`

```java
public record Pose(Vec3 translation, Quat rotation, double scale) {
    public static final Pose IDENTITY;
    public static Pose ofTranslation(Vec3 t);              // identity rotation, scale 1
    public Vec3 toWorld(Vec3 local);
    public Vec3 toLocal(Vec3 world);
    public Vec3 directionToWorld(Vec3 direction);
    public Pose then(Pose child);
    public Pose inverse();
}
```
- **Compact constructor:** `translation` must be finite; `scale` finite and `> 0`; `rotation` finite and
  non-zero. Violations throw `IllegalArgumentException`. The stored rotation is `rotation.normalized()`.
- `toWorld(p) = translation + scale * rotation.rotate(p)`.
- `toLocal(w) = rotation.conjugate().rotate(w - translation) / scale`.
- `directionToWorld(d) = rotation.rotate(d)` (no scale, no translation).
- **`then(child)`** is composition with this as the parent: `a.then(b).toWorld(p) == a.toWorld(b.toWorld(p))`.
  `t = t_a + s_a * R_a.rotate(t_b)`, `R = R_a.mul(R_b)`, `s = s_a * s_b`.
- **`inverse()`**: `R' = R.conjugate()`, `s' = 1/s`, `t' = -s' * R'.rotate(t)`.
- **[R2] Representability check (Tier 3).** `then` and `inverse` compute `t`, `R`, `s` as above and then **check**
  before constructing the result: every component of `t` is finite, and `s` is finite and strictly greater than 0.
  **[R4]** The scale is checked first, so the exception names the scale whenever the scale is not representable,
  including when that also made `t` non-finite (`inverse` of a subnormal scale). The set of inputs that throw is
  unchanged.
  If either check fails they throw **`ArithmeticException`** and return nothing (poses are immutable, so nothing
  is half-updated). They must not clamp, saturate, substitute a default, or reach the `Pose` constructor with an
  invalid value (which would raise the wrong exception type). Consequences, all pinned by `NumericPolicyTest`:
  - scale product overflowing to infinity (`1e200 * 1e200`) throws;
  - scale product underflowing to exactly zero (`1e-200 * 1e-200`) throws;
  - a **positive subnormal** scale is representable and **is accepted** (`1e-160 * 1e-160 = 1e-320`; also
    `1 / Double.MAX_VALUE`); it is not rejected and not flushed to zero;
  - a composed or inverse translation that overflows throws;
  - `inverse()` of a subnormal scale such as `1e-310` throws (`1/s` overflows).
  The rotation product of two unit rotations is always valid, so rotation never causes a failure.
- **[R2]** `toWorld`, `toLocal` and `directionToWorld` are Tier 1: plain IEEE, they may return infinity for extreme
  operands (for example `scale = 1e200` applied to a point with component `1e200`), never throw, never clamp.
- Only **uniform** scale. Non-uniform sizing belongs on render primitives in a later milestone.

## 6. Numerical accuracy summary (what the tests demand)

| Operation | Accuracy required by the tests |
|---|---|
| `axisAngle(...).rotate(v)` vs Python Rodrigues oracle | 1e-12 absolute (vector components up to about 17) |
| `qa.mul(qb).rotate(v)` vs oracle | 1e-12 |
| `Pose.then(...).toWorld(p)`, ordinary values | 1e-11 |
| Pose with translation near 3e7 | 1e-7 composed, 1e-6 for `toLocal` |
| `slerp` action vs oracle | 1e-11 |
| `Frame` factories | orthonormal and right-handed to 1e-12 |
| `angleTo`, `slerp` angle from `a` | 1e-12 (small angles), 1e-9 (general) |
| 1,000 compositions without renormalization | norm drift below 1e-12 (measured, see drift test) |
| **[R2, R3]** Tier 1 operations with every operand inside the supported-domain table of section 0A | finite; absolute error at most 1e-13 times the operand magnitudes involved, **[R4]** except `Quat.rotate`, whose bound for a quaternion of norm `k` is `(2 abs(k^2 - 1) + 1e-13) abs(v)` (section 3) (see `NumericPolicyTest`, `PolicyClarificationTest`) |
| **[R2, R3]** Tier 2 operations for any `double` operand value | valid, unit (or valid frame) to 1e-12; own fallbacks exactly as specified; caller-supplied fallback returned unchanged |

## 7. Out of scope for M1

Curves, sampling, time or clocks, easing, motion, graphs, render primitives, circle factories, JOML in
production, any Paper adapter. Do not add them. Do not add public API beyond this contract; helper methods
must be package-private or private.
