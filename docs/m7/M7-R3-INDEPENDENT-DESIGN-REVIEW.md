Bedwenton M7 R3 — Independent Design Acceptance Review
Date: 2026-10-17
Repository: Koi36m/Bedwenton
Milestone: M7 — Paper Rendering Adapter
Verdict: PASS — design and contract acceptance only. No production implementation or merge authorized.
Scope and authoritative commits
Ref	Commit
Accepted frozen M6	c43a66e656a77aa82bd72d8ffe27f1a037240e99
M7 kickoff	74fefd3dae0ba9d8002907513c838fbf417ffa2b
M7 R1	f72179d917623b5286bc6b52f6e8e982d3ad0d2d
M7 R2	15aa210ddcab79de6180df9bfa45a3ba17e1bcf7
M7 R3 content	c57c1b0612663e2296aa5cf5828708f64467f279
M7 R3 evidence tip	4eeb8354693463a95f7dd48cbf3891701dcc6acf


Independently reproduced results
- Extracted the supplied bedwenton-m7-r3-docs.zip with safe member-path inspection; all 67/67 SHA-256 manifest entries verified.
- Successfully ran git bundle verify, cloned the included bundle, inspected the refs and ancestry, and ran git fsck --full --no-reflogs without errors.
- R3 tip and chain to accepted M6 match claimed full SHAs. M6's tree is ef496f7b0c34796cdd30fe35d79cfd4e0c071ece.
- git diff --name-status accepted M6→R3 tip: 58 additions under docs/m7/, 0 other paths, 0 modifications/deletions. R2→R3: 43 paths. This independently confirms that the accepted M1–M6 foundation is unchanged.
- Compared every extracted docs/m7/ file with its version in the cloned R3 commit: 58/58 files match. The saved R2→R3 tip diff matches the independently regenerated Git diff byte-for-byte (SHA-256 1435818f29cb75fe4609703c818f01d77047347cbdf629e0309fc5289de76dac).
- Independently compiled the actual accepted src/main/java engine and all R3 SPI/reference classes with javac 21, -Xlint:all -Werror; no compiler warnings or errors.
- Independently compiled and executed all 25 actual proposed test methods against the engine and R3 scratch reference using an isolated, minimal, JUnit-compatible assertion/annotation harness on Java 21: 25 passed, 0 failed. No repository file was changed.
- Independently applied all 13 targeted mutant source edits, one at a time, compiled the mutants, and reran the same test methods: 13/13 mutant variants produced test failures. These are independent behavioral checks rather than acceptance of Claude's logged totals.
- Exercised a separate numeric edge-case probe (below).
Contract assessment
R3 correction A — invalid coordinates: PASS. The proposed rule explicitly rejects non-finite request anchor and viewer positions. The result classification is deterministic and normal online→world→permission→range order is preserved. The config contract restricts viewRadius to finite values in [4,64]; invalid config must refuse enable.
R3 correction B — request context mismatch: PASS. The proposed emitter rejects missing, mismatched, or invalid contexts before recipient checks, without guessing ownership. Test cases cover swapped owners, one invalid delivery alongside a valid delivery, invalid anchors, negative IDs, and placement accounting.
Prior R2 fixes F1–F3: PASS in design verification. Owner versus nonowner access, allocation-rank emission ordering, and cross-tick per-style failure streaks are covered by original tests and targeted mutants.
Placement accounting: PASS for provided valid planner fixtures, including whole-delivery and whole-recipient skips. Every planned placement is accounted for by the stated equations in these tests.
Limitations — distinct from the design PASS
1. Exact Java 25/Gradle/JUnit re-execution not performed in this review environment. Only JDK 21 is installed, and network access to Gradle/JUnit distribution endpoints is blocked. Claude's package includes logs asserting clean-clone 25/25, 13/13, M6 415/415, and seven datasets byte-identical on Java 25, but these exact toolchain executions were not independently reproduced here. Our independent Java 21 harness executes the same test method bodies but is not a certification of the JUnit 5.12.2 runner or Gradle 9.1.0 setup.
2. No Paper 26.3 API compile or in-game/server smoke test. Both must occur during the authorized production phase. No exact Paper build is pinned yet.
3. No GitHub push or remote CI verification. The provided Git bundle preserves full verifiable history; the package reports HTTP 403 on attempted push. We did not change any remote branch.
4. No production M7 source has been created. Acceptance of design does not authorize Codex, merges, or deployment.
Implementation review notes (nonblocking for the M7 design)
- Radius upper-bound must be enforced before instantiating the rule. The scratch constructor accepts any finite nonnegative radius; at an extreme unconfigured value such as 1e200, squaring the radius overflows to infinity and can admit a farther finite-position viewer. The normative Paper configuration contract forbids this by requiring viewRadius in [4,64]. Do not bypass that config guard in production. An optional defensive improvement is to make the constructor enforce the same maximum or compute range without overflow. This probe does not affect the defined [4,64] production configuration.
- Test script exit handling: run-tests.sh prints the captured Gradle exit status, but its final exit code is governed by the XML summarizer. On a failed Gradle invocation before test results are replaced, stale successful XML could yield a misleading zero exit. Clean-clone R3 evidence isn't implicated. An authorized future build/test script should explicitly return nonzero if Gradle fails.
- Viewer identity inputs: the intended owner-first selection assumes distinct sorted eligible nonowner IDs from player enumeration. Implementers must enforce uniqueness before passing data to M6, not rely on sorting alone to remove duplicates.
These notes do not require another Claude design iteration before owner review. They should be retained in Codex's implementation instructions and future acceptance criteria.
Final decision and next checkpoint
PASS — M7 R3 design accepted for the planning/contract gate, with the limitations above recorded.
- M1–M6: Accepted and frozen, still unchanged.
- M7 design baseline: 4eeb8354693463a95f7dd48cbf3891701dcc6acf (R3 tip), held locally via the bundled Git history.
- M7 production status: Not started; no authorizations implied by this review.
- Next action: The project owner may explicitly authorize Codex to implement M7 from the accepted design. If authorized, require paper-api compilation, clean Java 25 tests, defensive config enforcement, real-server smoke, and a separate implementation acceptance review before merge/release.