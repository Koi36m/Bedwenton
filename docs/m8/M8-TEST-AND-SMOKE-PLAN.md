# M8 test, verification and smoke plan (R3)

Status: **PROPOSED, revision 3.** Production tests and smoke rows are **NOT RUN**. What R1–R3 actually executed (probe,
SPI compilation, the R2/R3 proposed tests against a scratch reference) is recorded in `M8-R1-REPORT.md`,
`M8-R2-CHANGES.md` and `M8-R3-CHANGES.md`. Every test and smoke row below is **NOT RUN** until production evidence exists.

## 1. Gates

| Gate | Requirement |
|---|---|
| **C0 Paper pin** | M8 compiles against `io.papermc.paper:paper-api:26.3.build.204-stable`; `recordPaperApi` hash check passes; every Paper method named in the anchor spec exists in that jar (javap listing) |
| **C1 Preservation** | 415/415 M1–M6, 61/61 M7 adapter tests **unmodified**; seven datasets PASS (`verification/m6/regenerate_all.py`); diff vs `ed5fc7b` limited to approved files (architecture section 8) |
| **C2 Offline M8 tests** | section 2, exact count reported, all passing |
| **C3 Smoke** | section 3 rows with evidence |
| **C4 Performance** | section 4 of the performance spec measured and reported (no thresholds claimed met unless measured) |

## 2. Offline tests (pure core, fakes; JUnit 5.12.2, Java 25)

Fakes: scripted `AnchorSource` (snapshots per tick), M7 `RingDriver.Host` fake (as in `RingDriverTest`), recording
`ParticleSink`, counting planner wrapper.

