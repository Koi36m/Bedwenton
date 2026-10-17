# M6 Implementation Contract: `workshop.vfx.delivery` (runtime safety and particle delivery planning)

Audience: the engineer implementing the production source (Codex).

Status: **PROPOSED, revision 2. Not authorized for implementation.** R2 corrects G10 and makes the requested tier a
strict ceiling (`M6-R2-CHANGES.md`). The owner approves this contract with DM6-1 to
DM6-13 (`M6-DESIGN-DECISIONS.md`) after ChatGPT's independent review. Until then nothing in
`src/main/java/workshop/vfx/delivery` may be written.

**Baseline:**
- the accepted M5 GitHub source snapshot `d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb` (tree `43d1300ab42105c010dd43bfc89ee476491fcdaa`;
  363/363 tests);
- the kickoff `0587309877c1e3534e2209e4bd361bd6bca5fa8f` (`docs/m6/M6-DESIGN-KICKOFF.md`).

**Provenance.** The unpublished local M5 implementation commit `d5a8868...` has the same production sources. The
GitHub snapshot `d4970cc` is the accepted identity, and this proposal builds on it.

**Nothing in M1 to M5 changes.**

The tests in `src/test/java/workshop/vfx/delivery` are the executable form of this contract. Where the two disagree,
stop and report.

**Requirement words:** **normative** means it must hold exactly; **validated** means checked against independent
oracles, not proven.

## 1. Architecture

```
host / future Paper adapter (per tick, per budget scope)
  requests: recipe + requested tier + anchor/playback/now + world id + viewer ids + frame ordinal
  supported style keys (capability snapshot), DeliveryPolicy (numbers chosen by the server owner)
        |
        v
DeliveryPlanner.plan(tickOrdinal, requests, supportedStyles, policy)        (pure, stateless)
  phase 1 (admission, before M5): order -> skip (no viewers / unsupported / generation budget) or choose a tier
  M5: EffectScene.evaluate(anchor, playback, now) for admitted requests only
  phase 2 (allocation, after M5): order -> per-effect kept count k, per-viewer placements, layer roles,
                                  strided phase-rotated point selection
        |
        v
DeliveryPlan -> EffectDelivery per request: outcome, tier, frame reference, per-batch selection, recipients, counters
  -> adapter resolves styles and viewers, emits, and records its own attempted/succeeded/failed counts
```

- Package `workshop.vfx.delivery`. It may use `workshop.vfx.scene`, `workshop.vfx.math.core`,
  `workshop.vfx.math.time` and `java.util` (not `java.util.concurrent` or `java.util.function`).
- It must not use Bukkit, Paper, Minecraft, I/O, reflection, clocks, threads, synchronization or randomness.
- All public types are immutable. `plan` is a pure function: it keeps no state between calls and reads no clock.
  Cross-tick fairness and visual rotation come only from the caller's `tickOrdinal` and `frameOrdinal`.

## 2. Public API (complete)

