Bedwenton M8 R3 — Independent Design Review
Review date: 2026-10-18
Repository: Koi36m/Bedwenton
Submitted design branch: m8-spec-r3
Exact R3 tip: 17ad99186f6b2a73eaccaa22ff5aa0f0e7fd6d82
Parent: 3a731f40dec014cf19430e6090c9e0cb8ef8f529 (R3 intermediate)
R2 ancestor: f04a843e4d5f76b3c47f9e9dfc07e872b826eaa5
R3 Git tree: 8c24886ff2bad1fe3deabaf25a24974761addd25
Verdict: PASS — M8 R3 design accepted for the next gated implementation proposal; design only.
This is not a production-code review, acceptance of the M8 feature in game, main-branch merge, plugin release, or authorization for deployment. No accepted M1–M7 code was changed by this review.
1. Integrity, Git and preservation
- Uploaded bedwenton-m8-r3-design.zip SHA-256 matched the submitter's declared 9d03dbdf041bbb7ca327fb869c64ccbfaa2083d78aa2221635e3e635ecc70899; ZIP CRC passed.
- Internal SHA-256 manifest: 71/71 entries pass.
- Embedded m8-r3.bundle SHA-256 matched 0a872ba0524d9ed76e4c15362e6dd1170ec13f4e05814739dfe5cad31cce0789. Git reports a complete bundle with six branches: R3, R2, R1, M7 source snapshot, M7 R3 design, and accepted M6.
- Independently fetched bundle into a fresh Git repository and checked out exact R3 tip. Verified R2 f04a843, R1 7cf789c, M7 source snapshot ed5fc7b, and accepted M6 c43a66e are ancestors of R3. Note: native M7 R3 design history is separately bundled, not a parent of the GitHub-snapshot branch; this is disclosed, not hidden.
- 61 docs/m8/ archive files equal the files at R3 Git tip byte-for-byte. Every change from ed5fc7b to R3 is an addition under docs/m8/; no accepted M1–M7 source, config, tests, build files, or golden datasets were changed. In particular production RingDriver.java is unchanged.
2. Reconciliation with actual R2 review findings
Read the previously authored M8-R2-INDEPENDENT-DESIGN-REVIEW.md directly, not just the F5–F7 paraphrase. All three requested findings were addressed:
Finding	R3 correction	Judgment
F5 transactional effect replacement	DynamicEffectRegistry.start checks admission, constructs and validates a candidate, ensures task readiness before committing registration; failed first/replacement starts dispose candidate, preserve old effect and task. Matched reference driver ring-start ordering. Fault injection and disposal cases added.	PASS
F6 owner/session identity, freshness	AnchorBinding(owner UUID, session viewer id) fixed at effect creation; rule rejects wrong owner or viewer identity, released viewer IDs, stale/future tick, invalid world mapping, nonfinite, invalid height, unloaded chunk. 20 consecutive transient skipped ticks terminate. Registry and driver independently validate draft identity. Paper adapter responsibilities and no-chunk-load rule made explicit.	PASS at design/reference level
F7 feasible showcase capacity	Two players can run 8 distinct effect slots with 8/3 test overrides; repeated starts are replacements and must succeed. Default 4/2 global-cap denial is tested with a genuinely new fifth effect; optional third owner tests ninth admission refusal.	PASS


