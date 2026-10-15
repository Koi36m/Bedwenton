# M2 independent adversarial review: deterministic animation time (Claude Code)

| | |
|---|---|
| Reviewed | production `1e856820e606275c019f09d6f196bcf6a5a5138d`, evidence `80927177469d89b985d3b57a3219652a994d6d4b` (branch `m2-animation-time`) |
| Baseline | accepted M1 evidence `9e3f3acde0db4b9d0a202ba4f7f396c132843597` |
| Contract | M2 Revision 1 (`docs/m2/*`, handoff ZIP SHA-256 `ce791599...ce040a`) |
| Review branch | `review/m2-claude`, based on `80927177`; adds files only |
| Toolchain | Oracle JDK 25.0.4.1 (25.0.4.1+1-LTS-5), Gradle 9.1.0 wrapper, Linux x86_64 |
| **Recommendation** | **CONDITIONAL PASS.** No implementation defect. Conditions: the owner decides A1 to A3 (contract wording) and R1 (COSMETIC wrap-around) |

## 1. Summary

- **Integrity: confirmed independently.** Codex's input ZIP is the delivered handoff. All 25 handoff files are
  byte-identical in Codex's tree, M1 shows zero diff, and the production change is exactly the seven specified files
  (205 lines).
- **Reproduction: confirmed independently.** 169 of 169 tests pass (M1 125, M2 44), `check` succeeds, the golden
  regeneration is byte-identical, and production code compiles with no warnings.
- **Conformance.** Every production method follows the contract's operation order and validation order exactly. No
  implementation defect was found (no D findings).
- **Findings, all in the specification, which I wrote:**
  - A1: the contract understates the COSMETIC re-anchoring error bound.
  - A2: the rule "control events never go back in time" is not fully enforced, because no-op controls don't move the
    anchor.
  - A3: a FOREVER timing reports `AFTER` once the cycle index reaches 2^63.
  - R1: COSMETIC instants contradict `System.nanoTime`'s documented wrap-safe usage.
  - N1 to N5: nonblocking observations.
- **New tests:**
  - `M2ReviewAdversarialTest`: 13 tests, all passing on Codex's code. Total suite: 185 tests, 182 passed,
    3 skipped (disabled by design), 0 failed.
  - `M2ReviewFindingsTest`: 3 tests, disabled. Forced on, all 3 fail against `1e85682`, as designed.
  - Mutation run with the review tests alone on Codex's code: section 5.

## 2. Phase 1: integrity and architecture

### 2.1 Confirmed by this review (re-executed)

| Check | Result |
|---|---|
| Remote refs | `m2-animation-time` = `8092717`, `m2-handoff` = `2a222ce`; M1 refs unchanged |
| History | `9e3f3ac` → `2493a75` (handoff import) → `1e85682` (production) → `8092717` (evidence); linear, authored `Codex <codex@local>`, unsigned |
| Input ZIP in `verification/m2/input/` | SHA-256 `ce791599a7ba755a2a23f8002940808115c6dd71e99a27aa7f878f47b6ce040a`, identical to the delivered handoff |
| Handoff files in `8092717` | 25 of 25 byte-identical to the handoff's `SHA256SUMS.txt`, including all 8 test sources, 5 golden files, the generator and the docs |
| Handoff import compared with my `m2-spec` commit `fe1f5a6` | no difference in tests, golden data, docs or tools |
| M1 preservation | `git diff 9e3f3ac 8092717` over M1 production, M1 tests, M1 golden data, the M1 generator, `docs/*.md` and the build files: **0 lines** |
| Production scope | exactly 7 files under `src/main/java/workshop/vfx/math/time`; nothing else under `src/main` |
| Java 25 `clean test` | **169 / 169 passed** (M1 125, M2 44), 0 failed, 0 skipped |
| `check` | BUILD SUCCESSFUL (`verifyNoRuntimeDependencies` passed) |
| Golden regeneration | `tools/generate_m2_golden_vectors.py` output is byte-identical to `src/test/resources/m2/golden` |
| Compiler | no production warnings; the 5 JOML class-file warnings are the known test-scope ones |
| Hidden clocks, state, dependencies | none: the sources contain no clock, random, thread or `java.time` reference; all fields final; statics are primitive constants or enum constants; no imports at all |

