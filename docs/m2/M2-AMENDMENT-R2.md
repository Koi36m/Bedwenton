# M2 Amendment: contract revision 2 (APPROVED)

Status: **approved by the owner on 2026-10-15.** Prepared by Claude Code from the independent review
(`review/m2/M2-CLAUDE-REVIEW.md`, commit `a9d86c3e52e091c08dc359233999d08efa1394f5`) and the owner's decisions.

Baseline:
- revision 1 contract and tests: handoff ZIP `ce791599...ce040a`, import `2493a753d9c6e19f29a59ca445c0e54627b94523`;
- implementation: `1e856820e606275c019f09d6f196bcf6a5a5138d`, evidence `80927177469d89b985d3b57a3219652a994d6d4b`.

M1 is unchanged. M3 is not started.

## 1. Decisions

| ID | Decision | Kind | Production change |
|---|---|---|---|
| P-A1 | Correct the re-anchoring error guarantee: separate GAME and COSMETIC bounds | wording (specification error) | none; the arithmetic stays as it is |
| P-A2 | Control ordering is against the stored anchor; no control history is kept | wording (clarification) | none; current behaviour kept |
| P-A3 | Document the representational limit of `Timing.FOREVER` | wording (documented limitation) | none |
| P-R1 | COSMETIC follows `System.nanoTime` elapsed-time semantics | correction | **yes**: 2 changes, section 5 |

## 2. Contract changes (`M2-IMPLEMENTATION-CONTRACT.md`, marked [R2])

| # | Section | Revision 1 | Revision 2 | Decision |
|---|---|---|---|---|
| C1 | header | "PROPOSED, revision 1" | "APPROVED, revision 2", with provenance of revision 1 | all |
| C2 | 3, `ticksSince` | `Math.subtractExact` in both domains; overflow throws | GAME: `subtractExact` (unchanged); COSMETIC: `value - origin.value()` with wrapping, never throws | P-R1 |
| C3 | 3 | none | COSMETIC interval restriction: valid for true elapsed time below 2^63 ns in magnitude; -2^63 is read as backwards; larger intervals alias | P-R1 |
| C4 | 4, control rule 2 | `at.value() < anchor.value()` ("control events never go back in time") | GAME: `at.value() < anchor.value()`; COSMETIC: `at.value() - anchor.value() < 0` (wrapping). The check is only against the **stored anchor**; no history; no-op controls return `this` and do not move it | P-R1, P-A2 |
| C5 | 4, after the table | overflow in `L(at)` throws | only for GAME; COSMETIC never overflows | P-R1 |
| C6 | 5, `Timing` | "FOREVER means more cycles than will ever be sampled" | `AFTER` with cycle `Long.MAX_VALUE - 1` once the cycle index reaches 2^63; a documented limitation; never inside the guaranteed domain | P-A3 |
| C7 | 6.1, operations list | | adds wrapping `long` subtraction | P-R1 |
| C8 | 6.4, `ticksSince` row | `abs(delta) <= 2^53` | plus the COSMETIC 2^63 ns restriction | P-R1 |
| C9 | 6.4, control-events row | at most `1.5 * 2^-52 * max abs(local)` | `c * 2^-52 * M`: `c = 1.5` GAME, `2.5` COSMETIC with `abs(delta) <= 2^53` ns, `3.5` COSMETIC beyond | P-A1 |
| C10 | 6.4.1 (new) | none | derivation of C9 and the revision-1 counterexample | P-A1 |
| C11 | 6.5 | none | a COSMETIC wrap is not a discontinuity | P-R1 |
| C12 | 7, exceptions | instant difference overflow is an `ArithmeticException` | GAME only; COSMETIC never throws for differences | P-R1 |

