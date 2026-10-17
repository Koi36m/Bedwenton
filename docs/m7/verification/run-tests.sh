#!/usr/bin/env bash
# M7 design verification: compile the SPI, the scratch reference and the proposed tests against the accepted engine,
# run the tests, and print a per-test summary. Usage (from anywhere): docs/m7/verification/run-tests.sh [log file]
# Requires: JDK 25 on JAVA_HOME (or a Gradle-detectable toolchain), Python 3, and network access to Maven Central and
# services.gradle.org on first run (Gradle 9.1.0 wrapper of the repository root, JUnit 5.12.2).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
log="${1:-$here/build/m7-test-run.log}"
mkdir -p "$(dirname "$log")"
{
  echo "M7 design verification run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "commit: $(git -C "$root" rev-parse HEAD 2>/dev/null || echo unknown)"
  echo "uncommitted changes in checkout: $(git -C "$root" status --porcelain 2>/dev/null | grep -v '^??' | wc -l | tr -d ' ') tracked file(s)"
  java -version 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -1
  "$root/gradlew" --version 2>/dev/null | grep -E '^Gradle '
  set +e
  "$root/gradlew" -p "$here" --no-daemon -q -Porg.gradle.java.installations.auto-download=false clean test 2>&1 | grep -v -E 'JAVA_TOOL_OPTIONS|^\s*$'
  status=${PIPESTATUS[0]}
  set -e
  echo "gradle exit: $status"
  python3 -I "$here/summarize.py" "$here/build/test-results/test"
} 2>&1 | tee "$log"