### 2.2 Reported by Codex, not independently verifiable here

- That the owner authorized M2 Revision 1 and DM2-1 to DM2-9 before implementation. The docs still carry their
  "PROPOSED" headers; see N1.
- Codex's own environment details (`/workspace/work/...`), and the claim that its test suite passed on the first run.

### 2.3 API conformance (contract section 8)

All seven types exist with exactly the specified public members. Records: `TimePoint`, `Playback`, `Timing`,
`TimingSample`. Enums: `TimeDomain`, `RepeatMode`, `TimingSample.Stage`. `TickTime` is a final class with a private
constructor. Helpers are private (`validateDomain`, `validateControl`, `reanchorTicks`).

`Playback.withRate(TimePoint at, double rate)` names its parameter `rate`, which shadows the record field. Each use
was checked: `reanchorTicks` evaluates `localTicks`, which reads the field (the old rate), and the new record
receives the parameter (the new rate). Correct.

## 3. Phase 2: mathematical audit, method by method

| Method | Contract operation sequence | Implementation | Verdict |
|---|---|---|---|
| `TickTime.ticksToSeconds` / `secondsToTicks` | `t / 20.0`, `s * 20.0` | identical | conforms |
| `TimePoint` constructor | null domain gives IAE | identical | conforms |
| `TimePoint.ticksSince` | null or other domain gives IAE; `subtractExact`; `(double) d` or `(double) d / 5e7` | identical (the overflow is re-thrown with a clearer message) | conforms (R1 concerns the contract) |
| `Playback` constructor | non-null anchor; finite anchorTicks and rate | identical | conforms |
| `localTicks` | domain check; paused gives `anchorTicks`; else `anchorTicks + rate * ticksSince` | identical, no `fma` | conforms |
| `pause` / `resume` | instant checks; idempotent `this`; re-anchor at `L(at)` with a finiteness check | identical | conforms (A2 concerns the contract) |
| `withRate` | instant checks, then rate finite, then `L(at)` | identical order | conforms |
| `seek` / `restart` | instant checks; value check (seek); no evaluation | identical | conforms |
| `Timing` constructor | mode, finite delay, finite duration > 0, cycles >= 1, ONCE has 1 cycle | identical (`-0.0` and NaN rejected) | conforms |
| `Timing.sample` | finite check; `u`; finiteness; signed zero; BEFORE; `%`; `rint`; `n >= (double) cycles`; parity end; `(d - r) / d` | identical, same order | conforms (A3 concerns the contract) |
| `TimingSample` constructor | stage, cycle >= 0, progress in [0, 1] | identical | conforms |

The checklist items:
- **Integer gameplay timing.** Exact under theorem 6.3. Verified exhaustively for small integers (121,000 cases), by
  2,000 random integer control scripts against a pure `long` model, and by the boundary oracle.
- **Conversion boundaries.** The 2^53 rounding for GAME, and the double rounding for COSMETIC beyond 2^53 ns, behave
  as specified (`cosmeticBeyond2p53`).
- **Domain mismatch and long overflow.** IAE and `ArithmeticException` as specified. While paused no subtraction
  happens, so no overflow is possible.
- **Continuity of pause, resume, restart, seek and rate.** Correct. `restart` and `seek` never evaluate, so they
  succeed even where evaluation would overflow (`validationOrder`).
- **Negative and zero rates.** Reverse playback runs into `BEFORE`; rate 0 differs from paused. Correct.
- **Out-of-order evaluation versus out-of-order control.** Evaluation at any instant is allowed (affine
  extrapolation, no history). Control before the anchor is rejected. A no-op control does not advance the anchor
  (A2).
- **Cycle boundaries and adjacent doubles.** Exact on and next to `k d` for k up to 2^51 - 1, for 14 durations
  including `Double.MIN_VALUE` and 2^-1060 (`boundaryOracle`, 2,235 exact-rational cases).
