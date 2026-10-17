# M5 test specification and acceptance criteria

Status: PROPOSED, with the contract (revision 1).
- **Tests:** `src/test/java/workshop/vfx/scene/`: 50 test methods in 9 classes, plus the helper `M5TestSupport`.
- **Reference data:** `src/test/resources/m5/reference/`: 4 CSV files with 1,113 rows, and `MANIFEST.sha256`.
- **Generator:** `tools/generate_m5_reference_frames.py` (Python standard library; deterministic, byte-identical
  output).

## 1. Methodology: oracles independent of the recommended algorithm

| Oracle | Used for | Why it is independent |
|---|---|---|
| 40-digit Python generator | the four example scenes, at 66 frames (instant and playback combinations) | Decimal arithmetic; rotation **matrices** (from unit quaternions and Rodrigues' formula), not quaternion composition; Taylor-series trigonometry after exact turn reduction; **exact rational** M2 timing (`Fraction`); nested point maps, never composed poses |
| Test-side matrix oracle (`M5TestSupport.oracle`) | random motion chains and anchors (1,500 cases), extreme magnitudes, overlapping layers | 3x3 matrices; Rodrigues' formula with the angle formed directly (no turn reduction); scale as `from + (to - from) e` instead of the contract's `(1 - e) from + e to` |
| M1 `Quat.axisAngle` (`Math` trigonometry) | spin direction and angle (2,000 random cases) | a different construction from the contract's `StrictMath` quarter-turn reduction |
| Accepted M2, M3 and M4 behavior | time decisions, easing, shapes | M5 is specified to **reuse** them; tests call the accepted classes directly, not a re-implementation |
| Normative-order identities | `pose == anchor.then(motion.poseAt(L))`, `point == pose.toWorld(local)` bit for bit | these restate the contract (section 8). They are checked only where the contract mandates the exact operation, and the coordinates are also checked against the oracles above |
| Instrumented curves | 0 curve calls during evaluation | observation, not computation |
| Bitwise repetition | determinism across repetition, JIT and 8 threads | a property, not a value |

No expected value is produced by the recommended implementation. The tolerance is
`2^-40 (1 + M)(1 + abs(turns))` (contract 10.3), where M is the coordinate magnitude. Exact quantities are compared
exactly: stages, layers, styles, cycles, counts, local ticks, and endpoint scales and translations.

## 2. Inventory

| Class | Tests | Contract | Covers |
|---|---|---|---|
| `SceneValidationTest` | 7 | 3 to 6 | style keys and layer names (patterns, lengths, nulls); shapes (non-finite, null elements, `MAX_POINTS`, 0 points, copying); motions (axis, turns, phase, scale range, translations, nulls, 16-step limit); limits; scenes (lifetime incl. `-0.0` and `UNBOUNDED`, duplicates, layer and point preflight, copies, empty scenes); evaluate arguments |
| `TimeSemanticsTest` | 8 | 7 | GAME/COSMETIC mismatch in both directions; scene window (BEFORE, ACTIVE, AFTER, `UNBOUNDED`, -0.0); half-open passes, delays, `HOLD_AFTER`; loop and ping-pong cycles (even ping-pong ends at 0); pause, resume, seek, negative and zero rates, restart; GAME overflow, COSMETIC wrap, non-finite and astronomically large ticks; signed zero; reported local ticks |
| `CompositionTest` | 8 | 5, 8 | composition order vs the matrix oracle; spin direction, whole and negative turns, phase, 2,000 random spins vs M1 `axisAngle`; exact scale and translate endpoints and easing; the normative-order identity (300 random chains); shared motions (grouping); inverses and inverse anchors (500 cases); finite-result failures; 1,500 random chains vs the oracle |
| `FrameContractTest` | 7 | 8 | order and overlap (40 layers); omission of hidden, empty and unrevealed layers; empty scenes; `PREFIX` counts for n = 1 to 1,000, including `nextDown(end)`, held and ping-pong; immutability; exact single-point placement; point counts against `maxPointsPerFrame` at 765 instants; raw progress for reveal |
| `BudgetTest` | 4 | 9 | preflight (4,096 x 256 = 2^20 accepted, 4,096 x 257 rejected, no overflow); the largest frame (2^20 points); 0 curve calls over 1,000 frames; 16-step depth |
| `DemoReferenceTest` | 5 | 4 to 8 | manifest; the four example scenes (section 4) against the 40-digit oracle: 1,113 reference rows |
| `M5DeterminismTest` | 2 | 10.1 | bit-identical repetition (25 rounds); 8 threads on shared scenes |
| `M5BoundaryTest` | 3 | 1, 2, 10 | imports (M1 to M4 and `java.util` only; no Bukkit, Paper, Minecraft or I/O); forbidden calls (`Math` trigonometry, clocks, threads, randomness) and no mutable statics; exact API shape |
| `M5AdversarialTest` | 6 | 7 to 10 | overlapping animations (200 layers); stateless frames and the cancellation/state distinction; 0- and 1-sample shapes; extreme magnitudes (anchors to 1e15, scales 1e-9 to 1e9, subnormal and 1e100 local points); hidden layers never leak (2,000 instants); signed-zero inputs |

**Kickoff checklist (item 4):**

| Case | Test |
|---|---|
| overlapping animations | `M5AdversarialTest.overlapping`, `FrameContractTest.order` |
| inactive layers | `FrameContractTest.omission`, `M5AdversarialTest.inactiveLayers` |
| empty scenes | `FrameContractTest.omission`, `SceneValidationTest.scenes` |
| huge counts | `BudgetTest.preflight`, `.largestFrame` |
| cancellation/state distinction | `M5AdversarialTest.statelessFrames`, `TimeSemanticsTest.playbackControls` |
| 0/1 samples | `M5AdversarialTest.zeroAndOneSamples`, `FrameContractTest.reveal` |
| extremely large or small tick values | `TimeSemanticsTest.extremeInstants`, `.sceneWindow` |
| GAME/COSMETIC domain mismatch | `TimeSemanticsTest.domains` |
| signed zero | `TimeSemanticsTest.signedZero`, `M5AdversarialTest.signedZeroInputs` |
| deterministic ordering | `FrameContractTest.order`, `M5DeterminismTest` |

## 3. Reference data (`src/test/resources/m5/reference`)

| File | Rows | Content |
|---|---|---|
| `healing.csv` | 540 | 34 frames: forward playback (before the start, starts, ends of pulses, gaps, the scene end, 1e12); reverse (rate -1); a rotated and scaled anchor; paused; half rate |
| `orbit.csv` | 43 | 7 COSMETIC frames across the `long` wrap, with a moving, turning anchor; before the start |
| `trident.csv` | 91 | 7 frames: trident pose snapshots (including a non-unit quaternion that M1 normalizes) from the start to the scene end |
| `constellation.csv` | 439 | 18 frames, through every link stage, holds, the scene end and a seek |

Each row holds:
- the case and instant, the playback (anchor, local ticks, rate, paused), and the anchor pose inputs;
- the expected frame stage and local ticks;
- per point: the layer index, name, style, M2 stage, cycle, progress, point index and x, y, z.

An empty frame has one row with `layer_index = -1`. The generator also asserts that no `PREFIX` reveal falls within
1/1000 of a count boundary, so that the exact-rational and double decisions cannot differ.

## 4. Example scene definitions (normative for the tests; written for both the Java tests and the generator)

All four use only the public API of contract section 2, plus M3 and M4 for shapes.

**A. Healing ritual** (`TimeDomain.GAME`, lifetime 60, `SceneLimits(8, 256)`):
- **Shape `ring`:** `Sampler.uniformParameter(Spiral.circle(1.0), 24, EXCLUDE_END)`.
- **Layers `pulse-0`, `pulse-1`, `pulse-2`:**
  - timing `Timing.once(16).withDelay(20 k)`, `ACTIVE_ONLY`, `ALL`;
  - motion `translate((0, 0.1, 0) -> (0, 1.0, 0), same timing, LINEAR).then(scale(0.5 -> 3.0, same timing, QUAD_OUT))`;
  - style `bedwenton:heal_ring`.
- **Anchors:** `(100.5, 64, -20.5)` with identity rotation and scale 1; and `(-12.25, 3.5, 7)` with rotation
  `(0.5, 0.5, 0.5, -0.5)` and scale 2.

**B. Orbiting sparkles** (`TimeDomain.COSMETIC`, `UNBOUNDED`, `SceneLimits(16, 64)`):
- **Shape:** the point `(1.5, 0, 0)`.
- **Layers `spark-k`, k = 0 to 3:** timing `Timing.loop(40)`; motion `spin(+Y, 1, k/4, Timing.loop(40), LINEAR)`;
  style `bedwenton:spark`.
- **Layers `spark-0-trail-j`, j = 1 to 3:** the same, with phase `-3j/100` and style `bedwenton:spark_trail`.
- **Playback:** starts at `cosmeticNanos(2^63 - 1 - 400,000,000)`.

**C. Moving trident helix** (`TimeDomain.GAME`, lifetime 30, `SceneLimits(4, 64)`):
- **Shape:** `Sampler.uniformDistance(ArcLengthTable.build(Spiral.helix(0.6, 2.0, 3), ArcLengthTolerance(0, 2^-30),
  SampleLimits(64, 4096)), 37, INCLUDE_END)`.
- **Layer `water-helix`:**
  - timing `Timing.once(30)`, `ACTIVE_ONLY`, `PREFIX`;
  - motion `fixed(translation (0, -1, 0)).then(spin(+Y, 2, 0, Timing.once(30), LINEAR))`;
  - style `bedwenton:water`.
- **Anchors:** the 7 trident snapshots listed in the generator. The rows carry the exact inputs.

**D. Astral Prism constellation** (`TimeDomain.GAME`, lifetime 120, `SceneLimits(8, 64)`):
- **Stars:** `Sampler.uniformParameter(Spiral.circle(2.0), 5, EXCLUDE_END)`.
- **Shared motion `group`:** `spin(+Y, 1, 0, Timing.loop(120), LINEAR).then(fixed(rotation (0.98, 0.2, 0, 0)))`.
- **Layer `stars`:** timing `once(120)`, `ACTIVE_ONLY`, `ALL`, `group`, style `astral:star`.
- **Layers `link-j`, j = 0 to 4:**
  - shape `Sampler.uniformParameter(LineSegment(star_j, star_(j+2) mod 5), 9, INCLUDE_END)`;
  - timing `once(10).withDelay(10 + 12 j)`, `HOLD_AFTER`, `PREFIX`, `group`;
  - style `astral:link`.
- **Anchor:** `(-300.5, 80, 220.5)`, a yaw of 30 degrees, scale 1.5.

## 5. Validation of the tests (before handoff)

- **Throwaway reference implementation:** the tests were run against an implementation of this contract, kept outside
  the repository and not delivered. Result: **50/50 passed**. With the 313 baseline tests: **363/363** on Java
  25.0.4.1 (`validation/reference-run-summary.txt`).
- **Independence check:** the first run of the reference against the Python frames passed all 1,113 rows unchanged.
  Neither side was adjusted to the other.
- **Mutation analysis** (`validation/mutation-results.txt`): 33 mutants, all killed (section 7).
- The generator is deterministic: regenerating gives byte-identical files and manifest.

## 6. Acceptance criteria for the M5 implementation

1. **Approval first.** The owner approves the contract and DM5-1 to DM5-12, possibly after revision, with the tests
   revised to match before implementation.
2. **M1 to M4 untouched.** Compared with `e3bf5578c126a7d3498be0b30fb431d9ec9a3099`, nothing changes outside
   `src/main/java/workshop/vfx/scene`, except this proposal's own files (`docs/m5`, `src/test/java/workshop/vfx/scene`,
   `src/test/resources/m5`, `tools/generate_m5_reference_frames.py`).