R1 F1–F4 corrections and proposed K0–K4 staged implementation remain intact. No reason to restart the milestone design from scratch.
3. Evidence and independent execution
Independent execution in this review environment:
- The available JDK was OpenJDK 21 (no Java 25 installation available here). The original accepted engine sources, original M7 adapter core sources and all 18 proposed M8 SPI source files compiled using javac --release 21 -Xlint:all -Werror with zero warnings.
- Independently compiled the unaltered M8 R3 scratch reference against those classes using javac --release 21 -Xlint:all -Werror. As an additional check, reran the independent harness against this exact unmodified R3 reference: 16/16 PASS.
- Independently compiled and ran M8DesignProbe (P1–P3 numerical assertions); PASS. Its P4 result is a synthetic two-layer trail microbenchmark, not a Paper server TPS/MSPT measurement.
- Executed a separately written, non-JUnit, 16-assertion Java 21 adversarial harness against the compiled throwaway reference; 16/16 PASS, covering: first-start schedule refusal and clean retry, replacement scheduling failure preserving an undisposed and still-emitting old effect, false owner UUID, wrong viewer ID, stale snapshot and 20-tick bounded termination, M8-only scheduling, eight-effect mixed admission, replacement at capacity, ninth owner refusal, and default-cap refusal.
Submitter-supplied reproducibility evidence only (not independently re-run with Java 25/Gradle here):
- Clean-clone reproduce.sh --with-mutants finished with exit 0.
- M1–M6 415/415 and seven byte-identical reference datasets.
- M7 adapter core 61/61 (Paper/Bukkit layer excluded).
- Combined M8 R2/R3 scratch-reference and M7 tests 116/116 (45 R2/R3 design tests, 10 driver-compatibility tests, 61 unchanged M7 adapter tests).
- Mutation run 23/23 caught. Logs and scripts are packaged, and the evidence is internally consistent.
Limits: the pinned paper-api:26.3.build.204-stable wasn't compiled in this R3 design review; full Paper/Bukkit build, plugin JAR validation, live-server multiplayer behavior, and actual TPS/MSPT/FPS performance are NOT RUN. None of the 116/116 design/reference counts should be described as M8 production regression tests.
4. Accepted architectural direction
- Player-only effects for first production pass (non-player attachments remain specified but not implemented).
- One shared M7/M8 scheduler, one globally budgeted M6 planning pass per tick (skip plan for zero requests), no per-effect repeating scheduler.
- Start by implementing the shared driver hook (K0), then Aura (K1), Trail (K2), Activation (K3), Combined (K4), each subject to tests and review; preserve M7 static ring behavior.
- Owner-aware, per-viewer particle emission; no client mod required for vanilla particle types. The new M8 styles are proposed as DUST variations and require pinned Paper API confirmation at K0.
- Current proposed default behavior: aura survives world changes with a fresh anchor; aura/trail stop on death; activation ACTION, aura NORMAL, trail AMBIENT with zero ambient reserve; 2 simultaneous M8 kinds per player; global cap default 4. These are design choices to confirm with the owner when authorizing implementation, not permission for development.
5. Required implementation gates (not R3 design blockers)
1. Explicit owner approval before editing accepted RingDriver.java or any other M7 production source (Q6). Preserve M1–M6 byte-for-byte, and keep M7-only code paths testable.
2. K0 compile on Java 25 against exact pinned Paper 26.3 API; validate Bukkit snapshot signatures, main-thread guarantees, command registration and plugin packaging (verifyPluginJar). The inability to compile Paper in Claude's environment is not Paper compatibility evidence.
3. Port design/reference behavior into production classes and real tests; independently run all accepted 415 engine + 61 adapter tests, new M8 regressions and seven datasets. Never present the scratch reference as deployed code.
4. Enforce one global effect cap regardless of ring/M8 activation order; preservation under failed scheduling, owner/session freshness, no chunk loads, and exactly one plan per active tick; track zero-request tick behavior.
5. Real Paper smoke tests on an isolated server, including 2-player use vs view isolation before inviting outside players; K0–K4 visual checks; clean stop/quit/death/world change, actual MSPT/TPS evidence, and cap/soak scenarios.
6. No merge into main, release, or live SMP deployment without separate authorization and acceptance.
6. Decision
M8 R3 design: PASS. All named R2 HOLD findings (F5–F7) have a consistent design correction, source-level verification, and corresponding executable reference tests. The user can consider authorizing K0 only next; such approval must be explicit. M7 remains accepted as a prototype with its previously documented deferred smoke checks. World Events/Dragon Awakening and global artifact cinematic remain separate, future-scope requirements.