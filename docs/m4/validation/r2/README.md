# M4 R2 validation evidence

These files are review scratch, kept as evidence. All runs used Java 25.0.4.1 against the **throwaway** R1 and R2
reference implementations of the contract. Those implementations are kept outside the repository and are not
delivered.

| File | Content |
|---|---|
| `findings-r1.txt` | `Findings.java` and `Exhaust3.java` against R1. It reproduces findings 1 to 3: `tau = 0` for subnormal lengths; NaN from `lengthAt` on a pathological panel; Newton creep through all 64 iterations (384 evaluations), with a residual of 2.7e-8 |
| `findings-r2.txt` | the same probes against R2: `tau = 1024 MIN_VALUE` with 16 panels; finite linear-mode values. Creep, counting only the runs where the spike is invisible to the table (same length): for eps of at most 3e-4, all 21 runs end by tolerance within 321 evaluations, with `abs(t - t*) <= 8.4e-16` (R1: all 21 reach 384 evaluations, with errors up to 2.4e-4). For eps = 1e-3, the spike also reaches the nodes of `F_k`, which then jumps. 1 of 4 runs ends at the cap (336), with `abs(t - t*) = 1.1e-4`, at the better end of the bracket (R1: all 4 runs at 384, error up to 4.2e-4). Runs where the table sees the spike measure a different curve, so `t*` does not apply |
| `inversion-stats.txt` | `InvStats.java` against R2 and against the rejected R2 draft (Newton only after a halving step): exit kinds, iterations and evaluations for smooth curves, creep and a noisy speed |
| `linear-mode.txt` | `LinCount.java`: no reference panel is in linear mode (0 of 21,876 panels in 1,344 tables) |
| `r1-vs-r2-tests.txt` | the 54 R2 M4 tests against R1 (48 pass; the 6 failures are the F1 to F3 regression tests and the tie rule) and against R2 (54 pass) |

Running the probes:
- Compile them against the main classes of a build that contains a reference implementation, for example
  `javac -d out -cp build/classes/java/main docs/m4/validation/r2/Findings.java`.
- `InvStats.java` also needs a copy of the reference implementation instrumented with exit counters, as described
  in its header.
