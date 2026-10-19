# M8 showcase designs (R2)

Status: **PROPOSED, revision 2 — design only.** R2: trail clocks (B) and the combined-test configuration. All geometry uses accepted M3/M4/M5 APIs; all timing uses M2. Point
counts below were evaluated with the accepted engine by `validation/M8DesignProbe.java` where marked (P2, P3).
Tier semantics follow M6: the requested tier is a strict ceiling (G11); kept caps per tier come from the policy.

## A. Orbiting Aura — priority 1 (minimum demonstration, checkpoint K1)

**Recipe** `bedwenton-m8-aura`, priority NORMAL, built **once** at enable (no per-tick rebuild), scene lifetime =
`m8.auraLifetimeTicks` (default 1200 = 60 s; range 20–12000). Orientation `BODY_YAW`, lift 0.

| Layer | Shape (local frame) | Motion (parent → child) | Timing | Style | Role | SIMPLE / BALANCED / HEAVY |
|---|---|---|---|---|---|---|
| `orbit` | `Spiral.circle(0.9)`, uniform, EXCLUDE_END | `fixed(translate(0, 1.0, 0))` then `spin(+Y, 1 turn, phase 0, loop(60), LINEAR)` | `loop(60)` | `m8_aura_violet` | ESSENTIAL | 12 / 18 / 24 |
| `halo` | `Spiral.circle(0.45)` | `fixed(translate(0, 2.15, 0))` then `spin(+Y, −1 turn, loop(80), LINEAR)` | `loop(80)` | `m8_aura_gold` | STANDARD | 0 / 9 / 12 |

- Waist ring at 1.0 block, head halo at 2.15 blocks; the orbit turns once per 3 s counter-clockwise from above, the halo
  the other way once per 4 s. The spin is relative to the body yaw, so turning the body turns the formation and the
  orbit keeps its own rotation (probe P2: exact against an independent formula while moving and turning).
- Points per frame: 12 / 27 / 36 (maximum per frame = reservation). Placements per viewer per tick equal the kept count.
- **Behaviour:** follows walking, sprinting, jumping (y follows feet; no smoothing), turning (body yaw). Teleport: snaps to
  the new position next tick. World change: continues in the new world (Q2). Death, quit, disable: stops. Lifetime end:
  stops itself.
- **Not used:** pitch, head yaw, interpolation, velocity extrapolation.

## B. Movement Trail — priority 2 (checkpoint K2)

**Recipe** `bedwenton-m8-trail`, priority AMBIENT, **rebuilt each tick from history** (one scene per trail per tick,
only when the history is non-empty). Effect lifetime `m8.trailLifetimeTicks` (default 1200).

| Parameter | SIMPLE | BALANCED | HEAVY | Range |
|---|---|---|---|---|
| capacity (samples) | 12 | 24 | 36 | 1–48 |
| minSpacing (blocks) | 0.5 | 0.35 | 0.25 | (0, 4] |
| maxSamplesPerTick | 2 | 2 | 3 | 1–4 |
| maxAgeTicks | 20 | 30 | 40 | 1–200 |
| maxStep (blocks/tick) | 8 | 8 | 8 | (minSpacing, 64] |

- Layers per rebuilt scene: `fresh` (age ≤ maxAge / 2; `m8_trail_cyan`, ESSENTIAL) and `old` (`m8_trail_dim`,
  DECORATIVE), each present only when non-empty; points sit 0.1 block above the feet. Scene lifetime 2 ticks,
  `Timing.once(2)`, `Reveal.ALL`, `Motion.IDENTITY`; anchor = newest live sample (identity rotation); the shape is the list
  of live samples as local offsets.
- **Two clocks (R2, F3):** the rebuilt scene is evaluated with `Playback.start(gameTick(tick))` and `now = gameTick(tick)`
  (scene-local tick 0 every tick); the effect clock `age = tick − startTick` governs the trail's lifetime and is passed as
  M6 `frameOrdinal`. Executable: emissions continue through local tick 299 (`TrailTimingTest`).
- Only **positional** movement adds samples (history algorithm, anchor spec section 7); camera rotation and standing
  still add nothing, and an empty history means no request at all.
