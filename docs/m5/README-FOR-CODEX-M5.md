# M5 handoff for Codex: effect composition and animation

**Do not start until the owner authorizes M5 implementation**, after ChatGPT's independent review of this proposal.
If a decision changes, the contract and tests are revised first, and you receive a new package.

## Build

Ten public types in `src/main/java/workshop/vfx/scene/`, exactly as specified in
`docs/m5/M5-IMPLEMENTATION-CONTRACT.md` section 2:
- `StyleKey`, `Shape`, `Motion`, `Visibility`, `Reveal`, `Layer`, `SceneLimits`, `EffectScene`, `EffectFrame`,
  `FrameBatch`;
- package-private helpers are allowed.

Read in this order:
1. the contract (normative). Sections 5.2, 7 and 8 fix the operation order; follow them literally;
2. `M5-CONVENTIONS.md`;
3. `M5-TEST-SPECIFICATION.md`;
4. `M5-DESIGN-DECISIONS.md`, `M5-ARCHITECTURE.md` and `M5-RISKS.md` (background).

## Rules

- Use only `workshop.vfx.math.core`, `.time`, `.curve`, `.sample` and `java.util` (not `java.util.concurrent`).
- **Never** use Bukkit, Paper, Minecraft, plugin or I/O APIs. This package must stay platform-neutral.
- Reuse M1, M2 and M3 exactly as the contract names them:
  - `Playback.localTicks`, `Timing.sample` and `Easing.apply`;
  - `Pose.then`, `Pose.toWorld`, `Vec3.lerp` and `Vec3.normalizedOr`.

  Do not re-implement them.
- Spin: build the quaternion with `StrictMath.sin`/`cos` and the turn reduction of contract 5.2. **Do not** call M1
  `Quat.axisAngle`: it uses `Math` trigonometry, which is not bit-reproducible across JVMs.
- Forbidden: `Math` trigonometry, `pow`, `exp`, `log`, `hypot`, `fma`; randomness, clocks, threads, synchronization
  and mutable statics. A source-scan test enforces this.
- Validate everything at construction. `evaluate` either returns a complete frame or throws; it never returns a
  partial frame.
- Every loop has a fixed bound. There is no hidden state, caching or scheduling.
- Do not modify M1 to M4, any existing test, reference data, generator, contract or verification record. If a test
  seems wrong, stop and report it.
- Do not modify Full Heal, Astral Prism, Cloudstep, ParticleEmitter or other plugins. Do not add Paper or Bukkit code.

## Verify

```sh
sha256sum -c SHA256SUMS.txt     # from the handoff package root
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 363 tests, 363 passed
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m5_reference_frames.py /tmp/m5-ref && diff -r /tmp/m5-ref src/test/resources/m5/reference
git diff e3bf5578c126a7d3498be0b30fb431d9ec9a3099 --stat -- src/main/java/workshop/vfx/math src/test/java/workshop/vfx/math src/test/resources/m3 src/test/resources/m4   # empty
```

Report versions, logs, JUnit XML, the production diff and the commit hashes. Then stop for the independent review.
