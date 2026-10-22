# Ten-second reference VFX — isolated experiment

Reference: https://youtu.be/LqVTFFpzYz8, **00:00–00:10 only**. The video remains the artistic authority. Browser access
returned the page title “Becoming Minecraft's Honored One” but did not expose playable frames. This implementation
uses the owner's detailed appearance/timing brief; no frame-by-frame viewing, matching measurement or Minecraft
observation is claimed. Reference fidelity remains unverified and must be assessed during independent visual review.
The target dimensions below are prototype choices from that brief, not measurements extracted from the video.

Start from verified K1.2.2 native evidence `92a8f759dd81691e050eda3612eb480d134f473e`, preserving its exact history.
Experimental branch: `experiment/reference-vfx-10s`. The original Arcane Halo recipes, cadence, commands and tests are
byte-identical. This is a separate REFERENCE kind, not K2, K3 activation or K4. No main merge, release or deployment.

## Test command and protection

In a disposable Paper 26.3 server with the existing `m8.enabled: true`, use:

```text
/vfxreference balanced
/vfxreference heavy
/vfxreference simple
/vfxreference status
/vfxreference off
```

No argument uses existing m8.defaultTier. Heavy respects m8.heavyAllowed. Existing `bedwenton.vfxdemo.use` is required
and checked again in command execution; players only. Viewers need the existing view permission except for an owner
with use permission viewing their own effect. No new permissions or automatic starts. No new configuration keys.
Reference off stops only this prototype. Existing `/vfxdemo stop` and administrator `/vfxdemo stop all` also stop it.
Starting reference replaces only the same owner's reference effect. It can coexist with Arcane Halo if existing caps
allow. All current global/per-player capacity, recipient isolation, range, world and fail-closed checks remain intact.

The existing single K0 driver and one global M6 plan serve every effect. New command wiring and additional style keys
are the only changes to the Paper entry point. New code is under workshop.vfx.paper.reference; no engine, planner,
emitter, scheduler, registry, historical style key or existing test is modified. EffectKind gets one additive value.

## Geometry, timing and native appearance

All effect clocks are the existing GAME clock. Lifetime is exactly **200 ticks**, nominally ten seconds at 20 TPS.
The effect follows feet position and body yaw, ignoring camera pitch/head yaw. It continues after teleport/dimension
change when the current anchor validates; death, disconnect, expiry, invalid anchors or stop use existing cleanup.

White END_ROD disc: full radius **1.3**, center feet **+1.55**, backward offset **1.0**. The outline remains a stable
vertical rear circle. Four joined cubic Bézier sweeps form an asymmetric inward comma/spiral, with no duplicated
segment junction. This is new geometry, not the Arcane Halo two-stroke S. M4 samples once; M5 composes placement and
rotation. The entire inner motif turns around local Z once per **80 ticks**, phase zero at age 36. Control points and
samples remain strictly within the outer ring. The disc never rotates into a different plane or swings in front.

| Ages | Prototype behavior |
|---|---|
| 0–19 | Sparse warm motes; no disc or crown |
| 20–35 | Crown and low ground ring begin; motes continue |
| 36–99 | Outline reveals progressively; each of four curves reveals over 14 ticks from ages 44/58/72/86 |
| 40 onward | White/gold star accents alternate; prior layers remain |
| 100–199 | Complete white spiral/outline, crown, sparkles, ambient and ground coexist over their cadences |
| 200 | Stop spawning and clean registration; existing native particles expire naturally |

Age 36 has zero reveal progress; the first sampled white emission is age 40. No layer disappears because of a presumed
camera cut. Optional final flare/fade is omitted. No typography/camera controls, sound, gameplay or cinematic system.

