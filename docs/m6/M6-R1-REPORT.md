# M6 R1 proposal report (for ChatGPT's independent review)

Status: **design proposal only (R1; superseded in part by R2, see `M6-R2-CHANGES.md`).** There is no M6 production code, nothing merged into `main` and no particle emission.
The owner's authorization is required before Codex implements anything.

## Identities

| Item | Value |
|---|---|
| Accepted M5 GitHub snapshot (baseline) | `d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb`, tree `43d1300ab42105c010dd43bfc89ee476491fcdaa` |
| Unpublished local M5 implementation commit (same production sources, not the accepted identity) | `d5a8868...` |
| Kickoff | `0587309877c1e3534e2209e4bd361bd6bca5fa8f` (`docs/m6/M6-DESIGN-KICKOFF.md`) |
| Proposal branch | `m6-spec`, descending from the kickoff commit |

## Contents

- **Seven documents:** `M6-ARCHITECTURE.md`, `M6-DESIGN-DECISIONS.md` (DM6-1 to DM6-13), `M6-CONVENTIONS.md`,
  `M6-IMPLEMENTATION-CONTRACT.md` (normative API, validation, ordering, limits and guarantees G1 to G10),
  `M6-TEST-SPECIFICATION.md`, `M6-RISKS.md` and `README-FOR-CODEX-M6.md`.
- **Proposed API:** ten types in `workshop.vfx.delivery`, around one pure function
  `DeliveryPlanner.plan(tickOrdinal, requests, supportedStyles, policy)` (contract section 2).
- **Independent oracle:** `tools/generate_m6_reference_plans.py`, using exact integers. It produces 40 cases and 146
  per-request expectations in the six required families, under `src/test/resources/m6/reference/` with
  `MANIFEST.sha256`.
- **Java 25 tests:** 49 tests in 9 classes, plus the helper `M6TestSupport`, an independent invariant checker. They do
  not compile until an implementation exists, as for M1 to M5.
- **Evidence:** `docs/m6/validation/`: the run summary, regeneration, mutation results, performance and the baseline
  check.

## Results

| Check | Result |
|---|---|
| Full suite against the throwaway reference | **412/412** (313 M1-M4 + 50 M5 + 49 M6); `check` succeeds |
| Accepted 363 tests | unchanged, all pass |
| Reference generators M1-M6 | all byte-identical to the committed fixtures; M6 deterministic across two runs |
| Baseline diff against `d4970cc` | additions only, all within M6 paths |
| Mutation analysis | 58 mutants: 56 killed; 2 survivors (M34, M46), both proven equivalent |
| Denied 2^20-point preflight | under 64 KiB allocated per plan (asserted) |
| Performance | indicative only; see `validation/performance.txt` |

## Findings during R1 (self-review)

- **F1:** in the first draft of the cap on k, a request whose viewers had all exhausted their budgets was reported
  `DELIVERED` with zero placements. Fix: k is also capped by the largest remaining viewer budget, and `DELIVERED` means
  every viewer received every point (DM6-4).
- **F2:** mutant M46 showed that the `% n` in the selection rule could never take effect. The contract now states the
  no-wrap lemma, and indices strictly increase.
- **F3:** the first fixture policies violated the contract's kept-cap ordering. The generator now asserts every
  section 4.3 rule for its own policies.

## What ran and what was assumed

**Ran:**
- every test, generator, mutant and probe listed above, on Java 25.0.4.1, Gradle 9.1.0 and Python 3.13 in one cloud
  container;
- all of it against the throwaway reference, which is kept outside the repository and not delivered.

**Assumed, not run:**
- any Paper, Bukkit or Folia behaviour, and particle emission;
- packet, network, client or MSPT effects;
- GitHub Actions: no workflow was added for R1, and GitHub pushes from this environment have been refused (HTTP 403);
- that the reference's performance resembles a production implementation's.

## Open questions

Q1 to Q5 are in `M6-RISKS.md`:
- a NORMAL reserve;
- per-recipe kept caps;
- the conservative generation cost;
- strict `DELIVERED`;
- whether unsupported batches are charged.