```java
package workshop.vfx.delivery;

public enum QualityTier { SIMPLE, BALANCED, HEAVY }            // ascending geometry density / emission quota
public enum EffectPriority { ACTION, NORMAL, AMBIENT }          // allocation order: ACTION first
public enum LayerRole { ESSENTIAL, STANDARD, DECORATIVE }       // kept first .. dropped first
public enum Outcome {
    DELIVERED, THINNED,
    SKIPPED_NO_VIEWERS, SKIPPED_NO_ELIGIBLE_TIER, SKIPPED_UNSUPPORTED, SKIPPED_GENERATION_BUDGET,
    SKIPPED_INACTIVE, SKIPPED_DELIVERY_BUDGET, FAILED_EVALUATION
}

public final class EffectRecipe {
    public static final int MAX_ROLES = 4096;
    public static EffectRecipe of(String name, EffectPriority priority,
                                  Map<QualityTier, EffectScene> variants, Map<String, LayerRole> roles);
    public String name();
    public EffectPriority priority();
    public TimeDomain domain();
    public Set<QualityTier> tiers();                             // unmodifiable, ascending
    public EffectScene variant(QualityTier tier);                // IAE if absent
    public LayerRole role(String layerName);                     // STANDARD if not mapped
}

public final class DeliveryRequest {
    public static final int MAX_VIEWERS = 1 << 16;
    public static DeliveryRequest of(long id, EffectRecipe recipe, QualityTier tier, int worldId,
                                     Pose anchor, Playback playback, TimePoint now,
                                     long frameOrdinal, long[] viewerIds);
    public long id();
    public EffectRecipe recipe();
    public QualityTier tier();
    public int worldId();
    public Pose anchor();
    public Playback playback();
    public TimePoint now();
    public long frameOrdinal();
    public int viewerCount();
    public long viewer(int index);
}

public record DeliveryPolicy(int maxRequests, int maxViewersPerRequest, long maxViewerEntries,
                             long generatedPointBudget, long ambientGeneratedReserve,
                             long placementBudget, long ambientPlacementReserve,
                             long worldPlacementBudget, long viewerPlacementBudget,
                             int simpleKeptCap, int balancedKeptCap, int heavyKeptCap) {
    public static final int MAX_REQUESTS_CEILING = 1 << 16;
    public static final long MAX_VIEWER_ENTRIES_CEILING = 1L << 24;
    public static final long MAX_BUDGET = 1L << 50;
    public int keptCap(QualityTier tier);
}

public final class DeliveryPlanner {
    public static DeliveryPlan plan(long tickOrdinal, List<DeliveryRequest> requests,
                                    Set<StyleKey> supportedStyles, DeliveryPolicy policy);
}

public final class DeliveryPlan {
    public long tickOrdinal();
    public List<EffectDelivery> deliveries();                    // input order, unmodifiable
    public int count(Outcome outcome);
    public long reservedGeneratedPoints();
    public long sourcePoints();
    public long keptPoints();
    public long recipients();
    public long placements();
    public long unsupportedPoints();
}

public final class EffectDelivery {
    public long requestId();
    public int allocationRank();                                 // position in the allocation order
    public Outcome outcome();
    public QualityTier tier();                                   // null if no tier was chosen
    public EffectFrame frame();                                  // null if not evaluated; never copied
    public long reservedGeneratedPoints();
    public int sourcePoints();
    public int unsupportedPoints();
    public int keptPoints();
    public long placements();
    public int orderedBatchCount();                              // batches with kept points, in selection order
    public int orderedBatch(int position);                       // index into frame().batches()
    public int kept(int position);
    public int selectedIndex(int position, int j);               // point index inside that batch
    public int recipientCount();
    public long recipient(int r);                                // ascending viewer ids
    public int recipientPoints(int r);                           // first recipientPoints(r) selections
}
```

**Shape rules:**
- There are no other public types or members, apart from what records and enums generate.
- The classes are final, with no public constructors.
- One public type per file. Package-private helpers are allowed.

## 3. Common rules

- **Null:** a null argument or element gives `IllegalArgumentException` (IAE).
- **Validation order:** every check that bounds an allocation runs before that allocation (sizes first, then copies).
- **Exceptions:**
  - invalid input gives IAE;
  - nothing else is thrown by `plan`. M5 `ArithmeticException`s during evaluation are confined to their request
    (`FAILED_EVALUATION`).

  `plan` returns a complete plan or throws IAE before evaluating any scene.
- **Integers:** every count is computed in `long`. The bounds of sections 4 and 9 make every sum and product fit
  with margin (the largest is under 2^62), so no operation can overflow. `Math.multiplyExact` and `Math.addExact`
  are not needed.

## 4. Construction and validation

### 4.1 `EffectRecipe.of(name, priority, variants, roles)`

- `name` matches `[a-z0-9_.-]{1,64}`; priority is nonnull.
- `variants`: 1 to 3 entries, no null keys or values, and every scene with the same `TimeDomain` (else IAE).
- `roles`:
  - at most `MAX_ROLES` entries, no nulls;
  - every key is the name of a layer in at least one variant (else IAE, which catches typos).

  Unmapped layers are `STANDARD`.
- The maps are copied. `tiers()` lists the tiers present in ascending order.

### 4.2 `DeliveryRequest.of(...)`

- recipe, tier, anchor, playback and now nonnull.
- `playback.anchor().domain()` and `now.domain()` equal `recipe.domain()` (else IAE), so the M5 domain check cannot
  fail later.
- `viewerIds`:
  - nonnull, length at most `MAX_VIEWERS`, checked **before** copying;
  - then copied, and required to be strictly increasing and `>= 0` (else IAE).
- `worldId >= 0`. `id` and `frameOrdinal` may be any long.

### 4.3 `DeliveryPolicy`

The compact constructor enforces all of these (IAE otherwise):

