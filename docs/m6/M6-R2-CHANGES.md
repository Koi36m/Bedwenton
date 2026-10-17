# M6 R2: focused specification corrections

Status: **design proposal only, revision 2.** There is no M6 production code, no Paper code and nothing merged into
`main`. This revision stops for ChatGPT's independent acceptance review.

## Identities

| Item | Value |
|---|---|
| Starting point (R1 specification) | `e3df49957e900625cf1a05db17d8405d8cd9d39b` on `m6-spec` |
| R2 branch | `m6-spec-r2`, descending from `e3df499` |
| Accepted M5 baseline | `d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb` (unchanged) |
| Review | ChatGPT, `M6-R1-Independent-Design-Review.md`: HOLD for two corrections; architecture approved in principle |

**Input not available.** `M6-R1-Independent-Design-Review.md` was not attached and is not on any branch, so it could
not be read. The two corrections were implemented as the R2 instruction states them. The two G10 counterexamples below
are reconstructed from the mechanisms the instruction names: indivisible scene reservations and multi-viewer placement
costs. **If the review's counterexamples use other numbers, reconcile them before acceptance.** The tests check the
mechanisms, not particular numbers.

## F1: G10 corrected to reserve protection

**Problem.** R1's G10 promised that AMBIENT requests could always use at least `min(reserve, demand)` placements and
generation. That is false:

| Counterexample | Setting | Realized | R1 claim |
|---|---|---|---|
| Generation: reservations are indivisible | `G = 100`, `R_g = 30`; ACTION reserves 70; two AMBIENT scenes of 20 | 20 (the second scene needs 20 of the 10 left) | `min(30, 40) = 30` |
| | same, with one AMBIENT scene of 31 | 0 | `min(30, 31) = 30` |
| Placements: cost is per viewer | `P = 100`, `R_p = 5`; ACTION places 95; AMBIENT effect of 10 points, 3 viewers | k = 1, 3 placements | `min(5, 30) = 5` |
| | same, with 6 viewers | k = 0, `SKIPPED_DELIVERY_BUDGET` | 5 |

**New G10 (contract section 8).** It promises capacity, not allocation:
- non-ambient requests never consume the ambient reserves. In allocation order, non-ambient reservations stay at most
  `G - R_g`, and non-ambient placements at most `P - R_p`;
- so when the first AMBIENT request is considered, at least `R_g` generation and `R_p` global placements remain
  unspent;
- **no minimum realized ambient allocation is guaranteed.** The contract explains why, with both counterexamples.

The planning algorithm is **unchanged**. Only the stated guarantee was wrong.

**Tests.**
- `BudgetPropertyTest.reserveCounterexampleGeneration` and `.reserveCounterexamplePlacement` (new) check each
  counterexample. They assert that the realized value is below R1's claimed minimum, and that the reserve was intact
  when AMBIENT started.
- `BudgetPropertyTest.placementReserve` was rewritten: it no longer asserts a minimum.
- `M6TestSupport.checkInvariants` now asserts the G10 capacity condition at the start of the AMBIENT class, for every
  fixture case and random scenario.
- **Fixtures** (independent Python oracle): `stress-reserve-generation-indivisible`,
  `stress-reserve-generation-too-large`, `stress-reserve-placement-3-viewers` and `stress-reserve-placement-6-viewers`.

## F2: the requested quality tier is a strict ceiling

**Problem.** When a recipe had no tier at or below the request, R1 fell back to the recipe's lowest tier. A SIMPLE
request for a recipe with only BALANCED and HEAVY therefore silently became BALANCED.

**New rule** (contract section 6 step 2, and new guarantee G11):
- candidates are only the recipe's tiers at or below `q.tier()`;
- if there is none, the outcome is the new **`Outcome.SKIPPED_NO_ELIGIBLE_TIER`**, with no evaluation, no reservation
  and a null tier;
- the check runs after `SKIPPED_NO_VIEWERS` and before the style and budget checks. The outcome states the actual
  cause: the recipe offers nothing at the requested quality.

