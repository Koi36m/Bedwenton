# M8 anchor and motion specification (R3)

Status: **PROPOSED, revision 3 — design only.** R2 adds the trail's two clocks (section 5.1, F3). R3 makes snapshot
identity and freshness fail closed and splits validation between the Paper adapter and the pure core (sections 3.2–3.4, F6). Normative unless marked informative. Paper facts cite
`validation/paper-api-m8-signatures.txt` (PaperMC source `09ee222`, near the pinned build; **[verify]** against the
pinned jar `26.3.build.204-stable` at checkpoint K0).

## 1. Anchor identification

- An attached effect is bound to its **owner**, identified by `UUID` (`Entity#getUniqueId`). The effect never stores
  `Player`, `Entity`, `World` or `Location` objects; only UUIDs, session ids (M7 `DefaultSessionIds`) and numbers.
- **Resolution** each tick, on the main thread, by the Paper layer: `Server#getPlayer(UUID)` for players (M8 showcases).
  The extension for other entities (Q1, not in production M8) would use `Server#getEntity(UUID)`, which returns only
  loaded entities and never loads chunks; the snapshot would then use `LivingEntity#getBodyYaw` where available,
  otherwise `Location#getYaw`.
- If resolution fails (null) the snapshot is **absent**: the effect terminates (section 3.3).

## 2. Coordinate conventions (verified)

- **World axes:** Minecraft/Paper: +Y up; feet position from `Entity#getLocation()` (block coordinates, doubles).
- **Yaw** (`Location#getYaw`, `LivingEntity#getBodyYaw`), degrees, documented in Paper: 0 (or 360) faces +Z, 90 faces
  −X, 180 faces −Z, 270 faces +X; increasing yaw turns right. Values are not guaranteed to be within [0, 360); any finite
  float is accepted and reduced mathematically (sine/cosine are periodic; no wrap-around step is needed).
- **Facing** for yaw ψ (radians): `forward = (−sin ψ, 0, cos ψ)`.
- **Pitch** (`Location#getPitch`): 0 level, +90 down, −90 up. Recorded in snapshots; **never** used to orient the M8
  showcases (an aura must not tilt when the player looks up or down).
- **Local frame of attached effects:** +X left, +Y up, +Z forward — the same handedness and axes as Minecraft's caret
  notation (`^left ^up ^forward`). M3 `Spiral.circle` lies in the local XZ plane about +Y, so rings are horizontal.

## 3. Snapshots

### 3.1 When

Exactly once per effect per tick, inside `RingDriver.tick()` after ring pruning and before the single plan (architecture
section 4). The snapshot, the request's anchor pose, the request context's anchor and the viewer selection all use the
**same** observation; the emitter's gate re-checks viewers in the same tick (M7).

### 3.2 Content (SPI `AnchorSnapshot`, R3)

`tick, owner UUID, ownerViewerId, world UUID, worldId, x, y, z, bodyYaw, headYaw, pitch, minHeight, maxHeight,
chunkLoaded, online, valid, dead, discontinuitySerial`. The snapshot is an **untrusted carrier**: the core re-checks
every identity and freshness invariant (section 3.3) before reading any field.

**Binding (SPI `AnchorBinding`).** When an effect starts, the registry binds it to `(owner UUID, ownerViewerId)`, with
the viewer id taken from M7 `SessionIds.viewerId(owner)` at that moment. The binding never changes. Every draft carries
the **bound** owner and viewer id, never the snapshot's. Owner-aware visibility (M7 `OwnerAwareRecipientRule`: the owner
viewer needs `use`, every other viewer needs `view`) is therefore decided from the binding, so a malformed snapshot
cannot make another player the "owner" of an effect.

### 3.3 Validation (SPI `AnchorRules.validate(snapshot, binding, tick)`, fail closed, R3)

The checks run in this order, and the first failure wins. Each rejection is counted per effect by reason
(`AnchorRejection`).

