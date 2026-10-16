# M4 revision 2: corrections after the R1 independent review

Status: PROPOSED, revision 2. **Not authorized for implementation.** This revision answers ChatGPT's review of M4 R1
(decision: REVISION REQUIRED). It stops for ChatGPT's independent R2 acceptance review.

**What stays the same:**
- the architecture, scope, and the six public types and their signatures;
- the baseline `a6726c42a20940a6a6d2dd24477e6a642f6b8cbc`, and M1, M2 and M3, which are untouched;
- the reference data and generator, which are byte-identical to R1;
- every R1 test, none removed.

**Review document.** `M4-R1-Independent-Review.md` was not available in this session: it was not attached, and it is
not on any branch of the repository. The three blocking findings are addressed as the revision request states them.
The review's minor clarifications could not be read. Section 4 lists the clarifications I found myself. **Any minor
item in the review that is not covered there remains open.**

## 1. Finding 1: subnormal constant-speed arc lengths

**Problem (reproduced on R1, `validation/r2/findings-r1.txt`):**
- `tau = max(absolute, relative * L0)` underflows to 0 for subnormal lengths, so convergence needs `sum e == 0`
  exactly, which the last bits of the rounding decide. Some subnormal circles needed 32 panels; B5 claimed 16.
- A `7 MIN_VALUE` line has computed length 0, because `h * sum` underflows. That is outside the R1 allowance
  `(t1 - t0) dv + 80 eps L`, which had no absolute underflow term.
- R1's B5 proof ("`tau >= 2^-40 L` exceeds the panel rounding") ignored both effects.

**Correction:**
- **Contract 5.3:** `tau = max(max(absolute, relative * L0), TAU_FLOOR)`, with `TAU_FLOOR = 1024 MIN_VALUE`. The
  floor changes nothing for lengths of at least `2^-1022`, since `2^-40 * 2^-1022 > 2^-1064`.
- **Contract 10.2:** the allowance gains `8 K MIN_VALUE` (underflow of the rule's products, at most `2 MIN_VALUE`
  per rule), and `dv` gains `MIN_VALUE` (a subnormal speed).
- **Contract B5:** split into two parts.
  - **Unconditional** for a computed speed that is the same at every node (lines), at every magnitude, with a proof
    in `M4-ERROR-ANALYSIS.md` 4: `sum e <= 24 eps L + 144 MIN_VALUE <= tau`.
  - **Conditional** for curves whose computed speed varies by rounding (circles, helices): it holds when
    `2 (t1 - t0) dsv + 24 eps L + 144 MIN_VALUE <= tau`.

  R1 asserted B5 for circles without that condition. The condition is now explicit, and every tested circle and
  helix satisfies it.
- **DM4-5, DM4-17, conventions:** updated to match.

**Regression test:** `M4R2RegressionTest.subnormalConstantSpeed`.
- Inputs:
  - lines with nonzero subnormal speeds `7q`, `(3q, 5q, 11q)`, `q`, `123456q`, `(2^40 q, 3q)`, `(1e-320, 2e-320)`, on
    [0, 1] and on [-0.5, 1.5] (extrapolated);
  - circles and helices of radius `3q`, 1e-320, 1e-315 and 1e-310.
- Assertions:
  - `converged()`, 16 panels and 240 evaluations;
  - `tau == 1024 MIN_VALUE` exactly;
  - `abs(L^ - L) <=` the contract 10.2 allowance against an exact `BigDecimal` length;
  - the absolute-tolerance floor;
  - sampling a subnormal table.

R1 fails it: `tau` is 0 for the `7q` line.

## 2. Finding 2: overflow and NaN in the normalised partial integral

**Problem (R1):**
- `F_k(t) = G(a_k, t) * (I2_k / I1_k)` overflows when `I1_k` is tiny and `I2_k` huge (`I2 / I1 = infinity`).
- With `G = 0` at the sampled nodes it gives `0 * infinity = NaN`.
- So `lengthAt` returned NaN, and `parameterAt` misbehaved, on a finite-length table.

**Correction (contract 5.5):**
- **Ratio mode** only when `I1 > 0 && I2 <= 2 I1 && I1 <= 2 I2` (that is, `1/2 <= I2/I1 <= 2`, tested without
  dividing), with `F = min(I2, G * rho)`.
- Otherwise **linear mode**, `F = I2 (t - a)/(b - a)`.
- **Proven:** F is never NaN and lies in `[0, I2]`.
- Linear mode occurs only in panels with `e_k >= I2_k / 2`, so in a converged table only where `I2_k <= 2 tau`.
- No reference panel is in linear mode (0 of 21,876 panels in 1,344 tables, `validation/r2/linear-mode.txt`), so
  the reference results are unchanged.

