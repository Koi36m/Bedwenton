# M7 architecture and implementation contract: Paper 26.3 adapter and opt-in demo plugin (R3)

Status: **PROPOSED, revision 3. Not authorized for implementation.** R2 corrected owner-aware authorization,
emission order and style disabling (`M7-R2-CHANGES.md`). R3 makes coordinate handling fail closed, rejects
inconsistent request contexts, and adds placement accounting (`M7-R3-CHANGES.md`). This document does not claim Paper, Folia or
Geyser runtime compatibility. Compatibility is established only by the real-server smoke test in
`M7-TEST-AND-SMOKE-PLAN.md`.

| | |
|---|---|
| Accepted M6 baseline | `c43a66e656a77aa82bd72d8ffe27f1a037240e99` (tree `ef496f7b0c34796cdd30fe35d79cfd4e0c071ece`) |
| Kickoff | `74fefd3` on `m7-paper-adapter-kickoff` (`docs/m7/M7-KICKOFF.md`) |
| Target | ordinary Paper 26.3 server, Java 25. API signatures were read from PaperMC/Paper `09ee222482646f8ed84413aea89d0108653af938` (`mcVersion=26.3`, `apiVersion=26.3`, `channel=STABLE`); see `M7-R1-REPORT.md` section 2 |

**Requirement words:** **must** is normative; **should** is recommended; **[verify]** marks something that must be
confirmed against the pinned build or a real server before production evidence is accepted.

## 1. Shape

```
Koi36m/Bedwenton
├── build.gradle, settings.gradle, src/...      M1-M6, unchanged; root build unaware of the adapter
└── paper-adapter/                              NEW isolated Gradle build (its own settings.gradle)
    ├── settings.gradle                         rootProject.name = 'bedwenton-vfx-paper'; includeBuild('..')
    ├── build.gradle                            compileOnly paper-api (exact pin); implementation engine
    ├── src/main/java/workshop/vfx/paper/core/  pure Java: no org.bukkit / io.papermc imports
    ├── src/main/java/workshop/vfx/paper/bukkit/ Paper-facing: plugin, command, task, listeners, sink, gate
    ├── src/main/resources/plugin.yml, config.yml
    └── src/test/java/...                       offline JUnit tests (core only, fake sink and gate)
```

- **Isolation:** the root `build.gradle`, `settings.gradle`, workflows and the M1-M6 zero-runtime-dependency gate stay
  byte-identical.
- **Engine dependency:** the adapter build consumes the engine as `workshop.vfx:minecraft-vfx-math:0.1.0-m1`, which
  `includeBuild('..')` substitutes with the root project.
- **Spike evidence:** this was built in scratch with Java 25 and Gradle 9.1.0. The resulting jar embeds the 63 engine
  classes and contains no `org/bukkit`, `io/papermc` or JUnit class (`validation/build-spike.txt`).
- **Two layers:**
  - `core`: everything that can be tested offline, namely id mapping, style catalogue, plan walking, validation,
    limits and accounting. It follows the SPI proposal in `docs/m7/spi/`, which compiles against the accepted engine.
    The proposed tests in `docs/m7/tests/` run against a scratch reference of that SPI (`docs/m7/reference/`) with
    the runnable build in `docs/m7/verification/` (`M7-R3-VERIFICATION.md`).
  - `bukkit`: thin and verified on a real server.
- **No NMS**, no CraftBukkit, no `paper-plugin.yml`, no reflection, no other plugin dependencies.

## 2. Build (normative)

