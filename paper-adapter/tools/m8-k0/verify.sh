#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd)"
evidence="${1:-$(mktemp -d "${TMPDIR:-/tmp}/bedwenton-m8-k0.XXXXXX")}";mkdir -p "$evidence"
cd "$root"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test 2>&1 | tee "$evidence/engine-clean-test.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false check 2>&1 | tee "$evidence/engine-check.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false -p paper-adapter clean test 2>&1 | tee "$evidence/adapter-clean-test.log"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false -p paper-adapter check 2>&1 | tee "$evidence/adapter-check.log"
python3 paper-adapter/tools/m8-k0/summarize.py "$root" | tee "$evidence/test-summary.json"
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false dependencies --configuration runtimeClasspath 2>&1 | tee "$evidence/engine-runtime.log"
grep -q 'No dependencies' "$evidence/engine-runtime.log"
python3 verification/m6/regenerate_all.py "$evidence/reference-regeneration" 2>&1 | tee "$evidence/reference-regeneration.log"
