# M3 Revision 2 + Amendment A3 — first production verification

**242 / 242 tests passed on Java 25; Gradle check successful.** M1: 125 / 125; M2: 66 / 66; M3: 51 / 51. No failures, errors or skips. This is the first verified production implementation and delivery. M3 final acceptance remains pending the owner's decision after Claude's independent adversarial review. No main merge, history rewrite, force push, plugin changes or M4 implementation was performed.

## 1. Authorization and original input

The owner's exact pasted authorization is preserved in `input/OWNER-AUTHORIZATION.txt`. It accepts M2 evidence `e3b79bae1476e549d49b29dcf361e7da50fe67db`, approves M3 Revision 2 with Amendment A3 and all DM3-1 through DM3-14, and authorizes this implementation. The supplied documents' historical proposal/not-approved wording is preserved unchanged; the explicit user authorization governs this work.

Original archive: `input/minecraft-vfx-math-M3-r2-amendment-a3.zip`, **4430288 bytes**.
SHA-256: **`3317d69abf0a394ded1383733ac4340b97c2e57a15f25b485f2f36f790d909db`**.
The hash exactly matched the supplied expectation. ZIP CRC validation passed. All **49 / 49 internal SHA256SUMS.txt entries** passed. No missing file or commit was reconstructed.

The bundle was verified with Git and records complete history (no prerequisites). All supplied tree files matched the recovered specification tip; package metadata, patches, bundle and duplicate README were checked in their respective roles. Evidence: `logs/input-checksums.txt`, `logs/bundle-verify.txt`, `logs/recovered-file-integrity.txt`.

## 2. Exact specification history and production commit

The three original specification commits were fetched intact from the embedded bundle:

```text
7d0ee2b97f246ba27b19111fa3b585febf0ee0c6 e3b79bae1476e549d49b29dcf361e7da50fe67db Propose M3 curves and geometry paths: decisions, contract, independent tests
37152118093df38c95bc392e966bb23b4a435dd9 7d0ee2b97f246ba27b19111fa3b585febf0ee0c6 Propose M3 revision 2: exact startTurn reduction and extrapolation-safe spiral bounds
fe5c9fdd3e8da44517573332b163ca9af9a58d1b 37152118093df38c95bc392e966bb23b4a435dd9 Amend M3 revision 2 (A3): absolute underflow terms and overflow limits
```

The first is a direct child of accepted M2 R2 evidence `e3b79bae1476e549d49b29dcf361e7da50fe67db`. An ancestry check passed. Each supplied patch was also applied to a temporary index in order, and each resulting tree exactly matched its original commit tree. Patch metadata From SHAs matched the three commits. Historical whitespace warnings were preserved without changing the patches (`logs/patch-verification.txt`).

`m3-curves-paths` was created from exact approved tip **`fe5c9fdd3e8da44517573332b163ca9af9a58d1b`**. M1 and M2 production at that tip was unchanged.

**Production implementation commit: `d76258f343e28a8acb43f3ad8ba776adcabdd575`.** Its direct parent is `fe5c9fd`. The full production diff is `production-vs-spec.diff`; summary: **8 new Java source files, 310 added lines**.

Exactly the seven specified public types were implemented:

- `Curve`
- `LineSegment`
- `QuadraticBezier`
- `CubicBezier`
- `Spiral`
- `PosedCurve`
- `Easing`

One additional package-private source type, `CurveChecks`, shares constructor/parameter validation. `Spiral` also has a private nested `SinCos` record. Neither adds public API. All additions are confined to `src/main/java/workshop/vfx/math/curve`.

A subsequent evidence-only commit stores this report and all verification artifacts. Full original ancestor SHAs are preserved in `logs/commit-history.txt`. Original specification branches (`m3-spec`, `m3-spec-r2`, `m3-spec-r2-a3`) and the implementation branch are preserved in the deliverable bundle.

## 3. Mathematical implementation

The implementation follows the approved contract:

- Lines, quadratic and cubic Beziers use M1 `Vec3.lerp` de Casteljau evaluation. Endpoints are exact; derivatives evaluate first/second difference curves. Coincident control points have zero derivatives. Every finite parameter extrapolates; non-finite parameters are rejected.
- Spiral phases are in turns. `turns * t` and `startTurn` are reduced separately before adding and reducing their fractional parts. Quadrant reduction is then applied before `StrictMath.sin` and `StrictMath.cos`. This preserves exact quarter turns, seams and large-startTurn behavior under the contract's no-overflow conditions.
- Equal radii use the exact constant-radius branch. Other radii use `(1 - t) startRadius + t endRadius`, preserving exact endpoints and the approved extrapolation-safe S bound with A3 absolute underflow allowance. No clipping or artificial underflow repair was added.
- Spiral derivatives use the analytic expressions and local orientation `(r cos theta, rise t, -r sin theta)`. Clockwise/counter-clockwise factories follow frozen M1 conventions.
- `PosedCurve` uses M1 `Pose.toWorld` for points and rotated, uniformly scaled directions for both derivatives.
- All 15 easing modes are implemented with repeated multiplication and split symmetric smoothstep/smootherstep evaluation. Inputs/outputs clamp as specified; NaN is rejected.
- No forbidden transcendental calls, fused operations, hidden clocks, randomness, mutable global state, platform dependencies or dependency on M2 time were introduced. M3 uses only M1 core and the JDK.

