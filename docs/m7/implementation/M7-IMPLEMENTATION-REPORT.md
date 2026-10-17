# M7 — READY FOR INDEPENDENT IMPLEMENTATION REVIEW

Implementation and available automated verification are complete. Real-server acceptance remains pending.
No merge, live SMP deployment, M8 work, or release acceptance was performed.

## Authoritative recovery and preservation

The two uploaded ZIPs are byte-identical, SHA-256
`f911f7ce65cb4afa6867531d18937e28e6f3ab966a05a880701af54c8145c431`.
ZIP CRC and all 67 package-manifest entries pass. The complete no-prerequisite bundle passes Git verification
and strict fsck. Original R3 tip `4eeb8354693463a95f7dd48cbf3891701dcc6acf` and its exact ancestors are preserved.
Owner authorization supersedes the historical proposal status; it and the subsequently supplied independent
review are preserved verbatim. `INPUT-VERIFICATION.json` is the historical pre-review-arrival checkpoint;
`INPUT-ACCEPTANCE.json` records completion of the required inputs.

Accepted M6 is `c43a66e656a77aa82bd72d8ffe27f1a037240e99`, tree
`ef496f7b0c34796cdd30fe35d79cfd4e0c071ece`. All 1,040 files in that snapshot remain byte-identical.
All 58 original R3 files also remain byte-identical. The root build, prior workflows, engine sources,
tests, fixtures and generators are unchanged. The 25 R3 behavioral tests and their fixture were copied
byte-identically to the adapter tests. Original expectation/tolerance changes: zero.
The SHA-256 manifests and `local/preservation.json` record these comparisons.

## Functionality

The isolated `paper-adapter/` Gradle build consumes the unchanged engine through composite substitution.
Its platform-neutral layer provides sequential session IDs with no reuse, strict configuration validation,
a closed resolved style catalogue, owner-first unique viewer selection, a single global M6 plan per tick,
allocation-rank emission, exact per-recipient selection prefixes, finite coordinate/context checks,
world-boundary rules, aligned budgets, complete accounting and cross-tick style failure tracking.
The third consecutive send failure disables that style immediately for the session.

The thin Paper layer supplies JavaPlugin lifecycle, native BasicCommand registration, synchronous scheduling,
owner/world/death/teleport/quit listeners, world/chunk/permission checks and per-player count-0 particle calls,
zero offsets/speed and force=false. The opt-in ring is built with the approved M5 scene and M6 recipe:
16/32/48 white END_ROD points plus 8/16/24 red DUST points, radii 2.0/2.25, static block-centre anchor,
40-tick loop. Nothing starts automatically. One ring per owner is replaced on repeated starts.
Stale callbacks, repeated stops and duplicate tick calls cannot reset placement budgets.

## Exact source identities

| Identity | Value |
|---|---|
| Initial native implementation | `38fdbdf11a0e6d5e2ba1b079f98c3101ee6abdd3` |
| Final native production/test checkpoint | `c0fa177a73c0e088fca047d606e6f00a4a45bfd7` |
| GitHub verified source snapshot | `ed5fc7bff548edf4b005e919495b22213a65b64e` |
| Entire verified tree, native and GitHub | `afa33672b5850bdda3be636415659fa27ac53047` |
| Entire verified adapter subtree | `1d53ff79bb3d8113ca52149f5e7772c07cbae96b` |
| Frozen engine production subtree | `10fa150464001d617d33937f42a0c9da45ed99bb` |
| Frozen engine test/resource subtree | `d91d1e983c8497980a8e6d7b0494c81926720ab7` |

Native branch `m7-paper-adapter-implementation` descends from the exact R3 tip. GitHub publication uses new
snapshot commits because the available connector does not publish the original native commit objects.
These snapshots are explicitly distinct; their complete source tree was verified equal before publication.
`m7-actions-verification` remains at the tested snapshot. A separate evidence branch publishes the final records.
The local full-history bundle preserves the exact R3/native implementation ancestry; no existing history is rewritten.
The evidence-only child commit is identified in the delivery's `GIT-CHECKPOINT.txt` to avoid self-referential hashes.

## Java, Gradle and pinned Paper provenance

Local: Oracle Java/javac `25.0.4.1`, build `25.0.4.1+1-LTS-5`.
Actions: Temurin Java/javac `25.0.4.1`, build `25.0.4.1+1-LTS`.
Gradle wrapper: 9.1.0, revision `e45a8dbf2470c2e2474ccc25be9f49331406a07e`.
Full outputs are in `local/*version.log` and `cloud/logs/identity-and-integrity.log`.

Official PaperMC repository metadata was read successfully in Actions. Its `latest` entry is a prerelease;
the production dependency instead uses the highest explicitly listed stable version, `26.3.build.204-stable`.
API JAR SHA-256: `f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556`.
The metadata, POM, successful request URLs and hash receipt are in `paper-provenance/`.
Provenance run: https://github.com/Koi36m/Bedwenton/actions/runs/39067638639 .
The earlier metadata bootstrap fetched the prerelease `latest`; that JAR is not used by production.
JDK 25 `javap` checks the pinned JAR's native command, lifecycle, scheduler and particle signatures;
actual Paper compilation also succeeds. The build rechecks the exact downloaded API hash.

