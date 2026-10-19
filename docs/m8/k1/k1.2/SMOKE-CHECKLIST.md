# K1.2 focused owner visual check and rollback

All new K1.2 real Minecraft checks: **NOT RUN** by Codex. Earlier owner observations belong to their original JARs.
No replay of the entire M7 suite is requested without a relevant regression.

On a disposable ordinary Paper 26.3 server using Java 25: stop, back up the known K1.1 JAR/config, remove the previous
JAR, install only `bedwenton-vfx-paper-0.8.0-k1.2.jar` after checking SHA-256, preserve the existing opt-in config, restart.
`m8.enabled: true` permits commands; absent/false stays disabled. No effect starts automatically. Do not use `/reload`.

| Focus | Owner observation | Result |
|---|---|---|
| Back/front/side | Vertical white disc floats behind torso, radius 0.78, center feet +1.25 and backward 0.65; no waist donut or horizontal crescent | NOT RUN |
| Internal rotation | Two curved, asymmetric white sweeps visibly complete a revolution in about 4 seconds at 20 TPS; outer outline stays stable, no edge-on flip | NOT RUN |
| Rear attachment | Turn body through cardinal directions; disc stays behind the body, independent of head pitch | NOT RUN |
| Movement | Walk, sprint, jump/fall, teleport and change dimension; current pose follows without connecting streaks or clock restart | NOT RUN |
| Halo separation | Existing cyan halo at feet +2.15 remains recognizable and separate; assess resource pack/nameplate and native particle persistence | NOT RUN |
| Tiers | Simple20/Balanced34/Heavy44 points, readable curves without an opaque mass; tier replacement still one aura | NOT RUN |
| Focused cleanup | Aura off/stop, death or disconnect as convenient; investigate/replay wider lifecycle cases only if regression appears | NOT RUN |
| Performance | Same scene/config/clients, compare no aura and each tier including sustained Heavy; record perceived smoothness and actual TPS/MSPT/GC if measured | NOT RUN |

Record actual Paper/Java/plugin hash/config/client versions with observations. Synthetic pipeline timings in the report
are in-memory measurements, not native server or network performance. Vanilla END_ROD persistence can soften the
pattern or leave residual particles while turning; geometric tests cannot establish visual clarity.

Two-player permission isolation remains a separate gate before inviting outside players. Existing rules are unchanged.
Rollback: stop, remove K1.2 JAR, restore backed-up K1.1 JAR/config, restart. No migration or gameplay state changes.
Do not merge, release, deploy to the live SMP, self-accept or begin K2.
