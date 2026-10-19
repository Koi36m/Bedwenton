#!/usr/bin/env bash
# M8 R1/R2/R3 evidence reproduction (design evidence only; no M8 production code exists).
# Pass --with-mutants as the second argument to also run the 23 R2/R3 targeted mutants (about 10 minutes).
# Requires JDK 25 on JAVA_HOME, Python 3, network for Gradle 9.1.0 and JUnit 5.12.2 on first run. Run from anywhere.
set -euo pipefail
v="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$v/../../.." && pwd)"; out="${1:-$(mktemp -d)}"; mkdir -p "$out"
cd "$root"
echo "== engine jar (accepted M1-M6 sources, unchanged)"; ./gradlew --no-daemon -q jar
jar="$root/build/libs/minecraft-vfx-math-0.1.0-m1.jar"
echo "== SPI proposal compile (-Werror) against the engine and the unchanged M7 core"
javac -Xlint:all -Werror --release 25 -d "$out/spi" -cp "$jar" paper-adapter/src/main/java/workshop/vfx/paper/core/*.java $(find docs/m8/spi -name '*.java')
echo "   ok: $(find "$out/spi/workshop/vfx/paper/m8" -name '*.class' | wc -l) M8 SPI classes"
echo "== design probe P1-P4 (exits 1 on any P1-P3 violation)"; javac -Xlint:all -Werror -d "$out/probe" -cp "$jar" docs/m8/validation/M8DesignProbe.java
java -cp "$out/probe:$jar" M8DesignProbe > "$out/m8-design-probe.txt"; tail -1 "$out/m8-design-probe.txt"
echo "== M1-M6 suite"; ./gradlew --no-daemon -q clean test check; python3 -I docs/m7/verification/summarize.py build/test-results/test | tail -1
echo "== seven datasets"; python3 -I verification/m6/regenerate_all.py "$out/regenerated" | tail -1
echo "== M7 adapter tests without the Paper layer"; ./gradlew -p "$v/m7-core-rerun" --no-daemon -q clean test
python3 -I docs/m7/verification/summarize.py "$v/m7-core-rerun/build/test-results/test" | tail -1
echo "== M8 R2/R3 proposed tests + M7 compatibility + unchanged M7 adapter tests (scratch reference)"
"$v/../verification/run-tests.sh" "$out/m8-r3-tests.log" | tail -1
if [ "${2:-}" = "--with-mutants" ]; then echo "== M8 R2/R3 targeted mutants"; python3 -I "$v/../verification/run_mutants.py" "$out/m8-r3-mutants.txt" | tail -1; fi
echo "outputs in $out"