**Regression test:** `M4R2RegressionTest.pathologicalPanels`.
- Setup: a user curve whose speed is `half` at the half-panel Gauss nodes of [0, 1/16], `whole` at the whole-panel
  nodes, and 0 elsewhere. The cases for (whole, half) are (1e-300, 1e300), (0, 1e300), (1, 1000), (1e-300, 1e-200),
  (1000, 1) and (1e300, 1e-300).
- Assertions:
  - `lengthAt` is finite and monotone, and equals the linear fallback within 4 ulp;
  - `parameterAt(k L/16)` equals `k/256` within 4 ulp;
  - equal-distance samples are finite and monotone.

R1 fails it: `lengthAt` returns NaN.

**Second test:** `M4R2RegressionTest.partialClampedToPanel`, added for mutation survivor M37. A speed seen only by
the nodes of `G(0, 3/64)` makes `G * rho` exceed `I2_0`. `lengthAt` must stay within `[0, S_1]`, and must equal `S_1`
at `t = 3/64`.

## 3. Finding 3: Newton-bisection exhaustion

**Problem (R1):**
- R1 claimed that 64 iterations shrink any bracket by `2^-64`, to ulp accuracy. A Newton step only has to land
  strictly inside the bracket, so a large speed near the root (a spike invisible to the quadrature) makes every step
  tiny: Newton creep.
- R1 used all 64 iterations (384 evaluations) and stopped with a residual of 2.7e-8 and a parameter error of
  1.4e-8, against a tolerance of about 1e-15 (`validation/r2/findings-r1.txt`; 26 of 32 runs reach 384).
- The result after the 64th iteration was unspecified.

**Correction (contract 5.6, fully normative):**
- **Phases:** Newton candidates are formed only in iterations 0 to 15. From then on, every iterate is the midpoint.
- **Proven bracket progress (P3):** whenever the loop reaches the cap, the bracket is at most
  `2^-47 (b - a)(1 + 2^-46) + 2 ulp(M_k)`; at a collapse, at most `2 ulp(M_k)`. The proof, including midpoint
  rounding, is in `M4-ERROR-ANALYSIS.md` 7.
- **Exact exit behaviour (P4):**
  - tolerance or `f == 0` exits return the iterate;
  - cap and collapse exits return the end of the final bracket with the smaller residual (ties go to `hi`). The
    residuals `f_lo < 0 < f_hi` are tracked; the ends' residuals are known exactly, with no evaluation.
- **Defined edge cases:**
  - `r` is clamped to `I2_k`, because rounding of `S_(k+1)` could make `distance - S_k` exceed `I2_k`;
  - an end of the bracket already within the tolerance is returned at once;
  - if no double lies strictly inside the panel, the better end is returned.
- **Work bound (P5):** `64 * 5 + 16 = 336` derivative evaluations (R1: 400). The bound is tightened, never
  loosened. The tests that used 400 now use 336.

**Why not the first R2 draft:**
- The draft allowed a Newton step only after a step that at least halved the bracket. This is also provable
  (`2^-32`).
- But it disables Newton after every one-sided Newton step, which is the normal case. On the conical spiral of
  `M4AdversarialTest.newtonEfficiency` the mean cost rose to about 57 evaluations per sample, and the validated
  limit of 40 failed.
- The limit was **not** relaxed. The algorithm was changed instead: the measured mean is 17.7 there, and 13.8 over
  the reference rows (R1: 14.0) (`validation/r2/inversion-stats.txt`).

**Why the end acceptance (step 4):**
- The R1 algorithm evaluated its initial guess even when it sat on a bracket end. There, `F_k(b_k) = I2_k` costs
  nothing.
- R2 requires every evaluated iterate to lie strictly inside the bracket (needed for P2 and P3). Without step 4, a
  target at a panel's end could only be approached by bisection from one side (196 to 201 evaluations, measured).
- Step 4 restores the R1 cost: mean 13.8, max 29.

**Regression tests (`M4R2RegressionTest`):**

