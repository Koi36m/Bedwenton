# Dual-stream END_ROD — implementation verification

**READY FOR INDEPENDENT DUAL-STREAM IMPLEMENTATION REVIEW.** This is a mechanism experiment. Its native visual
outcome and similarity to the reference remain unverified; no production acceptance or live deployment.

## Video observations, not a claim about its implementation

Owner-provided 1022.mp4 was decoded locally. Only 00:00–00:10 was sampled: nominal 2fps contact sheet plus 10fps around
6.0–6.8s. The later close view shows two curling white lobes changing orientation within a bright circular envelope.
That observation is compatible with the proposed opposing streams; it does not establish particle sources/count,
directions, lifetime or whether an outline is separately drawn. The mechanism remains the owner's hypothesis.
Source SHA-256 and inspection limits are recorded in INPUT-VERIFICATION.json. Local extracted evidence is preserved;
the original video is not uploaded to GitHub or treated as instructions. No simulated trail is presented as Minecraft.

## Exact experiment

Separate `/vfxstreams [slow|normal|fast|stop|off|status]` command; no argument means normal. Existing use/player/m8.enabled
checks apply; Fast additionally respects heavyAllowed. No automatic start or configuration/permission change.
The original /vfxreference and Arcane Halo commands/classes are unchanged. Existing owner/admin stop and registry
death/disconnect/expiry/disable cleanup cover STREAMS. Same current body-yaw anchor, following teleport/world changes
when valid. Existing per-player/global capacity limits still apply; no other effect is silently replaced.

Each step requests exactly two END_ROD particles at the same rear torso origin, feet+1.55/back 1.0. Directions are
world-space current-body-yaw transforms of `(cos(theta),sin(theta),0)` and its exact negative, with
`theta=PI/2+2*PI*(age%80)/80`. Initial A up/B down, quarter turn 20 ticks, half 40, recurrence 80. Both rotate together,
not in opposite angular directions. No head/camera tilt. Ages 0,4,...196 emit pairs; skipped ticks never pause phase
or catch up. Lifetime 200 GAME ticks: 50 steps/100 native particles per eligible viewer under unconstrained successful sends.
Slow/normal/fast extra multipliers 0.10/0.15/0.22 are bounded inputs, not measured final native physical velocities.

No plotted circle, Bézier strokes, head/ground/ambient layers, client lifetime override or native simulation.
M6's two identical position points represent the two spawn origins for reservation/accounting. The actual stream
backend uses `Player.spawnParticle(END_ROD,x,y,z,0,dx,dy,dz,speed,null,false)` once per selected point; it never sends
these as stationary points. Paper's official particle documentation describes count 0/offset/extra behavior and lists
END_ROD as directional: https://docs.papermc.io/paper/dev/particles/. Effective native speed and trajectory need smoke tests.

## Narrow adapter extension and safety

DirectionalEmission is an immutable validated unit vector/speed payload with two closed experiment style keys.
EffectDraft/RequestContext carry immutable maps, with their legacy constructors retaining empty maps. The draft guard
requires both keys for STREAMS and rejects directional metadata on other effect kinds. RingDriver copies the map into
the existing same-ID owner/world context. Frozen M1–M6 sources, public APIs, mathematical contracts and goldens remain exact.

DefaultPlanEmitter validates both styles, two same-origin points, opposed directions and equal bounded speeds before
sending. Missing/malformed payloads are skipped as INVALID_DIRECTIONAL_CONTEXT, never routed to positional send.
M6 partial recipient shares are skipped as INCOMPLETE_STREAM_PAIR; their planned share/reservation remains accounted
and is not reallocated after planning. Both emitter call slots must fit the global/per-viewer limits. Disabled style
suppresses a pair. All existing context, permission/recipient, finite point/world, health and allocation-order checks
remain in the same path. Each actual directional send is one placement/attempt; success/failure accounting is unchanged.

ParticleSink adds a default directional method which throws when unsupported. This preserves old lambda/positioned
implementations and fails closed rather than faking support. The Paper override sends only to the validated Player;
no world broadcast, second scheduler/planner, per-effect timer, NMS/reflection/packets or production library dependency.
The backend consists of two independent native API calls: a failure can leave one visible particle and cannot be
rolled back atomically. Those calls are counted and bounded. Client particles can persist after permissions/stop,
as with all native particles; no new spawns are authorized after cleanup. Guarded quiet ticks create no stream request.

The production diff is limited to ten existing adapter/build files plus the new directional payload and six new
streams classes. Existing mathematical code, registry, old effects, style catalogue entries, test sources/fixtures,
contracts and generators are byte-identical. FROZEN-BASELINE-SHA256SUMS.txt verifies **2462** unchanged baseline files.
Legacy constructor/default-method paths remain available and pass all original tests. No engine or scheduler redesign.

## Git provenance

- Exact native baseline: `a372703e61a9a3014031d49d8ab9e2c0a859c3ea`.
- Exact GitHub baseline: `9108e679b012f111b5a1fc51039965d213b43947`.
- Native implementation: `15b84d0dc3066c74cdab5c2d30290de3c30b400b` on experiment/dual-stream-end-rod.
- GitHub tested source: `325f58aa3034c51aa34ef1cb05eb5c0a1e51dfb3` on experiment/dual-stream-end-rod-actions.
- Identical complete tested tree: `fb0830a0c6549671ce0c1502dd3bfc16f36e3c2d`.
- Fresh verification: https://github.com/Koi36m/Bedwenton/actions/runs/39764375954.