- **Precision around 2^51 and 2^53.** The contract's 2^51 limit is sufficient and tight. 200,000 random cases with
  elapsed/duration in [2^50, 2^51) are exact. Above it, mismatches occur: 10,475 of 200,000 random cases in
  [2^51, 2^52), 33,937 in [2^52, 2^53), 100,046 in [2^53, 2^54). This matches the proof bound
  `n * 2^-52 < 1/2`.
- **Signed zero, subnormal durations, `%`.** Correct. Small multiples of `Double.MIN_VALUE` give exact cycles.
- **Bitwise determinism.** The operation order is identical to the contract; results are bit-identical before and
  after 3 million hot iterations (C2 compiled), and Java forbids implicit FMA contraction.
- **Long runs and drift.** No accumulation (10^7 ticks from 2^40 in the delivered test). Re-anchoring error is
  bounded per event (A1 corrects the COSMETIC constant).
- **Freezes, low TPS, COSMETIC discontinuities.** As specified (delivered tests), apart from the platform risk R1.

## 4. Findings

There are **no D findings**: the implementation has no confirmed defect.

### A1. The contract understates the COSMETIC re-anchoring error bound (specification error)

- **Where:** `M2-IMPLEMENTATION-CONTRACT.md` 6.4, the row "control events: each re-anchoring adds at most
  `1.5 * 2^-52 * max abs(local)`". The behaviour belongs to `Playback.pause` and `Playback.withRate`.
- **Reproduction:** `Playback.start(TimePoint.cosmeticNanos(0), -0x1.f77c448775a2dp18, 0x1.16f13d3b52584p-1)`, then
  `.withRate(TimePoint.cosmeticNanos(113_443_124_582_639L), 1.0).anchorTicks()`. The exact value is
  `a + rate * delta / 5e7`.
  - Expected by the contract: error at most `1.5 * 2^-52 * M = 2.40e-10`.
  - Actual: error `2.87e-10`, which is **1.79 x 2^-52 x M**.
- **Reasoning:** my derivation counted two roundings, `fl(rate * dt)` and `fl(anchor + product)`, giving
  `3 * 2^-53 * M`. It omitted the COSMETIC conversion `delta / 5e7` (one more rounding of the product's input), and
  above 2^53 ns also the `long` to `double` conversion. Correct bounds:
  - GAME: 1.5 x 2^-52 x M;
  - COSMETIC with `abs(delta) <= 2^53` ns: 2.5 x 2^-52 x M;
  - COSMETIC beyond 2^53 ns: 3.5 x 2^-52 x M.
  All three are verified over 150,000 random cases (`reanchoringErrorBounds`).
- **Severity:** documentation only. The magnitudes are about 1e-10 ticks; no visible effect.
- **Blocks acceptance:** no.
- **Correction:** amend the 6.4 row (proposal P-A1, section 6). No code change.

### A2. "Control events never go back in time" is not enforced through no-op controls (specification ambiguity)

- **Where:** contract section 4, and `Playback.pause` / `Playback.resume` (the idempotent paths return `this`).
- **Reproduction:** `Playback.start(tick(0)).pause(tick(100)).pause(tick(200)).resume(tick(150))` is accepted. The
  second pause is a no-op, the anchor stays at 100, and 150 >= 100.
  - Expected under the contract's parenthetical rationale: IAE, because 150 precedes a control event at 200.
  - Actual: accepted. The result equals the history `pause@100, resume@150`.
- **Reasoning:** the normative rule compares with the anchor, and Codex implements it exactly. The rationale text
  promises more than the rule. The result is still well defined: it is the history with the no-op removed.
- **Severity:** low. Only a caller that issues redundant controls out of order is affected, and it gets a coherent
  result.
- **Blocks acceptance:** no.
- **Correction options:**
  - (a) Recommended: reword to "a control instant must not precede the anchor (the instant of the last control that
    changed the playback); no-op controls do not move the anchor". No code change.
  - (b) Make no-op controls return a new `Playback` with an advanced anchor. This breaks the "same object" clause and
    `PlaybackTest.idempotentControls`. Disabled test: `a2NoOpControlAdvancesTheControlClock` (option b only).

