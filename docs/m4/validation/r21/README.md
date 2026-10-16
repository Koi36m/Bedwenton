# M4 R2.1 validation evidence

These files are review scratch, kept as evidence. All runs used Java 25.0.4.1 against the **throwaway** R1, R2 and R2.1
reference implementations of the contract. Those implementations are kept outside the repository and are not
delivered.

| File | Content |
|---|---|
| `findings-r2.txt` | `F45.java` against R2, which reproduces the findings. F4: M3 lines on `[0, k MIN_VALUE]` have length 0 at every speed, up to 1e300. F5: `ArithmeticException` ("length is not finite") for lines with speeds 1e308 to `sqrt(2) MAX_VALUE` on short ranges, whose lengths are finite |
| `findings-r21.txt` | the same probe against R2.1: all of these lengths are finite and accurate, and `lengthAt` one ulp above t0 is correct |
| `r2-vs-r21-reference-values.txt` | `LenDump.java`: lengths, estimates, panel counts, `lengthAt` and `parameterAt` over the 1,344 reference tables, compared bit for bit between R2 and R2.1. Differences are rounding-level only: at most 7.9e-16 relative in a length, and no panel-count change |
| `before-after-tests.txt` | the 59 R2.1 M4 tests against R1 (48 pass), R2 (54 pass; the 5 failures are the R2.1 regression tests) and R2.1 (59 pass) |

Running the probes: compile them against the main classes of a build that contains a reference implementation, for
example `javac -d out -cp build/classes/java/main docs/m4/validation/r21/F45.java`.