| Field | Rule |
|---|---|
| `maxRequests` | `1 <= maxRequests <= 2^16` |
| `maxViewersPerRequest` | `0 <= maxViewersPerRequest <= DeliveryRequest.MAX_VIEWERS` |
| `maxViewerEntries` | `0 <= maxViewerEntries <= 2^24` |
| `generatedPointBudget`, `placementBudget`, `worldPlacementBudget`, `viewerPlacementBudget` | each in `[0, 2^50]` |
| `ambientGeneratedReserve` | in `[0, generatedPointBudget]` |
| `ambientPlacementReserve` | in `[0, placementBudget]` |
| kept caps | `0 <= simpleKeptCap <= balancedKeptCap <= heavyKeptCap <= 2^20` |

`keptCap(tier)` returns the cap of that tier.

There are **no default values**. Every number is a server owner's decision (DM6-11).

### 4.4 `plan(tickOrdinal, requests, supportedStyles, policy)`: input checks, in order

1. requests, supportedStyles and policy are nonnull.
2. `requests.size() <= policy.maxRequests()`, checked before copying.
3. Copy the requests and the style set. There must be no null element.
4. Request ids are unique.
5. For every request, `viewerCount() <= policy.maxViewersPerRequest()`, and the sum of viewer counts (in `long`) is
   at most `policy.maxViewerEntries()`.

Any failure gives IAE, and no scene has been evaluated.

## 5. Allocation order (normative)

- Group the requests by `recipe.priority()`, in the order ACTION, NORMAL, AMBIENT.
- Within a group of size m, sort by `id` ascending, then rotate: the order starts at position
  `s = Math.floorMod(tickOrdinal, m)` and wraps.
- The concatenation of the groups is the **allocation order**. `allocationRank()` is the position in it.
- Both phases process requests in this order.

## 6. Phase 1: admission before evaluation (normative)

**Budgets.** `G = generatedPointBudget`, `R_g = ambientGeneratedReserve`; `used_g = 0`.

**Available generation for request q:**
- if q is AMBIENT: `G - used_g`;
- otherwise: `max(0, G - R_g - used_g)`. Non-ambient requests can never consume the ambient reserve.

**Steps.** For each request q in allocation order:
1. If `q.viewerCount() == 0`: outcome `SKIPPED_NO_VIEWERS`. There is no evaluation and no reservation.
2. **Candidates:** the recipe's tiers that are at most `q.tier()`, in descending order. The requested tier is a
   **strict ceiling**: a higher tier is never selected.
   - If there is no candidate (for example a SIMPLE request for a recipe that has only BALANCED and HEAVY):
     `SKIPPED_NO_ELIGIBLE_TIER`. There is no evaluation and no reservation, and the delivery's `tier()` is null.
3. A candidate scene is **deliverable** iff at least one of its layers has a supported style and a non-empty shape.
4. If no candidate is deliverable: `SKIPPED_UNSUPPORTED`.
5. Otherwise, choose the first deliverable candidate with `scene.maxPointsPerFrame() <= available`.
   - If there is none: `SKIPPED_GENERATION_BUDGET`.
   - Else the request is **admitted** with that tier: `reservedGeneratedPoints = maxPointsPerFrame`, and `used_g`
     grows by that amount.
6. **Evaluate** each admitted request: `frame = variant.evaluate(q.anchor(), q.playback(), q.now())`.
   - An `ArithmeticException` gives `FAILED_EVALUATION`. The reservation stays counted, and there is no frame.
   - If `frame.stage() != ACTIVE` or `frame.isEmpty()`: `SKIPPED_INACTIVE`, with the frame kept for reference.

**Consequence (normative):**
- The sum of `reservedGeneratedPoints` is at most `G`, and that of non-ambient requests at most `G - R_g`.
- Every evaluated frame has `pointCount() <= reservedGeneratedPoints`, by the M5 preflight.
- Requests without viewers, without an eligible tier, without deliverable styles, or over budget cost **no
  evaluation**.
- The selected tier is never above `q.tier()`.

## 7. Phase 2: delivery allocation after evaluation (normative)

**Budgets.**
- Global: `P = placementBudget`, `R_p = ambientPlacementReserve`, `used_p = 0`.
- Per world: `W[w] = worldPlacementBudget` remaining, created on first use.
- Per viewer: `V[v] = viewerPlacementBudget` remaining, created on first use and keyed by viewer id across all worlds.

