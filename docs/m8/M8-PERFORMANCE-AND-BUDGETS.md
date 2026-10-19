# M8 performance safeguards, budgets and measurement plan (R2)

Status: **PROPOSED, revision 2 — design only.** R2: zero-request ticks, synthetic P4, test overrides. No performance result for dynamic effects exists yet. M7's smooth static
ring on a ~2 GB test server, and the older Astral prototype's TPS drops under Heavy/Experimental effects, are **context,
not benchmarks**; good static-ring behaviour does not predict dynamic-effect behaviour. All ceilings below bound work;
none is a claimed safe server value.

## 1. Preserved safeguards (normative)

| Safeguard | How M8 keeps it |
|---|---|
| One global M6 plan per tick | M8 requests join M7's single `planner.plan` call through the `TickContributor` hook (architecture section 4) |
| Allocation-rank fairness | unchanged emitter; M8 effects receive priorities (ACTION/NORMAL/AMBIENT) and unique ids |
| Global and per-viewer limits | unchanged `EmissionLimits` and the aligned policy (`DemoConfig.policy()`): effective M6 placement/world/viewer budgets ≤ `maxCallsPerTick` / `maxCallsPerViewerPerTick` |
| Owner-aware authorization | unchanged `OwnerAwareRecipientRule`, viewer selection and gate |
| Style-failure and invalid-coordinate protection | unchanged `SessionStyleHealth`, fail-closed rule, `WorldPointRules`; M8 adds snapshot validation before them |
| Deterministic termination | lifetime per effect, TERMINATE rules, dispose-once, task cancelled when nothing is active |
| No uncontrolled spawning | only selected M6 placements are sent, per player, `count 0`, `force false` |
| No chunk loading | snapshots read loaded players only; `isChunkLoaded` checks; `Server#getEntity` (future) returns loaded entities only |
| Bounded history | fixed-capacity trail ring buffer (≤ 48), age expiry |
| No per-effect scheduler | M7's single task; M8 adds none |

## 2. Per-tick work model (informative)

With `E` active effects (rings + M8, `E ≤ maxActiveEffects ≤ 16`) and `P` online players:
- snapshots: `E` reads of player state (O(1) each);
- viewer selection: O(E × P) rule checks (M7 code path, unchanged);
- trail rebuild: ≤ 48 points per trail (probe P4 is a **synthetic** benchmark of the two-layer rebuild plus one plan for
  a single trail: a few µs here; indicative only, not a server measurement);
- a registered but stationary trail with no live samples still gets a lightweight update every tick (snapshot,
  validation, O(capacity) expiry scan) and contributes no request;
- **zero requests in a tick** (for example only stationary trails): the global planner is **not called**; an all-zero
  report is recorded; the shared task keeps running while any effect is registered (architecture 4.2, tested in
  `M8OnlySchedulingTest.zeroRequests`);
- M6 plan: one call, bounded by the policy (≤ 1152 generated points, ≤ 4096 placements at the hard ceilings);
- emission: ≤ `maxCallsPerTick` per-player `spawnParticle` calls (hard ceiling 4096), ≤ `maxCallsPerViewerPerTick` per player.

## 3. Configuration (proposal; `m8:` section, strict validation, enable refused when invalid)

| Key | Default | Range | Notes |
|---|---|---|---|
| `m8.enabled` | `false` | boolean | M8 commands disabled until opted in |
| `m8.defaultTier` | `simple` | simple/balanced/heavy | conservative default (the M7 ring keeps its own `defaultTier`) |
| `m8.heavyAllowed` | `true` | boolean | false refuses `heavy` for M8 commands |
| `m8.maxEffectsPerPlayer` | 2 | 1–3 | one per kind in any case; S-X1 test override 3 |
| `m8.auraLifetimeTicks` | 1200 | 20–12000 | |
| `m8.trailLifetimeTicks` | 1200 | 20–12000 | |
| `m8.trailMaxStep` | 8.0 | (minSpacing, 64] | discontinuity threshold, finite |
| `m8.invalidAnchorTicksToTerminate` | 20 | 1–200 | consecutive SKIP_TICKs |

