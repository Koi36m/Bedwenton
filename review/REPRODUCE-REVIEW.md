# Reproducing the independent M1 review

Branch `review/m1-claude`, based on `m1-core-transforms` at `8397c04b55f7d506e8fec79fca120ce297e8ae22`.
The review adds files only: two test classes and this `review/` folder. Production code, the 97 supplied tests and
the golden vectors are unchanged (check with `git diff --stat 8397c04 -- src/main src/test/resources docs tools`
and `git diff --stat 8397c04 -- src/test/java`, which lists only the two new `Review*Test.java` files).

## Toolchain used

- Oracle JDK 25.0.4.1 (build 25.0.4.1+1-LTS-5), Linux x86_64, from
  `https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz`, SHA-256 checked against the published
  `.sha256` (`dd7e7f51d23bcffe85f1a439577ce1c0190a33417b34afdb338f9b3897bdeaec`).
- Gradle 9.1.0 through the project wrapper. The wrapper checks the distribution against the pinned
  `distributionSha256Sum` (`a17ddd85...c806`) and refuses a mismatch.
- `-Porg.gradle.java.installations.auto-download=false` makes sure Gradle cannot quietly provision another JDK.

## Commands

```sh
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
java -version            # must report 25
./gradlew --version      # must report Gradle 9.1.0 and Launcher JVM 25
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
```

Expected on 8397c04 plus the review tests: 122 tests, 115 passed, 0 failed, 7 skipped (the disabled
`ReviewFindingsTest`). `ReviewAdversarialTest.runsOnJava25` fails if the test JVM is not Java 25.

## Running the disabled findings tests

They are expected to fail against 8397c04; each one encodes one reading of the contract. Without editing the
repository, use a Gradle init script kept outside the project:

```groovy
// enable-findings.gradle
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'junit.jupiter.conditions.deactivate', 'org.junit.*DisabledCondition'
        filter { includeTestsMatching 'workshop.vfx.math.core.ReviewFindingsTest' }
    }
}
```

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false -I /path/to/enable-findings.gradle test
```

Expected on 8397c04: 7 tests, 7 failed (see `logs/review-findings-enabled.txt`).

## Probe programs

`review/probe/*.java` are the throwaway measurement programs behind the numbers in the report (not tests).
Compile them against `build/classes/java/main` after a build:

```sh
javac -cp build/classes/java/main -d /tmp/probe review/probe/Probe.java
java -cp /tmp/probe:build/classes/java/main Probe
```

In `logs/probe.txt` the line `transportY 1e6-step walk ... worst=Infinity` is that probe's way of flagging a
**bitwise** mismatch with `normalize(newY)` (finding A1). The frames themselves stay valid to 1e-12, as the
`ReviewAdversarialTest.transportYLongWalk` test confirms.

## Log index

| File | Content |
|---|---|
| `logs/integrity.txt` | remote refs, history, ZIP SHA-256, internal SHA256SUMS, byte comparison, golden manifest, bundle |
| `logs/versions.txt`, `logs/wrapper-attempt.txt` | `java -version`, `javac -version`, `./gradlew --version`, the first wrapper download |
| `logs/baseline-clean-test-attempt1-maven429.txt` | first `clean test`: Maven Central answered HTTP 429 while downloading JUnit (environment, not a test failure) |
| `logs/baseline-clean-test.txt`, `logs/baseline-check.txt`, `logs/junit-baseline/` | the unmodified 97 tests at 8397c04 |
| `logs/review-clean-test.txt`, `logs/review-check.txt`, `logs/junit-review/` | 97 + 25 review tests |
| `logs/review-findings-enabled.txt`, `logs/junit-findings-enabled.xml` | the 7 findings tests forced on |
| `logs/probe.txt` | measurements from `probe/Probe.java` and `probe/TransportYUlps.java` |

`$REVIEW_TMP` in the logs stands for the reviewer's scratch directory (Gradle user home, extracted archive).