For each request q in allocation order whose frame is ACTIVE and non-empty:
1. **Supported batches:** the frame's batches whose `style()` is in `supportedStyles`.
   - `unsupportedPoints` is the sum of the other batches' sizes.
   - `N` is the sum of the supported sizes. If `N == 0`: `SKIPPED_UNSUPPORTED`.
2. **Effect cap:** `kmax = min(N, policy.keptCap(tier), max over q's viewers v of V[v])`.
   - The last term means that no point is counted as kept unless at least one viewer can receive it, so
     `keptPoints` equals the largest `recipientPoints`.
   - In particular, if every viewer of q has exhausted its budget, `kmax = 0` and the outcome is
     `SKIPPED_DELIVERY_BUDGET` (step 5), not a delivery with zero placements.
3. **Budget:** `B = min(available_p, W[world])`, where available_p is `P - used_p` for AMBIENT and
   `max(0, P - R_p - used_p)` otherwise.
4. **Kept count:** `k` is the largest integer in `[0, kmax]` with
   `cost(k) = sum over q's viewers v of min(k, V[v]) <= B`.
   - cost is nondecreasing in k, and cost(0) = 0, so k exists.
   - Any exact method may be used; the tests compare k, not the method. The reference uses binary search.
5. If `k == 0`: `SKIPPED_DELIVERY_BUDGET`.
6. **Layer roles:** order the supported batches by `(role rank, layerIndex)`, with the rank ESSENTIAL 0, STANDARD 1,
   DECORATIVE 2. Take the role groups in rank order, with `r = k` points left to place:
   - if the group's total `T <= r`: keep every point of the group, and `r -= T`;
   - otherwise:
     - each batch b of size n_b gets `base_b = floor(r n_b / T)`;
     - the remaining `r - sum(base_b)` points go, one each, to the batches with the largest `(r n_b) mod T`, with ties
       to the earlier position;
     - then `r = 0`.

   Compute `r n_b` in long (at most 2^40). Batches whose kept count is 0 are dropped from the ordered list.
7. **Point selection** in a batch with n points and kept count m (`0 < m <= n`):
   - `g = (n + m - 1) / m` (that is `ceil(n / m)`), and `o = Math.floorMod(q.frameOrdinal(), g)`;
   - `selectedIndex(position, j) = o + (int) ((long) j * n / m)` for `j = 0 .. m - 1`.
   - **Lemma (no wrap):** `o <= g - 1` and `floor(j n / m) <= floor((m - 1) n / m) = n - g`, so every index is at
     most `n - 1`. The indices of a batch are strictly increasing, and no modulus is needed.
     Consecutive indices differ by at most g, and the first index is at most g - 1 and the last at least n - g, so the
     offsets `0 .. g - 1` together cover every index (G6).
8. **Recipients:** the viewers v of q in ascending id order with `c_v = min(k, V[v]) > 0`, with
   `recipientPoints = c_v`.
   - A recipient receives the **first** `c_v` selections of the sequence: ordered batches in position order, and
     within a batch j ascending. Saturated viewers therefore get the essential points first.
   - Then `V[v] -= c_v`, `W[world] -= cost(k)` and `used_p += cost(k)`. Here `placements = cost(k)` and
     `keptPoints = k`.
9. **Outcome:** `DELIVERED` if `k == N`, `unsupportedPoints == 0` and every viewer of q received all k points
   (`recipientCount() == q.viewerCount()` and every `recipientPoints == k`); otherwise `THINNED`. `DELIVERED`
   therefore means that every viewer was sent the complete frame.

**Plan totals** sum the per-delivery values:
- `count(outcome)`;
- `reservedGeneratedPoints`;
- `sourcePoints` (the `pointCount()` of every evaluated frame);
- `keptPoints`;
- `recipients` (the sum of `recipientCount()`);
- `placements`;
- `unsupportedPoints`.

## 8. Guarantees (normative; checked by the tests)