| # | Condition | Result | Reason |
|---|---|---|---|
| 1 | snapshot absent (owner not resolvable) | `TERMINATE` | `OWNER_UNRESOLVED` |
| 2 | `snapshot.owner != binding.owner` | `TERMINATE` | `OWNER_MISMATCH` |
| 3 | `snapshot.ownerViewerId != binding.ownerViewerId`, or `SessionIds.viewer(binding.ownerViewerId) != binding.owner` (id released on quit and reassigned) | `TERMINATE` | `VIEWER_ID_MISMATCH` |
| 4 | `!online`, `!valid` or `dead` | `TERMINATE` | `OWNER_GONE` |
| 5 | `snapshot.tick != planning tick` (stale **or** future) | `SKIP_TICK` | `STALE_OR_FUTURE` |
| 6 | `world == null`, `worldId < 0`, or `SessionIds.world(worldId) != world` | `SKIP_TICK` | `WORLD_INCONSISTENT` |
| 7 | any of x, y, z, body yaw, head yaw not finite | `SKIP_TICK` | `NON_FINITE` |
| 8 | `minHeight >= maxHeight`, or y outside `[minHeight, maxHeight)` (the same half-open range as M7 `WorldPointRules`) | `SKIP_TICK` | `OUTSIDE_WORLD_HEIGHT` |
| 9 | `!chunkLoaded` | `SKIP_TICK` | `CHUNK_NOT_LOADED` |
| — | otherwise | `READY` | `NONE` |

- **SKIP_TICK:** no draft this tick and the effect stays registered. 20 consecutive skipped ticks give `TERMINATE`.
- **TERMINATE:** the effect is stopped and disposed in this tick.
- **Identity failures (2, 3) are never transient.** They mean the adapter returned another player's data, or the owner
  left the session. Ending the effect is therefore the fail-closed choice.
- **Freshness and world failures (5, 6) can be transient,** for example a world unloading during a tick. They skip the
  tick and are bounded by the 20-tick rule.

**Draft identity, checked again below the effect (defence in depth):**

- `DynamicEffectRegistry.drafts` checks that each draft has its effect's request id, kind, bound owner and bound viewer id.
  If not, the draft is dropped, counted (`identityViolations`), and the effect is stopped this tick.
- The driver (`RingDriverWithHook`, the proposed `RingDriver` delta) refuses any draft whose:
  - request id is below `2^62` (the ring id range);
  - owner is not the player that `SessionIds` maps the draft's viewer id to;
  - world id is unknown to `SessionIds`.

  A refused draft gets no request and no context (`rejectedDrafts`). A non-finite anchor cannot reach the driver: the
  engine's `Pose` constructor rejects it.

### 3.4 Responsibilities: Paper snapshot adapter versus pure core (R3)

| Concern | Paper adapter (`bukkit` package; cannot be tested offline) | Pure core (`DefaultAnchorRules`, registry, driver; tested offline) |
|---|---|---|
| Owner resolution | `Server#getPlayer(UUID)` with the **bound** UUID; null gives an absent snapshot; never `getPlayerExact` or name lookups | `OWNER_UNRESOLVED`; checks that the returned UUID equals the binding |
| Owner identity | fills `owner = player.getUniqueId()`, `ownerViewerId = SessionIds.viewerId(player.getUniqueId())` | checks 2 and 3; drafts use the binding only |
| Freshness | `tick = Bukkit.getCurrentTick()` read during the snapshot phase of this tick; never cached between ticks | check 5 against the driver's `host.tick()` |
| World identity | `world = player.getWorld().getUID()`, `worldId = SessionIds.worldId(world)` | check 6 (`SessionIds.world(worldId)` must map back) |
| Position, yaw | `getLocation()`, `getBodyYaw()`, `Location#getYaw()`; copied as-is, never clamped or repaired | check 7 |
| World height | `minHeight = World#getMinHeight()`, `maxHeight = World#getMaxHeight()` for the **same** world | check 8 |
| Loaded chunk | `chunkLoaded = World#isChunkLoaded(floor(x) >> 4, floor(z) >> 4)` for the same world; **no** `getChunkAt`, `getBlockAt` or other loading call | check 9 |
| Liveness | `isOnline`, `isValid`, `isDead` | check 4 |
| Thread | main thread only (M7 `ThreadGuard`) | all entry points `ThreadGuard`-checked (unchanged M7 rule) |

The adapter performs no validation of its own beyond null resolution. It reports what Paper returns, and the core
decides. Every emitted point is also checked by M7's own height, border and finite-point rules, unchanged. Paper method
names are in `validation/paper-api-m8-signatures.txt`; `getMinHeight`, `getMaxHeight` and `isChunkLoaded(int, int)` are
already used by M7. They must be **[verified]** against the pinned jar at K0.