Shared M7 keys still govern the global policy: `maxActiveEffects` (default 4, ceiling 16; S-X1 test override 8) now
counts rings **and** M8 effects with one admission rule for both start paths (architecture 4.4); `generatedPointBudget` (1152), `placementBudget`/`worldPlacementBudget` (2048), `viewerPlacementBudget` (256),
kept caps 24/48/72, `maxCallsPerTick` 2048, `maxCallsPerViewerPerTick` 256. Their M7 ceilings are unchanged.

## 4. Budget arithmetic (informative; defaults)

| Effect | Generated (reservation) | Visible/kept per viewer per tick |
|---|---|---|
| Ring (M7) | 24 / 48 / 72 | 24 / 48 / 72 |
| Aura | 12 / 27 / 36 | 12 / 27 / 36 |
| Trail | ≤ 12 / 24 / 36 (live samples) | same; 0 when stationary |
| Activation | 52 / 78 / 104 | ≤ 16 / 24 / 32 |

- Example: 2 players, each with a balanced aura and trail, both watching both (view permission): generated ≈ 2 × (27 + 24)
  = 102 ≤ 1152; placements ≈ 2 viewers × 102 = 204 per tick ≤ 2048; per viewer ≈ 102 ≤ 256.
- At the hard ceilings, M6 thins or skips by priority and rank, deterministically; nothing exceeds the configured
  budgets (M6 G1/G3, M7 caps). Activation's conservative reservation (104 HEAVY while ≤ 32 visible) can block
  generation for others under a full budget; that is the accepted M6 trade-off (M6 risk R2).

## 5. Measurement plan (normative for checkpoints K1–K4)

Four distinct quantities, never conflated:

| Quantity | Source | Notes |
|---|---|---|
| Planner time | `EmissionReport.planningNanos` (exists) | per tick; report p50/p95/max over a run |
| Emitter time | `EmissionReport.emissionNanos` (exists) | per tick; same statistics |
| M8 snapshot + rebuild time | new `snapshotNanos` counter (M8) | per tick |
| Server MSPT / TPS | Paper `/mspt` and `/tps` (built-in), optionally spark on the **test server only** | before/during/after; idle baseline first |
| Client FPS | F3 debug overlay on each test client, screenshots | client-dependent; reported per client hardware, never generalized |

**Scenarios** (each ≥ 1200 ticks, after a 600-tick warm-up; idle baseline recorded first, same world, same view distance):
P1 idle with plugin enabled; P2 one aura simple / balanced / heavy; P3 two players, auras visible to each other;
P4 trail: sprinting circuit vs standing (expect zero M8 work when standing); P5 activation repeated every 100 ticks;
P6 soak: maximum allowed mix at default config for 30 minutes (memory: heap after full GC via spark or JVM flags,
active-effect count, task count). Record CPU model, RAM, Paper build, Java, view distance, player count.

**Reporting:** tables of p50/p95/max for planner, emitter, snapshot and total M8 time per tick; MSPT and TPS ranges;
client FPS per client; attempted/succeeded/failed calls and skip counters from `EmissionReport`. Thresholds for passing
are an **owner decision** after the first measurements; R1 proposes, as provisional review triggers only: M8 per-tick
time p95 > 1 ms at default config, any TPS below 19.5 attributable to the plugin, or monotonic heap growth over the soak.

## 6. Quality tiers

`simple` default for M8; `balanced` and `heavy` available, bounded by the kept caps and per-tier point counts above.
Experimental/high-density modes are not part of M8 R1 or its implementation unless separately authorized.

## 7. Risks (performance and safety)

| Risk | Mitigation | Residual |
|---|---|---|
| Dust particles persist several ticks, so moving auras smear behind the player | short-lived size choices; smoke-test review; tiers | visual only; may need a different particle (contract change) |
| Many players × effects × viewers scale O(E × P) in viewer selection | E ≤ 16, view radius ≤ 64, caps | measure in P6 |
| Trail rebuild allocates per tick | bounded ≤ 48 points; only when non-empty | GC pressure measured in P6 |
| Activation reservation over-reserves | budgets; ACTION priority short (80 ticks) | documented M6 trade-off |
| Lag spikes delay ticks | no catch-up emission, duplicate-tick guard | effects run "slower" in wall time under lag |
| Owner modifies `RingDriver` (Option B) incorrectly | additive hook, unchanged M7 tests as gate, ring smoke re-run | review |
