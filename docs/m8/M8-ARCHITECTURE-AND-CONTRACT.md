# M8 architecture and contract: dynamic VFX and entity attachment (R3)

Status: **PROPOSED, revision 3 — design only, for ChatGPT's independent design review.** R2 corrects the shared
capacity (F1), M8-only scheduling and tick order (F2), trail clocks (F3) and the combined test configuration (F4);
see `M8-R2-CHANGES.md`. R3 adds the transactional effect start (F5, section 4.5), fail-closed anchor identity and
freshness (F6, section 3 and anchor spec 3.2–3.4) and a feasible S-X1 capacity check (F7); see `M8-R3-CHANGES.md`. Nothing here is implemented,
accepted, deployed or authorized for Codex. Normative words: **must** (contract), **should** (recommended),
**[verify]** (to be confirmed against the pinned Paper jar or a real server before production evidence counts).

| Baseline | Value |
|---|---|
| Accepted M6 | `c43a66e656a77aa82bd72d8ffe27f1a037240e99` (tree `ef496f7b…`) |
| Accepted M7 R3 design | `4eeb8354693463a95f7dd48cbf3891701dcc6acf` |
| Accepted M7 production snapshot (inspected for this design) | `ed5fc7bff548edf4b005e919495b22213a65b64e`, tree `afa33672b5850bdda3be636415659fa27ac53047` (ancestry `ed5fc7b → 77bb965 → c43a66e`; GitHub snapshot commits, distinct from the native R3 descendant chain, as M7's implementation report states) |
| Pinned Paper | `io.papermc.paper:paper-api:26.3.build.204-stable`, jar SHA-256 `f6852324…d6ea1556` (`paper-adapter/paper-api-provenance.json`); Java 25 |
| M8 branches | R1 `m8-spec` (`7cf789c`), R2 `m8-spec-r2` (`f04a843`) from R1, R3 `m8-spec-r3` from R2; all change files under `docs/m8/` only |

Companion documents: `M8-ANCHOR-AND-MOTION-SPEC.md` (anchors, frames, timing, trail history), `M8-SHOWCASE-DESIGNS.md`
(Aura, Trail, Activation), `M8-PERFORMANCE-AND-BUDGETS.md`, `M8-TEST-AND-SMOKE-PLAN.md`, SPI proposal `docs/m8/spi/`,
evidence `docs/m8/validation/`, report `M8-R1-REPORT.md`.

## 1. What already exists (inspected at `ed5fc7b`, not assumed)

| Capability | Where | Used by M8 as |
|---|---|---|
| `Pose(Vec3, Quat, scale)`, `Pose.then`, `toWorld`; `Quat(w,x,y,z)` record constructor, `rotate`, `normalized` | M1 `workshop.vfx.math.core` | the per-tick **anchor pose** of an attached effect (translation = owner position, rotation = yaw) |
| `Timing.once/loop/withDelay`, `Playback.start(TimePoint)`, `TimePoint.gameTick`, `TimingSample` stages | M2 | deterministic animation time: local ticks = current tick − start tick |
| `Spiral.circle/helix`, `Easing` (LINEAR, QUAD_OUT, …) | M3 | orbit rings, activation helix, eased scale/translate |
| `Sampler.uniformParameter`, `Endpoints.EXCLUDE_END` | M4 | seam-free ring sampling |
| `EffectScene.of`, `Layer`, `Motion.fixed/spin/scale/translate/then`, `Reveal.PREFIX`, `Visibility`, `Shape.of/points`, `evaluate(anchor, playback, now)` | M5 | all showcase geometry and phases; **M5 already evaluates scenes at an arbitrary anchor `Pose`**, so moving anchors need no engine change |
| `DeliveryPlanner.plan`, `EffectRecipe`, `DeliveryRequest.of(id, recipe, tier, worldId, anchorPose, playback, now, frameOrdinal, viewers)`, priorities, tiers (strict ceiling, G11), roles, strided thinning, budgets | M6 | one global plan per tick for rings **and** M8 effects; fairness by allocation rank |
| `RingDriver` (one `runTaskTimer(…,1,1)` task, created on first effect, cancelled when empty; one `plan` per tick; stale-callback and duplicate-tick guards), `DefaultPlanEmitter` (rank order, recipient prefixes, accounting), `OwnerAwareRecipientRule` (fail-closed coordinates), `OwnerFirstViewerSelection`, `DefaultSessionIds` (sequential, never reused), `SessionStyleHealth`, `WorldPointRules`, `ThreadGuard`, `DemoConfig.policy()` (budgets aligned to emission caps) | M7 `paper-adapter/…/core` | reused as-is; M8 plugs into the same tick (section 4) |
| `VfxDemoPlugin` (lifecycle, `registerCommand("vfxdemo", …, BasicCommand)`, listeners for quit/world change/teleport/death/world unload, per-player `spawnParticle(…, count 0, offsets 0, speed 0, data, force=false)`), `DemoCommands` (ring/stop/status grammar), `DemoStyleCatalog` (END_ROD, red DUST) | M7 `paper-adapter` | extended additively (section 8) |

**Probe evidence** (`validation/M8DesignProbe.java`, output `validation/m8-design-probe.txt`, run on the accepted
engine jar): the yaw quaternion matches Paper's documented yaw convention to 1.6e-16; a spinning aura evaluated with a
moving, turning anchor matches an independent formula to 3.6e-15 blocks over 126 points; activation phases switch exactly
at their tick boundaries; rebuilding a 12–36-point trail scene plus one plan costs microseconds per tick here
(indicative only, not a server measurement).

**No M1–M6 limitation was found.** No change to accepted engine code is proposed or needed.

## 2. Architecture

```
Paper main thread, one repeating task (M7's, shared; started by the first ring OR the first M8 effect)
 RingDriver.tick()                       (M7 + additive hook; exact order in section 4.2)
  1 guards     : running/closed, generation token, duplicate tick
  2 rings      : prune (M7 rules)
  3 M8         : DynamicEffectRegistry.drafts(tick) — snapshot each effect's owner, contribute an EffectDraft or
                 nothing, dispose finished/terminated effects — ALWAYS called, also with zero rings
  4 empty?     : no ring and no registered M8 effect → cancel the task, return (no plan)
  5 requests   : ring requests (M7) ++ M8 drafts → viewer selection (same M7 rule) → DeliveryRequest + RequestContext
  6 zero?      : no request at all (for example only a stationary trail) → zero report, NO planner call, task stays
  7 plan       : ONE DeliveryPlanner.plan(tick, all requests, enabledStyles, policy)
  8 emit/report: DefaultPlanEmitter (rank order, owner-aware gate, style health, point rules, caps) → EmissionReport
```

- **Pure core** (`workshop.vfx.paper.m8`, no `org.bukkit`/`io.papermc` imports): `AnchorRules`, `TrailHistory`,
  `DynamicEffect` implementations (`AuraEffect`, `TrailEffect`, `ActivationEffect`), `DynamicEffectRegistry`
  (implements `TickContributor`), `M8Recipes`, `M8Commands`, `M8Config`. Fully unit-testable with fakes.
- **Thin Paper layer** (`workshop.vfx.paper.bukkit`): reads `Player` state into `AnchorSnapshot`, increments the
  owner's discontinuity serial in teleport/world-change/respawn listeners, dispatches the new subcommands, resolves the
  M8 particle styles, wires the registry into `RingDriver`. No new task, no NMS, no reflection, no broadcast.

## 3. Identities (normative)

| Identity | Rule |
|---|---|
| Owner | `Player#getUniqueId()`; mapped to the M7 session viewer id with `DefaultSessionIds.viewerId` (sequential, never reused, never hashed). R3: the pair is **bound** to the effect at start (`AnchorBinding`) and every draft carries the bound pair, never snapshot values; a snapshot or draft naming another owner or viewer id is rejected (anchor spec 3.3) |
| World | world UUID → M7 session world id (`DefaultSessionIds.worldId`), read **from the snapshot every tick** |
| Request id | M8 ids come from a registry counter starting at `2^62` and increasing by 1; M7 ring ids start at 0. The two ranges cannot meet within any session (`2^62` effects). Ids are never reused; M6 receives unique ids per plan (it rejects duplicates) |
| Request context | `RequestContext(requestId, ownerViewerId, worldId, anchorX, anchorY, anchorZ)` built from the **same** snapshot as the request's anchor pose; `context.requestId() == request.id()` (M7's emitter rejects mismatched, missing or non-finite contexts) |
| Viewers | exactly M7's rule: owner first when eligible (needs `bedwenton.vfxdemo.use`), then other eligible players (need `bedwenton.vfxdemo.view`, same world, within `viewRadius` of the **current** anchor), capped at `maxViewersPerEffect`, then sorted ascending (`OwnerFirstViewerSelection`) |
| Frame ordinal | `tick − startTick` (drives M6 thinning phase rotation and visual continuity) |

