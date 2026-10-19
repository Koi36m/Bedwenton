# M8 R3: focused design corrections (change log)

Status: **design only, revision 3, for ChatGPT's independent design review.** Not accepted, not implemented, not
deployed. `RingDriver.java` and every other accepted M1–M7 file are unchanged. The R2 corrections F1–F4 were
substantially accepted at design level and are preserved unchanged in substance.

| Item | Value |
|---|---|
| R3 branch | `m8-spec-r3`, created from R2 `f04a843e4d5f76b3c47f9e9dfc07e872b826eaa5` |
| R3 commit | the final commit on `m8-spec-r3` (full SHA in the package `PROVENANCE.txt`) |
| Base chain | `f04a843` (R2) ← `7cf789c` (R1) ← `ed5fc7b` (accepted M7 production snapshot) ← `77bb965` ← `c43a66e` (accepted M6) |
| Scope | changes and additions under `docs/m8/` only |

**Input not available.** The assignment names `M8-R2-INDEPENDENT-DESIGN-REVIEW.md` as the authoritative findings, but
that file was not attached to this session and is not on any branch. The corrections below implement F5–F7 exactly as
the R3 assignment states them. **Reconcile them with the review text before acceptance.**

## F5 — Transactional effect replacement

**Defect (R2).** `DynamicEffectRegistry.start` put the candidate into the map and disposed the old effect, and only then
called `scheduler.ensureRunning()`. If that call threw, the catch removed and disposed the candidate as well, so the
player lost a valid effect.

**Correction** (architecture 4.5; reference `DynamicEffectRegistry.start`). Every step that can fail runs before one
commit point:
1. admission, with no mutation;
2. request id allocation (never reused);
3. `EffectFactory.create(id, binding)`, and a check that the candidate reports this id, the bound owner and the requested
   kind;
4. `ensureRunning()`;
5. **commit** (register), then dispose the replaced effect.

A failure in steps 3–4 disposes only the candidate and rethrows. The registry, the old effect, the counts and the task
stay as they were. A throwing `dispose` after the commit is contained and counted. The driver owns the task: on an
`ensureRunning` failure it keeps `running = false` and advances its generation, so no task or stale callback is left
behind. The reference driver's ring start now follows the same order (`ensureRunning` before `rings.put`). Accepted
M7 cannot hit this failure, because an existing ring implies a running task.

**Executable:** `TransactionalStartTest` (8 tests) covers:
- injected failure at the first registration (real scheduler refusal);
- injected `ensureRunning` failure during a replacement: the old effect is still registered, undisposed and planned;
  only the candidate is disposed; count and task unchanged;
- factory failure, for a first registration and for a replacement;
- mismatched or null candidates;
- a successful replacement;
- a throwing old `dispose`;
- refused admission with no side effects;
- a first-ring scheduling failure.

## F6 — Fail-closed anchor identity and freshness

**Defect (R2).**
- Validation checked liveness and finiteness only.
- Drafts copied `ownerViewerId` from the snapshot. A malformed snapshot carrying another player's viewer id would make
  that player the "owner viewer". Under M7's owner-aware rule, a player with `use` but without `view` would then see
  someone else's effect.
- Staleness, world identity, world height and loaded-chunk checks were text only.

**Correction** (anchor spec 3.2–3.4; SPI `AnchorBinding`, `AnchorVerdict`, `AnchorRejection`, `EffectFactory`;
`AnchorSnapshot` gains `minHeight`, `maxHeight`, `chunkLoaded`):
- **Binding.** Each effect is bound at start to `(owner UUID, SessionIds viewer id)`. Drafts always carry the binding.
- **Ordered validation, first failure wins.**
  - `TERMINATE`:
    - absent snapshot;
    - owner mismatch;
    - viewer-id mismatch, including a released or reassigned id;
    - offline, invalid or dead.
  - `SKIP_TICK`, bounded by the 20-tick rule:
    - stale or future tick;
    - world UUID and world id disagreeing, or an unknown world id;
    - non-finite position or yaw;
    - y outside `[minHeight, maxHeight)`;
    - chunk not loaded.
- **Defence in depth.**
  - The registry drops a draft whose id, kind, owner or viewer id differs from the binding, and stops the effect.
  - The driver refuses drafts with a request id in the ring range, an owner not mapped from the draft's viewer id, or an
    unknown world id.
