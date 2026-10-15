# M2 revision 2: instructions for Codex (narrow P-R1 correction)

The owner approved M2 contract revision 2 on 2026-10-15. Your task is **only** the P-R1 correction below, then a
clean Java 25 verification. Start from branch `m2-r2-amendment` (bundle `m2-r2-amendment.bundle`, which contains
Claude's review commit and the amendment commits on top of your `8092717`), or apply the package's patches in order
on `8092717`.

## Read

1. `docs/m2/M2-AMENDMENT-R2.md`: decisions, the complete changelog and the authorized change (section 5).
2. `docs/m2/M2-IMPLEMENTATION-CONTRACT.md`: sections 3 and 4, marked [R2], are normative.

## Change exactly this (production)

1. `src/main/java/workshop/vfx/math/time/TimePoint.java`, `ticksSince`: in the COSMETIC domain, compute
   `(double) (value - origin.value) / 50_000_000.0` with Java's wrapping `-`. It must never throw for COSMETIC
   differences. GAME keeps `Math.subtractExact`, its `ArithmeticException` and `(double) delta`. The null and
   domain checks stay first.
2. `src/main/java/workshop/vfx/math/time/Playback.java`, control-instant validation: for a COSMETIC anchor, reject
   when `at.value() - anchor.value() < 0` (wrapping). GAME keeps `at.value() < anchor.value()`. The domain check
   stays first. No other behaviour changes: no-op controls still return `this`.

Do **not** change anything else in `src/main`, any test, golden file, review resource, generator, document or
anything in M1. If a test seems wrong, stop and report it.

## Verify

```sh
sha256sum -c SHA256SUMS.txt                               # from the root of the amendment package
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 191 tests, 191 passed
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m2_golden_vectors.py /tmp/m2-golden && diff -r /tmp/m2-golden src/test/resources/m2/golden
python3 review/m2/generate_boundary_vectors.py /tmp/m2-review && diff -r /tmp/m2-review src/test/resources/m2/review
git diff 1e856820e606275c019f09d6f196bcf6a5a5138d -- src/main     # only the two changes above
git diff 9e3f3acde0db4b9d0a202ba4f7f396c132843597 -- src/main/java/workshop/vfx/math/core src/test/java/workshop/vfx/math/core src/test/resources/golden   # empty
```

Before your change, the same run shows exactly 6 failures (amendment section 6, criterion 4). Record both runs if
you can.

## Report

`java -version`, `./gradlew --version`, both Gradle logs, JUnit XML, the production diff and the commit hashes. Then
stop for Claude's final review. Final M2 acceptance remains with the owner.
