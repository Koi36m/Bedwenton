# M7 R2: focused corrections (change log)

Status: **design proposal, revision 2, for ChatGPT's independent acceptance review.** There is no production adapter,
no plugin deployment, nothing merged into `main` and no combat or enchantment mechanics.

| Item | Value |
|---|---|
| Starting point | M7 R1 `f72179d917623b5286bc6b52f6e8e982d3ad0d2d` on `m7-spec` |
| R2 branch | `m7-spec-r2`, from `f72179d` |
| Accepted M6 baseline | `c43a66e656a77aa82bd72d8ffe27f1a037240e99`, unchanged |

**Input not available.** `M7-R1-Independent-Design-Review.md` was not attached and is not on any branch. The
corrections below implement the R2 instruction as stated. **Where the review's reasoning differs, reconcile it before
acceptance.**

## F1: owner-aware emission authorization

**Problem (R1).** `RecipientGate.check(viewerId, worldId, anchor)` received no request or owner identity, yet the rule
was "owner or has view". With two coincident rings, the gate could not tell whose ring a recipient was being judged
for.

**R2 changes:**
- **SPI:** `RequestContext(requestId, ownerViewerId, worldId, anchor)` is now its own record, keyed by the M6 request
  id.
- **SPI:** the gate becomes `RecipientGate.check(RequestContext, long viewerId)`.
- **SPI:** new pure rule `RecipientRule` (production `OwnerAwareRecipientRule`) over `ViewerFacts`:
  - the owner needs `bedwenton.vfxdemo.use`;
  - anyone else needs `bedwenton.vfxdemo.view`;
  - the checks are ordered offline, world, permission, range.
- **Missing context:** the whole delivery is skipped (new `SkipReason.MISSING_REQUEST_CONTEXT`). Nothing is guessed.
- **Contract:** section 7 step 1, and section 6 step 1 (contexts are recorded at the snapshot).

**Tests** (`OwnerAwareEmissionTest`, 7 tests): coincident rings with no view permission; a non-owner holding use but
not view refused for the other ring; view granted and revoked; owner use revoked; an owner who also holds view;
missing context.

**Smoke:** new rows S16 and S17.

## F2: allocation fairness during emission

**Problem (R1).** The emitter walked `plan.deliveries()`, which is in **input order**, and truncated at its own call
caps. When the caps bound, input order, not M6's rank, decided which ring was visible.

**R2 changes:**
- **Order:** the emitter sorts deliveries by `allocationRank()`.
- **Budget alignment (new contract section 9.1):** the effective M6 placement and world budgets are capped by
  `maxCallsPerTick`, and the viewer budget by `maxCallsPerViewerPerTick`. M6, allocating in rank order, therefore
  decides who gets capacity.
- **Defensive limits:** the emission limits remain as a backstop. If they bind, the points dropped are the latest in
  rank order.

**Tests** (`AllocationOrderEmissionTest`, 3 tests):
- with the caps forced to bind, the visible ring is the rank-0 ring for both input orders and alternates with the tick
  rotation;
- with the aligned policy there is no `EMISSION_LIMIT` skip, and attempted calls equal planned placements (fixed
  cases, and 300 random scenarios).

## F3: session-wide style disabling (contract section 7.1)

**Problem (R1).** "3 consecutive failures for one style in a tick" left undefined what consecutive means, when the
streak resets, and how planning excludes the style.

**R2 rule:**
- Each style has one streak for the whole session. A `RuntimeException` from `send` adds 1.
- Only a successful send **of the same style** resets it. It is not reset by other styles, ticks or rings.
- The third consecutive failure disables the style **at once**. Its remaining points in the tick become
  `STYLE_DISABLED`, and it is reported once in `stylesDisabledThisTick`.
- From the next planning call, `StyleHealth.enabledStyles()` omits the style, and M6 treats it as unsupported. **M6 is
  unchanged.**
- The style stays disabled until `onDisable`.
- **SPI:** new `StyleHealth`; `EmissionReport` gains `skippedDeliveries` and `stylesDisabledThisTick`; the emitter
  receives the health object.

**Tests** (`StyleHealthTest`, 5 tests): disable after three; the streak resets on a success of the same style; it is
not reset by the other style and spans ticks; the next plan excludes the style (M6 THINNED, with 16 unsupported
points); all styles disabled gives `SKIPPED_UNSUPPORTED`; a new session re-enables.

## Clarifications

| Topic | R2 resolution |
|---|---|
| Owner inclusion before viewer sorting | `ViewerSelection` (production `OwnerFirstViewerSelection`) admits the eligible owner first, fills the remaining places with the smallest other eligible ids, and only then sorts. Sorting never decides whether the owner is included. Covered by a test in `OwnerAwareEmissionTest`. |
| Plugin version placeholder | `processResources` expands `${version}` in `plugin.yml` from `project.version`. `verifyPluginJar` fails if the packaged file still contains `${` or a different version. |
| World-unload smoke | Unloading a world that contains the ring owner is impossible on ordinary Paper, and an owner who leaves has already stopped the ring, so the `WorldUnloadEvent` path with an active ring is defensive. It is tested offline (missing world drops the ring). S12 now checks world change (S9b) and shutdown unloading, without adding a debug command or extra plugin. |

## Files changed (`git diff --stat f72179d`)

**Documents:**
- `M7-ARCHITECTURE-AND-CONTRACT.md`: sections 2, 3, 6, 7 (rewritten, plus new 7.1), 8 (world unload) and 9.1 (new);
- `M7-TEST-AND-SMOKE-PLAN.md`: four new offline rows, S12 rewritten, S16 and S17 added;
- `M7-R1-REPORT.md`: status line; this file is new.

**SPI** (`docs/m7/spi`, now 15 files):
- new: `RequestContext`, `RecipientRule`, `ViewerFacts`, `ViewerSelection`, `StyleHealth`, `PointValidator`;
- changed: `RecipientGate`, `SkipReason`, `PlanEmitter`, `EmissionReport`, `EmissionLimits`.

**Proposed tests (new):** `docs/m7/tests/` (4 files, 15 tests).

**Evidence (new):** `validation/r2-tests.txt`, `validation/r2-mutants.py`, and `validation/r2-reference/` (the
throwaway reference: 4 classes, evidence only). `validation/preservation.txt` was refreshed.

## Verification

- **SPI:** `javac --release 25 -Xlint:all` against the accepted engine: exit 0, 0 warnings.
- **Proposed tests:** **15/15** against the scratch reference and the real accepted `DeliveryPlanner` and demo recipe,
  inside the isolated spike build (`includeBuild('..')`).
- **Targeted mutants of the reference: 6 of 6 caught**, including the three R1 defects:
  - emitting in input order;
  - the owner-blind "use or view" rule;
  - a streak reset each tick (along with resetting on another style's success).
- **Spike plugin jar:** no `org/bukkit`, `io/papermc` or `org/junit` entries.
- **Preservation:** against `c43a66e`, additions under `docs/m7/` only. The R1 run of 415/415 and the seven datasets
  remains valid: no file outside `docs/m7/` changed, so they were not rerun.
- **Not done:** compilation against `paper-api` (`repo.papermc.io` is still blocked here), and any Paper server run.
