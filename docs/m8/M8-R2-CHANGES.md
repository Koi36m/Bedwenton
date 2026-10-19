# M8 R2: design corrections (change log)

Status: **R2 change log (historical; F1–F4 substantially accepted at design level by the R2 review; R3 corrections in
`M8-R3-CHANGES.md`).** Not accepted, not implemented, not
deployed. `RingDriver.java` and every other accepted M1–M7 file are unchanged.

| Item | Value |
|---|---|
| R2 branch | `m8-spec-r2`, created from R1 `7cf789c4b6f87e4942f7bb3d02805e3407c1d244` |
| R2 commit | the commit adding this file (full SHA in the package `PROVENANCE.txt`) |
| Base chain | `7cf789c` (R1) ← `ed5fc7b` (accepted M7 production snapshot) ← `77bb965` ← `c43a66e` (accepted M6) |
| Scope | changes and additions under `docs/m8/` only |

**Input not available.** `M8-R1-INDEPENDENT-DESIGN-REVIEW.md` was not attached and is not on any branch. The
corrections implement F1–F4 and the verification items as the R2 instruction states them. **Reconcile with the review
text before acceptance.**

## F1 — Shared global effect capacity

**Defect (R1).** The text said rings and M8 effects share `maxActiveEffects`, but M7's ring start checks
`rings.size()` only, so admission depended on activation order.

**Correction** (architecture 4.4). One rule on both paths:
`hasCapacityFor(replacing) = replacing || rings + m8Effects < maxActiveEffects`.
- The ring path passes `replacing = rings.containsKey(owner)`.
- The M8 path passes `replacing = (owner, kind) registered`, and also checks the per-player M8 cap.
- Replacement never needs free capacity.
- With `TickContributor.NONE` the rule is exactly M7's.

**Executable:** `SharedCapacityTest` (6 tests) covers:
- rings first; M8 first;
- **all 120 orders** of a five-start mix (exactly 4 admitted and 1 refused each time, one task);
- replacement at the cap; the per-player cap; freed capacity.

`RingDriverWithHookM7CompatTest` runs the 10 M7 `RingDriverTest` cases against the hooked reference with no
contributor.

## F2 — M8-only scheduler and tick execution

**Defect (R1).** The hook sat after `if (rings.isEmpty()) { cancelIfEmpty(); return; }`, so M8 effects never ran
without a ring.

**Correction** (architecture 4.2–4.3):
- **Tick order:** guards → prune rings → `contributor.drafts(tick)` **always** → cancel only if no ring and no M8
  effect → build all requests → `lastPlannedTick = tick` → **zero requests: no planner call**, zero report → otherwise
  exactly one plan.
- **Task lifecycle:** `ensureRunning()` is shared by both start paths; `cancelIfEmpty()` considers both; stale-callback
  generation tokens and `close()` handling cover M8.
- **Exact delta:** the proposed production change is the executable scratch copy `reference/.../RingDriverWithHook.java`,
  shown as `validation/ringdriver-proposed.diff` (73 changed lines).

**Executable:** `M8OnlySchedulingTest` (6 tests) covers:
- aura without a ring, and ring plus aura in one plan;
- a ring stop that keeps the task alive for the aura;
- zero-request ticks (planner skipped, task kept);
- stale callbacks after an M8-only restart;
- natural end and termination;
- shutdown.

**SPI refinement.** M8 effects now return an `EffectDraft` (replacing R1's `TickRequest`). The driver applies the
**same** owner-first viewer selection as for rings and builds the M6 request and context from one id and one anchor.
New SPI files: `EffectDraft`, `AnchorSource`, `EffectScheduler`; `TickContributor` gains `activeCount()` and `NONE`.

## F3 — Trail playback timing

**Defect (R1).** The rebuilt 2-tick scene was described with the effect's playback, so from local tick 2 on every
rebuilt scene would be AFTER and emit nothing.

**Correction** (anchor spec 5.1; showcase B):
- **Scene clock:** restarted every tick, `Playback.start(gameTick(tick))` with `now = gameTick(tick)`, so the scene is
  at local tick 0.
- **Effect clock:** `age = tick − startTick` decides the lifetime and is passed as M6 `frameOrdinal`.

**Executable:** `TrailTimingTest` (6 tests) covers:
- delivered or thinned emissions at **every tick through local 299**, with explicit checks at 3, 50, **100** and 299
  (frame ordinal = age, scene local tick = 0);
- lifetime ending exactly at age 1200 (still active at 100 and 1199);
- expiry after stopping;
- **thinning offset = `floorMod(age, g)`**;
- long and short teleports breaking segments without interpolated samples.

## F4 — Combined showcase configuration

S-X1 now uses an explicit test override on the disposable server, `maxActiveEffects: 8` and
`m8.maxEffectsPerPlayer: 3`, for 2 players × (ring + 3 M8 effects). S-X2 tests admission at the **production
defaults** (4, 2), which are unchanged.

## Verification improvements

- **The probe now fails on violations.** It exits 1 when P1 > 1e-12, P2 > 1e-9, or any P3 phase boundary differs.
  As a negative self-check, a copy with one tampered P3 expectation exited 1. The same checks also exist as JUnit
  assertions (`OrientationAndAnimationTest`, 5 tests), covering yaw mapping, modes, snapshot validation, the aura
  formula, and the activation table for all tiers.
- **P4** is labelled a **synthetic** benchmark and now rebuilds the actual two-layer (fresh/old) trail scene. Its
  timings are indicative only.
- **Stationary trail:** an active trail with no live samples still gets a lightweight per-tick update (snapshot,
  validation, O(capacity) expiry scan) and contributes no request. When the tick has no request at all, **the global
  planner is skipped** (anchor spec section 7; performance section 2).
- **Runnable verification:** `docs/m8/verification/` holds the Gradle build, `run-tests.sh`, `run_mutants.py` and
  `mutants.py`. `validation/reproduce.sh` runs everything, with an optional `--with-mutants`.

## 6. Results (Java 25.0.4.1, Gradle 9.1.0)

| Check | Result |
|---|---|
| R2 design tests: 23 M8 (`SharedCapacity` 6, `M8OnlyScheduling` 6, `TrailTiming` 6, `OrientationAndAnimation` 5) + 10 M7-compatibility | **33/33** |
| Unchanged M7 adapter tests in the same build (Paper layer excluded) | **61/61** (total 94/94) |
| Targeted mutants (F1a, F1b, F2a–c, F3a–c, T1, O1) | **10 of 10 caught**. T1 first survived; the added short-teleport test catches it |
| Probe | PASS; exits 1 on a tampered expectation |
| SPI (14 files) compile `-Werror` against the engine and the unchanged M7 core | 0 warnings |
| M1–M6 / seven datasets / M7 core re-run | see the clean-clone log in the package (`reproduce.sh`) |

**Not verified:**
- compilation against the pinned `paper-api 26.3.build.204-stable` (`repo.papermc.io` is blocked here);
- the Paper layer of M7 and M8;
- any real-server behaviour;
- GitHub publication (expected HTTP 403).
