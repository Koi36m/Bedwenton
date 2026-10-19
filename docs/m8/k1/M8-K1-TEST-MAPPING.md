# K1 production verification mapping

The permanent baseline remains 415 engine +61 M7 +50 K0 tests, byte-identical. Historical R3 design tests/probes
are preserved as design evidence and are not counted as new production executions.

| Production class under test | New tests | Coverage |
|---|---|---|
| AuraRecipe, AuraEffect | K1GeometryTest (12) | independent formula for both rings/all tiers/boundaries; exact counts/roles/palette; yaw; pitch independence; walking/sprinting/jumping/falling; world continuation; shared recipe/shape identities; deterministic frames; bounds |
| AuraSession, registry, shared driver | K1LifecycleTest (12) | no auto-start; replacement/disposal; invalid replacement preservation; real scheduling failure/retry/stale callback; lifetime; death/disconnect; aura-only off; stop owner/all; shutdown;1000cycles |
| Registry, AuraEffect, M7 selection/gate | K1IdentityAndPermissionsTest (12) | owner/use vs viewer/view; grant/revoke including after plan; range/world; owner-first cap; forged/released identities; stale/future; nonfinite fields; world/height/chunk;20skip termination; binding defense |
| M8Commands, config, catalog, timing | K1CommandsAndSafetyTest (10) | grammar/defaults/tiers/off; opt-in; permission/console; Heavy refusal; future checkpoint refusal; invalid config; immutable closed palette; injected timing/thread guard |
| Actual M6/M7 pipeline | K1BudgetAndSchedulingTest (10) | one shared plan/task; both cap orders/replacement; per-owner cap; aligned emission/per-viewer limits; zero generated budget; strict Simple ceiling; persistent/unresolved cyan; all-tier1800tick runs;72000tick bounded soak |
| Actual pipeline, in-memory host/sink | K1SyntheticMeasurementsTest (1) |600warm-up+1200measured ticks per tier and two Heavy auras/two viewers; exact generated/planned/success/failure counts; real monotonic planner/emitter/snapshot distributions |

New total57; combined583. Fakes supply platform observations/scheduling and collect sends, while the real aura,
registry, RingDriver, M1–M6 recipes/planner and M7 emitter implement all visual, budget and security behavior.
The per-player-cap test uses an uncalled sentinel factory for a reserved kind; it does not implement a K2 effect.
The synthetic sink records calls in memory, so its latency is not native Paper particle/network latency.

During initial test authoring one new height-validation fixture's nested primitive conditional unboxed null and threw
before returning a snapshot. The preserved first-failure log shows the expected-active assertion failing because the
registry correctly contained the fixture exception. Explicit boxed Double alternatives corrected that new fixture.
No production change, frozen expectation change or tolerance weakening was made for it.

Use `bash paper-adapter/tools/m8-k1/verify.sh /absolute/evidence/path` with Java25 and Gradle9.1.0.
Older M7/K0 runners keep their historical fixed totals and remain unchanged; use the additive K1 runner for583tests.
