# M3 test specification and acceptance criteria

Status: PROPOSED, with the contract. Tests: `src/test/java/workshop/vfx/math/curve/`, 38 test methods in 7 classes
plus the helper `CurveTestSupport`. Reference data: `src/test/resources/m3/reference/`, 3 CSV files with 2,349 rows,
and `MANIFEST.sha256`. Generator: `tools/generate_m3_reference_vectors.py`.

## 1. Methodology: oracles independent of the recommended algorithms

| Oracle | Used for | Why it is independent |
|---|---|---|
| Exact rational arithmetic (`fractions`, `BigDecimal`) | easing, Bezier and line values | evaluates the exact Bernstein form or polynomial, not de Casteljau or the split evaluation |
| 60-digit Taylor series at the exact rational phase | spiral point, derivative, second derivative | no floating-point trigonometry, no turn reduction |
| Closed-form identities | endpoints, end derivatives `n(P1 - P0)`, helix speed, circle curvature 1/r, `3h^2` near the ends of smoothstep | mathematics, not code |
| Independent formulations | M1 `Quat.axisAngle(Y, θ)` rotation for circles; the Full Heal ring formula; degree elevation; Bezier reversal | different algorithms for the same object |
| Finite differences | all derivatives | numerical differentiation of `point` |
| Metamorphic relations | pose composition (`b.then(a)`), affine invariance, symmetry and duality of easings | relations between outputs, no expected values |
| Property and fuzz tests | ranges, monotonicity, seams, path independence, determinism under JIT | invariants |

Row classes in the reference data:
- **EXACT** (684 rows): inputs chosen so that the exact value is representable and every reasonable evaluation order
  is exact. Compared with `==`, which treats -0.0 and 0.0 as equal.
- **ROUNDED** (1,665 rows): compared within the contract's section 7 bounds.

No expected value is produced by running the recommended algorithm.

Provenance: the generator and all test code were written for this project. Python 3.9+ standard library only. No
third-party library and no tutorial or repository code.

## 2. Inventory

| Class | Tests | Contract | Covers |
|---|---|---|---|
| `EasingTest` | 8 | 6 | catalogue; endpoints, clamping, NaN; hand values; accuracy fuzz against `BigDecimal` (40,000 inputs x 15); monotonicity (exact for power easings, within 2^-49 for smooth ones); symmetry and duality; continuity and end slopes; composition with M2 `Timing` |
| `BezierTest` | 7 | 4 | validation; exact endpoints (20,000 random curves up to 1e100); end derivatives and C1 joins; degree elevation; affine invariance and reversal; finite differences; degenerate geometry |
| `SpiralTest` | 9 | 5 | validation; factories; **M1 CONVENTIONS 5 circle checks (exact)**; Full Heal and Astral Prism reproduction; exact seams at integer t; 2^20-turn exactness against the radian contrast; shape properties (radius, height, helix speed, curvature); finite differences; exact quarter points |
| `PosedCurveTest` | 5 | 3 | validation; placed circle geometry (plane, radius, orientation) up to world-border coordinates; composition with `Pose.then`; scaled-direction derivatives; orientation via `Frame.fromHeading` |
| `M3ReferenceVectorTest` | 4 | 4-7 | manifest; easing (1,155); Bezier and line (645); spiral (549) |
| `M3DeterminismTest` | 2 | 7.1 | value equality; bit-identical results before and after JIT; path independence |
| `M3BoundaryTest` | 3 | 1, 2, 7 | API shape; StrictMath-only trigonometry (source scan); purity, dependencies and statics |

## 3. Validation of the tests (before handoff)

- **Run against a throwaway reference implementation** of the contract, kept outside the repository and not
  delivered. With the 191 M1 and M2 tests: **229 / 229 passed**, and `check` succeeded (Java 25.0.4.1, Gradle 9.1.0).
- **Measured worst error / contract bound** over the ROUNDED rows (`validation/bound-ratios.txt`):

  | Quantity | Worst error / bound |
  |---|---|
  | easing | 0.159 |
  | Bezier point | 0.130 |
  | Bezier derivative | 0.112 |
  | Bezier second derivative | 0.012 |
  | spiral point | 0.417 |
  | spiral derivative | 0.417 |
  | spiral second derivative | 0.426 |

  Every bound has at least 2.3x headroom.
- **Mutation analysis.** 25 plausible defects were injected one at a time, and **25 / 25 were detected**
  (`validation/mutation-results.txt`): radian phase, `Math` trigonometry, reversed orientation, a wrong clockwise
  factory, the constant-radius rule, an inexact radius end, derivative terms, quadrant table, negative-phase wrap,
  `closed()`, de Casteljau weights, derivative factors, inexact line end, direct smootherstep, no clamping, NaN
  accepted, in-out scale, wrong power, pose derivative scale and translation, missing parameter checks, negative
  radius.
- The generator is deterministic, and regenerating gives byte-identical files.

## 4. Acceptance criteria for the M3 implementation

1. **Approval first.** The owner approves the contract and DM3-1 to DM3-14, possibly after revision, with the tests
   revised to match before implementation. M2 is formally accepted.
2. **M1 and M2 untouched.** Compared with `e3b79bae1476e549d49b29dcf361e7da50fe67db`, nothing changes under
   `src/main/java/workshop/vfx/math/core`, `src/main/java/workshop/vfx/math/time`, their tests, their golden and
   review data, their tools, M1 and M2 docs, or `verification/` and `review/` history.
3. **Tests untouched.** The M3 tests, the reference data and the generator are byte-identical to the handoff
   (`SHA256SUMS.txt`).
4. **Production scope.** Only `src/main/java/workshop/vfx/math/curve/`: the seven public types of contract section 2,
   plus optional non-public helpers. No dependencies; `verifyNoRuntimeDependencies` passes.
5. **Java 25 run.**
   `./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test` reports **229 tests,
   229 passed, 0 failed, 0 skipped** (M1 125, M2 66, M3 38). `./gradlew check` succeeds, and there are no
   production compiler warnings.
6. **Evidence** as for M1 and M2, then Claude Code's independent review.

Before the implementation exists, the M3 tests do not compile. This is expected; it was the same for M1 and M2.
