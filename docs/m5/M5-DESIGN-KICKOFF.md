# M5 — Effect Composition and Animation (Design Kickoff)

**State:** DESIGN KICKOFF ONLY. **No M5 production implementation, Java tests, reference generator, or approved public API exists.**
**Created:** 2026-10-16.
**Project:** Minecraft VFX Mathematics Engine, \`Koi36m/Bedwenton\`.
**Baseline:** GitHub-reviewed M4 production source snapshot \`e3bf5578c126a7d3498be0b30fb431d9ec9a3099\` (full source tree \`4e04763dfb803a790a4dbb97c3c1ca28bf474b56\`).
**Evidence checkpoint:** \`ea0dcbc2a3239b9dbf0821f1420ef13371576acf\`, from GitHub Actions run 38974334716, 313/313 tests.
**Roadmap:** \`docs/roadmap/PAPER-COMPATIBILITY-AND-VFX-READINESS.md\` and \`docs/roadmap/PAPER-UPGRADE-ACCEPTANCE-MATRIX.md\`.
**Branch:** \`m5-design-kickoff\`, intentionally branched from \`prep-paper-compatibility\` (documentation-only), *not* a new implementation or a merge into \`main\`.

## Mission

Build a deterministic, reusable, **platform-neutral effect-scene composition** foundation that answers:

> Given an explicit, caller-provided instant and anchor snapshot, which geometric particle positions and abstract styling requests should exist in this frame, and in which deterministic order?

M5 must combine accepted M1–M4 capabilities without modifying them.

- **M1:** \`Vec3\`, \`Pose\`, \`Quat\`, \`Frame\` for spaces and transforms.
- **M2:** \`TimePoint\` GAME/COSMETIC, \`Playback\`, \`Timing\`, \`TimingSample\` with explicit instants, half-open timing and repeated/ping-pong passes.
- **M3:** \`Curve\`, \`LineSegment\`, \`Spiral\`, Bézier and easing.
- **M4:** \`Sampler\`, \`SampleSet\`, \`ArcLengthTable\`, endpoint policy, count/budget constraints.

### Scope to propose, not yet approve

1. Pure Java, immutable reusable effect/scene definitions and immutable **frame snapshots**.
2. Named/layered effects with stable insertion order and deterministic transform composition.
3. Explicit time-progress evaluation using M2 only; *no* hidden system clock, delta accumulator or automatic scheduler.
4. Static sampled patterns and moving patterns, with transforms that may depend on explicit progress.
5. Abstract, platform-neutral particle/visual style identifiers: no Bukkit \`Particle\`, world, entity or plugin objects.
6. Safe activation/inactive frames, finite lifetimes and clear handling of looping/ping-pong.
7. Explicit bounded output size, depth and work; neither the scene nor a callback may silently create an unbounded list.
8. Simple hand-off to a later Paper adapter without changing the numerical/scene APIs.
9. Examples as executable/reference oracles: healing circle with three pulses, orbiting sparks, moving trident water helix, staged constellation effect.

### Explicitly deferred

- Paper/Bukkit/Folia API adapter, Java/Bedrock viewer selection, particle-to-Paper mapping, scheduler and actual network emission.
- Gameplay: custom enchantment triggers, inventory/PDC, cooldowns, ownership, permissions, damage/healing and cross-plugin hooks.
- Live entity/world queries inside M5; moving-world anchor *snapshots* come from the caller.
- Full runtime effects manager, tick subscriptions, task cancellation, persistent sessions, cache eviction, live diagnostic UI.
- Particle quality presets and runtime server/player budgets: later safety milestone (P2), though M5 must have a hard per-evaluation output bound now.
- Seeded random jitter and disk/sphere surface/volume distributions: consider M4.1 separately.
- Sound, light, shader-specific effects, animated color gradients and client mods until contracts and Paper support are explicit.
- Retroactive M1–M4 refactors or changes to accepted test counts / reference data.

## Design issues Claude must resolve in R1

**DM5-1 — Scene abstraction:** smallest immutable public model that represents parallel layers, transform grouping and time-local clips without premature framework complexity.

**DM5-2 — Rendering contract:** one \`EffectFrame\`-like immutable result with stable ordering, positions and abstract \`style\` metadata, such that the Paper adapter only translates data. Compare per-particle records with grouped batches; choose based on measured allocation and simplicity.

**DM5-3 — Time ownership:** how the caller supplies a \`TimePoint\`, chooses GAME/COSMETIC and optionally \`Playback\`; enforce domain validation. Define AFTER/BEFORE, negative/zero rate, seek/pause, and exact endpoint behavior using M2 rather than reimplementing M2 arithmetic.

**DM5-4 — Relative transforms:** parent-to-child \`Pose.then()\` order; world pose supplied as a snapshot per evaluation; rotation/translation/scale controls tied to progress. Define finite-result failures and scale restrictions.

**DM5-5 — Layering and ordering:** deterministic evaluation ordering across groups, clips and particles; stable ties; hide inactive layers; duplicate layers and empty layers permitted or forbidden, explicitly decided.

**DM5-6 — Particle semantics:** a logical \`style\` key resolved only in an adapter. What metadata is useful without becoming Paper-specific? How to represent true single-point placement, opacity/color as future optional keys, or leave them deferred.

**DM5-7 — Sampling reuse:** sample once and transform repeatedly where possible. Specify immutable sample-set reuse versus dynamic resampling and make derivative-evaluation budgets explicit.

**DM5-8 — Budget preflight:** explicitly bounded max nodes, layers, depth, points/frame and intermediate allocations; no overflow in counts; atomic fail-before-allocating or bounded truncation, with deterministic order. Do **not** silently exhaust host memory.

**DM5-9 — Lifecycle semantics:** M5 frames are snapshots, not independently scheduled processes. Define whether and how scene-level completion/empty frames are reported.

**DM5-10 — Thread safety:** immutable scenes and frames, no global mutable counters/caches, safe multithreaded reads with caller-owned thread-safe input; no engine background worker.

**DM5-11 — Precision and determinism:** IEEE-754 order, zero/sign conventions, disallow NaN/Infinity in authored transforms where required, distinguish mathematical ground truth vs renderer approximation.

**DM5-12 — Scope:** choose what must be included in M5 MVP and what can wait. Avoid a giant generic graph editor or effect scripting language.

## Candidate vertical-slice demos (required as contract examples)

**A. Healing ritual:** 3 visibly separated expanding ring pulses, each with deterministic progress and explicit scene-end behavior. Output visual positions only; healing mechanics remain outside.

**B. Orbiting sparkle group:** one anchor snapshot with multiple rotating nodes, stable phase offsets and optional bounded trails; verify pose/rotation composition.

**C. Moving water-trident helix:** predefined local-space helix transforms to caller-provided moving trident position/orientation. M5 never reads a Bukkit entity.

**D. Astral Prism constellation:** rotating nodes plus thin sampled line connections across phases; ensure layering/density budgets and reproducibility.

All four can be specified using invented abstract style keys and exact frames; no actual Minecraft VFX imagery is required.

## Required acceptance evidence for Claude's M5 R1 proposal

1. \`docs/m5/M5-ARCHITECTURE.md\`, \`M5-DESIGN-DECISIONS.md\`, \`M5-CONVENTIONS.md\`, \`M5-IMPLEMENTATION-CONTRACT.md\`, \`M5-TEST-SPECIFICATION.md\`, \`M5-RISKS.md\`, and \`README-FOR-CODEX-M5.md\`.
2. Exact proposed type names, signatures, packages, mutability, validation, error behavior, sample evaluation order, limits and public API review.
3. Independent golden/reference fixture producer (Python standard library preferred); reference frames for all four demos, including boundary instants, repeated cycles, inverted transforms and extreme valid input.
4. Adversarial tests for overlapping animations, inactive layers, empty scenes, huge counts, cancellation/state distinction, 0/1 samples, extremely large/small tick values, GAME/COSMETIC domain mismatch, signed-zero behavior, and deterministic ordering.
5. Mutation testing/reasoned survivor analysis when practical; tests must not only duplicate the proposed implementation's computation.
6. Java 25 / Gradle 9.1.0 **temporary throwaway reference implementation may be used solely to validate the test suite, but must not be delivered as accepted M5 production**.
7. All existing 313 M1–M4 tests, checksums, reference files and runtime independence unchanged; clear before/after file diff.
8. SHA-256 integrity-verified proposal ZIP + complete Git bundle, or publish the design branch to GitHub if access is available. Do not repeatedly attempt forbidden GitHub pushes.
9. Explicit remaining open decisions, mathematical limitations, actual executed tests vs assumptions. No unsupported claims of Paper compatibility.
10. Stop for **independent ChatGPT design acceptance**, then owner authorization of Codex implementation.

## Recommended delivery phases

- **M5 R1 (current):** independently derived design, normative proposed contract, reference vectors and adversarial tests. No production.
- **M5 R2 (if needed):** focused corrections to independently reproduced findings.
- **M5 production:** Codex implements exact approved contract, independently verified on Java 25 in GitHub Actions.
- **Later Paper adapter:** separately designed and tested on real disposable Paper servers.

## Acceptance / stop conditions

- This document is an **R0 kickoff brief**, not a binding numeric implementation specification.
- Do not turn candidate type names, limits, architecture or demo details into a public API without independent review.
- Do not expand scope to rendering or Paper integration merely because it is adjacent.
- No merge to \`main\`; do not modify current M1–M4 or accepted verified snapshot.
