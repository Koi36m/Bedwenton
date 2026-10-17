# M5 design decisions: effect composition and animation

Status: **PROPOSED (revision 1), not approved.** The contract and tests implement the **recommended** option of each
decision. Owner approval follows ChatGPT's independent review.

- Baseline: accepted M4 snapshot `e3bf5578c126a7d3498be0b30fb431d9ec9a3099` (313/313 tests).
- Kickoff: `6ada39e3a383f6b2df60e6744a39d37d9f24eb13` (`docs/m5/M5-DESIGN-KICKOFF.md`).

Notation:
- **[fact]**: checked by a test, probe or derivation (cited).
- **[pref]**: a design judgement.

## Summary

| ID | Decision | Recommended | Main alternatives |
|---|---|---|---|
| DM5-1 | Scene abstraction | a flat ordered list of `Layer`s (clip = M2 `Timing`; geometry = `Shape`; transform = shareable `Motion` chain) | node tree / scene graph; timeline with tracks; scripting |
| DM5-2 | Rendering contract | `EffectFrame` of `FrameBatch`es, one per visible layer, with packed `double` coordinates | one record per particle; batches holding `List<Vec3>` |
| DM5-3 | Time ownership | caller passes `Playback` + `TimePoint`; the scene declares its `TimeDomain`; M2 does all time arithmetic | raw local ticks; scene-owned clock; delta accumulation |
| DM5-4 | Relative transforms | anchor `Pose` snapshot per call; `Motion` steps (fixed, spin, scale, translate) composed with M1 `Pose.then`, each driven by its own M2 `Timing` and M3 `Easing` | per-layer single progress; matrices; Euler angles; keyframe tracks |
| DM5-5 | Layering and ordering | insertion order, stable; unique names; hidden and empty layers omitted; empty scenes allowed | priorities; z-order; named groups |
| DM5-6 | Particle semantics | `StyleKey("namespace:path")`, resolved only by an adapter; one point = one particle at exactly that position; no color or opacity yet | Bukkit `Particle` enum; free-form metadata maps; color and opacity now |
| DM5-7 | Sampling reuse | shapes sampled once (M4) at build time and copied; `evaluate` makes 0 curve calls; motion moves them | per-frame resampling; lazy caches |
| DM5-8 | Budget preflight | limits checked at construction (sum of shape sizes in `long`); `evaluate` cannot exceed them; reject, never truncate | runtime truncation; thinning; soft limits |
| DM5-9 | Lifecycle | stateless snapshots; scene window (`lifetimeTicks`, or `UNBOUNDED`) gives BEFORE, ACTIVE or AFTER; cancellation belongs to the caller | process or session objects; completion callbacks |
| DM5-10 | Thread safety | immutable values, no statics, no workers | locks; thread-local caches |
| DM5-11 | Precision and determinism | normative operation order; `StrictMath` trigonometry with turn reduction; NaN and infinity rejected; validated tolerance against a 40-digit oracle | `Math` trigonometry; M1 `Quat.axisAngle`; tolerance-only contract |
| DM5-12 | Scope | the 10 types above; defer adapters, gameplay, randomness, color, surfaces and per-frame resampling | a generic graph editor, expressions, scripting |

## DM5-1. Scene abstraction

- **Recommended:** `EffectScene` holds an ordered list of `Layer`s. A layer combines:
  - a clip: an M2 `Timing`, which gives its own delay, duration and repeat mode;
  - what is shown: a `Shape` of local points and a `StyleKey`;
  - where it is: a `Motion`;
  - how it starts and ends: `Visibility` and `Reveal`.

  The four kickoff demos need only this (see the test specification, section 4).
- **Transform grouping without a tree.** A `Motion` is an immutable value. Composing `Motion`s with `then` and
  **sharing** one value between layers is the grouping. The Astral Prism stars and its five staged links share one
  `group` motion while having five different clip timings.
  - [fact] `CompositionTest.sharedMotion`: one motion reused by layers with different timings gives an identical pose
    in each.
