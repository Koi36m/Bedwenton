# M2 Implementation Contract: `workshop.vfx.math.time` (deterministic animation time)

Audience: the engineer implementing the production source (Codex).
Status: **PROPOSED, revision 1. Not authorized for implementation** until the owner approves it together with the
decisions in `M2-DESIGN-DECISIONS.md` (DM2-1 to DM2-9). The tests in `src/test/java/workshop/vfx/math/time` are the
executable form of this contract. Where this document and a test disagree, stop and report the disagreement; do
not change either silently.

Baseline: M1 Revision 4, accepted (`74710d338eb8fcb48a64763fa277e1c7be475bba`, evidence
`9e3f3acde0db4b9d0a202ba4f7f396c132843597`). **Nothing in M1 changes**: no file under
`src/main/java/workshop/vfx/math/core`, `src/test/java/workshop/vfx/math/core`, `src/test/resources/golden`,
`docs/*.md` (M1 documents) or `tools/generate_golden_vectors.py` is modified. M2 does not depend on `core`.

Java 25. No dependencies. Package `workshop.vfx.math.time`. All types are immutable. Numbers are `double` (local
time, progress) and `long` (instants, cycle indices).

## 1. Architecture

Three layers, each a pure function of explicit inputs:

```
caller (adapter, later milestone)          workshop.vfx.math.time                        later milestones
---------------------------------          ----------------------                        ----------------
server tick counter ----------> TimePoint(GAME, tick) ------+
System.nanoTime() ------------> TimePoint(COSMETIC, ns) ----+--> Playback.localTicks --> Timing.sample --> TimingSample
                                                                (pause, rate, seek)      (delay, cycles,   (stage, cycle,
                                                                                         ONCE/LOOP/PING)   progress in [0,1])
                                                                                                            |
                                                                       M3 curves (progress) <---------------+
                                                                       M4 sampling and motion (localTicks, stage)
```

1. **Instants** (`TimePoint`): exact `long` values tagged with their clock domain. The math never reads a clock. The
   caller reads the server tick counter or `System.nanoTime()` and passes the value in.
2. **Playback** (`Playback`): an immutable affine map from an instant to *local animation ticks*. Pause, resume, rate
   (speed and reversal), seek and restart are value transitions at explicit instants.
3. **Timing** (`Timing`): an immutable definition (delay, duration, repeat mode, cycle count) that turns local ticks
   into a `TimingSample` (stage, cycle index, progress in [0, 1]).

There is no mutable state anywhere. A "running animation" is a `Playback` value held by its owner, replaced on
each control event, and evaluated as often as needed.

## 2. Units and constants: `TickTime`

```java
public final class TickTime {
    public static final long TICKS_PER_SECOND = 20L;
    public static final long NANOS_PER_TICK = 50_000_000L;
    public static double ticksToSeconds(double ticks);   // exactly: ticks / 20.0
    public static double secondsToTicks(double seconds); // exactly: seconds * 20.0
}
```

- Local animation time is measured in **ticks** (DM2-1). In the GAME domain one local tick is one server tick, at
  the nominal 20 per second. In the COSMETIC domain one local tick is 50 ms of supplied wall time.
- `ticksToSeconds` **must** divide by `20.0`. It must not multiply by `0.05`: 0.05 is not representable, and
  `3 * 0.05 = 0.15000000000000002` whereas `3 / 20.0` is the correctly rounded 0.15. Division gives the correctly
  rounded quotient for every input.
- `secondsToTicks(ticksToSeconds(t)) == t` held in every sample below 2^52 but **fails** for some `t` in
  [2^52, 2^53], for example `t = 9007199254540993`. Authoritative tick values must therefore never be derived
  from seconds.
- Both are Tier 1 (raw IEEE): NaN and infinities propagate, overflow gives infinity, nothing throws.
- Private constructor; no instances.

## 3. Time domains and instants: `TimeDomain`, `TimePoint`

```java
public enum TimeDomain { GAME, COSMETIC }

public record TimePoint(TimeDomain domain, long value) {
    public static TimePoint gameTick(long tick);       // GAME: value is a server tick count
    public static TimePoint cosmeticNanos(long nanos); // COSMETIC: value is nanoseconds from a monotonic source
    public double ticksSince(TimePoint origin);
}
```

- **GAME** is gameplay-authoritative. Its value is a server tick counter: identical on every evaluation,
  reproducible, and slowed by low TPS together with the game. **COSMETIC** is display-only. Its value is
  nanoseconds from a monotonic source the caller owns (normally `System.nanoTime()`), and it keeps real-time speed
  under low TPS. Choosing between them is the caller's decision (guidance in `M2-CONVENTIONS.md` section 2).
