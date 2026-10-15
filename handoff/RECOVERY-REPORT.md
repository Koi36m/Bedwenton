# M1 deliverable recovery and GitHub handoff

The tested M1 implementation is unchanged. No M2 work, mathematical edits, test edits, history rewriting, merges, or force pushes were performed. Final M1 acceptance remains pending Claude Code's independent review and the subsequent adversarial-test verification run.

## Deliverables and accessibility

The original files in `/workspace/output/` exist and passed `sha256sum -c SHA256SUMS.txt`. The source ZIP also passed CRC validation and its complete internal source manifest. The Git bundle passed `git bundle verify` and contains complete original history.

Conversation attachment delivery remains unavailable: the designated `/codex/browser/projectless/0002-files-mentioned-by-the-user-uploaded-file-pointe/output` does not exist, creation fails with `Read-only file system`, and this session exposes no general attachment upload tool. No conversation download URL is claimed.

The exact original artifacts are copied under `handoff/` on the separate `m1-review-handoff` branch. This preserves `m1-core-transforms` at the original tested checkpoint. Download individual files using GitHub's Raw/Download control, or clone the repository and check out this branch. The source ZIP includes verification logs, all 11 JUnit XML reports, the original independent handoff archive, and the HTML test report. The bundle contains the original four Git refs; it predates this recovery-only branch.

## GitHub and Git preservation

Repository: https://github.com/Koi36m/Bedwenton

Pre-push `git ls-remote --symref` succeeded and returned no refs: the repository was empty. The GitHub connector reported push permission. An authenticated Git dry run succeeded, followed by one atomic push creating `main`, `m1-core-transforms`, and `baseline-scaffold`. No unrelated history existed or was overwritten. GitHub CLI's separate token check failed, but Git transport authentication and the actual push succeeded.

Original commits, full SHAs:

```text
8397c04b55f7d506e8fec79fca120ce297e8ae22 Record Java 25 verification, unchanged test integrity and handoff evidence
05c27067f3e9e2480228d0d34e5a2bd64558c889 Implement M1 core transforms against revision 3
77a1430373d86c5025c7aecc49081b9e5f4f3b49 tests as delivered (unmodified)
870c9fd75bf9a7a9d619179993d37f2b6487b081 Scaffold standalone Java 25 M1 project
```

- Scaffold `main` and `baseline-scaffold`: `870c9fd75bf9a7a9d619179993d37f2b6487b081`.
- Exact delivered-test baseline: `77a1430373d86c5025c7aecc49081b9e5f4f3b49`.
- Production implementation only: `05c27067f3e9e2480228d0d34e5a2bd64558c889`.
- Original evidence checkpoint / `m1-core-transforms`: `8397c04b55f7d506e8fec79fca120ce297e8ae22`.

Original repository status: clean; branch `m1-core-transforms`. The recovery branch adds only `handoff/` artifacts and documentation. Push logs and remote ref observations are in `handoff/recovery-logs/`.

## Full recorded Java and Gradle version output

This is the original first-pass evidence, preserved without rerunning the mathematics:

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

## Test and check results

- `./gradlew clean test`: exit 0, 97 total / 97 passed / 0 failed / 0 errors / 0 skipped. Reconfirmed by parsing all 11 saved JUnit XML suites during recovery.
- `./gradlew check`: exit 0, `verifyNoRuntimeDependencies` passed, test task up-to-date from clean test. Recorded output: `BUILD SUCCESSFUL in 995ms`, `5 actionable tasks: 1 executed, 4 up-to-date`.
- Production runtime classpath empty; zero production compiler warnings.
- This recovery did not rerun tests or change compiled source; it validated the saved evidence and original artifacts.

## Numerical measurements, verbatim

```text
[M1 transport report] worst per-step twist: transport = 1.1102230246251568E-16 rad, naive alignY = 3.141592653589793 rad
[M1 JOML report] rotationXYZ applies X first = false, applies Z first = true
[M1 drift report] raw |norm-1| after 1,000,000 compositions = 1.6544543512964083E-12
[M1 drift report] worst |norm-1| between renormalizations (every 1000) = 6.8833827526759706E-15
```

The drift measurement includes both raw million-composition drift and the worst drift between periodic renormalizations. The raw measurement is informational; the periodic-renormalization assertion passed. Transport and its naive negative control behaved as expected. JOML's `rotationXYZ` applies Z first; the engine's explicitly named X-then-Y-then-Z helper intentionally uses its specified composition order. That pinned convention difference is expected and the corresponding tests passed.

## All five JOML compiler warnings, verbatim

```text
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Quaterniond.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector3f.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
/workspace/work/gradle-home/caches/modules-2/files-2.1/org.joml/joml/1.10.9/438e036486bad66b189bff385dd07dea4f74a146/joml-1.10.9.jar(/org/joml/Vector4d.class): warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

Each is a Java 25 class-file metadata warning in the resolved JOML 1.10.9 binary: its version-46 class files contain an attribute introduced for version 52. The compiler ignores that type-annotation attribute. These diagnostics do not report a numerical oracle difference, a precision-tolerance problem, or an implementation error. All four JOML oracle tests passed. The warning text alone does not prove all possible numerical cases, so independent mathematical review is still required. No dependency bytecode was changed and no warning was suppressed.

## Changed tests and exact diffs

None. `git diff 77a1430373d86c5025c7aecc49081b9e5f4f3b49 8397c04b55f7d506e8fec79fca120ce297e8ae22 -- src/test` is empty; its exact empty output is saved at `handoff/recovery-logs/test-diff.patch`.

All 19 delivered Java test/helper and golden resource files were also compared byte-for-byte with the original Revision 3 ZIP during recovery; all match. No expected value, tolerance, helper or golden vector changed. No mechanical compile fixes were needed.

## Artifact SHA-256 checksums

The original manifest remains unchanged:

```text
68c5e46f8243334a408f04cb61e98e300d02512085701b00e2674cbb138d5a10  GIT-CHECKPOINT.txt
3f2d1d6abd4da53542c333044f73072c7c471b74361cdf2b3352a49993d1f18e  VERIFICATION-REPORT.md
4f0b07ca823f8d958b0ea7cb9b0818e78f6da830f99583ed65ed3ac46cb52b50  minecraft-vfx-math-M1-rev3-source.zip
2bd5ccb38cd921e8d16f5a75ac5b8a337bcfa57c3330ec3311801414cdd39fed  minecraft-vfx-math-M1-rev3.git.bundle
```

SHA-256 of `SHA256SUMS.txt` itself:

```text
e0d0dc1e8bf3fe55db63873a3a537c8440be5456c023e9d87abc1c9b254fed24  SHA256SUMS.txt
```

The recovery report is additional documentation and is intentionally absent from that original manifest.
