# M2 design decisions: options and recommendations

Status: **PROPOSED, not approved.** Each decision below changes what M2 contains. The contract
(`M2-IMPLEMENTATION-CONTRACT.md`) and the tests are written against the **recommended** option in each case. If the
owner chooses differently, the affected contract sections and tests are named so they can be revised before Codex
starts.

Baseline: M1 Revision 4, accepted (production `74710d338eb8fcb48a64763fa277e1c7be475bba`, evidence
`9e3f3acde0db4b9d0a202ba4f7f396c132843597`). M2 adds a new package and changes nothing in M1.

Notation: **[fact]** marks a mathematical or platform fact that holds regardless of preference (each one is
checked by a test or by the golden-vector generator). **[preference]** marks a design judgement.

## Summary

| ID | Decision | Recommended option | Main alternative | Affects |
|---|---|---|---|---|
| DM2-1 | Unit of animation-local time | **ticks** (double) | seconds | all of M2 |
| DM2-2 | Gameplay exactness | **one double pipeline with a proven integer-exactness theorem** | a second, integer-only API | contract 5, 6.3 |
| DM2-3 | Gameplay vs cosmetic time | **`TimePoint` carries its domain; mixing domains throws** | raw `long` values | contract 3 |
| DM2-4 | Pause, resume, speed, reverse, seek | **one immutable affine `Playback` value** | a mutable clock object | contract 4 |
| DM2-5 | Evaluation before the last control event | **affine extrapolation; no history kept** | an event timeline with full history | contract 4 |
| DM2-6 | Cycle and end conventions | **half-open cycles; the end is `AFTER` with the end value** | closed cycles | contract 5 |
| DM2-7 | How exact the specification is | **operation-by-operation, bitwise golden vectors** | tolerance-only | contract 4-6, tests |
| DM2-8 | Wall-clock gaps (server freezes, GC pauses) | **caller handles them with pause/resume or seek** | a built-in step-capping clock | contract 6.5 |
| DM2-9 | Scope | **instants, playback, timing; defer the rest** | the full candidate list | everything |

## DM2-1. Unit of animation-local time: ticks or seconds

- **Option A (recommended): ticks, as `double`.** One local tick is one game tick (GAME domain), or 50 ms of
  supplied wall time (COSMETIC domain). `TickTime` converts to and from seconds.
- **Option B: seconds, as `double`.**

Reasoning:
- **[fact]** 1/20 is not a binary fraction. Tick-based durations stated in seconds are mostly inexact: 3 ticks is
  0.15 s, stored as 0.1499999999999999944…, and `3 * 0.05` gives `0.15000000000000002`. With seconds, boundary
  decisions such as "is a 3-tick effect over at tick 3?" depend on how each side was rounded. With delays they can
  disagree: `0.2 - 0.05` is not the same double as `0.15`.
- **[fact]** With ticks, every gameplay quantity (tick instants, offsets, durations, delays) is an integer, and
  integers below 2^53 are exact doubles.
- **[preference]** Plugin authors already think in ticks (`runTaskTimer` periods, `Display` interpolation
  durations).
- Cost: COSMETIC callers think in milliseconds. `TickTime.secondsToTicks` covers this.

## DM2-2. Exact gameplay decisions: one pipeline or two

- **Option A (recommended): a single `double` pipeline, plus a proven exactness theorem** (contract 6.3). With
  integer anchors, offsets, delays, durations and integer rates, every local time is an exact integer, every stage
  and cycle decision is exact, and progress is the correctly rounded value of an exact fraction.
- **Option B: a second API on `long` ticks** for gameplay (`TickTiming`, integer `floorDiv`/`floorMod`), next to the
  `double` API for cosmetic use.
- **Option C: fixed-point time** (for example `long` 1/65536 ticks) everywhere.

Reasoning:
- **[fact]** Option A gives the same answers as integer arithmetic whenever the inputs are integers.
  `TimingTest.integerExactness` checks 300,000 random integer configurations against `Math.floorDiv` and
  `Math.floorMod`, and the generator checks every integer row in exact rational arithmetic.
