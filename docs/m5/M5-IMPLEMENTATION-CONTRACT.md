# M5 Implementation Contract: `workshop.vfx.scene` (effect composition and animation)

Audience: the engineer implementing the production source (Codex).

Status: **PROPOSED, revision 1. Not authorized for implementation.** The owner approves this contract with DM5-1 to
DM5-12 (`M5-DESIGN-DECISIONS.md`) after ChatGPT's independent review. Until then nothing in
`src/main/java/workshop/vfx/scene` may be written.

**Baseline:**
- the accepted M4 source snapshot `e3bf5578c126a7d3498be0b30fb431d9ec9a3099`, which has 313/313 tests (M1 125, M2 66,
  M3 51, M3 review 12, M4 59);
- the kickoff commit `6ada39e3a383f6b2df60e6744a39d37d9f24eb13` (`docs/m5/M5-DESIGN-KICKOFF.md`).

**Nothing in M1 to M4 changes:** no production class, test, reference data, contract or verification record.

The tests in `src/test/java/workshop/vfx/scene` are the executable form of this contract. Where the two disagree, stop
and report; do not change either silently.

**Requirement words:**
- **Normative** requirements must hold exactly as stated. Where an operation order is given, it must be followed
  literally: results are then bit-identical across conforming implementations and JVMs (section 10).
- **Validated** statements are acceptance criteria measured against independent oracles. They are not proofs
  (section 10.3).

## 1. Architecture and dependencies

```
caller (later: Paper adapter, gameplay plugin)                       M5 workshop.vfx.scene (this contract)
  anchor Pose snapshot, M2 Playback, M2 TimePoint  --------------->  EffectScene.evaluate(anchor, playback, now)
                                                                         |  M2: Playback.localTicks, Timing.sample
                                                                         |  M3: Easing.apply
                                                                         |  M1: Pose.then, Pose.toWorld
  translate batches to particles  <---------------------------------  EffectFrame -> FrameBatch (style key, packed x,y,z)
build time (once per shape): M3 Curve -> M4 Sampler/ArcLengthTable -> SampleSet -> Shape.of
```

- Package `workshop.vfx.scene`. It may use `workshop.vfx.math.core`, `.time`, `.curve`, `.sample` and `java.util`
  (not `java.util.concurrent`).
- It must not use Bukkit, Paper, Minecraft or any plugin API; I/O; networking; reflection; clocks; threads;
  synchronization; or randomness.
- Java 25, with no dependencies. All public types are immutable, and results are pure functions of the arguments.
- M5 never reads a clock, schedules work, emits particles or keeps state between calls. The caller owns time,
  scheduling, cancellation and rendering.

## 2. Public API (complete)

