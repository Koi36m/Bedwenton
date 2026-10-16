# Minecraft VFX Engine — Platform Readiness and Future Paper Compatibility

**Status:** PREPARATION BLUEPRINT, not an implementation contract or a claim of future-version compatibility.
**Prepared:** 2026-10-16.
**Repository:** Koi36m/Bedwenton.
**Verified M4 snapshot:** e3bf5578c126a7d3498be0b30fb431d9ec9a3099.
**Evidence-only successor:** ea0dcbc2a3239b9dbf0821f1420ef13371576acf.
**Verification run:** https://github.com/Koi36m/Bedwenton/actions/runs/38974334716
**M1–M4:** accepted for the GitHub-tested snapshot; 313/313 JUnit tests and reference comparisons.
**Scope of this document:** planning and compatibility preparedness; no production source, math contracts, tests or references are changed.

## A. Non-negotiable architectural boundary

1. Keep M1–M4 pure Java 25 with zero Minecraft/Paper/Bukkit runtime dependencies and unchanged API contracts.
2. Design an effect model that emits **immutable, platform-neutral render commands**. It must not hold or expose Bukkit World, Location, Player, Particle, Scheduler or entity instances.
3. Put Minecraft-facing calls in a small, independently buildable **Paper adapter** that translates these commands to supported Paper APIs.
4. Keep enchantment **gameplay mechanics** (damage, cooldowns, ownership, item identity, hit detection and permission) separate from VFX; visuals never authorize gameplay changes.
5. Treat plugin-specific integrations (EcoEnchants, ExcellentEnchants, Geyser/Floodgate if applicable, etc.) as adapters; do not hard-depend on names of components that are not actual separately loadable plugins.
6. Never assume a future Paper release works merely because the code compiles. A new version requires a verified runtime smoke test and explicit release decision.
7. Preserve the approved M4 source tree, evidence, original tests and complete local bundle history; this new branch must be documentation-only until implementation is separately approved.

## B. Target module shape (proposal)