| Item | Rule |
|---|---|
| Toolchain | Java 25, `options.release = 25`, `-Xlint:all`; Gradle wrapper of the root (9.1.0), run as `../gradlew -p paper-adapter` or from inside `paper-adapter/` |
| Repositories | `mavenCentral()` and `https://repo.papermc.io/repository/maven-public/` |
| Paper | `compileOnly "io.papermc.paper:paper-api:${paperApiVersion}"`, with `paperApiVersion` an **exact** version in `paper-adapter/gradle.properties`. No `+`, ranges or `latest`. **[verify]** The exact build string must be read from that repository's `io/papermc/paper/paper-api/maven-metadata.xml`. It could not be read from this environment (host blocked), so R1 states no build number. |
| Engine | `implementation 'workshop.vfx:minecraft-vfx-math:0.1.0-m1'` (composite substitution) |
| Tests | `testImplementation 'org.junit.jupiter:junit-jupiter:5.12.2'`; tests cover `core` only and need no Paper jar at runtime |
| Jar | the plugin classes plus `runtimeClasspath` contents (the engine only), with `duplicatesStrategy = FAIL`. No shading plugin is needed. No relocation, because the `workshop.vfx` packages are unique. |
| Jar check | a `verifyPluginJar` task, run by `check`, fails if the jar contains any `org/bukkit/`, `io/papermc/`, `net/kyori/`, `com/mojang/` or `org/junit/` entry, or lacks `plugin.yml`, `workshop/vfx/delivery/DeliveryPlanner.class` or the main class |
| `plugin.yml` version | `processResources { filesMatching('plugin.yml') { expand(version: project.version) } }`. `project.version` comes from `paper-adapter/gradle.properties` (for example `0.7.0`). `verifyPluginJar` fails if the packaged `plugin.yml` still contains `${` or a `version` value that differs from `project.version`. |
| Provenance | the build writes `META-INF/bedwenton-build.properties` with the adapter commit, the engine tree id, `paperApiVersion` and the Java version. The evidence records the SHA-256 of the jar, the source ZIP and the downloaded `paper-api` jar. |

## 3. Plugin metadata (normative)

`plugin.yml` (a Bukkit-style plugin; Paper's experimental `paper-plugin.yml` loader is not used):

```yaml
name: BedwentonVfxDemo
version: '${version}'          # expanded by processResources at build time (section 2)
main: workshop.vfx.paper.bukkit.VfxDemoPlugin
api-version: '26.3'
description: Opt-in VFX engine debug demo. Particles only; no gameplay effects.
permissions:
  bedwenton.vfxdemo.use:   { default: op, description: 'Use /vfxdemo ring, stop and status' }
  bedwenton.vfxdemo.view:  { default: op, description: 'See demo effects started by other players' }
  bedwenton.vfxdemo.admin: { default: op, description: 'Stop all demo effects; see server-wide status' }
```

**Commands are not declared in `plugin.yml`.** In 26.3, `CommandExecutor` and `JavaPlugin#getCommand` are annotated
`@ApiStatus.Obsolete(since = "26.3")`. The plugin therefore registers its command in `onEnable` with
`JavaPlugin#registerCommand(String label, @Nullable String description, Collection<String> aliases, BasicCommand)`,
which the source shows registers through `LifecycleEvents.COMMANDS`. Its `BasicCommand`:
- returns `"bedwenton.vfxdemo.use"` from `permission()`;
- implements `execute(CommandSourceStack, String[])`;
- implements `suggest(...)` for the subcommands.

This is a deliberate deviation from the kickoff's "plugin.yml commands" (open issue O2).

## 4. Commands (normative)

Every command below requires `bedwenton.vfxdemo.use`. All replies are plain chat messages and print no other player's
name or UUID.

| Command | Sender | Effect |
|---|---|---|
| `/vfxdemo ring [simple\|balanced\|heavy]` | player only (`CommandSourceStack.getExecutor()` is a `Player`) | Starts the static demo ring centred at the player's current block-centre position. The tier defaults to `config.defaultTier`. A player's existing ring is **replaced**, never duplicated. Refused, with a message, if the global active-effect cap is reached, the anchor chunk is not loaded, or the anchor is outside the world border or height range. |
| `/vfxdemo stop` | player | Stops the sender's own ring. |
| `/vfxdemo stop all` | needs `bedwenton.vfxdemo.admin` | Stops every ring. |
| `/vfxdemo status` | any | Shows plugin and engine versions, `paperApiVersion`, the server's `Bukkit.getVersion()`, active rings (the sender's own, or a total count for admins), whether the task is running, and the last tick's `EmissionReport`. |

Unknown or extra arguments produce a usage line. No command changes gameplay state.

## 5. Demo effect (normative)

The demo is one `EffectRecipe` named `bedwenton-demo-ring`, priority `ACTION`, with three tiers. Each tier is an M5
`EffectScene` in the `GAME` domain, with lifetime `config.ringLifetimeTicks` and two layers. Every layer uses timing
`Timing.loop(40)`, `Visibility.ACTIVE_ONLY`, `Reveal.ALL` and `Motion.IDENTITY`.

