# M6 design decisions DM6-1 to DM6-13 (R1 proposal)

Status: **PROPOSED, revision 2** (R2 changes DM6-2 and DM6-5; see `M6-R2-CHANGES.md`). Each decision gives the choice, the alternatives considered, and the evidence. The
normative text is `M6-IMPLEMENTATION-CONTRACT.md`; where this document and the contract differ, the contract wins.

Baseline: accepted M5 GitHub snapshot `d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb` (tree `43d1300a...`), kickoff
`0587309877c1e3534e2209e4bd361bd6bca5fa8f`. The unpublished local commit `d5a8868...` has identical production
sources but is not the accepted identity.

## DM6-1: public package and API

**Decision.** One package, `workshop.vfx.delivery`, with ten public types:
- four enums: `QualityTier`, `EffectPriority`, `LayerRole`, `Outcome`;
- three immutable inputs: `EffectRecipe`, `DeliveryRequest`, `DeliveryPolicy` (a record);
- one pure function: `DeliveryPlanner.plan(long tickOrdinal, List<DeliveryRequest>, Set<StyleKey>, DeliveryPolicy)`;
- two immutable outputs: `DeliveryPlan` and `EffectDelivery`.

The exact signatures are in contract section 2. A reflection test (`M6BoundaryTest.apiShape`) pins them.

**Alternatives rejected.**
- A runtime manager with registration, effect handles and a per-tick ledger. It would need lifecycle, ownership and
  synchronization rules (DM6-10) that belong to the host, and it could not be tested without a scheduler.
- Builders and fluent configuration. They add API surface without adding safety; static factories with validation
  are enough.
- Separate "admission" and "allocation" entry points. A single call keeps both phases in one allocation order and
  makes it impossible to allocate placements for a request that was not admitted.

## DM6-2: preflight admission before evaluation

**Decision.** Phase 1 runs before any M5 call (contract section 6). For each request in allocation order:
1. no viewers: `SKIPPED_NO_VIEWERS`, with no evaluation and no reservation;
2. candidate tiers are those at most the requested tier, highest first. If none exists:
   `SKIPPED_NO_ELIGIBLE_TIER`, with no evaluation and no reservation;
3. only **deliverable** candidates count: at least one layer with a supported style and a non-empty shape. None
   deliverable: `SKIPPED_UNSUPPORTED`;
4. the first deliverable candidate with `maxPointsPerFrame() <= available generation` is chosen; otherwise
   `SKIPPED_GENERATION_BUDGET`.

**Conservative versus exact cost.**
- The preflight charges the **conservative** cost `EffectScene.maxPointsPerFrame()`: the sum of all shape sizes,
  known at construction. M5 guarantees `pointCount() <= maxPointsPerFrame()`, so the generation budget is a hard bound
  on the M5 work (G1, G2).
- The **exact** visible cost (which layers are active, PREFIX progress) is known only after evaluation. Using it for
  admission would require evaluating first, which is what the kickoff forbids. The exact cost is reported afterwards as
  `sourcePoints`, so an operator can see how conservative the reservation was.
- An alternative was a per-layer active-interval preflight (skip layers whose clips cannot be active). It would make
  the reservation tighter for long staged scenes, but it duplicates M2 timing logic and M5 visibility rules. It is
  listed as an open option in `M6-RISKS.md` (R4).

**The requested tier is a strict ceiling (R2, G11).** A SIMPLE request never selects BALANCED or HEAVY, even with
unlimited budgets.
- R1 fell back to the recipe's lowest tier when no tier was at or below the request. That silently upgraded a SIMPLE
  request for a recipe with only BALANCED and HEAVY. Review decided against this.
- The request is now reported as `SKIPPED_NO_ELIGIBLE_TIER`. The outcome names the cause exactly: the recipe offers
  nothing at the requested quality, which is neither a style problem nor a budget problem.
- An author who wants every request served ships a SIMPLE variant.

**Deferral** (kickoff: "skip, defer or choose"). M6 is stateless, so it cannot defer. A skipped request is reported
with its reason, and the host may resubmit it next tick; rotation (DM6-5) then changes its position.

**Evidence.** `AdmissionTest` (downgrade, missing tiers, deliverability, failing anchors that are never evaluated);
`M6StressTest.deniedPreflightAllocation` (a denied 2^20-point scene allocates under 64 KiB per plan, measured, where one
evaluation would allocate 24 MiB).