| Test | What it checks |
|---|---|
| `newtonCreep` | A 1e6 speed spike within 1e-7 of the exact root `-1 + sqrt(1 + 2s)`, at seven targets. The spike is invisible to the table, whose length equals the unspiked one. At most 336 evaluations; `abs(lengthAt(t) - s)` and `abs(t - t*)` within the bound |
| `nonConvergedExit` | A speed taken from bit 11 of t, so `F` jumps and the tolerance is unreachable. Every result is within the tolerance, or is the better end of an adjacent-double bracket. At most 336 evaluations; deterministic; more than 100 non-converged exits |
| `iterationCapExit` | A jump of F near `t = 2^-40`, where the bracket cannot collapse within 64 iterations, with zero speed at the first iterate. All 64 iterations run (332 to 336 evaluations). The result is the better end, on the correct side of the jump, within the P3 width of it |
| `panelEndTargets` | A target equal to a panel end returns that end exactly, with no evaluation |
| `tieGoesToUpperEnd` | Equal residuals return `hi`: (a) in the end acceptance, with a target halfway across a panel shorter than `2 tolInv`; (b) after a collapse between adjacent doubles, with five exact ties on a line over [1e6, 1e6 + 1]. Added for mutation survivor M40 |
| `stationaryTail` | Stationary intervals. `parameterAt(length()) == t1` when the last panels have zero length (survivor M26: with the end acceptance, the special case of 5.6 is needed only there). A distance reached at the start of a stationary interval in the middle maps to its start, the smallest panel k (survivor M15, which R1 wrongly classified as equivalent) |
| `nonFiniteAfterBuild` | A curve finite at every table node but NaN elsewhere: `lengthAt` and `parameterAt` throw `ArithmeticException` (contract 9) and never return NaN (survivor M12, which R1 wrongly classified as equivalent: the length check covers only `build`) |

R1 fails `newtonCreep`, `nonConvergedExit`, `iterationCapExit` (384 evaluations, or not the better end) and
`tieGoesToUpperEnd`. R1 passes the tests that guard rules it also satisfies: `panelEndTargets`, `stationaryTail` and
`nonFiniteAfterBuild`.

## 4. Minor clarifications (self-identified; the review's own list was not available)

| # | Where | Clarification |
|---|---|---|
| C1 | contract 5.1 | Finite components above `MAX_VALUE / sqrt(3)` (outside the domain) can round the speed to infinity. The length is then not finite, and 5.5 and 5.6 still never produce NaN |
| C2 | contract 5.5 | States when linear mode can occur, and the bound on its error in converged tables |
| C3 | contract 5.6 | Every step is in a fixed order. `f_lo` and `f_hi` are defined at the panel ends without evaluation. "Strictly inside" includes NaN rejection. At a collapse, R1 returned the end where `F >= r`; R2 returns the better end, the same rule as for the cap |
| C4 | contract 6.3, DM4-6, risks P4, conventions F7 | Measured costs refreshed from the R2 run (mean 13.8, max 29). R1's false ulp-accuracy claim removed. Pure bisection's cost corrected |
| C5 | error analysis 4 and 7 | Proofs rewritten: B5 with absolute underflow terms and the floor; the bracket bound with midpoint rounding |
| C6 | test specification, README, architecture | Counts updated: 54 M4 tests in 9 classes, 308 in total |

## 5. Tests, data and validation

- **Tests:**
  - M4 has 54 test methods in 9 classes, against 44 in 8 classes in R1. Added: `M4R2RegressionTest`, with 9 tests.
  - The only edit to an existing test is in `M4AdversarialTest.samplingWork`. It tightens the per-sample bound from
    400 to 336, the new contract bound. No test was removed or weakened.
- **Reference data:** unchanged. Regeneration is byte-identical, and `MANIFEST.sha256` verifies.
- **Full suite on Java 25.0.4.1, against the temporary R2 reference implementation (not delivered):**
  308 tests, 308 passed (M1 125, M2 66, M3 51, M3 review 12, M4 54). `check` succeeds. See
  `validation/reference-run-summary.txt`.
- **Before and after:**
  - the R2 test suite against the R1 reference: 48 of 54 pass. The 6 failures are the F1 to F3 regression tests and
    the tie rule;
  - against the R2 reference: 54 of 54 pass (`validation/r2/r1-vs-r2-tests.txt`).
- **Mutation analysis** (`validation/mutation-results.txt`, test specification section 5):
  - 47 mutants, including 14 new R2 mutants, give 42 killed, 4 equivalent and 1 tolerance-equivalent.
  - Six survivors turned out to change behaviour, and each got a test: M26, M36, M37 and M40 after the first R2
    run, then M12 and M15 after the full rerun. For M12 and M15 this also corrects R1, which had classified them as
    equivalent.
  - The remaining survivors (M13, M20, M33, M38, M45) are explained one by one.
