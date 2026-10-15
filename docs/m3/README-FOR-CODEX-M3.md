# M3 handoff for Codex: curves, spirals, easing

**Do not start until the owner authorizes M3 implementation and M2 is formally accepted.** This package is a
proposal under review (by the owner and by ChatGPT). If a design decision changes, the contract and tests are revised
first, and you receive a new package.

## Build

Seven public types in `src/main/java/workshop/vfx/math/curve/`, exactly as specified in
`docs/m3/M3-IMPLEMENTATION-CONTRACT.md` section 2: `Curve`, `LineSegment`, `QuadraticBezier`, `CubicBezier`,
`Spiral`, `PosedCurve`, `Easing`. Package-private helpers are allowed (for example the turn reduction).

Read in this order:
1. the contract (normative);
2. `M3-CONVENTIONS.md`;
3. `M3-TEST-SPECIFICATION.md`;
4. `M3-DESIGN-DECISIONS.md` and `M3-RISKS.md` (background).

## Rules

- Use only `workshop.vfx.math.core` from the project. Do not use `workshop.vfx.math.time` (DM3-13).
- Trigonometry only through `StrictMath.sin` and `StrictMath.cos`. Forbidden: `java.lang.Math` trigonometry,
  `pow`, `hypot`, `exp`, `log`, `fma` and `random` (a source-scan test enforces this).
- Phases in turns with exact reduction (contract 5, T2 to T4). Exact endpoints for Beziers (E1). The constant-radius
  rule for spirals.
- No clocks, randomness, threads, mutable statics, I/O, or Paper/Bukkit/Minecraft/JOML types.
- Do not modify M1, M2, any existing test, golden or reference data, generator, contract or verification record. If
  a test seems wrong, stop and report it.
- Do not modify Full Heal, Astral Prism, Cloudstep, ParticleEmitter or other plugins.

## Verify

```sh
sha256sum -c SHA256SUMS.txt     # from the handoff package root
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 229 tests, 229 passed
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m3_reference_vectors.py /tmp/m3-ref && diff -r /tmp/m3-ref src/test/resources/m3/reference
git diff e3b79bae1476e549d49b29dcf361e7da50fe67db --stat -- src/main/java/workshop/vfx/math/core src/main/java/workshop/vfx/math/time src/test/java/workshop/vfx/math/core src/test/java/workshop/vfx/math/time   # empty
```

Report versions, logs, JUnit XML, the production diff and the commit hashes. Then stop for Claude's independent
review.
