# M4 test specification and acceptance criteria

Status: PROPOSED, with the contract (revision 2.1).
- **Tests:** `src/test/java/workshop/vfx/math/sample/`, 59 test methods in 10 classes plus the helper
  `SampleTestSupport`. R2.1 added `M4R21RegressionTest` (5 tests) and changed no existing test. R2 added `M4R2RegressionTest` (10 tests), removed no test, and tightened one bound
  (`samplingWork`: from 400 to 336 evaluations).
- **Reference data:** unchanged in R2.
- **Reference data:** `src/test/resources/m4/reference/`: 4 CSV files with 3,064 rows, and `MANIFEST.sha256`.
- **Generator:** `tools/generate_m4_reference_vectors.py`.

## 1. Methodology: oracles independent of the recommended algorithms

| Oracle | Used for | Why it is independent |
|---|---|---|
| Exact closed forms (`BigDecimal`) | line lengths; circle and helix lengths (50-digit pi); uniform parameters on [0, 1] as correctly rounded `k/den` | no quadrature |
| Closed-form antiderivative of `sqrt(a t^2 + b t + c)` (asinh form) | quadratic Beziers, planar and conical spirals: lengths (60-digit Decimal in the generator; double in the tests), distances of equal-distance samples | analytic, not numerical integration |
| Composite 20-point Gauss-Legendre at 40 digits, nodes derived in the generator by Newton iteration, refined to 1e-28, split at known cusps | cubic Bezier lengths and inversion targets | a different rule, order, precision and refinement; validated against the closed forms (self-check to 1e-25) |
| Exact bisection on the oracle antiderivative | inversion targets t* | no Newton, no panel structure |
| Independent implementation of the normative definitions (Python floats, `BigDecimal`) | uniform parameters, spacing counts | a separate implementation of the written formula |
| Metamorphic relations | pose invariance (`length(posed) = scale * length`), additivity over subranges, `transformed` = posed curve, density under scaling | relations, no expected values |
| Instrumented curves | work bounds (exact call counts), failure propagation, rejection before work | observation, not computation |
| Bitwise repetition | determinism across repetition, JIT and threads | properties |

No expected value is produced by the recommended algorithm. Tolerances are the contract's:
- tau, reported by the table and checked against its definition;
- plus the rounding allowance of contract 10.2, computed from the inputs with the M3 derivative bounds
  (`SampleTestSupport.speedErrorBound`).

Provenance: written for this project; Python standard library only. No third-party or tutorial code.

## 2. Inventory