Native and connector commits preserve their actual separate ancestry/metadata. Original native history and all old
branches survive in the native bundle. Complete source/evidence trees are verified equal, without rewriting or
mislabeling commits. Final evidence/checkpoint IDs are in GIT-CHECKPOINT.json. The evidence-only commit adds report
and packaging workflow; verified Java/build/tests remain unchanged. Main/reference/K1.2.1/K1.2.2 refs are preserved.

## Fresh verification results

**659/659 passed; zero failures/errors/skips, 86 JUnit XML reports.** All 631 baseline cases remain byte-identical in source,
plus 28 new dual-stream cases. Original numerical expectations/tolerances are unchanged. Both clean test/check builds
passed using Java 25, Gradle 9.1.0 wrapper and exact Paper 26.3.build.204-stable.

M1–M6:415; M7:61; K0:50; K1:57; K1.1:6; K1.2:14; K1.2.2:8; Reference:20; Streams:28.

The new cases cover unit/opposed vectors and cardinal phases, body-yaw conversion, same origin, exact directional
calls, all speeds and invalid inputs, cadence/lifetime/missed ticks, immutable payload and legacy defaults, missing/
invalid/non-opposing metadata, unsupported sinks, malformed pair styles, M6/emitter partial budgets, generation budget,
shared plan/task, multiplayer worlds/permissions/range, post-plan revocation, owner-specific directions/speeds, health
failures, cleanup/replacement/stale callbacks, teleport, transactional failures and protected command/Paper wiring.

Engine runtime dependencies empty; adapter runtime only accepted engine, Paper compileOnly. Zero production compiler
warnings. JAR CRC/provenance/package checks pass, all 61 embedded engine class bytes equal the fresh engine JAR.
Pinned API SHA-256 f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556. All seven frozen independent reference
datasets/manifests regenerated byte-identically, M6 twice. Existing test-only JOML metadata warnings and Gradle notices
remain in the logs. Native Java 25 -Xlint:all/-Werror compile and 244/244 adapter tests also passed with no assertion fixes.

The first Actions run39763793156 passed engine tests but stopped at Paper compilation because the backend called a package-private finite-coordinate helper. A separate preserved follow-up commit replaces that call with direct Double.isFinite checks. No mathematics or expected values changed. The failed full job log is retained.

Local Gradle startup remains blocked: FileLockContentionHandler cannot determine a usable wildcard IP in the sandbox.
No local check/Paper build ran after that startup error, no permission retries or network bypass. Fresh Actions builds
the exact tree independently; it is not an old report. Full local/Actions logs, 86 XML, HTML, API signatures, source
integrity checks and reference-regeneration results are retained. Unchanged K1 synthetic timings measure Arcane Halo,
not stream trajectories, native performance or TPS/MSPT/FPS. No Minecraft test result is claimed.

## Delivery and focused next review

JAR `bedwenton-vfx-paper-0.8.0-streams.1.jar`, SHA-256 `0af73be9b7be07ce3e5826b157b26bce55026084238b92259ecd38792b3df2b3`. Original downloaded artifact digests/CRC are verified, and standalone
JAR equals the full verification JAR byte-for-byte. The source ZIP, exact implementation/adapter diff, native history
bundle, SHA256SUMS.txt, recovery/checkpoint data, logs and smoke checklist are preserved under /workspace/output/dual-streams/.
A packaging-only Actions workflow publishes source ZIP, original JAR, checksums and a clearly labeled GitHub snapshot
bundle. That published snapshot bundle is distinct from the original native-history bundle preserved locally.

- streams-java25-verification-39764375954: https://github.com/Koi36m/Bedwenton/actions/runs/39764375954/artifacts/12419148840
- bedwenton-vfx-paper-0.8.0-streams.1: https://github.com/Koi36m/Bedwenton/actions/runs/39764375954/artifacts/12419064261


See DUAL-STREAMS-EXPERIMENT.md for the focused Minecraft checklist. First verify two particles actually travel in the
expected directions, then compare S/circle formation and buildup across separate normal/slow/fast runs. Fixed cadence
is 4 ticks; inspect it rather than adding a runtime tuning system. Do not replay every old checkpoint absent a regression.
Stop after delivery for independent review. Hypothesis remains unconfirmed until native observation. No K2–K4,
main merge, release or live deployment.

Full Actions toolchain:

```text
325f58aa3034c51aa34ef1cb05eb5c0a1e51dfb3
fb0830a0c6549671ce0c1502dd3bfc16f36e3c2d
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
javac 25.0.4.1
Downloading https://services.gradle.org/distributions/gradle-9.1.0-bin.zip
............10%.............20%.............30%.............40%.............50%............60%.............70%.............80%.............90%.............100%

Welcome to Gradle 9.1.0!

Here are the highlights of this release:
 - Full Java 25 support
 - Native task graph visualization
 - Enhanced console output

For more details see https://docs.gradle.org/9.1.0/release-notes.html


------------------------------------------------------------
Gradle 9.1.0
------------------------------------------------------------

Build time:    2025-09-18 13:05:56 UTC
Revision:      e45a8dbf2470c2e2474ccc25be9f49331406a07e

Kotlin:        2.2.0
Groovy:        4.0.28
Ant:           Apache Ant(TM) version 1.10.15 compiled on August 25 2024
Launcher JVM:  25.0.4.1 (Eclipse Adoptium 25.0.4.1+1-LTS)
Daemon JVM:    /usr/lib/jvm/temurin-25-jdk-amd64 (no JDK specified, using current Java home)
OS:            Linux 7.0.0-1017-azure amd64
```