| Layer | Shape | Style key | Role | SIMPLE / BALANCED / HEAVY points |
|---|---|---|---|---|
| `ring` | `Spiral.circle(2.0)`, `Sampler.uniformParameter(..., n, Endpoints.EXCLUDE_END, ...)`, so there is no duplicated seam point | `bedwenton:demo_end_rod` | ESSENTIAL | 16 / 32 / 48 |
| `accent` | `Spiral.circle(2.25)`, same sampling | `bedwenton:demo_dust_red` | DECORATIVE | 8 / 16 / 24 |

- **Anchor:** captured once at `/vfxdemo ring`: the block centre `(bx + 0.5, by + 0.1, bz + 0.5)` of the player's
  location, with identity rotation and scale 1. It is static; a moving anchor is deferred until the static path
  passes.
- **Playback:** `Playback.start(TimePoint.gameTick(startTick))`, with now = `TimePoint.gameTick(currentTick)`, where
  `currentTick = Bukkit.getCurrentTick()`.
- **Frame ordinal:** `currentTick - startTick`.

**Style catalogue** (closed whitelist; contract with `StyleCatalog`):

| Style key | Spec | Paper call data |
|---|---|---|
| `bedwenton:demo_end_rod` | `Plain("minecraft:end_rod")` | `Particle.END_ROD`, `getDataType() == Void.class`, data `null` |
| `bedwenton:demo_dust_red` | `Dust(0xFF3030, 1.0f)` | `Particle.DUST`, `new Particle.DustOptions(Color.fromRGB(0xFF3030), 1.0f)` (size must be in [0.01, 4.0], per the `DustOptions` constructor) |

- At enable, each spec is resolved to its `Particle`, and the expected `getDataType()` is checked (`Void` for plain
  particles, `DustOptions` for dust).
- A spec that fails to resolve or check is removed from `supportedStyles`, and the failure is logged once. There is no
  fallback particle.
- The planner receives exactly the resolved keys.

## 6. Tick cycle and threading (normative)

**Main thread only.** All Bukkit and Paper access, planning and emission run on the server main thread. Each entry
point asserts `Bukkit.isPrimaryThread()` and refuses otherwise. There is no async task in M7.

**The task:**
- One repeating task, `BukkitScheduler#runTaskTimer(Plugin, Runnable, 1L, 1L)`, keeping the returned `BukkitTask`.
- It exists only while at least one ring is active. It is started by the first ring and cancelled (`BukkitTask#cancel`)
  when the last ring ends.
- Ambient visuals never start on enable.

**Each run, in order:**

1. **Snapshot.**
   - `tick = Bukkit.getCurrentTick()`.
   - For each active ring (at most `maxActiveEffects`, in ascending ring id), drop the ring if any of these hold:
     - its world is unloaded (`Bukkit.getWorld(worldUuid) == null`);
     - its anchor chunk is not loaded (`World#isChunkLoaded(cx, cz)`);
     - `tick - startTick >= ringLifetimeTicks`;
     - its owner is offline.
   - **Viewers** of a ring are chosen by `ViewerSelection` (production `OwnerFirstViewerSelection`) in this order:
     1. **The owner is admitted first** if eligible: online, valid, in the ring's world, within `viewRadius`, and
        holding `bedwenton.vfxdemo.use`.
     2. **The other places** (up to `maxViewersPerEffect`) go to the smallest session viewer ids among the other
        eligible players: online, in the ring's world, within `viewRadius`, and holding `bedwenton.vfxdemo.view`.
     3. **Only then** is the chosen set sorted strictly increasing for `DeliveryRequest.of`.

     Sorting never decides whether the owner is included. A ring with no eligible viewer is planned with an empty
     viewer list, and M6 reports `SKIPPED_NO_VIEWERS`.
   - **Context:** for each ring, record `RequestContext(requestId = ring id, ownerViewerId, worldId, anchor)` in a
     per-tick map keyed by request id.
2. **Plan.** Make **one** `DeliveryPlanner.plan(tick, requests, styleHealth.enabledStyles(), policy)` call for all
   rings: one budget scope per server tick.
   - `enabledStyles()` is the resolved catalogue minus the styles disabled this session (section 7.1).
   - The planner is never called per effect within a tick, so budgets are not reset.
   - There is no host ledger, because there is one call per tick.
   - `planningNanos` is measured.
3. **Emit.** Call `PlanEmitter.emit(plan, contexts, styleHealth, gate, pointValidator, sink, limits)` (section 7)
   and measure `emissionNanos`.
