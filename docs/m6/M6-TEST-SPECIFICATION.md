# M6 test specification (R1 proposal)

Status: **PROPOSED, revision 2.** The tests in `src/test/java/workshop/vfx/delivery` are the executable form of
`M6-IMPLEMENTATION-CONTRACT.md`. **52 tests in 9 classes**, plus the helper `M6TestSupport`.

Before the implementation exists, the M6 tests do not compile; this is expected (as for M1 to M5). The 363 accepted
tests are unchanged. With an implementation, `./gradlew test` must report **415 tests** (363 + 52), all passing.

## 1. Classes and coverage

| Class | Tests | Contract | What it pins |
|---|---|---|---|
| `DeliveryReferenceTest` | 7 | 5-7 | manifest, plus the six fixture families field by field (section 4) |
| `DeliveryValidationTest` | 7 | 3, 4 | every validation rule and bound inclusive at both ends; copies; check order; a hostile list rejected before it is read; accessor index errors; empty plan |
| `AdmissionTest` | 9 | 6 | downgrade-only tier choice; G11 strict ceiling (`SKIPPED_NO_ELIGIBLE_TIER` at any budget, never evaluated, no reservation, checked after the viewer check and before styles and budget); missing tiers; deliverability (empty shapes, unsupported styles); skipped requests never evaluated; isolated `FAILED_EVALUATION` with its reservation kept; `SKIPPED_INACTIVE` cases (before, after, ended layers, empty prefix); phase-2 `SKIPPED_UNSUPPORTED`; generation reserve |
| `BudgetPropertyTest` | 7 | 7, 8 | G1-G5, G7, G9 and phase 1 on 3,000 random scenarios (every outcome at least 20 times); input-order independence; exact global, world and viewer boundaries; saturated viewers; G10 reserve protection; **both R2 counterexamples to the R1 G10 claim** (indivisible generation reservations; per-viewer placement cost) |
| `ThinningSelectionTest` | 6 | 7.6-7.8 | the selection formula at extreme ordinals; G6 coverage over g consecutive ordinals for 10 sizes; G7 role precedence; largest-remainder ties; recipient prefixes; selections index the M5 frame |
| `FairnessTest` | 4 | 5, 8 | exact allocation order including `floorMod` at `Long.MIN_VALUE`/`MAX_VALUE`; rotation once per m ticks; strict priority (NORMAL can starve); both phases share the order |
| `M6DeterminismTest` | 2 | 8 (G8) | 200 scenarios x 10 repetitions; 100 scenarios on 8 threads |
| `M6BoundaryTest` | 4 | 1, 2, 10 | imports; forbidden constructs (including any floating point); static fields; the exact API shape and constants |
| `M6StressTest` | 6 | 9 | 2^16 requests; 2^20 viewer entries; the full 2^20-point frame kept and thinned; Long/Integer extremes and zero budgets; **denied preflight allocates under 64 KiB** for a 2^20-point scene; 4,096 layers |

## 2. The invariant checker (`M6TestSupport.checkInvariants`)

The checker is an independent oracle that shares no code with any planner. Given the inputs and a reported plan, it
checks:
- **deliveries:** they are in input order, and the allocation ranks are a permutation;
- **allocation order:** the priority order, and the rotation within each class (contract 5);
- **phase 1** (contract 6), replayed in rank order: expected outcome (including `SKIPPED_NO_ELIGIBLE_TIER`), tier
  and reservation; G11 (tier at most the requested tier);
- **G10:** when the AMBIENT class starts, at least the generation and placement reserves remain unspent;
- **G2:** the frame and source-point rules per outcome;
- **phase 2**, replaying the reported recipients to rebuild the remaining global, world and viewer budgets:
  - cost fits the budget (G3);
  - k cannot grow (G9);
  - `k <= min(N, cap, max V)`;
  - `keptPoints = max recipientPoints` (G4);
  - recipients equal `min(k, V[v]) > 0` in viewer order;
  - placements equal the cost;
  - the outcome rule;
- **G5:** selections are distinct and in range, sum to k, and use supported batches only;
- **G7:** a role keeps points only if every higher role is complete;
- **totals:** G1 and G3 totals, the counts per outcome, and every plan total.