**API change:** one enum constant, inserted after `SKIPPED_NO_VIEWERS`. `Outcome` now has nine constants. There is no
other signature change.

**Tests.**
- `AdmissionTest.missingTiers` was rewritten: a SIMPLE request against BALANCED/HEAVY at three budgets including
  unlimited, and a BALANCED request against HEAVY only.
- `AdmissionTest.noEligibleTierOrder` (new) checks:
  - the precedence against no viewers and unsupported styles;
  - a failing anchor that is never evaluated;
  - that nothing is reserved.
- The checker's phase-1 replay now expects `SKIPPED_NO_ELIGIBLE_TIER` and asserts G11 on every admitted request.
- `M6BoundaryTest.apiShape` lists the new constant.
- **Fixture:** `stress-no-eligible-tier`, in which the SIMPLE request carries a failing anchor and still gets
  `SKIPPED_NO_ELIGIBLE_TIER`.
- The random property test now produces every outcome, including the new one, at least 20 times.

## Exact changes (`git diff --stat e3df499`)

**Documents:**
- `M6-IMPLEMENTATION-CONTRACT.md`: status; `Outcome` gains `SKIPPED_NO_ELIGIBLE_TIER`; section 6 step 2 and its
  consequences; G10 rewritten; G11 added; the G10 rationale with both counterexamples.
- `M6-DESIGN-DECISIONS.md`: DM6-2 (strict ceiling replaces "never upgrade, except..."); DM6-5 (reserve protection,
  no minimum); DM6-12 case count.
- `M6-ARCHITECTURE.md`, `M6-TEST-SPECIFICATION.md`, `README-FOR-CODEX-M6.md`: the new outcome, the counts and the
  G10 wording.
- `M6-R1-REPORT.md`: status line only. This file is new.

**Tests:**
- `AdmissionTest`: +1 test, 1 rewritten;
- `BudgetPropertyTest`: +2 tests, 1 rewritten;
- `M6BoundaryTest`: the enum list;
- `M6TestSupport`: the phase-1 replay, G10 and G11 checks.

**Generator and fixtures:**
- `tools/generate_m6_reference_plans.py`: the strict ceiling and the 5 new stress cases;
- `stress.txt`: 5 cases appended;
- `MANIFEST.sha256`.

The other five fixture families and the 8 R1 stress cases are byte-identical to R1.

**Evidence:** in `docs/m6/validation/`, the run summary, regeneration and baseline check were refreshed, and
`mutation-results-r2.txt` and `mutants-r2.py` were added. `performance.txt` and the R1 mutation files are unchanged
from R1. Mutation testing is a historical R1 record; the planning cost is unaffected by R2.

## Counts

| | R1 | R2 |
|---|---|---|
| M6 tests | 49 in 9 classes | **52** in 9 classes (`AdmissionTest` 9, `BudgetPropertyTest` 7) |
| Full suite with the throwaway reference | 412/412 | **415/415** (313 + 50 + 52) |
| Fixture cases / per-request expectations | 40 / 146 | **45 / 158** |
| `Outcome` constants | 8 | **9** |
| Mutation | 58 mutants: 56 killed, 2 equivalent | unchanged, plus a **targeted 8 of 8 killed** for the R2 paths |

## Verification performed

- **Full Java 25 suite** against the revised throwaway reference: 415/415; `check` succeeds; no new warnings.
- **Independent regeneration** of all six generators: byte-identical. The M6 generator gives identical output on two
  runs, and its manifest verifies.
- **Budget invariants and failure cases:** G1 to G11 are checked on every fixture case and on 3,000 random scenarios.
  Both G10 counterexamples and the no-eligible-tier case are checked in Java and in the Python fixtures.
- **Baseline:** against `d4970cc`, additions only, all within M6 paths. M1 to M5 files are untouched.
- **Not run:** Paper, Bukkit, Folia and GitHub Actions. The R2 push from this environment is expected to be refused
  (HTTP 403), as before.
