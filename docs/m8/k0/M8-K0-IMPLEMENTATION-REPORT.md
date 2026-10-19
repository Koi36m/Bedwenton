# M8 K0 — implementation and verification report

**READY FOR INDEPENDENT K0 IMPLEMENTATION REVIEW.** All 526 automated tests pass; required real-server smoke
remains NOT RUN. K0 acceptance, later checkpoints, merge, release and deployment are not granted by this report.

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
27-point existing-style scenes, not the deferred showcase recipes. The full pinned Paper build and 415 engine
regressions also pass in the fresh Actions run detailed below. Local Gradle is blocked by FileLockContentionHandler /
no usable wildcard IP; no permission retries or Java substitutions are used. Pinned API JAR SHA-256 and javap signatures were checked locally:
26.3.build.204-stable, f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556.

M7 smoke S2/S4/S8/S9/S11 is NOT RUN: no disposable ordinary Paper server with matching clients is available.
The established M7 smoke evidence is historical and is not evidence for this K0 JAR. No production release,
merge, live SMP deployment, K1–K4 implementation, world event or independent acceptance is performed.

## Final verification — authoritative results

Recommendation: **READY FOR INDEPENDENT K0 IMPLEMENTATION REVIEW**. This is not K0 acceptance or server qualification.

| Identity | Value |
|---|---|
| Native implementation, branch m8-k0-implementation | `a033180b3128c51b2178a1b7a0c8fb1cf6b90378` |
| GitHub verified source snapshot, m8-k0-actions-verification | `37fef2a2670e6bed14661909ce83c7354ef9e8b2` |
| Complete source tree, identical on both | `eb09bdc031ac2bf06b82bb75473f611fbf05f991` |
| Accepted M7 snapshot | `ed5fc7bff548edf4b005e919495b22213a65b64e` |
| Accepted M7 native implementation (separate history) | `c0fa177a73c0e088fca047d606e6f00a4a45bfd7` |
| Complete tree of both M7 implementations | `afa33672b5850bdda3be636415659fa27ac53047` |

The native implementation descends from the exact R3 tip and preserves all original review commits. The GitHub
connector published a distinct source snapshot whose entire tree matches; it did not rewrite native history.
The bundle additionally preserves `preserved/m7-native-evidence` at `3f7c38ebfa2f72ecd0e67f2535229af0c4bbcb81`,
alongside the six original design-bundle refs. This exposes both M7 histories honestly. Existing GitHub branches
and main are not overwritten or force-pushed. The final evidence-only child and delivery hashes are identified in
GIT-CHECKPOINT.json / SHA256SUMS.txt; they cannot be embedded self-referentially in their own commit/archive.

Successful full build: https://github.com/Koi36m/Bedwenton/actions/runs/39203576470
Download JAR and original evidence: https://github.com/Koi36m/Bedwenton/actions/runs/39203576470/artifacts/12164892334
Original artifact SHA-256: `167ec1c92874555a8d50585624b2016697320e6c71ccd7af48d3800d80b21a6a`.
The artifact was downloaded, its Actions digest and ZIP CRC verified, and every JUnit XML parsed independently.

| Suite | Passed / total | Failed / errors / skipped | XML reports |
|---|---:|---:|---:|
| Unchanged M1–M6 | 415 / 415 | 0 / 0 / 0 | 59 |
| Unchanged M7 | 61 / 61 | 0 / 0 / 0 | 10 |
| New production K0 | 50 / 50 | 0 / 0 / 0 | 6 |
| Combined | 526 / 526 | 0 / 0 / 0 | 75 |

Both fresh clean test executions and both Gradle check executions succeeded, including verifyNoRuntimeDependencies,
verifyPluginJar and recordPaperApi. Engine runtimeClasspath is empty. The full Paper/Bukkit layer compiled against
the pinned API; it was not excluded or replaced with stubs. Production compiler warnings: zero (adapter uses
-Xlint:all -Werror). The root test compilation retains five historical JOML classfile metadata warnings; Gradle
reports deprecations for future Gradle 10. Neither is a production Java compiler warning or altered expectation.
Full console logs, XML and HTML reports are preserved under cloud/. Supplemental native results are under local/.