4. **Report and stop.** Store the `EmissionReport` for `/vfxdemo status`. If no ring remains, cancel the task.

**Request ids** are ring ids (a per-session counter); **world ids** come from `SessionIds.worldId`.

**Policy:** `DeliveryPolicy` is built from validated config (section 9). The planner sees nothing else.

## 7. Emission semantics (normative)

**Order.** Sort the plan's deliveries by `allocationRank()` ascending. This is M6's allocation order, which encodes
priority and tick rotation. **Never iterate `plan.deliveries()` in list order**: that is the request input order.
For each delivery:
- skip it unless the outcome is `DELIVERED` or `THINNED`;
- **context validation (R3), before any recipient is considered**, with `c = contexts.get(requestId())`. The first
  failure applies:

  | Condition | Skip reason |
  |---|---|
  | `c == null` | `MISSING_REQUEST_CONTEXT` |
  | `c.requestId() != requestId()` | `MISMATCHED_REQUEST_CONTEXT` |
  | any anchor coordinate is not finite, or `c.ownerViewerId() < 0`, or `c.worldId() < 0` | `INVALID_REQUEST_CONTEXT` |

  On failure, skip the whole delivery: count `skippedDeliveries[reason]` once and add `placements()` to
  `placementsInSkippedDeliveries`. Nothing of it is sent. No owner, world or anchor is substituted, reconstructed or
  inferred, and a rejected context never affects other deliveries;
- iterate the recipients `r = 0 .. recipientCount() - 1` in order (ascending viewer id).

For each recipient:

1. **Gate (owner-aware).** `RecipientGate.check(context, viewerId)` gathers `ViewerFacts` for the player:
   - found through `SessionIds.viewer` and `Server#getPlayer(UUID)`;
   - `isOnline()`, `isValid()`;
   - its world id and position;
   - `hasPermission` for the use and view permissions.

   It then applies `RecipientRule`, production `OwnerAwareRecipientRule(viewRadius)`. The checks run in this order,
   and the first that fails gives the reason:

   | Check | Fails as |
   |---|---|
   | online and valid | `VIEWER_OFFLINE` |
   | same world as `context.worldId()` | `VIEWER_WORLD_CHANGED` |
   | `viewerId == context.ownerViewerId()` ? has `bedwenton.vfxdemo.use` : has `bedwenton.vfxdemo.view` | `VIEWER_PERMISSION_REVOKED` |
   | anchor coordinates all finite (R3; the rule checks this itself, even when used without the emitter) | `INVALID_REQUEST_CONTEXT` |
   | viewer coordinates all finite (R3) | `VIEWER_POSITION_INVALID` |
   | `d2 <= radius * radius`, with `d2` the squared distance from the anchor, written as `!(d2 <= r2)` → deny, so NaN can never allow | `VIEWER_OUT_OF_RANGE` |

   **Fail closed (R3).** Every non-finite coordinate denies. NaN, +infinity and -infinity are all classified by the
   two validity rows above, never as allowed and never by an accidental comparison. Finite but huge coordinates whose
   squared distance overflows to +infinity are `VIEWER_OUT_OF_RANGE`. For normal (finite) inputs the order is
   unchanged from R2: online and valid, world, permission, range.

   **Radius policy (R3).** `OwnerAwareRecipientRule(radius)` throws `IllegalArgumentException` unless the radius is
   finite and >= 0. A radius of 0 admits only viewers exactly at the anchor. Config validation (section 9) additionally
   requires `viewRadius` to be finite and within [4.0, 64.0]. A NaN, infinite or out-of-range value (YAML `.nan` and
   `.inf` included) refuses enable with a clear message. The plugin never starts with a default substituted for an
   invalid radius.

   On failure, count `skippedRecipients[reason]` and add `recipientPoints(r)` to `placementsInSkippedRecipients`; the
   recipient receives nothing of **this** delivery this tick. A
   player who is the owner of one ring and a plain viewer of another, coincident ring is judged separately for each
   ring.
2. **Points.** The recipient receives exactly the **first `recipientPoints(r)` selections** of the sequence:
   ordered batches in position order, and within a batch `j = 0 .. kept(position) - 1`. Point j of position p is
   `frame().batches().get(orderedBatch(p))` at index `selectedIndex(p, j)`, read with `x(i)`, `y(i)` and `z(i)`.
   - No other point is ever sent. Unsupported styles never appear, because M6 never selects them.
   - A skipped point still uses up its place in the prefix: later selections are not promoted.
