# M6 handoff for Codex: runtime safety and particle delivery planning

**Do not start until the owner authorizes M6 implementation**, after ChatGPT's independent review of this proposal.
If a decision changes, the contract and tests are revised first, and you receive a new package.

## Build

Ten public types in `src/main/java/workshop/vfx/delivery/`, exactly as specified in
`docs/m6/M6-IMPLEMENTATION-CONTRACT.md` section 2:
- `QualityTier`, `EffectPriority`, `LayerRole`, `Outcome`;
- `EffectRecipe`, `DeliveryRequest`, `DeliveryPolicy`;
- `DeliveryPlanner`, `DeliveryPlan`, `EffectDelivery`.

Package-private helpers are allowed.

Read in this order:
1. the contract (normative). Sections 5 to 7 fix the order of every decision; follow them literally;
2. `M6-CONVENTIONS.md`;
3. `M6-TEST-SPECIFICATION.md`;
4. `M6-DESIGN-DECISIONS.md`, `M6-ARCHITECTURE.md` and `M6-RISKS.md` (background).

## Rules

- Use only `workshop.vfx.scene`, `workshop.vfx.math.core`, `workshop.vfx.math.time` and `java.util` (not
  `java.util.concurrent` or `java.util.function`).
- **Never** use Bukkit, Paper, Minecraft, Folia, plugin or I/O APIs. Do not emit particles.
- **Integers only** in planning: no `double` or `float`. Use `Math.floorMod` for caller ordinals and `long` for sums.
- **Evaluation:**
  - call `EffectScene.evaluate` only for admitted requests, exactly once each;
  - catch only `ArithmeticException`;
  - never read or copy frame coordinates.
- **Validation:**
  - validate sizes before copying;
  - `plan` either throws `IllegalArgumentException` before evaluating anything, or returns a complete plan.
- **No state:**
  - no mutable statics, caches, clocks, randomness or threads;
  - hash maps may hold remaining budgets only, and are never iterated for output.
- **Role split:** the reference picks the largest remainders quadratically. Production should sort, as contract
  section 9 states.
- Do not modify M1 to M5, any existing test, reference data, generator, contract or verification record. If a test
  seems wrong, stop and report it.

## Verify

```sh
sha256sum -c SHA256SUMS.txt     # from the handoff package root
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test   # 415 tests, 415 passed
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m6_reference_plans.py /tmp/m6-ref && diff -r /tmp/m6-ref src/test/resources/m6/reference
git diff d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb --stat -- src/main/java/workshop/vfx/math src/main/java/workshop/vfx/scene \
    src/test/java/workshop/vfx/math src/test/java/workshop/vfx/scene src/test/resources/m3 src/test/resources/m4 src/test/resources/m5   # empty
```

Report versions, logs, JUnit XML, the production diff and the commit hashes. Then stop for the independent review.