## DM6-3: dimensions of work

| Quantity | Meaning | Where |
|---|---|---|
| reserved generated points | conservative M5 output bound charged at admission | `reservedGeneratedPoints` |
| source points | points M5 actually generated (`EffectFrame.pointCount()`) | `sourcePoints` |
| unsupported points | generated points in batches whose style is not supported | `unsupportedPoints` |
| kept points (k) | distinct logical points selected for the effect | `keptPoints` |
| recipients | viewers that receive at least one point | `recipientCount()` |
| placements | logical (point, recipient) pairs: the unit of delivery budgets | `placements` |
| adapter calls attempted / succeeded / failed | what the adapter actually did | **not M6**: adapter diagnostics (DM6-13) |

- A placement is **not** a packet. The adapter decides how placements become API calls and packets (one call per
  point per viewer, or batched calls). The planner cannot know packet counts, client frame rate or network load and
  makes no claim about them.
- Every dimension has its own counter and its own total in `DeliveryPlan`.

## DM6-4: integers, quotas and validation

**Integers.** Every count is a `long` or `int`, and planning uses no floating point (enforced by
`M6BoundaryTest.forbidden`). The bounds make overflow impossible:
- budgets are at most 2^50;
- viewer entries per call are at most 2^24, and kept points per effect at most 2^20, so a per-request cost is at most
  2^44;
- the role split computes `r * n_b <= 2^20 * 2^20 = 2^40`.

No sum can reach 2^62, so no exact-arithmetic helper is needed.

**Quota hierarchy** (all must hold at once):
1. **generation:** total reservations `<= generatedPointBudget`; non-ambient reservations
   `<= generatedPointBudget - ambientGeneratedReserve`;
2. **global placements:** `<= placementBudget`; non-ambient `<= placementBudget - ambientPlacementReserve`;
3. **per world:** placements in a world `<= worldPlacementBudget`;
4. **per viewer:** placements to a viewer, across all worlds and effects, `<= viewerPlacementBudget`;
5. **per effect:** `k <= keptCap(tier)`.

The per-effect kept count k is the largest value for which the request's total cost
`sum over viewers min(k, V[v])` fits `min(global remaining, world remaining)`, capped by
`min(N, keptCap, max V[v])` (contract section 7). A viewer with less remaining budget receives the first `V[v]` points
of the same selection.

**Proof sketch that no plan exceeds its limits.** Each request is charged in allocation order against the remaining
budgets, and k is chosen so that the charge fits. Budgets only decrease, and every quantity charged is exactly the
quantity reported (`placements = cost(k)`). Induction over the allocation order gives G1 and G3. The tests check this
independently: `M6TestSupport.checkInvariants` replays the reported plan and recomputes every budget from the inputs.

**Validation.**
- Negative budgets, counts and world ids are rejected; zero is allowed everywhere (zero budgets give skips).
- Sizes are checked before proportional copies: the request list size before copying, and the viewer array length
  before cloning. A hostile list whose `size()` is too large is rejected without being read (tested).

**Self-review finding F1 (fixed in R1).** In the first draft, the cap on k was `min(N, keptCap)`. When every viewer
had exhausted its budget, every k cost 0, so k reached the cap and the request was reported `DELIVERED` with zero
placements. A stress test exposed this. The cap now includes `max V[v]`, so `keptPoints` equals the largest recipient
share. `DELIVERED` also now requires that every viewer received all k points.

## DM6-5: fairness and priority

**Decision.**
- Strict priority classes: ACTION, then NORMAL, then AMBIENT.
- Within a class, ids ascending, rotated by `floorMod(tickOrdinal, m)`. Ties cannot occur, because ids are unique.
- AMBIENT has protected reserves for generation and placements.

**Guarantees claimed** (contract section 8, nothing more):
- **rotation:** with the same m requests present for m consecutive tick ordinals, each one is first in its class exactly
  once (`FairnessTest.rotation`);
