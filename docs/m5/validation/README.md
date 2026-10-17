# M5 R1 validation evidence

These files are review scratch, kept as evidence. All runs used Java 25.0.4.1 and Gradle 9.1.0 against the
**throwaway** M5 reference implementation of the contract. That implementation is kept outside the repository and is
not delivered.

| File | Content |
|---|---|
| `reference-run-summary.txt` | the full suite (313 M1 to M4 + 50 M5 = 363 tests): 363/363 passed; `check` succeeds |
| `mutation-results.txt` | 33 mutants of the reference implementation, all killed, with the failing tests per mutant and the history of the two equivalent mutants (S15, S31) that simplified the reveal rule |
| `FrameLayoutProbe.java`, `frame-layout.txt` | the DM5-2 measurement: bytes and ns per point for per-particle records, `List<Vec3>` batches and packed batches |
| `PerformanceProbe.java`, `performance.txt` | indicative `evaluate` cost for the four example scenes and a dense 65,536-point scene |

Running the probes:
- `FrameLayoutProbe` needs only the M1 classes.
- `PerformanceProbe` is in package `workshop.vfx.scene`. Compile it together with
  `src/test/java/workshop/vfx/scene/M5TestSupport.java` against a build that contains an implementation.