```java
package workshop.vfx.scene;

public record StyleKey(String value) {                       // "namespace:path"
    public static StyleKey of(String value);
}

public final class Shape {                                   // immutable local points (packed copy)
    public static final int MAX_POINTS = 1 << 20;
    public static Shape point(Vec3 point);
    public static Shape points(List<Vec3> points);
    public static Shape of(SampleSet samples);
    public int size();
    public Vec3 point(int index);
}

public final class Motion {                                  // time-dependent local-to-parent pose
    public static final int MAX_STEPS = 16;
    public static final double MAX_TURNS = 1 << 20;          // 1,048,576
    public static final double MIN_SCALE = 1e-9, MAX_SCALE = 1e9;
    public static final Motion IDENTITY;                     // zero steps
    public static Motion fixed(Pose pose);
    public static Motion spin(Vec3 axis, double turns, double phaseTurns, Timing drive, Easing easing);
    public static Motion scale(double from, double to, Timing drive, Easing easing);
    public static Motion translate(Vec3 from, Vec3 to, Timing drive, Easing easing);
    public Motion then(Motion child);
    public int steps();
    public Pose poseAt(double localTicks);
}

public enum Visibility { ACTIVE_ONLY, HOLD_AFTER }
public enum Reveal { ALL, PREFIX }

public record Layer(String name, Timing timing, Visibility visibility, Reveal reveal,
                    Motion motion, Shape shape, StyleKey style) { }

public record SceneLimits(int maxLayers, int maxPointsPerFrame) {
    public static final int MAX_LAYERS_CEILING = 4096;
    public static final int MAX_POINTS_CEILING = 1 << 20;
}

public final class EffectScene {
    public static final double UNBOUNDED = Double.POSITIVE_INFINITY;
    public static EffectScene of(TimeDomain domain, double lifetimeTicks, List<Layer> layers, SceneLimits limits);
    public TimeDomain domain();
    public double lifetimeTicks();
    public List<Layer> layers();                             // unmodifiable copy
    public SceneLimits limits();
    public int maxPointsPerFrame();                          // sum of shape sizes (preflight)
    public EffectFrame evaluate(Pose anchor, Playback playback, TimePoint now);
}

public final class EffectFrame {
    public TimingSample.Stage stage();                       // BEFORE, ACTIVE, AFTER (scene window)
    public double localTicks();
    public List<FrameBatch> batches();                       // unmodifiable, layer order
    public int pointCount();
    public boolean isEmpty();
}

public final class FrameBatch {
    public int layerIndex();
    public String layer();
    public StyleKey style();
    public TimingSample timing();                            // the layer's M2 sample
    public Pose pose();                                      // anchor.then(motion.poseAt(local))
    public int size();
    public double x(int i);
    public double y(int i);
    public double z(int i);
    public Vec3 point(int i);
    public double[] coordinates();                           // new copy: x0, y0, z0, x1, ...
}
```

**Shape rules:**
- There are no other public types or members, apart from what records and enums generate. `toString` may be
  overridden.
- `Shape`, `Motion`, `EffectScene`, `EffectFrame` and `FrameBatch` are final, with no public or protected
  constructors.
- One public type per file. Package-private helpers are allowed.
- Reused M1 to M4 types: `Vec3`, `Pose` (M1); `TimeDomain`, `TimePoint`, `Playback`, `Timing`, `TimingSample` (M2);
  `Easing` (M3); `SampleSet` (M4).

## 3. Common rules

- **Null:** any null argument, or null element of a list argument, gives `IllegalArgumentException`.
- **Validation is complete before allocation:** an invalid argument never produces a partially built object.
- **Exceptions:**
  - invalid input gives `IllegalArgumentException`;
  - a non-finite or unrepresentable intermediate or result during `evaluate` gives `ArithmeticException`;
  - M1 and M2 exceptions propagate unchanged.

  `evaluate` either returns a complete frame or throws. It never returns a partial frame.
- **Identifiers** (ASCII only):
  - a layer name matches `[a-z0-9_.-]{1,64}`;
  - a style key matches `[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}`: exactly one colon, the slash allowed only in the path.

## 4. `Shape`

- `points(list)`: at most `MAX_POINTS` elements, each nonnull with finite components; otherwise
  `IllegalArgumentException`. The coordinates are **copied** into a packed array in list order. Later changes to the
  list do not affect the shape.
- `point(p)` is `points(List.of(p))`.
- `of(samples)` is `points(samples.points())`. It calls no curve: the M4 `SampleSet` already holds the points.
- An empty shape (0 points) is valid.
- `point(i)` returns `new Vec3(x_i, y_i, z_i)`. An index outside `[0, size())` gives `IndexOutOfBoundsException`.

## 5. `Motion`

A motion is an ordered list of at most `MAX_STEPS = 16` steps. It maps layer-local coordinates to the anchor's
coordinates. `IDENTITY` has no steps.

### 5.1 Construction (validation)

