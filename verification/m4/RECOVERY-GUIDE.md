# Recover M4 R2.1 for independent production review

The complete source ZIP and full-history Git bundle are local deliverables, not GitHub binary uploads. Their
SHA-256 values and final canonical evidence commit are in the delivery's `SHA256SUMS.txt` and `GIT-CHECKPOINT.txt`.

```sh
sha256sum -c SHA256SUMS.txt
git bundle verify minecraft-vfx-math-M4-r21.git.bundle
git clone -b m4-implementation minecraft-vfx-math-M4-r21.git.bundle minecraft-vfx-math
cd minecraft-vfx-math
git fsck --full
git log --oneline --decorate -10
```

Verify that HEAD equals the evidence commit in `GIT-CHECKPOINT.txt`, that it descends from the approved
`6ad7232cf030df649c70920893adb1f8a424a7cf`, and that production is exactly commit
`60164370d7f449b06a5f1c25501de96184745965`. Original M4 specification commits have not been rewritten.
All prior branch refs supplied in the original bundle are included.

The source ZIP contains every tracked project file, full verification evidence, and an additional root
`SOURCE-SHA256SUMS.txt` for validating every file without Git. It excludes `.git`, installed JDK/Gradle tools and
local caches. The bundle supplies native history. The historical root `SHA256SUMS.txt` belongs to an older handoff;
use the delivery and source manifests for current integrity checks.

Evidence locations:

- `verification/m4/VERIFICATION-REPORT.md`: outcomes, provenance, scope, warning and limitation review.
- `verification/m4/cloud/junit/TEST-*.xml`: all 41 fresh XML reports covering 313 tests.
- `verification/m4/cloud/logs/`: full successful Gradle logs, versions, reference comparisons and dependency guard.
- `verification/m4/cloud-job-log.txt`: complete timestamped Actions job log.
- `verification/m4/logs/`: blocked local Gradle attempt and successful supplemental native Java/JUnit checks.
- `verification/m4/production.diff`: exact seven-file production addition against accepted M3.
- `verification/m4/*SHA256SUMS.txt`: frozen baseline, approved specification, production source and evidence hashes.
- `verification/m4/input/`: original package metadata/checksums, patches and verification records. The original
  internal checksum list describes the original handoff archive; it is preserved as provenance, not as a new
  project-root manifest.

The isolated published branch `m4-actions-verification` contains an ordinary source snapshot and cloud evidence.
Its cloud snapshot commit differs from the canonical local commit because native Git transport was blocked;
the report records the matching complete tree SHA. Use the bundle for exact original specification ancestry.

Fresh verification requires a full JDK 25 and an environment permitting Gradle's local socket coordination:

```sh
java -version
javac -version
./gradlew --version
sha256sum -c verification/m4/FROZEN-M1-M3-SHA256SUMS.txt
sha256sum -c verification/m4/APPROVED-M4-SHA256SUMS.txt
sha256sum -c verification/m4/IMPLEMENTATION-SOURCE-SHA256SUMS.txt
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check
python3 tools/generate_m3_reference_vectors.py /tmp/m4-review-m3-reference
diff -r src/test/resources/m3/reference /tmp/m4-review-m3-reference
python3 tools/generate_m4_reference_vectors.py /tmp/m4-review-reference
diff -r src/test/resources/m4/reference /tmp/m4-review-reference
```

Expected: 313 successful tests, no failures/errors/skips, successful check and dependency guard, no production
warnings, unchanged references. Use fresh empty generator directories. Preserve the existing test expectations.
The added Actions workflow uses checkout/setup-java v5 and upload-artifact v6. A manual workflow dispatch with
`full_reference_regeneration=true` runs the release checks; ordinary pushes run bounded Java/hash checks. The
release marker in a commit message also enables full regeneration. Evidence-only publication uses `[skip ci]`.

Stop for independent production review. This package does not declare M4 accepted or authorize future milestones.
