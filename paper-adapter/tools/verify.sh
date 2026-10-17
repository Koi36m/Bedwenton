#!/usr/bin/env bash
# Failed Gradle commands always stop this runner, regardless of stale XML.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
evidence="${1:-$(mktemp -d "${TMPDIR:-/tmp}/bedwenton-m7-verification.XXXXXX")}"
mkdir -p "$evidence"
cd "$root"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test 2>&1 | tee "$evidence/engine-clean-test.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check 2>&1 | tee "$evidence/engine-check.log"
python3 paper-adapter/tools/summarize_tests.py build/test-results/test 415 | tee "$evidence/engine-summary.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false -p paper-adapter clean test 2>&1 | tee "$evidence/adapter-clean-test.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false -p paper-adapter check 2>&1 | tee "$evidence/adapter-check.log"
python3 paper-adapter/tools/summarize_tests.py paper-adapter/build/test-results/test 61 | tee "$evidence/adapter-summary.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false dependencies --configuration runtimeClasspath 2>&1 | tee "$evidence/engine-runtime.log"
grep -q 'No dependencies' "$evidence/engine-runtime.log"
python3 verification/m6/regenerate_all.py "$evidence/reference-regeneration" 2>&1 | tee "$evidence/reference-regeneration.log"