- Each motion step carries its **own drive `Timing`**. A group's rotation therefore runs on its own clock, independent
  of when each member layer is visible. Without this, staged layers that share a rotation would each rotate from
  their own start. [fact] This is the constellation fixture, whose links appear at local ticks 10, 22, 34, 46 and 58
  on one spinning ring.
- **Alternatives:**
  - A node tree (groups with children and their own clips) adds parent clip scoping, inherited visibility, cycles
    and depth validation, and recursion in evaluation. For the same expressive power on the demos, a flat list plus
    shareable motions is smaller and easier to bound. A tree can be added later as a builder that flattens into
    layers, without changing frames.
  - A timeline with tracks and keyframes needs interpolation policies and an editor format. Out of scope (DM5-12).
  - Scripting or expressions: rejected (kickoff).

## DM5-2. Rendering contract

- **Recommended:** `EffectFrame` holds an unmodifiable list of `FrameBatch`es, one per **visible, non-empty** layer,
  in layer order. A batch carries:
  - its layer index and name, its `StyleKey`, the layer's M2 `TimingSample` and the world `Pose`;
  - the positions as a **packed** `double[]` (x0, y0, z0, x1, ...).
- [fact] Measured by `validation/FrameLayoutProbe.java` (`validation/frame-layout.txt`): 10,000 points per frame,
  400 frames, warm JIT, Java 25.0.4.1:

  | Layout | Bytes per point | ns per point |
  |---|---|---|
  | per-particle records | 128.9 | 25.9 |
  | batch with `List<Vec3>` | 130.8 | 31.2 |
  | batch with packed doubles | **24.1** | **8.5** |

  24 bytes is the floor for three doubles. The packed layout allocates about 5 times less, and is about 3 times
  faster.
- [pref] Batches match how a Paper adapter emits: one particle type and options per batch, then a loop over
  positions. The adapter then only translates.
- The batch exposes its world pose, so an adapter can orient directional particles (`pose().directionToWorld`)
  without M5 defining particle directions.
- **Alternatives:**
  - per-particle records: simplest to read, but 5 times the allocation (above);
  - `List<Vec3>` batches: no smaller than per-particle records, because the `Vec3`s dominate.

## DM5-3. Time ownership

- **Recommended:** `evaluate(anchor, playback, now)`.
  - The caller owns the M2 `Playback` (start, pause, resume, seek, rate, restart) and supplies the `TimePoint`.
  - The scene declares its `TimeDomain`, and both the playback and the instant must match it (IAE).
  - Local ticks come only from `Playback.localTicks(now)`, and layer and drive progress only from `Timing.sample`.
  - M5 adds only the scene-window comparison (contract 7).
- **Endpoints and directions:** all from M2.
  - Passes are half-open: active at the start, not at the end.
  - AFTER reports progress 1, or 0 after an even number of ping-pong passes.
  - Negative rates play backwards; a zero rate freezes.
  - Seek and pause are `Playback` controls. COSMETIC nanoseconds wrap like Java `long`; GAME tick overflow is an
    `ArithmeticException`.
  - [fact] `TimeSemanticsTest` checks each of these through the public API.
- **Alternatives:**
  - Raw local ticks lose the domain check, which is a GAME/COSMETIC mix-up guard.
  - A scene-owned clock or delta accumulation is hidden state, which the kickoff forbids, and drifts.

## DM5-4. Relative transforms

- **Recommended:**
  - The world pose of a layer is `anchor.then(motion.poseAt(L))` (M1 `Pose.then`, parent first).
  - A motion is a chain of at most 16 steps:
    - `fixed(pose)`;
    - `spin(axis, turns, phase, drive, easing)`;
    - `scale(from, to, drive, easing)`;
    - `translate(from, to, drive, easing)`.
  - Each animated step takes the progress of its own M2 drive, through an M3 easing.
  - The anchor is a snapshot supplied per call; M5 never reads an entity.
- **Scale restrictions.** M1 requires a positive finite scale, which excludes mirroring. Step scales lie in
  `[1e-9, 1e9]`, so a lerp of two valid scales stays positive and finite. Products that overflow give M1's
  `ArithmeticException`, as does a non-finite world point (contract 8.5), and no frame is returned.