- **[preference]** Option B doubles the API surface and invites mixing the two. Option C cannot represent speed
  multipliers such as 0.37 without its own rounding rules.
- Risk of Option A: exactness is a property of the inputs, so a caller using a rate of 0.37 leaves the exact regime.
  Contract 6.3 states this. Nothing silently converts; the rate is visibly fractional.

## DM2-3. Distinguishing gameplay-authoritative from cosmetic time

- **Option A (recommended):** `TimePoint(TimeDomain domain, long value)`. GAME values are server ticks; COSMETIC
  values are nanoseconds from a monotonic source the caller reads. `ticksSince`, `Playback.localTicks` and every
  control operation reject a different domain with `IllegalArgumentException`.
- **Option B:** raw `long` values, with the domain documented only.
- **Option C:** two unrelated types, `GameTick` and `CosmeticNanos`, with generic playback.

Reasoning:
- **[fact]** Mixing the two is a real failure mode. `System.nanoTime` has an arbitrary origin, so a nanosecond value
  passed where a tick is expected yields local times in the 10^12 range, with no error.
- **[preference]** Option A catches it at the first evaluation, at the cost of one enum. Option C is safer at
  compile time but needs generics or duplicated `Playback` code for little extra benefit.

## DM2-4. Pause, resume, speed, reversal, seek, restart

- **Option A (recommended): one immutable `Playback` record** describing the affine map
  `local = anchorTicks + rate * (t - anchor)` (or the held value when paused). Every control operation takes an
  explicit instant and returns a new `Playback` that is continuous at that instant.
- **Option B:** a mutable `AnimationClock` with `pause()`, `resume()` and `setSpeed()`.

Reasoning:
- **[fact]** In Option A evaluation is a closed-form function of the instant, so there is no per-frame accumulation
  and no drift (contract 6.2, 6.4). Rounding happens once per control event, never once per frame.
- **[fact]** A paused span adds exactly nothing: resume reuses the held value bit for bit
  (`PlaybackTest.pauseAndResume`).
- **[preference]** Option A satisfies "no global mutable clocks", is thread-safe by immutability and can be stored,
  compared and replayed. The "stateful clock-control helper" asked for in the brief becomes a value transition, not
  mutable state. The caller keeps the current `Playback` in its own effect object.
- Reversal is a negative rate. A speed multiplier is `|rate|`. A rate of 0 freezes without being "paused".

## DM2-5. Evaluating before the last control event

- **Option A (recommended):** `Playback` keeps only its current segment. `localTicks(t)` for `t` before the anchor
  is the affine extrapolation (constant when paused). Control events must not go back in time
  (`IllegalArgumentException`).
- **Option B:** keep the full list of control events and evaluate any past instant historically.

Reasoning: **[preference]** M2 consumers render the present. History costs memory that grows with every control
event and is not needed for M3 or M4. It can be added later as a separate `PlaybackHistory` without changing
`Playback`.

## DM2-6. Cycle and end conventions

- **Option A (recommended):** cycles are half-open, `[n d, (n+1) d)`, so LOOP wraps to progress 0 at exactly `n d`.
  The end of a finite run (`u >= cycles * d`, decided on the exact cycle index) is the stage `AFTER`, with the end
  value of the last pass: 1 for ONCE and LOOP; for PING_PONG, 1 after an odd number of passes and 0 after an even
  number. PING_PONG odd passes run backwards and are continuous at every turn.
- **Option B:** closed cycles, so the end instant is still `ACTIVE` with progress 1.

Reasoning: **[fact]** half-open cycles partition time without overlap, so each instant has exactly one cycle index.
**[preference]** An explicit `AFTER` stage lets callers despawn and finish effects without comparing doubles.

## DM2-7. Operation-by-operation specification and bitwise golden vectors

- **Option A (recommended):** the contract fixes the exact operation sequence (contract 3-5), and the golden vectors
  are compared bit for bit.
- **Option B:** state only accuracy bounds and compare with tolerances, as M1 did.

