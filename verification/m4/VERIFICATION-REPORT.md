# M4 R2.1 production implementation and verification

Verification date: 2026-10-16. Status: implementation verified; independent production review and owner acceptance pending.

## Result

Fresh GitHub Actions execution passed **313 tests, 313 successful, 0 failures, 0 errors, 0 skips**. Gradle `check` and
`verifyNoRuntimeDependencies` succeeded. Production compilation emitted **0 warnings**. The production runtime
classpath has **no dependencies**. No assertions, tolerances, reference values, frozen source or existing contracts
were changed.

- Run: https://github.com/Koi36m/Bedwenton/actions/runs/38974334716
- Job: https://github.com/Koi36m/Bedwenton/actions/runs/38974334716/job/117220014719
- Published verification snapshot: `e3bf5578c126a7d3498be0b30fb431d9ec9a3099`, branch `m4-actions-verification`.
- Canonical local branch: `m4-implementation`.
- Exact production commit: **`60164370d7f449b06a5f1c25501de96184745965`**.
- Verified local source/workflow checkpoint: `9ce7906f3840988840f880b7f52d33a6b5052b75`.
- Complete source-tree SHA shared by that checkpoint and the Actions snapshot:
  **`4e04763dfb803a790a4dbb97c3c1ca28bf474b56`**.

The final evidence commit is recorded in the delivery's external `GIT-CHECKPOINT.txt`, avoiding a self-referential
commit identifier in this committed report.

## Input preservation and ancestry

- Submitted proposal ZIP SHA-256:
  `1abf94a6402e7d54554ae8b87c66f64ec0c98b515dd14ef039255a07c70cf44d` (matches owner-supplied value).
- ZIP CRC: pass. Internal checksums: **58/58** pass.
- Original `m4-spec-r21.bundle`: complete history, no prerequisites, 14 original refs; bundle verification and
  recovered repository `git fsck --full` pass.
- Accepted M3 baseline: `a6726c42a20940a6a6d2dd24477e6a642f6b8cbc`.
- Approved specification: `6ad7232cf030df649c70920893adb1f8a424a7cf`, descending from that exact baseline.
- Five original specification commits, unchanged:
  `3750b3491f1f4e579e0e1588a89bb051e62fd62a`, `664a23cfa9ce20c78bf8c244518ea25431103000`,
  `268b6aa51a9b6591f147ce983f2ef70e95d02fde`, `5eab460162bb6746f3664f04e93730a7314a2266`,
  `6ad7232cf030df649c70920893adb1f8a424a7cf`.
- Original specification branch refs are preserved locally and in the delivery bundle. No existing remote branches
  were changed, no history rewritten, no force push, and nothing merged into main.
- Independent review and documentation addendum were copied byte-identically into `docs/m4/` and committed as
  `3250a43` **before production coding**. Their hashes are in `input/DELIVERY-SHA256SUMS.txt`.
- The historical proposal's approval-status wording is preserved; the owner's explicit authorization governs this
  production pass. The independent review approves the specification, not an M4 production release.

## The five editorial clarifications

The additive `docs/m4/M4-R21-Documentation-Addendum.md` takes precedence for these wording issues. Historical
specification documents remain unchanged.

1. Absolute tolerance: the API retains a nonzero minimum relative contribution; `tau` is still the prescribed maximum.
2. Spacing: round half up selects the nearest interval count, without claiming to minimize realized-spacing error.
3. Scaled norm: small normalized ratios or their squares can underflow; a dominant ratio of 1 keeps the sum nonzero.
4. Manifest checks and independent reference regeneration are distinct. Per-push CI runs bounded tests, checks and
   hashes; manual verification or a commit marked `[m4-release-verification]` also regenerates all references.
5. Counts: 10 R2 regression tests plus 5 R2.1 regression tests; 59 M4 tests in 10 classes, 313 overall.

No normative algorithm or API was changed by reconciliation. No substantive specification contradiction was found.

## Production scope

Exactly six new public types under `workshop.vfx.math.sample`: `Endpoints`, `SampleLimits`, `ArcLengthTolerance`,
`ArcLengthTable`, `SampleSet`, `Sampler`. `SampleChecks` is the single package-private helper, sharing null, range
and count validation. Private immutable panel and speed records are internal to `ArcLengthTable`.

Production diff: `production.diff` (seven added files, 504 lines). Dependencies are limited to Java base classes,
M1 `Vec3`/`Pose` and M3 `Curve`; no M2 dependency or build configuration changes.

The implementation preserves the prescribed operation order: full panel width and hex half weights, normalized
speed pairs, all three product-order branches, global largest-error refinement with position tie breaks, tau floor,
ratio/linear partial modes, first-16-iteration Newton phase, 64-iteration inversion cap and better-end exits.
Sample counts and limits are checked before proportional allocation or curve evaluation. Sample sets copy arrays
and expose unmodifiable point lists. Sampling evaluates each point once, in index order.

