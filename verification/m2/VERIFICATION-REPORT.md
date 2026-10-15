# M2 deterministic animation time — first implementation verification

**169 / 169 tests passed on Java 25; Gradle check passed.** M1: 125 / 125; M2: 44 / 44. No failed, errored, or skipped tests. This is the first successful implementation pass. Claude's independent adversarial review and the owner's final M2 acceptance remain pending. No merge to main or acceptance tag was made.

## Authorization, input and history

The owner explicitly authorized M2 Revision 1 and decisions DM2-1 through DM2-9. The delivered documents retain their original proposal/status wording unchanged; that historical wording does not override the owner's approval.

Original ZIP: `input/minecraft-vfx-math-M2-handoff-r1.zip`, **122114 bytes**.
SHA-256: `ce791599a7ba755a2a23f8002940808115c6dd71e99a27aa7f878f47b6ce040a`.
This exactly matched the owner-supplied expected checksum. ZIP CRC validation passed. All **25 / 25 internal manifest entries** passed both before import and after implementation; the archive has 26 files including its checksum manifest.

No `m2-spec.bundle` was attached, included in the ZIP, or found in the workspace. No M2 specification branch existed on the remote at inspection time. The owner's explicitly authorized fallback was used: create `m2-animation-time` from accepted M1 evidence commit `9e3f3acde0db4b9d0a202ba4f7f396c132843597` and incorporate every exact handoff file in one new import commit. No missing artifact was reconstructed, and no claim is made to have recovered an unavailable Claude specification commit.

History:

- Accepted M1 production: `74710d338eb8fcb48a64763fa277e1c7be475bba`.
- Accepted M1 evidence / direct branch base: `9e3f3acde0db4b9d0a202ba4f7f396c132843597` (ancestry from accepted production checked).
- Exact handoff import: `2493a753d9c6e19f29a59ca445c0e54627b94523`.
- M2 production implementation: **`1e856820e606275c019f09d6f196bcf6a5a5138d`**.
- A subsequent evidence-only commit records this report and all verification files on `m2-animation-time`.

Full ancestor SHAs and messages: `logs/commit-history.txt`. Branch status was clean after the production commit (`logs/implementation-status.txt`). Original M1 branches and their histories are preserved. Remote refs before work are recorded in `logs/remote-before.txt`.

## Implementation and exact scope

Exactly seven new production source files were added under `workshop.vfx.math.time`:
`TimeDomain`, `TimePoint`, `TickTime`, `Playback`, `RepeatMode`, `Timing`, `TimingSample` (with nested `TimingSample.Stage`).

- Domain-tagged long instants use checked subtraction, followed by the specified conversion/division.
- Tick conversion uses division by 20.0 and multiplication by 20.0, exactly as specified.
- Playback is an immutable affine segment, with explicit pause/resume, rate, seek and restart transitions. Control validation and nonrepresentable derived-value checks follow the contract.
- Timing uses the mandated subtraction, signed-zero normalization, remainder, `Math.rint`, cycle decision and progress expressions in that order.
- No `Math.fma`, expression reassociation, hidden clock reads, random generator, thread, mutable global state, dependency on M1 core, platform import or production library dependency was added.
- Helpers are private; no public API beyond the supplied contract was added.

Production-only diff against accepted M1: `production-vs-M1.diff` (seven files, 205 lines added). `implementation-and-handoff-vs-M1.patch` is the complete exact binary-capable Git diff through the production commit, including the original handoff import. `logs/implementation-and-handoff-file-changes.txt` lists those paths. Evidence files added later are separately recorded by their evidence commit and source manifest.

No existing Minecraft plugin was touched. No future milestone was implemented.

## Full Java and Gradle output

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

The existing provisioned Oracle JDK 25.0.4.1 and pinned Gradle 9.1.0 wrapper were used. Toolchain selection is also recorded in `logs/java-toolchains.txt`. The installed Java 21 JRE was not used for compilation or testing. Java toolchain 25, `options.release = 25`, `-Xlint:all`, and all build configuration remain unchanged from accepted M1.

## Build and test results

