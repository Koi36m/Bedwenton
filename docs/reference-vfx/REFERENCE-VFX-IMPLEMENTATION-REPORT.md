# Reference VFX — implementation review report

**READY FOR INDEPENDENT REFERENCE VFX IMPLEMENTATION REVIEW.** This is an isolated experimental prototype, not a
claim of artistic acceptance, production release or live deployment.

## Reference access and visual limits

Artistic authority: https://youtu.be/LqVTFFpzYz8, 00:00–00:10 only. The browser retrieved the YouTube page title and
footer but could not inspect playable frames. The supplied owner brief, dimensions and approximate phases were used
as explicit prototype assumptions. No frame-by-frame comparison, inferred video measurement or Minecraft visual
observation is claimed. Similarity to the reference, native END_ROD accumulation, star appearance, transparency/glow,
clipping and performance require the focused independent Minecraft smoke review in REFERENCE-VFX-PROTOTYPE.md.

## Implementation

Use `/vfxreference [simple|balanced|heavy|off|status]` with the existing m8.enabled opt-in. No argument uses the existing
default tier; heavy obeys heavyAllowed. The Paper BasicCommand and pure command handler use bedwenton.vfxdemo.use,
player/config checks and existing start-readiness checks. The existing M7 owner/view/range/world/identity/permission
gates apply to every recipient, including post-plan revocation. No new permission, automatic start or configuration.

Exactly 200 GAME ticks; body-yaw following, feet-relative anchor. A vertical white END_ROD ring (radius 1.3, height 1.55,
rear offset 1.0) stays stable while a new four-cubic inward comma/spiral rotates about its normal every 80 ticks. Four
progressive curve reveals form an asymmetric motif distinct from the historical two-stroke S. Geometry is sampled
once via M3/M4 and animated with M5. It stays behind the torso and never follows head pitch or camera rotation.

At ages 0–19 there are sparse warm motes; crown/ground start 20, white reveal starts 36 (first particles 40), accents 40,
complete motif 100 through 199. Stop spawning at 200; pre-existing client particles expire naturally. No title typography,
camera cuts, sounds or final flare. The golden ground ring repeats an expanding cycle. The yellow-lime crown slowly
spins/floats above the head; white/gold head crosses alternate; warm motes move deterministically upward/downward.

White and ground emit every 4 ticks, crown every 2 ticks, each accent once per 16 ticks (white phase 0/gold phase 8), ambient
every 8 ticks. Native END_ROD lifetime is unchanged. Sixteen cadence entries reuse five immutable recipes, all layer/
shape objects reused across owners/replacements. Playback/now/frameOrdinal retain full elapsed time, so quiet/missed
ticks cannot pause phase, slow rotation or cause catch-up bursts. No random state, hidden clocks, per-effect timers,
new task, NMS, reflection, chunk load, world broadcast or production library dependency.

| Tier | Union sampled points | Maximum due scene/points | Outline | Curves | Crown | Each accent | Ground | Ambient |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Simple |24|23|8|8|3|1|2|1|
| Balanced |48|45|18|12|6|3|4|2|
| Heavy |72|68|24|20|8|4|8|4|

The two accents never emit together. Simple is intentionally coarse; heavy is the most readable geometry within
existing ceilings. Cadence omission happens before the existing one global M6 plan. M6 still conservatively reserves
the due scene maximum, including timed layers not yet visible. Quiet odd ticks use the existing empty-scene outcome
SKIPPED_UNSUPPORTED and cost zero reservation/placement; registration/safety/expiry continue. All existing budget,
priority, thinning/fairness and accounting rules remain unchanged. Under pressure, M6 can downgrade/thin as before.

## Protection and provenance

- Native verified K1.2.2 evidence baseline: `92a8f759dd81691e050eda3612eb480d134f473e`.
- GitHub baseline: `e1f81c2709a83f3b5fa9d714548b2e641bb10a5d`, same tree 6b4eb0ba54fef5277998e1a8d21bfe797c1eed3a.
- Native implementation: `a372703e61a9a3014031d49d8ab9e2c0a859c3ea`, branch `experiment/reference-vfx-10s`.
- GitHub tested snapshot: `9108e679b012f111b5a1fc51039965d213b43947`, branch `experiment/reference-vfx-actions`.
- Identical complete tested tree: `876d66f4eb04923c46dc4a04b6cc9af4d29d92eb`.
- Actions: https://github.com/Koi36m/Bedwenton/actions/runs/39757032499.

Native and connector commit metadata/ancestry differ; complete tested and final evidence trees are compared exactly,
not relabeled as identical commits. Full native original history/refs are in the Git bundle. Final checkpoint IDs are
in external GIT-CHECKPOINT.json. Evidence-only commits add this report and a packaging-only Actions workflow; tested Java/build/tests remain unchanged.

