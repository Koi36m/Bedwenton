# M5 architecture proposal: effect composition and animation

Status: **PROPOSED (revision 1), not approved** (see `M5-DESIGN-DECISIONS.md`).
- Baseline: accepted M4 snapshot `e3bf5578c126a7d3498be0b30fb431d9ec9a3099`, which passed 313/313 tests in GitHub
  Actions run 38974334716 (reported by the kickoff).
- Kickoff: `6ada39e3a383f6b2df60e6744a39d37d9f24eb13`, branch `m5-design-kickoff`.
- This proposal is on branch `m5-spec`, created from the kickoff commit.

## 1. Purpose and boundary

M5 answers one question, and only that question:

> Given an explicit instant (M2 `Playback` + `TimePoint`) and an anchor pose snapshot (M1 `Pose`), which particle
> positions, grouped under abstract style keys, exist in this frame, and in which order?

It decides no particle type, viewer, scheduler, network packet, gameplay effect or server budget. Those belong to
later milestones (section 6).

```
workshop.vfx.math.core    (M1, unchanged)  Vec3, Quat, Pose, Frame
workshop.vfx.math.time    (M2, unchanged)  TimePoint, Playback, Timing, TimingSample
workshop.vfx.math.curve   (M3, unchanged)  Curve, Spiral, Beziers, LineSegment, Easing
workshop.vfx.math.sample  (M4, unchanged)  Sampler, SampleSet, ArcLengthTable
workshop.vfx.scene        (M5, new)        StyleKey, Shape, Motion, Visibility, Reveal, Layer, SceneLimits,
                                           EffectScene, EffectFrame, FrameBatch
                                           depends on the four packages above; nothing depends on it
```

[pref] `workshop.vfx.scene` sits outside `workshop.vfx.math`, because scene composition is not a math primitive. It
maps onto the roadmap's future `vfx-scene` module and can be extracted without renaming. It stays in the existing
Gradle project for now: the roadmap says not to restructure modules before a verified migration gate.

## 2. Data flow per frame

```
build once:  Curve (M3) --Sampler / ArcLengthTable (M4)--> SampleSet --Shape.of--> Shape (packed local points)
             Motion.fixed / spin / scale / translate (+ then) --> Motion (shareable)
             Layer(name, Timing, Visibility, Reveal, Motion, Shape, StyleKey) x N --> EffectScene.of (preflight)

per tick:    caller: anchor Pose snapshot + Playback + TimePoint
             EffectScene.evaluate
               L = playback.localTicks(now)                     (M2)
               scene window: BEFORE / ACTIVE / AFTER            (comparisons only)
               for each layer, in order:
                 s = layer.timing.sample(L)                     (M2) -> visible? how many points (reveal)?
                 P = anchor.then(motion.poseAt(L))              (M1 Pose.then; M2 drives; M3 easing; StrictMath spin)
                 world_j = P.toWorld(local_j)                   (M1)
               -> EffectFrame(stage, L, [FrameBatch(layer, style, s, P, x,y,z...)])
             caller / future adapter: for each batch, resolve style -> particle type, emit one particle per point
```

## 3. Public API (summary; normative in the contract, section 2)