| Class | Tests | Contract | Covers |
|---|---|---|---|
| `UniformParameterTest` | 6 | 3, 4 | correctly rounded `k/den` on [0, 1]; counts 0, 1, 2; exact endpoints and points; signed zero; extrapolated, tiny and huge ranges; constant-speed chords; validation before evaluation; point once per sample |
| `ArcLengthTableTest` | 9 | 5 | exact lines (1e-300 to 1e100, extrapolated); circles and helices (BigDecimal pi, 16 panels); quadratic Beziers and conical spirals (closed form) at four tolerances; tau definition; convergence in the tolerance; exact ends and grid monotonicity; pose invariance and additivity; collapsed and stationary geometry; validation |
| `EqualDistanceTest` | 8 | 6 | spirals with changing radius and quadratics equally spaced within 2 tau (closed-form distances); constant-speed curves and seams (no duplicate seam with EXCLUDE_END); monotone parameters for cusps, stationary points and an oscillating curve, and under a loose tolerance with 60,000 samples; zero-length fallback; spacing rule and density; spacing and explosion guards |
| `SampleSetTest` | 3 | 7, 8 | immutability; `transformed` bit for bit equal to `PosedCurve`; limits and tolerance validation |
| `M4AdversarialTest` | 8 | 6.3, 8, 9 | budget never exceeded and `evaluations()` exact for six budgets and four curve kinds; oscillating curve (unconverged under a small budget, converged with a large one); per-call work bounds (336 per sample); Newton efficiency (mean below 40 evaluations per sample); failures (NaN, infinity, user exceptions, overflowing length); magnitudes from subnormal to 1e200; signed zero and one-ulp ranges; explosion guard with server-style limits |
| `M4R2RegressionTest` | 10 | 5.3 to 5.6, 9, 10.2 | R1 review findings and R2 rules. F1: nonzero subnormal-speed lines and subnormal circles and helices: converged, 16 panels, `tau = 1024 MIN_VALUE`, within the 10.2 allowance (BigDecimal). F2: pathological panels (`I2/I1` overflowing or underflowing, `I1 = 0`, `I2 = 1000 I1`, `I1 = 1000 I2`): finite linear-mode `lengthAt` and `parameterAt`; ratio mode clamped to `I2`. F3: Newton creep (a speed spike at the root), non-converged exits (the better end of the bracket), an exit at the iteration cap (332 to 336 evaluations, the better end within the P3 width), panel-end targets (exact, no evaluation), ties to the upper end (early and after a collapse). Stationary intervals: `parameterAt(length()) == t1` with a stationary tail, and the smallest panel at the start of a stationary interval. A non-finite derivative after build gives `ArithmeticException` from `lengthAt` and `parameterAt` |
| `M4R21RegressionTest` | 5 | 5.1, 5.2, 5.5, 10.2 | R2 review findings. F4: M3 lines (speeds 1 to 1e300, 3D directions) on ranges of 1 to 7 `MIN_VALUE`, and down to 1e-320, `2^-1060`, 1e-310, against BigDecimal lengths with the R2.1 allowance; unit speed on `[0, k MIN_VALUE]` exact. F5: lines with speeds 1e308 to `sqrt(3) MAX_VALUE` (components up to `MAX_VALUE`), a circle of radius 1e306: finite, converged, within the allowance; a finite table whose partial integral overflowed in R2: `lengthAt` and `parameterAt` finite, never NaN. 3,000 random lines over the whole exponent range: no spurious overflow or underflow |
| `M4ReferenceVectorTest` | 5 | 4 to 6 | manifest; 336 lengths; 216 inversions; 2,310 uniform parameters bit for bit; 202 spacing counts |
| `M4DeterminismTest` | 2 | 10.1 | bit-identical repetition (20 rounds, JIT); 8 threads, including a shared table |
| `M4BoundaryTest` | 3 | 1, 2, 10 | imports (core and curve only; never time); no trigonometry, pow, exp, log, fma, random, clocks, threads, synchronization or I/O; no mutable statics; exact API shape |

Reference data:
- `arclength.csv`: 336 rows. Lines 63, spirals 105, quadratic 103, cubic 65 (including 4 cusps and a stationary
  point).
- `inverse.csv`: 216 rows (30 spirals, 30 quadratics and 12 cubics, at distances 1/4, 1/2 and 3/4 of L).
- `uniform.csv`: 2,310 rows (25 ranges, 2 policies, 8 counts up to 2^20, selected indices).
- `counts.csv`: 202 rows (halves, `nextDown(0.5)`, saturation, random).

## 3. Validation of the tests (before handoff)

- **Throwaway reference implementation:** the tests were run against an implementation of contract revision 2.1,
  kept outside the repository and not delivered. Result: **59/59 passed**. With the 254 baseline tests: **313/313**
  on Java 25.0.4.1 (`validation/reference-run-summary.txt`).
- **Before and after** (`validation/r21/before-after-tests.txt`):
  - against the R2 reference implementation: 54/59. The 5 failures are exactly the R2.1 regression tests (F4, F5).
  - against R1: 48/59. The failures are those 5 plus the 6 R2 regression failures (`validation/r2/r1-vs-r2-tests.txt`).
- **Reference values:** unchanged files. Over the 1,344 reference tables, R2.1 and R2 differ only at rounding level:
  at most 7.9e-16 relative in a length, with no change of panel count (`validation/r21/r2-vs-r21-reference-values.txt`).
- **Measured accuracy and work** (`validation/evidence.txt`): all reference lengths converge with achieved error at
  most 0.31 tau; inversion costs 13.8 derivative evaluations on average and 29 at most (R1: 14.0 and 29).
- **Mutation analysis** (`validation/mutation-results.txt`): 55 plausible defects (revision 2.1) were injected one
  at a time. Results are in that file and summarised in section 5.
- The generator is deterministic, and regenerating gives byte-identical files.
- **Note:** the reference implementation was used to validate the tests and to measure evidence. It was **not** used
  to produce any expected value.

## 4. Acceptance criteria for the M4 implementation

