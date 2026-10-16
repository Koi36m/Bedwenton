# Paper Upgrade Acceptance Matrix and Smoke Test Checklist

**Status:** PROPOSED PROCEDURE. No builds tested and no Paper-version compatibility claims established by this document.
**Date:** 2026-10-16.
**Applicability:** Future VFX Paper adapter and opt-in demo plugin only. M1–M4 remain unchanged.
**Companion:** PAPER-COMPATIBILITY-AND-VFX-READINESS.md

## Version record (fill per candidate)

| Property | Current supported target | New candidate |
|---|---|---|
| Paper version + precise build | TBD | TBD |
| Server JAR checksum | TBD | TBD |
| Java runtime/version | TBD | TBD |
| Paper API dependency version | TBD | TBD |
| Adapter commit/source SHA | TBD | TBD |
| Plugin JAR checksum | TBD | TBD |
| api-version in plugin.yml | TBD | TBD |
| Other installed plugin versions | TBD | TBD |
| Testing date/runner | TBD | TBD |
| Test log/artifact links | TBD | TBD |
| Result | NOT TESTED | NOT TESTED |

## Pre-merge gate

- [ ] Candidate official Paper release notes reviewed and breaking changes recorded.
- [ ] Appropriate Java runtime requirement confirmed from official docs for that target.
- [ ] Pinned compile-only Paper API used; no transitive Paper dependency in \`vfx-math\`.
- [ ] No unapproved NMS, CraftBukkit, reobfuscation or internal-version package use.
- [ ] Any optional integrations are absent-safe; hard dependencies correspond to actual plugin IDs.
- [ ] All accepted math tests and reference data pass without modifications.
- [ ] VFX command schema and user configuration remain backwards compatible or have a documented migration.
- [ ] Production and test artifacts are checksum-verified and unambiguously linked to the source commit.
- [ ] Documentation clearly distinguishes *compiled* from *actually run successfully on the candidate*.

## Compile/static test lane

- [ ] Java 25 math test suite and independent reference manifests pass.
- [ ] Production math has zero Paper imports and no new runtime dependencies.
- [ ] Paper adapter compiles against the **pinned current supported Paper API**.
- [ ] Candidate adapter compiles against **the candidate Paper API** separately.
- [ ] Correct \`plugin.yml\` generated and inspected, with truthful \`api-version\`.
- [ ] No accidental shading of Java/Paper/EcoEnchants dependencies.
- [ ] No unrestricted debug commands or inadvertent administrative permission defaults.
- [ ] Forbidden API scan: \`org.bukkit\`, \`io.papermc\` cannot appear in math/scene/render-api packages.
- [ ] A failure in a candidate lane does not mutate the supported release artifact.

## Disposable real-server smoke matrix

Use a disposable world and separate empty server directories for each tested Paper version. Do not test upgrade candidates first on the live SMP.

| Test | Supported Paper | Candidate Paper | Evidence |
|---|---|---|---|
| Server startup without plugin | NOT TESTED | NOT TESTED | Startup logs |
| Plugin enable and version/status diagnostic | NOT TESTED | NOT TESTED | Console/status |
| Plugin disable / clean stop | NOT TESTED | NOT TESTED | Logs, no task leaks |
| Re-enable or server restart persistence | NOT TESTED | NOT TESTED | Logs |
| Basic particle at exact requested location | NOT TESTED | NOT TESTED | Visual/log |
| Color/data-bearing dust particle | NOT TESTED | NOT TESTED | Visual/log |
| Directional particle \`count = 0\` semantics | NOT TESTED | NOT TESTED | Visual/log |
| Per-player receiver/range filtering | NOT TESTED | NOT TESTED | Two client sessions |
| Static circle/ring with no duplicate seam | NOT TESTED | NOT TESTED | Effect capture |
| Moving-anchor effect / anchor invalidation | NOT TESTED | NOT TESTED | Console and capture |
| Player disconnect, teleport, death, world change | NOT TESTED | NOT TESTED | No orphan task |
| Chunk/world unload | NOT TESTED | NOT TESTED | No forced chunk load |
| Repeated trigger, cancellation, max active budget | NOT TESTED | NOT TESTED | Counters |
| Permission denied / owner binding | NOT TESTED | NOT TESTED | Action bars/logs |
| Missing optional plugin dependencies | NOT TESTED | NOT TESTED | Graceful disable |
| Config invalid / old schema migration | NOT TESTED | NOT TESTED | Controlled error/migration |
| Java client particle appearance | NOT TESTED | NOT TESTED | Screenshots/video |
| Geyser/Bedrock particle fallback (if supported) | NOT TESTED | NOT TESTED | Separate client evidence |
| Two-player and stress preset bounds | NOT TESTED | NOT TESTED | Diagnostics and profiler |
| Clean shutdown with no lingering temporary entities | NOT TESTED | NOT TESTED | Shutdown logs |

**Automated smoke tests** should assert machine-observable behavior (startup, logs, commands, counters and no exceptions). Visual correctness and Bedrock/Geyser parity may require separate manual acceptance and should not be silently marked passing from console logs alone.

## Scheduler / thread ownership contract (before adapter coding)

- On ordinary Paper, world/entity/player API access occurs on the server thread as required by API contracts.
- Off-thread tasks may do pure, deterministic mathematics only on **immutable captured values**.
- The adapter owns thread-affinity handoff and validates that referenced world/player/entity objects remain available.
- If future Folia support is requested, build a separate capability/scheduler test matrix with global, region, entity and async ownership paths and test on Folia. The presence of a \`folia-supported\` metadata flag alone is not evidence of compatibility.
- Cancellation is idempotent; callback executed after cancellation never emits particles or affects gameplay.

## Performance budget gate

Budgets should be empirically selected on the eventual hardware. The following are **dimensions**, not preapproved numerical limits:

- Peak active effect instances / server and / player.
- Particle commands generated, accepted, transmitted and dropped per tick.
- Recipients per effect, maximum viewer distance and maximum per-client particle demand.
- Math preparation CPU time/tick and actual server-thread render work.
- Table/cache memory and eviction/cleanup; cache invalidation on reload.
- Particle quality presets \`Simple\`, \`Balanced\`, \`Heavy\` and their per-effect override.
- Over-budget policy: deterministically drop non-gameplay visual work first.
- Effect lifecycle concurrency under many players entering/leaving range.
- No performance inference from a clean single-player startup smoke test.

## Rollout decision

**PASS** only if: baseline preserved; compile lane green; all required real-server smoke rows PASS; no blocking warnings or unknown required integration; artifacts provenance verified; owner accepts test evidence.

**CONDITIONAL PASS** only if a limitation has explicit owner approval and a safe fallback, and the release notes specify it; no untested Paper version may be advertised as supported.

**FAIL / HOLD** if: version refusal, plugin enable failure, unsafe async access, leaked tasks, owner permission bypass, particle flood, dependency misidentification, lost item data or missing rollback path.

## Rollback checklist

- [ ] Save known-good JAR and SHA-256.
- [ ] Save known-good Paper server JAR/build number.
- [ ] Back up disposable world and plugin configuration, with schema version.
- [ ] Keep release commits and produced logs; never overwrite existing evidence.
- [ ] Disable candidate effect renderer safely and verify mechanics continue where possible.
- [ ] Restore last supported target only after integrity and compatibility checks.
- [ ] Record root cause and regression test before trying the upgrade again.

## Official references

- https://docs.papermc.io/paper/getting-started/
- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/dev/plugin-yml/
- https://docs.papermc.io/paper/dev/particles/
- https://docs.papermc.io/paper/dev/scheduler/
- https://docs.papermc.io/paper/dev/folia-support/
- https://docs.papermc.io/paper/dev/data-component-api/
- https://docs.papermc.io/paper/dev/roadmap/
