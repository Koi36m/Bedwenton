# M2 risks, numerical edge cases and deferred improvements

Status: APPROVED, revision 2 (2026-10-15), with the contract; [R2] marks the changes.

## 1. Risks

| Risk | Effect | Mitigation in M2 | Residual |
|---|---|---|---|
| Caller mixes clock sources | nonsense local times | domain tag; mismatch is `IllegalArgumentException` | a caller can still put nanoseconds into `gameTick(...)`; adapters must be reviewed |
| Seconds used for gameplay decisions | boundary decisions depend on rounding | local time in ticks; division, never `* 0.05`; theorem 6.3 | durations given in seconds through `secondsToTicks` are inexact by nature |
| Fractional rates in gameplay logic | exactness lost | stated in 6.3; visible because the rate is a parameter | decisions near boundaries may differ by one tick from an exact rational model |
| Many control events | rounding accumulates per event | **[R2]** at most 1.5 (GAME), 2.5 or 3.5 (COSMETIC) x 2^-52 x M per event, derived in contract 6.4.1 and tested | 10^6 events at local 10^6 can move by about 3.3e-4 ticks (GAME) or 7.8e-4 ticks (COSMETIC beyond 2^53 ns) in the worst case; re-seek periodically if that matters |
| Tick counter reset or rollback | control throws, evaluation extrapolates | explicit `IllegalArgumentException` for control; `BEFORE` for evaluation | the caller must restart playbacks after a server restart |
| 32-bit tick counters in adapters | wrap after 3.4 years | the API takes `long` | adapter concern |
| Changed tick rate (`/tick rate`) | GAME seconds no longer real seconds | GAME counts ticks by definition | documented; COSMETIC for real-time effects |
| COSMETIC gaps | visible jump forward | documented; pause/resume or seek | automatic capping deferred (DM2-8) |
| **[R2]** `System.nanoTime` wrap-around | revision 1 threw or rejected valid controls when values crossed `Long.MAX_VALUE` | wrapping subtraction and wrapped ordering for COSMETIC (P-R1) | true intervals of 2^63 ns or more alias (the same limit as `System.nanoTime`) |
| **[R2]** `FOREVER` exhaustion | `AFTER` once the cycle index reaches 2^63 | documented limitation (contract 5) | needs elapsed/duration >= 2^63; never in practice |
| Implementer "optimizes" formulas | bits change; golden vectors fail | contract 6.1 forbids it; bitwise golden tests | none: the tests detect it (mutants M18, M24) |
| `Math.sin` in later milestones | 1-ulp differences between JVMs | not used in M2 | M3 must choose `Math` or `StrictMath` (F8) |

## 2. Numerical edge cases covered by tests

Signed zero in elapsed time; the last double before a cycle end (forward progress stays below 1); exact cycle ends
and turns; `Double.MIN_VALUE` durations; elapsed time over duration beyond 2^51 (valid sample, no exactness claim);
`Long.MIN_VALUE` and `Long.MAX_VALUE` instants (overflow detected; a paused playback never overflows);
`2^53 + 1` tick differences (rounding); COSMETIC differences of 1 ns and above 2^53 ns; infinite re-anchoring;
`Double.MAX_VALUE` elapsed-time overflow; negative delays and offsets; rate 0 compared with paused; reversal
through the start.

## 3. Deferred improvements (outside M2 unless the owner decides otherwise)

| Item | Why deferred | Natural home |
|---|---|---|
| Easing functions | they are curves on [0, 1] | M3 |
| Nested or parented timelines | composing two affine maps is simple, but anchors, pausing and domains need their own design | M2.1 or motion |
| Automatic gap capping (`CosmeticClock`) | path-dependent by nature; policy depends on the effect | M2.1 or M4 |
| Playback history (evaluate any past instant) | memory grows per event; nothing in M3 or M4 needs it | later, as a separate type |
| Keyframe tracks, events, callbacks, completion listeners | belong to scene and motion | motion and scene milestones |
| Tick-rate-aware seconds | needs the adapter to report the tick rate | Paper adapter milestone |
| `Duration`-style convenience types | not needed for the math | maybe never |
