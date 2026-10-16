# M4 R2.1 — Independent reviewer documentation addendum

**Status:** Editorial clarification for the approved M4 R2.1 specification. This document does not revise the six public APIs, the numerical contract algorithms, reference data, test assertions, or M1–M3.

**Precedence:** For the five wording inconsistencies listed below, use the corrections here rather than the older text in the R2.1 proposal. Before writing M4 production code, add this document to `docs/m4/` and, if desired, reconcile the cited files without changing their normative behavior.

1. **Absolute tolerance (§8 of `M4-IMPLEMENTATION-CONTRACT.md`).** Replace “For a purely absolute tolerance, use `relative = 2^-40`” with “A purely absolute tolerance is not representable by this API: the minimum allowed relative contribution remains `2^-40 * L0`. For the smallest permitted relative contribution, use `relative = 2^-40`.” The normative rule in §5.3 remains `tau = max(absolute, relative * L0, 1024 * MIN_VALUE)`.

2. **Spacing rounding (`M4-DESIGN-DECISIONS.md`, DM4-9).** The prescribed rounded interval count `m = round-half-up(L/spacing)` is retained exactly, but do **not** assert that it minimizes absolute realized spacing error `abs(L/m - spacing)` across integer m. Counterexample: `L = 1`, `spacing ≈ 0.7142857` gives q≈1.4 and m=1 by the contract, while m=2 has a closer realized spacing. Describe the rule as nearest *interval count*, not nearest realized spacing.

3. **Scaled norm and underflow (`M4-IMPLEMENTATION-CONTRACT.md`, input table at §10).** Replace “the scaled norm avoids squares overflowing or underflowing” with: “Scaling by the maximum absolute component avoids overflow when squaring normalized ratios; smaller ratios or their squares may underflow to zero. The dominant ratio is exactly 1 when the component maximum is nonzero, so the squared sum is nonzero. The §5.1 speed-pair and §5.2 panel guarantees, with their stated allowances, still apply.”

4. **CI vs. full reference regeneration.** Document two levels of future verification: (a) bounded per-push Java 25 `clean test` and `check`, plus existing manifest/hash checks; and (b) manual or separately scheduled, more expensive independent `generate_m4_reference_vectors.py` regeneration, whose outputs are compared **byte-for-byte** to the checked-in CSVs and manifest. Checking `MANIFEST.sha256` by itself only checks checked-in file integrity; it is *not* evidence that the generator regenerated identical rows. The R2.1 handoff continues to require full regeneration for release acceptance.

5. **Stale R2 test count (`M4-R2-CORRECTIONS.md`, section 5).** The R2 `M4R2RegressionTest` class has **10**, not 9, `@Test` methods. R2 had 54 M4 tests in 9 test classes; R2.1 adds **5** methods in `M4R21RegressionTest`, bringing the total to **59** M4 tests in 10 classes, **313** overall. Keep the historical R2 total, and correct only the miscounted class size and any implication that a ninth method was all that was added.

**No mathematical or API changes are authorized by this addendum.** If reconciliation reveals a substantive conflict, stop and request an independent review rather than modifying the accepted formulas.