## Fresh verification results

Final run: https://github.com/Koi36m/Bedwenton/actions/runs/39068228019
Artifact: https://github.com/Koi36m/Bedwenton/actions/runs/39068228019/artifacts/12113139938
Artifact ZIP SHA-256: `0e0558bd51543382be6e94e062c0ac81d311eb48be19fb23cb2b464569eccfd1`.
The original downloaded artifact was checked against the Actions digest and ZIP CRC.

| Suite | Tests | Passed | Failures/errors/skips | XML files |
|---|---:|---:|---:|---:|
| Accepted M1–M6 | 415 | 415 | 0/0/0 | 59 |
| M7 production adapter (25 unchanged R3 + 36 additional) | 61 | 61 | 0/0/0 | 10 |
| Total | 476 | 476 | 0/0/0 | 69 |

Fresh `clean test` and subsequent `check` succeed for both root and adapter builds. The root
`verifyNoRuntimeDependencies` guard passes and runtimeClasspath reports `No dependencies`.
The adapter runtime contains only the accepted engine; Paper is compile-only. Production compiler warnings: zero;
the adapter compiles with `-Xlint:all -Werror`. The unchanged engine tests retain five historical JOML classfile
metadata warnings. Gradle also reports build-feature deprecations for Gradle 10; these are not Java compiler warnings.
Gradle 9.1.0 is the supported build version here.

All seven datasets and manifests regenerate byte-identically in fresh directories, including the M6 second-run
check: M1, M2, M2 review, M3 (2,759 rows), M4 (3,064 rows), M5 (1,113 rows), M6 (45 cases/158 request expectations).
Logs are complete, and JUnit XML and HTML reports are preserved under `cloud/`.
The production runner propagates a failing Gradle status even if stale passing XML exists; a synthetic failure
fixture verifies exit code 23. Config boundary, ID collision/release, duplicate audience, command grammar,
permission isolation, selection bit patterns, point edges, session style health, cancellation, thread,
budget and synthetic package-failure checks are included.

An earlier complete run also passed 415+59 tests:
https://github.com/Koi36m/Bedwenton/actions/runs/39068052667 .
Two final tests added world-point boundary coverage and a stale-report failure fixture; the final 415+61 run
is authoritative. No assertion was weakened. The original R3 mutant evidence is historical; a new production
mutation campaign was not run.

## Plugin JAR

`bedwenton-vfx-paper-0.7.0.jar`: 151,406 bytes.
SHA-256: `a23d857bf5a616689e33457175a75034e7bf201562ea9a7cb7947215ca5effa9`.
Its provenance identifies the actual Actions source snapshot, accepted M6 tree, pinned Paper API and Java version.
Plugin metadata has the expanded version 0.7.0 and native command registration; config defaults are included.
All 61 class files from the freshly built accepted engine JAR are embedded byte-identically; the package guard
compares every class against that JAR. A separate class-name comparison against freshly compiled frozen engine
sources agrees. The historical design spike's count of 63 is not used as a substitute for this measured class set.
There are no bundled Paper, Bukkit, Adventure, Mojang or JUnit classes. Synthetic contaminated/wrong-version JARs
are rejected. The compiled JAR and complete raw verification artifact are available through the Actions artifact.

## Local limitation and remaining acceptance gates

Local Gradle cannot create FileLockContentionHandler: `Could not determine a usable wildcard IP for this machine`.
Local curl cannot connect to the configured proxy. Both failures are preserved in local logs. No repeated permission
requests or Java substitution occurred. Actual JDK 25 native compilation and actual JUnit 5.12.2 execution also
passed locally (415 engine and 61 adapter tests); successful Gradle/Paper evidence comes from fresh Actions runs.

No ordinary Paper server with matching clients was provisioned. S1–S17, including visibility, count-0 exact-position
appearance, red DUST appearance, two-client isolation, real command/lifecycle integration, MSPT and timing samples,
are NOT TESTED. No 16-ring server test, client-FPS, packet-count or performance guarantee is claimed.
`paper-adapter/README.md` gives installation, verification, rollback and the practical owner checklist; the unchanged
approved smoke plan specifies every row. Two clients can create at most two rings; 16 simultaneous rings require
16 owners. Offline fakes are not represented as server smoke evidence.

The server exposes a signed int tick counter; a reset/wrap that makes the current tick precede the start stops
existing rings. Session data and disabled styles are not persisted across enable/disable. Main-thread-only Paper
26.3 behavior is the target; no Folia, Bedrock parity, async planning or gameplay integration is added.
Independent source review and real-server acceptance remain separate gates controlled by the owner and ChatGPT.