3. **Per point, before the call, in order:**
   1. the style is disabled (section 7.1): count `skippedPoints[STYLE_DISABLED]`;
   2. `PointValidator`: coordinates must be finite (else `POINT_NOT_FINITE`); y must be in
      `[world.getMinHeight(), world.getMaxHeight())` and `world.getWorldBorder().isInside(location)` must hold (else
      `POINT_OUTSIDE_WORLD`);
   3. limits: if `maxCallsPerTick` or that viewer's `maxCallsPerViewerPerTick` is reached, count
      `skippedPoints[EMISSION_LIMIT]`.

   These limits are defensive. The M6 policy is aligned with them (section 9.1), so they do not bind unless something
   else is wrong. If they ever bind, the points dropped are those latest in allocation order.
4. **Call.** `player.spawnParticle(particle, x, y, z, 0, 0.0, 0.0, 0.0, 0.0, data, false)`. This is the declared
   `<T> spawnParticle(Particle, double, double, double, int, double, double, double, double, T, boolean)`, which
   delegates with `RandomizationType.DEFAULT`.
   - Count 0 with zero offsets requests one particle at the exact position. **[verify]** in a real client (smoke
     test S5).
   - `force = false`.
   - Never use `World#spawnParticle`, `ParticleBuilder#receivers(...)` with an area, or any broadcast.
   - On normal return: `succeededCalls++` and `styleHealth.recordSuccess(style)`.
   - On a `RuntimeException`: `failedCalls++` and `styleHealth.recordFailure(style)`. The exception never leaves the
     tick.
5. **Accounting.** See section 7.2. M6 counters are never altered. **Calls are not packets:** no packet or
   client-render claim is made.

### 7.2 Placement accounting (normative, R3)

Every placement M6 planned for an emitted outcome is accounted for exactly once:
- `plannedPlacements = plan.placements()`;
- `plannedPlacements = attemptedCalls + sum(skippedPoints) + placementsInSkippedRecipients + placementsInSkippedDeliveries`;
- `attemptedCalls = succeededCalls + failedCalls`.

M6 places no points for SKIPPED and FAILED outcomes, so they contribute nothing. The tests assert both identities in
the context and coordinate cases.

### 7.1 Session-wide style disabling (normative)

`StyleHealth` (production `SessionStyleHealth`) holds one consecutive-failure **streak** per style for the plugin
session.

| Rule | Detail |
|---|---|
| Failure | a `RuntimeException` from `ParticleSink.send` for that style; it adds 1 to the streak |
| Reset | a normal return of a send **of the same style** sets its streak to 0. Successes of other styles, new ticks and new rings do **not** reset it: the streak spans recipients, rings and ticks |
| Disable | the failure that makes the streak 3 (`DISABLE_AFTER_CONSECUTIVE_FAILURES`) disables the style **immediately**. The rest of the current tick skips its points as `STYLE_DISABLED`. The style is reported once in `EmissionReport.stylesDisabledThisTick`, and one warning is logged (rate-limited, no player data) |
| Next pass | from the next planning call, `enabledStyles()` omits it. M6 then treats it exactly like any unsupported style (its batches become `unsupportedPoints`; a ring with no enabled style is `SKIPPED_UNSUPPORTED` before evaluation). **No M6 code changes** |
| Duration | disabled until `onDisable`. A new enable creates a new `StyleHealth`; there is no re-enable command in M7 |
| Status | `/vfxdemo status` lists disabled styles |

## 8. Lifecycle (normative)

