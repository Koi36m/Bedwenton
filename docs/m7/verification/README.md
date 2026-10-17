# M7 design verification build (R3)

This build is **not** the production Paper adapter. It compiles and tests three things against the **real, accepted
M1–M6 engine** of this repository, without changing it:
- the M7 SPI proposal (`docs/m7/spi/`);
- a **throwaway scratch reference** of that SPI (`docs/m7/reference/`);
- the proposed offline tests (`docs/m7/tests/`).

No Paper or Bukkit class is involved anywhere.

| Part | What it is | Real or scratch |
|---|---|---|
| `workshop.vfx.delivery.DeliveryPlanner`, the M5 scene classes, the demo recipe | the accepted engine, built from the repository root through `includeBuild('../../..')` | **real accepted code** (unchanged since `c43a66e`) |
| `docs/m7/spi/` | SPI interfaces and records proposed for the adapter | proposal |
| `docs/m7/reference/` | `DefaultPlanEmitter`, `OwnerAwareRecipientRule`, `OwnerFirstViewerSelection`, `SessionStyleHealth` | **scratch reference**, written only to show the contract is implementable and the tests are meaningful; it is never production |
| `docs/m7/tests/` | 25 JUnit 5 tests in 5 classes, plus the `DemoFixture` helper | proposed tests (production must carry them or equivalents) |

## Requirements

- A JDK 25 (tested with 25.0.4.1) on `JAVA_HOME`, or one Gradle can detect.
- Python 3 (standard library only; tested with 3.13).
- Git.
- First run only: network access to `services.gradle.org` (the root's Gradle 9.1.0 wrapper) and Maven Central
  (`org.junit.jupiter:junit-jupiter:5.12.2`, `org.junit.platform:junit-platform-launcher`). The engine itself has no
  runtime dependencies.

## From a clean checkout or the bundle

```sh
git clone -b m7-spec-r3 m7-spec-r3.bundle bedwenton-m7 && cd bedwenton-m7   # or: git checkout m7-spec-r3
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"

docs/m7/verification/run-tests.sh /tmp/m7-tests.log            # expect: TOTAL 25 tests, 25 passed, 0 not passed
python3 docs/m7/verification/run_mutants.py /tmp/m7-mutants.txt # expect: MUTANTS: 13 caught of 13 (about 5 minutes)

# M1-M6 regression (accepted build and script, unchanged)
./gradlew --no-daemon clean test check                           # expect 415 tests, 415 passed
python3 verification/m6/regenerate_all.py /tmp/m6-regenerated    # expect: PASS: all seven ... byte-identically
```

**What the scripts do:**
- `run-tests.sh` runs `../../../gradlew -p docs/m7/verification clean test`, then `summarize.py`, which prints one
  `PASS`/`FAIL` line per test and the totals from the JUnit XML. It writes the same output to the log file given.
- `run_mutants.py` copies `docs/m7/reference/` to a temporary directory for each mutant in `mutants.py`, applies one
  exact textual replacement (it must occur exactly once), and runs the build with `-Pm7RefDir=<copy>`. It then lists
  the failing tests. The committed reference is never edited.
- Compilation uses `-Xlint:all -Werror`.

**Logs** of the runs made for R3 are in `logs/`:
- `r2-state-*`: the unchanged R2 code, reproduced with this build;
- `r3-tests-before-fix.log`: the new R3 tests against the R2 logic (7 failures, which reproduce the reviewed
  defects);
- `r3-tests.log`, `r3-mutants.txt`: the final R3 results;
- `r3-m1-m6-regression.log`: the 415 tests and the seven datasets.