| Id | Area | Cases |
|---|---|---|
| T-A1 | Pose | yaw 0/45/90/180/270/−90/359.5/720 map local +Z to (−sin, 0, cos) and +X to left; WORLD_FIXED identity; HEAD_YAW vs BODY_YAW selection; pitch never changes the pose |
| T-A2 | Aura geometry | at local ticks 0, 1, 15, 30, 59, 60, 123 every orbit/halo point equals an independent formula (≤ 1e-9) for a moving, turning anchor (probe P2 as a test) |
| T-A3 | Attachment | stationary, walking (0.22/tick), sprinting (0.28), jumping (y profile), turning: the formation centre equals the snapshot position plus lift every tick |
| T-A4 | Tiers | point counts 12/27/36; strict ceiling (heavy refused when disallowed) |
| T-I1 | Invalid snapshots | NaN/±∞ in x, y, z, yaw → SKIP_TICK, no request, counter; 20 consecutive → TERMINATE; offline/invalid/dead/absent → TERMINATE and dispose once |
| T-I2 | Identity | request ids from 2^62 upward, unique, never reused after replace/stop; context id == request id; owner viewer id from SessionIds; world id from the snapshot each tick (world change updates it) |
| T-T1 | Trail threshold | rotation-only and sub-threshold motion add nothing; d ≥ minSpacing adds floor(d/minSpacing) capped by maxSamplesPerTick |
| T-T2 | Trail bounds | capacity never exceeded under 10,000 ticks of sprinting; memory constant (object count) |
| T-T3 | Expiry | samples dropped exactly when tick − sample.tick > maxAge; stationary trail empties and then contributes no request |
| T-T4 | Discontinuity | serial change, world change, step > maxStep each start a new segment; no generated point lies between two segments (geometric check) |
| T-C1 | Activation phases | per-layer visible counts at local 0, 1, 10, 19, 20, 35, 49, 50, 59, 60, 79, 80 equal probe P3 for all tiers; finished at 80 and disposed in that tick |
| T-C2 | Activation anchor | static: owner movement does not move it; world change/teleport beyond radius/death/quit stop it |
| T-B1 | Single plan | for any mix of rings and M8 effects, exactly one planner call per tick; duplicate tick and stale callback make none |
| T-F1 | Shared capacity (R2) | rings first then M8; M8 first then rings; all 120 orders of a five-start mix; replacement at cap for both paths; per-player M8 cap; freed capacity reusable by either path; M7-only admission identical (executable now: `SharedCapacityTest`, `RingDriverWithHookM7CompatTest`) |
| T-F2 | M8-only scheduling (R2) | aura without any ring schedules one task and plans once per tick; ring + aura in one plan; ring stop keeps the task for the aura; zero-request tick skips the planner and keeps the task; stale callbacks after M8-only restart; natural end and termination cancel the task; close disposes everything and refuses later starts (executable now: `M8OnlySchedulingTest`) |
| T-F3 | Trail clocks (R2) | emissions at every tick through local 299 incl. 3, 50, 100; frame ordinal = effect age; scene-local tick 0; lifetime ends exactly at the configured age; expiry after stop; thinning offset = floorMod(age, g); long and short teleports break segments without interpolated samples (executable now: `TrailTimingTest`) |
| T-O1 | Orientation/animation (R2) | yaw mapping ≤ 1e-12, pitch ignored, modes, snapshot validation, aura vs independent formula ≤ 1e-9, activation phase table exact for all tiers (executable now: `OrientationAndAnimationTest`; the probe also exits 1 on violation) |
| T-F5 | Transactional start (R3) | first registration with an injected scheduler failure: exception, nothing registered, candidate disposed, no task, retry succeeds with a fresh id; replacement with an injected `ensureRunning` failure: old effect registered, undisposed and still planned, candidate disposed, count and task unchanged; factory failure (first and replacement); candidate with wrong id/owner/kind or null; successful replacement disposes old exactly once after commit; throwing old `dispose` contained after commit; refused admission calls neither factory nor scheduler; first-ring scheduling failure leaves no ring and no task (executable now: `TransactionalStartTest`) |
| T-F6 | Anchor identity and freshness (R3) | full reason table (owner mismatch, viewer id mismatch, released id, stale, future, world UUID/id disagreement, unknown world id, non-finite, height bounds incl. the half-open edge, unloaded chunk); with B holding `use` but not `view` next to A: every tampered snapshot leaves B with 0 particles and the right TERMINATE/SKIP_TICK outcome; a replayed released id rejected; drafts built from the binding even under a defective rule; forged drafts dropped by the registry (effect stopped, `identityViolations`) and by the driver (`rejectedDrafts`) (executable now: `AnchorIdentityTest`) |
| T-F7 | Showcase capacity feasibility (R3) | S-X1 with the 8/3 override: 8 effects, one task, one plan of 8, every A/B start a replacement that succeeds; S-X1b: third owner's first ring or M8 effect refused, admitted after capacity frees; S-X2 at defaults 4/2: ring-first and M8-first orders refuse the fifth unique effect on the right path with the right message, replacements succeed (executable now: `ShowcaseCapacityTest`) |
| T-B2 | Fairness/budgets | priorities and rotation order across rings/aura/trail/activation; placements ≤ aligned budgets; per-viewer cap; emitter never hits `EMISSION_LIMIT` with the aligned policy |
| T-B3 | Authorization | coincident aura of A and B: each owner sees only their own without view permission; view grant/revoke; owner losing use; M7 gate reasons counted |
| T-M1 | Multiple effects | one per kind per player; replace semantics; global cap shared with rings; per-player M8 cap |
| T-L1 | Lifecycle | start/stop/off/stop all/close: task created once, cancelled when empty (ring + registry); dispose idempotent; nothing contributes after close |
| T-L2 | Long run | 72,000 simulated ticks with random start/stop/teleport: live effects ≤ cap, trail sizes ≤ capacity, no growth of internal maps after all effects stop |
| T-H1 | Thread | every core entry point refuses a non-primary thread (`ThreadGuard`) |
| T-G1 | Commands | `M8Commands` grammar, defaults (simple), `off`, permissions, player-only; M7 `DemoCommands` tests unchanged |
| T-R1 | Regression | all 61 M7 tests and 415 engine tests pass untouched (gate C1) |

## 3. Real-server smoke plan (disposable Paper 26.3 server, never the SMP)

Record: Paper build number and SHA-256, Java version, plugin JAR SHA-256, config, clients and versions. Two Java
accounts: **A** (op) and **B** (non-op; permissions via console or a test-only permissions plugin, recorded).
**Two-player visibility and permission isolation (S-A6/S-A7) must pass before any other player is invited.**

