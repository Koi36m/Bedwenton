# M8 K1 implementation and verification report

**READY FOR INDEPENDENT K1 IMPLEMENTATION REVIEW.** Acceptance remains with ChatGPT and the project owner.
K1 only; no K2–K4, merge, deployment, release, Minecraft gameplay changes or unrelated plugin changes.

## Provenance

- Accepted R3 design: `17ad99186f6b2a73eaccaa22ff5aa0f0e7fd6d82`.
- Native K0 production: `a033180b3128c51b2178a1b7a0c8fb1cf6b90378`, tested tree `eb09bdc031ac2bf06b82bb75473f611fbf05f991`.
- Native K0 evidence parent: `9cfd4eac65502161ffa079a34bd1a5198580bb28` (exact clone, strict fsck passed).
- **Native K1 implementation: `632e3564420db67209cc1babc8b2039c80badd6f`.**
- **GitHub tested snapshot: `ca4e8170aa2c3d9a47802f145938834eb0cc7d42`.**
- **Both complete tested trees: `c06b957fc5adbdf308c627c9d1ac23ae182cb28c`.**
- Native branch: `m8-k1-implementation`; exact R3/K0 and native M7 history and old branch refs preserved in the bundle.
- GitHub branch: `m8-k1-actions-verification`, parent `d5ad9a321cd230df56971bb2068605b25aacb3bb` (K0 evidence snapshot).
  Native and GitHub commits are distinct: the connector publishes a new commit, with an explicitly verified equal full tree.
  No unavailable commits were reconstructed. The native bundle preserves native ancestry; GitHub snapshot objects have their
  own actual history on GitHub. Final evidence is an additive child of native K1; external GIT-CHECKPOINT.json identifies it.
- Fresh Actions: https://github.com/Koi36m/Bedwenton/actions/runs/39218386254 — successful, independently inspected.
- Source tree integrity passed in Actions before building. No source/build/test/workflow changes after the tested checkpoint.
- `main` remains `870c9fd75bf9a7a9d619179993d37f2b6487b081`. No force pushes or merge.

## Implementation

`AuraRecipe` samples the two approved circles with M3/M4 once at enabled-session construction. Its three immutable
M5 scene variants are shared across owners and replacements. M5 motion uses the original start's GAME Playback,
60 tick positive waist spin and80 tick negative halo spin. M1 Pose/Quat maps body yaw to the approved caret frame;
head pitch/head yaw do not tilt or orient the effect. Each fresh validated snapshot supplies position/world for that tick.
There is no history, interpolation or geometry rebuilding. The effect snaps across worlds without generating a line.

`AuraSession` validates command-start eligibility before K0's transactional registration; failed replacements retain
existing effects. `AuraEffect` carries bound owner/viewer identity and defensively guards direct contributions.
The unchanged production registry centrally validates every update, counts rejection reasons and terminates after
configured consecutive invalid snapshots. Death/quit listeners stop effects; expiry/off/stop/all/close dispose them.
The unchanged shared driver owns one task and one planner call, global capacity and owner-first authorization.
The unchanged native emitter uses per-recipient spawning with count0 and force=false and retains complete accounting.

Only four pre-existing files changed: Paper wiring, prerelease version, config comment and plugin metadata. All existing
core classes, K0 registry/guards/driver, accepted M1–M6 sources and every prior test are untouched. Six new production
classes implement only aura/recipe/session/commands/catalog/snapshot diagnostics. New commands respect opt-in, default
Simple and Heavy refusal; `aura off` preserves a ring; original M7 grammar and status fields remain. No auto-start.
M8 status adds own aura tier, bounded registrations, defaults and tick-labelled snapshot timing; debug logs expose timing
alongside existing emission reports. K2/K3 prefixes explicitly remain unavailable and are not suggested.

The palette amendment is additive: M7 END_ROD for waist, new `bedwenton:m8_arcane_halo_cyan` DUST (`0x5CE1FF`,0.7f)
for halo. Original M7 and historical future violet/gold keys are not reassigned. See M8-K1-ARCANE-HALO-AMENDMENT.md.