## 4. Integration with the single tick (normative)

### 4.1 Hook (recommended Option B; production change needs owner authorization, Q6)

M7's `RingDriver` gains an optional `TickContributor` (SPI) through a new constructor overload; existing constructors
pass `TickContributor.NONE`, with which behaviour is exactly M7's. The complete proposed delta is executable as the
scratch reference `docs/m8/reference/.../core/RingDriverWithHook.java` (a copy of the accepted file with only these
changes) and is shown as a unified diff in `validation/ringdriver-proposed.diff`. `RingDriver.java` itself is **not**
modified in R2.

### 4.2 Tick order (normative; corrected in R2, F2)

R1 placed the hook after M7's `if (rings.isEmpty()) { cancelIfEmpty(); return; }`, so an M8 effect without a ring
would never run. R2 order inside `tick()`:

1. `if (!running || closed) return;` `tick = host.tick();` `if (tick == lastPlannedTick) return;` (unchanged guards);
2. prune rings (unchanged M7 rules);
3. `drafts = contributor.drafts(tick)` — **always**, after ring pruning (the registry snapshots owners, collects drafts,
   disposes finished or terminated effects);
4. `if (rings.isEmpty() && !contributor.active()) { cancelIfEmpty(); return; }`;
5. build ring requests (unchanged), then one request per draft with the **same** owner-first viewer selection and
   `RequestContext(draft.requestId, ownerViewerId, worldId, anchor translation)`; R3: a draft failing the driver's
   identity check (ring-range request id, owner not mapped from its viewer id, unknown world id) is dropped first;
