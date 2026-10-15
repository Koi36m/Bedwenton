# M1 Revision 3 — first implementation verification report

Automated first-pass verification passed: **97 / 97 tests**, zero failures, errors or skips.
This is not final M1 acceptance. Claude's independent mathematical review and adversarial-test rerun are pending.

## 1. Environment

Linux x86_64, kernel 6.18.44; Intel Xeon Platinum 8573C, 3 visible CPUs.
Full OS/CPU output: `logs/os-cpu.txt`.

Actual Java output (`logs/toolchain.txt`):

```text
java version "25.0.4.1" 2026-08-18 LTS
Java(TM) SE Runtime Environment (build 25.0.4.1+1-LTS-5)
Java HotSpot(TM) 64-Bit Server VM (build 25.0.4.1+1-LTS-5, mixed mode, sharing)
javac 25.0.4.1
```

Actual wrapper output (`logs/gradlew-version.txt`):

```text
Gradle 9.1.0
Build time:    2025-09-18 13:05:56 UTC
Revision:      e45a8dbf2470c2e2474ccc25be9f49331406a07e
Kotlin:        2.2.0
Groovy:        4.0.28
Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
Launcher JVM:  25.0.4.1 (Oracle Corporation 25.0.4.1+1-LTS-5)
Daemon JVM:    /workspace/work/toolchains/jdk-25.0.4.1 (no JDK specified, using current Java home)
OS:            Linux 6.18.44 amd64
```

- `logs/java-toolchains.txt` confirms Oracle JDK 25 at that path (`Is JDK: true`). The installed Debian Java 21 is a JRE only and was not used for compilation or testing.
- Production classes have class-file major version 69 (Java 25). Build specifies both Java toolchain 25 and `options.release = 25`.
- JUnit Jupiter: **5.12.2**; JUnit Platform: **1.12.2**, resolved dependency report in `logs/test-dependencies.txt`.
- JOML: **1.10.9**, latest stable release in Maven Central metadata at provisioning time; **MIT License**, checked in the resolved artifact's POM, preserved at `logs/joml-1.10.9.pom`. The binary JAR has no license entry; license verification used its published POM. Test scope only.
- Revision 3 package integrity: **29 / 29 manifest entries OK** (`logs/archive-checksums.txt`). Golden manifest: **5 / 5 OK** (`logs/golden-checksums.txt`). Original archive is preserved in `input/`.
- Original ZIP SHA-256: `0f79530734458da8a3fd0fc0ab49c836fed0dbb70d815b1813e3e7b56180ed61`.
- JDK and Gradle downloads matched vendor-published SHA-256 values (`logs/toolchain-download-checksums.txt`). Gradle wrapper also pins the distribution checksum.

## 2. Repository

- Standalone repository: `/workspace/work/minecraft-vfx-math`, name `minecraft-vfx-math`.
- Main scaffold commit / `baseline-scaffold`: `870c9fd`.
- Gate A: **yes**, `./gradlew clean test` succeeded on Java 25 with no tests, `logs/gate-a.txt` (exit 0).
- Tests-as-delivered commit: `77a1430`, message `tests as delivered (unmodified)`.
- Implementation commit: `05c2706`, message `Implement M1 core transforms against revision 3`.
- Branch: `m1-core-transforms`; `main` remains at scaffold. No merge or `m1-verified` tag was made.
- No existing plugin or other repository modified: **yes**. Only this new source repository and task scratch/output files were written. No later milestones implemented.
- A subsequent verification-evidence commit is included in the Git bundle. Use `git log` after cloning to obtain its full ID.

## 3. Build and test results

`./gradlew clean test`: **exit 0**, **97 total / 97 passed / 0 failed / 0 errors / 0 skipped**.
The first implementation run succeeded; no assertion failure or compilation failure occurred.
Complete output: `logs/initial-clean-test.txt`; per-class counts: `test-summary.csv`.

```text
BUILD SUCCESSFUL in 8s
5 actionable tasks: 5 executed
```

`./gradlew check`: **exit 0**, `verifyNoRuntimeDependencies` executed and passed.
`test` was up-to-date from the successful clean test run. Complete output: `logs/check.txt`.