- Compact constructor: `domain` must not be null, else `IllegalArgumentException`. Any `long` value is valid.
- `ticksSince(origin)`, exactly:
  1. `origin` null, or `origin.domain() != domain`: `IllegalArgumentException`.
  2. `long delta = Math.subtractExact(value, origin.value())`. Overflow is an `ArithmeticException`.
  3. GAME: return `(double) delta`. COSMETIC: return `(double) delta / 50_000_000.0`.
- Consequences (tested): GAME differences are exact for `|delta| <= 2^53`. COSMETIC differences are the correctly
  rounded quotient for `|delta| <= 2^53` ns (104 days); beyond that, `delta` rounds once before the division.

## 4. Playback: `Playback`

```java
public record Playback(TimePoint anchor, double anchorTicks, double rate, boolean paused) {
    public static Playback start(TimePoint at);                                   // (at, 0.0, 1.0, false)
    public static Playback start(TimePoint at, double initialTicks, double rate); // (at, initialTicks, rate, false)
    public double localTicks(TimePoint t);
    public Playback pause(TimePoint at);
    public Playback resume(TimePoint at);
    public Playback withRate(TimePoint at, double rate);
    public Playback seek(TimePoint at, double ticks);
    public Playback restart(TimePoint at);
}
```

**Invariants (compact constructor, `IllegalArgumentException`):** `anchor` not null; `anchorTicks` finite; `rate`
finite. Any finite rate is valid. A negative rate plays in reverse, and `abs(rate)` is the speed multiplier. Rate 0
holds the value but is not "paused".

**Evaluation, exactly:**

```
localTicks(t):
    if t is null or t.domain() != anchor.domain(): IllegalArgumentException
    if paused: return anchorTicks                        // no instant arithmetic, so no overflow while paused
    return anchorTicks + rate * t.ticksSince(anchor)     // two roundings; no Math.fma
```

`t` may precede the anchor. The result is then the affine extrapolation, or the held value when paused. **History
before the anchor is not kept** (DM2-5). `localTicks` is Tier 1: the result may be infinite for extreme rates, and
it never throws except as stated.

**Control operations.** Every operation takes the instant `at` at which it happens and returns a new `Playback`.
Common validation first, in this order:
1. `at` null, or `at.domain() != anchor.domain()`: `IllegalArgumentException`.
2. `at.value() < anchor.value()`: `IllegalArgumentException` (control events never go back in time; `at` equal to
   the anchor is allowed).

| Operation | Result `(anchor, anchorTicks, rate, paused)` | Notes |
|---|---|---|
| `pause(at)` | already paused: `this` (same object); else `(at, L(at), rate, true)` | holds `L(at)` exactly |
| `resume(at)` | not paused: `this` (same object); else `(at, anchorTicks, rate, false)` | continues from the held value bit for bit; a paused span adds exactly 0 |
| `withRate(at, r)` | `(at, L(at), r, paused)` | `r` not finite: `IllegalArgumentException` (checked before `L(at)`); continuous at `at`; a paused playback stays paused and uses `r` after `resume` |
| `seek(at, x)` | `(at, x, rate, paused)` | `x` not finite: `IllegalArgumentException`; does not evaluate `L` |
| `restart(at)` | `(at, 0.0, rate, false)` | plays from 0 at the current rate, even if paused before |

