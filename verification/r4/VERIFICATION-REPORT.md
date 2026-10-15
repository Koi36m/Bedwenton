# M1 Revision 4 verification evidence

**Result: 125 tests passed; Gradle check passed on Java 25.** This report records verification, not the owner's final acceptance. No M2 work, new features, plugin changes, test expectation changes, or unrelated production changes were made.

## Input verification and exact history recovery

Input archive: `input/m1-review-r4.zip`, 202261 bytes.
SHA-256: `439a2561c2f942d907411ad08ab5bc16b6a22f4a9fa657f5e4a637990b4cd2f7`.
The outer archive hash was computed; no separately supplied expected outer hash was available. ZIP CRC validation passed. All **11/11 entries** in its internal `SHA256SUMS.txt` passed. No missing file was reconstructed.

The bundled branch requires checkpoint `8397c04b55f7d506e8fec79fca120ce297e8ae22` and ends at `802d9e7f95ccf8fdfd3879d99313794faa2c08d1`. `git bundle verify` passed. An ancestry check passed. Exactly four original review commits were recovered by fetching the bundle, without applying patches to recreate commits or changing original SHAs:

```text
da95a353b68002fa8e81606967f5740d74253a9b 8397c04b55f7d506e8fec79fca120ce297e8ae22 Add independent M1 review: adversarial tests, findings and evidence
09866629c59e3b322a409c44f7513fde1b714a85 da95a353b68002fa8e81606967f5740d74253a9b Propose Amendment 3 (revision 4) and acceptance tests for M1 review decisions
f9eefd69ae26652af90a96bf2bb8e73764933f2d 09866629c59e3b322a409c44f7513fde1b714a85 Approve Amendment 3 with clarified hint processing order
802d9e7f95ccf8fdfd3879d99313794faa2c08d1 f9eefd69ae26652af90a96bf2bb8e73764933f2d Apply Revision 4 [R4] text to the implementation contract
```

Each of the four supplied format-patches was independently applied in order to a temporary index initialized at `8397c04`. Every applied tree matched its corresponding original bundle commit tree exactly, and each patch's From SHA matched that commit. Evidence: `logs/patch-verification.txt`. Delivered log text in the patches contains trailing whitespace; Git warned about it. It was preserved, and all resulting tree IDs matched.

The standalone Revision 3 contract matches the original historical blob, and the standalone Revision 4 contract, approved amendment and review report match the recovered branch blobs. Revision 3 remains in Git history and the original archive. Current `docs/IMPLEMENTATION-CONTRACT.md` is the exact delivered Revision 4 document.

`review/m1-claude` was successfully published without force-pushing. Original `main`, `m1-core-transforms`, and `baseline-scaffold` remain unchanged. Push evidence: `logs/push-review.txt`; observed initial remote refs: `logs/remote-before.txt`.

## Production implementation

Final production implementation commit: **`74710d338eb8fcb48a64763fa277e1c7be475bba`**.
Parent: `802d9e7f95ccf8fdfd3879d99313794faa2c08d1`.
Branch: `m1-r4-verification`.

Only these approved corrections were made:

- D1: `Pose.checkResult` checks scale before translation. Both `then` and `inverse` share this check. Scale failures identify `scale`; translation-only failures identify `translation`.
- A3: `Quat.axisAngle` handles a null axis through the existing invalid-input path, throwing `IllegalArgumentException`.

Exact production diff against original implementation `05c27067f3e9e2480228d0d34e5a2bd64558c889` is `production.diff`. It contains exactly two insertions and two deletions in `Pose.java` and `Quat.java`. `Frame`, `MathUtil`, and `Vec3`, all build configuration, and all tests remain unchanged from the recovered review branch.

## Toolchain, full recorded output

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

`logs/java-toolchains.txt` confirms the selected Oracle Java 25 JDK. The Java 21 JRE present in the image was not used. Gradle wrapper remains 9.1.0 with its original pinned checksum. The build specifies toolchain and release 25. JUnit Jupiter is 5.12.2 and JOML is 1.10.9 (MIT, test-only), unchanged from the original build.