| # | Guarantee |
|---|---|
| G1 | sum of reserved generated points <= `generatedPointBudget`; the non-ambient part <= `generatedPointBudget - ambientGeneratedReserve` |
| G2 | every frame evaluated by `plan` has `pointCount() <= reservedGeneratedPoints` of its request; skipped requests are never evaluated |
| G3 | total placements <= `placementBudget`; non-ambient placements <= `placementBudget - ambientPlacementReserve`; per world <= `worldPlacementBudget`; per viewer <= `viewerPlacementBudget` |
| G4 | per delivery: `keptPoints <= min(N, keptCap(tier))`; `recipientPoints(r) <= keptPoints`; `keptPoints = max recipientPoints`; `placements = sum recipientPoints` |
| G5 | selected indices in a batch are distinct and in `[0, n)`; the kept counts per batch sum to `keptPoints` |
| G6 | **Coverage:** for fixed n and m, the union of the selections over any g consecutive frame ordinals is all n indices |
| G7 | **Role precedence:** a STANDARD point is kept only if every supported ESSENTIAL point is kept, and likewise DECORATIVE after STANDARD |
| G8 | **Determinism:** the plan is a pure function of the arguments: identical across repetition and threads |
| G9 | **Maximality:** k is the largest admissible value; increasing it by one would violate a budget or the cap |
| G10 | **Reserve protection (capacity, not allocation):** non-ambient requests never consume the ambient reserves. In allocation order, the non-ambient reservations never exceed `generatedPointBudget - ambientGeneratedReserve`, and the non-ambient placements never exceed `placementBudget - ambientPlacementReserve`. AMBIENT requests come last, so when the first AMBIENT request is considered, at least `ambientGeneratedReserve` generation and `ambientPlacementReserve` global placements remain unspent. **No minimum realized ambient allocation is guaranteed** (see below). |
| G11 | **Quality ceiling:** an admitted request's tier is at most its requested tier; a request with no recipe tier at or below it is `SKIPPED_NO_ELIGIBLE_TIER` |

**Fairness, honestly stated (DM6-5):**
- Within one priority group, if the same m requests are present for m consecutive tick ordinals, each one is first in
  its group exactly once (rotation).
- Priorities are strict: higher groups are served first. Lower groups are protected only by the AMBIENT reserves
  (G10); NORMAL requests have no reserve. **No starvation-freedom is claimed** beyond these two statements.

**Why G10 promises capacity and not a minimum allocation (R2 correction).** R1 claimed that ambient requests could
always use at least `min(reserve, demand)`. That is false, and the tests now contain both counterexamples:
- **Generation reservations are indivisible.** A scene costs its full `maxPointsPerFrame` or nothing. With
  `G = 100` and `R_g = 30`, if ACTION reserves 70 and two AMBIENT scenes cost 20 each, the first is admitted and the
  second needs 20 of the 10 remaining: realized 20 < min(30, 40) = 30. A single AMBIENT scene of 31 points is
  realized as 0 < 30.
- **Placement cost is per viewer.** A request's cost is `sum over viewers min(k, V[v])`, which grows in steps of up to
  the viewer count. With `P = 100` and `R_p = 5`, if ACTION places 95, an AMBIENT effect with 3 viewers gets k = 1 and
  3 placements < min(5, 30) = 5. With 6 viewers it gets k = 0 (`SKIPPED_DELIVERY_BUDGET`).

World budgets, viewer budgets and kept caps can also bind. The reserve guarantees only that the capacity is there
when the AMBIENT group starts.

## 9. Limits, work and memory (normative)

| Quantity | Bound |
|---|---|
| requests per call | `maxRequests <= 2^16` |
| viewer entries per call | `maxViewerEntries <= 2^24` |
| M5 evaluations | one per admitted request; their output is at most `generatedPointBudget` points in total |
| planner memory beyond M5 frames | O(requests + viewer entries + batches of admitted frames); no coordinate is copied |
| phase 2 work per request | O(viewers x log(kmax + 1) + batches log batches) |
| `selectedIndex` | O(1), computed on demand (no index arrays) |

## 10. Determinism and numbers

- **Integer arithmetic only** (`long`, `int`, `/`, `%`, `Math.floorMod`, `min`, `max`). No floating point is used in
  planning. Coordinates are M5's and are never read or copied by M6.
- The order of every iteration is specified (sections 5 to 7). Maps keyed by world or viewer id hold budgets only,
  and are never iterated for output.

## 11. Out of scope

- Particle emission, Paper particle types and data, viewer discovery, scheduling, threads, Folia regions,
  effect lifecycles (start, stop, logout, teleport, unload, reload) and persistent budgets across ticks.
- Gameplay: cooldowns, ownership, permissions, damage and healing.
- Client and network measurement.
- Adapter outcomes (attempted, succeeded, failed) are recorded by the adapter (`M6-ARCHITECTURE.md` section 6).
- Any change to M1 to M5.