```text
> Task :verifyNoRuntimeDependencies
> Task :check
BUILD SUCCESSFUL in 995ms
5 actionable tasks: 1 executed, 4 up-to-date
```

`runtimeClasspath` is empty, also confirmed by `logs/runtime-dependencies.txt`.

Compiler warnings with `-Xlint:all`: **0 production warnings; 5 test-compilation warnings** originating in the unmodified JOML 1.10.9 binary. Each says:

```text
warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

Affected classes: `Quaterniond`, `Vector3d`, `Vector4f`, `Vector3f`, `Vector4d`.
No warning suppression or third-party bytecode modification was applied. Full paths and diagnostics are in the test build log.

Printed measurements, verbatim:

```text
[M1 transport report] worst per-step twist: transport = 1.1102230246251568E-16 rad, naive alignY = 3.141592653589793 rad
[M1 JOML report] rotationXYZ applies X first = false, applies Z first = true
[M1 drift report] raw |norm-1| after 1,000,000 compositions = 1.6544543512964083E-12
[M1 drift report] worst |norm-1| between renormalizations (every 1000) = 6.8833827526759706E-15
```

JUnit XML: `verification/junit/TEST-*.xml` (11 suites). HTML report: `verification/tests/test/index.html`.
The raw million-composition drift is a measurement, not a failed assertion; periodic normalization passed its bound.

## 4. Changes to delivered tests

**None.** No mechanical test-compile fixes were required. Every delivered Java test/helper and golden resource is byte-identical to Revision 3. Hash comparison evidence: `logs/test-integrity.txt`. All supplied expected values and tolerances were preserved. Golden data was not regenerated.

## 4A. Numerical policy confirmation

- `Pose.then` and `Pose.inverse` check representability before construction and throw `ArithmeticException` for nonrepresentable scale/translation: **yes**, `NumericPolicyTest` passed.
- No unsolicited clamping, saturation, or NaN sanitizing in production arithmetic: **yes**, reviewed. `MathUtil.clamp` and explicitly specified degeneracy fallbacks implement the contract's exceptions.
- `NumericPolicyTest`: **13 / 13 passed**.
- Positive subnormal scales remain accepted; raw arithmetic stays IEEE-754.

## 4B. Revision 3 clarifications

- `Vec3.normalizedOr` returns the caller fallback as the same object, without validation, normalization or copying: **yes**.
- `MathUtil.clamp` accepts infinite bounds, maps NaN to `lo`, stays within valid bounds: **yes**.
- `Vec3.mul`, `Vec3.lerp`, `Quat.slerp`, `Quat.nlerp` do not clamp or saturate scalars: **yes**.
- `PolicyClarificationTest`: **9 / 9 passed**.

## 5. Deviations and findings

- Mathematical/API deviations from the contract: **none identified**.
- Failing/flaky tests: **none observed**. This was a single clean full-suite execution followed by `check`; no claim about exhaustive numerical correctness is made.
- Test compile fixes: **none**. Five external JOML class-file warnings remain recorded above.
- Environment setup initially found only Java 21, no javac, no Gradle. Provisioning was possible. An initial sandboxed curl could not connect to the proxy; enabling network access resolved it. Gradle wrapper URL validation initially failed because Java did not infer proxy settings. Explicit JVM proxy settings and the environment CA trust store resolved it. Failed and successful wrapper logs are retained. These were setup failures, not mathematical test failures.
- A report-assembly command initially looked for a license inside the JOML JAR and found none; the resolved POM confirmed MIT. This did not affect the build or tests.
- Not performed: Claude's independent review, adversarial tests, later milestones, plugin integration, final acceptance, merge to main, release publishing.
- SDK binaries and Gradle caches are not shipped in the source archive; their versions, verified hashes and provisioning instructions are included. Source, wrapper, original input, JUnit XML, HTML reports, and full build logs are included.

## 6. Declaration

- [x] Compiled on Java 25 using the Gradle toolchain
- [x] All supplied tests executed: 97 / 97 passed
- [x] No production dependency
- [x] No existing plugin modified

Status: **VERIFIED — automated initial implementation pass only**.
Final M1 acceptance is **pending Claude's independent review and adversarial-test rerun**. Work stops after this pass.