- **reserve protection (G10, corrected in R2):** non-ambient requests never consume the ambient reserves, so when the
  AMBIENT class starts, at least the reserved generation and placement capacity remains. **No minimum realized ambient
  allocation is claimed.**
  - Reservations are indivisible: a scene costs its full `maxPointsPerFrame` or nothing.
  - Placement costs grow with the viewer count.
  - World budgets, viewer budgets and kept caps also bind.

  R1's claim of "at least `min(reserve, demand)`" was false. The two counterexamples are regression tests
  (`BudgetPropertyTest.reserveCounterexampleGeneration` and `.reserveCounterexamplePlacement`) and fixtures
  (`stress-reserve-*`);
- **NORMAL has no reserve:** ACTION demand can starve NORMAL indefinitely (`FairnessTest.strictPriority`). **No
  starvation-freedom is claimed** beyond the two statements above.

**Alternatives.**
- Weighted fair queuing or deficit round-robin with carried credit. These need state across ticks (a ledger), which
  DM6-10 avoids.
- Pure round-robin without priorities. This would let ambient bursts displace action feedback, which the kickoff
  forbids.
- Rotation by a hash of the tick. This is less predictable and gives no exact once-per-m statement.

## DM6-6: thinning and visual continuity

**Decision.** In a batch of n points with kept count m:
- `g = ceil(n / m)`, `o = floorMod(frameOrdinal, g)`;
- `selectedIndex(j) = o + floor(j n / m)`. This never exceeds n - 1, because `o <= g - 1` and
  `floor((m - 1) n / m) = n - g`, so no wrap is needed and the indices increase strictly.

**Properties** (all tested):
- the indices are distinct and in range;
- they are evenly spread: consecutive base indices differ by at most g, including the wrap;
- **G6 coverage:** the union over any g consecutive frame ordinals is all n points. The gaps are at most g and the
  offsets take every value in `[0, g)`. A thinned ring therefore never loses the same sector across frames.

**Visible behaviour.** At a 50 % thinning (g = 2), alternate points shimmer between two interleaved halves on alternate
ordinals. At 10 % (g = 10), each point appears once every 10 ordinals. The phase comes only from the caller's
`frameOrdinal`, so two viewers of the same effect see the same points. A caller that wants a static selection passes a
constant ordinal.

**Alternatives rejected.**
- A prefix (first m points): it always drops the same sector.
- Random sampling: it is not deterministic without a seed, and it clumps.
- Fixed stride without phase: it is static, and points are never shown at all.

**Layer roles.** ESSENTIAL batches are kept in full before STANDARD, and STANDARD before DECORATIVE (G7). Inside the role
group that is cut, points are split proportionally by largest remainder, with ties going to the earlier layer.
Recipients receive a prefix of the selection sequence, so a saturated viewer gets the essential points first.
- **Healer:** markers are ESSENTIAL; pulse rings are STANDARD; sparkles are DECORATIVE.
- **Astral Prism:** stars are ESSENTIAL; links are STANDARD; trails are DECORATIVE, so trails degrade first.

## DM6-7: representation of the plan

**Decision.** `EffectDelivery` stores:
- the M5 `EffectFrame` reference (the coordinates are never copied);
- per kept batch, four ints: the frame batch index, the kept count m, the batch size n and the phase o;
- per recipient, a viewer id and a point count.

`selectedIndex(position, j)` is computed in O(1) on demand. There are no index arrays.

**Memory.** Beyond the M5 frames, the plan uses O(requests + viewer entries + kept batches). Planner work is
O(requests log requests + viewer entries x log(kmax + 1) + batches log batches). Measurements are in
`docs/m6/validation/performance.txt`.

**Alternative rejected.** Materialised `int[]` index lists or copied `double[]` coordinates cost O(kept points) per plan
and duplicate data the adapter can read from the frame.

## DM6-8: styles and viewers

- **Capabilities** arrive as an immutable `Set<StyleKey>` per call. Unknown styles fail closed: unsupported batches are
  never selected, they are counted as `unsupportedPoints`, and an effect with no deliverable layer is never evaluated.
- **Viewers** arrive as a strictly increasing `long[]` per request: an already-filtered audience. Authorization,
  distance, visibility, client settings and Bedrock parity are the host's job, done before planning.
- An empty audience costs nothing: no evaluation and no reservation.
- Viewer ids are opaque numbers. They are not Bukkit objects and not UUIDs; the host maps them.

## DM6-9: ordinals and time

- `tickOrdinal` (fairness rotation) and `frameOrdinal` (thinning phase) are caller-supplied longs. M6 keeps no counter
  and reads no clock.