Reasoning:
- **[fact]** M2 needs only `+ - * /`, `%`, `Math.rint` and long/double conversions. All are exactly specified by
  IEEE-754 and the Java Language Specification. Since Java 17 (JEP 306) all floating-point arithmetic is strict, so
  a fixed operation sequence yields the same bits on every conforming JVM. Python reproduces them too, which is
  what makes the generator an independent oracle.
- **[preference]** M2's purpose is determinism, and bitwise specification makes "identical inputs give identical
  results" checkable across implementations, not only within one run. The cost is that Codex has no freedom in
  arithmetic order. The formulas are short.
- Note for M3: `Math.sin` and `Math.cos` may differ by 1 ulp between JVMs and intrinsics; `StrictMath` does not.
  If curves must be bit-reproducible across machines, M3 has to decide this.

## DM2-8. Wall-clock gaps

- **Option A (recommended for M2):** no automatic gap handling. A COSMETIC playback shows wall-clock truth: after a
  10 s server freeze it jumps 200 ticks ahead. A caller that wants frozen-during-gap behaviour pauses and resumes, or
  seeks (both are demonstrated in `M2DeterminismTest.discontinuities`).
- **Option B:** a `CosmeticClock` value that turns raw nanosecond samples into capped cosmetic time
  (`step = min(raw step, maxStep)`), deterministic for a given sample sequence.

Reasoning: **[preference]** Which behaviour is right depends on the effect. Option B is cheap, but it is the only
piece of M2 that would depend on the sampling history, and therefore be path-dependent. Deferring it keeps M2
entirely path-independent. It can be added in M2.1 or M4 if the plugins need it.

## DM2-9. Scope: the brief's candidate list

| # | Candidate | Proposal |
|---|---|---|
| 1 | Tick-based time at 20 TPS | **in** (GAME domain, `TickTime`) |
| 2 | Wall-clock cosmetic time | **in**, as a domain; the caller supplies nanoseconds |
| 3 | Normalization, progress, repeating phases, intervals | **in** (`Timing`: delay, duration, ONCE/LOOP/PING_PONG, cycle count) |
| 4 | Pausing, resuming, restarting, offsets | **in** (`Playback`) |
| 5 | Speed multipliers, reversal | **in** (`Playback.rate`, negative allowed) |
| 6 | Long runs, precision, overflow, drift | **in** (contract 6.3, 6.4, explicit domain table) |
| 7 | Determinism | **in**, bitwise (DM2-7) |
| 8 | Irregular ticks, skipped frames, low TPS, discontinuities | **in** by design: path independence; discontinuities handled by explicit control (DM2-8) |
| 9 | Gameplay vs cosmetic distinction | **in** (DM2-3) |
| 10 | Compatibility with M3, M4 and motion | **in**: `TimingSample.progress` in [0, 1] is the curve parameter; `localTicks` is the motion clock |
| - | Easing functions | **deferred to M3** (they are curves on [0, 1]) |
| - | Nested or parented timelines (time hierarchies) | **deferred**: composition of two `Playback` maps needs its own analysis |
| - | Keyframe tracks, events and callbacks | **deferred**: scene and motion milestones |
| - | Automatic gap capping | **deferred** (DM2-8) |
| - | Awareness of a changed tick rate (`/tick rate`) | **out of the math core**: the GAME domain counts ticks, whatever their real length |

## What changes if the owner picks the alternative

| If the owner chooses | Revise |
|---|---|
| DM2-1 B (seconds) | contract 2, 4, 5; every test that states ticks; regenerate `playback.csv` and `timing.csv` |
| DM2-2 B (integer API) | add types to contract 5 and to `M2BoundaryTest`; `integerExactness` moves to the new type |
| DM2-3 B (raw longs) | contract 3; remove the domain-mismatch assertions |
| DM2-4 B (mutable clock) | contract 4 and `PlaybackTest`; the determinism tests need rewriting |
| DM2-6 B (closed cycles) | contract 5; `TimingTest.once`, `loop` and `pingPong`; regenerate `timing.csv` |
| DM2-7 B (tolerances) | `M2GoldenVectorTest` compares with tolerances; contract 6.1 is weakened |
| DM2-8 B (gap clock) | add `CosmeticClock` to contract 4 and its tests (path dependence documented) |