Here `L(at)` means `localTicks(at)`. Where an operation re-anchors at `L(at)` (pause, withRate), a non-finite
`L(at)` is an `ArithmeticException` (a derived value that is not representable, as for M1's `Pose`). An overflowing
instant difference inside `L(at)` is also an `ArithmeticException`.

## 5. Timing: `RepeatMode`, `Timing`, `TimingSample`

```java
public enum RepeatMode { ONCE, LOOP, PING_PONG }

public record Timing(double delayTicks, double durationTicks, RepeatMode mode, long cycles) {
    public static final long FOREVER = Long.MAX_VALUE;
    public static Timing once(double durationTicks);     // (0.0, d, ONCE, 1)
    public static Timing loop(double durationTicks);     // (0.0, d, LOOP, FOREVER)
    public static Timing pingPong(double durationTicks); // (0.0, d, PING_PONG, FOREVER)
    public Timing withDelay(double delayTicks);
    public Timing withCycles(long cycles);
    public TimingSample sample(double localTicks);
}

public record TimingSample(Stage stage, long cycle, double progress) {
    public enum Stage { BEFORE, ACTIVE, AFTER }
}
```

**`Timing` invariants (`IllegalArgumentException`):** `mode` not null; `delayTicks` finite (it may be negative: the
animation starts part-way through); `durationTicks` finite and `> 0` (so `-0.0` and `0.0` are rejected);
`cycles >= 1`; and `mode == ONCE` requires `cycles == 1`. `FOREVER` means "more cycles than will ever be sampled".
`withDelay` and `withCycles` return new values and validate the same way.

**`TimingSample` invariants (`IllegalArgumentException`):** `stage` not null; `cycle >= 0`; `progress` in [0, 1]
(NaN rejected).

**`sample(localTicks)`, exactly** (`d = durationTicks`):

```
if localTicks is not finite: IllegalArgumentException
u = localTicks - delayTicks
if u is not finite: ArithmeticException                              // only for |values| near Double.MAX_VALUE
if u == 0.0: u = 0.0                                                 // normalizes -0.0 to +0.0
if u < 0.0: return (BEFORE, 0, 0.0)
r = u % d                                                            // Java %: the exact remainder, 0 <= r < d
n = Math.rint((u - r) / d)                                           // the cycle index, as a double
if n >= (double) cycles:                                             // AFTER: decided on the cycle index
    end = (mode == PING_PONG && cycles is even) ? 0.0 : 1.0
    return (AFTER, cycles - 1, end)
k = (long) n
progress = (mode == PING_PONG && k is odd) ? (d - r) / d : r / d
return (ACTIVE, k, progress)
```

Conventions this fixes (DM2-6):
- Cycles are half-open: `[k d, (k+1) d)`. LOOP restarts at progress 0 exactly at `k d`.
- A finite run ends in `AFTER` at `u >= cycles * d`, with `cycle = cycles - 1` and the end value of the last pass.
  For ONCE and LOOP that is 1. For PING_PONG it is 1 after an odd number of passes and 0 after an even number.
- PING_PONG passes alternate direction: forward on even `k`, backward on odd `k`. The value is continuous at every
  turn.
- `BEFORE` reports cycle 0 and progress 0. Start-of-animation values are therefore well defined while waiting out a
  delay or after reversing past the start.
- **[fact]** For `0 <= r < d`, forward progress `r / d` is **strictly below 1**. The largest possible quotient,
  `nextDown(d) / d`, is at most `1 - 2^-53`, below the rounding midpoint `1 - 2^-54`. Only `AFTER` and backward
  passes report exactly 1.

## 6. Numerical policy

### 6.1 Determinism

- Evaluation is a pure function of its arguments. No clock reads (`System.nanoTime`, `System.currentTimeMillis`,
  `java.time`), no randomness, no threads, no mutable static state (`M2BoundaryTest`).
- **[fact]** The operations used (`+ - * /`, `%`, `Math.rint`, `Math.subtractExact`, long-to-double and
  double-to-long conversion) are exactly specified by IEEE-754 and the Java Language Specification. All Java
  floating-point arithmetic has been strict since Java 17 (JEP 306). Implemented in the order of sections 3 to 5,
  the results are **bit-identical on every conforming JVM** and reproducible in any IEEE-754 language (the Python
  generator reproduces them).
- Therefore: do not reorder, factor or "simplify" the formulas. Do not use `Math.fma`, and no transcendental
  function is needed. Golden vectors are compared bit for bit.

### 6.2 Path independence

**[fact]** `localTicks(t)` depends only on the current `Playback` and `t`. `sample(x)` depends only on the `Timing`
and `x`. Skipped frames, irregular tick spacing, out-of-order evaluation and low TPS therefore cannot accumulate
error, and evaluation at an instant gives the same bits whatever was evaluated before
(`M2DeterminismTest.pathIndependence`). Only control events change state, and each rounds at most twice.

### 6.3 Exactness theorem for integer gameplay time

**Theorem.** Let a GAME `Playback` have integer `anchorTicks = A` and integer `rate = k`, and let `t` be a tick
instant with `|A| + |k| * |t - anchor| <= 2^53`. Then `localTicks(t) = A + k (t - anchor)` exactly. Let a `Timing`
have integer `delayTicks = D` and integer `durationTicks = d >= 1`, and let `x = localTicks(t)` with
`|x - D| <= 2^53` and `(x - D) / d < 2^51`. Then:
- `stage` and `cycle` equal the results of `Math.floorDiv(x - D, d)` compared against `cycles`;
- `progress` is the correctly rounded value of `rem / d` (forward) or `(d - rem) / d` (backward), where
  `rem = Math.floorMod(x - D, d)`.

*Proof sketch.* Every intermediate is an integer of magnitude at most 2^53, hence representable. `+`, `-` and `*` of
representable integers with a representable result are exact. `%` is exact. `(u - r) / d` is an exact integer
quotient, so `rint` returns it. The single final division rounds correctly. `pause`, `resume`, `restart`, `seek`
with an integer target, and `withRate` with an integer rate all keep `anchorTicks` an integer. ∎

Consequence: **every gameplay-relevant decision is exact** when instants, offsets, delays, durations, seek targets
and rates are integers: has it started, which cycle, is it finished, and which pass direction. Fractional rates
(for example 0.37) leave this regime visibly. Dyadic rates (0.5, 0.25) stay exact while the values fit in 53 bits
(`playback.csv`, script `game-dyadic-rate`). Checked by `TimingTest.integerExactness` against integer arithmetic,
and by every integer row of the generator against exact fractions.

### 6.4 Precision, long runs and drift

| Quantity | Guaranteed domain | Guarantee |
|---|---|---|
| `TimePoint.ticksSince` | `abs(delta) <= 2^53` | GAME exact; COSMETIC correctly rounded |
| `Playback.localTicks` | `abs(anchorTicks)` and `abs(rate * ticksSince(anchor))` at most 2^1022 | finite; at most 2 roundings; exact under 6.3 |
| control events | as above | each re-anchoring adds at most `1.5 * 2^-52 * max abs(local)`; tested with 2x headroom over 10,000 events |
| `Timing.sample` | finite `localTicks`; `abs(localTicks - delayTicks)` finite | always a valid sample, never throws |
| cycle index | `(localTicks - delayTicks) / durationTicks < 2^51` | exact floor of the once-rounded elapsed time over the duration |
| progress | as above | forward: correctly rounded `r / d` with `r` exact; backward: within 2 ulp |

- **No drift.** Nothing accumulates per frame (6.2). Ten million consecutive ticks from tick 2^40 evaluate to exact
  integers (`longRunningNoDrift`).
- **Phase precision does not depend on elapsed time.** The remainder `u % d` is exact, so the phase is as accurate
  after a year as after a second. The naive `u / d - floor(u / d)` is off by 4.2e-4 at `u = 2^45 + 0.5`, `d = 7`,
  while the specified method is within 1.2e-17 (`phasePrecisionAtLargeTimes`).
- **Ranges.** 2^53 ticks is 14 million years at 20 TPS. A `long` of nanoseconds spans 292 years. One year is
  6.3e8 ticks; at that magnitude `ulp(local)` is 1.2e-7 ticks (6 ns).
- **Integer tick counters.** Adapters must pass a `long`. A 32-bit tick counter wraps after 3.4 years at 20 TPS,
  which is an adapter concern.

### 6.5 Discontinuities

- A GAME counter that restarts (for example on a server restart) produces instants before the anchor. Evaluation
  then returns the extrapolated value (normally `BEFORE` in `Timing`), and control operations throw
  `IllegalArgumentException`. The caller starts a new `Playback` on the new counter.
- A COSMETIC gap (server freeze, GC pause, an asynchronous task that missed frames) shows as a jump forward by the
  real elapsed time, which is the wall-clock truth. To exclude a gap, the caller pauses and resumes, or seeks back
  (DM2-8; `M2DeterminismTest.discontinuities`).
- Low TPS: GAME animations slow down with the game; COSMETIC animations keep real time with fewer frames
  (`M2DeterminismTest.lowTps`).

## 7. Exceptions

| Condition | Exception |
|---|---|
| null argument or null field; non-finite `anchorTicks`, `rate`, seek target, delay or duration; duration `<= 0`; `cycles < 1`; ONCE with `cycles != 1`; mismatched domains; a control instant before the anchor; non-finite `localTicks` passed to `sample`; an invalid `TimingSample` | `IllegalArgumentException` |
| instant difference overflows `long`; non-finite re-anchored value in `pause` or `withRate`; non-finite elapsed time in `sample` | `ArithmeticException` |
| `TickTime` conversions; `localTicks` producing infinity | never throw (raw IEEE) |

Exception messages name the operation and the reason. Only the exception types are tested.

## 8. Public API: complete list

Exactly these public types: `TimeDomain`, `TimePoint`, `TickTime`, `Playback`, `RepeatMode`, `Timing`,
`TimingSample` (with nested `TimingSample.Stage`). One source file each, seven files in
`src/main/java/workshop/vfx/math/time`. No other public members than those listed in sections 2 to 5, apart from
those that records and enums generate. Helpers must be private.

## 9. Out of scope for M2

Easing and curves (M3); sampling and emission scheduling (M4); nested or parented timelines; keyframes, events
and callbacks; automatic gap capping (DM2-8); awareness of a changed tick rate; any Paper, Bukkit or Minecraft
adapter; any change to M1.