| Event | Action |
|---|---|
| `onEnable` | Validate config (refuse to enable if invalid). Resolve the style catalogue. Register the command and listeners. **Start no task.** Log one line with plugin version, engine tree id, `paperApiVersion`, `Bukkit.getVersion()` and `Runtime.version()`. |
| `onDisable` | Cancel the task. Clear all rings, the session id maps and the catalogue. `Bukkit.getScheduler().cancelTasks(this)` as a backstop. Afterwards, `getPendingTasks()` must contain no task owned by the plugin (smoke S10). |
| `PlayerQuitEvent` | Stop the player's ring; `SessionIds.release(uuid)`. |
| `PlayerChangedWorldEvent`, `PlayerTeleportEvent` (destination in another world or beyond `viewRadius` of the owner's anchor), `PlayerDeathEvent` | Stop the owner's ring. As a viewer, the player is filtered out at the next snapshot. |
| `WorldUnloadEvent` | Stop every ring in that world; forget the world id. **Defensive:** on ordinary Paper a world that contains players cannot be unloaded, and an owner who leaves the world has already stopped their ring (row above). So this row cannot normally be reached with an active ring; the snapshot's world-missing check covers it too. Tested offline; see smoke S12. |
| Player or world gone between planning and emission | The gate or point validation skips it; nothing throws. |
| Config change | Read only at enable. Apply changes by restarting the server or disable-enable. `/reload` is unsupported; status says so. |
| Tick after cancellation | The task body first checks a `running` flag; a run after `cancel()` emits nothing. |

All listener handlers run on the main thread, through the Bukkit event system. Each handler touches only plugin state.

## 9. Configuration and bounds (normative)

`config.yml` values are validated at enable against hard ceilings compiled into the plugin. A value outside its range
refuses enable with a clear error. The ceilings bound worst-case work; **they are not recommended or "safe" server
values**.

| Key | Default | Allowed range (hard ceiling) | Maps to |
|---|---|---|---|
| `maxActiveEffects` | 4 | 1-16 | ring cap; `DeliveryPolicy.maxRequests` |
| `maxViewersPerEffect` | 4 | 1-16 | viewer cap; `maxViewersPerRequest`; `maxViewerEntries = maxActiveEffects x maxViewersPerEffect` |
| `viewRadius` | 32.0 | 4.0-64.0, and finite (NaN and infinity refuse enable) | viewer filter and `OwnerAwareRecipientRule` |
| `ringLifetimeTicks` | 600 | 20-6000 | scene lifetime and expiry |
| `defaultTier` | `balanced` | `simple`, `balanced`, `heavy` | requested tier (a strict ceiling, per M6 G11) |
| `generatedPointBudget` | 1152 | 0-1152 | M6 (`16 x 72`: every ring at HEAVY) |
| `placementBudget` | 2048 | 0-4096 | M6 |
| `worldPlacementBudget` | 2048 | 0-4096 | M6 |
| `viewerPlacementBudget` | 256 | 0-1024 | M6 |
| `simpleKeptCap` / `balancedKeptCap` / `heavyKeptCap` | 24 / 48 / 72 | 0-72, non-decreasing | M6 |
| `maxCallsPerTick` | 2048 | 1-4096 | `EmissionLimits` |
| `maxCallsPerViewerPerTick` | 256 | 1-1024 | `EmissionLimits` |

- Ambient reserves are 0: the demo uses only ACTION.

### 9.1 Budget alignment (normative)

The effective M6 policy is derived so that a fully gated-in plan never exceeds the emitter's defensive limits:
- effective `placementBudget = min(placementBudget, maxCallsPerTick)`;
- effective `worldPlacementBudget = min(worldPlacementBudget, maxCallsPerTick)`;
- effective `viewerPlacementBudget = min(viewerPlacementBudget, maxCallsPerViewerPerTick)`.

Each placement is one call, so M6, allocating in rank order, decides which ring and viewer get capacity, and the emitter
never truncates in its own order. Status shows the effective values. The tests check that an aligned policy yields no
`EMISSION_LIMIT` skip, and that the emitter honours rank when the limits are forced to bind.
- **Worst case per tick within the ceilings:** 16 plans of at most 72 generated points; at most 4096 calls; one M6
  call.

## 10. Logging and privacy (normative)

- **Startup:** one line (section 8).
- **Warnings:** rate-limited to one per distinct reason per 60 seconds. They report counts and style keys, never player
  names, UUIDs or coordinates.
- **Per-tick logging:** none by default. A `debug: false` config switch enables one summary line per 100 ticks, and
  even then names no player.

## 11. Explicitly out of scope for M7

- Folia: no `folia-supported` flag and no region schedulers. Geyser/Bedrock parity.
- Moving anchors (until the static ring passes).
- Gameplay integrations: Bloom, Cloudstep, EcoEnchants, artifacts, ownership, damage and cooldowns.
- Async planning; cross-tick ledgers; data-component APIs; arbitrary particle payloads.
- Deployment to any live server; merges into `main`.