The crown is yellow-lime DUST at feet+2.35 to +2.63, radius 0.43, staggered tips, slow spin and subtle 0.18-block vertical
bob. Two head-level white/gold DUST crosses blink alternately; balanced has three-point accents, heavy four-point
crosses, simple one-point glints. Gold DUST forms faint expanding horizontal rings at feet+0.08, radius 0.28→1.05 over
40 ticks. Sparse warm DUST motes use fixed offsets and bounded vertical motion. No random spawning or hidden clock.
Only END_ROD and DUST are used through the existing exact-point Player.spawnParticle path; no NMS/reflection,
world broadcast, velocity/lifetime hacks, chunk loads or platform API additions.

## Emission bounds and accounting

| Layer | Emission cadence |
|---|---|
| White outline and all curves | Every 4 ticks, effect-age relative |
| Crown | Every 2 ticks |
| White cross | Once per 16 ticks at phase 0, after activation |
| Gold cross | Once per 16 ticks at phase 8, after activation |
| Ground ring | Every 4 ticks |
| Ambient | Every 8 ticks |

Sixteen immutable cadence entries share **five** cached recipes; all reuse original layer/shape objects. Scene evaluation
receives the original Playback and current time, never age modulo 16, so omitted emissions never slow/pause rotation.
Missing ticks are not replayed. All geometry is pre-sampled once per session, reused across owners/replacements.

| Tier | Outline | Four curves total | Crown | White/gold accents | Ground | Ambient | Union points | Largest due frame/reservation |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Simple |8|8|3|1+1|2|1|24|23|
| Balanced |18|12|6|3+3|4|2|48|45|
| Heavy |24|20|8|4+4|8|4|72|68|

White/gold accents alternate, so the union is never emitted all at once. Simple geometry is deliberately coarse;
heavy provides the most readable shape without raising existing 72-point hard ceilings. Existing budgets may thin or
downgrade the prototype. Its NORMAL priority and layer roles retain disc/crown first, curves next, accents last.

Cadence omits whole layers before M6 planning, so omitted layers cost zero generation/placement budget. M6 still
conservatively reserves each selected scene's maximum even before timed layers activate; no accounting rules change.
Odd ticks use the existing empty-scene SKIPPED_UNSUPPORTED outcome, with zero reservations/placements; registration
and safety/expiry checks continue. Unsupported styles, thinning and send failures use the existing path. END_ROD's
vanilla lifetime is unchanged; four-tick emission bounds accumulation but does not guarantee native visual clarity.

## Focused smoke checklist — all Minecraft observations NOT RUN

1. Stop a disposable ordinary Paper 26.3 server; back up known K1.2.2 JAR/config, replace only the experimental JAR,
   preserve permissions/config, enable m8 deliberately, restart. No /reload. Restore backups/restart to roll back.
2. Record ten seconds of `/vfxreference heavy` from normal rear/side/front distances. Compare to reference 00:00–00:10,
   ignoring its camera cuts/title typography. Check silhouette, asymmetric spiral, crown color/height and sparse gold.
3. Check forming at ~1.8s, complete at 5s, sustained layers through 10s, then no new spawns. Inspect lingering END_ROD
   particles and four-tick strobe; tune only in a later authorized pass after observations.
4. Walk/sprint/jump/turn; verify stable rear vertical attachment, body-yaw following, no front swing, head/ground bounds.
5. Test off, replacement, exact expiry, death/quit, teleport/world change, stop owner/admin stop all and plugin shutdown.
   No orphan task, delayed burst or new particles after stop; old client particles may persist naturally.
6. Test two players: use without view sees only own; view sees eligible others; revoke permissions, leave range/world,
   and compare with ring/Arcane Halo concurrently. Confirm original aura/cyan appearance and controls are unchanged.
7. Observe basic server/client performance at equal player/effect counts; check debug accounting/caps. Automated timings
   are synthetic and not TPS/MSPT/FPS, network or native particle benchmarks. No live SMP deployment.

Automated tests cover back-relative geometry, continuous rotation, phases/cadence, head/ground placement, lifecycle,
permissions/identity, replacement, shared scheduling/budgets and recipient isolation. Existing 611 tests remain exact.
No automated evidence establishes artistic similarity, native persistence, camera-dependent readability or live performance.
