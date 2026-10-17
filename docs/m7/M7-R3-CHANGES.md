# M7 R3: design corrections (change log)

Status: **design proposal, revision 3, for ChatGPT's independent review.** This is not an accepted milestone. There
is no production Paper adapter, no plugin deployment or release, nothing merged into `main`, and no new effect
features.

| Item | Value |
|---|---|
| R3 branch | `m7-spec-r3`, created from the R2 commit `15aa210ddcab79de6180df9bfa45a3ba17e1bcf7` |
| Ancestry | `15aa210` (R2) ← `f72179d` (R1) ← `74fefd3` (kickoff) ← `c43a66e` (accepted M6), preserved unchanged |
| Accepted M6 baseline | `c43a66e656a77aa82bd72d8ffe27f1a037240e99`; every changed path relative to it is under `docs/m7/` (`M7-R3-VERIFICATION.md`) |

## 1. F1 robustness: invalid coordinates fail closed

**Defect (reproduced by ChatGPT, and again here).** `OwnerAwareRecipientRule` computed `d2 > r*r` to deny. With a
NaN viewer coordinate or anchor coordinate the comparison is false, so the recipient was **allowed**. With the R2 logic
and the new tests, a viewer at `x = NaN` received 96 particles (`verification/logs/r3-tests-before-fix.log`).

**Correction** (contract section 7 step 1; SPI `RecipientRule`, `SkipReason`; reference `OwnerAwareRecipientRule`):
- After the permission check, the rule validates finiteness:
  - an anchor coordinate that is not finite gives **`INVALID_REQUEST_CONTEXT`**;
  - a viewer coordinate that is not finite gives **`VIEWER_POSITION_INVALID`** (new).
- The range test is written as `!(d2 <= r2)` → deny, so a NaN can never allow. This is defence in depth: with the
  checks above, `d2` cannot be NaN.
- **Order for normal inputs is unchanged:** online and valid, world, permission, range. Invalid coordinates of a viewer
  who is offline, in another world or lacking permission are reported as that earlier reason. Either way the viewer is
  denied.
- **Classification:** NaN, +infinity and -infinity are all *invalid*. +infinity was previously
  `VIEWER_OUT_OF_RANGE`; it is now `VIEWER_POSITION_INVALID`. Finite coordinates whose squared distance overflows to
  +infinity remain `VIEWER_OUT_OF_RANGE`.
- **Radius policy:** the rule's constructor throws `IllegalArgumentException` unless the radius is finite and >= 0;
  0 means exact anchor only. Config validation refuses enable for a NaN or infinite `viewRadius`, or one outside
  [4, 64]. No default is substituted.

**Tests:** `RecipientRuleRobustnessTest` (5 tests) covers:
- NaN, +infinity and -infinity on each viewer axis, for the owner and a non-owner;
- the same three values on each anchor axis;
- normal controls: in range, exactly at the radius, just beyond, and huge finite coordinates;
- order preservation;
- the radius policy.

## 2. Request-context consistency

**Defect.** R2 skipped a delivery whose context was missing, but accepted a present context whose embedded
`requestId` differed from the delivery's. With swapped contexts, ring A would be judged with ring B's owner.

**Correction** (contract section 7; SPI `PlanEmitter`, `RequestContext`, `SkipReason`; reference
`DefaultPlanEmitter`). Before any recipient of a delivery is considered, the emitter checks in order:
1. missing context: `MISSING_REQUEST_CONTEXT`;
2. `context.requestId() != delivery.requestId()`: **`MISMATCHED_REQUEST_CONTEXT`** (new);
3. a non-finite anchor, or a negative owner or world id: **`INVALID_REQUEST_CONTEXT`** (new).

A failure skips the whole delivery deterministically, counted once in `skippedDeliveries`. Nothing is sent, and
nothing is substituted, reconstructed or inferred. Other deliveries are unaffected.

**Tests:** `RequestContextConsistencyTest` (5 tests) covers:
- a valid control;
- swapped contexts for two requests with different owners (both rejected, zero sends);
- one mismatch (only that delivery is rejected);
- five invalid-context variants;
- a NaN viewer at the emitter.

## 3. Accounting (new contract section 7.2)

`EmissionReport` gains `placementsInSkippedDeliveries` and `placementsInSkippedRecipients`, so that every planned
placement is accounted for exactly once:
- `plannedPlacements = attemptedCalls + sum(skippedPoints) + placementsInSkippedRecipients + placementsInSkippedDeliveries`;
- `attemptedCalls = succeededCalls + failedCalls`.

The R3 tests assert both identities.

## 4. F1–F3 preserved

| R2 correction | Status in R3 |
|---|---|
| F1 owner-aware rule | unchanged logic for valid inputs. The 7 `OwnerAwareEmissionTest` tests pass, and mutant E2 (owner-blind rule) is still caught |
| F2 allocation order | unchanged. The 3 `AllocationOrderEmissionTest` tests pass; E1 is caught |
| F3 style health | unchanged. The 5 `StyleHealthTest` tests pass; E4 and E5 are caught |

The R2 mutant E3 needed a new pattern, because the context code changed; its intent is the same, a guessed owner for
a missing context.

## 5. Reproducible verification (new `docs/m7/verification/`)

- **Build:** a Gradle build compiles the SPI, the scratch reference and the tests against the real accepted engine
  (`includeBuild('../../..')`), with `-Xlint:all -Werror`.
- **Runners:** `run-tests.sh` and `summarize.py` give one line per test plus totals; `run_mutants.py` and `mutants.py`
  run the 13 mutants. There is a README with exact commands and dependency versions.
- **R2 reproduced first:** the R2 results were reproduced on the unchanged R2 code before any R3 edit: 15/15 tests and
  6 of 6 mutants (`logs/r2-state-*`).
- **Moved files:** the scratch reference moved from `docs/m7/validation/r2-reference/` to
  `docs/m7/reference/workshop/vfx/paper/core/`, and is now a real build input. `validation/r2-mutants.py` became
  `verification/mutants.py`.
- **Historical files:** the historical R2 logs in `validation/r2-tests.txt` keep their old paths.

## 6. Counts

| | R2 (reported) | R2 (reproduced in R3) | R3 |
|---|---|---|---|
| Proposed tests | 15 | 15/15 | **25/25** (`RecipientRuleRobustnessTest` 5, `RequestContextConsistencyTest` 5, plus the 15) |
| Targeted mutants | 6 caught of 6 | 6 caught of 6 | **13 caught of 13** (E1-E8, E10-E14; E9 omitted as equivalent by construction) |
| `SkipReason` constants | 9 | — | 12 |
| SPI files | 15 | — | 15 |
| M1-M6 regression on this branch | — | — | 415/415; the seven datasets PASS (newly executed, `logs/r3-m1-m6-regression.log`) |

## 7. Not done (separate production gates)

- **No Paper compile:** nothing was compiled against `paper-api` 26.3. `repo.papermc.io` is blocked in this
  environment, and no exact build is pinned.
- **No server:** there was no Paper server run, no in-game smoke test and no client observation.
- **No GitHub:** nothing was pushed to GitHub (HTTP 403 from this environment), so there is no CI run for R3.