Also updated, marked [R2]:
- `M2-CONVENTIONS.md`: COSMETIC row, facts F11 (`System.nanoTime` semantics) and F12 (rounding bound);
- `M2-RISKS-AND-DEFERRED.md`: drift residual, wrap-around risk resolved, FOREVER limitation;
- `M2-TEST-SPECIFICATION.md`: new section 5;
- `M2-DESIGN-DECISIONS.md`: status only.

`README-FOR-CODEX-M2.md` (revision 1) is left unchanged; revision-2 instructions are in
`README-FOR-CODEX-M2-R2.md`.

## 3. Mathematical rationale

**P-R1.**
- `System.nanoTime` returns "nanoseconds since some fixed but arbitrary origin time (perhaps in the future, so values
  may be negative)". Its Javadoc computes elapsed time as `t1 - t0` and compares with `t1 - t0 >= x` "because of the
  possibility of numerical overflow" (JDK 25 source, `java/lang/System.java`).
- Two's-complement subtraction is arithmetic modulo 2^64. Read as a signed value, the result is the unique
  representative of `t1 - t0 (mod 2^64)` in [-2^63, 2^63 - 1]. It therefore equals the true elapsed time whenever
  that is in [-2^63, 2^63 - 1], whether or not the raw values wrapped.
- `Math.subtractExact` instead rejects exactly the cases where the raw values straddle the wrap, and a raw `<` orders
  instants by value, not by time. Both are wrong for a source whose values may wrap.
- GAME counters are plain integers without wrap semantics. There, an overflow genuinely means "not representable",
  so GAME keeps checked arithmetic.
- The change is translation invariant: shifting a whole COSMETIC history by any constant (with wrapping) leaves every
  difference, every ordering decision and every output bit unchanged (`M2Revision2Test.translationAcrossTheWrap`).

**P-A1.** Every IEEE-754 operation adds relative error at most `u = 2^-53`. Re-anchoring computes
`fl(A + fl(k * D))` with `abs(k * D) <= 2M`.
- GAME: `D` is exact, giving `u * 2M + u * M = 3uM`.
- COSMETIC: `D = fl(delta / 5e7)` adds `2uM`, giving `5uM`.
- COSMETIC with `abs(delta) > 2^53`: the long-to-double conversion adds another `2uM`, giving `7uM`.

Revision 1's 1.5 omitted the conversion roundings. The counterexample in contract 6.4.1 reaches 1.79.

**P-A2.** `pause` while paused and `resume` while playing return `this`. Their instant is therefore not recorded,
and the only instant a `Playback` holds is its anchor. A later control can precede a no-op's instant without
contradiction: the result equals the history with the no-op removed (`M2Revision2Test.controlOrderingAgainstStoredAnchor`).

**P-A3.** `(double) Long.MAX_VALUE` is 2^63, so `n >= (double) FOREVER` holds exactly when the cycle index
`n >= 2^63`. A `long` cannot index cycles beyond that. The guaranteed domain (elapsed/duration < 2^51) lies 12 binary
orders of magnitude below it.

## 4. Changes to test expectations (complete list)

Every change below follows from P-R1. **No other expected value, tolerance or golden byte changes.**

| # | File | Revision 1 | Revision 2 | Reason |
|---|---|---|---|---|
| T1 | `src/test/java/workshop/vfx/math/time/TimePointTest.java`, `invalidUse` | `assertThrows(ArithmeticException.class, () -> cosmeticNanos(Long.MIN_VALUE).ticksSince(cosmeticNanos(Long.MAX_VALUE)))` | `assertBits(2e-8, ...)`: the wrapped difference is +1 ns | `MIN - MAX = 1 (mod 2^64)`: one nanosecond after `MAX` comes `MIN` |
| G1 | `src/test/resources/m2/golden/timepoint.csv` | `COSMETIC,-9223372036854775808,1,OVERFLOW` | `COSMETIC,-9223372036854775808,1,0x1.5798ee2308c3ap+37` | `MIN - 1` wraps to `Long.MAX_VALUE` ns, a valid forward interval just below 2^63; `(double) Long.MAX_VALUE / 5e7 = 2^63 / 5e7 = 184467440737.09552` |
| G2 | `src/test/resources/m2/golden/MANIFEST.sha256` | `783f41b7...0633  timepoint.csv` | `4debf5c6a1b76782c5d452efc93c775a3b183a4d34769bed1ce8d06a5efa3458  timepoint.csv` | follows G1 |
| G3 | `tools/generate_m2_golden_vectors.py` | `ticks_since`: overflow gives `None` in both domains | COSMETIC: `float(wrap64(value - origin)) / 5e7`; GAME unchanged; docstring note | P-R1 |

