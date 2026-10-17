Bedwenton — M6 Production Implementation Authorization
Authorization: OWNER APPROVED Codex production implementation of M6, following ChatGPT's independent acceptance of M6 R2. This is permission to implement and verify M6 only. No main merge, release to SMP, Paper integration, or M7 work is authorized.
Attachments
1. bedwenton-m6-r2-proposal.zip — SHA-256 e2f1f95b95a176715d9988a895c854c07abd0d36691356e3a9d61a8fa9e880fb.
2. M6-R2-Independent-Design-Acceptance.md — the acceptance review (attach separately).
The ZIP includes m6-spec-r2.bundle — SHA-256 232f58e3e6b9118028a1dc453c832ac0c3c5a52387ffe5e53818799adb8ea574.
Repository identities
- Repo: Koi36m/Bedwenton.
- Accepted GitHub M5 baseline commit: d4970cc23938f95d12c5b5e3c3d3b4d687f3e4eb.
- Accepted GitHub M5 tree: 43d1300ab42105c010dd43bfc89ee476491fcdaa.
- M6 design kickoff: 0587309877c1e3534e2209e4bd361bd6bca5fa8f.
- M6 R1 proposal: e3df49957e900625cf1a05db17d8405d8cd9d39b.
- Accepted M6 R2 spec: a07b1e00a26afb5c4ed31f6d1a8b8f31e6318659, tree 97e48a6e68d7443863b58044f83f43468ddee513.
- Create separate implementation branch from exact R2 spec commit (e.g. m6-implementation). Native commits in the local bundle must not be misrepresented as GitHub-published commits if the connector publishes a source-equivalent snapshot instead.
Recover before coding
1. SHA-256-check proposal ZIP and embedded Git bundle. Verify all listed package checksums, git bundle verify, recovered Git ancestry, and git fsck.
2. Recover the exact R2 spec commit and read docs/m6/M6-IMPLEMENTATION-CONTRACT.md, M6-R2-CHANGES.md, M6-TEST-SPECIFICATION.md, M6-DESIGN-DECISIONS.md, M6-RISKS.md, and README-FOR-CODEX-M6.md.
3. Confirm the R2 design adds files only in M6 paths and modifies none of M1–M5. Preserve all approved M6 docs, 52 test invocations, 45 fixture cases/158 per-request expectations, and oracle generator without editing them to force a pass.
4. Treat the exact contract as authoritative. If it contradicts the tests or the acceptance review, stop and report instead of guessing.
Implement the exact M6 API
Package: workshop.vfx.delivery.
Exactly ten public types:
- QualityTier
- EffectPriority
- LayerRole
- Outcome
- EffectRecipe
- DeliveryRequest
- DeliveryPolicy
- DeliveryPlanner
- DeliveryPlan
- EffectDelivery
Package-private validation/helpers are allowed as needed, but no extra public types or API members beyond the approved contract. Keep every type immutable and the planner stateless.
Signature of main entry point:
DeliveryPlanner.plan(long tickOrdinal, List<DeliveryRequest> requests, Set<StyleKey> supportedStyles, DeliveryPolicy policy).
Implement the normative admission ordering, reserve-capacity protection, tier ceilings, bounds, allocation ranks, stage/unsupported handling, deterministic thinning/rotation, recipient allocation, outcome and accounting rules. Pay special attention to the accepted R2 fixes:
- G10: Protected AMBIENT capacity cannot be consumed by non-ambient requests; do not claim a realized minimum allocation when indivisible reservations or multi-viewer costs prevent using it.
- G11: A request may use only existing tiers at or below its requested tier; no silent SIMPLE-to-BALANCED upgrade. No eligible variant produces SKIPPED_NO_ELIGIBLE_TIER, without evaluating or reserving.
Input/size errors must follow the approved exception and allocation-order policy. M5 evaluation failure must be confined to the request where specified. Do not weaken accepted tests.
Prohibited changes
- No modifications to accepted M1–M5 code, tests, reference files, numerical policies, or generators.
- No modifications to the accepted M6 R2 contract, its 52 test invocations, oracle, fixture files, or thresholds merely to pass tests.
- No Paper/Bukkit/Folia/NMS/Minecraft code or dependencies. No actual particle emission, plugin runtime, scheduler, gameplay logic, cooldown/owner binding, or Minecraft deployment.
- No hidden clocks/randomness/unsafe mutable global state or unbounded per-frame memory.
- No merger into main, and no M7 implementation.
Verification requirements
Java 25, Gradle 9.1.0. Run ./gradlew --no-daemon clean test and ./gradlew --no-daemon check from a fresh checkout/export, including the no-runtime-dependency guard. Expected 415/415 JUnit invocations (363 frozen + 52 M6), zero failures/errors/skips, production compiler warnings zero. Report exact JUnit report count rather than inventing one.
Regenerate every accepted M1–M6 reference/golden dataset with original generators where applicable and verify byte-for-byte against checked-in files. Specifically M3 2,759, M4 3,064, M5 1,113 rows; M6 45 cases / 158 expectation records. Preserve all manifests/checksums, verify API boundary and no platform imports/dependencies. Add tests only if they do not weaken frozen ones.
Independently exercise particularly dangerous boundaries: empty recipients, zero quotas, huge long values, exact generation/placement limits, many worlds/viewers/requests, ACTION/NORMAL/AMBIENT priority and reserve protection, all 9 Outcome values, no-eligible-tier requests, strict tier ceilings, deterministic selection across repeated runs and threads, input validation before proportional allocation, and performance under stress. Do not claim real packet, MSPT or client performance results.
Obtain a successful GitHub Actions run on an isolated verification branch for the exact implemented source tree, including all reference regeneration, and preserve downloadable JUnit/log artifacts. Preserve existing workflows. If GitHub git push returns HTTP 403, stop retrying: use an authorized GitHub connector/source-snapshot publishing path if available. Record the complete source-tree SHA and prove source equivalence to the local implementation. Do not equate source-snapshot commit ID with original local native Git commit ID.
Deliverables and stop
Return: local native implementation SHA and tree SHA; GitHub-tested snapshot SHA/tree if distinct; source ZIP; complete Git bundle; SHA-256 hashes; production-only diff; frozen-baseline proof; Java/Gradle versions; JUnit reports and exact counts; clean test/check result; reference regeneration evidence; runtime dependency/compiler report; GitHub Actions URL/artifact; performance/limits evidence and documented risks. Be candid about anything not independently verified or blocked by publishing permissions.
Stop after M6 production implementation and verification. Do not self-declare M6 accepted. Wait for ChatGPT's independent production review and owner acceptance before any merge, Paper adapter, or future milestone.