6. `lastPlannedTick = tick`;
7. **zero requests** → `lastReport` = an all-zero `EmissionReport` for `tick`; **no planner call**; return (the task keeps
   running because effects are still registered);
8. otherwise exactly one `planner.plan(tick, requests, health.enabledStyles(), config.policy())`, then the unchanged
   emitter and report.

### 4.3 Scheduler lifecycle (normative)

| Event | Rule |
|---|---|
| Startup | `ensureRunning()` (extracted from M7's `start`) creates the single `runTaskTimer(…, 1, 1)` task when not running; called by the ring path and by the registry's start path; idempotent; refused after `close()` |
| Contribution | once per tick (4.2 step 3), also when no ring exists |
| Planning | at most one call per tick, only when at least one request exists |
| Cancellation | `cancelIfEmpty()` cancels only when `rings.isEmpty() && !contributor.active()`; called after ring stops, after registry stops (`effectsChanged()`), and in the tick |
| Stale callbacks | the existing generation token: a callback scheduled before a cancellation never runs `tick()` again, also after an M8-only restart |
| Shutdown | `close()`: `closed = true`, `contributor.close()` (disposes all M8 effects), `stopAll()` (rings), task cancelled; later starts throw |

### 4.4 Shared global capacity (normative; F1)

One rule, used by both start paths: `hasCapacityFor(replacing) = replacing || rings + m8Effects < maxActiveEffects`.

- Ring path: `replacing = rings.containsKey(owner)`.
- M8 path (registry): `replacing = (owner, kind) already registered`; additionally the per-player M8 cap
  `m8.maxEffectsPerPlayer` applies to non-replacing starts.
- **Replacement never needs free capacity.** Admission is independent of activation order (tested for all 120 orders
  of a five-start mix). Freed capacity is usable by either path.
- **M7-only compatibility:** with `TickContributor.NONE`, `m8Effects = 0`, and the rule equals M7's
  `!rings.containsKey(owner) && rings.size() >= maxActiveEffects`. The 10 M7 `RingDriverTest` cases are re-run
  against the reference driver (`RingDriverWithHookM7CompatTest`).
- All checks run on the main thread (`ThreadGuard`), so there is no race between the two paths.

### 4.5 Transactional effect start (normative; R3, F5)

R2's `DynamicEffectRegistry.start` disposed the replaced effect **before** `scheduler.ensureRunning()`. A failure there
then removed the candidate as well, so the player lost a valid effect. R3 orders every step that can fail before one
commit point:

| Step | Action | On failure |
|---|---|---|
| 1 | admission: replacement fits; otherwise the per-player M8 cap and the shared cap (4.4); no mutation | `IllegalArgumentException`; factory and scheduler not called; nothing changed |
| 2 | allocate the request id (`nextId++`) | — (an id consumed by a failed start is never reused) |
| 3 | `factory.create(id, binding)`; verify that the candidate reports this id, the bound owner and the requested kind | candidate (if any) disposed; exception rethrown; registry, old effect, counts and task unchanged |
| 4 | `scheduler.ensureRunning()` (idempotent; on failure the driver resets `running` and advances its generation, so it owns no task and no stale callback can run) | as step 3 |
| 5 | **commit:** `effects.put(key, candidate)`; then dispose the replaced effect | nothing after the commit can fail: a throwing `dispose` (a contract violation) is contained and counted (`disposeFailures`); the new effect stays registered |

**Invariants:**
- the registered count changes only at the commit;
- a replaced effect is disposed exactly once, and only after its successor is registered;
- the old effect keeps its request id and keeps contributing if the replacement fails;
- the shared task is owned by the driver alone, so the registry never creates or cancels it directly.

The ring path in the reference driver is ordered the same way: `ensureRunning()` before `rings.put`. In accepted M7 the
ring replacement cannot hit this failure, because an existing ring implies a running task, so M7 behaviour is unchanged.
Tests: `TransactionalStartTest` (8), with injected failures for the first registration and for a replacement.

**Alternatives considered** (section 12): a separate M8 driver/task (rejected — two plans per tick split budgets and
violate the one-plan rule); rewriting `RingDriver` into a generic `EffectDriver` (rejected for now — larger diff to
proven code); per-effect tasks (forbidden).

## 5. Effect lifecycle (normative)

| Phase | Rule |
|---|---|
| Start | command on the main thread → validate owner online/valid/not dead, world loaded at the owner's chunk, shared global cap (section 4.4), per-player cap (one effect per kind, `m8.maxEffectsPerPlayer`) → construct the candidate with `startTick = Bukkit.getCurrentTick()` and its binding → ensure the shared task runs → **commit** (register) → dispose the replaced effect (section 4.5, transactional). Starting a kind the player already has **replaces** it (new request id), as M7 replaces rings; a failed start changes nothing |
| Update | once per tick in the snapshot phase: `contribute` may return no request (stationary trail, skipped invalid tick, empty activation frame); never more than one |
| Natural end | `finished(tick)` true when local ticks ≥ the effect's lifetime (Aura/Trail: configured lifetime; Activation: 80 ticks); the registry disposes it in the same tick, before planning |
| Termination | snapshot `TERMINATE` (owner offline, invalid, dead, or missing), `/vfxdemo stop` (all of the player's effects, rings included), `/vfxdemo <kind> off`, `stop all` (admin), quit, world unload of an activation's world, plugin disable |
| Disposal | exactly once; clears trail history and references; idempotent; after disposal the effect contributes nothing even if a stale callback runs |
| Shutdown | `onDisable` → `RingDriver.close()` closes the contributor (all M8 effects disposed) → existing `cancelTasks` and pending-task check (M7 logs `remaining owned tasks`) |

Event handling (Paper layer): `PlayerQuitEvent` → stop owner's effects; `PlayerDeathEvent` → stop owner's effects;
`PlayerTeleportEvent` (MONITOR, ignoreCancelled), `PlayerChangedWorldEvent`, `PlayerRespawnEvent` → increment the owner's
discontinuity serial (Aura and Trail continue; see anchor spec section 6); Activation stops on world change or on a
teleport farther than `viewRadius` from its static anchor (M7 ring rule); `WorldUnloadEvent` → stop effects whose
static anchor is in that world.

## 6. Commands (proposal; coexisting with M7)

| Command | Behaviour |
|---|---|
| `/vfxdemo ring [tier]`, `/vfxdemo stop`, `/vfxdemo stop all`, `/vfxdemo status` | **unchanged M7 grammar and parsing** (`DemoCommands` and its 6 tests untouched). `stop` additionally stops the caller's M8 effects (the Paper endpoint's `stop()` calls both); the M7 reply text is kept and an extra line reports M8 effects stopped. `status` appends M8 counts and timings |
| `/vfxdemo aura [simple\|balanced\|heavy]` / `/vfxdemo aura off` | start/replace the caller's aura / stop it |
| `/vfxdemo trail [simple\|balanced\|heavy]` / `/vfxdemo trail off` | start/replace / stop the trail |
| `/vfxdemo activate [simple\|balanced\|heavy]` | play one activation at the caller's current position and yaw; replaces a running one |

- Permission: `bedwenton.vfxdemo.use` for all; viewing others' effects needs `bedwenton.vfxdemo.view` (M7 rule).
- Default tier for M8 commands: **simple** (`m8.defaultTier`); `heavy` refused when `m8.heavyAllowed: false`.
- Players only; console gets the M7 "requires a player" reply.
- Grammar lives in a new `M8Commands` (testable like `DemoCommands`); `PaperCommand` routes `aura|trail|activate` to it
  and everything else to `DemoCommands` unchanged; tab completion merges both (M7's `suggest` results unchanged).
- **Concurrency:** a player may run at most one effect of each kind simultaneously (ring, aura, trail, activation), and
  at most `m8.maxEffectsPerPlayer` M8 effects (default 2, range 1–3). All effects of all players share the global cap
  `maxActiveEffects` (M7 key; default 4, hard ceiling 16) and **one** M6 policy per tick (performance spec).

## 7. Configuration (proposal)

M7 keys and ceilings are unchanged (`DemoConfig` untouched; unknown keys are already ignored by `DemoConfig.from`). A
new nested section `m8:` is parsed by `M8Config` with the same strictness (invalid → enable refused, no fallback). Keys
and ranges are listed in `M8-PERFORMANCE-AND-BUDGETS.md` section 3. Absent section → M8 defaults; M8 commands work only
when `m8.enabled: true` (default **false**, so dropping the new jar into an M7 server changes nothing until opted in).

## 8. Files M8 production would add or change (for owner approval)

| File (paper-adapter) | Change | Reason |
|---|---|---|
| `core/RingDriver.java` | **additive** (section 4; exact delta = `validation/ringdriver-proposed.diff`): optional `TickContributor`, `ensureRunning`/`effectsChanged`/`hasCapacityFor`, corrected tick order, zero-request skip, R3 transactional ring start order and draft identity check; existing constructors and M7-only behaviour unchanged | one shared task, one global plan per tick, one global cap |
| `bukkit/VfxDemoPlugin.java` | wiring: registry, `AnchorSource`, discontinuity listeners, command routing, M8 style resolution, status text | thin Paper layer |
| `src/main/resources/config.yml`, `plugin.yml` | add `m8:` section (disabled); describe new subcommands; plugin version 0.8.0 | opt-in |
| `core/m8/*` (new package `workshop.vfx.paper.m8`) and tests | new | pure core |
| unchanged | `DemoCommands`, `DemoConfig`, `DemoRecipe`, `DemoStyleCatalog`, `DefaultPlanEmitter`, `OwnerAwareRecipientRule`, all M7 tests, root build, M1–M6 | preservation |

**Preservation gate:** all 61 M7 adapter tests and all 415 M1–M6 tests pass **without modification**, the seven datasets
regenerate byte-identically, `paperApiVersion` stays `26.3.build.204-stable` with the recorded SHA-256, and the M7 ring
smoke rows are re-run on a disposable server.

## 9. Particle styles (proposal)

Closed whitelist extension in a new `M8StyleCatalog` (M7's catalogue untouched). All are `Particle.DUST` with
`DustOptions(Color.fromRGB(rgb), size)` — the payload already validated in M7's real-server testing — so no new
particle payload family is introduced:

| Style key | RGB | Size | Used by |
|---|---|---|---|
| `bedwenton:m8_aura_violet` | `0x9B59FF` | 0.8 | Aura orbit, Activation embers |
| `bedwenton:m8_aura_gold` | `0xFFD15C` | 0.7 | Aura halo |
| `bedwenton:m8_trail_cyan` | `0x5CE1FF` | 0.9 | Trail (fresh band) |
| `bedwenton:m8_trail_dim` | `0x2A6F99` | 0.6 | Trail (old band) |
| `bedwenton:m8_flash_white` | `0xFFFFFF` | 1.5 | Activation release |

Activation also reuses M7's `END_ROD` and red `DUST` keys. Unresolvable styles are removed (fail closed); session style
health applies unchanged. **[verify]** visual suitability (dust lifetime/smear while moving) in the smoke test; any
change of particle type is a contract change.

## 10. Implementation sequence and checkpoints

| Checkpoint | Scope | Exit criteria |
|---|---|---|
| **K0** Hook | `TickContributor` hook in `RingDriver`, registry, config parsing, command routing; no visuals | 476/476 existing tests unchanged + the R2 proposed tests ported (`SharedCapacityTest`, `M8OnlySchedulingTest`, `RingDriverWithHookM7CompatTest` against the real modified `RingDriver`); M7 ring smoke re-run |
| **K1 Aura (minimum demonstration)** | `AnchorRules`, `AuraEffect`, styles, `/vfxdemo aura` | offline tests T-A*, T-I*, T-B*; smoke S-A1…S-A9 including two-player isolation; performance P1–P3 recorded |
| **K2 Trail** | `RingBufferTrailHistory`, `TrailEffect`, `/vfxdemo trail` | T-T*; smoke S-T1…S-T7; P4 |
| **K3 Activation** | `ActivationEffect`, `/vfxdemo activate` | T-C*; smoke S-C1…S-C5; P5 |
| **K4 Combined** | overlap, caps, long run | T-M*, T-L*; smoke S-X1…S-X4; P6 soak |

Each checkpoint is independently reviewable; later ones never weaken earlier gates.

## 11. Explicitly out of scope

Non-player entity attachment in production (specified in the anchor spec as a design extension, deferred — open
question Q1); wings, BlockDisplay/ghost blocks, Celestial Arsenal, Phase Cocoon, Astral repairs; gameplay effects;
Folia; Bedrock parity; experimental high-density modes; live-SMP deployment; merges into `main`.

## 12. Alternatives, open questions and risks

**Alternatives.** (a) Separate M8 task — rejected (two plans, split budgets). (b) Generic `EffectDriver` rewrite —
deferred; cleaner long-term, larger diff to proven code. (c) Rebuild the aura scene per tick with baked-in positions —
rejected; the M6 anchor `Pose` already carries motion, so aura recipes are built once. (d) Client-side interpolation —
impossible without a client mod; out of scope. (e) Trail as many single-point requests — rejected (consumes request
cap and fairness slots); one rebuilt scene per trail instead.

**Open questions for review.**
- **Q1** Should non-player entities (armor stands, mobs) be attachable in M8 production, or remain specified-only? R1
  recommends specified-only (players first).
- **Q2** Aura across world changes: R1 continues the aura in the new world (fresh snapshot, no history). Alternative:
  stop like the M7 ring. Owner preference?
- **Q3** Aura and trail on death: R1 stops them (deterministic, matches M7). Alternative: pause until respawn.
- **Q4** Priorities: R1 uses ring ACTION (unchanged), activation ACTION, aura NORMAL, trail AMBIENT with ambient
  reserves 0. Should a small ambient reserve protect trails? (M6 G10 promises capacity only.)
- **Q5** Per-player concurrency default 2 M8 effects (range 1–3): acceptable?
- **Q6** `RingDriver` modification (Option B, exact delta in `validation/ringdriver-proposed.diff`) needs explicit owner
  authorization because it touches an accepted M7 file. R2 does not modify it.

**Risks** (with mitigations) are in `M8-PERFORMANCE-AND-BUDGETS.md` section 7 and `M8-TEST-AND-SMOKE-PLAN.md` section 6.