- [fact] Exact endpoints: scale and translate return `from` exactly at progress 0, and `to` exactly at progress 1
  (`CompositionTest.endpoints`).
- [fact] The motion order matches an independent matrix oracle on 1,500 random chains of up to 6 steps
  (`CompositionTest.randomCompositions`).
- **Alternatives:**
  - A single progress per layer for all motions fails grouped rotation (DM5-1).
  - Matrices duplicate M1.
  - Euler angles have order ambiguity and gimbal lock.
  - Keyframe tracks are deferred (DM5-12).
  - Curve-following motion (moving along an M3 curve) is deferred: it needs per-frame curve evaluation (DM5-7).

## DM5-5. Layering and ordering

- **Recommended:**
  - Batches follow insertion order, and points follow shape order. There are no priorities or z-order: a particle
    renderer has none.
  - Layer names are unique (IAE), which gives stable identifiers for diagnostics and adapters. Duplicate geometry and
    timing are allowed: overlapping animations are just layers.
  - Hidden layers (BEFORE, or AFTER with ACTIVE_ONLY) and empty batches are omitted. Empty scenes are allowed, and
    give empty ACTIVE frames.
  - [fact] `FrameContractTest.order`, `.omission`; `M5AdversarialTest.overlapping`.
- **Alternatives:**
  - priorities: unnecessary without z-order;
  - emitting empty batches: adapters would have to filter them, so omission is simpler and deterministic.

## DM5-6. Particle semantics

- **Recommended:** a `StyleKey` (`namespace:path`, lower-case ASCII) names a visual style. Only an adapter resolves
  it, for example `bedwenton:heal_ring` to a Paper particle type and its options.
  - **One point is one particle at exactly that position**, with no spread. The Paper `count = 0` subtlety is the
    adapter's (roadmap P3).
  - Color, opacity and size are **not** in M5 R1. They become optional keys only when an adapter contract needs
    them. Meanwhile a style key can encode a fixed variant (for example `astral:link`).
- **Alternatives:**
  - The Bukkit `Particle` enum makes M5 Paper-dependent: forbidden.
  - A free-form metadata map is untyped, with no validation and no determinism story.
  - Color and opacity now means animated color is deferred by the kickoff.

## DM5-7. Sampling reuse

- **Recommended:**
  - Shapes are built **once**, normally from an M4 `SampleSet` (uniform parameter, uniform distance or spacing), and
    copied into a packed array.
  - `evaluate` makes **0** curve calls; motion moves the precomputed points.
  - Derivative budgets are therefore entirely M4's, spent at build time, and never per frame.
  - [fact] `BudgetTest.noCurveWork`: 1,000 frames add 0 curve calls.
- **Dynamic geometry** within that rule:
  - expanding rings use `scale`;
  - drawing a path uses `Reveal.PREFIX`;
  - orbiting uses `spin`;
  - stateless trails are lagging copies (phase offsets).
- **Alternatives:**
  - Per-frame resampling (for example a line between two moving entities) has unbounded per-frame derivative work.
    Deferred: a caller can rebuild a scene when its geometry changes.
  - Lazy caches are mutable state (DM5-10).

## DM5-8. Budget preflight

- **Recommended:**
  - `SceneLimits(maxLayers <= 4096, maxPointsPerFrame <= 2^20)`.
  - The scene computes the sum of its shape sizes in `long` at construction, and rejects it if over the limit. Since
    every batch is a prefix of its shape, `evaluate` can never exceed that sum.
  - Motion depth is at most 16. Work and allocation are therefore bounded per call (contract 9).
  - Rejection is atomic and happens before any frame exists. There is no silent truncation.
  - [fact] `BudgetTest`: 4,096 layers of 256 points fill exactly 2^20; one more point per layer fails; one layer of
    2^20 points evaluates.
- [pref] Runtime thinning and per-player or per-world budgets are server policy (roadmap P2). M5's bound is the hard
  per-evaluation ceiling the kickoff requires.

