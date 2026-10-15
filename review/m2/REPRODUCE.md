# Reproducing the M2 review

Base: `80927177469d89b985d3b57a3219652a994d6d4b` (Codex `m2-animation-time`). The review branch `review/m2-claude`
adds only `review/m2/**`, `src/test/java/workshop/vfx/math/time/M2Review*.java` and `src/test/resources/m2/review/**`.

```sh
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 185: 182 passed, 3 skipped
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 review/m2/generate_boundary_vectors.py /tmp/m2-review-regen && diff -r /tmp/m2-review-regen src/test/resources/m2/review
python3 review/m2/probe_bounds.py   # re-anchoring error ratios and cycle-exactness limits (random, seeded)
```

Disabled findings tests (expected to fail against 1e85682), run without editing the repository through an init script
kept outside the project:

```groovy
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'junit.jupiter.conditions.deactivate', 'org.junit.*DisabledCondition'
        filter { includeTestsMatching 'workshop.vfx.math.time.M2ReviewFindingsTest' }
    }
}
```

`$REVIEW_TMP` in the logs stands for the reviewer's scratch directory.