## Before-fix reference run

Command, at recovered review head with unchanged original production code:

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
```

Exit **1**, **125 total / 122 passed / 3 failed / 0 errors / 0 skipped**. Exactly the expected failures occurred:

- `d1InverseScaleFailureNamesScale`
- `d1ThenScaleFailureNamesScale`
- `a3NullAxisIsIllegalArgument`

The XML uses JUnit display names. `logs/before-fixes-summary.json` records those exact names, and `before-fixes-junit/` preserves all 13 XML reports. This was an expected reference failure, not a final verification failure. Complete console log: `logs/before-fixes-clean-test.txt`.

## Final clean test and check

Commands:

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew check
```

- Clean test: **exit 0; 125 total / 125 passed / 0 failed / 0 errors / 0 skipped**. `BUILD SUCCESSFUL in 24s`; `5 actionable tasks: 5 executed`.
- Split: original tests **97/97**, `ReviewAdversarialTest` **18/18**, `Amendment3AcceptanceTest` **10/10**. `runsOnJava25` passed.
- Check: **exit 0**; `verifyNoRuntimeDependencies` executed and passed. `BUILD SUCCESSFUL in 1s`; `5 actionable tasks: 1 executed, 4 up-to-date`. Its test task reused the immediately preceding successful clean execution.
- Production runtime classpath: **empty**, independently recorded at `logs/runtime-dependencies.txt`.
- Final failures, skipped tests, test-compile repairs, assertion changes: **none**.
- Final full console logs: `logs/clean-test.txt`, `logs/check.txt`.
- Final JUnit XML: **13 files** in `junit/`. Per-class counts: `test-summary.csv`. HTML: `tests/test/index.html`.
- Before/after exit codes: `logs/run-exit-codes.json`.

## Compiler diagnostics and numerical measurements

Production compiler warnings: **0**. Test compilation produced the same **5** JOML class-file metadata warnings as the initial pass; no warnings were suppressed. Exact diagnostics:

```text
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Quaterniond.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

These warnings concern an annotation attribute in JOML's class files and do not report an oracle numerical difference, precision-tolerance failure, or production defect. All four JOML oracle tests passed.

Printed measurements, verbatim:

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

## Original and review test integrity

- All **13 original Java test/helper files** are byte-identical to baseline commit `77a1430373d86c5025c7aecc49081b9e5f4f3b49`.
- All **19 original Java/golden resource files** also match the original Revision 3 ZIP byte-for-byte.
- `git diff 77a1430 HEAD -- src/test/resources tools` is empty.
- Both review test classes are byte-identical to `802d9e7f` (18 adversarial and 10 acceptance tests).
- `git diff 802d9e7f HEAD -- src/test` is empty; the exact empty diff is saved as `logs/test-changes-from-review.patch`.
- No original expected value, golden vector, tolerance, or helper changed. No supplied file was reconstructed.

Hash evidence and assertions: `logs/test-integrity.txt`. No change to any test was required.

## References and deliverables

Original baseline and history are retained. `m1-core-transforms` stays at `8397c04`; `review/m1-claude` stays at `802d9e7f`. The new verification branch descends directly from the four review commits. No merge, force push or acceptance tag was made.

This evidence is committed after the production-only commit. A source ZIP and Git bundle, final report, production diff, and deliverable SHA-256 manifest are published on a separate `m1-r4-handoff` branch, preserving the verification branch's source and test contents. A publication receipt records exact remote refs and download validation.

The source ZIP includes all tracked source, original review files, R3 input archive, R4 input archive, and this evidence. JDK/Gradle binary distributions and caches are excluded. Original artifacts from the earlier handoff remain intact.

**Status: M1 Revision 4 automated verification complete. Owner acceptance pending. Stop here; do not begin M2.**
