# M7 Kickoff — Paper 26.3 Rendering Adapter (lean vertical slice)

**Status:** kickoff / design authorization only. **Not** an implementation contract, deployment approval, or claim of Paper/Folia/Geyser compatibility.

**Repo:** `Koi36m/Bedwenton`
**Accepted M6 GitHub-tested snapshot:** `c43a66e656a77aa82bd72d8ffe27f1a037240e99`
**Accepted M6 tree:** `ef496f7b0c34796cdd30fe35d79cfd4e0c071ece`
**M6 verification:** https://github.com/Koi36m/Bedwenton/actions/runs/39015236273 (415/415; seven regenerated datasets)
**Accepted core:** M1–M6 must remain byte-for-byte unchanged; `main` untouched.
**Target:** disposable ordinary **Paper 26.3 + Java 25** server, pin actual Paper API build after resolving repository metadata. This milestone is not Folia-ready by assertion.

## Goal — an early in-game visual proof, without new gameplay logic

Deliver the smallest **opt-in Paper adapter/debug plugin** that can read an M5 scene through M6 `DeliveryPlanner.plan`, map known `StyleKey` values to supported Paper particles, and render *only* the selected points to *only* authorized, currently-valid viewer players. The first on-screen demonstration should be a static ring; optionally demonstrate a moving anchor after the static path passes. We explicitly do **not** integrate Bloom, Cloudstep, artifacts, enchant ownership, EcoEnchants, or damage/cooldowns during M7.

### Work packages

**M7 R1 — short design + test contract (Claude; reviewed by ChatGPT)**
- Design just the adapter/plugin interface, build isolation, exact-style mappings, per-viewer emission semantics, ID mapping, and main-thread lifecycle. State platform and host version assumptions.
- Provide a compile-tested SPI proposal (if tooling permits), the minimal test plan, disposable-server smoke checklist, and runnable Gradle/build plan. Avoid a second seven-document research marathon; at most 2–3 short documents plus a source-free acceptance matrix.
- Do not modify production code or freeze-breaking artifacts. Stop for review and owner signoff before Codex implementation.

**M7 production — Codex (only after R1 approval and owner authorization)**
- Keep core root `build.gradle` / M1–M6 zero-runtime-dependency gate unchanged. Prefer an **isolated Paper adapter build**, or justify the least intrusive alternative. Include/shade the accepted pure-Java engine into the demo JAR without shading Paper. Verify class provenance and no duplicate dependencies.
- `compileOnly` an **exact pinned** `io.papermc.paper:paper-api:26.3.build.<verified-build>`; no floating `+` in final evidence. Standard `plugin.yml` with deliberate `api-version: '26.3'`, commands, and permission defaults. Avoid NMS/CraftBukkit internals and unusual plugin loaders.
- Bridge the exact M6 contracts. `DeliveryRequest` supplies sorted unique nonnegative viewer IDs; use adapter-managed collision-free mapping for the current session (do **not** hash UUIDs to `long` and assume uniqueness). Keep world IDs distinct and verify current world membership.
- On ordinary Paper, collect entity/player/world snapshots, call the planner and emit through **server-main-thread** scheduling only for the initial version. Never access Bukkit world/player API from async threads. M6 is stateless: call it once per clearly defined tick/budget scope and maintain a host-side ledger if multiple planning calls share budgets. Do not accidentally reset quotas by calling the planner independently for each effect.
- Respect exact M6 ordering: deliver only `EffectDelivery` selected indices in ordered batches, and for each recipient send **the first `recipientPoints(r)` selected points**. No unsupported styles and no unselected points. Re-check online/world/visibility/permission before sending, without changing M6 counters retroactively. Distinguish planned from attempted/succeeded/skipped emission. Do not claim packet count equals placement count.
- Initial style capability list is a **closed whitelist**, for instance a no-data exact-position particle and a validated DustOptions-color style after verifying the actual 26.3 API signature. Never silently use a generic fallback for an unsupported style. Validate finite coordinates, world limits and valid particle data **before** any Paper emission.
- For point-perfect ring geometry, prefer a per-player exact-position send with `count=0` and zero offsets; test real behavior, including particle exceptions. Never use unrestricted `World.spawnParticle` or area-wide `receivers()` as a shortcut.
- Make all samples **explicitly opt-in**: permission-restricted player command(s), OP-default debug permission, no scheduled ambient visuals on startup, no auto-enchant integration. Suggested commands: `/vfxdemo ring`, `/vfxdemo stop`, `/vfxdemo status`. Restrict configuration caps and concurrent effects; refuse unsafe/out-of-range requests gracefully.
- State and test lifecycle: enable/disable/restart, zero task leak, cancel/quit/teleport/world change, unexpected world/chunk unload, reload of config, per-player target validation, and graceful failure if a world or player disappears between planning and emission. Debug logging must be bounded and should not expose personal data.
- No Folia support claim until dedicated region/entity scheduling implementation and actual Folia tests. Java/Geyser/Bedrock visual parity remains unverified pending manual captures.