1. **Approval first.** The owner approves the contract and DM4-1 to DM4-17, possibly after revision, with the tests
   revised to match before implementation.
2. **M1, M2 and M3 untouched.** Compared with `a6726c42a20940a6a6d2dd24477e6a642f6b8cbc`, nothing changes outside
   `src/main/java/workshop/vfx/math/sample`, except this proposal's own files.
3. **Tests untouched.** The M4 tests, reference data and generator are byte-identical to the handoff
   (`SHA256SUMS.txt`).
4. **Production scope.** Only the six public types of contract section 2, plus optional package-private helpers. No
   dependencies.
5. **Java 25 run.**
   `./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test` reports **313 tests,
   313 passed** (M1 125, M2 66, M3 51, M3 review 12, M4 59). `check` succeeds.
6. **Evidence** as for M1 to M3, then Claude Code's independent review.

Before the implementation exists, the M4 tests do not compile; this is expected (as for M1 to M3). The M3 GitHub
Actions workflow runs only on `m3-actions-verification` and is not affected by this branch.

## 5. Mutation results

**Revision 2.1.** 55 plausible defects were injected one at a time into the throwaway R2.1 reference implementation:
- the 47 R2 mutants, with the panel-rule mutants M01, M02 and M11 adapted to the new order;
- 8 R2.1 mutants:
  - M48: the R2 arithmetic `h * sum`;
  - M49 to M52 and M55: each product order forced or swapped;
  - M53: `nu` dropped from the ratios;
  - M54: no zero-speed shortcut.

**Result:** 50 killed, 4 equivalent (M20, M33, M38, M45), 1 tolerance-equivalent (M13). Per-mutant failing tests and
the reasons are in `validation/mutation-results.txt`.
- Every R2.1 mutant is killed by `M4R21RegressionTest`.
- M50 (always the fallback product order) survived the first run. Its error is rounding of `M * S` while subnormal,
  times the width. It was killed after `dv` lost its `MIN_VALUE` term (contract 10.2) and
  `subnormalSpeedLongRange` was added.
- M12 and M15, which needed new tests in R2, stay killed.

**Revision 2** (for reference). 47 plausible defects were injected one at a time into the throwaway R2 reference implementation:
- the 33 R1 mutants, adapted to the R2 code;
- 14 new R2 mutants: no tolerance floor; ratio mode as in R1, or one-sided; no clamp to `I2`; `r` not clamped; the
  last iterate returned; ties to `lo`; Newton in every iteration; pure bisection; no tolerance exit; `lo` always
  returned; no break at the cap; tolerance `tau` instead of `tau 2^-10`; no end acceptance.

Per-mutant failing tests are in `validation/mutation-results.txt`.

**42 killed.** Among them:
- every R1 kill;
- every R2 rule: M34 floor, M35 and M36 ratio mode, M37 clamp, M39 and M44 return rule, M40 ties, M41 and M42
  phases, M43 and M46 tolerance, M47 end acceptance.

**Survivors that turned out to change behaviour got tests** (all in `M4R2RegressionTest`):

| Mutant | Test |
|---|---|
| M26 | `stationaryTail` |
| M36 | new cases in `pathologicalPanels` |
| M37 | `partialClampedToPanel` |
| M40 | `tieGoesToUpperEnd` |
| M12 | `nonFiniteAfterBuild` |
| M15 | the middle stationary interval in `stationaryTail` |

**Correction of R1.** R1 had classified M12 (the non-finite check) and M15 (the strict panel search) as equivalent.
That was wrong:
- M12 matters for `lengthAt` and `parameterAt` after a successful build;
- M15 matters for a distance at the start of a stationary interval.

**4 equivalent mutants**, which cannot change any result (reasons in `mutation-results.txt`):
- M20, `Math.round`;
- M33, the `lengthAt(t0)` special case;
- M38, the clamp of `r`, which the end acceptance makes redundant;
- M45, the break at the cap.

**1 tolerance-equivalent mutant (M13):** the partial integral is not scaled by `rho`. It violates the normative order
of contract 5.5, but changes results only inside the panel error estimate. No independent test can see it without
copying the algorithm, so it is an item for the implementation review.

**Revision 1** (for reference): 33 mutants, with 28 killed, 4 classified as equivalent and 1 tolerance-equivalent.