The independent EXACT/ROUNDED and underflow suites passed without any edits to expected values or tolerances.

## 4. Java and Gradle versions — full output

```text
java version "25.0.4.1" 2026-08-18 LTS
Java(TM) SE Runtime Environment (build 25.0.4.1+1-LTS-5)
Java HotSpot(TM) 64-Bit Server VM (build 25.0.4.1+1-LTS-5, mixed mode, sharing)
javac 25.0.4.1

------------------------------------------------------------
Gradle 9.1.0
------------------------------------------------------------

Build time:    2025-09-18 13:05:56 UTC
Revision:      e45a8dbf2470c2e2474ccc25be9f49331406a07e

Kotlin:        2.2.0
Groovy:        4.0.28
Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
Launcher JVM:  25.0.4.1 (Oracle Corporation 25.0.4.1+1-LTS-5)
Daemon JVM:    /workspace/work/toolchains/jdk-25.0.4.1 (no JDK specified, using current Java home)
OS:            Linux 6.18.44 amd64
```

The existing Oracle JDK 25.0.4.1 and Gradle 9.1.0 wrapper were used. `logs/java-toolchains.txt` records the detected JDK. Toolchain 25, release 25, UTF-8 and `-Xlint:all` remain configured as before. Java 21 was not used.

## 5. Actual build and verification results

Required commands:

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
```

- **Clean test: exit 0; 242 total / 242 passed / 0 failed / 0 errors / 0 skipped.** `BUILD SUCCESSFUL in 1m 21s`; `5 actionable tasks: 4 executed, 1 up-to-date`.
- Counts parsed from **30 JUnit XML suites**: M1 **125**, M2 **66**, M3 **51**. The M3 inventory is exactly the supplied eight test classes. Per-class results: `test-summary.csv`.
- **Check: exit 0**, `verifyNoRuntimeDependencies` executed and passed. `BUILD SUCCESSFUL in 16s`; `5 actionable tasks: 1 executed, 4 up-to-date`. The test task reused the immediately preceding clean test run.
- Production runtime classpath: **empty**, also recorded in `logs/runtime-dependencies.txt`.
- Production compiler warnings: **zero**.
- Complete console logs: `logs/clean-test.txt`, `logs/check.txt`.
- All final JUnit XML: `junit/TEST-*.xml` (30). HTML report: `tests/test/index.html`.
- Exit-code record: `logs/run-exit-codes.json`.

One initial sandboxed startup attempt failed before compilation or test discovery: Gradle's lock coordination could not determine a usable wildcard IP. Its complete log is retained as `logs/clean-test-attempt1.txt`. The session's network permission was granted, allowing Gradle socket coordination and GitHub publication. The unchanged production source then passed the first run that reached compilation and test execution. No test failure or production revision was needed.

Five existing JOML 1.10.9 metadata warnings occurred in test compilation, as in accepted M1/M2. All JOML oracle tests passed. Exact diagnostics:

```text
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Quaterniond.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

These concern ignored class-file annotation attributes; they do not report a production compiler warning, numerical difference or tolerance failure. No warning was suppressed and no dependency was modified.

Printed measurements from the combined suite:

```text
[M1 transport report] worst per-step twist: transport = 1.1102230246251568E-16 rad, naive alignY = 3.141592653589793 rad
[M1 JOML report] rotationXYZ applies X first = false, applies Z first = true
[M1 drift report] raw |norm-1| after 1,000,000 compositions = 1.6544543512964083E-12
[M1 drift report] worst |norm-1| between renormalizations (every 1000) = 6.8833827526759706E-15
[M1 review] normalizedOr worst component error vs exact: 2.220446049250313E-16
[M1 review] slerp |t|<=1e3 worst action error vs analytic: 9.056739506726039E-13
[M1 review] shortestArc offset 0.1 worst error 8.065179992641965E-15 (allowed 1.0E-9)
[M1 review] shortestArc offset 0.001 worst error 7.980693752677138E-13 (allowed 1.0E-9)
[M1 review] shortestArc offset 1.0E-6 worst error 7.624904121111344E-10 (allowed 1.0E-9)
[M1 review] shortestArc offset 1.0E-7 worst error 7.937575590433542E-9 (allowed 1.0E-8)
[M1 review] shortestArc offset 1.0E-9 worst error 7.908609860608958E-7 (allowed 1.0E-6)
```

