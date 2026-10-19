# M8 — dynamic VFX and entity attachment (design package, R3)

Design only. Read in this order: `M8-R3-CHANGES.md` → `M8-R2-CHANGES.md` → `M8-R1-REPORT.md` → `M8-ARCHITECTURE-AND-CONTRACT.md` → `M8-ANCHOR-AND-MOTION-SPEC.md`
→ `M8-SHOWCASE-DESIGNS.md` → `M8-PERFORMANCE-AND-BUDGETS.md` → `M8-TEST-AND-SMOKE-PLAN.md`; SPI in `spi/`; evidence in
`validation/`.

## Reproduce the R1–R3 evidence from a clean checkout

```sh
git clone -b m8-spec-r3 m8-r3.bundle bedwenton-m8 && cd bedwenton-m8   # or: git checkout m8-spec-r3
export JAVA_HOME=/path/to/jdk-25 PATH="$JAVA_HOME/bin:$PATH"              # JDK 25, Python 3, network for Gradle/JUnit
docs/m8/validation/reproduce.sh /tmp/m8-evidence --with-mutants
```

Expected: 18 SPI files (19 classes) compile with `-Werror`; probe P1 max error ~1.6e-16, P2 ~3.6e-15 blocks, P3 phase table as in
`M8-SHOWCASE-DESIGNS.md`; M1–M6 415/415; seven datasets PASS; M7 adapter tests (without the Paper layer) 61/61; R2/R3 verification build 116/116
(55 design tests + 61 unchanged M7 tests); probe `PROBE RESULT: PASS`; mutants 23 caught of 23.
Probe P4 timings vary by machine and are indicative only.

## Check preservation

```sh
git diff --name-status ed5fc7bff548edf4b005e919495b22213a65b64e HEAD   # only docs/m8/... paths
git diff --name-status f04a843e4d5f76b3c47f9e9dfc07e872b826eaa5 HEAD   # R2 -> R3: only docs/m8/... paths
```