### A3. FOREVER can report `AFTER` once the cycle index reaches 2^63 (specification gap)

- **Where:** `Timing.sample`, the comparison `n >= (double) cycles` with `cycles = FOREVER = Long.MAX_VALUE`, which
  converts to 2^63.
- **Reproduction:**
  - `Timing.loop(Double.MIN_VALUE).sample(1.0)` returns `(AFTER, 9223372036854775806, 1.0)`.
  - `Timing.loop(1.0).sample(0x1p63)` returns `AFTER` too.
- **Expected:** the contract calls FOREVER "more cycles than will ever be sampled", and the guaranteed domain stops
  at elapsed/duration < 2^51, but no clause says what FOREVER does beyond it.
- **Reasoning:** beyond 2^63 cycles the cycle index cannot be represented as a `long`, so saturating to `AFTER` is
  one defensible choice.
- **Severity:** very low. It needs durations below about 1e-5 ticks with years of elapsed time, or about 2^63 ticks
  (1.5e10 years at 20 TPS).
- **Blocks acceptance:** no.
- **Correction options:**
  - (a) Recommended: document "FOREVER reports AFTER only if the cycle index reaches 2^63, which needs
    elapsed/duration >= 2^63, outside the guaranteed domain".
  - (b) Require `durationTicks >= 2^-20`. This alone does not prevent the case.
  - (c) Never report AFTER for FOREVER. Then the `ACTIVE` cycle index saturates, and parity is meaningless beyond
    2^53. Disabled test: `a3ForeverNeverAfter` (option c only).

### R1. COSMETIC instants contradict `System.nanoTime`'s documented wrap-safe usage (specification risk)

- **Where:** contract section 3, `TimePoint.ticksSince` (`Math.subtractExact`), and section 4,
  `Playback.validateControl` (`at.value() < anchor.value()`), applied to the COSMETIC domain.
- **Fact:** JDK 25's `System.nanoTime` Javadoc, read from the shipped `src.zip`:
  - "nanoseconds since some fixed but arbitrary origin time (perhaps in the future, so values may be negative)";
  - differences are meaningful;
  - to compare, use `System.nanoTime() - startTime >= timeoutNanos` instead of
    `System.nanoTime() >= startTime + timeoutNanos` "because of the possibility of numerical overflow".
  The API therefore guarantees correct wrapping differences up to 2^63 ns, not ordered raw values.
- **Reproduction:** `TimePoint.cosmeticNanos(Long.MIN_VALUE + 10).ticksSince(TimePoint.cosmeticNanos(Long.MAX_VALUE - 9))`.
  - Expected under the nanoTime contract: 20 ns, that is 4e-7 ticks.
  - Actual: `ArithmeticException`. `Playback.start(MAX - 9).pause(MIN + 10)` is also rejected with IAE, as
    "before the anchor".
- **Practical consequence:** on a JVM whose `nanoTime` origin makes the values cross `Long.MAX_VALUE` while an effect
  runs, COSMETIC playbacks start throwing. Common JVMs on Linux appear to derive `nanoTime` from a monotonic clock
  since boot, so values are small and positive and this does not happen; that is not verified here, and the Java
  specification does not guarantee it. GAME instants are unaffected (tick counters are ordinary integers).
- **Severity:** low in practice, but it is a latent portability defect in the specification. It would surface in
  the Paper adapter milestone, not in the math core's own tests.
- **Blocks acceptance:** not for M2 as specified (Codex implemented the contract faithfully). Recommended before any
  adapter uses COSMETIC time.
- **Correction (proposal P-R1):** in the COSMETIC domain only:
  - `delta = value - origin.value()`, wrapping, valid while the true elapsed time is below 2^63 ns;
  - ordering of control instants by `at.value() - anchor.value() < 0`.
  - GAME keeps `subtractExact` and direct comparison.
  - Cost: 2 lines of production. The tests change one assertion (`TimePointTest.invalidUse`, the COSMETIC
    `MIN - MAX` case) and one golden row (`timepoint.csv`: `COSMETIC,-9223372036854775808,1,OVERFLOW`, which would
    become `-9223372036854775807` ns), plus the generator's COSMETIC rule.
  - Disabled test: `r1CosmeticWrapAround`.

