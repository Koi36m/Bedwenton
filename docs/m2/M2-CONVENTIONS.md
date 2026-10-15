# M2 conventions: time, units and mathematical facts

Status: APPROVED, revision 2 (2026-10-15), together with `M2-IMPLEMENTATION-CONTRACT.md`; [R2] marks the changes. M1's `docs/CONVENTIONS.md` stays in force
unchanged: axes, handedness, rotation direction and composition order.

## 1. Units and signs

| Quantity | Unit | Type | Notes |
|---|---|---|---|
| GAME instant | server ticks | `long` | a monotonic server tick counter supplied by the adapter |
| COSMETIC instant | nanoseconds | `long` | from a monotonic source such as `System.nanoTime()`; the origin is arbitrary, only differences matter. **[R2]** Values may wrap past `Long.MAX_VALUE`; differences use wrapping subtraction and are valid for true intervals below 2^63 ns |
| local animation time | ticks | `double` | 1 local tick = 1 game tick (GAME) or 50 ms (COSMETIC) |
| delay, duration | local ticks | `double` | duration > 0 |
| rate | local ticks per input tick | `double` | negative plays in reverse; `abs(rate)` is the speed multiplier |
| progress | none | `double` | in [0, 1]; 0 at the start of a pass, 1 at its end |
| cycle | count | `long` | 0-based; `Timing.FOREVER = Long.MAX_VALUE` means unbounded |
| seconds | s | `double` | only through `TickTime`; `seconds = ticks / 20.0` |

Time increases with the instant value. Positive local time runs forward. A Timing is `BEFORE` when
`localTicks < delayTicks`.

## 2. Gameplay-authoritative or cosmetic: which domain to use

| Use GAME when | Use COSMETIC when |
|---|---|
| the effect must line up with game state: damage pulses, hitboxes, cooldown visuals, synchronized multi-entity patterns | the effect is purely visual and should keep real-time speed when the server lags |
| results must be reproducible exactly (replays, tests, several players seeing the same frame) | smoothness matters more than reproducibility, for example an asynchronous emitter faster than 20 Hz |
| the animation should freeze with the game (`/tick freeze`, server pauses) | the animation should keep running whatever the tick rate is |

Both domains are deterministic **given their inputs**. A COSMETIC result is not reproducible between runs only
because the supplied nanosecond values differ between runs. The math adds no nondeterminism.

The GAME domain counts ticks, not seconds. If the server's tick rate is changed (Minecraft Java 1.20.3 added the
`/tick` command; verify against the target server version), GAME animations follow the ticks, and
`TickTime.ticksToSeconds` becomes a nominal conversion only. Adapters should not use world day-time as the GAME
counter, because `/time set` changes it.

## 3. Composition with later milestones

- **M3 curves:** a curve is evaluated at `TimingSample.progress`, in [0, 1]. Easing is an M3 curve applied to
  progress. A PING_PONG Timing already provides the back-and-forth, so curves need not.
- **M4 sampling and motion:** use `TimingSample.stage` for spawn and despawn decisions (exact in the integer regime,
  contract 6.3), `cycle` for per-cycle variation, and `Playback.localTicks` for velocity-style motion
  (`angle = omega * localTicks`). For periodic motion over long runs, prefer a LOOP Timing's progress: it keeps
  full precision at any elapsed time (contract 6.4), whereas `omega * localTicks` loses absolute precision as
  `localTicks` grows.

## 4. Established facts the design relies on

Each fact is checked by the named test or by the golden-vector generator (`tools/generate_m2_golden_vectors.py`).

| # | Fact | Checked by |
|---|---|---|
| F1 | 0.05 is not a binary fraction. `t * 0.05` differs from the correctly rounded `t / 20` for 66,900 of the ticks 1 to 199,999, the first being t = 3 | `TickTimeTest`, generator `tick_seconds.csv` |
| F2 | `t / 20.0` is the correctly rounded quotient for every tick count below 2^53 | generator (exact fractions) |
| F3 | `(t / 20.0) * 20.0 == t` held in every sample below 2^52 but fails in [2^52, 2^53], for example at 9007199254540993 | `TickTimeTest` |
| F4 | Integers of magnitude at most 2^53 are exact doubles; 2^53 + 1 rounds to 2^53 | `TimePointTest` |
| F5 | Java's `%` on doubles (C `fmod`) returns the exact remainder | generator (200,000 random cases, exact fractions) |
| F6 | `0 <= r < d` implies `r / d < 1` after rounding | contract 5 (proof), generator, `TimingTest.totality` |
| F7 | `frac(u / d)` loses absolute precision as `u` grows (4.2e-4 at `u = 2^45 + 0.5`, `d = 7`); the exact remainder does not (1.2e-17) | `TimingTest.phasePrecisionAtLargeTimes`, generator |
| F8 | Java floating-point arithmetic has been strict since Java 17 (JEP 306); `+ - * / %`, `Math.rint` and conversions are fully specified, so results are bit-identical across JVMs. `Math.sin` and `Math.cos` are not (1 ulp freedom); `StrictMath` is | contract 6.1; bitwise golden tests |
| F9 | `-0.0 - 0.0 = -0.0`, so a signed zero has to be normalized explicitly | `TimingTest.delay` |
| F10 | `1 / 50,000,000` rounds to `2e-8`; 50,000,000 ns divides evenly into one tick | `TimePointTest` |
| F11 **[R2]** | `System.nanoTime` (JDK 25 Javadoc): "nanoseconds since some fixed but arbitrary origin time (perhaps in the future, so values may be negative)"; elapsed time is `t1 - t0` and comparisons use `t1 - t0 >= x` "because of the possibility of numerical overflow"; differences spanning more than about 292 years (2^63 ns) are not computed correctly | `M2Revision2Test` |
| F12 **[R2]** | Each IEEE-754 operation adds a relative error of at most 2^-53; re-anchoring therefore errs by at most 1.5, 2.5 or 3.5 x 2^-52 x M (GAME, COSMETIC below 2^53 ns, COSMETIC beyond) | contract 6.4.1, `M2ReviewAdversarialTest.reanchoringErrorBounds` |

## 5. Design preferences (not facts)

Ticks rather than seconds (DM2-1); one double pipeline (DM2-2); domain-tagged instants (DM2-3); an immutable
affine playback (DM2-4); no history before the anchor (DM2-5); half-open cycles with an explicit `AFTER` (DM2-6);
bitwise specification (DM2-7); caller-handled gaps (DM2-8); the scope cut (DM2-9). Each is argued, with its
alternative, in `M2-DESIGN-DECISIONS.md`.