## Acceptance gates

1. Existing 415 Java 25 tests still pass; all seven reference datasets remain byte-identical; zero changes to accepted M1–M6 paths.
2. Adapter compiles against an **exact verified** Paper 26.3 API build; executable JAR with correct plugin metadata, embedded engine classes, no accidental Paper shaded classes; artifact and source checksums.
3. Offline unit tests with a fake particle sink prove mapping, filter behavior, data validation, recipient prefix semantics, emission accounting, thread-boundary handoff, cancellation and strict output limits; report exact count, not a guessed target.
4. **Actual disposable Paper 26.3 smoke**: startup/enable/status, one-user ring, two-user isolation, at least one data-bearing particle, repeated trigger/cancel, quit/teleport/world change, disable/shutdown and no task leak. Capture server build, Java version, logs, test proof and footage/screenshots for visual assertions. CI compilation alone is **not** a real-server PASS.
5. Worst-case bounded invocation and two-player run with separately recorded planning time, main-thread emission time, attempted/successful calls, skipped recipients and visual behavior. No untested MSPT/network/client-FPS guarantees.
6. M7 deliverables: JAR, source ZIP, Git bundle or published branch, exact SHA256 manifest, tests, real-server smoke evidence, a concise install/rollback guide and declared limits. No merge into `main`, no SMP deployment or M8 until independent acceptance and owner authorization.

## Explicitly deferred

Custom enchant integrations, artifact binding, damage/ownership/cooldown rules, automatic live-server deployment, Folia, Bedrock parity, arbitrary particle payload families, advanced data-component API, UI editors and global cross-region fairness. Keep those for the correct later milestone.

## Paper source references (re-check against target build)

- https://docs.papermc.io/paper/dev/project-setup/ — pinned API dependency after build selection
- https://docs.papermc.io/paper/dev/plugin-yml/ — `api-version: '26.3'`, permissions, commands
- https://docs.papermc.io/paper/dev/particles/ — count=0 positional behavior, per-player emission and payload-specific quirks
- https://docs.papermc.io/paper/dev/scheduler/ — ordinary Paper main-thread ownership
- https://docs.papermc.io/paper/dev/folia-support/ — separate Folia handling
- https://github.com/Koi36m/Bedwenton/blob/prep-paper-compatibility/docs/roadmap/PAPER-UPGRADE-ACCEPTANCE-MATRIX.md — broader future smoke checklist

## Immediate next action

Have Claude produce **M7 R1 minimal adapter architecture + normative implementation/test contract** on this exact accepted M6 tree, with a concise open-issues summary. ChatGPT reviews that contract, then the owner authorizes Codex to implement the demo. Use the quickest correct path to the first in-game ring, without weakening preservation, permission, scheduler or real-server test gates.
