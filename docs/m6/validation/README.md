# M6 validation evidence (R1, refreshed for R2)

These files are review scratch, kept as evidence. All runs used Java 25.0.4.1, Gradle 9.1.0 and Python 3.13 in one
cloud container, against the **throwaway** M6 reference implementation of the contract. That implementation is kept
outside the repository and is **not** delivered or approved (as for M5 R1).

| File | Content |
|---|---|
| `reference-run-summary.txt` | R2: the full suite: 313 M1-M4 + 50 M5 + 52 M6 = **415 tests, 415 passed**; `check` succeeds (R1: 412/412) |
| `reference-regeneration.txt` | R2: all six generators (M1, M2, M3, M4, M5, M6) rerun into scratch: every output byte-identical to the committed fixtures; M6 run twice, byte-identical |
| `mutation-results-r2.txt`, `mutants-r2.py` | R2: 8 targeted mutants of the changed paths, all killed |
| `mutation-results.txt`, `mutants.py` | R1 (historical): 58 mutants: 56 killed, 2 survived and proven equivalent (M34, M46); M46 simplified the selection rule |
| `PerformanceProbe.java`, `performance.txt` | R1 (planning cost unchanged by R2):  indicative plan cost, M5 share and bytes per call, from 16 effects up to the request and viewer-entry ceilings and the full 2^20 frame |
| `baseline-check.txt` | R2: M1-M5 unchanged against `d4970cc` (only additions under `docs/m6`, `src/test/java/workshop/vfx/delivery`, `src/test/resources/m6`, `tools/generate_m6_reference_plans.py`) |

## Reading the performance numbers

- **Times are for the throwaway reference.** It boxes viewer budgets in `HashMap<Long, Long>`, regroups requests per
  priority with lists, and picks remainders quadratically. The worst cases:
  - 65,536 effects x 1 viewer: about 54 ms and 28 MB per plan;
  - 2^20 viewer entries: about 21 ms and 60 MB per plan.

  These figures show the cost of that code, not a property of the contract. A production implementation can use
  sorted primitive arrays. **No claim is made about server MSPT, network or clients.**
- A denied 2^20-point preflight costs under 64 KiB per plan; this is asserted by
  `M6StressTest.deniedPreflightAllocation`. An admitted full frame costs what M5 costs, about 18 ms and 24 MiB here;
  the planner adds about 0.4 %.

## Running the probe

`PerformanceProbe` is in package `workshop.vfx.delivery`. Compile it with `src/test/java/workshop/vfx/delivery/M6TestSupport.java`
(and the JUnit API jar for that helper's imports) against a build that contains an implementation, then run it with
`-Xmx2g`.