| Factory | Requirements |
|---|---|
| `fixed(pose)` | nonnull pose (M1 already guarantees finite translation, nonzero rotation and positive finite scale) |
| `spin(axis, turns, phaseTurns, drive, easing)` | `axis` finite with `axis.normalizedOr(null) != null` (M1: largest absolute component at least `1e-12`); the unit axis `u = axis.normalizedOr(null)` is stored; `turns` finite with `abs(turns) <= MAX_TURNS`; `phaseTurns` finite; drive and easing nonnull |
| `scale(from, to, drive, easing)` | `MIN_SCALE <= from <= MAX_SCALE`, the same for `to` (NaN fails); drive and easing nonnull |
| `translate(from, to, drive, easing)` | nonnull, finite components; drive and easing nonnull |
| `m.then(child)` | nonnull; `m.steps() + child.steps() <= MAX_STEPS`. Result: the steps of m followed by those of child |

### 5.2 Evaluation `poseAt(L)` (normative)

- L must be finite, else `IllegalArgumentException`.
- For each animated step, the eased progress is `e = easing.apply(drive.sample(L).progress())`, using M2 and M3
  unchanged. The drive's stage does not matter: BEFORE gives progress 0, AFTER gives M2's end progress (1, or 0 after
  an even number of ping-pong passes), so a finished drive holds its final pose.
- Step poses, in this exact form:
  - **fixed:** the given pose;
  - **scale:** `new Pose(Vec3.ZERO, Quat.IDENTITY, (1 - e) * from + e * to)`;
  - **translate:** `Pose.ofTranslation(from.lerp(to, e))` (M1 `Vec3.lerp`: `from.mul(1 - e).add(to.mul(e))`);
  - **spin:** with `frac(x) = x % 1.0`, plus 1.0 if negative, and `1.0` mapped to `0.0`:
    1. `f = frac(frac(turns * e) + frac(phaseTurns))`, so `f` lies in `[0, 1)`;
    2. `quarters = 2 * f` (the half angle `pi f` in quarter turns), `q = (int) Math.floor(quarters)` (0 or 1),
       `x = (quarters - q) * (Math.PI / 2)`;
    3. `c = StrictMath.cos(x)`, `s = StrictMath.sin(x)`; then `(cw, sw) = (c, s)` if `q == 0`, else `(-s, c)`;
    4. the pose is `new Pose(Vec3.ZERO, new Quat(cw, sw * u.x(), sw * u.y(), sw * u.z()), 1)`. The M1 constructor
       normalizes the quaternion.

    This is the right-handed rotation by `2 pi (phaseTurns + turns e)` about u. A positive quarter turn about +Y
    takes +X to -Z, the M3 `Spiral` direction. Reducing whole turns changes the sign of the quaternion, not the
    rotation. `M1 Quat.rotate` gives bit-identical results for q and -q (DM5-11).
- **Composition:** `poseAt(L) = P_0.then(P_1).then(...).then(P_last)`, folded left to right with M1 `Pose.then`, so
  the last step acts first on a point. With no steps, the result is `Pose.IDENTITY`. M1 exceptions
  (`ArithmeticException` for an unrepresentable scale or translation) propagate.

## 6. `Layer` and `EffectScene` construction

- `Layer`: every component nonnull; name as in section 3. One `Motion`, `Shape` or `Timing` value may be shared by
  any number of layers and scenes.
- `EffectScene.of(domain, lifetimeTicks, layers, limits)`:
  - domain, layers and limits nonnull, with no null element;
  - `lifetimeTicks > 0`: finite, or `UNBOUNDED` (+infinity). NaN, zeros and negative values fail;
  - `layers.size() <= limits.maxLayers()`;
  - layer names unique (exact string comparison);
  - **preflight:** `sum of layer.shape().size()`, computed in `long`, must be at most `limits.maxPointsPerFrame()`.
    The sum is stored as `maxPointsPerFrame()`.

  The list is copied, and `layers()` is unmodifiable. An empty layer list is valid.
- `SceneLimits`: `1 <= maxLayers <= 4096` and `1 <= maxPointsPerFrame <= 2^20`.

## 7. `evaluate(anchor, playback, now)`: time

