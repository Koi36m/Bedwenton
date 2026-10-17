# M6 conventions

These conventions apply to `workshop.vfx.delivery` and its tests, in addition to the M1 to M5 conventions.

## Code

- Java 25 with `--release 25`. No dependencies beyond the JDK and the accepted M1 to M5 packages.
- **Imports:**
  - `workshop.vfx.scene`, `workshop.vfx.math.core`, `workshop.vfx.math.time` and `java.util`;
  - not `java.util.concurrent` or `java.util.function`.
- **Forbidden:**
  - floating point in planning;
  - `Math.floor`, `ceil` and `round`, `StrictMath`;
  - randomness, clocks, `System`, threads, `synchronized`, `volatile`, logging;
  - `hashCode`-dependent output;
  - reflection, I/O;
  - Bukkit, Paper, Minecraft and Folia names.

  `M6BoundaryTest` scans for these.
- **Statics:** every static field is a `final` primitive, `String`, enum or empty-array constant.
- **Iteration:** every iteration that affects output follows a specified order: the allocation order, layer index,
  viewer order. Hash maps hold remaining budgets only and are never iterated for output.
- **Integers:** `long` for every sum and product of counts; `Math.floorMod` for every modulus of a caller-supplied
  ordinal; `(n + m - 1) / m` for the ceiling.
- **Validation:** throw `IllegalArgumentException` with a message `Type.method: rule`. Check sizes before proportional
  copies. Accessors with a bad index throw `IndexOutOfBoundsException`.
- **Immutability:**
  - inputs are copied (maps, lists, arrays);
  - outputs expose unmodifiable lists or scalar accessors, never internal arrays.

## Names and units

- **Points:** logical M5 points (one xyz). **Placements:** a point delivered to one recipient. **Calls** and
  **packets** are adapter terms and never appear in M6.
- **Ordinals:**
  - `tickOrdinal`: allocation rotation, one per call;
  - `frameOrdinal`: thinning phase, one per request.

  Both are caller-supplied, and any long is valid.
- **Ids:**
  - request ids: any long, unique per call;
  - world ids: `>= 0`;
  - viewer ids: `>= 0` and strictly increasing per request.

## Tests

- **Package and helpers:** tests live in `src/test/java/workshop/vfx/delivery` and use only the public API, plus
  `M6TestSupport` for builders and the independent invariant checker.
- **Test scenes:** every layer is a static ring with `Timing.once(duration)`, `ACTIVE_ONLY` and `Motion.IDENTITY`,
  evaluated at a paused playback. Geometry is irrelevant to planning; only sizes, styles, reveal and clip windows
  matter.
- **Failing request:** a request whose evaluation must fail uses `M6TestSupport.FAILING_ANCHOR`, a translation of
  `Double.MAX_VALUE` with scale 1e300, so any point with x > 0 overflows in M5.
- **Reference fixtures:** `src/test/resources/m6/reference/*.txt` and `MANIFEST.sha256`, generated only by
  `tools/generate_m6_reference_plans.py`. They are never edited by hand.
- **Randomness:** random property tests use fixed seeds.