Commands:

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew check
```

- Clean test: **exit 0**, **169 total / 169 passed / 0 failed / 0 errors / 0 skipped**. `BUILD SUCCESSFUL in 26s`; `5 actionable tasks: 4 executed, 1 up-to-date`.
- M1: **125 / 125**. M2: **44 / 44**. Counts were parsed from all **20 JUnit XML suites**; `test-summary.csv` contains every class count.
- Gradle check: **exit 0**, `verifyNoRuntimeDependencies` executed and passed; `BUILD SUCCESSFUL in 1s`; `5 actionable tasks: 1 executed, 4 up-to-date`. The test task reused the immediately preceding clean run.
- Production runtime classpath is empty (`logs/runtime-dependencies.txt`).
- Production compiler warnings: **zero**.
- Full console output: `logs/clean-test.txt` and `logs/check.txt`.
- All final JUnit XML: `junit/TEST-*.xml` (20 files). HTML report: `tests/test/index.html`.
- Command exit codes: `logs/run-exit-codes.json`.

The complete supplied suite passed on the first implementation run. No failing assertion, compile repair, or implementation revision was needed. No additional adversarial test round was started; that is reserved for Claude's independent review.

Test compilation emitted the same five external JOML 1.10.9 annotation/class-version warnings seen in accepted M1. All JOML oracle tests passed. Exact diagnostics:

```text
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Quaterniond.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

The warnings concern ignored class-file annotation metadata, not numerical test-oracle differences, tolerance failures, or production compiler issues. No warning was suppressed and no dependency was modified.

Existing printed numerical measurements:

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

## Independent golden regeneration

The delivered generator was executed independently of the Java implementation into a fresh scratch directory; the originals were never overwritten:

```sh
python3 tools/generate_m2_golden_vectors.py /workspace/work/m2-golden-regenerated
diff -r /workspace/work/m2-golden-regenerated src/test/resources/m2/golden
```

Both commands exited **0**. The independent Python generator's exact-rational and invariant checks passed. Every generated file, including `MANIFEST.sha256`, matched its supplied original byte-for-byte.

```text
{'tick_seconds.csv': 217, 'timepoint.csv': 171, 'timing.csv': 1692, 'playback.csv': 1096}
```

Total CSV rows: **3,176**. SHA-256 evidence:

```text
e8e28ab2267ef30757215df1f1675caf1bdb7422163391a01121b74c63eae7e6  MANIFEST.sha256  BYTE_IDENTICAL_REGENERATION
98ed9d39d8aab0870949222e3b40976f347d5dade2e69e1d375b5cbb575be847  playback.csv  BYTE_IDENTICAL_REGENERATION
e4bbba464ca6396cf637af2d82d2e86722217a13d8a674443c99b7a9c0c9d59c  tick_seconds.csv  BYTE_IDENTICAL_REGENERATION
783f41b7658e01d52c95a0e661993248a8eed342691933c36bec02acfc250633  timepoint.csv  BYTE_IDENTICAL_REGENERATION
eb8e95ad78179b7f77937eef538124443a0733cf96344cb6af44cf6f4f0205c7  timing.csv  BYTE_IDENTICAL_REGENERATION
```

## Integrity and preservation of expectations

- All **220 files tracked by accepted M1 evidence commit `9e3f3ac`** were compared byte-for-byte against that commit, including production code, every original and review test, M1 golden data, generator, documents and historical evidence. All are unchanged. Hash evidence: `logs/m1-integrity.txt`.
- All **26 M2 handoff files** are byte-identical to the ZIP, including the 8 test/helper Java files, 4 golden CSVs, manifest, generator, documents and Claude's reference validation records. Hash evidence: `logs/m2-handoff-integrity.txt`.
- The original SHA256SUMS.txt continues to verify all 25 entries at the final source root (`logs/final-handoff-checksums.txt`).
- No mathematical expected value, tolerance, golden vector, test helper or independent test source was edited. No mechanical test-compile fixes were required.
- Claude's shipped `validation/` logs describe its reference implementation; actual results for this implementation are under `verification/m2/`.

## Findings, unresolved issues and limits

No implementation/test disagreement or blocking specification contradiction was encountered. The deterministic operation sequence was preserved, and all 3,176 golden rows matched bitwise. The integer and long-running checks in the supplied tests passed. Passing this first suite is evidence for the contract, not a substitute for independent adversarial review.

The only input deviation is the absent optional specification bundle, handled using the owner's explicit fallback. The documents' proposal headers were preserved, with authorization recorded above. Known numerical bounds and deferred features remain exactly as documented in `docs/m2/M2-RISKS-AND-DEFERRED.md`; no attempt was made to expand them.

## Deliverables and review boundary

The complete source ZIP includes all tracked source, the exact M2 handoff, this original input ZIP, all actual build logs, all 20 JUnit XML reports, the HTML report, integrity records, and diffs. The Git bundle preserves accepted M1 and M2 history. SDK/Gradle binary distributions and caches are excluded.

A separate handoff branch publishes exact artifacts, their SHA-256 manifest, and a publication receipt after download verification. Conversation attachments remain unavailable in this cloud environment, so verified GitHub download URLs are provided. No force push, main merge, or final-acceptance tag is part of this work.

**Status: first successful M2 implementation and automated verification complete. Stop for Claude's independent review. Final M2 acceptance remains with the owner.**
