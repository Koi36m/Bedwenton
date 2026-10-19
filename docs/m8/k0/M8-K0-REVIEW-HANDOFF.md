# M8 K0 independent implementation review handoff

Checkpoint K0 only. Review the real additive RingDriver patch, transactional registry, identity guard,
Paper snapshot reader and wiring. Read the owner authorization and independent R3 review first.
M1–M6 are frozen. M7's 61 tests, all other M7 core classes and original verification tools are preserved.
Only the five authorized M7 support files listed in AUTHORIZED-CHANGED-FILES.json may differ.

Reproduce with Java 25 and the root Gradle 9.1.0 wrapper, Python 3, and Maven Central/PaperMC access:

```sh
java -version
javac -version
./gradlew --version
sha256sum -c docs/m8/k0/FROZEN-M1-M6-SHA256SUMS.txt
sha256sum -c docs/m8/k0/FROZEN-M7-SHA256SUMS.txt
sha256sum -c docs/m8/k0/APPROVED-R3-SHA256SUMS.txt
sha256sum -c docs/m8/k0/IMPLEMENTATION-SHA256SUMS.txt
bash paper-adapter/tools/m8-k0/verify.sh /absolute/path/to/new-evidence
```

The new script runs clean root tests/check, clean full Paper adapter tests/check (including verifyPluginJar
and recordPaperApi), verifies separate 415/61/50 counts, empty engine runtime dependencies and all seven fresh
reference regenerations. Do not use the historical M7 runner's fixed 61-total expectation for the enlarged suite.
The unchanged M7 runner remains in history and on disk. Failed commands propagate through pipefail.

The plugin version is 0.8.0-k0 and the exact API remains 26.3.build.204-stable. On a disposable test server only,
stop the server, back up the M7 JAR/config, replace the JAR and start with m8.enabled:false. Re-run M7 S2 (idle
enable), S4 (ring), S8 (repeat/stop), S9 (movement/world/death/quit cleanup) and S11 (restart). Record exact server,
Java/client versions, JAR SHA-256, logs and visual evidence. These rows remain NOT RUN without such evidence.
Then optionally restart with m8.enabled:true: no task or visuals starts automatically; aura/trail/activate report
not implemented, and ring/stop/status still work. Invalid M8 config must refuse enable. Verify clean shutdown.
Rollback: stop, restore the original M7 JAR and config, restart. No world/player data or M8 effects are persisted.

Do not interpret this checkpoint as Aura, Trail, Activation, combined showcase, generic entity attachment,
performance qualification, merge/release/deployment approval, or final acceptance. K1–K4 need separate authorization.
Exact source/JAR identities, build evidence and recommendation are below; archive and bundle hashes are in the delivery manifest.

## Final evidence and recommended review decision

**READY FOR INDEPENDENT K0 IMPLEMENTATION REVIEW**, with real-server smoke still NOT RUN.
Authoritative automated run: https://github.com/Koi36m/Bedwenton/actions/runs/39203576470 .
Artifact: https://github.com/Koi36m/Bedwenton/actions/runs/39203576470/artifacts/12164892334 .
526/526 passes = 476 unchanged regressions + 50 production K0 tests, zero failures/errors/skips. Both checks,
verifyPluginJar, recordPaperApi and all seven dataset regenerations pass. See the implementation report and
FINAL-VERIFICATION.json for source identities and full results, cloud/ for 75 JUnit XML files and console logs,
and local/RingDriver.patch for the exact accepted-source seam change.

Native production: a033180b3128c51b2178a1b7a0c8fb1cf6b90378 (R3 descendant).
Verified GitHub snapshot: 37fef2a2670e6bed14661909ce83c7354ef9e8b2 (distinct history, exact tree equality).
Complete verified tree: eb09bdc031ac2bf06b82bb75473f611fbf05f991.
JAR SHA-256: 1516906c829c3e8967f3f60a2b913b0131f9c2ddcd4c804cc404572e1d88f318.
Raw Actions artifact SHA-256: 167ec1c92874555a8d50585624b2016697320e6c71ccd7af48d3800d80b21a6a.
Source ZIP, full-history bundle and final evidence commit hashes are supplied in the delivery's
SHA256SUMS.txt and GIT-CHECKPOINT.json. This avoids impossible self-referential archive/commit hashes.
The bundle preserves all six original design refs plus the separate original native M7 evidence branch.

For installation, stop a disposable server first, back up/remove the old M7 JAR so only one BedwentonVfxDemo
plugin JAR is installed, and install bedwenton-vfx-paper-0.8.0-k0.jar. Keep m8.enabled:false for the required M7
smoke rerun. Stop the server before any config changes; /reload is unsupported. Back up and restore the original
M7 JAR/config for rollback. No real server was changed by this task.

Recommended adversarial focus: failures before/after the single registration commit, candidate aliasing and
reentrancy, callback generations across failed starts and zero-request ticks, bound identity after release/rejoin,
forged owner/world/time/request IDs at both guard layers, shared-cap start order and no planner/emission duplication.
Verify disabled routing still delegates exactly to unchanged M7 commands and that the actual Paper lifecycle leaves
no pending tasks after shutdown. Do not authorize K1 on the basis of this implementation report alone.
