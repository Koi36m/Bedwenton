# M6 architecture: runtime safety and particle delivery planning (R1 proposal)

Status: **PROPOSED, revision 2. Not authorized for implementation.**

## 1. Position in the engine

```
M1 math core   M2 time   M3 curves   M4 sampling   M5 scenes and frames      (accepted, frozen)
                                                         |
                                     M6 workshop.vfx.delivery: plan which work is done and delivered
                                                         |
                       future Paper adapter (separate module, separately versioned, not part of M6)
```

M6 is the last platform-neutral layer:
- it decides **which** M5 frames are generated and **which** points go to **which** viewers, within budgets the server
  owner sets;
- it never emits a particle, never touches a server object and keeps no state between calls.

## 2. Data flow of one call

```
inputs (immutable snapshots, supplied by the host each tick)
  DeliveryRequest x R : id, EffectRecipe (priority, 1-3 tier scenes, layer roles), requested tier,
                        world id, anchor Pose, Playback, now, frame ordinal, viewer ids (sorted, filtered by host)
  Set<StyleKey>       : styles the adapter can render (capability snapshot)
  DeliveryPolicy      : 12 owner-chosen numbers (no defaults)
  tickOrdinal         : caller's tick counter (fairness rotation)

DeliveryPlanner.plan
  0. validate sizes before copies, nulls, unique ids, viewer limits            -> IAE, nothing evaluated
  1. allocation order: ACTION | NORMAL | AMBIENT, ids ascending, rotated by tickOrdinal
  2. phase 1, admission (no M5 calls yet):
       no viewers -> SKIPPED_NO_VIEWERS
       no recipe tier <= requested (the request is a strict ceiling) -> SKIPPED_NO_ELIGIBLE_TIER
       no deliverable tier <= requested -> SKIPPED_UNSUPPORTED
       highest deliverable tier whose maxPointsPerFrame fits generation budget -> admit and reserve
       none fits -> SKIPPED_GENERATION_BUDGET
  3. M5 EffectScene.evaluate for admitted requests only
       ArithmeticException -> FAILED_EVALUATION (isolated); inactive or empty frame -> SKIPPED_INACTIVE
  4. phase 2, allocation:
       supported batches -> N; k = largest value whose per-viewer cost fits global/world budgets,
       capped by keptCap(tier) and the largest remaining viewer budget
       k = 0 -> SKIPPED_DELIVERY_BUDGET
       split k over layer roles (ESSENTIAL, STANDARD, DECORATIVE), largest remainder inside the cut role
       strided, phase-rotated selection per batch; recipients receive prefixes of the selection sequence
       -> DELIVERED (every viewer got the full frame) or THINNED

output
  DeliveryPlan: deliveries in input order + totals per dimension + counts per outcome
  EffectDelivery: outcome, tier, frame reference, ordered kept batches, selectedIndex(pos, j), recipients, counters
```

## 3. Why this shape

| Concern (kickoff) | Mechanism |
|---|---|
| prevent expensive M5 allocations | phase 1 runs before evaluation and charges `maxPointsPerFrame` (DM6-2) |
| geometry density versus emission quota | tiers are author-made scenes; kept caps and placement budgets are policy (DM6-11) |
| budget hierarchy | generation, global, world, viewer and per-effect caps, all checked in one ordered pass (DM6-4) |
| fairness | strict classes, rotation, ambient reserves that protect capacity (not a minimum allocation); honest statements only (DM6-5, G10) |
| visual continuity | strided selection with phase from `frameOrdinal`; coverage over g ordinals (DM6-6) |
| no coordinate copies | the plan references M5 frames and computes indices on demand (DM6-7) |
| unknown styles | fail closed, counted (DM6-8) |
| determinism and concurrency | a pure function, integer arithmetic, specified iteration orders (DM6-9, DM6-10) |
| explainable degradation | nine outcome codes, per-delivery counters and plan totals (DM6-3) |

## 4. Budget scopes in practice (non-normative examples)

The host decides the scope by what it passes to one call. All numbers below are placeholders, not recommendations.
- **One call per server tick** covering all worlds: world budgets protect each world from the others, and the viewer
  budget limits what any one player receives per tick.
- **One call per world** on its own thread: every call has its own global budget. The host splits the server budget
  across calls; M6 does not coordinate them.
- **Cosmetic-only effects** at render rate (COSMETIC domain): a separate call with its own policy and `frameOrdinal`.

## 5. Adapter boundary (separate interface proposal, non-normative)

The adapter is a separate future module. M6 constrains only what it receives. The sketch below shows the
responsibilities; it is **not** part of the M6 API and will be specified with live-server tests.

```java
// future module, e.g. workshop.vfx.paper (Paper API pinned to tested versions)
interface DeliveryHost {
    long tickOrdinal();                                   // e.g. the server's current tick
    Set<StyleKey> supportedStyles();                      // resolvable StyleKey -> particle type + data
    List<DeliveryRequest> collectRequests();              // active effects, filtered viewer ids, anchors from live entities
    DeliveryPolicy policy();                              // from owner configuration or a named preset
}
interface ParticleSink {                                  // runs on the thread that owns the viewers
    EmissionReport emit(DeliveryPlan plan);               // resolves viewer ids, sends placements
}
record EmissionReport(long attemptedCalls, long succeededCalls, long failedCalls, long placementsSent,
                      Map<String, Long> failureReasons) { }
```

The adapter alone handles:
- viewer discovery and visibility;
- mapping viewer ids to players;
- `StyleKey` to particle type and data;
- scheduler threads and regions;
- batching placements into API calls;
- attempted, succeeded and failed counts;
- lifecycle: effect start and stop, logout, teleport, world unload and plugin reload.

Paper particles must be sent on the correct thread for the viewer, and the adapter's tests must show this on real
servers. **No Folia support is claimed** (DM6-13).

## 6. What M6 does not do

M6 does not do:
- emission, packets, network or client-cost estimation;
- scheduling and threads;
- persistent budgets, deferral queues or cooldowns;
- effect lifecycle;
- live entity access.

M6 never affects game mechanics: dropping a particle never changes damage, healing, ownership, permissions or
cooldowns, because M6 receives none of them.

## 7. What ran and what was assumed (R1)

**Ran** (Java 25.0.4.1 and Gradle 9.1.0 in this container; logs in `docs/m6/validation/`):
- the M6 tests against the **throwaway** reference implementation (kept outside the repository, not delivered);
- the full suite: 363 accepted tests plus the M6 tests;
- the Python oracle, twice, with byte-identical output;
- mutation analysis;
- the performance probe;
- an unchanged-baseline check of M1 to M5 against `d4970cc`.

**Assumed, not run:**
- any Paper, Bukkit or Folia behaviour;
- any packet, network, client-FPS or MSPT effect;
- GitHub Actions: no workflow was added or run for M6 R1, because GitHub pushes from this environment have been
  refused (HTTP 403);
- that the throwaway reference implementation is representative of a production implementation's performance.

The reference implementation shows that the contract is implementable and that the tests are consistent with it.
**It is not approved production code.**
