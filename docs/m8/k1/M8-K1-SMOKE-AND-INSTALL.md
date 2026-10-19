# K1 disposable-server installation and smoke handoff

All new K1 real-server checks below are **NOT RUN** by Codex. No server/client pair is available here.
Owner-supplied K0 startup/emission/replacement/stop/teleport/dimension/death observations belong to the K0 JAR,
not this K1 build. K0 disconnect/restart checks were deferred. They are not converted into K1 passes.

Use an ordinary disposable Paper 26.3 server and Java 25. Stop the server, back up the current plugin JAR and config,
remove the older Bedwenton VFX JAR, and install only `bedwenton-vfx-paper-0.8.0-k1.jar`. Compare its SHA-256 to the
delivery manifest. Keep a backup of the proven K0 JAR. No merge, production release or live SMP deployment is authorized.

Existing config files are not overwritten by saveDefaultConfig. Set `m8.enabled: true` manually for aura testing;
absent/false stays disabled. Start with `m8.defaultTier: simple`, `m8.maxEffectsPerPlayer: 2`,
`m8.auraLifetimeTicks: 1200`, and unchanged global caps. Restart to apply. Do not use `/reload`.
No aura starts at enable. Use `/vfxdemo aura [simple|balanced|heavy]`, `aura off`, `stop`, admin `stop all`, and `status`.
The existing top-level defaultTier continues to control only the M7 ring. `m8.heavyAllowed: false` refuses Heavy.

Record Java/Paper build, plugin hash, config, CPU, heap allocation, view distance, player/client versions and permissions.
Keep appearance, cleanup and performance observations separate. This list focuses on K1, without repeating all M7 tests.

| Check | Procedure and required observation | Current result |
|---|---|---|
| Opt-in | With M8 disabled, aura refuses; M7 ring remains usable. Enable M8 and restart: still idle until commanded. | NOT RUN |
| Appearance | Simple: white END_ROD waist ring (r0.9, +1.0). Balanced/Heavy add cyan halo (r0.45, +2.15). Video rotation in both directions. | NOT RUN |
| Attachment | Walk, sprint, jump and fall; ring centres follow feet. Turn body; look up/down independently: rings stay horizontal. | NOT RUN |
| Replacement | Cycle all tiers repeatedly; one aura, one task, no duplicate formations. `aura off` preserves an active static ring. | NOT RUN |
| World/teleport | Teleport far, change dimension; aura snaps to new position next tick, continues, no connecting streak. | NOT RUN |
| Lifetime | Use lifetime20 on disposable server; expires exactly after20 game ticks, task idle if no other effect. Restore1200. | NOT RUN |
| Death/disconnect | Die, restart aura, disconnect/reconnect; each stops old aura, no restart or stale particles. | NOT RUN |
| Two-player isolation | A/B both have use, neither has view, both run auras: each sees only their own. Grant/revoke B view; B sees A only while granted. Revoke owner use; own deliveries stop. | NOT RUN — required before outside-player showcase |
| Shared budgets | Ring+aura at defaults, check status/accounting; max4 shared registrations, replacement at cap allowed. No extra tasks. | NOT RUN |
| Stop/shutdown | `stop`, admin `stop all`, then server shutdown/restart with active aura: no owned tasks, no persisted effects. | NOT RUN |
| Failure policy | Inspect logs/status for unavailable/disabled styles; no retry storm or broadcast fallback. | NOT RUN |
| Performance | Same world: idle600+1200ticks, then600warm-up+1200measured ticks per tier, including sustained Heavy; repeat two visible Heavy auras. | NOT RUN |

For performance record `/mspt`, `/tps`, client FPS, heap/GC and p50/p95/max planner/emitter/snapshot timings before/during/after.
Enable existing debug logging only when recording diagnostics; it logs every100ticks. Status retains M7 fields and adds
own aura tier, bounded registration count and tick-labelled snapshot timing. Full per-tick sampling would require a
separately approved diagnostic harness if the100tick samples are insufficient. Counters are not server timing guarantees.
Allocation concerns: M5 builds world-coordinate frame arrays; M6 plans, contexts, viewer lists and emission maps allocate
each tick; snapshot/pose/draft records are new each tick. Geometry/recipes and payloads are reused; no histories grow.

Rollback: stop server, remove K1 JAR, restore exact backup K0 JAR/config, restart. No world/player data migration is needed.
Do not keep both plugin versions installed. No gameplay state is modified by this plugin.
