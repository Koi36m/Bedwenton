# M1 Verification Report (to be completed by the implementing environment)

Do not write "verified" anywhere unless the evidence below exists. Write "not run" where applicable.

## 1. Environment (paste real output)
- OS and CPU:
- `java -version`:
- `javac -version`:
- Gradle version (`./gradlew --version`):
- JUnit Jupiter version used (expected 5.12.2):
- JOML version used and its license (verified from the resolved artifact):
- Package integrity (`sha256sum -c SHA256SUMS.txt`) result:

## 2. Repository
- Repository location and name (expected `minecraft-vfx-math`):
- `main` scaffold commit and tag `baseline-scaffold` (hash):
- Gate A (`./gradlew clean test` with zero tests succeeded on Java 25): yes / no, log attached
- Commit "tests as delivered (unmodified)" (hash):
- Implementation commit(s) (hash):
- Confirmation that no existing plugin or other repository was modified: yes / no

## 3. Build and test results (paste real output)
- `./gradlew clean test`: exit code, total / passed / failed / skipped (expected 97 / 97 / 0 / 0):
- `./gradlew check` including `verifyNoRuntimeDependencies`: result:
- Compiler warnings under `-Xlint:all`:
- Printed measurement lines, copied verbatim:
  - `[M1 drift report] ...`
  - `[M1 transport report] ...`
  - `[M1 JOML report] ...`
- JUnit XML report location:

## 4. Changes to delivered tests
List every edit to any file under `src/test`, with the exact diff and reason. If none: "none".
Any change to an expected value must include an independent derivation and is subject to review.

## 4A. Numerical policy confirmation (revision 2)
- `Pose.then` and `Pose.inverse` throw `ArithmeticException` for non-representable results (scale overflow,
  scale underflow to zero, translation overflow, subnormal reciprocal): confirmed by `NumericPolicyTest`, yes / no
- No clamping, saturation or NaN sanitizing exists anywhere in production code (reviewed by you): yes / no
- Number of `NumericPolicyTest` methods run and passed (expected 13 / 13):

## 4B. Revision 3 clarifications (confirm each)
- `Vec3.normalizedOr` returns the caller's fallback as the **same object** (not copied, not normalized): yes / no
- `MathUtil.clamp` accepts infinite bounds, maps a NaN value to `lo`, never leaves `[lo, hi]`: yes / no
- `Vec3.mul`, `Vec3.lerp`, `Quat.slerp`, `Quat.nlerp` do **not** clamp or saturate their scalar arguments: yes / no
- Number of `PolicyClarificationTest` methods run and passed (expected 9 / 9):

## 5. Deviations and findings
- Places where the implementation differs from `IMPLEMENTATION-CONTRACT.md` and why:
- Failing or flaky tests with your analysis:
- Anything that was not run or could not be verified:

## 6. Declaration
- [ ] Compiled on Java 25 with the Gradle toolchain
- [ ] All tests executed (state counts above)
- [ ] No production dependency
- [ ] No existing plugin modified
Status: NOT VERIFIED / VERIFIED (delete one; VERIFIED only if every box is ticked and evidence is attached)
