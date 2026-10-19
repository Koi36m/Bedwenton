# K1.1 focused disposable-server smoke checklist

All new K1.1 real-server/client checks: **NOT RUN** by Codex. The owner reports the original K1 followed players,
switched tiers, stopped, cleaned up on death and followed teleport/dimension changes with acceptable perceived
performance. Those observations belong to K1, not the new JAR. No quantitative TPS/MSPT claim is made.

Stop a disposable ordinary Paper 26.3 server, back up the known K1 JAR/config, remove the old JAR, and install only
`bedwenton-vfx-paper-0.8.0-k1.1.jar` after checking its SHA-256. Use Java 25, retain existing caps and opt-in config.
No effect starts automatically. Keep `m8.enabled: true` for testing; absent/false still refuses aura starts.
Restart to apply config; `/reload` remains unsupported. Do not deploy to a live SMP or keep both JARs installed.

| Check | What to record | Result |
|---|---|---|
| Rear crescent | Simple/Balanced/Heavy from front, rear and sides; front cleaner, waist still reads as attached magic; body yaw rotates the rear correctly | NOT RUN |
| Animation | Watch several 60-tick sway cycles; no front sweep or abrupt jump. Halo retains opposite 80-tick rotation | NOT RUN |
| Halo clarity | Balanced/Heavy cyan separation, smaller DUST, nameplate/resource-pack interaction, moving-particle persistence; video if possible | NOT RUN |
| Movement | Walk, sprint, jump/fall and turn body independently of camera pitch; arc/halo stay horizontal and follow feet | NOT RUN |
| Tier/off/stop | Cycle all tiers; one aura only, counts 8/18/24. `aura off` preserves any ring; `stop` and admin `stop all` clean up | NOT RUN |
| Death/disconnect | Die, restart aura, disconnect/reconnect: no stale effects or automatic restart | NOT RUN |
| Teleport/world | Far teleport and dimension change: next valid position, no streak, same aura continues | NOT RUN |
| Shared task/shutdown | Ring+aura share task/budget path; shutdown logs zero remaining tasks, restart idle | NOT RUN |
| Permissions | Preserve prior policy; two clients with use/no view see only their own, view grant/revoke works | NOT RUN; required before outside showcase |
| Performance | Same scene/config/clients: idle, original K1 and K1.1 per tier, including sustained Heavy; compare perceived smoothness and measured TPS/MSPT/GC if available | NOT RUN |

Record Paper/Java versions, plugin hash, config, clients, CPU/RAM and view distance. Separate appearance, cleanup and
performance results. Existing debug/status counters remain available without new scheduling. Synthetic automated
timings are in-memory pipeline observations, not native Paper emitter cost or a server performance guarantee.

Rollback: stop, remove K1.1 JAR, restore the backed-up K1 JAR/config, restart. No data migration or gameplay state changes.
Stop at K1.1 review; no K2–K4, merge, production release or live deployment.