- Every value, including negative ones and `Long.MIN_VALUE`, is valid, through `floorMod`.
- **Time domains:** M6 does not interpret time. `DeliveryRequest.of` checks that `playback` and `now` use the recipe's
  M2 domain, so the M5 evaluation cannot fail on a domain mismatch. GAME and COSMETIC scenes are planned identically.
- A typical host uses the server tick number for both ordinals for GAME scenes, and a render-frame counter for
  COSMETIC scenes.
- G6 needs consecutive ordinals. A host that skips ordinals still gets correct, but less even, coverage.

## DM6-10: no stateful ledger

**Decision.** M6 holds no budget ledger. A call's budgets are fresh, and they are spent only inside that call. The plan
is a pure function, safe to compute on any thread (G8, tested on 8 threads).

**Consequences.**
- **Scope.** The host chooses the budget scope by what it passes in one call: per server tick for all worlds, per
  world, or per region. Budgets across separate calls are **not** coordinated by M6.
- **Multiple threads.** A host that plans per region on several threads must split the budgets itself, for example by
  giving each region its own policy.
- **Cancellation.** Nothing to cancel: a plan that is never emitted has no side effect.

**Alternative rejected.** A shared mutable ledger. It needs atomic reservations, a rule for which thread owns a world,
rollback on adapter failure, and Folia region semantics. All of these belong to the adapter and cannot be validated
without a server.

## DM6-11: policy versus presets versus geometry

| Layer | Owner | In M6 |
|---|---|---|
| geometry density per tier | effect author | `EffectRecipe` variants (one M5 scene per tier) and layer roles |
| neutral engine policy | M6 | `DeliveryPolicy`: twelve numbers, **no defaults**, validated bounds only |
| named quality presets (for example "low-end server") | server owner, through adapter configuration | not in M6; a preset is just a `DeliveryPolicy` value |
| Paper configuration (view distance, particle settings) | adapter | not in M6 |

- No "safe" constant is proposed. The bounds in section 4.3 of the contract are overflow and memory ceilings, not
  recommendations.
- **Kickoff note:** "Balanced can be the default authoring tier" is the author's choice per request. M6 does not default
  the requested tier.
- **Effect-specific overrides** of emission quotas are expressed through geometry: an author ships smaller variants or
  marks layers DECORATIVE. A per-recipe kept-cap override was considered and left out to keep the policy single-sourced
  (open question Q2 in `M6-RISKS.md`).

## DM6-12: oracles and measurement

- **Independent oracle:** `tools/generate_m6_reference_plans.py`, written from the contract text. It uses Python exact
  integers and `fractions`, and a linear scan for k where the reference uses binary search. It generates 45 cases (R2; 40 in R1) in six
  families (Bloom, Cloudstep, three-pulse healer, moving trident helix, Astral Prism, stress) with every field of every
  delivery. It is deterministic: two runs are byte-identical.
- **Invariant oracle:** `M6TestSupport.checkInvariants` recomputes the phase-1 decisions, allocation order, rotation,
  budgets, maximality, role precedence and totals from the inputs and the reported plan alone. It runs on every
  fixture case and on 3,000 random scenarios.
- **Mutation analysis:** 58 mutants of the throwaway reference implementation. 56 are killed and 2 are proven
  equivalent; one of those (M46) removed a redundant modulus from the selection rule
  (`docs/m6/validation/mutation-results.txt`).
- **Measurement:** `docs/m6/validation/PerformanceProbe.java` measures the plan cost per call, separating the M5
  evaluation, and the bytes allocated per call, at and near the ceilings. The results are indicative timings on one
  container and imply no server-level claim.

## DM6-13: adapter boundary and Folia

- The Paper adapter is a **separate, separately versioned** module. A non-normative interface sketch is in
  `M6-ARCHITECTURE.md` section 5. It owns:
  - viewer discovery;
  - style resolution to particle types and data;
  - scheduling;
  - emission;
  - attempted, succeeded and failed counts;
  - lifecycle (logout, teleport, world unload, reload).
- M6 never holds Bukkit `Player`, `World`, `Entity`, `Scheduler` or `Location` objects (`M6BoundaryTest.imports`).
- **Folia:** M6 is pure and thread-agnostic, but **no Folia support is claimed**. Region scheduling, cross-region
  viewers and per-region budgets need real scheduler tests in the adapter milestone.
