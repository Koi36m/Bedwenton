# M2 test specification and acceptance criteria

Status: APPROVED, revision 2 (2026-10-15); see section 5 for the revision-2 additions. Revision-1 tests:
`src/test/java/workshop/vfx/math/time` (44 test methods in 7 classes, plus the helper `M2Support`). Golden vectors: `src/test/resources/m2/golden` (4 CSV files, 3,176 rows, and
`MANIFEST.sha256`). Generator: `tools/generate_m2_golden_vectors.py`.

## 1. Methodology

**Four independent references.** None of them shares code with the production classes.
1. **Exact rational arithmetic** (`fractions.Fraction`, in the generator) for every claim the contract makes about
   exactness and correct rounding: integer stages and cycles, exact remainders, correctly rounded forward progress,
   exact COSMETIC conversions.
2. **Integer arithmetic in Java** (`Math.floorDiv`, `Math.floorMod`) for the exactness theorem
   (`TimingTest.integerExactness`: 300,000 configurations).
3. **`BigDecimal`** for the COSMETIC conversion and for error growth in rate-change chains.
4. **A bitwise replay of the specified operation order** in Python (IEEE-754 doubles, `math.fmod`, round-half-even
   `round`). This pins the exact bits for 3,176 cases, which turns the determinism requirement into a cross-language
   check.

**Kinds of test:** hand-derived boundary values (exact by construction), golden vectors, invariants under random
and extreme inputs (fuzz), algebraic laws (continuity, idempotence, path independence, shifted-pause equivalence),
long-run drift checks, and architecture checks (types, statics, forbidden tokens).

**Validation of the tests themselves.**
- The tests were run against a throwaway reference implementation written from the contract, kept outside the
  repository and not delivered: 169 tests (125 M1 + 44 M2) passed on Java 25.0.4.1 with Gradle 9.1.0.
- The generator runs twice and produces byte-identical files.
- **Mutation analysis:** 25 plausible defects were injected one at a time, and **all 25 were detected**, by 1 to 7
  failing tests each:

| ID | Injected defect | Failing tests |
|---|---|---|
| M01 | `ticksToSeconds` multiplies by 0.05 | 2 |
| M02 | `secondsToTicks` divides by 0.05 | 2 |
| M03 | COSMETIC conversion by the reciprocal `* 2e-8` | 3 |
| M04 | instant difference without overflow check | 3 |
| M05 | domains may be mixed | 1 |
| M06 | paused evaluation still subtracts instants (overflows when far away) | 1 |
| M07 | pause does not re-anchor the value | 6 |
| M08 | resume counts the paused span | 5 |
| M09 | control events may go back in time | 3 |
| M10 | restart keeps the paused state | 2 |
| M11 | seek clears the paused state | 2 |
| M12 | rate change not continuous | 5 |
| M13 | naive `frac(u / d)` phase | 6 |
| M14 | end of the last cycle not `AFTER` (off by one) | 7 |
| M15 | PING_PONG end value ignores parity | 3 |
| M16 | the start instant counted as `BEFORE` | 5 |
| M17 | signed zero not normalized | 2 |
| M18 | cycle index from the rounded quotient `(long)(u / d)` | 1 (golden) |
| M19 | backward pass as `1 - r / d` (different rounding) | 2 |
| M20 | ONCE may repeat | 1 |
| M21 | zero duration accepted | 1 |
| M22 | `TimingSample` not validated | 1 |
| M23 | restart resets the rate to 1 | 2 |
| M24 | `Math.fma` in `localTicks` | 1 (golden) |
| M25 | non-finite re-anchored value accepted | 1 |

M18 and M24 are caught only by the bitwise golden vectors. That is intended: they change bits, not behaviour at the
hand-picked points.

## 2. Test inventory

| Class | Tests | Covers (contract section) |
|---|---|---|
| `TickTimeTest` | 4 | constants; division rather than 0.05; the seconds round trip and its failure near 2^53; raw IEEE (2) |
| `TimePointTest` | 4 | factories and validation; exact GAME differences and rounding beyond 2^53; COSMETIC against `BigDecimal`; domain mismatch, null and overflow (3) |
| `PlaybackTest` | 9 | validation and defaults; the formula and integer results; pause and resume exactness and shifted-pause equivalence; idempotence; rate continuity, reversal and rate 0; seek and restart; control-instant rules; `ArithmeticException` paths; COSMETIC speed (4) |
| `TimingTest` | 11 | validation; sample validation; ONCE, LOOP and PING_PONG boundaries and end values; delays and signed zero; invalid samples; the integer exactness theorem; phase precision at 2^45 and cycles at 2^50; totality fuzz (300,000 cases); monotonicity within a pass (5, 6.3, 6.4) |
| `M2GoldenVectorTest` | 5 | manifest SHA-256; four golden files bit for bit (2 to 5) |
| `M2DeterminismTest` | 8 | bit-identical repetition; path independence with skipped, irregular and shuffled frames; low TPS in both domains; 10^7 ticks from 2^40 without drift; 10^5 integer pause/resume events exact; fractional rate chains within the proven bound; discontinuities; reversal into `BEFORE` (6) |
| `M2BoundaryTest` | 3 | exactly seven source files and eight public types; no forbidden types; no mutable statics; no clock, random, thread or platform tokens in the sources (1, 6.1, 8) |

