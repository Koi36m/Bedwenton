# M5 production implementation: verification report

Status: **implemented and locally verified; awaiting independent ChatGPT production review and owner acceptance.**
This report does not declare M5 accepted.

## 1. Identities

| Item | Value |
|---|---|
| Accepted M4 GitHub verification snapshot | `e3bf5578c126a7d3498be0b30fb431d9ec9a3099` |
| M5 design kickoff | `6ada39e3a383f6b2df60e6744a39d37d9f24eb13` |
| Approved M5 R1 specification (unpublished; recovered from `m5-spec.bundle`) | `ac3e05b7f82d59128678737cba56ce5cac481867` |
| Implementation branch | `m5-implementation`, based on `ac3e05b` |
| Implementation commit | recorded in the delivery notes; a report cannot contain its own commit SHA |

**Recovery checks (phase 1):**
- the proposal ZIP SHA-256 `46a756f6...0502c358`, ZIP integrity, and all 35 listed package checksums;
- the bundle SHA-256 `ba2b337f...cd26ae`, its completeness (no prerequisites), and `git fsck`;
- `ac3e05b` descends from both `6ada39e` and `e3bf557`.

## 2. Inputs that were not available

**`M5-R1-Binding-Implementation-Addendum.md` and `M5-R1-Independent-Acceptance-Review.md` were not present in this
environment.** They were not attached and are not on any branch. The addendum therefore could **not** be copied
byte-identically to `docs/m5/M5-R1-BINDING-ADDENDUM.md`, and no substitute was written.

The five corrections were implemented as the authorization message states them:

| # | Correction (as stated in the authorization) | Implementation |
|---|---|---|
| 1 | `StyleKey.toString()` returns its raw value | `StyleKey.toString()` returns `value` (checked by `SceneValidationTest.styleKeys`) |
| 2 | Public record constructors validate their inputs | `StyleKey`, `Layer` and `SceneLimits` validate in their compact constructors; the `of` factories delegate to them |
| 3 | Invalid `FrameBatch` indices throw `IndexOutOfBoundsException` | `x`, `y`, `z` and `point` check `0 <= i < size()` first (`FrameContractTest.immutability`) |
| 4 | Prefix reveal is exactly `(int) Math.floor(progress * n)` | `EffectScene.evaluate`, with no other branch |
| 5 | Validate large allocation dimensions before allocating proportional output; never return partial frames | see below |

How correction 5 is met:
- `Shape.points` checks `size <= MAX_POINTS` before allocating.
- `EffectScene.of` checks `layers.size() <= maxLayers` before copying the list, and re-checks the copy.
- The point preflight (sum of sizes, in `long`) bounds every frame's output.
- `evaluate` allocates one `double[3k]` per batch, with k at most the shape size. It throws before returning if any
  position is not finite, so no partial frame can escape.

If the addendum text differs from this summary, the differences must be reconciled before acceptance.

## 3. Scope of the change

- **11 new files**, all in `src/main/java/workshop/vfx/scene/`: the 10 approved public types (`StyleKey`, `Shape`,
  `Motion`, `Visibility`, `Reveal`, `Layer`, `SceneLimits`, `EffectScene`, `EffectFrame`, `FrameBatch`), plus one
  package-private validation helper (`SceneChecks`, which holds the identifier patterns and null checks shared by
  five types).
- **Verification additions:** `verification/m5/` (this report, evidence and checksum lists) and
  `.github/workflows/m5-java25-verification.yml`.
- **Unchanged:** no build, Gradle or dependency changes; no existing workflow changed.
- **Frozen files** (`FROZEN-M1-M4-SHA256SUMS.txt`): all **735** tracked files of `e3bf557` are byte-identical.
- **Approved specification** (`APPROVED-M5-SHA256SUMS.txt`): all **30** files added by `ac3e05b` are byte-identical.
  This covers the docs, the 50 tests, the fixtures and the generator.
- **Production diff:** `production.diff`, 11 added files and nothing else under `src/main`.

## 4. Build and tests (Java 25.0.4.1, Gradle 9.1.0; `logs/`)

- `./gradlew --no-daemon clean test`: **363 tests, 363 passed**: 0 failures, 0 errors, 0 skipped, in 50 JUnit XML
  reports (`junit/`).

  | Milestone | Tests |
  |---|---|
  | M1 | 125 |
  | M2 | 66 |
  | M3 including review | 63 |
  | M4 | 59 |
  | M5 | 50 |
  | **Total** | **363** |

- `./gradlew --no-daemon check`: success, including `verifyNoRuntimeDependencies`.
- **Runtime classpath:** no dependencies (`logs/runtime-dependencies.txt`).
- **Compiler warnings** (`-Xlint:all`): production (`compileJava`) has **0**. Test compilation has 5 class-file
  notices from the JOML test-only jar, identical at the accepted baseline `e3bf557`.
- **API surface:** exactly the 10 approved public types and members (`M5BoundaryTest.apiShape`). There are no
  Bukkit, Paper, Minecraft or forbidden-call references (`M5BoundaryTest.imports`, `.forbidden`).

## 5. Reference regeneration (`reference-regeneration.txt`)

All generators were run unchanged into scratch directories and compared with `diff -r`:

| Milestone | Result | Rows |
|---|---|---|
| M3 | byte-identical | 2,759 |
| M4 | byte-identical | 3,064 |
| M5 | byte-identical | 1,113 |

All manifests verify.

## 6. Determinism, conformance and limits

- **Determinism:** `M5DeterminismTest` checks bit-identical frames over 25 repeated rounds and across 8 threads.
- **Conformance** (`ConformanceProbe.java`, `conformance.txt`): the production code and the independently written
  R1 reference implementation give bit-identical frames, with the same SHA-256 over the raw bits of
  **32,004 frames** (255,747 points). The frames cover the four example scenes at 2,001 instants each and 3,000
  random scenes. This confirms that production follows the normative operation order exactly.
- **Limits:**
  - `BudgetTest`: 4,096 x 256 points accepted, one more point per layer rejected at construction, a frame of 2^20
    points evaluated;
  - **0 curve calls** during evaluation over 1,000 frames;
  - the 16-step motion limit.
- **Performance** (`performance.txt`, indicative): about 1.0 to 1.5 µs per frame for the example scenes; 0.60 ms and
  25 bytes per point for a 65,536-point scene.

## 7. GitHub Actions

`.github/workflows/m5-java25-verification.yml` is included. It uses checkout v5, setup-java v5 and upload-artifact
v6, and runs on `m5-implementation` and `m5-actions-verification`. It verifies:
- the checksum lists and the three manifests;
- clean test and `check`;
- an empty runtime classpath;
- exact counts (363, in 50 reports);
- 0 production warnings;
- the API surface and the absence of platform imports.

With `workflow_dispatch` or `[m5-release-verification]`, it also regenerates and byte-compares M3, M4 and M5.

**It has not run.** GitHub refused the push of this branch with HTTP 403: there is no GitHub access for this repository
from this environment. There is therefore **no GitHub Actions run URL**, and no published snapshot. The only commit
identities are the local ones, preserved in the delivered bundle.

## 8. Known limitations (unchanged from the approved specification)

- Positions are validated within `2^-40 (1 + M)(1 + abs(turns))` of independent oracles; this is not a proven tight
  bound.
- The spin precision degrades linearly with `abs(turns)` per pass.
- The sign of zero coordinates is not significant.
- M2 `FOREVER` loops end after about 9.2e18 passes.
- No Paper, Bukkit or Folia code was written or run; there is no claim of platform compatibility.
