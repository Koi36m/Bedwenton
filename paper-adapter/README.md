# M7 opt-in Paper rendering adapter

Requires JDK 25, the root Gradle 9.1.0 wrapper, Python 3 and access to Maven Central/PaperMC.
The engine's root build is unchanged. From the repository root:

```sh
java -version
javac -version
./gradlew --version
bash paper-adapter/tools/verify.sh /absolute/path/to/fresh-evidence
```

The runner stops immediately on any failed build. All seven accepted reference datasets regenerate to a fresh directory.
The exact stable Paper API pin and independently downloaded SHA-256 are in `gradle.properties` and
`paper-api-provenance.json`. The metadata bootstrap tool is separate from the build; it never selects a build dependency.
`check` verifies the downloaded pinned API's hash, plugin metadata, absence of server/test libraries, and byte-identical
embedding of every engine class. The adapter runtime consists of the engine only; Paper is compile-only.

## Disposable-server installation and rollback

Real-server smoke is NOT TESTED by this package until separate evidence records it. Do not use the live SMP.

1. Use a fresh ordinary Paper 26.3 server with Java 25. Record the official server build, server JAR SHA-256,
   Java version, and matching Java client versions. Save a clean startup/shutdown log before installing.
2. Copy `paper-adapter/build/libs/bedwenton-vfx-paper-0.7.0.jar` into the disposable server's `plugins/` directory.
   Start normally. No effects or repeating task should start automatically. Inspect the generated config.
3. Run the S1–S17 checklist in `docs/m7/M7-TEST-AND-SMOKE-PLAN.md`. Record console output, status, two-client
   screenshots/video, exact-position count-0 behavior, red DUST appearance and owner/view isolation.
   Command permissions default to op. Nonowners need `.view`; owners need `.use` for their own ring.
4. Commands: `/vfxdemo ring [simple|balanced|heavy]`, `/vfxdemo stop`, `/vfxdemo stop all` (admin), `/vfxdemo status`.
   A repeated ring command replaces the owner's ring. The anchor stays at the captured block centre.
5. For S14, one ring per player means two clients can exercise at most two simultaneous rings. Testing all 16
   requires 16 eligible owners. Record planning and emission nanoseconds separately over 200 ticks and server MSPT
   if available. A single run provides no performance guarantee.
6. Stop the server before changing config. Invalid values refuse enable; no invalid value silently defaults.
   `/reload` is unsupported. Ceilings bound work; they are not recommended server settings.
7. Roll back by stopping the server and removing the plugin JAR and `plugins/BedwentonVfxDemo/`.
   No world/player data is persisted. Confirm no plugin-owned scheduled task remains on shutdown.

Main thread only; ordinary Paper only; no Folia, Bedrock parity, moving anchors, gameplay effects or external plugins.
The server tick API is an int; a server tick counter reset/wrap stops rings whose start is now in the future.
A server restart creates a new session. Local timings are diagnostics, not packet/client rendering counts.
All S1–S17 rows, two-client visibility, in-game geometry, red DUST data and shutdown behavior remain NOT TESTED
until run on an ordinary disposable server; offline fakes cover the pure controller paths only.