**2453 baseline files are byte-identical**, checked by FROZEN-BASELINE-SHA256SUMS.txt. Every original test, fixture,
golden vector/generator, engine class, planner/emitter/scheduler/registry and Arcane Halo K1.2.1/K1.2.2 class remains
unchanged. The only existing files changed are the plugin version, additive REFERENCE enum value and Paper wiring
for the separate command/session/style catalogue. Five new production classes live in workshop.vfx.paper.reference.
Historical M8Commands and M8StyleCatalog remain exact. Reference can coexist with aura/ring within existing caps;
reference off/replacement never removes the aura. Existing owner/admin stop, death/quit, disable and registry cleanup
also cover the reference kind. No K2/K3/K4 classes, main merge, rewritten history, release or live SMP deployment.

## Fresh automated verification

Independent parsing of the downloaded Actions JUnit XML confirms **631/631 passed, zero failures/errors/skips**,
across **85 XML reports**. All **611** baseline case IDs remain, plus **20** new reference cases.

| Suite | Passed |
|---|---:|
| M1–M6 |415|
| M7 |61|
| K0 |50|
| K1 |57|
| K1.1 |6|
| K1.2 |14|
| K1.2.2 |8|
| New reference prototype |20|

Java 25 and Gradle 9.1.0 wrapper, fresh engine and adapter clean test/check: successful. verifyNoRuntimeDependencies
passed; engine runtime empty, adapter runtime only the accepted engine, Paper compileOnly. Production compiler
warnings: zero. Plugin JAR integrity passed, all 61 embedded engine classes byte-identical to the fresh engine JAR.
Paper remains exact 26.3.build.204-stable, SHA-256 f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556.
All seven reference datasets/manifests regenerated byte-identically, including M3 2759, M4 3064, M5 1113 rows and M6
twice. Existing test-only JOML metadata warnings and future-Gradle notices are retained; no production warning added.

New tests cover rear/yaw/movement geometry, curved motif, exact quarter-turn/80-tick recurrence, phases, cadence,
continuous clocks, crown/ground bounds, deterministic twinkles/motes, expiry/quit/death/disable, replacement with
Arcane Halo preserved, one shared plan/task, all budget ceilings/accounting, multiplayer permission/world/range
isolation, post-plan revocation, stale/forged anchors, transaction failure, separate command grammar/gates and wiring.
Existing tests and tolerances were not edited. No failed test assertions were weakened; native adapter 216/216 also
passed on its first run. No real-server assertions or reference-image match are represented by those tests.

Local Gradle could not start FileLockContentionHandler because the sandbox exposes no usable wildcard IP. No local
Gradle check/Paper build ran after that startup error. No permission retries or network bypass. Fresh Actions built
the exact source tree independently. Local Java 25 -Xlint:all/-Werror production compilation, all 216 adapter tests and
independent regeneration also passed. Local logs and exact blocker are retained alongside complete Actions evidence.
Synthetic measurements generated by the unchanged K1 test belong to the Arcane Halo baseline; they are not a
reference-effect performance benchmark and not TPS/MSPT/FPS, network latency or native emitter timings.

## Delivery and next review

JAR `bedwenton-vfx-paper-0.8.0-reference.1.jar`, SHA-256 `562bb69dc9d6ae879cf6f36fdfa3ef38db93e293786430ee0c90943d034591b5`. It is byte-identical in the standalone and full Actions artifacts,
with verified original artifact SHA-256 and ZIP CRC. Full logs, all 85 XML, HTML reports, toolchain/API signatures,
native logs and reference-regeneration results are preserved. Complete source ZIP includes external evidence;
full-history native bundle, SHA256SUMS.txt, exact implementation/test diffs, recovery validation and focused smoke
checklist are under `/workspace/output/reference-vfx/`. A packaging-only Actions workflow publishes a downloadable complete source ZIP, original JAR, checksum manifest and GitHub snapshot bundle. That snapshot bundle is explicitly distinct from the original native-history bundle kept locally. No generic conversation attachment export is available.

- reference-java25-verification-39757032499: https://github.com/Koi36m/Bedwenton/actions/runs/39757032499/artifacts/12416536617
- bedwenton-vfx-paper-0.8.0-reference.1: https://github.com/Koi36m/Bedwenton/actions/runs/39757032499/artifacts/12416342258


Stop for independent source/visual review. The linked video remains artistic authority; owner verification of the
first ten seconds is especially necessary because playable frames were unavailable here. No independent acceptance
or native visual match is declared. No new work on any other milestone.

Full Actions toolchain output:

```text
9108e679b012f111b5a1fc51039965d213b43947
876d66f4eb04923c46dc4a04b6cc9af4d29d92eb
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