- **Responsibilities** (anchor spec 3.4).
  - The Paper adapter only resolves `Server#getPlayer(bound UUID)` and copies what Paper returns: current tick,
    `SessionIds` ids, position and yaw, `getMinHeight`/`getMaxHeight`, and `isChunkLoaded` (no chunk loading).
  - The pure core decides every rule.

**Executable:** `AnchorIdentityTest` (10 tests). In every test, B holds `use` but not `view` and stands next to A in the
same world. The tests cover:
- the full reason table (20 cases, including the half-open height edge);
- an owner swap and a viewer-id swap: A's aura is terminated and B receives 0 particles;
- stale, future and far-stale snapshots: skipped, recovered, and terminated after 20 in a row;
- world, height and chunk failures: skipped, with no request reaching the planner;
- a session id released on quit, and a replayed released id;
- drafts built from the binding even under a deliberately defective rule;
- forged drafts dropped by the registry;
- forged drafts dropped by the driver.

## F7 — Combined showcase test feasibility

**Defect (R2).** S-X1 asserted that "a ninth [effect is] refused". Two players with one effect per kind already hold all
8 (player, kind) slots, so every further start by A or B is a replacement. The step could not be reached.

**Correction** (test plan section 3; showcase F4 paragraph):
- **S-X1** keeps the two-player, eight-effect visual demonstration with the test overrides (`maxActiveEffects: 8`,
  `m8.maxEffectsPerPlayer: 3`). It now asserts that **replacement at capacity succeeds** (re-run aura and ring: still 8,
  no refusal).
- **S-X2** carries the **required** global-cap refusal, at the production defaults with the same two players:
  - Order 1: B's trail is refused with `Active effect cap reached` while B holds only one M8 effect.
  - Order 2: A's ring is refused with `Active ring cap reached`, and A's activation is refused by the per-player cap.
  - Replacements succeed.
- **S-X1b** is optional and needs a third test account C. C's first aura or ring is the ninth unique effect and is
  refused. After capacity frees, C's aura is admitted.

**Executable:** `ShowcaseCapacityTest` (4 tests) replays S-X1, S-X1b and both S-X2 orders, with exact messages.

## 4. SPI and reference delta

| Area | Change |
|---|---|
| SPI (18 files, 19 classes) | new `AnchorBinding`, `AnchorVerdict`, `AnchorRejection`, `EffectFactory`; `AnchorSnapshot` gains `minHeight`, `maxHeight`, `chunkLoaded`; `AnchorRules.validate(snapshot, binding, tick)` returns `AnchorVerdict`; documentation updates in `DynamicEffect`, `EffectDraft`, `AnchorState` |
| Reference (throwaway) | `DynamicEffectRegistry` (transactional start, binding, draft identity check, counters); `DefaultAnchorRules(SessionIds)`; `AbstractEffect` (binding, per-reason counters); Aura/Trail drafts use the binding; `RingDriverWithHook` (ring start order, `acceptDraft`, `rejectedDrafts`) |
| Proposed `RingDriver` delta | `validation/ringdriver-proposed.diff`, now 77 added and 13 removed lines (R2: 73 changed lines) |

## 5. Results (Java 25.0.4.1, Gradle 9.1.0)

| Check | Result |
|---|---|
| Design tests: R2 23 (`SharedCapacity` 6, `M8OnlyScheduling` 6, `TrailTiming` 6, `OrientationAndAnimation` 5) + R3 22 (`TransactionalStart` 8, `AnchorIdentity` 10, `ShowcaseCapacity` 4) + 10 M7-compatibility | **55/55** |
| Unchanged M7 adapter tests in the same build (Paper layer excluded) | **61/61** (total **116/116**; `verification/logs/r3-tests.log`) |
| Targeted mutants: R2's 10 + R3's 13 (F5a–c, F6a–i, F7a) | **23 of 23 caught** (`verification/logs/r3-mutants.txt`). In the first run F6f and F6g survived (no test isolated the released-id clause, or binding versus snapshot viewer id); the two tests added for them catch both |
| Probe | unchanged from R2; PASS (clean-clone log) |
| SPI compile `-Werror` against the engine and the unchanged M7 core | 18 files, 19 classes, 0 warnings |
| M1–M6 / seven datasets / M7 core re-run | see the clean-clone log in the package (`reproduce.sh --with-mutants`) |

**Not verified:**
- compilation against `paper-api 26.3.build.204-stable`, because `repo.papermc.io` is blocked here;
- the Paper snapshot adapter;
- any real-server behaviour; all smoke rows, including S-X1, S-X1b and S-X2, are NOT RUN;
- GitHub publication (expected HTTP 403).
