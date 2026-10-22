# Dual-stream END_ROD hypothesis test

This isolated experiment begins at native Reference VFX source `a372703e61a9a3014031d49d8ab9e2c0a859c3ea` /
GitHub-tested source `9108e679b012f111b5a1fc51039965d213b43947` (631 tests). No existing test expectations, M1–M6
production/contracts, Arcane Halo classes or /vfxreference classes are changed. New branch: experiment/dual-stream-end-rod.

## Video evidence and limits

The owner supplied 1022.mp4 (1920×1080, 30fps, 10.472s including its ending). Inspection was restricted to 00:00–00:10:
a 2fps contact sheet plus a 10fps sequence around 6.0–6.8s. White curved lobes change orientation within a nearly circular
white envelope; the closer shot shows two hook-like arms around the player. Brightness/occlusion and camera changes
limit interpretation. The clip does **not** establish emitter count, source location, particle age, spawn API, velocity,
or whether any separate outline is plotted. Two co-located opposing streams are the owner's testable hypothesis,
not a confirmed reconstruction technique. No new Minecraft experiment has been observed or compared yet.

The experiment deliberately contains **no circle, Bézier, crown, ground, ambient or pre-plotted trail**. M6 sees two
identical spawn-origin points solely to budget the two native directional particles. They are not sent as stationary
geometry. Old effects remain separately available and must be stopped when inspecting this experiment alone.

## Motion and controls

```text
/vfxstreams normal
/vfxstreams slow
/vfxstreams fast
/vfxstreams stop
/vfxstreams off
/vfxstreams status
```

No argument selects normal. Existing m8.enabled, use permission and player-only checks apply. Fast also requires
m8.heavyAllowed; all presets remain a two-point SIMPLE request, never promoting quality or bypassing budgets.
Speeds are Paper extra multipliers: slow 0.10, normal 0.15, fast 0.22. No claim that these equal measured client velocities.
Lifetime: 200 GAME ticks. At ages 0,4,...196 emit one opposing pair; no catch-up on missed/invalid ticks. At 20 TPS there
are 50 steps/100 particles per eligible viewer over ten seconds. Existing particles expire naturally after stop.

For age since activation:

```text
theta = PI/2 + 2*PI*(age % 80)/80
localA = (cos(theta), sin(theta), 0)
worldA = currentBodyYawRotation(localA)
worldB = -worldA
```

At age 0 A points up and B down; at 20 A left, at 40 down, at 60 right, at 80 up again. The vectors rotate together in the
same angular direction and stay opposed. Current feet-relative origin is (0,1.55,-1.0) transformed with current body
yaw. Head yaw/pitch do not rotate the plane. Age/Playback continue independently of emission cadence. Origin follows
walking/jumping/turning/teleport/world changes through existing anchor validation; already spawned particles move
in their own native world-space trajectories and do not get reattached to the player.

## Small adapter extension; frozen engine unchanged

- DirectionalEmission is an immutable adapter-only finite unit vector / bounded speed payload, with exactly two
  experiment style keys. Each key still resolves to native END_ROD, preserving every historical catalogue entry.
- EffectDraft and RequestContext gain immutable direction maps. Their original constructors remain available and
  supply empty maps. The identity guard requires both keys on STREAMS drafts and prohibits payloads on other kinds.
- RingDriver copies validated metadata into the same request-ID context used by the existing one global M6 plan.
  M6 continues to plan/reserve/thin two actual particle placements; no engine API or accounting contract changes.
- DefaultPlanEmitter requires a valid same-origin two-style opposing pair with equal speed. Missing/malformed data
  skips the delivery as INVALID_DIRECTIONAL_CONTEXT. A thinned one-point recipient share is skipped as
  INCOMPLETE_STREAM_PAIR, with its planned share fully accounted; that reservation is not reallocated late.
- Both call slots must fit the emitter's global/per-viewer ceilings before starting a pair. Disabled styles suppress
  the pair. Existing context, recipient authorization, finite position/world checks, limits and health/accounting run
  on the existing path. Quiet ticks create no stream request; the existing task remains active until normal cleanup.
- ParticleSink gains a default directional method that throws when unsupported. It never falls back to its original
  positional send. Paper overrides it with Player.spawnParticle(END_ROD, origin, count 0, directionXYZ, speed, null,false).
  Existing positioned effects keep their original exact API call and metadata-free behavior.

Paper's official [particle documentation](https://docs.papermc.io/paper/dev/particles/) describes count 0 as one
particle using offsets×extra and lists END_ROD as directional. It also warns that effective speed varies by type.
The pinned Paper 26.3 API is compiled/checked; client trajectory, lifetime, damping and visual appearance still require
the smoke experiment. No NMS/reflection/packets, second task/planner, global broadcast or lifetime override.

Accounting is unchanged: planned placements equal attempts + skipped points + placements in skipped recipients +
placements in skipped deliveries; attempts equal successful + failed calls. Each directional call counts as one
placement/attempt. A native send failure can make only one particle of a planned pair visible; two independent sends
cannot be rolled back atomically. Failures remain counted and bounded. Permission revocation stops future sends;
previous client particles naturally persist. Two new skip reasons distinguish invalid metadata and partial pairs.

## Focused Minecraft smoke checklist — NOT RUN

1. Use disposable ordinary Paper 26.3. Stop/back up the known JAR/config, install only this experimental JAR and restart;
   preserve permissions/config and enable m8 deliberately. Restore backups/restart to roll back. No /reload or live SMP.
2. Stop your other effects with existing /vfxdemo stop. Start /vfxstreams normal and observe just the pair origin from
   behind/side. At activation, one particle should travel up and one down; inspect both moving away from the same point.
3. Record the first 80 ticks: check up/down, left/right at 20, reversed vertical at 40, right/left at 60 and recurrence at 80.
   Compare emitted directions, not the whole accumulated cloud, whose particles have different ages.
4. Compare normal/slow/fast in separate clean runs after old particles clear. Record how far/fast particles travel,
   persistence/buildup and whether a readable rotating S/circle illusion emerges. Compare reference 00:00–00:10 without
   treating camera changes as particle changes. Decide whether this hypothesis works before any geometry tuning.
5. Cadence is fixed at one pair per 4 ticks in this first pass. Check for strobing/sparsity and note whether a different
   cadence warrants a later authorized pass. There is no runtime cadence adjustment or native lifetime override.
6. Walk/jump/turn, teleport/change dimension; inspect current origin/plane and old particles left behind. Stop,
   replace, expire at 200ticks, die/quit and shut down; no new spawns/orphan task after cleanup. Old particles may remain.
7. Two players: use-only owners see their own, view permission sees eligible others; revoke permission/change
   range/world. Run concurrently with original aura/reference only for a focused regression if anything looks wrong.
   Observe debug budgets/accounting and basic performance; no complete old-checkpoint visual replay without regression.

No Minecraft visual success, exact reference match or performance result is claimed. Preserve the hypothesis boundary
until owner evaluation. No K2–K4, engine redesign, merge, release or live deployment.