Accuracy criteria: exact (bitwise) wherever the contract fixes the operations. Tolerances appear only where a
mathematical bound is being tested: continuity at a turn (one time step), the PING_PONG backward pass (2 ulp,
generator), and rate-chain error (`2 n 2^-52 max abs(local)`).

## 3. Golden-vector files

| File | Rows | Content |
|---|---|---|
| `tick_seconds.csv` | 217 | ticks; `ticks / 20.0`; the rejected `ticks * 0.05`; the round trip |
| `timepoint.csv` | 171 | domain, value, origin, `ticksSince` or `OVERFLOW` (hand cases and 150 random) |
| `timing.csv` | 1,692 | delay, duration, mode, cycles, local; then stage, cycle, progress (hand cases, precision cases, 700 integer and 700 real random cases) |
| `playback.csv` | 1,096 | 29 control scripts: START, EVAL, PAUSE, RESUME, RATE, SEEK, RESTART; the expected `localTicks` or new `anchorTicks` |

Doubles are written as hexadecimal floating-point literals (`float.hex`), which `Double.parseDouble` reads exactly.
Eight `timing.csv` rows lie outside the cycle-exact domain (elapsed / duration >= 2^51). They pin the specified
operations; the generator does not claim exact cycles for them.

Regenerating: `python3 tools/generate_m2_golden_vectors.py src/test/resources/m2/golden` (Python 3.9 or later,
standard library only, deterministic). The output must be byte-identical to the delivered files.

## 4. Acceptance criteria for the M2 implementation

1. **Approval first.** The owner has approved the contract and DM2-1 to DM2-9 (or revised them, with the tests
   revised to match before implementation).
2. **M1 untouched.** `git diff 9e3f3acde0db4b9d0a202ba4f7f396c132843597 --` over `src/main/java/workshop/vfx/math/core`,
   `src/test/java/workshop/vfx/math/core`, `src/test/resources/golden`, `tools/generate_golden_vectors.py` and the
   M1 documents in `docs/*.md` is empty.
3. **Tests untouched.** The M2 test sources, `src/test/resources/m2/golden/*` and the generator are byte-identical to
   the handoff (verify with the handoff's `SHA256SUMS.txt`).
4. **Production scope.** Exactly the seven files of contract section 8, under
   `src/main/java/workshop/vfx/math/time`. No new dependencies; `verifyNoRuntimeDependencies` passes.
5. **Java 25 run.** With JDK 25 and the Gradle 9.1.0 wrapper:
   `./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test` reports
   **169 tests, 169 passed, 0 failed, 0 skipped** (125 M1 + 44 M2), and `./gradlew check` succeeds.
   `-Xlint:all` gives no warnings from production code.
6. **Evidence.** `java -version`, `./gradlew --version`, both Gradle logs, JUnit XML and the commit hashes, recorded
   as for M1.
7. **Independent review.** Claude Code reviews the implementation against this contract before final acceptance.

Before the implementation exists, the M2 tests do not compile. This is expected; it was the same for M1's
"tests as delivered" commit.

## 5. Revision 2 (owner decisions on the independent review, 2026-10-15)

**Expectation changes, each documented in `M2-AMENDMENT-R2.md` section 4:**
- One assertion in `TimePointTest.invalidUse`: COSMETIC `MIN - MAX` was an `ArithmeticException` and is now
  `+1 ns = 2e-8` ticks.
- One row of `timepoint.csv`: `COSMETIC,-9223372036854775808,1` was `OVERFLOW` and is now `0x1.5798ee2308c3ap+37`.
- The `MANIFEST.sha256` line for `timepoint.csv`.
- The generator's COSMETIC rule.
- No other expected value changes; every other golden byte is unchanged.

**Tests added permanently:**
- `M2ReviewAdversarialTest` (13), from the independent review.
- `M2Revision2Test` (9): P-R1 wrap-around forwards and backwards, zero elapsed time, the 2^63 interval edge,
  playback and control across the wrap, translation invariance across the wrap, unchanged domain mismatch and GAME
  behaviour; P-A2 ordering against the stored anchor; P-A3 FOREVER limit.
- `src/test/resources/m2/review/boundary_cases.csv`: 2,235 exact-rational cases, with
  `review/m2/generate_boundary_vectors.py`.

**Removed from the active suite:** `M2ReviewFindingsTest`, whose three disabled tests asserted alternatives the owner
rejected (A2 option b, A3 option c) or are now superseded by `M2Revision2Test` (R1). The source is preserved
unchanged in `review/m2/historical/M2ReviewFindingsTest.java.txt` and in commit `a9d86c3`.

**Revision-2 acceptance criteria.** These replace criteria 3 and 5 of section 4; the other criteria are unchanged.
- **3'.** The test sources, golden files, review resources and generators are byte-identical to the revision-2
  amendment package (`SHA256SUMS.txt`).
- **5'.** With JDK 25 and the Gradle 9.1.0 wrapper: **191 tests, 191 passed, 0 failed, 0 skipped**
  (M1 125, revision-1 M2 44, review 13, revision 2 9), and `./gradlew check` succeeds.
- **Reference point before the fix:** on `1e856820` exactly 6 tests fail: `TimePointTest.invalidUse`,
  `M2GoldenVectorTest.timePoints`, and 4 `M2Revision2Test` wrap tests.
- **Production diff:** limited to `TimePoint.ticksSince` (COSMETIC branch) and `Playback`'s control-ordering check
  (`M2-AMENDMENT-R2.md` section 5).
