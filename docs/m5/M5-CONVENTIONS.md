# M5 conventions

Status: PROPOSED, with the contract. The M1 to M4 conventions remain in force unchanged:
- `docs/CONVENTIONS.md`;
- `docs/m2/M2-CONVENTIONS.md`;
- `docs/m3/M3-CONVENTIONS.md`;
- `docs/m4/M4-CONVENTIONS.md`.

## 1. Spaces and composition

| Space | Meaning |
|---|---|
| shape space | the coordinates of a `Shape` (for example a ring in the XZ plane, about local +Y) |
| motion steps | each step maps its child's space into its parent's: `m.then(child)` applies the child first |
| anchor space | the result of the motion chain, mapped to world by the anchor `Pose` (M1) |
| world | Minecraft coordinates: +X east, +Y up, +Z south, right-handed (M1) |

- World pose of a layer: `anchor.then(step_0).then(step_1)...` (M1 `Pose.then`: parent first). A point goes through
  the last step first.
- **Rotation sense:** `spin` is right-handed about its axis. A positive quarter turn about +Y maps +X to -Z, which is
  counter-clockwise seen from above, the M3 `Spiral` convention. Full Heal's clockwise ring uses negative turns, or
  M3 `horizontalClockwiseFromAbove` shapes.
- **Scale** is uniform and positive (M1). There is no mirroring.

## 2. Units

| Quantity | Unit |
|---|---|
| positions, translations | blocks |
| local ticks, durations, delays, lifetime | ticks (GAME: server ticks; COSMETIC: 50,000,000 ns = 1 tick) |
| spin amounts | turns (1 turn = 360 degrees); `turns` is per pass of the drive timing |
| progress | dimensionless `[0, 1]` (M2) |
| scales | dimensionless |

## 3. Time

- The scene declares GAME or COSMETIC, and the playback and instant must match it.
  - **GAME:** gameplay-synchronized effects. Ticks are exact longs, and an overflowing difference is an
    `ArithmeticException` (M2).
  - **COSMETIC:** smooth client-rate visuals. Nanoseconds wrap like Java `long` subtraction (M2).
- Local ticks come from `Playback.localTicks(now)`. Every clip and drive interprets them through `Timing.sample`
  (half-open passes; LOOP; PING_PONG; delays; AFTER end progress).
- The scene window is `[0, lifetimeTicks)`: before it BEFORE, after it AFTER. Both are always empty frames.
- **M2 note (documented, not changed):** `Timing.loop(...)` has `cycles = Long.MAX_VALUE`. Once the pass index
  reaches about 9.2e18, that is at local ticks of 9.2e18 times the loop duration, M2 reports AFTER. An ACTIVE_ONLY
  looping layer then disappears. This is far beyond any server uptime: even a 1-tick loop needs 9.2e18 ticks, about
  1.5e10 years of game time. `TimeSemanticsTest.extremeInstants` records it at 1e300.

## 4. Frames

- A frame lists batches in layer order, and points in shape order. Hidden layers and empty batches are omitted.
- One point is **one particle at exactly that position**. No spread, count or speed is implied.
- `FrameBatch.timing()` is the layer's own M2 sample (stage, cycle, raw progress). Adapters may use it, for example
  to fade on a cycle change, but M5 does not interpret it further.
- `FrameBatch.pose()` is the layer's world pose, for orientation-aware adapters.
- The sign of zero coordinates is not significant (contract 10.2).

## 5. Identifiers

- Layer names: `[a-z0-9_.-]{1,64}`, unique per scene.
- Style keys: `namespace:path`, `[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}`, similar to Minecraft resource locations but
  **not** resolved against any registry by M5.
- Example namespaces used in this proposal: `bedwenton:` (healing, sparks, water) and `astral:` (Astral Prism). They
  are invented abstract keys, not Minecraft particle names.

## 6. Supported domains (see contract 10.4)

| Item | Supported |
|---|---|
| anchor | any valid M1 `Pose`; positions up to the world border and beyond (precision is eps times the magnitude) |
| motion steps | at most 16; step scales `[1e-9, 1e9]`; `abs(turns) <= 2^20` per pass |
| layers | at most 4,096 per scene |
| points | at most 2^20 per shape and per frame |
| local ticks | any finite M2 value; non-finite gives `ArithmeticException` |

## 7. Separation of mathematical accuracy and visual quality

- **Numerical guarantees:**
  - exact decisions (visibility, scene window, reveal counts, order);
  - exact endpoints for scale and translate;
  - bit-reproducible positions under the normative order;
  - accuracy within `2^-40 (1 + M)(1 + abs(turns))` of a 40-digit oracle (validated).
- **Visual quality** (density, flicker, overdraw, color) is **not** an M5 guarantee. Minecraft quantizes and
  transmits particle positions, and clients render them with their own settings.