| Id | Step | Verify (appearance / cleanup / performance recorded separately) |
|---|---|---|
| S-A1 | enable with `m8.enabled: false`, try `/vfxdemo aura` | refused; M7 ring still works (re-run M7 S4) |
| S-A2 | enable M8; A `/vfxdemo aura` (simple default) | orbit at waist follows A; status shows 1 aura, task running |
| S-A3 | A walks, sprints, jumps, turns | formation stays centred; turns with body; video |
| S-A4 | tiers balanced/heavy, replace | one aura only; halo appears from balanced |
| S-A5 | A teleports (command) same world; changes world | aura snaps without streak; continues in new world (Q2) |
| S-A6 | B (use, no view) stands next to A; both run auras | each sees only their own (screenshots both clients) |
| S-A7 | grant/revoke B `view` | B sees A's aura only while granted |
| S-A8 | A dies; A quits; `/vfxdemo aura off`; `/vfxdemo stop` | aura stops each time; task idles when nothing active |
| S-A9 | server stop with auras active | clean shutdown; `remaining owned tasks=0` in log |
| S-T1 | A `/vfxdemo trail`, sprints a circuit | trail behind A, bounded length, expires after stopping |
| S-T2 | A stands still and only rotates camera | no new trail particles; status shows trail contributing no request |
| S-T3 | A teleports far / changes world | no particles connecting old and new locations |
| S-T4 | elytra or boat movement (if available) | trail continuous, no false breaks; note results |
| S-T5..T7 | death/quit/off; two-player isolation as S-A6/S-A7 | as above |
| S-C1 | A `/vfxdemo activate` | four phases in order, about 4 s total, then nothing remains |
| S-C2 | A moves during activation | activation stays at the start position |
| S-C3 | spam activate 10× | at most one per player; no task growth |
| S-C4..C5 | world change/quit during activation; two-player isolation | stops; isolation holds |
| S-X1 | **with the documented test override** `maxActiveEffects: 8`, `m8.maxEffectsPerPlayer: 3` (disposable server only; production defaults 4 and 2 unchanged): both players run aura + trail + activation + ring; then each re-runs `/vfxdemo aura balanced` and `/vfxdemo ring` | all 8 render; status shows 8 active, one shared task; each re-run **replaces** (still 8, effect continues with a new id, no refusal). R3: no ninth-effect step here: A and B already hold every (player, kind) slot |
| S-X1b | *optional, only with a third test account C* (non-op, `use` only, after S-A6/S-A7 passed), same override, S-X1 state: C runs `/vfxdemo aura`, then `/vfxdemo ring` | both refused (`Active effect cap reached`, `Active ring cap reached`); A and B effects unaffected; after A stops a trail, C's aura is admitted |
| S-X2 | with production defaults (`maxActiveEffects: 4`, `m8.maxEffectsPerPlayer: 2`). **Required global-cap refusal (R3, F7).** Order 1: A ring, A aura, B ring, B aura, then B trail. Order 2 (after `stop all`): A aura, A trail, B aura, B trail, then A ring, then A activate | Order 1: B trail refused `Active effect cap reached` (B holds only 1 of 2 M8 effects, so it is the global cap); `/vfxdemo ring` by B and `/vfxdemo aura` by A then replace successfully. Order 2: A ring refused `Active ring cap reached`; A activate refused by the per-player cap; replacements succeed; no crash |
| S-X3 | 30-minute soak (perf P6) | no growth in active effects/tasks; heap stable; record MSPT/TPS/FPS |
| S-X4 | `/reload` not used; restart server | clean re-enable; no persisted state |

Appearance (screenshots/video), cleanup (logs, status, task counts) and performance (perf spec section 5) are recorded as
separate evidence columns; a pass in one never implies another.

## 4. Representation of results

Each executed check reports command, environment, exact counts, logs, and PASS/FAIL. Conceptual items stay NOT RUN.
No Folia, Bedrock, MSPT or client-FPS guarantee is inferred from any single run.

## 5. Design evidence actually produced (not M8 production test passes)

- R1: `M8-R1-REPORT.md` section 3 (SPI compilation, probe P1–P4, Paper API excerpt, preservation).
- R2/R3: `docs/m8/verification/run-tests.sh` runs the 45 M8 proposed tests (R2 23 + R3 22) and the 10 M7-compatibility
  tests (55 in total), plus the 61 unchanged M7 adapter tests against the scratch reference and the accepted engine;
  `run_mutants.py` runs 23 targeted mutants. Results: `M8-R3-CHANGES.md` section 5. These validate the design, not a
  production implementation.

## 6. Test risks

| Risk | Mitigation |
|---|---|
| Offline fakes miss Paper event ordering (teleport vs move vs world change) | smoke S-A5, S-T3, S-T4; serial-based design tolerates ordering |
| Visual smear judged subjectively | video evidence, both clients, owner review |
| Long-run memory measured noisily | full-GC heap samples via spark or JVM flags at fixed intervals |
