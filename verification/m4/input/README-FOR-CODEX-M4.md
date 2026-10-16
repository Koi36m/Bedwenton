# M4 handoff for Codex: particle sampling and distribution

**Do not start until the owner authorizes M4 implementation**, after ChatGPT's independent review of this proposal.
If a decision changes, the contract and tests are revised first, and you receive a new package.

## Build

Six public types in `src/main/java/workshop/vfx/math/sample/`, exactly as specified in
`docs/m4/M4-IMPLEMENTATION-CONTRACT.md` section 2:
- `Endpoints`, `SampleLimits`, `ArcLengthTolerance`, `ArcLengthTable`, `SampleSet`, `Sampler`;
- package-private helpers are allowed.

Read in this order:
1. the contract (normative). Sections 4 to 6 fix the operation order; follow them literally;
2. `M4-CONVENTIONS.md`;
3. `M4-TEST-SPECIFICATION.md`;
4. `M4-DESIGN-DECISIONS.md`, `M4-ERROR-ANALYSIS.md` and `M4-RISKS.md` (background).

## Rules

- Use only `workshop.vfx.math.core` and `workshop.vfx.math.curve`. Never `workshop.vfx.math.time`.
- Allowed arithmetic: `+ - * /`, `Math.sqrt`, `abs`, `min`, `max`, `floor` and comparisons.
- Forbidden: trigonometry, `pow`, `exp`, `log`, `hypot`, `fma`, randomness, clocks, threads, synchronization, I/O,
  and mutable statics. A source-scan test enforces this.
- The Gauss-Legendre constants must be the hex literals of contract 5.2.
- Respect the work bounds of contract section 8:
  - count `curve.derivative` calls during `build`, and never exceed `maxEvaluations`;
  - `evaluations()` must equal the count exactly.
- Validate counts and spacing **before** evaluating the curve or allocating proportional arrays.
- Every loop must have a fixed bound. No loop advances a double until a floating-point condition holds.
- Contract revision 2 (`M4-R2-CORRECTIONS.md`) changed 5.3 (tolerance floor), 5.5 (ratio and linear modes) and 5.6
  (inversion: Newton phase, exits and the better bracket end). Implement 5.6 step by step, in the stated order.
- Contract revision 2.1 (`M4-R21-CORRECTIONS.md`) changed 5.1 (speed pairs) and 5.2 (panel rule arithmetic). Do
  not compute the rule as `h * (W0 v0 + ...)`. Use the stated order: width d, half weights, ratios to the largest
  component, then the branch on `d * S`.
- Do not modify M1, M2, M3, any existing test, reference data, generator, contract or verification record. If a test
  seems wrong, stop and report it.
- Do not modify Full Heal, Astral Prism, Cloudstep, ParticleEmitter or other plugins. Do not add Paper or Bukkit code.

## Verify

```sh
sha256sum -c SHA256SUMS.txt     # from the handoff package root
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 313 tests, 313 passed
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m4_reference_vectors.py /tmp/m4-ref && diff -r /tmp/m4-ref src/test/resources/m4/reference   # about 1 minute
python3 tools/generate_m3_reference_vectors.py /tmp/m3-ref && diff -r /tmp/m3-ref src/test/resources/m3/reference
git diff a6726c42a20940a6a6d2dd24477e6a642f6b8cbc --stat -- src/main/java/workshop/vfx/math/core src/main/java/workshop/vfx/math/time src/main/java/workshop/vfx/math/curve src/test/java/workshop/vfx/math/core src/test/java/workshop/vfx/math/time src/test/java/workshop/vfx/math/curve src/test/resources/m3   # empty
```

Report versions, logs, JUnit XML, the production diff and the commit hashes. Then stop for Claude's independent
review.