- Teleports, world changes, respawns and steps > maxStep start a new segment; points of different segments are never
  joined, and no intermediate points are generated across a segment break.
- Expiry: deterministic by sample tick and maxAgeTicks; capacity bounds memory even while sprinting.

## C. Animated Activation — priority 3 (checkpoint K3)

**Recipe** `bedwenton-m8-activation`, priority ACTION, built once; scene lifetime 80 ticks; **static anchor** = feet
position and body yaw at command time (it does not follow the player). Phases are sequential layers driven by M2
`Timing.once(d).withDelay(t)` with `Visibility.ACTIVE_ONLY`:

| Phase | Local ticks | Layer | Shape | Motion / reveal | Style | SIMPLE / BALANCED / HEAVY points |
|---|---|---|---|---|---|---|
| Materialization | 0–19 | `form` | circle r 1.5 at y 0.1 | `Reveal.PREFIX` over `once(20)` | END_ROD | 16 / 24 / 32 |
| Buildup | 20–49 | `column` | `Spiral.helix(0.6, rise 2.0, 2 turns)`, INCLUDE_END | `spin(+Y, 1 turn)` over `once(30).withDelay(20)` | red DUST | 12 / 18 / 24 |
| Release | 50–59 | `burst` | circle r 1.0 at y 1.0 | `scale(1.0 → 3.5, QUAD_OUT)` over `once(10).withDelay(50)` | `m8_flash_white` | 16 / 24 / 32 |
| Dissolution | 60–79 | `embers` | circle r 2.5 | `translate(y 0.2 → 1.6, QUAD_IN)` over `once(20).withDelay(60)` | `m8_aura_violet` | 8 / 12 / 16 |

Probe P3 (HEAVY) confirms exact phase boundaries: local 0 → 0 points (prefix floor(0·n) = 0, M6 reports
`SKIPPED_INACTIVE`), 1 → 1, 10 → 16, 19 → 30 (form); 20–49 → 24 (column); 50–59 → 32 (burst); 60–79 → 16 (embers);
80 → stage AFTER, 0 points. Generation reservation (`maxPointsPerFrame`) = 104 HEAVY / 78 BALANCED / 52 SIMPLE, while at
most 32 are visible at once (the conservative M6 reservation; see performance spec). At local 80 the effect reports
`finished` and is disposed in the same tick: no task, history or state remains.

No damage, healing, combat, enchantment or cooldown logic is involved; activation is purely visual.

## Coexistence

A player may run a ring (M7), an aura, a trail and an activation, subject to one-per-kind, `m8.maxEffectsPerPlayer`
(default 2) and the global `maxActiveEffects`. All of them are planned together in the single M6 call each tick:
ACTION (ring, activation) before NORMAL (aura) before AMBIENT (trail), rotating within each priority, with global,
per-world and per-viewer placement budgets aligned to the emission caps (M7 `DemoConfig.policy()`).

**Combined showcase configuration (R2, F4).** Two players each running a ring, an aura, a trail and an activation need
8 concurrent effects and 3 M8 effects per player. The conservative production defaults (`maxActiveEffects: 4`,
`m8.maxEffectsPerPlayer: 2`) deliberately refuse that. The combined smoke test S-X1 therefore uses an explicit,
documented **test override** on the disposable server only: `maxActiveEffects: 8`, `m8.maxEffectsPerPlayer: 3`,
`m8.enabled: true` (all other keys default). Production defaults are not changed.

**R3 (F7).** With one effect per kind and four kinds, two players fill all 8 (player, kind) slots, so any further start
by A or B is a replacement. R2's "ninth effect refused" step in S-X1 was therefore unreachable.
- S-X1 keeps the two-player, eight-effect visual demonstration and now asserts that **replacement at capacity succeeds**.
- The **global-cap refusal** is asserted in S-X2, at the production defaults with the same two players. There the fifth
  unique effect is reachable and refused on both start paths.
- An optional S-X1b covers the case with a third test account: C's first effect is the ninth unique effect, and it is
  refused.

All three scenarios are replayed offline in `ShowcaseCapacityTest`.