## DM5-9. Lifecycle

- **Recommended:** frames are snapshots. The scene window is `[0, lifetimeTicks)` in local ticks, and the frame stage
  reports it:
  - **BEFORE:** not started (empty);
  - **ACTIVE:** running, possibly empty between pulses;
  - **AFTER:** ended, always empty, even with `HOLD_AFTER` layers.

  `UNBOUNDED` scenes never end on their own: persistent ambient effects.
- **Cancellation** is the caller's: stopping means no longer evaluating. Pausing is `Playback.pause`. M5 keeps no
  state, so evaluation order never matters. [fact] `M5AdversarialTest.statelessFrames`.
- **Alternatives:** a session object with `cancel()` is mutable state, and is deferred to a runtime manager.

## DM5-10. Thread safety

- **Recommended:** immutable values throughout (defensive copies; unmodifiable lists), no static mutable state, no
  background work.
  - A scene may be evaluated concurrently with different anchors and times.
  - Thread safety of caller-provided objects is moot, because M5 copies shapes at build time and never calls curves
    afterwards.
  - [fact] `M5DeterminismTest.threads` (8 threads, shared scenes); `M5BoundaryTest.forbidden` (no mutable statics,
    no threads).

## DM5-11. Precision and determinism

- **Recommended:**
  - a normative operation order (contract 5.2 and 8);
  - spin quaternions built from `StrictMath.sin` and `StrictMath.cos` after reducing the angle in **turns** (the M3
    `Spiral` technique);
  - every authored NaN or infinity rejected;
  - accuracy validated against a 40-digit oracle, separate from the normative order.
- [fact] M1 `Quat.axisAngle` uses `Math.sin` and `Math.cos`, which may differ by 1 ulp between JVMs (M3 conventions
  F4). M5 therefore builds spin quaternions itself.
- [fact] Whole-turn reduction flips the quaternion's sign. M1 `Quat.rotate` gives bit-identical results for q and -q:
  the two sign changes cancel in `uv * 2w` and `u x uv`. Reduction therefore changes no position.
- [fact] Turn reduction keeps quarter turns exact in direction (`CompositionTest.spin`) and keeps precision for large
  turn counts: the angle error is linear in `abs(turns)`.
- **Alternatives:**
  - M1 `Quat.axisAngle`: not bit-reproducible across JVMs.
  - A tolerance-only contract allows divergent frames between servers, which means visible desynchronization of
    shared effects.

## DM5-12. Scope

| Included in M5 R1 | Deferred (to) |
|---|---|
| 10 public types; flat layers; fixed, spin, scale and translate motions; reveal prefix; hold-after; scene window; style keys; packed batches; limits | Paper adapter, viewers, scheduling, emission (P3); gameplay (P4); runtime manager, budgets, presets (P2) |
| reuse of M1 `Pose`, M2 `Playback`/`Timing`/`TimingSample`, M3 `Easing`, M4 `SampleSet` | randomness and jitter, disk and sphere distributions (M4.1); color, opacity, size, sound, light (with adapter contracts) |
| four executable example scenes as reference oracles | keyframe tracks, curve-following motion, per-frame resampling, scene trees (M5.1 if needed) |

[pref] This is the smallest API that expresses all four kickoff demos exactly. Each deferred item adds either
per-frame curve work, state or platform coupling.

## What changes if the owner picks an alternative

| If | Revise |
|---|---|
| DM5-1 uses a node tree | contract 2, 6 and 8; a builder could flatten to the same frames; tests unaffected except API shape |
| DM5-2 uses per-particle records | `FrameBatch` and `EffectFrame`; the frame tests; the reference comparison is unaffected |
| DM5-3 takes raw local ticks | the domain checks and `TimeSemanticsTest.domains` |
| DM5-6 adds color and opacity | a new optional style payload type and its validation; no change to positions |
| DM5-7 allows per-frame resampling | a new shape kind with an M4 budget per frame; contract 9 work bounds |
| DM5-8 truncates instead of rejecting | contract 6 and 9; `BudgetTest.preflight` |
