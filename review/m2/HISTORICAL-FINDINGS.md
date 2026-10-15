# M2 review: historical findings record

The first review round (commit `a9d86c3e52e091c08dc359233999d08efa1394f5`, report `M2-CLAUDE-REVIEW.md`) raised four
findings and shipped three disabled tests, `M2ReviewFindingsTest`. Each test asserted a *proposed alternative*
behaviour and failed against `1e85682` by design (`logs/review-findings-enabled.txt`, `logs/junit-findings-enabled.xml`).

## Owner decisions (2026-10-15) and what happened to each test

| Finding | Disabled test (first round) | Decision | Disposition |
|---|---|---|---|
| A1: COSMETIC re-anchoring bound understated | none (documentation only) | P-A1 approved: corrected bounds documented | regression stays in `M2ReviewAdversarialTest.reanchoringErrorBounds` |
| A2: no-op controls do not move the anchor | `a2NoOpControlAdvancesTheControlClock` (option b: expected IAE) | P-A2 approved, option a: keep the behaviour, clarify the rule | **retired.** It contradicts the approved contract. Replaced by `M2Revision2Test.controlOrderingAgainstStoredAnchor`, which pins the approved behaviour |
| A3: FOREVER reports AFTER at 2^63 cycles | `a3ForeverNeverAfter` (option c: never AFTER) | P-A3 approved, option a: document the limitation | **retired.** It contradicts the approved contract. Replaced by `M2Revision2Test.foreverRepresentationalLimit`, which pins the documented behaviour |
| R1: COSMETIC must wrap like `System.nanoTime` | `r1CosmeticWrapAround` | P-R1 approved for correction | **superseded** by `M2Revision2Test`, which has 6 P-R1 tests covering more cases, including control ordering, zero elapsed time, the 2^63 edge, translation invariance and an unchanged GAME |

The retired source is preserved byte for byte in `historical/M2ReviewFindingsTest.java.txt`. It has a `.txt`
extension so it is not compiled, and it is also in Git history at `a9d86c3`. No test that contradicts the approved
contract remains in the active suite.

## Evidence of revision-1 behaviour (before the P-R1 correction)

- `logs/review-findings-enabled.txt`: the three findings tests forced on against `1e85682`. R1 failed with
  `ArithmeticException: TimePoint.ticksSince: instant difference overflows long`.
- `logs/r2-before-fix-clean-test.txt` and `logs/junit-r2-before-fix/`: the full revision-2 suite (191 tests) against
  `1e85682`. Exactly 6 tests fail, all P-R1: `TimePointTest.invalidUse`, `M2GoldenVectorTest.timePoints`, and 4
  `M2Revision2Test` wrap tests.
- `logs/r2-trial-fix.diff`, `logs/r2-trial-fix-clean-test.txt` and `logs/r2-trial-fix-check.txt`: a throwaway
  application of the minimal authorized change (not delivered, not committed): 191 of 191 pass and `check`
  succeeds.
- `logs/r2-wrong-fix-variants.txt`: two incorrect fixes are rejected by the tests.