### Nonblocking observations

- **N1. Repository hygiene.** Codex placed copies of `README-FOR-CODEX-M2.md`, `SHA256SUMS.txt` and my `validation/`
  reference logs at the repository root, and the M2 docs still say "PROPOSED" although Codex reports owner
  approval. Suggest marking the docs approved, as was done for M1 Amendment 3, and moving the handoff copies under
  `verification/m2/input/`.
- **N2. Same JDK build as Codex.** Oracle 25.0.4.1, as for M1. M2 uses only exactly specified IEEE-754 operations
  (contract 6.1), so cross-vendor risk is negligible.
- **N3. Gaps in my original M2 tests, closed by the review tests.**
  - No test made the validation order of `withRate` observable: injected defect C09 (rate validated after
    evaluation) survives the 44 delivered tests and is caught by `validationOrder`.
  - The re-anchoring bound was tested for GAME only.
  - No Java oracle for non-integer timing was independent of the specified algorithm (only the Python replay).
- **N4. Signed-zero normalization** is covered by the delivered `TimingTest.delay` but not by the review tests
  (mutant C01 survives the review suite alone); the full suite catches it.
- **N5. Unsigned commits.** Integrity rests on the SHA-256 chain, as for M1.

## 5. Phase 3: adversarial tests and evidence

New files only. No delivered test, golden file or production file is modified.

| File | Content |
|---|---|
| `src/test/java/workshop/vfx/math/time/M2ReviewAdversarialTest.java` | 13 tests, tag `m2-review` |
| `src/test/java/workshop/vfx/math/time/M2ReviewFindingsTest.java` | 3 disabled tests, tag `m2-review-finding` |
| `src/test/resources/m2/review/boundary_cases.csv` + `MANIFEST.sha256` | 2,235 exact-rational boundary cases |
| `review/m2/generate_boundary_vectors.py` | independent generator: no `fmod`, no `rint`, exact floors and quotients |
| `review/m2/logs/` | versions, Codex-commit rerun logs and JUnit XML, review run, findings run, mutation results |

`M2ReviewAdversarialTest`:
- `runsOnJava25`;
- `boundaryOracle`;
- `cycleDomainEdge`;
- `exhaustiveSmallIntegers`;
- `integerControlScriptsMatchLongModel`;
- `timeTranslationInvariance` (metamorphic);
- `pingPongMirror` (metamorphic);
- `stageMonotone`;
- `validationOrder`;
- `subnormalAndHugeDurations`;
- `cosmeticBeyond2p53`;
- `reanchoringErrorBounds` (A1);
- `jitStability`.

**Results** (Java 25.0.4.1, Gradle 9.1.0, on `review/m2-claude` = Codex `8092717` plus the review files):
- `clean test`: **185 tests: 182 passed, 0 failed, 3 skipped** (the disabled findings).
- `check`: BUILD SUCCESSFUL.
- Findings forced on with an init script: **3 of 3 fail as designed** (`logs/review-findings-enabled.txt`).

**Mutation run of the review tests alone** on Codex's code (`logs/review-mutation-results.txt`): 14 defects injected
one at a time into a scratch copy, never into the repository. **13 of 14 were detected by `M2ReviewAdversarialTest`
alone**, by 1 to 4 tests each:

| ID | Injected defect | Review tests failing |
|---|---|---|
| C01 | signed zero not normalized | 0 (caught by 2 delivered tests) |
| C02 | cycle from `floor(u / d)` | 2 |
| C03 | end of run off by one | 4 |
| C04 | backward pass as `1 - r / d` | 3 |
| C05 | PING_PONG end ignores parity | 3 |
| C06 | PING_PONG parity inverted | 4 |
| C07 | resume counts the paused span | 1 |
| C08 | `Math.fma` in `localTicks` | 1 |
| C09 | rate validated after evaluation | 1 (**not caught by the 44 delivered M2 tests**) |
| C10 | restart keeps the paused state | 1 |
| C11 | COSMETIC conversion by the reciprocal | 1 |
| C12 | seek clears the paused state | 1 |
| C13 | pause does not re-anchor | 2 |
| C14 | remainder via `u - d * floor(u / d)` | 2 |

