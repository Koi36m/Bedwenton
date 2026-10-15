# M2 Revision 2 — implementation and verification report

**Final result: 191 / 191 tests passed, Gradle check passed, Java 25 verified.** This is implementation and verification evidence, not final M2 acceptance. Final acceptance remains pending the owner's review. No merge into main, force push, plugin modification, or M3 work was performed.

## 1. Exact delivery and review recovery

All three original attachments are preserved in `input/`:

- `minecraft-vfx-math-M2-r2-amendment.zip`: 487174 bytes, SHA-256 `819d7796d9243472571112fc8241a5a7534fe522ec96cd9a5885f0d9eb6cc282` (matches the owner's expected hash and delivery manifest).
- `m2-r2-amendment.bundle`: 96894 bytes, SHA-256 `6d8ca5341ecb5e6fb416a06663297cce2331be3c015a1e61313f01d09042edc3` (matches delivery manifest).
- `DELIVERY-SHA256SUMS.txt`: exact original manifest, SHA-256 `c36dfd13ccfd3f20622101e5cc7959446ac66c621522088deff5f1ea06ab9c00`.

Both delivery entries passed, ZIP CRC validation passed, and all **118 / 118 internal checksum entries** passed. Evidence: `logs/delivery-checksums.txt`, `logs/input-checksums.txt`. All supplied tree files matched the recovered Git tree; package-only metadata, patches, embedded bundle, and the duplicate README were verified in their respective roles. The embedded and separately attached bundles are byte-identical. No file or existing commit was reconstructed.

`git bundle verify` passed with prerequisite `80927177469d89b985d3b57a3219652a994d6d4b`. Exact recovered branch tip: **`88702655a3f8c0428160a74f9193af7cc41b0b87`**. An ancestry check confirmed descent from M2 Revision 1 evidence. Its four original commits were fetched intact:

```text
a9d86c3e52e091c08dc359233999d08efa1394f5 80927177469d89b985d3b57a3219652a994d6d4b Add independent M2 adversarial review: report, tests and evidence
3cd62a65778ad26e5e417a7965db50900bf8192b a9d86c3e52e091c08dc359233999d08efa1394f5 Approve M2 contract revision 2: P-A1, P-A2, P-A3 wording and P-R1 COSMETIC wrap-around
70cecac95c2b5c9494c7ae58c794d8c3e85c631f 3cd62a65778ad26e5e417a7965db50900bf8192b Revise M2 tests for revision 2: one assertion, one golden row, regression tests
88702655a3f8c0428160a74f9193af7cc41b0b87 70cecac95c2b5c9494c7ae58c794d8c3e85c631f Record M2 revision 2 review evidence and historical findings
```

Each supplied patch was separately applied to a temporary index initialized at `8092717`; its resulting tree exactly matched the corresponding original commit, and its From SHA matched that commit. Patch whitespace warnings in historical evidence were retained without repair. Evidence: `logs/patch-verification.txt`. Production code at the recovered amendment tip was identical to Revision 1.

Branch **`m2-r2-verification`** was created directly from `88702655`, as requested. The original amendment branch was published as `m2-r2-amendment`, without force-pushing; `logs/push-amendment.txt` records the successful result.

## 2. Before-fix reference execution

On the exact amendment tip, before any production changes:

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
```

**Exit 1; 191 total / 185 passed / exactly 6 failed / 0 errors / 0 skipped.** There were no unexpected failures. Full console log: `logs/before-fix-clean-test.txt`; all **22 JUnit XML suites**: `before-fix-junit/`.

The failed methods were exactly those authorized by the amendment:

- `TimePointTest.invalidUse`
- `M2GoldenVectorTest.timePoints`
- `M2Revision2Test.cosmeticDifferenceAcrossTheWrap`
- `M2Revision2Test.intervalRestriction`
- `M2Revision2Test.cosmeticPlaybackAcrossTheWrap`
- `M2Revision2Test.translationAcrossTheWrap`

Method identities were confirmed from failure stack traces; exact JUnit display names and counts are in `logs/before-fix-summary.json`.

## 3. Authorized production corrections

Production implementation commit: **`15f6564b2580d3722b625c9be96e57f2580c81a3`**.
Its parent is the exact recovered amendment tip `88702655a3f8c0428160a74f9193af7cc41b0b87`.

The complete production diff against Revision 1 implementation `1e856820e606275c019f09d6f196bcf6a5a5138d` is `production-vs-r1.diff`: exactly two files, 7 lines added and 2 removed.

- **R1-A / `TimePoint.ticksSince`:** domain/null validation stays first. COSMETIC returns `(double) (value - origin.value) / 50_000_000.0` using wrapping signed-long subtraction. GAME retains the existing `Math.subtractExact`, exception handling/message, and cast result.
- **R1-B / `Playback.validateControl`:** domain validation stays first. COSMETIC uses wrapping `at.value() - anchor.value() < 0`; GAME retains ordinary `at.value() < anchor.value()`. The rejection message is unchanged.

No other production file changed. Evaluation expressions, public APIs, domains, no-op identity behavior, pause/resume semantics, timing calculations, re-anchoring arithmetic and all M1 behavior remain unchanged. No attempt was made to alter code to meet the retired Revision 1 error bound or redesign FOREVER.

## 4. Java and Gradle versions (complete output)

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

The existing Oracle JDK 25.0.4.1 and Gradle 9.1.0 wrapper were used. Toolchain selection is additionally recorded in `logs/java-toolchains.txt`. Toolchain/release 25 and `-Xlint:all` remain configured. Java 21 was not used for compilation or tests.

## 5. Final verification

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew check
```

- **Clean test: exit 0; 191 / 191 passed; 0 failed, 0 errors, 0 skipped.** `BUILD SUCCESSFUL in 26s`; `5 actionable tasks: 5 executed`.
- Split: accepted M1 **125/125**, original M2 suite with its approved R2 assertion/data updates **44/44**, permanent adversarial tests **13/13**, R2 regression tests **9/9**.
- **Check: exit 0**, `verifyNoRuntimeDependencies` executed and passed. `BUILD SUCCESSFUL in 1s`; `5 actionable tasks: 1 executed, 4 up-to-date`. The test task reused the immediately preceding successful clean run.
- Production runtime classpath is **empty**, also recorded in `logs/runtime-dependencies.txt`.
- Production compiler warnings: **zero**. No test compilation fixes or assertion edits were needed.
- All final **22 JUnit XML suites** are in `junit/`; per-class counts are in `test-summary.csv`. HTML report: `tests/test/index.html`.
- Full final console logs: `logs/clean-test.txt`, `logs/check.txt`. Command exit codes: `logs/run-exit-codes.json`.

All six before-fix failures now pass; the exact before/after mapping is preserved:

```text
M2GoldenVectorTest.timePoints  BEFORE: FAIL  AFTER: PASS
M2Revision2Test.cosmeticPlaybackAcrossTheWrap  BEFORE: FAIL  AFTER: PASS
M2Revision2Test.translationAcrossTheWrap  BEFORE: FAIL  AFTER: PASS
M2Revision2Test.intervalRestriction  BEFORE: FAIL  AFTER: PASS
M2Revision2Test.cosmeticDifferenceAcrossTheWrap  BEFORE: FAIL  AFTER: PASS
TimePointTest.invalidUse  BEFORE: FAIL  AFTER: PASS
```

This directly confirms that the two approved corrections address the observed failures without changing their expectations.

The five pre-existing JOML 1.10.9 class-file annotation warnings remain confined to test compilation. They do not report numerical differences or implementation issues; all four JOML oracle tests passed. Exact warnings:

```text
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Quaterniond.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

Existing printed measurements:

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

## 6. Golden/reference regeneration

The approved R2 generators were run to fresh scratch directories. Supplied originals were not overwritten:

```sh
python3 tools/generate_m2_golden_vectors.py /workspace/work/m2-r2-golden-regenerated
diff -r /workspace/work/m2-r2-golden-regenerated src/test/resources/m2/golden
python3 review/m2/generate_boundary_vectors.py /workspace/work/m2-r2-boundary-regenerated
diff -r /workspace/work/m2-r2-boundary-regenerated src/test/resources/m2/review
```

All commands exited **0**. All **7 generated files** (four golden CSVs and their manifest; one boundary CSV and its manifest) matched byte-for-byte. Golden CSV rows: **3,176**; boundary rows: **2,235**. Logs: `logs/golden-regeneration.txt`, `logs/boundary-regeneration.txt`; hashes: `logs/golden-integrity.txt`.

## 7. Independent test and M1 integrity

- Every approved R2 test, resource, document, generator and historical review record remains identical to the supplied package and recovered amendment tip. Exact diff against `88702655` is empty (`logs/no-independent-input-changes.patch`).
- The only R1 expectation/data changes are those already present in Claude's exact review commits: the one `TimePointTest` assertion, one `timepoint.csv` row and its generator/manifest changes. This implementation made no additional expectation, tolerance, test-count, golden or generator changes.
- All **220 files tracked at accepted M1 evidence `9e3f3ac`** remain byte-identical; hash evidence is `logs/m1-integrity.txt`. This includes all M1 source, tests, golden vectors, documents and evidence.
- Retired findings tests and their historical evidence remain exactly where Claude's amendment preserved them (`review/m2/HISTORICAL-FINDINGS.md`, `review/m2/historical/` and Git history).
- Revision 1 source, expectations, generator, data and evidence remain preserved in original commits and archives. The older root `SHA256SUMS.txt` belongs to the R1 handoff; the R2 package's internal manifest and the new deliverable manifest are the current integrity evidence. Historical manifests were not rewritten.

## 8. Remaining risks and limits

No unexpected production behavior or blocking contract/test discrepancy was observed. Both required clean runs were executed, and the observed before-fix failures match the approved amendment exactly.

The approved limitations remain:

- COSMETIC elapsed-time semantics require true intervals strictly below 2^63 nanoseconds in magnitude. Larger intervals alias modulo 2^64 and cannot be detected by this API; a wrapped difference of -2^63 is interpreted as backwards.
- Control ordering is against the stored anchor, not a remembered history; no-op controls keep that anchor and return the same object.
- Re-anchoring error bounds are those approved separately for GAME and COSMETIC. Arithmetic was not changed to satisfy the old R1 wording.
- FOREVER still reports AFTER when its representable cycle index reaches 2^63, as documented; this is outside the guaranteed cycle-exact domain.

Passing 191 tests and the generators is evidence for the approved contract, not a declaration of exhaustive mathematical correctness or owner acceptance. No extra adversarial round or future milestone work was started.

## 9. Deliverables and references

The production commit is followed by an evidence-only commit on `m2-r2-verification`. A complete source ZIP and Git bundle preserve exact accepted M1, M2 R1, the four original amendment commits, and the new implementation/evidence ancestry. The source includes the original three attachments, full before/after logs and XML, report, production diff, integrity checks, and all historical findings. SDK binaries and build caches are excluded.

Artifacts and their SHA-256 manifest are published on a separate handoff branch, with a publication receipt after byte-for-byte download verification. Prior branches remain preserved; no force push, main merge or acceptance tag was made. Original remote refs before work: `logs/remote-before.txt`.

**Status: M2 R2 implementation and verification complete; final acceptance pending the owner's review. Stop here.**
