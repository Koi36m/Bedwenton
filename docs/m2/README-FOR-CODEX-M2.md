# M2 handoff for Codex: deterministic animation time

**Do not start until the owner authorizes M2 implementation.** This package is a proposal under review. If the
owner changes a design decision, the contract and tests are revised first, and you receive a new package.

## What to build

Seven production files in `src/main/java/workshop/vfx/math/time`, exactly as specified in
`docs/m2/M2-IMPLEMENTATION-CONTRACT.md`:

`TimeDomain.java`, `TimePoint.java`, `TickTime.java`, `Playback.java`, `RepeatMode.java`, `Timing.java`,
`TimingSample.java` (with the nested enum `TimingSample.Stage`).

Read, in this order:
1. `docs/m2/M2-IMPLEMENTATION-CONTRACT.md` (normative). Sections 3 to 5 give the exact operation order; keep it.
2. `docs/m2/M2-CONVENTIONS.md`: units, domains, and facts versus preferences.
3. `docs/m2/M2-TEST-SPECIFICATION.md`: the tests, the golden files and the acceptance criteria.
4. `docs/m2/M2-DESIGN-DECISIONS.md` and `docs/m2/M2-RISKS-AND-DEFERRED.md`: the background.

## Rules

- Do not modify anything in M1: `workshop.vfx.math.core`, its tests, `src/test/resources/golden`,
  `tools/generate_golden_vectors.py`, or the M1 documents in `docs/*.md`.
- Do not modify the M2 tests, the files in `src/test/resources/m2/golden`, or `tools/generate_m2_golden_vectors.py`.
  If a test seems wrong, stop and report it.
- No dependencies, no clock reads, no randomness, no threads, no mutable statics, no `Math.fma`, and no reordering
  of the specified arithmetic (contract 6.1). The golden tests compare bits.
- No public API beyond contract section 8. Helpers are private.
- Do not modify existing Minecraft plugins.

## Verify

```sh
sha256sum -c SHA256SUMS.txt                     # from the root of this handoff package
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 169 tests, 169 passed
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m2_golden_vectors.py /tmp/m2-regen && diff -r /tmp/m2-regen src/test/resources/m2/golden
```

Report: versions, logs, JUnit XML and commit hashes (`M2-TEST-SPECIFICATION.md` section 4).

## Note on test validation

Before handoff, Claude Code ran these tests against a throwaway reference implementation of the contract. It is
not part of this package and must not be treated as the implementation: 169 of 169 passed on Java 25.0.4.1 with
Gradle 9.1.0, and 25 of 25 injected defects were detected. Logs are in `validation/`.