## Fresh verification

| Suite | Passed | Failed/errors/skipped |
|---|---:|---:|
| Unchanged M1–M6 |415|0/0/0|
| Unchanged M7 |61|0/0/0|
| Unchanged K0 |50|0/0/0|
| New genuine K1 production tests |57|0/0/0|
| **Combined** |**583**|**0/0/0**|

All 81 JUnit XML reports are included (59 engine,22 adapter), plus console logs and HTML reports. Engine and adapter
`clean test` and `check` succeeded. Plugin JAR verification and pinned Paper API checksum/signature checks succeeded.
Engine runtime classpath is empty. Adapter runtime contains only that engine; Paper is compileOnly. The packaging check
compares all 61 engine class files byte-for-byte with the freshly built engine JAR, rejects server/test namespaces,
checks CRC/duplicate entries/version/provenance, and passes. No K2–K4 visual class is packaged.

Java 25 and Gradle 9.1.0 are independently recorded in full in cloud/logs/source-and-toolchain.log and native local logs.
Cloud uses Temurin25.0.4.1; local native checks use Oracle25.0.4.1. Wrapper revision
`e45a8dbf2470c2e2474ccc25be9f49331406a07e`. Paper API exactly `26.3.build.204-stable`, SHA-256
`f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556`.
Zero production compiler warnings; adapter -Xlint:all/-Werror retained. Five unchanged JOML 46.0 classfile metadata
warnings occur in engine TEST compilation only; they are not production/oracle/tolerance failures. Gradle also reports
existing deprecated features for future Gradle 10, without failing supported9.1.0.

Native Java 25 compilation/JUnit independently passes415 engine +168 adapter. Local full Gradle execution remains unavailable
because this sandbox blocks local socket coordination (the prior K0 blocked log is preserved in docs/m8/k0/local).
No permission retry or sandbox bypass was attempted. The successful fresh full Gradle/Paper run is the Actions run above,
not a substituted historical report. One initial NEW fixture failure and its correction are documented in TEST-MAPPING;
the full first-failure log is retained. No frozen expectations or tolerances were changed.

All seven frozen M1/M2/M2-review/M3/M4/M5/M6 datasets and manifests regenerated byte-identically both locally and in
Actions, including M3's2759 rows, M4's3064 rows, M5's1113 rows and M6's45 cases/158 expectations. M6 regenerated twice.
`FROZEN-K0-SHA256SUMS.txt` verifies1453 unchanged files (all prior tests included); the four expressly authorized edits have
old/new SHA-256 in AUTHORIZED-EDIT-PROVENANCE.json. Original R3's61 documents/reference files remain byte-identical.
Implementation checksum and final evidence manifests are supplied. Full production/implementation diffs are included.

## Synthetic measurements — not a server benchmark

Each scenario has 600 warm-up ticks followed by 1200 measured ticks of real production aura/registry/planner/emitter with an
in-memory observation host and recording sink. Counters are exact; timings are environment-specific observations, not
pass thresholds, Paper network latency, client FPS, TPS or MSPT. Both Heavy scenarios are sustained. An additional
72000 tick simulated world-change/replacement soak verifies bounded registrations and no extra scheduling.

| Scenario | Generated/frame | Planned placements over1200 ticks | Succeeded/failed calls | Planner ns p50/p95/max | In-memory emitter ns p50/p95/max | Snapshot read ns p50/p95/max | Whole fake tick ns p50/p95/max |
|---|---:|---:|---:|---|---|---|---|
| SIMPLE | 12 | 14400 | 14400/0 | 17362/22733/82114 | 2404/3366/29044 | 491/1833/24817 | 38742/56446/112821 |
| BALANCED | 27 | 32400 | 32400/0 | 16892/21761/50184 | 3296/3977/121087 | 431/581/1984 | 36609/51417/156963 |
| HEAVY | 36 | 43200 | 43200/0 | 17723/22662/45034 | 4148/4939/31229 | 451/611/22251 | 38452/52067/77124 |
| two-heavy-auras-two-viewers | 72 | 172800 | 172800/0 | 20177/27051/75941 | 6923/8376/54992 | 510/631/16882 | 42439/65762/299961 |

