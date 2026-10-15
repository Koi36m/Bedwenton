# M1 Handoff: `minecraft-vfx-math`, milestone M1 (core transforms), revision 3

> **Revision 3 supersedes revisions 1 and 2.** Revision 2 replaced the false "finite in, finite out" rule with a
> four-tier numerical policy (`docs/AMENDMENT-1-NUMERIC-POLICY.md`). Revision 3 adds two narrow clarifications
> (`docs/AMENDMENT-2-CLARIFICATIONS.md`, read it first; it is short): **A**, a caller-supplied fallback is returned
> unchanged and `clamp` may have infinite bounds, so those guarantees depend on the caller's values; **B**, the
> supported domain 1e100 is now defined for every operand (scalar multipliers, interpolation parameters, angles,
> axes), and outside it plain IEEE applies. Section 0A of `docs/IMPLEMENTATION-CONTRACT.md` is the authority.
> **If you already extracted revision 2:** add `src/test/java/workshop/vfx/math/core/PolicyClarificationTest.java`
> and replace `README-FOR-CODEX.md`, `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION-CONTRACT.md`,
> `docs/AMENDMENT-1-NUMERIC-POLICY.md`, `docs/TEST-SPECIFICATION.md`, `docs/VERIFICATION-REPORT-TEMPLATE.md`, plus
> the new `docs/AMENDMENT-2-CLARIFICATIONS.md` and `SHA256SUMS.txt`. Nothing else changed: all earlier test files and
> golden files are byte-identical. The Java 25 toolchain requirement is unchanged: Step 0 still applies.

## 0. Boundaries (non-negotiable)

- New standalone repository named `minecraft-vfx-math`. **Do not modify** Full Heal 0.2.0, Astral Prism 0.2.0,
  ParticleEmitter, or any other plugin or repository.
- Production code: pure Java 25, **no dependencies at all**, no Paper, Bukkit, Minecraft or JOML.
  JOML is allowed in test scope only (`JomlOracle.java` is the only file that imports it).
- Package root `workshop.vfx.math`; M1 package `workshop.vfx.math.core`.
- Implement only: `Vec3`, `Quat`, `Pose`, `Frame`, `MathUtil`. No curves, clocks, sampling, graphs, rendering,
  or Paper code. No extra public API beyond `docs/IMPLEMENTATION-CONTRACT.md`.
- Do not copy code from Astral Prism's tutorial sources or from any external repository. Implement the math
  independently from the contract.
- **Never edit an expected value in a test to make it pass.** See `docs/TEST-SPECIFICATION.md` section 2.

## 1. What is in this package

| Path | Purpose |
|---|---|
| `docs/AMENDMENT-2-CLARIFICATIONS.md` | **[R3]** Clarifications A and B, change log, domain table, delta notes |
| `docs/AMENDMENT-1-NUMERIC-POLICY.md` | **[R2]** The four-tier policy: decisions and change log (partly refined by Amendment 2) |
| `docs/IMPLEMENTATION-CONTRACT.md` | The API, algorithms and accuracy requirements you implement (revision 3; changes marked [R2], [R3]) |
| `docs/CONVENTIONS.md` | Axes, handedness, rotation direction, frame conventions (binding) |
| `docs/REFERENCE-VECTORS.md` | Hand-derived values and the golden-file formats |
| `docs/TEST-SPECIFICATION.md` | Test inventory, rules, M0 regression mapping, pre-handoff checks |
| `docs/ARCHITECTURE.md` | Copy to the repository **root** as `ARCHITECTURE.md` |
| `docs/VERIFICATION-REPORT-TEMPLATE.md` | Fill this in and return it |
| `src/test/java/workshop/vfx/math/core/*.java` | 11 JUnit Jupiter test classes (97 test methods) plus 2 helper files |
| `src/test/resources/golden/*.csv`, `MANIFEST.sha256` | Independent oracle data produced by `tools/generate_golden_vectors.py` |
| `tools/generate_golden_vectors.py` | Python 3 (standard library) oracle generator |
| `SHA256SUMS.txt` | Integrity hashes of every file in this package |

## 2. Steps

### Step 0: verify the toolchain **before anything else**
Run and record the complete output of:
```
java -version
javac -version
./gradlew --version        # after Step 1 creates the wrapper (or: gradle --version)
```
`javac -version` must report Java 25 (any 25.x). If a Java 25 toolchain is not available, **stop** and report:
do not substitute Java 21, do not lower `options.release`, and do not claim verification. Also verify the
package first: `sha256sum -c SHA256SUMS.txt`.

