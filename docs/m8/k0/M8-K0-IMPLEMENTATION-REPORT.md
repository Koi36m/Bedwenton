# M8 K0 implementation checkpoint

Owner authorization is preserved byte-identically alongside the independent R3 design review.
Exact accepted R3 tip: `17ad99186f6b2a73eaccaa22ff5aa0f0e7fd6d82`, tree
`8c24886ff2bad1fe3deabaf25a24974761addd25`. ZIP CRC, all 71 checksum entries, the declared ZIP and bundle
SHA-256 values, complete bundle history, exact ancestry and 61 archived specification files were verified.

K0 adds only the shared scheduling/contribution seam, a bounded registry, effect binding/draft carriers,
ordered snapshot validation, strict opt-in configuration and thin Paper scaffolding. Original RingDriver
constructors use TickContributor.NONE. A single driver-owned scheduler handles all registrations and rings,
uses one shared cap in either start order, skips the planner on zero-request ticks, and retains generation-token
and duplicate-tick guards. All fallible candidate/readiness steps precede commit; disposal happens after commit.
No AuraEffect, TrailEffect, ActivationEffect, M8Recipes or trail history implementation is added.
M8 commands are unavailable in K0. Disabled M8 preserves original M7 parsing, suggestions and status formatting.

Only five accepted M7 files change: RingDriver.java, VfxDemoPlugin.java, gradle.properties (version 0.8.0-k0,
Paper pin unchanged), config.yml and plugin.yml. The root build, M1–M6, other M7 production classes, original
M7 tests, fixtures, reference files and existing verification scripts remain byte-identical.

The K0 registry validates every snapshot itself before invoking a future effect. This moves the reference's
AbstractEffect validation into shared infrastructure; a badly implemented effect cannot bypass it. Rejections
are counted per active registration and by a bounded session-wide reason map. Transient skips terminate after
the configured count (default 20). A second guard checks registry draft binding/world and a third checks driver
request range, live owner mapping, world mapping, current GAME time and duplicate request IDs.
Registered effects are bounded by the shared ceiling (16 maximum) and the per-player cap. Mutating callbacks
cannot reenter the registry; factory aliases cannot cause disposal of already registered effects. Throwing updates
and disposals are contained. Disabling forces cancellation even if an injected contributor violates close().

The Paper reader resolves only the bound player UUID. It copies fresh tick, identity, position, yaw, height and
loaded-chunk facts. It reads existing owner IDs rather than recreating a released ID; this preserves the same valid
binding and rejects a released one. No coordinates are repaired, no chunks loaded, and no entity-attachment or
visual orientation code is implemented. Discontinuity serial is reserved at zero until a later checkpoint.
The shared trailMaxStep setting must exceed 0.5 (the largest approved tier's minSpacing) and be finite and <=64,
so a configuration can support all future tiers. No trail behavior is implemented by that validation.

Local Java/javac 25.0.4.1 and Gradle wrapper 9.1.0 are verified. The full actual production core compiles with
-Xlint:all -Werror; 111 genuine JUnit tests pass locally: 61 unchanged M7 + 50 K0. Ports use scripted test-only
27-point existing-style scenes, not the deferred showcase recipes. Full Paper/Gradle verification is pending at
this checkpoint. Local Gradle is blocked by FileLockContentionHandler / no usable wildcard IP; no permission
retries or Java substitutions are used. Pinned API JAR SHA-256 and javap signatures were checked locally:
26.3.build.204-stable, f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556.

M7 smoke S2/S4/S8/S9/S11 is NOT RUN: no disposable ordinary Paper server with matching clients is available.
The established M7 smoke evidence is historical and is not evidence for this K0 JAR. No production release,
merge, live SMP deployment, K1–K4 implementation, world event or independent acceptance is performed.