Nominal generated/placement counts per viewer are12/27/36; M6 may thin or skip under tighter budgets. Budget tests
verify aligned total/per-viewer limits and no emitter EMISSION_LIMIT with the approved policy. Deliberately failed cyan
sends disable that style on exactly the third consecutive failure; accounting remains exact and the waist continues.
All successful measurement scenarios report zero failed calls. Synthetic idle starts zero tasks; real idle server timings
remain NOT RUN. Raw synthetic-measurements.json and native numbers are separately preserved; no cross-run timing equality
is asserted. Snapshot timing covers platform-read duration only, excluding validation/pose/viewer work. It is diagnostic,
injected separately from deterministic game time, constant-memory and labelled with its sample tick.

The engine still allocates evaluated coordinate arrays, plans and delivery/context structures per tick. The adapter adds
snapshot/Pose/draft objects; the shared immutable recipe/shapes/payloads are reused. Viewer selection retains O(E*P)
work under existing caps. No per-aura task, unbounded history, mutable global state, new dependency, chunk loading,
NMS, reflection, broadcast or gameplay operation was added. No heap-allocation profiler was run.

## Remaining limitations and owner handoff

All new K1 real-Paper/client smoke checks are **NOT RUN**. Owner-reported K0 observations remain historical K0 evidence;
they are not K1 passes. Read M8-K1-SMOKE-AND-INSTALL.md for opt-in installation, rollback and focused checklist.
Two-player real-client permission isolation is required before inviting outside players. Actual body-yaw observation,
Paper event ordering, particle persistence/smear, native payload rendering, shutdown/restart and real server performance
require the owner's disposable-server run. No screenshots, multiplayer results, FPS, TPS or MSPT are claimed.

Uniform rings are rotationally symmetric and persistent particles may soften readable motion; no extra sparkle layer,
point reservation or asymmetry was added without authorization. END_ROD appearance follows the client resource pack.
The shared END_ROD key also shares session-wide style health with M7 rings, by design. K0's discontinuity serial remains
reserved; aura has no history and does not consume it. The inherited Bukkit integer game-tick clock/long-running server
rollover behavior is unchanged. No ARM validation, new mutation campaign or profiler run is claimed.

No unexpected production failures or specification contradictions remain in available automated verification. Approval
of visual appearance and final K1 acceptance remains pending independent review; no K2 work has begun.

## Deliverables and publication

Plugin `bedwenton-vfx-paper-0.8.0-k1.jar` SHA-256: `db5a3895f802802dd7735fe64cd6fb735f5a0128971ec6fd7ed276463038a3b8`.
The standalone Actions artifact is separate from the full verification artifact:

- m8-k1-java25-verification-39218386254: https://github.com/Koi36m/Bedwenton/actions/runs/39218386254/artifacts/12170557123
- bedwenton-vfx-paper-0.8.0-k1: https://github.com/Koi36m/Bedwenton/actions/runs/39218386254/artifacts/12170517214


The external delivery directory contains source ZIP, complete native Git bundle, original Actions ZIPs, standalone JAR,
SHA256SUMS.txt, checkpoint/ref/history records, diffs and recovery validation. The source ZIP contains final evidence
and its own SOURCE-SHA256SUMS.txt; the source/build/test files are unchanged from the successful tested checkpoint.
The Git bundle contains exact original native ancestry, not a claim that connector snapshots are native commits.

The designated conversation-output path is absent in this executor and no general attachment-export tool is available.
Local files are preserved under /workspace/output/m8-k1; no nonworking conversation download links are invented.
Use the real standalone JAR/evidence Actions artifacts and published source branches for remotely accessible material.