### Step 1: repository scaffold on `main`
1. Create repository `minecraft-vfx-math` with branch `main`.
2. Add the Gradle wrapper. Gradle 9.3.1 is the version recorded in Full Heal 0.2.0's build log with Java 25;
   any Gradle release that supports Java 25 is acceptable, record the version you use.
3. Add `build.gradle`, `settings.gradle` (`rootProject.name = 'minecraft-vfx-math'`), `.gitignore`,
   a short `README.md`, `ARCHITECTURE.md` (from `docs/ARCHITECTURE.md`), the `docs/` files and `tools/`.
   Add `src/test/resources/golden/` too (data only).
4. Required build properties (the snippet is guidance and has not been run; adapt as needed):
```groovy
plugins { id 'java' }
group = 'workshop.vfx'
version = '0.1.0-m1'
repositories { mavenCentral() }
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.12.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testImplementation 'org.joml:joml:<latest stable on Maven Central; record version and check its license>'
}
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.withType(JavaCompile).configureEach {
    options.release = 25
    options.encoding = 'UTF-8'
    options.compilerArgs << '-Xlint:all'
}
test {
    useJUnitPlatform()
    maxHeapSize = '512m'
    testLogging { events 'passed', 'failed', 'skipped'; showStandardStreams = true }  // keep the printed [M1 ... report] lines
}
tasks.register('verifyNoRuntimeDependencies') {
    doLast {
        def files = configurations.runtimeClasspath.files
        if (!files.isEmpty()) throw new GradleException('Production runtime classpath must be empty, found: ' + files*.name)
    }
}
check.dependsOn 'verifyNoRuntimeDependencies'
```
   No Mockito. No production dependencies.
5. Run `./gradlew clean test` (there are no tests yet). It must succeed on Java 25. Commit and tag `baseline-scaffold`.
   **Gate A: toolchain verified.** Record the output.

### Step 2: add the tests unmodified
1. `git switch -c m1-core-transforms`.
2. Copy `src/test/java/**` from this package into the repository **without changing any file**. Commit as
   `tests as delivered (unmodified)` and record the commit hash in the report.

### Step 3: implement
Write `src/main/java/workshop/vfx/math/core/{MathUtil,Vec3,Quat,Frame,Pose}.java` from
`docs/IMPLEMENTATION-CONTRACT.md`. Read `docs/CONVENTIONS.md` first: most traps are about handedness and
operand order. Then read contract section 0A: raw arithmetic is plain IEEE (do **not** add clamping or
sanitizing, including of scalar multipliers and interpolation parameters), degeneracy operations must be total for
every `double` operand value, `normalizedOr` must return the caller's fallback **as the same object**, `clamp`
must accept infinite bounds, `slerp`/`nlerp` must not clamp `t`, and `Pose.then` / `Pose.inverse` must check the
result and throw `ArithmeticException` (not `IllegalArgumentException`).

### Step 4: build and test
```
./gradlew clean test
./gradlew check
```
Expected: compile succeeds with `-Xlint:all`; 97 tests run (93 plus the 4 JOML tests); all pass;
`verifyNoRuntimeDependencies` passes. Keep the full console output and the JUnit XML reports.

If something fails: apply the rules in `docs/TEST-SPECIFICATION.md` section 2. Compile-level repairs to test
files (an import, a JOML method name in `JomlOracle.java`) are allowed if you record the exact diff. A failing
assertion is a finding, not an invitation to edit: report it with your analysis.

### Step 5: report
Fill in `docs/VERIFICATION-REPORT-TEMPLATE.md` and return it with the source ZIP, `SHA256SUMS`, and logs.
**Do not call M1 verified** unless Step 4 passed on Java 25 and every item in the template is filled in with
real output. If anything was not run, say so plainly.

## 3. What happens next
Claude reviews your implementation's mathematics, adds adversarial tests in a separate commit on the same
branch, and asks you to rerun. M1 is accepted only after that rerun is green. Then the branch is merged to
`main` and tagged `m1-verified`.

## 4. Known facts about this package (so nothing surprises you)
- The tests were smoke-checked by the author against a **throwaway implementation** (not provided) with a
  small assertion shim on Java 21, and 12 injected bugs were all caught. That is not Java 25 / JUnit
  verification. Details: `docs/TEST-SPECIFICATION.md` section 5.
- `JomlOracle.java` and `JomlOracleTest.java` were **not compiled** by the author (no JOML available); the
  JOML calls (`Quaterniond(x,y,z,w)`, `transform(Vector3d)`, `mul`, `slerp`, `rotationXYZ`) are written from
  memory of the API.
- `QuatTest.driftOverMillionCompositions` allocates many short-lived objects; it ran in well under a second
  on the author's machine and needs no special JVM flags.
