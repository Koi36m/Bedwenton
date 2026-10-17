# M7 test plan, disposable-server smoke procedure and acceptance matrix (R3)

Status: **PROPOSED, revision 3.** Nothing here has been run against Paper. Every smoke row is NOT TESTED until it has
real-server evidence. CI compilation alone is never a real-server PASS.

## 1. Preservation gate (every build)

1. Root build: `./gradlew --no-daemon clean test check` gives **415 tests, 415 passed**, and the runtime classpath is
   empty.
2. All seven reference datasets regenerate byte-identically: `python3 verification/m6/regenerate_all.py <new dir>`
   (the accepted script: M1, M2, M2 review, M3, M4, M5 and M6, plus the M6 second-run check) prints `PASS`.
   plus the M6 manifest.
3. `git diff c43a66e656a77aa82bd72d8ffe27f1a037240e99 --name-status` lists only additions under `docs/m7/` and
   `paper-adapter/` (plus an optional new M7 workflow file).

## 2. Offline adapter tests (`paper-adapter/src/test`, core only, no Paper at runtime)

Fakes:
- `FakeSink` records `(viewerId, worldId, style, x, y, z)` calls and can throw on demand;
- `FakeGate` returns scripted results per viewer.

Plans come from the real accepted `DeliveryPlanner`, using the demo recipe and synthetic recipes. The exact test count
is reported at implementation; none is guessed here.

**R3 evidence:**
- **Proposed tests:** the proposed tests for the R2 and R3 rows are in `docs/m7/tests/`: 25 tests in 5 classes.
- **How they run:** `docs/m7/verification/run-tests.sh` runs them against the scratch reference in
  `docs/m7/reference/` and the real accepted `DeliveryPlanner`.
- **Mutation:** `docs/m7/verification/run_mutants.py` runs 13 targeted mutants.
- **Logs and commands:** in `M7-R3-VERIFICATION.md` and `docs/m7/verification/logs/`.
- **Production:** must keep these tests, or equivalent ones, in `paper-adapter/src/test`.