3. **Tests untouched.** The M5 tests, reference data and generator are byte-identical to the handoff (`SHA256SUMS.txt`).
4. **Production scope.** Only the ten public types of contract section 2, plus optional package-private helpers.
   No dependencies, and no Paper or Bukkit code.
5. **Java 25 run.** `./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false clean test` reports
   **363 tests, 363 passed** (313 + 50). `check` succeeds.
6. **Evidence** as for M1 to M4, then ChatGPT's independent review.

Before the implementation exists, the M5 tests do not compile; this is expected.

## 7. Mutation results

33 plausible defects were injected one at a time into the throwaway reference implementation. **All 33 are killed.**
The per-mutant failing tests are in `validation/mutation-results.txt`. The mutants cover:
- spin: half angle, direction, phase, easing, axis normalisation, turns limit;
- scale and translate endpoints;
- composition order of the steps and of the anchor;
- visibility: HOLD_AFTER ignored, AFTER always visible, BEFORE visible;
- both ends of the scene window;
- reveal (ceil, ignored), empty batches and layer order;
- the finite-position, non-finite-ticks, domain and duplicate-name checks;
- -0.0 normalisation;
- every limit (off by one, or removed);
- coordinate copying and the batch pose;
- the step limit, scale range and lifetime.

Two earlier mutants survived and were **equivalent**, which simplified the contract rather than the tests:
- S31: the `min(n - 1, ...)` clamp of the reveal count;
- S15: a `progress >= 1` branch.

The reveal count is now the single expression `floor(progress n)`, with its proof in contract 8.3.