It runs on every fixture case and every random scenario.

## 3. Random scenarios (`M6TestSupport.randomScenario`)

Each scenario has:
- 1 to 6 recipes with 1 to 3 tiers and 1 to 5 layers (sizes 0 to 40, random styles from 4, random roles, PREFIX
  clips, lifetimes 1 to 25);
- 0 to 40 requests with ids that are small or arbitrary longs, 0 to 6 viewers from a pool of 12, 3 worlds, about 7 %
  failing anchors, local ticks including -1 and half ticks, and ordinals that are small or arbitrary longs;
- a random subset of supported styles;
- small random budgets, so that every budget binds.

Seeds are fixed.

## 4. Reference fixtures

`tools/generate_m6_reference_plans.py` (Python 3 standard library, exact integers and `Fraction`) implements the
contract independently; for example, it finds k with a linear scan from the top.

Output in `src/test/resources/m6/reference/`, with `MANIFEST.sha256`:

| Family | Cases | Expectations | Focus |
|---|---|---|---|
| bloom | 6 | 57 | ambient flowers plus an action strike; reserves; rotation over 5 ticks; tier downgrade under a burst |
| cloudstep | 3 | 23 | overlapping activations; per-viewer limits; worlds; a decorative style unsupported; one viewer in three effects across worlds |
| healer | 8 | 8 | markers ESSENTIAL, sparkles DECORATIVE at caps 3-200; ended scene; placement-limited |
| trident | 6 | 6 | PREFIX self-drawing helix at varying viewer counts (0 to 300); world switch; end of life |
| astral | 9 | 9 | generation budgets forcing HEAVY to BALANCED to SIMPLE to skip; caps; trails dropped first; unsupported trails; nothing supported |
| stress | 13 | 55 | 30 simultaneous effects; R2: no eligible tier (strict ceiling), the two G10 counterexamples (generation indivisible: realized 20 < 30 and 0 < 30; placement per viewer: 3 viewers give 3 < 5, 6 viewers give 0); exact budget and one below; the full 2^20 frame denied and admitted; ids near 2^62 with negative ordinals and tick `Long.MIN_VALUE`; isolated failure; zero budgets |

Line format: `case`, `policy` (12 fields in record order), `styles`, `recipe`, `variant`, `request`, `expect`,
`totals`, `end`. The generator's docstring describes each field. The Java test rebuilds the scenes with the M5 API
(contract-neutral static rings) and compares:
- rank, outcome, tier, reserved, source, unsupported, kept and placements;
- every ordered batch (frame index, m, phase, n), with every selected index;
- every recipient;
- every total and count.

Regenerating the fixtures twice gives byte-identical files.

## 5. Mutation analysis

58 hand-written mutants of the throwaway reference implementation cover:
- order, rotation and priority;
- tier choice and deliverability;
- reserves and every budget;
- the k search and caps;
- roles and remainders;
- stride and phase;
- outcome rules;
- every validation bound.

**Result: 56 killed, 2 survivors, both proven equivalent** (M34: the explicit complete-group case; M46: the
redundant `% n`, now removed from the contract). The failing tests per mutant are in
`docs/m6/validation/mutation-results.txt`.

## 6. Measurement methodology

`docs/m6/validation/PerformanceProbe.java`:
- runs 3 warm-up rounds, then measures with `System.nanoTime` and `ThreadMXBean.getCurrentThreadAllocatedBytes`;
- reports per call: plan time, the time of the same M5 evaluations alone, the time per viewer entry net of evaluation,
  and bytes allocated;
- uses workloads from 16 effects x 8 viewers up to the request and viewer-entry ceilings, plus the full 2^20 frame.

These are single-JVM, single-container numbers and are indicative only. They are not server budgets.

## 7. Required production checks (for the later implementation step)

- the 415 tests pass; `check` passes; the runtime classpath is empty; production compiles with 0 warnings;
- the generator output is byte-identical to the fixtures, and the manifest verifies;
- M1 to M5 sources, tests, fixtures and generators are byte-identical to `d4970cc`;
- the M3, M4 and M5 reference generators still reproduce their fixtures.