Actions used Temurin Java/javac 25.0.4.1, build 25.0.4.1+1-LTS. Local native execution used Oracle 25.0.4.1+1-LTS-5.
The Gradle wrapper is 9.1.0, revision e45a8dbf2470c2e2474ccc25be9f49331406a07e. Exact version output is preserved
in cloud/logs/source-and-toolchain.log and local/*version.log. Pinned API 26.3.build.204-stable and its SHA-256
f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556 were reverified in the successful build.
The actual JAR's getBodyYaw/getLocation/yaw/pitch/liveness/UUID/world/height/chunk/currentTick APIs, native
BasicCommand registration, scheduler, Particle.DUST and DustOptions constructor signatures were checked with javap.
No M8 particle styles are activated at K0; the DUST API check is not a new visual or client-appearance claim.

All seven accepted datasets and manifests regenerated byte-identically, locally and in Actions: M1, M2, M2 review,
M3 (2,759 rows), M4 (3,064 rows), M5 (1,113 rows), M6 (45 cases / 158 request expectations), including a second M6
regeneration. No generator, reference row, tolerance or original test expectation was modified.
Preservation hashes cover 1,040 frozen M1–M6 snapshot files, 1,152 accepted M7 snapshot files excluding the five
explicitly authorized edits, all 61 R3 design files, and 78 implementation/build/test/tool files. Exact five-file
old/new SHA-256 values are in AUTHORIZED-EDIT-PROVENANCE.json; changed paths, full production/build/test diffs and
RingDriver.patch are in local/. The unchanged M7 test file set was also compared directly with the accepted Git blobs.

## Compiled plugin

`bedwenton-vfx-paper-0.8.0-k0.jar`, 181,252 bytes.
SHA-256: `1516906c829c3e8967f3f60a2b913b0131f9c2ddcd4c804cc404572e1d88f318`.
Metadata expands version 0.8.0-k0, keeps api-version 26.3 and includes disabled M8 defaults. The prerelease suffix
identifies this gated K0 artifact, not a completed M8 release. Its embedded build provenance names the actual
GitHub build snapshot and accepted engine tree. All 61 class files of the accepted engine JAR are embedded
byte-identically, and the class set independently matches freshly compiled frozen engine sources. Paper,
Bukkit, Adventure, Mojang and JUnit libraries are absent. K1–K4 implementation classes and test fixtures are absent.
The runtime dependency remains only the accepted engine. No production runtime libraries were added.
Rebuilding another native/evidence commit stamps that checkout's commit, so the JAR hash may differ; the delivered
JAR is the original verified Actions artifact, not a locally rebuilt substitute.

## Runtime gates and remaining limitations

| Required real-server check | Status | Reason / evidence to collect |
|---|---|---|
| S2 — enable / idle task | NOT RUN | No disposable ordinary Paper 26.3 server and matching clients provisioned |
| S4 — existing static ring | NOT RUN | Record geometry, two particle styles and actual count-0 appearance on this K0 JAR |
| S8 — repeated ring / stop | NOT RUN | Record task count, replacement and cleanup on this K0 JAR |
| S9 — range/world/death/quit cleanup | NOT RUN | Requires actual Paper event ordering; offline driver tests do not substitute |
| S11 — restart | NOT RUN | Record re-enable with M8 disabled and no persisted state |
| Enabled K0 routing / invalid M8 YAML / shutdown | NOT RUN | Unit parser/routing tests and full API compilation pass; actual plugin lifecycle remains a smoke gate |

C0 (Paper compile/package), C1 (frozen regressions/datasets) and C2 (production K0 tests) pass. C3 real-server smoke
is BLOCKED / NOT RUN for the environment above. Existing M7 in-game evidence is historical. No client visibility,
MSPT/TPS/FPS, packet count, soak or live-server compatibility claim is made. Performance measurements and every
K1–K4 effect remain outside this checkpoint. The original scratch 116-test/23-mutant design evidence is preserved
but not counted as production passes; no new production mutation campaign was run.

The Paper reader reserves discontinuity serial zero; real teleport/respawn history and visual pose/motion belong
to later checkpoints. K0 has no user-facing M8 starts: enabling M8 only wires the empty registry and unavailable
command routing. Factories and the scheduler host retain their documented internal contracts (factory construction
has no registration/scheduling side effects; scheduling returns a cancellation handle or refuses before ownership).
Ordinary Paper schedules at delay 1, after the main-thread start transaction has committed. A server tick reset/wrap
retains M7's existing behavior. Installation/rollback and exact reproduction steps are in M8-K0-REVIEW-HANDOFF.md.
The owner and ChatGPT decide K0 acceptance and any subsequent authorization independently.