Regenerating with the revision-2 generator reproduces all golden files byte for byte. Against revision 1 the only
differences are G1 and G2. The other 170 `timepoint.csv` rows and the `tick_seconds.csv`, `timing.csv` and
`playback.csv` files are byte-identical: no other row exercised a wrapping COSMETIC difference.

**Added (permanent):**
- `M2Revision2Test` (9 tests);
- `M2ReviewAdversarialTest` (13 tests, from the review);
- `src/test/resources/m2/review/` (2,235 boundary cases and their manifest).

**Removed from the active suite:** `M2ReviewFindingsTest` (3 disabled tests). It contradicted the approved contract
for A2 and A3, and is superseded for R1. Its history is preserved in `review/m2/HISTORICAL-FINDINGS.md` and
`review/m2/historical/`.

## 5. Authorized production change for Codex (minimum)

Exactly two semantic changes, in two files. The contract text in sections 3 and 4 is normative; the code below is a
non-binding illustration of the minimum.

1. **`TimePoint.ticksSince`**: for `TimeDomain.COSMETIC`, return `(double) (value - origin.value) / 50_000_000.0`,
   using plain, wrapping `-`. Keep the null and domain checks first. Keep `Math.subtractExact` and the
   `ArithmeticException` for GAME.
2. **`Playback` control-instant validation (`validateControl`)**: for an anchor in the COSMETIC domain, reject
   `at.value() - anchor.value() < 0` (wrapping). For GAME, keep `at.value() < anchor.value()`. Keep the domain
   check first.

```java
// illustration only
if (domain == TimeDomain.COSMETIC) return (double) (value - origin.value) / 50_000_000.0;       // TimePoint
boolean before = anchor.domain() == TimeDomain.COSMETIC
        ? at.value() - anchor.value() < 0 : at.value() < anchor.value();                       // Playback
```

**Not authorized:**
- any other production change;
- changing arithmetic to meet the old P-A1 bound;
- changing the behaviour of no-op controls;
- redesigning the cycle counter;
- editing tests, golden data or generators;
- touching M1.

**Verified before handoff** with a throwaway application of exactly these two changes, kept outside the repository
and not delivered: 191 of 191 tests passed and `check` succeeded on Java 25.0.4.1 with Gradle 9.1.0. Two incomplete
or over-broad variants were rejected by the tests:
- GAME also wrapping: 5 failures;
- COSMETIC subtraction fixed but the ordering left raw: 2 failures.

## 6. Acceptance criteria

1. The package's `SHA256SUMS.txt` verifies. Tests, golden data, review resources and generators are byte-identical
   to the package.
2. The production diff against `1e85682` is limited to section 5. M1 has no diff against `9e3f3ac`.
3. On Java 25 with Gradle 9.1.0: `clean test` gives **191 tests, 191 passed, 0 failed, 0 skipped**, and `check`
   succeeds.
4. Reference point: before the correction, exactly 6 tests fail:
   - `TimePointTest.invalidUse`;
   - `M2GoldenVectorTest.timePoints`;
   - `M2Revision2Test.cosmeticDifferenceAcrossTheWrap`, `intervalRestriction`, `cosmeticPlaybackAcrossTheWrap` and
     `translationAcrossTheWrap`.
5. Evidence as for revision 1, then Claude Code's final review before the owner's M2 acceptance.
