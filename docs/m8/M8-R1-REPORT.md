# M8 R1 report: provenance, evidence, decisions

Status: **R1 design package (historical; corrected in part by R2 — see `M8-R2-CHANGES.md`). Not accepted, not implemented.**
Recommendation: **READY FOR INDEPENDENT DESIGN REVIEW** (blockers for later gates listed in section 4).

## 1. Provenance

| Item | Value |
|---|---|
| Branch | `m8-spec` |
| Base | accepted M7 production snapshot `ed5fc7bff548edf4b005e919495b22213a65b64e` (tree `afa33672b5850bdda3be636415659fa27ac53047`), which descends from accepted M6 `c43a66e` |
| R1 commit | the commit adding this file; its full SHA is recorded in the package `PROVENANCE.txt` (a commit cannot contain its own hash) |
| Scope | adds files under `docs/m8/` only; no production source, test, fixture, build file or workflow changed |
| M7 R3 design (accepted) | `4eeb8354693463a95f7dd48cbf3891701dcc6acf` — note: `ed5fc7b` is a GitHub snapshot chain (`ed5fc7b → 77bb965 → c43a66e`) that does not contain `4eeb835` as an ancestor; M7's implementation report states the native implementation descends from `4eeb835` and the snapshot's tree was verified equal. This design inspects the snapshot's actual sources. |

## 2. Deliverables

| # | File |
|---|---|
| 1 | `M8-ARCHITECTURE-AND-CONTRACT.md` (incl. reusable M1–M7 capabilities, alternatives, open questions, implementation sequence) |
| 2 | `M8-ANCHOR-AND-MOTION-SPEC.md` |
| 3 | `M8-SHOWCASE-DESIGNS.md` |
| 4 | `M8-PERFORMANCE-AND-BUDGETS.md` (incl. risks) |
| 5 | `M8-TEST-AND-SMOKE-PLAN.md` |
| 6 | `spi/workshop/vfx/paper/m8/` — 12 SPI proposal files |
| 7 | `validation/` — design probe source and output, Paper API excerpt, regression evidence, `reproduce.sh`, M7 core re-run build |
| 8 | this report and `README.md` |

## 3. Evidence actually produced in R1 (Java 25.0.4.1, Gradle 9.1.0, one cloud container)

| Check | Result | Log |
|---|---|---|
| SPI proposal compiles (`-Xlint:all -Werror`) against the accepted engine and the **unchanged** M7 core classes | 12 classes, 0 warnings | `validation/reproduce.sh` |
| P1 yaw → quaternion vs Paper's documented yaw convention | max error 1.6e-16 | `validation/m8-design-probe.txt` |
| P2 aura with moving, turning anchor vs independent formula | 126 points, max 3.6e-15 blocks | same |
| P3 activation phases (M2 + M5) | exact boundaries 20/50/60/80; reservation 104 HEAVY | same |
| P4 per-tick trail rebuild + plan | ~2–5 µs per trail here (indicative only) | same |
| M1–M6 suite on `m8-spec` | 415/415 | `validation/regression-and-preservation.txt` |
| Seven datasets | PASS (byte-identical) | same |
| M7 adapter tests, re-run **without** the Paper layer (scratch build) | 61/61 | same |
| Paper API names used by M8 | all found in PaperMC source `09ee222` (near the pin) | `validation/paper-api-m8-signatures.txt` |

## 4. Not verified / blocked

| Item | Status |
|---|---|
| Compilation against the pinned `paper-api 26.3.build.204-stable` jar | **blocked here** (`repo.papermc.io` refused by this environment's network policy); M8 gate C0 |
| M7 adapter build with Paper layer, `verifyPluginJar`, `recordPaperApi` | not re-run here (same block); unchanged since the accepted GitHub run |
| Any real-server behaviour (attachment smoothness, particle smear, teleports, two-player isolation, MSPT/TPS/FPS) | not run; all smoke rows NOT RUN |
| GitHub publication | push expected to be refused (HTTP 403), as for M5–M7; delivered as Git bundle |

## 5. Unresolved decisions for the owner / reviewer

Q1 non-player entity attachment (recommend: specified, not implemented in M8); Q2 aura across world change (recommend:
continue); Q3 aura/trail on death (recommend: stop); Q4 priorities and ambient reserve (recommend: as specified, reserve
0); Q5 per-player M8 concurrency 2; **Q6 approval to modify M7's `RingDriver` additively** (required for one global plan
per tick with M8 effects).