Cross-check against the delivered suite, using clean runs with only the 7 delivered M2 classes selected
(`logs/original-suite-vs-C09-C01.txt`): unmutated 44/44 pass; **C09: 44/44 still pass**, which confirms the gap in
N3; C01: 2 delivered tests fail.

## 6. Proposed contract amendments (proposals only, not applied)

- **P-A1** (contract 6.4, control-events row): "each re-anchoring adds at most `1.5 * 2^-52 * M` (GAME),
  `2.5 * 2^-52 * M` (COSMETIC, `abs(delta) <= 2^53` ns) or `3.5 * 2^-52 * M` (COSMETIC beyond), with
  `M = max(abs(anchorTicks), abs(result))`".
- **P-A2** (contract 4, control rule): "A control instant must not precede the anchor, which is the instant of the
  last control that changed the playback. No-op controls (pause while paused, resume while playing) return `this`
  and do not move the anchor."
- **P-A3** (contract 5): "`FOREVER` reports `AFTER` only once the cycle index reaches 2^63, which requires
  elapsed/duration >= 2^63, outside the guaranteed domain."
- **P-R1** (contract 3 and 4, COSMETIC only): wrapping difference `value - origin.value()` and wrapped ordering
  `at.value() - anchor.value() < 0`, valid while the true elapsed time is below 2^63 ns, as `System.nanoTime`
  prescribes. GAME is unchanged. Requires one test assertion, one golden row and the generator's COSMETIC rule to be
  revised by the specification author before Codex changes 2 lines.

## 7. Recommendation

**CONDITIONAL PASS.** The implementation is correct, deterministic and faithful to Revision 1, and every claim Codex
reported was reproduced. The conditions are the owner's decisions:
1. A1, A2 and A3: adopt the wording amendments (no code change).
2. R1: adopt P-R1 now (recommended), or record it as a known limitation to be fixed before the adapter milestone.

If P-R1 is adopted, Codex changes 2 lines after the revised test and golden files are delivered, then reruns the
suite under Java 25. M2 final acceptance remains with the owner. M3 has not been started.

## 8. Addendum: owner decisions and contract revision 2 (2026-10-15)

| Finding | Decision | Result |
|---|---|---|
| A1 | P-A1 approved | contract 6.4 and new 6.4.1: bounds 1.5 / 2.5 / 3.5 x 2^-52 x M with derivation; production unchanged |
| A2 | P-A2 approved (option a) | contract 4: ordering against the stored anchor, no control history; behaviour unchanged |
| A3 | P-A3 approved (option a) | contract 5: FOREVER representational limit documented; behaviour unchanged |
| R1 | P-R1 approved for correction | contract 3, 4, 6, 7: COSMETIC uses wrapping subtraction and wrapped ordering; GAME unchanged. Codex makes the 2-change production correction (`docs/m2/M2-AMENDMENT-R2.md` section 5) |

**Test suite after revision 2: 191 tests.**
- M1: 125.
- Revision-1 M2: 44. One assertion in `TimePointTest` and one golden row are changed, both documented.
- `M2ReviewAdversarialTest`: 13.
- `M2Revision2Test`: 9.
- `M2ReviewFindingsTest` is retired from the active suite; see `HISTORICAL-FINDINGS.md`.

Against `1e85682` exactly 6 tests fail, all of them P-R1. With a throwaway minimal fix, 191 of 191 pass. The
complete expectation changelog is in `docs/m2/M2-AMENDMENT-R2.md` section 4.

Recommendation for M2 after Codex's correction: re-run the 191-test suite on Java 25 and repeat this review's
integrity checks on the production diff. If the diff is limited to the two authorized changes and the suite passes,
I expect to recommend **PASS**. Final acceptance remains the owner's.