| Area | Required cases |
|---|---|
| Session ids | first-use order 0, 1, 2; no reuse after release; distinct UUIDs give distinct ids, including UUIDs whose 64-bit halves collide under XOR or `hashCode` (proves there is no hashing); viewer ids passed to M6 are strictly increasing; world ids are independent of viewer ids |
| Style catalogue | exactly the whitelisted keys reach `supportedStyles`; an unknown key has no spec (no fallback); dust size outside [0.01, 4.0] or an RGB above 0xFFFFFF is rejected at load; a removed (unresolvable) style is never emitted |
| Recipient prefix | for every recipient, the sent points are exactly the first `recipientPoints(r)` selections in position and j order; checked against an independent re-derivation from `EffectDelivery` accessors over the demo recipe at all tiers, and over random M6 scenarios (fixed seeds) |
| Selection fidelity | no unselected index, no unsupported style, no SKIPPED or FAILED delivery is emitted; coordinates equal `FrameBatch.x/y/z` exactly (bitwise) |
| Gate | each `SkipReason.VIEWER_*` skips only that recipient for that tick; the other recipients are unaffected; skips are counted |
| **Owner-aware gate (R2 F1)** | two **coincident** rings owned by different players: each owner without view permission sees only their own ring; a non-owner planned into another's ring but holding only use permission is refused for that ring alone; view granted shows both rings; view revoked between planning and emission removes only the other ring; the owner's use permission revoked hides the owner's own ring; a missing `RequestContext` skips that delivery whole (`MISSING_REQUEST_CONTEXT`) while the other ring is unaffected |
| **Viewer selection (R2)** | the owner is admitted before capping and sorting (owner id larger than the capped others is kept); an ineligible owner is not admitted; the owner listed among the others counts once; cap 1 keeps only the owner; the output is strictly increasing |
| **Allocation order (R2 F2)** | with the limits forced to bind (unaligned policy), the emitted ring is the rank-0 ring for both input orders, and it alternates with the tick rotation; with the aligned policy (contract 9.1), no `EMISSION_LIMIT` skip occurs and attempted calls equal planned placements, over fixed and 300 random multi-ring scenarios |
| **Style health (R2 F3)** | the third consecutive failure disables a style at once, and its remaining points in the tick are `STYLE_DISABLED`; a success of the same style resets the streak (fail, fail, success, fail, fail: not disabled); successes of another style do not reset it; the streak spans ticks; the next plan excludes the style (M6 reports it unsupported; no send of it); all styles disabled gives `SKIPPED_UNSUPPORTED` before evaluation; a new session re-enables |
| **Coordinate robustness (R3)** | the rule denies a viewer whose x, y or z is NaN, +infinity or -infinity (`VIEWER_POSITION_INVALID`, owner and non-owner) and a request whose anchor x, y or z is NaN or infinite (`INVALID_REQUEST_CONTEXT`). In range, exactly at the radius: allowed. Just beyond, or finite-but-overflowing coordinates: `VIEWER_OUT_OF_RANGE`. The R2 order holds (offline, world and permission are reported before coordinate problems). A radius that is NaN, infinite or negative is rejected at construction; a radius of 0 is exact-anchor only |
| **Context consistency (R3)** | two requests with different owners and swapped contexts: both deliveries are rejected (`MISMATCHED_REQUEST_CONTEXT`) and nothing is sent. One mismatched context rejects only that delivery. An invalid context (non-finite anchor, negative owner or world id) is rejected whole (`INVALID_REQUEST_CONTEXT`). Valid contexts are unaffected. A NaN viewer at the emitter is refused for each delivery while other viewers are served. The section 7.2 accounting identities hold in every case |
| Point validation | NaN and infinite coordinates and outside-world points are skipped and counted; finite in-world points are sent (the world-limit predicate is injected as a pure function) |
| Limits | `maxCallsPerTick` and `maxCallsPerViewerPerTick` are never exceeded, including at exactly the limit and one over; excess points are counted as `EMISSION_LIMIT` |
| Accounting | `plannedPlacements = plan.placements()`; `attempted = succeeded + failed`; `attempted + skippedPoints = planned placements of gated-in recipients`; M6 plan objects are unchanged after emission |
| Failures | a sink `RuntimeException` is counted and does not stop the other recipients; nothing escapes `emit` (disabling: see Style health) |
| Single plan per tick | the tick driver (core part) calls the planner exactly once per tick for N rings (counted through an injected planner function) |
| Bounds | config validation: every key at min, max, min - 1 and max + 1; non-decreasing caps; the derived `DeliveryPolicy` passes M6 validation |
| Cancellation | after `stop()` or a cancelled flag, a driver run emits nothing; repeated stop is idempotent |
| Thread boundary | core entry points accept an injected `isPrimaryThread` check; a false result refuses work (the Paper layer wires `Bukkit::isPrimaryThread`) |
| Boundary scan | `core` sources import no `org.bukkit`, `io.papermc`, `net.kyori` or `com.mojang`; `bukkit` sources never call `World#spawnParticle`, `ParticleBuilder#receivers` or async scheduler methods |
| Jar check | `verifyPluginJar` passes for the built jar and fails on a planted `org/bukkit/` entry (a test against a synthetic zip); the packaged `plugin.yml` has its version expanded (no `${`, equal to `project.version`) |

## 3. Disposable Paper 26.3 smoke procedure

**Environment:**
- a fresh directory and a new world;
- the official Paper 26.3 server jar, with its exact build number and SHA-256 recorded;
- Java 25 (`java -version` recorded);
- `eula=true`, `online-mode` as appropriate for the test accounts;
- **no other plugins**;
- never the live SMP.

**Clients:** two Java Edition accounts, A and B, on the matching client version. A is op. B is not op, and is given
`bedwenton.vfxdemo.use` (and later `.view`) through console commands or a permissions plugin installed only for the
test. If a permissions plugin is used, the "no other plugins" rule is relaxed for it, and its name and version are
recorded.

**Rows.** Each row records the commands, the expected result, console logs, `/vfxdemo status` output, and a screenshot
or video where the result is visual.