## 4. Local-to-world pose (normative; verified by probe P1/P2)

For a READY snapshot, orientation mode `m` and lift `L` (world-space vertical offset, applied before rotation):

```
ψ  = toRadians(yaw)            yaw = bodyYaw (BODY_YAW, default) | headYaw (HEAD_YAW) | 0 (WORLD_FIXED)
q  = Quat(cos(−ψ/2), 0, sin(−ψ/2), 0)      computed with StrictMath; never Quat.axisAngle (Math trig, M5 rule)
anchorPose = Pose(Vec3(x, y + L, z), q, 1)
worldPoint = anchorPose.then(layerMotionPose(local ticks)).toWorld(localPoint)      (M5 evaluate does this)
```

Rotation about +Y by −ψ maps local +Z to `(−sin ψ, 0, cos ψ)` (forward) and local +X to `(cos ψ, 0, sin ψ)` (left).
Probe P1 shows the mapping for yaw 0, 45, 90, 180, 270, −90, 359.5 and 720 with maximum error 1.6e-16. Scale is always 1
(no player-size scaling in M8). The pose is rebuilt every tick; nothing is cached across ticks except the recipe.

## 5. Deterministic animation timing (normative)

- `startTick` = `Bukkit.getCurrentTick()` when the effect starts; `playback = Playback.start(TimePoint.gameTick(startTick))`;
  `now = TimePoint.gameTick(tick)`; local ticks = `tick − startTick` (M2).
- All motion (spin, scale, translate, reveal) is a function of local ticks through M2 `Timing` and M3 `Easing` inside M5:
  the frame at a given local tick is a pure function of (recipe, tier, anchor pose, local tick). Probe P2 evaluates an
  aura at ticks 1000–1123 against an independent formula (3.6e-15 blocks).
- **No interpolation between ticks.** The server emits at tick positions only; there is no client-side interpolation
  (no client mod). Smoothness is therefore 20 Hz positional sampling; this is stated, not hidden.
- Server lag (MSPT > 50) delays ticks but never changes which local tick a frame represents; skipped wall-clock time is
  not "caught up" with extra emissions (the duplicate-tick guard also forbids two plans in one tick).
- `frameOrdinal = local ticks` (M6 thinning phase).

### 5.1 Effect clock versus scene clock (normative; R2, F3)

Every effect has an **effect clock**: `age = tick − startTick`. It decides lifetime (`finished` when
`age ≥ lifetimeTicks`) and supplies `frameOrdinal = age` to M6, so thinning offsets keep rotating over the effect's
whole life.

- **Aura and Activation:** the scene clock **is** the effect clock (`Playback.start(gameTick(startTick))`, `now =
  gameTick(tick)`); their scenes are built once and live as long as the effect.
- **Trail:** the scene is rebuilt every tick with lifetime 2 ticks, so its **scene clock is restarted every tick**:
  `playback = Playback.start(TimePoint.gameTick(tick))`, `now = TimePoint.gameTick(tick)` → scene-local tick 0, which is
  always inside the 2-tick window. Using the effect's start tick there (the R1 wording) would put every rebuilt scene at
  local `age ≥ 2`, stage AFTER, and nothing would be emitted after the second tick. The effect clock still runs:
  lifetime ends at `age = trailLifetimeTicks`, and `frameOrdinal = age`.
- Executable evidence: `TrailTimingTest` (emissions at every tick to local 299, including 3, 50, 100; frame ordinal =
  age; scene local tick 0; lifetime end exactly at the configured age; expiry; thinning phase = `floorMod(age, g)`).
  Mutants F3a (scene clock = effect clock) and F3b (frame ordinal from the scene clock) are caught.

## 6. Discontinuities: teleports, world changes, respawn (normative)

- The Paper layer keeps `discontinuitySerial` per owner, incremented in `PlayerTeleportEvent` (MONITOR,
  ignoreCancelled — only teleports that happen), `PlayerChangedWorldEvent` and `PlayerRespawnEvent`.
- **Aura:** carries no history, so it simply renders at the new snapshot position on the next tick — no streak exists to
  prevent. Across a world change it continues in the new world (Q2), with the new world id in request and context.