| Type | Role |
|---|---|
| `StyleKey` | abstract visual style, `namespace:path`, resolved only by an adapter |
| `Shape` | immutable local points (from an M4 `SampleSet`, a list or a single point), packed |
| `Motion` | shareable chain of up to 16 steps (fixed, spin, scale, translate), each with its own M2 drive and M3 easing |
| `Visibility` | `ACTIVE_ONLY` or `HOLD_AFTER` (keep the final state after the layer's clip ends) |
| `Reveal` | `ALL` or `PREFIX` (draw the first `floor(progress n)` points) |
| `Layer` | a clip: name, M2 `Timing`, visibility, reveal, motion, shape, style |
| `SceneLimits` | hard per-scene ceilings: layers (at most 4,096) and points per frame (at most 2^20) |
| `EffectScene` | immutable, validated, preflighted scene; `evaluate(anchor, playback, now)` |
| `EffectFrame` | immutable snapshot: scene stage, local ticks, batches, point count |
| `FrameBatch` | one visible layer: index, name, style, M2 sample, world pose, packed coordinates |

Ten types, all needed by the four demos. The reasoning for each is in the design decisions, and the smallest-API
review is in DM5-12.

## 4. Phase C: the four example effects (exact definitions: test specification, section 4)

| Effect | Construction | What it exercises |
|---|---|---|
| **A. Healing ritual** | 3 layers `pulse-k`, clips `once(16)` at delays 0, 20 and 40 (visibly separated by 4-tick gaps); one 24-point unit ring shared by all; motion `translate(0.1 -> 1.0 up).then(scale(0.5 -> 3.0, QUAD_OUT))`; scene lifetime 60 | separate pulses, eased expansion, half-open ends, empty ACTIVE gaps, AFTER at 60; reverse, paused and half-rate playback; a rotated and scaled anchor |
| **B. Orbiting sparkles** | 4 one-point layers `spark-k`, each `spin(Y, 1 turn per 40 ticks, phase k/4)`; a 3-ghost trail for spark 0 (phases -0.03j, style `spark_trail`); COSMETIC, `UNBOUNDED` | phase offsets, stateless bounded trails, moving and turning anchors, the COSMETIC nanosecond wrap |
| **C. Moving trident helix** | one layer: an M4 arc-length-sampled helix (radius 0.6, rise 2, 3 turns, 37 points); `fixed(down 1).then(spin(Y, 2 turns over 30))`; `Reveal.PREFIX`; lifetime 30 | M4 reuse (no curve work per frame), self-drawing, a caller-supplied trident pose per tick (M5 never reads an entity) |
| **D. Astral Prism constellation** | a 5-star ring and 5 pentagram links (9 points each, M4 line sampling); all share `group = spin(Y, 1 turn per 120).then(fixed(tilt))`; links staged at 10 + 12j with `PREFIX` + `HOLD_AFTER` | transform grouping by a shared motion, staged reveal, held states, layering and density budget, seek |

[fact] All four are reproduced by an independent 40-digit Python oracle (`DemoReferenceTest`, 1,113 reference rows),
and their point counts are within their `SceneLimits`.

## 5. Platform independence and the later Paper adapter

- M5 imports no Bukkit or Paper types (`M5BoundaryTest.imports`).
- A frame is plain data: style keys, doubles, M1 poses and M2 samples. Translating it needs no M5 change:
  - map `StyleKey` to a particle type and options;
  - map a batch to a loop of single-particle emissions at the exact coordinates (with Paper's `count = 0` semantics,
    to be verified on a real server: roadmap P3);
  - pick viewers and the thread or region.
- What M5 deliberately does **not** claim: any Paper version compatibility, particle fidelity, client behavior, or
  Bedrock/Geyser behavior. These require the roadmap's real-server gates.
- Heavy pure math may run off the main thread on immutable snapshots: the anchor pose must be captured on the owning
  thread first. That scheduling is the adapter's.

## 6. Scope boundary and deferred items

| Item | Proposed home | Reason |
|---|---|---|
| Paper/Bukkit/Folia adapter, particle mapping, viewers, scheduler, emission | P3 adapter | platform-specific; needs real-server verification |
| gameplay (triggers, cooldowns, healing, damage, ownership) | P4 | must stay separate from visuals |
| runtime effect manager, sessions, cancellation API, caches | P2/P3 | stateful |
| per-tick, per-player and per-world budgets, quality presets, thinning | P2 | server policy; M5 has the hard per-frame bound |
| seeded randomness and jitter; disk and sphere distributions | M4.1 | needs a specified generator and statistical oracles |
| color, opacity, size, sound, light | with the adapter contract | needs Paper particle data audit |
| keyframe tracks, curve-following motion, per-frame resampling, scene trees | M5.1 if needed | each adds per-frame curve work or nesting rules |

## 7. Deliverables of this proposal

| Item | Location |
|---|---|
| architecture | `docs/m5/M5-ARCHITECTURE.md` |
| design decisions DM5-1 to DM5-12 | `docs/m5/M5-DESIGN-DECISIONS.md` |
| conventions | `docs/m5/M5-CONVENTIONS.md` |
| normative implementation contract (exact API signatures) | `docs/m5/M5-IMPLEMENTATION-CONTRACT.md` |
| test specification and example-scene definitions | `docs/m5/M5-TEST-SPECIFICATION.md` |
| numerical and performance risks | `docs/m5/M5-RISKS.md` |
| Codex handoff guide | `docs/m5/README-FOR-CODEX-M5.md` |
| independent tests | `src/test/java/workshop/vfx/scene/` (50 tests in 9 classes, plus a helper) |
| independent reference frames and generator | `src/test/resources/m5/reference/` (4 CSVs, 1,113 rows, manifest), `tools/generate_m5_reference_frames.py` |
| validation evidence | `docs/m5/validation/` |

## 8. Open decisions, limitations, and what was executed vs assumed

**Open decisions for the reviewer and owner** (each has a recommended default in the design decisions):
1. **Package name** `workshop.vfx.scene` (DM5-12, section 1), versus `workshop.vfx.math.scene`, or a separate Gradle
   module now.
2. **Flat layers with shared motions** (DM5-1), versus a node tree. A tree can be added later as a builder that
   flattens.
3. **Ceilings** `maxLayers <= 4096`, `maxPointsPerFrame <= 2^20`, `MAX_STEPS = 16`, step scales `[1e-9, 1e9]`,
   `abs(turns) <= 2^20` (DM5-8). These are proposals; the demos need far less.
4. **Whether color, opacity or size enter M5** before the Paper particle-data audit (DM5-6). The recommendation is no.
5. **Rejection versus truncation** of over-budget scenes (DM5-8). The recommendation is rejection at construction.

**Mathematical limitations:**
- Positions are validated against oracles within `2^-40 (1 + M)(1 + abs(turns))`, not proven to a tight bound
  (contract 10.3).
- The spin angle precision degrades linearly with `abs(turns)` per pass.
- The sign of zero coordinates is not significant.
- M2's `FOREVER` loops end after about 9.2e18 passes (conventions 3).

**Executed (on this container, Java 25.0.4.1, Gradle 9.1.0):**
- the full suite against a throwaway reference implementation: 363/363 (313 M1 to M4 + 50 M5), and `check`;
- byte-identical regeneration of the 1,113 reference rows;
- mutation analysis of the reference (`validation/mutation-results.txt`);
- the frame-layout and performance probes.

**Not executed / assumed:**
- **No Paper, Bukkit or Folia code or server was run.** There is no claim of compatibility with any Paper version,
  particle type, `count` semantics, client or Bedrock behavior.
- No production M5 code exists. The reference implementation only validates the tests.
- The GitHub Actions run of the 313-test baseline is as reported by the kickoff; it was not re-run here.
- Timings are indicative, from one container.
