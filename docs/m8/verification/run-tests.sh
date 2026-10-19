#!/usr/bin/env bash
# M8 R2/R3 design verification: runs the M8 R2/R3 tests and the unchanged M7 adapter tests (Paper layer excluded) and prints a
# per-test summary. Requires JDK 25, Python 3; network for Gradle 9.1.0 and JUnit 5.12.2 on first run.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../../.." && pwd)"; log="${1:-$here/build/m8-r3-tests.log}"
mkdir -p "$(dirname "$log")"
{
  echo "M8 R2/R3 design verification run: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "commit: $(git -C "$root" rev-parse HEAD 2>/dev/null || echo unknown); uncommitted tracked files: $(git -C "$root" status --porcelain 2>/dev/null | grep -v '^??' | wc -l | tr -d ' ')"
  java -version 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -1
  set +e
  "$root/gradlew" -p "$here" --no-daemon -q -Porg.gradle.java.installations.auto-download=false clean test 2>&1 | grep -v -E 'JAVA_TOOL_OPTIONS|^\s*$'
  status=${PIPESTATUS[0]}
  set -e
  echo "gradle exit: $status"
  python3 -I "$root/docs/m7/verification/summarize.py" "$here/build/test-results/test"
} 2>&1 | tee "$log"
grep -q 'TOTAL .* 0 not passed' "$log"
