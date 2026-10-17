# M7 R3 verification record

Status: **evidence for ChatGPT's independent review.** This is not an acceptance. Every result below was executed in
this R3 session unless it is explicitly marked *historical*.

## 1. Identities

| Item | Value |
|---|---|
| R3 content commit (SPI, reference, tests, contract, runners) | `c57c1b0612663e2296aa5cf5828708f64467f279` |
| R3 tip on `m7-spec-r3` | the evidence commit on top of `c57c1b0`. It adds only this file, `verification/evidence/` and the clean-checkout logs, all under `docs/m7/`. Its full SHA is in the package `README.md` and `PROVENANCE.txt`, because a commit cannot contain its own hash. |
| Parent of `c57c1b0` | R2 `15aa210ddcab79de6180df9bfa45a3ba17e1bcf7` |
| Ancestry | `c57c1b0` → `15aa210` (R2) → `f72179d` (R1) → `74fefd3` (kickoff) → `c43a66e` (accepted M6); see `verification/evidence/ancestry.txt` |
| Accepted M6 | `c43a66e656a77aa82bd72d8ffe27f1a037240e99`, tree `ef496f7b0c34796cdd30fe35d79cfd4e0c071ece` |
| Toolchain | Java 25.0.4.1, Gradle 9.1.0 (root wrapper), JUnit 5.12.2, Python 3.13.16; one Linux cloud container |

## 2. Preservation

- **M6 to `c57c1b0`:** 50 changed paths, all additions, all under `docs/m7/`; none outside it
  (`verification/evidence/m6-to-r3.txt`).
- **Tip:** the package's `PROVENANCE.txt` repeats the check for the tip.
- **R2 to R3:** the diff statistics, the name-status listing and the full diff are in
  `verification/evidence/r2-to-r3.txt` and `verification/evidence/r2-to-r3.diff`.
- **No accepted M1-M6 file changed:** this covers source, tests, fixtures, generators, build configuration, workflows
  and datasets. History was not rewritten, and nothing was merged into `main`.

## 3. Results

| # | Check | Command | Where it ran | Result | Log |
|---|---|---|---|---|---|
| V1 | R2 tests reproduced, unchanged R2 code | `docs/m7/verification/run-tests.sh` | working tree at `15aa210` plus the new runner only | **15/15** | `verification/logs/r2-state-tests.log` |
| V2 | R2 mutants reproduced | `python3 docs/m7/verification/run_mutants.py` | same | **6 caught of 6** | `verification/logs/r2-state-mutants.txt` |
| V3 | New R3 tests against the R2 logic (defect reproduction) | `run-tests.sh` | R2 rule and emitter, with only the new report fields added | 25 tests, **7 fail** (for example, a NaN viewer received 96 particles; swapped contexts authorised the wrong owner) | `verification/logs/r3-tests-before-fix.log` |
| V4 | R3 tests | `run-tests.sh` | **clean clone at `c57c1b0`** (0 uncommitted files) | **25/25** | `verification/logs/r3-clean-checkout-tests.log` (also `r3-tests.log`, from the working tree) |
| V5 | R3 mutants | `run_mutants.py` | clean clone at `c57c1b0` | **13 caught of 13** | `verification/logs/r3-clean-checkout-mutants.txt` (also `r3-mutants.txt`) |
| V6 | M1-M6 regression | `./gradlew --no-daemon clean test check`; `python3 verification/m6/regenerate_all.py <dir>` | clean clone at `c57c1b0` | **415/415**; **PASS**, seven datasets byte-identical | `verification/logs/r3-clean-checkout-m1-m6-regression.log` (also `r3-m1-m6-regression.log`) |
| V7 | SPI compile | part of V4: `-Xlint:all -Werror` | clean clone | 0 warnings | V4 log (gradle exit 0) |

**Tests per class (V4):**

| Class | Tests | Scope |
|---|---|---|
| `OwnerAwareEmissionTest` | 7 | R2 F1 and owner-first selection |
| `AllocationOrderEmissionTest` | 3 | R2 F2 |
| `StyleHealthTest` | 5 | R2 F3 |
| `RecipientRuleRobustnessTest` | 5 | R3: NaN, ±infinity, controls, order, radius |
| `RequestContextConsistencyTest` | 5 | R3: valid control, swapped, one mismatch, invalid contexts, NaN viewer at the emitter |

**Mutants (V5):**

| Mutant | Breaks | Tests that catch it |
|---|---|---|
| E1 | emission in input order (R2 F2) | 1 |
| E2 | owner-blind rule (R2 F1) | 7 |
| E3 | guessed owner for a missing context | 1 |
| E4 | any style's success resets every streak (F3) | 1 |
| E5 | streak reset every tick (F3) | 1 |
| E6 | owner not admitted before capping | 1 |
| E7 | **viewer position not validated (the reviewed NaN defect)** | 2 |
| E8 | anchor not validated in the rule | 1 |
| E10 | radius not validated | 1 |
| E11 | mismatched context accepted | 2 |
| E12 | invalid context not rejected at delivery level | 1 |
| E13 | skipped-delivery placements not counted | 3 |
| E14 | skipped-recipient placements not counted | 4 |

E9, the range test written as `d2 > r2`, is omitted on purpose. Once the finiteness checks pass, `d2` cannot be NaN:
finite inputs overflow only to +infinity. The mutant is therefore equivalent by construction, and the negated form
stays as defence in depth.

## 4. Real engine versus scratch reference

- **Real, accepted and unchanged:**
  - `DeliveryPlanner`, `EffectRecipe`, the M5 scenes, samplers and curves;
  - every plan in the emitter tests comes from the real planner, with the contract's demo recipe built through the
    accepted API.
- **Scratch reference** (`docs/m7/reference/`, 4 classes): emitter, rule, viewer selection and style health. They
  exist so that the contract can be executed and the tests can be mutation-checked. **They are not production code**,
  and passing them says nothing about a future Paper implementation beyond showing that the contract is consistent.
- **No Paper or Bukkit type** is compiled or loaded anywhere in this verification.

## 5. Not verified or blocked

| Item | Status |
|---|---|
| Compilation against `io.papermc.paper:paper-api` 26.3 | **Blocked.** `repo.papermc.io` is refused by this environment's network policy. No exact build is pinned. Signatures were read from PaperMC source `09ee222` (R1 evidence, *historical*). |
| Disposable Paper server smoke S1-S17 | **Not run.** This is a separate production gate. |
| GitHub push and CI for R3 | **Blocked** (HTTP 403 from this environment). Delivery is by Git bundle. |
| Visual behaviour (count-0 placement, dust colour), client performance, MSPT | Not run; no claim is made. |
