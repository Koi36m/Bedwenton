# M7 implementation checkpoint

Owner authorization is preserved verbatim in `docs/m7/M7-PRODUCTION-AUTHORIZATION.txt` and supersedes the
historical proposal status. The separately supplied independent acceptance review is preserved byte-identically.
Accepted R3 tip: `4eeb8354693463a95f7dd48cbf3891701dcc6acf`.
Accepted M6 commit: `c43a66e656a77aa82bd72d8ffe27f1a037240e99`, tree `ef496f7b0c34796cdd30fe35d79cfd4e0c071ece`.
All 1,040 baseline files and 58 original R3 documents/tests/reference files are frozen by SHA-256 manifests.

An isolated `paper-adapter/` build contains the pure controller/emitter and thin Paper lifecycle/command/event layer.
The 25 R3 tests and fixture were copied byte-identically into the production test suite. Additional core tests cover
config bounds, IDs, deterministic prefix coordinates, budgets, permissions, cancellation, stale callbacks,
one plan per tick, command grammar, style failures, source boundaries and synthetic JAR rejection.

Paper provenance: official repository metadata was read in GitHub Actions because local sockets are blocked.
The metadata's `latest` entry is a prerelease; the build uses the highest explicitly listed stable build,
`26.3.build.204-stable`, never a floating dependency. API JAR SHA-256:
`f6852324bbf519d8c27ba06cf2391ee21aed542852de9ac20b9403b7d6ea1556`.
The pinned JAR was downloaded through the supported Actions-artifact mechanism and its required signatures checked
with JDK 25 `javap`. Successful stable-provenance run:
https://github.com/Koi36m/Bedwenton/actions/runs/39067638639
The earlier provenance run downloaded the metadata's prerelease latest; that artifact is not the production dependency.

Local JDK 25.0.4.1 compiles the engine and core with `-Xlint:all -Werror`; 59 adapter tests pass under the actual
JUnit 5.12.2 launcher. Local Gradle cannot create FileLockContentionHandler (no usable wildcard IP), and local curl
cannot connect to the configured proxy. No permission retries or Java substitution were used.
Fresh full Gradle/Paper compilation and regression results are pending at this checkpoint.

S1–S17 real-server/client smoke: NOT TESTED. There is no ordinary Paper server with two matching clients available.
The practical install/rollback and honest two-versus-16-ring checklist is `paper-adapter/README.md`.
No in-game visibility, packet count, client-FPS or MSPT guarantee is claimed. M7 acceptance is pending independent
implementation review; nothing is merged into main, deployed to the live SMP, or extended to M8.