- \`vfx-math\`: M1–M4 frozen math primitives and reference suite. Initially **leave the existing root Gradle project unchanged**; later extract a module only with an explicit migration/verification gate.
- \`vfx-scene\`: future pure-Java effect specification, clips, timeline scheduling semantics, transforms, layers, scene commands and state. No Paper import.
- \`vfx-render-api\`: future platform-neutral immutable \`ParticleCommand\`, \`SoundCommand\` (optional), receiver policy, anchor snapshot and bounded \`EffectFrame\`.
- \`vfx-paper\`: future Paper-specific conversion, particle validation, viewer resolution, safe scheduler routing and lifecycle hooks, compiled against a pinned Paper API.
- \`vfx-demo-plugin\`: future **opt-in** test harness with permission-restricted debug commands. It does not grant gameplay abilities.
- \`vfx-compat-tests\`: future build-time and actual-server startup checks.

This structure is illustrative, not approval to create these modules or to refactor M1–M4.

## C. Future milestones and acceptance gates

### P0 — Preservation and engineering policy (documentation-only)
- Record validated M4 snapshot/tree SHA and successful CI link.
- Maintain one compatibility policy and release checklist (this document and companion checklist).
- Establish responsibilities: Claude mathematical design/adversarial oracles, Codex implementation and CI, ChatGPT independent review, owner final go/no-go.
- Before any new code, authorize and specify the smallest next milestone.
**Gate:** docs present and reviewed; no changes to production, tests or reference generators.

### P1 — Platform-neutral animation/effect model (proposed M5)
- Agree on deterministic time composition using M2, placements using M1, curves using M3 and sampling using M4.
- Specify immutable command structure, interpolation, reusable effect definitions, timed layers and previewable frames.
- Separate one-shot effects, persistent looping effects, moving anchors and trigger/stop semantics.
- Keep deterministic tie ordering, bounded allocations and cancellation semantics.
- Include reference examples: healing ring, orbiting sparkles, moving trident helix and constellation links.
- Decide whether seeded randomness, additional shape distributions and surface sampling belong to M5 or a later M4.1.
**Gate:** mathematical/behavioral contract and independent tests approved before implementation.

### P2 — Safety, budgets and observability
- Explicit maximum samples/effect, **actual emitted particle calls**/tick/player/world, maximum active effects and lifetime, maximum per-frame CPU preparation and effect distance.
- Presets **Simple / Balanced / Heavy** chosen per enchant; Balanced can be the default, not a global hard-coded number.
- Receiver filtering by world, visibility/range and settings; no hidden broadcast to every player.
- Over-budget policy: deterministic thinning or dropping **visual** frames, never changing underlying mechanics.
- Cache immutable static geometry; avoid recreating arc-length tables per tick. Moving poses can reuse samples.
- Diagnostics for requested/sent points, skipped frames, active effects, cancellation and build identifier; no sensitive player telemetry.
- Rate-limit warning logs; disable VFX harmlessly when the adapter cannot render an effect.
**Gate:** stress tests for concurrency and burst triggers, configured caps, baseline resource measurements and graceful fallback.

### P3 — Paper adapter MVP
- Start with the **public Paper API** and \`plugin.yml\`, conservative \`api-version\` selected for the minimum *actually tested* target.
- Pin \`compileOnly\` Paper API and Java toolchain; no NMS/CraftBukkit internals by default.
- Implement one accurate particle emission path, initial receiver selection, safe scheduling and lifecycle cancellation.
- Audit individual particle types and required payloads (DustOptions, color, block/item data, velocity, trail, etc.).
- Be precise about particle \`count\` semantics: Paper's documentation notes that \`count=0\` spawns a single particle, whereas \`count>0\` invokes client Gaussian offsets; verify exact-position behavior and any special particle exceptions in a real server.
- Keep client-side mod optional: Java players and Geyser/Bedrock players must retain server-authoritative mechanics; VFX degradation is permitted where particle capabilities differ.
- Never label Folia compatible until entity/region/global scheduling and actual Folia testing have passed.
**Gate:** compiled JAR with correct metadata; actual Paper server enable, render, disable, reload, world-change and shutdown smoke tests. Debug commands restricted by permission.

### P4 — Reusable enchantment integration
- Define a small event/command interface for gameplay plugins to trigger, update and stop visual effects.
- Preserve per-plugin owner binding, cooldown, item identity and persistence; VFX cannot bypass access checks.
- Never derive item identity from display names alone; prefer versioned namespaced data. Preserve unrelated lore, PDC and metadata on item modifications.
- Add separate smoke fixtures for Bloom, Cloudstep, Cloud Weaving, Astral Prism and healing artifacts where source is available.
- Do not rewrite already-working standalone plugins merely to adopt this engine.
**Gate:** unchanged gameplay regression tests, effect-level cancellation, multi-player interference and duplicate-trigger checks.

## D. Paper compatibility policy — for every future release

1. **Detect**: read official PaperMC changelog/release notes and development roadmap; identify Java requirement, API changes, deprecated methods and particle behavior changes.
2. **Classify**: stable tested target versus new candidate. A candidate never replaces the production target automatically.
3. **Compile**: build the Paper adapter against the candidate API with Java versions officially supported by that Paper release; keep pure math tests on Java 25 until a separate compatibility decision.
4. **Smoke**: on a clean disposable Paper test server, verify startup, shutdown, plugin metadata, dependency detection, commands, simple particle emit, special-data particle emit, item interactions, tasks, world unload, player disconnect and cancellation. A compiler success alone is insufficient.
5. **Review**: inspect client-dependent particles in both Java and, if relevant, Geyser/Bedrock; verify any changed plugin dependency or registry names. Any NMS-dependent feature, if ever approved, needs version-specific maintenance.
6. **Performance**: compare work, emission counts and jitter under identical effect fixtures. Test multiple simultaneous viewers and a worst-case approved effect, never an unbounded all-player broadcast.
7. **Release**: publish an explicitly versioned, checksum-verified JAR only after all gates pass; include supported Paper versions, Java requirement, known limitations and rollback instructions.
8. **Rollback**: preserve the last known-good JAR, its matching source SHA, plugin configuration schema and a restorable test-world backup; never test candidate builds first on the live SMP.
9. **Record**: log the version/build tested, date, exact commits, test-run URL, accepted exceptions and signed-off decision.
10. **No guarantee**: support for the next Paper release is a goal, not assumed compatibility.

Suggested CI lanes (to design later):
- **Every PR/push**: 313+ pure-Java math tests, compile, dependency guard, approved-reference hash checks and adapter compile against current supported Paper API.
- **Release candidate/manual**: reference *regeneration*, startup/shutdown smoke tests on current Paper and next candidate when available, artifact integrity and optional load test.
- **Scheduled discovery (opt-in)**: check official Paper release information and raise a compatibility issue; do not silently edit dependency versions or auto-deploy.

## E. Runtime lifecycle checklist (future integration)

- Plugin enable/disable/reload: register and unregister tasks/listeners; cancel visuals; remove owned temporary entities.
- Player quit/death/teleport/world switch: detach anchors and stop or safely transfer effects according to contract.
- Entity invalidation/chunk unload: no uncaught access; no forced chunk loads just to animate particles.
- Scheduler: only access world/entity APIs on correct thread or region; heavy **pure math** calculations may run separately only on immutable snapshots.
- Permissions/owner: only gameplay module decides whether an effect may start; default deny unknown artifact owners.
- Configuration versioning: validated schema, safe defaults, migration report and explicit rollback.
- Multiple triggers: no duplicate ticking task per effect; no duplicate particle loop; predictable priority.
- Environment: log plugin version, selected Paper version, Java version and detected optional integrations on startup.

## F. Integration warnings from official PaperMC documentation (reviewed 2026-10-16)

1. Paper 26.1+ requires Java 25: https://docs.papermc.io/paper/getting-started/
2. \`plugin.yml\` supports \`api-version: '26.3'\` and servers below the declared API version reject the plugin. Choose the floor intentionally: https://docs.papermc.io/paper/dev/plugin-yml/
3. ParticleBuilder is recommended; particle \`count\` and \`extra\` semantics depend on type: https://docs.papermc.io/paper/dev/particles/
4. Data components can break across updates; \`ItemMeta\` is suggested when simple cross-version metadata compatibility is a goal: https://docs.papermc.io/paper/dev/data-component-api/
5. Asynchronous Bukkit code must not directly access most world state: https://docs.papermc.io/paper/dev/scheduler/
6. Folia needs real region/entity scheduler support, not just \`folia-supported: true\`: https://docs.papermc.io/paper/dev/folia-support/
7. Paper 26.1+ no longer supports reobfuscating plugins to Spigot runtime mappings; avoid NMS absent a justified version-specific module: https://docs.papermc.io/paper/dev/userdev/
8. Review planned API deprecations before upgrade: https://docs.papermc.io/paper/dev/roadmap/
9. Paper's \`paper-plugin.yml\` loader is still described as experimental; don't migrate loaders solely for novelty: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/

## G. Development/acceptance discipline

- **Freeze accepted baselines**: no new API, core formula, test oracle or CSV changes without separately reviewed amendment and re-verification.
- **One goal per branch**; no merges to \`main\` or deployments without owner approval.
- **Ordinary GitHub files before large bundles**: native Git bundle remains local if a connector cannot publish it; publicly published cloud-verified snapshot must be tied to the same source tree.
- **Reproducibility**: pin toolchains, dependency versions, verified releases and action major versions; update consciously after review.
- **Citable evidence**: source SHA, toolchain, JUnit exact counts, reference regeneration, artifact checksums, source diff, Paper server startup/stop logs and runtime test notes.
- **Opt-in updates only**: future automation may detect Paper releases and propose upgrades, but must never automatically push new plugins to a live server.

## H. Immediate next decision

**Recommended first implementation:** P1 / M5 design, centered on a platform-neutral \`EffectFrame\` and reusable animation/composition rather than a large Paper plugin.

In parallel, define the P3 Paper adapter interface in a *separate document-only proposal* to prevent M5 from accidentally depending on changing Paper APIs. Do not implement two large subsystems concurrently until contracts are stable.

**No future milestones, runtime plugins or compatibility support have been declared complete by this preparation document.**