| # | Step | Expected |
|---|---|---|
| S1 | Start the server without the plugin, stop it | Clean start and stop; baseline log captured |
| S2 | Start with the plugin | One startup line with versions; no warnings or stack traces; no task running (`/vfxdemo status` shows task: idle) |
| S3 | B runs `/vfxdemo ring` without permission | Refused; no task starts |
| S4 | A runs `/vfxdemo ring` | Task starts; A sees the white END_ROD ring (radius 2, 32 points, no doubled seam point) and the red DUST accent (16 points) at the block-centre anchor |
| S5 | Inspect point placement | Particles sit on the circle without visible random spread (count = 0 exact-position behaviour) |
| S6 | B, without `.view`, stands inside `viewRadius` | B sees nothing; status shows A's ring with 1 recipient (two-user isolation) |
| S7 | Grant B `.view` | B sees A's ring from the next tick; status shows 2 recipients |
| S8 | A runs `/vfxdemo ring heavy` five times quickly, then `/vfxdemo stop` | Never more than one ring for A; stop removes it; task idles when no ring remains |
| S9 | A starts a ring, then (a) teleports more than `viewRadius` away; (b) changes world; (c) dies; (d) quits | The ring stops in each case; no exception; the task idles |
| S10 | With rings active, stop the server | Clean shutdown; no plugin task in `getPendingTasks()` (checked by a debug log line at disable); no stack trace |
| S11 | Restart and repeat S4 | Same behaviour (persistence of nothing but config) |
| S12 | World unload. (a) A ring owner travels to another world: covered by S9(b). (b) With a ring active in the overworld, stop the server: worlds unload at shutdown with no exception. Unloading a world that still contains the ring's owner is impossible on ordinary Paper, and an owner who leaves has already stopped the ring, so the `WorldUnloadEvent` path with an active ring is **defensive** and is covered offline (snapshot with a missing world drops the ring; the handler stops rings in that world). No extra plugin or debug command is added to force it. | No exception; no forced chunk load; no lingering task |
| S13 | Invalid config (for example `maxActiveEffects: 99`) | The plugin refuses to enable with a clear message; the server keeps running |
| S14 | Worst case within the ceilings: 2 players, `maxActiveEffects` rings at HEAVY | Record `planningNanos`, `emissionNanos`, attempted, succeeded and skipped from status over 200 ticks, and a Paper `/mspt` or spark sample if available; describe the visual behaviour. **No MSPT, network or client-FPS guarantee is inferred.** |
| S15 | A data-bearing particle | The DUST accent renders red (S4 screenshot) |
| S16 | (R2) A and B (both with `use`, without `view`) stand together; each runs `/vfxdemo ring` at the same block | Each sees only their own ring (screenshots from both clients); status shows 1 recipient per ring. Then grant B `view`: B sees both rings; revoke it: B again sees only its own |
| S17 | (R2) Revoke A's `bedwenton.vfxdemo.use` while A's ring is active | A stops seeing the ring from the next tick: the owner is no longer eligible at the snapshot (M6 reports `SKIPPED_NO_VIEWERS`), or, if the change lands between planning and emission, the gate counts `VIEWER_PERMISSION_REVOKED`. Restoring the permission makes it visible again while the ring lives |

## 4. Acceptance matrix (source-free)

| Gate | Evidence required | Status |
|---|---|---|
| G-A Preservation | 415/415, 7 datasets byte-identical, diff limited to M7 paths | NOT RUN (implementation) |
| G-B Exact Paper pin | `paperApiVersion` exact, with the `maven-metadata.xml` excerpt and the paper-api jar SHA-256 | NOT DONE (repository blocked in R1) |
| G-C Plugin jar | `verifyPluginJar` green; `plugin.yml` inspected; jar, source ZIP and SHA-256 | NOT RUN |
| G-D Offline tests | exact count, all passing (section 2) | NOT RUN in production. R3 proposed tests: 25/25 against the scratch reference; 13 of 13 mutants caught (`M7-R3-VERIFICATION.md`) |
| G-E Real-server smoke | S1-S17 with server build, Java version, logs, screenshots or video | NOT TESTED |
| G-F Worst-case run | S14 measurements, separated planning and emission timings | NOT TESTED |
| G-G Declared limits and rollback | install/rollback guide; limits listed (section 5) | NOT WRITTEN |

## 5. Install and rollback (summary for the implementation package)

- **Install:** copy the single jar into `plugins/` on a disposable server and start it. There is no other dependency.
  The rings are opt-in; nothing renders until a permitted player runs `/vfxdemo ring`.
- **Rollback:** stop the server and delete the jar and `plugins/BedwentonVfxDemo/`. The plugin writes nothing else and
  persists no world or player data.
- **Declared limits:** ordinary Paper 26.3 only; main thread only; no Folia; no Bedrock parity; static anchor; two
  whitelisted styles; the ceilings in contract section 9.