In this exact order:
1. anchor, playback and now nonnull, else `IllegalArgumentException`.
2. If `playback.anchor().domain() != domain()` or `now.domain() != domain()`, `IllegalArgumentException`. A GAME scene
   is never driven by a COSMETIC clock, or the reverse.
3. `L = playback.localTicks(now)` (M2, unchanged). M2 exceptions propagate; for example, an overflowing GAME tick
   difference gives `ArithmeticException`. If L is not finite, `ArithmeticException`. If `L == 0`, then `L = +0.0`.
4. **Scene window:**
   - `L < 0` gives a frame with stage BEFORE and no batches;
   - `L >= lifetimeTicks` gives stage AFTER and no batches (ended);
   - otherwise the stage is ACTIVE and section 8 applies.

   `localTicks()` reports L in every case.

Pause, resume, seek, restart, rate changes (including zero and negative rates) and the COSMETIC wrap are entirely
M2's `Playback`. M5 adds no time arithmetic beyond the comparisons of step 4.

## 8. `evaluate`: frame content (normative)

For each layer i in list order:
1. `s = layer.timing().sample(L)` (M2).
2. **Visibility:** the layer is visible iff `s.stage() == ACTIVE`, or `s.stage() == AFTER` and
   `visibility == HOLD_AFTER`. BEFORE is never visible. If it is not visible, skip the layer.
3. **Count:** with `n = shape.size()`:
   - `k = n` for `Reveal.ALL`;
   - `k = (int) Math.floor(s.progress() * n)` for `Reveal.PREFIX`.

     Since M2 progress lies in `[0, 1]`:
     - this k is 0 for an empty shape;
     - it is exactly n at progress 1;
     - it is at most `n - 1` for every progress below 1. The largest such progress is `1 - 2^-53`, and
       `(1 - 2^-53) n` lies more than half an ulp below n for every `n <= 2^53`, so the rounded product stays below
       n. Rounding is monotone, so this covers every smaller progress. It was checked exhaustively for all n up to
       2^20.

     The R1 draft's extra branches (`n == 0`, `progress >= 1`) and its `min(n - 1, ...)` clamp were redundant:
     mutants S15 and S31 were equivalent. They have been removed.

   If `k == 0`, skip the layer. No empty batch is ever emitted. Reveal uses the raw M2 progress; easing applies only
   to motions.
4. **Pose:** `P = anchor.then(layer.motion().poseAt(L))` (M1). M1 exceptions propagate.
5. **Points:** for j = 0 to k - 1, `w = P.toWorld(new Vec3(x_j, y_j, z_j))` (M1). If any component of w is not finite,
   `ArithmeticException`. Store `w.x(), w.y(), w.z()` at positions `3j`, `3j + 1`, `3j + 2`.
6. Append the batch `(i, layer.name(), layer.style(), s, P, coordinates)`.

The frame's `pointCount()` is the sum of the batch sizes. Batches appear in increasing `layerIndex`, and points in
shape order: the first k points of the shape.

**Consequences, checked by the tests:**
- `pointCount() <= maxPointsPerFrame()`.
- A frame never contains a hidden layer, an empty batch, or a point outside the scene window.
- With the identity anchor and no motion, coordinates are returned exactly.
- `FrameBatch.pose().toWorld(shape.point(j))` equals `point(j)` bit for bit.

## 9. Limits, work and memory (normative)

| Quantity | Bound |
|---|---|
| layers per scene | `limits.maxLayers() <= 4096` |
| points per frame | `maxPointsPerFrame() <= limits.maxPointsPerFrame() <= 2^20`, checked at construction |
| motion steps per layer | 16 |
| M2 `Timing.sample` calls per `evaluate` | at most `layers * 17` (one per layer, plus one per animated step) |
| M1 `Pose.then` calls per `evaluate` | at most `layers * 16` |
| M1 `Pose.toWorld` calls per `evaluate` | `pointCount()` |
| curve evaluations per `evaluate` | **0**: shapes are built once, outside evaluation |
| allocation per `evaluate` | one `double[3k]` per emitted batch, plus O(layers * steps) small objects; the output is never larger than `3 * 2^20` doubles (24 MiB) |

