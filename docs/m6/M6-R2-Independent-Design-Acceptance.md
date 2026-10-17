M6 R2 — Independent Design Acceptance Review
Decision: PASS — M6 R2 specification accepted for a subsequent, separately authorized Codex production implementation.
Scope: Specification and test design only. No M6 production Java has been delivered, compiled or accepted. No GitHub Actions M6 implementation run and no Paper server runtime test exists. Owner authorization for production is still required.
1. Identities and independently inspected artifacts
- Project: Koi36m/Bedwenton.
- Accepted M5 GitHub-tested baseline: d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb; source tree 43d1300ab42105c010dd43bfc89ee476491fcdaa.
- M6 kickoff: 0587309877c1e3534e2209e4bd361bd6bca5fa8f.
- M6 R1 commit: e3df49957e900625cf1a05db17d8405d8cd9d39b.
- Accepted M6 R2 proposal commit: a07b1e00a26afb5c4ed31f6d1a8b8f31e6318659 (tree 97e48a6e68d7443863b58044f83f43468ddee513), direct parent 130f5d7303bd5b69a4d73391546c085cd1fc1010, which descends from R1.
- Uploaded R2 ZIP: bedwenton-m6-r2-proposal.zip, SHA-256 e2f1f95b95a176715d9988a895c854c07abd0d36691356e3a9d61a8fa9e880fb.
- Included recoverable bundle: m6-spec-r2.bundle, SHA-256 232f58e3e6b9118028a1dc453c832ac0c3c5a52387ffe5e53818799adb8ea574.
Checks performed: extracted ZIP; all listed SHA256SUMS.txt entries returned OK; verified complete bundle in a clean temporary Git repository (19 branch refs); cloned m6-spec-r2; checked native commit chain and git fsck; compared packaged proposal bytes to restored Git files (no differences). Comparison against M5 baseline shows 38 added files only, in M6 documentation/tests/fixtures and its reference generator: zero edits/deletions to M1–M5 and no M6 production code.
2. Disposition of prior R1 findings
F1 — G10 ambient reserve overclaim: CORRECTED
- Contract section 8 now claims capacity protection, not a realized minimum allocation. Non-AMBIENT generation reservations cannot exceed G - R_g, and their global placements cannot exceed P - R_p.
- Due to indivisible preflight scene reservations and multi-viewer point costs, AMBIENT may receive fewer points than its reserved capacity; the contract explicitly states this.
- The R2 regression tests cover two 20-point AMBIENT scenes after a 70-point ACTION with a 30-point reserve (20 points realized), a single 31-point scene with that same 30-point reserve (0 realized), and three/six-viewer placement cases with five reserved placements (3/0 realized).
- These numbers differ from the earlier independent review's illustrative 40-point generation and 1-point placement reserves, but the same general mechanisms and false lower-bound guarantee are exercised. The G10 invariant checker now verifies the remaining reserve when AMBIENT allocation begins; no minimum is claimed.
- Independent mathematical check: In allocation order ACTION → NORMAL → AMBIENT, Phase 1 forbids every non-ambient reservation from consuming R_g, and Phase 2 similarly protects R_p. Their sums are therefore at most G-R_g and P-R_p; with AMBIENT last, the capacity remains at the first AMBIENT request. A scene whose reservation exceeds it, or a logical point whose recipient cost exceeds it, need not be admitted. Thus R2's G10 statement is supported by the algorithm.
F2 — quality tier could silently upgrade: CORRECTED
- Phase 1 candidates are now only existing recipe tiers <= requestedTier, searched from highest downward; no candidates yields Outcome.SKIPPED_NO_ELIGIBLE_TIER with null tier/frame and zero reservation.
- SKIPPED_NO_VIEWERS retains precedence; no-tier is checked before style availability or quota checks. This is explicit, deterministic behavior.
- The proposed API changes by just the new Outcome enum constant, to nine possible outcomes. New/revised AdmissionTest assertions and the independently generated stress-no-eligible-tier scenario check this and the absence of unnecessary frame evaluation.
- Independent reasoning: Building the candidate list by intersecting available tiers with tiers at most the user's request cannot return a higher tier; the empty-list case is handled as a non-evaluating skip. This resolves R1's contradiction without changing M5.
3. Independently verified reference/evidence coverage
- Executed python3 tools/generate_m6_reference_plans.py into a fresh directory from the R2 Git tree. diff -qr against all checked-in M6 reference files produced no differences; checked-in fixture MANIFEST.sha256 passed.
- Counted 45 distinct cases / 158 per-request expectation records across six families: Bloom 6/57, Cloudstep 3/23, healer 8/8, trident 6/6, Astral Prism 9/9, stress 13/55.
- Verified only the M6 stress fixture and its manifest changed from R1 and the five new R2 scenarios are appended without changing prior R1 scenario bytes.
- R1→R2 Git diff: 20 M6 paths changed (documents, tests, generator, fixture, validation evidence); no accepted baseline changes.
- Independently enumerated test annotations: 46 ordinary @Test methods plus a six-value @ParameterizedTest yields 52 M6 test invocations across nine test classes, consistent with the reported total 415 = 363 accepted + 52 proposed M6 tests.
Execution caveat: Claude reports successful Java 25.0.4.1/Gradle 9.1.0 clean test check, 415/415, zero failures; also 8/8 targeted R2 mutation kills. These are Claude's local execution records, not a fresh independent Java 25 run; the temporary M6 reference implementation is explicitly excluded from the ZIP and the available review runtime uses Java 21. Java 25 production verification must run again after Codex implements M6. R1's 58-mutant results remain historical (56 killed, two argued equivalent).
4. Remaining choices / non-blocking risks
- The planner protects capacity, not minimum AMBIENT service. NORMAL requests have no reserved share; strict ACTION demand can starve them. This is an accepted, documented policy tradeoff, not a hidden fairness guarantee.
- Phase 1's conservative maxPointsPerFrame may reject effects whose currently visible frame is much smaller. This is deliberately safe but may need later performance tuning.
- A scene with positive generation allowance may still be evaluated when global, world or per-viewer placement quotas are zero. This can waste CPU and temporary memory; add a proven early-skip optimization in a separately reviewed scope if needed, not an unreviewed R2 formula change.
- Requested tier is a hard maximum; recipes that omit SIMPLE correctly skip SIMPLE requests. Authors should include low-density variants.
- Quality presets are not empirically calibrated to a Minecraft server. The planner's logical placement counts are not packets, actual emission calls, client particles or MSPT measurements.
- No M6 GitHub Actions run or Paper/Bukkit/Folia runtime test occurred. No main merge or server deployment is authorized.
5. Final acceptance boundary and next gate
M6 R2 specification is accepted. Preserve the exact R2 commit, all 52 proposed tests and their fixture generator, and the frozen M1–M5 baseline. The implementation engineer should recover m6-spec-r2 from the verified bundle and implement the documented ten public types, with no new dependency on Minecraft/Paper, no gameplay changes and no edits to accepted tests or fixtures. R2's docs/m6/M6-IMPLEMENTATION-CONTRACT.md and docs/m6/M6-R2-CHANGES.md are normative for these corrections.
Codex production implementation is NOT yet authorized by this design acceptance alone. Obtain explicit owner approval. Once authorized, require fresh Java 25 clean test and check (expected 415 tests), independent regeneration of all accepted references, hash manifests, runtime dependency checks, compilation warnings, source-tree/bundle provenance, and a GitHub Actions run before independent production review.