All **672** tracked accepted M3-baseline files are byte-identical, including prior production, tests, contracts,
verification records, generators and the existing M3 workflow. All **50** tracked approved M4 handoff files are
byte-identical to the specification commit and archive. See the three SHA-256 manifests, `scope-and-integrity.txt`,
and the fresh cloud `source-integrity.txt`. There are no changes to Minecraft plugins or later milestones.

## Actual execution

The local environment has Oracle JDK **25.0.4.1**, javac **25.0.4.1**, Gradle **9.1.0**. Its initial Gradle build
failed before compilation because `FileLockContentionHandler` could not determine a usable wildcard IP. The
blocked console log and exit code 1 are preserved under `logs/`. No permission requests or repeat Gradle attempts
were made. Direct native Git access also failed to connect to the environment proxy.

As authorized, ordinary UTF-8 source/test/specification files were published through the GitHub connector to a
new isolated Actions branch. No ZIP, bundle, binary fragment or reconstructed historical commit was uploaded.
The connector creates a new commit rather than transporting native Git objects, so the cloud execution snapshot
has the accepted M3 commit as its parent. It is **not** presented as recovery of the original unpublished M4
specification history: that exact ancestry remains on canonical local `m4-implementation` and in the full-history
bundle. Equality of the entire source tree, not just production file hashes, ties cloud results to the local
verified checkpoint.

The cloud runner used:

```text
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS, mixed mode, sharing)
javac 25.0.4.1
Gradle 9.1.0
Revision: e45a8dbf2470c2e2474ccc25be9f49331406a07e
OS: Linux 6.17.0-1022-azure amd64
```

Full version output is in `cloud/logs/toolchain.txt`. The fresh commands executed successfully:

```sh
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false dependencies --configuration runtimeClasspath
```

| Suite | Tests | Failures/errors/skips |
|---|---:|---:|
| M1 | 125 | 0/0/0 |
| M2 | 66 | 0/0/0 |
| M3 original and independent review | 51 + 12 | 0/0/0 |
| M4 | 59 | 0/0/0 |
| Total | **313** | **0/0/0** |

All **41 downloaded JUnit XML files** were independently parsed again in this session and confirmed those totals.
Full Gradle output, test summaries, HTML reports, XML, the entire timestamped job log and run metadata are preserved.
The downloaded Actions artifact ZIP passed CRC and its published SHA-256 digest:
`6262d6d2b144cc36399f80514c98db7e419da78c67b4d00e83873be5e0bac361`.

Supplemental local Java 25 compilation and native JUnit Platform execution also passed 313/313. This independent
launcher run is labeled supplemental; it does not substitute for the required fresh Gradle execution above.

## Reference regeneration, determinism and resource limits

Both generators were run locally and freshly in Actions, with byte-for-byte comparisons against the supplied
CSVs and manifests. No checked-in reference data or generator was changed.

| Reference family | Row counts | Total | Result |
|---|---|---:|---|
| M3 | Bezier 645; easing 1,155; spiral 959 | 2,759 | byte-identical |
| M4 | lengths 336; inversions 216; uniform 2,310; counts 202 | 3,064 | byte-identical |

See cloud generator logs, empty reference-diff logs, `reference-summary.txt`, local `reference-comparison.txt`
and `reference-counts.json`.

The supplied permanent tests exercised the exact table evaluation accounting, budget rejection, per-call work
bounds (5 for partial lengths, 336 for inversion), oscillatory refinement, fixed inversion exits, allocation guards,
endpoint policies, stationary intervals, subnormal and extreme magnitudes, API shape and forbidden-dependency scan.
Their repetition/JIT and eight-thread checks passed. The F1–F5 regressions all passed. Review of the explicit
integer loop bounds confirms refinement is bounded by the chosen evaluation budget, depth by 50, inversion by 64,
binary search by 32 and sample loops by the validated count. No hidden clock, randomness or mutable global state
is used. Maximum supported limits are not a universal latency or memory guarantee for a plugin.

## Warnings and limitations

No production compiler warnings. Test compilation emits the same five JOML 1.10.9 classfile metadata warnings for
`Quaterniond`, `Vector3d`, `Vector4f`, `Vector3f` and `Vector4d`. Each has the exact message:

```text
warning: [classfile] RuntimeVisibleTypeAnnotations attribute introduced in version 52.0 class files is ignored in version 46.0 class files
```

The complete paths/messages are retained in `cloud/logs/clean-test.txt` and local `logs/javac-tests.txt`. These
are test-dependency metadata warnings, not oracle disagreements, tolerance failures or production implementation
warnings. The JOML dependency and all original test expectations remain unchanged.

Remaining limits are those of the approved contract: arbitrary black-box curves can fool quadrature; convergence
reports the error estimate, not a proof of true error for every curve. Discontinuous inverse primitives may use the
specified cap/collapse exit without a residual guarantee. Extremely small lengths can lose the permitted half-ulp
per rule; large panel counts accumulate left-to-right summation error. Shared tables require thread-safe curves.
Verification covered amd64, not ARM. No additional numerical issue was observed in this first implementation pass.

No M4.1, M5, emission scheduling or Paper integration was implemented. Nothing was merged into main.
Independent ChatGPT/Claude production review and owner acceptance remain pending.