## 6. Independent reference regeneration

```sh
python3 tools/generate_m3_reference_vectors.py /workspace/work/m3-reference-regenerated
diff -r /workspace/work/m3-reference-regenerated src/test/resources/m3/reference
```

Both commands exited **0**. All three generated CSVs and `MANIFEST.sha256` matched the supplied originals byte-for-byte. The generator uses exact rational Bernstein/polynomial values and 60-digit decimal Taylor series at exact phases, independently of the production algorithms. Originals were never overwritten.

Generated counts:

```text
{'easing.csv': 1155, 'bezier.csv': 645, 'spiral.csv': 959}
```

Total **2,759 rows**, split into **836 EXACT** and **1,923 ROUNDED**, agreeing with the approved test specification. Counts by file/class are preserved in `logs/reference-row-counts.json`. Tests compare EXACT values without tolerance and ROUNDED values against the approved input-scale bounds, including A3 upward-rounded absolute underflow terms. All comparisons passed.

SHA-256 regeneration evidence:

```text
a0e8f162ee69dc86dd6c85d599e1cddc18ae03458ff1e7fa70a53b190897107f  MANIFEST.sha256  BYTE_IDENTICAL_REGENERATION
f57f1aabda39ef2196ac275a355635e0ff6217c173914c1bbd90ed3396f2f7a1  bezier.csv  BYTE_IDENTICAL_REGENERATION
d0967102774eab4fe974b089a6e488dec28c323b279495a49f9320e050ab2e8d  easing.csv  BYTE_IDENTICAL_REGENERATION
df6f0e5966032e209ef560c84e203bcc29f21f1007dcba2b26551c60d0cab7f1  spiral.csv  BYTE_IDENTICAL_REGENERATION
```

## 7. Frozen milestone and independent-input integrity

- All **521 files tracked at accepted M2 R2 evidence `e3b79ba`** were compared byte-for-byte against that exact commit and remain unchanged (`logs/m1-m2-integrity.txt`). This includes M1/M2 production, tests, golden/review resources, generators, contracts and previous verification/history records.
- Every supplied M3 test/helper, reference resource, generator, contract and validation record matches its handoff checksum and recovered spec blob (`logs/m3-independent-input-integrity.txt`).
- The exact diff of `src/test`, `docs` and `tools` against approved spec tip `fe5c9fd` is empty (`logs/no-test-or-contract-changes.patch`). Build and wrapper files are unchanged too.
- No test, expectation, reference vector, tolerance, generator or contract was modified. No mechanical test compile fix was required.
- The author-supplied validation/mutation records under `docs/m3/validation/` describe its throwaway reference; actual production results are under `verification/m3/`.
- All pre-existing files remain present. Evidence additions use the new `verification/m3/` directory and do not replace earlier milestone records.

## 8. Remaining limitations and review boundary

No observed implementation/test disagreement, blocking specification contradiction or unexpected production change remains. This is a single conforming Java 25 runtime execution; cross-JVM reproducibility follows the approved strict-arithmetic/StrictMath design, not a separately performed multi-JVM experiment. No benchmark or new mutation/adversarial suite was run; Claude's independent review is the next step.

Approved limits remain as documented:

- Phase resolution is limited by rounding of `turns * t` and quantization of supplied doubles. Reducing startTurn separately prevents evaluation loss from its magnitude, but does not recover fractional bits absent from the input.
- Extrapolated radius cancellation has an absolute bound in S and can have unbounded relative error near zero. Subnormal results have absolute, rather than relative, underflow guarantees.
- Overflow follows IEEE-754; exactness guarantees apply only under the approved finite/no-overflow conditions.
- Ordinary de Casteljau evaluation can wobble within bounds for coincident controls, while keeping exact endpoints and zero derivatives.
- Smoothstep/smootherstep monotonicity is approximate to the specified bound. Exact monotonicity is required and tested for the power easings.
- An open user-defined Curve implementation is responsible for its own behavior. Arc length, sampling, piecewise paths and other deferred features remain outside this implementation.

## 9. Deliverables and publication

The complete source ZIP includes all tracked source and independent inputs, the original M3 ZIP, the owner authorization, full console logs, all 30 XML suites, HTML report, production diff and integrity/regeneration evidence. SDK distributions and build caches are excluded. A Git bundle preserves complete baseline, specification and implementation ancestry.

The new artifacts and SHA-256 manifest are published on a separate M3 handoff branch. A publication receipt records exact refs and byte-for-byte download validation. No previous branch is overwritten; no force push, main merge or acceptance tag is made. Remote refs before work: `logs/remote-before.txt`.

**Status: first verified M3 implementation delivered. Stop for Claude's independent adversarial review; final acceptance remains with the project owner.**
