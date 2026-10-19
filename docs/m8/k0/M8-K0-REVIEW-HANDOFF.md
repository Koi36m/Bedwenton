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
Exact source/JAR hashes, build evidence, recovery identifiers and final recommendation are supplied after verification.
