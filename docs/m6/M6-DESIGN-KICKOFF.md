# M6 — Runtime Safety and Particle Delivery Planning

**Status:** R0 DESIGN KICKOFF ONLY — no M6 production implementation approved.
**Branch:** \`m6-runtime-readiness-design\`
**Accepted M5 source snapshot:** \`d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb\`
**Verified source tree:** \`43d1300ab42105c010dd43bfc89ee476491fcdaa\`
**M5 verification:** https://github.com/Koi36m/Bedwenton/actions/runs/38993397487 — 363/363 Java 25 tests and 6,936 independently regenerated M3/M4/M5 reference rows.
**Repository:** https://github.com/Koi36m/Bedwenton

## Mission

Prepare a **pure-Java, deterministic, bounded delivery-planning layer** between M5's abstract effect frames and a future Paper particle adapter. M6 should make future Minecraft enchantment VFX inexpensive to author, straightforward to tune, and safe under multiplayer load.

**Important:** M5's limit of 2^20 generated coordinates per frame (24 MiB for packed xyz alone) is a mathematical construction ceiling, **not** a safe operational particle or packet budget. M6 must distinguish frame generation, retained logical particle points, emission calls, intended recipients, actual transmissions and client-side work. No invented "safe" MSPT or particle/network constants.

Intended future flow:

\`\`\`
Effect recipe + selected density tier (Simple/Balanced/Heavy)
    -> immutable capability/viewer/quota snapshots
    -> preflight: skip, defer, or choose a bounded density scene
    -> M5 EffectScene.evaluate(anchor, playback, now)
    -> deterministic selection/admission plan
    -> separately implemented Paper adapter resolves style, viewer, scheduler
    -> emission outcomes and diagnostics
\`\`\`

This is not a guarantee that future Paper versions are automatically compatible. Paper-facing integrations, scheduling, particle payloads and actual emission belong entirely to a separately versioned adapter.

## Frozen baseline and existing APIs

- **Do not change M1–M5** production Java, tests, reference fixtures, public signatures, numerical behavior, reference generators or accepted GitHub verification evidence.
- Relevant M5 types: \`EffectScene\` (\`maxPointsPerFrame()\`, \`evaluate(Pose,Playback,TimePoint)\`); \`EffectFrame\` (\`batches()\`, \`pointCount()\`, \`stage()\`); \`FrameBatch\` (\`layerIndex()\`, \`style()\`, \`size()\`, \`x(i)\`, \`y(i)\`, \`z(i)\`); \`StyleKey\` (neutral style identifier).
- M5 positions are immutable; \`FrameBatch.coordinates()\` returns a defensive clone. Avoid unnecessary cloning during M6 planning.
- M2 owns clock/timing semantics. Any frame sequence ordinal/priority/group ID must be explicit caller-supplied data, not a hidden mutable counter or wall clock.
- M1–M5 must remain independent of Minecraft, Paper, Bukkit, Folia, NMS and client mods.
- Accepted M5 GitHub source snapshot is distinct from the unpublished original local implementation commit \`d5a8868...\`; preserve provenance labels correctly.

## Proposed M6 MVP capabilities (design decisions pending)

1. **Quality profiles:** per effect choose Simple, Balanced or Heavy. Distinguish *geometry density* from *emission quotas*; permit effect-specific overrides. Balanced can be the default authoring tier, but do not fix untested numeric server caps.
2. **Pre-evaluation safety:** reject, skip or choose a cheaper scene from immutable bounds **before** calling \`EffectScene.evaluate\`. Post-frame point thinning alone cannot prevent expensive M5 frame allocations.
3. **Post-frame deterministic selection:** choose selected point indices, bounded per-layer quotas, and stable order; the plan should not duplicate all x/y/z arrays.
4. **Budget hierarchy:** clearly define how per-effect, per-world, per-viewer and server/tick quotas interact; use overflow-safe \`long\` operations; allow an effect with zero eligible viewers to do zero frame work.
5. **Fairness:** compare round-robin/priority and stable tie-breaking. Preserve essential action effects without indefinite starvation of low-priority ambient effects; state only guarantees proved by the specified algorithm.
6. **Visual continuity:** prevent repeatedly selecting only the same sector of a ring/spiral; if deterministic index phase rotation is chosen, derive solely from caller input and justify visible behavior.
7. **Output accounting:** separate source points, kept points, intended recipients, logical per-recipient point placements, adapter calls attempted, successful calls and errors. The pure planner cannot know actual packets or client FPS.
8. **Explainable degradation:** immutable plan with reason codes and counters for admitted/thinned/skipped/unsupported/no viewers/over budget. Visual drops never affect game mechanics, item owners, cooldowns, permission or damage/healing.
9. **Capabilities:** style support and audience filtering arrive as immutable platform-neutral snapshots. Unknown visual styles fail visually closed; no direct Paper enum or registry types in M6.
10. **Memory and concurrency:** reject malformed/oversize dimensions before allocations proportional to demand; deterministic output across repeated/concurrent pure evaluations. Any stateful per-tick ledger requires declared ownership, sorting, snapshot semantics and clear synchronization assumptions.
11. **Lifecycle boundary:** effect start/stop, task scheduling, unload, player logout, teleport, world unload and plugin reload are runtime host/adapter responsibilities. M6 must not hold a Bukkit Player, World, Entity, Scheduler or Location object.
12. **Performance visibility:** counts, policy decisions, failure reasons and reproducible synthetic benchmarks. No speculative client, network or server performance claims.

## Required explicit M6 R1 design decisions

**DM6-1:** minimal public package/types and exact Java 25 API signature; prefer pure immutable design over large runtime management framework.

**DM6-2:** preflight admission using \`EffectScene.maxPointsPerFrame()\`, alternative density scenes and zero-viewer scenarios; distinguish conservative versus exact visible costs.

**DM6-3:** precise dimensions for generated, retained, delivered and attempted work, without conflating logical points and packets.

**DM6-4:** overflow and integer policy, quota hierarchy, zero and negative input validation; prove no valid plan can exceed its admitted limits.

**DM6-5:** fairness and priority ordering, tie behavior and what starvation guarantees can honestly be claimed.

**DM6-6:** deterministic thinning/index staggering across frames; avoid visual clumping and repeated "missing ring sector".

**DM6-7:** compact immutable selection-plan representation, bounded memory and iteration cost; no full-frame coordinate copying in the planner.

**DM6-8:** unsupported style and viewer snapshot semantics; avoid evaluating a heavy scene when no authorized recipient exists.

**DM6-9:** caller-supplied frame ordinal/cycle ID and interaction with M2 GAME/COSMETIC time domains without reinventing M2.

**DM6-10:** if budget ledgers are stateful, scope single/multi-thread ownership, per-world coordination, atomicity and cancellation.

**DM6-11:** distinguish neutral engine policy, Paper configuration, named quality presets and per-enchantment geometry density.

**DM6-12:** independent adversarial oracles with integer extremes, fixed memory upper bounds, deterministic evaluation and measurement methodology.

**DM6-13:** keep Paper adapter responsibilities in a separate interface proposal and do not assert Folia support without actual scheduler tests.

## Required scene-level and stress examples

- **Bloom:** many small ambient flowers near moving players; low-priority burst admission.
- **Cloudstep:** walking/sprinting clouds, overlapping activations, limited per-player viewer demand.
- **Three-pulse healer:** preserve salient high-priority pulse markers while reducing decorative particles; the actual healing remains unaffected.
- **Moving trident helix:** moving anchor, varying viewer count, and world switch; no M6 access to live entity state.
- **Astral Prism:** multiple layers of essential nodes, connections and optional trails; degrade trails first.
- **Stress:** zero viewers, one and many viewers, multiple simultaneous effects, the full M5 2^20-point source frame, exact quota boundaries, huge and negative integers, many layers, concurrent read determinism and no unexpectedly proportional allocations during denied preflight.

## M6 R1 deliverables and gate

Produce:
- \`docs/m6/M6-ARCHITECTURE.md\`
- \`docs/m6/M6-DESIGN-DECISIONS.md\`
- \`docs/m6/M6-CONVENTIONS.md\`
- \`docs/m6/M6-IMPLEMENTATION-CONTRACT.md\`
- \`docs/m6/M6-TEST-SPECIFICATION.md\`
- \`docs/m6/M6-RISKS.md\`
- \`docs/m6/README-FOR-CODEX-M6.md\`
- Exact public type/signature/validation/error rules and numerical/ordering/limit guarantees.
- Independent reference fixture generator and reference outputs covering all six case families.
- Java 25 contract and adversarial tests. All previously accepted 363 tests and frozen reference vectors must remain unchanged.
- Optional temporary throwaway reference implementation only to verify oracles. It is **not** approved production.
- SHA-256 manifests, changeset/provenance proof, reproducible performance evidence, and recoverable ZIP/Git bundle or GitHub-published branch.
- Explicit "what ran vs what was assumed" report.

**Stop for ChatGPT's independent review before requesting owner's authorization for Codex production.** No implementation by default, no change to \`main\`, and no server deployment.

## Future Paper testing and upgrade policy

The eventual Paper adapter will be pinned to explicitly tested API versions, have a compiler/static lane **and** disposable-server startup/render/shutdown tests. Paper/Java version support must be stated after live-server evidence; include player visibility, particle-data semantics, correct thread scheduling, life-cycle cleanup, a server-load safety test, and rollback. Separate Java/Geyser-Bedrock visual parity and Folia scheduler testing if desired.

References:
- https://docs.papermc.io/paper/getting-started/
- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/dev/particles/
- https://docs.papermc.io/paper/dev/scheduler/
- https://docs.papermc.io/paper/dev/folia-support/
- https://docs.papermc.io/paper/dev/plugin-yml/
- https://docs.papermc.io/paper/dev/roadmap/
