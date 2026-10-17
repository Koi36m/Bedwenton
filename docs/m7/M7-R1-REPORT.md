# M7 R1 report: provenance, verified API facts, what ran, open issues

Status: **design proposal (R1; corrected in part by R2, see `M7-R2-CHANGES.md`).** There is no production adapter, nothing merged into
`main`, no gameplay change and no runtime compatibility claim.

## 1. Provenance

| Item | Value |
|---|---|
| Accepted M6 baseline | `c43a66e656a77aa82bd72d8ffe27f1a037240e99`, tree `ef496f7b0c34796cdd30fe35d79cfd4e0c071ece`, CI run https://github.com/Koi36m/Bedwenton/actions/runs/39015236273 |
| Kickoff | `74fefd3dae0ba9d8002907513c838fbf417ffa2b` on `m7-paper-adapter-kickoff` |
| R1 branch | `m7-spec`, from the kickoff; adds files under `docs/m7/` only |
| Roadmap read | `prep-paper-compatibility`: `docs/roadmap/PAPER-COMPATIBILITY-AND-VFX-READINESS.md`, `PAPER-UPGRADE-ACCEPTANCE-MATRIX.md` |
| Paper source read | PaperMC/Paper `09ee222482646f8ed84413aea89d0108653af938` (main, 2026-10-16), `gradle.properties`: `mcVersion=26.3`, `apiVersion=26.3`, `channel=STABLE` |

**R1 contents:**
- `M7-ARCHITECTURE-AND-CONTRACT.md`: normative;
- `M7-TEST-AND-SMOKE-PLAN.md`: offline tests, the disposable-server procedure and the acceptance matrix;
- this report;
- `spi/`: a compile-tested SPI proposal, 9 files;
- `validation/`: API signatures, build spike, demo-recipe probe and the preservation check.

## 2. Verified Paper API facts (from source; `validation/paper-api-signatures.txt` has file:line)

| Fact | Consequence in the contract |
|---|---|
| `Player#spawnParticle(Particle, double x, double y, double z, int count, double offX, double offY, double offZ, double speed, @Nullable T data, boolean force)` exists, delegating to a new 14-argument overload with `Particle.RandomizationType.DEFAULT` | Per-player exact-position send with `count = 0` and zero offsets (section 7) |
| `Particle.DUST` takes `DustOptions(Color, float size)`, with the size checked in [0.01, 4.0]; `Particle.END_ROD` has data type `Void`; `Particle#getDataType()` exists | Whitelist with a data-type check at enable (section 5) |
| `CommandExecutor` and `JavaPlugin#getCommand` are `@ApiStatus.Obsolete(since = "26.3")`; `JavaPlugin#registerCommand(label, description, aliases, BasicCommand)` registers through `LifecycleEvents.COMMANDS`; `BasicCommand` has `permission()`, `canUse`, `execute` and `suggest` | The command is registered with `BasicCommand`, not in `plugin.yml` (open issue O2) |
| `BukkitScheduler#runTaskTimer(Plugin, Runnable, long, long)` returns `BukkitTask`; `cancelTasks`, `getPendingTasks`, `BukkitTask#cancel` and `isCancelled` exist | One main-thread timer, created only while rings exist; a leak check at disable |
| `Bukkit.isPrimaryThread()`, `Bukkit.getCurrentTick()`, `World#isChunkLoaded(int, int)`, `WorldBorder#isInside(Location)`, `WorldInfo#getMinHeight()` / `getMaxHeight()` / `getUID()` | Thread assertion, tick ordinal, and anchor and point validation |
| `PlayerQuitEvent`, `PlayerChangedWorldEvent`, `PlayerTeleportEvent`, `PlayerDeathEvent`, `WorldUnloadEvent` exist | Lifecycle table (section 8) |

**Not verified:**
- The exact published `paper-api` Maven version string and its build number. `repo.papermc.io`, `docs.papermc.io`,
  `fill.papermc.io` and `api.papermc.io` are blocked by this environment's network policy (the proxy refused the
  connection). The Paper source was reached through git.
- That the published jar matches this source commit.
- Any runtime behaviour.

## 3. What ran and what was assumed

**Ran** (Java 25.0.4.1, Gradle 9.1.0, Python 3.13 in one cloud container):
- Root suite on the R1 branch: **415/415**; `check` succeeds.
- `verification/m6/regenerate_all.py`: **PASS** for all seven datasets.
- Diff against `c43a66e`: additions under `docs/m7/` only.
- **Isolated-build spike** (scratch): a standalone `paper-adapter/` build with `includeBuild('..')` produced a jar with
  the 63 engine classes and no Paper, Bukkit or JUnit entries. The root was untouched.
- **SPI proposal:** compiles with `-Xlint:all`, with 0 warnings, against the accepted engine.
- **Demo recipe probe:**
  - the section 5 recipe builds with the accepted M5 API;
  - it plans DELIVERED at SIMPLE, BALANCED and HEAVY (24, 48 or 72 points; 2 recipients);
  - the first ring point is exactly anchor + (2, 0, 0);
  - at the hard ceilings (16 rings x 16 viewers, HEAVY), placements are exactly 4096. The outcomes are 3 DELIVERED,
    1 THINNED and 12 SKIPPED_DELIVERY_BUDGET, which shows the ceilings bind (`validation/demo-recipe-probe.txt`).

**Not run or assumed:**
- Compilation against `paper-api`, because no jar was obtainable.
- Any Paper server.
- Count-0 exact positioning; the visual result of `END_ROD` and `DUST`.
- Two-client isolation.
- Lifecycle behaviour.
- MSPT, network or client effects.
- GitHub Actions for M7: the push is expected to be refused (HTTP 403), as for M5 and M6.

## 4. Open issues for review

- **O1. Exact Paper pin.** The implementer must resolve `paperApiVersion` from PaperMC's `maven-metadata.xml`, and
  record it with the jar's SHA-256, before any evidence counts. If the published API differs from the signatures in
  section 2, the contract must be amended first.
- **O2. Commands.**
  - **Option A (proposed):** `JavaPlugin#registerCommand` with `BasicCommand`, which avoids APIs that are obsolete
    since 26.3.
  - **Option B:** the kickoff's `plugin.yml` command with `CommandExecutor`, which still works but is marked
    obsolete.
- **O3. Viewers.** By default only the owner sees a ring; other players need `bedwenton.vfxdemo.view` and must be
  within `viewRadius`. This gives a two-user isolation test that M6 recipients can exercise. Is the default right for
  a demo?
- **O4. Planning on the main thread.** It is cheap for the bounded demo (one M6 call per tick, at most 1152 generated
  points). An async split is deferred, because M7 must not touch Bukkit off-thread, and snapshot hand-off adds risk
  without need.
- **O5. Ceilings.**
  - The hard ceilings (16 rings, 16 viewers, 4096 calls per tick) are engineering bounds, not recommendations.
  - At the ceilings, strict ACTION priority means most rings are skipped. That is correct M6 behaviour, but it may
    look odd in a demo.
- **O6. Moving anchor.** Deferred until the static ring passes S1 to S15.
- **O7. Visual fidelity.** Count-0 positioning and the dust colour are asserted only by S5 and S15 with screenshots.
  A failure there would change the contract, for example by moving to `RandomizationType` or `ParticleBuilder`.