- Every loop has a fixed bound. No count can overflow: sums are in `long`, and `3 * 2^20` fits in `int`.
- Over-budget scenes are rejected at construction, atomically. There is no silent truncation and no thinning; budget
  thinning is a later server-policy milestone (P2).

## 10. Numerical policy

### 10.1 Determinism (normative)

- Results depend only on the arguments.
- The operations used are `+ - * /`, `%`, `Math.floor`, `Math.min`, `Math.max`, `StrictMath.sin` and
  `StrictMath.cos`, comparisons, and the M1, M2 and M3 calls named above. All of these are exactly specified in Java.
  `Math.sin` and `Math.cos`, `pow`, `exp`, `log`, `hypot`, `fma` and randomness are forbidden: `Math.sin` may differ
  by 1 ulp between JVMs (M3 conventions F4).
- Sections 5.2, 7 and 8 fix every operation and its order. Conforming implementations therefore give bit-identical
  frames on every Java 25 JVM.
- Scenes and frames are immutable and may be shared between threads. M5 has no mutable static state.

### 10.2 Signed zero and special values

- Local ticks of -0.0 are reported as +0.0 and treated as the scene start.
- **The sign of a zero coordinate in a frame is not significant.** It is deterministic (10.1), but M1 arithmetic
  normalizes some zeros (for example `-0.0 + 0.0 = +0.0`). Adapters must not depend on it.
- NaN and infinities are rejected in every authored input: shapes, motions, poses (M1), timings (M2), lifetime
  (except `UNBOUNDED`), limits.

### 10.3 Accuracy (validated, not normative)

- M5 is exact in its decisions:
  - visibility, scene window and reveal counts are computed from M2 values by the stated comparisons;
  - for integer ticks and durations, M2 progress is the exactly rounded quotient.
- **Positions** carry the rounding of M1 (`Pose.then`, `Pose.toWorld`, quaternion normalization and rotation) and of
  the spin angle. Combining the M1 error model (a few eps per operation, at most 17 compositions) with the angle
  error of the turn reduction (at most `2 pi eps (1 + abs(turns))` radians) gives a derived bound below
  `1e-14 (1 + M) (1 + abs(turns))`. Here M is the magnitude of the coordinates involved.
- The acceptance tolerance used by the tests is `2^-40 (1 + M)(1 + abs(turns))`. It is about 100 times the derived
  bound, and is checked against independent oracles (`M5-TEST-SPECIFICATION.md`).
- Mathematical ground truth (a 40-digit oracle) is distinct from the renderer's approximation. Client particle
  positions are quantized and jittered by Minecraft, which is outside M5.

### 10.4 Domains

| Item | Supported | Note |
|---|---|---|
| anchor translation | finite, M1 | world-border coordinates (3e7) and beyond are exact inputs; precision is eps times the magnitude |
| anchor and step scales | positive finite (M1); step scales in `[1e-9, 1e9]` | products that overflow give `ArithmeticException` (M1) |
| local ticks | any finite value from M2 | see the M2 notes in `M5-CONVENTIONS.md` for `FOREVER` loops at astronomically large ticks |
| spin turns per pass | `abs(turns) <= 2^20` | angle precision degrades linearly with `abs(turns)` |
| points per frame | at most 2^20 | |

## 11. Out of scope

Deferred (see `M5-ARCHITECTURE.md` section 6):
- the Paper/Bukkit/Folia adapter, viewers, schedulers and emission;
- gameplay;
- live world queries;
- a runtime effect manager;
- quality presets and runtime budgets (P2);
- randomness and jitter;
- surface and volume distributions;
- color, opacity, size and other particle data;
- sound and light;
- per-frame resampling of curves;
- curve-following motion;
- trails with state;
- any change to M1 to M4.