- **Trail:** a changed serial, a changed world id, or a per-tick displacement greater than `maxStep` starts a **new
  segment** (section 7); on world change the history is cleared. No point is ever generated between samples of
  different segments, so no streak can connect distant locations.
- **Activation:** static anchor (start position and yaw); stops on world change, on teleport farther than `viewRadius`
  from its anchor, on death and on quit (the M7 ring rule).
- Ordinary movement (walk ≈0.22, sprint ≈0.28, sprint-jump peaks ≈0.6 blocks/tick; elytra and riptide faster) stays
  continuous because `maxStep` defaults to 8 blocks/tick; anything above is treated as a discontinuity even without an
  event (covers plugin-driven position changes that bypass teleport events). **[verify]** the event behaviour for
  vehicles and elytra in smoke tests.

## 7. Bounded movement history (normative; SPI `TrailHistory`, `TrailRules`)

State: a fixed-capacity ring buffer of `TrailSample(tick, x, y, z, segment)`, the last observed position, world id and
serial, and the current segment number. Capacity is fixed at construction (tier: 12 / 24 / 36; ceiling 48); the buffer
never grows.

On each READY snapshot (at most once per tick):

1. If world id or serial changed since the last observation, or this is the first observation: clear (world change) or
   start a new segment (serial change), record the position as the newest sample, done.
2. `d = |p − last|`. If `d > maxStep`: new segment, newest sample = p, done.
3. If `d < minSpacing` (default 0.35): add nothing (stationary or rotation only — yaw is not an input at all).
4. Otherwise add `n = min(maxSamplesPerTick, floor(d / minSpacing))` samples evenly placed on the segment from the newest
   sample toward p, the last exactly at p. Each insertion overwrites the oldest sample when full.

`live(tick)` drops samples with `tick − sample.tick > maxAgeTicks` (default 30) and returns at most `capacity` samples,
newest first. Consequences: a stationary player's trail empties within `maxAgeTicks` and then contributes **no request
and no planning work**; memory per trail is O(capacity) constant; work per tick is O(capacity).

**Stationary active trail (normative).** The effect stays registered until its lifetime ends or it is stopped. Each tick
it still receives one lightweight update — owner snapshot, validation, `observe` (no sample), `live` expiry scan of at
most `capacity` entries — so that movement resumes the trail immediately and lifetime and termination are detected. It
contributes no draft; if no other effect contributes a request in that tick, the driver skips the global planner
entirely (architecture section 4.2 step 7) and records an all-zero report; the shared task keeps running because an
effect is registered.

Rendering (showcase B): the trail's anchor pose is `Pose.ofTranslation(newest live sample)` (identity rotation); live
samples become local offsets `sample − newest`, split by age into a fresh and an old layer (layers and roles only for
non-empty groups, since M6 accepts roles only for existing layers), and one `EffectScene` + `EffectRecipe` is rebuilt
for that tick and evaluated on the restarted scene clock (section 5.1). Probe P4 is a synthetic benchmark of this
two-layer rebuild (microseconds here; indicative only). Segments are not drawn as lines — only sample points
are emitted — so even adjacent segments are never visually joined.

## 8. Invalid, missing or stale data (summary)

| Situation | Handling |
|---|---|
| Owner offline, quit, kicked, invalid, dead | quit/death listeners stop at once; snapshot TERMINATE as backstop |
| Non-finite position/yaw | SKIP_TICK; 20 in a row → TERMINATE |
| Snapshot of another owner, or another viewer id (R3) | TERMINATE (`OWNER_MISMATCH`, `VIEWER_ID_MISMATCH`); drafts always carry the binding |
| Stale or future snapshot tick (R3) | SKIP_TICK (`STALE_OR_FUTURE`); 20 in a row → TERMINATE |
| Feet outside world height, chunk not loaded (R3) | SKIP_TICK; no chunk is loaded |
| World unloaded / unknown world id / world UUID and id disagree | SKIP_TICK (`WORLD_INCONSISTENT`); 20 in a row → TERMINATE |
| Stale callback after stop/disable | M7 generation token and `running`/`closed` flags; disposed effects return no request |
| Request/context mismatch | impossible by construction (same snapshot, same id); M7 emitter rejects it anyway |
| Viewer changed permission/world/range between selection and emission | M7 owner-aware gate re-check (unchanged) |
